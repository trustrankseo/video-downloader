# Universal Downloader v1.5.0 Uptodown AppLovin MAX Design

## Goal

Release Universal Downloader v1.5.0 for Uptodown with ads-only monetization using AppLovin MAX. Remove the current subscription/Premium purchase flow, keep Single/Bulk/Channel downloading free, show a small banner on the Downloader screen, and show a controlled interstitial only after a successful user-initiated download run.

## Current state

- Repository: `trustrankseo/video-downloader`
- Branch: `fresh-android-v2`
- Android app: `android-v2/`
- Package: `com.faisal.freshdownloader`
- Current code version: `1.4.9`, versionCode `34`
- Current billing dependency: Google Play Billing Client
- Current UI still exposes a Premium page and subscription/trial language.
- Existing first-launch privacy consent supports English and Simplified Chinese.
- Current release workflow builds a production-signed APK with the existing Huawei release signing chain.

## Versioning

The Uptodown ads release will be:

- `versionName = "1.5.0"`
- `versionCode = 35`

The package name must remain `com.faisal.freshdownloader` so Uptodown recognizes it as an update to the existing app.

## Monetization model

v1.5.0 is ads-only.

- No paid subscription.
- No purchase or restore-purchase UI.
- No Premium paywall.
- No 3-trial gate for Bulk or Channel/Profile.
- Single, Bulk, and Channel/Profile remain available without payment.
- Referral remains available for organic sharing, but no Premium/trial reward language or paid-entitlement behavior remains in this release.

## AppLovin MAX integration

Use AppLovin MAX as the only ad SDK in this release.

Create a focused `AdsManager` abstraction responsible for SDK initialization, banner lifecycle, interstitial preloading, cooldown tracking, and safe show behavior. Downloader and UI code must not directly own AppLovin implementation details beyond calling this abstraction.

Release configuration must use environment/Gradle-injected values for:

- AppLovin SDK key
- Banner ad unit ID
- Interstitial ad unit ID

Do not commit production ad credentials directly into Kotlin source. A release build intended for monetization must fail clearly if required production ad configuration is missing. Debug/test builds may use AppLovin test mode or non-production configuration.

## Privacy and consent

The existing Universal Downloader privacy gate remains the first app gate.

No AppLovin SDK initialization and no ad request should occur before the user accepts the app's privacy policy. After acceptance, initialize the ad subsystem.

Update English and Simplified Chinese privacy disclosures to explain that the advertising SDK may process advertising identifiers, device/app information, IP/network information, coarse location derived from network signals where applicable, ad interaction data, fraud-prevention signals, and consent/privacy choices as described by the advertising provider.

Keep the in-app Privacy Policy page and public privacy pages consistent.

For regions where advertising consent is legally required, use AppLovin's current official privacy/consent flow or the current officially supported consent mechanism recommended by AppLovin at implementation time. Ads must not be requested before the required consent state is resolved.

## Banner behavior

Use one small banner ad on the main Downloader screen only.

- Place it at the bottom of the Downloader content area without covering buttons, progress cards, navigation, or system bars.
- The downloader remains fully usable if the banner fails to load.
- Do not show duplicate banners on sidebar pages.
- Do not show a banner on the first-launch privacy consent screen.
- Do not force layout jumps that make the primary download button move unpredictably while the user is interacting with it.

## Interstitial behavior

Interstitial ads are tied to completed user-initiated download runs, not individual files inside a queue.

- Single mode: eligible after one successful single download finishes.
- Bulk mode: eligible once after the whole user-started bulk queue finishes, provided at least one item completed successfully.
- Channel/Profile mode: eligible once after the whole discovered collection run finishes, provided at least one item completed successfully.
- Failed, cancelled, stopped, or discovery-only operations do not trigger an interstitial.
- Do not show an interstitial in the middle of active downloading.
- Do not show an interstitial on app launch.
- Do not block the saved-file success state if no ad is ready.

A 90-second cooldown applies between interstitial displays. If the ad is not loaded or the cooldown has not elapsed, skip the ad and continue normally. Preload the next interstitial after dismissal or load failure using normal AppLovin retry guidance.

## Download completion signal

`DownloaderViewModel` currently knows when Single, Bulk, and Collection runs finish. Add a run-level completion event or monotonically increasing completion token that the Compose UI can observe once per completed user action.

The event must include enough information to decide whether an interstitial is eligible, for example:

- run type: Single / Bulk / Collection
- successful item count
- cancelled/stopped flag
- unique run ID or completion sequence

The UI consumes each completion event only once and asks `AdsManager` to show an interstitial if eligible. This prevents showing one interstitial for every item in Bulk/Channel runs.

## Subscription removal

Remove the Google Play subscription system from the Uptodown v1.5.0 app code path.

- Remove `com.android.billingclient:billing-ktx` from the app dependency set for this release.
- Remove the Premium navigation item.
- Remove Premium purchase/restore UI.
- Remove or retire `BillingManager.kt` from the compiled release code.
- Remove subscription/trial gating from Bulk and Channel/Profile.
- Remove user-facing references to monthly price, Premium status, purchase restore, and free Premium trials.
- Update Device/Eligibility, Referral, README, feature-status documentation, and privacy text so they do not claim subscription benefits that no longer exist.

Do not delete unrelated referral/install attribution functionality simply because the reward model changes.

## UI behavior

Keep the existing hamburger sidebar, themes, appearance controls, privacy page, About Me, Contact Us, Report a Problem, referral/sharing, and downloader UI.

The sidebar should no longer contain `Premium`.

The Downloader page should continue using the existing clean layout, with one bottom banner container added. No ad should cover the URL input, format controls, download buttons, task list, Stop button, or system navigation.

## Failure handling

Ads are optional to the core downloader flow.

- SDK initialization failure: app and downloader continue working.
- Banner load failure: hide/leave the banner area non-blocking and retry according to SDK guidance.
- Interstitial load failure: skip the ad; never treat the download as failed.
- No network: downloader behavior remains independent; ad failures stay silent/non-blocking.
- Activity not in a valid foreground state: do not attempt to show the interstitial.
- Privacy consent not accepted/resolved: do not initialize or request ads.

## Release signing and distribution

Keep the existing production signing identity so this APK can update the currently published Uptodown package. Do not switch to the debug/test signing key.

The GitHub Actions artifact should be renamed for this release to:

`UniversalDownloader-v1.5.0-Uptodown-AppLovin`

The workflow must verify the generated release APK signature before artifact upload.

## Testing requirements

Before calling v1.5.0 complete, verify at minimum:

1. Fresh install shows privacy consent before any ad initialization.
2. Declining privacy exits without requesting ads.
3. Accepting privacy allows the main app and initializes ads.
4. Banner appears only on Downloader and never covers controls.
5. Banner failure does not break the Downloader screen.
6. Single successful download can trigger one interstitial.
7. Failed/cancelled Single download triggers no interstitial.
8. Bulk queue with multiple successful items triggers at most one interstitial after the full run.
9. Channel/Profile collection triggers at most one interstitial after the full run.
10. 90-second cooldown prevents back-to-back interstitials.
11. No ready interstitial does not delay or invalidate a completed download.
12. Premium sidebar item is gone.
13. Google Billing dependency and purchase code are absent from the v1.5.0 release.
14. Bulk and Channel/Profile are usable without a subscription or trial check.
15. English and Simplified Chinese privacy disclosures include advertising processing.
16. Release APK remains signed with the existing production signing identity.
17. Package remains `com.faisal.freshdownloader`.
18. Version reports `1.5.0` and versionCode `35`.

## Non-goals

This release does not add:

- subscriptions
- paid Premium
- rewarded ads
- cash referral rewards
- user accounts/login
- a new backend
- additional ad networks
- ad mediation beyond AppLovin MAX's normal SDK capabilities

Those can be separate later changes after v1.5.0 is stable on Uptodown.
