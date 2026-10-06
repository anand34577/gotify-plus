package com.gotify.client.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.net.toUri
import com.gotify.client.data.datastore.ThemeMode
import com.gotify.client.ui.components.gotifyTopAppBarColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class SettingsState(
    val notificationsEnabled: Boolean   = true,
    val vibrationEnabled:     Boolean   = true,
    val dynamicColorEnabled:  Boolean   = false,
    val themeMode:            ThemeMode = ThemeMode.SYSTEM,
    val markdownEnabled:      Boolean   = true,
    val keepAliveEnabled:     Boolean   = true,
    val quietHoursEnabled:    Boolean   = false,
    val quietStartMinutes:    Int       = 22 * 60,
    val quietEndMinutes:      Int       = 7 * 60,
    val appLockEnabled:       Boolean   = false,
    val serverIntentsEnabled: Boolean   = false,
    val serverName:           String    = "",
    val serverUrl:            String    = "",
    val appVersion:           String    = "1.0.0"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state:                  SettingsState,
    onToggleNotifications:  (Boolean) -> Unit,
    onToggleVibration:      (Boolean) -> Unit,
    onToggleDynamicColor:   (Boolean) -> Unit,
    onSetThemeMode:         (ThemeMode) -> Unit,
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
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var editingQuietStart by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier       = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar         = {
            TopAppBar(
                title          = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors         = gotifyTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                SettingsSection(title = "Notifications") {
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.Notifications,
                        title     = "Notifications",
                        subtitle  = "Show a notification for each new message",
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
                        subtitle  = if (state.quietHoursEnabled && state.notificationsEnabled)
                            "${formatMinutes(state.quietStartMinutes, is24Hour)} – ${formatMinutes(state.quietEndMinutes, is24Hour)} · high priority still alerts"
                        else "Deliver silently at night; high priority (8+) still alerts",
                        checked   = state.quietHoursEnabled,
                        onChecked = onToggleQuietHours,
                        enabled   = state.notificationsEnabled
                    )
                    AnimatedVisibility(visible = state.quietHoursEnabled && state.notificationsEnabled) {
                        Column {
                            ClickableSettingsRow(
                                icon     = Icons.Outlined.Schedule,
                                title    = "Starts",
                                value    = formatMinutes(state.quietStartMinutes, is24Hour),
                                onClick  = { editingQuietStart = true }
                            )
                            ClickableSettingsRow(
                                icon     = Icons.Outlined.WbSunny,
                                title    = "Ends",
                                value    = formatMinutes(state.quietEndMinutes, is24Hour),
                                onClick  = { editingQuietStart = false }
                            )
                        }
                    }
                    ClickableSettingsRow(
                        icon     = Icons.Outlined.Tune,
                        title    = "Notification channels",
                        subtitle = "Sound and importance per app, in system settings",
                        external = true,
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
                        subtitle = "Allow background activity for reliable delivery",
                        external = true,
                        onClick  = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    )
                }

                SettingsSection(title = "Appearance") {
                    ThemeModeRow(selected = state.themeMode, onSelect = onSetThemeMode)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchSettingsRow(
                            icon      = Icons.Outlined.Palette,
                            title     = "Dynamic color",
                            subtitle  = "Match colors to your wallpaper",
                            checked   = state.dynamicColorEnabled,
                            onChecked = onToggleDynamicColor
                        )
                    }
                    SwitchSettingsRow(
                        icon      = Icons.Outlined.TextFormat,
                        title     = "Render Markdown",
                        subtitle  = "Format messages that are sent as Markdown",
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

                SettingsSection(title = "Active server") {
                    InfoSettingsRow(
                        icon  = Icons.Outlined.Dns,
                        title = "Nickname",
                        value = state.serverName.ifBlank { "—" }
                    )
                    InfoSettingsRow(
                        icon  = Icons.Outlined.Language,
                        title = "URL",
                        value = state.serverUrl.ifBlank { "—" }
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
                        subtitle = "View the project on GitHub",
                        external = true,
                        onClick  = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://github.com/anand34577/gotify-plus".toUri())
                                )
                            }
                        }
                    )
                }

                // Destructive action last, visually separated from regular settings.
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape    = MaterialTheme.shapes.large,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border   = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
                Text(
                    "Signing out removes every saved server and its cached messages from this device.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    editingQuietStart?.let { isStart ->
        val initial = if (isStart) state.quietStartMinutes else state.quietEndMinutes
        val pickerState = rememberTimePickerState(
            initialHour   = initial / 60,
            initialMinute = initial % 60,
            is24Hour      = is24Hour
        )
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
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatMinutes(minutes: Int, is24Hour: Boolean): String =
    LocalTime.of(minutes / 60, minutes % 60)
        .format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a"))

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelLarge,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)
                .semantics { heading() }
        )
        Surface(
            shape    = MaterialTheme.shapes.extraLarge,
            color    = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ThemeModeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "System", Icons.Outlined.BrightnessAuto),
        Triple(ThemeMode.LIGHT,  "Light",  Icons.Outlined.LightMode),
        Triple(ThemeMode.DARK,   "Dark",   Icons.Outlined.DarkMode)
    )
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsIcon(Icons.Outlined.Contrast, enabled = true)
            Column(Modifier.weight(1f)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Choose light, dark, or follow the system",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick  = { onSelect(mode) },
                    shape    = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon     = {
                        SegmentedButtonDefaults.Icon(active = selected == mode) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                        }
                    },
                    label    = { Text(label, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, enabled: Boolean, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        icon, null,
        tint     = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier.size(24.dp)
    )
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
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    // One toggleable target for the whole row; the Switch is decorative so TalkBack announces a single control.
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsIcon(icon, enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else disabled
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabled
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ClickableSettingsRow(
    icon:     ImageVector,
    title:    String,
    onClick:  () -> Unit,
    subtitle: String? = null,
    value:    String? = null,
    external: Boolean = false
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsIcon(icon, enabled = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                if (external) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoSettingsRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsIcon(icon, enabled = true)
        Text(
            title,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier  = Modifier.weight(1f)
        )
    }
}
