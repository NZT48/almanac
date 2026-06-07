package com.example.almanac.ui.main

import com.example.almanac.data.file.WeeklySummary
import com.example.almanac.domain.sink.SinkResult
import java.time.LocalDate

sealed interface MainUiState {
    data object Loading : MainUiState
    data object HealthConnectUnavailable : MainUiState
    data class NeedsPermission(val missing: Set<String>) : MainUiState
    data object Ready : MainUiState
    data object Exporting : MainUiState
    data class Done(
        val result: SinkResult.Success,
        val counts: ExportCounts,
        val summary: WeeklySummary,
        val allEmptyHint: Boolean,
    ) : MainUiState
    data class Error(val message: String) : MainUiState
}

data class ExportCounts(
    val stepDays: Int,
    val weightReadings: Int,
    val sleepSessions: Int,
)

data class PickerState(
    val from: LocalDate,
    val to: LocalDate,
    val preset: RangePreset,
) {
    val valid: Boolean get() = !from.isAfter(to)
}

enum class RangePreset(val label: String) {
    LAST_WEEK("Last week"),
    THIS_WEEK_SO_FAR("This week so far"),
    LAST_30_DAYS("Last 30 days"),
    CUSTOM("Custom");
}
