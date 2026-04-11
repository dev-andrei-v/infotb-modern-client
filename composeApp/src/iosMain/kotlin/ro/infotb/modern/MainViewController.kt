package ro.infotb.modern

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
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
