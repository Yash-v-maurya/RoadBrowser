package com.yashmaurya.roadbrowser.media

import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Page-side half of the media session integration.
 *
 * A shim installed in every document watches `<video>`/`<audio>` play, pause and ended events
 * (capture phase, so sites that stop propagation can't hide them) and reports the aggregate
 * state through the [JS_OBJECT_NAME] interface, together with whatever the page put into
 * `navigator.mediaSession.metadata` (YouTube, Spotify and most players fill it in).
 *
 * It also wraps `navigator.mediaSession.setActionHandler` so the handlers the page registers
 * for play / pause / nexttrack / previoustrack are kept in `window.__aabMedia.handlers`. Steering
 * wheel and notification buttons are then routed to the page's own handlers via [dispatch] —
 * which is what makes "next" work on YouTube — with a plain `video.play()`/`pause()` fallback
 * for pages that never register any.
 */
class MediaSessionBridge(
    private val onStateChanged: (playing: Boolean, title: String, artist: String) -> Unit
) {

    @JavascriptInterface
    fun onPlaybackChanged(playing: Boolean, title: String?, artist: String?) {
        onStateChanged(playing, title.orEmpty(), artist.orEmpty())
    }

    companion object {
        const val JS_OBJECT_NAME = "AABMedia"

        val PAGE_SHIM_JS = """
            (function(){
                try {
                    if (window.__aabMedia) return;
                    var state = { handlers: {}, playing: false, last: '' };
                    window.__aabMedia = state;

                    function meta() {
                        var t = '', a = '';
                        try {
                            var m = navigator.mediaSession && navigator.mediaSession.metadata;
                            if (m) { t = m.title || ''; a = m.artist || ''; }
                        } catch(e) {}
                        if (!t) t = document.title || '';
                        return { title: String(t).slice(0, 200), artist: String(a).slice(0, 200) };
                    }
                    function anyPlaying() {
                        var els = document.querySelectorAll('video, audio');
                        for (var i = 0; i < els.length; i++) {
                            var el = els[i];
                            if (!el.paused && !el.ended && el.readyState > 2) return true;
                        }
                        return false;
                    }
                    function report() {
                        var playing = anyPlaying();
                        var m = meta();
                        var key = playing + '|' + m.title + '|' + m.artist;
                        if (key === state.last) return;
                        state.last = key;
                        state.playing = playing;
                        try { $JS_OBJECT_NAME.onPlaybackChanged(playing, m.title, m.artist); } catch(e) {}
                    }
                    state.report = report;
                    var scheduled = null;
                    function schedule() {
                        if (scheduled) return;
                        scheduled = setTimeout(function(){ scheduled = null; report(); }, 150);
                    }
                    var EVENTS = ['play', 'playing', 'pause', 'ended', 'emptied', 'abort'];
                    for (var i = 0; i < EVENTS.length; i++) {
                        document.addEventListener(EVENTS[i], schedule, true);
                    }
                    // Metadata usually lands a moment after play; poll lightly while playing.
                    setInterval(function(){ if (state.playing || anyPlaying()) schedule(); }, 3000);

                    try {
                        var ms = navigator.mediaSession;
                        if (ms && typeof ms.setActionHandler === 'function') {
                            var orig = ms.setActionHandler.bind(ms);
                            ms.setActionHandler = function(action, handler) {
                                if (handler) state.handlers[action] = handler; else delete state.handlers[action];
                                try { return orig(action, handler); } catch(e) {}
                            };
                        }
                    } catch(e) {}

                    function primary() {
                        var els = document.querySelectorAll('video, audio');
                        var best = null;
                        for (var i = 0; i < els.length; i++) {
                            var el = els[i];
                            if (!el.paused) return el;
                            if (!best || (el.readyState > best.readyState)) best = el;
                        }
                        return best;
                    }
                    state.dispatch = function(action) {
                        var h = state.handlers[action];
                        if (h) { try { h({ action: action }); schedule(); return true; } catch(e) {} }
                        var el = primary();
                        if (!el) return false;
                        try {
                            if (action === 'play') el.play();
                            else if (action === 'pause') el.pause();
                            else if (action === 'stop') { el.pause(); }
                            else if (action === 'seekforward') el.currentTime += 10;
                            else if (action === 'seekbackward') el.currentTime = Math.max(0, el.currentTime - 10);
                            else return false;
                        } catch(e) { return false; }
                        schedule();
                        return true;
                    };
                    schedule();
                } catch(e) {}
            })();
        """.trimIndent()

        /** Installs [PAGE_SHIM_JS]; idempotent per document. */
        fun inject(webView: WebView) {
            webView.evaluateJavascript(PAGE_SHIM_JS, null)
        }

        /** Asks the page to perform a Media Session action (`play`, `pause`, `nexttrack`, …). */
        fun dispatch(webView: WebView, action: String) {
            val safe = action.filter { it.isLetter() }
            webView.evaluateJavascript(
                "(function(){try{return window.__aabMedia&&window.__aabMedia.dispatch('$safe');}catch(e){return false;}})();",
                null
            )
        }

        /** Forces the page to re-report its state (e.g. after the tab became active again). */
        fun requestState(webView: WebView) {
            webView.evaluateJavascript(
                "(function(){try{if(window.__aabMedia){window.__aabMedia.last='';window.__aabMedia.report();}}catch(e){}})();",
                null
            )
        }
    }
}
