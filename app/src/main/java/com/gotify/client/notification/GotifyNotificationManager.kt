/*
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
    private val prefs: PreferencesRepository          // ← injected for runtime pref reads
) {
    companion object {
        const val CHANNEL_FOREGROUND = "gotify_foreground"
        const val CHANNEL_DEFAULT    = "gotify_default"
        private const val CHANNEL_APP_PREFIX = "app_"
        const val NOTIFICATION_ID_FOREGROUND = 1
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ── Channel Setup ─────────────────────────────────────────────────────────

    fun createBaseChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(CHANNEL_FOREGROUND, "Background connection",
            "Keeps Gotify connected", NotificationManager.IMPORTANCE_MIN, sound = false, vibrate = false)
        createChannel(CHANNEL_DEFAULT, "Gotify notifications",
            "Default channel", NotificationManager.IMPORTANCE_DEFAULT, sound = true, vibrate = false)
    }

    fun createAppChannels(app: GotifyApplication) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(channelId(app.id, Priority.LOW),
            "${app.name} — Low", "Silent from ${app.name}",
            NotificationManager.IMPORTANCE_LOW, sound = false, vibrate = false)
        createChannel(channelId(app.id, Priority.NORMAL),
            app.name, "From ${app.name}",
            NotificationManager.IMPORTANCE_DEFAULT, sound = true, vibrate = false)
        createChannel(channelId(app.id, Priority.HIGH),
            "${app.name} — High", "Urgent from ${app.name}",
            NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = true)
    }


    fun recreateHighPriorityChannels(app: GotifyApplication, vibrate: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val id = channelId(app.id, Priority.HIGH)
        manager.deleteNotificationChannel(id)
        createChannel(id, "${app.name} — High", "Urgent from ${app.name}",
            NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = vibrate)
    }

    // ── Post Notification ─────────────────────────────────────────────────────

    fun postMessageNotification(
        message:    GotifyMessage,
        app:        GotifyApplication?,
        tapIntent:  PendingIntent
    ) {
        if (message.priority == 0) return

        // Read current prefs synchronously — this is on a background thread (IO dispatcher)
        val userPrefs = runBlocking { prefs.userPreferences.first() }
        if (!userPrefs.notificationsEnabled) return

        val priority  = Priority.fromInt(message.priority)
        val channelId = if (app != null) channelId(app.id, priority) else CHANNEL_DEFAULT
        val appName   = app?.name ?: "Gotify"

        val builder = NotificationCompat.Builder(context, channelId)
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

        // FIX 2: Apply vibration at BUILDER level, not just channel level.
        // This gives us runtime control regardless of what the channel was created with.
        if (priority == Priority.HIGH) {
            if (userPrefs.vibrationEnabled) {
                builder.setVibrate(longArrayOf(0, 250, 100, 250))
            } else {
                builder.setVibrate(longArrayOf(0))   // zero-length = no vibration
            }
            builder.setLights(Color.BLUE, 500, 500)
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
            try { notify(message.id.toInt(), builder.build()) }
            catch (_: SecurityException) { }
        }
    }

    fun buildForegroundNotification(serverName: String, tapIntent: PendingIntent): android.app.Notification =
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun channelId(appId: Int, priority: Priority) =
        "${CHANNEL_APP_PREFIX}${appId}_${priority.name.lowercase()}"

    private fun createChannel(
        id: String, name: String, description: String,
        importance: Int, sound: Boolean, vibrate: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Don't recreate if already exists — caller must delete first if they want to change it
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(id, name, importance).apply {
            this.description = description
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(vibrate)
            if (sound) {
                val attrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attrs)
            } else {
                setSound(null, null)
            }
        }
        manager.createNotificationChannel(channel)
    }
}

// ─── Priority ─────────────────────────────────────────────────────────────────

enum class Priority(val notifCompat: Int) {
    LOW(NotificationCompat.PRIORITY_LOW),
    NORMAL(NotificationCompat.PRIORITY_DEFAULT),
    HIGH(NotificationCompat.PRIORITY_HIGH);

    companion object {
        fun fromInt(p: Int): Priority = when {
            p <= 0  -> LOW
            p <= 3  -> LOW
            p <= 7  -> NORMAL
            else    -> HIGH
        }
    }
}
*/


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
        const val CHANNEL_DEFAULT    = "gotify_default"
        private const val CHANNEL_APP_PREFIX = "app_"
        const val NOTIFICATION_ID_FOREGROUND = 1
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ── Channel Setup ─────────────────────────────────────────────────────────
    //
    // KEY FIX: vibration is NEVER set on any channel — always false.
    // Channels are immutable after creation, so setting vibration on them
    // means we can never reliably turn it off (Android restores the old setting
    // even after delete+recreate with the same ID).
    //
    // Vibration is controlled 100% at the NotificationCompat.Builder level
    // in postMessageNotification(), which reads the live user preference each time.

    fun createBaseChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(
            id = CHANNEL_FOREGROUND, name = "Background connection",
            description = "Keeps Gotify connected", importance = NotificationManager.IMPORTANCE_MIN,
            sound = false
        )
        createChannel(
            id = CHANNEL_DEFAULT, name = "Gotify notifications",
            description = "Default channel", importance = NotificationManager.IMPORTANCE_DEFAULT,
            sound = true
        )
    }

    fun createAppChannels(app: GotifyApplication) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createChannel(
            id = channelId(app.id, Priority.LOW),
            name = "${app.name} — Low", description = "Silent from ${app.name}",
            importance = NotificationManager.IMPORTANCE_LOW, sound = false
        )
        createChannel(
            id = channelId(app.id, Priority.NORMAL),
            name = app.name, description = "From ${app.name}",
            importance = NotificationManager.IMPORTANCE_DEFAULT, sound = true
        )
        createChannel(
            id = channelId(app.id, Priority.HIGH),
            name = "${app.name} — High", description = "Urgent from ${app.name}",
            importance = NotificationManager.IMPORTANCE_HIGH, sound = true
            // vibration intentionally omitted — controlled at builder level only
        )
    }

    // ── Post Notification ─────────────────────────────────────────────────────

    fun postMessageNotification(
        message:   GotifyMessage,
        app:       GotifyApplication?,
        tapIntent: PendingIntent
    ) {
        if (message.priority == 0) return

        val userPrefs = runBlocking { prefs.userPreferences.first() }
        if (!userPrefs.notificationsEnabled) return

        val priority  = Priority.fromInt(message.priority)
        val channelId = if (app != null) channelId(app.id, priority) else CHANNEL_DEFAULT
        val appName   = app?.name ?: "Gotify"

        val builder = NotificationCompat.Builder(context, channelId)
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

        // Vibration — controlled here at builder level, NOT on the channel.
        // This is the only place that needs to change when the user toggles.
        // On Android O+, builder-level vibration works as long as the channel
        // does NOT have vibration set (which we ensure above).
        if (priority == Priority.HIGH && userPrefs.vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 100, 250))
            builder.setLights(Color.BLUE, 500, 500)
        }
        // When vibration is disabled, we simply don't call setVibrate() at all.
        // This guarantees no vibration regardless of Android version.

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
            try { notify(message.id.toInt(), builder.build()) }
            catch (_: SecurityException) { }
        }
    }

    fun buildForegroundNotification(
        serverName: String,
        tapIntent:  PendingIntent
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun channelId(appId: Int, priority: Priority) =
        "${CHANNEL_APP_PREFIX}${appId}_${priority.name.lowercase()}"

    private fun createChannel(
        id:          String,
        name:        String,
        description: String,
        importance:  Int,
        sound:       Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(id, name, importance).apply {
            this.description = description
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(false)  // always false — vibration via builder only
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
            p <= 0 -> LOW; p <= 3 -> LOW; p <= 7 -> NORMAL; else -> HIGH
        }
    }
}