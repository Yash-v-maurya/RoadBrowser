package com.yashmaurya.roadbrowser.startpage

import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ActivityMainBinding
import com.yashmaurya.roadbrowser.ui.adapters.StartPageAdapter
import com.yashmaurya.roadbrowser.ui.adapters.SlotItem
import com.yashmaurya.roadbrowser.data.SiteIconCache
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StartPageManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val bookmarkManager: BookmarkManager,
    private val callbacks: StartPageCallbacks
) {

    interface StartPageCallbacks {
        fun onNavigateToUrl(url: String)
        fun onShowMenuOverlay()
        fun onHideMenuOverlay()
        fun onEnterFullscreen()
        fun onExitFullscreen()
        fun isInFullscreen(): Boolean
        fun getCurrentUrl(): String
        fun resolveThemeColor(attrRes: Int): Int
        fun updateNavigationButtons()
        fun updateConnectionSecurityIcon(url: String?)
        fun updateToolbarVisibility()
        fun loadUrlFromIntent(url: String)
        fun resolveReadableTextColor(bg: Int, pr: Int, fb: Int): Int
    }

    private companion object {
        /**
         * Spans that divide [BrowserPreferences.MAX_START_PAGE_SITES] evenly, widest first.
         * Restricting the grid to these keeps the last row full instead of ragged.
         */
        private val TIDY_SPANS = listOf(6, 3, 2)
    }

    var isShowingStartPage: Boolean = false
    var isStartPagePhotoOnlyMode: Boolean = false
    
    private var loadedStartPageBackgroundUri: String? = null
    private var loadedStartPageBackgroundBitmap: Bitmap? = null
    private var cachedStartPageGradientSignature: Int = 0

    private val startPageAdapter: StartPageAdapter by lazy {
        StartPageAdapter(
            bookmarkManager = bookmarkManager,
            onSlotClick = { url ->
                if (url.isEmpty()) {
                    callbacks.onShowMenuOverlay()
                    bookmarkManager.showBookmarkManager()
                } else {
                    callbacks.loadUrlFromIntent(url)
                }
            },
            onReordered = { newList ->
                syncBookmarksFromSlots(newList.map { it.url })
            },
            resolveThemeColor = callbacks::resolveThemeColor
        )
    }

    private var backgroundLoadJob: Job? = null

    private var lastConfigurationSignature: Int = 0

    /**
     * The activity handles orientation and screen-size changes itself, so nothing is recreated
     * on a rotation or a window resize: the tiles keep the dimensions they were inflated with
     * and the grid keeps the span it was given. This listens for the change and re-applies
     * everything the start page sizes at runtime.
     */
    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val signature = configurationSignature(newConfig)
            if (signature == lastConfigurationSignature) {
                return
            }
            lastConfigurationSignature = signature
            // Posted so the activity's own resources have picked up the new configuration
            // before anything is measured against it.
            binding.startPageRoot.post { reapplyStartPageMetrics() }
        }

        override fun onLowMemory() {}
    }

    init {
        lastConfigurationSignature = configurationSignature(activity.resources.configuration)
        activity.registerComponentCallbacks(configurationCallbacks)
    }

    private fun configurationSignature(config: Configuration): Int {
        var result = config.screenWidthDp
        result = 31 * result + config.screenHeightDp
        result = 31 * result + config.smallestScreenWidthDp
        result = 31 * result + config.orientation
        result = 31 * result + config.densityDpi
        result = 31 * result + (config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        return result
    }

    /**
     * Re-applies everything that is resolved per qualifier at runtime. The tiles carry their
     * own dimensions from the inflated layout, so the view holders have to be rebuilt rather
     * than merely rebound.
     */
    private fun reapplyStartPageMetrics() {
        val grid = binding.startPageQuickLinksContainer
        if (grid.adapter != null) {
            grid.adapter = null
            grid.recycledViewPool.clear()
            grid.layoutManager = GridLayoutManager(activity, computeGridMetrics().span)
            grid.adapter = startPageAdapter
        }
        applyGridMetrics()
        applyResumeCardDensity()
        if (binding.startPageResumeCard.isVisible) {
            refreshStartPageResumeCard()
        }
        // The gradient's blob radii are in px, so it is rebuilt against the new density.
        cachedStartPageGradientSignature = 0
        applyDynamicStartPageGradientBackground()
    }

    fun onDestroy() {
        activity.unregisterComponentCallbacks(configurationCallbacks)
        backgroundLoadJob?.cancel()
        backgroundLoadJob = null
        loadedStartPageBackgroundBitmap?.recycle()
        loadedStartPageBackgroundBitmap = null
        loadedStartPageBackgroundUri = null
    }

    private fun setupRecyclerView() {
        if (binding.startPageQuickLinksContainer.adapter != null) {
            return
        }
        
        binding.startPageQuickLinksContainer.apply {
            layoutManager = GridLayoutManager(activity, computeGridMetrics().span)
            adapter = startPageAdapter
        }

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                startPageAdapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.startPageQuickLinksContainer)
    }

    private data class GridMetrics(val span: Int, val sidePaddingPx: Int)

    /**
     * Column count and gutters for the quick-link grid.
     *
     * Span is derived from the width actually available to the grid and the per-qualifier
     * target tile width, never hardcoded: take the most columns that still leave every tile
     * at least [R.dimen.start_page_grid_min_tile_width] wide. Only spans that divide the six
     * slots evenly are allowed, so the grid always ends on a full row instead of a ragged one.
     *
     * On very wide screens the cell is capped at [R.dimen.start_page_grid_max_tile_width] and
     * the leftover width becomes symmetric padding, so an ultrawide unit gets a centred block
     * of readable tiles rather than a row of stamps or four billboards.
     */
    private fun computeGridMetrics(): GridMetrics {
        val resources = activity.resources
        val density = resources.displayMetrics.density
        val pagePaddingDp = resources.getDimension(R.dimen.start_page_padding) / density
        val availableDp = (resources.configuration.screenWidthDp - 2f * pagePaddingDp).coerceAtLeast(1f)
        val minTileDp = (resources.getDimension(R.dimen.start_page_grid_min_tile_width) / density)
            .coerceAtLeast(1f)
        val maxTileDp = (resources.getDimension(R.dimen.start_page_grid_max_tile_width) / density)
            .coerceAtLeast(minTileDp)

        val span = TIDY_SPANS.firstOrNull { availableDp / it >= minTileDp } ?: TIDY_SPANS.last()
        val cellDp = (availableDp / span).coerceAtMost(maxTileDp)
        val sidePaddingDp = ((availableDp - cellDp * span) / 2f).coerceAtLeast(0f)
        return GridMetrics(span, (sidePaddingDp * density).toInt())
    }

    private fun applyGridMetrics() {
        val metrics = computeGridMetrics()
        val grid = binding.startPageQuickLinksContainer
        (grid.layoutManager as? GridLayoutManager)?.let { manager ->
            if (manager.spanCount != metrics.span) {
                manager.spanCount = metrics.span
                manager.requestLayout()
            }
        }
        if (grid.paddingStart != metrics.sidePaddingPx || grid.paddingEnd != metrics.sidePaddingPx) {
            grid.setPaddingRelative(
                metrics.sidePaddingPx,
                grid.paddingTop,
                metrics.sidePaddingPx,
                grid.paddingBottom
            )
        }
    }

    private fun syncBookmarksFromSlots(slotUrls: List<String>) {
        slotUrls.forEachIndexed { index, url ->
            BrowserPreferences.setStartPageSlot(activity, index, url.takeIf { it.isNotEmpty() })
        }
        
        val allBookmarks = BrowserPreferences.getBookmarks(activity).toMutableList()
        val nonSlotBookmarks = allBookmarks.filter { it !in slotUrls.filter { s -> s.isNotEmpty() } }
        
        val newOrder = mutableListOf<String>()
        slotUrls.filter { it.isNotEmpty() }.forEach { newOrder.add(it) }
        newOrder.addAll(nonSlotBookmarks)
        
        BrowserPreferences.setBookmarks(activity, newOrder)
        bookmarkManager.refreshBookmarks()
    }

    fun showStartPage() {
        val homePageUrl = BrowserPreferences.getHomePageUrl(activity)
        if (!homePageUrl.isNullOrBlank()) {
            val message = activity.getString(R.string.start_page_disabled_by_home_page)
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            callbacks.loadUrlFromIntent(homePageUrl)
            return
        }
        
        if (callbacks.isInFullscreen()) {
            callbacks.onExitFullscreen()
        }
        
        isShowingStartPage = true
        isStartPagePhotoOnlyMode = false
        binding.startPageRoot.visibility = View.VISIBLE
        binding.pageTitle.text = activity.getString(R.string.start_page_title)
        binding.addressEdit.setText("")
        
        callbacks.updateConnectionSecurityIcon(null)
        refreshStartPage()
        applyStartPagePhotoOnlyMode()
        callbacks.updateNavigationButtons()
        callbacks.updateToolbarVisibility()
    }

    fun hideStartPage(currentPageTitle: String, currentUrl: String) {
        if (!isShowingStartPage && binding.startPageRoot.visibility != View.VISIBLE) {
            return
        }
        
        isShowingStartPage = false
        isStartPagePhotoOnlyMode = false
        applyStartPagePhotoOnlyMode()
        binding.startPageRoot.visibility = View.GONE
        
        binding.pageTitle.text = currentPageTitle.ifBlank {
            currentUrl.takeIf { it.isNotBlank() }?.let { bookmarkManager.displayLabelForUrl(it) }.orEmpty()
        }
        
        if (currentUrl.isNotBlank()) {
            if (binding.addressEdit.text?.toString() != currentUrl) {
                binding.addressEdit.setText(currentUrl)
                binding.addressEdit.setSelection(currentUrl.length)
            }
        } else {
            binding.addressEdit.setText("")
        }
        
        val iconUrl = currentUrl.takeIf { bookmarkManager.isActiveWebsiteUrl(it) }
        callbacks.updateConnectionSecurityIcon(iconUrl)
        callbacks.updateNavigationButtons()
        bookmarkManager.refreshBookmarks()
    }

    fun applyStartPagePhotoOnlyMode() {
        val visibility = if (isStartPagePhotoOnlyMode) View.GONE else View.VISIBLE
        binding.startPageScroll.visibility = visibility
        binding.startPageDimOverlay.visibility = visibility
        // The toolbar goes away too so the background photo is genuinely unobstructed.
        callbacks.updateToolbarVisibility()
    }

    fun refreshStartPage() {
        setupRecyclerView()
        applyGridMetrics()
        refreshStartPageQuickLinks()
        refreshStartPageBackground()
        refreshStartPageResumeCard()
        setupStartPageCardGlassBackground()
    }

    private fun setupStartPageCardGlassBackground() {
        // The quick-links grid carries no frame of its own — the tiles are the structure. Only
        // the resume card is a surface, so it reads as one object rather than part of the grid.
        binding.startPageCard.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.startPageCard.strokeWidth = 0

        val outlineColor = callbacks.resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
        binding.startPageResumeCard.setCardBackgroundColor(
            callbacks.resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLowest)
        )
        binding.startPageResumeCard.strokeColor = outlineColor
        binding.startPageResumeCard.strokeWidth = activity.resources.displayMetrics.density.toInt()
    }

    fun refreshStartPageBackground() {
        applyDynamicStartPageGradientBackground()
        val backgroundUri = BrowserPreferences.getStartPageBackgroundUri(activity)
        
        if (backgroundUri.isNullOrBlank()) {
            clearBackground()
            return
        }

        if (backgroundUri == loadedStartPageBackgroundUri && loadedStartPageBackgroundBitmap != null) {
            binding.startPageBackgroundImage.setImageBitmap(loadedStartPageBackgroundBitmap)
            binding.startPageBackgroundImage.visibility = View.VISIBLE
            return
        }

        backgroundLoadJob?.cancel()
        val uriToLoad = Uri.parse(backgroundUri)
        val metrics = activity.resources.displayMetrics
        val reqWidth = metrics.widthPixels.coerceAtLeast(1)
        val reqHeight = metrics.heightPixels.coerceAtLeast(1)

        backgroundLoadJob = activity.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeSampledBitmapFromUri(uriToLoad, reqWidth, reqHeight)
            }
            
            loadedStartPageBackgroundBitmap?.recycle()
            loadedStartPageBackgroundBitmap = bitmap
            loadedStartPageBackgroundUri = if (bitmap != null) backgroundUri else null

            if (bitmap != null) {
                binding.startPageBackgroundImage.setImageBitmap(bitmap)
                binding.startPageBackgroundImage.visibility = View.VISIBLE
            } else {
                binding.startPageBackgroundImage.setImageBitmap(null)
                binding.startPageBackgroundImage.visibility = View.GONE
            }
        }
    }

    private fun clearBackground() {
        loadedStartPageBackgroundBitmap?.recycle()
        loadedStartPageBackgroundBitmap = null
        loadedStartPageBackgroundUri = null
        binding.startPageBackgroundImage.setImageBitmap(null)
        binding.startPageBackgroundImage.visibility = View.GONE
    }

    private fun applyDynamicStartPageGradientBackground() {
        val baseSurface = callbacks.resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        val primaryContainer = callbacks.resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
        val secondaryContainer = callbacks.resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val tertiaryContainer = callbacks.resolveThemeColor(com.google.android.material.R.attr.colorTertiaryContainer)

        val signature = baseSurface xor primaryContainer xor secondaryContainer xor tertiaryContainer
        if (cachedStartPageGradientSignature == signature) {
            return
        }

        val linearStart = ColorUtils.blendARGB(baseSurface, secondaryContainer, 0.30f)
        val linearMid = ColorUtils.blendARGB(baseSurface, tertiaryContainer, 0.28f)
        val linearEnd = ColorUtils.blendARGB(baseSurface, primaryContainer, 0.30f)

        val baseLayer = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(linearStart, linearMid, linearEnd)).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }

        val density = activity.resources.displayMetrics.density
        val blobs = arrayOf(
            createBlob(primaryContainer, tertiaryContainer, 0.45f, 460f * density, 0.18f, 0.22f, 170),
            createBlob(secondaryContainer, primaryContainer, 0.50f, 520f * density, 0.78f, 0.30f, 160),
            createBlob(tertiaryContainer, secondaryContainer, 0.42f, 540f * density, 0.55f, 0.82f, 150)
        )

        binding.startPageRoot.background = LayerDrawable(arrayOf(baseLayer) + blobs)
        cachedStartPageGradientSignature = signature
    }

    private fun createBlob(c1: Int, c2: Int, blend: Float, radius: Float, x: Float, y: Float, alpha: Int): GradientDrawable {
        val color = ColorUtils.blendARGB(c1, c2, blend)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radius
            setGradientCenter(x, y)
            colors = intArrayOf(ColorUtils.setAlphaComponent(color, alpha), Color.TRANSPARENT)
        }
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        return runCatching {
            activity.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun refreshStartPageQuickLinks() {
        val slots = BrowserPreferences.getStartPageSlots(activity).map { url ->
            val cleanUrl = url ?: ""
            val hasIcon = if (cleanUrl.isNotEmpty()) SiteIconCache.getCachedIcon(activity, cleanUrl) != null else false
            SlotItem(cleanUrl, hasIcon)
        }
        startPageAdapter.submitList(slots)
    }

    fun refreshStartPageResumeCard() {
        val lastUrl = BrowserPreferences.getLastVisitedUrl(activity)
        val show = !lastUrl.isNullOrBlank()
        binding.startPageResumeCard.isVisible = show
        if (!show) {
            return
        }
        val url = lastUrl!!
        applyResumeCardDensity()
        binding.startPageResumeTitle.text = bookmarkManager.displayTitleForUrl(url)
        binding.startPageResumeUrl.text = url

        val resources = activity.resources
        val density = resources.displayMetrics.density
        val iconSizeDp = resources.getDimension(R.dimen.start_page_resume_icon_size) / density
        binding.startPageResumeIconContainer.removeAllViews()
        binding.startPageResumeIconContainer.addView(
            bookmarkManager.createSiteIconBadge(
                url = url,
                sizeDp = iconSizeDp,
                cornerRadiusDp = iconSizeDp * 0.3f,
                paddingDp = iconSizeDp * 0.17f,
                backgroundColor = callbacks.resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
            )
        )
    }

    /**
     * On a short car screen the resume card competes with the grid, so it collapses to a
     * single line with a smaller badge and a tighter row; everywhere else it stays full size.
     */
    private fun applyResumeCardDensity() {
        val resources = activity.resources
        val compact = resources.getBoolean(R.bool.start_page_resume_compact)
        // Compact leaves exactly one line: the site title. The caption and the URL are what the
        // card can afford to lose when the grid needs the height.
        binding.startPageResumeCaption.isVisible = !compact
        binding.startPageResumeUrl.isVisible = !compact
        binding.startPageResumeCard.radius = resources.getDimension(R.dimen.start_page_resume_corner)

        (binding.startPageResumeIconContainer.parent as? android.view.ViewGroup)?.let { row ->
            row.minimumHeight = resources.getDimensionPixelSize(R.dimen.start_page_resume_row_min_height)
            val paddingV = resources.getDimensionPixelSize(R.dimen.start_page_resume_padding_v)
            row.setPaddingRelative(row.paddingStart, paddingV, row.paddingEnd, paddingV)
        }
    }

    fun handleStartPageBackgroundPicked(uri: Uri?) {
        if (uri == null) {
            return
        }
        if (activity.contentResolver.openInputStream(uri)?.use { true } != true) {
            Toast.makeText(activity, R.string.start_page_background_error, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val prev = BrowserPreferences.getStartPageBackgroundUri(activity)
        BrowserPreferences.setStartPageBackgroundUri(activity, uri.toString())
        if (!prev.isNullOrBlank() && prev != uri.toString()) {
            runCatching { activity.contentResolver.releasePersistableUriPermission(Uri.parse(prev), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        refreshStartPage()
        Toast.makeText(activity, R.string.start_page_background_set, Toast.LENGTH_SHORT).show()
    }

    fun clearStartPageBackground() {
        val prev = BrowserPreferences.getStartPageBackgroundUri(activity)
        if (prev == null) {
            return
        }
        runCatching { activity.contentResolver.releasePersistableUriPermission(Uri.parse(prev), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        BrowserPreferences.clearStartPageBackgroundUri(activity)
        refreshStartPage()
        Toast.makeText(activity, R.string.start_page_background_cleared, Toast.LENGTH_SHORT).show()
    }
}
