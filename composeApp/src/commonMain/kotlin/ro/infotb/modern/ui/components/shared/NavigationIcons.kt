package ro.infotb.modern.ui.components.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun navGlyph(name: String, vararg pathData: String): ImageVector =
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

val MapFilledIcon: ImageVector by lazy {
    navGlyph(
        "MapFilled",
        "M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z"
    )
}

val MapOutlinedIcon: ImageVector by lazy {
    navGlyph(
        "MapOutlined",
        "M15 5.1L9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5l-.16.03L15 5.1zM5 6.48l3-1.05v11.99l-3 1.05V6.48zm5-1.07l4 1.4v12l-4-1.4V5.41zm9 13.11l-3 1.01V7.53l3-1.01v12z"
    )
}

val PlannerFilledIcon: ImageVector by lazy {
    navGlyph(
        "PlannerFilled",
        "M21.41 10.59l-7.99-8c-.78-.78-2.05-.78-2.83 0l-8 8c-.78.78-.78 2.05 0 2.83l8 8c.78.78 2.05.78 2.83 0l7.99-8c.79-.79.79-2.05 0-2.83zM13.5 14.5V12H10v3H8v-4c0-.55.45-1 1-1h4.5V7.5L17 11l-3.5 3.5z"
    )
}

val PlannerOutlinedIcon: ImageVector by lazy {
    navGlyph(
        "PlannerOutlined",
        "M21.41 10.59l-7.99-8c-.78-.78-2.05-.78-2.83 0l-8 8c-.78.78-.78 2.05 0 2.83l8 8c.78.78 2.05.78 2.83 0l7.99-8c.79-.79.79-2.05 0-2.83zm-8.82 9.42l-8-8 8-8 8 8-8 8zm-.59-5.51L15.5 11 12 7.5V10H9c-.55 0-1 .45-1 1v4h2v-3h2v2.5z"
    )
}
