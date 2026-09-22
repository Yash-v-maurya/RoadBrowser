package com.yashmaurya.roadbrowser.web

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.yashmaurya.roadbrowser.data.BrowserPreferences

/**
 * YouTube-specific workarounds, gated on [BrowserPreferences.isYouTubeCompatEnabled].
 *
 * The desktop user agent makes YouTube serve its heavy desktop UI, which is slow on head-unit
 * hardware and frequently refuses to start playback, so YouTube is pinned to the mobile identity
 * even when the browser-wide desktop toggle is on. Every entry point no-ops when the pref is off.
 */
object YouTubeCompat {

    private val YOUTUBE_DOMAINS = setOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "youtubekids.com"
    )

    fun isYouTubeHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.lowercase().removeSuffix(".")
        return YOUTUBE_DOMAINS.any { normalized == it || normalized.endsWith(".$it") }
    }

    fun isYouTubeUrl(url: String?): Boolean {
        val uri = url?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        return isYouTubeHost(uri.host)
    }

    /** True when [url] is a YouTube page and compat mode is enabled. */
    fun appliesTo(context: Context, url: String?): Boolean {
        if (!isYouTubeUrl(url)) return false
        return BrowserPreferences.isYouTubeCompatEnabled(context)
    }

    /** Patches the loaded YouTube page. Safe to call for every page; no-ops elsewhere. */
    fun injectPagePatch(webView: WebView, url: String?) {
        if (!appliesTo(webView.context, url)) return
        webView.evaluateJavascript(PAGE_PATCH_JS, null)
    }

    private val PAGE_PATCH_JS = """
        (function(){
            try {
                var CSS_ID = '__aab_yt_compat_css';
                var REJECT = /(reject|decline|refuser|ablehnen|rechazar|necessary)/i;
                var ACCEPT = /(accept|agree|allow|akzeptieren|aceptar|accepter|同意)/i;
                var BANNERS = [
                    '#unsupported-browser',
                    '.unsupported-browser',
                    'ytd-unsupported-browser-banner',
                    'ytm-unsupported-browser-banner',
                    'yt-upsell-dialog-renderer',
                    '.ytd-browser-not-supported-renderer'
                ];
                var CONSENT = [
                    'ytd-consent-bump-v2-lightbox',
                    'tp-yt-paper-dialog#consent-bump',
                    '#consent-bump',
                    'ytm-consent-bump-v2-renderer',
                    'form[action*="consent"]',
                    'div[aria-modal="true"][class*="consent"]'
                ];

                function injectCss(){
                    try {
                        if (document.getElementById(CSS_ID)) return;
                        var head = document.head || document.documentElement;
                        if (!head) return;
                        var style = document.createElement('style');
                        style.id = CSS_ID;
                        style.textContent =
                            BANNERS.join(',') + '{display:none!important;}' +
                            'ytd-app button:not([class*="ytp-"]),' +
                            'ytm-app button:not([class*="ytp-"]),' +
                            'ytd-app a.yt-simple-endpoint,' +
                            'ytm-app a.yt-simple-endpoint' +
                            '{min-height:40px;}';
                        head.appendChild(style);
                    } catch(e) {}
                }

                function hideBanners(){
                    try {
                        for (var i = 0; i < BANNERS.length; i++) {
                            var found = document.querySelectorAll(BANNERS[i]);
                            for (var j = 0; j < found.length; j++) {
                                if (found[j] && found[j].style) found[j].style.display = 'none';
                            }
                        }
                    } catch(e) {}
                }

                function labelOf(el){
                    try {
                        var text = el.getAttribute('aria-label') || el.textContent || '';
                        return String(text).trim();
                    } catch(e) { return ''; }
                }

                function dismissConsent(){
                    try {
                        for (var i = 0; i < CONSENT.length; i++) {
                            var box = document.querySelector(CONSENT[i]);
                            if (!box) continue;
                            var buttons = box.querySelectorAll('button, tp-yt-paper-button, [role="button"]');
                            var fallback = null;
                            for (var j = 0; j < buttons.length; j++) {
                                var label = labelOf(buttons[j]);
                                if (!label) continue;
                                if (REJECT.test(label)) { buttons[j].click(); return true; }
                                if (!fallback && ACCEPT.test(label)) fallback = buttons[j];
                            }
                            if (fallback) { fallback.click(); return true; }
                        }
                    } catch(e) {}
                    return false;
                }

                function run(){
                    injectCss();
                    hideBanners();
                    return dismissConsent();
                }

                if (window.__aabYtCompat) {
                    window.__aabYtCompat.run();
                    return;
                }

                var state = { run: run, ticks: 0, timer: null };
                window.__aabYtCompat = state;
                run();
                state.timer = window.setInterval(function(){
                    try {
                        state.ticks++;
                        if (run() || state.ticks > 12) {
                            window.clearInterval(state.timer);
                            state.timer = null;
                        }
                    } catch(e) {
                        try { window.clearInterval(state.timer); } catch(e2) {}
                    }
                }, 500);
            } catch(e) {}
        })();
    """.trimIndent()
}
