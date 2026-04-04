package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Organization(
    val id: Long,
    val code: String? = null,
    val name: String? = null,
    val logoFile: String? = null,
    val logo: String? = null
)
