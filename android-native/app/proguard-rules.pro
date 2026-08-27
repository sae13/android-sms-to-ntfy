# Add project specific PROGUARD rules here.
# For more details, see https://developer.android.com/build/shrink-code

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Moshi
-keep class kotlin.reflect.jvm.internal.impl.builtins.KotlinBuiltIns { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.**
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# DataStore
-keep class androidx.datastore.** { *; }
