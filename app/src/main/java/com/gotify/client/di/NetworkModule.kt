package com.gotify.client.di
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.repository.ApplicationRepository
import com.gotify.client.data.repository.AuthRepository
import com.gotify.client.data.repository.MessageRepository
import com.gotify.client.data.repository.ServerManager
import com.gotify.client.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()
    @Provides
    @Singleton
    fun provideWebSocketManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        gson: Gson
    ): GotifyWebSocketManager = GotifyWebSocketManager(gson, context)
    @Provides
    @Singleton
    fun provideServerManager(
        webSocketManager: GotifyWebSocketManager
    ): ServerManager = ServerManager(webSocketManager)
}
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    fun provideAuthRepository(
        serverManager: ServerManager
    ): AuthRepository? = serverManager.apiClient?.let { AuthRepository(it) }
    @Provides
    fun provideMessageRepository(
        serverManager: ServerManager
    ): MessageRepository? = serverManager.apiClient?.let { MessageRepository(it) }
    @Provides
    fun provideApplicationRepository(
        serverManager: ServerManager
    ): ApplicationRepository? = serverManager.apiClient?.let { ApplicationRepository(it) }
    @Provides
    fun provideUserRepository(
        serverManager: ServerManager
    ): UserRepository? = serverManager.apiClient?.let { UserRepository(it) }
}
