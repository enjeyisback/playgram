package app.playgram.core.database

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

abstract class KinoDatabase {
    abstract fun downloadDao(): DownloadDao
    abstract fun watchPositionDao(): WatchPositionDao
    abstract fun channelScanDao(): ChannelScanDao
    abstract fun automationDao(): AutomationDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: KinoDatabase? = null

        fun getInstance(context: Context): KinoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaygramDatabaseImpl(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

class PlaygramDatabaseImpl(private val context: Context) : KinoDatabase() {
    private val downloadDaoImpl = PlaygramDownloadDao()
    private val watchPositionDaoImpl = PlaygramWatchPositionDao(context)
    private val channelScanDaoImpl = PlaygramChannelScanDao()
    private val automationDaoImpl = PlaygramAutomationDao()
    private val deviceDaoImpl = PlaygramDeviceDao()

    override fun downloadDao(): DownloadDao = downloadDaoImpl
    override fun watchPositionDao(): WatchPositionDao = watchPositionDaoImpl
    override fun channelScanDao(): ChannelScanDao = channelScanDaoImpl
    override fun automationDao(): AutomationDao = automationDaoImpl
    override fun deviceDao(): DeviceDao = deviceDaoImpl
}

class PlaygramDownloadDao : DownloadDao {
    private val downloadsMap = MutableStateFlow<Map<String, DownloadEntity>>(emptyMap())

    override fun getAllDownloads(): Flow<List<DownloadEntity>> =
        downloadsMap.map { it.values.sortedByDescending { item -> item.addedTimeMs } }

    override fun getActiveDownloads(): Flow<List<DownloadEntity>> =
        downloadsMap.map { it.values.filter { item -> item.status in listOf("QUEUED", "DOWNLOADING") } }

    override fun getCompletedDownloads(): Flow<List<DownloadEntity>> =
        downloadsMap.map {
            it.values.filter { item -> item.status == "COMPLETED" }
                .sortedByDescending { item -> item.completionTimeMs ?: 0L }
        }

    override suspend fun getDownloadById(id: String): DownloadEntity? =
        downloadsMap.value[id]

    override suspend fun insertOrUpdate(download: DownloadEntity) {
        val current = downloadsMap.value.toMutableMap()
        current[download.id] = download
        downloadsMap.value = current
    }

    override suspend fun deleteById(id: String) {
        val current = downloadsMap.value.toMutableMap()
        current.remove(id)
        downloadsMap.value = current
    }

    override suspend fun updateStatus(id: String, status: String) {
        val current = downloadsMap.value.toMutableMap()
        val item = current[id] ?: return
        current[id] = item.copy(status = status, speedBytesPerSec = 0)
        downloadsMap.value = current
    }

    override suspend fun updateProgress(id: String, bytes: Long, speed: Long) {
        val current = downloadsMap.value.toMutableMap()
        val item = current[id] ?: return
        current[id] = item.copy(downloadedBytes = bytes, speedBytesPerSec = speed)
        downloadsMap.value = current
    }
}

class PlaygramWatchPositionDao(context: Context) : WatchPositionDao {
    private val prefs = context.getSharedPreferences("playgram.watch", Context.MODE_PRIVATE)
    private val watchMap = MutableStateFlow(load())

    private fun load(): Map<String, WatchPositionEntity> = try {
        val arr = org.json.JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it) }.associate { o ->
            o.getString("id") to WatchPositionEntity(
                o.getString("id"), o.getString("title"), o.getLong("pos"), o.getLong("dur"),
                o.getLong("at"), o.getBoolean("done"), o.optString("url", o.getString("id"))
            )
        }
    } catch (_: Exception) { emptyMap() }

    private fun persist(map: Map<String, WatchPositionEntity>) {
        val arr = org.json.JSONArray()
        map.values.sortedByDescending { it.lastWatchedAt }.take(50).forEach {
            arr.put(org.json.JSONObject().put("id", it.mediaId).put("title", it.title).put("pos", it.positionMs)
                .put("dur", it.durationMs).put("at", it.lastWatchedAt).put("done", it.completed).put("url", it.url))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    override fun getAllWatchHistory(): Flow<List<WatchPositionEntity>> =
        watchMap.map { it.values.sortedByDescending { item -> item.lastWatchedAt } }

    override suspend fun getWatchPosition(mediaId: String): WatchPositionEntity? =
        watchMap.value[mediaId]

    override suspend fun saveWatchPosition(item: WatchPositionEntity) {
        val current = watchMap.value.toMutableMap()
        current[item.mediaId] = item
        watchMap.value = current
        persist(current)
    }

    override suspend fun clearHistory(mediaId: String) {
        val current = watchMap.value.toMutableMap()
        current.remove(mediaId)
        watchMap.value = current
        persist(current)
    }
}

class PlaygramChannelScanDao : ChannelScanDao {
    private val scansMap = MutableStateFlow<Map<Long, ChannelScanEntity>>(emptyMap())

    override fun getAllScannedChannels(): Flow<List<ChannelScanEntity>> =
        scansMap.map { it.values.sortedByDescending { item -> item.lastScannedAt } }

    override suspend fun saveScan(scan: ChannelScanEntity) {
        val current = scansMap.value.toMutableMap()
        current[scan.channelId] = scan
        scansMap.value = current
    }
}

class PlaygramAutomationDao : AutomationDao {
    private val rulesMap = MutableStateFlow<Map<String, AutomationRuleEntity>>(emptyMap())

    override fun getAllRules(): Flow<List<AutomationRuleEntity>> =
        rulesMap.map { it.values.sortedBy { item -> item.name } }

    override suspend fun saveRule(rule: AutomationRuleEntity) {
        val current = rulesMap.value.toMutableMap()
        current[rule.id] = rule
        rulesMap.value = current
    }

    override suspend fun deleteRule(id: String) {
        val current = rulesMap.value.toMutableMap()
        current.remove(id)
        rulesMap.value = current
    }
}

class PlaygramDeviceDao : DeviceDao {
    private val devicesMap = MutableStateFlow<Map<String, DeviceNodeEntity>>(emptyMap())

    override fun getAllDevices(): Flow<List<DeviceNodeEntity>> =
        devicesMap.map { it.values.sortedBy { item -> item.name } }

    override suspend fun saveDevice(device: DeviceNodeEntity) {
        val current = devicesMap.value.toMutableMap()
        current[device.id] = device
        devicesMap.value = current
    }

    override suspend fun deleteDevice(id: String) {
        val current = devicesMap.value.toMutableMap()
        current.remove(id)
        devicesMap.value = current
    }
}
