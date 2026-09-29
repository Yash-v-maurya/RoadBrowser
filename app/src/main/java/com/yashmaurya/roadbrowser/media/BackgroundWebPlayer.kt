package com.yashmaurya.roadbrowser.media

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.web.BackgroundPlaybackWebView
import com.yashmaurya.roadbrowser.web.BrowserCallbacks
import com.yashmaurya.roadbrowser.web.configureWebView
import com.yashmaurya.roadbrowser.web.prepareBrowserIdentityFor
import com.yashmaurya.roadbrowser.web.releaseCompletely

/**
 * Windowless page player behind the Android Auto media screen.
 *
 * Once the car moves, Android Auto covers the browser with its "restricted while driving" screen
 * and won't open it, but media apps stay usable through Android Auto's own driver-safe player. A
 * quick link or bookmark picked there is loaded into this WebView, which is never attached to a
 * window: nothing from the page reaches the car screen, only its audio and the title/artist it
 * publishes to the media session. Controls reach it through [dispatch], the same way the browser
 * tabs get them. Main thread only.
 */
@SuppressLint("StaticFieldLeak") // The WebView is built on the application context.
object BackgroundWebPlayer {

    fun interface Listener {
        fun onPlaybackChanged(playing: Boolean, title: String, artist: String, pageUrl: String?)
    }

    private const val VIEWPORT_WIDTH_PX = 1280
    private const val VIEWPORT_HEIGHT_PX = 720

    // Pages that wait for a click before playing (most radio players) get a play() once they
    // have settled; the page's own autoplay usually wins well before the first nudge.
    private val AUTOPLAY_NUDGE_DELAYS_MS = longArrayOf(2_500L, 7_000L)

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var isPlaying = false
    private val autoplayNudge = Runnable { if (!isPlaying) dispatch("play") }

    var listener: Listener? = null

    val isLoaded: Boolean
        get() = webView != null

    @VisibleForTesting
    internal val loadedWebView: WebView?
        get() = webView

    fun play(context: Context, url: String) {
        val view = webView ?: create(context.applicationContext).also { webView = it }
        isPlaying = false
        handler.removeCallbacks(autoplayNudge)
        view.prepareBrowserIdentityFor(url)
        view.loadUrl(url)
    }

    /** Sends a Media Session action (`play`, `pause`, `nexttrack`, …) to the loaded page. */
    fun dispatch(action: String): Boolean {
        val view = webView ?: return false
        MediaSessionBridge.dispatch(view, action)
        return true
    }

    fun release() {
        handler.removeCallbacks(autoplayNudge)
        isPlaying = false
        webView?.let {
            it.onPause()
            it.releaseCompletely()
        }
        webView = null
    }

    private fun create(appContext: Context): WebView {
        val view = BackgroundPlaybackWebView(appContext)
        // No UI callbacks: with an application context the defaults refuse anything that would
        // need a dialog (cleartext prompts, permissions, SSL errors), which is what a hidden page
        // on the road should do.
        configureWebView(
            view,
            BrowserCallbacks(onUrlChange = { scheduleAutoplayNudges() }),
            useDesktopMode = false,
            userAgentProfile = BrowserPreferences.getUserAgentProfile(appContext),
            allowDarkPages = false
        )
        MediaSessionBridge.attach(view) { playing, title, artist ->
            handler.post {
                if (webView !== view) return@post
                isPlaying = playing
                listener?.onPlaybackChanged(playing, title, artist, view.url)
            }
        }
        // The tabs only pin page visibility when background audio is switched on; here the page
        // is always hidden, so the shim goes in unconditionally.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, BackgroundPlaybackWebView.VISIBILITY_SHIM_JS, setOf("*"))
        }
        // Never attached to a window, so the viewport has to be set by hand; some players refuse
        // to initialise in a 0x0 page.
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT_PX, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, VIEWPORT_WIDTH_PX, VIEWPORT_HEIGHT_PX)
        return view
    }

    private fun scheduleAutoplayNudges() {
        handler.removeCallbacks(autoplayNudge)
        AUTOPLAY_NUDGE_DELAYS_MS.forEach { handler.postDelayed(autoplayNudge, it) }
    }
}
