package com.gotify.client.ui.apps
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.ui.components.EmptyState
import com.gotify.client.ui.components.SectionHeader
import com.gotify.client.ui.components.ShimmerBox
import com.gotify.client.ui.viewmodel.PublishState
fun resolveAppImageUrl(baseUrl: String, relativePath: String, token: String = ""): String? {
    if (relativePath.isBlank() || 
        relativePath.contains("gotify-logo.png", ignoreCase = true) || 
        relativePath.contains("defaultapp.png", ignoreCase = true)
    ) return null
    val cleanBase = baseUrl.trimEnd('/')
    val cleanPath = relativePath.trimStart('/')
    return if (token.isNotBlank()) {
        "$cleanBase/$cleanPath?token=$token"
    } else {
        "$cleanBase/$cleanPath"
    }
}
@Composable
fun AppIconResolved(
    resolvedImageUrl: String?,
    appName: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val hue = (appName.hashCode().and(0x7FFFFFFF) % 360).toFloat()
    val bg = androidx.compose.ui.graphics.Color.hsl(hue, 0.6f, 0.35f)
    val initial = appName.firstOrNull()?.uppercaseChar() ?: '?'
    if (!resolvedImageUrl.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = resolvedImageUrl,
            contentDescription = appName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
        ) {
            when (painter.state) {
                is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                else -> Box(
                    modifier = Modifier
                        .size(size)
                        .clip(shape)
                        .background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsScreen(
    applications: List<GotifyApplication>,
    messageCounts: Map<Int, Int>,
    serverBaseUrl: String,
    isLoading: Boolean,
    clientToken: String,
    isRefreshing: Boolean,
    publishState: PublishState,
    onRefresh: () -> Unit,
    onAppClick: (GotifyApplication) -> Unit,
    onDeleteApp: (Int) -> Unit,
    onDeleteAppMessages: (Int) -> Unit,
    onCreateApp: (name: String, description: String) -> Unit,
    onPublishMessage: (GotifyApplication, String, String, Int) -> Unit,
    onResetPublishState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPublishSheet by remember { mutableStateOf(false) }
    var selectedPublishApp by remember { mutableStateOf<GotifyApplication?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pullState = rememberPullToRefreshState()
    val displayedApps = remember(applications) {
        applications.filter { !it.internal }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Applications", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Add, "New application")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && displayedApps.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(4) { ShimmerBox(Modifier
                        .fillMaxWidth()
                        .height(80.dp)) }
                }
            } else if (displayedApps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Apps,
                        title = "No applications yet",
                        subtitle = "Applications send messages to your Gotify server",
                        action = {
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SectionHeader(
                            title = "${displayedApps.size} application${if (displayedApps.size != 1) "s" else ""}",
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(displayedApps, key = { it.id }) { app ->
                        val resolvedImageUrl = resolveAppImageUrl(serverBaseUrl, app.image, clientToken)
                        ApplicationCard(
                            application = app,
                            resolvedImageUrl = resolvedImageUrl,
                            messageCount = messageCounts[app.id] ?: 0,
                            onClick = { onAppClick(app) },
                            onDelete = { onDeleteApp(app.id) },
                            onClearMessages = { onDeleteAppMessages(app.id) },
                            onPublishClick = {
                                selectedPublishApp = app
                                showPublishSheet = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
    if (showCreateSheet) {
        CreateApplicationSheet(
            onCreate = { name, desc -> onCreateApp(name, desc); showCreateSheet = false },
            onDismiss = { showCreateSheet = false }
        )
    }
    if (showPublishSheet) {
        PublishMessageSheet(
            applications = displayedApps,
            initialSelectedApp = selectedPublishApp,
            publishState = publishState,
            onPublish = onPublishMessage,
            onDismiss = { showPublishSheet = false },
            onResetState = onResetPublishState
        )
    }
}
@Composable
private fun ApplicationCard(
    application: GotifyApplication,
    resolvedImageUrl: String?,
    messageCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onClearMessages: () -> Unit,
    onPublishClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppIconResolved(
                resolvedImageUrl = resolvedImageUrl,
                appName = application.name,
                size = 48.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = application.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (application.internal) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "INTERNAL",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                if (application.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = application.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "Token: ${application.token.take(8)}…",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (messageCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                "$messageCount msgs",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onPublishClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Send,
                    "Publish message",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("View messages") },
                        leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                        onClick = { onClick(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Clear messages") },
                        leadingIcon = { Icon(Icons.Outlined.ClearAll, null) },
                        onClick = { onClearMessages(); showMenu = false }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete application",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { showConfirm = true; showMenu = false }
                    )
                }
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"${application.name}\"?") },
            text = { Text("This will delete the application and all its messages permanently.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateApplicationSheet(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "New Application",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Application name") }, placeholder = { Text("My App") },
                singleLine = true, shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description (optional)") },
                maxLines = 3, shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), description.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Create Application", fontWeight = FontWeight.SemiBold) }
        }
    }
}