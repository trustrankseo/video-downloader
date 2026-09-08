package com.faisal.freshdownloader

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Privacy-safe referral/install record manager.
 * Uses only an app-generated installation id; no IMEI, serial number or hardware fingerprint.
 */
class ReferralManager(context: Context) {
    companion object {
        private const val PREFS = "universal_downloader_referral_v42"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_FIRST_INSTALL_MS = "first_install_ms"
        private const val KEY_INSTALL_SOURCE = "install_source"
        private const val KEY_PENDING_CODE = "pending_referral_code"
        private const val KEY_REFERRAL_ACTIVATED = "referral_activated"
        private const val KEY_SHARES = "organic_share_count"
        private const val KEY_REFERRER_FETCHED = "install_referrer_fetched"
        private const val ELIGIBILITY_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
    }

    data class Snapshot(
        val installId: String,
        val referralCode: String,
        val installSource: String,
        val firstInstallMs: Long,
        val eligibleForReferral: Boolean,
        val pendingReferralCode: String?,
        val referralActivated: Boolean,
        val shareCount: Int,
        val bonusTrials: Int
    )

    sealed class ApplyResult {
        data object Accepted : ApplyResult()
        data object AlreadyUsed : ApplyResult()
        data object OwnCode : ApplyResult()
        data object Invalid : ApplyResult()
        data object NotEligible : ApplyResult()
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_INSTALL_ID)) {
            prefs.edit()
                .putString(KEY_INSTALL_ID, UUID.randomUUID().toString())
                .putLong(KEY_FIRST_INSTALL_MS, System.currentTimeMillis())
                .putString(KEY_INSTALL_SOURCE, "direct_or_unknown")
                .apply()
        }
    }

    fun snapshot(): Snapshot {
        val access = SubscriptionAccess(appContext)
        return Snapshot(
            installId = installId(),
            referralCode = referralCode(),
            installSource = prefs.getString(KEY_INSTALL_SOURCE, "direct_or_unknown").orEmpty(),
            firstInstallMs = prefs.getLong(KEY_FIRST_INSTALL_MS, System.currentTimeMillis()),
            eligibleForReferral = isInstallEligible(),
            pendingReferralCode = prefs.getString(KEY_PENDING_CODE, null),
            referralActivated = prefs.getBoolean(KEY_REFERRAL_ACTIVATED, false),
            shareCount = prefs.getInt(KEY_SHARES, 0).coerceAtLeast(0),
            bonusTrials = access.referralBonusTrials()
        )
    }

    fun referralCode(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((installId() + appContext.packageName).toByteArray())
        val short = digest.take(5).joinToString("") { "%02X".format(it) }
        return "UD$short"
    }

    fun installId(): String = prefs.getString(KEY_INSTALL_ID, "")
        .orEmpty()
        .ifBlank {
            UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_INSTALL_ID, it).apply()
            }
        }

    fun applyReferralCode(raw: String, source: String = "manual"): ApplyResult {
        val code = raw.trim().uppercase()
        if (!Regex("^UD[A-Z0-9]{10}$").matches(code)) return ApplyResult.Invalid
        if (code == referralCode()) return ApplyResult.OwnCode
        if (prefs.getBoolean(KEY_REFERRAL_ACTIVATED, false) || !prefs.getString(KEY_PENDING_CODE, null).isNullOrBlank()) {
            return ApplyResult.AlreadyUsed
        }
        if (!isInstallEligible()) return ApplyResult.NotEligible

        prefs.edit()
            .putString(KEY_PENDING_CODE, code)
            .putString(KEY_INSTALL_SOURCE, source.ifBlank { "referral" })
            .apply()
        return ApplyResult.Accepted
    }

    /** Reward becomes active only after the referred install completes one successful download. */
    fun activatePendingReferralAfterSuccessfulDownload(): Boolean {
        if (prefs.getBoolean(KEY_REFERRAL_ACTIVATED, false)) return false
        val pending = prefs.getString(KEY_PENDING_CODE, null).orEmpty()
        if (pending.isBlank()) return false
        val added = SubscriptionAccess(appContext).addReferralBonusTrial()
        if (!added) return false
        prefs.edit().putBoolean(KEY_REFERRAL_ACTIVATED, true).apply()
        return true
    }

    fun noteOrganicShare() {
        prefs.edit().putInt(KEY_SHARES, prefs.getInt(KEY_SHARES, 0) + 1).apply()
    }

    fun shareText(): String {
        val code = referralCode()
        val referrer = URLEncoder.encode("ref_code=$code&utm_source=referral&utm_medium=organic_share", StandardCharsets.UTF_8.name())
        val playUrl = "https://play.google.com/store/apps/details?id=${appContext.packageName}&referrer=$referrer"
        return "Try Universal Downloader. Use my referral code $code after install to unlock a bonus premium trial after your first successful download.\n$playUrl"
    }

    fun captureReferralUri(uri: Uri?) {
        if (uri == null) return
        val code = uri.getQueryParameter("code") ?: uri.getQueryParameter("ref_code") ?: return
        applyReferralCode(code, "deep_link")
    }

    fun refreshInstallReferrer() {
        if (prefs.getBoolean(KEY_REFERRER_FETCHED, false)) return
        val client = InstallReferrerClient.newBuilder(appContext).build()
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            val raw = client.installReferrer.installReferrer.orEmpty()
                            if (raw.isNotBlank()) {
                                val parsed = Uri.parse("https://referrer.local/?$raw")
                                val source = parsed.getQueryParameter("utm_source") ?: "google_play"
                                prefs.edit().putString(KEY_INSTALL_SOURCE, source).apply()
                                val code = parsed.getQueryParameter("ref_code")
                                    ?: parsed.getQueryParameter("referral_code")
                                if (!code.isNullOrBlank()) applyReferralCode(code, source)
                            } else {
                                prefs.edit().putString(KEY_INSTALL_SOURCE, "google_play").apply()
                            }
                        }
                    } catch (_: Throwable) {
                        // Store/referrer service is optional; direct installs continue normally.
                    } finally {
                        prefs.edit().putBoolean(KEY_REFERRER_FETCHED, true).apply()
                        runCatching { client.endConnection() }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        }
    }

    private fun isInstallEligible(): Boolean {
        if (prefs.getBoolean(KEY_REFERRAL_ACTIVATED, false)) return false
        if (!prefs.getString(KEY_PENDING_CODE, null).isNullOrBlank()) return false
        val first = prefs.getLong(KEY_FIRST_INSTALL_MS, System.currentTimeMillis())
        return System.currentTimeMillis() - first <= ELIGIBILITY_WINDOW_MS
    }
}
