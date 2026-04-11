package ro.infotb.modern.notification

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class LiveTrackingService : Service() {

    companion object {
        private const val ACTION_START = "ro.infotb.modern.START_LIVE_TRACKING"
        private const val ACTION_UPDATE = "ro.infotb.modern.UPDATE_LIVE_TRACKING"
        private const val ACTION_STOP = "ro.infotb.modern.STOP_LIVE_TRACKING"

        fun start(context: Context, notification: Notification) {
            val intent = Intent(context, LiveTrackingService::class.java).apply {
                action = ACTION_START
            }
            // Store notification to be picked up in onStartCommand
            pendingNotification = notification
            context.startForegroundService(intent)
        }

        fun update(context: Context, notification: Notification) {
            val intent = Intent(context, LiveTrackingService::class.java).apply {
                action = ACTION_UPDATE
            }
            pendingNotification = notification
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        @Volatile
        private var pendingNotification: Notification? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = pendingNotification
                if (notification != null) {
                    startForeground(AndroidNotifier.LIVE_NOTIF_ID, notification)
                    pendingNotification = null
                }
            }
            ACTION_UPDATE -> {
                val notification = pendingNotification
                if (notification != null) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(AndroidNotifier.LIVE_NOTIF_ID, notification)
                    pendingNotification = null
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}
