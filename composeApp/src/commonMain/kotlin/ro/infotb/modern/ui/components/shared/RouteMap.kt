package ro.infotb.modern.ui.components.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition

data class LatLng(val lat: Double, val lng: Double)

@Composable
fun RouteMap(
    stops: List<Stop>,
    vehicles: List<VehiclePosition>,
    lineColor: Color,
    segmentPath: String?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Decode polyline points or fall back to stop coordinates
    val routePoints = remember(segmentPath, stops) {
        if (!segmentPath.isNullOrBlank()) {
            decodePolyline(segmentPath)
        } else {
            stops.map { LatLng(it.lat, it.lng) }
        }
    }

    // Compute bounding box from all points
    val allPoints = remember(routePoints, stops, vehicles) {
        routePoints +
                stops.map { LatLng(it.lat, it.lng) } +
                vehicles.map { LatLng(it.lat, it.lng) }
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    if (allPoints.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(0.5f, 5f)
                    panOffset += pan
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 24.dp.toPx()

            val minLat = allPoints.minOf { it.lat }
            val maxLat = allPoints.maxOf { it.lat }
            val minLng = allPoints.minOf { it.lng }
            val maxLng = allPoints.maxOf { it.lng }

            val latRange = (maxLat - minLat).coerceAtLeast(0.001)
            val lngRange = (maxLng - minLng).coerceAtLeast(0.001)

            val drawW = size.width - padding * 2
            val drawH = size.height - padding * 2

            // Keep aspect ratio
            val scale = minOf(drawW / lngRange, drawH / latRange) * zoom

            val centerLng = (minLng + maxLng) / 2
            val centerLat = (minLat + maxLat) / 2

            fun project(lat: Double, lng: Double): Offset {
                val x = ((lng - centerLng) * scale).toFloat() + size.width / 2 + panOffset.x
                val y = (-(lat - centerLat) * scale).toFloat() + size.height / 2 + panOffset.y
                return Offset(x, y)
            }

            // Draw route polyline
            if (routePoints.size >= 2) {
                val path = Path()
                val first = project(routePoints[0].lat, routePoints[0].lng)
                path.moveTo(first.x, first.y)
                for (i in 1 until routePoints.size) {
                    val p = project(routePoints[i].lat, routePoints[i].lng)
                    path.lineTo(p.x, p.y)
                }
                // Draw shadow
                drawPath(
                    path = path,
                    color = lineColor.copy(alpha = 0.15f),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Draw line
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw stops
            stops.forEachIndexed { index, stop ->
                val pos = project(stop.lat, stop.lng)
                val isTerminal = index == 0 || index == stops.lastIndex
                val radius = if (isTerminal) 6.dp.toPx() else 4.dp.toPx()

                // White border
                drawCircle(
                    color = Color.White,
                    radius = radius + 1.5.dp.toPx(),
                    center = pos
                )
                drawCircle(
                    color = lineColor,
                    radius = radius,
                    center = pos
                )

                // Label for first and last stop
                if (isTerminal) {
                    drawStopLabel(textMeasurer, stop.name, pos, size.width)
                }
            }

            // Draw vehicles
            vehicles.forEach { vehicle ->
                val pos = project(vehicle.lat, vehicle.lng)

                // Pulsing outer ring
                drawCircle(
                    color = Color(0xFF43A047).copy(alpha = 0.25f),
                    radius = 12.dp.toPx(),
                    center = pos
                )
                // White border
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = pos
                )
                // Green dot
                drawCircle(
                    color = Color(0xFF43A047),
                    radius = 5.dp.toPx(),
                    center = pos
                )
            }
        }
    }
}

private fun DrawScope.drawStopLabel(
    textMeasurer: TextMeasurer,
    name: String,
    position: Offset,
    canvasWidth: Float
) {
    val label = if (name.length > 18) name.take(16) + "..." else name
    val style = TextStyle(fontSize = 9.sp, color = Color.DarkGray)
    val measured = textMeasurer.measure(label, style)

    // Position label so it doesn't overflow the canvas
    val x = (position.x - measured.size.width / 2)
        .coerceIn(4f, canvasWidth - measured.size.width - 4f)
    val y = position.y + 10.dp.toPx()

    drawText(
        textLayoutResult = measured,
        topLeft = Offset(x, y)
    )
}

/**
 * Decodes a Google-encoded polyline string into a list of LatLng points.
 */
fun decodePolyline(encoded: String): List<LatLng> {
    val points = mutableListOf<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0

    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        points.add(LatLng(lat / 1e5, lng / 1e5))
    }
    return points
}
