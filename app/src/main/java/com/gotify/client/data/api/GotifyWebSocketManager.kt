package com.gotify.client.data.api
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.gson.Gson
import com.gotify.client.data.model.GotifyMessage
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
    private var webSocket: WebSocket? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0
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
    private val _connectionState =
        MutableStateFlow<StreamState>(StreamState.Closed(0, "Not started"))
    val connectionState: StateFlow<StreamState> = _connectionState.asStateFlow()
    private var currentBaseUrl: String = ""
    private var currentToken: String = ""
    fun connect(baseUrl: String, token: String) {
        currentBaseUrl = baseUrl.trimEnd('/')
        currentToken = token
        retryAttempt = 0
        cancelRetry()
        disconnect(notifyListeners = false)
        openSocket()
        registerNetworkCallback()
    }
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_connectionState.value is StreamState.Error || (_connectionState.value is StreamState.Closed && currentBaseUrl.isNotBlank())) {
                    Log.d(TAG, "Network restored, attempting to reconnect...")
                    retryAttempt = 0
                    cancelRetry()
                    scheduleReconnect()
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
    fun disconnect(notifyListeners: Boolean = true) {
        cancelRetry()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        if (notifyListeners) {
            emit(StreamState.Closed(1000, "Disconnected"))
        }
    }
    fun destroy() {
        disconnect()
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
            }
        }
        scope.cancel()
    }
    private fun openSocket() {
        val streamUrl = buildStreamUrl(currentBaseUrl, currentToken)
        Log.d(TAG, "Connecting to: $streamUrl")
        emit(StreamState.Connecting)
        val request = Request.Builder()
            .url(streamUrl)
            .build()
        webSocket = okHttpClient.newWebSocket(request, GotifyWebSocketListener())
    }
    private fun buildStreamUrl(baseUrl: String, token: String): String {
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return "$wsBase/stream?token=$token"
    }
    private fun scheduleReconnect() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val hasInternet =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!hasInternet) {
            Log.w(TAG, "No internet connection, pausing reconnection until network is restored.")
            emit(StreamState.Error("Waiting for network connection"))
            return
        }
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached — giving up")
            emit(StreamState.Error("Connection lost after $MAX_RETRY_ATTEMPTS attempts"))
            return
        }
        val delay = (RETRY_BASE_DELAY_MS * (1L shl retryAttempt))
            .coerceAtMost(RETRY_MAX_DELAY_MS)
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt ${retryAttempt + 1})")
        retryJob = scope.launch {
            if (!isActive) return@launch
            delay(delay)
            if (isActive) {
                retryAttempt++
                openSocket()
            }
        }
    }
    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }
    private fun emit(state: StreamState) {
        _connectionState.value = state
        scope.launch { _streamState.emit(state) }
    }
    private inner class GotifyWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            retryAttempt = 0
            emit(StreamState.Connected)
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val message = gson.fromJson(text, GotifyMessage::class.java)
                Log.d(TAG, "Message received: id=${message.id} appId=${message.appId}")
                emit(StreamState.Message(message))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse WebSocket message: $text", e)
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val reason = t.message ?: "Unknown error"
            Log.w(TAG, "WebSocket failure: $reason — scheduling reconnect")
            emit(StreamState.Error(reason))
            scheduleReconnect()
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            emit(StreamState.Closed(code, reason))
            if (code != 1000) {
                scheduleReconnect()
            }
        }
    }
}
