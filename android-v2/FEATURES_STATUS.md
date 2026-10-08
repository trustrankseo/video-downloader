# Universal Downloader — Feature Status & How It Works

Current user-facing version: **v1.6.3**

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

Version 1.6.3 does **not** gate Bulk or Channel/Profile behind a subscription.

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

The old Premium purchase page and Google Billing integration are removed from v1.6.0.

### ✅ Uptodown review feedback prompt
- Triggered only after real usage: at least 3 successful downloads
- Opens the configured official Uptodown app listing
- Maybe Later snoozes the prompt for 7 days
- Don't Ask Again permanently disables the prompt on that installation
- Once the Uptodown page is opened for review, the automatic prompt stops
- No fake/incentivized review text is generated; users are asked for their own experience

### ✅ Ad-free distribution
- No AppLovin MAX SDK.
- No banner or interstitial advertising.
- No advertising-ID permission.
- Download and referral behavior is independent of ad services.

### ✅ Privacy
- Mandatory first-launch privacy consent
- English and Simplified Chinese policy
- In-app Privacy Policy remains accessible from the sidebar
- No precise-location permission is required

### ✅ Refer & Share / install record
Each installation has a privacy-safe app-generated install ID and referral code. Referral linking can be entered manually or captured from supported referral sources. A pending referral activates after a successful download.

### ✅ Appearance
- Dark
- White
- System
- Accent color choices

### ✅ About, Contact, Report
The About page identifies **Zubair Abbas — Developer & creator of Universal Downloader**. Contact Us and Report a Problem use the user's email app; reports are not silently uploaded.

## Platform-dependent behavior

Direct public URLs can work where the relevant source exposes media to the extractor. Channel/profile enumeration can be limited by authentication, anti-bot changes, source redesigns or other platform-side behavior. The app should fail cleanly rather than claim a bypass.

## Public-media policy

Universal Downloader is an independent utility and is not affiliated with, endorsed by, sponsored by, or associated with any third-party social-media or media platform. Users should download only content they own or are authorized to save.

## How a normal download works

1. User accepts the current Privacy Policy.
2. App initializes its local media engine.
3. User selects Single, Bulk or Channel/Profile.
4. User selects MP4 or MP3.
5. App processes the public URL(s) through the local downloader engine.
6. Progress/status appears in the queue.
7. On success, files are saved to the Universal Downloader output folder.
8. Successful usage increments the local review-prompt eligibility counter.
9. Once eligible, the app can ask the user to share an honest rating/review on Uptodown.

## Backend-required future features

- Account login and cloud sync
- Cross-device referral rewards/statistics
- Central fraud controls
- Direct in-app support reporting
- Server-side entitlement systems if subscriptions are reintroduced later


## v1.6.3 quality gates

A release is considered publishable only when all of these pass:

- Host JVM unit tests
- Android instrumented launch/privacy smoke test on an emulator
- Release APK build
- APK ZIP integrity and zipalign verification
- APK Signature Scheme verification
- Exact existing-update certificate match: MD5 `471d573bfd94138a052087a80679b4f4`
- Exact certificate SHA-256 `146b8de20d69a75cecef7b949e2280a6779ae2590e4ed2af0c3f733a774af1ed`
- arm64-v8a-only native-library gate
- APK size no larger than 85 MiB
- Production publishing only from `fresh-android-v2` after verification and emulator smoke tests pass
