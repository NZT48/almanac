package com.example.almanac.domain.usecase

import com.example.almanac.domain.model.HealthRecord
import com.example.almanac.domain.model.SleepRecord
import com.example.almanac.domain.model.StepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.model.WeightRecord
import com.example.almanac.domain.source.DataSource

class ReadHealthDataUseCase(private val source: DataSource) {
    suspend operator fun invoke(range: TimeRange): ReadResult {
        val steps = source.readSteps(range)
        val weight = source.readWeight(range)
        val sleep = source.readSleep(range)
        return ReadResult(steps, weight, sleep)
    }
}

data class ReadResult(
    val steps: List<StepRecord>,
    val weight: List<WeightRecord>,
    val sleep: List<SleepRecord>,
) {
    fun all(): List<HealthRecord> = steps + weight + sleep
    fun allEmpty(): Boolean = steps.isEmpty() && weight.isEmpty() && sleep.isEmpty()
}
