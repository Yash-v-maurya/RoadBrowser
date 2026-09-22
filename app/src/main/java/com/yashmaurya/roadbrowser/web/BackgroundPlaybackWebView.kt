package com.yashmaurya.roadbrowser.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView
import com.yashmaurya.roadbrowser.data.BrowserPreferences

/**
 * WebView that keeps the page "visible" while the browser window is hidden.
 *
 * Pausing the WebView is already gated on the background-audio preference, but that is not
 * enough on its own: as soon as the window stops being visible Blink flips the Page Visibility
 * API to `hidden`, and YouTube (like most video sites) pauses playback on that signal. On a head
 * unit the window is hidden constantly — navigation takes over, the launcher is opened, the
 * assistant pops up — so playback would die every time the user glances at Maps.
 *
 * When background audio is enabled, window-visibility changes are reported to the WebView as
 * VISIBLE. The view's own visibility is untouched, so inactive tabs (which are GONE and paused
 * by [com.yashmaurya.roadbrowser.tabs.TabManager]) still stop.
 */
@SuppressLint("ViewConstructor")
class BackgroundPlaybackWebView(context: Context) : WebView(context) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        val spoofed = if (visibility != View.VISIBLE && BrowserPreferences.isBackgroundAudioEnabled(context)) {
            View.VISIBLE
        } else {
            visibility
        }
        super.onWindowVisibilityChanged(spoofed)
    }

    companion object {
        /**
         * Page-side half of the fix. Blink still fires `visibilitychange` / `blur` / `pagehide`
         * events for the frame in some paths (e.g. when the Activity is stopped rather than merely
         * covered), so `document.hidden` is pinned to `false` and those events are swallowed
         * before the page's own listeners see them. Installed once per document; survives SPA
         * navigation because the document object does.
         */
        val VISIBILITY_SHIM_JS = """
            (function(){
                try {
                    if (window.__aabBgPlayback) return;
                    window.__aabBgPlayback = true;

                    function pin(obj, name, value) {
                        try {
                            Object.defineProperty(obj, name, {
                                get: function(){ return value; },
                                configurable: true
                            });
                        } catch(e) {}
                    }
                    pin(document, 'hidden', false);
                    pin(document, 'visibilityState', 'visible');
                    pin(document, 'webkitHidden', false);
                    pin(document, 'webkitVisibilityState', 'visible');
                    try { document.hasFocus = function(){ return true; }; } catch(e) {}

                    var SWALLOW = ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide', 'freeze'];
                    function stop(e) {
                        // Element-level blur (an input losing focus) must keep working; only the
                        // window/document-level signals that mean "page went to background" die here.
                        if (e.type === 'blur' && e.target !== window && e.target !== document) return;
                        try { e.stopImmediatePropagation(); } catch(err) {}
                    }
                    for (var i = 0; i < SWALLOW.length; i++) {
                        try { window.addEventListener(SWALLOW[i], stop, true); } catch(e) {}
                        try { document.addEventListener(SWALLOW[i], stop, true); } catch(e) {}
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        /** Installs [VISIBILITY_SHIM_JS] when background audio is enabled; no-op otherwise. */
        fun injectVisibilityShim(webView: WebView) {
            if (!BrowserPreferences.isBackgroundAudioEnabled(webView.context)) return
            webView.evaluateJavascript(VISIBILITY_SHIM_JS, null)
        }
    }
}
