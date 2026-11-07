package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable
import ro.infotb.modern.util.isAirportLine

@Serializable
data class Line(
    val id: Long,
    val name: String,
    val type: TransportType,
    val color: String,
    val hasNotifications: Boolean = false,
    val organization: Organization? = null,
    val startStopName: String? = null,
    val endStopName: String? = null
) {
    val isAirport: Boolean get() = isAirportLine(name, listOfNotNull(startStopName, endStopName).joinToString(" "))
}

@Serializable
data class LineDetail(
    val id: Long,
    val name: String,
    val type: TransportType,
    val color: String,
    val segmentPath: String? = null,
    val directionNameTur: String? = null,
    val directionNameRetur: String? = null,
    val stops: List<Stop> = emptyList()
) {
    val isAirport: Boolean get() = isAirportLine(name, listOfNotNull(directionNameTur, directionNameRetur).joinToString(" "))
}
