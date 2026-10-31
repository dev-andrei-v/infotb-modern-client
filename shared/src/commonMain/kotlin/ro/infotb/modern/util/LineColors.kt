package ro.infotb.modern.util

import ro.infotb.modern.data.model.TransportType

/**
 * Metrorex line colours. The STB API sends the same near-black for every
 * metro line, so these replace it to match the maps riders know.
 */
private val METRO_COLORS = mapOf(
    1 to "#FFC20E", // M1 yellow
    2 to "#0070BA", // M2 blue
    3 to "#E2231A", // M3 red
    4 to "#009A49", // M4 green
    5 to "#F7941D"  // M5 orange (both branches)
)

private val METRO_NAME = Regex("""^M(\d+)\b""", RegexOption.IGNORE_CASE)

/**
 * The colour to show for a line: the official one for metro lines named
 * "M1", "M2", ... (including variants such as "M5 - Raul Doamnei - Eroilor 2"),
 * otherwise whatever the API sent.
 */
fun lineColor(name: String, type: TransportType, apiColor: String): String {
    if (type != TransportType.SUBWAY) return apiColor
    val number = METRO_NAME.find(name.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return apiColor
    return METRO_COLORS[number] ?: apiColor
}

/** True when black text reads better than white on [hex] (e.g. the yellow M1). */
fun isLightColor(hex: String): Boolean {
    val value = hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16) ?: return false
    val r = (value shr 16 and 0xFF) / 255.0
    val g = (value shr 8 and 0xFF) / 255.0
    val b = (value and 0xFF) / 255.0
    return 0.299 * r + 0.587 * g + 0.114 * b > 0.65
}
