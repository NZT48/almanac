package com.example.almanac.data.notion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class NotionResult<out T> {
    data class Success<T>(val value: T) : NotionResult<T>()
    data class Failure(val error: NotionError) : NotionResult<Nothing>()
}

sealed class NotionError(open val message: String) {
    data object MissingCredentials : NotionError("Add your Notion API key in Settings.")
    data object Unauthorized : NotionError("Notion rejected the API key. Check Settings.")
    data object NotFound : NotionError(
        "Almanac can't see this database. Share it with the integration in Notion.",
    )
    data class RateLimited(val retryAfterSeconds: Int?) :
        NotionError("Notion is rate-limiting requests. Try again shortly.")
    data class Network(override val message: String) : NotionError(message)
    data class Unexpected(override val message: String) : NotionError(message)
}

class NotionApi(
    private val credentials: NotionCredentialsStore,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    suspend fun fetchDatabaseInfo(): NotionResult<JsonObject> {
        val (apiKey, dbId) = resolveCreds() ?: return NotionResult.Failure(NotionError.MissingCredentials)
        val request = baseRequest(apiKey)
            .url("$BASE_URL/databases/$dbId")
            .get()
            .build()
        return execute(request)
    }

    suspend fun createPage(properties: JsonObject): NotionResult<JsonObject> {
        val (apiKey, dbId) = resolveCreds() ?: return NotionResult.Failure(NotionError.MissingCredentials)
        val bodyJson = buildJsonObject {
            putJsonObject("parent") { put("database_id", dbId) }
            put("properties", properties)
        }.toString()
        val request = baseRequest(apiKey)
            .url("$BASE_URL/pages")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        return execute(request)
    }

    suspend fun updatePage(pageId: String, properties: JsonObject): NotionResult<JsonObject> {
        val apiKey = credentials.currentApiKey() ?: return NotionResult.Failure(NotionError.MissingCredentials)
        val bodyJson = buildJsonObject { put("properties", properties) }.toString()
        val request = baseRequest(apiKey)
            .url("$BASE_URL/pages/$pageId")
            .patch(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        return execute(request)
    }

    suspend fun queryAllRows(sortNewestFirst: Boolean = false): NotionResult<List<JsonObject>> {
        val (apiKey, dbId) = resolveCreds() ?: return NotionResult.Failure(NotionError.MissingCredentials)
        val rows = mutableListOf<JsonObject>()
        var cursor: String? = null
        while (true) {
            val bodyJson = buildJsonObject {
                put("page_size", 100)
                cursor?.let { put("start_cursor", it) }
                if (sortNewestFirst) {
                    putJsonArray("sorts") {
                        add(
                            buildJsonObject {
                                put("timestamp", "created_time")
                                put("direction", "descending")
                            },
                        )
                    }
                }
            }.toString()
            val request = baseRequest(apiKey)
                .url("$BASE_URL/databases/$dbId/query")
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()

            val page = when (val r = execute(request)) {
                is NotionResult.Failure -> return r
                is NotionResult.Success -> r.value
            }

            (page["results"] as? JsonArray)?.forEach { item ->
                (item as? JsonObject)?.let(rows::add)
            }

            val hasMore = page["has_more"]?.jsonPrimitive?.booleanOrNull == true
            cursor = page["next_cursor"]?.jsonPrimitive?.contentOrNull
            if (!hasMore || cursor.isNullOrBlank()) return NotionResult.Success(rows)
        }
    }

    private fun resolveCreds(): Pair<String, String>? {
        val apiKey = credentials.currentApiKey() ?: return null
        val dbId = credentials.currentDatabaseId() ?: return null
        return apiKey to dbId
    }

    private fun baseRequest(apiKey: String): Request.Builder =
        Request.Builder()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Notion-Version", NOTION_VERSION)
            .addHeader("Content-Type", "application/json")

    private suspend fun execute(request: Request): NotionResult<JsonObject> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { resp ->
                    val bodyString = resp.body?.string().orEmpty()
                    when (resp.code) {
                        in 200..299 -> runCatching { json.parseToJsonElement(bodyString).jsonObject }
                            .fold(
                                onSuccess = { NotionResult.Success(it) },
                                onFailure = {
                                    NotionResult.Failure(NotionError.Unexpected("Malformed Notion response."))
                                },
                            )
                        400 -> NotionResult.Failure(
                            NotionError.Unexpected(notionMessage(bodyString) ?: "Notion rejected the request."),
                        )
                        401 -> NotionResult.Failure(NotionError.Unauthorized)
                        404 -> NotionResult.Failure(NotionError.NotFound)
                        429 -> NotionResult.Failure(
                            NotionError.RateLimited(resp.header("Retry-After")?.toIntOrNull()),
                        )
                        else -> NotionResult.Failure(
                            NotionError.Unexpected(
                                notionMessage(bodyString) ?: "Notion returned ${resp.code}.",
                            ),
                        )
                    }
                }
            } catch (e: IOException) {
                NotionResult.Failure(NotionError.Network(e.message ?: "Network failure."))
            }
        }

    private fun notionMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    companion object {
        const val BASE_URL = "https://api.notion.com/v1"
        const val NOTION_VERSION = "2022-06-28"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
