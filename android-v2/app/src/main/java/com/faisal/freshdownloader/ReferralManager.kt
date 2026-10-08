package com.faisal.freshdownloader

import android.content.Context
import android.net.Uri
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
        private const val ELIGIBILITY_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
        private const val UPTODOWN_FALLBACK_TEXT = "Search for Universal Downloader on Uptodown"
    }

    data class Snapshot(
        val installId: String,
        val referralCode: String,
        val installSource: String,
        val firstInstallMs: Long,
        val eligibleForReferral: Boolean,
        val pendingReferralCode: String?,
        val referralActivated: Boolean,
        val shareCount: Int
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
                .putString(KEY_INSTALL_SOURCE, "uptodown_or_direct")
                .apply()
        } else if (prefs.getString(KEY_INSTALL_SOURCE, "") == "google_play") {
            // Older builds used the Play Install Referrer API even though the app
            // is distributed through Uptodown. Do not keep showing Play Store as
            // the source for existing installations.
            prefs.edit().putString(KEY_INSTALL_SOURCE, "uptodown_or_direct").apply()
        }
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            installId = installId(),
            referralCode = referralCode(),
            installSource = prefs.getString(KEY_INSTALL_SOURCE, "uptodown_or_direct").orEmpty(),
            firstInstallMs = prefs.getLong(KEY_FIRST_INSTALL_MS, System.currentTimeMillis()),
            eligibleForReferral = isInstallEligible(),
            pendingReferralCode = prefs.getString(KEY_PENDING_CODE, null),
            referralActivated = prefs.getBoolean(KEY_REFERRAL_ACTIVATED, false),
            shareCount = prefs.getInt(KEY_SHARES, 0).coerceAtLeast(0)
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
        prefs.edit().putBoolean(KEY_REFERRAL_ACTIVATED, true).apply()
        return true
    }

    fun noteOrganicShare() {
        prefs.edit().putInt(KEY_SHARES, prefs.getInt(KEY_SHARES, 0) + 1).apply()
    }

    fun uptodownUrl(): String = BuildConfig.UPTODOWN_APP_URL.trim()

    fun shareText(): String {
        val code = referralCode()
        val url = uptodownUrl()
        val installLine = if (url.isNotBlank()) {
            "Download Universal Downloader from Uptodown: $url"
        } else {
            "$UPTODOWN_FALLBACK_TEXT."
        }

        return buildString {
            appendLine("Try Universal Downloader.")
            appendLine(installLine)
            appendLine("My referral code: $code")
            append("After installing, open Refer & Share and enter the code. The referral activates after the first successful download.")
        }
    }

    fun captureReferralUri(uri: Uri?) {
        if (uri == null) return
        val code = uri.getQueryParameter("code") ?: uri.getQueryParameter("ref_code") ?: return
        applyReferralCode(code, "deep_link")
    }

    /**
     * Uptodown does not use Google's Play Install Referrer service.
     * Referral attribution is handled by the shared referral code/deep link instead.
     */
    fun refreshInstallReferrer() {
        if (prefs.getString(KEY_INSTALL_SOURCE, "").isNullOrBlank()) {
            prefs.edit().putString(KEY_INSTALL_SOURCE, "uptodown_or_direct").apply()
        }
    }

    private fun isInstallEligible(): Boolean {
        if (prefs.getBoolean(KEY_REFERRAL_ACTIVATED, false)) return false
        if (!prefs.getString(KEY_PENDING_CODE, null).isNullOrBlank()) return false
        val first = prefs.getLong(KEY_FIRST_INSTALL_MS, System.currentTimeMillis())
        return System.currentTimeMillis() - first <= ELIGIBILITY_WINDOW_MS
    }
}
