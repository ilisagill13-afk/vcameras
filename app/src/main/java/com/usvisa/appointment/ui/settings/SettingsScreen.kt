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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.usvisa.appointment.data.model.CANADA_FACILITIES
import com.usvisa.appointment.data.model.FacilityOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var facilityDropdownExpanded by remember { mutableStateOf(false) }
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
            // Consulate Selection
            SectionHeader(icon = Icons.Default.Business, title = "Consulate Location")
            ExposedDropdownMenuBox(
                expanded = facilityDropdownExpanded,
                onExpandedChange = { facilityDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedFacility.city,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Consulate") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = facilityDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = facilityDropdownExpanded,
                    onDismissRequest = { facilityDropdownExpanded = false }
                ) {
                    CANADA_FACILITIES.forEach { facility ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(facility.city, fontWeight = FontWeight.Medium)
                                    Text("ID: ${facility.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            },
                            onClick = {
                                viewModel.onFacilitySelected(facility)
                                facilityDropdownExpanded = false
                            },
                            leadingIcon = {
                                if (facility.id == uiState.selectedFacility.id) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Date Range
            SectionHeader(icon = Icons.Default.DateRange, title = "Date Range to Monitor")
            Text(
                "Enter the date range within which you want to book an appointment (format: YYYY-MM-DD)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            OutlinedTextField(
                value = uiState.startDate,
                onValueChange = viewModel::onStartDateChange,
                label = { Text("Start Date (YYYY-MM-DD)") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text("2025-01-01") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.endDate,
                onValueChange = viewModel::onEndDateChange,
                label = { Text("End Date (YYYY-MM-DD)") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                placeholder = { Text("2025-12-31") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            HorizontalDivider()

            // Check Interval
            SectionHeader(icon = Icons.Default.Timer, title = "Check Interval")
            Text(
                "How often to check for new appointment slots",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            val intervalOptions = listOf(5, 10, 15, 30, 60)
            ExposedDropdownMenuBox(
                expanded = intervalDropdownExpanded,
                onExpandedChange = { intervalDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${uiState.intervalMinutes} minutes",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Interval") },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = intervalDropdownExpanded,
                    onDismissRequest = { intervalDropdownExpanded = false }
                ) {
                    intervalOptions.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("Every $minutes minutes") },
                            onClick = {
                                viewModel.onIntervalChange(minutes)
                                intervalDropdownExpanded = false
                            },
                            leadingIcon = {
                                if (minutes == uiState.intervalMinutes) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Behavior Settings
            SectionHeader(icon = Icons.Default.Tune, title = "Behavior")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    SettingToggle(
                        title = "Auto-Book Appointment",
                        description = "Automatically book the earliest available slot when found",
                        checked = uiState.autoBook,
                        onCheckedChange = viewModel::onAutoBookChange,
                        icon = Icons.Default.BookmarkAdd
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingToggle(
                        title = "Notify When Found",
                        description = "Send a notification when slots are available",
                        checked = uiState.notifyOnFound,
                        onCheckedChange = viewModel::onNotifyOnFoundChange,
                        icon = Icons.Default.Notifications
                    )
                }
            }

            if (!uiState.autoBook) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Notify-only mode: app will alert you when slots are found but won't book automatically.",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Save hint
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
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
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
