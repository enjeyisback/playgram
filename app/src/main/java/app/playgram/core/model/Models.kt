package app.playgram.core.model

import java.io.Serializable

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

data class DownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val status: DownloadStatus,
    val mimeType: String,
    val destinationPath: String,
    val addedTimeMs: Long,
    val completionTimeMs: Long? = null,
    val errorMessage: String? = null
) : Serializable {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() {
            val kb = speedBytesPerSec / 1024.0
            return if (kb > 1024) String.format("%.1f MB/s", kb / 1024.0) else String.format("%.0f KB/s", kb)
        }

    val formattedSize: String
        get() {
            val mb = totalBytes / (1024.0 * 1024.0)
            return if (mb > 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }
}

data class MediaItem(
    val id: String,
    val title: String,
    val durationSeconds: Long,
    val sizeBytes: Long,
    val streamUrl: String,
    val thumbnailUrl: String? = null,
    val mimeType: String = "video/mp4",
    val quality: String = "1080p",
    val channelTitle: String? = null,
    val resolution: String = "1920x1080",
    val audioTracks: List<String> = listOf("Default Audio", "Secondary Stream"),
    val subtitleTracks: List<String> = listOf("English", "Spanish", "Russian")
) : Serializable {
    val formattedDuration: String
        get() {
            val m = durationSeconds / 60
            val s = durationSeconds % 60
            return String.format("%02d:%02d", m, s)
        }

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb > 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }
}

data class TelegramChat(
    val id: Long,
    val title: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val unreadCount: Int = 0,
    val isChannel: Boolean = true,
    val isGroup: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageTime: String = "",
    val mediaCount: Int = 0,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false
)

data class TelegramMessage(
    val id: Long,
    val chatId: Long,
    val text: String,
    val dateMs: Long,
    val media: MediaItem? = null,
    val photoFileId: Int? = null
)

data class WatchProgress(
    val mediaId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val completed: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class AutomationRule(
    val id: String,
    val name: String,
    val sourceChatId: Long,
    val targetDirectory: String,
    val regexFilter: String,
    val minSizeBytes: Long = 0,
    val maxSizeBytes: Long = Long.MAX_VALUE,
    val enabled: Boolean = true,
    val autoDownload: Boolean = true,
    val totalMatched: Int = 0
)

enum class DeviceType {
    SMB, SFTP, WEBDAV, CAST
}

data class DeviceNode(
    val id: String,
    val name: String,
    val type: DeviceType,
    val host: String,
    val port: Int,
    val sharePath: String = "/",
    val username: String = "",
    val isConnected: Boolean = false
)

data class LinkProbeResult(
    val url: String,
    val statusCode: Int,
    val contentType: String,
    val contentLength: Long,
    val isDirectVideo: Boolean,
    val isHlsStream: Boolean,
    val headers: Map<String, String>
)
