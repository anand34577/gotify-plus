package com.gotify.client.ui.servers
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.ui.components.ConnectionDot
import com.gotify.client.ui.components.ConnectionStatus
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSwitcherSheet(
    servers: List<GotifyServer>,
    connectionStatus: ConnectionStatus,
    onDismiss: () -> Unit,
    onAddServer: () -> Unit,
    onSwitchServer: (Long) -> Unit,
    onDeleteServer: (Long) -> Unit
) {
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
                    onSwitchServer = { onSwitchServer(server.id) },
                    onDeleteServer = { onDeleteServer(server.id) }
                )
            }
            if (servers.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No server configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            androidx.compose.material3.Button(
                onClick = onAddServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Add, null, modifier = Modifier.size(20.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Add Server", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
@Composable
private fun ServerRow(
    server: GotifyServer,
    connectionStatus: ConnectionStatus,
    onSwitchServer: () -> Unit,
    onDeleteServer: () -> Unit
) {
    Surface(
        onClick = onSwitchServer,
        color = if (server.isActive) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (server.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
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
                    if (server.isActive) {
                        ConnectionDot(status = connectionStatus)
                    }
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
                Icon(
                    androidx.compose.material.icons.Icons.Outlined.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                androidx.compose.material3.IconButton(
                    onClick = onDeleteServer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Outlined.Delete, "Delete server",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}