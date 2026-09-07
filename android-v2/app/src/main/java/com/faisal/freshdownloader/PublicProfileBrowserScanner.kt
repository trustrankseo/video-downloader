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
        val result = withTimeoutOrNull(900_000) { deferred.await() }.orEmpty()
        pending.remove(requestId)
        return result
    }

    fun cancelActive() {
        pending.values.forEach { if (!it.isCompleted) it.complete(emptyList()) }
        pending.clear()
        Handler(Looper.getMainLooper()).post { activeActivity?.get()?.cancelFromEngine() }
    }
    internal fun attach(activity: PublicProfileScanActivity) { activeActivity = WeakReference(activity) }
    internal fun detach(activity: PublicProfileScanActivity) { if (activeActivity?.get() === activity) activeActivity = null }
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
    private lateinit var continueButton: Button
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
    private var loginFlowActive = false
    private var loginCompletedInThisActivity = false
    private var tiktokReadyToFinish = false

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

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = if (platform == "tiktok") DESKTOP_UA else MOBILE_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = platform == "tiktok"
            setBackgroundColor(Color.rgb(13, 23, 40))
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
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
                if (platform == "tiktok") {
                    inspectTikTokPage(current)
                    return
                }
                if (loginFlowActive && isLoginUrl(current)) {
                    statusView.text = "Sign in on the official Instagram page below."
                    return
                }
                if (loginFlowActive && isPlatformHost(current)) {
                    loginFlowActive = false
                    loginCompletedInThisActivity = true
                    statusView.text = "Instagram sign-in completed. Reopening requested profile…"
                    handler.postDelayed({ if (!finished) webView.loadUrl(targetUrl) }, 650)
                    return
                }
                openOrScanProfile(current)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(Button(this).apply {
            text = "SIGN IN TO ${platformName().uppercase()}"
            setOnClickListener {
                if (finished) return@setOnClickListener
                generation++
                loginFlowActive = true
                tiktokReadyToFinish = false
                found.clear()
                continueButton.text = "CONTINUE TO PROFILE"
                continueButton.visibility = if (platform == "tiktok") View.VISIBLE else View.GONE
                statusView.text = if (platform == "tiktok") "Complete TikTok login and CAPTCHA below. Nothing will auto-close; only you can continue." else "Opening official Instagram sign-in…"
                webView.loadUrl(loginUrl())
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 })

        continueButton = Button(this).apply {
            text = "CONTINUE TO PROFILE"
            visibility = View.GONE
            setOnClickListener {
                if (finished) return@setOnClickListener
                if (platform == "tiktok" && tiktokReadyToFinish) {
                    completeAndFinish()
                    return@setOnClickListener
                }
                CookieManager.getInstance().flush()
                generation++
                loginFlowActive = false
                loginCompletedInThisActivity = true
                tiktokReadyToFinish = false
                text = "CONTINUE TO PROFILE"
                visibility = View.GONE
                statusView.text = "Using your TikTok session. Opening requested profile…"
                webView.loadUrl(targetUrl)
            }
        }
        root.addView(continueButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })

        scanButton = Button(this).apply {
            text = "SCAN PROFILE"
            setOnClickListener {
                if (finished) return@setOnClickListener
                generation++
                loginFlowActive = false
                tiktokReadyToFinish = false
                continueButton.text = "CONTINUE TO PROFILE"
                continueButton.visibility = View.GONE
                found.clear()
                webView.loadUrl(targetUrl)
            }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })
        root.addView(Button(this).apply { text = "CANCEL"; setOnClickListener { completeAndFinish() } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 })
        root.addView(TextView(this).apply {
            text = if (platform == "tiktok") "TikTok CAPTCHA is manual. This browser will never auto-finish a TikTok scan; after verification and scanning, tap USE FOUND VIDEOS yourself." else "Your password is entered only on Instagram's official webpage."
            textSize = 12f
            setTextColor(Color.rgb(85, 221, 247))
            setPadding(0, 10, 0, 0)
        })
        setContentView(root)
        webView.loadUrl(targetUrl)
    }

    private fun inspectTikTokPage(current: String) {
        val script = """
            (function(){try{
              const href=(location.href||'').toLowerCase();
              const body=(document.body&&document.body.innerText||'').toLowerCase();
              const node=!!document.querySelector('iframe[src*="captcha" i],iframe[src*="verify" i],[id*="captcha" i],[class*="captcha" i],[id*="verify" i],[class*="verify" i],[data-e2e*="captcha" i],[class*="secsdk" i]');
              const text=body.includes('verify to continue')||body.includes('security verification')||body.includes('complete the puzzle')||body.includes('drag the slider')||body.includes('captcha')||body.includes('puzzle');
              const route=href.includes('/login')||href.includes('/challenge')||href.includes('/verify')||href.includes('/verification')||href.includes('captcha');
              return JSON.stringify({challenge:(node||text||route),login:href.includes('/login')});
            }catch(e){return JSON.stringify({challenge:true,login:true});}})();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            if (finished) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            val obj = runCatching { JSONObject(decoded) }.getOrNull()
            val challenge = obj?.optBoolean("challenge", true) != false
            val login = obj?.optBoolean("login", true) != false
            if (challenge || login || loginFlowActive) {
                generation++
                loginFlowActive = true
                tiktokReadyToFinish = false
                continueButton.text = "CONTINUE TO PROFILE"
                continueButton.visibility = View.VISIBLE
                statusView.text = "TikTok login/CAPTCHA is active. Finish it completely. This screen cannot auto-close; then tap CONTINUE TO PROFILE."
                return@evaluateJavascript
            }
            openOrScanProfile(current)
        }
    }

    private fun openOrScanProfile(current: String) {
        if (!isTargetProfile(current)) {
            if (hasAuthenticatedSession() || loginCompletedInThisActivity) {
                statusView.text = "Login session active. Opening requested profile…"
                handler.postDelayed({ if (!finished && !loginFlowActive) webView.loadUrl(targetUrl) }, 500)
            } else statusView.text = "Tap SIGN IN once, or SCAN PROFILE to try the visible page."
            return
        }
        generation++
        scanAttempt = 0
        noGrowthRounds = 0
        lastFoundCount = found.size
        statusView.text = if (hasAuthenticatedSession() || loginCompletedInThisActivity) "Signed in • scanning profile…" else "Scanning visible profile…"
        val g = generation
        handler.postDelayed({ scanDom(g) }, 850)
    }

    private fun scanDom(expectedGeneration: Int) {
        if (finished || expectedGeneration != generation || loginFlowActive) return
        val p = if (platform == "instagram") "instagram" else "tiktok"
        val script = """
            (function(){try{
              const p='$p',href=(location.href||'').toLowerCase(),html=(document.documentElement&&document.documentElement.innerHTML)||'',body=(document.body&&document.body.innerText||'').toLowerCase();
              let links=Array.from(document.querySelectorAll('a[href]')).map(a=>a.href||'');
              if(p==='instagram'){(html.match(/\/(?:reel|p)\/[A-Za-z0-9_-]+\/?/gi)||[]).forEach(x=>links.push('https://www.instagram.com'+x));links=links.filter(h=>/instagram\.com\/(reel|p)\/[A-Za-z0-9_-]+/i.test(h));}
              else{(html.match(/\/@[^\"'<>\\/]+\/video\/\d+/gi)||[]).forEach(x=>links.push('https://www.tiktok.com'+x));links=links.filter(h=>/tiktok\.com\/@[^/]+\/video\/\d+/i.test(h));}
              const pw=!!document.querySelector('input[type="password"]');
              const login=p==='instagram'?href.includes('/accounts/login'):href.includes('/login');
              const captcha=p==='tiktok'&&(href.includes('/challenge')||href.includes('/verify')||href.includes('captcha')||body.includes('verify to continue')||body.includes('security verification')||body.includes('complete the puzzle')||body.includes('drag the slider')||body.includes('puzzle')||!!document.querySelector('iframe[src*="captcha" i],[id*="captcha" i],[class*="captcha" i],[class*="secsdk" i]'));
              return JSON.stringify({links:Array.from(new Set(links)),login:(login||pw),captcha:captcha});
            }catch(e){return JSON.stringify({links:[],login:false,captcha:true});}})();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            if (finished || expectedGeneration != generation || loginFlowActive) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            val obj = runCatching { JSONObject(decoded) }.getOrNull()
            if (platform == "tiktok" && obj?.optBoolean("captcha", true) != false) {
                generation++
                loginFlowActive = true
                tiktokReadyToFinish = false
                continueButton.text = "CONTINUE TO PROFILE"
                continueButton.visibility = View.VISIBLE
                statusView.text = "TikTok CAPTCHA detected. Scanning is paused. Finish CAPTCHA, then tap CONTINUE TO PROFILE."
                return@evaluateJavascript
            }
            val links = obj?.optJSONArray("links")
            if (links != null) for (i in 0 until links.length()) {
                val link = links.optString(i).trim()
                if (isValidVideoLink(link)) found += link.substringBefore('#').substringBefore('?')
            }
            val authenticated = hasAuthenticatedSession() || loginCompletedInThisActivity
            if (obj?.optBoolean("login", false) == true && !authenticated) {
                statusView.text = "${platformName()} requires sign-in for this profile."
                return@evaluateJavascript
            }
            scanAttempt++
            if (found.size == lastFoundCount) noGrowthRounds++ else noGrowthRounds = 0
            lastFoundCount = found.size
            statusView.text = if (found.isEmpty()) "Scanning… $scanAttempt/14" else "Found ${found.size} videos…"
            if (scanAttempt < 14 && found.size < 300 && noGrowthRounds < 5) {
                webView.evaluateJavascript("window.scrollBy(0,Math.max(window.innerHeight*2.7,1900));", null)
                handler.postDelayed({ scanDom(expectedGeneration) }, 1000)
            } else if (found.isEmpty() && authenticated) {
                statusView.text = "Signed in, but no video links were visible yet. Scroll once and tap SCAN PROFILE again."
            } else if (platform == "tiktok") {
                // Critical: TikTok never auto-closes. CAPTCHA overlays can appear after feed links
                // are already visible, so only an explicit user tap may return discovered videos.
                tiktokReadyToFinish = true
                continueButton.text = "USE ${found.size} FOUND VIDEOS"
                continueButton.visibility = View.VISIBLE
                statusView.text = "Found ${found.size} videos. If TikTok shows verification, finish it first. When ready, tap USE FOUND VIDEOS."
            } else {
                completeAndFinish()
            }
        }
    }

    private fun hasAuthenticatedSession(): Boolean {
        val cm = CookieManager.getInstance()
        val cookies = if (platform == "instagram") listOf(cm.getCookie("https://www.instagram.com/").orEmpty(), cm.getCookie("https://instagram.com/").orEmpty()).joinToString(";") else listOf(cm.getCookie("https://www.tiktok.com/").orEmpty(), cm.getCookie("https://tiktok.com/").orEmpty()).joinToString(";")
        val c = cookies.lowercase()
        return if (platform == "instagram") "sessionid=" in c || "ds_user_id=" in c else "sessionid=" in c || "sessionid_ss=" in c || "sid_tt=" in c || "sid_guard=" in c
    }
    private fun loginUrl() = if (platform == "instagram") "https://www.instagram.com/accounts/login/" else "https://www.tiktok.com/login/phone-or-email/email"
    private fun isLoginUrl(raw: String) = if (platform == "instagram") "instagram.com/accounts/login" in raw.lowercase() else "tiktok.com/login" in raw.lowercase()
    private fun isPlatformHost(raw: String): Boolean = runCatching { val h=URL(raw).host.removePrefix("www.").lowercase(); if(platform=="instagram") h=="instagram.com"||h.endsWith(".instagram.com") else h=="tiktok.com"||h.endsWith(".tiktok.com") }.getOrDefault(false)
    private fun isTargetProfile(raw: String): Boolean = runCatching { val t=URL(targetUrl);val c=URL(raw);val th=t.host.removePrefix("www.").lowercase();val ch=c.host.removePrefix("www.").lowercase();if(ch!=th&&!ch.endsWith(".$th")) return@runCatching false;val tp=t.path.trimEnd('/').lowercase();val cp=c.path.trimEnd('/').lowercase();cp==tp||cp.startsWith("$tp/") }.getOrDefault(false)
    private fun isValidVideoLink(raw: String)=if(platform=="instagram") Regex("https?://(?:www\\.)?instagram\\.com/(reel|p)/[A-Za-z0-9_-]+",RegexOption.IGNORE_CASE).containsMatchIn(raw) else Regex("https?://(?:www\\.)?tiktok\\.com/@[^/]+/video/\\d+",RegexOption.IGNORE_CASE).containsMatchIn(raw)
    private fun platformName()=if(platform=="instagram") "Instagram" else "TikTok"
    private fun isHttpUrl(raw:String)=raw.startsWith("https://",true)||raw.startsWith("http://",true)
    private fun normalizeUrl(raw:String):String{val v=raw.trim();return when{v.startsWith("https://",true)||v.startsWith("http://",true)->v;v.startsWith("://")->"https$v";v.startsWith("//")->"https:$v";v.startsWith("www.")||v.startsWith("instagram.com",true)||v.startsWith("tiktok.com",true)->"https://$v";else->v}}

    internal fun cancelFromEngine()=completeAndFinish()
    private fun completeAndFinish(){if(finished)return;finished=true;handler.removeCallbacksAndMessages(null);CookieManager.getInstance().flush();PublicProfileBrowserScanner.complete(requestId,found.toList());if(::webView.isInitialized){runCatching{webView.stopLoading()};runCatching{webView.destroy()}};finish()}
    override fun onBackPressed(){completeAndFinish()}
    override fun onDestroy(){PublicProfileBrowserScanner.detach(this);if(!finished)PublicProfileBrowserScanner.complete(requestId,found.toList());handler.removeCallbacksAndMessages(null);if(::webView.isInitialized)runCatching{webView.destroy()};super.onDestroy()}
}
