package com.yashmaurya.roadbrowser.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Android Auto media list is the only way to make the hidden car player load a page, so it
 * must list exactly the user's own pages and refuse anything else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarMediaCatalogTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BrowserPreferences.setBookmarks(context, listOf("https://radio.example/live", "https://music.youtube.com/"))
        for (slot in 0 until BrowserPreferences.MAX_START_PAGE_SITES) {
            BrowserPreferences.clearStartPageSlot(context, slot)
        }
        BrowserPreferences.setStartPageSlot(context, 0, "https://podcasts.example/")
    }

    @Test
    fun root_offersQuickLinksAndBookmarksAsBrowsableCategories() {
        val root = CarMediaCatalog.children(context, CarMediaCatalog.ROOT_ID)

        assertEquals(listOf("Quick links", "Bookmarks"), root.map { it.description.title.toString() })
        assertTrue(root.all { it.isBrowsable && !it.isPlayable })
    }

    @Test
    fun listedPages_arePlayableAndResolveBackToTheirUrl() {
        val bookmarks = CarMediaCatalog.children(context, bookmarksId())

        assertTrue(bookmarks.all { it.isPlayable })
        assertEquals(
            listOf("https://radio.example/live", "https://music.youtube.com/"),
            bookmarks.map { CarMediaCatalog.pageFor(context, it.mediaId)?.url }
        )
    }

    @Test
    fun pageFor_refusesUrlsThatAreNotInTheList() {
        assertNull(CarMediaCatalog.pageFor(context, "page:https://attacker.example/"))
        assertNull(CarMediaCatalog.pageFor(context, "https://radio.example/live"))
        assertNull(CarMediaCatalog.pageFor(context, null))
    }

    @Test
    fun quickLinks_startWithTheLastPlayedPageWithoutDuplicatingIt() {
        BrowserPreferences.setLastMediaPage(context, "https://podcasts.example/", "Morning show")

        val quickLinks = CarMediaCatalog.children(context, quickLinksId())

        assertEquals(listOf("Morning show"), quickLinks.map { it.description.title.toString() })
        assertEquals("https://podcasts.example/", CarMediaCatalog.pageFor(context, quickLinks.first().mediaId)?.url)
    }

    @Test
    fun lastPlayed_ignoresNonWebUrls() {
        BrowserPreferences.setLastMediaPage(context, "https://radio.example/live", "Radio")
        BrowserPreferences.setLastMediaPage(context, "javascript:alert(1)", "Nope")

        assertEquals("https://radio.example/live", CarMediaCatalog.lastPlayed(context)?.url)
    }

    @Test
    fun search_picksThePageMatchingMostWordsOfTheVoiceRequest() {
        assertEquals("https://music.youtube.com/", CarMediaCatalog.search(context, "YouTube Music")?.url)
        assertEquals("https://radio.example/live", CarMediaCatalog.search(context, "the live radio")?.url)
        assertNull(CarMediaCatalog.search(context, "jazz"))
        assertNull(CarMediaCatalog.search(context, "  "))
    }

    private fun quickLinksId() = CarMediaCatalog.children(context, CarMediaCatalog.ROOT_ID)[0].mediaId!!

    private fun bookmarksId() = CarMediaCatalog.children(context, CarMediaCatalog.ROOT_ID)[1].mediaId!!
}
