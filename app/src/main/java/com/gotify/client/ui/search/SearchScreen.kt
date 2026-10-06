package com.gotify.client.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
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
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar   = {
            TopAppBar(
                title = {
                    // Pill-shaped search field that fits the app bar (an outlined field gets clipped at this height).
                    TextField(
                        value         = query,
                        onValueChange = onQueryChange,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                            .heightIn(min = 48.dp)
                            .focusRequester(focusRequester),
                        placeholder   = { Text("Search messages") },
                        leadingIcon   = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction    = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        trailingIcon  = {
                            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                                IconButton(onClick = onClearQuery) {
                                    Icon(Icons.Outlined.Close, "Clear search")
                                }
                            }
                        },
                        shape  = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor  = Color.Transparent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                colors = gotifyTopAppBarColors(),
                scrollBehavior = scrollBehavior
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
                            title    = "Search your messages",
                            subtitle = "Find messages stored on this device by text, or narrow them down with the filters above."
                        )
                    }
                }

                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon     = Icons.Outlined.SearchOff,
                            title    = if (query.isBlank()) "No matching messages" else "No results for \u201c${query.trim()}\u201d",
                            subtitle = "Try a different search term or clear some filters."
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.TopCenter),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "count") {
                            SectionHeader(
                                title = "${results.size} result${if (results.size != 1) "s" else ""}",
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FilterChip(
                selected    = filters.appId != null,
                onClick     = { appMenuOpen = true },
                label       = { Text(filters.appId?.let { applications[it]?.name } ?: "Any app", maxLines = 1) },
                trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) }
            )
            DropdownMenu(expanded = appMenuOpen, onDismissRequest = { appMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Any app") }, onClick = { onAppFilter(null); appMenuOpen = false })
                applications.values.sortedBy { it.name.lowercase() }.forEach { app ->
                    DropdownMenuItem(text = { Text(app.name) }, onClick = { onAppFilter(app.id); appMenuOpen = false })
                }
            }
        }
        VerticalDivider(Modifier.height(24.dp))
        PriorityFilter.entries.forEach { p ->
            FilterChip(
                selected = filters.priority == p,
                onClick  = { onPriorityFilter(p) },
                label    = { Text("${p.label} priority") }
            )
        }
        VerticalDivider(Modifier.height(24.dp))
        listOf(DateRange.DAY, DateRange.WEEK).forEach { r ->
            FilterChip(
                selected = filters.dateRange == r,
                onClick  = { onDateRange(r) },
                label    = { Text("Last ${r.label}") }
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
                    style      = MaterialTheme.typography.labelLarge,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                if (message.priority >= 8) PriorityBadge(priority = message.priority)
                RelativeTime(isoDate = message.date)
            }

            Spacer(Modifier.height(8.dp))

            if (message.title.isNotBlank()) {
                Text(
                    text       = highlightQuery(message.title, query),
                    style      = MaterialTheme.typography.titleMedium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text     = highlightQuery(remember(message.message) { previewText(message.message) }, query),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun highlightQuery(text: String, query: String): AnnotatedString {
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color      = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold
    )
    // The ViewModel searches with the trimmed query, so highlight the same thing.
    val needle = query.trim()
    return remember(text, needle, style) {
        buildAnnotatedString {
            if (needle.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }
            // ignoreCase indexOf works on the original string, so indices stay valid even when
            // lowercasing would change the text length (e.g. "İ").
            var start = 0
            while (start < text.length) {
                val idx = text.indexOf(needle, start, ignoreCase = true)
                if (idx == -1) {
                    append(text.substring(start))
                    break
                }
                append(text.substring(start, idx))
                withStyle(style) { append(text.substring(idx, idx + needle.length)) }
                start = idx + needle.length
            }
        }
    }
}
