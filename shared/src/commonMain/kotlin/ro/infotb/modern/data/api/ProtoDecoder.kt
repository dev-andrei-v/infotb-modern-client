package ro.infotb.modern.data.api

import ro.infotb.modern.data.model.*

object ProtoDecoder {

    fun decodeLines(data: ByteArray): List<Line> {
        val reader = ProtobufReader(data)
        val lines = mutableListOf<Line>()
        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> {
                    val sub = reader.readMessage()
                    lines.add(decodeLine(sub))
                }
                else -> reader.skipField(wireType)
            }
        }
        return lines
    }

    private fun decodeLine(reader: ProtobufReader): Line {
        var id = 0L
        var name = ""
        var type = TransportType.BUS
        var hasNotifications = false
        var color = "#666"
        var organization: Organization? = null

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> id = reader.readLong()
                2 -> name = reader.readString()
                3 -> type = TransportType.fromString(reader.readString())
                4 -> hasNotifications = reader.readBool()
                6 -> color = reader.readString()
                9 -> {
                    val sub = reader.readMessage()
                    organization = decodeOrganization(sub)
                }
                else -> reader.skipField(wireType)
            }
        }
        return Line(id, name, type, color.ifEmpty { "#666" }, hasNotifications, organization)
    }

    private fun decodeOrganization(reader: ProtobufReader): Organization {
        var id = 0L
        var code: String? = null
        var name: String? = null
        var logoFile: String? = null
        var logo: String? = null

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> id = reader.readLong()
                2 -> code = reader.readString()
                3 -> name = reader.readString()
                4 -> logoFile = reader.readString()
                5 -> logo = reader.readString()
                else -> reader.skipField(wireType)
            }
        }
        return Organization(id, code, name, logoFile, logo)
    }

    fun decodeLineDetail(data: ByteArray): LineDetail {
        val reader = ProtobufReader(data)
        var id = 0L
        var name = ""
        var type = TransportType.BUS
        var color = "#666"
        var segmentPath: String? = null
        var directionNameTur: String? = null
        var directionNameRetur: String? = null
        val stops = mutableListOf<Stop>()

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> id = reader.readLong()
                2 -> name = reader.readString()
                3 -> type = TransportType.fromString(reader.readString())
                5 -> color = reader.readString()
                9 -> segmentPath = reader.readString()
                10 -> directionNameTur = reader.readString()
                11 -> directionNameRetur = reader.readString()
                12 -> {
                    val sub = reader.readMessage()
                    stops.add(decodeStop(sub))
                }
                else -> reader.skipField(wireType)
            }
        }
        return LineDetail(id, name, type, color.ifEmpty { "#666" }, segmentPath, directionNameTur, directionNameRetur, stops)
    }

    private fun decodeStop(reader: ProtobufReader): Stop {
        var id = 0L
        var lat = 0.0
        var lng = 0.0
        var name = ""
        var description: String? = null
        var type: String? = null
        var iconColor: String? = null
        val lines = mutableListOf<LineArrival>()

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> id = reader.readLong()
                2 -> lat = reader.readDouble()
                3 -> lng = reader.readDouble()
                4 -> name = reader.readString()
                5 -> description = reader.readString()
                6 -> type = reader.readString()
                9 -> {
                    val sub = reader.readMessage()
                    lines.add(decodeLineArrival(sub))
                }
                10 -> iconColor = reader.readString()
                else -> reader.skipField(wireType)
            }
        }
        return Stop(id, lat, lng, name, description, type, iconColor, lines.ifEmpty { null })
    }

    private fun decodeLineArrival(reader: ProtobufReader): LineArrival {
        var id = 0L
        var name = ""
        var type = TransportType.BUS
        var color = "#666"
        var direction = Direction.TUR
        var directionName = ""
        var arrivingTime = -1L
        var isTimetable = false
        val arrivingTimes = mutableListOf<ArrivalTime>()
        val timetable = mutableListOf<TimetableHour>()
        var hasDisability = false

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> id = reader.readLong()
                2 -> name = reader.readString()
                3 -> type = TransportType.fromString(reader.readString())
                4 -> color = reader.readString()
                6 -> direction = Direction.fromInt(reader.readInt())
                7 -> directionName = reader.readString()
                8 -> arrivingTime = reader.readLong()
                9 -> {
                    val sub = reader.readMessage()
                    arrivingTimes.add(decodeArrivalTime(sub))
                }
                10 -> isTimetable = reader.readBool()
                11 -> {
                    val sub = reader.readMessage()
                    timetable.add(decodeTimetableHour(sub))
                }
                13 -> hasDisability = reader.readBool()
                else -> reader.skipField(wireType)
            }
        }
        return LineArrival(id, name, type, color.ifEmpty { "#666" }, direction, directionName, arrivingTime, isTimetable, arrivingTimes, timetable, hasDisability)
    }

    private fun decodeArrivalTime(reader: ProtobufReader): ArrivalTime {
        var isTimetable = false
        var arrivingTime = 0L
        var hasDisability = false

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> isTimetable = reader.readBool()
                2 -> arrivingTime = reader.readLong()
                3 -> hasDisability = reader.readBool()
                else -> reader.skipField(wireType)
            }
        }
        return ArrivalTime(arrivingTime, isTimetable, hasDisability)
    }

    private fun decodeTimetableHour(reader: ProtobufReader): TimetableHour {
        var hour = ""
        val minutes = mutableListOf<String>()

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> hour = reader.readString()
                2 -> minutes.add(reader.readString())
                else -> reader.skipField(wireType)
            }
        }
        return TimetableHour(hour, minutes)
    }

    fun decodeStopInfo(data: ByteArray): StopInfo {
        val reader = ProtobufReader(data)
        var name = ""
        var address: String? = null
        var image: String? = null
        var type: String? = null
        var scheduleWeekday: String? = null
        var scheduleSaturday: String? = null
        var scheduleSunday: String? = null
        val lines = mutableListOf<LineArrivalDetail>()
        var hasDisability = false

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> name = reader.readString()
                2 -> address = reader.readString()
                3 -> image = reader.readString()
                5 -> type = reader.readString()
                7 -> scheduleWeekday = reader.readString()
                8 -> scheduleSaturday = reader.readString()
                9 -> scheduleSunday = reader.readString()
                10 -> {
                    val sub = reader.readMessage()
                    lines.add(decodeResponseLine(sub))
                }
                11 -> hasDisability = reader.readBool()
                else -> reader.skipField(wireType)
            }
        }
        return StopInfo(name, address, image, type, scheduleWeekday, scheduleSaturday, scheduleSunday, lines, hasDisability)
    }

    private fun decodeResponseLine(reader: ProtobufReader): LineArrivalDetail {
        var name = ""
        var id = 0L
        var type = TransportType.BUS
        var color = "#666"
        var directionName = ""
        var arrivingTime = -1L
        var isTimetable = false
        var direction = Direction.TUR
        val arrivingTimes = mutableListOf<ArrivalTime>()
        val timetable = mutableListOf<TimetableHour>()
        var segmentPath: String? = null
        val vehicles = mutableListOf<VehiclePosition>()
        var hasDisability = false

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> name = reader.readString()
                2 -> id = reader.readLong()
                3 -> type = TransportType.fromString(reader.readString())
                4 -> color = reader.readString()
                5 -> directionName = reader.readString()
                6 -> arrivingTime = reader.readLong()
                7 -> isTimetable = reader.readBool()
                8 -> direction = Direction.fromInt(reader.readInt())
                9 -> {
                    val sub = reader.readMessage()
                    arrivingTimes.add(decodeArrivalTime(sub))
                }
                10 -> {
                    val sub = reader.readMessage()
                    timetable.add(decodeTimetableHour(sub))
                }
                11 -> segmentPath = reader.readString()
                12 -> {
                    val sub = reader.readMessage()
                    vehicles.add(decodeResponseVehicle(sub))
                }
                13 -> hasDisability = reader.readBool()
                else -> reader.skipField(wireType)
            }
        }
        return LineArrivalDetail(id, name, type, color.ifEmpty { "#666" }, direction, directionName, arrivingTime, isTimetable, arrivingTimes, timetable, hasDisability, segmentPath, vehicles)
    }

    private fun decodeResponseVehicle(reader: ProtobufReader): VehiclePosition {
        var id = 0L
        var lat = 0.0
        var lng = 0.0
        var transportType: TransportType? = null
        var hasDisability = false

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> id = reader.readLong()
                2 -> lat = reader.readDouble()
                3 -> lng = reader.readDouble()
                4 -> transportType = TransportType.fromString(reader.readString())
                5 -> hasDisability = reader.readBool()
                else -> reader.skipField(wireType)
            }
        }
        return VehiclePosition(id, lat, lng, transportType, null, hasDisability)
    }

    fun decodeStopDetails(data: ByteArray): StopDetails {
        val reader = ProtobufReader(data)
        val groups = mutableListOf<TransportTypeGroup>()

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> {
                    val sub = reader.readMessage()
                    groups.add(decodeTransportTypeGroup(sub))
                }
                else -> reader.skipField(wireType)
            }
        }
        return StopDetails(groups)
    }

    private fun decodeTransportTypeGroup(reader: ProtobufReader): TransportTypeGroup {
        var transportType = TransportType.BUS
        val lines = mutableListOf<LineArrival>()
        var name = ""
        var description: String? = null

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> transportType = TransportType.fromString(reader.readString())
                2 -> {
                    val sub = reader.readMessage()
                    lines.add(decodeLineArrival(sub))
                }
                3 -> name = reader.readString()
                4 -> description = reader.readString()
                else -> reader.skipField(wireType)
            }
        }
        return TransportTypeGroup(transportType, lines, name, description)
    }

    fun decodeVehicles(data: ByteArray): List<VehiclePosition> {
        val reader = ProtobufReader(data)
        val vehicles = mutableListOf<VehiclePosition>()

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> {
                    val sub = reader.readMessage()
                    vehicles.add(decodeGetLineVehicle(sub))
                }
                else -> reader.skipField(wireType)
            }
        }
        return vehicles
    }

    private fun decodeGetLineVehicle(reader: ProtobufReader): VehiclePosition {
        var id = 0L
        var lat = 0.0
        var lng = 0.0
        var code: String? = null
        var transportType: TransportType? = null
        var hasDisability = false

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> id = reader.readLong()
                2 -> lat = reader.readDouble()
                3 -> lng = reader.readDouble()
                4 -> code = reader.readString()
                5 -> transportType = TransportType.fromString(reader.readString())
                6 -> hasDisability = reader.readBool()
                else -> reader.skipField(wireType)
            }
        }
        return VehiclePosition(id, lat, lng, transportType, code, hasDisability)
    }

    fun decodeHomeStops(data: ByteArray): List<Stop> {
        val reader = ProtobufReader(data)
        val stops = mutableListOf<Stop>()

        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x7
            when (fieldNumber) {
                1 -> {
                    val sub = reader.readMessage()
                    stops.add(decodeStop(sub))
                }
                else -> reader.skipField(wireType)
            }
        }
        return stops
    }
}
