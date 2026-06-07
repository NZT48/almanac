package com.example.almanac.ui.main

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.almanac.AlmanacApplication
import com.example.almanac.data.file.WeeklySummary
import com.example.almanac.data.healthconnect.HealthConnectSource
import com.example.almanac.data.settings.SettingsRepository
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.sink.SinkResult
import com.example.almanac.domain.usecase.ExportDataUseCase
import com.example.almanac.domain.usecase.ReadHealthDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(
    application: Application,
    private val healthConnect: HealthConnectClient?,
    private val readUseCase: ReadHealthDataUseCase?,
    private val exportUseCase: ExportDataUseCase,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val initialDefault = run {
        val (f, t) = RangePresets.resolve(RangePreset.LAST_WEEK)
        PickerState(f, t, RangePreset.LAST_WEEK)
    }
    private val _picker = MutableStateFlow(initialDefault)
    val picker: StateFlow<PickerState> = _picker.asStateFlow()

    init {
        refreshState()
    }

    /**
     * Re-checks Health Connect availability and granted permissions. Called on
     * init and again after returning from the permission contract or HC settings.
     */
    fun refreshState() {
        viewModelScope.launch {
            if (healthConnect == null) {
                _uiState.value = MainUiState.HealthConnectUnavailable
                return@launch
            }
            val granted = try {
                healthConnect.permissionController.getGrantedPermissions()
            } catch (t: Throwable) {
                _uiState.value = MainUiState.Error(t.message ?: "Failed to read permissions")
                return@launch
            }
            val missing = HealthConnectSource.REQUIRED_PERMISSIONS - granted
            _uiState.value = if (missing.isEmpty()) MainUiState.Ready else MainUiState.NeedsPermission(missing)
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        val missing = HealthConnectSource.REQUIRED_PERMISSIONS - granted
        _uiState.value = if (missing.isEmpty()) MainUiState.Ready else MainUiState.NeedsPermission(missing)
    }

    fun selectPreset(preset: RangePreset) {
        if (preset == RangePreset.CUSTOM) {
            _picker.value = _picker.value.copy(preset = RangePreset.CUSTOM)
            return
        }
        val (f, t) = RangePresets.resolve(preset)
        _picker.value = PickerState(from = f, to = t, preset = preset)
    }

    fun setFrom(date: LocalDate) {
        val current = _picker.value
        val newTo = if (date.isAfter(current.to)) date else current.to
        _picker.value = PickerState(
            from = date,
            to = newTo,
            preset = RangePresets.matchingPreset(date, newTo),
        )
    }

    fun setTo(date: LocalDate) {
        val current = _picker.value
        val newFrom = if (date.isBefore(current.from)) date else current.from
        _picker.value = PickerState(
            from = newFrom,
            to = date,
            preset = RangePresets.matchingPreset(newFrom, date),
        )
    }

    fun runExport() {
        val state = _uiState.value
        if (state != MainUiState.Ready && state !is MainUiState.Done) return
        val picker = _picker.value
        if (!picker.valid) return

        val read = readUseCase ?: run {
            _uiState.value = MainUiState.HealthConnectUnavailable
            return
        }

        viewModelScope.launch {
            _uiState.value = MainUiState.Exporting
            try {
                val zoneId = ZoneId.systemDefault()
                val range = TimeRange.fromInclusiveDates(picker.from, picker.to, zoneId)
                val stepTarget = settingsRepository.dailyStepTarget.first()

                val readResult = read(range)
                val summary = WeeklySummary.from(readResult.all(), range, zoneId, stepTarget)
                val sinkResult = exportUseCase(readResult.all(), range, stepTarget)

                when (sinkResult) {
                    is SinkResult.Success -> {
                        val counts = ExportCounts(
                            stepDays = summary.steps.perDayTotal.size,
                            weightReadings = readResult.weight.size,
                            sleepSessions = readResult.sleep.size,
                        )
                        _uiState.value = MainUiState.Done(
                            result = sinkResult,
                            counts = counts,
                            summary = summary,
                            allEmptyHint = readResult.allEmpty(),
                        )
                    }
                    is SinkResult.Failure -> {
                        _uiState.value = MainUiState.Error("Export failed: ${sinkResult.reason}")
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = MainUiState.Error(t.message ?: "Unexpected error")
            }
        }
    }

    fun resetForNewExport() {
        refreshState()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AlmanacApplication
                val container = app.container
                MainViewModel(
                    application = app,
                    healthConnect = container.healthConnect,
                    readUseCase = container.readUseCase,
                    exportUseCase = container.exportUseCase,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
