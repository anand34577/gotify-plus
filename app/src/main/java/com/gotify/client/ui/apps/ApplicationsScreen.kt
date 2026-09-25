package com.gotify.client.ui.apps

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.ui.components.*
import java.net.URI





// Gotify returns "static/defaultapp.jpg" (its own logo) for apps with no custom
// icon uploaded — treat it as "no image" so the initials avatar shows instead.
// Gotify server (api/application.go withResolvedImage) returns "static/defaultapp.png" for apps
// without an uploaded icon. Match any extension and relative/absolute/sub-path forms.
private val GOTIFY_DEFAULT_APP_IMAGE = Regex("""(^|/)static/defaultapp\.[a-z]+$""", RegexOption.IGNORE_CASE)

fun resolveAppImageUrl(baseUrl: String, relativePath: String): String? {
    if (baseUrl.isBlank() || relativePath.isBlank()) return null
    if (GOTIFY_DEFAULT_APP_IMAGE.containsMatchIn(relativePath.substringBefore('?'))) return null
    val candidate = if (relativePath.startsWith("https://", ignoreCase = true) ||
        relativePath.startsWith("http://", ignoreCase = true)
    ) {
        relativePath
    } else {
        "${baseUrl.trimEnd('/')}/${relativePath.trimStart('/')}"
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    return candidate.takeIf {
        uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)
    }
}





private fun muteLabel(untilMillis: Long): String {
    if (untilMillis == Long.MAX_VALUE) return "Muted"
    val until = java.time.Instant.ofEpochMilli(untilMillis).atZone(java.time.ZoneId.systemDefault())
    val pattern = if (until.toLocalDate() == java.time.LocalDate.now()) "HH:mm" else "EEE HH:mm"
    return "Muted until " + until.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
}

@Composable
fun AppIconResolved(
    resolvedImageUrl: String?,
    appName: String,
    modifier: Modifier = Modifier,
    clientToken: String = "",
    authBaseUrl: String = "",
    size: Dp = 40.dp
) {
    AppIcon(
        imageUrl = resolvedImageUrl,
        appName = appName,
        modifier = modifier,
        clientToken = clientToken,
        authBaseUrl = authBaseUrl,
        size = size
    )
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsScreen(
    applications: List<GotifyApplication>,
    messageCounts: Map<Int, Int>,
    mutedUntil: Map<Int, Long>,
    onMuteApp: (appId: Int, untilMillis: Long?) -> Unit,
    serverBaseUrl: String,
    isLoading: Boolean,
    clientToken: String,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onAppClick: (GotifyApplication) -> Unit,
    onDeleteApp: (Int) -> Unit,
    onDeleteAppMessages: (Int) -> Unit,
    onCreateApp: (name: String, description: String) -> Unit,
    onEditApp: (appId: Int, name: String, description: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier     = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title   = { Text("Applications", fontWeight = FontWeight.Bold) },
                actions = {

                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, "Refresh")
                    }
                },
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                shape          = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Add, "New application")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->


        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = onRefresh,
            state        = pullState,
            modifier     = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && applications.isEmpty()) {
                LazyColumn(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                        .align(Alignment.TopCenter),
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(4) { ShimmerBox(Modifier.fillMaxWidth().height(80.dp)) }
                }
            } else if (applications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon     = Icons.Outlined.Apps,
                        title    = "No applications yet",
                        subtitle = "Applications send messages to your Gotify server",
                        action   = {
                            Button(onClick = { showCreateSheet = true }) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create Application")
                            }
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 960.dp)
                        .align(Alignment.TopCenter),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SectionHeader(
                            title    = "${applications.size} application${if (applications.size != 1) "s" else ""}",
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(applications, key = { it.id }) { app ->

                        val resolvedImageUrl = resolveAppImageUrl(serverBaseUrl, app.image)

                        ApplicationCard(
                            application      = app,
                            resolvedImageUrl = resolvedImageUrl,
                            clientToken      = clientToken,
                            authBaseUrl      = serverBaseUrl,
                            messageCount     = messageCounts[app.id] ?: 0,
                            mutedUntil       = mutedUntil[app.id],
                            onMute           = { until -> onMuteApp(app.id, until) },
                            onClick          = { onAppClick(app) },
                            onDelete         = { onDeleteApp(app.id) },
                            onClearMessages  = { onDeleteAppMessages(app.id) },
                            onEdit          = { name, description ->
                                onEditApp(app.id, name, description)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCreateSheet) {
        ApplicationEditorSheet(
            title     = "New application",
            onSave   = { name, desc -> onCreateApp(name, desc); showCreateSheet = false },
            onDismiss = { showCreateSheet = false }
        )
    }
}



@Composable
private fun ApplicationCard(
    application:      GotifyApplication,
    resolvedImageUrl: String?,
    clientToken: String,
    authBaseUrl: String,
    messageCount:     Int,
    mutedUntil:       Long?,
    onClick:          () -> Unit,
    onDelete:         () -> Unit,
    onClearMessages:  () -> Unit,
    onMute:           (untilMillis: Long?) -> Unit,
    onEdit:           (name: String, description: String) -> Unit,
    modifier:         Modifier = Modifier
) {
    var showMenu    by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    val isMuted = mutedUntil != null && mutedUntil > System.currentTimeMillis()

    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp),
        border    = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            AppIconResolved(
                resolvedImageUrl = resolvedImageUrl,
                appName          = application.name,
                clientToken      = clientToken,
                authBaseUrl      = authBaseUrl,
                size             = 48.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text       = application.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (application.internal) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "INTERNAL",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (application.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = application.description,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (application.token != null) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            if (application.token != null) "Token saved" else "Token unavailable",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (application.token != null) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (messageCount > 0) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                            Text(
                                "$messageCount msgs",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (isMuted) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Outlined.NotificationsOff, null, Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    muteLabel(mutedUntil!!),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text("View messages") },
                        leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                        onClick     = { onClick(); showMenu = false }
                    )
                    if (!application.internal) {
                        DropdownMenuItem(
                            text        = { Text("Edit application") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick     = { showEditSheet = true; showMenu = false }
                        )
                    }
                    if (isMuted) {
                        DropdownMenuItem(
                            text        = { Text("Unmute notifications") },
                            leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                            onClick     = { onMute(null); showMenu = false }
                        )
                    } else {
                        DropdownMenuItem(
                            text        = { Text("Mute notifications…") },
                            leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null) },
                            onClick     = { showMuteDialog = true; showMenu = false }
                        )
                    }
                    DropdownMenuItem(
                        text        = { Text("Clear messages") },
                        leadingIcon = { Icon(Icons.Outlined.ClearAll, null) },
                        onClick     = { showClearConfirm = true; showMenu = false }
                    )
                    if (!application.internal) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text        = { Text("Delete application", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showConfirm = true; showMenu = false }
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon    = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete \"${application.name}\"?") },
            text    = { Text("This will delete the application and all its messages permanently.") },
            confirmButton = {
                Button(onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showMuteDialog) {
        val hour = 60 * 60 * 1000L
        val options = listOf(
            "1 hour" to hour,
            "8 hours" to 8 * hour,
            "24 hours" to 24 * hour,
            "Until I turn it back on" to null
        )
        AlertDialog(
            onDismissRequest = { showMuteDialog = false },
            icon  = { Icon(Icons.Outlined.NotificationsOff, null) },
            title = { Text("Mute ${application.name}") },
            text  = {
                Column {
                    options.forEach { (label, duration) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable {
                                onMute(duration?.let { System.currentTimeMillis() + it } ?: Long.MAX_VALUE)
                                showMuteDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMuteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Outlined.ClearAll, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear ${application.name} messages?") },
            text = { Text("All cached and server messages from this application will be deleted.") },
            confirmButton = {
                Button(
                    onClick = { onClearMessages(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear messages") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditSheet) {
        ApplicationEditorSheet(
            title = "Edit application",
            initialName = application.name,
            initialDescription = application.description,
            onSave = { name, description ->
                onEdit(name, description)
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false }
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplicationEditorSheet(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name        by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Application name") }, placeholder = { Text("My App") },
                singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description (optional)") },
                maxLines = 3, shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick  = { if (name.isNotBlank()) onSave(name.trim(), description.trim()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = name.isNotBlank(),
                shape    = RoundedCornerShape(12.dp)
            ) { Text(if (initialName.isBlank()) "Create application" else "Save changes", fontWeight = FontWeight.SemiBold) }
        }
    }
}
