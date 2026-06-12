package com.gotify.client.data.api
import com.gotify.client.data.model.ClientResponse
import com.gotify.client.data.model.CreateApplicationRequest
import com.gotify.client.data.model.CreateUserRequest
import com.gotify.client.data.model.GotifyApplication
import com.gotify.client.data.model.GotifyMessage
import com.gotify.client.data.model.GotifyUser
import com.gotify.client.data.model.HealthResponse
import com.gotify.client.data.model.LoginRequest
import com.gotify.client.data.model.PagedMessages
import com.gotify.client.data.model.PublishMessageRequest
import com.gotify.client.data.model.UpdateUserRequest
import com.gotify.client.data.model.VersionInfo
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
interface AuthApi {
    @POST("client")
    suspend fun createClient(
        @Body request: LoginRequest, @Header("Authorization") basicAuth: String
    ): Response<ClientResponse>
    @DELETE("client/{id}")
    suspend fun deleteClient(
        @Path("id") clientId: Int
    ): Response<Unit>
    @GET("client")
    suspend fun getClients(): Response<List<ClientResponse>>
}
interface MessageApi {
    @GET("message")
    suspend fun getMessages(
        @Query("limit") limit: Int = 100, @Query("since") since: Long? = null
    ): Response<PagedMessages>
    @GET("application/{appId}/message")
    suspend fun getMessagesByApp(
        @Path("appId") appId: Int,
        @Query("limit") limit: Int = 100,
        @Query("since") since: Long? = null
    ): Response<PagedMessages>
    @DELETE("message/{id}")
    suspend fun deleteMessage(
        @Path("id") messageId: Long
    ): Response<Unit>
    @DELETE("message")
    suspend fun deleteAllMessages(): Response<Unit>
    @DELETE("application/{appId}/message")
    suspend fun deleteMessagesByApp(
        @Path("appId") appId: Int
    ): Response<Unit>
}
/** Separate interface for publishing messages — uses an app token, not a client token. */
interface MessagePublishApi {
    @POST("message")
    suspend fun publishMessage(
        @Header("X-Gotify-Key") appToken: String,
        @Body request: PublishMessageRequest
    ): Response<GotifyMessage>
}
interface ApplicationApi {
    @GET("application")
    suspend fun getApplications(): Response<List<GotifyApplication>>
    @POST("application")
    suspend fun createApplication(
        @Body request: CreateApplicationRequest
    ): Response<GotifyApplication>
    @PUT("application/{id}")
    suspend fun updateApplication(
        @Path("id") appId: Int, @Body request: CreateApplicationRequest
    ): Response<GotifyApplication>
    @DELETE("application/{id}")
    suspend fun deleteApplication(
        @Path("id") appId: Int
    ): Response<Unit>
    @Multipart
    @POST("application/{id}/image")
    suspend fun uploadApplicationImage(
        @Path("id") appId: Int, @Part image: MultipartBody.Part
    ): Response<GotifyApplication>
}
interface UserApi {
    @GET("user")
    suspend fun getUsers(): Response<List<GotifyUser>>
    @GET("user/current")
    suspend fun getCurrentUser(): Response<GotifyUser>
    @POST("user")
    suspend fun createUser(
        @Body request: CreateUserRequest
    ): Response<GotifyUser>
    @GET("user/{id}")
    suspend fun getUser(
        @Path("id") userId: Int
    ): Response<GotifyUser>
    @POST("user/{id}")
    suspend fun updateUser(
        @Path("id") userId: Int, @Body request: UpdateUserRequest
    ): Response<GotifyUser>
    @DELETE("user/{id}")
    suspend fun deleteUser(
        @Path("id") userId: Int
    ): Response<Unit>
}
interface ServerApi {
    @GET("version")
    suspend fun getVersion(): Response<VersionInfo>
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>
}
