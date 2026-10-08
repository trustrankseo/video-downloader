# Universal Downloader Android

Native Kotlin + Jetpack Compose implementation of Universal Downloader.

Current user-facing version: **v1.6.0**

## Main app areas

- Single Downloader
- Bulk Downloader
- Channel / Playlist / Profile workflow
- MP4 / MP3
- Hamburger sidebar navigation
- Refer & Share
- Device & Eligibility
- App & Engine
- Appearance: Dark / White / System + accent color panel
- Privacy Policy in English + Simplified Chinese
- Uptodown review feedback prompt after real app usage
- About Me — Zubair Abbas
- Contact Us
- Report a Problem

Version 1.6.0 is a free Uptodown-focused build. Google Play subscription/Premium purchase UI and billing are removed. Single, Bulk and Channel/Profile modes are not subscription-gated.

Version 1.6.0 integrates AppLovin MAX for monetization after privacy consent: a small banner is shown on the Downloader screen and an interstitial may appear only after a successful completed download operation. Interstitials use a 90-second local cooldown and ad failures never block downloads. Live ad serving requires the AppLovin SDK key and Banner/Interstitial Ad Unit IDs to be configured in the release build.

The app can politely ask active users for an Uptodown rating/review after at least 3 successful downloads. Users can choose Rate on Uptodown, Maybe Later (7-day snooze), or Don't Ask Again. The prompt only activates when the exact Uptodown listing URL is configured for the build.

The app uses a local yt-dlp/FFmpeg based engine, public/guest access first, no paid YouTube Data API, and does not store social-platform passwords.

## Important platform note

The Android app and queue/UI features can be confirmed independently, but public-media downloading is source/extractor dependent. No third-party source is advertised as guaranteed or presented as affiliated with Universal Downloader.


## v1.6.0 hardening

- Uptodown update certificate is pinned in CI by both MD5 and SHA-256.
- Release APK is restricted to arm64-v8a, matching the current Uptodown distribution architecture.
- Release minification/resource shrinking is enabled with an 85 MiB APK size gate.
- Android 10+ downloads are exported through MediaStore to Downloads/UniversalDownloader.
- Android 9 and older request legacy write permission explicitly.
- Active transfers use a foreground keep-alive service to reduce background termination.
- URL normalization/validation has dedicated unit tests.
- CI includes a real Android emulator Compose launch/privacy smoke test before production publishing.
