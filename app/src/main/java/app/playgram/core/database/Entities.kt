package app.playgram.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.playgram.core.model.DownloadItem
import app.playgram.core.model.DownloadStatus
import app.playgram.core.model.WatchProgress

@Entity(tableName = "download_items")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val status: String,
    val mimeType: String,
    val destinationPath: String,
    val addedTimeMs: Long,
    val completionTimeMs: Long? = null,
    val errorMessage: String? = null
) {
    fun toModel(): DownloadItem {
        val st = try {
            DownloadStatus.valueOf(status)
        } catch (_: Exception) {
            DownloadStatus.QUEUED
        }
        return DownloadItem(
            id = id,
            title = title,
            url = url,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            speedBytesPerSec = speedBytesPerSec,
            status = st,
            mimeType = mimeType,
            destinationPath = destinationPath,
            addedTimeMs = addedTimeMs,
            completionTimeMs = completionTimeMs,
            errorMessage = errorMessage
        )
    }

    companion object {
        fun fromModel(model: DownloadItem): DownloadEntity {
            return DownloadEntity(
                id = model.id,
                title = model.title,
                url = model.url,
                totalBytes = model.totalBytes,
                downloadedBytes = model.downloadedBytes,
                speedBytesPerSec = model.speedBytesPerSec,
                status = model.status.name,
                mimeType = model.mimeType,
                destinationPath = model.destinationPath,
                addedTimeMs = model.addedTimeMs,
                completionTimeMs = model.completionTimeMs,
                errorMessage = model.errorMessage
            )
        }
    }
}

@Entity(tableName = "watch_positions")
data class WatchPositionEntity(
    @PrimaryKey val mediaId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val completed: Boolean,
    /** Link the player opens; [mediaId] is the stable key (Telegram file ids change between sessions). */
    val url: String = mediaId
) {
    fun toModel(): WatchProgress = WatchProgress(
        mediaId = mediaId,
        title = title,
        positionMs = positionMs,
        durationMs = durationMs,
        lastWatchedAt = lastWatchedAt,
        completed = completed
    )
}

@Entity(tableName = "channel_scans")
data class ChannelScanEntity(
    @PrimaryKey val channelId: Long,
    val channelTitle: String,
    val lastMessageId: Long,
    val lastScannedAt: Long,
    val totalMediaFound: Int,
    val syncEnabled: Boolean
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceChatId: Long,
    val targetDirectory: String,
    val regexFilter: String,
    val minSizeBytes: Long,
    val maxSizeBytes: Long,
    val enabled: Boolean,
    val autoDownload: Boolean,
    val totalMatched: Int
)

@Entity(tableName = "device_nodes")
data class DeviceNodeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val sharePath: String,
    val username: String
)
