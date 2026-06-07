package com.example.almanac.data.notion

import com.example.almanac.domain.model.SleepRecord
import com.example.almanac.domain.model.StepRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.model.WeightRecord
import com.example.almanac.domain.source.DataSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Reads rows from the configured Notion *Physical* database and maps them to
 * neutral [com.example.almanac.domain.model.HealthRecord]s.
 *
 * Until the exact column schema of *Physical* is locked in, this mapper looks
 * for the conventional names below and silently ignores rows missing them:
 *   - "Date"   (date)
 *   - "Steps"  (number)
 *   - "Weight" (number, kilograms)
 *   - "Sleep"  (number, hours)
 *
 * Column names are matched case-insensitively. Update this file when the real
 * schema is decided.
 */
class NotionSource(
    private val api: NotionApi,
) : DataSource {

    override suspend fun readSteps(range: TimeRange): List<StepRecord> =
        rowsInRange(range).mapNotNull { (date, props) ->
            val count = numberProperty(props, "Steps")?.toLong() ?: return@mapNotNull null
            val start = date.atStartOfDay(UTC).toInstant()
            val end = date.plusDays(1).atStartOfDay(UTC).toInstant()
            StepRecord(
                recordedAt = start,
                periodStart = start,
                periodEnd = end,
                count = count,
                source = SOURCE_NAME,
            )
        }

    override suspend fun readWeight(range: TimeRange): List<WeightRecord> =
        rowsInRange(range).mapNotNull { (date, props) ->
            val kg = numberProperty(props, "Weight") ?: return@mapNotNull null
            WeightRecord(
                recordedAt = date.atStartOfDay(UTC).toInstant(),
                kilograms = kg,
                source = SOURCE_NAME,
            )
        }

    override suspend fun readSleep(range: TimeRange): List<SleepRecord> =
        rowsInRange(range).mapNotNull { (date, props) ->
            val hours = numberProperty(props, "Sleep") ?: return@mapNotNull null
            val start = date.atStartOfDay(UTC).toInstant()
            val durationMinutes = (hours * 60.0).toLong()
            SleepRecord(
                recordedAt = start,
                start = start,
                end = start.plusSeconds(durationMinutes * 60),
                durationMinutes = durationMinutes,
                source = SOURCE_NAME,
            )
        }

    private suspend fun rowsInRange(range: TimeRange): List<Pair<LocalDate, JsonObject>> {
        val rows = when (val r = api.queryAllRows()) {
            is NotionResult.Failure -> emptyList()
            is NotionResult.Success -> r.value
        }
        return rows.mapNotNull { row ->
            val props = row["properties"] as? JsonObject ?: return@mapNotNull null
            val date = dateProperty(props, "Date") ?: return@mapNotNull null
            val instant = date.atStartOfDay(UTC).toInstant()
            if (instant >= range.start && instant < range.end) date to props else null
        }
    }

    private fun findProperty(props: JsonObject, name: String): JsonObject? {
        props[name]?.let { return it as? JsonObject }
        return props.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value as? JsonObject
    }

    private fun numberProperty(props: JsonObject, name: String): Double? {
        val prop = findProperty(props, name) ?: return null
        return prop["number"]?.jsonPrimitive?.doubleOrNull
    }

    private fun dateProperty(props: JsonObject, name: String): LocalDate? {
        val prop = findProperty(props, name) ?: return null
        val raw = (prop["date"] as? JsonObject)
            ?.get("start")?.jsonPrimitive?.contentOrNull
            ?: return null
        return try {
            // Notion may emit either an ISO date ("2025-06-04") or a datetime.
            if (raw.length == 10) LocalDate.parse(raw)
            else Instant.parse(raw).atZone(UTC).toLocalDate()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private companion object {
        const val SOURCE_NAME = "notion"
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
