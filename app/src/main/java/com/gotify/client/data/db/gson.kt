package com.gotify.client.data.db
import com.google.gson.Gson
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.data.model.GotifyServer
import com.gotify.client.data.model.MessageExtras
private val gson = Gson()
fun MessageEntity.toDomain(): GotifyMessage = GotifyMessage(
    id = id,
    appId = appId,
    title = title,
    message = message,
    priority = priority,
    date = date,
    extras = extrasJson?.let {
        try {
            gson.fromJson(it, MessageExtras::class.java)
        } catch (e: Exception) {
            null
        }
    }
)
fun GotifyMessage.toEntity(serverId: Long): MessageEntity = MessageEntity(
    id = id,
    serverId = serverId,
    appId = appId,
    title = title,
    message = message,
    priority = priority,
    date = date,
    extrasJson = extras?.let { gson.toJson(it) }
)
fun ApplicationEntity.toDomain(): GotifyApplication = GotifyApplication(
    id = id,
    token = token,
    name = name,
    description = description,
    internal = internal,
    image = image
)
fun GotifyApplication.toEntity(serverId: Long): ApplicationEntity = ApplicationEntity(
    id = id,
    serverId = serverId,
    token = token,
    name = name,
    description = description,
    internal = internal,
    image = image
)
fun ServerEntity.toDomain(): GotifyServer = GotifyServer(
    id = id,
    name = name,
    baseUrl = baseUrl,
    clientToken = clientToken,
    isActive = isActive
)
fun GotifyServer.toEntity(clientId: Int = 0): ServerEntity = ServerEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    clientToken = clientToken,
    clientId = clientId,
    isActive = isActive
)
