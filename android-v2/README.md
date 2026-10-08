# Universal Downloader Android

Native Kotlin + Jetpack Compose implementation of Universal Downloader.

Current user-facing version: **v1.5.0**

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

Version 1.5.0 is a free Uptodown-focused build. Google Play subscription/Premium purchase UI and billing are removed. Single, Bulk and Channel/Profile modes are not subscription-gated.

Advertising is intentionally deferred until the app reaches the planned user milestone. No AppLovin or other ad SDK is included in v1.5.0.

The app can politely ask active users for an Uptodown rating/review after at least 3 successful downloads. Users can choose Rate on Uptodown, Maybe Later (7-day snooze), or Don't Ask Again. The prompt only activates when the exact Uptodown listing URL is configured for the build.

The app uses a local yt-dlp/FFmpeg based engine, public/guest access first, no paid YouTube Data API, and does not store social-platform passwords.

## Important platform note

The Android app and queue/UI features can be confirmed independently, but public-media downloading is source/extractor dependent. No third-party source is advertised as guaranteed or presented as affiliated with Universal Downloader.
