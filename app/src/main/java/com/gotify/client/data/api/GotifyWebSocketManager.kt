package com.gotify.client.data.api
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.gson.Gson
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.data.model.StreamState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
private const val TAG = "GotifyWebSocket"
private const val MAX_RETRY_ATTEMPTS = 10
private const val RETRY_BASE_DELAY_MS = 1_000L
private const val RETRY_MAX_DELAY_MS = 60_000L
class GotifyWebSocketManager @Inject constructor(
    private val gson: Gson,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Concurrent maps for server connections
    private val webSockets = java.util.concurrent.ConcurrentHashMap<Long, WebSocket>()
    private val retryJobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()
    private val retryAttempts = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    private val serverDetails = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, String>>()
    private val unauthorizedCallbacks = java.util.concurrent.ConcurrentHashMap<Long, () -> Unit>()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val _streamState = MutableSharedFlow<StreamState>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val streamState: SharedFlow<StreamState> = _streamState.asSharedFlow()

    private val _connectionStates = MutableStateFlow<Map<Long, StreamState>>(emptyMap())
    val connectionStates: StateFlow<Map<Long, StreamState>> = _connectionStates.asStateFlow()

    fun updateServers(servers: List<GotifyServer>, onUnauthorized: (Long) -> Unit) {
        val currentIds = serverDetails.keys.toList()
        val newIds = servers.map { it.id }

        // Remove deleted servers
        for (id in currentIds) {
            if (id !in newIds) {
                disconnectServer(id)
                serverDetails.remove(id)
                unauthorizedCallbacks.remove(id)
            }
        }

        // Add or update servers
        for (server in servers) {
            val cached = serverDetails[server.id]
            val hasChanged = cached == null || cached.first != server.baseUrl || cached.second != server.clientToken
            
            if (hasChanged) {
                disconnectServer(server.id)
                serverDetails[server.id] = Pair(server.baseUrl, server.clientToken)
                unauthorizedCallbacks[server.id] = { onUnauthorized(server.id) }
                connectServer(server.id)
            }
        }
        registerNetworkCallback()
    }

    private fun connectServer(serverId: Long) {
        val details = serverDetails[serverId] ?: return
        val baseUrl = details.first
        val token = details.second
        val streamUrl = buildStreamUrl(baseUrl, token)
        
        Log.d(TAG, "Server $serverId - Connecting to: $streamUrl")
        emit(serverId, StreamState.Connecting(serverId))
        
        val request = Request.Builder()
            .url(streamUrl)
            .build()
        webSockets[serverId] = okHttpClient.newWebSocket(request, GotifyWebSocketListener(serverId))
    }

    private fun buildStreamUrl(baseUrl: String, token: String): String {
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return "$wsBase/stream?token=$token"
    }

    fun disconnectServer(serverId: Long) {
        cancelRetry(serverId)
        webSockets[serverId]?.close(1000, "Disconnected")
        webSockets.remove(serverId)
        emit(serverId, StreamState.Closed(serverId, 1000, "Disconnected"))
    }

    fun disconnectAll() {
        for (id in serverDetails.keys) {
            disconnectServer(id)
        }
        serverDetails.clear()
        unauthorizedCallbacks.clear()
    }

    fun destroy() {
        disconnectAll()
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
            }
        }
        scope.cancel()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network restored, checking connections...")
                val states = _connectionStates.value
                for ((id, details) in serverDetails) {
                    val state = states[id]
                    if (state is StreamState.Error || (state is StreamState.Closed && details.first.isNotBlank())) {
                        Log.d(TAG, "Server $id - Reconnecting due to network restore")
                        retryAttempts[id] = 0
                        cancelRetry(id)
                        scheduleReconnect(id)
                    }
                }
            }
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            networkCallback!!
        )
    }

    private fun scheduleReconnect(serverId: Long) {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val hasInternet =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!hasInternet) {
            Log.w(TAG, "Server $serverId - No internet connection, pausing reconnection.")
            emit(serverId, StreamState.Error(serverId, "Waiting for network connection"))
            return
        }

        val attempt = retryAttempts[serverId] ?: 0
        if (attempt >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Server $serverId - Max reconnection attempts reached")
            emit(serverId, StreamState.Error(serverId, "Connection lost after $MAX_RETRY_ATTEMPTS attempts"))
            return
        }

        val delay = (RETRY_BASE_DELAY_MS * (1L shl attempt))
            .coerceAtMost(RETRY_MAX_DELAY_MS)
        Log.d(TAG, "Server $serverId - Reconnecting in ${delay}ms (attempt ${attempt + 1})")
        
        cancelRetry(serverId)
        retryJobs[serverId] = scope.launch {
            if (!isActive) return@launch
            delay(delay)
            if (isActive) {
                retryAttempts[serverId] = attempt + 1
                connectServer(serverId)
            }
        }
    }

    private fun cancelRetry(serverId: Long) {
        retryJobs[serverId]?.cancel()
        retryJobs.remove(serverId)
    }

    private fun emit(serverId: Long, state: StreamState) {
        if (state !is StreamState.Message) {
            _connectionStates.value = _connectionStates.value.toMutableMap().apply {
                put(serverId, state)
            }
        }
        scope.launch { _streamState.emit(state) }
    }

    private inner class GotifyWebSocketListener(private val serverId: Long) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Server $serverId - WebSocket connected")
            retryAttempts[serverId] = 0
            emit(serverId, StreamState.Connected(serverId))
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val message = gson.fromJson(text, GotifyMessage::class.java)
                Log.d(TAG, "Server $serverId - Message received: id=${message.id} appId=${message.appId}")
                emit(serverId, StreamState.Message(serverId, message))
            } catch (e: Exception) {
                Log.e(TAG, "Server $serverId - Failed to parse message: $text", e)
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (response?.code == 401) {
                Log.w(TAG, "Server $serverId - WebSocket unauthorized (401)")
                unauthorizedCallbacks[serverId]?.invoke()
                return
            }
            val reason = t.message ?: "Unknown error"
            Log.w(TAG, "Server $serverId - WebSocket failure: $reason")
            emit(serverId, StreamState.Error(serverId, reason))
            scheduleReconnect(serverId)
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Server $serverId - WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Server $serverId - WebSocket closed: $code $reason")
            emit(serverId, StreamState.Closed(serverId, code, reason))
            if (code != 1000) {
                scheduleReconnect(serverId)
            }
        }
    }
}
