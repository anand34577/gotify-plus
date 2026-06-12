package com.gotify.client.di
import android.content.Context
import androidx.room.Room
import com.gotify.client.data.datastore.PreferencesRepository
import com.gotify.client.data.db.ApplicationDao
import com.gotify.client.data.db.GotifyDatabase
import com.gotify.client.data.db.MessageDao
import com.gotify.client.data.db.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GotifyDatabase =
        Room.databaseBuilder(
            context,
            GotifyDatabase::class.java,
            GotifyDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    @Provides
    fun provideServerDao(db: GotifyDatabase): ServerDao = db.serverDao()
    @Provides
    fun provideMessageDao(db: GotifyDatabase): MessageDao = db.messageDao()
    @Provides
    fun provideApplicationDao(db: GotifyDatabase): ApplicationDao = db.applicationDao()
    @Provides
    @Singleton
    fun providePreferencesRepository(@ApplicationContext context: Context): PreferencesRepository =
        PreferencesRepository(context)
}
