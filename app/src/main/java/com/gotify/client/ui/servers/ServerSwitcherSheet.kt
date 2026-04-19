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
    onAddServer: () -> Unit,
    onRemoveServer: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier  = Modifier.fillMaxWidth(),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Servers",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddServer) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add server")
                }
            }

            servers.forEach { server ->
                ServerRow(
                    server           = server,
                    connectionStatus = if (server.isActive) connectionStatus
                                      else ConnectionStatus.DISCONNECTED,
                    onSelect         = { onSwitchServer(server.id) },
                    onRemove         = { onRemoveServer(server.id) }
                )
            }

            if (servers.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No servers added yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: GotifyServer,
    connectionStatus: ConnectionStatus,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (server.isActive)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = containerColor,
        border   = if (server.isActive) BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ) else null
    ) {
        Row(
            modifier  = Modifier.padding(16.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Storage, null,
                        tint     = if (server.isActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text       = server.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (server.isActive) {
                        ConnectionDot(status = connectionStatus)
                    }
                }
                Text(
                    text     = server.baseUrl,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (server.isActive) {
                Icon(
                    Icons.Outlined.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                IconButton(
                    onClick  = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Remove, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
