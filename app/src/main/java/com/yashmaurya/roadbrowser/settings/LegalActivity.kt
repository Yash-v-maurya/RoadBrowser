package com.yashmaurya.roadbrowser.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.yashmaurya.roadbrowser.MainActivity
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityLegalBinding

/**
 * Shows one of the legal pages bundled in `assets/legal` (privacy policy, terms of use, driving
 * safety, open-source notices). They ship with the app so they open offline, car screen
 * included. The pages link to each other; links to other sites are handed to the browser.
 * The HTML is generated from `docs/legal` by `scripts/build_legal.py`.
 */
class LegalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLegalBinding

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { BrowserPreferences.createScaledContext(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLegalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topAppBar.setNavigationOnClickListener { finish() }

        val webView = binding.legalWebView
        webView.setBackgroundColor(Color.TRANSPARENT)
        // Static pages: no scripts needed, and none allowed.
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (isLegalPage(uri)) return false
                openInBrowser(uri)
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.title?.substringBefore(" · ")?.takeIf { it.isNotBlank() }?.let { binding.topAppBar.title = it }
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(pageUrl(intent.getStringExtra(EXTRA_PAGE)))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.legalWebView.saveState(outState)
    }

    override fun onDestroy() {
        binding.legalWebView.destroy()
        super.onDestroy()
    }

    private fun openInBrowser(uri: Uri) {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        // Back to the existing browser window (never a second one), which opens the link.
        startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    companion object {
        const val PRIVACY = "privacy"
        const val TERMS = "terms"
        const val DRIVING_SAFETY = "driving-safety"
        const val NOTICES = "notices"

        private const val EXTRA_PAGE = "page"
        private const val ASSET_PREFIX = "/android_asset/legal/"
        private val PAGES = setOf(PRIVACY, TERMS, DRIVING_SAFETY, NOTICES)

        fun intent(context: Context, page: String): Intent =
            Intent(context, LegalActivity::class.java).putExtra(EXTRA_PAGE, page)

        internal fun pageUrl(page: String?): String {
            val known = page?.takeIf { it in PAGES } ?: PRIVACY
            return "file://$ASSET_PREFIX$known.html"
        }

        internal fun isLegalPage(uri: Uri): Boolean {
            return uri.scheme == "file" && uri.path?.startsWith(ASSET_PREFIX) == true && !uri.path.orEmpty().contains("..")
        }
    }
}
