package com.yashmaurya.roadbrowser.settings

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yashmaurya.roadbrowser.AppConstants
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.model.AppThemeMode
import com.yashmaurya.roadbrowser.model.SearchEngine
import com.yashmaurya.roadbrowser.model.UserAgentProfile
import com.yashmaurya.roadbrowser.web.AdBlocker

data class SettingsCallbacks(
    val onClose: () -> Unit = {},
    val onThemeChanged: () -> Unit = {},
    val onPageDarkeningChanged: () -> Unit = {},
    val onScaleChanged: () -> Unit = {},
    val onHomePageChanged: () -> Unit = {},
    val onPickStartPageBackground: (() -> Unit)? = null,
    val onClearStartPageBackground: (() -> Unit)? = null,
    val onShieldsChanged: () -> Unit = {}
)

object SettingsViews {

    private const val MATCH_PARENT = LinearLayout.LayoutParams.MATCH_PARENT
    private const val WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT

    fun createSettingsContent(
        context: Context,
        includeDragHandle: Boolean = true,
        callbacks: SettingsCallbacks = SettingsCallbacks()
    ): View {
        val res = context.resources

        fun dp(v: Int): Int = (v * res.displayMetrics.density).toInt()
        fun px(dimenRes: Int): Int = res.getDimensionPixelSize(dimenRes)

        fun getColorFromAttr(attrResId: Int): Int {
            val tv = TypedValue()
            if (context.theme.resolveAttribute(attrResId, tv, true)) {
                if (tv.resourceId != 0) {
                    return androidx.core.content.ContextCompat.getColor(context, tv.resourceId)
                }
                return tv.data
            }
            return Color.TRANSPARENT
        }

        // Typography roles come from the theme (TextAppearance.RoadBrowser.*): titles are
        // 600 weight, body 400, labels 500. Never a raw textSize/textStyle here.
        fun styleFromAttr(attrResId: Int): Int {
            val tv = TypedValue()
            return if (context.theme.resolveAttribute(attrResId, tv, true)) tv.resourceId else 0
        }

        val roleTitleLarge = styleFromAttr(com.google.android.material.R.attr.textAppearanceTitleLarge)
        val roleTitleMedium = styleFromAttr(com.google.android.material.R.attr.textAppearanceTitleMedium)
        val roleTitleSmall = styleFromAttr(com.google.android.material.R.attr.textAppearanceTitleSmall)
        val roleBodyMedium = styleFromAttr(com.google.android.material.R.attr.textAppearanceBodyMedium)
        val roleBodySmall = styleFromAttr(com.google.android.material.R.attr.textAppearanceBodySmall)
        val roleLabelLarge = styleFromAttr(com.google.android.material.R.attr.textAppearanceLabelLarge)

        fun TextView.applyRole(styleRes: Int) {
            if (styleRes != 0) {
                setTextAppearance(styleRes)
            }
        }

        val columnCount = res.getInteger(R.integer.settings_column_count).coerceAtLeast(1)
        val columnGap = px(R.dimen.settings_column_gap)
        val cardSpacing = px(R.dimen.settings_card_spacing)
        val cardCorner = px(R.dimen.settings_card_corner_radius).toFloat()
        val cardPadH = px(R.dimen.settings_card_padding_horizontal)
        val cardPadV = px(R.dimen.settings_card_padding_vertical)
        val cardPadWide = px(R.dimen.settings_card_padding_wide)
        val headerPad = px(R.dimen.settings_header_padding)
        val sectionIconGap = px(R.dimen.settings_section_icon_gap)
        val sectionSpacing = px(R.dimen.settings_section_spacing)
        val rowMinHeight = px(R.dimen.settings_row_min_height)
        val rowPadH = px(R.dimen.settings_row_padding_horizontal)
        val rowPadV = px(R.dimen.settings_row_padding_vertical)
        val rowIconSize = px(R.dimen.settings_row_icon_size)
        val rowIconGap = px(R.dimen.settings_row_icon_gap)
        val rowCorner = px(R.dimen.settings_row_corner_radius).toFloat()
        val rowValueSpacing = px(R.dimen.settings_row_value_spacing)
        val buttonMinHeight = px(R.dimen.settings_button_min_height)

        fun createStyledCard(): MaterialCardView = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = cardSpacing
            }
            radius = cardCorner
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow))
        }

        fun createSectionTitle(
            titleText: String,
            iconRes: Int,
            iconWidthDp: Int = 0,
            iconHeightDp: Int = 0,
            tintIcon: Boolean = true,
            spaceBelow: Boolean = false
        ): LinearLayout {
            val defaultIcon = px(R.dimen.settings_section_icon_size)
            val iconWidth = if (iconWidthDp > 0) dp(iconWidthDp) else defaultIcon
            val iconHeight = if (iconHeightDp > 0) dp(iconHeightDp) else defaultIcon
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                if (spaceBelow) {
                    setPadding(0, 0, 0, sectionSpacing)
                }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconWidth, iconHeight).apply {
                        marginEnd = sectionIconGap
                    }
                    setImageResource(iconRes)
                    if (tintIcon) {
                        imageTintList = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                })

                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    text = titleText
                    applyRole(roleTitleMedium)
                })
            }
        }

        fun createListButton(idRes: Int, textStr: String, iconRes: Int): MaterialButton {
            return MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                id = idRes
                text = textStr
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                minHeight = buttonMinHeight
                minimumHeight = buttonMinHeight
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                applyRole(roleLabelLarge)
                setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                setIconResource(iconRes)
                iconSize = res.getDimensionPixelSize(R.dimen.icon_size_small)
                iconPadding = dp(12)
                iconTint = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconTintMode = android.graphics.PorterDuff.Mode.SRC_IN
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                alpha = 1.0f
                isClickable = true
                isFocusable = true
            }
        }

        fun showSuccessDialog(title: String, message: String) {
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        fun showConfirmationDialog(
            title: String,
            message: String,
            onConfirm: () -> Unit
        ) {
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_action_delete) { _, _ -> onConfirm() }
                .show()
        }

        fun rowRipple(): android.graphics.drawable.Drawable {
            val onSurfaceColorVal = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val rippleColor = ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColorVal, 30)
            )
            val contentBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = rowCorner
                setColor(Color.TRANSPARENT)
            }
            val maskBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = rowCorner
                setColor(Color.WHITE)
            }
            return android.graphics.drawable.RippleDrawable(rippleColor, contentBg, maskBg)
        }

        fun createSettingRow(
            title: String,
            statusText: String,
            iconRes: Int,
            onClick: () -> Unit
        ): LinearLayout {
            val onSurfaceColorVal = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariantColorVal = getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                minimumHeight = rowMinHeight
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)
                background = rowRipple()
                setOnClickListener { onClick() }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(rowIconSize, rowIconSize).apply {
                        marginEnd = rowIconGap
                    }
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                })

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                }
                textCol.addView(TextView(context).apply {
                    text = title
                    applyRole(roleTitleSmall)
                    setTextColor(onSurfaceColorVal)
                })
                textCol.addView(TextView(context).apply {
                    text = statusText
                    applyRole(roleBodyMedium)
                    setTextColor(onSurfaceVariantColorVal)
                    setPadding(0, rowValueSpacing, 0, 0)
                })
                addView(textCol)

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(rowIconSize, rowIconSize)
                    setImageResource(R.drawable.arrow_forward_24px)
                    imageTintList = ColorStateList.valueOf(onSurfaceVariantColorVal)
                    alpha = 0.5f
                })
            }
        }

        fun createSettingSwitchRow(
            title: String,
            description: String,
            iconRes: Int,
            isCheckedValue: Boolean,
            isEnabledValue: Boolean = true,
            onCheckedChange: (Boolean) -> Unit
        ): LinearLayout {
            val onSurfaceColorVal = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariantColorVal = getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            val switch = SwitchMaterial(context).apply {
                isChecked = isCheckedValue
                isEnabled = isEnabledValue
                minimumHeight = rowMinHeight
                setUseMaterialThemeColors(true)
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = isEnabledValue
                isFocusable = isEnabledValue
                minimumHeight = rowMinHeight
                setPadding(rowPadH, rowPadV, rowPadH, rowPadV)

                if (isEnabledValue) {
                    background = rowRipple()
                    setOnClickListener {
                        switch.toggle()
                    }
                } else {
                    alpha = 0.6f
                }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(rowIconSize, rowIconSize).apply {
                        marginEnd = rowIconGap
                    }
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                })

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                        marginEnd = rowIconGap
                    }
                }
                textCol.addView(TextView(context).apply {
                    text = title
                    applyRole(roleTitleSmall)
                    setTextColor(onSurfaceColorVal)
                })
                textCol.addView(TextView(context).apply {
                    text = description
                    applyRole(roleBodyMedium)
                    setTextColor(onSurfaceVariantColorVal)
                    setPadding(0, rowValueSpacing, 0, 0)
                })
                addView(textCol)

                switch.setOnCheckedChangeListener { _, isChecked ->
                    onCheckedChange(isChecked)
                }
                addView(switch)
            }
            return row
        }

        val smallIconSize = res.getDimensionPixelSize(R.dimen.icon_size_small)
        val onSurfaceColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariantColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            val sidePad = px(R.dimen.settings_page_padding_horizontal)
            setPadding(sidePad, 0, sidePad, px(R.dimen.settings_page_padding_bottom))
        }

        // Section cards are collected first and flowed into one or two columns at the end,
        // depending on the available width. The weight is a rough row count, used to keep
        // the two columns close to the same height on a wide, short head unit.
        val sectionCards = mutableListOf<Pair<View, Int>>()
        fun addSection(card: View, weight: Int) {
            sectionCards.add(card to weight)
        }

        if (includeDragHandle) {
            val handleFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setPadding(0, dp(12), 0, dp(16))
            }
            handleFrame.addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(5)).apply { gravity = Gravity.CENTER }
                setBackgroundResource(R.drawable.drag_handle_background)
            })
            container.addView(handleFrame)
        }

        val headerCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            radius = cardCorner
            cardElevation = 0f
            setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow))
            strokeWidth = dp(1)
            strokeColor = getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
        }
        val headerInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = rowMinHeight
            setPadding(headerPad, headerPad, headerPad, headerPad)
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginEnd = rowIconGap
            }
        }
        titleCol.addView(TextView(context).apply {
            id = R.id.settingsHeaderTitle
            text = context.getString(R.string.settings_title)
            applyRole(roleTitleLarge)
            setTextColor(onSurfaceColor)
        })
        titleCol.addView(TextView(context).apply {
            text = context.getString(R.string.settings_subtitle)
            applyRole(roleBodySmall)
            setTextColor(onSurfaceVariantColor)
            alpha = 0.8f
        })
        val backBtn = MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            id = R.id.buttonSettingsBack
            text = context.getString(R.string.menu_back)
            applyRole(roleLabelLarge)
            setTextColor(onSurfaceColor)
            minHeight = buttonMinHeight
            minimumHeight = buttonMinHeight
            setIconResource(R.drawable.arrow_back_24px)
            iconTint = ColorStateList.valueOf(onSurfaceColor)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconSize = smallIconSize
            iconPadding = dp(8)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        headerInner.addView(titleCol)
        headerInner.addView(backBtn)
        headerCard.addView(headerInner)
        container.addView(headerCard)

        val appearanceCard = createStyledCard()
        val appearanceInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        appearanceInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_appearance),
                R.drawable.settings_24px,
                spaceBelow = true
            )
        )

        val themeMode = BrowserPreferences.getThemeMode(context)
        val themeStatusText = when (themeMode) {
            AppThemeMode.AUTO -> context.getString(R.string.settings_theme_auto)
            AppThemeMode.LIGHT -> context.getString(R.string.settings_theme_light)
            AppThemeMode.DARK -> context.getString(R.string.settings_theme_dark)
        }
        val themeRow = createSettingRow(
            title = "App Theme",
            statusText = themeStatusText,
            iconRes = R.drawable.settings_24px
        ) {
            val themes = arrayOf(
                context.getString(R.string.settings_theme_auto),
                context.getString(R.string.settings_theme_light),
                context.getString(R.string.settings_theme_dark)
            )
            val selectedIndex = when (themeMode) {
                AppThemeMode.AUTO -> 0
                AppThemeMode.LIGHT -> 1
                AppThemeMode.DARK -> 2
            }
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    val newMode = when (which) {
                        1 -> AppThemeMode.LIGHT
                        2 -> AppThemeMode.DARK
                        else -> AppThemeMode.AUTO
                    }
                    if (newMode != themeMode) {
                        BrowserPreferences.setThemeMode(context, newMode)
                        callbacks.onThemeChanged()
                    }
                }
                .show()
        }
        appearanceInner.addView(themeRow)

        val betaDarkRow = createSettingSwitchRow(
            title = context.getString(R.string.settings_beta_dark_pages),
            description = context.getString(R.string.settings_beta_dark_pages_description),
            iconRes = R.drawable.devices_other_24px,
            isCheckedValue = BrowserPreferences.isBetaForceDarkPagesEnabled(context)
        ) { isChecked ->
            BrowserPreferences.setBetaForceDarkPagesEnabled(context, isChecked)
            callbacks.onPageDarkeningChanged()
        }
        appearanceInner.addView(betaDarkRow)

        appearanceCard.addView(appearanceInner)
        addSection(appearanceCard, 2)

        val displayScaleCard = createStyledCard()
        val displayScaleInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        displayScaleInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_display_scale),
                R.drawable.computer_24,
                spaceBelow = true
            )
        )

        val currentScale = BrowserPreferences.getGlobalScalePercent(context)
        val scaleRow = createSettingRow(
            title = context.getString(R.string.settings_display_scale),
            statusText = context.getString(R.string.settings_scale_option, currentScale),
            iconRes = R.drawable.computer_24
        ) {
            val presetOptions = listOf(85, 100, 115, 130, 150)
            val customText = "Custom..."
            val dialogOptions = presetOptions.map { context.getString(R.string.settings_scale_option, it) }.toMutableList()
            dialogOptions.add(customText)

            val selectedIndex = presetOptions.indexOf(currentScale).let { if (it >= 0) it else presetOptions.size }

            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select Page Zoom")
                .setSingleChoiceItems(dialogOptions.toTypedArray(), selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    if (which == presetOptions.size) {
                        val inputLayout = TextInputLayout(context).apply {
                            hint = context.getString(R.string.settings_scale_custom_hint)
                            helperText = context.getString(
                                R.string.settings_scale_custom_helper,
                                BrowserPreferences.MIN_GLOBAL_SCALE_PERCENT,
                                BrowserPreferences.MAX_GLOBAL_SCALE_PERCENT
                            )
                            setPadding(dp(24), dp(8), dp(24), dp(8))
                        }
                        val input = TextInputEditText(context).apply {
                            inputType = InputType.TYPE_CLASS_NUMBER
                            setText(currentScale.toString())
                        }
                        inputLayout.addView(input)

                        MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                            .setTitle("Enter Custom Zoom")
                            .setView(inputLayout)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok) { customDialog, _ ->
                                val entered = input.text?.toString()?.trim().orEmpty()
                                val value = entered.toIntOrNull()
                                if (value != null) {
                                    val sanitized = BrowserPreferences.sanitizeGlobalScalePercent(value)
                                    if (sanitized != currentScale) {
                                        BrowserPreferences.setGlobalScalePercent(context, sanitized)
                                        callbacks.onScaleChanged()
                                    }
                                }
                            }
                            .show()
                    } else {
                        val selectedPreset = presetOptions[which]
                        if (selectedPreset != currentScale) {
                            BrowserPreferences.setGlobalScalePercent(context, selectedPreset)
                            callbacks.onScaleChanged()
                        }
                    }
                }
                .show()
        }
        displayScaleInner.addView(scaleRow)

        displayScaleCard.addView(displayScaleInner)
        addSection(displayScaleCard, 1)

        val homePageCard = createStyledCard()
        val homePageInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        homePageInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_home_page),
                R.drawable.home_24px,
                spaceBelow = true
            )
        )

        val currentHomePage = BrowserPreferences.getHomePageUrl(context)
        val homePageStatusText = if (currentHomePage.isNullOrBlank()) {
            context.getString(R.string.settings_home_page_inactive)
        } else {
            currentHomePage
        }
        val homePageRow = createSettingRow(
            title = "Home Page URL",
            statusText = homePageStatusText,
            iconRes = R.drawable.home_24px
        ) {
            val inputLayout = TextInputLayout(context).apply {
                hint = context.getString(R.string.settings_home_page_hint)
                helperText = context.getString(R.string.settings_home_page_helper)
                setPadding(dp(24), dp(8), dp(24), dp(8))
            }
            val input = TextInputEditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setText(currentHomePage.orEmpty())
            }
            inputLayout.addView(input)

            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Set Home Page")
                .setView(inputLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Clear") { _, _ ->
                    BrowserPreferences.clearHomePageUrl(context)
                    callbacks.onHomePageChanged()
                    Toast.makeText(context, R.string.home_page_cleared, Toast.LENGTH_SHORT).show()
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val entered = input.text?.toString()?.trim().orEmpty()
                    if (entered.isNotBlank()) {
                        BrowserPreferences.setHomePageUrl(context, entered)
                        callbacks.onHomePageChanged()
                        Toast.makeText(context, R.string.home_page_set, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
        homePageInner.addView(homePageRow)

        homePageCard.addView(homePageInner)
        addSection(homePageCard, 1)

        val startupCard = createStyledCard()
        val startupInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        startupInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_startup),
                R.drawable.refresh_24px,
                spaceBelow = true
            )
        )

        val homePageSet = !BrowserPreferences.getHomePageUrl(context).isNullOrBlank()

        val restoreTabsRow = createSettingSwitchRow(
            title = context.getString(R.string.settings_restore_tabs_on_launch),
            description = if (homePageSet) context.getString(R.string.settings_restore_tabs_home_override) else context.getString(R.string.settings_restore_tabs_on_launch_description),
            iconRes = R.drawable.refresh_24px,
            isCheckedValue = BrowserPreferences.shouldRestoreTabsOnLaunch(context),
            isEnabledValue = !homePageSet
        ) { isChecked ->
            BrowserPreferences.setRestoreTabsOnLaunch(context, isChecked)
        }
        startupInner.addView(restoreTabsRow)

        val resumePageRow = createSettingSwitchRow(
            title = context.getString(R.string.settings_resume_last_page_on_launch),
            description = if (homePageSet) context.getString(R.string.settings_resume_last_page_home_override) else context.getString(R.string.settings_resume_last_page_on_launch_description),
            iconRes = R.drawable.refresh_24px,
            isCheckedValue = BrowserPreferences.shouldResumeLastPageOnLaunch(context),
            isEnabledValue = !homePageSet
        ) { isChecked ->
            BrowserPreferences.setResumeLastPageOnLaunch(context, isChecked)
        }
        startupInner.addView(resumePageRow)

        startupCard.addView(startupInner)
        addSection(startupCard, 2)

        val startPageCard = createStyledCard()
        val startPageInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadWide, cardPadV, cardPadWide, cardPadV)
        }
        startPageInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_start_page),
                R.drawable.kid_star_24px,
                spaceBelow = true
            )
        )

        val startPageCount = BrowserPreferences.getStartPageSites(context).size
        val countRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = rowMinHeight
            setPadding(0, rowPadV, 0, rowPadV)

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(rowIconSize, rowIconSize).apply { marginEnd = rowIconGap }
                setImageResource(R.drawable.kid_star_24px)
                imageTintList = ColorStateList.valueOf(getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            })
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(context).apply {
                text = "Quick Links"
                applyRole(roleTitleSmall)
                setTextColor(onSurfaceColor)
            })
            textCol.addView(TextView(context).apply {
                text = context.getString(
                    R.string.settings_start_page_count,
                    startPageCount,
                    BrowserPreferences.MAX_START_PAGE_SITES
                )
                applyRole(roleBodyMedium)
                setTextColor(onSurfaceVariantColor)
                setPadding(0, rowValueSpacing, 0, 0)
            })
            addView(textCol)
        }
        startPageInner.addView(countRow)

        val backgroundStatus = BrowserPreferences.getStartPageBackgroundUri(context)
        val bgStatusText = if (backgroundStatus.isNullOrBlank()) {
            context.getString(R.string.settings_start_page_background_default)
        } else {
            context.getString(R.string.settings_start_page_background_custom)
        }

        startPageInner.addView(TextView(context).apply {
            text = "Background Image"
            applyRole(roleTitleSmall)
            setTextColor(onSurfaceColor)
            setPadding(0, rowPadV, 0, rowValueSpacing)
        })

        startPageInner.addView(TextView(context).apply {
            text = bgStatusText
            applyRole(roleBodyMedium)
            setTextColor(onSurfaceVariantColor)
            setPadding(0, 0, 0, sectionSpacing)
        })

        val startPageButtons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val chooseBackgroundButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            text = context.getString(R.string.settings_start_page_choose_background)
            applyRole(roleLabelLarge)
            minHeight = buttonMinHeight
            minimumHeight = buttonMinHeight
            setIconResource(R.drawable.search_24px)
            iconSize = smallIconSize
            iconPadding = dp(8)
            isEnabled = callbacks.onPickStartPageBackground != null
            alpha = if (isEnabled) 1f else 0.6f
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                callbacks.onPickStartPageBackground?.invoke()
            }
        }
        val clearBackgroundButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = context.getString(R.string.settings_start_page_clear_background)
            applyRole(roleLabelLarge)
            minHeight = buttonMinHeight
            minimumHeight = buttonMinHeight
            setIconResource(R.drawable.delete_forever_24px)
            iconSize = smallIconSize
            iconPadding = dp(8)
            isEnabled = !backgroundStatus.isNullOrBlank() && callbacks.onClearStartPageBackground != null
            alpha = if (isEnabled) 1f else 0.6f
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
            setOnClickListener {
                callbacks.onClearStartPageBackground?.invoke()
            }
        }
        startPageButtons.addView(chooseBackgroundButton)
        startPageButtons.addView(clearBackgroundButton)
        startPageInner.addView(startPageButtons)

        startPageCard.addView(startPageInner)
        addSection(startPageCard, 3)

        val searchCard = createStyledCard()
        val searchInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        searchInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_search_engine),
                R.drawable.search_24px,
                spaceBelow = true
            )
        )
        searchInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_search_engine_description)
            applyRole(roleBodyMedium)
            setTextColor(onSurfaceVariantColor)
            setPadding(rowPadH, 0, rowPadH, sectionSpacing)
        })

        var currentEngine = BrowserPreferences.getSearchEngine(context)
        val engineOrder = SearchEngine.entries.toList()
        var searchEngineStatusView: TextView? = null
        val searchEngineRow = createSettingRow(
            title = context.getString(R.string.settings_search_engine),
            statusText = context.getString(currentEngine.titleRes),
            iconRes = R.drawable.search_24px
        ) {
            val engineLabels = engineOrder.map { context.getString(it.titleRes) }.toTypedArray()
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(R.string.settings_search_engine_dialog_title)
                .setSingleChoiceItems(engineLabels, engineOrder.indexOf(currentEngine)) { dialog, which ->
                    dialog.dismiss()
                    val selected = engineOrder[which]
                    if (selected != currentEngine) {
                        currentEngine = selected
                        BrowserPreferences.setSearchEngine(context, selected)
                        searchEngineStatusView?.text = context.getString(selected.titleRes)
                    }
                }
                .show()
        }
        // Row structure is icon / text column (title, value) / chevron.
        searchEngineStatusView =
            ((searchEngineRow.getChildAt(1) as? LinearLayout)?.getChildAt(1) as? TextView)
        searchInner.addView(searchEngineRow)

        searchCard.addView(searchInner)
        addSection(searchCard, 2)

        val shieldsCard = createStyledCard()
        val shieldsInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        shieldsInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_shields),
                R.drawable.security_24px,
                spaceBelow = true
            )
        )

        fun shieldsStatusText(enabled: Boolean): String = if (enabled) {
            context.getString(R.string.settings_shields_blocked_count, AdBlocker.blockedThisSession.toInt())
        } else {
            context.getString(R.string.settings_shields_disabled)
        }

        val shieldsStatusView = TextView(context).apply {
            text = shieldsStatusText(BrowserPreferences.isShieldsEnabled(context))
            applyRole(roleBodyMedium)
            setTextColor(onSurfaceVariantColor)
            setPadding(rowPadH, 0, rowPadH, sectionSpacing)
        }
        shieldsInner.addView(shieldsStatusView)

        val shieldsRow = createSettingSwitchRow(
            title = context.getString(R.string.settings_shields_title),
            description = context.getString(R.string.settings_shields_description),
            iconRes = R.drawable.security_24px,
            isCheckedValue = BrowserPreferences.isShieldsEnabled(context)
        ) { isChecked ->
            BrowserPreferences.setShieldsEnabled(context, isChecked)
            shieldsStatusView.text = shieldsStatusText(isChecked)
            callbacks.onShieldsChanged()
        }
        shieldsInner.addView(shieldsRow)

        shieldsCard.addView(shieldsInner)
        addSection(shieldsCard, 2)

        val mediaCard = createStyledCard()
        val mediaInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        mediaInner.addView(
            createSectionTitle(
                context.getString(R.string.settings_media_section_title),
                R.drawable.devices_other_24px,
                spaceBelow = true
            )
        )

        val youTubeCompatRow = createSettingSwitchRow(
            title = context.getString(R.string.settings_youtube_compat_title),
            description = context.getString(R.string.settings_youtube_compat_subtitle),
            iconRes = R.drawable.public_24px,
            isCheckedValue = BrowserPreferences.isYouTubeCompatEnabled(context)
        ) { isChecked ->
            BrowserPreferences.setYouTubeCompatEnabled(context, isChecked)
        }
        mediaInner.addView(youTubeCompatRow)

        val backgroundAudioRow = createSettingSwitchRow(
            title = context.getString(R.string.settings_background_audio_title),
            description = context.getString(R.string.settings_background_audio_subtitle),
            iconRes = R.drawable.devices_other_24px,
            isCheckedValue = BrowserPreferences.isBackgroundAudioEnabled(context)
        ) { isChecked ->
            BrowserPreferences.setBackgroundAudioEnabled(context, isChecked)
        }
        mediaInner.addView(backgroundAudioRow)

        val openPopupsInNewTabRow = createSettingSwitchRow(
            title = context.getString(R.string.settings_open_popups_in_new_tab_title),
            description = context.getString(R.string.settings_open_popups_in_new_tab_subtitle),
            iconRes = R.drawable.new_window_24px,
            isCheckedValue = BrowserPreferences.isOpenPopupsInNewTabEnabled(context)
        ) { isChecked ->
            BrowserPreferences.setOpenPopupsInNewTabEnabled(context, isChecked)
        }
        mediaInner.addView(openPopupsInNewTabRow)

        mediaCard.addView(mediaInner)
        addSection(mediaCard, 3)

        val uaCard = createStyledCard()
        val uaInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadH, cardPadV, cardPadH, cardPadV)
        }
        uaInner.addView(createSectionTitle(context.getString(R.string.settings_user_agent), R.drawable.devices_other_24px, spaceBelow = true))

        val currentProfile = BrowserPreferences.getUserAgentProfile(context)
        val uaStatusText = if (currentProfile == UserAgentProfile.SAFARI) {
            context.getString(R.string.settings_user_agent_safari)
        } else {
            context.getString(R.string.settings_user_agent_android)
        }
        val uaRow = createSettingRow(
            title = context.getString(R.string.settings_user_agent),
            statusText = uaStatusText,
            iconRes = R.drawable.devices_other_24px
        ) {
            val uaOptions = arrayOf(
                context.getString(R.string.settings_user_agent_android),
                context.getString(R.string.settings_user_agent_safari)
            )
            val selectedIndex = if (currentProfile == UserAgentProfile.SAFARI) 1 else 0
            MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Select User Agent")
                .setSingleChoiceItems(uaOptions, selectedIndex) { dialog, which ->
                    dialog.dismiss()
                    val selectedProfile = if (which == 1) UserAgentProfile.SAFARI else UserAgentProfile.ANDROID_CHROME
                    if (selectedProfile != currentProfile) {
                        BrowserPreferences.setUserAgentProfile(context, selectedProfile)
                    }
                }
                .show()
        }
        uaInner.addView(uaRow)

        uaCard.addView(uaInner)
        addSection(uaCard, 1)

        val siteDataCard = createStyledCard()
        val siteDataInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadWide, cardPadV, cardPadWide, cardPadV)
        }
        siteDataInner.addView(createSectionTitle(context.getString(R.string.settings_site_data_title), R.drawable.security_24px, spaceBelow = true))
        siteDataInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_site_data_description)
            applyRole(roleBodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, rowValueSpacing, 0, sectionSpacing)
        })
        val clearSitePermissionsButton = createListButton(
            R.id.buttonClearSitePermissions,
            context.getString(R.string.settings_clear_site_permissions),
            R.drawable.lock_reset_24px
        )
        val clearHttpHostsButton = createListButton(
            R.id.buttonClearHttpHosts,
            context.getString(R.string.settings_clear_http_hosts),
            R.drawable.security_24px
        )
        val clearCookiesButton = createListButton(
            R.id.buttonClearCookies,
            context.getString(R.string.settings_clear_cookies),
            R.drawable.delete_forever_24px
        )
        siteDataInner.addView(clearSitePermissionsButton)
        siteDataInner.addView(clearHttpHostsButton)
        siteDataInner.addView(clearCookiesButton)
        siteDataCard.addView(siteDataInner)
        addSection(siteDataCard, 4)

        val legalCard = createStyledCard()
        val legalInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadWide, cardPadV, cardPadWide, cardPadV)
        }
        legalInner.addView(createSectionTitle(context.getString(R.string.settings_legal), R.drawable.security_24px, spaceBelow = true))
        legalInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_legal_description)
            applyRole(roleBodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, 0, 0, sectionSpacing)
        })
        class LegalLink(val idRes: Int, val labelRes: Int, val iconRes: Int, val page: String)
        listOf(
            LegalLink(R.id.viewPrivacyPolicyButton, R.string.legal_privacy_policy, R.drawable.lock_24, LegalActivity.PRIVACY),
            LegalLink(R.id.viewTermsButton, R.string.legal_terms_of_use, R.drawable.info_24px, LegalActivity.TERMS),
            LegalLink(R.id.viewDrivingSafetyButton, R.string.legal_driving_safety, R.drawable.ic_warning_red, LegalActivity.DRIVING_SAFETY),
            LegalLink(R.id.viewNoticesButton, R.string.legal_notices, R.drawable.public_24px, LegalActivity.NOTICES)
        ).forEach { link ->
            legalInner.addView(createListButton(link.idRes, context.getString(link.labelRes), link.iconRes).apply {
                setOnClickListener {
                    // Same task (and so the same display, car screen included) when started from
                    // an activity.
                    val intent = LegalActivity.intent(context, link.page)
                    if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            })
        }
        legalCard.addView(legalInner)
        addSection(legalCard, 3)

        val licenseCard = createStyledCard()
        val licenseInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(cardPadWide, cardPadV, cardPadWide, cardPadV)
        }
        licenseInner.addView(createSectionTitle(context.getString(R.string.settings_license), R.drawable.gplv3, iconWidthDp = 48, iconHeightDp = 24, tintIcon = false, spaceBelow = true))
        licenseInner.addView(TextView(context).apply {
            text = context.getString(R.string.settings_license_description)
            applyRole(roleBodyMedium)
            setTextColor(onSurfaceColor)
            setPadding(0, 0, 0, sectionSpacing)
        })
        val viewAuthorButton = createListButton(R.id.viewAuthorButton, context.getString(R.string.author_name), R.drawable.ic_github)
        val viewSourceButton = createListButton(R.id.viewSourceButton, context.getString(R.string.settings_view_source), R.drawable.ic_github)
        val viewLicenseButton = createListButton(R.id.viewLicenseButton, context.getString(R.string.settings_license), R.drawable.info_24px)
        val viewOssLicensesButton = createListButton(R.id.viewOssLicensesButton, context.getString(R.string.open_source_view_licenses), R.drawable.search_24px)
        licenseInner.addView(viewAuthorButton)
        licenseInner.addView(viewSourceButton)
        licenseInner.addView(viewLicenseButton)
        licenseInner.addView(viewOssLicensesButton)
        licenseCard.addView(licenseInner)
        addSection(licenseCard, 5)

        if (columnCount <= 1) {
            sectionCards.forEach { (card, _) -> container.addView(card) }
        } else {
            val columnsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                isBaselineAligned = false
            }
            val columns = List(columnCount) { index ->
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = columnGap
                    }
                }
            }
            columns.forEach { columnsRow.addView(it) }

            val columnWeights = IntArray(columnCount)
            sectionCards.forEach { (card, weight) ->
                var target = 0
                for (i in 1 until columnCount) {
                    if (columnWeights[i] < columnWeights[target]) {
                        target = i
                    }
                }
                columns[target].addView(card)
                columnWeights[target] += weight
            }
            container.addView(columnsRow)
        }

        backBtn.setOnClickListener { callbacks.onClose() }

        fun openUrl(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
            }
        }

        viewAuthorButton.setOnClickListener { openUrl(AppConstants.AUTHOR_GITHUB_URL) }
        viewSourceButton.setOnClickListener { openUrl(AppConstants.GITHUB_REPO_URL) }
        viewLicenseButton.setOnClickListener { openUrl("https://www.gnu.org/licenses/gpl-3.0.html") }
        viewOssLicensesButton.setOnClickListener {
            try {
                val activityClass = Class.forName("com.google.android.gms.oss.licenses.OssLicensesMenuActivity")
                val intent = Intent(context, activityClass)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.error_generic_message, Toast.LENGTH_SHORT).show()
            }
        }

        clearSitePermissionsButton.setOnClickListener {
            showConfirmationDialog(
                title = context.getString(R.string.settings_clear_site_permissions_title),
                message = context.getString(R.string.settings_clear_site_permissions_message)
            ) {
                BrowserPreferences.clearSavedSitePermissions(context)
                android.webkit.GeolocationPermissions.getInstance().clearAll()
                android.webkit.WebView.clearClientCertPreferences(null)
                com.yashmaurya.roadbrowser.web.SslErrorHandlerHelper.clearAllowedSslHosts()
                showSuccessDialog(
                    title = context.getString(R.string.settings_clear_site_permissions_success_title),
                    message = context.getString(R.string.settings_clear_site_permissions_success_message)
                )
            }
        }

        clearHttpHostsButton.setOnClickListener {
            showConfirmationDialog(
                title = context.getString(R.string.settings_clear_http_hosts_title),
                message = context.getString(R.string.settings_clear_http_hosts_message)
            ) {
                BrowserPreferences.clearAllowedCleartextHosts(context)
                showSuccessDialog(
                    title = context.getString(R.string.settings_clear_http_hosts_success_title),
                    message = context.getString(R.string.settings_clear_http_hosts_success_message)
                )
            }
        }

        clearCookiesButton.setOnClickListener {
            showConfirmationDialog(
                title = context.getString(R.string.settings_clear_cookies_title),
                message = context.getString(R.string.settings_clear_cookies_message)
            ) {
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(context).apply {
                    clearHttpAuthUsernamePassword()
                }
                runCatching { context.deleteDatabase("webview.db") }
                runCatching { context.deleteDatabase("webviewCache.db") }
                runCatching {
                    val webViewCacheDir = java.io.File(context.cacheDir, "org.chromium.android_webview")
                    if (webViewCacheDir.exists()) {
                        webViewCacheDir.deleteRecursively()
                    }
                }
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    showSuccessDialog(
                        title = context.getString(R.string.settings_clear_cookies_success_title),
                        message = context.getString(R.string.settings_clear_cookies_success_message)
                    )
                }
            }
        }

        return container
    }

    fun createSettingsActivityView(context: Context): View = createSettingsContent(
        context = context,
        includeDragHandle = false,
        callbacks = SettingsCallbacks(
            onClose = { (context as? android.app.Activity)?.finish() },
            onThemeChanged = { (context as? android.app.Activity)?.recreate() },
            onPageDarkeningChanged = { (context as? android.app.Activity)?.recreate() },
            onScaleChanged = { (context as? android.app.Activity)?.recreate() },
            onHomePageChanged = { (context as? android.app.Activity)?.recreate() },
            onShieldsChanged = { (context as? android.app.Activity)?.recreate() }
        )
    )
}
