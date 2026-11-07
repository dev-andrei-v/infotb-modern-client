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

val DashboardGridFilledIcon: ImageVector by lazy {
    navGlyph(
        "DashboardGridFilled",
        "M3 5c0-1.1.9-2 2-2h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2H5c-1.1 0-2-.9-2-2V5zm10 0c0-1.1.9-2 2-2h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2h-4c-1.1 0-2-.9-2-2V5zm-10 10c0-1.1.9-2 2-2h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2H5c-1.1 0-2-.9-2-2v-4zm10 0c0-1.1.9-2 2-2h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2h-4c-1.1 0-2-.9-2-2v-4z"
    )
}

val DashboardGridOutlinedIcon: ImageVector by lazy {
    navGlyph(
        "DashboardGridOutlined",
        "M5 3h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2H5c-1.1 0-2-.9-2-2V5c0-1.1.9-2 2-2zm0 2v4h4V5H5zm10-2h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2h-4c-1.1 0-2-.9-2-2V5c0-1.1.9-2 2-2zm0 2v4h4V5h-4zM5 13h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2H5c-1.1 0-2-.9-2-2v-4c0-1.1.9-2 2-2zm0 2v4h4v-4H5zm10-2h4c1.1 0 2 .9 2 2v4c0 1.1-.9 2-2 2h-4c-1.1 0-2-.9-2-2v-4c0-1.1.9-2 2-2zm0 2v4h4v-4h-4z"
    )
}

val LinesRouteFilledIcon: ImageVector by lazy {
    navGlyph(
        "LinesRouteFilled",
        "M19 15.18V7c0-2.21-1.79-4-4-4s-4 1.79-4 4v10c0 1.1-.9 2-2 2s-2-.9-2-2V8.82C8.16 8.4 9 7.3 9 6c0-1.66-1.34-3-3-3S3 4.34 3 6c0 1.3.84 2.4 2 2.82V17c0 2.21 1.79 4 4 4s4-1.79 4-4V7c0-1.1.9-2 2-2s2 .9 2 2v8.18c-1.16.41-2 1.51-2 2.82 0 1.66 1.34 3 3 3s3-1.34 3-3c0-1.31-.84-2.41-2-2.82z"
    )
}

val LinesRouteOutlinedIcon: ImageVector by lazy {
    navGlyph(
        "LinesRouteOutlined",
        "M19 15.18V7c0-2.21-1.79-4-4-4s-4 1.79-4 4v10c0 1.1-.9 2-2 2s-2-.9-2-2V8.82C8.16 8.4 9 7.3 9 6c0-1.66-1.34-3-3-3S3 4.34 3 6c0 1.3.84 2.4 2 2.82V17c0 2.21 1.79 4 4 4s4-1.79 4-4V7c0-1.1.9-2 2-2s2 .9 2 2v8.18c-1.16.41-2 1.51-2 2.82 0 1.66 1.34 3 3 3s3-1.34 3-3c0-1.31-.84-2.41-2-2.82zm-13-8c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm12 12c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z"
    )
}

val PlannerSwapFilledIcon: ImageVector by lazy {
    navGlyph(
        "PlannerSwapFilled",
        "M4 7h11.17l-2.58-2.59L14 3l6 6-6 6-1.41-1.41L15.17 11H4V7zm16 10H8.83l2.58 2.59L10 21l-6-6 6-6 1.41 1.41L8.83 15H20v2z"
    )
}

val PlannerSwapOutlinedIcon: ImageVector by lazy {
    navGlyph(
        "PlannerSwapOutlined",
        "M4 7h11.17l-2.58-2.59L14 3l6 6-6 6-1.41-1.41L15.17 11H4V7zm16 10H8.83l2.58 2.59L10 21l-6-6 6-6 1.41 1.41L8.83 15H20v2z"
    )
}

val LocationSearchingIcon: ImageVector by lazy {
    navGlyph(
        "LocationSearching",
        "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"
    )
}

val LocationOffIcon: ImageVector by lazy {
    navGlyph(
        "LocationOff",
        "M12 6.5c1.38 0 2.5 1.12 2.5 2.5 0 .74-.33 1.39-.83 1.85l3.63 3.63c.98-1.86 1.7-3.8 1.7-5.48 0-3.87-3.13-7-7-7-1.98 0-3.76.83-5.04 2.15l3.19 3.19c.46-.51 1.11-.84 1.85-.84zm1.15 11.23l-3.3-3.3c-.02-.03-.04-.06-.06-.09-1.39-1.9-2.79-4.31-2.79-5.84 0-.31.04-.61.1-.91L2.1 2.59 0.69 4l2.85 2.85C3.21 7.57 3 8.27 3 9c0 5.25 7 13 7 13s2.32-2.56 4.31-5.74l5.14 5.14 1.41-1.41-7.71-7.76z"
    )
}
