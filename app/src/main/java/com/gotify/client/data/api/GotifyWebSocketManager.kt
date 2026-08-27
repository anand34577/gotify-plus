package com.gotify.client.data.api

import android.util.Log
import com.google.gson.Gson
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.data.model.StreamState
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


private const val RETRY_BASE_DELAY_MS = 1_000L
private const val RETRY_MAX_DELAY_MS  = 60_000L


class GotifyWebSocketManager @Inject constructor(
    private val gson: Gson
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var retryJob: Job? = null
    private var retryAttempt = 0
    @Volatile private var manuallyDisconnected = true


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

    private val _connectionState = MutableStateFlow<StreamState>(StreamState.Closed(0, "Not started"))
    
    val connectionState: StateFlow<StreamState> = _connectionState.asStateFlow()



    private var currentBaseUrl: String = ""
    private var currentToken: String = ""



    
    fun connect(baseUrl: String, token: String) {
        if (baseUrl.isBlank() || token.isBlank()) return
        disconnect(notifyListeners = false)
        manuallyDisconnected = false
        currentBaseUrl = baseUrl.trimEnd('/')
        currentToken   = token
        retryAttempt   = 0

        openSocket()
    }

    
    fun disconnect(notifyListeners: Boolean = true) {
        manuallyDisconnected = true
        cancelRetry()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        if (notifyListeners) {
            emit(StreamState.Closed(1000, "Disconnected"))
        }
    }

    
    fun destroy() {
        disconnect()
        scope.cancel()
    }



    private fun openSocket() {
        val streamUrl = buildStreamUrl(currentBaseUrl)
        Log.d(TAG, "Connecting to Gotify stream")

        emit(StreamState.Connecting)

        val request = Request.Builder()
            .url(streamUrl)
            .header("X-Gotify-Key", currentToken)
            .build()

        webSocket = okHttpClient.newWebSocket(request, GotifyWebSocketListener())
    }

    
    private fun buildStreamUrl(baseUrl: String): String {
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://",  "ws://")
        return "$wsBase/stream"
    }

    private fun scheduleReconnect() {
        if (manuallyDisconnected || currentBaseUrl.isBlank() || currentToken.isBlank()) return
        if (retryJob?.isActive == true) return

        val delay = (RETRY_BASE_DELAY_MS * (1L shl retryAttempt.coerceAtMost(6)))
            .coerceAtMost(RETRY_MAX_DELAY_MS)

        Log.d(TAG, "Reconnecting in ${delay}ms (attempt ${retryAttempt + 1})")

        retryJob = scope.launch {
            if (!isActive) return@launch
            delay(delay)
            if (isActive) {
                retryJob = null
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
        if (state !is StreamState.Message) _connectionState.value = state
        scope.launch { _streamState.emit(state) }
    }



    private inner class GotifyWebSocketListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (this@GotifyWebSocketManager.webSocket !== webSocket) {
                webSocket.close(1000, "Superseded")
                return
            }
            Log.d(TAG, "WebSocket connected")
            cancelRetry()
            retryAttempt = 0
            emit(StreamState.Connected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (this@GotifyWebSocketManager.webSocket !== webSocket) return
            try {
                val message = gson.fromJson(text, GotifyMessage::class.java)
                Log.d(TAG, "Message received: id=${message.id} appId=${message.appId}")
                emit(StreamState.Message(message))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse WebSocket message", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (this@GotifyWebSocketManager.webSocket !== webSocket) return
            val reason = t.message ?: "Unknown error"
            Log.w(TAG, "WebSocket failure: $reason — scheduling reconnect")
            emit(StreamState.Error(reason))
            if (!manuallyDisconnected) scheduleReconnect()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (this@GotifyWebSocketManager.webSocket !== webSocket) return
            Log.d(TAG, "WebSocket closed: $code $reason")
            emit(StreamState.Closed(code, reason))


            if (code != 1000 && !manuallyDisconnected) {
                scheduleReconnect()
            }
        }
    }
}
