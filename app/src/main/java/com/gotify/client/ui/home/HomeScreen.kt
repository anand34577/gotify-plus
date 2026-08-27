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
    serverBaseUrl:       String,
    connectionStatus:    ConnectionStatus,
    activeServerName:    String,
    unreadCount:         Int,
    cachedMessageCount:  Int,
    cacheSyncTruncated:  Boolean,
    isLoading:           Boolean,
    isRefreshing:        Boolean,
    hasMorePages:        Boolean,
    errorMessage:        String?,
    onRefresh:           () -> Unit,
    onLoadMore:          () -> Unit,
    onMessageClick:      (GotifyMessage) -> Unit,
    onDeleteMessage:     (Long) -> Unit,
    onDeleteAllMessages: () -> Unit,
    onMarkAllAsRead:     () -> Unit,
    onOpenServers:       () -> Unit,
    onOpenSearch:        () -> Unit,
    modifier:            Modifier = Modifier
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var messagePendingDelete by remember { mutableStateOf<GotifyMessage?>(null) }
    val snackbarHostState   = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    // ── FIX 1: filter state lives HERE, not inside AppFilterChips ─────────────
    var selectedAppId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(applications) {
        if (selectedAppId != null && applications.none { it.id == selectedAppId }) {
            selectedAppId = null
        }
    }

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
                onDeleteAll      = { showDeleteAllDialog = true },
                unreadCount      = unreadCount,
                onMarkAllAsRead  = onMarkAllAsRead
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
                modifier            = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 960.dp)
                    .align(Alignment.TopCenter),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                item(key = "summary") {
                    HomeSummaryCard(
                        unreadCount = unreadCount,
                        cachedCount = cachedMessageCount,
                        connectionStatus = connectionStatus,
                        cacheSyncTruncated = cacheSyncTruncated
                    )
                }

                errorMessage?.let { error ->
                    item(key = "sync_error") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRefresh) { Text("Retry") }
                            }
                        }
                    }
                }

                // Filter chips
                if (applications.isNotEmpty()) {
                    item(key = "filter_chips") {
                        AppFilterChips(
                            applications  = applications,
                            clientToken   = clientToken,
                            authBaseUrl   = serverBaseUrl,
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
                            authBaseUrl = serverBaseUrl,
                            priority    = message.priority,
                            date        = message.date,
                            isRead      = message.isRead,
                            onClick     = { onMessageClick(message) },
                            onDelete    = { messagePendingDelete = message }
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

    messagePendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { messagePendingDelete = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete message?") },
            text = { Text("This permanently removes the message from Gotify and this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMessage(pending.id)
                        messagePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { messagePendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HomeSummaryCard(
    unreadCount: Int,
    cachedCount: Int,
    connectionStatus: ConnectionStatus,
    cacheSyncTruncated: Boolean,
    modifier: Modifier = Modifier
) {
    val connectionLabel = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> "Live"
        ConnectionStatus.CONNECTING -> "Connecting"
        ConnectionStatus.ERROR -> "Connection issue"
        ConnectionStatus.DISCONNECTED -> "Offline"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Live stream",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConnectionDot(connectionStatus)
                        Text(
                            connectionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Text(
                text = if (unreadCount == 0) "All clear" else "$unreadCount to review",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$cachedCount cached message${if (cachedCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )
            if (cacheSyncTruncated) {
                Text(
                    "Sync reached the history safety limit; older messages may not be cached.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
        }
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
    onDeleteAll:      () -> Unit,
    unreadCount:      Int,
    onMarkAllAsRead:  () -> Unit
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
                    if (unreadCount > 0) {
                        DropdownMenuItem(
                            text        = { Text("Mark all as read") },
                            leadingIcon = { Icon(Icons.Outlined.DoneAll, null) },
                            onClick     = { onMarkAllAsRead(); showMenu = false }
                        )
                    }
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
    authBaseUrl:   String,
    selectedAppId: Int?,          // ← controlled by parent
    onSelectApp:   (Int?) -> Unit  // ← parent decides toggle logic
) {
    Row(
        modifier              = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedAppId == null,
            onClick = { onSelectApp(null) },
            label = { Text("All") },
            leadingIcon = {
                Icon(Icons.Outlined.AllInbox, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            shape = RoundedCornerShape(8.dp)
        )
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
                        authBaseUrl = authBaseUrl,
                        size        = 18.dp
                    )
                },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
