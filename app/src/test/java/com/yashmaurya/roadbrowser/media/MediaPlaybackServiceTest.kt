package com.yashmaurya.roadbrowser.media

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * The service is shared by two players: the browser tabs and the hidden car player that Android
 * Auto's media screen drives while the browser itself is blocked. These pin down who gets the
 * buttons, who gets paused, and who may browse the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShadowWebSettingsCompat::class], instrumentedPackages = ["androidx.webkit"])
class MediaPlaybackServiceTest {

    private lateinit var context: Context
    private lateinit var controller: ServiceController<MediaPlaybackService>
    private lateinit var service: MediaPlaybackService
    private val tabActions = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BrowserPreferences.setBookmarks(context, listOf("https://radio.example/live"))
        BrowserPreferences.acceptCurrentTerms(context)
        MediaPlaybackService.actionHandler = MediaPlaybackService.MediaActionHandler { tabActions += it }
        controller = Robolectric.buildService(MediaPlaybackService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        MediaPlaybackService.actionHandler = null
        controller.destroy()
    }

    @Test
    fun onlyAndroidAutoTheSystemAndTheAppMayBrowseTheList() {
        assertNotNull(service.onGetRoot("com.google.android.projection.gearhead", 10_123, null))
        assertNotNull(service.onGetRoot("com.android.systemui", 10_050, null))
        assertNotNull(service.onGetRoot(context.packageName, Process.myUid(), null))
        assertNull(service.onGetRoot("com.example.snooper", 10_999, null))
    }

    @Test
    fun pickingAPageInTheCar_pausesTheTabAndLoadsItInTheHiddenPlayer() {
        playInBrowserTab()

        service.playFromMediaId("page:https://radio.example/live")

        assertEquals(listOf("pause"), tabActions)
        assertEquals("https://radio.example/live", shadowOf(BackgroundWebPlayer.loadedWebView).lastLoadedUrl)
    }

    @Test
    fun pagesOutsideTheList_areIgnored() {
        playInBrowserTab()

        service.playFromMediaId("page:https://attacker.example/")

        assertTrue(tabActions.isEmpty())
        assertFalse(BackgroundWebPlayer.isLoaded)
    }

    @Test
    fun whileTheCarPlayerOwnsTheSession_buttonsGoToItAndNotToTheTab() {
        service.playFromMediaId("page:https://radio.example/live")
        tabActions.clear()
        // The tab reporting "paused" after being paused must not take the session back.
        MediaPlaybackService.update(context, MediaPlaybackService.Source.BROWSER, false, "Song", "", "https://music.example/")

        service.forward("nexttrack")

        assertTrue(tabActions.isEmpty())
        assertTrue(shadowOf(BackgroundWebPlayer.loadedWebView).lastEvaluatedJavascript.contains("nexttrack"))
    }

    @Test
    fun tabStartingToPlayAgain_takesTheSessionBackAndPausesTheCarPlayer() {
        service.playFromMediaId("page:https://radio.example/live")
        tabActions.clear()

        playInBrowserTab()
        service.forward("pause")

        assertTrue(shadowOf(BackgroundWebPlayer.loadedWebView).lastEvaluatedJavascript.contains("pause"))
        assertEquals(listOf("pause"), tabActions)
    }

    @Test
    fun closingTheBrowser_leavesCarPlaybackRunning() {
        service.playFromMediaId("page:https://radio.example/live")

        MediaPlaybackService.onBrowserClosed()

        assertTrue(BackgroundWebPlayer.isLoaded)
    }

    @Test
    fun playWithNothingLoaded_resumesTheLastPlayedPage() {
        BrowserPreferences.setLastMediaPage(context, "https://radio.example/live", "Radio")

        service.resume()

        assertEquals("https://radio.example/live", shadowOf(BackgroundWebPlayer.loadedWebView).lastLoadedUrl)
    }

    @Test
    fun voiceSearch_playsTheBestMatchingPage() {
        service.playFromSearch("live radio")

        assertEquals("https://radio.example/live", shadowOf(BackgroundWebPlayer.loadedWebView).lastLoadedUrl)
    }

    @Test
    fun voiceSearchWithoutAMatch_loadsNothing() {
        service.playFromSearch("jazz")

        assertFalse(BackgroundWebPlayer.isLoaded)
    }

    @Test
    fun emptyVoiceSearch_resumesTheLastPlayedPage() {
        BrowserPreferences.setLastMediaPage(context, "https://radio.example/live", "Radio")

        service.playFromSearch("")

        assertEquals("https://radio.example/live", shadowOf(BackgroundWebPlayer.loadedWebView).lastLoadedUrl)
    }

    @Test
    fun nothingPlaysInTheCarBeforeTheTermsAreAccepted() {
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE).edit().remove("accepted_terms_version").commit()

        service.playFromMediaId("page:https://radio.example/live")
        service.playFromSearch("radio")

        assertFalse(BackgroundWebPlayer.isLoaded)
    }

    @Test
    fun tabPlayback_isRememberedForTheCarList() {
        playInBrowserTab()

        assertEquals("https://music.example/a", BrowserPreferences.getLastMediaPage(context)?.url)
    }

    private fun playInBrowserTab() {
        MediaPlaybackService.update(context, MediaPlaybackService.Source.BROWSER, true, "Song", "Band", "https://music.example/a")
    }
}
