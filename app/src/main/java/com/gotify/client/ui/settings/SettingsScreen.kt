package com.gotify.client.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.net.toUri

data class SettingsState(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled:     Boolean = true,
    val dynamicColorEnabled:  Boolean = false,
    val darkThemeEnabled:     Boolean = true,
    val markdownEnabled:      Boolean = true,
    val keepAliveEnabled:     Boolean = true,
    val quietHoursEnabled:    Boolean = false,
    val quietStartMinutes:    Int     = 22 * 60,
    val quietEndMinutes:      Int     = 7 * 60,
    val appLockEnabled:       Boolean = false,
    val serverIntentsEnabled: Boolean = false,
    val serverName:           String  = "",
    val serverUrl:            String  = "",
    val appVersion:           String  = "1.0.0"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state:                  SettingsState,
    onBack:                 () -> Unit,
    onToggleNotifications:  (Boolean) -> Unit,
    onToggleVibration:      (Boolean) -> Unit,
    onToggleDynamicColor:   (Boolean) -> Unit,
    onToggleDarkTheme:      (Boolean) -> Unit,
    onToggleMarkdown:       (Boolean) -> Unit,
    onToggleKeepAlive:      (Boolean) -> Unit,
    onToggleQuietHours:     (Boolean) -> Unit,
    onSetQuietHours:        (start: Int, end: Int) -> Unit,
    onToggleAppLock:        (Boolean) -> Unit,
    onToggleServerIntents:  (Boolean) -> Unit,
    onLogout:               () -> Unit,
    modifier:               Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var editingQuietStart by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end

    Scaffold(
        modifier       = modifier,
        topBar         = {
            TopAppBar(
                title          = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                expandedHeight = 56.dp,
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
                    icon      = Icons.Outlined.Notifications,
                    title     = "Enable notifications",
                    subtitle  = "Show push notifications for new messages",
                    checked   = state.notificationsEnabled,
                    onChecked = onToggleNotifications
                )
                SwitchSettingsRow(
                    icon      = Icons.Outlined.Vibration,
                    title     = "Vibration",
                    subtitle  = "Vibrate for high-priority messages",
                    checked   = state.vibrationEnabled,
                    onChecked = onToggleVibration,
                    enabled   = state.notificationsEnabled
                )
                SwitchSettingsRow(
                    icon      = Icons.Outlined.Bedtime,
                    title     = "Quiet hours",
                    subtitle  = "Deliver silently; high priority (8+) still alerts",
                    checked   = state.quietHoursEnabled,
                    onChecked = onToggleQuietHours,
                    enabled   = state.notificationsEnabled
                )
                if (state.quietHoursEnabled && state.notificationsEnabled) {
                    ClickableSettingsRow(
                        icon     = Icons.Outlined.Schedule,
                        title    = "Starts",
                        subtitle = formatMinutes(state.quietStartMinutes),
                        onClick  = { editingQuietStart = true }
                    )
                    ClickableSettingsRow(
                        icon     = Icons.Outlined.WbSunny,
                        title    = "Ends",
                        subtitle = formatMinutes(state.quietEndMinutes),
                        onClick  = { editingQuietStart = false }
                    )
                }
                ClickableSettingsRow(
                    icon     = Icons.Outlined.Tune,
                    title    = "Notification channels",
                    subtitle = "Configure per-app notification behavior",
                    onClick  = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                )
                ClickableSettingsRow(
                    icon     = Icons.Outlined.BatteryAlert,
                    title    = "Battery optimization",
                    subtitle = "Review system battery settings for reliable delivery",
                    onClick  = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                )
            }

            SettingsSection(title = "Appearance") {
                SwitchSettingsRow(
                    icon      = Icons.Outlined.DarkMode,
                    title     = "Dark theme",
                    subtitle  = "Use dark color scheme",
                    checked   = state.darkThemeEnabled,
                    onChecked = onToggleDarkTheme
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Palette,
                        title     = "Dynamic color",
                        subtitle  = "Match wallpaper colors (Android 12+)",
                        checked   = state.dynamicColorEnabled,
                        onChecked = onToggleDynamicColor
                    )
                }
                SwitchSettingsRow(
                    icon      = Icons.Outlined.TextFormat,
                    title     = "Render Markdown",
                    subtitle  = "Format message bodies as Markdown",
                    checked   = state.markdownEnabled,
                    onChecked = onToggleMarkdown
                )
            }

            SettingsSection(title = "Connection") {
                SwitchSettingsRow(
                    icon      = Icons.Outlined.Sync,
                    title     = "Keep connection alive",
                    subtitle  = if (state.keepAliveEnabled) "Instant delivery via a persistent connection"
                                else "Off: checks for new messages about every 15 minutes",
                    checked   = state.keepAliveEnabled,
                    onChecked = onToggleKeepAlive
                )
            }

            SettingsSection(title = "Privacy & security") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Fingerprint,
                        title     = "App lock",
                        subtitle  = "Require biometrics or screen lock to open",
                        checked   = state.appLockEnabled,
                        onChecked = onToggleAppLock
                    )
                }
                SwitchSettingsRow(
                    icon      = Icons.Outlined.Bolt,
                    title     = "Allow server actions",
                    subtitle  = "Let messages trigger Android broadcasts on arrival",
                    checked   = state.serverIntentsEnabled,
                    onChecked = onToggleServerIntents
                )
            }

            SettingsSection(title = "Active Server") {
                InfoSettingsRow(
                    icon  = Icons.Outlined.Storage,
                    title = "Nickname",
                    value = state.serverName.ifBlank { "—" }
                )
                InfoSettingsRow(
                    icon  = Icons.Outlined.Language,
                    title = "URL",
                    value = state.serverUrl.ifBlank { "—" }
                )
            }

            SettingsSection(title = "Account") {
                ClickableSettingsRow(
                    icon      = Icons.AutoMirrored.Outlined.Logout,
                    title     = "Sign out",
                    subtitle  = "Remove saved servers and credentials from this device",
                    onClick   = { showLogoutDialog = true },
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
                    icon    = Icons.Outlined.Code,
                    title   = "Source code",
                    subtitle = "View on GitHub",
                    onClick  = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW,
                                "https://github.com/anand34577/gotify-plus".toUri())
                        )
                    }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    editingQuietStart?.let { isStart ->
        val initial = if (isStart) state.quietStartMinutes else state.quietEndMinutes
        val pickerState = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60)
        AlertDialog(
            onDismissRequest = { editingQuietStart = null },
            title = { Text(if (isStart) "Quiet hours start" else "Quiet hours end") },
            text  = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.hour * 60 + pickerState.minute
                    if (isStart) onSetQuietHours(picked, state.quietEndMinutes)
                    else onSetQuietHours(state.quietStartMinutes, picked)
                    editingQuietStart = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editingQuietStart = null }) { Text("Cancel") } }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon    = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Sign out?") },
            text    = { Text("This removes every saved server and its local cache from this device. The active client token will also be revoked when the server accepts the request.") },
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

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text          = title.uppercase(),
            style         = MaterialTheme.typography.labelSmall,
            color         = MaterialTheme.colorScheme.primary,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier      = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Surface(
            shape    = MaterialTheme.shapes.large,
            color    = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SwitchSettingsRow(
    icon:      ImageVector,
    title:     String,
    subtitle:  String,
    checked:   Boolean,
    onChecked: (Boolean) -> Unit,
    enabled:   Boolean = true
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChecked(!checked) }
            .padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon, null,
            tint     = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface
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
    icon:      ImageVector,
    title:     String,
    subtitle:  String? = null,
    onClick:   () -> Unit,
    tintError: Boolean = false
) {
    val tint = if (tintError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = if (tintError) MaterialTheme.colorScheme.error
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
                tint     = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoSettingsRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            title,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
