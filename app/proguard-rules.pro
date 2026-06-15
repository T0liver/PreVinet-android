# Keep kotlinx.serialization generated serializers for API DTOs.
-keepclassmembers class hu.toliver.previnet.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class hu.toliver.previnet.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
