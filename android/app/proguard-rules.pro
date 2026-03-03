# ProGuard rules for Financial Management System

# Keep all classes in the app package
-keep class com.psychologist.financial.** { *; }

# Keep Room database classes
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep Kotlin classes
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Serialization
-keepclassmembers class * {
    *** **(***);
}
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * implements kotlinx.serialization.Serializable {
    *** Companion;
    *** INSTANCE;
}

# Keep Tink encryption
-keep class com.google.crypto.tink.** { *; }

# Keep BiometricPrompt
-keep class androidx.biometric.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# SQLCipher - Keep native library references
-keep class net.zetetic.** { *; }

# Apache Commons CSV
-keep class org.apache.commons.csv.** { *; }

# Generic Android stuff
-keep public class android.** { public *; }
-keep public class javax.** { *; }

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Remove logging in production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep BuildConfig for version info
-keep class **.BuildConfig { *; }

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep model classes with annotations
-keep class * {
    @com.google.crypto.tink.annotations.* <fields>;
}

# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove verbose logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
