package com.gotify.client.data.repository

import com.gotify.client.data.api.GotifyApiClient
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.db.toEntity
import com.gotify.client.data.datastore.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerManager @Inject constructor(
    private val webSocketManager: GotifyWebSocketManager,
    private val serverDao: dagger.Lazy<ServerDao>,
    private val prefs: dagger.Lazy<PreferencesRepository>
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _servers = MutableStateFlow<List<GotifyServer>>(emptyList())
    val servers: StateFlow<List<GotifyServer>> = _servers.asStateFlow()

    private val _activeServer = MutableStateFlow<GotifyServer?>(null)
    val activeServer: StateFlow<GotifyServer?> = _activeServer.asStateFlow()

    private var _apiClient: GotifyApiClient? = null
    val apiClient: GotifyApiClient? get() = _apiClient

    private val _unauthorizedMessage = MutableStateFlow<String?>(null)
    val unauthorizedMessage: StateFlow<String?> = _unauthorizedMessage.asStateFlow()

    init {
        scope.launch {
            try {
                serverDao.get().getAllServers().collect { entities ->
                    val domainServers = entities.map { it.toDomain() }
                    _servers.value = domainServers
                    
                    // Sync sockets
                    syncWebSockets()
                    
                    // Active server mapping
                    val active = domainServers.find { it.isActive }
                    if (active != null) {
                        if (_activeServer.value?.id != active.id || _activeServer.value?.clientToken != active.clientToken || _activeServer.value?.baseUrl != active.baseUrl) {
                            activateServer(active)
                        }
                    } else {
                        _activeServer.value = null
                        _apiClient = null
                    }
                }
            } catch (e: Exception) {
                // Ignore initialization errors at startup
            }
        }
    }

    fun clearUnauthorizedMessage() {
        _unauthorizedMessage.value = null
    }

    fun handleUnauthorized(serverId: Long) {
        _unauthorizedMessage.value = "Session expired. Client was terminated on the server."
        scope.launch {
            serverDao.get().deleteServer(serverId)
            val active = _activeServer.value
            if (active?.id == serverId) {
                prefs.get().setActiveServerId(-1L)
            }
        }
    }

    fun addServer(server: GotifyServer) {
        scope.launch {
            val resolvedId = if (server.id > 0L) server.id else System.currentTimeMillis()
            var entity = server.toEntity().copy(id = resolvedId)
            if (server.isActive) {
                serverDao.get().deactivateAll()
                entity = entity.copy(isActive = true)
            }
            serverDao.get().insertServer(entity)
            if (server.isActive) {
                prefs.get().setActiveServerId(resolvedId)
            }
        }
    }

    fun switchServer(serverId: Long) {
        scope.launch {
            serverDao.get().switchActiveServer(serverId)
            prefs.get().setActiveServerId(serverId)
        }
    }

    fun removeServer(serverId: Long) {
        scope.launch {
            serverDao.get().deleteServer(serverId)
            val active = _activeServer.value
            if (active?.id == serverId) {
                prefs.get().setActiveServerId(-1L)
            }
        }
    }

    fun reset() {
        scope.launch {
            val active = _activeServer.value
            if (active != null) {
                serverDao.get().deleteServer(active.id)
            }
            _activeServer.value = null
            _apiClient = null
            webSocketManager.disconnectAll()
        }
    }

    fun updateServer(server: GotifyServer) {
        scope.launch {
            serverDao.get().updateServer(server.toEntity())
        }
    }

    private fun syncWebSockets() {
        webSocketManager.updateServers(_servers.value) { serverId ->
            handleUnauthorized(serverId)
        }
    }

    private fun activateServer(server: GotifyServer) {
        _activeServer.value = server
        _apiClient = NetworkClientFactory.create(
            server = server,
            isDebug = true,
            onUnauthorized = { handleUnauthorized(server.id) }
        )
    }
}
