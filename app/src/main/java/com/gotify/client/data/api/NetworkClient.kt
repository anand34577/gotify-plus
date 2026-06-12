package com.gotify.client.data.api
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.gotify.client.data.model.GotifyServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
class TokenAuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider()
        if (token.isNullOrBlank()) return chain.proceed(original)
        val authenticated = original.newBuilder()
            .header("X-Gotify-Key", token)
            .build()
        val response = chain.proceed(authenticated)
        if (response.code == 401) {
            onUnauthorized()
        }
        return response
    }
}
fun buildBasicAuth(username: String, password: String): String {
    val credentials = "$username:$password"
    val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    return "Basic $encoded"
}
object NetworkClientFactory {
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()
    fun create(
        server: GotifyServer,
        isDebug: Boolean = false,
        onUnauthorized: () -> Unit = {}
    ): GotifyApiClient {
        val baseUrl = server.baseUrl.trimEnd('/') + "/"
        val okHttpClient = buildOkHttpClient(
            tokenProvider = { server.clientToken },
            isDebug = isDebug,
            onUnauthorized = onUnauthorized
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        // Publish API uses a plain client — the app token is supplied per-call via @Header
        val plainClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val publishRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(plainClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return GotifyApiClient(
            auth         = retrofit.create(AuthApi::class.java),
            messages     = retrofit.create(MessageApi::class.java),
            applications = retrofit.create(ApplicationApi::class.java),
            users        = retrofit.create(UserApi::class.java),
            server       = retrofit.create(ServerApi::class.java),
            publish      = publishRetrofit.create(MessagePublishApi::class.java)
        )
    }
    private fun buildOkHttpClient(
        tokenProvider: () -> String?,
        isDebug: Boolean,
        onUnauthorized: () -> Unit
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(TokenAuthInterceptor(tokenProvider, onUnauthorized))
        if (isDebug) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }
}
data class GotifyApiClient(
    val auth:         AuthApi,
    val messages:     MessageApi,
    val applications: ApplicationApi,
    val users:        UserApi,
    val server:       ServerApi,
    val publish:      MessagePublishApi
)
