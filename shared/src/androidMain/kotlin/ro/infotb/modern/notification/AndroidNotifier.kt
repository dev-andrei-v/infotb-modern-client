package ro.infotb.modern.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import ro.infotb.modern.AndroidContext

class AndroidNotifier : PlatformNotifier {

    companion object {
        private const val CHANNEL_ID = "stb_arrivals"
        private const val CHANNEL_NAME = "Arrival Alerts"
        private const val LIVE_CHANNEL_ID = "stb_live_tracking"
        private const val LIVE_CHANNEL_NAME = "Live Arrival Tracking"
        const val LIVE_NOTIF_ID = 9001
    }

    private val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    private var serviceStarted = false

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
            NotificationManager.IMPORTANCE_DEFAULT
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
            "Line ${event.lineName} \u2014 Arrived!"
        } else {
            "Line ${event.lineName} \u2014 ${event.minutes} min"
        }
        val source = if (event.isTimetable) "scheduled" else "GPS"
        val body = "Arrives at ${event.stopName} ($source)"

        val notifId = "${event.stopId}-${event.lineName}".hashCode()
        val contentIntent = buildStopDetailIntent(context, event, notifId)
        val busIcon = context.resources.getIdentifier("ic_notification_bus", "drawable", context.packageName)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (busIcon != 0) busIcon else android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setColor(parseHexColor(event.lineColor))
            .setContentIntent(contentIntent)
            .build()

        manager.notify(notifId, notification)
    }

    override fun startLiveTracking(event: NotificationEvent) {
        val context = AndroidContext.appContext
        val notification = buildLiveNotification(context, event)

        if (isSamsung) {
            LiveTrackingService.start(context, notification)
            serviceStarted = true
        } else {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(LIVE_NOTIF_ID, notification)
        }
    }

    override fun updateLiveTracking(event: NotificationEvent) {
        val context = AndroidContext.appContext
        val notification = buildLiveNotification(context, event)

        if (isSamsung && serviceStarted) {
            LiveTrackingService.update(context, notification)
        } else if (isSamsung) {
            LiveTrackingService.start(context, notification)
            serviceStarted = true
        } else {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(LIVE_NOTIF_ID, notification)
        }
    }

    override fun stopLiveTracking() {
        val context = AndroidContext.appContext
        if (isSamsung && serviceStarted) {
            LiveTrackingService.stop(context)
            serviceStarted = false
        } else {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(LIVE_NOTIF_ID)
        }
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
        val sourceIconRes = if (event.isTimetable) "ic_notification_scheduled" else "ic_notification_realtime"
        val sourceIcon = context.resources.getIdentifier(sourceIconRes, "drawable", context.packageName)
        val smallIcon = if (sourceIcon != 0) sourceIcon else android.R.drawable.ic_menu_mylocation
        val iconBgColor = if (event.isTimetable) Color.parseColor("#FF9800") else Color.parseColor("#4CAF50")

        if (isSamsung) {
            return buildSamsungNotification(context, event, minuteText, source, iconBgColor, contentIntent, smallIcon)
        }

        // Standard Android notification with custom layout
        val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(iconBgColor)
            .setColorized(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        applyCustomLayout(builder, context, event, minuteText, source, lineColor)

        // Android 16+ ProgressStyle
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
                val progressStyle = progressStyleClass.getConstructor().newInstance()
                val progress = when {
                    event.minutes <= 0 -> 100
                    event.minutes >= 30 -> 5
                    else -> ((30 - event.minutes) * 100 / 30).coerceIn(5, 100)
                }
                progressStyleClass.getMethod("setProgressPoint", Int::class.java)
                    .invoke(progressStyle, progress)
                Notification.Builder::class.java.getMethod("setStyle", Notification.Style::class.java)
                    .invoke(builder, progressStyle)
            } catch (_: Exception) { }
        }

        return builder.build()
    }

    private fun buildSamsungNotification(
        context: Context,
        event: NotificationEvent,
        minuteText: String,
        source: String,
        lineColor: Int,
        contentIntent: PendingIntent,
        smallIcon: Int
    ): Notification {
        val builder = NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("${event.lineName} \u2192 ${event.stopName}")
            .setContentText("$minuteText  \u2022  $source")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(lineColor)
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setTicker(minuteText)
            .setShortCriticalText(minuteText)
            .setRequestPromotedOngoing(true)

        // Samsung Live Notification + Now Bar extras
        val samsungExtras = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)

            // Live Notification standard style
            putString("android.ongoingActivityNoti.primaryInfo", minuteText)
            putString("android.ongoingActivityNoti.secondaryInfo", "${event.lineName} \u2192 ${event.stopName}")

            // Chip (status bar pill)
            putInt("android.ongoingActivityNoti.chipBgColor", lineColor)
            putString("android.ongoingActivityNoti.chipExpandedText", "${event.lineName} \u2022 $minuteText")

            // Now Bar (lock screen)
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", minuteText)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", "${event.lineName} \u2192 ${event.stopName}")
        }
        builder.addExtras(samsungExtras)

        val notification = builder.build()

        // semFlags hack: promote to Now Bar
        try {
            val semFlagsField = Notification::class.java.getDeclaredField("semFlags")
            semFlagsField.isAccessible = true
            val currentFlags = semFlagsField.getInt(notification)
            semFlagsField.setInt(notification, currentFlags or 0x8000)
        } catch (_: Exception) { }

        return notification
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

                val sourceIconId = context.resources.getIdentifier("source_icon", "id", context.packageName)
                val sourceIconDrawable = if (event.isTimetable) {
                    context.resources.getIdentifier("ic_notification_scheduled", "drawable", context.packageName)
                } else {
                    context.resources.getIdentifier("ic_notification_realtime", "drawable", context.packageName)
                }
                setImageViewResource(sourceIconId, sourceIconDrawable)

                val minutesId = context.resources.getIdentifier("minutes_text", "id", context.packageName)
                val minuteColor = if (event.minutes <= 2) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
                setTextColor(minutesId, minuteColor)

                val sourceTextId = context.resources.getIdentifier("source_text", "id", context.packageName)
                val sourceColor = if (event.isTimetable) Color.parseColor("#FF9800") else Color.parseColor("#4CAF50")
                setTextColor(sourceTextId, sourceColor)
            }

            builder
                .setCustomContentView(customView)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setContentTitle("${event.lineName} \u2192 ${event.stopName}")
                .setContentText("$minuteText  \u2022  $source")
        } else {
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
