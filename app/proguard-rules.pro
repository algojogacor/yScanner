# Proguard rules for yScanner app
-keep class androidx.compose.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class com.yscanner.domain.model.** { *; }
