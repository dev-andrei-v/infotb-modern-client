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
import ro.infotb.modern.data.model.Stop
import ro.infotb.modern.data.model.StopInfo
import ro.infotb.modern.data.model.TimetableHour
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.util.Constants

class StopDetailViewModel(
    private val repository: TransitRepository
) : ViewModel() {

    private val _stopInfo = MutableStateFlow<StopInfo?>(null)
    val stopInfo: StateFlow<StopInfo?> = _stopInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Timetables fetched on-demand for non-primary lines: key = "lineId_directionValue" */
    private val _lineTimetables = MutableStateFlow<Map<String, List<TimetableHour>>>(emptyMap())
    val lineTimetables: StateFlow<Map<String, List<TimetableHour>>> = _lineTimetables.asStateFlow()

    private val _timetableLoading = MutableStateFlow<Set<String>>(emptySet())
    val timetableLoading: StateFlow<Set<String>> = _timetableLoading.asStateFlow()

    /** Stop coordinates loaded from homeStops API */
    private val _stopCoordinates = MutableStateFlow<Stop?>(null)
    val stopCoordinates: StateFlow<Stop?> = _stopCoordinates.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var refreshJob: Job? = null
    private var currentStopId: Long? = null
    private var currentLineId: Long? = null
    private var currentDirection: Direction? = null

    fun loadStop(stopId: Long, lineId: Long? = null, direction: Direction? = null) {
        currentStopId = stopId
        currentLineId = lineId
        currentDirection = direction
        fetchStop(stopId, lineId, direction, showLoading = true)
        fetchStopCoordinates(stopId)
        startRefresh()
    }

    fun refreshNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val stopId = currentStopId ?: run { _isRefreshing.value = false; return@launch }
            fetchStop(stopId, currentLineId, currentDirection, showLoading = false)
            _isRefreshing.value = false
            startRefresh()
        }
    }

    private fun fetchStopCoordinates(stopId: Long) {
        viewModelScope.launch {
            try {
                val stops = repository.getHomeStops(stopId)
                _stopCoordinates.value = stops.firstOrNull { it.id == stopId }
                    ?: stops.firstOrNull()
            } catch (_: Exception) {
                // coordinates are non-critical
            }
        }
    }

    /**
     * Load timetable for a specific line at the current stop.
     * Called when user expands timetable for a non-primary line.
     */
    fun loadLineTimetable(lineId: Long, direction: Direction) {
        val stopId = currentStopId ?: return
        val key = "${lineId}_${direction.value}"
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

    private fun fetchStop(stopId: Long, lineId: Long?, direction: Direction?, showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) _isLoading.value = true
            try {
                val info = repository.getStopInfo(stopId, lineId, direction)
                _stopInfo.value = info
            } catch (_: Exception) {
                if (showLoading) _stopInfo.value = null
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }

    private fun startRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(Constants.REFETCH_ARRIVALS_MS)
                val stopId = currentStopId ?: break
                fetchStop(stopId, currentLineId, currentDirection, showLoading = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
