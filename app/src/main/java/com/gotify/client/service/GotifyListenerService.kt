package com.gotify.client.service
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.db.ApplicationDao
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.db.toEntity
import com.gotify.client.data.model.StreamState
import com.gotify.client.data.repository.ServerManager
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.notification.GotifyNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
private const val TAG = "GotifyService"
@AndroidEntryPoint
class GotifyListenerService : Service() {
    @Inject
    lateinit var webSocketManager: GotifyWebSocketManager
    @Inject
    lateinit var serverManager: ServerManager
    @Inject
    lateinit var messageDao: MessageDao
    @Inject
    lateinit var applicationDao: ApplicationDao
    @Inject
    lateinit var notificationManager: GotifyNotificationManager
    @Inject
    lateinit var prefs: PreferencesRepository
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
        observeConnectionStates()
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
                    is StreamState.Message -> handleNewMessage(state.serverId, state.message)
                    is StreamState.Error -> Log.w(TAG, "Server ${state.serverId} stream error: ${state.reason}")
                    is StreamState.Closed -> Log.d(TAG, "Server ${state.serverId} stream closed: ${state.reason}")
                    else -> {}
                }
            }
        }
    }
    private fun observeConnectionStates() {
        scope.launch {
            webSocketManager.connectionStates.collect { states ->
                val connectedCount = states.values.count { it is StreamState.Connected }
                val totalCount = states.size
                val serverName = serverManager.activeServer.value?.name ?: "Gotify"
                val text = if (totalCount > 1) {
                    "Connected to $connectedCount/$totalCount servers"
                } else {
                    serverName
                }
                updateForegroundNotification(text)
            }
        }
    }
    private fun updateForegroundNotification(text: String) {
        val notification = notificationManager.buildForegroundNotification(text, buildTapIntent())
        val systemNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        systemNotificationManager.notify(GotifyNotificationManager.NOTIFICATION_ID_FOREGROUND, notification)
    }
    private fun observeActiveServer() {
        scope.launch {
            serverManager.activeServer.collect { server ->
                if (server != null) {
                    Log.d(TAG, "Active server changed to: ${server.name}")
                } else {
                    val savedId = prefs.userPreferences.first().activeServerId
                    if (savedId == -1L) {
                        Log.d(TAG, "Active server is null and no active server in preferences, stopping service")
                        stop(this@GotifyListenerService)
                    }
                }
            }
        }
    }
    private suspend fun handleNewMessage(serverId: Long, message: com.gotify.client.data.model.GotifyMessage) {
        Log.d(
            TAG,
            "New message from server $serverId: id=${message.id} appId=${message.appId} priority=${message.priority}"
        )
        // 1. Insert message into Database
        messageDao.insertMessage(message.toEntity(serverId))

        // 2. Perform Auto-Purge check
        val userPrefs = prefs.userPreferences.first()
        if (userPrefs.autoPurgeDays > 0) {
            val cutoff = System.currentTimeMillis() - (userPrefs.autoPurgeDays * 24L * 60L * 60L * 1000L)
            messageDao.evictOldMessages(cutoff)
        }

        // 3. Post System Notification with launcher badge count
        val unreadCount = messageDao.getTotalUnreadCount()
        val app = applicationDao.getApplicationById(message.appId)?.toDomain()
        if (app != null) notificationManager.createAppChannels(app)
        notificationManager.postMessageNotification(
            message = message,
            app = app,
            tapIntent = buildDetailTapIntent(message.id),
            unreadCount = unreadCount
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
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                    Log.e(TAG, "Not allowed to start foreground service from background", e)
                } else {
                    Log.e(TAG, "Failed to start service", e)
                }
            }
        }
        fun stop(context: Context) {
            val intent = Intent(context, GotifyListenerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
