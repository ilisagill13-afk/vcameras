package com.usvisa.appointment.ui.home

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

private const val BASE = "https://ais.usvisa-info.com/en-ca/niv"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }

    val scheduleId = settings.manualScheduleId.ifEmpty { settings.scheduleId }
    val startUrl = if (scheduleId.isNotEmpty())
        "$BASE/schedule/$scheduleId/appointment"
    else
        "$BASE/users/sign_in"

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Full-screen WebView ────────────────────────────────────────────
        val webViewBottomPad = if (showLogs) 220.dp else 64.dp
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = webViewBottomPad),
            factory = { ctx ->
                // Use 'also' with explicit 'wv' to avoid shadowing the outer 'settings' variable
                WebView(ctx).also { wv ->
                    wv.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.6367.82 Mobile Safari/537.36"
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest
                        ) = false
                    }
                    wv.loadUrl(startUrl)
                }
            }
        )

        // ── Bottom panel ───────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {

                // ── Log list (collapsible) ────────────────────────────────
                if (showLogs && uiState.logMessages.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        reverseLayout = false
                    ) {
                        items(uiState.logMessages.take(20)) { msg ->
                            val color = when {
                                msg.contains("✓") || msg.contains("BOOKED") -> Color(0xFF66BB6A)
                                msg.contains("✗") || msg.contains("Error") || msg.contains("expired") -> Color(0xFFEF5350)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = msg,
                                fontSize = 10.sp,
                                color = color,
                                modifier = Modifier.padding(vertical = 1.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ── Control bar ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Status dot
                    val statusColor = when {
                        uiState.needsLogin       -> Color(0xFFFFB300)
                        uiState.isServiceRunning -> Color(0xFF66BB6A)
                        else                     -> Color(0xFF546E7A)
                    }
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    // Last log message (or status label) — tappable to toggle log panel
                    val lastMsg = uiState.logMessages.firstOrNull()
                    val statusLabel = when {
                        uiState.needsLogin       -> "Login needed"
                        uiState.isServiceRunning -> "Monitoring"
                        else                     -> "Stopped"
                    }
                    val displayText = if (lastMsg != null && (uiState.isServiceRunning || uiState.totalChecks > 0))
                        lastMsg else statusLabel

                    TextButton(
                        onClick = { showLogs = !showLogs },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = displayText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (lastMsg != null && (uiState.isServiceRunning || uiState.totalChecks > 0))
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            else statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }

                    if (uiState.totalChecks > 0) {
                        Text(
                            "#${uiState.totalChecks}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Check Now
                    IconButton(
                        onClick = { viewModel.checkNow() },
                        enabled = !uiState.isCheckingNow && !uiState.needsLogin
                    ) {
                        if (uiState.isCheckingNow) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Check Now", modifier = Modifier.size(22.dp))
                        }
                    }

                    // Start / Stop monitoring
                    if (!uiState.isServiceRunning) {
                        IconButton(onClick = { viewModel.startMonitoring() }, enabled = !uiState.needsLogin) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start Monitoring",
                                tint = if (uiState.needsLogin) Color.Gray else Color(0xFF66BB6A),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.stopMonitoring() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Monitoring", tint = Color(0xFFEF5350), modifier = Modifier.size(26.dp))
                        }
                    }

                    // Settings
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(22.dp))
                    }

                    // Logout
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", modifier = Modifier.size(22.dp))
                    }
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
                }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}
