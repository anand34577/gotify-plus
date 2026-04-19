






package com.gotify.client.data.repository

import com.gotify.client.data.api.GotifyApiClient
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.api.NetworkClientFactory
import com.gotify.client.data.model.GotifyServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerManager @Inject constructor(
    private val webSocketManager: GotifyWebSocketManager
) {
    private val _servers = MutableStateFlow<List<GotifyServer>>(emptyList())
    val servers: StateFlow<List<GotifyServer>> = _servers.asStateFlow()

    private val _activeServer = MutableStateFlow<GotifyServer?>(null)
    val activeServer: StateFlow<GotifyServer?> = _activeServer.asStateFlow()

    private var _apiClient: GotifyApiClient? = null
    val apiClient: GotifyApiClient? get() = _apiClient

    
    fun addServer(server: GotifyServer) {
        val existing = _servers.value.toMutableList()


        val resolvedId = if (server.id > 0L) server.id else System.currentTimeMillis()


        if (server.isActive) {
            existing.replaceAll { it.copy(isActive = false) }
        }

        val newServer = server.copy(
            id       = resolvedId,
            isActive = server.isActive || existing.isEmpty()
        )


        val idx = existing.indexOfFirst { it.id == resolvedId }
        if (idx >= 0) existing[idx] = newServer else existing.add(newServer)

        _servers.value = existing

        if (newServer.isActive) activateServer(newServer)
    }

    
    fun switchServer(serverId: Long) {
        val target = _servers.value.find { it.id == serverId } ?: return
        _servers.value = _servers.value.map { it.copy(isActive = it.id == serverId) }
        activateServer(target)
    }

    
    fun removeServer(serverId: Long) {
        val updated = _servers.value.filter { it.id != serverId }
        _servers.value = updated

        if (_activeServer.value?.id == serverId) {
            _apiClient = null
            _activeServer.value = null
            webSocketManager.disconnect()
            updated.firstOrNull()?.let { activateServer(it) }
        }
    }

    
    fun reset() {
        _servers.value = emptyList()
        _apiClient = null
        _activeServer.value = null
        webSocketManager.disconnect()
    }

    fun updateServer(server: GotifyServer) {
        _servers.value = _servers.value.map { if (it.id == server.id) server else it }
        if (_activeServer.value?.id == server.id) activateServer(server)
    }



    private fun activateServer(server: GotifyServer) {
        _activeServer.value = server
        _apiClient = NetworkClientFactory.create(server, isDebug = true)
        webSocketManager.connect(server.baseUrl, server.clientToken)
    }
}
