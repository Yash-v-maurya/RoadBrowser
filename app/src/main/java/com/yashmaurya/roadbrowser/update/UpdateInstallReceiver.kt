package com.yashmaurya.roadbrowser.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.yashmaurya.roadbrowser.R

/**
 * Receives the installer's answer for an update started by [AppUpdater.install]. Android always
 * needs the user's confirmation for an app-initiated install, so the usual answer is
 * STATUS_PENDING_USER_ACTION with the system's confirmation screen to show.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The process is replaced by the new version.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit // The user cancelled.
            else -> {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(context, context.getString(R.string.update_install_failed, reason), Toast.LENGTH_LONG).show()
            }
        }
    }
}
