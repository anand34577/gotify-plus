package com.gotify.client.ui.viewmodel

import androidx.lifecycle.*
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.api.buildBasicAuth
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.*
import com.gotify.client.data.model.*
import com.gotify.client.data.repository.*
import com.gotify.client.ui.components.ConnectionStatus
import com.gotify.client.ui.settings.SettingsState
import com.gotify.client.util.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.gotify.client.BuildConfig



@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val serverDao:     ServerDao,
    private val prefs:         PreferencesRepository
) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            val savedId = prefs.userPreferences.first().activeServerId
            val servers = serverDao.getAllServers().first()
            val domains = servers.map { it to it.toDomain() }
            val preferredId = savedId.takeIf { id -> domains.any { it.first.id == id } }
                ?: domains.firstOrNull { it.first.isActive }?.first?.id
            val activeId = preferredId?.takeIf { id ->
                domains.firstOrNull { it.first.id == id }?.second?.clientToken?.isNotBlank() == true
            }

            if (activeId != null) serverDao.switchActiveServer(activeId)
            else serverDao.deactivateAll()
            serverManager.reset()
            prefs.setActiveServerId(activeId ?: -1L)

            domains.forEach { (entity, domain) ->
                val shouldActivate = domain.clientToken.isNotBlank() && entity.id == activeId
                val added = serverManager.addServer(domain.copy(isActive = shouldActivate))
                if (!added) {
                    // Do not leave startup suspended forever when an old row
                    // contains a URL that is no longer accepted.
                    serverDao.updateServer(entity.copy(isActive = false))
                    if (shouldActivate) prefs.setActiveServerId(-1L)
                    return@forEach
                }
                if (entity.isActive != shouldActivate) {
                    serverDao.updateServer(entity.copy(isActive = shouldActivate))
                }
                if (!CredentialCipher.isEncrypted(entity.clientToken)) {
                    serverDao.updateServer(
                        entity.copy(
                            clientToken = CredentialCipher.encrypt(entity.clientToken).orEmpty(),
                            isActive = shouldActivate
                        )
                    )
                }
            }
            _ready.value = true
        }
    }
}



@HiltViewModel
class LoginViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val serverDao:     ServerDao,
    private val prefs:         PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun loginWithPassword(serverUrl: String, username: String, password: String, serverName: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, loginSuccess = false) }
            if (username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Enter your username and password") }
                return@launch
            }
            val trimmedUrl = validateServerUrl(serverUrl) ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Enter a valid HTTPS server URL") }
                return@launch
            }
            val tempServer = GotifyServer(name = "", baseUrl = trimmedUrl, clientToken = "")
            val tempApi = try { NetworkClientFactory.create(tempServer) } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid server URL") }
                return@launch
            }

            val versionResult = safeApiCall { tempApi.server.getVersion() }
            if (versionResult is ApiResult.Error) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Cannot reach server: ${versionResult.message}") }
                return@launch
            }
            val authResult = safeApiCall {
                tempApi.auth.createClient(LoginRequest("GotifyPlus Android"), buildBasicAuth(username, password))
            }
            when (authResult) {
                is ApiResult.Success -> {
                    val client = authResult.data
                    try {
                        saveAndActivate(GotifyServer(name = serverName.ifBlank { trimmedUrl }, baseUrl = trimmedUrl, clientToken = client.token, isActive = true), clientId = client.id)
                        _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Could not securely store the client token") }
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = if (authResult.code == 401) "Wrong username or password" else "Login failed: ${authResult.message}")
                }
                else -> {}
            }
        }
    }

    fun loginWithToken(serverUrl: String, token: String, serverName: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, loginSuccess = false) }
            val trimmedToken = token.trim()
            if (trimmedToken.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Enter a client token") }
                return@launch
            }
            val trimmedUrl = validateServerUrl(serverUrl) ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Enter a valid HTTPS server URL") }
                return@launch
            }
            val server = GotifyServer(name = serverName.ifBlank { trimmedUrl }, baseUrl = trimmedUrl, clientToken = trimmedToken, isActive = true)
            when (val result = safeApiCall { NetworkClientFactory.create(server).messages.getMessages(limit = 1) }) {
                is ApiResult.Success -> try {
                    saveAndActivate(server, 0)
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not securely store the client token") }
                }
                is ApiResult.Error   -> _uiState.update { it.copy(isLoading = false, errorMessage = if (result.code == 401) "Invalid token" else "Could not connect: ${result.message}") }
                else -> {}
            }
        }
    }

    private suspend fun saveAndActivate(server: GotifyServer, clientId: Int) {
        serverDao.deactivateAll()
        val existing = serverDao.getServerByBaseUrl(server.baseUrl)
        val entity = server.copy(id = existing?.id ?: 0).toEntity(
            clientId.takeIf { it > 0 } ?: existing?.clientId ?: 0
        ).copy(
            isActive = true,
            addedAt = existing?.addedAt ?: System.currentTimeMillis()
        )
        val newId = serverDao.insertServer(entity)
        prefs.setActiveServerId(newId)
        check(serverManager.addServer(server.copy(id = newId, isActive = true))) {
            "Unable to activate the saved server"
        }
    }

    private fun validateServerUrl(raw: String): String? {
        val url = raw.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null
        ) return null
        if (url.scheme != "https" && url.host !in setOf("localhost", "127.0.0.1", "10.0.2.2")) return null
        return url.toString().trimEnd('/')
    }

    fun clearLoginSuccess() {
        _uiState.update { it.copy(loginSuccess = false) }
    }
}

data class LoginUiState(
    val isLoading:    Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false
)



@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverManager:    ServerManager,
    private val messageDao:       MessageDao,
    private val applicationDao:   ApplicationDao,
    private val webSocketManager: GotifyWebSocketManager,
    private val prefs:            PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val activeServerName: StateFlow<String> = serverManager.activeServer
        .map { it?.name ?: "Gotify" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Gotify")

    val serverBaseUrl: StateFlow<String> = serverManager.activeServer
        .map { it?.baseUrl.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")


    val clientToken: StateFlow<String> = serverManager.activeServer
        .map { it?.clientToken ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val connectionStatus: StateFlow<ConnectionStatus> = webSocketManager.connectionState
        .map { state ->
            when (state) {
                is StreamState.Connected  -> ConnectionStatus.CONNECTED
                is StreamState.Connecting -> ConnectionStatus.CONNECTING
                is StreamState.Error      -> ConnectionStatus.ERROR
                else                      -> ConnectionStatus.DISCONNECTED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.DISCONNECTED)

    private var observedServerId = -1L
    private var messageJob: Job? = null
    private var appJob:     Job? = null
    private var unreadJob:  Job? = null
    private var countJob:   Job? = null
    private var syncJob:    Job? = null
    private val pageLimit = MutableStateFlow(50)

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
                    val existing = messageDao.getMessageById(observedServerId, state.message.id)
                    messageDao.insertMessage(state.message.toEntity(observedServerId, existing?.isRead ?: false))
                }
            }
        }
    }

    private fun startObservingData(server: GotifyServer) {
        messageJob?.cancel(); appJob?.cancel(); unreadJob?.cancel(); countJob?.cancel()
        pageLimit.value = 50
        _uiState.update {
            it.copy(
                messages = emptyList(),
                applications = emptyList(),
                unreadCount = 0,
                cachedMessageCount = 0,
                isLoading = true,
                isRefreshing = false,
                hasMorePages = false,
                cacheSyncTruncated = false,
                errorMessage = null
            )
        }

        messageJob = viewModelScope.launch {
            pageLimit.flatMapLatest { limit -> messageDao.getMessagesPaged(server.id, limit) }
                .collect { entities ->
                    _uiState.update {
                        it.copy(messages = entities.map { e -> e.toDomain() }, isLoading = false, hasMorePages = entities.size >= pageLimit.value)
                    }
                }
        }
        appJob = viewModelScope.launch {
            applicationDao.getApplications(server.id).collect { entities ->
                _uiState.update { it.copy(applications = entities.map { e -> e.toDomain().resolvedFor(server) }) }
            }
        }
        unreadJob = viewModelScope.launch {
            messageDao.getUnreadCount(server.id).collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
        countJob = viewModelScope.launch {
            messageDao.getMessageCount(server.id).collect { count ->
                _uiState.update { it.copy(cachedMessageCount = count) }
            }
        }
    }

    private fun syncFromServer(server: GotifyServer) {
        val api = serverManager.apiClient ?: run {
            _uiState.update { it.copy(isRefreshing = false, errorMessage = "No active server connection") }
            return
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                when (val result = ApplicationRepository(api).getApplications()) {
                    is ApiResult.Success -> {
                        val entities = result.data.map { app ->
                            app.toEntity(server.id, applicationDao.getApplicationById(server.id, app.id)?.token)
                        }
                        if (entities.isEmpty()) applicationDao.deleteAllForServer(server.id)
                        else {
                            applicationDao.insertApplications(entities)
                            applicationDao.deleteMissingSafely(server.id, entities.map { it.id })
                        }
                    }
                    is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                    else -> Unit
                }
                when (val result = MessageRepository(api).getAllMessages()) {
                    is ApiResult.Success -> {
                        val sync = result.data
                        val readIds = messageDao.getReadMessageIds(server.id).toHashSet()
                        _uiState.update { it.copy(cacheSyncTruncated = !sync.complete) }
                        messageDao.insertMessages(sync.messages.map { message ->
                            message.toEntity(server.id, readIds.contains(message.id))
                        })
                        if (sync.complete) {
                            val remoteIds = sync.messages.map { it.id }
                            if (remoteIds.isEmpty()) messageDao.deleteAllMessages(server.id)
                            else messageDao.deleteMissingSafely(server.id, remoteIds)
                        }
                        messageDao.evictOldMessages(server.id, System.currentTimeMillis() - CACHE_RETENTION_MS)
                        prefs.setLastSyncTimestamp(System.currentTimeMillis())
                    }
                    is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                    else -> Unit
                }
            } finally {
                if (currentCoroutineContext().isActive && serverManager.activeServer.value?.id == server.id) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun refresh() { serverManager.activeServer.value?.let { syncFromServer(it) } }

    fun loadMore() {
        pageLimit.update { it + 50 }
    }

    fun deleteMessage(messageId: Long) {
        val server = serverManager.activeServer.value ?: return
        val api = serverManager.apiClient
        if (api == null) {
            _uiState.update { it.copy(errorMessage = "No active server connection") }
            return
        }
        viewModelScope.launch {
            when (val result = MessageRepository(api).deleteMessage(messageId)) {
                is ApiResult.Success -> messageDao.deleteMessage(server.id, messageId)
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun deleteAllMessages() {
        val server = serverManager.activeServer.value ?: return
        val api = serverManager.apiClient
        if (api == null) {
            _uiState.update { it.copy(errorMessage = "No active server connection") }
            return
        }
        viewModelScope.launch {
            when (val result = MessageRepository(api).deleteAllMessages()) {
                is ApiResult.Success -> messageDao.deleteAllMessages(server.id)
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> Unit
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            if (observedServerId > 0) messageDao.markAllAsRead(observedServerId)
        }
    }
}

data class HomeUiState(
    val messages:     List<GotifyMessage>         = emptyList(),
    val applications: List<GotifyApplication>     = emptyList(),
    val cachedMessageCount: Int                   = 0,
    val isLoading:    Boolean                     = true,
    val isRefreshing: Boolean                     = false,
    val hasMorePages: Boolean                     = false,
    val unreadCount:  Int                         = 0,
    val cacheSyncTruncated: Boolean               = false,
    val errorMessage: String?                     = null
)

private const val CACHE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

private fun ServerEntity.safeApiClient() = runCatching {
    NetworkClientFactory.create(toDomain(), isDebug = BuildConfig.DEBUG)
}.getOrNull()

private suspend fun MessageDao.deleteMissingSafely(serverId: Long, remoteIds: List<Long>) {
    val remoteIdSet = remoteIds.toHashSet()
    getMessageIds(serverId)
        .filterNot(remoteIdSet::contains)
        .chunked(500)
        .forEach { ids -> if (ids.isNotEmpty()) deleteMessagesByIds(serverId, ids) }
}

private suspend fun MessageDao.deleteMissingForAppSafely(
    serverId: Long,
    appId: Int,
    remoteIds: List<Long>
) {
    val remoteIdSet = remoteIds.toHashSet()
    getMessageIdsForApp(serverId, appId)
        .filterNot(remoteIdSet::contains)
        .chunked(500)
        .forEach { ids -> if (ids.isNotEmpty()) deleteMessagesByIdsForApp(serverId, appId, ids) }
}

private suspend fun ApplicationDao.deleteMissingSafely(serverId: Long, remoteIds: List<Int>) {
    val remoteIdSet = remoteIds.toHashSet()
    getApplicationIds(serverId)
        .filterNot(remoteIdSet::contains)
        .chunked(500)
        .forEach { ids -> if (ids.isNotEmpty()) deleteApplicationsByIds(serverId, ids) }
}



@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val messageDao:     MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager:  ServerManager,
    private val serverDao:      ServerDao,
    savedStateHandle:           SavedStateHandle
) : ViewModel() {

    private val messageId: Long = checkNotNull(savedStateHandle["messageId"])
    private val requestedServerId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private var resolvedServerId: Long = -1L

    private val _message     = MutableStateFlow<GotifyMessage?>(null)
    val message: StateFlow<GotifyMessage?> = _message.asStateFlow()

    private val _application = MutableStateFlow<GotifyApplication?>(null)
    val application: StateFlow<GotifyApplication?> = _application.asStateFlow()
    private val _serverId = MutableStateFlow(-1L)
    val serverId: StateFlow<Long> = _serverId.asStateFlow()
    private val _clientToken = MutableStateFlow("")
    val clientToken: StateFlow<String> = _clientToken.asStateFlow()
    private val _serverBaseUrl = MutableStateFlow("")
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            val serverId = requestedServerId.takeIf { it > 0 }
                ?: serverManager.activeServer.value?.id
                ?: run {
                    _loaded.value = true
                    return@launch
                }
            resolvedServerId = serverId
            _serverId.value = serverId
            serverDao.getServerById(serverId)?.toDomain()?.let { server ->
                _clientToken.value = server.clientToken
                _serverBaseUrl.value = server.baseUrl
            }
            val entity = messageDao.getMessageById(serverId, messageId)
            _message.value = entity?.toDomain()
            entity?.let {
                messageDao.markAsRead(serverId, messageId)
                _application.value = applicationDao.getApplicationById(serverId, it.appId)?.toDomain()
            }
            _loaded.value = true
        }
    }

    fun deleteMessage(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val serverId = resolvedServerId.takeIf { it > 0 } ?: return@launch
            val active = serverManager.activeServer.value
            val api = if (active?.id == serverId) serverManager.apiClient else {
                serverDao.getServerById(serverId)?.safeApiClient()
            }
            when (val result = api?.let { MessageRepository(it).deleteMessage(messageId) }) {
                is ApiResult.Success -> {
                    messageDao.deleteMessage(serverId, messageId)
                    onDeleted()
                }
                is ApiResult.Error -> _errorMessage.value = result.message
                else -> _errorMessage.value = "Unable to delete this message"
            }
            _loaded.value = true
        }
    }
}



@HiltViewModel
class AppsViewModel @Inject constructor(
    private val serverManager:  ServerManager,
    private val applicationDao: ApplicationDao,
    private val messageDao:     MessageDao
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
    private var appsJob:   Job? = null
    private var countsJob: Job? = null
    private var syncJob:   Job? = null

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
        _uiState.update {
            it.copy(
                applications = emptyList(),
                messageCounts = emptyMap(),
                isLoading = true,
                isRefreshing = false,
                errorMessage = null
            )
        }

        appsJob = viewModelScope.launch {
            applicationDao.getApplications(server.id).collect { entities ->
                val apps = entities.map { it.toDomain().resolvedFor(server) }
                _uiState.update { it.copy(applications = apps, isLoading = false) }

                countsJob?.cancel()
                countsJob = viewModelScope.launch {
                    val countFlows = apps.map { app ->
                        messageDao.getMessageCountForApp(server.id, app.id).map { app.id to it }
                    }
                    if (countFlows.isEmpty()) { _uiState.update { it.copy(messageCounts = emptyMap()) }; return@launch }
                    combine(countFlows) { pairs -> pairs.toMap() }
                        .collect { counts -> _uiState.update { it.copy(messageCounts = counts) } }
                }
            }
        }
    }

    fun refresh() { serverManager.activeServer.value?.let { syncAppsFromServer(it) } }

    private fun syncAppsFromServer(server: GotifyServer) {
        val api = serverManager.apiClient ?: run {
            _uiState.update { it.copy(isRefreshing = false, errorMessage = "No active server connection") }
            return
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = ApplicationRepository(api).getApplications()
            if (result is ApiResult.Success) {
                val entities = result.data.map { app -> app.toEntity(server.id, applicationDao.getApplicationById(server.id, app.id)?.token) }
                if (entities.isEmpty()) applicationDao.deleteAllForServer(server.id) else {
                    applicationDao.insertApplications(entities)
                    applicationDao.deleteMissingSafely(server.id, entities.map { it.id })
                }
            } else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
            if (serverManager.activeServer.value?.id == server.id) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun createApplication(name: String, description: String) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = "No active server connection") }
            return
        }
        viewModelScope.launch {
            val result = ApplicationRepository(api).createApplication(name, description)
            if (result is ApiResult.Success) applicationDao.insertApplication(result.data.toEntity(serverId))
            else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
        }
    }

    fun updateApplication(appId: Int, name: String, description: String) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = "No active server connection") }
            return
        }
        viewModelScope.launch {
            val existing = applicationDao.getApplicationById(serverId, appId)
            val result = ApplicationRepository(api).updateApplication(appId, name, description)
            when (result) {
                is ApiResult.Success -> applicationDao.insertApplication(
                    result.data.toEntity(serverId, existing?.token)
                )
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun deleteApplication(appId: Int) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = "No active server connection") }
            return
        }
        viewModelScope.launch {
            val app = applicationDao.getApplicationById(serverId, appId) ?: return@launch
            if (app.internal) return@launch
            val result = ApplicationRepository(api).deleteApplication(appId)
            if (result is ApiResult.Success) {
                applicationDao.deleteApplication(serverId, appId)
                messageDao.deleteMessagesByApp(serverId, appId)
            } else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
        }
    }

    fun deleteAppMessages(appId: Int) {
        val api      = serverManager.apiClient
        val serverId = serverManager.activeServer.value?.id
        if (api == null || serverId == null) {
            _uiState.update { it.copy(errorMessage = "No active server connection") }
            return
        }
        viewModelScope.launch {
            val result = MessageRepository(api).deleteMessagesByApp(appId)
            if (result is ApiResult.Success) {
                messageDao.deleteMessagesByApp(serverId, appId)
            } else if (result is ApiResult.Error) _uiState.update { it.copy(errorMessage = result.message) }
        }
    }
}

data class AppsUiState(
    val applications:  List<GotifyApplication> = emptyList(),
    val messageCounts: Map<Int, Int>            = emptyMap(),
    val isLoading:     Boolean                  = true,
    val isRefreshing:  Boolean                  = false,
    val errorMessage:  String?                  = null
)






@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs:         PreferencesRepository,
    private val serverManager: ServerManager,
    private val serverDao:     ServerDao,
    private val messageDao:    MessageDao,
    private val applicationDao:ApplicationDao



) : ViewModel() {

    val settingsState: StateFlow<SettingsState> = combine(
        prefs.userPreferences,
        serverManager.activeServer
    ) { userPrefs, server ->
        SettingsState(
            notificationsEnabled = userPrefs.notificationsEnabled,
            vibrationEnabled     = userPrefs.vibrationEnabled,
            dynamicColorEnabled  = userPrefs.dynamicColorEnabled,
            darkThemeEnabled     = userPrefs.darkThemeEnabled,
            markdownEnabled      = userPrefs.markdownEnabled,
            keepAliveEnabled     = userPrefs.keepAliveEnabled,
            serverName           = server?.name ?: "",
            serverUrl            = server?.baseUrl ?: "",
            appVersion           = BuildConfig.VERSION_NAME
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())




    fun setNotifications(v: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(v) }
    fun setVibration(v: Boolean)     = viewModelScope.launch { prefs.setVibrationEnabled(v) }
    fun setDynamicColor(v: Boolean)  = viewModelScope.launch { prefs.setDynamicColorEnabled(v) }
    fun setDarkTheme(v: Boolean)     = viewModelScope.launch { prefs.setDarkThemeEnabled(v) }
    fun setMarkdown(v: Boolean)      = viewModelScope.launch { prefs.setMarkdownEnabled(v) }
    fun setKeepAlive(v: Boolean)     = viewModelScope.launch { prefs.setKeepAliveEnabled(v) }

    
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            val servers = serverDao.getAllServers().first()
            val active = serverManager.activeServer.value


            if (active != null) {
                serverManager.apiClient?.let { api ->
                    val clientId = serverDao.getServerById(active.id)?.clientId ?: 0
                    if (clientId > 0) {
                        withTimeoutOrNull(5_000) { AuthRepository(api).logout(clientId) }
                    }
                }

                serverDao.deleteServer(active.id)
                messageDao.deleteAllMessages(active.id)
                applicationDao.deleteAllForServer(active.id)
            }

            // Remove inactive servers as well so a future startup cannot
            // silently reactivate a credential the user intended to remove.
            servers.filterNot { it.id == active?.id }.forEach { server ->
                serverDao.deleteServer(server.id)
                messageDao.deleteAllMessages(server.id)
                applicationDao.deleteAllForServer(server.id)
            }



            serverManager.reset()


            // Keep appearance and notification preferences; only the active
            // server selection belongs to the account session.
            prefs.setActiveServerId(-1L)

            onLoggedOut()
        }
    }
}



@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppInboxViewModel @Inject constructor(
    private val messageDao:     MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager:  ServerManager,
    private val serverDao:      ServerDao,
    savedStateHandle:           SavedStateHandle
) : ViewModel() {

    private val appId: Int = checkNotNull(savedStateHandle["appId"])
    private val requestedServerId: Long = savedStateHandle.get<Long>("serverId") ?: -1L
    private var resolvedServerId: Long = -1L

    private val _uiState = MutableStateFlow(AppInboxUiState())
    val uiState: StateFlow<AppInboxUiState> = _uiState.asStateFlow()

    private val _serverId = MutableStateFlow(-1L)
    val serverId: StateFlow<Long> = _serverId.asStateFlow()
    private val _serverBaseUrl = MutableStateFlow("")
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()
    private val _clientToken = MutableStateFlow("")
    val clientToken: StateFlow<String> = _clientToken.asStateFlow()

    private val pageLimit = MutableStateFlow(50)
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val requested = requestedServerId.takeIf { it > 0 }
            val serverId = if (requested != null) {
                requested.takeIf { serverDao.getServerById(it) != null }
            } else {
                serverManager.activeServer.value?.id
            } ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Server not found") }
                return@launch
            }
            resolvedServerId = serverId
            _serverId.value = serverId
            val server = serverDao.getServerById(serverId)?.toDomain()
                ?: serverManager.activeServer.value?.takeIf { it.id == serverId }
                ?: run {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Server not found") }
                    return@launch
                }
            _serverBaseUrl.value = server.baseUrl
            _clientToken.value = server.clientToken
            val app = applicationDao.getApplicationById(serverId, appId)
            _uiState.update { it.copy(appName = app?.name ?: "App $appId", appImageUrl = app?.image) }

            pageLimit.flatMapLatest { limit ->
                messageDao.getMessagesByAppPaged(serverId, appId, limit)
            }.collect { entities ->
                _uiState.update {
                    it.copy(
                        messages = entities.map { e -> e.toDomain() },
                        isLoading = false,
                        hasMorePages = entities.size >= pageLimit.value
                    )
                }
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        val serverId = resolvedServerId.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            val api = if (serverManager.activeServer.value?.id == serverId) {
                serverManager.apiClient
            } else {
                serverDao.getServerById(serverId)?.safeApiClient()
            }
            when (val result = api?.let { MessageRepository(it).deleteMessage(messageId) }) {
                is ApiResult.Success -> messageDao.deleteMessage(serverId, messageId)
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                null -> _uiState.update { it.copy(errorMessage = "No active server connection") }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun clearAllMessages() {
        val serverId = resolvedServerId.takeIf { it > 0 } ?: return
        viewModelScope.launch {
            val api = if (serverManager.activeServer.value?.id == serverId) {
                serverManager.apiClient
            } else {
                serverDao.getServerById(serverId)?.safeApiClient()
            }
            when (val result = api?.let { MessageRepository(it).deleteMessagesByApp(appId) }) {
                is ApiResult.Success -> messageDao.deleteMessagesByApp(serverId, appId)
                is ApiResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
                null -> _uiState.update { it.copy(errorMessage = "No active server connection") }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun refresh() {
        val serverId = resolvedServerId.takeIf { it > 0 } ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val api = if (serverManager.activeServer.value?.id == serverId) {
                serverManager.apiClient
            } else {
                serverDao.getServerById(serverId)?.safeApiClient()
            }
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            if (api == null) {
                _uiState.update { it.copy(isRefreshing = false, errorMessage = "No active server") }
                return@launch
            }
            var error: String? = null
            when (val result = MessageRepository(api).getAllMessagesByApp(appId)) {
                is ApiResult.Success -> {
                    val sync = result.data
                    val readIds = messageDao.getReadMessageIdsForApp(serverId, appId).toHashSet()
                    _uiState.update { it.copy(cacheSyncTruncated = !sync.complete) }
                    messageDao.insertMessages(sync.messages.map { message ->
                        message.toEntity(serverId, readIds.contains(message.id))
                    })
                    if (sync.complete) {
                        val remoteIds = sync.messages.map { it.id }
                        if (remoteIds.isEmpty()) messageDao.deleteMessagesByApp(serverId, appId)
                        else messageDao.deleteMissingForAppSafely(serverId, appId, remoteIds)
                    }
                }
                is ApiResult.Error -> error = result.message
                else -> Unit
            }
            _uiState.update { it.copy(isRefreshing = false, errorMessage = error) }
        }
    }

    fun loadMore() {
        pageLimit.update { it + 50 }
    }

}

data class AppInboxUiState(
    val messages:    List<GotifyMessage> = emptyList(),
    val appName:     String              = "",
    val appImageUrl: String?             = null,
    val isLoading:   Boolean             = true,
    val isRefreshing:Boolean             = false,
    val hasMorePages:Boolean             = false,
    val cacheSyncTruncated:Boolean       = false,
    val errorMessage:String?             = null
)



@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val messageDao:     MessageDao,
    private val applicationDao: ApplicationDao,
    private val serverManager:  ServerManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val clientToken: StateFlow<String> = serverManager.activeServer
        .map { it?.clientToken.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val serverBaseUrl: StateFlow<String> = serverManager.activeServer
        .map { it?.baseUrl.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val searchResults: StateFlow<List<GotifyMessage>> = combine(
        _query.debounce(300),
        serverManager.activeServer.map { it?.id }.distinctUntilChanged()
    ) { q, serverId -> q to serverId }
        .flatMapLatest { (q, serverId) ->
            if (serverId == null) return@flatMapLatest flowOf(emptyList())
            if (q.isBlank()) flowOf(emptyList())
            else messageDao.searchMessages(serverId, q.escapeLike()).map { it.map { e -> e.toDomain() } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val applications: StateFlow<Map<Int, GotifyApplication>> = serverManager.activeServer
        .flatMapLatest { server ->
            if (server == null) {
                flowOf(emptyMap())
            } else {
                applicationDao.getApplications(server.id).map { entities ->
                    entities.associate { entity ->
                        entity.id to entity.toDomain().resolvedFor(server)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setQuery(q: String) { _query.value = q }
    fun clearQuery()        { _query.value = "" }

    private fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}



@HiltViewModel
class ServersViewModel @Inject constructor(
    private val serverManager:    ServerManager,
    private val serverDao:        ServerDao,
    private val prefs:            PreferencesRepository,
    private val webSocketManager: GotifyWebSocketManager,
    private val messageDao:       MessageDao,
    private val applicationDao:   ApplicationDao
) : ViewModel() {

    private val _serverInfo = MutableStateFlow(ServerInfoUiState())
    val serverInfo: StateFlow<ServerInfoUiState> = _serverInfo.asStateFlow()
    private var serverInfoJob: Job? = null

    val servers: StateFlow<List<GotifyServer>> = serverDao.getAllServers()
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectionStatus: StateFlow<ConnectionStatus> = webSocketManager.connectionState
        .map { state ->
            when (state) {
                is StreamState.Connected  -> ConnectionStatus.CONNECTED
                is StreamState.Connecting -> ConnectionStatus.CONNECTING
                is StreamState.Error      -> ConnectionStatus.ERROR
                else                      -> ConnectionStatus.DISCONNECTED
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.DISCONNECTED)

    init {
        viewModelScope.launch {
            serverManager.activeServer
                .map { it?.id }
                .distinctUntilChanged()
                .collect {
                    _serverInfo.value = ServerInfoUiState()
                    refreshServerInfo()
                }
        }
    }

    fun refreshServerInfo() {
        serverInfoJob?.cancel()
        val serverId = serverManager.activeServer.value?.id
        val api = serverManager.apiClient ?: run {
            _serverInfo.value = ServerInfoUiState(errorMessage = "No active server connection")
            return
        }
        serverInfoJob = viewModelScope.launch {
            _serverInfo.update { it.copy(isLoading = true, errorMessage = null) }
            val repository = AuthRepository(api)
            val version = repository.getServerVersion()
            val health = repository.getServerHealth()
            if (!isActive || serverManager.activeServer.value?.id != serverId) return@launch
            val error = listOfNotNull(
                (version as? ApiResult.Error)?.message,
                (health as? ApiResult.Error)?.message
            ).firstOrNull()
            _serverInfo.value = ServerInfoUiState(
                version = (version as? ApiResult.Success)?.data?.version,
                health = (health as? ApiResult.Success)?.data?.health,
                database = (health as? ApiResult.Success)?.data?.database,
                isLoading = false,
                errorMessage = error
            )
        }
    }

    fun switchServer(serverId: Long) {
        viewModelScope.launch {
            val target = serverDao.getServerById(serverId) ?: return@launch
            if (!serverManager.addServer(target.toDomain().copy(isActive = true))) {
                _serverInfo.update { it.copy(errorMessage = "This server has an invalid URL") }
                return@launch
            }
            serverDao.switchActiveServer(serverId)
            prefs.setActiveServerId(serverId)
        }
    }

    fun removeServer(serverId: Long) {
        viewModelScope.launch {
            val wasActive = serverManager.activeServer.value?.id == serverId
            serverDao.deleteServer(serverId)
            messageDao.deleteAllMessages(serverId)
            applicationDao.deleteAllForServer(serverId)
            serverManager.removeServer(serverId)
            if (wasActive) {
                val next = serverDao.getAllServers().first().firstOrNull()
                if (next != null) switchServer(next.id) else prefs.setActiveServerId(-1L)
            }
        }
    }
}

data class ServerInfoUiState(
    val version: String? = null,
    val health: String? = null,
    val database: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

private fun GotifyApplication.resolvedFor(server: GotifyServer): GotifyApplication = copy(
    image = when {
        image.isBlank() -> ""
        image.startsWith("https://", ignoreCase = true) ||
            image.startsWith("http://", ignoreCase = true) -> image
        else -> "${server.baseUrl.trimEnd('/')}/${image.trimStart('/')}"
    }
)
