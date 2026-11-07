package ro.infotb.modern.ui.components.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.VehiclePosition
import ro.infotb.modern.util.decodePolyline

private const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/bright"

@Composable
actual fun LeafletRouteMap(
    stops: List<Stop>,
    vehicles: List<VehiclePosition>,
    lineColor: String,
    segmentPath: String?,
    modifier: Modifier
) {
    val context = LocalContext.current
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapStyleLoaded by remember { mutableStateOf(false) }

    val parsedColor = remember(lineColor) { parseLineColor(lineColor) }
    val routeCoords = remember(segmentPath, stops) {
        if (!segmentPath.isNullOrBlank()) {
            decodePolyline(segmentPath).map { LatLng(it.first, it.second) }
        } else {
            stops.map { LatLng(it.lat, it.lng) }
        }
    }

    val polylinesRef = remember { mutableListOf<Polyline>() }
    val stopMarkersRef = remember { mutableListOf<Marker>() }
    val vehicleMarkersRef = remember { mutableListOf<Marker>() }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                mapInstance = map
                map.setStyle(OPENFREEMAP_STYLE) {
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    isMapStyleLoaded = true
                }
            }
        }
    }

    // Render route polylines and stop markers when route/stops change
    LaunchedEffect(isMapStyleLoaded, routeCoords, stops, parsedColor) {
        if (!isMapStyleLoaded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val iconFactory = IconFactory.getInstance(context)

        // Clear previous polylines & stop markers
        polylinesRef.forEach { map.removePolyline(it) }
        polylinesRef.clear()
        stopMarkersRef.forEach { map.removeMarker(it) }
        stopMarkersRef.clear()

        // 1. Dual-stroke polyline (white casing + colored core)
        if (routeCoords.size >= 2) {
            val casing = map.addPolyline(
                PolylineOptions()
                    .addAll(routeCoords)
                    .color(Color.WHITE)
                    .width(7f)
            )
            val core = map.addPolyline(
                PolylineOptions()
                    .addAll(routeCoords)
                    .color(parsedColor)
                    .width(4.5f)
            )
            polylinesRef.add(casing)
            polylinesRef.add(core)
        }

        // 2. Stop markers
        val terminalIcon = iconFactory.fromBitmap(createRouteStopMarkerBitmap(isTerminal = true, lineColor = parsedColor))
        val regularIcon = iconFactory.fromBitmap(createRouteStopMarkerBitmap(isTerminal = false, lineColor = parsedColor))

        stops.forEachIndexed { index, stop ->
            val isTerminal = index == 0 || index == stops.size - 1
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.lat, stop.lng))
                    .title(stop.name)
                    .icon(if (isTerminal) terminalIcon else regularIcon)
            )
            stopMarkersRef.add(marker)
        }

        // 3. Fit bounds to route / stops
        val allPoints = if (routeCoords.isNotEmpty()) routeCoords else stops.map { LatLng(it.lat, it.lng) }
        if (allPoints.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            allPoints.forEach { boundsBuilder.include(it) }
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 90))
            } catch (_: Exception) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(allPoints.first(), 13.5))
            }
        }
    }

    // Realtime vehicle markers update
    LaunchedEffect(isMapStyleLoaded, vehicles) {
        if (!isMapStyleLoaded) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val iconFactory = IconFactory.getInstance(context)

        // Clear previous vehicle markers
        vehicleMarkersRef.forEach { map.removeMarker(it) }
        vehicleMarkersRef.clear()

        if (vehicles.isEmpty()) return@LaunchedEffect

        val vehicleIcon = iconFactory.fromBitmap(createVehicleMarkerBitmap())
        vehicles.forEach { vehicle ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(vehicle.lat, vehicle.lng))
                    .title("Vehicle ${vehicle.code ?: vehicle.id}")
                    .icon(vehicleIcon)
            )
            vehicleMarkersRef.add(marker)
        }
    }

    // Forward Android lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

private fun createRouteStopMarkerBitmap(isTerminal: Boolean, lineColor: Int): Bitmap {
    val size = if (isTerminal) 42 else 28
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f + 1.5f, size / 2f - 2f, shadowPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = if (isTerminal) 3.5f else 2.5f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, strokePaint)

    if (isTerminal) {
        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 4.5f, innerDotPaint)
    }

    return bitmap
}

private fun createVehicleMarkerBitmap(): Bitmap {
    val size = 52
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f + 2f, size / 2f - 4f, shadowPaint)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // Realtime green
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5f, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5f, strokePaint)

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, 6.5f, innerPaint)

    return bitmap
}

private fun parseLineColor(hex: String?, fallback: Int = Color.parseColor("#1565C0")): Int {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        Color.parseColor(clean)
    } catch (_: Exception) {
        fallback
    }
}
