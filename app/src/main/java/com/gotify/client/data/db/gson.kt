package com.gotify.client.data.db


import com.google.gson.Gson
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.data.model.MessageExtras

private val gson = Gson()

fun MessageEntity.toDomain(): GotifyMessage = GotifyMessage(
    id       = id,
    appId    = appId,
    title    = title,
    message  = message,
    priority = priority,
    date     = date,
    extras   = extrasJson?.let {
        try { gson.fromJson(it, MessageExtras::class.java) } catch (e: Exception) { null }
    },
    isRead   = isRead
)

fun GotifyMessage.toEntity(serverId: Long, isRead: Boolean? = null): MessageEntity = MessageEntity(
    id         = id,
    serverId   = serverId,
    appId      = appId,
    title      = title,
    message    = message,
    priority   = priority,
    date       = date,
    extrasJson = extras?.let { gson.toJson(it) },
    isRead     = isRead ?: this.isRead
)

fun ApplicationEntity.toDomain(): GotifyApplication = GotifyApplication(
    id          = id,
    token       = CredentialCipher.decrypt(token),
    name        = name,
    description = description,
    internal    = internal,
    image       = image
)

fun GotifyApplication.toEntity(serverId: Long, cachedToken: String? = null): ApplicationEntity = ApplicationEntity(
    id          = id,
    serverId    = serverId,
    // Preserve an encrypted cached token if the Android Keystore is temporarily
    // unavailable; a sync must never silently erase the only local credential.
    token       = CredentialCipher.encrypt(
        token ?: CredentialCipher.decrypt(cachedToken) ?: cachedToken
    ),
    name        = name,
    description = description,
    internal    = internal,
    image       = image
)

fun ServerEntity.toDomain(): GotifyServer = GotifyServer(
    id          = id,
    name        = name,
    baseUrl     = baseUrl,
    clientToken = CredentialCipher.decrypt(clientToken).orEmpty(),
    isActive    = isActive
)

fun GotifyServer.toEntity(clientId: Int = 0): ServerEntity = ServerEntity(
    id          = id,
    name        = name,
    baseUrl     = baseUrl,
    clientToken = CredentialCipher.encrypt(clientToken).orEmpty(),
    clientId    = clientId,
    isActive    = isActive
)
