package com.faisal.freshdownloader

object AdsConfig {
    val sdkKey: String get() = BuildConfig.APPLOVIN_SDK_KEY.trim()
    val bannerAdUnitId: String get() = BuildConfig.APPLOVIN_BANNER_AD_UNIT_ID.trim()
    val interstitialAdUnitId: String get() = BuildConfig.APPLOVIN_INTERSTITIAL_AD_UNIT_ID.trim()

    val sdkConfigured: Boolean get() = sdkKey.isNotBlank()
    val bannerConfigured: Boolean get() = sdkConfigured && bannerAdUnitId.isNotBlank()
    val interstitialConfigured: Boolean get() = sdkConfigured && interstitialAdUnitId.isNotBlank()
    val isConfigured: Boolean get() = bannerConfigured && interstitialConfigured
}
