package app.playgram.core.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import app.playgram.PlaygramApp
import app.playgram.core.telegram.TdFileDataSource
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Serves Telegram files at http://127.0.0.1:<port>/<token>/<name> so other players (VLC, MX Player,
 * mpv) can stream them with seeking. Only loopback is bound, and each link gets a random token, so
 * other apps cannot read files they were not handed.
 */
@UnstableApi
object StreamServer {
    private const val TAG = "PlaygramStream"
    private var server: ServerSocket? = null
    private val links = ConcurrentHashMap<String, Pair<String, String>>()
    val activeConnections = AtomicInteger(0)
    @Volatile var lastActivityMs = 0L
        private set

    /** A URL another app can play for the `tg://` [link]. Starts the server on first use. */
    fun urlFor(context: Context, link: String, title: String, mime: String): String {
        val port = start(context)
        val token = UUID.randomUUID().toString().replace("-", "")
        links[token] = link to mime
        lastActivityMs = System.currentTimeMillis()
        PlaybackService.start(context)
        return "http://127.0.0.1:$port/$token/${Uri.encode(title.ifBlank { "video" })}"
    }

    @Synchronized
    private fun start(context: Context): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        server = s
        thread(isDaemon = true, name = "PlaygramStreamServer") {
            while (!s.isClosed) {
                val socket = try { s.accept() } catch (_: IOException) { break }
                thread(isDaemon = true, name = "PlaygramStreamConn") { serve(socket) }
            }
        }
        return s.localPort
    }

    private fun serve(socket: Socket) {
        activeConnections.incrementAndGet()
        try {
            socket.use { handle(it) }
        } catch (e: IOException) {
            // Players drop connections all the time when they seek; nothing to report.
        } catch (e: Exception) {
            Log.w(TAG, "Stream request failed", e)
        } finally {
            activeConnections.decrementAndGet()
            lastActivityMs = System.currentTimeMillis()
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 60_000
        val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val request = input.readLine() ?: return
        val method = request.substringBefore(' ')
        val path = request.substringAfter(' ').substringBefore(' ')
        var range: String? = null
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
        }
        val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
        val (link, mime) = links[path.trimStart('/').substringBefore('/')] ?: run {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); return
        }
        lastActivityMs = System.currentTimeMillis()

        val source = TdFileDataSource(PlaygramApp.instance.telegramManager)
        val total = try { source.open(DataSpec(Uri.parse(link))) } finally { source.close() }

        // "bytes=start-end", "bytes=start-" or "bytes=-suffix"
        var start = 0L
        var end = total - 1
        val spec = range?.takeIf { it.startsWith("bytes=") }?.removePrefix("bytes=")?.substringBefore(',')
        if (spec != null) {
            val a = spec.substringBefore('-').trim()
            val b = spec.substringAfter('-').trim()
            if (a.isEmpty()) { start = (total - (b.toLongOrNull() ?: 0L)).coerceAtLeast(0L) }
            else { start = a.toLongOrNull() ?: 0L; if (b.isNotEmpty()) end = minOf(b.toLong(), total - 1) }
        }
        if (start >= total) {
            out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush(); return
        }
        val length = end - start + 1
        val head = buildString {
            append(if (spec != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: $length\r\n")
            if (spec != null) append("Content-Range: bytes $start-$end/$total\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        if (method.equals("HEAD", ignoreCase = true)) { out.flush(); return }

        source.open(DataSpec.Builder().setUri(Uri.parse(link)).setPosition(start).setLength(length).build())
        var left = length
        try {
            val buffer = ByteArray(64 * 1024)
            while (left > 0) {
                val n = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, n)
                left -= n
                lastActivityMs = System.currentTimeMillis()
            }
            out.flush()
        } finally {
            source.close()
        }
    }
}
