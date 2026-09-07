package com.faisal.freshdownloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.ref.WeakReference
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PublicProfileBrowserScanner {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<List<String>>>()
    @Volatile private var activeActivity: WeakReference<PublicProfileScanActivity>? = null

    suspend fun scan(context: Context, inputUrl: String, platform: String): List<String> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<List<String>>()
        pending[requestId] = deferred

        val intent = Intent(context, PublicProfileScanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(PublicProfileScanActivity.EXTRA_REQUEST_ID, requestId)
            putExtra(PublicProfileScanActivity.EXTRA_URL, inputUrl)
            putExtra(PublicProfileScanActivity.EXTRA_PLATFORM, platform.lowercase())
        }
        context.startActivity(intent)

        val result = withTimeoutOrNull(180_000) { deferred.await() }.orEmpty()
        pending.remove(requestId)
        return result
    }

    fun cancelActive() {
        pending.values.forEach { deferred ->
            if (!deferred.isCompleted) deferred.complete(emptyList())
        }
        pending.clear()
        Handler(Looper.getMainLooper()).post {
            activeActivity?.get()?.cancelFromEngine()
        }
    }

    internal fun attach(activity: PublicProfileScanActivity) {
        activeActivity = WeakReference(activity)
    }

    internal fun detach(activity: PublicProfileScanActivity) {
        if (activeActivity?.get() === activity) activeActivity = null
    }

    internal fun complete(requestId: String?, urls: List<String>) {
        if (requestId.isNullOrBlank()) return
        pending.remove(requestId)?.complete(urls.distinct().take(300))
    }
}

class PublicProfileScanActivity : Activity() {
    companion object {
        const val EXTRA_REQUEST_ID = "profile_scan_request_id"
        const val EXTRA_URL = "profile_scan_url"
        const val EXTRA_PLATFORM = "profile_scan_platform"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var scanButton: Button
    private val found = linkedSetOf<String>()
    private var requestId: String? = null
    private var platform = ""
    private var targetUrl = ""
    private var finished = false
    private var generation = 0
    private var scanAttempt = 0
    private var noGrowthRounds = 0
    private var lastFoundCount = 0
    private var loginPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PublicProfileBrowserScanner.attach(this)

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        platform = intent.getStringExtra(EXTRA_PLATFORM).orEmpty().lowercase()
        targetUrl = normalizeUrl(intent.getStringExtra(EXTRA_URL).orEmpty())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 16, 30))
            setPadding(28, 32, 28, 20)
        }

        val title = TextView(this).apply {
            text = "Universal Downloader • ${platformName()}"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            text = "Opening ${platformName()} profile…"
            textSize = 15f
            setTextColor(Color.rgb(145, 161, 185))
            setPadding(0, 10, 0, 16)
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val progress = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = 12
        })

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            setBackgroundColor(Color.rgb(13, 23, 40))

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val scheme = request?.url?.scheme?.lowercase().orEmpty()
                    return scheme.isNotBlank() && scheme != "http" && scheme != "https"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (finished) return
                    val current = url.orEmpty()
                    if (!isHttpUrl(current)) return

                    CookieManager.getInstance().flush()
                    generation++
                    scanAttempt = 0
                    noGrowthRounds = 0
                    lastFoundCount = found.size

                    if (isLoginUrl(current)) {
                        showLoginRequired()
                        handler.postDelayed({ scanDom(generation) }, 450)
                        return
                    }

                    if (loginPrompted && !isTargetProfile(current)) {
                        statusView.text = "Sign-in complete? Tap SCAN PROFILE below."
                        scanButton.visibility = View.VISIBLE
                        return
                    }

                    loginPrompted = false
                    scanButton.visibility = View.GONE
                    statusView.text = "Scanning ${platformName()} profile…"
                    handler.postDelayed({ scanDom(generation) }, 650)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        scanButton = Button(this).apply {
            text = "I'M SIGNED IN — SCAN PROFILE"
            visibility = View.GONE
            setOnClickListener {
                if (finished) return@setOnClickListener
                loginPrompted = false
                found.clear()
                statusView.text = "Loading profile with your ${platformName()} session…"
                visibility = View.GONE
                CookieManager.getInstance().flush()
                webView.loadUrl(targetUrl)
            }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 12
        })

        val cancelButton = Button(this).apply {
            text = "CANCEL"
            setOnClickListener { completeAndFinish() }
        }
        root.addView(cancelButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 8
        })

        val note = TextView(this).apply {
            text = "Sign-in happens on the official ${platformName()} webpage. Universal Downloader does not read or store your password. The browser session can remain signed in until the platform expires it."
            textSize = 12f
            setTextColor(Color.rgb(85, 221, 247))
            setPadding(0, 12, 0, 0)
        }
        root.addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        webView.loadUrl(targetUrl)
    }

    private fun scanDom(expectedGeneration: Int) {
        if (finished || expectedGeneration != generation) return
        val platformLiteral = if (platform == "instagram") "instagram" else "tiktok"
        val script = """
            (function() {
              try {
                const p = '$platformLiteral';
                const href = (location.href || '').toLowerCase();
                const links = Array.from(document.querySelectorAll('a[href]'))
                  .map(a => a.href || '')
                  .filter(h => p === 'instagram'
                    ? /instagram\.com\/(reel|p)\/[A-Za-z0-9_-]+/i.test(h)
                    : /tiktok\.com\/@[^/]+\/video\/\d+/i.test(h));
                const hasPassword = !!document.querySelector('input[type="password"]');
                const dialog = document.querySelector('[role="dialog"]');
                const dialogText = ((dialog && dialog.innerText) || '').toLowerCase();
                const loginByUrl = p === 'instagram'
                  ? href.includes('/accounts/login')
                  : href.includes('/login');
                const loginByDialog = p === 'instagram'
                  ? (dialogText.includes('log in') || dialogText.includes('sign up'))
                  : (dialogText.includes('log in to tiktok') || dialogText.includes('sign up for tiktok'));
                return JSON.stringify({links:Array.from(new Set(links)), login:(loginByUrl || hasPassword || loginByDialog)});
              } catch (e) {
                return JSON.stringify({links:[], login:false});
              }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            if (finished || expectedGeneration != generation) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            val obj = runCatching { JSONObject(decoded) }.getOrNull()
            val links = obj?.optJSONArray("links")
            if (links != null) {
                for (i in 0 until links.length()) {
                    val link = links.optString(i).trim()
                    if (isValidVideoLink(link)) found += cleanVideoLink(link)
                }
            }

            if (obj?.optBoolean("login", false) == true) {
                showLoginRequired()
                return@evaluateJavascript
            }

            if (loginPrompted && !isTargetProfile(webView.url.orEmpty())) {
                statusView.text = "Sign-in complete? Tap SCAN PROFILE below."
                scanButton.visibility = View.VISIBLE
                return@evaluateJavascript
            }

            loginPrompted = false
            scanButton.visibility = View.GONE
            scanAttempt++
            if (found.size == lastFoundCount) noGrowthRounds++ else noGrowthRounds = 0
            lastFoundCount = found.size

            statusView.text = if (found.isEmpty()) {
                "Scanning ${platformName()} profile… $scanAttempt/10"
            } else {
                "Found ${found.size} videos…"
            }

            if (scanAttempt < 10 && found.size < 300 && noGrowthRounds < 3) {
                webView.evaluateJavascript("window.scrollBy(0, Math.max(window.innerHeight * 2.3, 1500));", null)
                handler.postDelayed({ scanDom(expectedGeneration) }, 900)
            } else {
                completeAndFinish()
            }
        }
    }

    private fun showLoginRequired() {
        if (finished) return
        loginPrompted = true
        statusView.text = "${platformName()} requires sign-in for this profile. Sign in on the official page below, then tap SCAN PROFILE."
        scanButton.visibility = View.VISIBLE
    }

    private fun isLoginUrl(raw: String): Boolean {
        val value = raw.lowercase()
        return when (platform) {
            "instagram" -> "instagram.com/accounts/login" in value
            "tiktok" -> "tiktok.com/login" in value
            else -> false
        }
    }

    private fun isTargetProfile(raw: String): Boolean {
        return runCatching {
            val target = URL(targetUrl)
            val current = URL(raw)
            val targetHost = target.host.removePrefix("www.").lowercase()
            val currentHost = current.host.removePrefix("www.").lowercase()
            if (currentHost != targetHost && !currentHost.endsWith(".$targetHost")) return@runCatching false
            val targetPath = target.path.trimEnd('/').lowercase()
            val currentPath = current.path.trimEnd('/').lowercase()
            currentPath == targetPath || currentPath.startsWith("$targetPath/")
        }.getOrDefault(false)
    }

    private fun isValidVideoLink(raw: String): Boolean {
        val value = raw.lowercase()
        return when (platform) {
            "instagram" -> Regex("https?://(?:www\\.)?instagram\\.com/(reel|p)/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE).containsMatchIn(value)
            "tiktok" -> Regex("https?://(?:www\\.)?tiktok\\.com/@[^/]+/video/\\d+", RegexOption.IGNORE_CASE).containsMatchIn(value)
            else -> false
        }
    }

    private fun cleanVideoLink(raw: String): String = raw.substringBefore('#')

    private fun platformName(): String = when (platform) {
        "instagram" -> "Instagram"
        "tiktok" -> "TikTok"
        else -> "profile"
    }

    private fun isHttpUrl(raw: String): Boolean {
        val value = raw.lowercase()
        return value.startsWith("https://") || value.startsWith("http://")
    }

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.startsWith("://") -> "https$value"
            value.startsWith("//") -> "https:$value"
            value.startsWith("www.") || value.startsWith("instagram.com", true) || value.startsWith("tiktok.com", true) -> "https://$value"
            else -> value
        }
    }

    internal fun cancelFromEngine() {
        completeAndFinish()
    }

    private fun completeAndFinish() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        PublicProfileBrowserScanner.complete(requestId, found.toList())
        if (::webView.isInitialized) {
            runCatching { CookieManager.getInstance().flush() }
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
        finish()
    }

    override fun onBackPressed() {
        completeAndFinish()
    }

    override fun onDestroy() {
        PublicProfileBrowserScanner.detach(this)
        if (!finished) PublicProfileBrowserScanner.complete(requestId, found.toList())
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) runCatching { webView.destroy() }
        super.onDestroy()
    }
}
