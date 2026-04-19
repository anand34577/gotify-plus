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
import com.gotify.client.data.db.toEntity
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.model.StreamState
import com.gotify.client.data.repository.ServerManager
import com.gotify.client.notification.GotifyNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val TAG = "GotifyService"


@AndroidEntryPoint
class GotifyListenerService : Service() {

    @Inject lateinit var webSocketManager:      GotifyWebSocketManager
    @Inject lateinit var serverManager:         ServerManager
    @Inject lateinit var messageDao:            MessageDao
    @Inject lateinit var applicationDao:        ApplicationDao
    @Inject lateinit var notificationManager:   GotifyNotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)



    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationManager.createBaseChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startAsForeground()
        observeWebSocket()
        observeActiveServer()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        scope.cancel()
        super.onDestroy()
    }



    private fun startAsForeground() {
        val tapIntent = buildTapIntent()
        val serverName = serverManager.activeServer.value?.name ?: "Gotify"
        val notification = notificationManager.buildForegroundNotification(serverName, tapIntent)
        startForeground(GotifyNotificationManager.NOTIFICATION_ID_FOREGROUND, notification)
    }



    private fun observeWebSocket() {
        scope.launch {
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
        scope.launch {
            serverManager.activeServer.collect { server ->
                if (server != null) {
                    Log.d(TAG, "Active server changed to: ${server.name}")

                }
            }
        }
    }



    private suspend fun handleNewMessage(message: com.gotify.client.data.model.GotifyMessage) {
        val serverId = serverManager.activeServer.value?.id ?: return
        Log.d(TAG, "New message: id=${message.id} appId=${message.appId} priority=${message.priority}")


        messageDao.insertMessage(message.toEntity(serverId))


        val app = applicationDao.getApplicationById(message.appId)?.toDomain()


        if (app != null) notificationManager.createAppChannels(app)


        notificationManager.postMessageNotification(
            message    = message,
            app        = app,
            tapIntent  = buildDetailTapIntent(message.id)
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

    private fun buildDetailTapIntent(messageId: Long): PendingIntent {

        val intent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("message_id", messageId)
            }
            ?: Intent()
        return PendingIntent.getActivity(
            this, messageId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }



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
