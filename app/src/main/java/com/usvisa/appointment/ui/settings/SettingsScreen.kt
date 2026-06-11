package com.usvisa.appointment.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var intervalDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.saveSettings() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Consulate / Facility ───────────────────────────────────────────
            SectionHeader(Icons.Default.Business, "Consulate (Facility)")

            // Show auto-detected value if available
            if (uiState.settings.facilityId.isNotEmpty() && uiState.manualFacilityId.isEmpty()) {
                InfoCard(
                    "Auto-detected: ID ${uiState.settings.facilityId} — ${uiState.settings.facilityName}",
                    isSuccess = true
                )
            }

            OutlinedTextField(
                value = uiState.facilityId,
                onValueChange = viewModel::onFacilityIdChange,
                label = { Text("Consulate Facility ID *") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                placeholder = { Text("e.g. 94") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(
                        "Find this on ais.usvisa-info.com → your appointment → check URL for 'facility_id'",
                        fontSize = 11.sp
                    )
                }
            )

            OutlinedTextField(
                value = uiState.facilityName,
                onValueChange = viewModel::onFacilityNameChange,
                label = { Text("Consulate City/Name") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                placeholder = { Text("e.g. Toronto") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // ── Date Range ────────────────────────────────────────────────────
            SectionHeader(Icons.Default.DateRange, "Date Range to Monitor")

            OutlinedTextField(
                value = uiState.startDate,
                onValueChange = viewModel::onStartDateChange,
                label = { Text("Earliest Acceptable Date *") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.endDate,
                onValueChange = viewModel::onEndDateChange,
                label = { Text("Latest Acceptable Date *") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // ── Check Interval ────────────────────────────────────────────────
            SectionHeader(Icons.Default.Timer, "Check Interval")

            val intervalOptions = listOf(5, 10, 15, 30, 60)
            ExposedDropdownMenuBox(
                expanded = intervalDropdownExpanded,
                onExpandedChange = { intervalDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = "Every ${uiState.intervalMinutes} minutes",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Polling Interval") },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = intervalDropdownExpanded,
                    onDismissRequest = { intervalDropdownExpanded = false }
                ) {
                    intervalOptions.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("Every $minutes minutes") },
                            onClick = { viewModel.onIntervalChange(minutes); intervalDropdownExpanded = false },
                            leadingIcon = {
                                if (minutes == uiState.intervalMinutes)
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Behavior ──────────────────────────────────────────────────────
            SectionHeader(Icons.Default.Tune, "Behavior")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    SettingToggle(
                        title = "Auto-Book Appointment",
                        description = "Automatically book the earliest slot when found",
                        checked = uiState.autoBook,
                        onCheckedChange = viewModel::onAutoBookChange,
                        icon = Icons.Default.BookmarkAdd
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingToggle(
                        title = "Notify When Found",
                        description = "Push notification when slots are available",
                        checked = uiState.notifyOnFound,
                        onCheckedChange = viewModel::onNotifyOnFoundChange,
                        icon = Icons.Default.Notifications
                    )
                }
            }

            HorizontalDivider()

            // ── Advanced / Manual Overrides ───────────────────────────────────
            SectionHeader(Icons.Default.Code, "Advanced (Manual Override)")

            Text(
                "Only fill these if auto-detection failed after login. " +
                "Leave blank to use auto-detected values.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            val detectedScheduleId = uiState.settings.scheduleId
            if (detectedScheduleId.isNotEmpty()) {
                InfoCard("Auto-detected Schedule ID: $detectedScheduleId", isSuccess = true)
            }

            OutlinedTextField(
                value = uiState.manualScheduleId,
                onValueChange = viewModel::onManualScheduleIdChange,
                label = { Text("Manual Schedule ID (override)") },
                leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                placeholder = { Text("e.g. 55123456") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("From URL: ais.usvisa-info.com/…/schedule/[THIS]/appointment", fontSize = 11.sp)
                }
            )

            OutlinedTextField(
                value = uiState.manualFacilityId,
                onValueChange = viewModel::onManualFacilityIdChange,
                label = { Text("Manual Facility ID (override)") },
                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                placeholder = { Text("e.g. 94") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text("From the consulate dropdown on the website appointment page", fontSize = 11.sp)
                }
            )

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun InfoCard(message: String, isSuccess: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) Color(0xFF1B5E20).copy(alpha = 0.15f)
                             else Color(0xFFB71C1C).copy(alpha = 0.15f)
        )
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFFFA726),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
