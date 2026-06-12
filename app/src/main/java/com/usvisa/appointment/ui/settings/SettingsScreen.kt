package com.usvisa.appointment.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.usvisa.appointment.data.model.CANADA_FACILITIES
import com.usvisa.appointment.data.repository.FacilityFromPage
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var facilitiesExpanded by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onBack()
    }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDatePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        viewModel.onStartDateChange(date)
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDatePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        viewModel.onEndDateChange(date)
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }

    val facilities: List<FacilityFromPage> =
        uiState.detectedFacilities.ifEmpty {
            CANADA_FACILITIES.map { FacilityFromPage(it.id, it.city) }
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

            // ── Account Credentials ───────────────────────────────────────────
            SectionHeader(Icons.Default.AccountCircle, "Account Credentials")

            Text(
                "Saved for automatic re-login when session expires.",
                fontSize = 12.sp,
                color = if (uiState.password.isEmpty()) Color(0xFFFF7043)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (uiState.password.isEmpty()) {
                InfoCard("Password not saved — auto re-login will fail. Enter it below.", isSuccess = false)
            }

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // ── Consulate / Facility ───────────────────────────────────────────
            SectionHeader(Icons.Default.Business, "Consulate (Facility)")

            if (uiState.isLoadingFacilities) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Detecting consulates from your account...", fontSize = 13.sp)
                }
            } else if (uiState.detectedFacilities.isNotEmpty()) {
                InfoCard("${uiState.detectedFacilities.size} consulates detected from your account", isSuccess = true)
            }

            ExposedDropdownMenuBox(
                expanded = facilitiesExpanded,
                onExpandedChange = { if (!uiState.isLoadingFacilities) facilitiesExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.facilityName.ifEmpty { if (uiState.facilityId.isNotEmpty()) "ID: ${uiState.facilityId}" else "" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Consulate City *") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = facilitiesExpanded) },
                    placeholder = { Text("Select a consulate city") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = facilitiesExpanded,
                    onDismissRequest = { facilitiesExpanded = false }
                ) {
                    facilities.forEach { facility ->
                        DropdownMenuItem(
                            text = { Text(facility.name) },
                            onClick = {
                                viewModel.onFacilitySelected(facility)
                                facilitiesExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Date Range ────────────────────────────────────────────────────
            SectionHeader(Icons.Default.DateRange, "Date Range to Monitor")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Start Date",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            uiState.startDate.ifEmpty { "Tap to select" },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (uiState.startDate.isEmpty())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "End Date",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            uiState.endDate.ifEmpty { "Tap to select" },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (uiState.endDate.isEmpty())
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Check Interval ────────────────────────────────────────────────
            SectionHeader(Icons.Default.Timer, "Check Interval (Seconds)")

            OutlinedTextField(
                value = uiState.intervalSecondsText,
                onValueChange = viewModel::onIntervalSecondsChange,
                label = { Text("Check every N seconds *") },
                leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text("30") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    val secs = uiState.intervalSecondsText.toIntOrNull()?.coerceAtLeast(5) ?: 30
                    Text("Min 5s • ${secs}s interval = ${3600 / secs} checks/hour", fontSize = 11.sp)
                }
            )

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

            HorizontalDivider()

            // ── Proxy / IP Hide ───────────────────────────────────────────────
            SectionHeader(Icons.Default.VpnLock, "Proxy (Hide IP)")

            Text(
                "Route all monitoring traffic through an HTTP or SOCKS5 proxy to hide your IP from the visa website.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VpnLock, null, modifier = Modifier.size(24.dp),
                        tint = if (uiState.proxyEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Proxy", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            if (uiState.proxyEnabled && uiState.proxyHost.isNotEmpty())
                                "${uiState.proxyType}  ${uiState.proxyHost}:${uiState.proxyPortText}"
                            else "Disabled — real IP used",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(checked = uiState.proxyEnabled, onCheckedChange = viewModel::onProxyEnabledChange)
                }
            }

            if (uiState.proxyEnabled) {
                // Proxy type selector
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.proxyType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Proxy Type") },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        listOf("HTTP", "SOCKS5").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = { viewModel.onProxyTypeChange(type); typeExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.proxyHost,
                        onValueChange = viewModel::onProxyHostChange,
                        label = { Text("Proxy Host") },
                        placeholder = { Text("e.g. 192.168.1.1") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.proxyPortText,
                        onValueChange = viewModel::onProxyPortChange,
                        label = { Text("Port") },
                        placeholder = { Text("8080") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.width(100.dp),
                        singleLine = true
                    )
                }

                if (uiState.proxyHost.isNotEmpty()) {
                    InfoCard("Proxy active: ${uiState.proxyType} ${uiState.proxyHost}:${uiState.proxyPortText}", isSuccess = true)
                }
            }

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
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
