package ro.infotb.modern.ui.components.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

private const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/bright"

@Composable
actual fun EmbeddedMapView(
    lat: Double,
    lng: Double,
    label: String,
    zoom: Int,
    modifier: Modifier
) {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }

    val mapView = remember {
        createTransitMapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                mapInstance = map
                map.startInBucharest()
                map.setStyle(OPENFREEMAP_STYLE) {
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    styleLoaded = true
                }
            }
        }
    }

    // Update camera and marker if coordinates change
    LaunchedEffect(lat, lng, zoom, label, styleLoaded) {
        if (!styleLoaded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val target = LatLng(lat, lng)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom.toDouble()))
        currentMarker?.let { map.removeMarker(it) }
        val iconFactory = IconFactory.getInstance(context)
        val icon = iconFactory.fromBitmap(createStopPinBitmap())
        currentMarker = map.addMarker(
            MarkerOptions()
                .position(target)
                .title(label)
                .icon(icon)
        )
        currentMarker?.showInfoWindow(map, mapView)
    }

    TransitMapLifecycle(mapView)

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

private fun createStopPinBitmap(): Bitmap {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f + 2f, size / 2f - 4f, shadowPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C92335")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, strokePaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 8f, dotPaint)

    return bitmap
}
