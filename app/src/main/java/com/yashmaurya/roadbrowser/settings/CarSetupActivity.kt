package com.yashmaurya.roadbrowser.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityCarSetupBinding

/**
 * Walks through everything RoadBrowser needs to show up and keep playing in Android Auto, with
 * a live status for each step and a button that opens the right settings page. Statuses are
 * re-read every time the screen comes back, so returning from a settings page updates it.
 */
class CarSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarSetupBinding

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openNotificationSettings()
        render()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { BrowserPreferences.createScaledContext(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topAppBar.setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val container = binding.setupSteps
        // Keep the intro text (first child); rebuild the step cards below it.
        while (container.childCount > 1) container.removeViewAt(1)
        CarSetupChecks.steps(this).forEachIndexed { index, step ->
            val card = layoutInflater.inflate(R.layout.item_setup_step, container, false)
            val status = card.findViewById<TextView>(R.id.stepStatus)
            val (statusText, statusColor) = when (step.status) {
                CarSetupChecks.Status.DONE -> R.string.car_setup_status_done to androidx.appcompat.R.attr.colorPrimary
                CarSetupChecks.Status.TODO -> R.string.car_setup_status_todo to androidx.appcompat.R.attr.colorError
                CarSetupChecks.Status.MANUAL -> R.string.car_setup_status_manual to com.google.android.material.R.attr.colorOnSurfaceVariant
            }
            status.text = getString(R.string.car_setup_step_label, index + 1, getString(statusText))
            status.setTextColor(MaterialColors.getColor(status, statusColor))
            card.findViewById<TextView>(R.id.stepTitle).setText(step.titleRes)
            card.findViewById<TextView>(R.id.stepBody).setText(step.bodyRes)
            val button = card.findViewById<MaterialButton>(R.id.stepAction)
            if (step.action != null && step.actionLabelRes != null) {
                button.visibility = View.VISIBLE
                button.setText(step.actionLabelRes)
                button.setOnClickListener { perform(step.action) }
            }
            container.addView(card)
        }
        container.addView(guideButton())
    }

    private fun guideButton(): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.car_setup_open_guide)
            minHeight = resources.getDimensionPixelSize(R.dimen.car_setup_button_min_height)
            setOnClickListener { startActivity(LegalActivity.intent(this@CarSetupActivity, LegalActivity.ANDROID_AUTO_SETUP)) }
        }
    }

    private fun perform(action: CarSetupChecks.Action) {
        when (action) {
            CarSetupChecks.Action.INSTALL_ANDROID_AUTO -> openStore(CarSetupChecks.ANDROID_AUTO_PACKAGE)
            CarSetupChecks.Action.OPEN_ANDROID_AUTO_SETTINGS -> openAndroidAutoSettings()
            CarSetupChecks.Action.NOTIFICATIONS -> {
                val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (granted) openNotificationSettings() else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            CarSetupChecks.Action.BATTERY -> {
                Toast.makeText(this, R.string.car_setup_battery_hint, Toast.LENGTH_LONG).show()
                openAppDetails(packageName)
            }
            CarSetupChecks.Action.BACKGROUND_AUDIO -> {
                BrowserPreferences.setBackgroundAudioEnabled(this, true)
                render()
            }
        }
    }

    /** Android Auto's own settings screen, or its app page when that screen can't be opened. */
    private fun openAndroidAutoSettings() {
        val settings = Intent().setComponent(ComponentName(CarSetupChecks.ANDROID_AUTO_PACKAGE, ANDROID_AUTO_SETTINGS_ACTIVITY))
        if (!tryStart(settings)) openAppDetails(CarSetupChecks.ANDROID_AUTO_PACKAGE)
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (!tryStart(intent)) openAppDetails(packageName)
    }

    private fun openAppDetails(pkg: String) {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
    }

    private fun openStore(pkg: String) {
        if (!tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))) {
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun tryStart(intent: Intent): Boolean = runCatching { startActivity(intent) }.isSuccess

    companion object {
        private const val ANDROID_AUTO_SETTINGS_ACTIVITY =
            "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"

        fun intent(context: Context): Intent = Intent(context, CarSetupActivity::class.java)
    }
}
