package com.example.almanac.data.notion

import com.example.almanac.domain.model.NewPhysicalEntryDraft
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Encodes a draft as the Notion `properties` payload for `POST /v1/pages`
 * (create) or `PATCH /v1/pages/{id}` (update).
 *
 * For create, blank/null fields are omitted so Notion leaves them unset.
 * For update, every column is sent — blanks become explicit nulls/empties
 * so a user clearing a field actually clears it in Notion. Omitting a
 * property on PATCH would leave the existing value untouched.
 */
class PhysicalEntryWriter(private val api: NotionApi) {

    suspend fun create(draft: NewPhysicalEntryDraft): NotionResult<Unit> {
        val schema = loadSchema() ?: return NotionResult.Failure(
            NotionError.Unexpected("Notion returned no schema."),
        )
        val properties = buildJsonObject { writeColumns(draft, schema, this, includeBlanks = false) }
        return when (val r = api.createPage(properties)) {
            is NotionResult.Failure -> r
            is NotionResult.Success -> NotionResult.Success(Unit)
        }
    }

    suspend fun update(entryId: String, draft: NewPhysicalEntryDraft): NotionResult<Unit> {
        val schema = loadSchema() ?: return NotionResult.Failure(
            NotionError.Unexpected("Notion returned no schema."),
        )
        val properties = buildJsonObject { writeColumns(draft, schema, this, includeBlanks = true) }
        return when (val r = api.updatePage(entryId, properties)) {
            is NotionResult.Failure -> r
            is NotionResult.Success -> NotionResult.Success(Unit)
        }
    }

    private suspend fun loadSchema(): JsonObject? =
        when (val r = api.fetchDatabaseInfo()) {
            is NotionResult.Failure -> null
            is NotionResult.Success -> r.value["properties"] as? JsonObject
        }

    private fun writeColumns(
        draft: NewPhysicalEntryDraft,
        schema: JsonObject,
        out: JsonObjectBuilder,
        includeBlanks: Boolean,
    ) {
        encodeText(draft.kpi, COL_KPI, schema, out, includeBlanks)
        encodeText(draft.weekLabel, COL_WEEK, schema, out, includeBlanks)
        encodeText(draft.avgSleep, COL_AVG_SLEEP, schema, out, includeBlanks)
        encodeText(draft.bedTime, COL_BED_TIME, schema, out, includeBlanks)
        encodeText(draft.wakeUp, COL_WAKE_UP, schema, out, includeBlanks)
        encodeText(draft.sport, COL_SPORT, schema, out, includeBlanks)
        encodeText(draft.notes, COL_NOTES, schema, out, includeBlanks)
        encodeNumber(draft.avgSteps?.toDouble(), COL_AVG_STEPS, schema, out, includeBlanks)
        encodeNumber(draft.stepTargetDaysHit?.toDouble(), COL_STEP_TARGET, schema, out, includeBlanks)
        encodeNumber(draft.weight, COL_WEIGHT, schema, out, includeBlanks)
        encodeNumber(draft.flaster?.toDouble(), COL_FLASTER, schema, out, includeBlanks)
        encodeNumber(draft.noEith?.toDouble(), COL_NO_EITH, schema, out, includeBlanks)
        encodeNumber(draft.noSugar?.toDouble(), COL_NO_SUGAR, schema, out, includeBlanks)
    }

    private fun encodeText(
        value: String,
        column: String,
        schema: JsonObject,
        out: JsonObjectBuilder,
        includeBlanks: Boolean,
    ) {
        val type = schema.columnType(column) ?: return
        val blank = value.isBlank()
        if (blank && !includeBlanks) return
        when (type) {
            "title" -> out.putJsonObject(column) {
                putJsonArray("title") {
                    if (!blank) addJsonObject {
                        putJsonObject("text") { put("content", value) }
                    }
                }
            }
            "rich_text" -> out.putJsonObject(column) {
                putJsonArray("rich_text") {
                    if (!blank) addJsonObject {
                        putJsonObject("text") { put("content", value) }
                    }
                }
            }
            else -> { /* unsupported column type for text; skip */ }
        }
    }

    private fun encodeNumber(
        value: Double?,
        column: String,
        schema: JsonObject,
        out: JsonObjectBuilder,
        includeBlanks: Boolean,
    ) {
        val type = schema.columnType(column) ?: return
        if (type != "number") return
        if (value == null && !includeBlanks) return
        out.putJsonObject(column) {
            if (value == null) put("number", JsonNull) else put("number", JsonPrimitive(value))
        }
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
