package ro.infotb.modern.data.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ro.infotb.modern.data.model.*
import ro.infotb.modern.util.Constants
import ro.infotb.modern.util.lineColor

class StbApi(private val client: HttpClient, private val authManager: AuthManager) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun getHeaders(token: String?): Map<String, String> {
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json, text/plain, */*",
            "OS-Type" to "Web",
            "Lang" to "ro",
            "App-Version" to "0.0.0",
            "Device-Name" to "Chrome",
            "OS-Version" to "Mozilla/5.0 (Linux; Android) KMP/1.0",
            "Source" to Constants.APP_SOURCE,
            "App-Id" to authManager.appId
        )
        if (token != null) {
            headers["User-Info"] = token
        }
        return headers
    }

    private suspend fun request(path: String, token: String): HttpResponse {
        return client.get("${Constants.API_BASE_URL}$path") {
            for ((key, value) in getHeaders(token)) {
                header(key, value)
            }
        }
    }

    private suspend fun requestRoutes(body: String, token: String): HttpResponse =
        client.post("${Constants.API_BASE_URL}/routes") {
            for ((key, value) in getHeaders(token)) header(key, value)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /**
     * Performs an authenticated GET. On 401/412 the token is refreshed once
     * (shared across concurrent callers) and the request is retried.
     */
    private suspend fun fetch(path: String): HttpResponse {
        val token = authManager.ensureAuthenticated(client)
        var response = request(path, token)

        if (response.status.value == 401 || response.status.value == 412) {
            val fresh = authManager.reauthenticate(client, token)
            response = request(path, fresh)
        }

        if (response.status.value !in 200..299) {
            throw RuntimeException("API error: ${response.status.value} ${response.status.description}")
        }
        return response
    }

    private suspend fun fetchRoutes(body: String): String {
        val token = authManager.ensureAuthenticated(client)
        var response = requestRoutes(body, token)
        if (response.status.value == 401 || response.status.value == 412) {
            response = requestRoutes(body, authManager.reauthenticate(client, token))
        }
        if (response.status.value !in 200..299) {
            throw RuntimeException("API error: ${response.status.value} ${response.status.description}")
        }
        return response.bodyAsText()
    }

    private suspend fun fetchProto(path: String): ByteArray = fetch(path).readRawBytes()

    private suspend fun fetchJson(path: String): String = fetch(path).bodyAsText()

    suspend fun getAllLines(): List<Line> {
        val data = fetchProto("/lines?lang=ro")
        return ProtoDecoder.decodeLines(data)
    }

    suspend fun getLineDetail(lineId: Long): LineDetail {
        val data = fetchProto("/lines/$lineId?lang=ro")
        return ProtoDecoder.decodeLineDetail(data)
    }

    suspend fun getLineDirection(lineId: Long, direction: Direction): LineDetail {
        val data = fetchProto("/lines/$lineId/direction/${direction.value}?lang=ro")
        return ProtoDecoder.decodeLineDetail(data)
    }

    suspend fun getStopInfo(stopId: Long, lineId: Long?, direction: Direction?): StopInfo {
        val params = buildString {
            append("stop_id=$stopId")
            if (lineId != null) append("&selected_line_id=$lineId")
            if (direction != null) append("&direction=${direction.value}")
        }
        val data = fetchProto("/lines/stop?$params")
        return ProtoDecoder.decodeStopInfo(data)
    }

    suspend fun getStopDetails(stopId: Long): StopDetails {
        val data = fetchProto("/lines/stops/$stopId")
        return ProtoDecoder.decodeStopDetails(data)
    }

    /** Lightweight memberships; this endpoint currently serves JSON, including metro stops. */
    suspend fun getStopLines(stopId: Long): List<Line> {
        return decodeStopLines(fetchJson("/lines/stops/$stopId"))
    }

    suspend fun getVehicles(lineId: Long, direction: Direction): List<VehiclePosition> {
        val data = fetchProto("/lines/v2/$lineId/vehicles/${direction.value}?lang=ro")
        return ProtoDecoder.decodeVehicles(data)
    }

    suspend fun getAnnouncements(limit: Int, offset: Int): List<Announcement> {
        val body = fetchJson("/notifications?limit=$limit&offset=$offset")
        val root = json.parseToJsonElement(body)
        val notificationsArray = announcementArray(root)
            ?: throw IllegalStateException("Unexpected announcements response shape")
        return json.decodeFromString<List<Announcement>>(notificationsArray.toString()).map { notice ->
            notice.copy(lines = notice.lines.map { it.copy(color = lineColor(it.name, it.type, it.color)) })
        }
    }

    suspend fun searchPlannerPlaces(query: String): List<PlannerPlace> {
        val root = json.parseToJsonElement(fetchJson("/places?query=${encodeQuery(query)}"))
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["places"] as? JsonArray)
                ?: (root["data"] as? JsonArray)
                ?: (root["results"] as? JsonArray)
                ?: (root["items"] as? JsonArray)
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { element ->
            val place = element as? JsonObject ?: return@mapNotNull null
            val name = (place["name"] ?: place["label"])?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val lat = (place["lat"] ?: place["latitude"])?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lng = (place["lng"] ?: place["lon"] ?: place["longitude"])?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            PlannerPlace(
                name = name,
                lat = lat,
                lng = lng,
                type = place["type"]?.jsonPrimitive?.contentOrNull,
                description = place["description"]?.jsonPrimitive?.contentOrNull,
                stop_id = (place["stop_id"] ?: place["id"])?.jsonPrimitive?.longOrNull
            )
        }
    }

    suspend fun planRoutes(start: PlannerPlace, destination: PlannerPlace, maxWalkDistance: Int, startTimeMs: Long, vehicleTypes: List<String>, accessible: Boolean): String {
        // The client model calls this mode CABLE_CAR, while the web planner
        // expects its public API name, TROLLEYBUS.
        val apiVehicleTypes = vehicleTypes.map { if (it == "CABLE_CAR") "TROLLEYBUS" else it }
        val body = buildString {
            append("{\"start_lat\":${start.lat},\"start_lng\":${start.lng},\"stop_lat\":${destination.lat},\"stop_lng\":${destination.lng},\"max_walk_distance\":$maxWalkDistance,\"start_time\":$startTimeMs")
            append(",\"vehicle_types\":${json.encodeToString(apiVehicleTypes)}")
            append(",\"accessibility\":${if (accessible) "true" else "false"}}")
        }
        return fetchRoutes(body)
    }

    private fun encodeQuery(value: String): String = value.encodeToByteArray().joinToString("") { byte ->
        val unsigned = byte.toInt() and 0xff
        if ((unsigned in 48..57) || (unsigned in 65..90) || (unsigned in 97..122) || unsigned in listOf(45, 46, 95, 126)) {
            unsigned.toChar().toString()
        } else "%" + unsigned.toString(16).uppercase().padStart(2, '0')
    }

    private fun announcementArray(root: JsonElement): JsonArray? = when (root) {
        is JsonArray -> root
        is JsonObject -> {
            (root["notifications"] as? JsonArray)
                ?: (root["data"] as? JsonArray)
                ?: ((root["data"] as? JsonObject)?.get("notifications") as? JsonArray)
                ?: ((root["data"] as? JsonObject)?.get("items") as? JsonArray)
                ?: (root["items"] as? JsonArray)
                ?: (root["results"] as? JsonArray)
        }
        else -> null
    }

    suspend fun getHomeStops(stopId: Long): List<Stop> {
        val data = fetchProto("/lines/v2/home/stops/$stopId")
        return ProtoDecoder.decodeHomeStops(data)
    }

    suspend fun getStopsInBounds(south: Double, west: Double, north: Double, east: Double): List<Stop> {
        require(south.isFinite() && north.isFinite() && west.isFinite() && east.isFinite())
        require(south >= -90 && north <= 90 && south < north && west >= -180 && east <= 180 && west < east)
        val data = fetchProto("/lines/v2/home/stops/$south/$west/$north/$east")
        return ProtoDecoder.decodeHomeStops(data)
    }
}
