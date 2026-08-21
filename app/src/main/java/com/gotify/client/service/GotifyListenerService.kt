package com.gotify.client.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.db.ApplicationDao
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.toEntity
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.model.StreamState
import com.gotify.client.data.repository.ServerManager
import com.gotify.client.notification.GotifyNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val TAG = "GotifyService"


@AndroidEntryPoint
class GotifyListenerService : Service() {

    @Inject lateinit var webSocketManager:      GotifyWebSocketManager
    @Inject lateinit var serverManager:         ServerManager
    @Inject lateinit var messageDao:            MessageDao
    @Inject lateinit var applicationDao:        ApplicationDao
    @Inject lateinit var notificationManager:   GotifyNotificationManager
    @Inject lateinit var serverDao:              ServerDao
    @Inject lateinit var prefs:                  PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null
    private var serverJob: Job? = null
    private var startupJob: Job? = null



    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationManager.createBaseChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                webSocketManager.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startAsForeground()
        if (startupJob?.isActive != true) {
            startupJob = scope.launch { initializeAndObserve() }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        webSocketManager.disconnect()
        scope.cancel()
        super.onDestroy()
    }



    private fun startAsForeground() {
        val tapIntent = buildTapIntent()
        val serverName = serverManager.activeServer.value?.name ?: "Gotify"
        val notification = notificationManager.buildForegroundNotification(serverName, tapIntent)
        startForeground(GotifyNotificationManager.NOTIFICATION_ID_FOREGROUND, notification)
    }

    private suspend fun initializeAndObserve() {
        val preferences = prefs.userPreferences.first()
        if (!preferences.keepAliveEnabled) {
            stopSelf()
            return
        }
        if (serverManager.activeServer.value == null) {
            val entity = serverDao.getServerById(preferences.activeServerId)
                ?: serverDao.getActiveServer()
            if (entity == null) {
                stopSelf()
                return
            }
            serverManager.addServer(entity.toDomain())
        }
        observeWebSocket()
        observeActiveServer()
    }



    private fun observeWebSocket() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch {
            webSocketManager.streamState.collect { state ->
                when (state) {
                    is StreamState.Message -> handleNewMessage(state.message)
                    is StreamState.Error   -> Log.w(TAG, "Stream error: ${state.reason}")
                    is StreamState.Closed  -> Log.d(TAG, "Stream closed: ${state.reason}")
                    else -> {}
                }
            }
        }
    }

    private fun observeActiveServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            serverManager.activeServer.collect { server ->
                if (server != null) {
                    Log.d(TAG, "Active server changed to: ${server.name}")
                    startAsForeground()
                } else {
                    stopSelf()
                }
            }
        }
    }



    private suspend fun handleNewMessage(message: com.gotify.client.data.model.GotifyMessage) {
        val serverId = serverManager.activeServer.value?.id ?: return
        Log.d(TAG, "New message: id=${message.id} appId=${message.appId} priority=${message.priority}")


        val existing = messageDao.getMessageById(serverId, message.id)
        messageDao.insertMessage(message.toEntity(serverId, existing?.isRead ?: false))

        message.extras?.action?.onReceive?.intentUrl?.takeIf { it.isNotBlank() }?.let { intentUrl ->
            runCatching { Intent.parseUri(intentUrl, Intent.URI_INTENT_SCHEME) }
                .onSuccess { sendBroadcast(it) }
                .onFailure { Log.w(TAG, "Ignoring invalid onReceive intent") }
        }


        val app = applicationDao.getApplicationById(serverId, message.appId)?.toDomain()


        if (app != null) notificationManager.createAppChannels(serverId, app)


        notificationManager.postMessageNotification(
            message    = message,
            app        = app,
            tapIntent  = buildDetailTapIntent(serverId, message.id),
            serverId   = serverId
        )
    }



    private fun buildTapIntent(): PendingIntent {
        val intent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
            ?: Intent()
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDetailTapIntent(serverId: Long, messageId: Long): PendingIntent {

        val intent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("message_id", messageId)
                putExtra("server_id", serverId)
            }
            ?: Intent()
        return PendingIntent.getActivity(
            this, stableRequestCode(serverId, messageId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stableRequestCode(serverId: Long, messageId: Long): Int =
        31 * serverId.hashCode() + messageId.hashCode()



    companion object {
        const val ACTION_STOP = "com.gotify.client.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, GotifyListenerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GotifyListenerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
