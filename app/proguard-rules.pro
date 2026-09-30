# Smart Intercom ProGuard Rules

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes Signature

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }

# Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }

# Tink crypto
-keep class com.google.crypto.tink.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep model classes for serialization
-keep class com.bikeride.intercom.core.model.** { *; }

# Keep all project application components, services, and models intact
-keep class com.bikeride.intercom.** { *; }
-keepclassmembers class com.bikeride.intercom.** { *; }

# Vosk offline speech (Voice SOS) uses JNA, which relies on reflection
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
