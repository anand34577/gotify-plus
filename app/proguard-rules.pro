# ============================================================
# Gotify Plus – ProGuard / R8 rules
# ============================================================
# ---------- Android / AndroidX basics ----------
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
# Keep all public Activity / Service / BroadcastReceiver / ContentProvider
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
# ---------- Hilt (Dagger) ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-dontwarn dagger.hilt.**
# Keep all @HiltViewModel annotated classes
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
# ---------- Retrofit ----------
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Retrofit interfaces used in this project
-keep interface com.gotify.client.data.api.** { *; }
# ---------- Gson / JSON models ----------
# Keep all data classes used for JSON serialisation (Retrofit responses)
-keep class com.gotify.client.data.model.** { *; }
# Gson internals
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**
# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# ---------- Room ----------
# Room generates implementation classes at compile time – keep the annotations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
# ---------- Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
# ---------- Kotlin ----------
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
# ---------- Coil (image loading) ----------
-dontwarn coil.**
# ---------- DataStore ----------
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
# ---------- Compose ----------
# Compose does not require keep rules for runtime, but retain source mapping
-dontwarn androidx.compose.**
# ---------- App-specific sealed classes ----------
# StreamState and ApiResult use sealed subclasses – keep them so type checks work
-keep class com.gotify.client.data.model.StreamState { *; }
-keep class com.gotify.client.data.model.StreamState$* { *; }
-keep class com.gotify.client.data.model.ApiResult { *; }
-keep class com.gotify.client.data.model.ApiResult$* { *; }
# ---------- Room entity / DB ----------
-keep class com.gotify.client.data.db.** { *; }
# ---------- WebSocket / Network client ----------
-keep class com.gotify.client.data.api.NetworkClientFactory { *; }
-keep class com.gotify.client.data.api.GotifyApiClient { *; }
-keep class com.gotify.client.data.api.TokenAuthInterceptor { *; }
# ---------- Miscellaneous ----------
# Suppress warnings for optional dependencies
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.gotify.client.model.** { *; }
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**
