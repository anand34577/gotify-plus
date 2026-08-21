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
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GotifyNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository
) {
    companion object {
        const val CHANNEL_FOREGROUND    = "gotify_foreground"
        const val CHANNEL_DEFAULT       = "gotify_default"
        const val CHANNEL_HIGH_FALLBACK = "gotify_high"
        private  const val PREFIX       = "app_"
        const val NOTIFICATION_ID_FOREGROUND = 1
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createBaseChannels() {
        createChannel(
            id         = CHANNEL_FOREGROUND,
            name       = context.getString(R.string.notification_channel_foreground),
            importance = NotificationManager.IMPORTANCE_MIN,
            sound      = false,
            vibrate    = false
        )
        createChannel(
            id         = CHANNEL_DEFAULT,
            name       = context.getString(R.string.notification_channel_default),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            sound      = true,
            vibrate    = false
        )
        createChannel(
            id         = CHANNEL_HIGH_FALLBACK,
            name       = context.getString(R.string.notification_channel_high),
            importance = NotificationManager.IMPORTANCE_HIGH,
            sound      = true,
            vibrate    = true
        )
    }

    fun createAppChannels(serverId: Long, app: GotifyApplication) {
        createChannel(
            id         = lowChannelId(serverId, app.id),
            name       = "${app.name} — Low",
            importance = NotificationManager.IMPORTANCE_LOW,
            sound      = false,
            vibrate    = false
        )
        createChannel(
            id         = normalChannelId(serverId, app.id),
            name       = app.name,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            sound      = true,
            vibrate    = false
        )
        createChannel(
            id         = highVibrateChannelId(serverId, app.id),
            name       = "${app.name} — High",
            importance = NotificationManager.IMPORTANCE_HIGH,
            sound      = true,
            vibrate    = true
        )
        createChannel(
            id         = highSilentChannelId(serverId, app.id),
            name       = "${app.name} — High (silent)",
            importance = NotificationManager.IMPORTANCE_HIGH,
            sound      = true,
            vibrate    = false
        )
    }

    suspend fun postMessageNotification(
        message:   GotifyMessage,
        app:       GotifyApplication?,
        tapIntent: PendingIntent,
        serverId:  Long
    ) {
        if (message.priority <= 0) return
        val userPrefs = prefs.userPreferences.first()
        if (!userPrefs.notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return

        val priority  = Priority.fromInt(message.priority)
        val appName   = app?.name ?: "Gotify"

        val channelId = when {
            app == null && priority != Priority.HIGH -> CHANNEL_DEFAULT
            app == null -> CHANNEL_HIGH_FALLBACK
            priority == Priority.LOW    -> lowChannelId(serverId, app.id)
            priority == Priority.NORMAL -> normalChannelId(serverId, app.id)
            userPrefs.vibrationEnabled  -> highVibrateChannelId(serverId, app.id)
            else                        -> highSilentChannelId(serverId, app.id)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title.ifBlank { appName })
            .setContentText(message.message)
            .setSubText(appName)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setGroup("gotify_${serverId}_${app?.id ?: 0}")
            .setPriority(priority.notifCompat)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (message.message.length > 80) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message.message)
                    .setBigContentTitle(message.title.ifBlank { appName })
            )
        }

        val actionUrl = message.extras?.notification?.click?.url
            ?: message.extras?.action?.onClick?.intentUrl
        if (!actionUrl.isNullOrBlank()) {
            val targetIntent = try {
                Intent.parseUri(actionUrl, Intent.URI_INTENT_SCHEME)
            } catch (_: Exception) {
                Intent(Intent.ACTION_VIEW, actionUrl.toUri())
            }
            val actionIntent = PendingIntent.getActivity(
                context, stableId(serverId, message.id),
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification, "Open", actionIntent)
        }

        with(NotificationManagerCompat.from(context)) {
            notify(stableId(serverId, message.id), builder.build())
        }
    }

    fun buildForegroundNotification(serverName: String, tapIntent: PendingIntent): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.service_running))
            .setContentText(serverName)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    fun cancelNotification(serverId: Long, messageId: Long) = manager.cancel(stableId(serverId, messageId))
    fun cancelAllNotifications()            = manager.cancelAll()

    private fun lowChannelId(serverId: Long, appId: Int)        = "${PREFIX}${serverId}_${appId}_low"
    private fun normalChannelId(serverId: Long, appId: Int)     = "${PREFIX}${serverId}_${appId}_normal"
    private fun highVibrateChannelId(serverId: Long, appId: Int)= "${PREFIX}${serverId}_${appId}_high_v"
    private fun highSilentChannelId(serverId: Long, appId: Int) = "${PREFIX}${serverId}_${appId}_high_s"
    private fun stableId(serverId: Long, messageId: Long): Int = 31 * serverId.hashCode() + messageId.hashCode()

    private fun createChannel(
        id:         String,
        name:       String,
        importance: Int,
        sound:      Boolean,
        vibrate:    Boolean
    ) {
        manager.getNotificationChannel(id)?.let { existing ->
            if (existing.name != name) {
                existing.name = name
                manager.createNotificationChannel(existing)
            }
            return
        }
        val channel = NotificationChannel(id, name, importance).apply {
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(vibrate)
            if (vibrate) vibrationPattern = longArrayOf(0, 250, 100, 250)
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
            else   -> HIGH
        }
    }
}
