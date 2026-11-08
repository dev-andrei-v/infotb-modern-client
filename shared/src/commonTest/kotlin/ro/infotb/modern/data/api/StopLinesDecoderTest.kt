package ro.infotb.modern.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ro.infotb.modern.data.model.TransportType

class StopLinesDecoderTest {
    @Test
    fun decodesMembershipsWithoutDependingOnNullableArrivals() {
        val lines = decodeStopLines("""{"name":"Universitate","lines":[
            {"id":121,"name":"205","type":"BUS","color":"#1D71B8","direction":0,"arriving_time":null,"arriving_times":null,"timetable":null},
            {"id":121,"name":"205","type":"BUS","color":"#1D71B8","direction":1},
            {"id":2,"name":"M2","type":"SUBWAY","color":"#1D1D1B"}
        ]}""")
        assertEquals(listOf(121L, 2L), lines.map { it.id })
        assertEquals(TransportType.BUS, lines.first().type)
        assertEquals("#1D71B8", lines.first().color)
        assertEquals("#0070BA", lines.last().color)
    }

    @Test
    fun toleratesEmptyMembershipsAndMissingColor() {
        assertTrue(decodeStopLines("""{"lines":null}""").isEmpty())
        assertTrue(decodeStopLines("""{"lines":[]}""").isEmpty())
        val line = decodeStopLines("""{"lines":[{"id":1,"name":"1","type":"TRAM","color":null}]}""").single()
        assertEquals("#1E88E5", line.color)
    }
}
