package com.yashmaurya.roadbrowser.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.data.CrashLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The setup checklist, the crash log, and the Settings entries that lead to them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarSetupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        CrashLog.clear(context)
        BrowserPreferences.setBackgroundAudioEnabled(context, true)
    }

    @Test
    fun missingAndroidAuto_isFlaggedWithAnInstallButton() {
        val first = CarSetupChecks.steps(context).first()

        assertEquals(CarSetupChecks.Status.TODO, first.status)
        assertEquals(CarSetupChecks.Action.INSTALL_ANDROID_AUTO, first.action)
    }

    @Test
    fun installedAndroidAuto_isDone_andItsSettingsCanBeOpened() {
        installAndroidAuto()

        val steps = CarSetupChecks.steps(context)

        assertEquals(CarSetupChecks.Status.DONE, steps[0].status)
        assertNull(steps[0].action)
        assertEquals(CarSetupChecks.Action.OPEN_ANDROID_AUTO_SETTINGS, steps[1].action)
        assertEquals(CarSetupChecks.Status.MANUAL, steps[1].status)
    }

    @Test
    fun backgroundAudioOff_isFlagged_andTheButtonTurnsItOn() {
        BrowserPreferences.setBackgroundAudioEnabled(context, false)
        val activity = Robolectric.buildActivity(CarSetupActivity::class.java).setup().get()

        val backgroundAudioCard = activity.findViewById<LinearLayout>(R.id.setupSteps).getChildAt(6)
        backgroundAudioCard.findViewById<MaterialButton>(R.id.stepAction).performClick()

        assertTrue(BrowserPreferences.isBackgroundAudioEnabled(context))
        assertFalse(CarSetupChecks.steps(context).any { it.titleRes == R.string.car_setup_background_audio_title && it.status != CarSetupChecks.Status.DONE })
    }

    @Test
    fun setupScreen_listsEveryStepAndTheGuide() {
        val activity = Robolectric.buildActivity(CarSetupActivity::class.java).setup().get()
        val container = activity.findViewById<LinearLayout>(R.id.setupSteps)

        // Intro text, seven steps, guide button.
        assertEquals(9, container.childCount)
        (container.getChildAt(8) as MaterialButton).performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(LegalActivity::class.java.name, started.component?.className)
        assertEquals(LegalActivity.ANDROID_AUTO_SETUP, started.getStringExtra("page"))
    }

    @Test
    fun crashLog_keepsNewestFirstWithDeviceDetails() {
        CrashLog.record(context, "main", IllegalStateException("first"))
        CrashLog.record(context, "main", IllegalArgumentException("second"))

        val report = CrashLog.read(context).orEmpty()

        assertTrue(report.startsWith("RoadBrowser "))
        assertTrue(report.indexOf("second") < report.indexOf("first"))
        assertTrue(report.contains("Android "))
    }

    @Test
    fun crashLog_staysSmallEnoughToShare() {
        repeat(200) { CrashLog.record(context, "main", RuntimeException("crash $it")) }

        assertTrue(CrashLog.read(context).orEmpty().length <= 48 * 1024)
    }

    @Test
    fun settingsShareButton_isDisabledUntilACrashIsRecorded_thenSharesTheText() {
        val quiet = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertFalse(quiet.findViewById<MaterialButton>(R.id.shareCrashReportButton).isEnabled)

        CrashLog.record(context, "main", IllegalStateException("boom"))
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        activity.findViewById<MaterialButton>(R.id.shareCrashReportButton).performClick()

        val chooser = shadowOf(activity).nextStartedActivity
        val shared = chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)
        assertTrue(shared?.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty().contains("boom"))
    }

    @Test
    fun settingsSetupButton_opensTheSetupScreen() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        activity.findViewById<MaterialButton>(R.id.carSetupButton).performClick()

        assertEquals(CarSetupActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    private fun installAndroidAuto() {
        val info = PackageInfo().apply { packageName = CarSetupChecks.ANDROID_AUTO_PACKAGE }
        shadowOf(context.packageManager).installPackage(info)
    }
}
