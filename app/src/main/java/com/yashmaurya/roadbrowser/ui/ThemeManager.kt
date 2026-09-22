package com.yashmaurya.roadbrowser.ui

import android.content.res.ColorStateList
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.yashmaurya.roadbrowser.databinding.ActivityMainBinding

class ThemeManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding
) {

    fun resolveThemeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        if (activity.theme.resolveAttribute(attrRes, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(activity, typedValue.resourceId)
            }
            return typedValue.data
        }
        return android.graphics.Color.TRANSPARENT
    }

    fun resolveReadableTextColor(backgroundColor: Int, preferredColor: Int, fallbackColor: Int): Int {
        val preferredContrast = ColorUtils.calculateContrast(preferredColor, backgroundColor)
        val fallbackContrast = ColorUtils.calculateContrast(fallbackColor, backgroundColor)
        return if (preferredContrast >= fallbackContrast) {
            preferredColor
        } else {
            fallbackColor
        }
    }

    fun applyMenuHeaderColors() {
        val headerBg = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow)
        val onSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val onPrimary = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
        
        val textCol = resolveReadableTextColor(headerBg, onSurface, onPrimary)
        val tint = ColorStateList.valueOf(textCol)
        
        updateTitleColors(textCol)
        updateButtonColors(textCol, tint)
    }

    private fun updateTitleColors(textColor: Int) {
        val titleViews = listOf(
            binding.menuTitle,
            binding.pageTitle,
            binding.bookmarkManagerTitle,
            binding.bookmarkManagerSubtitle,
            binding.tabManagerTitle,
            binding.tabManagerSubtitle,
            binding.checkLatestViewTitle,
            binding.checkLatestViewSubtitle
        )
        titleViews.forEach { 
            it.setTextColor(textColor) 
        }
    }

    private fun updateButtonColors(textColor: Int, tint: ColorStateList) {
        val buttons = listOf(
            binding.buttonClose,
            binding.buttonBookmarkManagerBack,
            binding.buttonTabManagerBack,
            binding.buttonCheckLatestBack
        )
        buttons.forEach { button ->
            button.setTextColor(textColor)
            button.iconTint = tint
            button.strokeColor = tint
        }
    }
}
