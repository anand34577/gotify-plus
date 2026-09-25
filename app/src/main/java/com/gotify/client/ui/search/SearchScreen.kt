package com.gotify.client.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.gotify.client.ui.components.*
import com.gotify.client.ui.viewmodel.DateRange
import com.gotify.client.ui.viewmodel.PriorityFilter
import com.gotify.client.ui.viewmodel.SearchFilters
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<GotifyMessage>,
    applications: Map<Int, GotifyApplication>,
    clientToken: String,
    serverBaseUrl: String,
    filters: SearchFilters,
    onPriorityFilter: (PriorityFilter) -> Unit,
    onAppFilter: (Int?) -> Unit,
    onDateRange: (DateRange) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
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
        topBar   = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value         = query,
                        onValueChange = onQueryChange,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder   = { Text("Search messages…") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction    = ImeAction.Search
                        ),
                        trailingIcon  = {
                            AnimatedVisibility(visible = query.isNotEmpty()) {
                                IconButton(onClick = onClearQuery) {
                                    Icon(Icons.Outlined.Clear, "Clear")
                                }
                            }
                        },
                        shape  = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor   = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                expandedHeight = 56.dp,
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
            SearchFilterRow(
                filters          = filters,
                applications     = applications,
                onPriorityFilter = onPriorityFilter,
                onAppFilter      = onAppFilter,
                onDateRange      = onDateRange
            )
          Box(Modifier.weight(1f)) {
            when {

                query.isBlank() && !filters.isActive -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon     = Icons.Outlined.Search,
                            title    = "Search messages",
                            subtitle = "Search by text, or pick a filter above"
                        )
                    }
                }


                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon     = Icons.Outlined.SearchOff,
                            title    = if (query.isBlank()) "No matching messages" else "No results for \"$query\"",
                            subtitle = "Try a different search term or filter"
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
                                message     = message,
                                appName     = app?.name ?: "App ${message.appId}",
                                appImageUrl = app?.image,
                                query       = query,
                                clientToken = clientToken,
                                authBaseUrl = serverBaseUrl,
                                onClick     = { onMessageClick(message) }
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
private fun SearchFilterRow(
    filters: SearchFilters,
    applications: Map<Int, GotifyApplication>,
    onPriorityFilter: (PriorityFilter) -> Unit,
    onAppFilter: (Int?) -> Unit,
    onDateRange: (DateRange) -> Unit
) {
    var appMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            FilterChip(
                selected    = filters.appId != null,
                onClick     = { appMenuOpen = true },
                label       = { Text(filters.appId?.let { applications[it]?.name } ?: "Any app") },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = appMenuOpen, onDismissRequest = { appMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Any app") }, onClick = { onAppFilter(null); appMenuOpen = false })
                applications.values.sortedBy { it.name.lowercase() }.forEach { app ->
                    DropdownMenuItem(text = { Text(app.name) }, onClick = { onAppFilter(app.id); appMenuOpen = false })
                }
            }
        }
        PriorityFilter.entries.forEach { p ->
            FilterChip(
                selected = filters.priority == p,
                onClick  = { onPriorityFilter(p) },
                label    = { Text(p.label) }
            )
        }
        listOf(DateRange.DAY, DateRange.WEEK).forEach { r ->
            FilterChip(
                selected = filters.dateRange == r,
                onClick  = { onDateRange(r) },
                label    = { Text(r.label) }
            )
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
    clientToken: String,
    authBaseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppIcon(
                    imageUrl = appImageUrl,
                    appName = appName,
                    clientToken = clientToken,
                    authBaseUrl = authBaseUrl,
                    size = 28.dp
                )
                Text(
                    text       = appName,
                    style      = MaterialTheme.typography.labelMedium,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )
                PriorityBadge(priority = message.priority)
                RelativeTime(isoDate = message.date)
            }

            Spacer(Modifier.height(8.dp))

            if (message.title.isNotBlank()) {
                Text(
                    text       = highlightQuery(message.title, query),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text     = highlightQuery(message.message, query),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val lowerText  = text.lowercase()
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
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(text.substring(idx, idx + query.length))
        }
        start = idx + query.length
    }
}
