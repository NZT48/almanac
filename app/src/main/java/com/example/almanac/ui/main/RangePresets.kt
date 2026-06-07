package com.example.almanac.ui.main

import java.time.DayOfWeek
import java.time.LocalDate

object RangePresets {

    fun resolve(preset: RangePreset, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
        return when (preset) {
            RangePreset.LAST_WEEK -> lastFullMonSunWeek(today)
            RangePreset.THIS_WEEK_SO_FAR -> {
                val mon = today.with(DayOfWeek.MONDAY)
                mon to today
            }
            RangePreset.LAST_30_DAYS -> today.minusDays(29) to today
            RangePreset.CUSTOM -> error("CUSTOM has no canonical range")
        }
    }

    private fun lastFullMonSunWeek(today: LocalDate): Pair<LocalDate, LocalDate> {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val lastMonday = thisMonday.minusWeeks(1)
        val lastSunday = lastMonday.plusDays(6)
        return lastMonday to lastSunday
    }

    fun matchingPreset(from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now()): RangePreset {
        for (preset in arrayOf(RangePreset.LAST_WEEK, RangePreset.THIS_WEEK_SO_FAR, RangePreset.LAST_30_DAYS)) {
            val (f, t) = resolve(preset, today)
            if (f == from && t == to) return preset
        }
        return RangePreset.CUSTOM
    }
}
