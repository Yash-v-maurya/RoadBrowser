package com.yashmaurya.roadbrowser.ui

import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.TypedValue
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
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

    companion object {
        /**
         * How much of the window a menu sheet may claim. The remainder keeps the page (and the
         * scrim you tap to dismiss) visible, so the sheet always reads as a sheet.
         */
        private const val SHEET_MAX_WINDOW_PERCENT = 88
    }

    interface UICallbacks {
        fun onNavigateToAddress(raw: String, closeMenuAfterNavigate: Boolean)
        fun onShowQrCodeView()
        fun onShowCheckLatestView()
        fun onShowSettingsView()
        fun resolveThemeColor(attrRes: Int): Int
    }

    private var isSyncingAddressFields: Boolean = false
    /** Window signature the strip metrics were last sized for. See applyChromeMetrics(). */
    private var appliedChromeMetricsKey: String? = null
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

        applyChromeMetrics()
        applyToolbarDensity()

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

    /**
     * Re-resolves every strip metric from resources and pushes it onto the views.
     *
     * The activity declares `configChanges` for orientation and screen size, so it is never
     * recreated on a rotation or a window resize and the sizes baked in at inflation time
     * would otherwise stay on the old bucket's numbers. Re-applying here (and from the
     * configuration callback installed by [installChromeAdaptation]) is what makes the
     * width buckets in values-w###dp/dimens_chrome.xml actually take effect on a live window.
     */
    private fun applyChromeMetrics() {
        val res = activity.resources
        val config = res.configuration
        // applyToolbarLayout() runs on every navigation; the metrics only move when the window
        // does, and each assignment below costs a requestLayout.
        val key = "${config.screenWidthDp}x${config.screenHeightDp}" +
            "@${config.densityDpi}:${config.fontScale}"
        if (key == appliedChromeMetricsKey) {
            return
        }
        appliedChromeMetricsKey = key

        val stripHeight = res.getDimensionPixelSize(R.dimen.toolbar_chrome_height)
        val sidePadding = res.getDimensionPixelSize(R.dimen.toolbar_chrome_side_padding)
        val gap = res.getDimensionPixelSize(R.dimen.toolbar_chrome_gap)
        val buttonSize = res.getDimensionPixelSize(R.dimen.toolbar_chrome_button_size)
        val iconSize = res.getDimensionPixelSize(R.dimen.toolbar_chrome_icon_size)
        val pillHeight = res.getDimensionPixelSize(R.dimen.toolbar_chrome_pill_height)
        val pillPaddingStart = res.getDimensionPixelSize(R.dimen.toolbar_chrome_pill_padding_start)
        val pillPaddingEnd = res.getDimensionPixelSize(R.dimen.toolbar_chrome_pill_padding_end)
        val pillButtonSize = res.getDimensionPixelSize(R.dimen.toolbar_chrome_pill_button_size)
        val pillIconSize = res.getDimensionPixelSize(R.dimen.toolbar_chrome_pill_button_icon_size)
        val lockSize = res.getDimensionPixelSize(R.dimen.toolbar_chrome_lock_icon_size)
        val textGap = res.getDimensionPixelSize(R.dimen.toolbar_chrome_pill_text_gap)
        val badgeSize = res.getDimensionPixelSize(R.dimen.toolbar_chrome_badge_size)
        val contentMaxWidth = res.getDimensionPixelSize(R.dimen.toolbar_chrome_content_max_width)

        binding.toolbarStrip.updateLayoutParams { height = stripHeight }
        binding.toolbarContent.setPaddingRelative(sidePadding, 0, sidePadding, 0)
        (binding.toolbarContent.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.matchConstraintMaxWidth = contentMaxWidth
            binding.toolbarContent.layoutParams = params
        }

        // Back is the anchor of the row; everything after it carries the same gap, so the
        // rhythm survives any combination of gated controls.
        sizeIconButton(binding.toolbarButtonBack, buttonSize, iconSize, marginStart = 0)
        sizeIconButton(binding.toolbarButtonHome, buttonSize, iconSize, marginStart = gap)
        sizeIconButton(binding.toolbarButtonReload, buttonSize, iconSize, marginStart = gap)
        sizeIconButton(binding.toolbarButtonBookmarks, buttonSize, iconSize, marginStart = gap)
        sizeIconButton(binding.toolbarButtonTabs, buttonSize, iconSize, marginStart = null)
        sizeIconButton(binding.toolbarButtonMenu, buttonSize, iconSize, marginStart = gap)
        binding.toolbarTabsSlot.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = gap
        }
        binding.toolbarTabCount.updateLayoutParams {
            width = badgeSize
            height = badgeSize
        }

        binding.toolbarAddressPill.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = pillHeight
            marginStart = gap
        }
        binding.toolbarAddressPill.setPaddingRelative(pillPaddingStart, 0, pillPaddingEnd, 0)
        listOf(binding.persistentAddressSecureIcon, binding.persistentAddressInsecureIcon)
            .forEach { lockIcon ->
                lockIcon.updateLayoutParams {
                    width = lockSize
                    height = lockSize
                }
            }
        binding.persistentAddressEdit.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = textGap
            marginEnd = textGap
        }
        binding.persistentAddressEdit.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            res.getDimension(R.dimen.toolbar_chrome_address_text_size)
        )
        sizeIconButton(binding.persistentButtonClearAddress, pillButtonSize, pillIconSize, marginStart = null)
        sizeIconButton(binding.persistentButtonGo, pillButtonSize, pillIconSize, marginStart = null)
    }

    private fun sizeIconButton(
        button: MaterialButton,
        size: Int,
        iconSize: Int,
        marginStart: Int?
    ) {
        button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = size
            height = size
            if (marginStart != null) {
                this.marginStart = marginStart
            }
        }
        button.iconSize = iconSize
    }

    /**
     * Drops strip controls that the current width cannot hold without squeezing the address
     * pill below readable. Everything dropped here is still one tap away in the menu sheet:
     * Reload / Bookmarks / Forward live in the action grid, Home is "Start page", and the
     * pill's Go button is replaced by the keyboard's Go key.
     *
     * The thresholds are resource qualifiers (values-w###dp/bools.xml), not numbers in code,
     * so a new screen size is a new bucket rather than a new branch.
     */
    private fun applyToolbarDensity() {
        val res = activity.resources
        val showSecondary = res.getBoolean(R.bool.toolbar_show_secondary_actions)
        binding.toolbarButtonReload.isVisible = showSecondary
        binding.toolbarButtonBookmarks.isVisible = showSecondary
        binding.toolbarButtonHome.isVisible = res.getBoolean(R.bool.toolbar_show_home)
        binding.persistentButtonGo.isVisible = res.getBoolean(R.bool.toolbar_show_pill_go)
    }

    private fun toolbarHeightPx(): Int {
        return activity.resources.getDimensionPixelSize(R.dimen.toolbar_chrome_height)
    }

    /**
     * One-time wiring for chrome that has to react to the real window rather than to a
     * resource qualifier. Called once from [MainActivitySetup].
     */
    fun installChromeAdaptation() {
        // The bookmark and tab overlays are shown by their own managers, and the settings
        // overlay grows as its content is built, so the cap is enforced from layout rather
        // than from every call site that could change the sheet's contents.
        binding.menuOverlay.viewTreeObserver.addOnGlobalLayoutListener {
            if (binding.menuOverlay.isVisible) {
                applySheetHeightCap()
            }
        }

        // The activity keeps its instance across configuration changes, so this is the only
        // place the chrome hears about a resize.
        activity.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                applyToolbarLayout()
                if (binding.menuOverlay.isVisible) {
                    applySheetHeightCap()
                }
            }

            override fun onLowMemory() = Unit
        })

        applyToolbarLayout()
    }

    /**
     * Keeps the menu sheet (and every overlay it swaps in) short enough to sit on screen with
     * its buttons reachable. A head unit is 480dp tall, so the 600dp-class ceiling the phone
     * layout wants would run off the top and strand the bottom row under the sheet's own
     * edge; the real window always wins.
     *
     * A scroll view is left at WRAP_CONTENT while its content fits, so short sheets stay
     * short, and is pinned to the cap once the content is taller, which is exactly when the
     * scroll view needs to scroll.
     */
    fun applySheetHeightCap() {
        val cap = sheetMaxHeightPx()
        sheetScrollViews().forEach { scroll ->
            val content = scroll.getChildAt(0) ?: return@forEach
            val params = scroll.layoutParams ?: return@forEach
            val target = if (scroll.isVisible && content.height > cap) {
                cap
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            if (params.height != target) {
                params.height = target
                scroll.layoutParams = params
            }
        }
    }

    private fun sheetScrollViews(): List<NestedScrollView> = listOf(
        binding.menuScroll,
        binding.bookmarkManagerRoot,
        binding.tabManagerRoot,
        binding.checkLatestViewRoot,
        binding.qrCodeViewRoot,
        binding.settingsViewRoot
    )

    private fun sheetMaxHeightPx(): Int {
        val res = activity.resources
        val window = binding.menuOverlay.height.takeIf { it > 0 }
            ?: res.displayMetrics.heightPixels
        // The drag handle and the sheet's own top rounding sit above the scroll view, so the
        // cap is on the scrolling part, not on the card.
        val chrome = binding.dragHandleArea.height.takeIf { it > 0 }
            ?: (2 * res.getDimensionPixelSize(R.dimen.sheet_handle_padding_vertical))
        val fromWindow = (window * SHEET_MAX_WINDOW_PERCENT / 100) - chrome
        val ceiling = res.getDimensionPixelSize(R.dimen.menu_sheet_max_height)
        val floor = res.getDimensionPixelSize(R.dimen.menu_sheet_min_height)
        return minOf(fromWindow, ceiling).coerceAtLeast(floor)
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
            applySheetHeightCap()
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
