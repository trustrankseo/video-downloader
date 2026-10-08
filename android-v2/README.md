# Universal Downloader for Android

Universal Downloader is a native Android media-downloader app built with **Kotlin + Jetpack Compose**. It supports individual public media links, bulk queues, and public channel/playlist/profile discovery through a local **yt-dlp + FFmpeg** engine.

> Current Android target: **v1.6.1** · versionCode **42**

## What the app can do

### Single Download
Paste one direct public media URL and download it as:

- MP4 video
- MP3 audio

Single mode is intended for individual video/post/short links. If a channel, playlist, or profile URL is detected, the app directs the user to **Channel mode** instead of leaving the job stuck on “Starting”.

### Bulk Download
Paste multiple direct media URLs, one per line.

- Duplicate URLs are removed automatically.
- Choose **1–8 parallel download threads**.
- Multiple items can begin downloading at the same time.
- Failed or cancelled items can be retried without rebuilding the whole queue.

### Channel / Playlist / Profile
Paste a supported public collection URL.

The app first discovers public items, then starts the download queue.

- YouTube channel tabs can be discovered in parallel.
- Choose **1–8 parallel download threads** for the discovered items.
- The discovered queue remains available after cancellation/failure.
- Retrying an item does **not** require fetching the whole channel again.

Availability depends on what each third-party source exposes publicly without account authentication.

## Retry system

v1.6.1 adds two retry levels:

- **RETRY THIS ITEM** — shown on each failed or cancelled item.
- **RETRY ALL** — restarts all failed and cancelled items in the current queue.

The original URL and selected MP4/MP3 format are preserved for retry.

## Download engine improvements in v1.6.1

- Multi-process yt-dlp job tracking instead of a single global process ID.
- Cancel/Abort can stop all active download processes in a parallel queue.
- Each active download uses its own temporary job workspace to avoid filename collisions.
- Media engine readiness is checked before download/discovery work begins.
- Extra extractor, fragment, and socket retry handling.
- Up to 4 concurrent fragments inside each yt-dlp transfer.
- Bulk and Channel queues support **1–8 simultaneous items**.
- Progress now reports a clearer **Fetching media info…** state before actual byte progress begins.
- Direct-media modes reject obvious collection URLs early rather than appearing frozen.

## Queue controls

Each queue item shows:

- URL
- Status
- Progress bar
- Progress/ETA or error message
- Per-item retry when applicable

Queue summary shows:

- Total
- Found
- Active
- Done
- Failed
- Cancelled

The main **STOP / ABORT DOWNLOADS** action stops current active jobs and marks queued jobs as cancelled so they can be retried later.

## Storage

On Android 10 and newer, completed files are published through MediaStore to:

`Downloads/UniversalDownloader`

On Android 9 and older, the app uses the legacy public Downloads directory and requests write permission when required.

Temporary job files are kept in the app workspace and removed after completion.

## Background downloads

Active transfers use an Android foreground service so the operating system is less likely to kill a running download when the app is backgrounded.

## Downloader Engine Update

The sidebar includes an **Update Engine** action. It updates the bundled yt-dlp engine through its stable update channel.

This can help when a supported website changes its public extraction behavior.

## Privacy and third-party platforms

Universal Downloader is designed around public/guest-access media extraction.

- The app does not store social-platform passwords.
- No paid YouTube Data API is required.
- Third-party download availability can change when a source changes its website or requires authentication.
- Universal Downloader is not affiliated with the websites whose public links a user may choose to process.
- Users are responsible for downloading only content they are authorized to save.

## Monetization

The Uptodown build integrates **AppLovin MAX** after privacy consent.

- A banner may appear on the Downloader screen.
- An interstitial may appear after a successful completed download operation.
- Interstitials have a local cooldown.
- Ad failures never block a download.

Live ads require the AppLovin SDK key and ad-unit IDs to be configured in the release environment.

## Review prompt

After real successful usage, the app may ask the user to leave feedback on Uptodown.

Available choices:

- Rate on Uptodown
- Maybe Later
- Don’t Ask Again

The prompt only activates when the Uptodown listing URL is configured in the build.

## Appearance and app sections

The app also includes:

- Dark / White / System appearance modes
- Accent color selection
- Refer & Share
- Device & Eligibility
- App & Engine
- Privacy Policy
- About
- Contact Us
- Report a Problem

## Android requirements

- Minimum Android version: **Android 7.0 (API 24)**
- Target SDK: **Android 15 / API 35**
- Current production APK architecture: **arm64-v8a**

The arm64-only production build is intentional to keep the APK substantially smaller than a multi-ABI package.

## Build

The Android project is inside `android-v2/`.

Unit tests:

```bash
cd android-v2
gradle :app:testDebugUnitTest --stacktrace --no-daemon
```

Build the Uptodown-compatible APK:

```bash
cd android-v2
gradle :app:assembleUptodown --stacktrace --no-daemon
```

Expected APK path:

```text
android-v2/app/build/outputs/apk/uptodown/app-uptodown.apk
```

## Release verification

The GitHub Actions release gate verifies:

- Unit tests
- Uptodown-compatible APK build
- Existing-app signing certificate SHA-256
- Existing Uptodown certificate MD5
- APK ZIP integrity
- Android package metadata
- zipalign
- APK signature validity
- arm64-v8a-only architecture
- APK size limit of **60 MiB**
- Final release artifact integrity

Device/runtime testing is currently performed manually on a physical Android phone before wider rollout; emulator testing is not used as a release blocker.

## v1.6.1 highlights

- Fixed downloads appearing to stay on **Starting** without useful progress feedback.
- Added safer media-engine initialization before actual extraction.
- Added proper parallel-process tracking.
- Added Bulk **Threads** selector.
- Added Channel **Threads** selector.
- Added per-item **Retry**.
- Added **Retry All**.
- Failed/cancelled Channel items can retry without discovering the channel again.
- Added Single-mode protection against accidentally submitting a channel/profile URL as a direct video.
- Preserved update compatibility with the existing Uptodown signing chain.

---

**Universal Downloader** is intended as a practical public-media download manager with a fast queue, clear failure handling, and user-controlled concurrency.
