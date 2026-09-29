package com.yashmaurya.roadbrowser.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.databinding.ItemStartPageSlotBinding

data class SlotItem(
    val url: String,
    val hasCachedIcon: Boolean
)

class StartPageAdapter(
    private val bookmarkManager: BookmarkManager,
    private val onSlotClick: (String) -> Unit,
    private val onReordered: (List<SlotItem>) -> Unit,
    private val resolveThemeColor: (Int) -> Int
) : ListAdapter<SlotItem, StartPageAdapter.SlotViewHolder>(SlotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val binding = ItemStartPageSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SlotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val list = currentList.toMutableList()
        val item = list.removeAt(fromPosition)
        list.add(toPosition, item)
        submitList(list)
        onReordered(list)
    }

    inner class SlotViewHolder(private val binding: ItemStartPageSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SlotItem) {
            val url = item.url
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val isEmpty = url.isEmpty()

            // Flat tiles: a raised paper surface with a hairline edge. Shadows under every tile
            // turn a grid into visual noise on a dashboard, so there are none.
            val corner = context.resources.getDimension(R.dimen.tile_corner_radius)
            binding.root.elevation = 0f
            binding.root.background = null
            binding.root.clipToOutline = true

            val surfaceColor = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLowest)
            val outlineColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            val hairline = (density).toInt().coerceAtLeast(1)

            val contentDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = corner
                if (isEmpty) {
                    // An empty slot is an invitation, not a destination: thinner paper and a
                    // dashed edge so it sits behind the filled tiles in the reading order.
                    setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 120))
                    setStroke(
                        hairline,
                        androidx.core.graphics.ColorUtils.setAlphaComponent(outlineColor, 140),
                        5f * density,
                        4f * density
                    )
                } else {
                    setColor(surfaceColor)
                    setStroke(hairline, outlineColor)
                }
            }

            val maskDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = corner
                setColor(android.graphics.Color.WHITE)
            }
            
            val rippleColor = android.content.res.ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(
                    resolveThemeColor(com.google.android.material.R.attr.colorOnSurface), 30
                )
            )
            
            val rippleDrawable = android.graphics.drawable.RippleDrawable(
                rippleColor,
                contentDrawable,
                maskDrawable
            )
            
            binding.slotContentLayout.background = rippleDrawable
            binding.slotContentLayout.setOnClickListener { onSlotClick(url) }

            val iconSizeDp = context.resources.getDimension(R.dimen.start_page_slot_icon_size) / density
            binding.iconContainer.removeAllViews()
            binding.iconContainer.addView(
                bookmarkManager.createSiteIconBadge(
                    url = if (isEmpty) null else url,
                    sizeDp = iconSizeDp,
                    cornerRadiusDp = iconSizeDp * 0.3f,
                    paddingDp = iconSizeDp * 0.18f,
                    backgroundColor = if (isEmpty) {
                        androidx.core.graphics.ColorUtils.setAlphaComponent(
                            resolveThemeColor(com.google.android.material.R.attr.colorSecondaryContainer),
                            140
                        )
                    } else {
                        resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer)
                    },
                    showAddOnEmptyUrl = true
                )
            )

            binding.titleText.text = if (isEmpty) {
                context.getString(R.string.start_page_slot_empty_title)
            } else {
                bookmarkManager.displayTitleForUrl(url)
            }
            val onSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariant = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            binding.titleText.setTextColor(if (isEmpty) onSurfaceVariant else onSurface)

            // On short car screens the URL line is dropped entirely rather than being
            // squeezed until it truncates mid-word.
            binding.labelView.isVisible = context.resources.getBoolean(R.bool.start_page_slot_show_label)
            binding.labelView.text = if (isEmpty) {
                context.getString(R.string.start_page_slot_empty_label)
            } else {
                bookmarkManager.displayLabelForUrl(url)
            }
            binding.labelView.setTextColor(
                if (isEmpty) {
                    androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceVariant, 170)
                } else {
                    onSurfaceVariant
                }
            )
        }
    }

    class SlotDiffCallback : DiffUtil.ItemCallback<SlotItem>() {
        override fun areItemsTheSame(oldItem: SlotItem, newItem: SlotItem): Boolean = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: SlotItem, newItem: SlotItem): Boolean = oldItem == newItem
    }
}
