package com.yashmaurya.roadbrowser.ui

/**
 * The set of screens RoadBrowser has to survive on.
 *
 * Adding a new screen is one line in [BASE]. Every entry is automatically run in both the
 * day (`notnight`) and dark (`night`) resource variants by [all].
 *
 * [qualifiers] is a Robolectric/AAPT qualifier string. AAPT only accepts qualifiers in the
 * canonical order (smallestWidth, availableWidth, availableHeight, orientation, night mode,
 * density), so [label] carries the short human form used in failure messages while
 * [qualifiers] carries the machine form. `sw` is stated explicitly because it decides whether
 * `values-sw600dp/dimens.xml` wins over `values-land/dimens.xml`, which changes the toolbar
 * metrics substantially.
 */
object ScreenMatrix {

    data class Screen(
        /** Short, human readable name used in assertion failure messages. */
        val label: String,
        /** Everything up to and including orientation, in canonical AAPT order. */
        private val prefix: String,
        /** Density bucket qualifier, e.g. `mdpi`, `xhdpi`, `xxhdpi`. */
        private val density: String,
        val widthDp: Int,
        val heightDp: Int,
        /** True for head-unit style screens, where the stricter car budgets apply. */
        val isCar: Boolean,
        val night: Boolean = false,
    ) {
        val qualifiers: String
            get() = "$prefix-${if (night) "night" else "notnight"}-$density"

        val displayLabel: String
            get() = if (night) "$label-night" else label

        fun night(): Screen = copy(night = true)
    }

    /** One line per screen. */
    private val BASE: List<Screen> = listOf(
        // Baseline Android Auto head unit: 800x480 @160dpi, so 1px == 1dp. Tightest case.
        Screen("w800dp-h480dp-mdpi-land", "sw480dp-w800dp-h480dp-land", "mdpi", 800, 480, isCar = true),
        Screen("w1024dp-h600dp-mdpi-land", "sw600dp-w1024dp-h600dp-land", "mdpi", 1024, 600, isCar = true),
        Screen("w1280dp-h720dp-mdpi-land", "sw720dp-w1280dp-h720dp-land", "mdpi", 1280, 720, isCar = true),
        // Ultrawide head unit.
        Screen("w1920dp-h720dp-mdpi-land", "sw720dp-w1920dp-h720dp-land", "mdpi", 1920, 720, isCar = true),
        // Phone, portrait and landscape.
        Screen("w411dp-h891dp-xxhdpi-port", "sw411dp-w411dp-h891dp-port", "xxhdpi", 411, 891, isCar = false),
        Screen("w891dp-h411dp-xxhdpi-land", "sw411dp-w891dp-h411dp-land", "xxhdpi", 891, 411, isCar = false),
        // Smallest sane phone.
        Screen("w320dp-h480dp-mdpi-port", "sw320dp-w320dp-h480dp-port", "mdpi", 320, 480, isCar = false),
        // Tablet.
        Screen("w800dp-h1280dp-xhdpi-port", "sw800dp-w800dp-h1280dp-port", "xhdpi", 800, 1280, isCar = false),
    )

    /** Every screen in both day and night resource variants. */
    fun all(): List<Screen> = BASE.flatMap { listOf(it, it.night()) }

    /** Only the head-unit screens, in both day and night variants. */
    fun cars(): List<Screen> = all().filter { it.isCar }
}
