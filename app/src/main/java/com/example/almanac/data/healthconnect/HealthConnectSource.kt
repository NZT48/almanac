package com.example.almanac.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord as HcWeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.almanac.domain.model.SleepRecord
import com.example.almanac.domain.model.SleepStage
import com.example.almanac.domain.model.StepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.model.WeightRecord
import com.example.almanac.domain.source.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectSource(
    private val client: HealthConnectClient,
) : DataSource {

    /**
     * Daily step totals via Health Connect's aggregation API. We use
     * [HealthConnectClient.aggregateGroupByPeriod] with [StepsRecord.COUNT_TOTAL] rather than
     * summing raw `StepsRecord`s because Samsung Health typically writes both
     * minute-level entries **and** overlapping roll-up entries for the same
     * window. Summing the raw records double-counts; the aggregation API
     * dedupes by data-origin priority and overlap.
     */
    override suspend fun readSteps(range: TimeRange): List<StepRecord> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val localStart = range.start.atZone(zone).toLocalDateTime()
        val localEnd = range.end.atZone(zone).toLocalDateTime()
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(localStart, localEnd),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        response.mapNotNull { group ->
            val count = group.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
            val bucketStart = group.startTime.atZone(zone).toInstant()
            val bucketEnd = group.endTime.atZone(zone).toInstant()
            StepRecord(
                recordedAt = bucketStart,
                periodStart = bucketStart,
                periodEnd = bucketEnd,
                count = count,
                source = SOURCE_NAME,
            )
        }
    }

    override suspend fun readWeight(range: TimeRange): List<WeightRecord> = withContext(Dispatchers.IO) {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HcWeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(range.start, range.end),
            )
        )
        response.records.map { rec ->
            WeightRecord(
                recordedAt = rec.time,
                kilograms = rec.weight.inKilograms,
                source = SOURCE_NAME,
            )
        }
    }

    /**
     * Sleep is bucketed by **wake-up time** (`endTime`), not start time.
     * We widen the query by 24h on the lower bound to catch sessions that
     * started before the range but ended inside it, then post-filter in code
     * to `endTime ∈ [range.start, range.end)`. See spec §7.1.
     */
    override suspend fun readSleep(range: TimeRange): List<SleepRecord> = withContext(Dispatchers.IO) {
        val widened = TimeRangeFilter.between(
            range.start.minus(Duration.ofDays(1)),
            range.end,
        )
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = widened,
            )
        )
        response.records
            .filter { rec ->
                rec.endTime >= range.start && rec.endTime < range.end
            }
            .map { rec ->
                val duration = ChronoUnit.MINUTES.between(rec.startTime, rec.endTime)
                SleepRecord(
                    recordedAt = rec.startTime,
                    start = rec.startTime,
                    end = rec.endTime,
                    durationMinutes = duration,
                    stages = rec.stages.map { st ->
                        SleepStage(
                            start = st.startTime,
                            end = st.endTime,
                            type = mapStageType(st.stage),
                        )
                    },
                    source = SOURCE_NAME,
                )
            }
    }

    private fun mapStageType(stage: Int): SleepStage.StageType = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.StageType.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.StageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.StageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.StageType.REM
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.StageType.OUT_OF_BED
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.StageType.SLEEPING
        else -> SleepStage.StageType.UNKNOWN
    }

    companion object {
        const val SOURCE_NAME = "health_connect"

        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HcWeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )
    }
}
