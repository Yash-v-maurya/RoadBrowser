package com.yashmaurya.roadbrowser.web

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight ad/tracker blocker in the spirit of Brave Shields.
 *
 * Requests are matched by host against a bundled domain list (assets/blocklist.txt).
 * Matching walks the parent domains, so a single "doubleclick.net" entry also covers
 * "stats.g.doubleclick.net". Main-frame navigations are never blocked, and requests
 * back to the page's own domain are treated as first party and left alone.
 *
 * Playback and DRM hosts in [MEDIA_CRITICAL_DOMAINS] are checked first and can never be
 * blocked, so a future blocklist entry cannot break video.
 */
object AdBlocker {

    private const val BLOCKLIST_ASSET = "blocklist.txt"
    private const val MIN_MATCHABLE_LABELS = 2

    private val MEDIA_CRITICAL_DOMAINS = setOf(
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com",
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "youtubei.googleapis.com",
        "jnn-pa.googleapis.com",
        "gvt1.com",
        "gstatic.com",
        "googleapis.com",
        "googleusercontent.com",
        "widevine.com",
        "license.widevine.com"
    )

    @Volatile
    private var blockedDomains: Set<String> = emptySet()

    @Volatile
    private var loaded = false

    private val sessionBlockCount = AtomicLong(0)

    val blockedThisSession: Long
        get() = sessionBlockCount.get()

    fun resetSessionCount() {
        sessionBlockCount.set(0)
    }

    /** Parses the bundled list once. Safe to call from any thread. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            blockedDomains = runCatching { parseBlocklist(context) }.getOrDefault(emptySet())
            loaded = true
        }
    }

    private fun parseBlocklist(context: Context): Set<String> {
        return context.applicationContext.assets.open(BLOCKLIST_ASSET).bufferedReader().useLines { lines ->
            lines.map { it.substringBefore('#').trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }

    fun isBlockedHost(host: String?): Boolean {
        if (blockedDomains.isEmpty()) return false
        return matchesDomain(host, blockedDomains)
    }

    /** Hosts that must always load: media segments, thumbnails, player APIs and DRM licences. */
    fun isMediaCriticalHost(host: String?): Boolean = matchesDomain(host, MEDIA_CRITICAL_DOMAINS)

    private fun matchesDomain(host: String?, domains: Set<String>): Boolean {
        if (host.isNullOrBlank() || domains.isEmpty()) return false
        val normalized = host.lowercase().removeSuffix(".")
        if (normalized in domains) return true

        var index = normalized.indexOf('.')
        while (index in 0 until normalized.lastIndex) {
            val parent = normalized.substring(index + 1)
            if (parent.count { it == '.' } + 1 < MIN_MATCHABLE_LABELS) break
            if (parent in domains) return true
            index = normalized.indexOf('.', index + 1)
        }
        return false
    }

    /**
     * Returns an empty response when [requestUrl] should be blocked, or null to let it through.
     * [pageUrl] is the URL of the page making the request, used for the first-party exemption.
     */
    fun interceptOrNull(requestUrl: Uri?, pageUrl: String?, isMainFrame: Boolean): WebResourceResponse? {
        if (isMainFrame || requestUrl == null) return null

        val scheme = requestUrl.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null

        val requestHost = requestUrl.host ?: return null
        if (isMediaCriticalHost(requestHost)) return null
        if (isFirstParty(requestHost, pageUrl)) return null
        if (!isBlockedHost(requestHost)) return null

        sessionBlockCount.incrementAndGet()
        return emptyResponse()
    }

    private fun isFirstParty(requestHost: String, pageUrl: String?): Boolean {
        val pageHost = pageUrl?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
            ?: return false
        return registrableDomain(requestHost) == registrableDomain(pageHost)
    }

    private fun registrableDomain(host: String): String {
        val labels = host.lowercase().removeSuffix(".").split('.')
        if (labels.size <= MIN_MATCHABLE_LABELS) return labels.joinToString(".")
        return labels.takeLast(MIN_MATCHABLE_LABELS).joinToString(".")
    }

    /**
     * A real 204 rather than the 3-argument constructor's implicit 200 with an empty body:
     * players and trackers that parse the response hang or throw when handed an empty 200.
     */
    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
