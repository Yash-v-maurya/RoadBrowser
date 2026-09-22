package com.yashmaurya.roadbrowser.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityMainBinding
import com.yashmaurya.roadbrowser.permissions.PermissionManager
import com.yashmaurya.roadbrowser.startpage.StartPageManager
import com.yashmaurya.roadbrowser.tabs.TabManager
import com.yashmaurya.roadbrowser.ui.BrowserUIManager
import com.yashmaurya.roadbrowser.web.prepareBrowserIdentityFor

class NavigationManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val tabManager: TabManager,
    private val permissionManager: PermissionManager,
    private val startPageManager: StartPageManager,
    private val uiManager: BrowserUIManager,
    private val callbacks: NavigationCallbacks
) {

    interface NavigationCallbacks {
        fun onNavigationStarted(url: String)
        fun onNavigationFinished(url: String)
        fun onHideStartPage()
        fun getCurrentUrl(): String
        fun setCurrentUrl(url: String)
        fun setCurrentPageTitle(title: String)
    }

    fun extractBrowsableUrl(intent: Intent?): String? {
        val data = intent?.data
        if (data == null) {
            return null
        }
        val scheme = data.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            return data.toString()
        }
        return null
    }

    fun loadUrlFromIntent(rawUrl: String) {
        val navigable = BrowserPreferences.formatNavigableUrl(rawUrl.trim(), activity)
        if (navigable.isNotEmpty()) {
            navigateActiveTabTo(navigable, closeMenuAfterNavigate = true)
        }
    }

    fun navigateToAddress(raw: String, closeMenuAfterNavigate: Boolean) {
        val navigable = BrowserPreferences.formatNavigableUrl(raw, activity)
        if (navigable.isNotEmpty()) {
            navigateActiveTabTo(navigable, closeMenuAfterNavigate)
        }
    }

    private fun navigateActiveTabTo(navigable: String, closeMenuAfterNavigate: Boolean) {
        var targetTab = tabManager.activeTab
        if (targetTab == null) {
            targetTab = tabManager.createNewTab(activate = true)
        }
        if (targetTab == null) {
            return
        }
        
        val targetWebView = targetTab.webView
        val uri = runCatching { Uri.parse(navigable) }.getOrNull()
        if (uri == null) {
            return
        }

        val finishNavigation: (() -> Unit) -> Unit = { loadAction ->
            targetTab.currentUrl = navigable
            targetTab.currentTitle = ""
            
            if (targetTab.id == tabManager.activeTabId) {
                callbacks.setCurrentUrl(navigable)
                callbacks.setCurrentPageTitle("")
                if (binding.addressEdit.text?.toString() != navigable) {
                    binding.addressEdit.setText(navigable)
                    binding.addressEdit.setSelection(navigable.length)
                }
            }
            
            BrowserPreferences.persistUrl(activity, navigable)
            tabManager.persistTabSession()
            callbacks.onHideStartPage()
            loadAction()
            
            if (closeMenuAfterNavigate && binding.menuOverlay.isVisible) {
                uiManager.hideMenuOverlay()
            } else {
                uiManager.hideKeyboard(binding.persistentAddressEdit)
                binding.persistentAddressEdit.clearFocus()
            }
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        
        if (scheme == "http" && !BrowserPreferences.isHostAllowedCleartext(activity, host)) {
            permissionManager.showCleartextNavigationDialog(
                uri = uri,
                onAllowOnce = {
                    finishNavigation {
                        targetWebView.setTag(R.id.webview_allow_once_uri_tag, navigable)
                        targetWebView.post { 
                            targetWebView.prepareBrowserIdentityFor(navigable)
                            targetWebView.loadUrl(navigable) 
                        }
                    }
                },
                onAllowHost = {
                    if (host != null) {
                        BrowserPreferences.addAllowedCleartextHost(activity, host)
                    }
                    finishNavigation {
                        targetWebView.setTag(R.id.webview_allow_once_uri_tag, navigable)
                        targetWebView.post { 
                            targetWebView.prepareBrowserIdentityFor(navigable)
                            targetWebView.loadUrl(navigable) 
                        }
                    }
                },
                onCancel = {
                    if (closeMenuAfterNavigate && binding.menuOverlay.isVisible) {
                        uiManager.hideMenuOverlay()
                    }
                }
            )
            return
        }

        finishNavigation { 
            targetWebView.prepareBrowserIdentityFor(navigable)
            targetWebView.loadUrl(navigable) 
        }
    }
}
