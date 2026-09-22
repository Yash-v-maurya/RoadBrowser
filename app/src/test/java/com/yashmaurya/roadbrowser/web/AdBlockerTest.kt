package com.yashmaurya.roadbrowser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure host-matching logic. Anything that needs a Context or a Uri is left to
 * instrumented tests; these run on the JVM against the stubbed android.jar.
 */
class AdBlockerTest {

    @Test
    fun mediaCriticalHost_matchesExactDomain() {
        assertTrue(AdBlocker.isMediaCriticalHost("googlevideo.com"))
        assertTrue(AdBlocker.isMediaCriticalHost("youtube.com"))
        assertTrue(AdBlocker.isMediaCriticalHost("ytimg.com"))
    }

    @Test
    fun mediaCriticalHost_matchesSubdomains() {
        assertTrue(AdBlocker.isMediaCriticalHost("rr3---sn-ab5l6nzy.googlevideo.com"))
        assertTrue(AdBlocker.isMediaCriticalHost("i.ytimg.com"))
        assertTrue(AdBlocker.isMediaCriticalHost("yt3.ggpht.com"))
        assertTrue(AdBlocker.isMediaCriticalHost("www.youtube.com"))
    }

    @Test
    fun mediaCriticalHost_isCaseAndTrailingDotInsensitive() {
        assertTrue(AdBlocker.isMediaCriticalHost("RR1---SN-ABC.GoogleVideo.Com"))
        assertTrue(AdBlocker.isMediaCriticalHost("i.ytimg.com."))
    }

    @Test
    fun mediaCriticalHost_rejectsUnrelatedAndLookalikeHosts() {
        assertFalse(AdBlocker.isMediaCriticalHost("doubleclick.net"))
        assertFalse(AdBlocker.isMediaCriticalHost("example.com"))
        assertFalse(AdBlocker.isMediaCriticalHost(null))
        assertFalse(AdBlocker.isMediaCriticalHost(""))
        // Suffix matching must be label-aware: this is not a youtube.com subdomain.
        assertFalse(AdBlocker.isMediaCriticalHost("notyoutube.com"))
        assertFalse(AdBlocker.isMediaCriticalHost("youtube.com.evil.example"))
    }

    @Test
    fun blockedHost_isFalseWhenBlocklistNeverLoaded() {
        // No Context here, so the list is empty; matching must degrade to "allow", never crash.
        assertFalse(AdBlocker.isBlockedHost("doubleclick.net"))
        assertFalse(AdBlocker.isBlockedHost(null))
    }
}
