package ro.infotb.modern

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import io.github.vinceglb.filekit.core.FileKit
import ro.infotb.modern.location.AndroidLocationPermission
import ro.infotb.modern.ui.App
import ro.infotb.modern.ui.PendingStopNav
import ro.infotb.modern.ui.PendingQuickAction
import ro.infotb.modern.ui.widget.TransitAppShortcuts

class MainActivity : ComponentActivity() {

    private val pendingNav = mutableStateOf<PendingStopNav?>(null)
    private val pendingQuickAction = mutableStateOf<PendingQuickAction?>(null)
    private var quickActionRequestId = 0

    private var notificationPermissionInFlight = false
    private var locationPermissionInFlight = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationPermissionInFlight = false
        launchPendingLocationPermission()
    }

    private val locationPermissionCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val requestLocationPermission: ((Boolean) -> Unit) -> Unit = { onResult ->
        locationPermissionCallbacks.add(onResult)
        launchPendingLocationPermission()
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        locationPermissionInFlight = false
        val granted = result.values.any { it }
        val callbacks = locationPermissionCallbacks.toList()
        locationPermissionCallbacks.clear()
        callbacks.forEach { it(granted) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        AndroidLocationPermission.requester = requestLocationPermission
        enableEdgeToEdge()
        requestNotificationPermission()
        requestBatteryOptimizationExemption()
        handleNotificationIntent(intent)
        handleQuickActionIntent(intent)
        TransitAppShortcuts.refresh(this)
        setContent {
            App(notificationNav = pendingNav, quickActionNav = pendingQuickAction)
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        launchPendingLocationPermission()
    }

    private fun launchPendingLocationPermission() {
        // Startup also opens notification/battery dialogs. Wait until this
        // Activity is visible and its other runtime permission request is done.
        if (locationPermissionCallbacks.isEmpty() || locationPermissionInFlight ||
            notificationPermissionInFlight || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        locationPermissionInFlight = true
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    override fun onDestroy() {
        if (AndroidLocationPermission.requester === requestLocationPermission) {
            AndroidLocationPermission.requester = null
        }
        val callbacks = locationPermissionCallbacks.toList()
        locationPermissionCallbacks.clear()
        callbacks.forEach { it(false) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
        handleQuickActionIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionInFlight = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val stopId = intent?.getLongExtra("navigate_stop_id", -1L) ?: -1L
        if (stopId >= 0) {
            val lineId = intent?.getLongExtra("navigate_line_id", -1L) ?: -1L
            val direction = intent?.getIntExtra("navigate_direction", -1) ?: -1
            pendingNav.value = PendingStopNav(stopId, lineId, direction)
        }
    }

    private fun handleQuickActionIntent(intent: Intent?) {
        val sourceIntent = intent ?: return
        val action = when {
            sourceIntent.getBooleanExtra(ro.infotb.modern.ui.widget.TransitGroupAppWidget.EXTRA_REFRESH_WIDGET, false) -> "refresh_dashboard"
            sourceIntent.hasExtra(ro.infotb.modern.ui.widget.TransitGroupAppWidget.EXTRA_OPEN_GROUP) -> "open_dashboard_group"
            else -> sourceIntent.getStringExtra(TransitAppShortcuts.ACTION_KEY)
        } ?: return
        val destinationJson = sourceIntent.getStringExtra(TransitAppShortcuts.DESTINATION_KEY)
            ?: sourceIntent.getStringExtra(ro.infotb.modern.ui.widget.TransitGroupAppWidget.EXTRA_OPEN_GROUP)
        if (action == "plan_destination") {
            TransitAppShortcuts.recordDestinationUse(
                this,
                sourceIntent.getStringExtra(TransitAppShortcuts.TRIP_ID_KEY).orEmpty()
            )
        }
        quickActionRequestId += 1
        pendingQuickAction.value = PendingQuickAction(action, destinationJson, quickActionRequestId)
    }
}
