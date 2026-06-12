package com.gotify.client.notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class GotifyNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository
) {
    companion object {
        const val CHANNEL_FOREGROUND = "gotify_foreground"
        const val CHANNEL_DEFAULT = "gotify_default"
        private const val PREFIX = "app_"
        const val NOTIFICATION_ID_FOREGROUND = 1
    }
    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    fun createBaseChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(
            id = CHANNEL_FOREGROUND,
            name = "Background connection",
            importance = NotificationManager.IMPORTANCE_MIN,
            sound = false,
            vibrate = false
        )
        createChannel(
            id = CHANNEL_DEFAULT,
            name = "Gotify notifications",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            sound = true,
            vibrate = false
        )
    }
    fun createAppChannels(app: GotifyApplication) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(
            id = lowChannelId(app.id),
            name = "${app.name} — Low",
            importance = NotificationManager.IMPORTANCE_LOW,
            sound = false,
            vibrate = false
        )
        createChannel(
            id = normalChannelId(app.id),
            name = app.name,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            sound = true,
            vibrate = false
        )
        createChannel(
            id = highVibrateChannelId(app.id),
            name = "${app.name} — High",
            importance = NotificationManager.IMPORTANCE_HIGH,
            sound = true,
            vibrate = true
        )
        createChannel(
            id = highSilentChannelId(app.id),
            name = "${app.name} — High (silent)",
            importance = NotificationManager.IMPORTANCE_HIGH,
            sound = true,
            vibrate = false
        )
    }
    fun postMessageNotification(
        message: GotifyMessage,
        app: GotifyApplication?,
        tapIntent: PendingIntent,
        unreadCount: Int = 0
    ) {
        if (message.priority == 0) return
        val userPrefs = runBlocking { prefs.userPreferences.first() }
        if (!userPrefs.notificationsEnabled) return
        val priority = Priority.fromInt(message.priority)
        val appName = app?.name ?: "Gotify"
        val channelId = when {
            app == null -> CHANNEL_DEFAULT
            priority == Priority.LOW -> lowChannelId(app.id)
            priority == Priority.NORMAL -> normalChannelId(app.id)
            userPrefs.vibrationEnabled -> highVibrateChannelId(app.id)
            else -> highSilentChannelId(app.id)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setNumber(unreadCount)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(message.title.ifBlank { appName })
            .setContentText(message.message)
            .setSubText(appName)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setGroup("gotify_${app?.id ?: 0}")
            .setPriority(priority.notifCompat)
            .setCategory(
                if (priority == Priority.HIGH) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_MESSAGE
            )
        if (message.message.length > 80) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message.message)
                    .setBigContentTitle(message.title.ifBlank { appName })
            )
        }
        val actionUrl = message.extras?.action?.onClick?.intentUrl
        if (!actionUrl.isNullOrBlank()) {
            val actionIntent = PendingIntent.getActivity(
                context, message.id.toInt(),
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(actionUrl)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Open", actionIntent)
        }
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(message.id.toInt(), builder.build())
            } catch (_: SecurityException) {
            }
        }
    }
    fun buildForegroundNotification(
        serverName: String,
        tapIntent: PendingIntent
    ): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Gotify connected")
            .setContentText(serverName)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    fun cancelNotification(messageId: Long) = manager.cancel(messageId.toInt())
    fun cancelAllNotifications() = manager.cancelAll()
    private fun lowChannelId(appId: Int) = "${PREFIX}${appId}_low"
    private fun normalChannelId(appId: Int) = "${PREFIX}${appId}_normal"
    private fun highVibrateChannelId(appId: Int) = "${PREFIX}${appId}_high_v"
    private fun highSilentChannelId(appId: Int) = "${PREFIX}${appId}_high_s"
    private fun createChannel(
        id: String,
        name: String,
        importance: Int,
        sound: Boolean,
        vibrate: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Channels are immutable once created — skip if already registered.
        // Both vibrate and silent variants are always kept registered; routing
        // in postMessageNotification picks the correct one per user preference.
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(id, name, importance).apply {
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(vibrate)
            if (vibrate) {
                vibrationPattern = longArrayOf(0, 250, 100, 250)
            } else {
                // Explicitly set a zero-length pattern so that high-importance
                // channels don't vibrate due to ROM-level default behaviour.
                vibrationPattern = longArrayOf(0)
            }
            setSound(
                if (sound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null,
                if (sound) AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                else null
            )
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Ensures both the vibrate and silent high-priority channel variants are
     * registered for every app. Never deletes channels — deleting and recreating
     * with the same ID causes Android to inherit a silenced state, breaking sound.
     * The routing logic in [postMessageNotification] picks the correct channel
     * based on the user's current vibration preference.
     */
    fun updateVibrationChannels(apps: List<GotifyApplication>) {
        // Simply ensure both channel variants exist for each app.
        // createChannel is a no-op if the channel is already registered.
        apps.forEach { app -> createAppChannels(app) }
    }
}
enum class Priority(val notifCompat: Int) {
    LOW(NotificationCompat.PRIORITY_LOW),
    NORMAL(NotificationCompat.PRIORITY_DEFAULT),
    HIGH(NotificationCompat.PRIORITY_HIGH);
    companion object {
        fun fromInt(p: Int): Priority = when {
            p <= 0 -> LOW
            p <= 3 -> LOW
            p <= 7 -> NORMAL
            else -> HIGH
        }
    }
}