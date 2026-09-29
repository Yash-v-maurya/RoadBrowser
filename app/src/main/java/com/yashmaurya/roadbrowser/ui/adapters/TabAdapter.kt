package com.yashmaurya.roadbrowser.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.databinding.ItemBrowserTabBinding
import com.yashmaurya.roadbrowser.tabs.BrowserTab

class TabAdapter(
    private val bookmarkManager: BookmarkManager,
    private val activeTabIdProvider: () -> Long?,
    private val onTabClick: (BrowserTab) -> Unit,
    private val onTabClose: (BrowserTab) -> Unit,
    private val onReordered: (List<BrowserTab>) -> Unit,
    private val resolveThemeColor: (Int) -> Int,
    private val resolveReadableTextColor: (Int, Int, Int) -> Int
) : ListAdapter<BrowserTab, TabAdapter.TabViewHolder>(TabDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemBrowserTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        applyResponsiveSpan(recyclerView)
    }

    /**
     * Wide screens show the open tabs as a grid so a short head unit screen fits more than
     * three tabs at a glance; narrow screens keep the single column. The span count is
     * qualifier driven (see the dimens_settings.xml resources).
     */
    private fun applyResponsiveSpan(recyclerView: RecyclerView) {
        val spanCount = recyclerView.resources.getInteger(R.integer.list_span_count).coerceAtLeast(1)
        val current = recyclerView.layoutManager
        if (spanCount <= 1) {
            if (current == null) {
                recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
            }
            return
        }
        if (current is GridLayoutManager && current.spanCount == spanCount) {
            return
        }
        recyclerView.layoutManager = GridLayoutManager(recyclerView.context, spanCount)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val list = currentList.toMutableList()
        val item = list.removeAt(fromPosition)
        list.add(toPosition, item)
        submitList(list)
        onReordered(list)
    }

    inner class TabViewHolder(private val binding: ItemBrowserTabBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tab: BrowserTab) {
            val context = itemView.context
            val activeTabId = activeTabIdProvider()
            val isActive = tab.id == activeTabId

            val cardBackgroundColor = resolveThemeColor(
                if (isActive) com.google.android.material.R.attr.colorPrimaryContainer 
                else com.google.android.material.R.attr.colorSurfaceContainer
            )
            val primaryTextColor = resolveReadableTextColor(
                cardBackgroundColor,
                resolveThemeColor(if (isActive) com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSurface),
                resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
            )
            val secondaryTextColor = resolveReadableTextColor(
                cardBackgroundColor,
                resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
                primaryTextColor
            )

            binding.root.setCardBackgroundColor(cardBackgroundColor)
            binding.root.strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            binding.root.setOnClickListener { onTabClick(tab) }

            val iconSizeDp = context.resources.getDimension(R.dimen.list_item_icon_size) /
                context.resources.displayMetrics.density

            binding.iconContainer.removeAllViews()
            binding.iconContainer.addView(
                bookmarkManager.createSiteIconBadge(
                    url = tab.currentUrl.takeIf { bookmarkManager.isActiveWebsiteUrl(it) },
                    sizeDp = iconSizeDp,
                    cornerRadiusDp = 12f,
                    paddingDp = 6f,
                    backgroundColor = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
            )

            binding.activeBadge.visibility = if (isActive) View.VISIBLE else View.GONE
            binding.activeBadge.setTextColor(primaryTextColor)

            binding.titleText.text = tab.currentTitle.ifBlank { bookmarkManager.displayTitleForUrl(tab.currentUrl) }
            binding.titleText.setTextColor(primaryTextColor)

            binding.urlText.text = tab.currentUrl.ifBlank { context.getString(R.string.tab_manager_blank_subtitle) }
            binding.urlText.setTextColor(secondaryTextColor)

            binding.buttonClose.setOnClickListener { onTabClose(tab) }
        }
    }

    class TabDiffCallback : DiffUtil.ItemCallback<BrowserTab>() {
        override fun areItemsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean = false
    }
}
