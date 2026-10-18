package ro.infotb.modern.ui.components.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import ro.infotb.modern.data.model.TransportType

/**
 * Vector glyphs for each vehicle type. They replace the emoji that used to
 * stand in for them, so they render identically on every platform and can be
 * tinted with the transport colour.
 */
fun vehicleIcon(type: TransportType): ImageVector = when (type) {
    TransportType.BUS -> BusIcon
    TransportType.TRAM -> TramIcon
    TransportType.CABLE_CAR -> TrolleybusIcon
    TransportType.SUBWAY -> MetroIcon
}

private fun glyph(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        pathData.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            )
        }
    }.build()

/** Front-facing bus with windows and wheels cut out. */
val BusIcon: ImageVector by lazy {
    glyph(
        "Bus",
        "M4 16c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1c0 .55.45 1 1 1h1" +
            "c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4s-8 .5-8 4v10z" +
            "m3.5 1c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17z" +
            "m9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" +
            "m1.5-6H6V6h12v5z"
    )
}

/** Tram with pantograph. */
val TramIcon: ImageVector by lazy {
    glyph(
        "Tram",
        "M19 16.94V8.5c0-2.79-2.61-3.4-6.01-3.49l.76-1.51H17V2H7v1.5h4.75l-.76 1.52" +
            "C7.86 5.11 5 5.73 5 8.5v8.44c0 1.45 1.19 2.66 2.59 2.97L6 21.5v.5h2.23l2-2H14l2 2h2v-.5" +
            "L16.5 20h-.08c1.69 0 2.58-1.37 2.58-3.06z" +
            "m-7 1.56c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" +
            "m5-4.5H7V9h10v5z"
    )
}

/** Bus body with two trolley poles reaching up to the wire. */
val TrolleybusIcon: ImageVector by lazy {
    glyph(
        "Trolleybus",
        // Body, slightly shorter than the bus to leave room for the poles
        "M6 6.5h12c1.1 0 2 .9 2 2V17c0 .74-.4 1.38-1 1.73V20c0 .55-.45 1-1 1h-1c-.55 0-1-.45-1-1v-1" +
            "H8v1c0 .55-.45 1-1 1H6c-.55 0-1-.45-1-1v-1.27C4.4 18.38 4 17.74 4 17V8.5c0-1.1.9-2 2-2z" +
            "m1.5 11c.83 0 1.5-.67 1.5-1.5S8.33 14.5 7.5 14.5 6 15.17 6 16s.67 1.5 1.5 1.5z" +
            "m9 0c.83 0 1.5-.67 1.5-1.5s-.67-1.5-1.5-1.5-1.5.67-1.5 1.5.67 1.5 1.5 1.5z" +
            "M6.5 8.5v4h11v-4h-11z",
        // Poles
        "M9.2 6.6 6.3 2.4l1-.7 2.9 4.2z",
        "M15.2 6.6 12.3 2.4l1-.7 2.9 4.2z",
        // Overhead wire
        "M3 1.2h18v1.1H3z"
    )
}

/** Metro car in a tunnel arch. */
val MetroIcon: ImageVector by lazy {
    glyph(
        "Metro",
        "M12 2C6.48 2 2 6.48 2 12v9c0 .55.45 1 1 1h2v-2H4v-8c0-4.41 3.59-8 8-8s8 3.59 8 8v8h-1v2h2" +
            "c.55 0 1-.45 1-1v-9c0-5.52-4.48-10-10-10z",
        "M8 7.5h8c1.1 0 2 .9 2 2V16c0 .74-.4 1.38-1 1.73V19.5c0 .28-.22.5-.5.5h-1c-.28 0-.5-.22-.5-.5V18H9v1.5" +
            "c0 .28-.22.5-.5.5h-1c-.28 0-.5-.22-.5-.5v-1.77C6.4 17.38 6 16.74 6 16V9.5c0-1.1.9-2 2-2z" +
            "m.5 9.5c.55 0 1-.45 1-1s-.45-1-1-1-1 .45-1 1 .45 1 1 1z" +
            "m7 0c.55 0 1-.45 1-1s-.45-1-1-1-1 .45-1 1 .45 1 1 1z" +
            "M8 9.5v4h8v-4H8z"
    )
}
