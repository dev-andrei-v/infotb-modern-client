package ro.infotb.modern.data.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ro.infotb.modern.data.model.*
import ro.infotb.modern.util.Constants

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

    suspend fun getVehicles(lineId: Long, direction: Direction): List<VehiclePosition> {
        val data = fetchProto("/lines/v2/$lineId/vehicles/${direction.value}?lang=ro")
        return ProtoDecoder.decodeVehicles(data)
    }

    suspend fun getAnnouncements(limit: Int, offset: Int): List<Announcement> {
        val body = fetchJson("/notifications?limit=$limit&offset=$offset")
        val jsonObj = json.parseToJsonElement(body).jsonObject
        val notificationsArray = jsonObj["notifications"]?.jsonArray ?: return emptyList()
        return json.decodeFromString<List<Announcement>>(notificationsArray.toString())
    }

    suspend fun getHomeStops(stopId: Long): List<Stop> {
        val data = fetchProto("/lines/v2/home/stops/$stopId")
        return ProtoDecoder.decodeHomeStops(data)
    }
}
