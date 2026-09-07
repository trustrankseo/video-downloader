package com.faisal.freshdownloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        context.startActivity(Intent(context, PublicProfileScanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(PublicProfileScanActivity.EXTRA_REQUEST_ID, requestId)
            putExtra(PublicProfileScanActivity.EXTRA_URL, inputUrl)
            putExtra(PublicProfileScanActivity.EXTRA_PLATFORM, platform.lowercase())
        })
        val result = withTimeoutOrNull(300_000) { deferred.await() }.orEmpty()
        pending.remove(requestId)
        return result
    }

    fun cancelActive() {
        pending.values.forEach { if (!it.isCompleted) it.complete(emptyList()) }
        pending.clear()
        Handler(Looper.getMainLooper()).post { activeActivity?.get()?.cancelFromEngine() }
    }

    internal fun attach(activity: PublicProfileScanActivity) { activeActivity = WeakReference(activity) }
    internal fun detach(activity: PublicProfileScanActivity) {
        if (activeActivity?.get() === activity) activeActivity = null
    }
    internal fun complete(requestId: String?, urls: List<String>) {
        if (!requestId.isNullOrBlank()) pending.remove(requestId)?.complete(urls.distinct().take(300))
    }
}

class PublicProfileScanActivity : Activity() {
    companion object {
        const val EXTRA_REQUEST_ID = "profile_scan_request_id"
        const val EXTRA_URL = "profile_scan_url"
        const val EXTRA_PLATFORM = "profile_scan_platform"
        private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var signInButton: Button
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
    private var returningFromLogin = false
    private var loginCompletedInThisActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PublicProfileBrowserScanner.attach(this)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        platform = intent.getStringExtra(EXTRA_PLATFORM).orEmpty().lowercase()
        targetUrl = normalizeUrl(intent.getStringExtra(EXTRA_URL).orEmpty())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 16, 30))
            setPadding(28, 30, 28, 20)
        }
        root.addView(TextView(this).apply {
            text = "Universal Downloader • ${platformName()}"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        statusView = TextView(this).apply {
            text = if (hasAuthenticatedSession()) "Saved ${platformName()} login found. Opening profile…" else "Opening profile… Sign in once if needed."
            textSize = 15f
            setTextColor(Color.rgb(145, 161, 185))
            setPadding(0, 10, 0, 12)
        }
        root.addView(statusView)
        root.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8))

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.settings.userAgentString = if (platform == "tiktok") DESKTOP_UA else MOBILE_UA
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = platform == "tiktok"
        webView.setBackgroundColor(Color.rgb(13, 23, 40))
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
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

                if (isLoginUrl(current)) {
                    returningFromLogin = true
                    statusView.text = "Sign in on the official ${platformName()} page below. After login, the requested profile will reopen automatically."
                    return
                }

                if (returningFromLogin && isPlatformHost(current)) {
                    // Some WebView builds do not expose HttpOnly auth cookies through getCookie(),
                    // so a successful navigation away from the login route is also accepted.
                    returningFromLogin = false
                    loginCompletedInThisActivity = true
                    statusView.text = "Sign-in completed. Reopening requested profile…"
                    handler.postDelayed({ if (!finished) webView.loadUrl(targetUrl) }, 650)
                    return
                }

                if (!isTargetProfile(current)) {
                    if (hasAuthenticatedSession() || loginCompletedInThisActivity) {
                        statusView.text = "Login session active. Opening requested profile…"
                        handler.postDelayed({ if (!finished) webView.loadUrl(targetUrl) }, 500)
                    } else {
                        statusView.text = "Tap SIGN IN once, or SCAN PROFILE to try the visible page."
                    }
                    return
                }

                generation++
                scanAttempt = 0
                noGrowthRounds = 0
                lastFoundCount = found.size
                statusView.text = if (hasAuthenticatedSession() || loginCompletedInThisActivity) "Signed in • scanning profile…" else "Scanning visible profile…"
                handler.postDelayed({ scanDom(generation) }, 800)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        signInButton = Button(this).apply {
            text = "SIGN IN TO ${platformName().uppercase()}"
            visibility = View.VISIBLE
            setOnClickListener {
                if (finished) return@setOnClickListener
                returningFromLogin = true
                found.clear()
                statusView.text = "Opening official ${platformName()} sign-in…"
                CookieManager.getInstance().flush()
                webView.loadUrl(loginUrl())
            }
        }
        root.addView(signInButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 })

        scanButton = Button(this).apply {
            text = "SCAN PROFILE"
            visibility = View.VISIBLE
            setOnClickListener {
                if (finished) return@setOnClickListener
                found.clear()
                returningFromLogin = false
                statusView.text = if (hasAuthenticatedSession() || loginCompletedInThisActivity) "Using saved login session…" else "Scanning current session…"
                CookieManager.getInstance().flush()
                webView.loadUrl(targetUrl)
            }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })

        root.addView(Button(this).apply {
            text = "CANCEL"
            setOnClickListener { completeAndFinish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })

        root.addView(TextView(this).apply {
            text = "Your password is entered only on the official ${platformName()} webpage. Universal Downloader keeps that website session on this device, so normally you sign in only once until the platform expires the session."
            textSize = 12f
            setTextColor(Color.rgb(85, 221, 247))
            setPadding(0, 10, 0, 0)
        })

        setContentView(root)
        webView.loadUrl(targetUrl)
    }

    private fun scanDom(expectedGeneration: Int) {
        if (finished || expectedGeneration != generation) return
        val p = if (platform == "instagram") "instagram" else "tiktok"
        val script = """
            (function() {
              try {
                const p='$p';
                const href=(location.href||'').toLowerCase();
                const html=(document.documentElement&&document.documentElement.innerHTML)||'';
                let links=Array.from(document.querySelectorAll('a[href]')).map(a=>a.href||'');
                if (p==='instagram') {
                  const rel=html.match(/\/(?:reel|p)\/[A-Za-z0-9_-]+\/?/gi)||[];
                  rel.forEach(x=>links.push('https://www.instagram.com'+x));
                  links=links.filter(h=>/instagram\.com\/(reel|p)\/[A-Za-z0-9_-]+/i.test(h));
                } else {
                  const rel=html.match(/\/@[^\"'<>\\/]+\/video\/\d+/gi)||[];
                  rel.forEach(x=>links.push('https://www.tiktok.com'+x));
                  links=links.filter(h=>/tiktok\.com\/@[^/]+\/video\/\d+/i.test(h));
                }
                const body=(document.body&&document.body.innerText||'').toLowerCase();
                const pw=!!document.querySelector('input[type="password"]');
                const loginRoute=p==='instagram' ? href.includes('/accounts/login') : href.includes('/login');
                const hardLogin=p==='instagram'
                  ? ((body.includes('log in')||body.includes('sign up'))&&body.includes('instagram')&&links.length===0)
                  : ((body.includes('log in to tiktok')||body.includes('sign up for tiktok'))&&links.length===0);
                return JSON.stringify({links:Array.from(new Set(links)),login:(loginRoute||pw||hardLogin)});
              } catch(e) { return JSON.stringify({links:[],login:false}); }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            if (finished || expectedGeneration != generation) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            val obj = runCatching { JSONObject(decoded) }.getOrNull()
            val links = obj?.optJSONArray("links")
            if (links != null) for (i in 0 until links.length()) {
                val link = links.optString(i).trim()
                if (isValidVideoLink(link)) found += link.substringBefore('#').substringBefore('?')
            }

            val authenticated = hasAuthenticatedSession() || loginCompletedInThisActivity
            if (obj?.optBoolean("login", false) == true && !authenticated) {
                statusView.text = "${platformName()} requires sign-in for this profile. Tap SIGN IN above and log in once."
                return@evaluateJavascript
            }

            scanAttempt++
            if (found.size == lastFoundCount) noGrowthRounds++ else noGrowthRounds = 0
            lastFoundCount = found.size
            statusView.text = if (found.isEmpty()) {
                if (authenticated) "Signed in • scanning… $scanAttempt/14" else "Scanning… $scanAttempt/14"
            } else {
                "Found ${found.size} videos…"
            }

            if (scanAttempt < 14 && found.size < 300 && noGrowthRounds < 5) {
                webView.evaluateJavascript("window.scrollBy(0, Math.max(window.innerHeight*2.7,1900));", null)
                handler.postDelayed({ scanDom(expectedGeneration) }, 1000)
            } else {
                if (found.isEmpty() && authenticated) {
                    statusView.text = "Signed in, but no video links were visible on this profile. You can scroll the profile once and tap SCAN PROFILE again."
                    scanButton.visibility = View.VISIBLE
                } else {
                    completeAndFinish()
                }
            }
        }
    }

    private fun hasAuthenticatedSession(): Boolean {
        val cm = CookieManager.getInstance()
        val cookies = when (platform) {
            "instagram" -> listOf(
                cm.getCookie("https://www.instagram.com/").orEmpty(),
                cm.getCookie("https://instagram.com/").orEmpty()
            ).joinToString(";")
            "tiktok" -> listOf(
                cm.getCookie("https://www.tiktok.com/").orEmpty(),
                cm.getCookie("https://tiktok.com/").orEmpty()
            ).joinToString(";")
            else -> ""
        }.lowercase()
        return when (platform) {
            "instagram" -> "sessionid=" in cookies || "ds_user_id=" in cookies
            "tiktok" -> "sessionid=" in cookies || "sessionid_ss=" in cookies || "sid_tt=" in cookies || "sid_guard=" in cookies
            else -> false
        }
    }

    private fun loginUrl(): String = if (platform == "instagram") {
        "https://www.instagram.com/accounts/login/"
    } else {
        "https://www.tiktok.com/login/phone-or-email/email"
    }

    private fun isLoginUrl(raw: String): Boolean {
        val v = raw.lowercase()
        return if (platform == "instagram") "instagram.com/accounts/login" in v else "tiktok.com/login" in v
    }

    private fun isPlatformHost(raw: String): Boolean = runCatching {
        val h = URL(raw).host.removePrefix("www.").lowercase()
        if (platform == "instagram") h == "instagram.com" || h.endsWith(".instagram.com") else h == "tiktok.com" || h.endsWith(".tiktok.com")
    }.getOrDefault(false)

    private fun isTargetProfile(raw: String): Boolean = runCatching {
        val target = URL(targetUrl)
        val current = URL(raw)
        val th = target.host.removePrefix("www.").lowercase()
        val ch = current.host.removePrefix("www.").lowercase()
        if (ch != th && !ch.endsWith(".$th")) return@runCatching false
        val tp = target.path.trimEnd('/').lowercase()
        val cp = current.path.trimEnd('/').lowercase()
        cp == tp || cp.startsWith("$tp/")
    }.getOrDefault(false)

    private fun isValidVideoLink(raw: String): Boolean = when (platform) {
        "instagram" -> Regex("https?://(?:www\\.)?instagram\\.com/(reel|p)/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE).containsMatchIn(raw)
        "tiktok" -> Regex("https?://(?:www\\.)?tiktok\\.com/@[^/]+/video/\\d+", RegexOption.IGNORE_CASE).containsMatchIn(raw)
        else -> false
    }

    private fun platformName() = if (platform == "instagram") "Instagram" else "TikTok"
    private fun isHttpUrl(raw: String) = raw.startsWith("https://", true) || raw.startsWith("http://", true)
    private fun normalizeUrl(raw: String): String {
        val v = raw.trim()
        return when {
            v.startsWith("https://", true) || v.startsWith("http://", true) -> v
            v.startsWith("://") -> "https$v"
            v.startsWith("//") -> "https:$v"
            v.startsWith("www.") || v.startsWith("instagram.com", true) || v.startsWith("tiktok.com", true) -> "https://$v"
            else -> v
        }
    }

    internal fun cancelFromEngine() = completeAndFinish()

    private fun completeAndFinish() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().flush()
        PublicProfileBrowserScanner.complete(requestId, found.toList())
        if (::webView.isInitialized) {
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
        finish()
    }

    override fun onBackPressed() { completeAndFinish() }
    override fun onDestroy() {
        PublicProfileBrowserScanner.detach(this)
        if (!finished) PublicProfileBrowserScanner.complete(requestId, found.toList())
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) runCatching { webView.destroy() }
        super.onDestroy()
    }
}
