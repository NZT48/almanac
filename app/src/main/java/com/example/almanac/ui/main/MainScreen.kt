package com.example.almanac.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.almanac.data.file.WeeklySummary
import com.example.almanac.data.file.formatDurationMinutes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onOpenPhysicalEntries: () -> Unit,
    onOpenNewEntry: () -> Unit,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Almanac") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBanner(
                state = uiState,
                onGrantAccess = { missing -> permissionLauncher.launch(missing) },
                onOpenHealthConnectSettings = { openHealthConnectSettings(context) },
            )

            PresetChipsRow(
                selected = picker.preset,
                onSelect = viewModel::selectPreset,
            )

            DateField(
                label = "From",
                date = picker.from,
                onPick = viewModel::setFrom,
                maxDate = picker.to,
            )

            DateField(
                label = "To",
                date = picker.to,
                onPick = viewModel::setTo,
                minDate = picker.from,
            )

            val exportEnabled = uiState is MainUiState.Ready || uiState is MainUiState.Done
            Button(
                onClick = { viewModel.runExport() },
                enabled = exportEnabled && picker.valid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState is MainUiState.Done) "Re-export" else "Export")
            }

            OutlinedButton(
                onClick = onOpenPhysicalEntries,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Browse Notion entries")
            }

            OutlinedButton(
                onClick = onOpenNewEntry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("New Notion entry")
            }

            when (val s = uiState) {
                is MainUiState.Exporting -> ExportingPanel()
                is MainUiState.Done -> ResultPanel(
                    state = s,
                    onShare = { shareFile(context, s) },
                    onOpen = { openFile(context, s) },
                    onNewExport = viewModel::resetForNewExport,
                    onOpenHealthConnectDataSources = { openHealthConnectSettings(context) },
                )
                is MainUiState.Error -> ErrorPanel(s.message)
                else -> {}
            }
        }
    }
}

@Composable
private fun StatusBanner(
    state: MainUiState,
    onGrantAccess: (Set<String>) -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
) {
    when (state) {
        is MainUiState.Loading -> InfoCard("Checking Health Connect…")
        is MainUiState.HealthConnectUnavailable -> InfoCard(
            "Health Connect is not available on this device.",
            tone = Tone.WARNING,
        )
        is MainUiState.NeedsPermission -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Almanac needs access to your steps, weight, and sleep from Health Connect.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val missingLabel = state.missing.joinToString { permissionLabel(it) }
                Text(
                    "Missing: $missingLabel",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onGrantAccess(state.missing) }) {
                        Text("Grant access")
                    }
                    OutlinedButton(onClick = onOpenHealthConnectSettings) {
                        Text("Open Health Connect settings")
                    }
                }
            }
        }
        is MainUiState.Ready -> InfoCard("Ready to export.", tone = Tone.SUCCESS)
        is MainUiState.Exporting,
        is MainUiState.Done,
        is MainUiState.Error -> {} // panels below carry the message
    }
}

private enum class Tone { INFO, SUCCESS, WARNING }

@Composable
private fun InfoCard(message: String, tone: Tone = Tone.INFO) {
    val color = when (tone) {
        Tone.INFO -> MaterialTheme.colorScheme.surfaceVariant
        Tone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
        Tone.WARNING -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetChipsRow(selected: RangePreset, onSelect: (RangePreset) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        RangePreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onSelect(preset) },
                label = { Text(preset.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    date.format(DAY_FMT),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(onClick = { showDialog = true }) {
                Text("Change")
            }
        }
    }

    if (showDialog) {
        val initialMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        val clamped = picked
                            .let { if (minDate != null && it.isBefore(minDate)) minDate else it }
                            .let { if (maxDate != null && it.isAfter(maxDate)) maxDate else it }
                        onPick(clamped)
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ExportingPanel() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("Reading Health Connect and writing file…")
        }
    }
}

@Composable
private fun ResultPanel(
    state: MainUiState.Done,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onNewExport: () -> Unit,
    onOpenHealthConnectDataSources: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Saved to ${state.result.locationLabel}", style = MaterialTheme.typography.titleMedium)

            CountsRow(state.counts)

            DigestPreview(state.summary)

            if (state.allEmptyHint) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "All three data types were empty — check that Samsung Health is syncing to Health Connect.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = onOpenHealthConnectDataSources) {
                            Text("Open Health Connect")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare) { Text("Share") }
                OutlinedButton(onClick = onOpen) { Text("Open") }
                OutlinedButton(
                    onClick = onNewExport,
                    colors = ButtonDefaults.outlinedButtonColors(),
                ) { Text("New export") }
            }
        }
    }
}

@Composable
private fun CountsRow(counts: ExportCounts) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CountCell("Steps", "${counts.stepDays} days")
        CountCell("Weight", "${counts.weightReadings} readings")
        CountCell("Sleep", "${counts.sleepSessions} sessions")
    }
}

@Composable
private fun CountCell(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DigestPreview(summary: WeeklySummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Week ${summary.isoWeekTag} preview", style = MaterialTheme.typography.titleSmall)

        if (!summary.steps.isEmpty) {
            PreviewLine("Steps total", "%,d".format(summary.steps.weeklyTotal))
            PreviewLine("Daily average", "%,d".format(summary.steps.dailyAverage))
            PreviewLine("Days target hit", "${summary.steps.daysTargetHit} / ${summary.steps.daysInWeek}")
        }
        if (!summary.weight.isEmpty) {
            summary.weight.weekDeltaKg?.let { delta ->
                val sign = if (delta >= 0) "+" else "−"
                PreviewLine("Weight delta", "$sign%.1f kg".format(kotlin.math.abs(delta)))
            }
            summary.weight.weeklyAverageKg?.let {
                PreviewLine("Weight average", "%.1f kg".format(it))
            }
        }
        if (!summary.sleep.isEmpty) {
            summary.sleep.weeklyAverageMinutes?.let {
                PreviewLine("Sleep average", formatDurationMinutes(it))
            }
            summary.sleep.averageBedtime?.let {
                PreviewLine("Avg bedtime", it.format(TIME_FMT))
            }
            summary.sleep.averageWakeTime?.let {
                PreviewLine("Avg wake time", it.format(TIME_FMT))
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, modifier = Modifier.padding(16.dp))
    }
}

private fun permissionLabel(permission: String): String = when {
    permission.endsWith("READ_STEPS") -> "Steps"
    permission.endsWith("READ_WEIGHT") -> "Weight"
    permission.endsWith("READ_SLEEP") -> "Sleep"
    else -> permission.substringAfterLast(".")
}

private fun openHealthConnectSettings(context: android.content.Context) {
    val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Throwable) {
        // fall back to app settings
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun shareFile(context: android.content.Context, state: MainUiState.Done) {
    val uri = resolveDownloadsUri(context, state.result.displayName) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Almanac — ${state.summary.isoWeekTag}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Almanac export"))
}

private fun openFile(context: android.content.Context, state: MainUiState.Done) {
    val uri = resolveDownloadsUri(context, state.result.displayName) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "text/plain")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Throwable) {
        // no installed viewer for text/plain — silently ignore
    }
}

private fun resolveDownloadsUri(context: android.content.Context, displayName: String): Uri? {
    val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
    return context.contentResolver.query(
        collection,
        arrayOf(android.provider.MediaStore.MediaColumns._ID),
        "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME}=?",
        arrayOf(displayName),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
        } else null
    }
}

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH)
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
