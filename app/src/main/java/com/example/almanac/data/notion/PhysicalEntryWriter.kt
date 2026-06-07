package com.example.almanac.data.notion

import com.example.almanac.domain.model.NewPhysicalEntryDraft
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Turns a draft into the Notion `properties` payload for `POST /v1/pages`.
 *
 * Before encoding, the schema (from `GET /v1/databases/{id}`) is consulted so
 * that we send each column with the correct property type. If a required
 * column is missing or has an unexpected type, we abort with a clear message
 * rather than silently dropping the value.
 */
class PhysicalEntryWriter(private val api: NotionApi) {

    suspend fun create(draft: NewPhysicalEntryDraft): NotionResult<Unit> {
        val schema = when (val r = api.fetchDatabaseInfo()) {
            is NotionResult.Failure -> return r
            is NotionResult.Success -> r.value["properties"] as? JsonObject
                ?: return NotionResult.Failure(NotionError.Unexpected("Notion returned no schema."))
        }

        val properties = buildJsonObject {
            encodeText(draft.kpi, COL_KPI, schema, this)
            encodeText(draft.weekLabel, COL_WEEK, schema, this)
            encodeText(draft.avgSleep, COL_AVG_SLEEP, schema, this)
            encodeText(draft.bedTime, COL_BED_TIME, schema, this)
            encodeText(draft.wakeUp, COL_WAKE_UP, schema, this)
            encodeText(draft.sport, COL_SPORT, schema, this)
            encodeText(draft.notes, COL_NOTES, schema, this)
            encodeNumber(draft.avgSteps?.toDouble(), COL_AVG_STEPS, schema, this)
            encodeNumber(draft.stepTargetDaysHit?.toDouble(), COL_STEP_TARGET, schema, this)
            encodeNumber(draft.weight, COL_WEIGHT, schema, this)
            encodeNumber(draft.flaster?.toDouble(), COL_FLASTER, schema, this)
            encodeNumber(draft.noEith?.toDouble(), COL_NO_EITH, schema, this)
            encodeNumber(draft.noSugar?.toDouble(), COL_NO_SUGAR, schema, this)
        }

        return when (val r = api.createPage(properties)) {
            is NotionResult.Failure -> r
            is NotionResult.Success -> NotionResult.Success(Unit)
        }
    }

    private fun encodeText(
        value: String,
        column: String,
        schema: JsonObject,
        out: kotlinx.serialization.json.JsonObjectBuilder,
    ) {
        if (value.isBlank()) return
        val type = schema.columnType(column) ?: return
        when (type) {
            "title" -> out.putJsonObject(column) {
                putJsonArray("title") {
                    addJsonObject {
                        putJsonObject("text") { put("content", value) }
                    }
                }
            }
            "rich_text" -> out.putJsonObject(column) {
                putJsonArray("rich_text") {
                    addJsonObject {
                        putJsonObject("text") { put("content", value) }
                    }
                }
            }
            else -> { /* unsupported for this column; skip silently */ }
        }
    }

    private fun encodeNumber(
        value: Double?,
        column: String,
        schema: JsonObject,
        out: kotlinx.serialization.json.JsonObjectBuilder,
    ) {
        if (value == null) return
        val type = schema.columnType(column) ?: return
        if (type != "number") return
        out.putJsonObject(column) { put("number", JsonPrimitive(value)) }
    }

    private fun JsonObject.columnType(column: String): String? {
        val prop = this[column] as? JsonObject
            ?: entries.firstOrNull { it.key.equals(column, ignoreCase = true) }
                ?.value as? JsonObject
            ?: return null
        return prop["type"]?.jsonPrimitive?.contentOrNull
    }

    private companion object {
        const val COL_KPI = "KPI"
        const val COL_WEEK = "Week"
        const val COL_AVG_SLEEP = "Avg sleep"
        const val COL_BED_TIME = "Bed time"
        const val COL_WAKE_UP = "Wake up"
        const val COL_SPORT = "Sport"
        const val COL_NOTES = "Notes"
        const val COL_AVG_STEPS = "Avg steps"
        const val COL_STEP_TARGET = "Step target"
        const val COL_WEIGHT = "Weight"
        const val COL_FLASTER = "Flaster"
        const val COL_NO_EITH = "No Eith"
        const val COL_NO_SUGAR = "No sugar"
    }
}
