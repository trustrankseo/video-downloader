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
- AppLovin MAX banner + post-download interstitial monetization
- Device & Eligibility
- App & Engine
- Appearance: Dark / White / System + accent color panel
- Privacy Policy in English + Simplified Chinese
- About Me — Zubair Abbas
- Contact Us
- Report a Problem

Version 1.5.0 is a free, ad-supported Uptodown build. Google Play subscription/Premium purchase UI is not included in this build. Single, Bulk and Channel/Profile modes are not subscription-gated.

Ads initialize only after the user accepts Privacy Policy v4. A banner may appear on the Downloader screen and an interstitial may appear after a successful completed download operation, subject to a 90-second cooldown. Ad load/show failures never block downloads.

The app uses a local yt-dlp/FFmpeg based engine, public/guest access first, no paid YouTube Data API, and does not store social-platform passwords.

## Important platform note

The Android app and queue/UI features can be confirmed independently, but public-media downloading is source/extractor dependent. No third-party source is advertised as guaranteed or presented as affiliated with Universal Downloader.

For the detailed confirmed-feature list, limitations, platform status, referral flow and how each feature works, see **[FEATURES_STATUS.md](FEATURES_STATUS.md)**.
