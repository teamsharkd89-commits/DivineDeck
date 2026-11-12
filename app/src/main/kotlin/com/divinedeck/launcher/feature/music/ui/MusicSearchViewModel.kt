package com.divinedeck.launcher.feature.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estados de la pantalla de búsqueda de música.
 */
sealed interface SearchUiState {
    data object Empty : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<SearchResult>, val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/**
 * ViewModel para gestionar el ciclo de vida, debounce, cancelación y resultados
 * de la búsqueda original de BitChord dentro de DIVINE DECK.
 */
class MusicSearchViewModel(
    private val repository: YtMusicRepository = YtMusicRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Empty)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private var searchJob: Job? = null
    private var debounceJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        val trimmed = newQuery.trim()
        if (trimmed.isBlank()) {
            debounceJob?.cancel()
            searchJob?.cancel()
            _suggestions.value = emptyList()
            _uiState.value = SearchUiState.Empty
            return
        }

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            // Debounce para evitar sobrecargar la red mientras el usuario escribe
            delay(350)
            executeSearch(trimmed, _filter.value)
        }

        // Sugerencias rápidas en paralelo
        viewModelScope.launch(Dispatchers.IO) {
            repository.searchSuggestions(trimmed).onSuccess { list ->
                if (_query.value.trim() == trimmed) {
                    _suggestions.value = list
                }
            }
        }
    }

    fun onFilterSelected(newFilter: SearchFilter) {
        if (_filter.value == newFilter) return
        _filter.value = newFilter
        val currentQuery = _query.value.trim()
        if (currentQuery.isNotBlank()) {
            debounceJob?.cancel()
            executeSearch(currentQuery, newFilter)
        }
    }

    fun onSuggestionSelected(suggestion: String) {
        _query.value = suggestion
        _suggestions.value = emptyList()
        debounceJob?.cancel()
        executeSearch(suggestion.trim(), _filter.value)
    }

    fun onClearQuery() {
        debounceJob?.cancel()
        searchJob?.cancel()
        _query.value = ""
        _suggestions.value = emptyList()
        _uiState.value = SearchUiState.Empty
    }

    fun retry() {
        val currentQuery = _query.value.trim()
        if (currentQuery.isNotBlank()) {
            executeSearch(currentQuery, _filter.value)
        }
    }

    fun submitQuery() {
        val currentQuery = _query.value.trim()
        if (currentQuery.isNotBlank()) {
            debounceJob?.cancel()
            executeSearch(currentQuery, _filter.value)
        }
    }

    private fun executeSearch(query: String, filter: SearchFilter) {
        searchJob?.cancel()
        _uiState.value = SearchUiState.Loading

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.searchPage(query, filter)
                result.fold(
                    onSuccess = { page ->
                        _uiState.value = SearchUiState.Success(
                            results = page.rows,
                            query = query
                        )
                    },
                    onFailure = { throwable ->
                        if (throwable is CancellationException) throw throwable
                        _uiState.value = SearchUiState.Error(
                            message = throwable.localizedMessage ?: "Error de conexión con YouTube Music"
                        )
                    }
                )
            } catch (ce: CancellationException) {
                // Cancelación intencionada por nueva consulta del usuario
                throw ce
            } catch (t: Throwable) {
                _uiState.value = SearchUiState.Error(
                    message = t.localizedMessage ?: "Error inesperado al buscar música"
                )
            }
        }
    }
}
