package ro.infotb.modern.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.ui.theme.parseColor

enum class BadgeSize { SMALL, MEDIUM, LARGE }

@Composable
fun LineBadge(
    name: String,
    color: String,
    size: BadgeSize = BadgeSize.MEDIUM,
    modifier: Modifier = Modifier
) {
    val bgColor = try {
        parseColor(color)
    } catch (_: Exception) {
        Color(0xFF1E88E5)
    }

    val (fontSize, hPad, vPad, minWidth) = when (size) {
        BadgeSize.SMALL -> arrayOf(10f, 4f, 1f, 28f)
        BadgeSize.MEDIUM -> arrayOf(13f, 8f, 3f, 36f)
        BadgeSize.LARGE -> arrayOf(16f, 12f, 5f, 48f)
    }

    Box(
        modifier = modifier
            .widthIn(min = Dp(minWidth))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = Dp(hPad), vertical = Dp(vPad)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
