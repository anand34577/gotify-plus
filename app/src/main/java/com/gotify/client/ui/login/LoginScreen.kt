package com.gotify.client.ui.login
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component3
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component4
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gotify.client.R
import com.gotify.client.ui.theme.AccentPurple
import com.gotify.client.ui.theme.AccentRed
import com.gotify.client.ui.theme.GotifyBlue
@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLoginWithPassword: (serverUrl: String, username: String, password: String, serverName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrl by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // Grouped Focus Requesters
    val (urlFocus, nameFocus, userFocus, passFocus) = remember { FocusRequester.createRefs() }
    val canSubmit by remember(serverUrl, username, password, isLoading) {
        derivedStateOf { serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isLoading }
    }
    fun submit() {
        if (canSubmit) onLoginWithPassword(
            serverUrl.trim(),
            username.trim(),
            password,
            serverName.trim()
        )
    }
    // Root Container - Handles Tablet Centering
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080C14)),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Background Elements (Responsive)
        Box(
            Modifier
                .fillMaxSize(0.6f)
                .align(Alignment.TopStart)
                .offset(x = (-50).dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(listOf(GotifyBlue.copy(alpha = 0.15f), Color.Transparent)),
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
                        listOf(
                            AccentPurple.copy(alpha = 0.12f),
                            Color.Transparent
                        )
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
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Connect to your server",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(40.dp))
            // Form Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F1623).copy(alpha = 0.85f), // Slight transparency for modern look
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
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
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    FieldGroup(label = "CREDENTIALS") {
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentRed.copy(alpha = 0.15f))
                                    .border(
                                        1.dp,
                                        AccentRed.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentRed.copy(alpha = 0.9f),
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
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GotifyBlue,
                            disabledContainerColor = GotifyBlue.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
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
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Login,
                                        contentDescription = null,
                                        Modifier.size(20.dp)
                                    )
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
            color = GotifyBlue.copy(alpha = 0.8f),
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
    placeholder: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisible: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isFilled = value.isNotBlank()
    val iconTint by animateColorAsState(
        targetValue = if (isFilled) GotifyBlue else Color.White.copy(alpha = 0.3f),
        label = "iconTint"
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, color = Color.White.copy(alpha = 0.2f)) }
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
                        tint = Color.White.copy(alpha = 0.4f),
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
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GotifyBlue,
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedLabelColor = GotifyBlue,
            unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White.copy(alpha = 0.9f),
            cursorColor = GotifyBlue,
            focusedContainerColor = Color.White.copy(alpha = 0.03f),
            unfocusedContainerColor = Color.Transparent,
        )
    )
}