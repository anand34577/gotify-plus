package com.gotify.client.ui.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.ui.viewmodel.PublishState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishMessageSheet(
    applications: List<GotifyApplication>,
    initialSelectedApp: GotifyApplication?,
    publishState: PublishState,
    onPublish: (GotifyApplication, String, String, Int) -> Unit,
    onDismiss: () -> Unit,
    onResetState: () -> Unit
) {
    var selectedApp by remember { mutableStateOf(initialSelectedApp ?: applications.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(5f) } // Default priority = 5 (Normal)
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(publishState) {
        if (publishState is PublishState.Success) {
            onDismiss()
            onResetState()
        }
    }

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            onResetState()
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Publish Message",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // App Selector
            if (applications.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Target Application",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val appName = selectedApp?.name ?: "Select an application"
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            applications.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text(app.name, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        selectedApp = app
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No applications available. Please create an application first.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title (optional)") },
                placeholder = { Text("Enter message title") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Message Field
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                placeholder = { Text("Enter message body") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp)
            )

            // Priority Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val priorityVal = priority.roundToInt()
                val (priorityLabel, priorityColor) = when (priorityVal) {
                    0 -> "Min (Silent)" to Color.Gray
                    in 1..3 -> "Low" to MaterialTheme.colorScheme.primary
                    in 4..7 -> "Normal" to MaterialTheme.colorScheme.secondary
                    in 8..10 -> "High / Max" to Color(0xFFD32F2F) // Deep Red
                    else -> "Normal" to MaterialTheme.colorScheme.secondary
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Priority: $priorityVal",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = priorityColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = priorityLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Slider(
                    value = priority,
                    onValueChange = { priority = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = priorityColor,
                        activeTrackColor = priorityColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            // Error Message
            if (publishState is PublishState.Error) {
                Text(
                    text = publishState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Send Button
            Button(
                onClick = {
                    val app = selectedApp
                    if (app != null && message.isNotBlank()) {
                        onPublish(app, title.trim(), message.trim(), priority.roundToInt())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = selectedApp != null && message.isNotBlank() && publishState !is PublishState.Loading,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (publishState is PublishState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Publish Message", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
