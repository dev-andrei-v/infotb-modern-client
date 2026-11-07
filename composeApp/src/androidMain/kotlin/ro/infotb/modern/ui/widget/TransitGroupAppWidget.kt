package ro.infotb.modern.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
                val group = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(groupKey(widgetId), "").orEmpty()
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
        const val EXTRA_OPEN_GROUP = "open_dashboard_group"
        const val ACTION_ROTATE = "ro.infotb.modern.widget.ROTATE"
        const val ACTION_OPEN_GROUP = "ro.infotb.modern.widget.OPEN_GROUP"

        fun groupKey(id: Int) = "group_$id"
        private fun indexKey(id: Int) = "index_$id"

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val groupName = prefs.getString(groupKey(widgetId), "").orEmpty()
            val snapshot = runCatching { JSONObject(prefs.getString(SNAPSHOT_KEY, "{}") ?: "{}") }.getOrNull()
            val groups = snapshot?.optJSONArray("groups")
            val group = (0 until (groups?.length() ?: 0))
                .mapNotNull { groups?.optJSONObject(it) }
                .firstOrNull { it.optString("name") == groupName }
            val stops = group?.optJSONArray("stops")
            val stopCount = stops?.length() ?: 0
            val empty = stopCount == 0
            val index = if (empty) 0 else Math.floorMod(prefs.getInt(indexKey(widgetId), 0), stopCount)
            val stop = if (empty) null else stops?.optJSONObject(index)
            val views = RemoteViews(context.packageName, R.layout.widget_transit_group).apply {
                setTextViewText(R.id.widget_group_name, groupName.ifBlank { context.getString(R.string.widget_choose_group) })
                setTextViewText(R.id.widget_stop_name, stop?.optString("name") ?: context.getString(R.string.widget_no_stops))
                setTextViewText(R.id.widget_arrivals, stop?.optJSONArray("arrivals")?.let(::formatArrivals)
                    ?: context.getString(R.string.widget_open_dashboard))
                setViewVisibility(R.id.widget_rotate, if (stopCount > 1) android.view.View.VISIBLE else android.view.View.GONE)
                setOnClickPendingIntent(R.id.widget_rotate, broadcast(context, widgetId, ACTION_ROTATE, 1))
                setOnClickPendingIntent(R.id.widget_open, broadcast(context, widgetId, ACTION_OPEN_GROUP, 2))
                setOnClickPendingIntent(R.id.widget_root, activity(context, groupName, widgetId))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun formatArrivals(arrivals: org.json.JSONArray): String {
            if (arrivals.length() == 0) return ""
            return (0 until minOf(arrivals.length(), 3)).joinToString("  ·  ") { i ->
                val item = arrivals.optJSONObject(i) ?: JSONObject()
                val seconds = item.optLong("seconds", -1L)
                val time = if (seconds <= 60L) "Due" else "${(seconds + 30) / 60} min"
                "${item.optString("line")}  $time"
            }
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
        val names = storedNames.ifEmpty { listOf("") }
        val options = names.map { it.ifBlank { getString(R.string.widget_default_group) } }.toTypedArray()
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
