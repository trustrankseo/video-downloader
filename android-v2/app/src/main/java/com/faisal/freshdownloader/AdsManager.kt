package com.faisal.freshdownloader

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration

object AdsManager : MaxAdListener {
    private const val PREFS = "universal_downloader_ads"
    private const val KEY_LAST_INTERSTITIAL_MS = "last_interstitial_display_ms"
    private const val COOLDOWN_MS = 90_000L

    private val policy = AdDisplayPolicy(COOLDOWN_MS)

    @Volatile
    private var initialized = false

    val initializedState = mutableStateOf(false)

    @Volatile
    private var initializing = false

    private var interstitial: MaxInterstitialAd? = null
    private var appContext: Context? = null

    val isConfigured: Boolean
        get() = BuildConfig.APPLOVIN_SDK_KEY.isNotBlank() &&
            BuildConfig.APPLOVIN_BANNER_AD_UNIT_ID.isNotBlank() &&
            BuildConfig.APPLOVIN_INTERSTITIAL_AD_UNIT_ID.isNotBlank()

    fun initialize(context: Context) {
        if (!isConfigured || initialized || initializing) return

        val applicationContext = context.applicationContext
        appContext = applicationContext
        initializing = true

        runCatching {
            val initConfig = AppLovinSdkInitializationConfiguration
                .builder(BuildConfig.APPLOVIN_SDK_KEY)
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .setAdUnitIds(
                    listOf(
                        BuildConfig.APPLOVIN_BANNER_AD_UNIT_ID,
                        BuildConfig.APPLOVIN_INTERSTITIAL_AD_UNIT_ID
                    )
                )
                .build()

            AppLovinSdk.getInstance(applicationContext).initialize(initConfig) {
                initialized = true
                initializedState.value = true
                initializing = false
                preloadInterstitial()
            }
        }.onFailure {
            initialized = false
            initializedState.value = false
            initializing = false
        }
    }

    fun createBanner(context: Context): MaxAdView? {
        if (!initialized || !isConfigured) return null
        return runCatching {
            MaxAdView(BuildConfig.APPLOVIN_BANNER_AD_UNIT_ID, context).apply {
                setPlacement("downloader_bottom")
                loadAd()
            }
        }.getOrNull()
    }

    fun showInterstitialIfEligible(
        activity: Activity,
        operationSucceeded: Boolean,
        downloadRunning: Boolean
    ): Boolean {
        if (!initialized || !isConfigured) return false

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastDisplayed = prefs.getLong(KEY_LAST_INTERSTITIAL_MS, 0L)
        val now = System.currentTimeMillis()

        if (!policy.canShow(now, lastDisplayed, operationSucceeded, downloadRunning)) return false

        val ad = interstitial ?: return false
        if (!ad.isReady) {
            preloadInterstitial()
            return false
        }

        return runCatching {
            ad.showAd(activity)
            true
        }.getOrDefault(false)
    }

    private fun preloadInterstitial() {
        if (!initialized || !isConfigured) return

        runCatching {
            if (interstitial == null) {
                interstitial = MaxInterstitialAd(BuildConfig.APPLOVIN_INTERSTITIAL_AD_UNIT_ID).also {
                    it.setListener(this)
                }
            }
            interstitial?.loadAd()
        }
    }

    override fun onAdLoaded(ad: MaxAd) = Unit

    override fun onAdDisplayed(ad: MaxAd) {
        appContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putLong(KEY_LAST_INTERSTITIAL_MS, System.currentTimeMillis())
            ?.apply()
    }

    override fun onAdHidden(ad: MaxAd) {
        preloadInterstitial()
    }

    override fun onAdClicked(ad: MaxAd) = Unit

    override fun onAdLoadFailed(adUnitId: String, error: MaxError) = Unit

    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
        preloadInterstitial()
    }
}
