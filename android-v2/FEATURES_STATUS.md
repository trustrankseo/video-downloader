# Universal Downloader — Feature Status & How It Works

Current user-facing version: **v1.5.0**

A successful Android build confirms that the app compiles and packages correctly. Third-party media downloading remains source/extractor dependent and can change outside the app.

## Confirmed app features

### ✅ Downloader modes
- Single public-link download
- Bulk URL queue
- Channel / Playlist / Profile discovery workflow where the source exposes public items
- MP4 / MP3
- Stop / Abort
- Retry Failed
- Clear Finished
- Progress and ETA where available

Version 1.5.0 does **not** gate Bulk or Channel/Profile behind a subscription.

### ✅ Sidebar
- Downloader
- Refer & Share
- Device & Eligibility
- App & Engine
- Appearance
- Privacy Policy
- About Me
- Contact Us
- Report a Problem

The old Premium purchase page is removed from this Uptodown ads build.

### ✅ AppLovin MAX ads
- Banner placement: Downloader screen bottom
- Interstitial placement: after a successful completed download operation
- Bulk/Channel: maximum one interstitial opportunity after the whole operation, not one per item
- 90-second interstitial cooldown
- Failed/cancelled operations do not trigger an ad
- Ad load/show failure does not stop or fail a download
- AppLovin initialization happens only after Privacy Policy consent

Live ads require the release build to receive the developer's AppLovin SDK Key, Banner Ad Unit ID and Interstitial Ad Unit ID at build time.

### ✅ Privacy
- Mandatory first-launch privacy consent
- English and Simplified Chinese policy
- Privacy consent version 4 for the ads change
- AppLovin advertising/device-signal disclosure
- In-app Privacy Policy remains accessible from the sidebar
- No precise-location permission is required

### ✅ Refer & Share / install record
Each installation has a privacy-safe app-generated install ID and referral code. Referral linking can be entered manually or captured from supported referral sources. A pending referral activates after a successful download.

The current free ads build does not award Premium trials. Cross-device referral rewards/statistics still require a backend/account system.

### ✅ Appearance
- Dark
- White
- System
- Accent color choices

### ✅ About, Contact, Report
The About page identifies **Zubair Abbas — Developer & creator of Universal Downloader**. Contact Us and Report a Problem use the user's email app; reports are not silently uploaded.

## Platform-dependent behavior

### 🟡 YouTube
Direct public URLs are a strong use case. Playlist/channel discovery is implemented, but YouTube may return anti-bot, authentication or PO-token restrictions depending on the current platform environment.

### ⚠️ Instagram / Facebook / other sources
Direct public items may work when the platform exposes them to guest extraction. Full profile/page enumeration is not guaranteed. The app should fail cleanly rather than claim a bypass.

### Public-media policy
Universal Downloader is an independent utility and is not affiliated with, endorsed by, sponsored by, or associated with any third-party social-media or media platform. Users should download only content they own or are authorized to save.

## How a normal download works

1. User accepts the current Privacy Policy.
2. App initializes its local media engine and, when configured, AppLovin MAX.
3. User selects Single, Bulk or Channel/Profile.
4. User selects MP4 or MP3.
5. App processes the public URL(s) through the local downloader engine.
6. Progress/status appears in the queue.
7. On success, files are saved to the Universal Downloader output folder.
8. After the whole successful operation finishes, one interstitial may be shown if loaded and the 90-second cooldown permits it.
9. Ad unavailability never blocks the completed download.

## Backend-required future features

- Account login and cloud sync
- Cross-device referral rewards/statistics
- Central fraud controls
- Direct in-app support reporting
- Server-side entitlement systems if subscriptions are reintroduced later

## Recommended next feature

A local **Download History & File Manager** remains a useful next feature: title, format, date, file location, open/play, share, delete and re-download.
