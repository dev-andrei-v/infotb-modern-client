package ro.infotb.modern

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import ro.infotb.modern.ui.App
import ro.infotb.modern.ui.PendingStopNav

class MainActivity : ComponentActivity() {

    private val pendingNav = mutableStateOf<PendingStopNav?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, we continue */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        handleNotificationIntent(intent)
        setContent {
            App(notificationNav = pendingNav)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
}
