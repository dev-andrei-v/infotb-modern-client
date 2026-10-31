package ro.infotb.modern.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import ro.infotb.modern.di.sharedModule
import ro.infotb.modern.notification.NoticeWatcher
import ro.infotb.modern.notification.NotificationEngine

/**
 * Entry points the Swift app calls at launch. Swift sees them as
 * `IosAppKt.initKoin()` and friends.
 */
fun initKoin() {
    startKoin {
        modules(sharedModule)
    }
    // Arrival polling runs for the whole life of the process, as it did
    // under the Compose UI, so the Dynamic Island keeps updating.
    KoinPlatform.getKoin().get<NotificationEngine>().start()
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
