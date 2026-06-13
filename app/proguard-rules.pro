# Keep kotlinx.serialization generated serializers for API DTOs.
-keepclassmembers class com.previNet.android.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class com.previNet.android.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
