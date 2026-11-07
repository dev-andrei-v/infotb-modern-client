package ro.infotb.modern.ui.components.shared

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** Set before loading tiles so every Android map opens in Bucharest. */
fun MapLibreMap.startInBucharest() {
    cameraPosition = CameraPosition.Builder()
        .target(LatLng(44.4268, 26.1025))
        .zoom(13.5)
        .build()
}

/** TextureView participates in Compose clipping and navigation transitions. */
fun createTransitMapView(context: Context): MapView {
    MapLibre.getInstance(context)
    return MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
}

@Composable
fun TransitMapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        var destroyed = false
        fun pause() {
            if (resumed) { mapView.onPause(); resumed = false }
        }
        fun stop() {
            pause()
            if (started) { mapView.onStop(); started = false }
        }
        fun destroy() {
            if (!destroyed) {
                mapView.visibility = View.INVISIBLE
                stop()
                mapView.onDestroy()
                destroyed = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (!destroyed) {
                when (event) {
                    Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                    Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                    Lifecycle.Event.ON_PAUSE -> pause()
                    Lifecycle.Event.ON_STOP -> stop()
                    Lifecycle.Event.ON_DESTROY -> destroy()
                    else -> Unit
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            destroy()
        }
    }
}
