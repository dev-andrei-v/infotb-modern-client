package ro.infotb.modern.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import ro.infotb.modern.AndroidContext
import ro.infotb.modern.MainActivity
import org.json.JSONArray
import org.json.JSONObject

actual fun publishDashboardWidgetSnapshot(snapshot: String) {
    val context = AndroidContext.appContext
    context.getSharedPreferences(TransitGroupAppWidget.PREFERENCES, 0)
        .edit()
        .putString(TransitGroupAppWidget.SNAPSHOT_KEY, snapshot)
        .apply()
    runCatching {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, TransitGroupAppWidget::class.java)
        manager.getAppWidgetIds(provider).forEach { TransitGroupAppWidget.update(context, manager, it) }
    }.onFailure { Log.w("TransitGroupWidget", "Unable to refresh dashboard widget", it) }
}

actual fun publishPlannerQuickShortcutSnapshot(savedTripsJson: String) {
    val context = AndroidContext.appContext
    val prefs = context.getSharedPreferences(TransitGroupAppWidget.PREFERENCES, Context.MODE_PRIVATE)
    prefs.edit().putString(TransitAppShortcuts.TRIPS_KEY, savedTripsJson).apply()
    TransitAppShortcuts.refresh(context, savedTripsJson)
}

object TransitAppShortcuts {
    const val ACTION_KEY = "ro.infotb.modern.quick_action"
    const val DESTINATION_KEY = "ro.infotb.modern.quick_destination"
    const val TRIP_ID_KEY = "ro.infotb.modern.quick_trip_id"
    const val USAGE_KEY = "planner.destination_shortcut_usage"
    const val TRIPS_KEY = "planner.saved_trips_snapshot"

    fun refresh(context: Context, savedTripsJson: String? = null) {
        if (android.os.Build.VERSION.SDK_INT < 25) return
        val prefs = context.getSharedPreferences(TransitGroupAppWidget.PREFERENCES, Context.MODE_PRIVATE)
        val trips = savedTripsJson ?: prefs.getString(TRIPS_KEY, "[]") ?: "[]"
        val shortcuts = mutableListOf(
            shortcut(context, "lines", if (java.util.Locale.getDefault().language == "en") "Lines" else "Linii", android.R.drawable.ic_menu_directions),
            shortcut(context, "map", if (java.util.Locale.getDefault().language == "en") "Map" else "Hartă", android.R.drawable.ic_menu_mapmode)
        )
        val mostUsedTrip = runCatching {
            val allTrips = JSONArray(trips)
            val usage = prefs.getStringSet(USAGE_KEY, emptySet()).orEmpty()
                .mapNotNull { raw -> raw.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].toIntOrNull().orZero() } }
                .toMap()
            (0 until allTrips.length()).mapNotNull { allTrips.optJSONObject(it) }
                .sortedWith(compareByDescending<JSONObject> { usage[it.optString("id")] ?: 0 })
                .firstOrNull()
        }.getOrNull()
        val mostUsedDestination = mostUsedTrip?.optJSONObject("destination")
        if (mostUsedTrip != null && mostUsedDestination != null) {
            val destinationJson = mostUsedDestination.toString()
            val placeName = mostUsedDestination.optString("name", "Destination")
            val title = (if (java.util.Locale.getDefault().language == "en") "Plan to " else "Ruta spre ") + placeName.take(18)
            shortcuts += shortcut(context, "plan_destination", title, android.R.drawable.ic_menu_directions, destinationJson, mostUsedTrip.optString("id"))
        }
        runCatching { context.getSystemService(ShortcutManager::class.java)?.setDynamicShortcuts(shortcuts) }
            .onFailure { Log.w("TransitQuickActions", "Unable to update launcher shortcuts", it) }
    }

    fun recordDestinationUse(context: Context, tripId: String) {
        if (tripId.isBlank()) return
        val prefs = context.getSharedPreferences(TransitGroupAppWidget.PREFERENCES, Context.MODE_PRIVATE)
        val counts = prefs.getStringSet(USAGE_KEY, emptySet()).orEmpty().mapNotNull { raw ->
            raw.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) }
        }.toMap().toMutableMap()
        counts[tripId] = (counts[tripId] ?: 0) + 1
        prefs.edit().putStringSet(USAGE_KEY, counts.map { "${it.key}=${it.value}" }.toSet()).apply()
        refresh(context)
    }

    private fun shortcut(context: Context, id: String, label: String, iconRes: Int, destinationJson: String? = null, tripId: String? = null): ShortcutInfo {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ACTION_KEY, id)
            destinationJson?.let { putExtra(DESTINATION_KEY, it) }
            tripId?.let { putExtra(TRIP_ID_KEY, it) }
        }
        return ShortcutInfo.Builder(context, id)
            .setShortLabel(label.take(25))
            .setLongLabel(label.take(40))
            .setIcon(Icon.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }

    private fun Int?.orZero() = this ?: 0
}
