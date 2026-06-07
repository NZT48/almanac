package com.example.almanac.data.file

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a [WeeklySummary] as the human-readable digest documented in §8.
 * The formatter is pure: same summary input → same text output. The on-screen
 * preview uses the same [WeeklySummary] so the file and the panel cannot drift.
 */
class WeeklyDigestFormatter {

    fun format(summary: WeeklySummary, generatedAt: Instant = Instant.now()): String = buildString {
        val firstDay = summary.days.first()
        val lastDay = summary.days.last()
        appendLine("ALMANAC — Week ${summary.isoWeekTag}  (${DAY_HEADER.format(firstDay)} — ${DAY_HEADER.format(lastDay)})")
        appendLine("generated:    ${ISO_OFFSET.format(generatedAt.atZone(summary.zoneId))}")
        appendLine("zone:         ${summary.zoneId}")
        appendLine("source:       health_connect")
        appendLine("step target:  ${"%,d".format(summary.stepTarget)}")
        appendLine()
        appendSteps(summary)
        appendLine()
        appendWeight(summary)
        appendLine()
        appendSleep(summary)
    }

    private fun StringBuilder.appendSteps(summary: WeeklySummary) {
        appendLine("== STEPS ==")
        val s = summary.steps
        if (s.isEmpty) {
            appendLine("(no records)")
            return
        }
        for (day in summary.days) {
            val total = s.perDayTotal[day]
            val label = "${shortWeekday(day)} $day"
            if (total == null) {
                appendLine("%-19s   —".format(label))
            } else {
                appendLine("%-19s %s".format(label, "%,d".format(total).padStart(7)))
            }
        }
        appendLine()
        appendLine("%-19s %s".format("weekly total", "%,d".format(s.weeklyTotal).padStart(10)))
        appendLine("%-19s %s".format("daily average", "%,d".format(s.dailyAverage).padStart(10)))
        appendLine("%-19s %d / %d".format("days target hit", s.daysTargetHit, s.daysInWeek))
    }

    private fun StringBuilder.appendWeight(summary: WeeklySummary) {
        appendLine("== WEIGHT ==")
        val w = summary.weight
        if (w.isEmpty) {
            appendLine("(no records)")
            return
        }
        for (r in w.readings) {
            appendLine("%-19s %5.1f kg".format("${shortWeekday(r.date)} ${r.date}", r.kilograms))
        }
        if (w.weekDeltaKg != null) {
            appendLine()
            val delta = w.weekDeltaKg
            val sign = if (delta >= 0) "+" else "−"
            appendLine("%-19s %s%.1f kg   (first vs last reading)".format("week delta", sign, kotlin.math.abs(delta)))
        }
        if (w.weeklyAverageKg != null) {
            appendLine("%-19s %5.1f kg   (%d readings)".format("weekly average", w.weeklyAverageKg, w.readings.size))
        }
    }

    private fun StringBuilder.appendSleep(summary: WeeklySummary) {
        appendLine("== SLEEP ==")
        val s = summary.sleep
        if (s.isEmpty) {
            appendLine("(no records)")
            return
        }
        appendLine("(each row is bucketed by wake-up time — see §7.1)")
        for (day in summary.days) {
            val night = s.perWakeDay[day]
            if (night == null) {
                appendLine("%s wake %s   —".format(shortWeekday(day), day))
            } else {
                appendLine(
                    "%s wake %s  %s   %s".format(
                        shortWeekday(day),
                        day,
                        TIME_FMT.format(night.wakeTime),
                        formatDurationMinutes(night.durationMinutes),
                    )
                )
            }
        }
        appendLine()
        s.weeklyAverageMinutes?.let {
            appendLine("%-19s %s".format("weekly average", formatDurationMinutes(it)))
        }
        s.averageBedtime?.let {
            appendLine("%-19s %s".format("average bedtime", TIME_FMT.format(it)))
        }
        s.averageWakeTime?.let {
            appendLine("%-19s %s".format("average wake time", TIME_FMT.format(it)))
        }
        if (s.totalDeepMinutes > 0) {
            appendLine("%-19s %s".format("total deep", formatDurationMinutes(s.totalDeepMinutes)))
        }
        if (s.totalRemMinutes > 0) {
            appendLine("%-19s %s".format("total REM", formatDurationMinutes(s.totalRemMinutes)))
        }
    }

    private fun shortWeekday(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)

    companion object {
        private val DAY_HEADER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.ENGLISH)
        private val ISO_OFFSET: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
