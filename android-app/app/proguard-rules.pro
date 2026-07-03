# ProGuard rules for TracciaNei

# ---- kotlinx.serialization ----
# Keep serialization classes and their generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable data classes in our data package
-keep class com.example.chronomapscanner.data.** { *; }
-keepclassmembers class com.example.chronomapscanner.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.chronomapscanner.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- CameraX ----
-keep class androidx.camera.** { *; }

# ---- Coil ----
-dontwarn coil.**
