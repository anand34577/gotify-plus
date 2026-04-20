package com.gotify.client.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.*
import com.gotify.client.ui.appinbox.AppInboxScreen
import com.gotify.client.ui.apps.ApplicationsScreen
import com.gotify.client.ui.detail.MessageDetailScreen
import com.gotify.client.ui.home.HomeScreen
import com.gotify.client.ui.search.SearchScreen
import com.gotify.client.ui.servers.ServerSwitcherSheet
import com.gotify.client.ui.settings.SettingsScreen
import com.gotify.client.ui.viewmodel.*

object Routes {
    const val HOME      = "home"
    const val APPS      = "apps"
    const val SETTINGS  = "settings"
    const val DETAIL    = "detail/{messageId}"
    const val APP_INBOX = "app_inbox/{appId}"
    const val SEARCH    = "search"

    fun detail(messageId: Long) = "detail/$messageId"
    fun appInbox(appId: Int)    = "app_inbox/$appId"
}

data class BottomNavDestination(
    val route:        String,
    val icon:         ImageVector,
    val selectedIcon: ImageVector,
    val label:        String
)

val bottomNavDestinations = listOf(
    BottomNavDestination(Routes.HOME,     Icons.Outlined.Inbox,    Icons.Filled.Inbox,    "Messages"),
    BottomNavDestination(Routes.APPS,     Icons.Outlined.Apps,     Icons.Filled.Apps,     "Apps"),
    BottomNavDestination(Routes.SETTINGS, Icons.Outlined.Settings, Icons.Filled.Settings, "Settings")
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
        modifier  = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter   = slideInVertically(initialOffsetY = { it }),
                exit    = slideOutVertically(targetOffsetY = { it })
            ) {
                GotifyBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController      = navController,
            startDestination   = Routes.HOME,
            modifier           = Modifier.padding(innerPadding),
            enterTransition    = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
            exitTransition     = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 10 } },
            popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 10 } },
            popExitTransition  = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 10 } }
        ) {

            composable(Routes.HOME) {
                val vm: HomeViewModel = hiltViewModel()
                val uiState     by vm.uiState.collectAsStateWithLifecycle()
                val connStatus  by vm.connectionStatus.collectAsStateWithLifecycle()
                val serverName  by vm.activeServerName.collectAsStateWithLifecycle()
                val clientToken by vm.clientToken.collectAsStateWithLifecycle()

                HomeScreen(
                    messages            = uiState.messages,
                    applications        = uiState.applications,
                    clientToken         = clientToken,
                    connectionStatus    = connStatus,
                    activeServerName    = serverName,
                    isLoading           = uiState.isLoading,
                    isRefreshing        = uiState.isRefreshing,
                    hasMorePages        = uiState.hasMorePages,
                    onRefresh           = vm::refresh,
                    onLoadMore          = vm::loadMore,
                    onMessageClick      = { navController.navigate(Routes.detail(it.id)) },
                    onDeleteMessage     = vm::deleteMessage,
                    onDeleteAllMessages = vm::deleteAllMessages,
                    onOpenServers       = { showServerSheet = true },
                    onOpenSearch        = { navController.navigate(Routes.SEARCH) }
                )
            }

            composable(Routes.APPS) {
                val vm: AppsViewModel = hiltViewModel()
                val uiState      by vm.uiState.collectAsStateWithLifecycle()
                val serverBaseUrl by vm.serverBaseUrl.collectAsStateWithLifecycle()
                val clientToken  by vm.clientToken.collectAsStateWithLifecycle()

                ApplicationsScreen(
                    applications        = uiState.applications,
                    messageCounts       = uiState.messageCounts,
                    serverBaseUrl       = serverBaseUrl,
                    clientToken         = clientToken,
                    isLoading           = uiState.isLoading,
                    isRefreshing        = uiState.isRefreshing,
                    onRefresh           = vm::refresh,
                    onAppClick          = { navController.navigate(Routes.appInbox(it.id)) },
                    onDeleteApp         = vm::deleteApplication,
                    onDeleteAppMessages = vm::deleteAppMessages,
                    onCreateApp         = vm::createApplication
                )
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = hiltViewModel()
                val state by vm.settingsState.collectAsStateWithLifecycle()

                SettingsScreen(
                    state                 = state,
                    onBack                = { navController.popBackStack() },
                    onToggleNotifications = vm::setNotifications,
                    onToggleVibration     = vm::setVibration,
                    onToggleDynamicColor  = vm::setDynamicColor,
                    onToggleDarkTheme     = vm::setDarkTheme,
                    onToggleMarkdown      = vm::setMarkdown,
                    onToggleKeepAlive     = vm::setKeepAlive,
                    onLogout              = {
                        vm.logout {
                            navController.navigate(Routes.HOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route     = Routes.DETAIL,
                arguments = listOf(navArgument("messageId") { type = NavType.LongType })
            ) {
                val vm: MessageDetailViewModel = hiltViewModel()
                val message     by vm.message.collectAsStateWithLifecycle()
                val application by vm.application.collectAsStateWithLifecycle()

                message?.let { msg ->
                    MessageDetailScreen(
                        message        = msg,
                        application    = application,
                        onBack         = { navController.popBackStack() },
                        onDelete       = { vm.deleteMessage { navController.popBackStack() } },
                        onOpenAppInbox = { navController.navigate(Routes.appInbox(msg.appId)) }
                    )
                }
            }

            composable(
                route     = Routes.APP_INBOX,
                arguments = listOf(navArgument("appId") { type = NavType.IntType })
            ) {
                val vm: AppInboxViewModel = hiltViewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val resolvedIconUrl = uiState.appImageUrl?.let { path ->
                    com.gotify.client.ui.apps.resolveAppImageUrl(vm.serverBaseUrl, path)
                }

                AppInboxScreen(
                    appName         = uiState.appName,
                    appImageUrl     = resolvedIconUrl,
                    messages        = uiState.messages,
                    isLoading       = uiState.isLoading,
                    onBack          = { navController.popBackStack() },
                    onMessageClick  = { navController.navigate(Routes.detail(it.id)) },
                    onDeleteMessage = vm::deleteMessage,
                    onClearAll      = vm::clearAllMessages
                )
            }

            composable(Routes.SEARCH) {
                val vm: SearchViewModel = hiltViewModel()
                val query   by vm.query.collectAsStateWithLifecycle()
                val results by vm.searchResults.collectAsStateWithLifecycle()
                val apps    by vm.applications.collectAsStateWithLifecycle()

                SearchScreen(
                    query          = query,
                    results        = results,
                    applications   = apps,
                    onQueryChange  = vm::setQuery,
                    onClearQuery   = vm::clearQuery,
                    onBack         = { navController.popBackStack() },
                    onMessageClick = { navController.navigate(Routes.detail(it.id)) }
                )
            }
        }
    }

    if (showServerSheet) {
        val vm: ServersViewModel = hiltViewModel()
        val servers    by vm.servers.collectAsStateWithLifecycle()
        val connStatus by vm.connectionStatus.collectAsStateWithLifecycle()

        ServerSwitcherSheet(
            servers          = servers,
            connectionStatus = connStatus,
            onDismiss        = { showServerSheet = false }
        )
    }
}

@Composable
private fun GotifyBottomBar(
    navController: NavController,
    currentRoute:  String?,
    modifier:      Modifier = Modifier
) {
    NavigationBar(
        modifier       = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        bottomNavDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon  = {
                    Icon(
                        imageVector        = if (selected) dest.selectedIcon else dest.icon,
                        contentDescription = dest.label
                    )
                },
                label = {
                    Text(
                        text       = dest.label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            )
        }
    }
}