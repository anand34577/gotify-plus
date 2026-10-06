package com.gotify.client.ui.servers

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.ui.components.ConnectionDot
import com.gotify.client.ui.components.ConnectionStatus
import com.gotify.client.ui.components.SectionHeader
import com.gotify.client.ui.viewmodel.ServerInfoUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSwitcherSheet(
    servers: List<GotifyServer>,
    connectionStatus: ConnectionStatus,
    serverInfo: ServerInfoUiState,
    onSwitchServer: (Long) -> Unit,
    onRemoveServer: (Long) -> Unit,
    onRefreshInfo: () -> Unit,
    onAddServer: () -> Unit,
    onDismiss: () -> Unit
) {
    var serverToRemove by remember { mutableStateOf<GotifyServer?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Many saved servers must stay reachable on short screens.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Servers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            ConnectionCard(
                connectionStatus = connectionStatus,
                serverInfo       = serverInfo,
                onRefreshInfo    = onRefreshInfo
            )

            if (servers.isNotEmpty()) {
                SectionHeader(
                    title    = "Saved servers",
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            servers.forEach { server ->
                ServerRow(
                    server = server,
                    connectionStatus = connectionStatus,
                    onClick = { if (!server.isActive) { onSwitchServer(server.id); onDismiss() } },
                    onRemove = { serverToRemove = server }
                )
            }

            if (servers.isEmpty()) {
                Text(
                    "No server configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            OutlinedButton(
                onClick = { onDismiss(); onAddServer() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Add server")
            }
        }
    }

    serverToRemove?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToRemove = null },
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove ${server.name}?") },
            text = {
                Text(
                    if (server.isActive && servers.size > 1)
                        "This removes the server and its cached messages from this device. Gotify+ will switch to another saved server."
                    else "This removes the saved server and its locally cached messages from this device."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveServer(server.id)
                        serverToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { serverToRemove = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    connectionStatus: ConnectionStatus,
    serverInfo: ServerInfoUiState,
    onRefreshInfo: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConnectionDot(connectionStatus)
                    Text(
                        when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "Stream is live"
                            ConnectionStatus.CONNECTING -> "Connecting…"
                            ConnectionStatus.ERROR -> "Connection needs attention"
                            ConnectionStatus.DISCONNECTED -> "Stream is offline"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                val details = when {
                    serverInfo.isLoading -> "Checking server…"
                    serverInfo.version != null || serverInfo.health != null -> buildString {
                        serverInfo.version?.let { append("Gotify $it") }
                        serverInfo.health?.let {
                            if (isNotEmpty()) append(" · ")
                            append("Health: $it")
                        }
                        serverInfo.database?.let {
                            if (isNotEmpty()) append(" · ")
                            append("DB: $it")
                        }
                    }
                    else -> serverInfo.errorMessage
                }
                if (details != null) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                }
            }
            if (serverInfo.isLoading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                IconButton(onClick = onRefreshInfo) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Check server status")
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: GotifyServer,
    connectionStatus: ConnectionStatus,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (server.isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (server.isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (server.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (server.isActive) Icons.Outlined.Check else Icons.Outlined.Dns,
                        contentDescription = if (server.isActive) "Active server" else null,
                        tint = if (server.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (server.isActive) ConnectionDot(status = connectionStatus)
                }
                Text(
                    text = server.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${server.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
