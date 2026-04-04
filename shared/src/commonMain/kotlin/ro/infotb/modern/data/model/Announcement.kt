package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Announcement(
    val id: Long,
    val title: String,
    val message: String,
    val lines: List<AnnouncementLine> = emptyList(),
    val created_at: Long
)

@Serializable
data class AnnouncementLine(
    val id: Long? = null,
    val name: String,
    val color: String,
    val type: TransportType
)
