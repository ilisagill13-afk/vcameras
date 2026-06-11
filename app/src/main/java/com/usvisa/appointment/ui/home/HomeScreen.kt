package com.usvisa.appointment.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val status = uiState.monitoringStatus
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("US Visa Monitor", fontWeight = FontWeight.Bold)
                        Text(
                            settings.email,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Status Card
            item {
                MonitoringStatusCard(
                    isRunning = uiState.isWorkerRunning,
                    isChecking = uiState.isCheckingNow,
                    facilityName = settings.facilityName,
                    startDate = settings.startDate,
                    endDate = settings.endDate,
                    intervalMinutes = settings.checkIntervalMinutes,
                    lastChecked = status.lastChecked,
                    onStart = { viewModel.startMonitoring() },
                    onStop = { viewModel.stopMonitoring() },
                    onCheckNow = { viewModel.checkNow() },
                    isConfigured = settings.startDate.isNotEmpty() && settings.endDate.isNotEmpty(),
                    isLoggedIn = settings.isLoggedIn
                )
            }

            // Config summary
            if (settings.scheduleId.isNotEmpty()) {
                item {
                    ConfigSummaryCard(settings)
                }
            }

            // Activity Log
            item {
                Text(
                    "Activity Log",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (uiState.logMessages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            "No activity yet. Press 'Check Now' or start monitoring.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(uiState.logMessages) { log ->
                    LogEntry(log)
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Stop monitoring and sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onLogout()
                }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MonitoringStatusCard(
    isRunning: Boolean,
    isChecking: Boolean,
    facilityName: String,
    startDate: String,
    endDate: String,
    intervalMinutes: Int,
    lastChecked: Long,
    isConfigured: Boolean,
    isLoggedIn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCheckNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) Color(0xFF0D2137) else MaterialTheme.colorScheme.surface
        ),
        border = if (isRunning) {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1565C0))
        } else null
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Status indicator row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isChecking -> Color(0xFFFFA726)
                                isRunning -> Color(0xFF66BB6A)
                                else -> Color(0xFF546E7A)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isChecking -> "Checking..."
                        isRunning -> "Monitoring Active"
                        else -> "Monitoring Stopped"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = when {
                        isChecking -> Color(0xFFFFA726)
                        isRunning -> Color(0xFF66BB6A)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            if (isRunning && startDate.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Looking for slots between $startDate → $endDate",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    "Checking every $intervalMinutes min • $facilityName",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (lastChecked > 0) {
                    Text(
                        "Last check: ${formatTime(lastChecked)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else if (!isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFA726),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Configure date range in Settings first",
                        fontSize = 13.sp,
                        color = Color(0xFFFFA726)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isRunning) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        enabled = isConfigured && isLoggedIn && !isChecking,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start")
                    }
                } else {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                OutlinedButton(
                    onClick = onCheckNow,
                    modifier = Modifier.weight(1f),
                    enabled = isConfigured && isLoggedIn && !isChecking
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isChecking) "Checking" else "Check Now")
                }
            }
        }
    }
}

@Composable
private fun ConfigSummaryCard(settings: com.usvisa.appointment.data.model.AppSettings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Configuration",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            ConfigRow(Icons.Default.Business, "Consulate", settings.facilityName)
            if (settings.startDate.isNotEmpty() && settings.endDate.isNotEmpty()) {
                ConfigRow(Icons.Default.DateRange, "Date Range", "${settings.startDate} → ${settings.endDate}")
            }
            ConfigRow(Icons.Default.Timer, "Check Interval", "${settings.checkIntervalMinutes} minutes")
            ConfigRow(
                Icons.Default.BookmarkAdd,
                "Auto-Book",
                if (settings.autoBook) "Enabled" else "Notify only"
            )
            if (settings.scheduleId.isNotEmpty()) {
                ConfigRow(Icons.Default.Tag, "Schedule ID", settings.scheduleId)
            }
        }
    }
}

@Composable
private fun ConfigRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.width(4.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LogEntry(log: String) {
    val color = when {
        log.contains("✓") -> Color(0xFF4CAF50)
        log.contains("✗") || log.contains("ERROR") -> Color(0xFFEF5350)
        log.contains("Booked") -> Color(0xFF66BB6A)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    Text(
        text = log,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
