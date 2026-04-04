package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VehiclePosition(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val transportType: TransportType? = null,
    val code: String? = null,
    val hasDisability: Boolean = false
)
