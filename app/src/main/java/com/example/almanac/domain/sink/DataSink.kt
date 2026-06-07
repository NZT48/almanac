package com.example.almanac.domain.sink

import com.example.almanac.domain.model.HealthRecord
import com.example.almanac.domain.model.TimeRange

interface DataSink {
    suspend fun write(
        records: List<HealthRecord>,
        range: TimeRange,
        stepTarget: Int,
    ): SinkResult
}

sealed interface SinkResult {
    data class Success(val displayName: String, val locationLabel: String) : SinkResult
    data class Failure(val reason: String) : SinkResult
}
