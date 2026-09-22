package com.yashmaurya.roadbrowser.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityMainBinding
import com.yashmaurya.roadbrowser.startpage.StartPageManager
import com.yashmaurya.roadbrowser.tabs.TabManager

class BrowserUIManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val tabManager: TabManager,
    private val bookmarkManager: BookmarkManager,
    private val startPageManager: StartPageManager,
    private val callbacks: UICallbacks
) {

    interface UICallbacks {
        fun onNavigateToAddress(raw: String, closeMenuAfterNavigate: Boolean)
        fun onShowQrCodeView()
        fun onShowCheckLatestView()
        fun onShowSettingsView()
        fun resolveThemeColor(attrRes: Int): Int
    }

    private var isSyncingAddressFields: Boolean = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    fun configureAddressField(
        editText: com.google.android.material.textfield.TextInputEditText,
        clearButton: MaterialButton,
        goButton: MaterialButton,
        closeMenuAfterNavigate: Boolean
    ) {
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                callbacks.onNavigateToAddress(
                    editText.text?.toString().orEmpty(), 
                    closeMenuAfterNavigate
                )
                true
            } else {
                false
            }
        }

        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                syncAddressFieldsFrom(editText)
                updateAddressClearButtons()
            }
        })

        editText.setOnFocusChangeListener { _, _ -> updateAddressClearButtons() }

        clearButton.setOnClickListener {
            editText.setText("")
            editText.requestFocus()
            showKeyboard(editText)
        }

        goButton.setOnClickListener {
            callbacks.onNavigateToAddress(
                editText.text?.toString().orEmpty(), 
                closeMenuAfterNavigate
            )
        }
    }

    fun syncAddressFieldsFrom(source: com.google.android.material.textfield.TextInputEditText) {
        if (isSyncingAddressFields) {
            return
        }
        
        val text = source.text?.toString().orEmpty()
        isSyncingAddressFields = true
        try {
            val addressEdit = binding.addressEdit
            val persistentAddressEdit = binding.persistentAddressEdit
            
            if (addressEdit !== source && addressEdit.text?.toString() != text) {
                addressEdit.setText(text)
                if (addressEdit.hasFocus()) {
                    addressEdit.setSelection(text.length)
                }
            }
            
            if (persistentAddressEdit !== source && persistentAddressEdit.text?.toString() != text) {
                persistentAddressEdit.setText(text)
                if (persistentAddressEdit.hasFocus()) {
                    persistentAddressEdit.setSelection(text.length)
                }
            }
        } finally {
            isSyncingAddressFields = false
        }
    }

    fun updateAddressClearButtons() {
        val hasAddressText = !binding.addressEdit.text.isNullOrEmpty()
        updateAddressClearButton(binding.buttonClearAddress, hasAddressText)
        
        // The toolbar pill is narrow, so its clear button only appears while editing.
        val persistentEdit = binding.persistentAddressEdit
        val hasPersistentAddressText = !persistentEdit.text.isNullOrEmpty() && persistentEdit.hasFocus()
        updateAddressClearButton(binding.persistentButtonClearAddress, hasPersistentAddressText)
    }

    fun updateConnectionSecurityIcon(url: String?) {
        val icons = listOf(
            binding.addressSecureIcon, 
            binding.addressInsecureIcon, 
            binding.persistentAddressSecureIcon, 
            binding.persistentAddressInsecureIcon
        )
        
        if (url.isNullOrBlank()) {
            icons.forEach { it.visibility = View.GONE }
            return
        }
        
        val isSecure = try { 
            url.lowercase().startsWith("https://") 
        } catch (_: Exception) { 
            false 
        }
        
        binding.addressSecureIcon.isVisible = isSecure
        binding.addressInsecureIcon.isVisible = !isSecure
        binding.persistentAddressSecureIcon.isVisible = isSecure
        binding.persistentAddressInsecureIcon.isVisible = !isSecure
    }

    private fun updateAddressClearButton(button: View, shouldShow: Boolean) {
        if (shouldShow && button.visibility != View.VISIBLE) {
            button.visibility = View.VISIBLE
            button.alpha = 0f
            button.animate().alpha(1f).setDuration(150).start()
        } else if (!shouldShow && button.visibility == View.VISIBLE) {
            button.animate().alpha(0f).setDuration(100).withEndAction {
                button.visibility = View.GONE
            }.start()
        }
    }

    /**
     * Shows or hides the bottom toolbar and keeps page content clear of it. The toolbar is the
     * only chrome, so it is always on unless a video is fullscreen or the start page is in
     * background-only mode.
     */
    fun applyToolbarLayout() {
        val photoOnly = startPageManager.isShowingStartPage && startPageManager.isStartPagePhotoOnlyMode
        // While the menu sheet is up the toolbar would draw over it (higher elevation), and the
        // sheet carries its own controls anyway.
        val shouldShow = !isInFullscreen() && !photoOnly && !binding.menuOverlay.isVisible
        binding.carToolbar.isVisible = shouldShow

        val showSecondary = activity.resources.getBoolean(R.bool.toolbar_show_secondary_actions)
        binding.toolbarButtonReload.isVisible = showSecondary
        binding.toolbarButtonBookmarks.isVisible = showSecondary

        val bottomInset = if (shouldShow) toolbarHeightPx() else 0
        tabManager.browserTabs.forEach { tab ->
            tab.webView.setPadding(0, 0, 0, bottomInset)
        }

        val pagePadding = activity.resources.getDimensionPixelSize(R.dimen.start_page_padding)
        binding.startPageScroll.updatePadding(
            left = pagePadding,
            top = pagePadding,
            right = pagePadding,
            bottom = pagePadding + bottomInset
        )

        updateAddressClearButtons()
    }

    fun updateToolbarState(canGoBack: Boolean, canReload: Boolean) {
        setToolbarButtonEnabled(binding.toolbarButtonBack, canGoBack)
        setToolbarButtonEnabled(binding.toolbarButtonReload, canReload)
    }

    private fun setToolbarButtonEnabled(button: MaterialButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.38f
    }

    private fun toolbarHeightPx(): Int {
        val res = activity.resources
        return res.getDimensionPixelSize(R.dimen.toolbar_height) +
            2 * res.getDimensionPixelSize(R.dimen.toolbar_margin)
    }

    fun focusMenuAddressBar() {
        binding.addressEdit.requestFocus()
        val length = binding.addressEdit.text?.length ?: 0
        binding.addressEdit.setSelection(length)
        showKeyboard(binding.addressEdit)
    }

    fun showMenuOverlay(focusAddressBar: Boolean = false) {
        binding.menuOverlay.visibility = View.VISIBLE
        binding.carToolbar.visibility = View.GONE
        binding.menuCard.post {
            binding.menuCard.translationY = binding.menuCard.height.toFloat()
            binding.menuCard.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
                
            binding.menuOverlayScrim.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
                
            if (focusAddressBar) {
                focusMenuAddressBar()
            }
        }
        bookmarkManager.refreshBookmarks()
        tabManager.refreshTabs()
        startPageManager.refreshStartPage()
    }

    fun hideMenuOverlay() {
        hideKeyboard(binding.addressEdit)
        binding.menuCard.animate()
            .translationY(binding.menuCard.height.toFloat())
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.menuOverlay.visibility = View.GONE
                bookmarkManager.hideBookmarkManager()
                tabManager.hideTabManager()
                
                binding.checkLatestViewRoot.visibility = View.GONE
                binding.qrCodeViewRoot.visibility = View.GONE
                binding.settingsViewRoot.visibility = View.GONE
                applyToolbarLayout()
            }
            .start()
            
        binding.menuOverlayScrim.animate()
            .alpha(0f)
            .setDuration(200)
            .start()
    }

    fun openUriExternally(uri: Uri) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }.onFailure {
            Toast.makeText(activity, R.string.error_open_external, Toast.LENGTH_SHORT).show()
        }
    }

    fun sanitizeJsExternalUrl(sourceWebView: android.webkit.WebView, rawUrl: String?): Uri? {
        val currentPage = sourceWebView.url
        if (currentPage == null) {
            return null
        }
        if (!currentPage.startsWith("file:///android_asset/error.html")) {
            return null
        }

        val candidate = rawUrl?.trim()
        if (candidate.isNullOrBlank()) {
            return null
        }
        
        val parsed = runCatching { Uri.parse(candidate) }.getOrNull()
        if (parsed == null) {
            return null
        }
        val scheme = parsed.scheme?.lowercase()
        if (scheme == null) {
            return null
        }
        if (scheme != "http" && scheme != "https") {
            return null
        }
        
        return parsed
    }

    fun showKeyboard(view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.post { 
            imm.showSoftInput(view, 0) 
        }
    }

    fun hideKeyboard(view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun isInFullscreen(): Boolean {
        return customView != null
    }

    fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        
        (view.parent as? ViewGroup)?.removeView(view)
        customView = view
        customViewCallback = callback
        
        if (binding.menuOverlay.isVisible) {
            hideMenuOverlay()
        }
        
        binding.carToolbar.visibility = View.GONE
        tabManager.activeTab?.webView?.visibility = View.INVISIBLE
        
        binding.fullscreenContainer.apply {
            visibility = View.VISIBLE
            removeAllViews()
            addView(view, FrameLayout.LayoutParams(-1, -1))
            bringToFront()
        }
        
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(activity.window, binding.fullscreenContainer)
        // Transient bars: on a touch head unit a stray tap would otherwise bring the system bars
        // back permanently and shrink the video for the rest of the session.
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun setupManualDragLogic() {
        var startY = 0f
        var initialTranslationY = 0f
        val swipeThreshold = 250f

        binding.dragHandleArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    initialTranslationY = binding.menuCard.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    if (deltaY > 0) {
                        binding.menuCard.translationY = initialTranslationY + deltaY
                        val progress = (deltaY / binding.menuCard.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        binding.menuOverlayScrim.alpha = 1f - progress
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val totalDeltaY = event.rawY - startY
                    if (totalDeltaY > swipeThreshold) {
                        hideMenuOverlay()
                    } else {
                        binding.menuCard.animate()
                            .translationY(0f)
                            .setDuration(200)
                            .start()
                        binding.menuOverlayScrim.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun exitFullscreen(fromWebChrome: Boolean = false) {
        // Claim the fullscreen state before touching anything else. This is called from onDestroy,
        // from the back press and from onHideCustomView, so a re-entrant call (the callback below
        // making the page fire onHideCustomView again) must become a no-op instead of invoking
        // onCustomViewHidden() twice.
        val activeCustomView = customView
        val callback = customViewCallback
        customView = null
        customViewCallback = null

        // Restored unconditionally so the screen is never left pinned on, even if the activity is
        // destroyed while a video is fullscreen.
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(activity.window, binding.root)
        controller.show(WindowInsetsCompat.Type.systemBars())

        if (activeCustomView == null) {
            return
        }

        binding.fullscreenContainer.apply {
            removeAllViews()
            visibility = View.GONE
        }

        val webViewVisibility = if (startPageManager.isShowingStartPage) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
        tabManager.activeTab?.webView?.visibility = webViewVisibility

        if (!fromWebChrome) {
            callback?.onCustomViewHidden()
        }

        applyToolbarLayout()
    }
}
