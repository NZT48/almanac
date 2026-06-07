package com.example.almanac.data.file

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.almanac.domain.model.HealthRecord
import com.example.almanac.domain.model.TimeRange
import com.example.almanac.domain.sink.DataSink
import com.example.almanac.domain.sink.SinkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Writes the weekly digest to public `Downloads/` via MediaStore.
 *
 * Spec §7.2 atomic write protocol:
 *  1. compute ISO-week filename
 *  2. find existing row (overwrite) or insert new with IS_PENDING=1
 *  3. truncate-write
 *  4. flip IS_PENDING=0 on success; on failure delete newly-inserted row.
 */
class DownloadsTextSink(
    private val context: Context,
    private val formatter: WeeklyDigestFormatter,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : DataSink {

    override suspend fun write(
        records: List<HealthRecord>,
        range: TimeRange,
        stepTarget: Int,
    ): SinkResult = withContext(Dispatchers.IO) {
        try {
            val zoneId = zoneIdProvider()
            val summary = WeeklySummary.from(records, range, zoneId, stepTarget)
            val text = formatter.format(summary)
            val displayName = "almanac-${summary.isoWeekTag}.txt"

            val (uri, insertedNew) = findOrInsert(displayName)
                ?: return@withContext SinkResult.Failure("Could not create Downloads entry")

            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: throw IllegalStateException("openOutputStream returned null")

                // Flip IS_PENDING off so the file becomes visible.
                val finishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, finishValues, null, null)
                SinkResult.Success(
                    displayName = displayName,
                    locationLabel = "Downloads/$displayName",
                )
            } catch (t: Throwable) {
                if (insertedNew) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                SinkResult.Failure(t.message ?: "Write failed")
            }
        } catch (t: Throwable) {
            SinkResult.Failure(t.message ?: "Unknown error")
        }
    }

    private fun findOrInsert(displayName: String): Pair<Uri, Boolean>? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val resolver = context.contentResolver

        val existingUri = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(displayName, "${Environment.DIRECTORY_DOWNLOADS}/"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                ContentUris.withAppendedId(collection, id)
            } else null
        }

        if (existingUri != null) {
            // Re-pending while we overwrite, so a crashed re-write hides the partial.
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.update(existingUri, values, null, null)
            return existingUri to false
        }

        val newValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val inserted = resolver.insert(collection, newValues) ?: return null
        return inserted to true
    }
}
