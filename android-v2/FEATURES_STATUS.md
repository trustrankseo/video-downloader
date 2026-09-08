# Universal Downloader — Feature Status & How It Works

Current user-facing version: **v1.4.2**

This file separates features that are implemented/confirmed in the Android app from features that depend on third-party platform behavior. A successful Android build confirms that the code compiles and packages correctly; it does not guarantee that every social platform will keep exposing downloadable media at runtime.

## Status legend

- ✅ **Confirmed app feature** — implemented in the Android code and expected to work independently of a social platform.
- 🟡 **Platform-dependent** — implemented, but final success depends on the target website/extractor/public access.
- ⚠️ **Limited / not fully confirmed** — known restrictions exist.
- 🔜 **Future / backend required** — architecture may be ready, but a server/account system is still needed.

## Confirmed app features

### ✅ Hamburger sidebar navigation

The home screen stays focused on downloading. Other tools live inside the three-line menu:

- Downloader
- Refer & Earn
- Premium
- Device & Eligibility
- App & Engine
- Appearance
- About Me
- Contact Us
- Report a Problem

This avoids showing every card on one long page.

### ✅ Appearance: Dark, White and System

Three theme modes are available:

- **Dark** — black/dark interface.
- **White** — clean light interface.
- **System** — automatically follows the Android device light/dark setting.

The selected mode is saved locally and reused on future launches.

### ✅ Accent color panel

The user can choose an interface accent color:

- Blue
- Cyan
- Purple
- Green
- Orange
- Red

The accent is used for primary controls, selected states and highlights. The choice is stored on the device.

### ✅ Single Downloader

Single mode accepts one supported public media URL. The app detects the platform, sends the URL to the downloader engine, shows progress, and saves the completed file to the Universal Downloader output folder.

Single mode is intended to remain free.

### ✅ Bulk queue system

Bulk mode accepts multiple URLs, one per line. The app:

1. Cleans the input.
2. Removes duplicate URLs.
3. Creates a local queue.
4. Downloads items one by one.
5. Shows completed, failed and cancelled states.
6. Allows failed items to be retried.
7. Allows the active queue to be stopped.

Bulk access is connected to the Premium/trial rules.

### ✅ Channel / Playlist / Profile workflow

The app can pass a collection URL to the discovery engine. If the target platform exposes public items, discovered URLs are converted into download tasks and processed through the same queue.

The collection workflow itself is implemented. Whether a specific profile/channel can be enumerated is platform-dependent.

### ✅ MP4 / MP3 format selection

The downloader supports video and audio presets through the local yt-dlp/FFmpeg engine.

### ✅ Local download queue controls

Implemented controls include:

- Paste Link
- Clear Input
- Stop / Abort
- Retry Failed
- Clear Finished
- Progress and ETA display where available

### ✅ Local referral system

Each installation gets an app-generated referral identity. The referral system supports:

- A unique referral code.
- Manual referral-code entry.
- Referral deep links.
- Google Play Install Referrer when installed through Google Play.
- Organic share counting.
- Self-referral rejection on the same local installation.
- Referral activation only after a successful download.
- Bonus Premium trial credit after a valid referral activation.

Important: current referral rewards are local to the installation. Cross-device referrer rewards require a backend/account system.

### ✅ Device & eligibility record

The app stores a privacy-safe local installation record, including information such as:

- App-generated install ID.
- Install source when available.
- Referral state.
- Referral eligibility.
- Organic share count.
- App version.

The app does **not** use IMEI, hardware serial number, contacts, microphone, camera or screen recording for this feature.

### ✅ Free trial access rules

Current Premium access logic:

- Single Downloader remains free.
- Bulk and Channel/Profile start with **3 free trials**.
- Referral activation can add bonus trial entitlement up to the configured app limit.
- After available trials are consumed, Premium is required.

Trial state is currently device-local. A reinstall-proof/cross-device entitlement system requires authenticated backend storage.

### ✅ Google Play Billing integration

Google Play Billing code is included for the subscription product:

`universal_downloader_premium_monthly`

The app can query the subscription product, open the purchase flow and restore purchases when the product/base plan is correctly configured and active in Google Play Console.

The actual store price should come from Google Play rather than being treated as a permanent hardcoded price.

### ✅ About Me

The About page identifies:

**Zubair Abbas — Developer & creator of Universal Downloader**

It also shows the current app version and a short product description.

### ✅ Contact Us

Contact Us opens the user's email app with a Universal Downloader support message ready to send.

### ✅ Report a Problem

Users can select an issue category, add a title and explain the problem. The report prepares useful diagnostics such as:

- App version.
- Android SDK version.
- Device manufacturer/model.
- Short local install ID.
- Install source.

The user's email app opens with the report for review before sending. Nothing is silently uploaded in the background.

### ✅ App & Engine page

The menu contains an App & Engine section showing the current status and version. It also exposes the downloader-engine update action where supported.

## Platform download status

### 🟡 YouTube

Direct public video URLs are the strongest current use case. Playlist/channel discovery is implemented, but YouTube may still return anti-bot, authentication or PO-token related restrictions for some URLs/environments.

Do not describe every YouTube URL as guaranteed.

### ⚠️ TikTok

TikTok profile discovery and direct media downloading are **not confirmed as reliably working** in the current Android environment. Recent TikTok/yt-dlp behavior can require impersonation capabilities that are not available in this build, and TikTok may not expose a usable MP4/CDN URL to the Android WebView/native resolver.

The app should fail cleanly rather than claim a bypass.

### ⚠️ Instagram

Public direct reel/post URLs may work when the platform exposes them to guest extraction. Full profile discovery is not confirmed and Instagram can require login.

### ⚠️ Facebook

Public direct reel/video URLs may work when Facebook exposes them. Full Page/Profile enumeration is not considered reliable and may fail when public profile data changes or requires authentication.

### 🟡 X / Twitter, Reddit, Vimeo, Twitch, SoundCloud, Pinterest, Bilibili, Dailymotion, RedNote and other supported extractors

The app can route supported public URLs through the downloader engine. Success is extractor/site dependent and should not be advertised as guaranteed until runtime-tested against the current platform version.

## How a normal download works

1. User opens **Downloader** from the sidebar.
2. User chooses Single, Bulk or Channel/Profile.
3. User selects MP4 or MP3.
4. App validates the URL input.
5. The local downloader engine starts processing.
6. Progress/status is shown in the queue.
7. On success, the file is saved to the configured Universal Downloader download folder.
8. On failure, the app shows a friendly platform/engine error where possible.

## How Premium access works

1. Single remains free.
2. Bulk/Channel checks whether Premium is active.
3. If not Premium, the app checks remaining free + referral bonus trials.
4. If a trial is available, one entitlement is consumed for the Premium operation.
5. When trials reach zero, the Premium screen is shown.
6. On Google Play builds, purchase/restore uses Google Play Billing.

## How referral activation works

1. A new installation receives or manually enters a referral code.
2. The app checks basic local eligibility and blocks use of its own code.
3. The referral stays pending.
4. The referred installation completes a successful download.
5. The referral becomes activated and local bonus access is credited according to the current rules.

For reliable referrer-side rewards across different phones, accounts and reinstalls, a backend is still required.

## Features that still need a backend for full protection

### 🔜 Account login and cloud entitlement

Needed for:

- Reinstall-proof free-trial enforcement.
- Cross-device Premium entitlement outside store restore.
- Cross-device referral rewards.
- Central referral statistics.
- Server-side fraud checks.
- Web/admin reporting dashboard.

### 🔜 Direct in-app report delivery

Current reports use the user's email client. To make reports arrive automatically inside an admin dashboard without requiring email, a secure backend/API is required.

## Recommended next feature

The best next user-facing feature is **Download History & File Manager**.

Recommended behavior:

- Keep a local history of successful downloads.
- Show title, platform, format, date and file location.
- Open/play the downloaded file.
- Share the file.
- Delete the file with confirmation.
- Re-download from the original URL.
- Filter history by platform or MP4/MP3.

This would add daily usefulness without depending on another social platform API and would fit naturally as another sidebar page.
