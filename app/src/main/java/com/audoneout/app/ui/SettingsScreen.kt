package com.audoneout.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audoneout.app.MainUiState
import com.audoneout.app.data.FolderBlacklistRuleEntity
import com.audoneout.app.data.MusicRootEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    state: MainUiState,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onAddRoot: (String, String, String) -> Unit,
    onRootIncludedChange: (Long, Boolean) -> Unit,
    onRescanRoot: (Long) -> Unit,
    onRemoveRoot: (Long) -> Unit,
    onAddRule: (String) -> Unit,
    onAddBlacklistPath: (String, String) -> Unit,
    onRuleEnabledChange: (Long, Boolean) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onRestoreRules: () -> Unit,
    onAutomaticChecksChange: (Boolean) -> Unit,
    onCheckFrequencyChange: (String) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAutoEnhanceChange: (Boolean) -> Unit,
    onChargingOnlyChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onQuietModeChange: (Boolean) -> Unit,
    onOnlineEnrichmentChange: (Boolean) -> Unit,
    onLastFmEnabledChange: (Boolean) -> Unit,
    onSaveLastFm: (String, String) -> Unit,
    onOpenLastFmApiSetup: () -> Unit
) {
    val context = LocalContext.current
    var newRule by remember { mutableStateOf("") }
    var lastFmUsername by remember(state.settings.lastFmUsername) { mutableStateOf(state.settings.lastFmUsername) }
    var lastFmApiKey by remember(state.settings.lastFmApiKey) { mutableStateOf(state.settings.lastFmApiKey) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val selection = uri.toMusicRootSelection()
            onAddRoot(selection.displayName, uri.toString(), selection.relativeLocation)
        }
    }
    val blacklistFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val selection = uri.toMusicRootSelection()
            onAddBlacklistPath(selection.displayName, selection.relativeLocation)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeading("Music folders") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add folder")
                }
                OutlinedButton(
                    onClick = if (state.scanRunning) onCancelScan else onScan,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (state.scanRunning) Icons.Rounded.Cancel else Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.scanRunning) "Cancel scan" else "Scan now")
                }
            }
        }
        if (state.roots.isEmpty()) {
            item {
                EmptyPanel(
                    icon = { Icon(Icons.Rounded.Storage, null, tint = AudColors.Teal, modifier = Modifier.size(34.dp)) },
                    title = "No folders selected",
                    body = "Add Music, Downloads/Music, or an SD-card folder."
                )
            }
        } else {
            items(state.roots, key = { "root-${it.id}" }) { root ->
                MusicRootRow(root, onRootIncludedChange, onRescanRoot, onRemoveRoot)
            }
        }
        item { SectionHeading("Folder blacklist") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newRule,
                    onValueChange = { newRule = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Folder name") }
                )
                IconButton(
                    onClick = { onAddRule(newRule.trim()); newRule = "" },
                    enabled = newRule.isNotBlank()
                ) { Icon(Icons.Rounded.Add, "Add blacklist rule") }
            }
        }
        item {
            OutlinedButton(
                onClick = { blacklistFolderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Folder, null)
                Spacer(Modifier.width(6.dp))
                Text("Exclude a selected folder")
            }
        }
        item {
            OutlinedButton(onClick = onRestoreRules, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Restore default exclusions")
            }
        }
        items(state.blacklistRules, key = { "blacklist-${it.id}" }) { rule ->
            BlacklistRuleRow(rule, onRuleEnabledChange, onDeleteRule)
        }
        item { SectionHeading("Background library checks") }
        item {
            SettingsGroup {
                SettingSwitch("Automatic checks", "Detect new and changed local music", state.settings.automaticLibraryChecking, onAutomaticChecksChange)
                CheckFrequencyRow(state.settings.checkFrequency, onCheckFrequencyChange)
                SettingSwitch("Analyse while charging", "Use charging time for heavier metadata work", state.settings.analyseOnlyWhileCharging, onChargingOnlyChange)
                SettingSwitch("Enhance new tracks", "Add local era, mood, energy, and quality signals", state.settings.enhanceNewTracksAutomatically, onAutoEnhanceChange)
                SettingSwitch("New-music notifications", "Notify only after useful results are ready", state.settings.notifyWhenNewTracksReady, onNotificationsChange)
                SettingSwitch("Quiet background mode", "Delay non-urgent work", state.settings.quietBackgroundMode, onQuietModeChange)
            }
        }
        item { SectionHeading("Online metadata") }
        item {
            SettingsGroup {
                SettingSwitch("Online enrichment", "Allow configured metadata providers", state.settings.onlineEnrichmentEnabled, onOnlineEnrichmentChange)
                SettingSwitch("Wi-Fi only", "Keep online enrichment off mobile data", state.settings.wifiOnlyOnlineEnrichment, onWifiOnlyChange)
            }
        }
        item { SectionHeading("Last.fm taste sync") }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountCircle, null, tint = AudColors.Coral)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Public profile recommendations", fontWeight = FontWeight.Bold)
                            Text(
                                state.lastFmProfile?.let {
                                    "${state.recentLastFmScrobbleCount} recent scrobbles and ${it.playCount} lifetime plays connected"
                                } ?: "Uses recent, top, and loved tracks as recommendation seeds",
                                color = AudColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = state.settings.lastFmEnabled, onCheckedChange = onLastFmEnabledChange)
                    }
                    OutlinedTextField(
                        value = lastFmUsername,
                        onValueChange = { lastFmUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Last.fm username") },
                        leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) }
                    )
                    OutlinedTextField(
                        value = lastFmApiKey,
                        onValueChange = { lastFmApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API key") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onSaveLastFm(lastFmUsername, lastFmApiKey) },
                            enabled = lastFmUsername.isNotBlank() && lastFmApiKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Save and sync") }
                        OutlinedButton(onClick = onOpenLastFmApiSetup, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Get API key")
                        }
                    }
                    Text(
                        "Enter the API key only, never the shared secret. AudOneOut reads public profile metadata and does not submit scrobbles.",
                        color = AudColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        item { SectionHeading("Privacy") }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, null, tint = AudColors.Teal)
                        Spacer(Modifier.width(8.dp))
                        Text("Local by default", fontWeight = FontWeight.Bold)
                    }
                    PrivacyLine("Audio uploads", "Never")
                    PrivacyLine("Analytics", "Off")
                    PrivacyLine("Broad file access", "Not requested")
                    PrivacyLine("File-tag changes", "Only after explicit confirmation")
                    PrivacyLine("Last.fm", if (state.settings.lastFmEnabled) "Public profile metadata" else "Off")
                }
            }
        }
    }
}

@Composable
private fun MusicRootRow(
    root: MusicRootEntity,
    onIncludedChange: (Long, Boolean) -> Unit,
    onRescan: (Long) -> Unit,
    onRemove: (Long) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = AudColors.Amber)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(root.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(root.location, color = AudColors.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(root.uri, color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = root.included, onCheckedChange = { onIncludedChange(root.id, it) })
            }
            Text("${root.indexedTrackCount} tracks - ${formatBytes(root.storageBytes)} - ${root.scanStatus}", color = AudColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (root.lastScanTimeMillis > 0) {
                Text("Last scan ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(root.lastScanTimeMillis))}", color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onRescan(root.id) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Rescan")
                }
                IconButton(onClick = { onRemove(root.id) }) { Icon(Icons.Rounded.DeleteOutline, "Remove folder") }
            }
        }
    }
}

@Composable
private fun BlacklistRuleRow(
    rule: FolderBlacklistRuleEntity,
    onEnabledChange: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Block, null, tint = if (rule.enabled) AudColors.Coral else AudColors.TextMuted)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(rule.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${rule.excludedPreviewCount} matching tracks - ${rule.matchType}", color = AudColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = rule.enabled, onCheckedChange = { onEnabledChange(rule.id, it) })
        if (!rule.defaultSuggestion) {
            IconButton(onClick = { onDelete(rule.id) }) { Icon(Icons.Rounded.DeleteOutline, "Delete rule") }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = AudColors.Surface)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AudColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CheckFrequencyRow(selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Check frequency", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Daily", "Weekly").forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(option) }
                )
            }
        }
    }
}

@Composable
private fun PrivacyLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = AudColors.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private data class MusicRootSelection(val displayName: String, val relativeLocation: String)

private fun Uri.toMusicRootSelection(): MusicRootSelection {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrDefault(toString())
    val relative = documentId.substringAfter(':', documentId).trim('/').ifBlank { "Music" }
    val display = relative.substringAfterLast('/').ifBlank { "Music" }
    return MusicRootSelection(display, relative)
}
