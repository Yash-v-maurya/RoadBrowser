package com.yashmaurya.roadbrowser.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yashmaurya.roadbrowser.MainActivity
import com.yashmaurya.roadbrowser.databinding.ActivityWelcomeBinding
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Nothing opens until the user has agreed to the terms, the privacy policy and the safety rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WelcomeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun browserSendsFirstTimeUsersToTheAgreement_keepingTheLinkTheyOpened() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
            .setClass(context, MainActivity::class.java)
        val controller = Robolectric.buildActivity(MainActivity::class.java, link).create()

        val started = shadowOf(controller.get()).nextStartedActivity
        assertEquals(WelcomeActivity::class.java.name, started.component?.className)
        assertTrue(controller.get().isFinishing)
        assertEquals("https://example.com/", started.getParcelableExtra("next", Intent::class.java)?.dataString)
    }

    @Test
    fun acceptStaysDisabledUntilAllThreeBoxesAreTicked() {
        val (activity, views) = launch()

        assertFalse(views.acceptButton.isEnabled)
        views.checkTerms.isChecked = true
        views.checkPrivacy.isChecked = true
        assertFalse(views.acceptButton.isEnabled)
        views.checkSafety.isChecked = true
        assertTrue(views.acceptButton.isEnabled)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun acceptingRecordsTheVersion_thenOffersAndroidAutoSetup() {
        val (activity, views) = launch()
        listOf(views.checkTerms, views.checkPrivacy, views.checkSafety).forEach { it.isChecked = true }

        views.acceptButton.performClick()
        assertTrue(BrowserPreferences.hasAcceptedCurrentTerms(context))
        assertTrue(BrowserPreferences.getTermsAcceptedAt(context) > 0)

        views.setupNowButton.performClick()
        val shadow = shadowOf(activity)
        val first = shadow.nextStartedActivity
        val second = shadow.nextStartedActivity
        assertEquals(setOf(MainActivity::class.java.name, CarSetupActivity::class.java.name), setOf(first.component?.className, second.component?.className))
    }

    @Test
    fun decliningClosesWithoutRecordingAnything() {
        val (activity, views) = launch()

        views.declineButton.performClick()

        assertTrue(activity.isFinishing)
        assertFalse(BrowserPreferences.hasAcceptedCurrentTerms(context))
    }

    private fun launch(): Pair<WelcomeActivity, ActivityWelcomeBinding> {
        val activity = Robolectric.buildActivity(WelcomeActivity::class.java, WelcomeActivity.intent(context, null)).setup().get()
        return activity to ActivityWelcomeBinding.bind(activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))
    }
}
