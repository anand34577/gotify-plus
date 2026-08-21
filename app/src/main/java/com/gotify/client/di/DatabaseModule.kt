package com.gotify.client.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS messages_new (
                    id INTEGER NOT NULL,
                    serverId INTEGER NOT NULL,
                    appId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    message TEXT NOT NULL,
                    priority INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    extrasJson TEXT,
                    isRead INTEGER NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    PRIMARY KEY(serverId, id)
                )
            """.trimIndent())
            db.execSQL("INSERT INTO messages_new SELECT id, serverId, appId, title, message, priority, date, extrasJson, isRead, cachedAt FROM messages")
            db.execSQL("DROP TABLE messages")
            db.execSQL("ALTER TABLE messages_new RENAME TO messages")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_serverId ON messages(serverId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_appId ON messages(appId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_date ON messages(date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_priority ON messages(priority)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS applications_new (
                    id INTEGER NOT NULL,
                    serverId INTEGER NOT NULL,
                    token TEXT,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    internal INTEGER NOT NULL,
                    image TEXT NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    PRIMARY KEY(serverId, id)
                )
            """.trimIndent())
            db.execSQL("INSERT INTO applications_new SELECT id, serverId, token, name, description, internal, image, cachedAt FROM applications")
            db.execSQL("DROP TABLE applications")
            db.execSQL("ALTER TABLE applications_new RENAME TO applications")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_applications_serverId ON applications(serverId)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GotifyDatabase =
        Room.databaseBuilder(
            context,
            GotifyDatabase::class.java,
            GotifyDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideServerDao(db: GotifyDatabase):      ServerDao      = db.serverDao()
    @Provides fun provideMessageDao(db: GotifyDatabase):     MessageDao     = db.messageDao()
    @Provides fun provideApplicationDao(db: GotifyDatabase): ApplicationDao = db.applicationDao()

    @Provides
    @Singleton
    fun providePreferencesRepository(@ApplicationContext context: Context): PreferencesRepository =
        PreferencesRepository(context)
}
