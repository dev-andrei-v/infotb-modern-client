package ro.infotb.modern.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.infotb.modern.data.model.Announcement
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.LineDetail
import ro.infotb.modern.data.model.VehiclePosition
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.util.Constants

class LineDetailViewModel(
    private val repository: TransitRepository
) : ViewModel() {

    private val _lineDetail = MutableStateFlow<LineDetail?>(null)
    val lineDetail: StateFlow<LineDetail?> = _lineDetail.asStateFlow()

    private val _vehicles = MutableStateFlow<List<VehiclePosition>>(emptyList())
    val vehicles: StateFlow<List<VehiclePosition>> = _vehicles.asStateFlow()

    private val _direction = MutableStateFlow(Direction.TUR)
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    private var vehicleRefreshJob: Job? = null
    private var currentLineId: Long? = null

    fun loadLine(lineId: Long) {
        currentLineId = lineId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val detail = repository.getLineDirection(lineId, _direction.value)
                _lineDetail.value = detail
                loadVehicles(lineId)
                loadAnnouncementsForLine(lineId)
            } catch (e: Exception) {
                _lineDetail.value = null
            } finally {
                _isLoading.value = false
            }
        }
        startVehicleRefresh(lineId)
    }

    fun setDirection(direction: Direction) {
        _direction.value = direction
        val lineId = currentLineId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val detail = repository.getLineDirection(lineId, direction)
                _lineDetail.value = detail
                loadVehicles(lineId)
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadVehicles(lineId: Long) {
        try {
            val v = repository.getVehicles(lineId, _direction.value)
            _vehicles.value = v
        } catch (_: Exception) {
            _vehicles.value = emptyList()
        }
    }

    private fun loadAnnouncementsForLine(lineId: Long) {
        viewModelScope.launch {
            try {
                val all = repository.getAnnouncements()
                _announcements.value = all.filter { announcement ->
                    announcement.lines.any { it.id == lineId }
                }
            } catch (_: Exception) {
                _announcements.value = emptyList()
            }
        }
    }

    private fun startVehicleRefresh(lineId: Long) {
        vehicleRefreshJob?.cancel()
        vehicleRefreshJob = viewModelScope.launch {
            while (true) {
                delay(Constants.REFETCH_VEHICLES_MS)
                loadVehicles(lineId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vehicleRefreshJob?.cancel()
    }
}
