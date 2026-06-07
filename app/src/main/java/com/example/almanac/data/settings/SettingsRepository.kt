package com.example.almanac.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "almanac_settings")

class SettingsRepository(private val context: Context) {

    val dailyStepTarget: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[DAILY_STEP_TARGET_KEY] ?: DEFAULT_DAILY_STEP_TARGET
    }

    suspend fun setDailyStepTarget(value: Int) {
        require(value > 0) { "Step target must be positive" }
        context.settingsDataStore.edit { prefs ->
            prefs[DAILY_STEP_TARGET_KEY] = value
        }
    }

    companion object {
        const val DEFAULT_DAILY_STEP_TARGET = 7000
        private val DAILY_STEP_TARGET_KEY = intPreferencesKey("daily_step_target")
    }
}
