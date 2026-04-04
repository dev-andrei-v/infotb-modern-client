package ro.infotb.modern.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.StopInfo
import ro.infotb.modern.data.model.TimetableHour
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.DashboardStore
import ro.infotb.modern.data.store.WidgetConfig
import ro.infotb.modern.data.store.WidgetSize
import ro.infotb.modern.util.Constants

class DashboardViewModel(
    private val repository: TransitRepository,
    private val dashboardStore: DashboardStore
) : ViewModel() {

    val widgets: StateFlow<List<WidgetConfig>> = dashboardStore.widgets
    val groups: StateFlow<List<String>> = dashboardStore.groups
    val hiddenGroups: StateFlow<List<String>> = dashboardStore.hiddenGroups

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _widgetData = MutableStateFlow<Map<Long, StopInfo>>(emptyMap())
    val widgetData: StateFlow<Map<Long, StopInfo>> = _widgetData.asStateFlow()

    /** On-demand timetable cache: key = "stopId_lineId_direction" */
    private val _lineTimetables = MutableStateFlow<Map<String, List<TimetableHour>>>(emptyMap())
    val lineTimetables: StateFlow<Map<String, List<TimetableHour>>> = _lineTimetables.asStateFlow()

    private val _timetableLoading = MutableStateFlow<Set<String>>(emptySet())
    val timetableLoading: StateFlow<Set<String>> = _timetableLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var refreshJob: Job? = null

    init {
        startWidgetDataRefresh()
    }

    fun refreshNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadAllWidgetData()
            _isRefreshing.value = false
            // Reset the periodic timer
            startWidgetDataRefresh()
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
        _widgetData.value = emptyMap()
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

    private fun startWidgetDataRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                loadAllWidgetData()
                delay(Constants.REFETCH_ARRIVALS_MS)
            }
        }
    }

    private suspend fun loadAllWidgetData() {
        val currentWidgets = widgets.value
        val currentHiddenGroups = hiddenGroups.value
        // Only fetch data for visible widgets (not hidden individually or by group)
        val visibleWidgets = currentWidgets.filter { w ->
            !w.hidden && w.group !in currentHiddenGroups
        }
        val stopIds = visibleWidgets.map { it.stopId }.distinct()
        val dataMap = _widgetData.value.toMutableMap()

        for (stopId in stopIds) {
            try {
                val info = repository.getStopInfo(stopId)
                dataMap[stopId] = info
            } catch (e: Exception) {
                println("Dashboard: failed to load stop $stopId: ${e.message}")
            }
        }
        _widgetData.value = dataMap
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
