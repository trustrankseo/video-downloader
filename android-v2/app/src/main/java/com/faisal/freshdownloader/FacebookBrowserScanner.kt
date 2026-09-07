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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Last-resort Facebook Page/Profile discovery using a normal Android WebView in guest mode.
 * It does not inject cookies, credentials, tokens, or bypass Facebook authentication.
 * Only reel/video links that Facebook actually renders to a signed-out browser are collected.
 */
object FacebookBrowserScanner {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<List<String>>>()

    suspend fun scan(context: Context, inputUrl: String): List<String> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<List<String>>()
        pending[requestId] = deferred

        val intent = Intent(context, FacebookScanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(FacebookScanActivity.EXTRA_REQUEST_ID, requestId)
            putExtra(FacebookScanActivity.EXTRA_URL, inputUrl)
        }
        context.startActivity(intent)

        val result = withTimeoutOrNull(38_000) { deferred.await() }.orEmpty()
        pending.remove(requestId)
        return result
    }

    internal fun complete(requestId: String?, urls: List<String>) {
        if (requestId.isNullOrBlank()) return
        pending.remove(requestId)?.complete(urls.distinct().take(300))
    }
}

class FacebookScanActivity : Activity() {
    companion object {
        const val EXTRA_REQUEST_ID = "facebook_scan_request_id"
        const val EXTRA_URL = "facebook_scan_url"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private val found = linkedSetOf<String>()
    private val candidates = ArrayDeque<String>()
    private var requestId: String? = null
    private var scanAttempt = 0
    private var pageGeneration = 0
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val input = normalizeUrl(intent.getStringExtra(EXTRA_URL).orEmpty())
        candidates.add(input)
        addFacebookVariants(input)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 16, 30))
            setPadding(28, 32, 28, 20)
        }

        val title = TextView(this).apply {
            text = "Universal Downloader"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            text = "Scanning public Facebook Reels / Videos…"
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
            settings.loadsImagesAutomatically = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
            setBackgroundColor(Color.rgb(13, 23, 40))
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString().orEmpty()
                    val scheme = request?.url?.scheme?.lowercase().orEmpty()

                    // Facebook frequently tries to redirect signed-out WebViews to fb://profile/...
                    // which is an app-only deep link. Never let the WebView navigate to it.
                    if (scheme.isNotBlank() && scheme != "http" && scheme != "https") {
                        if (request?.isForMainFrame == true) {
                            pageGeneration++
                            scanAttempt = 0
                            statusView.text = "Facebook app-only link skipped. Trying public web page…"
                            handler.postDelayed({ if (!finished) loadNextCandidate() }, 250)
                        }
                        return true
                    }

                    if (isLoginUrl(target)) {
                        collectVisibleLinksThenStop()
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (!finished && request?.isForMainFrame == true && error?.errorCode == ERROR_UNSUPPORTED_SCHEME) {
                        pageGeneration++
                        scanAttempt = 0
                        statusView.text = "Unsupported Facebook app link skipped. Trying another public page…"
                        handler.postDelayed({ if (!finished) loadNextCandidate() }, 250)
                        return
                    }
                    super.onReceivedError(view, request, error)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (finished) return
                    val current = normalizeUrl(url.orEmpty())
                    if (!isHttpUrl(current)) {
                        pageGeneration++
                        statusView.text = "Facebook app-only redirect skipped."
                        handler.postDelayed({ if (!finished) loadNextCandidate() }, 250)
                        return
                    }
                    if (isLoginUrl(current)) {
                        collectVisibleLinksThenStop()
                        return
                    }
                    addFacebookVariants(current)
                    val generation = ++pageGeneration
                    scanAttempt = 0
                    statusView.text = "Checking guest access…"
                    handler.postDelayed({ detectLoginWall(generation) }, 450)
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val note = TextView(this).apply {
            text = "Guest mode only • No Facebook login is stored or bypassed"
            textSize = 12f
            setTextColor(Color.rgb(85, 221, 247))
            setPadding(0, 14, 0, 0)
        }
        root.addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        loadNextCandidate()
    }

    private fun detectLoginWall(generation: Int) {
        if (finished || generation != pageGeneration) return
        val script = """
            (function() {
              try {
                const href = (location.href || '').toLowerCase();
                if (href.includes('/login') || href.includes('checkpoint') || href.includes('login.php')) return 'LOGIN';
                const hasPass = !!document.querySelector('input[type="password"], input[name="pass"]');
                const hasEmail = !!document.querySelector('input[name="email"], input[type="email"]');
                const hasLoginForm = !!document.querySelector('form[action*="login"], form[id*="login"], form[data-sigil*="login"]');
                const text = ((document.body && document.body.innerText) || '').toLowerCase();
                const exactLoginButton = Array.from(document.querySelectorAll('a,button,[role="button"]'))
                  .some(el => ((el.innerText || el.textContent || '').trim().toLowerCase() === 'log in'));
                const loginText =
                  text.includes('log in to facebook') ||
                  text.includes('login to facebook') ||
                  text.includes('you must log in') ||
                  text.includes('please log in') ||
                  text.includes('log in to get all of the details') ||
                  (text.includes('see more from') && exactLoginButton);
                return ((hasPass && hasEmail) || hasLoginForm || loginText) ? 'LOGIN' : 'OK';
              } catch (e) { return 'OK'; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            if (finished || generation != pageGeneration) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            if (decoded.equals("LOGIN", ignoreCase = true)) {
                collectVisibleLinksThenStop()
            } else {
                statusView.text = "Scanning visible public links…"
                handler.postDelayed({ scanDom(generation) }, 500)
            }
        }
    }

    private fun scanDom(generation: Int) {
        if (finished || generation != pageGeneration) return
        val script = """
            (function() {
              try {
                const text = ((document.body && document.body.innerText) || '').toLowerCase();
                const exactLoginButton = Array.from(document.querySelectorAll('a,button,[role="button"]'))
                  .some(el => ((el.innerText || el.textContent || '').trim().toLowerCase() === 'log in'));
                const loginWall =
                  text.includes('log in to get all of the details') ||
                  text.includes('log in to facebook') ||
                  text.includes('login to facebook') ||
                  (text.includes('see more from') && exactLoginButton) ||
                  !!document.querySelector('input[type="password"], input[name="pass"], form[action*="login"]');

                const links = Array.from(document.querySelectorAll('a[href]'))
                  .map(a => a.href || '')
                  .filter(h => {
                    const x = h.toLowerCase();
                    return x.includes('facebook.com/reel/') ||
                           /facebook\.com\/[^/?#]+\/videos\//i.test(h) ||
                           /facebook\.com\/watch\/?\?v=/i.test(h) ||
                           /facebook\.com\/share\/[rv]\//i.test(h);
                  });
                return (loginWall ? 'LOGIN' : 'OK') + '\\n' + Array.from(new Set(links)).join('\\n');
              } catch (e) { return 'OK\\n'; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            if (finished || generation != pageGeneration) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            val lines = decoded.lineSequence().toList()
            val loginWall = lines.firstOrNull().equals("LOGIN", ignoreCase = true)
            lines.drop(1)
                .map { it.trim() }
                .filter { it.startsWith("https://") && "facebook.com" in it.lowercase() }
                .forEach { found += cleanFacebookVideoUrl(it) }

            if (loginWall) {
                statusView.text = if (found.isNotEmpty()) {
                    "Facebook guest limit reached. Found ${found.size} visible public videos."
                } else {
                    "Facebook requires login for more Page/Profile content. Guest scan stopped."
                }
                handler.postDelayed({ completeAndFinish() }, 350)
                return@evaluateJavascript
            }

            scanAttempt++
            statusView.text = if (found.isEmpty()) {
                "Scanning page… ${scanAttempt}/8"
            } else {
                "Found ${found.size} public videos…"
            }

            if (scanAttempt < 8 && found.size < 300) {
                webView.evaluateJavascript("window.scrollBy(0, Math.max(window.innerHeight * 1.8, 1100));", null)
                handler.postDelayed({ scanDom(generation) }, 850)
            } else if (found.isNotEmpty()) {
                completeAndFinish()
            } else {
                loadNextCandidate()
            }
        }
    }

    private fun collectVisibleLinksThenStop() {
        if (finished || !::webView.isInitialized) return
        val generation = ++pageGeneration
        scanAttempt = 0
        statusView.text = "Facebook login wall detected. Collecting visible public links once…"
        val script = """
            (function() {
              try {
                const links = Array.from(document.querySelectorAll('a[href]'))
                  .map(a => a.href || '')
                  .filter(h => {
                    const x = h.toLowerCase();
                    return x.includes('facebook.com/reel/') ||
                           /facebook\.com\/[^/?#]+\/videos\//i.test(h) ||
                           /facebook\.com\/watch\/?\?v=/i.test(h) ||
                           /facebook\.com\/share\/[rv]\//i.test(h);
                  });
                return Array.from(new Set(links)).join('\\n');
              } catch (e) { return ''; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            if (finished || generation != pageGeneration) return@evaluateJavascript
            val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            decoded.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("https://") && "facebook.com" in it.lowercase() }
                .forEach { found += cleanFacebookVideoUrl(it) }

            statusView.text = if (found.isNotEmpty()) {
                "Found ${found.size} visible public videos before Facebook login wall."
            } else {
                "Facebook requires login for this Page/Profile. Guest scan stopped."
            }
            handler.postDelayed({ completeAndFinish() }, 350)
        }
    }

    private fun loadNextCandidate() {
        if (finished) return
        var next = candidates.removeFirstOrNull()
        while (next != null && !isHttpUrl(next)) {
            next = candidates.removeFirstOrNull()
        }
        if (next == null) {
            completeAndFinish()
            return
        }
        if (isLoginUrl(next)) {
            collectVisibleLinksThenStop()
            return
        }
        statusView.text = "Opening public Facebook page…"
        webView.loadUrl(next)
    }

    private fun addFacebookVariants(raw: String) {
        if (isLoginUrl(raw) || !isHttpUrl(raw)) return
        val base = facebookBase(raw) ?: return
        val variants = listOf(
            "$base/reels/",
            "$base/videos/",
            "$base/reels",
            "$base/videos"
        )
        variants.forEach { candidate ->
            if (candidate != webViewUrlSafe() && !candidates.contains(candidate)) candidates.add(candidate)
        }
    }

    private fun stopForLoginWall() {
        if (finished) return
        pageGeneration++
        runCatching { webView.stopLoading() }
        if (::webView.isInitialized) webView.visibility = View.INVISIBLE
        statusView.text = "Facebook requires login for this Page/Profile. Guest scan stopped."
        handler.postDelayed({ completeAndFinish() }, 500)
    }

    private fun isLoginUrl(raw: String): Boolean {
        val value = raw.lowercase()
        return "facebook.com/login" in value ||
            "/login.php" in value ||
            "checkpoint" in value ||
            "recover/initiate" in value
    }

    private fun isHttpUrl(raw: String): Boolean {
        val value = raw.trim().lowercase()
        return value.startsWith("https://") || value.startsWith("http://")
    }

    private fun webViewUrlSafe(): String? = if (::webView.isInitialized) webView.url else null

    private fun facebookBase(raw: String): String? {
        val normalized = normalizeUrl(raw)
        return runCatching {
            val uri = URI(normalized)
            if (!uri.host.orEmpty().contains("facebook.com", ignoreCase = true)) return@runCatching null
            var path = uri.path.orEmpty().trimEnd('/')
            path = path.replace(Regex("/(reels|videos)$", RegexOption.IGNORE_CASE), "")
            if (path.isBlank() || path == "/" || path.startsWith("/share/") || path.startsWith("/login")) return@runCatching null
            "https://www.facebook.com$path"
        }.getOrNull()
    }

    private fun cleanFacebookVideoUrl(url: String): String =
        url.substringBefore('#').replace("m.facebook.com", "www.facebook.com")

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.startsWith("://") -> "https$value"
            value.startsWith("//") -> "https:$value"
            value.startsWith("www.") || value.startsWith("facebook.com") -> "https://$value"
            else -> value
        }
    }

    private fun completeAndFinish() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        FacebookBrowserScanner.complete(requestId, found.toList())
        runCatching { webView.stopLoading() }
        runCatching { webView.destroy() }
        finish()
    }

    override fun onBackPressed() {
        completeAndFinish()
    }

    override fun onDestroy() {
        if (!finished) FacebookBrowserScanner.complete(requestId, found.toList())
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) runCatching { webView.destroy() }
        super.onDestroy()
    }
}
