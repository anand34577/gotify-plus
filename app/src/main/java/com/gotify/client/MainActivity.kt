package com.gotify.client
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    private val startupViewModel: AppStartupViewModel by viewModels()
    private val loginViewModel: LoginViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLinkMessageId = intent?.getLongExtra("message_id", -1L)?.takeIf { it != -1L }
        setContent {
            val appReady by startupViewModel.ready.collectAsStateWithLifecycle()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
            GotifyTheme(
                darkTheme = settingsState.darkThemeEnabled,
                dynamicColor = settingsState.dynamicColorEnabled,
                themeSelection = settingsState.themeSelection
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!appReady) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        return@Surface
                    }
                    AppContent(
                        isLoggedIn = settingsState.serverUrl.isNotBlank(),
                        deepLinkMessageId = deepLinkMessageId,
                        loginState = loginState,
                        onServerUrlChanged = { url -> loginViewModel.checkServerUrl(url) },
                        onLoginWithPassword = { url, user, pass, name ->
                            loginViewModel.loginWithPassword(url, user, pass, serverName = name)
                        }
                    )
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        GotifyListenerService.start(this)
    }
}
@Composable
private fun AppContent(
    isLoggedIn: Boolean,
    deepLinkMessageId: Long?,
    loginState: LoginUiState,
    onServerUrlChanged: (String) -> Unit,
    onLoginWithPassword: (url: String, username: String, password: String, serverName: String) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isLoggedIn) {
        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { hasPermission = it }
        )
        LaunchedEffect(Unit) {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LaunchedEffect(deepLinkMessageId) {
        deepLinkMessageId?.let { navController.navigate(Routes.detail(it)) }
    }
    LaunchedEffect(loginState.loginSuccess) {
        if (loginState.loginSuccess) {
            navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
        }
    }
    if (!isLoggedIn && !loginState.loginSuccess) {
        LoginScreen(
            isLoading = loginState.isLoading,
            errorMessage = loginState.errorMessage,
            serverVersion = loginState.serverVersion,
            serverVersionError = loginState.serverVersionError,
            isCheckingServer = loginState.isCheckingServer,
            onServerUrlChanged = onServerUrlChanged,
            onLoginWithPassword = onLoginWithPassword
        )
    } else {
        MainNavHost(navController = navController)
    }
}