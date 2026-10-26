package app.playgram

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow

/** What another app asked Playgram to open (share sheet, t.me link, video file). */
sealed class IncomingRequest {
    /** A Telegram link to a post or channel. */
    data class TelegramLink(val url: String) : IncomingRequest()
    /** A video Android can read directly (content:// or file://, or a plain web address). */
    data class VideoUri(val uri: String, val title: String) : IncomingRequest()
}

object Incoming {
    /** Set by MainActivity, consumed by the navigation once the user is signed in. */
    val pending = MutableStateFlow<IncomingRequest?>(null)

    private val linkPattern = Regex("""(?:https?://)?(?:t|telegram)\.me/[^\s<>"']+""", RegexOption.IGNORE_CASE)

    fun from(context: Context, intent: Intent?): IncomingRequest? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> IncomingRequest.VideoUri(stream.toString(), displayName(context, stream))
                    text != null -> telegramLink(text)?.let { IncomingRequest.TelegramLink(it) }
                    else -> null
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return null
                val s = data.toString()
                when {
                    data.scheme == "tg" -> tgToWeb(data)?.let { IncomingRequest.TelegramLink(it) }
                    data.scheme == "playgram" -> null
                    (data.scheme == "http" || data.scheme == "https") && data.host?.let { it == "t.me" || it == "telegram.me" } == true ->
                        IncomingRequest.TelegramLink(s)
                    else -> IncomingRequest.VideoUri(s, displayName(context, data))
                }
            }
            else -> null
        }
    }

    private fun telegramLink(text: String): String? = linkPattern.find(text)?.value?.let {
        if (it.startsWith("http", ignoreCase = true)) it else "https://$it"
    }

    /** tg://resolve?domain=x&post=1 and tg://privatepost?channel=1&post=2 as web links. */
    private fun tgToWeb(uri: Uri): String? {
        val post = uri.getQueryParameter("post")
        return when (uri.host) {
            "resolve" -> uri.getQueryParameter("domain")?.let { d -> if (post != null) "https://t.me/$d/$post" else "https://t.me/$d" }
            "privatepost" -> {
                val ch = uri.getQueryParameter("channel")
                if (ch != null && post != null) "https://t.me/c/$ch/$post" else null
            }
            else -> null
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Shared video"
    }
}
