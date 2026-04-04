package ro.infotb.modern

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import ro.infotb.modern.ui.App
import ro.infotb.modern.ui.PendingStopNav

class MainActivity : ComponentActivity() {

    private val pendingNav = mutableStateOf<PendingStopNav?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)
        setContent {
            App(notificationNav = pendingNav)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
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
