package com.example.almanac.domain.model

data class NewPhysicalEntryDraft(
    val range: TimeRange,
    val weekLabel: String,
    val kpi: String = "",
    val avgSleep: String = "",
    val bedTime: String = "",
    val wakeUp: String = "",
    val avgSteps: Long? = null,
    val stepTargetDaysHit: Int? = null,
    val weight: Double? = null,
    val sport: String = "",
    val flaster: Long? = null,
    val noEith: Long? = null,
    val noSugar: Long? = null,
    val notes: String = "",
)
