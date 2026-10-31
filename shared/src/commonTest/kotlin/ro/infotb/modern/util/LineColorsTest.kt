package ro.infotb.modern.util

import ro.infotb.modern.data.model.TransportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineColorsTest {
    private val api = "#1D1D1B"

    @Test
    fun metroLinesGetTheirOfficialColours() {
        assertEquals("#FFC20E", lineColor("M1", TransportType.SUBWAY, api))
        assertEquals("#0070BA", lineColor("M2", TransportType.SUBWAY, api))
        assertEquals("#E2231A", lineColor("M3", TransportType.SUBWAY, api))
        assertEquals("#009A49", lineColor("M4", TransportType.SUBWAY, api))
    }

    @Test
    fun bothM5BranchesAreOrange() {
        assertEquals("#F7941D", lineColor("M5 - Raul Doamnei - Eroilor 2", TransportType.SUBWAY, api))
        assertEquals("#F7941D", lineColor("M5 - Valea Ialomitei - Eroilor 2", TransportType.SUBWAY, api))
    }

    @Test
    fun otherLinesKeepTheApiColour() {
        assertEquals("#B71C1C", lineColor("41", TransportType.TRAM, "#B71C1C"))
        assertEquals(api, lineColor("M6", TransportType.SUBWAY, api))
        assertEquals(api, lineColor("M1", TransportType.BUS, api))
        assertEquals(api, lineColor("M10", TransportType.SUBWAY, api))
    }

    @Test
    fun yellowNeedsDarkText() {
        assertTrue(isLightColor("#FFC20E"))
        assertFalse(isLightColor("#0070BA"))
        assertFalse(isLightColor("#F7941D"))
        assertFalse(isLightColor("not a colour"))
    }
}
