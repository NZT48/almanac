package com.example.almanac.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Half-open time range `[start, end)`. The UI presents an inclusive
 * day-level range (`Mon Jun 1 .. Sun Jun 7`) but the use cases work in
 * Instants and require the end to be the exclusive next-day midnight,
 * which kills a class of one-second-gap bugs at the boundary.
 */
data class TimeRange(val start: Instant, val end: Instant) {
    companion object {
        fun fromInclusiveDates(
            from: LocalDate,
            to: LocalDate,
            zoneId: ZoneId,
        ): TimeRange {
            val startInstant = from.atStartOfDay(zoneId).toInstant()
            val endInstant = to.plusDays(1).atStartOfDay(zoneId).toInstant()
            return TimeRange(startInstant, endInstant)
        }
    }
}
