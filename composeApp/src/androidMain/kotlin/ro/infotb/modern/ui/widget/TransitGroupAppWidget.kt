package ro.infotb.modern.ui.widget

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import org.json.JSONObject
import ro.infotb.modern.MainActivity
import ro.infotb.modern.R

class TransitGroupAppWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (intent.action == ACTION_SCHEDULE_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, TransitGroupAppWidget::class.java)).forEach { update(context, manager, it) }
            scheduleNextBoundary(context)
            return
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when (intent.action) {
            ACTION_OPEN_GROUP -> {
                val group = resolveGroupName(context, widgetId)
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_GROUP, group)
                })
            }
            ACTION_REFRESH -> {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_REFRESH_WIDGET, true)
                })
            }
        }
    }

    companion object {
        const val PREFERENCES = "transit_group_home_widget"
        const val SNAPSHOT_KEY = "dashboard_snapshot"
        const val SELECTED_STOP_KEY = "dashboard_selected_stop"
        const val DASHBOARD_SELECTION_ID = "__dashboard_selection__"
        const val NEAREST_STOP_ID = "__nearest_stop__"
        const val ROTATING_GROUP_ID = "__active_group__"
        const val EXTRA_OPEN_GROUP = "open_dashboard_group"
        const val ACTION_SCHEDULE_UPDATE = "ro.infotb.modern.widget.SCHEDULE_UPDATE"
        const val ACTION_OPEN_GROUP = "ro.infotb.modern.widget.OPEN_GROUP"
        const val ACTION_REFRESH = "ro.infotb.modern.widget.REFRESH"
        const val EXTRA_REFRESH_WIDGET = "refresh_dashboard_from_widget"

        fun groupKey(id: Int) = "group_$id"
        fun stopKey(id: Int) = "stop_$id"

        private fun resolveGroupName(context: Context, widgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val selected = prefs.getString(groupKey(widgetId), "").orEmpty()
            if (selected == DASHBOARD_SELECTION_ID || selected == NEAREST_STOP_ID) {
                val snapshot = runCatching { JSONObject(prefs.getString(SNAPSHOT_KEY, "{}") ?: "{}") }.getOrNull()
                val stopId = if (selected == NEAREST_STOP_ID) snapshot?.optString("nearestStopId")?.takeIf { it != "null" && it.isNotBlank() }
                    else prefs.getString(SELECTED_STOP_KEY, null)
                if (stopId == null) return ""
                val groups = runCatching { JSONObject(prefs.getString(SNAPSHOT_KEY, "{}") ?: "{}").optJSONArray("groups") }.getOrNull()
                return (0 until (groups?.length() ?: 0)).mapNotNull { groups?.optJSONObject(it) }
                    .firstOrNull { group ->
                        val stops = group.optJSONArray("stops")
                        (0 until (stops?.length() ?: 0)).any { stops?.optJSONObject(it)?.optString("id") == stopId }
                    }?.optString("name").orEmpty()
            }
            if (selected != ROTATING_GROUP_ID) return selected
            val groups = runCatching {
                JSONObject(prefs.getString(SNAPSHOT_KEY, "{}") ?: "{}").optJSONArray("groups")
            }.getOrNull()
            val populated = (0 until (groups?.length() ?: 0)).mapNotNull { groups?.optJSONObject(it) }
                .filter { (it.optJSONArray("stops")?.length() ?: 0) > 0 }
            if (populated.isEmpty()) return ""
            return activeGroup(populated, System.currentTimeMillis())?.optString("name") ?: populated.first().optString("name")
        }

        private fun activeGroup(groups: List<JSONObject>, atMillis: Long): JSONObject? {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = atMillis }
            val minute = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
            fun isActive(from: Int, to: Int) = if (from <= to) minute in from..to else minute >= from || minute <= to
            return groups.firstOrNull { group ->
                val from = group.optInt("activeFrom", -1)
                val to = group.optInt("activeTo", -1)
                from >= 0 && to >= 0 && isActive(from, to)
            } ?: groups.firstOrNull { it.optInt("activeFrom", -1) < 0 || it.optInt("activeTo", -1) < 0 }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val storedGroup = prefs.getString(groupKey(widgetId), "").orEmpty()
            val groupName = resolveGroupName(context, widgetId)
            val snapshot = runCatching { JSONObject(prefs.getString(SNAPSHOT_KEY, "{}") ?: "{}") }.getOrNull()
            val groups = snapshot?.optJSONArray("groups")
            val selectedStopId = if (storedGroup == NEAREST_STOP_ID) snapshot?.optString("nearestStopId")?.takeIf { it != "null" && it.isNotBlank() }
                else prefs.getString(SELECTED_STOP_KEY, null)
            val widgetStopId = prefs.getString(stopKey(widgetId), null)
            val selectedStop = if ((storedGroup == DASHBOARD_SELECTION_ID || storedGroup == NEAREST_STOP_ID) && selectedStopId != null) {
                (0 until (groups?.length() ?: 0)).asSequence()
                    .mapNotNull { groups?.optJSONObject(it)?.optJSONArray("stops") }
                    .flatMap { stops -> (0 until stops.length()).asSequence().mapNotNull { stops.optJSONObject(it) } }
                    .firstOrNull { it.optString("id") == selectedStopId }
            } else null
            val group = (0 until (groups?.length() ?: 0))
                .mapNotNull { groups?.optJSONObject(it) }
                .firstOrNull { it.optString("name") == groupName }
            val groupStops = group?.optJSONArray("stops")
            val stop = when {
                storedGroup == NEAREST_STOP_ID -> selectedStop
                selectedStop != null -> selectedStop
                widgetStopId != null -> (0 until (groupStops?.length() ?: 0)).asSequence().mapNotNull { groupStops?.optJSONObject(it) }
                    .firstOrNull { it.optString("id") == widgetStopId }
                else -> group?.optLong("selectedStopId")?.takeIf { it != 0L }?.let { target ->
                    (0 until (groupStops?.length() ?: 0)).asSequence().mapNotNull { groupStops?.optJSONObject(it) }
                        .firstOrNull { it.optString("id").toLongOrNull() == target }
                } ?: (0 until (groupStops?.length() ?: 0)).asSequence().mapNotNull { groupStops?.optJSONObject(it) }.firstOrNull()
            }
            val views = RemoteViews(context.packageName, R.layout.widget_transit_group).apply {
                val selectedGroupName = if (storedGroup == DASHBOARD_SELECTION_ID) {
                    val stopId = selectedStop?.optString("id")
                    (0 until (groups?.length() ?: 0)).mapNotNull { groups?.optJSONObject(it) }.firstOrNull { candidate ->
                        val candidateStops = candidate.optJSONArray("stops")
                        (0 until (candidateStops?.length() ?: 0)).any { candidateStops?.optJSONObject(it)?.optString("id") == stopId }
                    }?.optString("name").orEmpty()
                } else groupName
                val title = when {
                    storedGroup == NEAREST_STOP_ID -> context.getString(R.string.widget_nearest_stop)
                    storedGroup == DASHBOARD_SELECTION_ID && selectedGroupName.isNotBlank() ->
                        context.getString(R.string.widget_dashboard_selection) + " · " + selectedGroupName
                    storedGroup == DASHBOARD_SELECTION_ID -> context.getString(R.string.widget_dashboard_selection)
                    storedGroup == ROTATING_GROUP_ID && groupName.isNotBlank() -> context.getString(R.string.widget_rotating_group) + " · " + groupName
                    else -> groupName.ifBlank { context.getString(R.string.widget_choose_group) }
                }
                setTextViewText(R.id.widget_group_name, title)
                setTextViewText(R.id.widget_stop_name, stop?.optString("name") ?: context.getString(R.string.widget_choose_stop))
                val arrivals = stop?.optJSONArray("arrivals")
                removeAllViews(R.id.widget_line_rows)
                val visibleCount = minOf(arrivals?.length() ?: 0, 2)
                setViewVisibility(R.id.widget_line_rows, if (visibleCount > 0) android.view.View.VISIBLE else android.view.View.GONE)
                setViewVisibility(R.id.widget_empty, if (visibleCount == 0) android.view.View.VISIBLE else android.view.View.GONE)
                if (visibleCount == 0) {
                    setTextViewText(R.id.widget_empty, if (storedGroup == NEAREST_STOP_ID && stop == null) context.getString(R.string.widget_nearest_unavailable) else if (stop == null) context.getString(R.string.widget_choose_stop) else context.getString(R.string.widget_open_dashboard))
                }
                var accent = Color.parseColor("#C92335")
                repeat(visibleCount) { rowIndex ->
                    val item = arrivals?.optJSONObject(rowIndex) ?: JSONObject()
                    val lineColor = parseColor(item.optString("color")) ?: Color.parseColor("#1E88E5")
                    if (rowIndex == 0) accent = lineColor
                    val row = RemoteViews(context.packageName, R.layout.widget_transit_line_row)
                    row.setTextViewText(R.id.widget_line_badge, item.optString("line"))
                    row.setTextViewText(R.id.widget_line_direction, item.optString("direction"))
                    val seconds = item.optLong("seconds", -1L)
                    val isTimetable = item.optBoolean("isTimetable", false)
                    row.setTextViewText(R.id.widget_arrival_badge, when {
                        seconds < 0 -> context.getString(R.string.widget_no_arrival)
                        seconds < 60 -> context.getString(R.string.widget_due)
                        else -> context.getString(R.string.widget_minutes, ((seconds + 30) / 60).toInt())
                    })
                    val arrivalColor = if (isTimetable) context.getColor(R.color.widget_arrival_scheduled) else context.getColor(R.color.widget_arrival_live)
                    row.setTextColor(R.id.widget_arrival_badge, arrivalColor)
                    if (Build.VERSION.SDK_INT >= 31) {
                        row.setColorStateList(R.id.widget_line_badge, "setBackgroundTintList", ColorStateList.valueOf(lineColor))
                        row.setColorStateList(R.id.widget_arrival_badge, "setBackgroundTintList", ColorStateList.valueOf(withAlpha(arrivalColor, 0x28)))
                    } else {
                        row.setInt(R.id.widget_line_badge, "setBackgroundColor", lineColor)
                    }
                    row.setTextColor(R.id.widget_line_badge, if (isLight(lineColor)) Color.BLACK else Color.WHITE)
                    addView(R.id.widget_line_rows, row)
                }
                setInt(R.id.widget_accent, "setBackgroundColor", accent)
                val stopIdLong = stop?.optString("id")?.toLongOrNull()
                setOnClickPendingIntent(R.id.widget_refresh, activityRefresh(context, widgetId))
                setOnClickPendingIntent(R.id.widget_settings, configure(context, widgetId))
                setOnClickPendingIntent(R.id.widget_open, broadcast(context, widgetId, ACTION_OPEN_GROUP, 2))
                setOnClickPendingIntent(R.id.widget_root, activity(context, selectedGroupName, widgetId, stopIdLong))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun parseColor(value: String): Int? = runCatching { Color.parseColor(value) }.getOrNull()
        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
        private fun isLight(color: Int): Boolean {
            val r = Color.red(color) / 255.0
            val g = Color.green(color) / 255.0
            val b = Color.blue(color) / 255.0
            return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 0.6
        }

        fun scheduleNextBoundary(context: Context) {
            val snapshot = runCatching { JSONObject(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(SNAPSHOT_KEY, "{}") ?: "{}") }.getOrNull()
            val groups = snapshot?.optJSONArray("groups")
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val minuteNow = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val boundaries = (0 until (groups?.length() ?: 0)).flatMap { i ->
                val group = groups?.optJSONObject(i) ?: return@flatMap emptyList()
                listOf(group.optInt("activeFrom", -1), group.optInt("activeTo", -1)).filter { it in 0..1439 }
            }
            val delta = boundaries.map { boundary -> ((boundary - minuteNow + 1440) % 1440).takeIf { it > 0 } ?: 1440 }.minOrNull() ?: return
            val whenMillis = now + delta * 60_000L
            val intent = Intent(context, TransitGroupAppWidget::class.java).apply { action = ACTION_SCHEDULE_UPDATE }
            val pending = PendingIntent.getBroadcast(context, 79021, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
            else alarm.set(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }

        private fun configure(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, TransitGroupWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(context, widgetId * 10 + 4, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun broadcast(context: Context, widgetId: Int, action: String, salt: Int): PendingIntent {
            val intent = Intent(context, TransitGroupAppWidget::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context, widgetId * 10 + salt, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun activity(context: Context, groupName: String, widgetId: Int, stopId: Long? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (stopId != null && stopId >= 0) {
                    putExtra("navigate_stop_id", stopId)
                } else {
                    putExtra(EXTRA_OPEN_GROUP, groupName)
                }
            }
            return PendingIntent.getActivity(
                context, widgetId * 10 + 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun activityRefresh(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_REFRESH_WIDGET, true)
            }
            return PendingIntent.getActivity(
                context, widgetId * 10 + 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

class TransitGroupWidgetConfigureActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val snapshot = runCatching {
            JSONObject(getSharedPreferences(TransitGroupAppWidget.PREFERENCES, MODE_PRIVATE)
                .getString(TransitGroupAppWidget.SNAPSHOT_KEY, "{}") ?: "{}")
        }.getOrNull()
        val groups = snapshot?.optJSONArray("groups")
        val storedNames = (0 until (groups?.length() ?: 0))
            .mapNotNull { groups?.optJSONObject(it)?.optString("name") }
            .distinct()
        val names = listOf(TransitGroupAppWidget.ROTATING_GROUP_ID, TransitGroupAppWidget.DASHBOARD_SELECTION_ID, TransitGroupAppWidget.NEAREST_STOP_ID) + storedNames.ifEmpty { listOf("") }
        val options = names.map { name ->
            if (name == TransitGroupAppWidget.ROTATING_GROUP_ID) getString(R.string.widget_rotating_group)
            else if (name == TransitGroupAppWidget.NEAREST_STOP_ID) getString(R.string.widget_nearest_stop)
            else if (name == TransitGroupAppWidget.DASHBOARD_SELECTION_ID) getString(R.string.widget_dashboard_selection)
            else name.ifBlank { getString(R.string.widget_default_group) }
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.widget_choose_group)
            .setItems(options) { _, which ->
                val selected = names[which]
                val groupObj = (0 until (groups?.length() ?: 0)).mapNotNull { groups?.optJSONObject(it) }.firstOrNull { it.optString("name") == selected }
                val groupStops = groupObj?.optJSONArray("stops")
                val stops = (0 until (groupStops?.length() ?: 0)).mapNotNull { groupStops?.optJSONObject(it) }

                if (selected != TransitGroupAppWidget.ROTATING_GROUP_ID && selected != TransitGroupAppWidget.DASHBOARD_SELECTION_ID && stops.size > 1) {
                    val stopOptions = listOf(getString(R.string.widget_default_stop)) + stops.map { it.optString("name") }
                    android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.widget_choose_stop)
                        .setItems(stopOptions.toTypedArray()) { _, stopIdx ->
                            val editor = getSharedPreferences(TransitGroupAppWidget.PREFERENCES, MODE_PRIVATE).edit()
                            editor.putString(TransitGroupAppWidget.groupKey(widgetId), selected)
                            if (stopIdx == 0) {
                                editor.remove(TransitGroupAppWidget.stopKey(widgetId))
                            } else {
                                editor.putString(TransitGroupAppWidget.stopKey(widgetId), stops[stopIdx - 1].optString("id"))
                            }
                            editor.apply()
                            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            setResult(RESULT_OK, result)
                            TransitGroupAppWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
                            finish()
                        }
                        .setOnCancelListener {
                            getSharedPreferences(TransitGroupAppWidget.PREFERENCES, MODE_PRIVATE)
                                .edit().putString(TransitGroupAppWidget.groupKey(widgetId), selected).apply()
                            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            setResult(RESULT_OK, result)
                            TransitGroupAppWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
                            finish()
                        }
                        .show()
                } else {
                    getSharedPreferences(TransitGroupAppWidget.PREFERENCES, MODE_PRIVATE)
                        .edit()
                        .putString(TransitGroupAppWidget.groupKey(widgetId), selected)
                        .remove(TransitGroupAppWidget.stopKey(widgetId))
                        .apply()
                    val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    setResult(RESULT_OK, result)
                    TransitGroupAppWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
                    finish()
                }
            }
            .setOnCancelListener { setResult(RESULT_CANCELED); finish() }
            .show()
    }
}
