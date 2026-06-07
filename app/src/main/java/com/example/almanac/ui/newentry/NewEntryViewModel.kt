package com.example.almanac.ui.newentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.almanac.AlmanacApplication
import com.example.almanac.data.notion.NotionResult
import com.example.almanac.data.notion.PhysicalEntryWriter
import com.example.almanac.data.settings.SettingsRepository
import com.example.almanac.domain.model.NewPhysicalEntryDraft
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.usecase.SuggestPhysicalEntryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class NewEntryViewModel(
    private val suggest: SuggestPhysicalEntryUseCase?,
    private val writer: PhysicalEntryWriter,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: LocalDate = LocalDate.now(zone),
) : ViewModel() {

    private val _state = MutableStateFlow<NewEntryUiState>(NewEntryUiState.PreparingSuggestions)
    val state: StateFlow<NewEntryUiState> = _state.asStateFlow()

    init {
        loadSuggestions()
    }

    private fun loadSuggestions() {
        _state.value = NewEntryUiState.PreparingSuggestions
        viewModelScope.launch {
            val range = lastFullWeekRange()
            val target = settings.dailyStepTarget.first()
            val draft = suggest?.invoke(range, zone, target)
                ?: NewPhysicalEntryDraft(
                    range = range,
                    weekLabel = SuggestPhysicalEntryUseCase.formatWeekLabel(range, zone),
                )
            _state.value = NewEntryUiState.Editing(NewEntryUiState.Step.SLEEP, draft)
        }
    }

    fun updateDraft(transform: (NewPhysicalEntryDraft) -> NewPhysicalEntryDraft) {
        val editing = _state.value as? NewEntryUiState.Editing ?: return
        _state.value = editing.copy(draft = transform(editing.draft))
    }

    fun goNext() {
        when (val s = _state.value) {
            is NewEntryUiState.Editing -> {
                val next = s.step.next()
                _state.value = if (next == null) {
                    NewEntryUiState.Reviewing(s.draft)
                } else {
                    NewEntryUiState.Editing(next, s.draft)
                }
            }
            else -> {}
        }
    }

    fun goBack() {
        when (val s = _state.value) {
            is NewEntryUiState.Editing -> {
                val prev = s.step.previous()
                if (prev != null) _state.value = NewEntryUiState.Editing(prev, s.draft)
            }
            is NewEntryUiState.Reviewing -> {
                _state.value = NewEntryUiState.Editing(NewEntryUiState.Step.CONTEXT, s.draft)
            }
            is NewEntryUiState.SaveFailed -> {
                _state.value = NewEntryUiState.Reviewing(s.draft)
            }
            else -> {}
        }
    }

    fun save() {
        val draft = when (val s = _state.value) {
            is NewEntryUiState.Reviewing -> s.draft
            is NewEntryUiState.SaveFailed -> s.draft
            else -> return
        }
        _state.value = NewEntryUiState.Saving
        viewModelScope.launch {
            _state.value = when (val r = writer.create(draft)) {
                is NotionResult.Success -> NewEntryUiState.Saved
                is NotionResult.Failure -> NewEntryUiState.SaveFailed(draft, r.error.message)
            }
        }
    }

    private fun lastFullWeekRange(): TimeRange {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val lastMonday = thisMonday.minusWeeks(1)
        val lastSunday = lastMonday.plusDays(6)
        return TimeRange.fromInclusiveDates(lastMonday, lastSunday, zone)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AlmanacApplication
                NewEntryViewModel(
                    suggest = app.container.suggestUseCase,
                    writer = app.container.physicalEntryWriter,
                    settings = app.container.settingsRepository,
                )
            }
        }
    }
}

sealed interface NewEntryUiState {
    data object PreparingSuggestions : NewEntryUiState
    data class Editing(val step: Step, val draft: NewPhysicalEntryDraft) : NewEntryUiState
    data class Reviewing(val draft: NewPhysicalEntryDraft) : NewEntryUiState
    data object Saving : NewEntryUiState
    data class SaveFailed(val draft: NewPhysicalEntryDraft, val message: String) : NewEntryUiState
    data object Saved : NewEntryUiState

    enum class Step(val title: String, val index: Int) {
        SLEEP("Sleep", 1),
        ACTIVITY("Activity", 2),
        BODY("Body", 3),
        HABITS("Habits", 4),
        CONTEXT("Context", 5);

        fun next(): Step? = entries.getOrNull(ordinal + 1)
        fun previous(): Step? = entries.getOrNull(ordinal - 1)

        companion object {
            const val TOTAL = 5
        }
    }
}
