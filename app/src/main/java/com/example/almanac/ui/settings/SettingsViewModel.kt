package com.example.almanac.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.almanac.AlmanacApplication
import com.example.almanac.data.notion.NotionApi
import com.example.almanac.data.notion.NotionCredentialsStore
import com.example.almanac.data.notion.NotionDatabaseUrl
import com.example.almanac.data.notion.NotionResult
import com.example.almanac.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val notionCredentials: NotionCredentialsStore,
    private val notionApi: NotionApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(loaded = false))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stepTarget = settingsRepository.dailyStepTarget.first()
            val creds = notionCredentials.credentials.first()
            _state.value = SettingsUiState(
                loaded = true,
                dailyStepTargetField = stepTarget.toString(),
                savedDailyStepTarget = stepTarget,
                notionApiKeyField = creds.apiKey.orEmpty(),
                notionDatabaseUrlField = creds.databaseUrl.orEmpty(),
                savedNotionDatabaseId = creds.databaseId,
            )
        }
    }

    fun onStepTargetChanged(value: String) {
        _state.value = _state.value.copy(
            dailyStepTargetField = value.filter(Char::isDigit).take(6),
            errorMessage = null,
        )
    }

    fun onNotionApiKeyChanged(value: String) {
        _state.value = _state.value.copy(
            notionApiKeyField = value,
            notionMessage = null,
        )
    }

    fun onNotionDatabaseUrlChanged(value: String) {
        _state.value = _state.value.copy(
            notionDatabaseUrlField = value,
            notionMessage = null,
        )
    }

    fun toggleApiKeyVisibility() {
        _state.value = _state.value.copy(notionApiKeyVisible = !_state.value.notionApiKeyVisible)
    }

    fun save() {
        val current = _state.value
        val parsed = current.dailyStepTargetField.toIntOrNull()
        if (parsed == null || parsed <= 0) {
            _state.value = current.copy(errorMessage = "Enter a positive number")
            return
        }
        viewModelScope.launch {
            settingsRepository.setDailyStepTarget(parsed)
            _state.value = _state.value.copy(
                savedDailyStepTarget = parsed,
                errorMessage = null,
                savedTick = _state.value.savedTick + 1,
            )
        }
    }

    fun saveNotionCredentials() {
        val current = _state.value
        val urlField = current.notionDatabaseUrlField.trim()
        val keyField = current.notionApiKeyField.trim()
        val parsedId = if (urlField.isBlank()) null else NotionDatabaseUrl.extractDatabaseId(urlField)
        if (urlField.isNotBlank() && parsedId == null) {
            _state.value = current.copy(
                notionMessage = NotionMessage.Error("Couldn't find a database ID in that URL."),
            )
            return
        }
        viewModelScope.launch {
            if (keyField.isNotBlank()) notionCredentials.setApiKey(keyField)
            notionCredentials.setDatabaseUrl(urlField)
            _state.value = _state.value.copy(
                savedNotionDatabaseId = parsedId,
                notionMessage = NotionMessage.Info("Saved."),
            )
        }
    }

    fun testNotionConnection() {
        _state.value = _state.value.copy(
            notionTesting = true,
            notionMessage = null,
        )
        viewModelScope.launch {
            val result = notionApi.fetchDatabaseInfo()
            _state.value = _state.value.copy(
                notionTesting = false,
                notionMessage = when (result) {
                    is NotionResult.Success -> NotionMessage.Info("Connection OK.")
                    is NotionResult.Failure -> NotionMessage.Error(result.error.message)
                },
            )
        }
    }

    fun clearNotionCredentials() {
        viewModelScope.launch {
            notionCredentials.clear()
            _state.value = _state.value.copy(
                notionApiKeyField = "",
                notionDatabaseUrlField = "",
                savedNotionDatabaseId = null,
                notionMessage = NotionMessage.Info("Notion credentials cleared."),
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AlmanacApplication
                SettingsViewModel(
                    settingsRepository = app.container.settingsRepository,
                    notionCredentials = app.container.notionCredentials,
                    notionApi = app.container.notionApi,
                )
            }
        }
    }
}

data class SettingsUiState(
    val loaded: Boolean,
    val dailyStepTargetField: String = "",
    val savedDailyStepTarget: Int = 0,
    val errorMessage: String? = null,
    val savedTick: Int = 0,
    val notionApiKeyField: String = "",
    val notionApiKeyVisible: Boolean = false,
    val notionDatabaseUrlField: String = "",
    val savedNotionDatabaseId: String? = null,
    val notionTesting: Boolean = false,
    val notionMessage: NotionMessage? = null,
)

sealed interface NotionMessage {
    val text: String
    data class Info(override val text: String) : NotionMessage
    data class Error(override val text: String) : NotionMessage
}
