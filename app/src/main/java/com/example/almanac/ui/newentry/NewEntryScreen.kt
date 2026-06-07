package com.example.almanac.ui.newentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.almanac.domain.model.NewPhysicalEntryDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: NewEntryViewModel = viewModel(factory = NewEntryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is NewEntryUiState.Saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topTitle(state)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
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
        ) {
            when (val s = state) {
                is NewEntryUiState.PreparingSuggestions -> LoadingPanel("Reading Health Connect…")
                is NewEntryUiState.Saving -> LoadingPanel("Saving to Notion…")
                is NewEntryUiState.Saved -> LoadingPanel("Saved.")
                is NewEntryUiState.Editing -> EditingPanel(s, viewModel)
                is NewEntryUiState.Reviewing -> ReviewPanel(s.draft, viewModel)
                is NewEntryUiState.SaveFailed -> ReviewPanel(
                    s.draft,
                    viewModel,
                    errorMessage = s.message,
                )
            }
        }
    }
}

private fun topTitle(state: NewEntryUiState): String = when (state) {
    is NewEntryUiState.Editing -> "Step ${state.step.index} of ${NewEntryUiState.Step.TOTAL} — ${state.step.title}"
    is NewEntryUiState.Reviewing,
    is NewEntryUiState.SaveFailed -> "Review"
    is NewEntryUiState.PreparingSuggestions -> "New Notion entry"
    is NewEntryUiState.Saving -> "Saving…"
    is NewEntryUiState.Saved -> "Saved"
}

@Composable
private fun LoadingPanel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 64.dp),
    ) {
        CircularProgressIndicator()
        Text(text)
    }
}

@Composable
private fun EditingPanel(state: NewEntryUiState.Editing, viewModel: NewEntryViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        LinearProgressIndicator(
            progress = { state.step.index.toFloat() / NewEntryUiState.Step.TOTAL },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Suggestions are based on the previous week (${state.draft.weekLabel}).",
            style = MaterialTheme.typography.bodySmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.step) {
                    NewEntryUiState.Step.SLEEP -> SleepStep(state.draft, viewModel)
                    NewEntryUiState.Step.ACTIVITY -> ActivityStep(state.draft, viewModel)
                    NewEntryUiState.Step.BODY -> BodyStep(state.draft, viewModel)
                    NewEntryUiState.Step.HABITS -> HabitsStep(state.draft, viewModel)
                    NewEntryUiState.Step.CONTEXT -> ContextStep(state.draft, viewModel)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::goBack,
                enabled = state.step.previous() != null,
                modifier = Modifier.weight(1f),
            ) { Text("Back") }
            Button(
                onClick = viewModel::goNext,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.step.next() == null) "Review" else "Next")
            }
        }
    }
}

@Composable
private fun SleepStep(draft: NewPhysicalEntryDraft, vm: NewEntryViewModel) {
    TextField("Avg sleep (e.g. 7h 58m)", draft.avgSleep) { v -> vm.updateDraft { it.copy(avgSleep = v) } }
    TextField("Bed time (HH:MM)", draft.bedTime) { v -> vm.updateDraft { it.copy(bedTime = v) } }
    TextField("Wake up (HH:MM)", draft.wakeUp) { v -> vm.updateDraft { it.copy(wakeUp = v) } }
}

@Composable
private fun ActivityStep(draft: NewPhysicalEntryDraft, vm: NewEntryViewModel) {
    NumberField("Avg steps", draft.avgSteps?.toString().orEmpty()) { v ->
        vm.updateDraft { it.copy(avgSteps = v.toLongOrNull()) }
    }
    NumberField("Step target — days hit (0–7)", draft.stepTargetDaysHit?.toString().orEmpty()) { v ->
        vm.updateDraft { it.copy(stepTargetDaysHit = v.toIntOrNull()) }
    }
}

@Composable
private fun BodyStep(draft: NewPhysicalEntryDraft, vm: NewEntryViewModel) {
    DecimalField("Weight (kg)", draft.weight?.toString().orEmpty()) { v ->
        vm.updateDraft { it.copy(weight = v.replace(',', '.').toDoubleOrNull()) }
    }
}

@Composable
private fun HabitsStep(draft: NewPhysicalEntryDraft, vm: NewEntryViewModel) {
    NumberField("Flaster", draft.flaster?.toString().orEmpty()) { v ->
        vm.updateDraft { it.copy(flaster = v.toLongOrNull()) }
    }
    NumberField("No Eith", draft.noEith?.toString().orEmpty()) { v ->
        vm.updateDraft { it.copy(noEith = v.toLongOrNull()) }
    }
    NumberField("No sugar", draft.noSugar?.toString().orEmpty()) { v ->
        vm.updateDraft { it.copy(noSugar = v.toLongOrNull()) }
    }
}

@Composable
private fun ContextStep(draft: NewPhysicalEntryDraft, vm: NewEntryViewModel) {
    TextField("KPI (title)", draft.kpi) { v -> vm.updateDraft { it.copy(kpi = v) } }
    TextField("Week", draft.weekLabel) { v -> vm.updateDraft { it.copy(weekLabel = v) } }
    TextField("Sport", draft.sport) { v -> vm.updateDraft { it.copy(sport = v) } }
    TextField("Notes", draft.notes) { v -> vm.updateDraft { it.copy(notes = v) } }
}

@Composable
private fun TextField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DecimalField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReviewPanel(
    draft: NewPhysicalEntryDraft,
    viewModel: NewEntryViewModel,
    errorMessage: String? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    draft.kpi.ifBlank { "(no KPI)" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("Week ${draft.weekLabel}", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()

                ReviewRow("Avg sleep", draft.avgSleep)
                ReviewRow("Bed time", draft.bedTime)
                ReviewRow("Wake up", draft.wakeUp)
                ReviewRow("Avg steps", draft.avgSteps?.let { "%,d".format(it) }.orEmpty())
                ReviewRow("Step target (days hit)", draft.stepTargetDaysHit?.toString().orEmpty())
                ReviewRow("Weight", draft.weight?.let { "%.1f kg".format(it) }.orEmpty())
                ReviewRow("Sport", draft.sport)
                HorizontalDivider()
                ReviewRow("Flaster", draft.flaster?.toString().orEmpty())
                ReviewRow("No Eith", draft.noEith?.toString().orEmpty())
                ReviewRow("No sugar", draft.noSugar?.toString().orEmpty())
                if (draft.notes.isNotBlank()) {
                    HorizontalDivider()
                    Text("Notes", style = MaterialTheme.typography.labelMedium)
                    Text(draft.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(errorMessage, modifier = Modifier.padding(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::goBack,
                modifier = Modifier.weight(1f),
            ) { Text("Edit") }
            Button(
                onClick = viewModel::save,
                modifier = Modifier.weight(1f),
            ) { Text("Save to Notion") }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
