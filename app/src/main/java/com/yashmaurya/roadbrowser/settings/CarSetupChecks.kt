package com.yashmaurya.roadbrowser.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences

/**
 * The checklist behind the Android Auto setup screen. Steps the app can verify report DONE or
 * TODO; the ones that live inside Android Auto's own settings (which no other app can read)
 * report MANUAL and say where to look.
 */
object CarSetupChecks {

    const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

    enum class Status { DONE, TODO, MANUAL }

    enum class Action { INSTALL_ANDROID_AUTO, OPEN_ANDROID_AUTO_SETTINGS, NOTIFICATIONS, BATTERY, BACKGROUND_AUDIO }

    data class Step(val titleRes: Int, val bodyRes: Int, val status: Status, val action: Action?, val actionLabelRes: Int?)

    fun steps(context: Context): List<Step> {
        val androidAuto = isAndroidAutoInstalled(context)
        return listOf(
            Step(
                R.string.car_setup_android_auto_title,
                R.string.car_setup_android_auto_body,
                if (androidAuto) Status.DONE else Status.TODO,
                Action.INSTALL_ANDROID_AUTO.takeUnless { androidAuto },
                R.string.car_setup_action_get_android_auto.takeUnless { androidAuto }
            ),
            Step(
                R.string.car_setup_unknown_sources_title,
                R.string.car_setup_unknown_sources_body,
                Status.MANUAL,
                Action.OPEN_ANDROID_AUTO_SETTINGS.takeIf { androidAuto },
                R.string.car_setup_action_open_android_auto.takeIf { androidAuto }
            ),
            Step(
                R.string.car_setup_launcher_title,
                R.string.car_setup_launcher_body,
                Status.MANUAL,
                Action.OPEN_ANDROID_AUTO_SETTINGS.takeIf { androidAuto },
                R.string.car_setup_action_open_android_auto.takeIf { androidAuto }
            ),
            status(
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
                R.string.car_setup_notifications_title,
                R.string.car_setup_notifications_body,
                Action.NOTIFICATIONS,
                R.string.car_setup_action_allow
            ),
            status(
                isBatteryUnrestricted(context),
                R.string.car_setup_battery_title,
                R.string.car_setup_battery_body,
                Action.BATTERY,
                R.string.car_setup_action_open_battery
            ),
            status(
                BrowserPreferences.isBackgroundAudioEnabled(context),
                R.string.car_setup_background_audio_title,
                R.string.car_setup_background_audio_body,
                Action.BACKGROUND_AUDIO,
                R.string.car_setup_action_turn_on
            ),
            Step(R.string.car_setup_reconnect_title, R.string.car_setup_reconnect_body, Status.MANUAL, null, null)
        )
    }

    fun isAndroidAutoInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(ANDROID_AUTO_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        true
    }.getOrDefault(false)

    private fun isBatteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun status(done: Boolean, titleRes: Int, bodyRes: Int, action: Action, labelRes: Int) =
        Step(titleRes, bodyRes, if (done) Status.DONE else Status.TODO, action.takeUnless { done }, labelRes.takeUnless { done })
}
