package com.yashmaurya.roadbrowser.settings

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.yashmaurya.roadbrowser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The legal pages must ship with the app, open from Settings, and stay inside `assets/legal`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegalPagesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyPageIsBundledWithItsTitle() {
        val expected = mapOf(
            LegalActivity.PRIVACY to "Privacy Policy",
            LegalActivity.TERMS to "Terms of Use",
            LegalActivity.DRIVING_SAFETY to "Driving Safety",
            LegalActivity.NOTICES to "Open-Source Notices",
            LegalActivity.ANDROID_AUTO_SETUP to "Android Auto Setup"
        )
        expected.forEach { (page, title) ->
            val html = context.assets.open("legal/$page.html").bufferedReader().use { it.readText() }
            assertTrue("$page title", html.contains("<title>$title · RoadBrowser</title>"))
            assertFalse("$page must not link to Markdown", html.contains(".md\""))
        }
    }

    @Test
    fun activityOpensTheRequestedPage_andFallsBackToThePrivacyPolicy() {
        assertEquals("file:///android_asset/legal/terms.html", loadedUrlFor(LegalActivity.TERMS))
        assertEquals("file:///android_asset/legal/privacy.html", loadedUrlFor("../../databases/secret"))
    }

    @Test
    fun onlyBundledLegalPagesStayInTheViewer() {
        assertTrue(LegalActivity.isLegalPage(Uri.parse("file:///android_asset/legal/notices.html")))
        assertFalse(LegalActivity.isLegalPage(Uri.parse("https://github.com/Yash-v-maurya/RoadBrowser")))
        assertFalse(LegalActivity.isLegalPage(Uri.parse("file:///android_asset/error.html")))
        assertFalse(LegalActivity.isLegalPage(Uri.parse("file:///android_asset/legal/../blocklist.txt")))
        assertFalse(LegalActivity.isLegalPage(Uri.parse("file:///data/data/com.yashmaurya.roadbrowser/legal/x.html")))
    }

    @Test
    fun settingsLegalButtonsOpenTheMatchingPage() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val buttons = mapOf(
            R.id.viewPrivacyPolicyButton to LegalActivity.PRIVACY,
            R.id.viewTermsButton to LegalActivity.TERMS,
            R.id.viewDrivingSafetyButton to LegalActivity.DRIVING_SAFETY,
            R.id.viewNoticesButton to LegalActivity.NOTICES
        )
        buttons.forEach { (id, page) ->
            activity.findViewById<MaterialButton>(id).performClick()
            val started = shadowOf(activity).nextStartedActivity
            assertEquals(LegalActivity::class.java.name, started.component?.className)
            assertEquals(page, started.getStringExtra("page"))
        }
    }

    private fun loadedUrlFor(page: String): String? {
        val intent = LegalActivity.intent(context, page)
        val activity = Robolectric.buildActivity(LegalActivity::class.java, intent).setup().get()
        return shadowOf(activity.findViewById<WebView>(R.id.legalWebView)).lastLoadedUrl
    }
}
