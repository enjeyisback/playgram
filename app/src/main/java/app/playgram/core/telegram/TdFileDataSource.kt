package app.playgram.core.telegram

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.drinkless.tdlib.TdApi
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lets ExoPlayer play `tg://file/<id>` while TDLib is still downloading it, the way a local HTTP
 * stream server does:
 *
 * - how much is ready at the read position comes from TDLib's GetFileDownloadedPrefixSize, which
 *   counts what is on disk at any offset, not just around the range TDLib happens to be fetching;
 * - a missing position asks TDLib for `DownloadFile(offset = position, limit = 0)`, unless TDLib is
 *   already fetching close enough before it that the bytes will arrive by themselves;
 * - a read waits for data instead of pushing TDLib around: it asks again after [ASK_AGAIN_MS] and
 *   gives up after [GIVE_UP_MS].
 *
 * Extractors (Matroska especially) read a byte or two at a time, so the ready span is remembered
 * and TDLib is only asked again once a read goes past it.
 */
@UnstableApi
class TdFileDataSource(
    private val telegram: TelegramManager,
    private val session: StreamSession = StreamSession.OPEN
) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var fileId = 0
    private var size = 0L
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var raf: RandomAccessFile? = null
    private var rafPosition = -1L
    private var opened = false

    /** Bytes known to be on disk from [readyFrom]; reads inside it need no TDLib round trip. */
    private var readyFrom = -1L
    private var readyBytes = 0L
    private var askedAt = -1L
    private var askedFor = -1L

    private companion object {
        /** TDLib fetching this close before the read position will reach it soon; don't move it. */
        const val LEAD = 24L shl 20
        const val ASK_AGAIN_MS = 20_000L
        const val GIVE_UP_MS = 90_000L
        const val POLL_MS = 100L
    }

    class Factory(private val telegram: TelegramManager, private val session: StreamSession = StreamSession.OPEN) : DataSource.Factory {
        override fun createDataSource(): DataSource = TdFileDataSource(telegram, session)
    }

    private fun <T> await(timeoutS: Long, call: ((T?) -> Unit) -> Unit): Pair<Boolean, T?> {
        val latch = CountDownLatch(1)
        var result: T? = null
        call { result = it; latch.countDown() }
        val answered = try {
            latch.await(timeoutS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException(e)
        }
        return answered to result
    }

    private fun fetch(): TdApi.File? = await<TdApi.File>(5) { telegram.getFile(fileId, it) }.second

    /** Right after a cold start TDLib can take 10+ seconds to answer, so this waits up to a minute. */
    private fun resolve(link: Uri): TdApi.File {
        val (answered, f) = await<TdApi.File>(60) { telegram.resolveFile(link, it) }
        if (!answered) throw IOException("Telegram is still connecting. Try again in a moment.")
        return (f ?: throw IOException("This file is no longer available on Telegram")).also { fileId = it.id }
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        if (dataSpec.uri.lastPathSegment?.toIntOrNull() == null) throw IOException("Bad Telegram file link")
        transferInitializing(dataSpec)
        val f = resolve(dataSpec.uri)
        size = if (f.size > 0) f.size else f.expectedSize
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else (size - position)
        readyFrom = -1L
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    /** Ready bytes at [position], from the remembered span or TDLib. */
    private fun readyAt(): Long {
        if (readyFrom >= 0 && position >= readyFrom && position < readyFrom + readyBytes) {
            return readyFrom + readyBytes - position
        }
        val n = await<Long>(5) { telegram.downloadedPrefixSize(fileId, position, it) }.second ?: 0L
        readyFrom = position
        readyBytes = n
        return n
    }

    /** Asks TDLib for [position] unless it is already fetching just before it. */
    private fun askFor(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && askedFor == position && now - askedAt < 200) return
        if (!force) {
            val f = fetch()
            if (f != null && f.local.isDownloadingActive) {
                val fetchingUpTo = f.local.downloadOffset + f.local.downloadedPrefixSize
                if (position >= f.local.downloadOffset && position <= fetchingUpTo + LEAD) return
            }
        }
        telegram.requestFileRange(fileId, position, 0L, session)
        askedFor = position
        askedAt = now
    }

    private fun openFile(): RandomAccessFile? {
        raf?.let { return it }
        val path = fetch()?.local?.path?.takeIf { it.isNotEmpty() } ?: return null
        // TDLib can still list a partial that was deleted from disk (cache cleared).
        if (!java.io.File(path).exists()) return null
        return RandomAccessFile(path, "r").also { raf = it; rafPosition = -1L }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT

        val started = System.currentTimeMillis()
        var lastAsk = 0L
        while (true) {
            val ready = readyAt()
            val file = if (ready > 0) openFile() else null
            if (ready > 0 && file != null) {
                if (rafPosition != position) file.seek(position)
                val toRead = minOf(length.toLong(), ready, if (remaining == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else remaining).toInt()
                val n = file.read(buffer, offset, toRead)
                if (n <= 0) { readyFrom = -1L; return C.RESULT_END_OF_INPUT }
                position += n
                rafPosition = position
                if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
                bytesTransferred(n)
                return n
            }
            readyFrom = -1L
            val now = System.currentTimeMillis()
            when {
                lastAsk == 0L -> { askFor(force = false); lastAsk = now }
                now - lastAsk > ASK_AGAIN_MS -> { askFor(force = true); lastAsk = now }
            }
            if (now - started > GIVE_UP_MS) throw IOException("No data from Telegram for 90 s")
            if (session.closed) throw IOException("Player closed")
            try { Thread.sleep(POLL_MS) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException(e) }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { raf?.close() } catch (_: Exception) {}
        raf = null
        rafPosition = -1L
        readyFrom = -1L
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
