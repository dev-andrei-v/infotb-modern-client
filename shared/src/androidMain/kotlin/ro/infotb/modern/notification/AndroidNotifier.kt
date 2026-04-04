package ro.infotb.modern.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import ro.infotb.modern.AndroidContext

class AndroidNotifier : PlatformNotifier {

    companion object {
        private const val CHANNEL_ID = "stb_arrivals"
        private const val CHANNEL_NAME = "Arrival Alerts"
        private const val LIVE_CHANNEL_ID = "stb_live_tracking"
        private const val LIVE_CHANNEL_NAME = "Live Arrival Tracking"
        private const val LIVE_NOTIF_ID = 9001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val context = AndroidContext.appContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Alert channel (high importance, heads-up)
        val alertChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Arrival threshold alerts"
        }
        manager.createNotificationChannel(alertChannel)

        // Live tracking channel (low importance, ongoing pill)
        val liveChannel = NotificationChannel(
            LIVE_CHANNEL_ID,
            LIVE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live arrival countdown"
            setShowBadge(false)
        }
        manager.createNotificationChannel(liveChannel)
    }

    override fun showNotification(event: NotificationEvent) {
        val context = AndroidContext.appContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = if (event.minutes <= 0) {
            "Line ${event.lineName} — Arrived!"
        } else {
            "Line ${event.lineName} — ${event.minutes} min"
        }
        val source = if (event.isTimetable) "scheduled" else "GPS"
        val body = "Arrives at ${event.stopName} ($source)"

        val notifId = "${event.stopId}-${event.lineName}".hashCode()
        val contentIntent = buildStopDetailIntent(context, event, notifId)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setColor(parseHexColor(event.lineColor))
            .setContentIntent(contentIntent)
            .build()

        manager.notify(notifId, notification)
    }

    override fun startLiveTracking(event: NotificationEvent) {
        updateLiveTracking(event)
    }

    override fun updateLiveTracking(event: NotificationEvent) {
        val context = AndroidContext.appContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = buildLiveNotification(context, event)
        manager.notify(LIVE_NOTIF_ID, notification)
    }

    override fun stopLiveTracking() {
        val context = AndroidContext.appContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(LIVE_NOTIF_ID)
    }

    private fun buildLiveNotification(context: Context, event: NotificationEvent): Notification {
        val minuteText = when {
            event.minutes <= 0 -> "NOW"
            event.minutes == 1 -> "1 min"
            else -> "${event.minutes} min"
        }
        val source = if (event.isTimetable) "Scheduled" else "GPS"
        val contentIntent = buildStopDetailIntent(context, event, LIVE_NOTIF_ID)

        return Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("\uD83D\uDE8C ${event.lineName} → ${event.stopName}")
            .setContentText("$minuteText  •  $source")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(parseHexColor(event.lineColor))
            .setColorized(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.BigTextStyle()
                .setBigContentTitle("\uD83D\uDE8C Line ${event.lineName}")
                .bigText("Arriving at ${event.stopName}\n$minuteText  •  $source"))
            .build()
    }

    private fun buildStopDetailIntent(context: Context, event: NotificationEvent, requestCode: Int): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply {
            putExtra("navigate_stop_id", event.stopId)
            putExtra("navigate_line_id", event.lineId)
            putExtra("navigate_direction", event.direction)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun parseHexColor(hex: String): Int {
        return try {
            Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        } catch (_: Exception) {
            Color.parseColor("#1976D2")
        }
    }
}
