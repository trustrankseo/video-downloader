# Keep the downloader bridge used by the embedded Python/FFmpeg runtime.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Keep Kotlin metadata required by reflection-heavy libraries.
-keep class kotlin.Metadata { *; }

