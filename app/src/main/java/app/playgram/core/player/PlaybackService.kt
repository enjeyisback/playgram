package app.playgram.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi

/**
 * Keeps Playgram running while another player streams from [StreamServer]; without it Android
 * freezes the app in the background and the other player stalls. Stops itself once idle.
 */
@OptIn(UnstableApi::class)
class PlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val idleCheck = object : Runnable {
        override fun run() {
            val idleFor = System.currentTimeMillis() - StreamServer.lastActivityMs
            if (StreamServer.activeConnections.get() == 0 && idleFor > IDLE_STOP_MS) stopSelf()
            else handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification("Streaming to another player", "Playgram is serving the video. It stops by itself when you're done.")
        ServiceCompat.startForeground(
            this, PLAYBACK_NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        handler.postDelayed(idleCheck, 60_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        super.onDestroy()
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Playgram Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while another player streams a video through Playgram"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playgram_playback_channel"
        const val PLAYBACK_NOTIFICATION_ID = 2001
        private const val IDLE_STOP_MS = 10 * 60_000L

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (_: Exception) {
                // Not allowed from the background; the stream still works while Playgram stays awake.
            }
        }
    }
}
