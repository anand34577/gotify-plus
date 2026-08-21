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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSwitcherSheet(
    servers: List<GotifyServer>,
    connectionStatus: ConnectionStatus,
    onSwitchServer: (Long) -> Unit,
    onRemoveServer: (Long) -> Unit,
    onAddServer: () -> Unit,
    onDismiss: () -> Unit
) {
    var serverToRemove by remember { mutableStateOf<GotifyServer?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Server",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            servers.forEach { server ->
                ServerRow(
                    server = server,
                    connectionStatus = connectionStatus,
                    onClick = { if (!server.isActive) { onSwitchServer(server.id); onDismiss() } },
                    onRemove = { serverToRemove = server }
                )
            }

            if (servers.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No server configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = { onDismiss(); onAddServer() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
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
                Text("This removes the saved server and its locally cached messages from this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveServer(server.id)
                        serverToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { serverToRemove = null }) { Text("Cancel") }
            }
        )
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
        shape = MaterialTheme.shapes.medium,
        color = if (server.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (server.isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Storage, null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

            if (server.isActive) {
                Icon(Icons.Outlined.CheckCircle, "Active server", tint = MaterialTheme.colorScheme.primary)
            } else {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, "Remove server")
                }
            }

        }
    }
}
