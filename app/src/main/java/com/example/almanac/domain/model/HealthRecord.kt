package com.example.almanac.domain.model

import java.time.Instant

sealed interface HealthRecord {
    val recordedAt: Instant
    val source: String
}

data class StepRecord(
    override val recordedAt: Instant,
    val periodStart: Instant,
    val periodEnd: Instant,
    val count: Long,
    override val source: String,
) : HealthRecord

data class WeightRecord(
    override val recordedAt: Instant,
    val kilograms: Double,
    override val source: String,
) : HealthRecord

data class SleepRecord(
    override val recordedAt: Instant,
    val start: Instant,
    val end: Instant,
    val durationMinutes: Long,
    val stages: List<SleepStage> = emptyList(),
    override val source: String,
) : HealthRecord

data class SleepStage(
    val start: Instant,
    val end: Instant,
    val type: StageType,
) {
    enum class StageType { AWAKE, LIGHT, DEEP, REM, OUT_OF_BED, SLEEPING, UNKNOWN }
}
