package com.gotify.client

import android.os.Bundle
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.gotify.client.service.GotifyListenerService
import com.gotify.client.ui.login.LoginScreen
import com.gotify.client.ui.navigation.MainNavHost
import com.gotify.client.ui.navigation.Routes
import com.gotify.client.ui.theme.GotifyTheme
import com.gotify.client.ui.viewmodel.AppStartupViewModel
import com.gotify.client.ui.viewmodel.LoginUiState
import com.gotify.client.ui.viewmodel.LoginViewModel
import com.gotify.client.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val startupViewModel:  AppStartupViewModel  by viewModels()
    private val loginViewModel:    LoginViewModel        by viewModels()
    private val settingsViewModel: SettingsViewModel     by viewModels()
    private var pendingMessageId by mutableStateOf<Long?>(null)
    private var pendingServerId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingMessageId = messageIdFrom(intent)
        pendingServerId = serverIdFrom(intent)

        setContent {
            val appReady      by startupViewModel.ready.collectAsStateWithLifecycle()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            val loginState    by loginViewModel.uiState.collectAsStateWithLifecycle()
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(appReady, settingsState.serverUrl, settingsState.keepAliveEnabled) {
                if (appReady && settingsState.serverUrl.isNotBlank() && settingsState.keepAliveEnabled) {
                    GotifyListenerService.start(this@MainActivity)
                } else if (appReady) {
                    GotifyListenerService.stop(this@MainActivity)
                }
            }
            LaunchedEffect(appReady, settingsState.notificationsEnabled, settingsState.serverUrl) {
                if (appReady && settingsState.serverUrl.isNotBlank() && settingsState.notificationsEnabled && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }

            GotifyTheme(
                darkTheme    = settingsState.darkThemeEnabled,
                dynamicColor = settingsState.dynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    if (!appReady) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        return@Surface
                    }

                    AppContent(
                        isLoggedIn        = settingsState.serverUrl.isNotBlank(),
                        deepLinkMessageId = pendingMessageId,
                        deepLinkServerId  = pendingServerId,
                        loginState        = loginState,
                        onLoginWithPassword = { url, user, pass, name ->
                            loginViewModel.loginWithPassword(url, user, pass, serverName = name)
                        },
                        onLoginWithToken = { url, token, name ->
                            loginViewModel.loginWithToken(url, token, serverName = name)
                        },
                        onDeepLinkConsumed = { pendingMessageId = null; pendingServerId = null },
                        onLoginSuccessConsumed = loginViewModel::clearLoginSuccess
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMessageId = messageIdFrom(intent)
        pendingServerId = serverIdFrom(intent)
    }

    private fun messageIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra("message_id", -1L)?.takeIf { it >= 0 }
            ?: intent?.data?.takeIf { it.scheme == "gotifyclient" && it.host == "message" }
                ?.pathSegments?.firstOrNull()?.toLongOrNull()

    private fun serverIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra("server_id", -1L)?.takeIf { it >= 0 }
}

@Composable
private fun AppContent(
    isLoggedIn:          Boolean,
    deepLinkMessageId:   Long?,
    deepLinkServerId:    Long?,
    loginState:          LoginUiState,
    onLoginWithPassword: (url: String, username: String, password: String, serverName: String) -> Unit,
    onLoginWithToken:    (url: String, token: String, serverName: String) -> Unit,
    onDeepLinkConsumed:  () -> Unit,
    onLoginSuccessConsumed: () -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(deepLinkMessageId, deepLinkServerId, isLoggedIn, loginState.loginSuccess) {
        if (!isLoggedIn) return@LaunchedEffect

        if (deepLinkMessageId != null) {
            navController.navigate(Routes.detail(deepLinkMessageId, deepLinkServerId))
            onDeepLinkConsumed()
        } else if (loginState.loginSuccess) {
            navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
        }

        if (loginState.loginSuccess) onLoginSuccessConsumed()
    }

    if (!isLoggedIn) {
        LoginScreen(
            isLoading            = loginState.isLoading,
            errorMessage         = loginState.errorMessage,
            onLoginWithPassword  = onLoginWithPassword,
            onLoginWithToken     = onLoginWithToken
        )
    } else {
        MainNavHost(navController = navController)
    }
}
