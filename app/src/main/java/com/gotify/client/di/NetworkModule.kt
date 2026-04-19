package com.gotify.client.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.gotify.client.data.api.GotifyApiClient
import com.gotify.client.data.api.GotifyWebSocketManager
import com.gotify.client.data.repository.*
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
    fun provideWebSocketManager(gson: Gson): GotifyWebSocketManager =
        GotifyWebSocketManager(gson)

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
