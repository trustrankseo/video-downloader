# Universal Downloader Android

Native Kotlin + Jetpack Compose implementation of Universal Downloader.

Current user-facing version: **v1.4.2**

## Main app areas

- Single Downloader
- Bulk Downloader
- Channel / Playlist / Profile workflow
- MP4 / MP3
- Hamburger sidebar navigation
- Refer & Earn
- Premium + free-trial access
- Device & Eligibility
- App & Engine
- Appearance: Dark / White / System + accent color panel
- About Me — Zubair Abbas
- Contact Us
- Report a Problem

The app uses a local yt-dlp/FFmpeg based engine, public/guest access first, no paid YouTube Data API, and does not store social-platform passwords.

## Important platform note

The Android app and queue/UI features can be confirmed independently, but social-platform downloading is site/extractor dependent. YouTube direct public links are the strongest current use case. TikTok profile/media extraction, Instagram profile discovery and Facebook profile enumeration are not advertised as guaranteed.

For the detailed confirmed-feature list, limitations, platform status, referral/Premium flow and how each feature works, see **[FEATURES_STATUS.md](FEATURES_STATUS.md)**.
