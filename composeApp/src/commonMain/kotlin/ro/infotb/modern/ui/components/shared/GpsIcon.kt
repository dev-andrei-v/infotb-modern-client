package ro.infotb.modern.ui.components.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A signal/wireless-style icon representing live GPS tracking.
 */
val GpsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GpsSignal",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Center dot
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            // Circle at center
            moveTo(12f, 14f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14f, 12f)
            arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 14f)
            close()
        }
        // Inner arc
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(8.46f, 15.54f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 12f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.46f, 8.46f)
            lineTo(9.88f, 9.88f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.88f, 14.12f)
            close()
        }
        // Inner arc right
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(15.54f, 8.46f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 12f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.54f, 15.54f)
            lineTo(14.12f, 14.12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 12f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 14.12f, 9.88f)
            close()
        }
        // Outer arc left
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(5.64f, 18.36f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.64f, 5.64f)
            lineTo(7.05f, 7.05f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5f, 12f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.05f, 16.95f)
            close()
        }
        // Outer arc right
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(18.36f, 5.64f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.36f, 18.36f)
            lineTo(16.95f, 16.95f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19f, 12f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16.95f, 7.05f)
            close()
        }
    }.build()
}
