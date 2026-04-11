package ro.infotb.modern.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
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

        val alertChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Arrival threshold alerts"
        }
        manager.createNotificationChannel(alertChannel)

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
            .setSmallIcon(context.resources.getIdentifier("ic_notification_bus", "drawable", context.packageName))
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
        val source = if (event.isTimetable) "Scheduled" else "Real-time"
        val lineColor = parseHexColor(event.lineColor)
        val contentIntent = buildStopDetailIntent(context, event, LIVE_NOTIF_ID)
        val busIcon = context.resources.getIdentifier("ic_notification_bus", "drawable", context.packageName)

        val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(busIcon)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(lineColor)
            .setColorized(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        // Android 16+ (API 36): Use ProgressStyle for rich ongoing pill
        if (Build.VERSION.SDK_INT >= 36) {
            builder
                .setContentTitle("${event.lineName} \u2192 ${event.stopName}")
                .setContentText(minuteText)
            try {
                val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
                val constructor = progressStyleClass.getConstructor()
                val progressStyle = constructor.newInstance()

                // Set progress point (percentage-like, inverted: lower minutes = more progress)
                val progress = when {
                    event.minutes <= 0 -> 100
                    event.minutes >= 30 -> 5
                    else -> ((30 - event.minutes) * 100 / 30).coerceIn(5, 100)
                }
                val setProgressPoint = progressStyleClass.getMethod("setProgressPoint", Int::class.java)
                setProgressPoint.invoke(progressStyle, progress)

                val setStyle = Notification.Builder::class.java.getMethod("setStyle", Notification.Style::class.java)
                setStyle.invoke(builder, progressStyle)
            } catch (_: Exception) {
                // ProgressStyle not available on this build, fall back to custom layout
                applyCustomLayout(builder, context, event, minuteText, source, lineColor)
            }
        } else {
            // Pre-Android 16: Use custom RemoteViews layout
            applyCustomLayout(builder, context, event, minuteText, source, lineColor)
        }

        return builder.build()
    }

    private fun applyCustomLayout(
        builder: Notification.Builder,
        context: Context,
        event: NotificationEvent,
        minuteText: String,
        source: String,
        lineColor: Int
    ) {
        val layoutId = context.resources.getIdentifier("notification_live_tracking", "layout", context.packageName)

        if (layoutId != 0) {
            val customView = RemoteViews(context.packageName, layoutId).apply {
                setTextViewText(
                    context.resources.getIdentifier("line_badge", "id", context.packageName),
                    event.lineName
                )
                setTextViewText(
                    context.resources.getIdentifier("stop_name", "id", context.packageName),
                    event.stopName
                )
                setTextViewText(
                    context.resources.getIdentifier("minutes_text", "id", context.packageName),
                    if (event.minutes <= 0) "NOW" else "${event.minutes}"
                )
                setTextViewText(
                    context.resources.getIdentifier("minutes_label", "id", context.packageName),
                    if (event.minutes <= 0) "" else "min"
                )
                setTextViewText(
                    context.resources.getIdentifier("source_text", "id", context.packageName),
                    source
                )

                // Set source dot color
                val dotId = context.resources.getIdentifier("source_dot", "id", context.packageName)
                val dotDrawable = if (event.isTimetable) {
                    context.resources.getIdentifier("notification_dot_orange", "drawable", context.packageName)
                } else {
                    context.resources.getIdentifier("notification_dot_green", "drawable", context.packageName)
                }
                setImageViewResource(dotId, dotDrawable)

                // Color the minutes text: red if <= 2, green otherwise
                val minutesId = context.resources.getIdentifier("minutes_text", "id", context.packageName)
                val minuteColor = if (event.minutes <= 2) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
                setTextColor(minutesId, minuteColor)
            }

            builder
                .setCustomContentView(customView)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setContentTitle("${event.lineName} \u2192 ${event.stopName}")
                .setContentText("$minuteText  \u2022  $source")
        } else {
            // Fallback if layout not found
            builder
                .setContentTitle("\uD83D\uDE8C ${event.lineName} \u2192 ${event.stopName}")
                .setContentText("$minuteText  \u2022  $source")
                .setStyle(
                    Notification.BigTextStyle()
                        .setBigContentTitle("\uD83D\uDE8C Line ${event.lineName}")
                        .bigText("Arriving at ${event.stopName}\n$minuteText  \u2022  $source")
                )
        }
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
