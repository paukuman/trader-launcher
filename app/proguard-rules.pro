# R8 rules for the release build.

# OkHttp and Okio reference optional TLS providers that are not on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Declared in the manifest (kept automatically); listed so the intent is explicit.
-keep class com.example.traderlauncher.MonitorService
-keep class com.example.traderlauncher.BootReceiver
