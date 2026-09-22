package com.yashmaurya.roadbrowser.web

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.yashmaurya.roadbrowser.BuildConfig
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.model.UserAgentProfile

data class BrowserCallbacks(
    val onUrlChange: (String) -> Unit = {},
    val onTitleChange: (String?) -> Unit = {},
    val onFaviconReceived: (String, Bitmap?) -> Unit = { _, _ -> },
    val onProgressChange: (Int) -> Unit = {},
    val onShowDownloadPrompt: (Uri) -> Unit = {},
    val onError: (Int, String?) -> Unit = { _, _ -> },
    val onCleartextNavigationRequested: (
        Uri,
        allowOnce: () -> Unit,
        allowHostPermanently: () -> Unit,
        cancel: () -> Unit
    ) -> Unit = { _, _, _, cancel -> cancel() },
    val onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, _ -> },
    val onExitFullscreen: () -> Unit = {},
    val onPermissionRequest: (PermissionRequest) -> Unit = { it.deny() },
    val onGeolocationPermissionRequest: (String?, android.webkit.GeolocationPermissions.Callback?) -> Unit = { _, callback -> callback?.invoke(null, false, false) },
    val onOpenInNewTab: (url: String) -> Boolean = { false }
)

fun configureWebView(
    webView: WebView,
    callbacks: BrowserCallbacks = BrowserCallbacks(),
    useDesktopMode: Boolean = false,
    userAgentProfile: UserAgentProfile = UserAgentProfile.ANDROID_CHROME,
    allowDarkPages: Boolean = false
) {
    val appContext = webView.context.applicationContext
    // Written on the UI thread, read by shouldInterceptRequest on WebView's background thread.
    val currentPageUrl = java.util.concurrent.atomic.AtomicReference<String?>(null)

    with(webView) {
        setBackgroundColor(Color.TRANSPARENT)

        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = true

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val originalUserAgent = settings.userAgentString
        setTag(R.id.webview_original_user_agent_tag, originalUserAgent)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true

            setSupportMultipleWindows(true)

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                offscreenPreRaster = true
            }
        }

        applyPageDarkening(allowDarkPages)
        applyBrowserIdentity(userAgentProfile, useDesktopMode)

        CookieManager.getInstance().also {
            it.setAcceptCookie(true)
            it.setAcceptThirdPartyCookies(this, true)
        }

        //setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (!BrowserPreferences.isShieldsEnabled(appContext)) {
                    return null
                }
                AdBlocker.ensureLoaded(appContext)
                return AdBlocker.interceptOrNull(
                    requestUrl = request.url,
                    pageUrl = currentPageUrl.get(),
                    isMainFrame = request.isForMainFrame
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (handleCleartextIfNeeded(view, uri, callbacks, onPageStart = false)) {
                    return true
                }
                if (handleUri(view, uri)) {
                    return true
                }
                if (request.isForMainFrame) {
                    view.syncBrowserIdentity(uri?.toString())
                }
                return false
            }

            private fun handleUri(view: WebView, uri: Uri?): Boolean {
                if (uri == null) {
                    return false
                }
                val scheme = uri.scheme?.lowercase()
                if (scheme == null || scheme in setOf("http", "https", "about", "file", "data", "javascript")) {
                    return false
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                currentPageUrl.set(url)
                val stringUrl = url
                if (stringUrl == null) {
                    return
                }
                val uri = Uri.parse(stringUrl)
                val scheme = uri.scheme?.lowercase()

                if (scheme == "http") {
                    val allowedOnce = getTag(R.id.webview_allow_once_uri_tag) as? String
                    if (allowedOnce == stringUrl) {
                        setTag(R.id.webview_allow_once_uri_tag, null)
                    } else if (handleCleartextIfNeeded(view, uri, callbacks, onPageStart = true)) {
                        return
                    }
                }

                if (scheme != "http" && scheme != "https") {
                    return
                }
                // Host-initiated loads and server redirects never pass through
                // shouldOverrideUrlLoading, so the document may already be in flight with the
                // wrong identity. One reload per URL swaps it, and the guard tag stops a loop.
                if (view.syncBrowserIdentity(stringUrl) &&
                    view.getTag(R.id.webview_user_agent_reload_tag) as? String != stringUrl
                ) {
                    view.setTag(R.id.webview_user_agent_reload_tag, stringUrl)
                    view.post {
                        if (view.url == stringUrl) {
                            view.reload()
                        }
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                currentPageUrl.set(url)
                view.evaluateJavascript(SpeechRecognitionBridge.POLYFILL_JS, null)
                BackgroundPlaybackWebView.injectVisibilityShim(view)
                com.yashmaurya.roadbrowser.media.MediaSessionBridge.inject(view)
                YouTubeCompat.injectPagePatch(view, url)
                url?.let(callbacks.onUrlChange)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    val code = error.errorCode
                    val shouldShowErrorPage = when (code) {
                        WebViewClient.ERROR_HOST_LOOKUP,
                        WebViewClient.ERROR_CONNECT,
                        WebViewClient.ERROR_TIMEOUT,
                        WebViewClient.ERROR_UNKNOWN,
                        WebViewClient.ERROR_PROXY_AUTHENTICATION -> true
                        else -> false
                    }

                    if (shouldShowErrorPage) {
                        val failed = request.url?.toString().orEmpty()
                        val message = error.description?.toString().orEmpty()
                        val assetUrl = "file:///android_asset/error.html?failedUrl=${Uri.encode(failed)}&code=$code&message=${Uri.encode(message)}"
                        try {
                            view.loadUrl(assetUrl)
                        } catch (_: Exception) {
                            callbacks.onError(code, error.description?.toString())
                        }
                        return
                    }
                }
                callbacks.onError(error.errorCode, error.description?.toString())
            }

            /**
             * Only a failed GET of the page the WebView is actually showing may be replaced by
             * the error page: a POST or a sub-navigation must not blow away a working document.
             */
            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (!request.isForMainFrame) {
                    return
                }
                if (!request.method.orEmpty().equals("GET", ignoreCase = true)) {
                    return
                }
                val failed = request.url?.toString().orEmpty()
                if (failed.isEmpty() || (failed != view.url && failed != currentPageUrl.get())) {
                    return
                }
                val code = errorResponse.statusCode
                if (code in 400..599 && code != 429) {
                    val message = errorResponse.reasonPhrase.orEmpty()
                    val assetUrl = "file:///android_asset/error.html?failedUrl=${Uri.encode(failed)}&code=$code&message=${Uri.encode(message)}"
                    try {
                        view.loadUrl(assetUrl)
                    } catch (_: Exception) {
                        callbacks.onError(code, message)
                    }
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val activity = view.context as? android.app.Activity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    SslErrorHandlerHelper.handleSslError(activity, handler, error)
                } else {
                    handler.cancel()
                }
            }

            override fun onReceivedClientCertRequest(view: WebView, request: android.webkit.ClientCertRequest) {
                val activity = view.context as? android.app.Activity
                if (activity != null) {
                    ClientCertHandler.handleClientCertRequest(activity, request)
                } else {
                    request.cancel()
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgressChange(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                callbacks.onTitleChange(title)
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                val pageUrl = view?.url?.takeIf { it.isNotBlank() } ?: return
                callbacks.onFaviconReceived(pageUrl, icon)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    callbacks.onEnterFullscreen(view, callback)
                } else {
                    super.onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                callbacks.onExitFullscreen()
                super.onHideCustomView()
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) {
                    return
                }

                val allowed = setOf(
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                )

                val grantable = request.resources.filter { it in allowed }.toTypedArray()

                if (grantable.isEmpty()) {
                    request.deny()
                    return
                }

                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in grantable) {
                    callbacks.onPermissionRequest(request)
                    return
                }

                // Widevine: View.post() is dropped while the WebView is detached, which would
                // silently starve the licence request, so grant on the main looper instead.
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    request.grant(grantable)
                } else {
                    Handler(Looper.getMainLooper()).post { request.grant(grantable) }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                callbacks.onGeolocationPermissionRequest(origin, callback)
            }

            /**
             * With multiple windows enabled, returning false here makes the WebView drop every
             * target="_blank" link and window.open() call without a trace. A throwaway WebView is
             * handed to the transport purely to capture the destination URL, then destroyed.
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) {
                    return false
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

                val host = view ?: webView
                val popup = runCatching { WebView(host.context) }.getOrNull() ?: return false
                val mainHandler = Handler(Looper.getMainLooper())
                val settled = java.util.concurrent.atomic.AtomicBoolean(false)

                val dispose = Runnable {
                    popup.stopLoading()
                    popup.webChromeClient = null
                    popup.webViewClient = WebViewClient()
                    (popup.parent as? android.view.ViewGroup)?.removeView(popup)
                    popup.removeAllViews()
                    popup.destroy()
                }

                fun capture(target: String?): Boolean {
                    val url = target?.takeIf { it.isNotBlank() } ?: return false
                    if (!settled.compareAndSet(false, true)) {
                        return true
                    }
                    val openedInNewTab = BrowserPreferences.isOpenPopupsInNewTabEnabled(appContext) &&
                        callbacks.onOpenInNewTab(url)
                    if (!openedInNewTab) {
                        mainHandler.post { webView.loadUrl(url) }
                    }
                    mainHandler.post(dispose)
                    return true
                }

                popup.settings.javaScriptEnabled = true
                popup.settings.javaScriptCanOpenWindowsAutomatically = true
                popup.settings.userAgentString = host.settings.userAgentString
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        popupView: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        return capture(request.url?.toString())
                    }

                    @Deprecated("Kept for WebView implementations that still use the string overload")
                    override fun shouldOverrideUrlLoading(popupView: WebView, url: String?): Boolean {
                        return capture(url)
                    }

                    override fun onPageStarted(popupView: WebView, url: String?, favicon: Bitmap?) {
                        if (!capture(url)) {
                            super.onPageStarted(popupView, url, favicon)
                        }
                    }
                }

                mainHandler.postDelayed({
                    if (settled.compareAndSet(false, true)) {
                        dispose.run()
                    }
                }, POPUP_CAPTURE_TIMEOUT_MS)

                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }

        setDownloadListener(DownloadListener { url, _, _, _, _ ->
            val uri = url?.takeIf { it.isNotBlank() }?.toUri()
            if (uri == null) {
                return@DownloadListener
            }
            callbacks.onShowDownloadPrompt(uri)
        })
    }
}

private fun handleCleartextIfNeeded(view: WebView, uri: Uri?, callbacks: BrowserCallbacks, onPageStart: Boolean = false): Boolean {
    if (uri == null) {
        return false
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme == null) {
        return false
    }
    if (scheme != "http") {
        return false
    }

    val allowedOnce = view.getTag(R.id.webview_allow_once_uri_tag) as? String
    if (allowedOnce == uri.toString()) {
        view.setTag(R.id.webview_allow_once_uri_tag, null)
        return false
    }

    val host = uri.host?.lowercase()
    if (com.yashmaurya.roadbrowser.data.BrowserPreferences.isHostAllowedCleartext(view.context, host)) {
        return false
    }
    if (onPageStart) view.stopLoading()
    val allowOnce = {
        view.setTag(R.id.webview_allow_once_uri_tag, uri.toString())
        view.post { view.loadUrl(uri.toString()) }
        kotlin.Unit
    }
    val allowHost = {
        view.context?.let { ctx ->
            val hostToStore = uri.host?.lowercase()
            if (hostToStore != null) com.yashmaurya.roadbrowser.data.BrowserPreferences.addAllowedCleartextHost(ctx, hostToStore)
        }
        view.setTag(R.id.webview_allow_once_uri_tag, uri.toString())
        view.post { view.loadUrl(uri.toString()) }
        kotlin.Unit
    }
    val cancel = {
        if (onPageStart) view.stopLoading()
        kotlin.Unit
    }
    callbacks.onCleartextNavigationRequested(uri, allowOnce, allowHost, cancel)
    return true
}

fun WebView.updateDesktopMode(enable: Boolean, profile: UserAgentProfile) {
    applyBrowserIdentity(profile, enable)
    reload()
}

fun WebView.updateUserAgentProfile(profile: UserAgentProfile, desktop: Boolean) {
    applyBrowserIdentity(profile, desktop)
    reload()
}

/**
 * Applies the identity a host-initiated load needs *before* it starts. Navigations routed through
 * shouldOverrideUrlLoading are already covered; loadUrl() calls made by the app are not, and would
 * otherwise fetch the document with the wrong user agent and be reloaded from onPageStarted.
 */
fun WebView.prepareBrowserIdentityFor(url: String?) {
    syncBrowserIdentity(url)
}

fun WebView.updatePageDarkening(enabled: Boolean) {
    applyPageDarkening(enabled)
    reload()
}

fun WebView.releaseCompletely() {
    stopLoading()
    webChromeClient = null
    webViewClient = WebViewClient()
    setDownloadListener(null)
    clearHistory()
    (parent as? android.view.ViewGroup)?.removeView(this)
    removeAllViews()
    destroy()
}

private fun WebView.applyBrowserIdentity(profile: UserAgentProfile, desktop: Boolean) {
    setTag(R.id.webview_user_agent_profile_tag, profile.storageKey)
    setTag(R.id.webview_desktop_mode_tag, desktop)
    applyIdentityFor(profile, desktop, url)
}

/**
 * Re-applies the identity for the page being navigated to, since YouTube compat mode overrides
 * the user's desktop choice per host. Returns true when the user agent actually changed.
 */
private fun WebView.syncBrowserIdentity(url: String?): Boolean {
    val profile = UserAgentProfile.fromKey(getTag(R.id.webview_user_agent_profile_tag) as? String)
    val desktop = getTag(R.id.webview_desktop_mode_tag) as? Boolean ?: false
    return applyIdentityFor(profile, desktop, url)
}

private fun WebView.applyIdentityFor(profile: UserAgentProfile, desktop: Boolean, url: String?): Boolean {
    val forceMobile = YouTubeCompat.appliesTo(context, url)
    val effectiveDesktop = desktop && !forceMobile
    val effectiveProfile = if (forceMobile) UserAgentProfile.ANDROID_CHROME else profile
    val userAgent = buildUserAgent(effectiveProfile, effectiveDesktop)
    val changed = settings.userAgentString != userAgent

    settings.userAgentString = userAgent
    settings.useWideViewPort = effectiveDesktop
    settings.loadWithOverviewMode = effectiveDesktop

    val scalePercent = BrowserPreferences.getGlobalScalePercent(context)
    if (effectiveDesktop) {
        setInitialScale(0)
        settings.textZoom = scalePercent
    } else {
        setInitialScale(mobileInitialScalePercent())
        settings.textZoom = 100
    }

    applyUserAgentMetadata(effectiveProfile, effectiveDesktop)
    return changed
}

private fun WebView.mobileInitialScalePercent(): Int {
    return (context.resources.displayMetrics.density * 100).toInt()
}

private fun WebView.applyUserAgentMetadata(profile: UserAgentProfile, desktop: Boolean) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
        return
    }

    val metadata = when (profile) {
        UserAgentProfile.ANDROID_CHROME -> buildChromeUserAgentMetadata(desktop)
        UserAgentProfile.SAFARI -> buildSafariLikeUserAgentMetadata(desktop)
    }
    WebSettingsCompat.setUserAgentMetadata(settings, metadata)
}

private fun WebView.applyPageDarkening(enabled: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
    }
}

private fun buildUserAgent(profile: UserAgentProfile, desktop: Boolean): String {
    return when (profile) {
        UserAgentProfile.ANDROID_CHROME -> if (desktop) WINDOWS_CHROME_UA else MOBILE_CHROME_UA
        UserAgentProfile.SAFARI -> if (desktop) SAFARI_MAC_UA else SAFARI_IOS_UA
    }
}

private fun buildChromeUserAgentMetadata(desktop: Boolean): UserAgentMetadata {
    return UserAgentMetadata.Builder()
        .setBrandVersionList(chromeBrandVersions())
        .setFullVersion(CHROME_VERSION)
        .setPlatform(if (desktop) "Windows" else "Android")
        .setPlatformVersion(if (desktop) WINDOWS_PLATFORM_VERSION else ANDROID_PLATFORM_VERSION)
        .setArchitecture(if (desktop) "x86" else "")
        .setModel("")
        .setMobile(!desktop)
        .setBitness(if (desktop) DESKTOP_BITNESS else UserAgentMetadata.BITNESS_DEFAULT)
        .setWow64(false)
        .build()
}

private fun buildSafariLikeUserAgentMetadata(desktop: Boolean): UserAgentMetadata {
    return UserAgentMetadata.Builder()
        .setPlatform(if (desktop) "macOS" else "iOS")
        .setPlatformVersion(if (desktop) MACOS_PLATFORM_VERSION else IOS_PLATFORM_VERSION)
        .setArchitecture(if (desktop) "arm" else "")
        .setModel("")
        .setMobile(!desktop)
        .setBitness(if (desktop) DESKTOP_BITNESS else UserAgentMetadata.BITNESS_DEFAULT)
        .setWow64(false)
        .build()
}

private fun chromeBrandVersions(): List<UserAgentMetadata.BrandVersion> {
    val majorVersion = CHROME_VERSION.substringBefore('.')
    return listOf(
        UserAgentMetadata.BrandVersion.Builder()
            .setBrand("Chromium")
            .setMajorVersion(majorVersion)
            .setFullVersion(CHROME_VERSION)
            .build(),
        UserAgentMetadata.BrandVersion.Builder()
            .setBrand("Google Chrome")
            .setMajorVersion(majorVersion)
            .setFullVersion(CHROME_VERSION)
            .build()
    )
}

private const val DESKTOP_INITIAL_SCALE_PERCENT = 100
private const val DESKTOP_BITNESS = 64
private const val POPUP_CAPTURE_TIMEOUT_MS = 15_000L
private const val CHROME_VERSION = "149.0.0.0"
private const val ANDROID_PLATFORM_VERSION = "10.0.0"
private const val WINDOWS_PLATFORM_VERSION = "10.0.0"
private const val MACOS_PLATFORM_VERSION = "14.0.0"
private const val IOS_PLATFORM_VERSION = "17.0.0"
private const val MOBILE_CHROME_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION} Mobile Safari/537.36"
private const val WINDOWS_CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROME_VERSION} Safari/537.36"
private const val SAFARI_MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
private const val SAFARI_IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
