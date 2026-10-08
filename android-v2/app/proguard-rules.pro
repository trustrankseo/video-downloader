# Keep the downloader bridge used by the embedded Python/FFmpeg runtime.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Keep Kotlin metadata required by reflection-heavy libraries.
-keep class kotlin.Metadata { *; }

# AppLovin ships consumer rules; retain public SDK entry points as an additional release safeguard.
-keep class com.applovin.sdk.** { public *; }
-keep class com.applovin.mediation.** { public *; }

# AppLovin OMID can reference Amazon PrivacyPass when that optional runtime is absent.
-dontwarn com.amazon.privacypass.**
