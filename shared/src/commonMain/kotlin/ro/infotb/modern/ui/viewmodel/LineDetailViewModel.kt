package ro.infotb.modern.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import ro.infotb.modern.data.model.Announcement
import ro.infotb.modern.data.model.Direction
import ro.infotb.modern.data.model.LineDetail
import ro.infotb.modern.data.model.VehiclePosition
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.PreferencesStore
import ro.infotb.modern.util.Constants
import ro.infotb.modern.location.PlatformLocation
import ro.infotb.modern.location.GeoPoint
import ro.infotb.modern.data.model.LineArrivalDetail
import ro.infotb.modern.currentTimeMillis
import ro.infotb.modern.util.recentLineNews

class LineDetailViewModel(
    private val repository: TransitRepository,
    private val preferencesStore: PreferencesStore,
    private val platformLocation: PlatformLocation = PlatformLocation()
) : ViewModel() {

    val lineDisplayStyle = preferencesStore.lineDisplayStyle
    val nearbyStopCount = preferencesStore.nearbyStopCount
    val nearbyStopRadius = preferencesStore.nearbyStopRadius
    private val _userLocation = MutableStateFlow<GeoPoint?>(null)
    val userLocation = _userLocation.asStateFlow()
    private val _locationLoading = MutableStateFlow(false)
    val locationLoading = _locationLoading.asStateFlow()

    fun refreshLocation() {
        if (_locationLoading.value || !platformLocation.isSupported()) return
        viewModelScope.launch {
            _locationLoading.value = true
            try {
                _userLocation.value = platformLocation.current()
            } finally {
                _locationLoading.value = false
            }
        }
    }

    suspend fun arrivalAt(stopId: Long): LineArrivalDetail? {
        val id = currentLineId ?: return null
        val direction = _direction.value
        return try {
            repository.getStopInfo(stopId, id, direction).lines.firstOrNull { it.id == id && it.direction == direction }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private val _lineDetail = MutableStateFlow<LineDetail?>(null)
    val lineDetail: StateFlow<LineDetail?> = _lineDetail.asStateFlow()

    private val _vehicles = MutableStateFlow<List<VehiclePosition>>(emptyList())
    val vehicles: StateFlow<List<VehiclePosition>> = _vehicles.asStateFlow()

    private val _isRefreshingVehicles = MutableStateFlow(false)
    val isRefreshingVehicles: StateFlow<Boolean> = _isRefreshingVehicles.asStateFlow()

    private val _direction = MutableStateFlow(Direction.TUR)
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _announcements = MutableStateFlow(repository.getCachedAnnouncements().orEmpty())
    private val noticeTime = MutableStateFlow(currentTimeMillis())
    val announcements: StateFlow<List<Announcement>> = combine(_announcements, _lineDetail, preferencesStore.dismissedAnnouncementIds, noticeTime) { notices, line, dismissed, now ->
        recentLineNews(notices, line, dismissed, now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun dismissAnnouncement(id: Long) = preferencesStore.dismissAnnouncement(id)

    private var vehicleRefreshJob: Job? = null
    private var lineLoadJob: Job? = null
    private var currentLineId: Long? = null

    fun loadLine(lineId: Long) {
        currentLineId = lineId
        lineLoadJob?.cancel()
        lineLoadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val detail = repository.getLineDirection(lineId, _direction.value)
                _lineDetail.value = detail
                loadVehicles(lineId)
                loadAnnouncementsForLine(lineId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lineDetail.value = null
            } finally {
                _isLoading.value = false
            }
        }
        startVehicleRefresh(lineId)
    }

    fun setDirection(direction: Direction) {
        if (_direction.value == direction) return
        _direction.value = direction
        _vehicles.value = emptyList()
        val lineId = currentLineId ?: return
        lineLoadJob?.cancel()
        lineLoadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val detail = repository.getLineDirection(lineId, direction)
                _lineDetail.value = detail
                loadVehicles(lineId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshVehicles() {
        val lineId = currentLineId ?: return
        viewModelScope.launch {
            _isRefreshingVehicles.value = true
            try {
                loadVehicles(lineId)
            } finally {
                _isRefreshingVehicles.value = false
            }
        }
    }

    private suspend fun loadVehicles(lineId: Long) {
        val expectedDirection = _direction.value
        try {
            val v = repository.getVehicles(lineId, expectedDirection)
            if (currentLineId == lineId && _direction.value == expectedDirection) _vehicles.value = v
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the last known positions when a refresh fails.
        }
    }

    private fun loadAnnouncementsForLine(lineId: Long) {
        viewModelScope.launch {
            try {
                val all = repository.getAnnouncements()
                _announcements.value = all
                noticeTime.value = currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep cached recent notices while offline.
            }
        }
    }

    private fun startVehicleRefresh(lineId: Long) {
        vehicleRefreshJob?.cancel()
        vehicleRefreshJob = viewModelScope.launch {
            var lastNewsRefresh = currentTimeMillis()
            while (true) {
                delay(Constants.REFETCH_VEHICLES_MS)
                noticeTime.value = currentTimeMillis()
                if (currentTimeMillis() - lastNewsRefresh >= 60_000) {
                    loadAnnouncementsForLine(lineId)
                    lastNewsRefresh = currentTimeMillis()
                }
                loadVehicles(lineId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vehicleRefreshJob?.cancel()
    }
}
