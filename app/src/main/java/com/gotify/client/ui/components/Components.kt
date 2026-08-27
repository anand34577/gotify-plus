package com.gotify.client.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URI
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ─── Connection Status Dot ────────────────────────────────────────────────────

enum class ConnectionStatus { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

@Composable
fun ConnectionDot(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ConnectionStatus.CONNECTED    -> com.gotify.client.ui.theme.AccentGreen
        ConnectionStatus.CONNECTING   -> com.gotify.client.ui.theme.AccentOrange
        ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
        ConnectionStatus.ERROR        -> com.gotify.client.ui.theme.AccentRed
    }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (status == ConnectionStatus.CONNECTING) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ─── App Icon ─────────────────────────────────────────────────────────────────
//
// FIX 4 — Guaranteed initials always render. The image overlays on top only
// if it loads successfully. If Coil gets a 401 / network error / empty URL,
// the letter + colour background is already drawn underneath and visible.
//
// FIX 4 — Auth header: Gotify's /image/ endpoint requires X-Gotify-Key.
// We pass it via ImageRequest headers so Coil can actually download the icon.

@Composable
fun AppIcon(
    imageUrl:  String?,          // fully-resolved URL (null = show initials only)
    appName:   String,
    modifier:  Modifier = Modifier,
    clientToken: String = "",    // Gotify client token for auth header
    authBaseUrl: String = "",     // Only send the token to this server origin
    size:      Dp = 40.dp
) {
    val shape   = MaterialTheme.shapes.medium
    // Deterministic hue from app name so every app always has the same colour
    val hue     = (appName.hashCode().and(0x7FFFFFFF) % 360).toFloat()
    val bgColor = Color.hsl(hue, 0.55f, 0.38f)
    val initial = appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier         = modifier
            .size(size)
            .clip(shape)
            .background(bgColor),      // ← always drawn first
        contentAlignment = Alignment.Center
    ) {
        // Initial letter — always visible as a background layer
        Text(
            text       = initial,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = (size.value * 0.38f).sp
        )

        // Image layer — only renders if the URL is non-empty AND loads successfully
        if (!imageUrl.isNullOrBlank()) {
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

            SubcomposeAsyncImage(
                model              = request,
                contentDescription = appName,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
                // Loading state: show nothing (initials underneath are visible)
                loading = { },
                // Error state: show nothing (initials underneath are visible)
                error   = { }
            )
        }
    }
}

// ─── Priority Badge ───────────────────────────────────────────────────────────

@Composable
fun PriorityBadge(priority: Int, modifier: Modifier = Modifier) {
    if (priority == 0) return
    val color = priorityColor(priority)
    val label = priorityLabel(priority)
    Surface(
        modifier = modifier,
        shape    = MaterialTheme.shapes.extraSmall,
        color    = color.copy(alpha = 0.15f)
    ) {
        Text(
            text       = label,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelSmall,
            color      = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun priorityColor(priority: Int): Color = when {
    priority == 0 -> MaterialTheme.colorScheme.outlineVariant
    priority <= 3 -> MaterialTheme.colorScheme.onSurfaceVariant
    priority <= 7 -> com.gotify.client.ui.theme.AccentOrange
    else          -> com.gotify.client.ui.theme.AccentRed
}

fun priorityLabel(priority: Int): String = when {
    priority == 0 -> "Silent"
    priority <= 3 -> "Low"
    priority <= 7 -> "Normal"
    else          -> "High"
}

// ─── Relative Timestamp ───────────────────────────────────────────────────────

@Composable
fun RelativeTime(isoDate: String, modifier: Modifier = Modifier) {
    val now by produceState(initialValue = Instant.now(), key1 = isoDate) {
        while (isActive) {
            delay(60_000)
            value = Instant.now()
        }
    }
    val text = remember(isoDate, now) { formatRelativeTime(isoDate, now) }
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

fun formatRelativeTime(isoDate: String): String {
    return formatRelativeTime(isoDate, Instant.now())
}

private fun formatRelativeTime(isoDate: String, now: Instant): String {
    return try {
        val instant = Instant.parse(isoDate)
        val diff    = ChronoUnit.MINUTES.between(instant, now)
        when {
            diff < 1     -> "just now"
            diff < 60    -> "${diff}m ago"
            diff < 1440  -> "${diff / 60}h ago"
            diff < 10080 -> "${diff / 1440}d ago"
            else -> DateTimeFormatter.ofPattern("MMM d")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }
    } catch (e: Exception) { isoDate }
}

// ─── Message Card ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCard(
    title:       String,
    message:     String,
    appName:     String,
    appImageUrl: String?,
    priority:    Int,
    date:        String,
    onClick:     () -> Unit,
    onDelete:    () -> Unit,
    modifier:    Modifier = Modifier,
    clientToken: String = "",
    authBaseUrl: String = "",
    isRead:      Boolean = false
) {
    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(
            containerColor = if (isRead) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 112.dp)
                    .background(
                        priorityColor(priority).copy(alpha = if (isRead) 0.45f else 1f),
                        MaterialTheme.shapes.extraSmall
                    )
            ) {
            }
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIcon(
                        imageUrl    = appImageUrl,
                        appName     = appName,
                        clientToken = clientToken,
                        authBaseUrl = authBaseUrl,
                        size        = 36.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = appName,
                            style      = MaterialTheme.typography.labelLarge,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        RelativeTime(isoDate = date)
                    }
                    PriorityBadge(priority = priority)
                }
                Spacer(Modifier.height(12.dp))
                if (title.isNotBlank()) {
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isRead) FontWeight.Medium else FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text       = message,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines   = 3,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector        = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete message",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun shouldAttachGotifyKey(imageUrl: String, token: String, authBaseUrl: String): Boolean {
    if (token.isBlank() || authBaseUrl.isBlank()) return false
    val image = runCatching { URI(imageUrl) }.getOrNull() ?: return false
    val base = runCatching { URI(authBaseUrl) }.getOrNull() ?: return false
    return image.scheme in setOf("http", "https") &&
        base.scheme in setOf("http", "https") &&
        image.scheme.equals(base.scheme, ignoreCase = true) &&
        image.host.equals(base.host, ignoreCase = true) &&
        effectivePort(image) == effectivePort(base)
}

private fun effectivePort(uri: URI): Int = when {
    uri.port != -1 -> uri.port
    uri.scheme.equals("https", ignoreCase = true) -> 443
    else -> 80
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title:    String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text          = title.uppercase(),
            style         = MaterialTheme.typography.labelSmall,
            color         = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        trailing?.invoke()
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action:   (@Composable () -> Unit)? = null
) {
    Column(
        modifier              = modifier.padding(32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            modifier           = Modifier.size(56.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(text = title,    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium,  color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

// ─── Loading Shimmer ──────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun MessageCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ShimmerBox(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShimmerBox(Modifier.size(width = 80.dp, height = 10.dp))
                    ShimmerBox(Modifier.size(width = 50.dp, height = 8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(12.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(12.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
    }
}

// ─── Snackbar helpers ────────────────────────────────────────────────────────

suspend fun SnackbarHostState.showInfo(message: String) =
    showSnackbar(message = message, duration = SnackbarDuration.Short)

suspend fun SnackbarHostState.showError(message: String) =
    showSnackbar(message = message, duration = SnackbarDuration.Long, actionLabel = "Dismiss")
