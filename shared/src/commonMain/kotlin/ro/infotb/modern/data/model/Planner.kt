package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable

/** A place returned by STB's planner autocomplete. */
@Serializable
data class PlannerPlace(
    val name: String,
    val lat: Double,
    val lng: Double,
    val type: String? = null,
    val description: String? = null,
    val stop_id: Long? = null
)
