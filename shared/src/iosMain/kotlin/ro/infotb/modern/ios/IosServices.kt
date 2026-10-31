package ro.infotb.modern.ios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ro.infotb.modern.data.backup.BackupManager
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.LineArrivalDetail
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.DashboardStore
import ro.infotb.modern.data.store.FavoritesStore
import ro.infotb.modern.data.store.NotificationStore
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.notification.ArrivalTracker
import ro.infotb.modern.notification.NotificationEngine
import ro.infotb.modern.ui.viewmodel.AnnouncementsViewModel
import ro.infotb.modern.ui.viewmodel.DashboardViewModel
import ro.infotb.modern.ui.viewmodel.LineDetailViewModel
import ro.infotb.modern.ui.viewmodel.LinesViewModel
import ro.infotb.modern.ui.viewmodel.StopDetailViewModel

/**
 * The Koin graph as seen from Swift (`IosServices.shared`). Singletons are
 * exposed as properties; view models are created fresh, mirroring App.kt.
 */
object IosServices : KoinComponent {
    val preferencesStore: PreferencesStore get() = get()
    val dashboardStore: DashboardStore get() = get()
    val favoritesStore: FavoritesStore get() = get()
    val notificationStore: NotificationStore get() = get()
    val notificationEngine: NotificationEngine get() = get()
    val backupManager: BackupManager get() = get()
    val arrivalTracker: ArrivalTracker get() = get()

    fun dashboardViewModel() = DashboardViewModel(get(), get(), get(), get(), get())
    fun linesViewModel() = LinesViewModel(get(), get())
    fun lineDetailViewModel() = LineDetailViewModel(get())
    fun stopDetailViewModel() = StopDetailViewModel(get())
    fun announcementsViewModel() = AnnouncementsViewModel(get(), get())

    /**
     * One line's arrivals at one stop, for the live map's stop card. Returns
     * null instead of throwing, since a Kotlin exception would crash Swift.
     */
    suspend fun lineArrival(stopId: Long, lineId: Long, direction: Direction): LineArrivalDetail? = try {
        get<TransitRepository>().getStopInfo(stopId, lineId, direction)
            .lines.firstOrNull { it.id == lineId && it.direction == direction }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** A running flow subscription; Swift cancels it when the observing model goes away. */
class FlowSubscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/**
 * Calls [onEach] on the main thread for every value of [flow]. For a
 * StateFlow the current value is delivered synchronously, before this
 * returns, so SwiftUI never renders a placeholder first.
 */
fun watch(flow: Flow<*>, onEach: (Any?) -> Unit): FlowSubscription {
    val job = MainScope().launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
        flow.collect { onEach(it) }
    }
    return FlowSubscription(job)
}

/**
 * Owns a view model on behalf of a Swift object. [clear] runs the view
 * model's onCleared(), cancelling its viewModelScope and refresh loops,
 * just like leaving a Compose navigation destination does.
 */
class ViewModelOwner(viewModel: ViewModel) {
    private val store = ViewModelStore().apply { put("vm", viewModel) }

    fun clear() = store.clear()
}
