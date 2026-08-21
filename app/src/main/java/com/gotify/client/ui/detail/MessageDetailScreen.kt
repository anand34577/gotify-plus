package com.gotify.client.ui.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.ui.apps.resolveAppImageUrl
import com.gotify.client.ui.components.*
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    message: GotifyMessage,
    application: GotifyApplication?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onOpenAppInbox: () -> Unit,
    modifier: Modifier = Modifier,
    markdownEnabled: Boolean = true,
    clientToken: String = "",
    serverBaseUrl: String = "",
    errorMessage: String? = null
) {
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val applicationImageUrl = application?.image?.let { image ->
        resolveAppImageUrl(serverBaseUrl, image)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }


    val isMarkdown = markdownEnabled && message.extras?.display?.contentType == "text/markdown"

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar   = {
            TopAppBar(
                title          = { Text("Message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {


            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAppInbox)
                        .padding(16.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppIcon(
                        imageUrl = applicationImageUrl,
                        appName  = application?.name ?: "App",
                        clientToken = clientToken,
                        authBaseUrl = serverBaseUrl,
                        size     = 48.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = application?.name ?: "App ${message.appId}",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                        if (!application?.description.isNullOrBlank()) {
                            Text(
                                text  = application.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        PriorityBadge(priority = message.priority)
                        Spacer(Modifier.height(4.dp))
                        RelativeTime(isoDate = message.date)
                    }
                }
            }


            if (message.title.isNotBlank()) {
                Text(
                    text       = message.title,
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 32.sp
                )
            }


            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (isMarkdown) {
                        MarkdownText(
                            markdown  = message.message,
                            style     = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            )
                        )
                    } else {
                        Text(
                            text       = message.message,
                            style      = MaterialTheme.typography.bodyLarge,
                            color      = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        )
                    }
                }
            }


            val bigImageUrl = message.extras?.notification?.bigImageUrl
            if (!bigImageUrl.isNullOrBlank()) {
                MessageImageCard(
                    imageUrl = resolveAppImageUrl(serverBaseUrl, bigImageUrl) ?: bigImageUrl,
                    clientToken = clientToken,
                    authBaseUrl = serverBaseUrl
                )
            }


            val actionUrl = message.extras?.notification?.click?.url
                ?: message.extras?.action?.onClick?.intentUrl
                ?: message.extras?.action?.onReceive?.intentUrl
            if (!actionUrl.isNullOrBlank()) {
                Button(
                    onClick  = {
                        runCatching { uriHandler.openUri(actionUrl) }
                            .onFailure { error ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        error.message ?: "Unable to open this action"
                                    )
                                }
                            }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Action", fontWeight = FontWeight.SemiBold)
                }
            }


            DetailMetadataCard(message = message)

            Spacer(Modifier.height(40.dp))
        }
    }
}



@Composable
private fun MessageImageCard(
    imageUrl: String,
    clientToken: String,
    authBaseUrl: String
) {
    val context = LocalContext.current
    val request = remember(imageUrl, clientToken, authBaseUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                if (shouldAttachGotifyKey(imageUrl, clientToken, authBaseUrl)) {
                    addHeader("X-Gotify-Key", clientToken)
                }
            }
            .crossfade(true)
            .build()
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Image, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Attachment",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))

            SubcomposeAsyncImage(
                model = request,
                contentDescription = "Message attachment",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(MaterialTheme.shapes.medium),
                loading = {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.BrokenImage, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unable to load attachment")
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailMetadataCard(message: GotifyMessage) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Message details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaRow("Message ID",  "#${message.id}")
                    MetaRow("App ID",      "#${message.appId}")
                    MetaRow("Priority",    "${message.priority}")
                    MetaRow("Timestamp",   message.date)
                    if (message.extras != null) {
                        MetaRow("Content type",
                            message.extras.display?.contentType ?: "text/plain")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color      = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
