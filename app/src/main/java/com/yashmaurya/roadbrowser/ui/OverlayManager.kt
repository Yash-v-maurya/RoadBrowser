package com.yashmaurya.roadbrowser.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.yashmaurya.roadbrowser.AppConstants
import com.yashmaurya.roadbrowser.BuildConfig
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityMainBinding
import com.yashmaurya.roadbrowser.settings.SettingsCallbacks
import com.yashmaurya.roadbrowser.settings.SettingsViews
import com.yashmaurya.roadbrowser.startpage.StartPageManager
import com.yashmaurya.roadbrowser.tabs.TabManager
import com.yashmaurya.roadbrowser.update.AppUpdater
import com.yashmaurya.roadbrowser.web.updatePageDarkening

class OverlayManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val tabManager: TabManager,
    private val bookmarkManager: BookmarkManager,
    private val startPageManager: StartPageManager,
    private val uiManager: BrowserUIManager,
    private val callbacks: OverlayCallbacks
) {

    private var latestRelease: AppUpdater.Release? = null
    private var isDownloadingUpdate = false

    interface OverlayCallbacks {
        fun onRecreateRequested()
        fun onHomePageChanged()
        fun onPickBackgroundRequested()
        fun onVersionInfoReceived(latestUrl: String, tagName: String)
    }

    fun showQrCodeView(url: String) {
        if (url.isBlank()) {
            return
        }

        hideAllOverlays()
        binding.qrCodeViewRoot.visibility = View.VISIBLE
        binding.qrCodeImage.setImageBitmap(null)
        binding.qrCodeUrl.text = url

        uiManager.applySheetHeightCap()

        activity.lifecycleScope.launch {
            val bitmap = QRUtils.generateQrCodeAsync(url)
            if (bitmap != null) {
                binding.qrCodeImage.setImageBitmap(bitmap)
            }
            uiManager.applySheetHeightCap()
        }
    }

    fun hideQrCodeView() {
        binding.qrCodeViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
        uiManager.applySheetHeightCap()
    }

    fun showSettingsView() {
        hideAllOverlays()
        binding.settingsViewRoot.visibility = View.VISIBLE
        ensureSettingsContentPopulated()
        uiManager.applySheetHeightCap()
    }

    fun hideSettingsView() {
        binding.settingsViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
        uiManager.applySheetHeightCap()
    }

    fun showCheckLatestView() {
        hideAllOverlays()
        binding.checkLatestViewRoot.visibility = View.VISIBLE
        binding.checkLatestProgressIndicator.visibility = View.VISIBLE

        binding.checkLatestLatestVersion.text = activity.getString(R.string.menu_checking_latest)
        binding.checkLatestLatestVersion.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary))

        val packageName = activity.packageName
        binding.checkLatestInstalledVersion.text = activity.getString(
            R.string.installed_version_label,
            "v${com.yashmaurya.roadbrowser.BuildConfig.VERSION_NAME}"
        )

        uiManager.applySheetHeightCap()
        fetchLatestVersion()
    }

    fun hideCheckLatestView() {
        binding.checkLatestViewRoot.visibility = View.GONE
        binding.menuScroll.visibility = View.VISIBLE
        uiManager.applySheetHeightCap()
    }

    /**
     * Only one of these is ever on screen at a time; they share the sheet, so hiding the rest
     * is what lets [BrowserUIManager.applySheetHeightCap] size the sheet to the visible one.
     */
    private fun hideAllOverlays() {
        val views = listOf(
            binding.menuScroll,
            binding.bookmarkManagerRoot,
            binding.tabManagerRoot,
            binding.checkLatestViewRoot,
            binding.settingsViewRoot,
            binding.qrCodeViewRoot
        )
        views.forEach {
            it.visibility = View.GONE
        }
    }

    private fun ensureSettingsContentPopulated() {
        if (binding.settingsContentContainer.childCount > 0) {
            return
        }

        try {
            val settingsCallbacks = SettingsCallbacks(
                onClose = {
                    hideSettingsView()
                },
                onThemeChanged = {
                    callbacks.onRecreateRequested()
                },
                onPageDarkeningChanged = {
                    val enabled = BrowserPreferences.isBetaForceDarkPagesEnabled(activity)
                    tabManager.browserTabs.forEach { tab ->
                        tab.webView.updatePageDarkening(enabled)
                    }
                },
                onScaleChanged = {
                    callbacks.onRecreateRequested()
                },
                onHomePageChanged = {
                    callbacks.onHomePageChanged()
                },
                onPickStartPageBackground = {
                    callbacks.onPickBackgroundRequested()
                },
                onClearStartPageBackground = {
                    startPageManager.clearStartPageBackground()
                },
                onShieldsChanged = {
                    tabManager.browserTabs.forEach { tab ->
                        tab.webView.reload()
                    }
                }
            )

            val contentView = SettingsViews.createSettingsContent(activity, false, settingsCallbacks)
            binding.settingsContentContainer.addView(contentView)
        } catch (e: Exception) {
            // Log or handle error
        }
    }

    private fun fetchLatestVersion() {
        latestRelease = null
        binding.checkLatestProgressIndicator.isIndeterminate = true
        binding.checkLatestOpenReleaseButton.setText(R.string.check_latest_view_release)
        Thread {
            val release = runCatching { AppUpdater.fetchLatest() }.getOrNull()
            activity.runOnUiThread {
                binding.checkLatestProgressIndicator.visibility = View.GONE
                if (release == null) {
                    binding.checkLatestLatestVersion.setText(R.string.check_latest_failed)
                    return@runOnUiThread
                }
                latestRelease = release
                if (AppUpdater.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    binding.checkLatestLatestVersion.text = activity.getString(R.string.check_latest_update_available, release.tag)
                    binding.checkLatestLatestVersion.setTextColor(getColorFromAttr(androidx.appcompat.R.attr.colorError))
                    if (release.apkUrl != null) {
                        binding.checkLatestOpenReleaseButton.text = activity.getString(R.string.update_install_button, release.version)
                    }
                } else {
                    binding.checkLatestLatestVersion.text = activity.getString(R.string.check_latest_up_to_date, BuildConfig.VERSION_NAME)
                    binding.checkLatestLatestVersion.setTextColor(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                }
                callbacks.onVersionInfoReceived(release.pageUrl, release.tag)
            }
        }.start()
    }

    /** Installs a newer official release when there is one, otherwise opens the releases page. */
    fun onReleaseButtonClicked() {
        val release = latestRelease
        if (release?.apkUrl != null && AppUpdater.isNewer(release.version, BuildConfig.VERSION_NAME)) {
            startUpdate(release)
        } else {
            uiManager.openUriExternally(Uri.parse(release?.pageUrl ?: AppConstants.GITHUB_REPO_URL + "/releases/latest"))
        }
    }

    private fun startUpdate(release: AppUpdater.Release) {
        if (isDownloadingUpdate) return
        // Android's own per-app switch; the user grants it once in system settings.
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_installs, Toast.LENGTH_LONG).show()
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                )
            }
            return
        }
        isDownloadingUpdate = true
        binding.checkLatestOpenReleaseButton.isEnabled = false
        binding.checkLatestOpenReleaseButton.setText(R.string.update_downloading)
        binding.checkLatestProgressIndicator.apply {
            visibility = View.VISIBLE
            isIndeterminate = false
            max = 100
            progress = 0
        }
        Thread {
            val result = runCatching {
                val apk = AppUpdater.download(activity, release) { percent ->
                    activity.runOnUiThread { binding.checkLatestProgressIndicator.progress = percent }
                }
                AppUpdater.verify(activity, apk)?.let { reason -> error(reason) }
                apk
            }
            activity.runOnUiThread {
                isDownloadingUpdate = false
                binding.checkLatestOpenReleaseButton.isEnabled = true
                binding.checkLatestOpenReleaseButton.text = activity.getString(R.string.update_install_button, release.version)
                binding.checkLatestProgressIndicator.visibility = View.GONE
                result
                    .mapCatching { AppUpdater.install(activity, it) }
                    .onFailure {
                        Toast.makeText(activity, activity.getString(R.string.update_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
                    }
            }
        }.start()
    }

    private fun getColorFromAttr(attrResId: Int): Int {
        val tv = android.util.TypedValue()
        if (activity.theme.resolveAttribute(attrResId, tv, true)) {
            if (tv.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(activity, tv.resourceId)
            }
            return tv.data
        }
        return android.graphics.Color.TRANSPARENT
    }
}
