/*
package com.gotify.client.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.gotify.client.data.model.*
import com.gotify.client.ui.components.*
import com.gotify.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    messages:         List<GotifyMessage>,
    applications:     List<GotifyApplication>,
    clientToken:      String,               // FIX 4 — passed down to AppIcon
    connectionStatus: ConnectionStatus,
    activeServerName: String,
    isLoading:        Boolean,
    isRefreshing:     Boolean,
    hasMorePages:     Boolean,
    onRefresh:        () -> Unit,
    onLoadMore:       () -> Unit,
    onMessageClick:   (GotifyMessage) -> Unit,
    onDeleteMessage:  (Long) -> Unit,
    onDeleteAllMessages: () -> Unit,
    onOpenServers:    () -> Unit,
    onOpenSearch:     () -> Unit,
    modifier:         Modifier = Modifier
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState   = remember { SnackbarHostState() }
    val appMap = remember(applications) { applications.associateBy { it.id } }
    val filteredMessages    = messages   // filter chips handled inside

    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier      = modifier,
        snackbarHost  = { SnackbarHost(snackbarHostState) },
        topBar        = {
            HomeTopBar(
                serverName       = activeServerName,
                connectionStatus = connectionStatus,
                onOpenServers    = onOpenServers,
                onOpenSearch     = onOpenSearch,
                onDeleteAll      = { showDeleteAllDialog = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = onRefresh,
            state        = pullRefreshState,
            modifier     = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // App filter chips
                if (applications.isNotEmpty()) {
                    item(key = "filter_chips") {
                        AppFilterChips(
                            applications  = applications,
                            clientToken   = clientToken,    // FIX 4
                            selectedAppId = null,
                            onSelectApp   = {}
                        )
                    }
                }

                if (isLoading && messages.isEmpty()) {
                    items(5, key = { "skeleton_$it" }) { MessageCardSkeleton() }
                }

                if (!isLoading && filteredMessages.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon     = Icons.Outlined.NotificationsNone,
                                title    = "No messages yet",
                                subtitle = "Messages from your Gotify server will appear here"
                            )
                        }
                    }
                }

                items(filteredMessages, key = { it.id }) { message ->
                    val app = appMap[message.appId]
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        MessageCard(
                            title       = message.title,
                            message     = message.message,
                            appName     = app?.name ?: "App ${message.appId}",
                            appImageUrl = app?.image,   // raw path — AppIcon resolves it internally
                            clientToken = clientToken,  // FIX 4
                            priority    = message.priority,
                            date        = message.date,
                            onClick     = { onMessageClick(message) },
                            onDelete    = { onDeleteMessage(message.id) }
                        )
                    }
                }

                if (hasMorePages && !isLoading) {
                    item(key = "load_more") {
                        OutlinedButton(
                            onClick  = onLoadMore,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) { Text("Load more messages") }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon    = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete all messages?") },
            text    = { Text("This will permanently delete all messages across all applications.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteAllMessages(); showDeleteAllDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") } }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    serverName: String,
    connectionStatus: ConnectionStatus,
    onOpenServers: () -> Unit,
    onOpenSearch: () -> Unit,
    onDeleteAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.clickable(onClick = onOpenServers)
            ) {
                Column {
                    Text("Messages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ConnectionDot(status = connectionStatus)
                        Text(
                            text     = serverName,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Outlined.UnfoldMore, null,
                            modifier = Modifier.size(12.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, "Search") }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text("Delete all messages") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                        onClick     = { onDeleteAll(); showMenu = false }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

// ─── App Filter Chips ─────────────────────────────────────────────────────────

@Composable
private fun AppFilterChips(
    applications:  List<GotifyApplication>,
    clientToken:   String,
    selectedAppId: Int?,
    onSelectApp:   (Int) -> Unit
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    Row(
        modifier              = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        applications.forEach { app ->
            val isSelected = selected == app.id
            FilterChip(
                selected = isSelected,
                onClick  = { selected = if (isSelected) null else app.id; onSelectApp(app.id) },
                label    = { Text(app.name, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    // FIX 4 — AppIcon in chip also shows initials correctly
                    AppIcon(
                        imageUrl    = app.image,
                        appName     = app.name,
                        clientToken = clientToken,
                        size        = 18.dp
                    )
                },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

// ─── Delete All Dialog ────────────────────────────────────────────────────────

@Composable
private fun DeleteAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title   = { Text("Delete all messages?") },
        text    = { Text("This will permanently delete all messages across all applications. This cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Delete all") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
*/


package com.gotify.client.ui.home

import androidx.compose.animation.*
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
import com.gotify.client.data.model.*
import com.gotify.client.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    messages:            List<GotifyMessage>,
    applications:        List<GotifyApplication>,
    clientToken:         String,
    connectionStatus:    ConnectionStatus,
    activeServerName:    String,
    isLoading:           Boolean,
    isRefreshing:        Boolean,
    hasMorePages:        Boolean,
    onRefresh:           () -> Unit,
    onLoadMore:          () -> Unit,
    onMessageClick:      (GotifyMessage) -> Unit,
    onDeleteMessage:     (Long) -> Unit,
    onDeleteAllMessages: () -> Unit,
    onOpenServers:       () -> Unit,
    onOpenSearch:        () -> Unit,
    modifier:            Modifier = Modifier
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState   = remember { SnackbarHostState() }

    // ── FIX 1: filter state lives HERE, not inside AppFilterChips ─────────────
    var selectedAppId by remember { mutableStateOf<Int?>(null) }

    val appMap = remember(applications) { applications.associateBy { it.id } }

    // ── FIX 1: this actually filters now ──────────────────────────────────────
    val filteredMessages = remember(messages, selectedAppId) {
        if (selectedAppId == null) messages
        else messages.filter { it.appId == selectedAppId }
    }

    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier       = modifier,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar         = {
            HomeTopBar(
                serverName       = activeServerName,
                connectionStatus = connectionStatus,
                onOpenServers    = onOpenServers,
                onOpenSearch     = onOpenSearch,
                onDeleteAll      = { showDeleteAllDialog = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = onRefresh,
            state        = pullRefreshState,
            modifier     = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // Filter chips
                if (applications.isNotEmpty()) {
                    item(key = "filter_chips") {
                        AppFilterChips(
                            applications  = applications,
                            clientToken   = clientToken,
                            selectedAppId = selectedAppId,        // ← controlled from parent
                            onSelectApp   = { id ->
                                // Toggle: tap selected chip again to clear filter
                                selectedAppId = if (selectedAppId == id) null else id
                            }
                        )
                    }
                }

                // Loading skeletons
                if (isLoading && messages.isEmpty()) {
                    items(5, key = { "skeleton_$it" }) { MessageCardSkeleton() }
                }

                // Empty state
                if (!isLoading && filteredMessages.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon     = Icons.Outlined.NotificationsNone,
                                title    = if (selectedAppId != null) "No messages from this app"
                                else "No messages yet",
                                subtitle = "Messages from your Gotify server will appear here"
                            )
                        }
                    }
                }

                // Message cards
                items(filteredMessages, key = { it.id }) { message ->
                    val app = appMap[message.appId]
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        MessageCard(
                            title       = message.title,
                            message     = message.message,
                            appName     = app?.name ?: "App ${message.appId}",
                            appImageUrl = app?.image,
                            clientToken = clientToken,
                            priority    = message.priority,
                            date        = message.date,
                            onClick     = { onMessageClick(message) },
                            onDelete    = { onDeleteMessage(message.id) }
                        )
                    }
                }

                if (hasMorePages && !isLoading) {
                    item(key = "load_more") {
                        OutlinedButton(
                            onClick  = onLoadMore,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape    = RoundedCornerShape(12.dp)
                        ) { Text("Load more messages") }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon    = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete all messages?") },
            text    = { Text("This will permanently delete all messages across all applications.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteAllMessages(); showDeleteAllDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") } }
        )
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    serverName:       String,
    connectionStatus: ConnectionStatus,
    onOpenServers:    () -> Unit,
    onOpenSearch:     () -> Unit,
    onDeleteAll:      () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.clickable(onClick = onOpenServers)
            ) {
                Column {
                    Text(
                        "Messages",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ConnectionDot(status = connectionStatus)
                        Text(
                            text     = serverName,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Outlined.UnfoldMore, null,
                            modifier = Modifier.size(12.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, "Search") }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text        = { Text("Delete all messages") },
                        leadingIcon = {
                            Icon(Icons.Outlined.DeleteSweep, null,
                                tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { onDeleteAll(); showMenu = false }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ─── App Filter Chips — now fully controlled (no internal state) ──────────────

@Composable
private fun AppFilterChips(
    applications:  List<GotifyApplication>,
    clientToken:   String,
    selectedAppId: Int?,          // ← controlled by parent
    onSelectApp:   (Int) -> Unit  // ← parent decides toggle logic
) {
    Row(
        modifier              = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        applications.forEach { app ->
            val isSelected = selectedAppId == app.id
            FilterChip(
                selected    = isSelected,
                onClick     = { onSelectApp(app.id) },
                label       = { Text(app.name, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    AppIcon(
                        imageUrl    = app.image,
                        appName     = app.name,
                        clientToken = clientToken,
                        size        = 18.dp
                    )
                },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}