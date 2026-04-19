package com.gotify.client.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

data class SettingsState(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean     = true,
    val dynamicColorEnabled: Boolean  = true,
    val darkThemeEnabled: Boolean     = true,
    val markdownEnabled: Boolean      = true,
    val keepAliveEnabled: Boolean     = true,
    val serverName: String            = "",
    val serverUrl: String             = "",
    val appVersion: String            = "1.0.0"
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
    onToggleMarkdown: (Boolean) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar   = {
            TopAppBar(
                title          = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                    icon        = Icons.Outlined.Notifications,
                    title       = "Enable notifications",
                    subtitle    = "Show push notifications for new messages",
                    checked     = state.notificationsEnabled,
                    onChecked   = onToggleNotifications
                )
                SwitchSettingsRow(
                    icon        = Icons.Outlined.Vibration,
                    title       = "Vibration",
                    subtitle    = "Vibrate for high-priority messages (8–10)",
                    checked     = state.vibrationEnabled,
                    onChecked   = onToggleVibration,
                    enabled     = state.notificationsEnabled
                )


                ClickableSettingsRow(
                    icon     = Icons.Outlined.Inbox,
                    title    = "Notification channels",
                    subtitle = "Configure per-app notification behavior",
                    onClick  = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                ClickableSettingsRow(
                    icon     = Icons.Outlined.BatteryAlert,
                    title    = "Battery optimization",
                    subtitle = "Disable to ensure reliable delivery",
                    onClick  = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }


            SettingsSection(title = "Appearance") {
                SwitchSettingsRow(
                    icon        = Icons.Outlined.DarkMode,
                    title       = "Dark theme",
                    subtitle    = "Use dark color scheme",
                    checked     = state.darkThemeEnabled,
                    onChecked   = onToggleDarkTheme
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchSettingsRow(
                        icon        = Icons.Outlined.Palette,
                        title       = "Dynamic color",
                        subtitle    = "Match your wallpaper colors (Android 12+)",
                        checked     = state.dynamicColorEnabled,
                        onChecked   = onToggleDynamicColor
                    )
                }
                SwitchSettingsRow(
                    icon        = Icons.Outlined.TextFormat,
                    title       = "Render Markdown",
                    subtitle    = "Format message body as Markdown",
                    checked     = state.markdownEnabled,
                    onChecked   = onToggleMarkdown
                )
            }


            SettingsSection(title = "Connection") {
                SwitchSettingsRow(
                    icon        = Icons.Outlined.Sync,
                    title       = "Keep connection alive",
                    subtitle    = "Maintain persistent WebSocket connection",
                    checked     = state.keepAliveEnabled,
                    onChecked   = onToggleKeepAlive
                )
            }


            SettingsSection(title = "Active Server") {
                InfoSettingsRow(
                    icon     = Icons.Outlined.Storage,
                    title    = "Server",
                    value    = state.serverName.ifBlank { "—" }
                )
                InfoSettingsRow(
                    icon     = Icons.Outlined.Language,
                    title    = "URL",
                    value    = state.serverUrl.ifBlank { "—" }
                )
            }


            SettingsSection(title = "Account") {
                ClickableSettingsRow(
                    icon     = Icons.Outlined.Logout,
                    title    = "Sign out",
                    subtitle = "Remove this server and sign out",
                    onClick  = { showLogoutDialog = true },
                    tintError = true
                )
            }


            SettingsSection(title = "About") {
                InfoSettingsRow(
                    icon  = Icons.Outlined.Info,
                    title = "Version",
                    value = state.appVersion
                )
                ClickableSettingsRow(
                    icon     = Icons.Outlined.Code,
                    title    = "Source code",
                    subtitle = "View on GitHub",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/anand34577/gotify_plus_android_app"))
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
            icon    = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Sign out?") },
            text    = { Text("This will remove \"${state.serverName}\" and revoke your client token.") },
            confirmButton = {
                Button(
                    onClick = { onLogout(); showLogoutDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}



@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text       = title.uppercase(),
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier   = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
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
        modifier  = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline
            )
            Text(subtitle,
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

    Surface(
        onClick = onClick,
        color   = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (tintError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoSettingsRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(title,
            style    = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
