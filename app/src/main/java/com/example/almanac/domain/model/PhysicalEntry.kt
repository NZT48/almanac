package com.example.almanac.domain.model

import java.time.Instant

/**
 * One row of the Notion "Physical" database. Field names mirror the column
 * labels in Notion. Display-only — Almanac doesn't write back yet.
 */
data class PhysicalEntry(
    val id: String,
    val createdAt: Instant,
    val kpi: String,
    val weekLabel: String?,
    val weight: Double?,
    val avgSteps: Long?,
    val stepTarget: Long?,
    val avgSleep: String?,
    val bedTime: String?,
    val wakeUp: String?,
    val sport: String?,
    val notes: String?,
    val flaster: Long?,
    val noEith: Long?,
    val noSugar: Long?,
)
