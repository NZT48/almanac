package com.example.almanac

import com.example.almanac.data.file.WeeklyDigestFormatter
import com.example.almanac.data.file.WeeklySummary
import com.example.almanac.domain.model.StepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.model.WeightRecord
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyDigestFormatterTest {

    private val zone: ZoneId = ZoneId.of("Europe/Belgrade")

    private fun instant(date: LocalDate, h: Int, m: Int = 0): Instant =
        ZonedDateTime.of(date, LocalTime.of(h, m), zone).toInstant()

    @Test fun renderedDigest_includesHeaderAndSectionTitlesInOrder() {
        val mon = LocalDate.of(2026, 5, 25)
        val records = listOf(
            StepRecord(instant(mon, 9), instant(mon, 9), instant(mon, 9, 30), 8421, "x"),
            WeightRecord(instant(mon.plusDays(1), 8), 82.4, "x"),
        )
        val range = TimeRange.fromInclusiveDates(mon, mon.plusDays(6), zone)
        val summary = WeeklySummary.from(records, range, zone, stepTarget = 7000)

        val generatedAt = ZonedDateTime.of(2026, 6, 1, 10, 23, 14, 0, zone).toInstant()
        val text = WeeklyDigestFormatter().format(summary, generatedAt)

        assertTrue("header missing", text.contains("ALMANAC — Week 2026-W22"))
        assertTrue("zone missing", text.contains("zone:         Europe/Belgrade"))
        assertTrue("step target missing", text.contains("step target:  7,000"))
        assertTrue("steps section missing", text.contains("== STEPS =="))
        assertTrue("weight section missing", text.contains("== WEIGHT =="))
        assertTrue("sleep section missing", text.contains("== SLEEP =="))
        assertTrue("monday step total missing", text.contains("Mon 2026-05-25"))
        assertTrue("days target hit missing", text.contains("days target hit"))

        // Sections must appear in canonical order: STEPS < WEIGHT < SLEEP.
        val stepsIdx = text.indexOf("== STEPS ==")
        val weightIdx = text.indexOf("== WEIGHT ==")
        val sleepIdx = text.indexOf("== SLEEP ==")
        assertTrue(stepsIdx in 0 until weightIdx)
        assertTrue(weightIdx < sleepIdx)
    }

    @Test fun emptyAllTypes_stillEmitsAllSectionHeadersWithNoRecordsBody() {
        val range = TimeRange.fromInclusiveDates(
            LocalDate.of(2026, 5, 25),
            LocalDate.of(2026, 5, 31),
            zone,
        )
        val summary = WeeklySummary.from(emptyList(), range, zone, stepTarget = 7000)
        val text = WeeklyDigestFormatter().format(summary)

        assertTrue(text.contains("== STEPS =="))
        assertTrue(text.contains("== WEIGHT =="))
        assertTrue(text.contains("== SLEEP =="))
        // Each section body should be "(no records)"
        val noRecordsCount = "(no records)".toRegex().findAll(text).count()
        assertTrue("expected 3 (no records) entries, got $noRecordsCount", noRecordsCount == 3)
    }
}
