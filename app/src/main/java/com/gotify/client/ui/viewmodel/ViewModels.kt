package com.gotify.client.ui.viewmodel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Build
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.api.buildBasicAuth
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ApplicationDao
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.db.toEntity
import com.gotify.client.data.model.ApiResult
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.data.model.LoginRequest
import com.gotify.client.data.model.StreamState
import com.gotify.client.data.repository.ApplicationRepository
import com.gotify.client.data.repository.AuthRepository
import com.gotify.client.data.repository.MessageRepository
import com.gotify.client.data.repository.ServerManager
import com.gotify.client.ui.components.ConnectionStatus
import com.gotify.client.notification.GotifyNotificationManager
import com.gotify.client.ui.settings.SettingsState
import com.gotify.client.util.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val serverDao: ServerDao,
    private val prefs: PreferencesRepository
) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    init {
        viewModelScope.launch {
            val savedId = prefs.userPreferences.first().activeServerId
            if (savedId != -1L) {
                val entity = serverDao.getServerById(savedId) ?: serverDao.getActiveServer()
                entity?.let { serverManager.addServer(it.toDomain()) }
            }
            _ready.value = true
        }
    }
}
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val serverDao: ServerDao,
    private val prefs: PreferencesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serverManager.unauthorizedMessage.collect { msg ->
                if (msg != null) {
                    _uiState.update { it.copy(errorMessage = msg) }
                    serverManager.clearUnauthorizedMessage()
                }
            }
        }
    }

    fun loginWithPassword(
        serverUrl: String,
        username: String,
        password: String,
        serverName: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val trimmedUrl = serverUrl.trimEnd('/')
            if (!trimmedUrl.startsWith(
                    "http://",
                    ignoreCase = true
                ) && !trimmedUrl.startsWith("https://", ignoreCase = true)
            ) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Server URL must start with http:// or https://"
                    )
                }
                return@launch
            }
            val tempServer = GotifyServer(name = "", baseUrl = trimmedUrl, clientToken = "")
            val tempApi = NetworkClientFactory.create(tempServer)
            val versionResult = safeApiCall { tempApi.server.getVersion() }
            if (versionResult is ApiResult.Error) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Cannot reach server: ${versionResult.message}"
                    )
                }
                return@launch
            }
            val deviceName = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
                Build.MODEL
            } else {
                "${Build.MANUFACTURER} ${Build.MODEL}"
            }
            val clientName = "GotifyPlus ($deviceName)"
            val authResult = safeApiCall {
                tempApi.auth.createClient(
                    LoginRequest(clientName),
                    buildBasicAuth(username, password)
                )
            }
            when (authResult) {
                is ApiResult.Success -> {
                    val client = authResult.data
                    saveAndActivate(
                        GotifyServer(
                            name = serverName.ifBlank { trimmedUrl },
                            baseUrl = trimmedUrl,
                            clientToken = client.token,
                            isActive = true
                        ), clientId = client.id
                    )
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (authResult.code == 401) "Wrong username or password" else "Login failed: ${authResult.message}"
                    )
                }
                else -> {}
            }
        }
    }
    fun loginWithToken(serverUrl: String, token: String, serverName: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val trimmedUrl = serverUrl.trimEnd('/')
            if (!trimmedUrl.startsWith(
                    "http://",
                    ignoreCase = true
                ) && !trimmedUrl.startsWith("https://", ignoreCase = true)
            ) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Server URL must start with http:// or https://"
                    )
                }
                return@launch
            }
            val server = GotifyServer(
                name = serverName.ifBlank { trimmedUrl },
                baseUrl = trimmedUrl,
                clientToken = token,
                isActive = true
            )
            when (val result =
                safeApiCall { NetworkClientFactory.create(server).users.getCurrentUser() }) {
                is ApiResult.Success -> {
                    saveAndActivate(server, 0); _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccess = true
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (result.code == 401) "Invalid token" else "Could not connect: ${result.message}"
                    )
                }
                else -> {}
            }
        }
    }
    private var checkServerJob: Job? = null
    fun checkServerUrl(url: String) {
        checkServerJob?.cancel()
        if (url.isBlank()) {
            _uiState.update { it.copy(serverVersion = null, serverVersionError = null, isCheckingServer = false) }
            return
        }
        _uiState.update { it.copy(isCheckingServer = true, serverVersion = null, serverVersionError = null) }
        checkServerJob = viewModelScope.launch {
            delay(600)
            val trimmedUrl = url.trim().trimEnd('/')
            if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
                !trimmedUrl.startsWith("https://", ignoreCase = true)
            ) {
                _uiState.update {
                    it.copy(
                        isCheckingServer = false,
                        serverVersionError = "URL must start with http:// or https://"
                    )
                }
                return@launch
            }
            val tempServer = GotifyServer(name = "", baseUrl = trimmedUrl, clientToken = "")
            val tempApi = NetworkClientFactory.create(tempServer)
            val versionResult = safeApiCall { tempApi.server.getVersion() }
            if (versionResult is ApiResult.Success) {
                _uiState.update {
                    it.copy(
                        isCheckingServer = false,
                        serverVersion = "Gotify Server v${versionResult.data.version}",
                        serverVersionError = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isCheckingServer = false,
                        serverVersion = null,
                        serverVersionError = "Not a valid Gotify server URL"
                    )
                }
            }
        }
    }
    private suspend fun saveAndActivate(server: GotifyServer, clientId: Int) {
        serverDao.deactivateAll()
        val newId = serverDao.insertServer(server.toEntity(clientId).copy(isActive = true))
        prefs.setActiveServerId(newId)
        serverManager.addServer(server.copy(id = newId, isActive = true))
    }
}
data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false,
    val serverVersion: String? = null,
    val serverVersionError: String? = null,
    val isCheckingServer: Boolean = false
)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val messageDao: MessageDao,
    private val applicationDao: ApplicationDao,
    private val webSocketManager: GotifyWebSocketManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val activeServerName: StateFlow<String> = serverManager.activeServer
        .map { it?.name ?: "Gotify" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Gotify")
    val clientToken: StateFlow<String> = serverManager.activeServer
        .map { it?.clientToken ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val connectionStatus: StateFlow<ConnectionStatus> = webSocketManager.connectionState
        .map { state ->
            when (state) {
                is StreamState.Connected -> ConnectionStatus.CONNECTED
                is StreamState.Connecting -> ConnectionStatus.CONNECTING
                is StreamState.Error -> ConnectionStatus.ERROR
                else -> ConnectionStatus.DISCONNECTED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.DISCONNECTED)
    private var observedServerId = -1L
    private var messageJob: Job? = null
    private var appJob: Job? = null
    init {
        viewModelScope.launch {
            serverManager.activeServer
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collect { server ->
                    if (server.id != observedServerId) {
                        observedServerId = server.id
                        startObservingData(server)
                        syncFromServer(server)
                    }
                }
        }
        viewModelScope.launch {
            webSocketManager.streamState.collect { state ->
                if (state is StreamState.Message && observedServerId != -1L) {
                    messageDao.insertMessage(state.message.toEntity(observedServerId))
                }
            }
        }
    }
    private fun startObservingData(server: GotifyServer) {
        messageJob?.cancel(); appJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }
        messageJob = viewModelScope.launch {
            messageDao.getMessagesPaged(server.id).collect { entities ->
                _uiState.update {
                    it.copy(
                        messages = entities.map { e -> e.toDomain() },
                        isLoading = false
                    )
                }
            }
        }
        appJob = viewModelScope.launch {
            applicationDao.getApplications(server.id).collect { entities ->
                _uiState.update { it.copy(applications = entities.map { e -> e.toDomain() }) }
            }
        }
    }
    private fun syncFromServer(server: GotifyServer) {
        val api = serverManager.apiClient ?: return
        viewModelScope.launch {
            val result = ApplicationRepository(api).getApplications()
            if (result is ApiResult.Success) applicationDao.insertApplications(result.data.map {
                it.toEntity(
                    server.id
                )
            })
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            MessageRepository(api).getAllMessagesFlow().collect { result ->
                if (result is ApiResult.Success) messageDao.insertMessages(result.data.map {
                    it.toEntity(
                        server.id
                    )
                })
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
    fun refresh() {
        serverManager.activeServer.value?.let { syncFromServer(it) }
    }
    fun loadMore() {
        val current = _uiState.value.messages.size
        viewModelScope.launch {
            messageDao.getMessagesPaged(observedServerId, limit = current + 50).first()
                .let { entities ->
                    _uiState.update { it.copy(messages = entities.map { e -> e.toDomain() }) }
                }
        }
    }
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            messageDao.deleteMessage(messageId)
            serverManager.apiClient?.let { MessageRepository(it).deleteMessage(messageId) }
        }
    }
    fun deleteAllMessages() {
        viewModelScope.launch {
            if (observedServerId == -1L) return@launch
            messageDao.deleteAllMessages(observedServerId)
            serverManager.apiClient?.let { MessageRepository(it).deleteAllMessages() }
        }
    }

    // ---- Publish message ----
    private val _publishState = MutableStateFlow<PublishState>(PublishState.Idle)
    val publishState: StateFlow<PublishState> = _publishState.asStateFlow()

    fun publishMessage(
        app: GotifyApplication,
        title: String,
        message: String,
        priority: Int
    ) {
        val api = serverManager.apiClient ?: run {
            _publishState.value = PublishState.Error("Not connected to server")
            return
        }
        viewModelScope.launch {
            _publishState.value = PublishState.Loading
            val result = MessageRepository(api).publishMessage(
                appToken = app.token,
                title    = title,
                message  = message,
                priority = priority
            )
            _publishState.value = when (result) {
                is ApiResult.Success -> PublishState.Success
                is ApiResult.Error   -> PublishState.Error(result.message)
                ApiResult.Loading    -> PublishState.Loading
            }
        }
    }

    fun resetPublishState() { _publishState.value = PublishState.Idle }
}
data class HomeUiState(
    val messages: List<GotifyMessage> = emptyList(),
    val applications: List<GotifyApplication> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasMorePages: Boolean = false
)
@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager: ServerManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val messageId: Long = checkNotNull(savedStateHandle["messageId"])
    private val _message = MutableStateFlow<GotifyMessage?>(null)
    val message: StateFlow<GotifyMessage?> = _message.asStateFlow()
    private val _application = MutableStateFlow<GotifyApplication?>(null)
    val application: StateFlow<GotifyApplication?> = _application.asStateFlow()
    init {
        viewModelScope.launch {
            val entity = messageDao.getMessageById(messageId)
            _message.value = entity?.toDomain()
            entity?.let {
                messageDao.markAsRead(messageId)
                _application.value = applicationDao.getApplicationById(it.appId)?.toDomain()
            }
        }
    }
    fun deleteMessage(onDeleted: () -> Unit) {
        viewModelScope.launch {
            messageDao.deleteMessage(messageId)
            serverManager.apiClient?.let { MessageRepository(it).deleteMessage(messageId) }
            onDeleted()
        }
    }
}
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val applicationDao: ApplicationDao,
    private val messageDao: MessageDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()
    val serverBaseUrl: StateFlow<String> = serverManager.activeServer
        .map { it?.baseUrl ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val clientToken: StateFlow<String> = serverManager.activeServer
        .map { it?.clientToken ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private var observedServerId = -1L
    private var appsJob: Job? = null
    private var countsJob: Job? = null
    init {
        viewModelScope.launch {
            serverManager.activeServer
                .filterNotNull()
                .distinctUntilChangedBy { it.id }
                .collect { server ->
                    observedServerId = server.id
                    startObservingApps(server)
                    syncAppsFromServer(server)
                }
        }
    }
    private fun startObservingApps(server: GotifyServer) {
        appsJob?.cancel(); countsJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }
        appsJob = viewModelScope.launch {
            applicationDao.getApplications(server.id).collect { entities ->
                val apps = entities.map { it.toDomain() }
                _uiState.update { it.copy(applications = apps, isLoading = false) }
                countsJob?.cancel()
                countsJob = viewModelScope.launch {
                    val countFlows = apps.map { app ->
                        messageDao.getMessageCountForApp(server.id, app.id).map { app.id to it }
                    }
                    if (countFlows.isEmpty()) {
                        _uiState.update { it.copy(messageCounts = emptyMap()) }; return@launch
                    }
                    combine(countFlows) { pairs -> pairs.toMap() }
                        .collect { counts -> _uiState.update { it.copy(messageCounts = counts) } }
                }
            }
        }
    }
    fun refresh() {
        serverManager.activeServer.value?.let { syncAppsFromServer(it) }
    }
    private fun syncAppsFromServer(server: GotifyServer) {
        val api = serverManager.apiClient ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = ApplicationRepository(api).getApplications()
            if (result is ApiResult.Success) {
                applicationDao.insertApplications(result.data.map { it.toEntity(server.id) })
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
    fun createApplication(name: String, description: String) {
        val api = serverManager.apiClient ?: return
        val serverId = serverManager.activeServer.value?.id ?: return
        viewModelScope.launch {
            val result = ApplicationRepository(api).createApplication(name, description)
            if (result is ApiResult.Success) applicationDao.insertApplication(
                result.data.toEntity(
                    serverId
                )
            )
        }
    }
    fun deleteApplication(appId: Int) {
        val api = serverManager.apiClient ?: return
        val serverId = serverManager.activeServer.value?.id ?: return
        viewModelScope.launch {
            applicationDao.deleteApplication(appId)
            messageDao.deleteMessagesByApp(serverId, appId)
            ApplicationRepository(api).deleteApplication(appId)
        }
    }
    fun deleteAppMessages(appId: Int) {
        val api = serverManager.apiClient ?: return
        val serverId = serverManager.activeServer.value?.id ?: return
        viewModelScope.launch {
            messageDao.deleteMessagesByApp(serverId, appId)
            MessageRepository(api).deleteMessagesByApp(appId)
        }
    }

    // ---- Publish message ----
    private val _publishState = MutableStateFlow<PublishState>(PublishState.Idle)
    val publishState: StateFlow<PublishState> = _publishState.asStateFlow()

    fun publishMessage(
        app: GotifyApplication,
        title: String,
        message: String,
        priority: Int
    ) {
        val api = serverManager.apiClient ?: run {
            _publishState.value = PublishState.Error("Not connected to server")
            return
        }
        viewModelScope.launch {
            _publishState.value = PublishState.Loading
            val result = MessageRepository(api).publishMessage(
                appToken = app.token,
                title    = title,
                message  = message,
                priority = priority
            )
            _publishState.value = when (result) {
                is ApiResult.Success -> PublishState.Success
                is ApiResult.Error   -> PublishState.Error(result.message)
                ApiResult.Loading    -> PublishState.Loading
            }
        }
    }

    fun resetPublishState() { _publishState.value = PublishState.Idle }
}
data class AppsUiState(
    val applications: List<GotifyApplication> = emptyList(),
    val messageCounts: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

sealed class PublishState {
    object Idle    : PublishState()
    object Loading : PublishState()
    object Success : PublishState()
    data class Error(val message: String) : PublishState()
}
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val serverManager: ServerManager,
    private val serverDao: ServerDao,
    private val applicationDao: ApplicationDao,
    private val notificationManager: GotifyNotificationManager
) : ViewModel() {
    val settingsState: StateFlow<SettingsState> = combine(
        prefs.userPreferences,
        serverManager.activeServer
    ) { userPrefs, server ->
        SettingsState(
            notificationsEnabled = userPrefs.notificationsEnabled,
            vibrationEnabled = userPrefs.vibrationEnabled,
            dynamicColorEnabled = userPrefs.dynamicColorEnabled,
            darkThemeEnabled = userPrefs.darkThemeEnabled,
            markdownEnabled      = userPrefs.markdownEnabled,
            keepAliveEnabled     = userPrefs.keepAliveEnabled,
            themeSelection       = userPrefs.themeSelection,
            serverName           = server?.name ?: "",
            serverUrl            = server?.baseUrl ?: "",
            appVersion           = "1.0.0"
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())
    fun setNotifications(v: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(v) }
    fun setVibration(v: Boolean) = viewModelScope.launch {
        prefs.setVibrationEnabled(v)
        // Android notification channels are immutable once created.
        // We must delete + recreate the high-priority channels to apply the change.
        val serverId = serverManager.activeServer.value?.id ?: return@launch
        val apps = applicationDao.getApplications(serverId).first()
        notificationManager.updateVibrationChannels(apps.map { it.toDomain() })
    }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { prefs.setDynamicColorEnabled(v) }
    fun setDarkTheme(v: Boolean) = viewModelScope.launch { prefs.setDarkThemeEnabled(v) }
    fun setMarkdown(v: Boolean)      = viewModelScope.launch { prefs.setMarkdownEnabled(v) }
    fun setKeepAlive(v: Boolean)     = viewModelScope.launch { prefs.setKeepAliveEnabled(v) }
    fun setThemeSelection(theme: String) = viewModelScope.launch { prefs.setThemeSelection(theme) }
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            val server = serverManager.activeServer.value
            if (server != null) {
                serverManager.apiClient?.let { api ->
                    val clientId = serverDao.getServerById(server.id)?.clientId ?: 0
                    if (clientId > 0) {
                        // Launch network call asynchronously so it doesn't block local cleanup
                        launch(Dispatchers.IO) {
                            AuthRepository(api).logout(clientId)
                        }
                    }
                }
                serverDao.deleteServer(server.id)
            }
            serverManager.reset()
            prefs.clearAll()
            onLoggedOut()
        }
    }
}
@HiltViewModel
class AppInboxViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager: ServerManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val appId: Int = checkNotNull(savedStateHandle["appId"])
    private val _uiState = MutableStateFlow(AppInboxUiState())
    val uiState: StateFlow<AppInboxUiState> = _uiState.asStateFlow()
    val serverBaseUrl: String get() = serverManager.activeServer.value?.baseUrl ?: ""
    val clientToken: String get() = serverManager.activeServer.value?.clientToken ?: ""
    init {
        viewModelScope.launch {
            val serverId = serverManager.activeServer.value?.id ?: return@launch
            val app = applicationDao.getApplicationById(appId)
            _uiState.update {
                it.copy(
                    appName = app?.name ?: "App $appId",
                    appImageUrl = app?.image
                )
            }
            messageDao.getMessagesByAppPaged(serverId, appId).collect { entities ->
                _uiState.update {
                    it.copy(
                        messages = entities.map { e -> e.toDomain() },
                        isLoading = false
                    )
                }
            }
        }
    }
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            messageDao.deleteMessage(messageId)
            serverManager.apiClient?.let { MessageRepository(it).deleteMessage(messageId) }
        }
    }
    fun clearAllMessages() {
        val api = serverManager.apiClient ?: return
        val serverId = serverManager.activeServer.value?.id ?: return
        viewModelScope.launch {
            messageDao.deleteMessagesByApp(serverId, appId)
            MessageRepository(api).deleteMessagesByApp(appId)
        }
    }
}
data class AppInboxUiState(
    val messages: List<GotifyMessage> = emptyList(),
    val appName: String = "",
    val appImageUrl: String? = null,
    val isLoading: Boolean = true
)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager: ServerManager
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val searchResults: StateFlow<List<GotifyMessage>> = _query
        .debounce(300)
        .flatMapLatest { q ->
            val serverId =
                serverManager.activeServer.value?.id ?: return@flatMapLatest flowOf(emptyList())
            if (q.isBlank()) flowOf(emptyList())
            else messageDao.searchMessages(serverId, q).map { it.map { e -> e.toDomain() } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val applications: StateFlow<Map<Int, GotifyApplication>> = serverManager.activeServer
        .filterNotNull()
        .flatMapLatest { server -> applicationDao.getApplications(server.id) }
        .map { entities -> entities.associate { it.id to it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    fun setQuery(q: String) {
        _query.value = q
    }
    fun clearQuery() {
        _query.value = ""
    }
}
@HiltViewModel
class ServersViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val serverDao: ServerDao,
    private val prefs: PreferencesRepository,
    private val webSocketManager: GotifyWebSocketManager
) : ViewModel() {
    val servers: StateFlow<List<GotifyServer>> = serverDao.getAllServers()
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val connectionStatus: StateFlow<ConnectionStatus> = webSocketManager.connectionState
        .map { state ->
            when (state) {
                is StreamState.Connected -> ConnectionStatus.CONNECTED
                is StreamState.Connecting -> ConnectionStatus.CONNECTING
                is StreamState.Error -> ConnectionStatus.ERROR
                else -> ConnectionStatus.DISCONNECTED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.DISCONNECTED)
    fun switchServer(serverId: Long) {
        viewModelScope.launch {
            serverDao.switchActiveServer(serverId)
            prefs.setActiveServerId(serverId)
            serverManager.switchServer(serverId)
        }
    }
    fun removeServer(serverId: Long) {
        viewModelScope.launch {
            serverDao.deleteServer(serverId)
            serverManager.removeServer(serverId)
        }
    }
}