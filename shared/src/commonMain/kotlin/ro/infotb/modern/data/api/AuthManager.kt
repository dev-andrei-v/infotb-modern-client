package ro.infotb.modern.data.api

import com.russhwolf.settings.Settings
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ro.infotb.modern.util.Constants

class AuthManager(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true }

    var appId: String
        get() {
            val stored = settings.getStringOrNull(Constants.SETTINGS_KEY_APP_ID)
            if (stored != null) return stored
            val generated = generateUuid()
            settings.putString(Constants.SETTINGS_KEY_APP_ID, generated)
            return generated
        }
        set(value) {
            settings.putString(Constants.SETTINGS_KEY_APP_ID, value)
        }

    var userInfo: String?
        get() = settings.getStringOrNull(Constants.SETTINGS_KEY_USER_INFO)
        set(value) {
            if (value != null) {
                settings.putString(Constants.SETTINGS_KEY_USER_INFO, value)
            } else {
                settings.remove(Constants.SETTINGS_KEY_USER_INFO)
            }
        }

    val isAuthenticated: Boolean get() = userInfo != null

    suspend fun authenticate(client: HttpClient): String {
        val currentAppId = appId
        val response = client.get("${Constants.API_BASE_URL}/proxy/user/auth") {
            header("App-key", Constants.APP_KEY)
            header("App-Id", currentAppId)
            header("Content-Type", "application/json")
            header("Accept", "application/json, text/plain, */*")
        }

        if (response.status.value != 200) {
            throw RuntimeException("Auth failed: ${response.status.value}")
        }

        val body = response.bodyAsText()
        val jsonObj = json.parseToJsonElement(body).jsonObject
        val token = jsonObj["data"]?.jsonObject?.get("userInfo")?.jsonPrimitive?.content
            ?: jsonObj["userInfo"]?.jsonPrimitive?.content
            ?: throw RuntimeException("No userInfo in auth response")

        userInfo = token
        return token
    }

    fun clear() {
        userInfo = null
    }

    private fun generateUuid(): String {
        val chars = "0123456789abcdef"
        val template = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx"
        return buildString {
            for (c in template) {
                when (c) {
                    'x' -> append(chars.random())
                    'y' -> append(chars["89ab".random().digitToInt()])
                    else -> append(c)
                }
            }
        }
    }
}
