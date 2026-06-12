package com.gotify.client.data.datastore
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gotify_prefs")
data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true,
    val dynamicColorEnabled: Boolean = false,
    val markdownEnabled: Boolean = true,
    val keepAliveEnabled: Boolean = true,
    val themeSelection: String = "DEFAULT",
    val activeServerId: Long = -1L,
    val lastSyncTimestamp: Long = 0L
)
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val MARKDOWN_ENABLED = booleanPreferencesKey("markdown_enabled")
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val THEME_SELECTION = stringPreferencesKey("theme_selection")
        val ACTIVE_SERVER_ID = longPreferencesKey("active_server_id")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
    }
    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            UserPreferences(
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
                darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true,
                dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: false,
                markdownEnabled = prefs[Keys.MARKDOWN_ENABLED] ?: true,
                keepAliveEnabled = prefs[Keys.KEEP_ALIVE_ENABLED] ?: true,
                themeSelection = prefs[Keys.THEME_SELECTION] ?: "DEFAULT",
                activeServerId = prefs[Keys.ACTIVE_SERVER_ID] ?: -1L,
                lastSyncTimestamp = prefs[Keys.LAST_SYNC_TIMESTAMP] ?: 0L
            )
        }
    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    suspend fun setVibrationEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    suspend fun setDarkThemeEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DARK_THEME_ENABLED] = enabled }
    suspend fun setDynamicColorEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    suspend fun setMarkdownEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.MARKDOWN_ENABLED] = enabled }
    suspend fun setKeepAliveEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.KEEP_ALIVE_ENABLED] = enabled }
    suspend fun setThemeSelection(theme: String) =
        context.dataStore.edit { it[Keys.THEME_SELECTION] = theme }
    suspend fun setActiveServerId(id: Long) =
        context.dataStore.edit { it[Keys.ACTIVE_SERVER_ID] = id }
    suspend fun setLastSyncTimestamp(ts: Long) =
        context.dataStore.edit { it[Keys.LAST_SYNC_TIMESTAMP] = ts }
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
