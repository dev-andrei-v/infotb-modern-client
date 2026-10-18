package ro.infotb.modern

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import ro.infotb.modern.notification.NoticeWatcher
import ro.infotb.modern.di.sharedModule
import ro.infotb.modern.ui.App
import ro.infotb.modern.ui.PendingStopNav

private val pendingNav = mutableStateOf<PendingStopNav?>(null)

fun MainViewController() = ComposeUIViewController {
    App(notificationNav = pendingNav)
}

fun handleIncomingNavigation(stopId: Long, lineId: Long, direction: Int) {
    pendingNav.value = PendingStopNav(stopId, lineId, direction)
}

fun initKoin() {
    startKoin {
        modules(sharedModule)
    }
}

/**
 * Called by the iOS background refresh task: fetches STB notices and raises a
 * notification for new ones about followed lines. [onDone] runs on the main
 * thread with true when something was announced.
 */
fun checkNoticesInBackground(onDone: (Boolean) -> Unit) {
    val watcher = KoinPlatform.getKoin().get<NoticeWatcher>()
    CoroutineScope(Dispatchers.Default).launch {
        val announced = try { watcher.checkNow() } catch (_: Exception) { false }
        withContext(Dispatchers.Main) { onDone(announced) }
    }
}
