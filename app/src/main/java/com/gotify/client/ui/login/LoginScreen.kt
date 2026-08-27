package com.gotify.client.ui.login

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Login
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

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLoginWithPassword: (serverUrl: String, username: String, password: String, serverName: String) -> Unit,
    onLoginWithToken: (serverUrl: String, token: String, serverName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrl by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var useToken by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Grouped Focus Requesters
    val (urlFocus, nameFocus, userFocus, passFocus) = remember { FocusRequester.createRefs() }

    val canSubmit by remember(serverUrl, username, password, token, useToken, isLoading) {
        derivedStateOf {
            serverUrl.isNotBlank() && !isLoading &&
                if (useToken) token.isNotBlank() else username.isNotBlank() && password.isNotBlank()
        }
    }

    fun submit() {
        if (!canSubmit) return
        if (useToken) onLoginWithToken(serverUrl.trim(), token.trim(), serverName.trim())
        else onLoginWithPassword(serverUrl.trim(), username.trim(), password, serverName.trim())
    }

    // Root Container - Handles Tablet Centering
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Background Elements (Responsive)
        Box(
            Modifier
                .fillMaxSize(0.6f)
                .align(Alignment.TopStart)
                .offset(x = (-50).dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .fillMaxSize(0.5f)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // Main Content Column - Constrained for Tablets
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp) // <- THE MAGIC FIX FOR TABLETS
                .fillMaxHeight()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Centers vertically on tall screens
        ) {

            // Branding Section
            Image(
                painter = painterResource(id = R.drawable.main_logo),
                contentDescription = "Gotify+ Logo",
                modifier = Modifier.size(88.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Gotify+",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Connect to your server",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            // Form Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                    FieldGroup(label = "SERVER DETAILS") {
                        LoginField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = "Server URL",
                            placeholder = "https://gotify.example.com",
                            icon = Icons.Outlined.Language,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            onNext = { nameFocus.requestFocus() },
                            modifier = Modifier.focusRequester(urlFocus)
                        )
                        LoginField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            label = "Nickname (Optional)",
                            placeholder = "Home, VPS, Work…",
                            icon = Icons.Outlined.BookmarkBorder,
                            imeAction = ImeAction.Next,
                            onNext = { userFocus.requestFocus() },
                            modifier = Modifier.focusRequester(nameFocus)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    FieldGroup(label = "CREDENTIALS") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !useToken, onClick = { useToken = false }, label = { Text("Password") })
                            FilterChip(selected = useToken, onClick = { useToken = true }, label = { Text("Client token") })
                        }
                        Text(
                            text = if (useToken) {
                                "Use a client token from Gotify when you do not want to share a password with this device."
                            } else {
                                "Gotify will create a dedicated client token for this device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (useToken) {
                            LoginField(
                                value = token,
                                onValueChange = { token = it },
                                label = "Client token",
                                icon = Icons.Outlined.Key,
                                isPassword = true,
                                passwordVisible = passwordVisible,
                                onToggleVisible = { passwordVisible = !passwordVisible },
                                imeAction = ImeAction.Done,
                                onDone = { submit() },
                                modifier = Modifier.focusRequester(userFocus)
                            )
                        } else {
                            LoginField(
                                value = username,
                                onValueChange = { username = it },
                                label = "Username",
                                icon = Icons.Outlined.Person,
                                imeAction = ImeAction.Next,
                                onNext = { passFocus.requestFocus() },
                                modifier = Modifier.focusRequester(userFocus)
                            )
                            LoginField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Password",
                                icon = Icons.Outlined.Lock,
                                isPassword = true,
                                passwordVisible = passwordVisible,
                                onToggleVisible = { passwordVisible = !passwordVisible },
                                imeAction = ImeAction.Done,
                                onDone = { submit() },
                                modifier = Modifier.focusRequester(passFocus)
                            )
                        }
                    }

                    // Error Message Animation
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        errorMessage?.let {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), MaterialTheme.shapes.small)
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Submit Button
                    Button(
                        onClick = { submit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(top = 8.dp),
                        enabled = canSubmit,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        AnimatedContent(
                            targetState = isLoading,
                            label = "LoadingButtonAnimation"
                        ) { loading ->
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, Modifier.size(20.dp))
                                    Text(
                                        text = "Connect",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
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

@Composable
private fun FieldGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisible: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    val isFilled = value.isNotBlank()
    val iconTint by animateColorAsState(
        targetValue = if (isFilled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "iconTint"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
        } else null,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        },
        trailingIcon = if (isPassword && onToggleVisible != null) {
            {
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible)
            PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    )
}
