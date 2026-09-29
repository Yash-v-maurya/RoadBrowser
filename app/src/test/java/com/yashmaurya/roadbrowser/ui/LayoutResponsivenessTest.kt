package com.yashmaurya.roadbrowser.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import com.yashmaurya.roadbrowser.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Headless layout responsiveness harness.
 *
 * Inflates the real production layouts under every screen in [ScreenMatrix], measures them with
 * an exact-width / at-most-height spec (exactly what a window does), lays them out, and asserts
 * they actually fit. Nothing here restyles anything: a failure is the deliverable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "sw480dp-w800dp-h480dp-land-notnight-mdpi")
class LayoutResponsivenessTest {

    // region -------- infrastructure --------

    /** Minimum Android touch target. */
    private val minTouchDp = 48
    /** Android Auto prefers bigger; anything below this on a car screen is warned about. */
    private val carPreferredTouchDp = 56
    /** The address field must keep at least this much room to be readable. */
    /**
     * The address field has to stay readable, but a flat floor is wrong on a small phone: 120dp
     * of a 320dp screen is more than a third of it. Ask for 120dp where there is room, and 32%
     * of the screen where there isn't.
     */
    private fun minAddressFieldDp(screenWidthDp: Int): Int =
        minOf(120, (screenWidthDp * 0.32f).toInt())
    /** Bottom chrome may not eat more than this share of a head-unit screen. */
    private val carChromeHeightBudget = 0.20
    /** A start page tile taller than this share of the screen leaves only one row visible. */
    private val tileHeightBudget = 0.40

    private val warnings = mutableListOf<String>()

    private lateinit var themed: Context
    private var density: Float = 1f

    /** Applies a screen configuration and returns a freshly themed context for it. */
    private fun applyScreen(screen: ScreenMatrix.Screen) {
        RuntimeEnvironment.setQualifiers(screen.qualifiers)
        val app = ApplicationProvider.getApplicationContext<Context>()
        themed = ContextThemeWrapper(app, R.style.Theme_RoadBrowser)
        density = themed.resources.displayMetrics.density
    }

    private fun px(dp: Int): Int = (dp * density).roundToInt()
    private fun dp(px: Int): Int = (px / density).roundToInt()
    private fun dpf(px: Int): Float = px / density

    private fun screenWidthPx(screen: ScreenMatrix.Screen) = px(screen.widthDp)
    private fun screenHeightPx(screen: ScreenMatrix.Screen) = px(screen.heightDp)

    private fun inflate(layoutRes: Int): View =
        LayoutInflater.from(themed).inflate(layoutRes, null, false)

    /** Measures with EXACTLY width / AT_MOST height, then lays out, like a real window. */
    private fun measureAndLayout(root: View, widthPx: Int, heightPx: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.AT_MOST),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    /** Inflates + measures activity_main for the given screen. */
    private fun mainScreen(screen: ScreenMatrix.Screen, showStartPage: Boolean = false): View {
        val root = inflate(R.layout.activity_main)
        if (showStartPage) {
            root.findViewById<View>(R.id.startPageRoot)?.visibility = View.VISIBLE
            root.findViewById<View>(R.id.webViewContainer)?.visibility = View.GONE
        }
        measureAndLayout(root, screenWidthPx(screen), screenHeightPx(screen))
        return root
    }

    private fun descendants(v: View): Sequence<View> = sequence {
        yield(v)
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) yieldAll(descendants(v.getChildAt(i)))
        }
    }

    private fun visibleDescendants(v: View): Sequence<View> =
        descendants(v).filter { it.visibility != View.GONE }

    /** The horizontal LinearLayout strip inside the bottom car toolbar. */
    private fun toolbarRow(root: View): LinearLayout {
        val card = root.findViewById<View>(R.id.carToolbar)
            ?: error("activity_main no longer has R.id.carToolbar")
        return descendants(card)
            .filterIsInstance<LinearLayout>()
            .first { it.orientation == LinearLayout.HORIZONTAL }
    }

    private fun idName(v: View): String =
        if (v.id == View.NO_ID) "<no id>/${v.javaClass.simpleName}"
        else try {
            v.resources.getResourceEntryName(v.id)
        } catch (_: Exception) {
            "id:0x%08x".format(v.id)
        }

    private fun marginStart(v: View): Int =
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0

    private fun marginEnd(v: View): Int =
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0

    private fun failIfAny(failures: List<String>) {
        assertTrue(
            "\n" + failures.joinToString("\n") { "  FAIL $it" } +
                "\n(${failures.size} layout responsiveness failure(s))",
            failures.isEmpty(),
        )
    }

    private fun dumpWarnings(title: String) {
        if (warnings.isEmpty()) return
        println("---- $title: ${warnings.size} warning(s) ----")
        warnings.forEach { println("  WARN $it") }
        println("---- end $title warnings ----")
        warnings.clear()
    }

    // endregion

    // region -------- 1. horizontal overflow --------

    /**
     * The failure that makes a head unit clip the menu button: a horizontal row whose children
     * need more width than the row has. Checked two ways for every horizontal LinearLayout in
     * the bottom toolbar: the required-width sum, and the actual laid-out right edges (weighted
     * children can be clamped to 0 and push later siblings off-screen).
     */
    @Test
    fun bottomToolbarHasNoHorizontalOverflow() {
        val failures = mutableListOf<String>()

        for (screen in ScreenMatrix.all()) {
            applyScreen(screen)
            val root = mainScreen(screen)
            val row = toolbarRow(root)

            val rows = descendants(row)
                .filterIsInstance<LinearLayout>()
                .filter { it.visibility != View.GONE && it.orientation == LinearLayout.HORIZONTAL }
                .toList()

            for (group in rows) {
                val available = group.measuredWidth
                val padding = group.paddingStart + group.paddingEnd
                var required = padding
                var maxRight = group.paddingStart
                var zeroWidthChild: View? = null

                for (i in 0 until group.childCount) {
                    val child = group.getChildAt(i)
                    if (child.visibility == View.GONE) continue
                    required += child.measuredWidth + marginStart(child) + marginEnd(child)
                    maxRight = max(maxRight, child.right + marginEnd(child))
                    if (child.measuredWidth == 0) zeroWidthChild = child
                }

                if (required > available) {
                    failures += "${screen.displayLabel}: ${idName(group)} children " +
                        "${dp(required)}dp exceed ${dp(available)}dp width"
                }
                if (maxRight > available - group.paddingEnd) {
                    val overflowing = (0 until group.childCount)
                        .map { group.getChildAt(it) }
                        .filter { it.visibility != View.GONE }
                        .filter { it.right + marginEnd(it) > available - group.paddingEnd }
                        .joinToString(", ") { idName(it) }
                    failures += "${screen.displayLabel}: ${idName(group)} lays out to " +
                        "${dp(maxRight)}dp, past the ${dp(available - group.paddingEnd)}dp " +
                        "content edge (clipped: $overflowing)"
                }
                zeroWidthChild?.let {
                    failures += "${screen.displayLabel}: ${idName(group)} squeezed " +
                        "${idName(it)} to 0dp wide (no room left in a " +
                        "${dp(available)}dp row)"
                }
            }
        }

        failIfAny(failures)
    }

    /** Same overflow rule applied to the list item rows, so a long row cannot clip its buttons. */
    @Test
    fun listItemRowsHaveNoHorizontalOverflow() {
        val failures = mutableListOf<String>()
        val layouts = listOf(
            R.layout.item_bookmark to "item_bookmark",
            R.layout.item_browser_tab to "item_browser_tab",
        )

        for (screen in ScreenMatrix.all()) {
            applyScreen(screen)
            for ((layoutRes, name) in layouts) {
                val root = inflate(layoutRes)
                measureAndLayout(root, screenWidthPx(screen), screenHeightPx(screen))

                for (group in visibleDescendants(root)
                    .filterIsInstance<LinearLayout>()
                    .filter { it.orientation == LinearLayout.HORIZONTAL }) {

                    val available = group.measuredWidth
                    var required = group.paddingStart + group.paddingEnd
                    for (i in 0 until group.childCount) {
                        val child = group.getChildAt(i)
                        if (child.visibility == View.GONE) continue
                        required += child.measuredWidth + marginStart(child) + marginEnd(child)
                    }
                    if (required > available) {
                        failures += "${screen.displayLabel}: $name/${idName(group)} children " +
                            "${dp(required)}dp exceed ${dp(available)}dp width"
                    }
                }
            }
        }

        failIfAny(failures)
    }

    // endregion

    // region -------- 2. touch targets --------

    /**
     * Every clickable view in the bottom toolbar and on the start page must be at least
     * 48dp x 48dp. Anything under 56dp on a car qualifier is reported as a warning, because
     * Android Auto's own guidance is larger than the phone minimum.
     */
    @Test
    fun clickableTargetsAreLargeEnough() {
        val failures = mutableListOf<String>()

        for (screen in ScreenMatrix.all()) {
            applyScreen(screen)
            val root = mainScreen(screen, showStartPage = true)

            val regions = listOf(
                "toolbar" to toolbarRow(root) as View,
                "startPage" to (root.findViewById<View>(R.id.startPageRoot)
                    ?: error("activity_main no longer has R.id.startPageRoot")),
            )

            for ((region, regionRoot) in regions) {
                for (view in visibleDescendants(regionRoot)) {
                    if (!view.isClickable) continue
                    // Containers that fill the region are not "targets" in the sense meant here.
                    if (view === regionRoot) continue

                    val wDp = dpf(view.measuredWidth)
                    val hDp = dpf(view.measuredHeight)

                    if (wDp + 0.5f < minTouchDp || hDp + 0.5f < minTouchDp) {
                        failures += "${screen.displayLabel}: $region/${idName(view)} measures " +
                            "${"%.0f".format(wDp)}x${"%.0f".format(hDp)}dp, " +
                            "below the ${minTouchDp}dp minimum touch target"
                    } else if (screen.isCar &&
                        (wDp + 0.5f < carPreferredTouchDp || hDp + 0.5f < carPreferredTouchDp)
                    ) {
                        warnings += "${screen.displayLabel}: $region/${idName(view)} measures " +
                            "${"%.0f".format(wDp)}x${"%.0f".format(hDp)}dp, " +
                            "under the ${carPreferredTouchDp}dp preferred car target"
                    }
                }
            }
        }

        dumpWarnings("clickableTargetsAreLargeEnough")
        failIfAny(failures)
    }

    /** Informational: list-row controls are outside the strict rule but still car relevant. */
    @Test
    fun reportListItemTouchTargets() {
        for (screen in ScreenMatrix.cars()) {
            applyScreen(screen)
            for ((layoutRes, name) in listOf(
                R.layout.item_bookmark to "item_bookmark",
                R.layout.item_browser_tab to "item_browser_tab",
            )) {
                val root = inflate(layoutRes)
                measureAndLayout(root, screenWidthPx(screen), screenHeightPx(screen))
                for (view in visibleDescendants(root)) {
                    if (!view.isClickable || view === root) continue
                    val wDp = dpf(view.measuredWidth)
                    val hDp = dpf(view.measuredHeight)
                    if (wDp + 0.5f < carPreferredTouchDp || hDp + 0.5f < carPreferredTouchDp) {
                        warnings += "${screen.displayLabel}: $name/${idName(view)} measures " +
                            "${"%.0f".format(wDp)}x${"%.0f".format(hDp)}dp"
                    }
                }
            }
        }
        dumpWarnings("reportListItemTouchTargets")
    }

    // endregion

    // region -------- 3. chrome budget --------

    /** On a head unit the bottom toolbar may not eat more than 20% of the screen height. */
    @Test
    fun bottomToolbarFitsCarChromeBudget() {
        val failures = mutableListOf<String>()

        for (screen in ScreenMatrix.cars()) {
            applyScreen(screen)
            val root = mainScreen(screen)
            val toolbar = root.findViewById<View>(R.id.carToolbar)
                ?: error("activity_main no longer has R.id.carToolbar")

            val budgetPx = screenHeightPx(screen) * carChromeHeightBudget
            if (toolbar.measuredHeight > budgetPx) {
                failures += "${screen.displayLabel}: carToolbar is " +
                    "${dp(toolbar.measuredHeight)}dp tall, over the " +
                    "${dp(budgetPx.toInt())}dp budget (20% of ${screen.heightDp}dp)"
            }
        }

        failIfAny(failures)
    }

    // endregion

    // region -------- 4. address field not crushed --------

    /** The address pill's text field has to stay readable on every screen. */
    @Test
    fun addressFieldIsNotCrushed() {
        val failures = mutableListOf<String>()

        for (screen in ScreenMatrix.all()) {
            applyScreen(screen)
            val root = mainScreen(screen)
            val field = descendants(toolbarRow(root))
                .filterIsInstance<TextInputEditText>()
                .firstOrNull()
                ?: error("bottom toolbar no longer has a TextInputEditText")

            // A loaded page shows one of the two mutually exclusive security icons, which is the
            // real worst case for the field's width — measure that, not just the blank state.
            descendants(toolbarRow(root))
                .firstOrNull { idName(it) == "persistentAddressSecureIcon" }
                ?.let { it.visibility = android.view.View.VISIBLE }
            measureAndLayout(root, screenWidthPx(screen), screenHeightPx(screen))

            val minDp = minAddressFieldDp(screen.widthDp)
            val wDp = dpf(field.measuredWidth)
            if (wDp + 0.5f < minDp) {
                failures += "${screen.displayLabel}: ${idName(field)} measures " +
                    "${"%.0f".format(wDp)}dp wide, below the ${minDp}dp minimum " +
                    "(screen is ${screen.widthDp}dp)"
            }
        }

        failIfAny(failures)
    }

    // endregion

    // region -------- 5. start page tiles --------

    /** A tile taller than 40% of the screen means only one row is visible on a short car screen. */
    @Test
    fun startPageTileFitsShortScreens() {
        val failures = mutableListOf<String>()

        for (screen in ScreenMatrix.all()) {
            applyScreen(screen)
            val tile = inflate(R.layout.item_start_page_slot)
            measureAndLayout(tile, screenWidthPx(screen), screenHeightPx(screen))

            val budgetPx = screenHeightPx(screen) * tileHeightBudget
            if (tile.measuredHeight > budgetPx) {
                failures += "${screen.displayLabel}: item_start_page_slot measures " +
                    "${dp(tile.measuredHeight)}dp tall, over the ${dp(budgetPx.toInt())}dp " +
                    "budget (40% of ${screen.heightDp}dp)"
            }
        }

        failIfAny(failures)
    }

    // endregion

    // region -------- 6. whole-layout fit --------

    /** Every layout in scope inflates and fits the exact window width at every qualifier. */
    @Test
    fun allLayoutsInflateAndFitWindowWidth() {
        val failures = mutableListOf<String>()
        val layouts = listOf(
            R.layout.activity_main to "activity_main",
            R.layout.activity_settings to "activity_settings",
            R.layout.item_start_page_slot to "item_start_page_slot",
            R.layout.item_bookmark to "item_bookmark",
            R.layout.item_browser_tab to "item_browser_tab",
        )

        for (screen in ScreenMatrix.all()) {
            applyScreen(screen)
            val widthPx = screenWidthPx(screen)
            val heightPx = screenHeightPx(screen)

            for ((layoutRes, name) in layouts) {
                val root = try {
                    inflate(layoutRes)
                } catch (t: Throwable) {
                    failures += "${screen.displayLabel}: $name failed to inflate: $t"
                    continue
                }
                measureAndLayout(root, widthPx, heightPx)

                if (root.measuredWidth > widthPx) {
                    failures += "${screen.displayLabel}: $name measures " +
                        "${dp(root.measuredWidth)}dp wide on a ${screen.widthDp}dp screen"
                }
                if (name == "activity_main" && root.measuredHeight > heightPx) {
                    failures += "${screen.displayLabel}: $name measures " +
                        "${dp(root.measuredHeight)}dp tall on a ${screen.heightDp}dp screen"
                }
            }
        }

        failIfAny(failures)
    }

    // endregion

    // region -------- 7. metrics table --------

    /** Informational: prints the measured numbers behind every budget, for the report. */
    @Test
    fun printMeasuredMetrics() {
        println("---- measured layout metrics ----")
        println(
            "%-28s %8s %10s %10s %10s %10s".format(
                "qualifier", "screen", "toolbarH", "pillEdit", "tileH", "btn",
            )
        )
        for (screen in ScreenMatrix.all()) {
            applyScreen(screen)
            val root = mainScreen(screen)
            val toolbar = root.findViewById<View>(R.id.carToolbar)
            val field = descendants(toolbarRow(root)).filterIsInstance<TextInputEditText>().first()
            val btn = root.findViewById<View>(R.id.toolbarButtonMenu)
            val tile = inflate(R.layout.item_start_page_slot)
            measureAndLayout(tile, screenWidthPx(screen), screenHeightPx(screen))
            println(
                "%-28s %8s %10s %10s %10s %10s".format(
                    screen.displayLabel,
                    "${screen.widthDp}x${screen.heightDp}",
                    "${dp(toolbar.measuredHeight)}dp/" +
                        "%.0f%%".format(100.0 * toolbar.measuredHeight / screenHeightPx(screen)),
                    "${dp(field.measuredWidth)}dp",
                    "${dp(tile.measuredHeight)}dp/" +
                        "%.0f%%".format(100.0 * tile.measuredHeight / screenHeightPx(screen)),
                    "${dp(btn.measuredWidth)}x${dp(btn.measuredHeight)}dp",
                )
            )
        }
        println("---- end measured layout metrics ----")
    }

    // endregion
}
