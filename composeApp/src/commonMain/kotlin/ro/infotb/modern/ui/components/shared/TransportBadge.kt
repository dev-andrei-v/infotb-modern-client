package ro.infotb.modern.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.ui.theme.transportColor

@Composable
fun TransportBadge(
    type: TransportType,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val color = transportColor(type)
    val label = when (type) {
        TransportType.BUS -> "BUS"
        TransportType.TRAM -> "TRAM"
        TransportType.CABLE_CAR -> "TROLLEY"
        TransportType.SUBWAY -> "METRO"
    }
    val icon = when (type) {
        TransportType.BUS -> "\uD83D\uDE8C"
        TransportType.TRAM -> "\uD83D\uDE8B"
        TransportType.CABLE_CAR -> "\uD83D\uDE8E"
        TransportType.SUBWAY -> "\uD83D\uDE87"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 14.sp)
            if (showLabel) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
