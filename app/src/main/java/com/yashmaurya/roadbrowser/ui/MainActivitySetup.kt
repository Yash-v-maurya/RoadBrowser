package com.yashmaurya.roadbrowser.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.yashmaurya.roadbrowser.AppConstants
import com.yashmaurya.roadbrowser.MainActivity
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityMainBinding
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.startpage.StartPageManager
import com.yashmaurya.roadbrowser.tabs.TabManager
import com.yashmaurya.roadbrowser.navigation.NavigationManager

class MainActivitySetup(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val managers: Managers
) {

    data class Managers(
        val bookmarkManager: BookmarkManager,
        val startPageManager: StartPageManager,
        val tabManager: TabManager,
        val uiManager: BrowserUIManager,
        val navigationManager: NavigationManager,
        val overlayManager: OverlayManager
    )

    fun setupClickListeners() {
        setupToolbarButtons()
        setupNavigationButtons()
        setupMenuButtons()
        setupBookmarkButtons()
        setupOverlayButtons()
        setupStartPageButtons()
    }

    private fun setupToolbarButtons() {
        binding.toolbarButtonBack.setOnClickListener {
            activity.webView?.let { webView ->
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
            activity.updateNavigationButtonsProxy()
        }

        binding.toolbarButtonReload.setOnClickListener {
            activity.webView?.reload()
        }

        binding.toolbarButtonHome.setOnClickListener {
            activity.showStartPageProxy()
        }

        binding.toolbarButtonBookmarks.setOnClickListener {
            managers.uiManager.showMenuOverlay()
            managers.bookmarkManager.showBookmarkManager()
        }

        binding.toolbarButtonTabs.setOnClickListener {
            managers.uiManager.showMenuOverlay()
            managers.tabManager.showTabManager()
        }

        binding.toolbarButtonMenu.setOnClickListener {
            managers.uiManager.showMenuOverlay()
        }
    }

    private fun setupNavigationButtons() {
        binding.buttonBack.setOnClickListener {
            activity.webView?.let { webView ->
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
            activity.updateNavigationButtonsProxy()
        }

        binding.buttonForward.setOnClickListener {
            activity.webView?.let { webView ->
                if (webView.canGoForward()) {
                    webView.goForward()
                }
            }
            activity.updateNavigationButtonsProxy()
        }

        binding.buttonReload.setOnClickListener {
            activity.webView?.reload()
            managers.uiManager.hideMenuOverlay()
        }
    }

    private fun setupMenuButtons() {
        binding.buttonClose.setOnClickListener {
            managers.uiManager.hideMenuOverlay()
        }

        binding.menuOverlayScrim.setOnClickListener {
            managers.uiManager.hideMenuOverlay()
        }
    }

    private fun setupBookmarkButtons() {
        binding.buttonBookmarks.setOnClickListener {
            managers.bookmarkManager.showBookmarkManager()
        }

        binding.buttonBookmarkManagerBack.setOnClickListener {
            managers.bookmarkManager.hideBookmarkManager()
        }

        binding.buttonBookmarkAdd.setOnClickListener {
            managers.bookmarkManager.addBookmarkForCurrentPage()
        }

        binding.buttonBookmarkStartPageAdd.setOnClickListener {
            managers.bookmarkManager.showStartPageSlotPicker(activity.currentUrlProxy)
        }

        binding.buttonBookmarkSetHomePage.setOnClickListener {
            managers.bookmarkManager.setCurrentPageAsHomePage()
        }
    }

    private fun setupOverlayButtons() {
        binding.buttonExternal.setOnClickListener {
            managers.overlayManager.showQrCodeView(activity.currentUrlProxy)
        }

        binding.buttonExternalGithub.setOnClickListener {
            val uri = Uri.parse(AppConstants.GITHUB_REPO_URL)
            managers.uiManager.openUriExternally(uri)
        }

        binding.buttonSettings.setOnClickListener {
            managers.overlayManager.showSettingsView()
        }

        binding.buttonCheckLatest.setOnClickListener {
            managers.overlayManager.showCheckLatestView()
        }

        binding.buttonQrCodeBack.setOnClickListener {
            managers.overlayManager.hideQrCodeView()
        }

        binding.buttonCheckLatestBack.setOnClickListener {
            managers.overlayManager.hideCheckLatestView()
        }

        binding.checkLatestOpenReleaseButton.setOnClickListener {
            val uri = Uri.parse(activity.latestReleaseUrlProxy)
            managers.uiManager.openUriExternally(uri)
        }
    }

    private fun setupStartPageButtons() {
        binding.buttonTabs.setOnClickListener {
            managers.tabManager.showTabManager()
        }

        binding.buttonTabManagerBack.setOnClickListener {
            managers.tabManager.hideTabManager()
        }

        binding.buttonNewTab.setOnClickListener {
            managers.tabManager.createNewTab(true)
            managers.uiManager.hideMenuOverlay()
        }

        binding.buttonTabManagerAdd.setOnClickListener {
            managers.tabManager.createNewTab(true)
            managers.uiManager.hideMenuOverlay()
        }

        binding.buttonStartPage.setOnClickListener {
            activity.showStartPageProxy()
            managers.uiManager.hideMenuOverlay()
        }

        binding.startPageResumeCard.setOnClickListener {
            val resumeUrl = BrowserPreferences.getLastVisitedUrl(activity)
            if (resumeUrl.isNullOrBlank()) {
                val message = activity.getString(R.string.start_page_no_last_page)
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            } else {
                managers.navigationManager.loadUrlFromIntent(resumeUrl)
            }
        }

        binding.buttonStartPagePhotoOnly.setOnClickListener {
            managers.startPageManager.isStartPagePhotoOnlyMode = !managers.startPageManager.isStartPagePhotoOnlyMode
            managers.startPageManager.applyStartPagePhotoOnlyMode()
        }

        binding.startPageRoot.setOnClickListener {
            if (managers.startPageManager.isStartPagePhotoOnlyMode) {
                managers.startPageManager.isStartPagePhotoOnlyMode = false
                managers.startPageManager.applyStartPagePhotoOnlyMode()
            }
        }
    }
}
