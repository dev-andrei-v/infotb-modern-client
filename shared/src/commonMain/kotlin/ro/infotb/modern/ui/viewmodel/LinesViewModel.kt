package ro.infotb.modern.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    val favoriteIds: StateFlow<List<Long>> = favoritesStore.favoriteLineIds

    val filteredLines: StateFlow<List<Line>> = combine(
        _lines, _searchQuery, _selectedTypes, favoriteIds
    ) { allLines, query, types, favIds ->
        var result = allLines

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(q) }
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

    init {
        loadLines()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
