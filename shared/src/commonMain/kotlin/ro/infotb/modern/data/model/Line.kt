package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Line(
    val id: Long,
    val name: String,
    val type: TransportType,
    val color: String,
    val hasNotifications: Boolean = false,
    val organization: Organization? = null
)

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
)
