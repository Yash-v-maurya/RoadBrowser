package com.yashmaurya.roadbrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Updates may only ever come from this repository's own release assets, and only move forward. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdaterTest {

    private val release = """
        {"tag_name": "v2.4.0", "html_url": "https://github.com/Yash-v-maurya/RoadBrowser/releases/tag/v2.4.0",
         "assets": [
           {"name": "RoadBrowser.apk", "browser_download_url": "https://github.com/Yash-v-maurya/RoadBrowser/releases/download/v2.4.0/RoadBrowser.apk"},
           {"name": "RoadBrowser-2.4.0.apk", "browser_download_url": "https://github.com/Yash-v-maurya/RoadBrowser/releases/download/v2.4.0/RoadBrowser-2.4.0.apk"},
           {"name": "notes.txt", "browser_download_url": "https://github.com/Yash-v-maurya/RoadBrowser/releases/download/v2.4.0/notes.txt"}
         ]}
    """.trimIndent()

    @Test
    fun parseRelease_prefersTheVersionedApk() {
        val parsed = AppUpdater.parseRelease(release)

        assertEquals("2.4.0", parsed.version)
        assertEquals("https://github.com/Yash-v-maurya/RoadBrowser/releases/download/v2.4.0/RoadBrowser-2.4.0.apk", parsed.apkUrl)
    }

    @Test
    fun parseRelease_ignoresApksHostedAnywhereElse() {
        val foreign = release.replace("https://github.com/Yash-v-maurya/RoadBrowser/releases/download", "https://evil.example/download")

        assertNull(AppUpdater.parseRelease(foreign).apkUrl)
    }

    @Test
    fun onlyThisRepositorysReleaseAssetsCount() {
        assertTrue(AppUpdater.isOfficialAssetUrl("https://github.com/Yash-v-maurya/RoadBrowser/releases/download/v2.4.0/RoadBrowser.apk"))
        assertFalse(AppUpdater.isOfficialAssetUrl("http://github.com/Yash-v-maurya/RoadBrowser/releases/download/v2.4.0/RoadBrowser.apk"))
        assertFalse(AppUpdater.isOfficialAssetUrl("https://github.com/someone-else/RoadBrowser/releases/download/v9/RoadBrowser.apk"))
        assertFalse(AppUpdater.isOfficialAssetUrl("https://github.com.evil.example/Yash-v-maurya/RoadBrowser/releases/download/x.apk"))
    }

    @Test
    fun isNewer_comparesDottedVersionsNumerically() {
        assertTrue(AppUpdater.isNewer("2.3.1", "2.3"))
        assertTrue(AppUpdater.isNewer("v2.10", "2.9.9"))
        assertFalse(AppUpdater.isNewer("2.3", "2.3.0"))
        assertFalse(AppUpdater.isNewer("2.2", "2.3.1"))
    }
}
