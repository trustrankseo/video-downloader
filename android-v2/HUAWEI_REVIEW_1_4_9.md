# Huawei AppGallery Review Notes — Universal Downloader 1.4.9

## Release identity

- App: Universal Downloader
- Package: `com.faisal.freshdownloader`
- Version name: `1.4.9`
- Version code: `34` (one above rejected 1.4.8/version code 33)
- Signing: Huawei production release keystore (`huawei-release.jks`, alias `universalrelease`) supplied by encrypted GitHub Actions secrets. The workflow does not create or use the test/debug key for the release artifact.

## 1. China Mainland privacy issue

Huawei reported that the submitted policy did not provide a convenient Simplified Chinese version for China Mainland users.

Implemented fixes:

- Added a complete Simplified Chinese policy containing the same material disclosures as English.
- Added a static, script-free language selector at the top of the public policy.
- Added a direct Simplified Chinese route.
- Updated the in-app policy to the same English and Simplified Chinese disclosures.
- Simplified Chinese locales default to Chinese while both languages remain selectable.
- Increased the in-app policy consent version so existing users see the materially updated policy.
- Downloader-engine initialization and referral attribution are deferred until consent is accepted. Billing starts only if the accepted user opens Premium.
- Kept a permanent **Privacy Policy** item in the sidebar.

Public policy URLs:

- English and Simplified Chinese: https://trustrankseo.github.io/video-downloader/privacy.html
- Simplified Chinese direct page: https://trustrankseo.github.io/video-downloader/privacy-zh-cn.html

## 2. Third-party intellectual-property similarity issue

Huawei reported that the prior app presentation could be confused with a third-party social-media app.

Implemented fixes:

- Removed the third-party platform/logo carousel and all trademark-style platform badges from the app UI.
- Replaced platform-specific promotional wording and task labels with neutral terms such as **Supported public media links** and **Public media**.
- Removed third-party names from user-facing error and status messages.
- Kept Universal Downloader's existing blue/purple identity and launcher icon unchanged.
- Added this visible About/legal disclaimer:

> Universal Downloader is an independent utility and is not affiliated with, endorsed by, sponsored by, or associated with any third-party social media or media platform. Users are responsible for downloading only content they own or are authorized to save.

The same notice is included in the public and in-app privacy policies.

### Internal technical identifiers retained

The only remaining `tiktok` text is the literal `tiktok.com` host used internally in `DownloaderEngine.kt` for URL normalization, redirect handling, extractor request headers and source/profile recognition. It is not rendered in Compose UI, status/error messages, splash content, About content, policy text or promotional material. Removing these host checks would break processing of a user-supplied compatible URL, which the review brief explicitly permits when technically supported and lawful.

## Additional installation compatibility

- The minimum SDK is API 24, matching the native downloader library's actual requirement.
- The release no longer filters to one ABI; the APK includes ARM64, ARMv7, x86 and x86_64 native libraries.
- The obsolete manifest native-library and legacy-storage flags were removed.

## Manual reviewer test steps

1. Install the fresh 1.4.9 production-signed release APK.
2. Launch the app and confirm Privacy Policy & User Consent appears before the main app.
3. Confirm no downloader engine, referral attribution or billing flow starts before consent.
4. Select **简体中文** and read the complete Chinese policy.
5. Tap **阅读隐私政策** and confirm the direct Chinese public page opens.
6. Return, select the acknowledgement checkbox and tap **同意并继续**.
7. Open the sidebar and select **Privacy Policy**; confirm both languages remain accessible.
8. Open **About Me** and verify the independent-app disclaimer.
9. Inspect the splash, Downloader, queue and About screens; confirm there is no third-party logo, trademark badge, platform carousel or official-association claim.
10. Confirm Single, Bulk, Channel, MP4, MP3, referral, Premium, themes, sidebar and queue navigation remain present.
11. Confirm the launcher icon matches the existing Universal Downloader AppGallery listing icon.

