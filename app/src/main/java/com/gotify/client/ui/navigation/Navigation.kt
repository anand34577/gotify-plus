package com.gotify.client.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gotify.client.ui.appinbox.AppInboxScreen
import com.gotify.client.ui.apps.ApplicationsScreen
import com.gotify.client.ui.detail.MessageDetailScreen
import com.gotify.client.ui.home.HomeScreen
import com.gotify.client.ui.search.SearchScreen
import com.gotify.client.ui.servers.ServerSwitcherSheet
import com.gotify.client.ui.settings.SettingsScreen
import com.gotify.client.ui.viewmodel.AppInboxViewModel
import com.gotify.client.ui.viewmodel.AppsViewModel
import com.gotify.client.ui.viewmodel.HomeViewModel
import com.gotify.client.ui.viewmodel.MessageDetailViewModel
import com.gotify.client.ui.viewmodel.SearchViewModel
import com.gotify.client.ui.viewmodel.ServersViewModel
import com.gotify.client.ui.viewmodel.SettingsViewModel
import com.gotify.client.ui.viewmodel.PublishState

object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{messageId}"
    const val APP_INBOX = "app_inbox/{appId}"
    const val SEARCH = "search"
    const val ADD_SERVER = "add_server"

    fun detail(messageId: Long) = "detail/$messageId"
    fun appInbox(appId: Int) = "app_inbox/$appId"
}

data class BottomNavDestination(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String
)

val bottomNavDestinations = listOf(
    BottomNavDestination(Routes.HOME, Icons.Outlined.Inbox, Icons.Filled.Inbox, "Messages"),
    BottomNavDestination(Routes.APPS, Icons.Outlined.Apps, Icons.Filled.Apps, "Apps"),
    BottomNavDestination(
        Routes.SETTINGS,
        Icons.Outlined.Settings,
        Icons.Filled.Settings,
        "Settings"
    )
)

@Composable
fun MainNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = currentRoute in bottomNavDestinations.map { it.route }
    var showServerSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                GotifyBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
            exitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 10 } },
            popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 10 } },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 10 } }
        ) {

            composable(Routes.HOME) {
                val vm: HomeViewModel = hiltViewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val connStatus by vm.connectionStatus.collectAsStateWithLifecycle()
                val serverName by vm.activeServerName.collectAsStateWithLifecycle()
                val clientToken by vm.clientToken.collectAsStateWithLifecycle()
                val publishState by vm.publishState.collectAsStateWithLifecycle()

                HomeScreen(
                    messages = uiState.messages,
                    applications = uiState.applications,
                    clientToken = clientToken,
                    connectionStatus = connStatus,
                    activeServerName = serverName,
                    isLoading = uiState.isLoading,
                    isRefreshing = uiState.isRefreshing,
                    hasMorePages = uiState.hasMorePages,
                    publishState = publishState,
                    onRefresh = vm::refresh,
                    onLoadMore = vm::loadMore,
                    onMessageClick = { navController.navigate(Routes.detail(it.id)) },
                    onDeleteMessage = vm::deleteMessage,
                    onDeleteAllMessages = vm::deleteAllMessages,
                    onOpenServers = { showServerSheet = true },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onPublishMessage = vm::publishMessage,
                    onResetPublishState = vm::resetPublishState
                )
            }

            composable(Routes.APPS) {
                val vm: AppsViewModel = hiltViewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val serverBaseUrl by vm.serverBaseUrl.collectAsStateWithLifecycle()
                val clientToken by vm.clientToken.collectAsStateWithLifecycle()
                val publishState by vm.publishState.collectAsStateWithLifecycle()

                ApplicationsScreen(
                    applications = uiState.applications,
                    messageCounts = uiState.messageCounts,
                    serverBaseUrl = serverBaseUrl,
                    clientToken = clientToken,
                    isLoading = uiState.isLoading,
                    isRefreshing = uiState.isRefreshing,
                    publishState = publishState,
                    onRefresh = vm::refresh,
                    onAppClick = { navController.navigate(Routes.appInbox(it.id)) },
                    onDeleteApp = vm::deleteApplication,
                    onDeleteAppMessages = vm::deleteAppMessages,
                    onCreateApp = vm::createApplication,
                    onPublishMessage = vm::publishMessage,
                    onResetPublishState = vm::resetPublishState
                )
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = hiltViewModel()
                val state by vm.settingsState.collectAsStateWithLifecycle()

                SettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onToggleNotifications = vm::setNotifications,
                    onToggleVibration = vm::setVibration,
                    onToggleDynamicColor = vm::setDynamicColor,
                    onToggleDarkTheme = vm::setDarkTheme,
                    onSetTheme = vm::setThemeSelection,
                    onToggleMarkdown = vm::setMarkdown,
                    onToggleKeepAlive = vm::setKeepAlive,
                    onSetAutoPurgeDays = vm::setAutoPurgeDays,
                    onLogout = {
                        vm.logout {
                            navController.navigate(Routes.HOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("messageId") { type = NavType.LongType })
            ) {
                val vm: MessageDetailViewModel = hiltViewModel()
                val message by vm.message.collectAsStateWithLifecycle()
                val application by vm.application.collectAsStateWithLifecycle()

                message?.let { msg ->
                    MessageDetailScreen(
                        message = msg,
                        application = application,
                        onBack = { navController.popBackStack() },
                        onDelete = { vm.deleteMessage { navController.popBackStack() } },
                        onOpenAppInbox = { navController.navigate(Routes.appInbox(msg.appId)) },
                        onAddTag = vm::addTag,
                        onRemoveTag = vm::removeTag
                    )
                }
            }

            composable(
                route = Routes.APP_INBOX,
                arguments = listOf(navArgument("appId") { type = NavType.IntType })
            ) {
                val vm: AppInboxViewModel = hiltViewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val resolvedIconUrl = uiState.appImageUrl?.let { path ->
                    com.gotify.client.ui.apps.resolveAppImageUrl(vm.serverBaseUrl, path, vm.clientToken)
                }

                AppInboxScreen(
                    appName = uiState.appName,
                    appImageUrl = resolvedIconUrl,
                    messages = uiState.messages,
                    isLoading = uiState.isLoading,
                    onBack = { navController.popBackStack() },
                    onMessageClick = { navController.navigate(Routes.detail(it.id)) },
                    onDeleteMessage = vm::deleteMessage,
                    onClearAll = vm::clearAllMessages
                )
            }

            composable(Routes.SEARCH) {
                val vm: SearchViewModel = hiltViewModel()
                val query by vm.query.collectAsStateWithLifecycle()
                val results by vm.searchResults.collectAsStateWithLifecycle()
                val apps by vm.applications.collectAsStateWithLifecycle()
                val selectedPriority by vm.selectedPriority.collectAsStateWithLifecycle()
                val selectedAppId by vm.selectedAppId.collectAsStateWithLifecycle()
                val selectedDateFilter by vm.selectedDateFilter.collectAsStateWithLifecycle()

                SearchScreen(
                    query = query,
                    results = results,
                    applications = apps,
                    selectedPriority = selectedPriority,
                    selectedAppId = selectedAppId,
                    selectedDateFilter = selectedDateFilter,
                    onQueryChange = vm::setQuery,
                    onClearQuery = vm::clearQuery,
                    onPriorityChange = vm::setPriority,
                    onAppIdChange = vm::setAppId,
                    onDateFilterChange = vm::setDateFilter,
                    onBack = { navController.popBackStack() },
                    onMessageClick = { navController.navigate(Routes.detail(it.id)) }
                )
            }

            composable(Routes.ADD_SERVER) {
                val vm: com.gotify.client.ui.viewmodel.LoginViewModel = hiltViewModel()
                val loginState by vm.uiState.collectAsStateWithLifecycle()

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    vm.resetState()
                }

                androidx.compose.runtime.LaunchedEffect(loginState.loginSuccess) {
                    if (loginState.loginSuccess) {
                        navController.popBackStack()
                    }
                }

                com.gotify.client.ui.servers.AddServerScreen(
                    isLoading = loginState.isLoading,
                    errorMessage = loginState.errorMessage,
                    serverVersion = loginState.serverVersion,
                    serverVersionError = loginState.serverVersionError,
                    isCheckingServer = loginState.isCheckingServer,
                    onServerUrlChanged = vm::checkServerUrl,
                    onLoginWithPassword = vm::loginWithPassword,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showServerSheet) {
        val vm: ServersViewModel = hiltViewModel()
        val servers by vm.servers.collectAsStateWithLifecycle()
        val connStatus by vm.connectionStatus.collectAsStateWithLifecycle()

        ServerSwitcherSheet(
            servers = servers,
            connectionStatus = connStatus,
            onDismiss = { showServerSheet = false },
            onAddServer = { 
                showServerSheet = false
                navController.navigate(Routes.ADD_SERVER)
            },
            onSwitchServer = { serverId ->
                vm.switchServer(serverId)
                showServerSheet = false
            },
            onDeleteServer = { serverId ->
                vm.removeServer(serverId)
            }
        )
    }
}

@Composable
private fun GotifyBottomBar(
    navController: NavController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        bottomNavDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.icon,
                        contentDescription = dest.label
                    )
                },
                label = {
                    Text(
                        text = dest.label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            )
        }
    }
}