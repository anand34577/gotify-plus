package com.gotify.client.service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gotify.client.data.datastore.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject
    lateinit var prefs: PreferencesRepository
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in validActions) return
        val hasServer = runBlocking {
            prefs.userPreferences.first().activeServerId != -1L
        }
        if (hasServer) {
            GotifyListenerService.start(context)
        }
    }
}
