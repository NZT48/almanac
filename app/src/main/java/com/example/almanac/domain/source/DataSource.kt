package com.example.almanac.domain.source

import com.example.almanac.domain.model.SleepRecord
import com.example.almanac.domain.model.StepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.model.WeightRecord

interface DataSource {
    suspend fun readSteps(range: TimeRange): List<StepRecord>
    suspend fun readWeight(range: TimeRange): List<WeightRecord>
    suspend fun readSleep(range: TimeRange): List<SleepRecord>
}
