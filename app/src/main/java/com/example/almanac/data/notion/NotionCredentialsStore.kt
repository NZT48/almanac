package com.example.almanac.data.notion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.notionPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "notion_prefs")

data class NotionCredentials(
    val apiKey: String?,
    val databaseUrl: String?,
    val databaseId: String?,
) {
    val isComplete: Boolean
        get() = !apiKey.isNullOrBlank() && !databaseId.isNullOrBlank()
}

class NotionCredentialsStore(private val context: Context) {

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "notion_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val credentials: Flow<NotionCredentials> = combine(
        context.notionPrefsDataStore.data.map { it[DB_URL_KEY] },
        context.notionPrefsDataStore.data.map { it[DB_ID_KEY] },
    ) { url, id ->
        NotionCredentials(
            apiKey = encryptedPrefs.getString(API_KEY, null)?.takeIf { it.isNotBlank() },
            databaseUrl = url,
            databaseId = id,
        )
    }.distinctUntilChanged()

    fun currentApiKey(): String? =
        encryptedPrefs.getString(API_KEY, null)?.takeIf { it.isNotBlank() }

    fun currentDatabaseId(): String? = runCatching {
        // Synchronous read for use cases that already hold the encrypted key
        encryptedPrefs.getString(DB_ID_MIRROR, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    suspend fun setApiKey(value: String) {
        encryptedPrefs.edit().putString(API_KEY, value.trim()).apply()
    }

    suspend fun setDatabaseUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        val id = NotionDatabaseUrl.extractDatabaseId(trimmed)
        context.notionPrefsDataStore.edit { prefs ->
            prefs[DB_URL_KEY] = trimmed
            if (id != null) prefs[DB_ID_KEY] = id else prefs.remove(DB_ID_KEY)
        }
        encryptedPrefs.edit().putString(DB_ID_MIRROR, id ?: "").apply()
    }

    suspend fun clear() {
        encryptedPrefs.edit().clear().apply()
        context.notionPrefsDataStore.edit { it.clear() }
    }

    private companion object {
        const val API_KEY = "notion_api_key"
        const val DB_ID_MIRROR = "notion_db_id_mirror"
        val DB_URL_KEY = stringPreferencesKey("notion_db_url")
        val DB_ID_KEY = stringPreferencesKey("notion_db_id")
    }
}
