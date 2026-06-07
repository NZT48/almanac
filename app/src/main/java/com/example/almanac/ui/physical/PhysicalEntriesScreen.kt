package com.example.almanac.ui.physical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.almanac.domain.model.PhysicalEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalEntriesScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: PhysicalEntriesViewModel = viewModel(factory = PhysicalEntriesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstResume = remember { mutableStateOf(true) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume.value) {
            firstResume.value = false
        } else {
            viewModel.reloadPreservingCurrent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physical entries") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                is PhysicalEntriesUiState.Loading -> LoadingPanel()
                is PhysicalEntriesUiState.Empty -> EmptyPanel(onRetry = viewModel::load)
                is PhysicalEntriesUiState.Error -> ErrorPanel(message = s.message, onRetry = viewModel::load)
                is PhysicalEntriesUiState.Loaded -> LoadedPanel(
                    state = s,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onEdit = { onEdit(s.current.id) },
                )
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 64.dp),
    ) {
        CircularProgressIndicator()
        Text("Loading from Notion…")
    }
}

@Composable
private fun EmptyPanel(onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 64.dp),
    ) {
        Text("No rows in the Physical database.")
        OutlinedButton(onClick = onRetry) { Text("Refresh") }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message)
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun LoadedPanel(
    state: PhysicalEntriesUiState.Loaded,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Latest first", style = MaterialTheme.typography.labelLarge)
            Text(state.positionLabel, style = MaterialTheme.typography.labelLarge)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EntryContent(state.current)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = state.hasPrevious,
                modifier = Modifier.weight(1f),
            ) { Text("Previous") }
            Button(
                onClick = onNext,
                enabled = state.hasNext,
                modifier = Modifier.weight(1f),
            ) { Text("Next") }
        }

        Button(
            onClick = onEdit,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Edit this entry") }
    }
}

@Composable
private fun EntryContent(entry: PhysicalEntry) {
    Text(
        entry.kpi.ifBlank { "(no KPI)" },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    entry.weekLabel?.let {
        Text("Week $it", style = MaterialTheme.typography.bodyMedium)
    }

    HorizontalDivider()

    Field("Weight", entry.weight?.let { "%.1f kg".format(it) })
    Field("Avg steps", entry.avgSteps?.let { "%,d".format(it) })
    Field("Step target", entry.stepTarget?.toString())
    Field("Avg sleep", entry.avgSleep)
    Field("Bed time", entry.bedTime)
    Field("Wake up", entry.wakeUp)
    Field("Sport", entry.sport)

    HorizontalDivider()

    Field("Flaster", entry.flaster?.toString())
    Field("No Eith", entry.noEith?.toString())
    Field("No sugar", entry.noSugar?.toString())

    if (!entry.notes.isNullOrBlank()) {
        HorizontalDivider()
        Text("Notes", style = MaterialTheme.typography.labelMedium)
        Text(entry.notes, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
