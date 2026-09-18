package com.faisal.freshdownloader

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinPrivacySettings
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import java.lang.ref.WeakReference

object AdsManager {
    private const val PREFS = "universal_downloader_ads"
    private const val KEY_LAST_INTERSTITIAL_DISPLAY_MS = "last_interstitial_display_ms"
    private const val INTERSTITIAL_PLACEMENT = "download_complete"
    private const val BANNER_PLACEMENT = "downloader_bottom"

    val initialized = mutableStateOf(false)

    private val policy = AdDisplayPolicy(90_000L)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var initializationStarted = false
    private var retryAttempt = 0
    private var interstitialAd: MaxInterstitialAd? = null
    private var interstitialActivity = WeakReference<Activity>(null)
    private var pendingActivity = WeakReference<Activity>(null)

    @Synchronized
    fun initialize(context: Context) {
        if (initialized.value || initializationStarted || !AdsConfig.sdkConfigured) return

        initializationStarted = true
        val appContext = context.applicationContext

        runCatching {
            // The app's own consent gate is completed before this method is called.
            // Interest-based ads are disabled by default until a dedicated ad-consent
            // preference is added; this keeps the first ads build conservative.
            AppLovinPrivacySettings.setHasUserConsent(false, appContext)
            AppLovinPrivacySettings.setDoNotSell(true, appContext)

            val initConfig = AppLovinSdkInitializationConfiguration
                .builder(AdsConfig.sdkKey, appContext)
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .build()

            AppLovinSdk.getInstance(appContext).initialize(initConfig) {
                initializationStarted = false
                initialized.value = true
                pendingActivity.get()?.let { preloadInterstitial(it) }
            }
        }.onFailure {
            initializationStarted = false
            initialized.value = false
        }
    }

    fun preloadInterstitial(activity: Activity) {
        pendingActivity = WeakReference(activity)
        if (!initialized.value || !AdsConfig.interstitialConfigured) return

        val currentActivity = interstitialActivity.get()
        if (interstitialAd == null || currentActivity !== activity) {
            interstitialAd?.setListener(null)
            retryAttempt = 0

            val ad = MaxInterstitialAd(AdsConfig.interstitialAdUnitId, activity)
            ad.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    retryAttempt = 0
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    retryAttempt++
                    val delaySeconds = 1L shl minOf(6, retryAttempt)
                    mainHandler.postDelayed(
                        { runCatching { interstitialAd?.loadAd() } },
                        delaySeconds * 1_000L
                    )
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    runCatching { interstitialAd?.loadAd() }
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    activity.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_LAST_INTERSTITIAL_DISPLAY_MS, System.currentTimeMillis())
                        .apply()
                }

                override fun onAdClicked(ad: MaxAd) = Unit

                override fun onAdHidden(ad: MaxAd) {
                    runCatching { interstitialAd?.loadAd() }
                }
            })
            interstitialAd = ad
            interstitialActivity = WeakReference(activity)
        }

        runCatching { interstitialAd?.loadAd() }
    }

    fun showInterstitialIfEligible(
        activity: Activity,
        operationSucceeded: Boolean,
        downloadRunning: Boolean
    ): Boolean {
        if (!initialized.value || !AdsConfig.interstitialConfigured) {
            preloadInterstitial(activity)
            return false
        }

        val lastDisplayed = activity.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_INTERSTITIAL_DISPLAY_MS, 0L)

        if (!policy.canShow(
                nowMs = System.currentTimeMillis(),
                lastDisplayedAtMs = lastDisplayed,
                operationSucceeded = operationSucceeded,
                downloadRunning = downloadRunning
            )
        ) {
            return false
        }

        val ad = interstitialAd
        if (ad == null || !ad.isReady) {
            preloadInterstitial(activity)
            return false
        }

        return runCatching {
            ad.showAd(INTERSTITIAL_PLACEMENT)
            true
        }.getOrElse {
            preloadInterstitial(activity)
            false
        }
    }

    fun createBanner(
        context: Context,
        onLoaded: () -> Unit,
        onLoadFailed: () -> Unit
    ): MaxAdView? {
        if (!initialized.value || !AdsConfig.bannerConfigured) return null

        return runCatching {
            MaxAdView(AdsConfig.bannerAdUnitId, context).apply {
                setPlacement(BANNER_PLACEMENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setListener(object : MaxAdViewAdListener {
                    override fun onAdLoaded(ad: MaxAd) = onLoaded()
                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) = onLoadFailed()
                    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) = Unit
                    override fun onAdDisplayed(ad: MaxAd) = Unit
                    override fun onAdClicked(ad: MaxAd) = Unit
                    override fun onAdHidden(ad: MaxAd) = Unit
                    override fun onAdExpanded(ad: MaxAd) = Unit
                    override fun onAdCollapsed(ad: MaxAd) = Unit
                })
                loadAd()
            }
        }.getOrElse {
            onLoadFailed()
            null
        }
    }
}
