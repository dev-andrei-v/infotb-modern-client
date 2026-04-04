package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Stop(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val name: String,
    val description: String? = null,
    val type: String? = null,
    val iconColor: String? = null,
    val lines: List<LineArrival>? = null
)

@Serializable
data class StopInfo(
    val name: String,
    val address: String? = null,
    val image: String? = null,
    val type: String? = null,
    val scheduleWeekday: String? = null,
    val scheduleSaturday: String? = null,
    val scheduleSunday: String? = null,
    val lines: List<LineArrivalDetail> = emptyList(),
    val hasDisability: Boolean = false
)

@Serializable
data class StopDetails(
    val transportTypes: List<TransportTypeGroup> = emptyList()
)

@Serializable
data class TransportTypeGroup(
    val transportType: TransportType,
    val lines: List<LineArrival> = emptyList(),
    val name: String,
    val description: String? = null
)
