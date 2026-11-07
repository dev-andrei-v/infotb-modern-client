package ro.infotb.modern.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import ro.infotb.modern.data.model.Line
import ro.infotb.modern.data.model.TransportType
import ro.infotb.modern.data.repository.TransitRepository
import ro.infotb.modern.data.store.FavoritesStore

class LinesViewModel(
    private val repository: TransitRepository,
    private val favoritesStore: FavoritesStore
) : ViewModel() {

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypes = MutableStateFlow<Set<TransportType>>(emptySet())
    val selectedTypes: StateFlow<Set<TransportType>> = _selectedTypes.asStateFlow()

    private val stopSearchLineIds = MutableStateFlow<Set<Long>>(emptySet())
    private val lineSummaries = MutableStateFlow<Map<Long, LineSummary>>(emptyMap())
    private val requestedSummaryIds = mutableSetOf<Long>()
    private var stopSearchJob: Job? = null

    private data class LineSummary(val start: String?, val end: String?)

    val favoriteIds: StateFlow<List<Long>> = favoritesStore.favoriteLineIds

    private val baseFilteredLines = combine(
        _lines, _searchQuery, _selectedTypes, favoriteIds, stopSearchLineIds
    ) { allLines, query, types, favIds, stopLineIds ->
        var result = allLines

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(q) || it.id in stopLineIds }
        }

        if (types.isNotEmpty()) {
            result = result.filter { it.type in types }
        }

        result.sortedWith(
            compareByDescending<Line> { it.id in favIds }
                .thenBy { it.type.ordinal }
                .thenBy {
                    val num = it.name.filter { c -> c.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE
                    num
                }
                .thenBy { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredLines: StateFlow<List<Line>> = combine(baseFilteredLines, lineSummaries) { lines, summaries ->
        lines.map { line ->
            val summary = summaries[line.id]
            if (summary == null) line else line.copy(startStopName = summary.start, endStopName = summary.end)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadLines()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        stopSearchJob?.cancel()
        stopSearchLineIds.value = emptySet()
        val normalized = query.trim()
        if (normalized.length < 2) return
        stopSearchJob = viewModelScope.launch {
            delay(300)
            val stopIds = runCatching { repository.searchPlannerPlaces(normalized) }
                .getOrDefault(emptyList())
                .mapNotNull { it.stop_id }
                .distinct()
                .take(4)
            val matchingIds = mutableSetOf<Long>()
            for (stopId in stopIds) {
                if (!coroutineContext.isActive) return@launch
                runCatching { repository.getStopLines(stopId) }
                    .getOrDefault(emptyList())
                    .mapTo(matchingIds) { it.id }
            }
            if (_searchQuery.value.trim() == normalized) stopSearchLineIds.value = matchingIds
        }
    }

    fun loadLineSummary(lineId: Long) {
        if (lineId in lineSummaries.value || !requestedSummaryIds.add(lineId)) return
        viewModelScope.launch {
            try {
                val detail = repository.getLineDetail(lineId)
                val start = detail.directionNameTur?.takeIf { it.isNotBlank() }
                    ?: detail.stops.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                val end = detail.directionNameRetur?.takeIf { it.isNotBlank() }
                    ?: detail.stops.lastOrNull()?.name?.takeIf { it.isNotBlank() }
                if (start != null || end != null) {
                    lineSummaries.update { it + (lineId to LineSummary(start, end)) }
                } else {
                    requestedSummaryIds.remove(lineId)
                }
            } catch (_: Exception) {
                requestedSummaryIds.remove(lineId)
            }
        }
    }

    fun toggleTypeFilter(type: TransportType) {
        val current = _selectedTypes.value.toMutableSet()
        if (current.contains(type)) {
            current.remove(type)
        } else {
            current.add(type)
        }
        _selectedTypes.value = current
    }

    fun toggleFavorite(lineId: Long) {
        favoritesStore.toggleFavorite(lineId)
    }

    fun loadLines() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = repository.getAllLines()
                _lines.value = result
            } catch (e: Exception) {
                println("LinesViewModel error: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace()
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
