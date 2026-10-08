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

The old Premium purchase page and Google Billing integration are removed from v1.5.0.

### ✅ Uptodown review feedback prompt
- Triggered only after real usage: at least 3 successful downloads
- Opens the configured official Uptodown app listing
- Maybe Later snoozes the prompt for 7 days
- Don't Ask Again permanently disables the prompt on that installation
- Once the Uptodown page is opened for review, the automatic prompt stops
- No fake/incentivized review text is generated; users are asked for their own experience

### ✅ AppLovin MAX ads
- AppLovin MAX SDK is integrated for v1.5.0.
- A small banner is placed at the bottom of the Downloader screen.
- Interstitial ads are requested only after a completed operation with at least one successful download.
- Bulk and Channel/Profile operations trigger at most one interstitial after the whole operation, not one per item.
- A 90-second local cooldown prevents repeated interstitials.
- Ads initialize only after the current privacy consent is accepted.
- Missing ad fill, load errors or display errors do not block downloads.
- Live serving requires valid AppLovin SDK Key, Banner Ad Unit ID and Interstitial Ad Unit ID in the release environment.

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
