package app.playgram.core.download

import android.content.Context
import android.os.Environment
import android.util.Log
import app.playgram.PlaygramApp
import app.playgram.core.database.DownloadDao
import app.playgram.core.database.DownloadEntity
import app.playgram.core.database.KinoDatabase
import app.playgram.core.model.DownloadItem
import app.playgram.core.model.DownloadStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads plain http(s) links with resume support, and `tg://file/<id>` links through TDLib.
 */
class DownloadManager(private val context: Context) {
    private val db = KinoDatabase.getInstance(context)
    private val downloadDao: DownloadDao = db.downloadDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    /** Download id -> TDLib file id it is fetching (link ids can be stale after a restart). */
    private val telegramFiles = ConcurrentHashMap<String, Int>()

    /** TDLib files a download is fetching right now; the player must not stop or delete these. */
    fun busyTelegramFileIds(): Set<Int> = telegramFiles.values.toSet()

    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads().map { list ->
        list.map { it.toModel() }
    }

    val activeDownloads: Flow<List<DownloadItem>> = downloadDao.getActiveDownloads().map { list ->
        list.map { it.toModel() }
    }

    fun enqueueDownload(
        id: String,
        title: String,
        url: String,
        totalBytes: Long,
        mimeType: String = "video/mp4"
    ) {
        scope.launch {
            val destDir = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
            val destFile = uniqueFile(destDir, fileNameFor(title, url, mimeType))

            val entity = DownloadEntity(
                id = id,
                title = title,
                url = url,
                totalBytes = totalBytes.coerceAtLeast(0),
                downloadedBytes = 0,
                speedBytesPerSec = 0,
                status = DownloadStatus.DOWNLOADING.name,
                mimeType = mimeType,
                destinationPath = destFile.absolutePath,
                addedTimeMs = System.currentTimeMillis()
            )
            downloadDao.insertOrUpdate(entity)
            startDownloadJob(entity.toModel())
        }
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            downloadDao.getDownloadById(id)?.let { e ->
                telegramFileIdFor(id, e.url)?.let { PlaygramApp.instance.telegramManager.cancelFileDownload(it) }
            }
            downloadDao.updateStatus(id, DownloadStatus.PAUSED.name)
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            val entity = downloadDao.getDownloadById(id) ?: return@launch
            if (entity.status == DownloadStatus.COMPLETED.name) return@launch
            downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING.name)
            startDownloadJob(entity.toModel())
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            val entity = downloadDao.getDownloadById(id)
            if (entity != null) {
                // Cancelling also drops what TDLib fetched so far; a cancelled download keeps no space.
                telegramFileIdFor(id, entity.url)?.let {
                    PlaygramApp.instance.telegramManager.cancelFileDownload(it)
                    PlaygramApp.instance.telegramManager.deleteCachedFile(it)
                }
                val f = File(entity.destinationPath)
                if (f.exists()) f.delete()
            }
            downloadDao.deleteById(id)
        }
    }

    private fun telegramFileIdFor(id: String, url: String): Int? = telegramFiles[id] ?: telegramFileId(url)

    private fun telegramFileId(url: String): Int? =
        if (url.startsWith("tg://file/")) url.removePrefix("tg://file/").substringBefore('?').toIntOrNull() else null

    private fun startDownloadJob(item: DownloadItem) {
        activeJobs[item.id]?.cancel()
        val job = scope.launch {
            try {
                val tgId = telegramFileId(item.url)
                if (tgId != null) downloadTelegramFile(item, tgId) else downloadHttp(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${item.title}", e)
                downloadDao.insertOrUpdate(
                    DownloadEntity.fromModel(item.copy(status = DownloadStatus.FAILED, speedBytesPerSec = 0, errorMessage = e.message ?: "Download failed"))
                )
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    // ------------------------------------------------------------------ http(s)

    private suspend fun downloadHttp(item: DownloadItem) = coroutineScope {
        val target = File(item.destinationPath)
        target.parentFile?.mkdirs()
        var downloaded = if (target.exists()) target.length() else 0L

        val conn = (URL(item.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Playgram/1.0.0")
            if (downloaded > 0) setRequestProperty("Range", "bytes=$downloaded-")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("Server answered HTTP $code")
            val resumed = code == HttpURLConnection.HTTP_PARTIAL
            if (!resumed) downloaded = 0L // server ignored the range, start over
            val length = conn.contentLengthLong
            val total = when {
                length > 0 -> length + downloaded
                item.totalBytes > 0 -> item.totalBytes
                else -> 0L
            }
            if (total > 0) downloadDao.insertOrUpdate(DownloadEntity.fromModel(item.copy(totalBytes = total, downloadedBytes = downloaded)))

            var lastTime = System.currentTimeMillis()
            var lastBytes = downloaded
            val buffer = ByteArray(64 * 1024)
            conn.inputStream.use { input ->
                FileOutputStream(target, resumed).use { out ->
                    while (isActive) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 500) {
                            val speed = (downloaded - lastBytes) * 1000 / (now - lastTime)
                            downloadDao.updateProgress(item.id, downloaded, speed)
                            lastTime = now
                            lastBytes = downloaded
                        }
                    }
                }
            }
            if (isActive) {
                downloadDao.insertOrUpdate(
                    DownloadEntity.fromModel(
                        item.copy(
                            totalBytes = if (total > 0) total else downloaded,
                            downloadedBytes = downloaded,
                            speedBytesPerSec = 0,
                            status = DownloadStatus.COMPLETED,
                            completionTimeMs = System.currentTimeMillis(),
                            errorMessage = null
                        )
                    )
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------------------------ Telegram files

    private suspend fun downloadTelegramFile(item: DownloadItem, linkFileId: Int) = coroutineScope {
        val tg = PlaygramApp.instance.telegramManager
        val resolved = CompletableDeferred<org.drinkless.tdlib.TdApi.File?>()
        tg.resolveFile(android.net.Uri.parse(item.url)) { resolved.complete(it) }
        val fileId = resolved.await()?.id ?: error("This file is no longer available on Telegram")
        telegramFiles[item.id] = fileId
        coroutineContext[Job]?.invokeOnCompletion { telegramFiles.remove(item.id) }
        val updates = tg.fileUpdates.filter { it.id == fileId }
        tg.startFileDownload(fileId, priority = 16)

        var lastTime = System.currentTimeMillis()
        var lastBytes = item.downloadedBytes
        var done = false

        // The file may already be complete locally.
        val initial = CompletableDeferred<org.drinkless.tdlib.TdApi.File?>()
        tg.getFile(fileId) { initial.complete(it) }
        initial.await()?.let { f -> if (f.local.isDownloadingCompleted) { finishTelegram(item, f); done = true } }

        if (!done) {
            val finished = updates.first { f ->
                val total = if (f.size > 0) f.size else f.expectedSize
                val now = System.currentTimeMillis()
                if (!f.local.isDownloadingCompleted && now - lastTime >= 500) {
                    val got = f.local.downloadedSize
                    val speed = (got - lastBytes).coerceAtLeast(0) * 1000 / (now - lastTime)
                    if (total > 0 && total != item.totalBytes) {
                        downloadDao.insertOrUpdate(DownloadEntity.fromModel(item.copy(totalBytes = total, downloadedBytes = got, speedBytesPerSec = speed)))
                    } else {
                        downloadDao.updateProgress(item.id, got, speed)
                    }
                    lastTime = now
                    lastBytes = got
                }
                f.local.isDownloadingCompleted
            }
            finishTelegram(item, finished)
        }
    }

    private suspend fun finishTelegram(item: DownloadItem, f: org.drinkless.tdlib.TdApi.File) {
        val src = File(f.local.path)
        val dest = File(item.destinationPath)
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()
            if (src.absolutePath != dest.absolutePath) src.copyTo(dest, overwrite = true)
        }
        // The copy in Downloads is the saved file; TDLib's cache copy would only take the space twice.
        if (src.absolutePath != dest.absolutePath && dest.exists()) PlaygramApp.instance.telegramManager.deleteCachedFile(f.id)
        val size = if (dest.exists()) dest.length() else f.size
        downloadDao.insertOrUpdate(
            DownloadEntity.fromModel(
                item.copy(
                    totalBytes = size,
                    downloadedBytes = size,
                    speedBytesPerSec = 0,
                    status = DownloadStatus.COMPLETED,
                    completionTimeMs = System.currentTimeMillis(),
                    errorMessage = null
                )
            )
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun fileNameFor(title: String, url: String, mime: String): String {
        val safe = title.replace(Regex("[^\\p{L}\\p{N}._ ()\\[\\]-]"), "_").trim().ifBlank { "download" }
        val hasExt = Regex("\\.[A-Za-z0-9]{2,4}$").containsMatchIn(safe)
        if (hasExt) return safe
        val urlExt = Regex("\\.([A-Za-z0-9]{2,4})(?:$|\\?)").find(url.substringBefore('#'))?.groupValues?.get(1)
        val ext = urlExt ?: when {
            mime.contains("matroska") -> "mkv"
            mime.contains("webm") -> "webm"
            mime.startsWith("audio/") -> "mp3"
            else -> "mp4"
        }
        return "$safe.$ext"
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    companion object {
        private const val TAG = "PlaygramDL"

        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                val inst = DownloadManager(context.applicationContext)
                INSTANCE = inst
                inst
            }
        }
    }
}
