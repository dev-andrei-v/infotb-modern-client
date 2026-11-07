package ro.infotb.modern.ui.screens.planner

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ro.infotb.modern.data.model.PlannerPlace
import ro.infotb.modern.util.lineColor

@Serializable
data class SavedTripRecord(
    val id: String,
    val origin: PlannerPlace,
    val destination: PlannerPlace,
    val savedAtTimestamp: Long,
    val modes: List<String> = emptyList(),
    val accessible: Boolean = false,
    val maxWalkDistance: Int = 1000
)

data class RouteOption(
    val id: String,
    val title: String,
    val durationText: String,
    val arrivalTime: String?,
    val departureTime: String?,
    val summary: String?,
    val legs: List<RouteLeg>
)

data class RouteLeg(
    val mode: String, // "WALK", "BUS", "TRAM", "TROLLEYBUS", "SUBWAY"
    val isWalk: Boolean,
    val lineName: String?,
    val lineColor: String?,
    val fromStop: String?,
    val toStop: String?,
    val durationText: String?,
    val distanceText: String?
)

object RouteJsonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawJson: String): List<RouteOption> {
        return try {
            val element = json.parseToJsonElement(rawJson)
            val array = findRoutesArray(element) ?: return emptyList()
            array.mapIndexedNotNull { index, item ->
                val obj = item as? JsonObject ?: return@mapIndexedNotNull null
                parseOption(obj, index)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findRoutesArray(element: JsonElement): JsonArray? {
        if (element is JsonArray) return element
        if (element !is JsonObject) return null
        for (key in listOf("routes", "options", "itineraries", "results", "data")) {
            val child = element[key]
            if (child is JsonArray && child.isNotEmpty()) return child
            if (child is JsonObject) {
                val nested = findRoutesArray(child)
                if (nested != null && nested.isNotEmpty()) return nested
            }
        }
        return null
    }

    private fun parseOption(obj: JsonObject, index: Int): RouteOption {
        val title = obj.string("title", "name") ?: "Varianta ${index + 1}"
        val durationValue = obj.primitive("duration", "total_duration")
        val durationText = formatDuration(durationValue)

        val arrivalTime = obj.string("arrival_time", "arrival", "end_time")
        val departureTime = obj.string("departure_time", "departure", "start_time")
        val summary = obj.string("summary", "description")

        val segments = (obj["segments"] as? JsonArray)
            ?: (obj["steps"] as? JsonArray)
            ?: (obj["legs"] as? JsonArray)
            ?: JsonArray(emptyList())

        val legs = segments.mapNotNull { segmentElem ->
            val seg = segmentElem as? JsonObject ?: return@mapNotNull null
            parseLeg(seg)
        }

        return RouteOption(
            id = obj.string("id") ?: "route_$index",
            title = title,
            durationText = durationText,
            arrivalTime = arrivalTime,
            departureTime = departureTime,
            summary = summary,
            legs = legs
        )
    }

    private fun parseLeg(seg: JsonObject): RouteLeg? {
        val rawMode = (seg.string("type", "mode", "vehicle_type") ?: "TRANSIT").uppercase()
        val isWalk = rawMode.contains("WALK") || rawMode.contains("FOOT")

        val lineObj = seg["line"] as? JsonObject
        val lineName = lineObj?.string("name", "short_name")
            ?: seg.string("line_name", "route_name", "line")
        val rawColor = lineObj?.string("color") ?: seg.string("color") ?: "#1E88E5"

        val fromStop = seg.string("from_stop", "from", "start_stop")
        val toStop = seg.string("to_stop", "to", "end_stop")

        val durationVal = seg.primitive("duration", "time")
        val durationText = formatDuration(durationVal)

        val distVal = seg.primitive("distance", "length")
        val distanceText = distVal?.let { formatDistance(it) }

        val normalizedMode = when {
            isWalk -> "WALK"
            rawMode.contains("SUBWAY") || rawMode.contains("METRO") -> "SUBWAY"
            rawMode.contains("TRAM") -> "TRAM"
            rawMode.contains("TROLLEY") || rawMode.contains("CABLE") -> "CABLE_CAR"
            else -> "BUS"
        }

        val type = if (normalizedMode == "SUBWAY") ro.infotb.modern.data.model.TransportType.SUBWAY else ro.infotb.modern.data.model.TransportType.BUS
        val lineColor = lineName?.let { lineColor(it, type, rawColor) } ?: rawColor

        return RouteLeg(
            mode = normalizedMode,
            isWalk = isWalk,
            lineName = lineName,
            lineColor = lineColor,
            fromStop = fromStop,
            toStop = toStop,
            durationText = durationText,
            distanceText = distanceText
        )
    }

    private fun formatDuration(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val num = value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
        return if (num != null) {
            if (num > 120) "${num / 60} min" else "$num min"
        } else {
            value
        }
    }

    private fun formatDistance(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val meters = value.toDoubleOrNull()?.toInt() ?: value.toIntOrNull()
        return if (meters != null) {
            if (meters >= 1000) String.format("%.1f km", meters / 1000f) else "$meters m"
        } else {
            value
        }
    }

    private fun JsonObject.string(vararg keys: String): String? {
        for (k in keys) {
            val v = this[k]?.jsonPrimitive?.contentOrNull?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    private fun JsonObject.primitive(vararg keys: String): String? {
        for (k in keys) {
            val v = this[k]?.jsonPrimitive?.contentOrNull?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }
}
