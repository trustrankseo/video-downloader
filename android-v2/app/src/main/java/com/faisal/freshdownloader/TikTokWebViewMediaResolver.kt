package com.faisal.freshdownloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener

/** Resolves a TikTok video CDN URL from the same signed-in WebView cookie jar. */
object TikTokWebViewMediaResolver {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    suspend fun resolve(context: Context, videoUrl: String): String? {
        val result = CompletableDeferred<String?>()
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var checks = 0

        fun finish(value: String?) {
            if (!result.isCompleted) result.complete(value)
            handler.post {
                runCatching { webView?.stopLoading() }
                runCatching { webView?.destroy() }
                webView = null
            }
        }

        fun isMediaUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val u = url.lowercase()
            return u.startsWith("http") && (
                u.contains(".mp4") ||
                u.contains("video/tos") ||
                u.contains("/video/") && (u.contains("mime_type=video") || u.contains("mime_type=video_mp4")) ||
                u.contains("bytevc") ||
                u.contains("v16-webapp") ||
                u.contains("v19-webapp")
            )
        }

        fun inspect() {
            val w = webView ?: return
            if (result.isCompleted) return
            checks++
            val script = """
                (function(){try{
                  const out=[];
                  const v=document.querySelector('video');
                  if(v){ if(v.currentSrc) out.push(v.currentSrc); if(v.src) out.push(v.src); }
                  document.querySelectorAll('video source, source[type^="video"]').forEach(x=>{if(x.src)out.push(x.src)});
                  ['og:video','og:video:url','og:video:secure_url','twitter:player:stream'].forEach(k=>{
                    const m=document.querySelector('meta[property="'+k+'"],meta[name="'+k+'"]'); if(m&&m.content)out.push(m.content);
                  });
                  try{performance.getEntriesByType('resource').forEach(e=>{const n=e.name||''; if(/\.mp4|video\/tos|bytevc|v16-webapp|v19-webapp/i.test(n))out.push(n);});}catch(e){}
                  const html=(document.documentElement&&document.documentElement.innerHTML)||'';
                  const pats=[
                    /\"playAddr\"\s*:\s*\"(https?:[^\"]+)/i,
                    /\"downloadAddr\"\s*:\s*\"(https?:[^\"]+)/i,
                    /\"playAddr\"\s*:\s*\[\s*\"(https?:[^\"]+)/i,
                    /\"downloadAddr\"\s*:\s*\[\s*\"(https?:[^\"]+)/i,
                    /\"urlList\"\s*:\s*\[\s*\"(https?:[^\"]+)/i,
                    /\"UrlList\"\s*:\s*\[\s*\"(https?:[^\"]+)/i
                  ];
                  for(const p of pats){const m=html.match(p); if(m&&m[1])out.push(m[1]);}
                  return JSON.stringify(out);
                }catch(e){return '[]';}})();
            """.trimIndent()
            w.evaluateJavascript(script) { raw ->
                if (result.isCompleted) return@evaluateJavascript
                val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
                val candidates = Regex("https?[^\\\"']+").findAll(decoded.replace("\\u002F", "/").replace("\\/", "/").replace("\\u0026", "&"))
                    .map { it.value.replace("&amp;", "&") }
                    .toList()
                val hit = candidates.firstOrNull { isMediaUrl(it) }
                if (hit != null) finish(hit)
                else if (checks < 20) handler.postDelayed({ inspect() }, 700)
                else finish(null)
            }
        }

        handler.post {
            try {
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                }
                webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = UA
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            CookieManager.getInstance().flush()
                            handler.postDelayed({ inspect() }, 350)
                        }

                        override fun onLoadResource(view: WebView?, url: String?) {
                            super.onLoadResource(view, url)
                            if (isMediaUrl(url)) finish(url)
                        }
                    }
                    loadUrl(videoUrl)
                }
                handler.postDelayed({ inspect() }, 1200)
            } catch (_: Throwable) {
                finish(null)
            }
        }

        return withTimeoutOrNull(18_000) { result.await() }.also {
            if (!result.isCompleted) finish(null)
        }
    }
}
