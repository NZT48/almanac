package com.example.almanac.domain.usecase

import com.example.almanac.domain.model.HealthRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.sink.DataSink
import com.example.almanac.domain.sink.SinkResult

class ExportDataUseCase(private val sink: DataSink) {
    suspend operator fun invoke(
        records: List<HealthRecord>,
        range: TimeRange,
        stepTarget: Int,
    ): SinkResult = sink.write(records, range, stepTarget)
}
