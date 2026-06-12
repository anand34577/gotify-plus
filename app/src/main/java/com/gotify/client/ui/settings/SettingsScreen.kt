package com.gotify.client.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SettingsState(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val dynamicColorEnabled: Boolean = false,
    val darkThemeEnabled: Boolean = true,
    val markdownEnabled: Boolean = true,
    val keepAliveEnabled: Boolean = true,
    val themeSelection: String = "DEFAULT",
    val serverName: String = "",
    val serverUrl: String = "",
    val appVersion: String = "1.0.0"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSetTheme: (String) -> Unit,
    onToggleMarkdown: (Boolean) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            SettingsSection(title = "Notifications") {
                SwitchSettingsRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Enable notifications",
                    subtitle = "Show push notifications for new messages",
                    checked = state.notificationsEnabled,
                    onChecked = onToggleNotifications
                )
                SwitchSettingsRow(
                    icon = Icons.Outlined.Vibration,
                    title = "Vibration",
                    subtitle = "Vibrate for high-priority messages",
                    checked = state.vibrationEnabled,
                    onChecked = onToggleVibration,
                    enabled = state.notificationsEnabled
                )
                ClickableSettingsRow(
                    icon = Icons.Outlined.Tune,
                    title = "Notification channels",
                    subtitle = "Configure per-app notification behavior",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    }
                )
                ClickableSettingsRow(
                    icon = Icons.Outlined.BatteryAlert,
                    title = "Battery optimization",
                    subtitle = "Disable to ensure reliable delivery",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .apply { data = Uri.parse("package:${context.packageName}") }
                        )
                    }
                )
            }

            SettingsSection(title = "Appearance") {
                SwitchSettingsRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark theme",
                    subtitle = "Use dark color scheme",
                    checked = state.darkThemeEnabled,
                    onChecked = onToggleDarkTheme
                )
                ClickableSettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = "Color Theme",
                    subtitle = state.themeSelection,
                    onClick = { showThemeDialog = true }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchSettingsRow(
                        icon = Icons.Outlined.Palette,
                        title = "Dynamic color",
                        subtitle = "Match wallpaper colors (Android 12+)",
                        checked = state.dynamicColorEnabled,
                        onChecked = onToggleDynamicColor
                    )
                }
                SwitchSettingsRow(
                    icon = Icons.Outlined.TextFormat,
                    title = "Render Markdown",
                    subtitle = "Format message bodies as Markdown",
                    checked = state.markdownEnabled,
                    onChecked = onToggleMarkdown
                )
            }

            SettingsSection(title = "Connection") {
                SwitchSettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = "Keep connection alive",
                    subtitle = "Maintain persistent WebSocket connection",
                    checked = state.keepAliveEnabled,
                    onChecked = onToggleKeepAlive
                )
            }

            SettingsSection(title = "Active Server") {
                InfoSettingsRow(
                    icon = Icons.Outlined.Storage,
                    title = "Nickname",
                    value = state.serverName.ifBlank { "—" }
                )
                InfoSettingsRow(
                    icon = Icons.Outlined.Language,
                    title = "URL",
                    value = state.serverUrl.ifBlank { "—" }
                )
            }

            SettingsSection(title = "Account") {
                ClickableSettingsRow(
                    icon = Icons.Outlined.Logout,
                    title = "Sign out",
                    subtitle = "Remove server and sign out",
                    onClick = { showLogoutDialog = true },
                    tintError = true
                )
            }

            SettingsSection(title = "About") {
                InfoSettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    value = state.appVersion
                )
                ClickableSettingsRow(
                    icon = Icons.Outlined.Code,
                    title = "Source code",
                    subtitle = "View on GitHub",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/anand34577/gotify-plus")
                            )
                        )
                    }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Sign out?") },
            text = { Text("This will remove \"${state.serverName}\" and revoke your client token.") },
            confirmButton = {
                Button(
                    onClick = { onLogout(); showLogoutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showThemeDialog) {
        val themes = listOf("DEFAULT", "AMOLED", "DRACULA", "NORD")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    themes.forEach { theme ->
                        TextButton(
                            onClick = {
                                onSetTheme(theme)
                                showThemeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (theme == state.themeSelection) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Text(theme)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SwitchSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon, null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun ClickableSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    tintError: Boolean = false
) {
    val tint = if (tintError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (tintError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoSettingsRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}