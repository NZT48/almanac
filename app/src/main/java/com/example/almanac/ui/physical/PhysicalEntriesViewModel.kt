package com.example.almanac.ui.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.almanac.AlmanacApplication
import com.example.almanac.data.notion.NotionPhysicalRepository
import com.example.almanac.data.notion.NotionResult
import com.example.almanac.domain.model.PhysicalEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhysicalEntriesViewModel(
    private val repository: NotionPhysicalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PhysicalEntriesUiState>(PhysicalEntriesUiState.Loading)
    val state: StateFlow<PhysicalEntriesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = PhysicalEntriesUiState.Loading
        viewModelScope.launch {
            _state.value = when (val r = repository.loadLatestFirst()) {
                is NotionResult.Failure -> PhysicalEntriesUiState.Error(r.error.message)
                is NotionResult.Success -> if (r.value.isEmpty()) {
                    PhysicalEntriesUiState.Empty
                } else {
                    PhysicalEntriesUiState.Loaded(entries = r.value, index = 0)
                }
            }
        }
    }

    fun next() {
        val current = _state.value as? PhysicalEntriesUiState.Loaded ?: return
        if (current.index < current.entries.lastIndex) {
            _state.value = current.copy(index = current.index + 1)
        }
    }

    fun previous() {
        val current = _state.value as? PhysicalEntriesUiState.Loaded ?: return
        if (current.index > 0) {
            _state.value = current.copy(index = current.index - 1)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AlmanacApplication
                PhysicalEntriesViewModel(app.container.notionPhysicalRepository)
            }
        }
    }
}

sealed interface PhysicalEntriesUiState {
    data object Loading : PhysicalEntriesUiState
    data object Empty : PhysicalEntriesUiState
    data class Error(val message: String) : PhysicalEntriesUiState
    data class Loaded(
        val entries: List<PhysicalEntry>,
        val index: Int,
    ) : PhysicalEntriesUiState {
        val current: PhysicalEntry get() = entries[index]
        val hasPrevious: Boolean get() = index > 0
        val hasNext: Boolean get() = index < entries.lastIndex
        val positionLabel: String get() = "${index + 1} / ${entries.size}"
    }
}
