package com.example.almanac.data.notion

import com.example.almanac.domain.model.PhysicalEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException

class NotionPhysicalRepository(private val api: NotionApi) {

    private val cache = mutableMapOf<String, PhysicalEntry>()

    suspend fun loadLatestFirst(): NotionResult<List<PhysicalEntry>> =
        when (val r = api.queryAllRows(sortNewestFirst = true)) {
            is NotionResult.Failure -> r
            is NotionResult.Success -> {
                val entries = r.value.mapNotNull(::toEntry)
                cache.clear()
                entries.forEach { cache[it.id] = it }
                NotionResult.Success(entries)
            }
        }

    fun cached(id: String): PhysicalEntry? = cache[id]

    private fun toEntry(page: JsonObject): PhysicalEntry? {
        val id = page["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val createdAt = page["created_time"]?.jsonPrimitive?.contentOrNull?.let { raw ->
            try {
                Instant.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: Instant.EPOCH
        val props = page["properties"] as? JsonObject ?: return null

        return PhysicalEntry(
            id = id,
            createdAt = createdAt,
            kpi = textProperty(props, "KPI").orEmpty(),
            weekLabel = textProperty(props, "Week"),
            weight = numberProperty(props, "Weight"),
            avgSteps = numberProperty(props, "Avg steps")?.toLong(),
            stepTarget = numberProperty(props, "Step target")?.toLong(),
            avgSleep = textProperty(props, "Avg sleep"),
            bedTime = textProperty(props, "Bed time"),
            wakeUp = textProperty(props, "Wake up"),
            sport = textProperty(props, "Sport"),
            notes = textProperty(props, "Notes"),
            flaster = numberProperty(props, "Flaster")?.toLong(),
            noEith = numberProperty(props, "No Eith")?.toLong(),
            noSugar = numberProperty(props, "No sugar")?.toLong(),
        )
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

    /**
     * Pulls plain text out of whichever shape Notion uses for the column:
     * title / rich_text / select / multi_select / date / formula / number.
     */
    private fun textProperty(props: JsonObject, name: String): String? {
        val prop = findProperty(props, name) ?: return null

        (prop["title"] as? JsonArray)?.let { spans ->
            return joinSpans(spans).takeIf { it.isNotEmpty() }
        }
        (prop["rich_text"] as? JsonArray)?.let { spans ->
            return joinSpans(spans).takeIf { it.isNotEmpty() }
        }
        (prop["select"] as? JsonObject)?.let { sel ->
            return sel["name"]?.jsonPrimitive?.contentOrNull
        }
        (prop["multi_select"] as? JsonArray)?.let { items ->
            val names = items.mapNotNull {
                (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            }
            return names.joinToString(", ").takeIf { it.isNotEmpty() }
        }
        (prop["date"] as? JsonObject)?.let { date ->
            return date["start"]?.jsonPrimitive?.contentOrNull
        }
        (prop["formula"] as? JsonObject)?.let { formula ->
            return formula["string"]?.jsonPrimitive?.contentOrNull
                ?: formula["number"]?.jsonPrimitive?.contentOrNull
        }
        return prop["number"]?.jsonPrimitive?.contentOrNull
    }

    private fun joinSpans(spans: JsonArray): String =
        spans.joinToString("") { span ->
            (span as? JsonObject)?.get("plain_text")?.jsonPrimitive?.contentOrNull.orEmpty()
        }
}
