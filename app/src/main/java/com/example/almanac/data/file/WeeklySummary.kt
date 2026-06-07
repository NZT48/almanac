package com.example.almanac.data.file

import com.example.almanac.domain.model.HealthRecord
import com.example.almanac.domain.model.SleepRecord
import com.example.almanac.domain.model.SleepStage
import com.example.almanac.domain.model.StepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.model.WeightRecord
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * Structured snapshot of one weekly export. The file (via
 * [WeeklyDigestFormatter]) and the on-screen result panel (via
 * [com.example.almanac.ui.DigestPreviewCard]) both render from this single
 * object so they can never drift apart (spec §FR-10).
 */
data class WeeklySummary(
    val range: TimeRange,
    val zoneId: ZoneId,
    val stepTarget: Int,
    val isoYear: Int,
    val isoWeek: Int,
    val days: List<LocalDate>,
    val steps: StepsSummary,
    val weight: WeightSummary,
    val sleep: SleepSummary,
) {
    val isoWeekTag: String get() = "%04d-W%02d".format(isoYear, isoWeek)
    val allEmpty: Boolean get() = steps.isEmpty && weight.isEmpty && sleep.isEmpty

    companion object {
        fun from(
            records: List<HealthRecord>,
            range: TimeRange,
            zoneId: ZoneId,
            stepTarget: Int,
        ): WeeklySummary {
            val firstDay = LocalDate.ofInstant(range.start, zoneId)
            val lastDay = LocalDate.ofInstant(range.end.minus(Duration.ofSeconds(1)), zoneId)
            val days = generateSequence(firstDay) { it.plusDays(1) }
                .takeWhile { !it.isAfter(lastDay) }
                .toList()

            val steps = records.filterIsInstance<StepRecord>()
            val weight = records.filterIsInstance<WeightRecord>().sortedBy { it.recordedAt }
            val sleep = records.filterIsInstance<SleepRecord>().sortedBy { it.end }

            return WeeklySummary(
                range = range,
                zoneId = zoneId,
                stepTarget = stepTarget,
                isoYear = firstDay.get(IsoFields.WEEK_BASED_YEAR),
                isoWeek = firstDay.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                days = days,
                steps = StepsSummary.from(steps, days, zoneId, stepTarget),
                weight = WeightSummary.from(weight, zoneId),
                sleep = SleepSummary.from(sleep, days, zoneId),
            )
        }
    }
}

data class StepsSummary(
    val perDayTotal: Map<LocalDate, Long>,
    val weeklyTotal: Long,
    val dailyAverage: Long,
    val daysTargetHit: Int,
    val daysInWeek: Int,
) {
    val isEmpty: Boolean get() = perDayTotal.isEmpty()

    companion object {
        fun from(
            steps: List<StepRecord>,
            days: List<LocalDate>,
            zoneId: ZoneId,
            target: Int,
        ): StepsSummary {
            val byDay = steps
                .groupBy { LocalDate.ofInstant(it.periodStart, zoneId) }
                .mapValues { (_, list) -> list.sumOf { it.count } }
            val withData = byDay.values
            val total = withData.sum()
            val avg = if (withData.isNotEmpty()) total / withData.size else 0L
            val hits = withData.count { it >= target }
            return StepsSummary(byDay, total, avg, hits, days.size)
        }
    }
}

data class WeightSummary(
    val readings: List<WeightReading>,
    val weekDeltaKg: Double?,
    val weeklyAverageKg: Double?,
) {
    val isEmpty: Boolean get() = readings.isEmpty()

    data class WeightReading(val date: LocalDate, val kilograms: Double)

    companion object {
        fun from(weights: List<WeightRecord>, zoneId: ZoneId): WeightSummary {
            val readings = weights.map {
                WeightReading(LocalDate.ofInstant(it.recordedAt, zoneId), it.kilograms)
            }
            val delta = if (readings.size >= 2) readings.last().kilograms - readings.first().kilograms else null
            val avg = if (readings.size >= 2) readings.sumOf { it.kilograms } / readings.size else null
            return WeightSummary(readings, delta, avg)
        }
    }
}

data class SleepSummary(
    val perWakeDay: Map<LocalDate, Night>,
    val days: List<LocalDate>,
    val weeklyAverageMinutes: Long?,
    val averageBedtime: LocalTime?,
    val averageWakeTime: LocalTime?,
    val totalDeepMinutes: Long,
    val totalRemMinutes: Long,
) {
    val isEmpty: Boolean get() = perWakeDay.isEmpty()

    data class Night(
        val wakeTime: LocalTime,
        val bedtime: LocalTime,
        val durationMinutes: Long,
    )

    companion object {
        /**
         * Records shorter than this are excluded from every aggregate
         * (duration average, bedtime/wake-time averages, stage totals). Per-day
         * rows in the digest still show short records — the threshold only
         * filters out stray naps from the *summary* numbers.
         */
        const val MIN_AGGREGATE_DURATION_MINUTES: Long = 120

        fun from(
            sleep: List<SleepRecord>,
            days: List<LocalDate>,
            zoneId: ZoneId,
        ): SleepSummary {
            if (sleep.isEmpty()) {
                return SleepSummary(emptyMap(), days, null, null, null, 0, 0)
            }
            val perDay = sleep.associate { rec ->
                val day = LocalDate.ofInstant(rec.end, zoneId)
                day to Night(
                    wakeTime = LocalTime.ofInstant(rec.end, zoneId),
                    bedtime = LocalTime.ofInstant(rec.start, zoneId),
                    durationMinutes = rec.durationMinutes,
                )
            }
            val significant = sleep.filter { it.durationMinutes >= MIN_AGGREGATE_DURATION_MINUTES }
            val avgDur = if (significant.isNotEmpty()) significant.sumOf { it.durationMinutes } / significant.size else null
            val avgBedtime = if (significant.isNotEmpty()) averageBedtime(significant.map { LocalTime.ofInstant(it.start, zoneId) }) else null
            val avgWake = if (significant.isNotEmpty()) averageTime(significant.map { LocalTime.ofInstant(it.end, zoneId) }) else null
            val deep = stageTotal(significant, SleepStage.StageType.DEEP)
            val rem = stageTotal(significant, SleepStage.StageType.REM)
            return SleepSummary(perDay, days, avgDur, avgBedtime, avgWake, deep, rem)
        }

        private fun stageTotal(sleep: List<SleepRecord>, type: SleepStage.StageType): Long =
            sleep.sumOf { rec ->
                rec.stages.filter { it.type == type }
                    .sumOf { ChronoUnit.MINUTES.between(it.start, it.end) }
            }

        private fun averageBedtime(times: List<LocalTime>): LocalTime {
            if (times.isEmpty()) return LocalTime.MIDNIGHT
            val minutes = times.map { it.toSecondOfDay() / 60 }
            val adjusted = minutes.map { if (it < 12 * 60) it + 24 * 60 else it }
            val avg = adjusted.average().toInt() % (24 * 60)
            return LocalTime.of(avg / 60, avg % 60)
        }

        private fun averageTime(times: List<LocalTime>): LocalTime {
            if (times.isEmpty()) return LocalTime.MIDNIGHT
            val avg = times.map { it.toSecondOfDay() / 60 }.average().toInt()
            return LocalTime.of(avg / 60, avg % 60)
        }
    }
}

fun formatDurationMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return "%dh %02dm".format(h, m)
}
