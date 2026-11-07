package ro.infotb.modern.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
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
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when (intent.action) {
            ACTION_ROTATE -> {
                val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                prefs.edit().putInt(indexKey(widgetId), prefs.getInt(indexKey(widgetId), 0) + 1).apply()
                update(context, AppWidgetManager.getInstance(context), widgetId)
            }
            ACTION_OPEN_GROUP -> {
                val group = resolveGroupName(context, widgetId)
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_GROUP, group)
                })
            }
        }
    }

    companion object {
        const val PREFERENCES = "transit_group_home_widget"
        const val SNAPSHOT_KEY = "dashboard_snapshot"
        const val SELECTED_STOP_KEY = "dashboard_selected_stop"
        const val DASHBOARD_SELECTION_ID = "__dashboard_selection__"
        const val ROTATING_GROUP_ID = "__rotating_group__"
        const val EXTRA_OPEN_GROUP = "open_dashboard_group"
        const val ACTION_ROTATE = "ro.infotb.modern.widget.ROTATE"
        const val ACTION_OPEN_GROUP = "ro.infotb.modern.widget.OPEN_GROUP"

        fun groupKey(id: Int) = "group_$id"
        private fun indexKey(id: Int) = "index_$id"

        private fun resolveGroupName(context: Context, widgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val selected = prefs.getString(groupKey(widgetId), "").orEmpty()
            if (selected == DASHBOARD_SELECTION_ID) {
                val stopId = prefs.getString(SELECTED_STOP_KEY, null) ?: return ""
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
            val rotationBucket = System.currentTimeMillis() / ROTATION_INTERVAL_MS
            val index = Math.floorMod(rotationBucket, populated.size.toLong()).toInt()
            return populated[index].optString("name")
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val storedGroup = prefs.getString(groupKey(widgetId), "").orEmpty()
            val groupName = resolveGroupName(context, widgetId)
            val snapshot = runCatching { JSONObject(prefs.getString(SNAPSHOT_KEY, "{}") ?: "{}") }.getOrNull()
            val groups = snapshot?.optJSONArray("groups")
            val selectedStopId = prefs.getString(SELECTED_STOP_KEY, null)
            val selectedStop = if (storedGroup == DASHBOARD_SELECTION_ID && selectedStopId != null) {
                (0 until (groups?.length() ?: 0)).asSequence()
                    .mapNotNull { groups?.optJSONObject(it)?.optJSONArray("stops") }
                    .flatMap { stops -> (0 until stops.length()).asSequence().mapNotNull { stops.optJSONObject(it) } }
                    .firstOrNull { it.optString("id") == selectedStopId }
            } else null
            val group = (0 until (groups?.length() ?: 0))
                .mapNotNull { groups?.optJSONObject(it) }
                .firstOrNull { it.optString("name") == groupName }
            val stops = if (selectedStop != null) org.json.JSONArray().put(selectedStop) else group?.optJSONArray("stops")
            val stopCount = stops?.length() ?: 0
            val empty = stopCount == 0
            val index = if (empty) 0 else Math.floorMod(prefs.getInt(indexKey(widgetId), 0), stopCount)
            val stop = if (empty) null else stops?.optJSONObject(index)
            val views = RemoteViews(context.packageName, R.layout.widget_transit_group).apply {
                val selectedGroupName = if (storedGroup == DASHBOARD_SELECTION_ID) {
                    val stopId = selectedStop?.optString("id")
                    (0 until (groups?.length() ?: 0)).mapNotNull { groups?.optJSONObject(it) }.firstOrNull { candidate ->
                        val candidateStops = candidate.optJSONArray("stops")
                        (0 until (candidateStops?.length() ?: 0)).any { candidateStops?.optJSONObject(it)?.optString("id") == stopId }
                    }?.optString("name").orEmpty()
                } else groupName
                val title = when {
                    storedGroup == DASHBOARD_SELECTION_ID && selectedGroupName.isNotBlank() ->
                        context.getString(R.string.widget_dashboard_selection) + " · " + selectedGroupName
                    storedGroup == DASHBOARD_SELECTION_ID -> context.getString(R.string.widget_dashboard_selection)
                    storedGroup == ROTATING_GROUP_ID && groupName.isNotBlank() -> context.getString(R.string.widget_rotating_group) + " · " + groupName
                    else -> groupName.ifBlank { context.getString(R.string.widget_choose_group) }
                }
                setTextViewText(R.id.widget_group_name, title)
                setTextViewText(R.id.widget_stop_name, stop?.optString("name") ?: context.getString(R.string.widget_no_stops))
                val arrivals = stop?.optJSONArray("arrivals")
                removeAllViews(R.id.widget_line_rows)
                val visibleCount = minOf(arrivals?.length() ?: 0, 2)
                setViewVisibility(R.id.widget_line_rows, if (visibleCount > 0) android.view.View.VISIBLE else android.view.View.GONE)
                setViewVisibility(R.id.widget_empty, if (visibleCount == 0) android.view.View.VISIBLE else android.view.View.GONE)
                if (visibleCount == 0) {
                    setTextViewText(R.id.widget_empty, context.getString(R.string.widget_open_dashboard))
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
                setViewVisibility(R.id.widget_rotate, if (stopCount > 1) android.view.View.VISIBLE else android.view.View.GONE)
                setOnClickPendingIntent(R.id.widget_rotate, broadcast(context, widgetId, ACTION_ROTATE, 1))
                setOnClickPendingIntent(R.id.widget_open, broadcast(context, widgetId, ACTION_OPEN_GROUP, 2))
                setOnClickPendingIntent(R.id.widget_root, activity(context, selectedGroupName, widgetId))
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

        private const val ROTATION_INTERVAL_MS = 30 * 60 * 1000L

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

        private fun activity(context: Context, groupName: String, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_GROUP, groupName)
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
        val names = listOf(TransitGroupAppWidget.ROTATING_GROUP_ID, TransitGroupAppWidget.DASHBOARD_SELECTION_ID) + storedNames.ifEmpty { listOf("") }
        val options = names.map { name ->
            if (name == TransitGroupAppWidget.ROTATING_GROUP_ID) getString(R.string.widget_rotating_group)
            else if (name == TransitGroupAppWidget.DASHBOARD_SELECTION_ID) getString(R.string.widget_dashboard_selection)
            else name.ifBlank { getString(R.string.widget_default_group) }
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.widget_choose_group)
            .setItems(options) { _, which ->
                val selected = names[which]
                getSharedPreferences(TransitGroupAppWidget.PREFERENCES, MODE_PRIVATE)
                    .edit().putString(TransitGroupAppWidget.groupKey(widgetId), selected).putInt("index_$widgetId", 0).apply()
                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(RESULT_OK, result)
                TransitGroupAppWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
                finish()
            }
            .setOnCancelListener { setResult(RESULT_CANCELED); finish() }
            .show()
    }
}
