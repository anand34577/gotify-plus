package com.gotify.client.ui.search
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.ui.components.AppIcon
import com.gotify.client.ui.components.EmptyState
import com.gotify.client.ui.components.PriorityBadge
import com.gotify.client.ui.components.RelativeTime
import com.gotify.client.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<GotifyMessage>,
    applications: Map<Int, GotifyApplication>,
    selectedPriority: String,
    selectedAppId: Int,
    selectedDateFilter: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onPriorityChange: (String) -> Unit,
    onAppIdChange: (Int) -> Unit,
    onDateFilterChange: (String) -> Unit,
    onBack: () -> Unit,
    onMessageClick: (GotifyMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("Search messages…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        trailingIcon = {
                            AnimatedVisibility(visible = query.isNotEmpty()) {
                                IconButton(onClick = onClearQuery) {
                                    Icon(Icons.Outlined.Clear, "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchFiltersRow(
                applications = applications,
                selectedPriority = selectedPriority,
                selectedAppId = selectedAppId,
                selectedDateFilter = selectedDateFilter,
                onPriorityChange = onPriorityChange,
                onAppIdChange = onAppIdChange,
                onDateFilterChange = onDateFilterChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    query.isBlank() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Outlined.Search,
                                title = "Search messages",
                                subtitle = "Search by title or message content"
                            )
                        }
                    }
                    results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Outlined.SearchOff,
                                title = "No results for \"$query\"",
                                subtitle = "Try a different search term or change filters"
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                SectionHeader(
                                    title = "${results.size} result${if (results.size != 1) "s" else ""}",
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            items(results, key = { it.id }) { message ->
                                val app = applications[message.appId]
                                SearchResultCard(
                                    message = message,
                                    appName = app?.name ?: "App ${message.appId}",
                                    appImageUrl = app?.image,
                                    query = query,
                                    onClick = { onMessageClick(message) }
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFiltersRow(
    applications: Map<Int, GotifyApplication>,
    selectedPriority: String,
    selectedAppId: Int,
    selectedDateFilter: String,
    onPriorityChange: (String) -> Unit,
    onAppIdChange: (Int) -> Unit,
    onDateFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPriorityMenu by remember { mutableStateOf(false) }
    var showAppMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Priority Filter
        Box {
            FilterChip(
                selected = selectedPriority != "All",
                onClick = { showPriorityMenu = true },
                label = { Text(if (selectedPriority == "All") "Priority: All" else "Priority: $selectedPriority", style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(16.dp)
            )
            DropdownMenu(
                expanded = showPriorityMenu,
                onDismissRequest = { showPriorityMenu = false }
            ) {
                listOf("All", "Low", "Normal", "High").forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onPriorityChange(p)
                            showPriorityMenu = false
                        }
                    )
                }
            }
        }

        // App Filter
        Box {
            val selectedAppName = if (selectedAppId == -1) "All Apps" else applications[selectedAppId]?.name ?: "Unknown App"
            FilterChip(
                selected = selectedAppId != -1,
                onClick = { showAppMenu = true },
                label = { Text(selectedAppName, style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(16.dp)
            )
            DropdownMenu(
                expanded = showAppMenu,
                onDismissRequest = { showAppMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All Apps", style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onAppIdChange(-1)
                        showAppMenu = false
                    }
                )
                applications.values.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.name, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onAppIdChange(app.id)
                            showAppMenu = false
                        }
                    )
                }
            }
        }

        // Date Filter
        Box {
            val dateLabel = when (selectedDateFilter) {
                "24h" -> "Last 24 hours"
                "7d" -> "Last 7 days"
                else -> "Date: Anytime"
            }
            FilterChip(
                selected = selectedDateFilter != "Anytime",
                onClick = { showDateMenu = true },
                label = { Text(dateLabel, style = MaterialTheme.typography.labelMedium) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(16.dp)
            )
            DropdownMenu(
                expanded = showDateMenu,
                onDismissRequest = { showDateMenu = false }
            ) {
                listOf("Anytime" to "Anytime", "24h" to "Last 24 hours", "7d" to "Last 7 days").forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onDateFilterChange(value)
                            showDateMenu = false
                        }
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultCard(
    message: GotifyMessage,
    appName: String,
    appImageUrl: String?,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcon(imageUrl = appImageUrl, appName = appName, size = 28.dp)
                Text(
                    text = appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                PriorityBadge(priority = message.priority)
                RelativeTime(isoDate = message.date)
            }
            Spacer(Modifier.height(8.dp))
            if (message.title.isNotBlank()) {
                Text(
                    text = highlightQuery(message.title, query),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = highlightQuery(message.message, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
@Composable
private fun highlightQuery(text: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var start = 0
    while (start < text.length) {
        val idx = lowerText.indexOf(lowerQuery, start)
        if (idx == -1) {
            append(text.substring(start))
            break
        }
        append(text.substring(start, idx))
        withStyle(
            SpanStyle(
                background = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(text.substring(idx, idx + query.length))
        }
        start = idx + query.length
    }
}
