package ro.infotb.modern.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.ui.theme.RealtimeColor
import ro.infotb.modern.ui.theme.ScheduledColor
import ro.infotb.modern.util.formatArrivalBadge

/** Minimum width of the big arrival badge so "<1 min" and "45 min" rows keep their columns aligned. */
val HERO_ARRIVAL_MIN_WIDTH = 92.dp

@Composable
fun ArrivalTimeBadge(
    seconds: Long,
    isTimetable: Boolean = false,
    size: BadgeSize = BadgeSize.MEDIUM,
    modifier: Modifier = Modifier
) {
    val color = if (isTimetable) ScheduledColor else RealtimeColor
    val text = formatArrivalBadge(seconds)
    var showTooltip by remember { mutableStateOf(false) }

    val (fontSize, hPad, vPad, iconSize) = when (size) {
        BadgeSize.SMALL -> Quad(10f, 4f, 1f, 10f)
        BadgeSize.MEDIUM -> Quad(13f, 8f, 3f, 13f)
        BadgeSize.LARGE -> Quad(15f, 10f, 5f, 15f)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable { showTooltip = true }
            .padding(horizontal = hPad.dp, vertical = vPad.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isTimetable) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(iconSize.dp)
            )
        } else {
            Icon(
                GpsIcon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(iconSize.dp)
            )
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }

    if (showTooltip) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            title = {
                Text(
                    if (isTimetable) "Scheduled" else "Live GPS"
                )
            },
            text = {
                Text(
                    if (isTimetable)
                        "This arrival time is based on the scheduled timetable, not real-time tracking."
                    else
                        "This arrival time is based on live GPS tracking of the vehicle."
                )
            },
            confirmButton = {
                TextButton(onClick = { showTooltip = false }) {
                    Text("OK")
                }
            }
        )
    }
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
