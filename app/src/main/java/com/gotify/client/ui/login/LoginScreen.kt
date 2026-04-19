package com.gotify.client.ui.login

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.gotify.client.R
import com.gotify.client.ui.theme.*

private enum class LoginMode { PASSWORD, TOKEN }

@Composable
fun LoginScreen(
    isLoading:    Boolean,
    errorMessage: String?,
    onLoginWithPassword: (serverUrl: String, username: String, password: String, serverName: String) -> Unit,
    onLoginWithToken:    (serverUrl: String, token: String, serverName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrl       by remember { mutableStateOf("") }
    var serverName      by remember { mutableStateOf("") }
    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var token           by remember { mutableStateOf("") }
    var loginMode       by remember { mutableStateOf(LoginMode.PASSWORD) }
    var passwordVisible by remember { mutableStateOf(false) }

    val urlFocus   = remember { FocusRequester() }
    val userFocus  = remember { FocusRequester() }
    val passFocus  = remember { FocusRequester() }
    val tokenFocus = remember { FocusRequester() }

    val canSubmit = serverUrl.isNotBlank() && !isLoading && when (loginMode) {
        LoginMode.PASSWORD -> username.isNotBlank() && password.isNotBlank()
        LoginMode.TOKEN    -> token.isNotBlank()
    }

    fun submit() {
        if (!canSubmit) return
        if (loginMode == LoginMode.PASSWORD)
            onLoginWithPassword(serverUrl.trim(), username.trim(), password, serverName.trim())
        else
            onLoginWithToken(serverUrl.trim(), token.trim(), serverName.trim())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f   to Color(0xFF0A0F1A),
                    0.5f to Color(0xFF0D1421),
                    1f   to Color(0xFF05080D)
                )
            )
    ) {
        // Ambient glow blobs for background depth
        Box(
            Modifier
                .size(400.dp)
                .offset(x = (-100).dp, y = (-100).dp)
                .background(GotifyBlue.copy(alpha = 0.06f), CircleShape)
        )
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .background(AccentPurple.copy(alpha = 0.04f), CircleShape)
        )

        // This Box centers everything vertically.
        // It prevents scrolling on initial load, but allows it when the keyboard opens.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Compact Horizontal Brand Header ────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.main_logo),
                        contentDescription = "Gotify logo",
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Gotify+",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Self-hosted push notifications",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Modern Form Card ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF111827).copy(alpha = 0.6f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {

                        // -- Server Section --
                        SectionHeader("SERVER")

                        LoginField(
                            value         = serverUrl,
                            onValueChange = { serverUrl = it },
                            label         = "Server URL",
                            placeholder   = "https://gotify.example.com",
                            icon          = Icons.Outlined.Language,
                            keyboardType  = KeyboardType.Uri,
                            imeAction     = ImeAction.Next,
                            onNext        = { userFocus.requestFocus() },
                            modifier      = Modifier.focusRequester(urlFocus)
                        )

                        Spacer(Modifier.height(10.dp))

                        LoginField(
                            value         = serverName,
                            onValueChange = { serverName = it },
                            label         = "Nickname (optional)",
                            placeholder   = "Home, Work, VPS…",
                            icon          = Icons.Outlined.Bookmark,
                            imeAction     = ImeAction.Next,
                            onNext        = { userFocus.requestFocus() }
                        )

                        Text(
                            text = "Shown in the top bar of the Messages screen",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.padding(start = 12.dp, top = 6.dp)
                        )

                        Spacer(Modifier.height(24.dp))

                        // -- Authentication Section --
                        SectionHeader("AUTHENTICATION")

                        // Unified Segmented Toggle
                        SegmentedLoginModeToggle(
                            currentMode = loginMode,
                            onModeSelected = { loginMode = it }
                        )

                        Spacer(Modifier.height(16.dp))

                        // Credential fields — animated swap
                        AnimatedContent(
                            targetState = loginMode,
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { if (targetState == LoginMode.TOKEN) 50 else -50 }) togetherWith
                                        (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { if (targetState == LoginMode.TOKEN) -50 else 50 })
                            },
                            label = "credFields"
                        ) { mode ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                when (mode) {
                                    LoginMode.PASSWORD -> {
                                        LoginField(
                                            value         = username,
                                            onValueChange = { username = it },
                                            label         = "Username",
                                            icon          = Icons.Outlined.Person,
                                            imeAction     = ImeAction.Next,
                                            onNext        = { passFocus.requestFocus() },
                                            modifier      = Modifier.focusRequester(userFocus)
                                        )
                                        LoginField(
                                            value           = password,
                                            onValueChange   = { password = it },
                                            label           = "Password",
                                            icon            = Icons.Outlined.Lock,
                                            isPassword      = true,
                                            passwordVisible = passwordVisible,
                                            onToggleVisible = { passwordVisible = !passwordVisible },
                                            imeAction       = ImeAction.Done,
                                            onDone          = { submit() },
                                            modifier        = Modifier.focusRequester(passFocus)
                                        )
                                    }
                                    LoginMode.TOKEN -> {
                                        LoginField(
                                            value           = token,
                                            onValueChange   = { token = it },
                                            label           = "Client Token",
                                            placeholder     = "Ab1Cd2Ef3…",
                                            icon            = Icons.Outlined.Key,
                                            isPassword      = true,
                                            passwordVisible = true, // always show token
                                            imeAction       = ImeAction.Done,
                                            onDone          = { submit() },
                                            modifier        = Modifier.focusRequester(tokenFocus)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(start = 12.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Info, null,
                                                modifier = Modifier.size(14.dp),
                                                tint     = Color.White.copy(alpha = 0.35f)
                                            )
                                            Text(
                                                "Gotify web UI → Clients → copy token",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.35f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Smooth expanding error message
                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            errorMessage?.let {
                                Column {
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentRed.copy(alpha = 0.1f))
                                            .border(1.dp, AccentRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.ErrorOutline, null,
                                            tint = AccentRed, modifier = Modifier.size(18.dp))
                                        Text(it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AccentRed.copy(alpha = 0.9f),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Submit button
                        Button(
                            onClick  = { submit() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled  = canSubmit,
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = GotifyBlue,
                                disabledContainerColor = GotifyBlue.copy(alpha = 0.25f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            )
                        ) {
                            AnimatedContent(targetState = isLoading, label = "btnContent") { loading ->
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(22.dp),
                                        color       = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Login, null, Modifier.size(20.dp))
                                        Text(
                                            "Connect",
                                            fontWeight = FontWeight.Bold,
                                            fontSize   = 16.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Reusable UI Components ───────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = GotifyBlue,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
    )
}

@Composable
private fun SegmentedLoginModeToggle(
    currentMode: LoginMode,
    onModeSelected: (LoginMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentedButton(
            label = "Password",
            icon = Icons.Outlined.Lock,
            isSelected = currentMode == LoginMode.PASSWORD,
            onClick = { onModeSelected(LoginMode.PASSWORD) },
            modifier = Modifier.weight(1f)
        )
        SegmentedButton(
            label = "Token",
            icon = Icons.Outlined.Key,
            isSelected = currentMode == LoginMode.TOKEN,
            onClick = { onModeSelected(LoginMode.TOKEN) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentedButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) GotifyBlue.copy(alpha = 0.15f) else Color.Transparent,
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) GotifyBlue else Color.White.copy(alpha = 0.4f),
        label = "contentColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = contentColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LoginField(
    value:           String,
    onValueChange:   (String) -> Unit,
    label:           String,
    placeholder:     String      = "",
    icon:            androidx.compose.ui.graphics.vector.ImageVector,
    isPassword:      Boolean     = false,
    passwordVisible: Boolean     = false,
    onToggleVisible: (() -> Unit)? = null,
    keyboardType:    KeyboardType = KeyboardType.Text,
    imeAction:       ImeAction   = ImeAction.Next,
    onNext:          (() -> Unit)? = null,
    onDone:          (() -> Unit)? = null,
    modifier:        Modifier    = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = modifier.fillMaxWidth(),
        label         = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        placeholder   = if (placeholder.isNotBlank()) ({ Text(placeholder, color = Color.White.copy(alpha = 0.2f)) }) else null,
        leadingIcon   = {
            Icon(icon, null,
                tint     = if (value.isNotBlank()) GotifyBlue else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon  = if (isPassword && onToggleVisible != null) ({
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = "Toggle visibility",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }) else null,
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        singleLine = true,
        shape      = RoundedCornerShape(14.dp),
        colors     = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = GotifyBlue.copy(alpha = 0.8f),
            unfocusedBorderColor = Color.Transparent,
            focusedLabelColor    = GotifyBlue,
            unfocusedLabelColor  = Color.White.copy(alpha = 0.4f),
            focusedTextColor     = Color.White,
            unfocusedTextColor   = Color.White.copy(alpha = 0.9f),
            cursorColor          = GotifyBlue,
            focusedContainerColor   = Color.White.copy(alpha = 0.06f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
        )
    )
}