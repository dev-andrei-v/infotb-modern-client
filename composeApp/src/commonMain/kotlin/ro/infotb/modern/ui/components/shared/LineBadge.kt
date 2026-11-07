package ro.infotb.modern.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.ui.theme.parseColor
import ro.infotb.modern.util.isAirportLine
import ro.infotb.modern.util.isLightColor

enum class BadgeSize { SMALL, MEDIUM, LARGE }

@Composable
fun LineBadge(
    name: String,
    color: String,
    size: BadgeSize = BadgeSize.MEDIUM,
    modifier: Modifier = Modifier,
    isAirport: Boolean = isAirportLine(name)
) {
    val displayName = if (name.matches(Regex("(?i)^M\\d+\\s*-.*"))) name.substringBefore('-').trim() else name
    val bgColor = try {
        parseColor(color)
    } catch (_: Exception) {
        Color(0xFF1E88E5)
    }

    val (fontSize, hPad, vPad, minWidth, iconSize) = when (size) {
        BadgeSize.SMALL -> arrayOf(10f, 4f, 1f, 28f, 10f)
        BadgeSize.MEDIUM -> arrayOf(13f, 8f, 3f, 36f, 12f)
        BadgeSize.LARGE -> arrayOf(16f, 12f, 5f, 48f, 15f)
    }
    val contentColor = if (isLightColor(color)) Color.Black else Color.White

    Box(
        modifier = modifier
            .widthIn(min = Dp(minWidth))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = Dp(hPad), vertical = Dp(vPad)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isAirport) {
                Icon(
                    imageVector = AirplaneIcon,
                    contentDescription = "Airport",
                    tint = contentColor,
                    modifier = Modifier.size(Dp(iconSize)).rotate(45f)
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = displayName,
                color = contentColor,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
