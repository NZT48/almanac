package com.example.almanac

import com.example.almanac.data.file.WeeklySummary
import com.example.almanac.domain.model.SleepRecord
import com.example.almanac.domain.model.StepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.model.WeightRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklySummaryTest {

    private val zone: ZoneId = ZoneId.of("Europe/Belgrade")

    private fun instant(date: LocalDate, h: Int, m: Int = 0) =
        ZonedDateTime.of(date, LocalTime.of(h, m), zone).toInstant()

    private fun rangeForWeek(): TimeRange = TimeRange.fromInclusiveDates(
        LocalDate.of(2026, 5, 25),
        LocalDate.of(2026, 5, 31),
        zone,
    )

    @Test fun stepsAggregation_perDayTotalsAndAverageAndTargetHits() {
        val day = LocalDate.of(2026, 5, 25)
        val records = listOf(
            StepRecord(instant(day, 9), instant(day, 9), instant(day, 9, 30), 5000, "x"),
            StepRecord(instant(day, 18), instant(day, 18), instant(day, 18, 30), 3421, "x"),
            StepRecord(instant(day.plusDays(1), 10), instant(day.plusDays(1), 10), instant(day.plusDays(1), 11), 7103, "x"),
        )
        val summary = WeeklySummary.from(records, rangeForWeek(), zone, stepTarget = 7000)
        assertEquals(8421L, summary.steps.perDayTotal[day])
        assertEquals(7103L, summary.steps.perDayTotal[day.plusDays(1)])
        assertEquals(15524L, summary.steps.weeklyTotal)
        assertEquals(7762L, summary.steps.dailyAverage) // 15524 / 2
        // Only day 2026-05-25 (8421) and 2026-05-26 (7103) ≥ 7000.
        assertEquals(2, summary.steps.daysTargetHit)
        assertEquals(7, summary.steps.daysInWeek)
    }

    @Test fun weightAggregation_deltaAndAverageOnlyWithTwoOrMoreReadings() {
        val readings = listOf(
            WeightRecord(instant(LocalDate.of(2026, 5, 26), 8), 82.4, "x"),
            WeightRecord(instant(LocalDate.of(2026, 5, 28), 8), 82.1, "x"),
            WeightRecord(instant(LocalDate.of(2026, 5, 31), 8), 81.8, "x"),
        )
        val summary = WeeklySummary.from(readings, rangeForWeek(), zone, stepTarget = 7000)
        assertEquals(3, summary.weight.readings.size)
        assertEquals(-0.6, summary.weight.weekDeltaKg!!, 0.0001)
        assertEquals(82.1, summary.weight.weeklyAverageKg!!, 0.0001)
    }

    @Test fun weightAggregation_singleReadingHasNoDeltaOrAverage() {
        val readings = listOf(
            WeightRecord(instant(LocalDate.of(2026, 5, 26), 8), 82.4, "x"),
        )
        val summary = WeeklySummary.from(readings, rangeForWeek(), zone, stepTarget = 7000)
        assertNull(summary.weight.weekDeltaKg)
        assertNull(summary.weight.weeklyAverageKg)
    }

    @Test fun sleepAggregation_bucketedByWakeUpTime() {
        // Sleep ending Mon morning belongs to Monday's row.
        val mon = LocalDate.of(2026, 5, 25)
        val tue = LocalDate.of(2026, 5, 26)
        val sleep = listOf(
            SleepRecord(
                recordedAt = instant(mon.minusDays(1), 23),
                start = instant(mon.minusDays(1), 23),
                end = instant(mon, 7),
                durationMinutes = 8 * 60,
                stages = emptyList(),
                source = "x",
            ),
            SleepRecord(
                recordedAt = instant(mon, 23),
                start = instant(mon, 23),
                end = instant(tue, 6, 30),
                durationMinutes = 7 * 60 + 30,
                stages = emptyList(),
                source = "x",
            ),
        )
        val summary = WeeklySummary.from(sleep, rangeForWeek(), zone, stepTarget = 7000)
        assertTrue(summary.sleep.perWakeDay.containsKey(mon))
        assertTrue(summary.sleep.perWakeDay.containsKey(tue))
        assertEquals(7 * 60 + 45L, summary.sleep.weeklyAverageMinutes) // (480 + 450) / 2
    }

    @Test fun sleepAggregates_excludeRecordsShorterThanTwoHours() {
        val mon = LocalDate.of(2026, 5, 25)
        val tue = LocalDate.of(2026, 5, 26)
        val wed = LocalDate.of(2026, 5, 27)
        // Two real nights (8h, 7h) + one 1h fluke that should be ignored by averages but
        // still show up in perWakeDay for transparency.
        val sleep = listOf(
            SleepRecord(
                recordedAt = instant(mon.minusDays(1), 23),
                start = instant(mon.minusDays(1), 23),
                end = instant(mon, 7),
                durationMinutes = 8 * 60,
                stages = emptyList(),
                source = "x",
            ),
            SleepRecord(
                recordedAt = instant(mon, 23),
                start = instant(mon, 23),
                end = instant(tue, 6),
                durationMinutes = 7 * 60,
                stages = emptyList(),
                source = "x",
            ),
            SleepRecord(
                recordedAt = instant(tue, 14),
                start = instant(tue, 14),
                end = instant(tue, 15),
                durationMinutes = 60, // a nap — must NOT pull averages down
                stages = emptyList(),
                source = "x",
            ),
            SleepRecord(
                recordedAt = instant(tue, 23, 30),
                start = instant(tue, 23, 30),
                end = instant(wed, 6, 30),
                durationMinutes = 7 * 60,
                stages = emptyList(),
                source = "x",
            ),
        )
        val summary = WeeklySummary.from(sleep, rangeForWeek(), zone, stepTarget = 7000)
        // Per-day mapping still includes every record (note: associate keeps the latest
        // ending per wake-day, so the Tue nap is overwritten by the Wed-morning sleep —
        // see SleepSummary.from. Either way the 1h record is not in the averages.)
        assertEquals(7 * 60 + 20L, summary.sleep.weeklyAverageMinutes) // (480 + 420 + 420) / 3
    }

    @Test fun allEmpty_isTrueWhenNothingProvided() {
        val summary = WeeklySummary.from(emptyList(), rangeForWeek(), zone, stepTarget = 7000)
        assertTrue(summary.allEmpty)
    }

    @Test fun isoWeekTagFromMay25_2026_isW22() {
        val summary = WeeklySummary.from(emptyList(), rangeForWeek(), zone, stepTarget = 7000)
        assertEquals("2026-W22", summary.isoWeekTag)
    }
}
