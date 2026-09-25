package com.gotify.client.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gotify.client.data.datastore.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MuteAppReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var notifications: GotifyNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1L)
        val appId = intent.getIntExtra(EXTRA_APP_ID, -1)
        if (serverId < 0 || appId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                prefs.setAppMute(serverId, appId, System.currentTimeMillis() + ONE_HOUR_MS)
                notifications.cancelServerNotifications(serverId, appId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_SERVER_ID = "server_id"
        private const val EXTRA_APP_ID = "app_id"
        private const val ONE_HOUR_MS = 60 * 60 * 1000L

        fun intent(context: Context, serverId: Long, appId: Int, messageId: Long): Intent =
            Intent(context, MuteAppReceiver::class.java)
                .setAction("mute:$serverId:$appId:$messageId")
                .putExtra(EXTRA_SERVER_ID, serverId)
                .putExtra(EXTRA_APP_ID, appId)
    }
}
