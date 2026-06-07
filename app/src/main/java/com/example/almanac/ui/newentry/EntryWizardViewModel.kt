package com.example.almanac.ui.newentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.almanac.AlmanacApplication
import com.example.almanac.data.notion.NotionPhysicalRepository
import com.example.almanac.data.notion.NotionResult
import com.example.almanac.data.notion.PhysicalEntryWriter
import com.example.almanac.data.settings.SettingsRepository
import com.example.almanac.domain.model.NewPhysicalEntryDraft
import com.example.almanac.domain.model.PhysicalEntry
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

class EntryWizardViewModel(
    private val mode: Mode,
    private val suggest: SuggestPhysicalEntryUseCase?,
    private val writer: PhysicalEntryWriter,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: LocalDate = LocalDate.now(zone),
) : ViewModel() {

    private val _state = MutableStateFlow<EntryWizardUiState>(EntryWizardUiState.PreparingSuggestions)
    val state: StateFlow<EntryWizardUiState> = _state.asStateFlow()

    val isEditing: Boolean get() = mode is Mode.Edit

    init {
        when (mode) {
            is Mode.Create -> loadCreateSuggestions()
            is Mode.Edit -> _state.value = EntryWizardUiState.Editing(
                EntryWizardUiState.Step.SLEEP,
                mode.initial,
            )
            is Mode.EditMissing -> _state.value = EntryWizardUiState.Unavailable(
                "Couldn't load entry. Open the browser again.",
            )
        }
    }

    private fun loadCreateSuggestions() {
        _state.value = EntryWizardUiState.PreparingSuggestions
        viewModelScope.launch {
            val range = lastFullWeekRange()
            val target = settings.dailyStepTarget.first()
            val draft = suggest?.invoke(range, zone, target)
                ?: NewPhysicalEntryDraft(
                    range = range,
                    weekLabel = SuggestPhysicalEntryUseCase.formatWeekLabel(range, zone),
                )
            _state.value = EntryWizardUiState.Editing(EntryWizardUiState.Step.SLEEP, draft)
        }
    }

    fun updateDraft(transform: (NewPhysicalEntryDraft) -> NewPhysicalEntryDraft) {
        val editing = _state.value as? EntryWizardUiState.Editing ?: return
        _state.value = editing.copy(draft = transform(editing.draft))
    }

    fun goNext() {
        when (val s = _state.value) {
            is EntryWizardUiState.Editing -> {
                val next = s.step.next()
                _state.value = if (next == null) {
                    EntryWizardUiState.Reviewing(s.draft)
                } else {
                    EntryWizardUiState.Editing(next, s.draft)
                }
            }
            else -> {}
        }
    }

    fun goBack() {
        when (val s = _state.value) {
            is EntryWizardUiState.Editing -> {
                val prev = s.step.previous()
                if (prev != null) _state.value = EntryWizardUiState.Editing(prev, s.draft)
            }
            is EntryWizardUiState.Reviewing -> {
                _state.value = EntryWizardUiState.Editing(EntryWizardUiState.Step.CONTEXT, s.draft)
            }
            is EntryWizardUiState.SaveFailed -> {
                _state.value = EntryWizardUiState.Reviewing(s.draft)
            }
            else -> {}
        }
    }

    fun save() {
        val draft = when (val s = _state.value) {
            is EntryWizardUiState.Reviewing -> s.draft
            is EntryWizardUiState.SaveFailed -> s.draft
            else -> return
        }
        _state.value = EntryWizardUiState.Saving
        viewModelScope.launch {
            val result = when (val m = mode) {
                is Mode.Create -> writer.create(draft)
                is Mode.Edit -> writer.update(m.entryId, draft)
                is Mode.EditMissing -> return@launch
            }
            _state.value = when (result) {
                is NotionResult.Success -> EntryWizardUiState.Saved
                is NotionResult.Failure -> EntryWizardUiState.SaveFailed(draft, result.error.message)
            }
        }
    }

    private fun lastFullWeekRange(): TimeRange {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val lastMonday = thisMonday.minusWeeks(1)
        val lastSunday = lastMonday.plusDays(6)
        return TimeRange.fromInclusiveDates(lastMonday, lastSunday, zone)
    }

    sealed interface Mode {
        data object Create : Mode
        data class Edit(val entryId: String, val initial: NewPhysicalEntryDraft) : Mode
        data object EditMissing : Mode
    }

    companion object {
        const val ENTRY_ID_ARG = "entryId"

        val CreateFactory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AlmanacApplication
                EntryWizardViewModel(
                    mode = Mode.Create,
                    suggest = app.container.suggestUseCase,
                    writer = app.container.physicalEntryWriter,
                    settings = app.container.settingsRepository,
                )
            }
        }

        val EditFactory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AlmanacApplication
                val handle: SavedStateHandle = createSavedStateHandle()
                val entryId = handle.get<String>(ENTRY_ID_ARG)
                val mode = resolveEditMode(entryId, app.container.notionPhysicalRepository)
                EntryWizardViewModel(
                    mode = mode,
                    suggest = app.container.suggestUseCase,
                    writer = app.container.physicalEntryWriter,
                    settings = app.container.settingsRepository,
                )
            }
        }

        private fun resolveEditMode(
            entryId: String?,
            repository: NotionPhysicalRepository,
        ): Mode {
            if (entryId.isNullOrBlank()) return Mode.EditMissing
            val entry = repository.cached(entryId) ?: return Mode.EditMissing
            return Mode.Edit(entryId, draftFrom(entry))
        }

        private fun draftFrom(entry: PhysicalEntry): NewPhysicalEntryDraft = NewPhysicalEntryDraft(
            range = TimeRange(entry.createdAt, entry.createdAt),
            weekLabel = entry.weekLabel.orEmpty(),
            kpi = entry.kpi,
            avgSleep = entry.avgSleep.orEmpty(),
            bedTime = entry.bedTime.orEmpty(),
            wakeUp = entry.wakeUp.orEmpty(),
            avgSteps = entry.avgSteps,
            stepTargetDaysHit = entry.stepTarget?.toInt(),
            weight = entry.weight,
            sport = entry.sport.orEmpty(),
            flaster = entry.flaster,
            noEith = entry.noEith,
            noSugar = entry.noSugar,
            notes = entry.notes.orEmpty(),
        )
    }
}

sealed interface EntryWizardUiState {
    data object PreparingSuggestions : EntryWizardUiState
    data class Editing(val step: Step, val draft: NewPhysicalEntryDraft) : EntryWizardUiState
    data class Reviewing(val draft: NewPhysicalEntryDraft) : EntryWizardUiState
    data object Saving : EntryWizardUiState
    data class SaveFailed(val draft: NewPhysicalEntryDraft, val message: String) : EntryWizardUiState
    data object Saved : EntryWizardUiState
    data class Unavailable(val message: String) : EntryWizardUiState

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
