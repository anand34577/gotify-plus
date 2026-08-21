# The app deserializes Gotify payloads through Gson and Retrofit reflection.
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod

-keep class com.gotify.client.data.model.** { *; }
-keep interface com.gotify.client.data.api.** { *; }
