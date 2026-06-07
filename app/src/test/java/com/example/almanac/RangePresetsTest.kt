package com.example.almanac

import com.example.almanac.ui.main.RangePreset
import com.example.almanac.ui.main.RangePresets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RangePresetsTest {

    @Test fun lastWeek_whenTodayIsWednesday_returnsPriorMonToSun() {
        // 2026-06-03 is a Wednesday. Previous Mon-Sun is 2026-05-25 .. 2026-05-31.
        val today = LocalDate.of(2026, 6, 3)
        val (from, to) = RangePresets.resolve(RangePreset.LAST_WEEK, today)
        assertEquals(LocalDate.of(2026, 5, 25), from)
        assertEquals(LocalDate.of(2026, 5, 31), to)
    }

    @Test fun lastWeek_whenTodayIsMonday_stillReturnsPriorWeek() {
        val today = LocalDate.of(2026, 6, 1) // Monday
        val (from, to) = RangePresets.resolve(RangePreset.LAST_WEEK, today)
        assertEquals(LocalDate.of(2026, 5, 25), from)
        assertEquals(LocalDate.of(2026, 5, 31), to)
    }

    @Test fun lastWeek_whenTodayIsSunday_returnsThePreviousFullWeek() {
        val today = LocalDate.of(2026, 6, 7) // Sunday
        val (from, to) = RangePresets.resolve(RangePreset.LAST_WEEK, today)
        assertEquals(LocalDate.of(2026, 5, 25), from)
        assertEquals(LocalDate.of(2026, 5, 31), to)
    }

    @Test fun thisWeekSoFar_runsFromMondayThroughToday() {
        val today = LocalDate.of(2026, 6, 3) // Wednesday
        val (from, to) = RangePresets.resolve(RangePreset.THIS_WEEK_SO_FAR, today)
        assertEquals(LocalDate.of(2026, 6, 1), from)
        assertEquals(today, to)
    }

    @Test fun last30Days_isInclusive() {
        val today = LocalDate.of(2026, 6, 3)
        val (from, to) = RangePresets.resolve(RangePreset.LAST_30_DAYS, today)
        assertEquals(LocalDate.of(2026, 5, 5), from)
        assertEquals(today, to)
    }

    @Test fun matchingPreset_recognisesLastWeek() {
        val today = LocalDate.of(2026, 6, 3)
        assertEquals(
            RangePreset.LAST_WEEK,
            RangePresets.matchingPreset(
                LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 5, 31),
                today,
            ),
        )
    }

    @Test fun matchingPreset_unknownRangeIsCustom() {
        val today = LocalDate.of(2026, 6, 3)
        assertEquals(
            RangePreset.CUSTOM,
            RangePresets.matchingPreset(
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 5, 30),
                today,
            ),
        )
    }
}
