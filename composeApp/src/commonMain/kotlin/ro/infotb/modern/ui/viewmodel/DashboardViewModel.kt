package ro.infotb.modern.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.StopInfo
import ro.infotb.modern.data.model.TimetableHour
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.DashboardStore
import ro.infotb.modern.data.store.WidgetConfig
import ro.infotb.modern.data.store.WidgetSize
import ro.infotb.modern.util.Constants

/**
 * Per-stop state shown by a dashboard card.
 *
 * [info] keeps the last successful result even after a failed refresh so the
 * card never flashes back to a spinner; [error] and [lastUpdatedMs] let the UI
 * flag stale data instead.
 */
data class StopCardState(
    val info: StopInfo? = null,
    val lastUpdatedMs: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val hasData: Boolean get() = info != null
}

class DashboardViewModel(
    private val repository: TransitRepository,
    private val dashboardStore: DashboardStore
) : ViewModel() {

    val widgets: StateFlow<List<WidgetConfig>> = dashboardStore.widgets
    val groups: StateFlow<List<String>> = dashboardStore.groups
    val hiddenGroups: StateFlow<List<String>> = dashboardStore.hiddenGroups

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _cardStates = MutableStateFlow<Map<Long, StopCardState>>(emptyMap())
    val cardStates: StateFlow<Map<Long, StopCardState>> = _cardStates.asStateFlow()

    /** On-demand timetable cache: key = "stopId_lineId_direction" */
    private val _lineTimetables = MutableStateFlow<Map<String, List<TimetableHour>>>(emptyMap())
    val lineTimetables: StateFlow<Map<String, List<TimetableHour>>> = _lineTimetables.asStateFlow()

    private val _timetableLoading = MutableStateFlow<Set<String>>(emptySet())
    val timetableLoading: StateFlow<Set<String>> = _timetableLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Wall-clock time of the last completed full refresh, 0 if none yet. */
    private val _lastRefreshMs = MutableStateFlow(0L)
    val lastRefreshMs: StateFlow<Long> = _lastRefreshMs.asStateFlow()

    private val fetchSemaphore = Semaphore(Constants.DASHBOARD_MAX_PARALLEL_FETCHES)

    /** Stops currently shown on screen, derived from widget + group visibility. */
    private val visibleStopIds: StateFlow<Set<Long>> = combine(widgets, hiddenGroups) { ws, hidden ->
        ws.filter { !it.hidden && it.group !in hidden }.map { it.stopId }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var tickerJob: Job? = null
    private var isActive = false

    init {
        // React to widgets being added, removed, hidden or unhidden: fetch the
        // newcomers right away and drop state for stops that are gone.
        viewModelScope.launch {
            visibleStopIds.collect { ids ->
                val known = _cardStates.value.keys
                val removed = known - ids
                if (removed.isNotEmpty()) {
                    _cardStates.update { it - removed }
                }
                val missing = ids.filter { it !in known || !_cardStates.value.getValue(it).hasData }
                if (missing.isNotEmpty() && isActive) {
                    fetchStops(missing, force = false)
                }
            }
        }
    }

    /**
     * Called by the screen when it enters or leaves the composition so the
     * periodic refresh only runs while the dashboard is actually visible.
     */
    fun setScreenActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        if (active) startTicker() else stopTicker()
    }

    fun refreshNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchStops(visibleStopIds.value, force = true)
            _isRefreshing.value = false
            if (isActive) startTicker()
        }
    }

    fun toggleEditing() {
        _isEditing.value = !_isEditing.value
    }

    fun removeWidget(id: String) {
        dashboardStore.removeWidget(id)
    }

    fun clearAll() {
        dashboardStore.clearWidgets()
        _cardStates.value = emptyMap()
    }

    fun resizeWidget(id: String, size: WidgetSize) {
        dashboardStore.resizeWidget(id, size)
    }

    fun toggleWidgetHidden(id: String) {
        dashboardStore.toggleWidgetHidden(id)
    }

    fun toggleLineVisibility(id: String, lineKey: String) {
        dashboardStore.toggleLineVisibility(id, lineKey)
    }

    fun setWidgetGroup(id: String, group: String) {
        dashboardStore.setWidgetGroup(id, group)
    }

    fun addGroup(name: String) {
        dashboardStore.addGroup(name)
    }

    fun toggleGroupHidden(name: String) {
        dashboardStore.toggleGroupHidden(name)
    }

    fun loadLineTimetable(stopId: Long, lineId: Long, direction: Direction) {
        val key = "${stopId}_${lineId}_${direction.value}"
        if (_lineTimetables.value.containsKey(key)) return
        if (_timetableLoading.value.contains(key)) return

        viewModelScope.launch {
            _timetableLoading.value = _timetableLoading.value + key
            try {
                val info = repository.getStopInfo(stopId, lineId, direction)
                val timetable = info.lines
                    .firstOrNull { it.id == lineId && it.direction == direction }
                    ?.timetable ?: emptyList()
                _lineTimetables.value = _lineTimetables.value + (key to timetable)
            } catch (_: Exception) {
                _lineTimetables.value = _lineTimetables.value + (key to emptyList())
            } finally {
                _timetableLoading.value = _timetableLoading.value - key
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                fetchStops(visibleStopIds.value, force = false)
                delay(Constants.REFETCH_ARRIVALS_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Fetches every stop in [stopIds] concurrently (bounded by the semaphore)
     * and publishes each result as soon as it lands, so fast stops don't wait
     * for slow ones. A failure only marks that one card.
     */
    private suspend fun fetchStops(stopIds: Collection<Long>, force: Boolean) {
        if (stopIds.isEmpty()) return
        val ids = stopIds.distinct()

        _cardStates.update { states ->
            states + ids.associateWith { id -> (states[id] ?: StopCardState()).copy(isLoading = true) }
        }

        coroutineScope {
            for (stopId in ids) {
                launch {
                    fetchSemaphore.withPermit {
                        val maxAge = if (force) 0L else Constants.STOP_INFO_CACHE_MS
                        try {
                            val info = repository.getStopInfo(stopId, maxAgeMs = maxAge)
                            _cardStates.update { states ->
                                states + (stopId to StopCardState(
                                    info = info,
                                    lastUpdatedMs = currentTimeMillis(),
                                    isLoading = false,
                                    error = null
                                ))
                            }
                        } catch (e: Exception) {
                            println("Dashboard: failed to load stop $stopId: ${e.message}")
                            _cardStates.update { states ->
                                val previous = states[stopId] ?: StopCardState()
                                states + (stopId to previous.copy(
                                    isLoading = false,
                                    error = e.message ?: "error"
                                ))
                            }
                        }
                    }
                }
            }
        }
        _lastRefreshMs.value = currentTimeMillis()
    }

    override fun onCleared() {
        super.onCleared()
        stopTicker()
    }
}
