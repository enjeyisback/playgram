package app.playgram.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_items ORDER BY addedTimeMs DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE status IN ('QUEUED', 'DOWNLOADING')")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY completionTimeMs DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE download_items SET status = :status, speedBytesPerSec = 0 WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE download_items SET downloadedBytes = :bytes, speedBytesPerSec = :speed WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, speed: Long)
}

@Dao
interface WatchPositionDao {
    @Query("SELECT * FROM watch_positions ORDER BY lastWatchedAt DESC")
    fun getAllWatchHistory(): Flow<List<WatchPositionEntity>>

    @Query("SELECT * FROM watch_positions WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getWatchPosition(mediaId: String): WatchPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWatchPosition(item: WatchPositionEntity)

    @Query("DELETE FROM watch_positions WHERE mediaId = :mediaId")
    suspend fun clearHistory(mediaId: String)
}

@Dao
interface ChannelScanDao {
    @Query("SELECT * FROM channel_scans ORDER BY lastScannedAt DESC")
    fun getAllScannedChannels(): Flow<List<ChannelScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveScan(scan: ChannelScanEntity)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_rules ORDER BY name ASC")
    fun getAllRules(): Flow<List<AutomationRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: AutomationRuleEntity)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteRule(id: String)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device_nodes ORDER BY name ASC")
    fun getAllDevices(): Flow<List<DeviceNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDevice(device: DeviceNodeEntity)

    @Query("DELETE FROM device_nodes WHERE id = :id")
    suspend fun deleteDevice(id: String)
}
