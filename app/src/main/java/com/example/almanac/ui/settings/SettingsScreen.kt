package com.example.almanac.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.loaded) {
                Text("Loading…")
                return@Column
            }

            Text(
                "Daily step target",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Used to compute \"days target hit\" in the steps section of every export.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.dailyStepTargetField,
                onValueChange = viewModel::onStepTargetChanged,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.errorMessage != null,
                supportingText = {
                    val msg = state.errorMessage
                        ?: "Currently saved: ${state.savedDailyStepTarget}"
                    Text(msg)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::save,
                enabled = state.dailyStepTargetField.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            HorizontalDivider()

            NotionSection(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun NotionSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Text(
        "Notion connection",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "Paste your internal-integration token and the share URL of the Physical database. " +
            "Remember to add the integration under Connections on that page in Notion.",
        style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
        value = state.notionApiKeyField,
        onValueChange = viewModel::onNotionApiKeyChanged,
        singleLine = true,
        label = { Text("Notion API key") },
        visualTransformation = if (state.notionApiKeyVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = viewModel::toggleApiKeyVisibility) {
                Icon(
                    imageVector = if (state.notionApiKeyVisible) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = if (state.notionApiKeyVisible) "Hide" else "Show",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.notionDatabaseUrlField,
        onValueChange = viewModel::onNotionDatabaseUrlChanged,
        singleLine = true,
        label = { Text("Physical database URL") },
        supportingText = {
            val savedId = state.savedNotionDatabaseId
            Text(if (savedId != null) "Database ID: $savedId" else "No database saved yet")
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = viewModel::saveNotionCredentials,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save Notion credentials")
    }

    OutlinedButton(
        onClick = viewModel::testNotionConnection,
        enabled = !state.notionTesting && state.notionApiKeyField.isNotBlank() && state.savedNotionDatabaseId != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.notionTesting) "Testing…" else "Test connection")
    }

    OutlinedButton(
        onClick = viewModel::clearNotionCredentials,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Clear Notion credentials")
    }

    state.notionMessage?.let { msg ->
        val color = when (msg) {
            is NotionMessage.Error -> MaterialTheme.colorScheme.error
            is NotionMessage.Info -> MaterialTheme.colorScheme.primary
        }
        Text(msg.text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}
