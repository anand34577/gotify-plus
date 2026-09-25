package com.gotify.client.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.db.ServerDao
import com.gotify.client.data.db.CredentialCipher
import com.gotify.client.data.db.toDomain
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.repository.ServerManager
import com.gotify.client.notification.GotifyNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val TAG = "GotifyService"


@AndroidEntryPoint
class GotifyListenerService : Service() {

    @Inject lateinit var serverManager:        ServerManager
    @Inject lateinit var notificationManager:   GotifyNotificationManager
    @Inject lateinit var serverDao:              ServerDao
    @Inject lateinit var prefs:                  PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var startupJob: Job? = null



    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        notificationManager.createBaseChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        startAsForeground()
        if (startupJob?.isActive != true) {
            startupJob = scope.launch { initializeAndObserve() }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        // Socket is owned by ServerManager; the foreground app keeps using it after keep-alive is turned off.
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
            val server = entity.toDomain().copy(isActive = true)
            runCatching {
                check(server.clientToken.isNotBlank()) { "Saved server token is unavailable" }
                check(serverManager.addServer(server)) { "Invalid saved server URL" }
                if (!CredentialCipher.isEncrypted(entity.clientToken)) {
                    serverDao.updateServer(
                        entity.copy(
                            clientToken = CredentialCipher.encrypt(entity.clientToken).orEmpty(),
                            isActive = true
                        )
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to restore the saved server", error)
                stopSelf()
                return
            }
        }
        // Messages are ingested app-wide (GotifyApplication); this service only keeps the process alive.
        observeActiveServer()
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

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GotifyListenerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GotifyListenerService::class.java))
        }
    }
}
