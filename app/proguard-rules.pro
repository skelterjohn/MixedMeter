# Release shrinking (R8). mapping.txt is copied to app/release/ after bundleRelease.

# Readable deobfuscated stack traces in Play Console (upload mapping.txt per release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin / coroutines (Compose, lifecycle, DataStore).
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Jetpack Compose runtime keeps (@Stable / @Immutable are handled by default consumer rules).
-keep @androidx.compose.runtime.Stable class *
-keep @androidx.compose.runtime.Immutable class *
