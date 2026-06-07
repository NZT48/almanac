package com.example.almanac.domain.usecase

import com.example.almanac.domain.model.NewPhysicalEntryDraft
import com.example.almanac.domain.model.SleepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.source.DataSource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class SuggestPhysicalEntryUseCase(
    private val source: DataSource,
) {

    suspend operator fun invoke(
        range: TimeRange,
        zone: ZoneId,
        dailyStepTarget: Int,
    ): NewPhysicalEntryDraft {
        val steps = source.readSteps(range)
        val weights = source.readWeight(range)
        val sleep = source.readSleep(range)

        val totalSteps = steps.sumOf { it.count }
        val avgSteps = if (steps.isNotEmpty()) (totalSteps.toDouble() / 7.0).roundToLong() else null
        val daysHit = steps.count { it.count >= dailyStepTarget }
        val avgWeight = weights.takeIf { it.isNotEmpty() }
            ?.let { it.sumOf(com.example.almanac.domain.model.WeightRecord::kilograms) / it.size }
            ?.let { (it * 10.0).roundToInt() / 10.0 }

        val sleepByNight = sleep.groupBy { LocalDate.ofInstant(it.end, zone) }
        val nightlyMinutes = sleepByNight.values.map { perNight -> perNight.sumOf(SleepRecord::durationMinutes) }
        val avgSleepText = nightlyMinutes.takeIf { it.isNotEmpty() }
            ?.let { it.sum().toDouble() / it.size }
            ?.roundToInt()
            ?.let(::formatDurationMinutes)
            .orEmpty()

        val bedTimeText = averageTimeOfDay(sleep.map { LocalTime.ofInstant(it.start, zone) }, shift = true)
        val wakeUpText = averageTimeOfDay(sleep.map { LocalTime.ofInstant(it.end, zone) }, shift = true)

        return NewPhysicalEntryDraft(
            range = range,
            weekLabel = formatWeekLabel(range, zone),
            avgSleep = avgSleepText,
            bedTime = bedTimeText,
            wakeUp = wakeUpText,
            avgSteps = avgSteps,
            stepTargetDaysHit = if (steps.isNotEmpty()) daysHit else null,
            weight = avgWeight,
        )
    }

    private fun formatDurationMinutes(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return "${h}h ${"%02d".format(m)}m"
    }

    companion object {
        private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.")
        private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun formatWeekLabel(range: TimeRange, zone: ZoneId): String {
            val start = LocalDate.ofInstant(range.start, zone)
            val end = LocalDate.ofInstant(range.end.minusSeconds(1), zone)
            return "${start.format(DAY_MONTH)} - ${end.format(DAY_MONTH)}"
        }

        /**
         * Time-of-day mean that survives midnight rollover. Shifting by +12h
         * before averaging means a 23:30 + 00:30 pair returns 00:00 instead
         * of 12:00. Safe to apply even when no rollover is in play.
         */
        fun averageTimeOfDay(times: List<LocalTime>, shift: Boolean): String {
            if (times.isEmpty()) return ""
            val dayMinutes = 24 * 60
            val shiftBy = if (shift) 12 * 60 else 0
            val shifted = times.map { ((it.hour * 60 + it.minute) + shiftBy) % dayMinutes }
            val meanShifted = shifted.average().roundToInt()
            val actual = ((meanShifted - shiftBy) % dayMinutes + dayMinutes) % dayMinutes
            return LocalTime.of(actual / 60, actual % 60).format(HH_MM)
        }
    }
}
