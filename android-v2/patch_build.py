from pathlib import Path

p = Path('app/src/main/java/com/faisal/freshdownloader/PublicProfileBrowserScanner.kt')
s = p.read_text()
old = 'webView.loadUrl(targetUrl)\n            }\n        }\n        root.addView(continueButton'
new = 'if (platform == "tiktok") {\n                    webView.settings.userAgentString = MOBILE_UA\n                }\n                webView.loadUrl(targetUrl)\n            }\n        }\n        root.addView(continueButton'
if old in s:
    s = s.replace(old, new, 1)

old = "else{(html.match(/\\/@[^\\\"'<>\\\\/]+\\/video\\/\\d+/gi)||[]).forEach(x=>links.push('https://www.tiktok.com'+x));links=links.filter(h=>/tiktok\\.com\\/@[^/]+\\/video\\/\\d+/i.test(h));}"
new = """else{
                (html.match(/\\/@[^\\\"'<>\\\\/]+\\/video\\/\\d+/gi)||[]).forEach(x=>links.push('https://www.tiktok.com'+x));
                const m=(location.pathname||'').match(/^\\/@([^/]+)/i); const user=m&&m[1]?m[1]:'';
                if(user){
                  const pats=[/\"id\"\\s*:\\s*\"(\\d{10,})\"\\s*,\\s*\"desc\"/g,/\"awemeId\"\\s*:\\s*\"(\\d{10,})\"/g,/\"aweme_id\"\\s*:\\s*\"(\\d{10,})\"/g,/\"itemId\"\\s*:\\s*\"(\\d{10,})\"/g];
                  pats.forEach(r=>{let q; while((q=r.exec(html))!==null){links.push('https://www.tiktok.com/@'+user+'/video/'+q[1]);}});
                }
                links=links.filter(h=>/tiktok\\.com\\/@[^/]+\\/video\\/\\d+/i.test(h));
              }"""
if old in s:
    s = s.replace(old, new, 1)

old = '                found.clear()\n                webView.loadUrl(targetUrl)\n            }\n        }\n        root.addView(scanButton'
new = '                found.clear()\n                if (platform == "tiktok") {\n                    webView.settings.userAgentString = MOBILE_UA\n                    val joiner = if (targetUrl.contains("?")) "&" else "?"\n                    webView.loadUrl(targetUrl + joiner + "is_from_webapp=1&sender_device=mobile")\n                } else {\n                    webView.loadUrl(targetUrl)\n                }\n            }\n        }\n        root.addView(scanButton'
if old in s:
    s = s.replace(old, new, 1)

old = '            } else if (found.isEmpty() && authenticated) {\n                statusView.text = "Signed in, but no video links were visible yet. Scroll once and tap SCAN PROFILE again."\n            } else if (platform == "tiktok") {'
new = '            } else if (found.isEmpty() && authenticated) {\n                statusView.text = if (platform == "tiktok") "Signed in, but TikTok did not render video links yet. Tap SCAN PROFILE to reload the mobile profile and scan again." else "Signed in, but no video links were visible yet. Scroll once and tap SCAN PROFILE again."\n            } else if (platform == "tiktok") {'
if old in s:
    s = s.replace(old, new, 1)

old = 'override fun onDestroy(){PublicProfileBrowserScanner.detach(this);if(!finished)PublicProfileBrowserScanner.complete(requestId,found.toList());handler.removeCallbacksAndMessages(null);if(::webView.isInitialized)runCatching{webView.destroy()};super.onDestroy()}'
new = '''override fun onDestroy(){
              PublicProfileBrowserScanner.detach(this)
              if(!finished && platform != "tiktok") PublicProfileBrowserScanner.complete(requestId,found.toList())
              handler.removeCallbacksAndMessages(null)
              if(::webView.isInitialized) runCatching{webView.destroy()}
              super.onDestroy()
          }'''
if old in s:
    s = s.replace(old, new, 1)
p.write_text(s)

e = Path('app/src/main/java/com/faisal/freshdownloader/DownloaderEngine.kt')
t = e.read_text()
old = '''    private fun applySessionCookie(request: YoutubeDLRequest, rootUrl: String) {
        val cookie = runCatching { CookieManager.getInstance().getCookie(rootUrl) }.getOrNull()
        if (!cookie.isNullOrBlank()) {
            request.addOption("--add-header", "Cookie: $cookie")
        }
    }
'''
new = '''    private fun applySessionCookie(request: YoutubeDLRequest, rootUrl: String) {
        SocialCookieJar.create(context, rootUrl)?.let { cookieFile ->
            request.addOption("--cookies", cookieFile.absolutePath)
        }
    }
'''
if old not in t:
    raise SystemExit('applySessionCookie patch target not found')
t = t.replace(old, new, 1)

# TikTok broke in recent yt-dlp nightly builds on Android because those builds request
# browser impersonation support that youtubedl-android does not package. Keep the
# library's bundled yt-dlp for social downloads instead of replacing it with the
# currently broken nightly release at runtime.
old = '''    private fun prepareSocialEngineIfNeeded() {
        if (socialEnginePrepared || cancelRequested) return
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
        socialEnginePrepared = true
    }
'''
new = '''    private fun prepareSocialEngineIfNeeded() {
        if (socialEnginePrepared || cancelRequested) return
        socialEnginePrepared = true
    }
'''
if old not in t:
    raise SystemExit('prepareSocialEngineIfNeeded patch target not found')
t = t.replace(old, new, 1)

e.write_text(t)
