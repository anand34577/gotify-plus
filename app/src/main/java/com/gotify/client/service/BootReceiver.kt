package com.gotify.client.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gotify.client.data.datastore.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.gotify.client.data.db.ServerDao
import javax.inject.Inject


@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var serverDao: ServerDao

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in validActions) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val preferences = prefs.userPreferences.first()
                val hasServer = preferences.activeServerId != -1L &&
                    serverDao.getServerById(preferences.activeServerId) != null
                if (preferences.keepAliveEnabled && hasServer) GotifyListenerService.start(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
