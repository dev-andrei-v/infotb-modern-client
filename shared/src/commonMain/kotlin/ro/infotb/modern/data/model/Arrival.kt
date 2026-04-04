package ro.infotb.modern.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LineArrival(
    val id: Long,
    val name: String,
    val type: TransportType,
    val color: String,
    val direction: Direction,
    val directionName: String,
    val arrivingTime: Long,
    val isTimetable: Boolean,
    val arrivingTimes: List<ArrivalTime> = emptyList(),
    val timetable: List<TimetableHour> = emptyList(),
    val hasDisability: Boolean = false
)

@Serializable
data class LineArrivalDetail(
    val id: Long,
    val name: String,
    val type: TransportType,
    val color: String,
    val direction: Direction,
    val directionName: String,
    val arrivingTime: Long,
    val isTimetable: Boolean,
    val arrivingTimes: List<ArrivalTime> = emptyList(),
    val timetable: List<TimetableHour> = emptyList(),
    val hasDisability: Boolean = false,
    val segmentPath: String? = null,
    val vehicles: List<VehiclePosition> = emptyList()
)

@Serializable
data class ArrivalTime(
    val arrivingTime: Long,
    val isTimetable: Boolean,
    val hasDisability: Boolean = false
)

@Serializable
data class TimetableHour(
    val hour: String,
    val minutes: List<String> = emptyList()
)
