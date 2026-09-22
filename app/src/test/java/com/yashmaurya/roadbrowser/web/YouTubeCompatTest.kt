package com.yashmaurya.roadbrowser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host matching decides whether YouTube gets pinned to the mobile identity, so a lookalike host
 * must never be able to opt itself into the override.
 */
class YouTubeCompatTest {

    @Test
    fun isYouTubeHost_matchesKnownDomainsAndSubdomains() {
        assertTrue(YouTubeCompat.isYouTubeHost("youtube.com"))
        assertTrue(YouTubeCompat.isYouTubeHost("www.youtube.com"))
        assertTrue(YouTubeCompat.isYouTubeHost("m.youtube.com"))
        assertTrue(YouTubeCompat.isYouTubeHost("music.youtube.com"))
        assertTrue(YouTubeCompat.isYouTubeHost("youtu.be"))
        assertTrue(YouTubeCompat.isYouTubeHost("www.youtube-nocookie.com"))
    }

    @Test
    fun isYouTubeHost_isCaseAndTrailingDotInsensitive() {
        assertTrue(YouTubeCompat.isYouTubeHost("WWW.YouTube.COM"))
        assertTrue(YouTubeCompat.isYouTubeHost("m.youtube.com."))
    }

    @Test
    fun isYouTubeHost_rejectsLookalikes() {
        assertFalse(YouTubeCompat.isYouTubeHost("notyoutube.com"))
        assertFalse(YouTubeCompat.isYouTubeHost("youtube.com.phish.example"))
        assertFalse(YouTubeCompat.isYouTubeHost("myyoutu.be"))
        assertFalse(YouTubeCompat.isYouTubeHost("example.com"))
        assertFalse(YouTubeCompat.isYouTubeHost(null))
        assertFalse(YouTubeCompat.isYouTubeHost(""))
    }
}
