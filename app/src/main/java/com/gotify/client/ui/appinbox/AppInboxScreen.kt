package com.gotify.client.ui.appinbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.ui.components.AppIcon
import com.gotify.client.ui.components.EmptyState
import com.gotify.client.ui.components.MessageCard
import com.gotify.client.ui.components.MessageCardSkeleton
import com.gotify.client.ui.components.SectionHeader
import com.gotify.client.ui.apps.AppIconResolved

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInboxScreen(
    appName: String,
    appImageUrl: String?,
    clientToken: String,
    serverBaseUrl: String,
    messages: List<GotifyMessage>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    hasMorePages: Boolean,
    cacheSyncTruncated: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onMessageClick: (GotifyMessage) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var messagePendingDelete by remember { mutableStateOf<GotifyMessage?>(null) }
    val pullState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIconResolved(
                            resolvedImageUrl = appImageUrl,
                            appName = appName,
                            clientToken = clientToken,
                            authBaseUrl = serverBaseUrl,
                            size = 32.dp
                        )
                        Text(appName, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Outlined.ClearAll,
                                contentDescription = "Clear all",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
            if (isLoading && messages.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(5) { MessageCardSkeleton() }
                }
            } else if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = "No messages from $appName",
                        subtitle = "Messages sent by this app will appear here"
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SectionHeader(
                            title = "${messages.size} message${if (messages.size != 1) "s" else ""}",
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(messages, key = { it.id }) { message ->
                        MessageCard(
                            title = message.title,
                            message = message.message,
                            appName = appName,
                            appImageUrl = appImageUrl,
                            clientToken = clientToken,
                            authBaseUrl = serverBaseUrl,
                            priority = message.priority,
                            date = message.date,
                            isRead = message.isRead,
                            onClick = { onMessageClick(message) },
                            onDelete = { messagePendingDelete = message }
                        )
                    }
                    if (cacheSyncTruncated) {
                        item {
                            Text(
                                "Sync reached the history safety limit; older messages may not be cached.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    if (hasMorePages && !isLoading) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = MaterialTheme.shapes.medium
                            ) { Text("Load more messages") }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear all messages?") },
            text = { Text("All messages from \"$appName\" will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = { onClearAll(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
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
