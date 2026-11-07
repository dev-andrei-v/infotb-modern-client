package ro.infotb.modern.data.api

import kotlinx.serialization.json.*
import ro.infotb.modern.data.model.Line
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.util.lineColor

/** Stop memberships contain nullable arrival fields, so decode only line metadata. */
internal fun decodeStopLines(data: String): List<Line> {
    val body = Json.parseToJsonElement(data).jsonObject
    val lines = body["lines"] as? JsonArray ?: return emptyList()
    return lines.map { entry ->
        val value = entry.jsonObject
        val name = value.getValue("name").jsonPrimitive.content
        val type = TransportType.fromString(value.getValue("type").jsonPrimitive.content)
        Line(
            id = value.getValue("id").jsonPrimitive.long,
            name = name,
            type = type,
            color = lineColor(name, type, value["color"]?.jsonPrimitive?.contentOrNull ?: "#1E88E5")
        )
    }.distinctBy { it.id }
}
