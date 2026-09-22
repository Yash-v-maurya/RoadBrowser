package com.yashmaurya.roadbrowser.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.databinding.ItemBookmarkBinding

class BookmarkAdapter(
    private val bookmarkManager: BookmarkManager,
    private val onBookmarkClick: (String) -> Unit,
    private val onPinClick: (String, Int) -> Unit,
    private val onDeleteClick: (String) -> Unit,
    private val onReordered: (List<String>) -> Unit,
    private val resolveThemeColor: (Int) -> Int,
    private val isHomePageEnabled: () -> Boolean
) : ListAdapter<String, BookmarkAdapter.BookmarkViewHolder>(BookmarkDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val binding = ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val list = currentList.toMutableList()
        val item = list.removeAt(fromPosition)
        list.add(toPosition, item)
        submitList(list)
    }

    fun onReorderedComplete() {
        onReordered(currentList)
    }

    inner class BookmarkViewHolder(private val binding: ItemBookmarkBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookmark: String) {
            val context = itemView.context
            val startPageSlot = BrowserPreferences.findStartPageSlot(context, bookmark)
            val currentHomePage = BrowserPreferences.getHomePageUrl(context)
            val isHomePage = currentHomePage == bookmark
            val homePageEnabled = isHomePageEnabled()

            binding.root.setCardBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainer))
            binding.root.strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            binding.root.setOnClickListener { onBookmarkClick(bookmark) }

            binding.iconContainer.removeAllViews()
            binding.iconContainer.addView(
                bookmarkManager.createSiteIconBadge(
                    url = bookmark,
                    sizeDp = 40f,
                    cornerRadiusDp = 12f,
                    paddingDp = 6f,
                    backgroundColor = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
            )

            binding.titleText.text = bookmarkManager.displayLabelForUrl(bookmark)
            binding.urlText.text = bookmark

            val badgeText = StringBuilder()
            if (startPageSlot >= 0) {
                badgeText.append(context.getString(R.string.bookmark_start_page_badge, context.getString(R.string.start_page_slot_number, startPageSlot + 1)))
            }
            if (isHomePage) {
                if (badgeText.isNotEmpty()) badgeText.append(" | ")
                badgeText.append(context.getString(R.string.bookmark_home_page_badge))
            }
            binding.badgeText.text = badgeText.toString()
            binding.badgeText.visibility = if (badgeText.isNotEmpty()) View.VISIBLE else View.GONE
            binding.badgeText.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))

            val isPinned = startPageSlot >= 0
            val bgAttr = if (isPinned) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSecondaryContainer
            val iconAttr = if (isPinned) com.google.android.material.R.attr.colorOnPrimaryContainer else com.google.android.material.R.attr.colorOnSecondaryContainer
            
            binding.buttonPin.backgroundTintList = ColorStateList.valueOf(resolveThemeColor(bgAttr))
            binding.buttonPin.iconTint = ColorStateList.valueOf(resolveThemeColor(iconAttr))
            binding.buttonPin.alpha = if (homePageEnabled) 0.5f else 1.0f
            binding.buttonPin.setOnClickListener { onPinClick(bookmark, startPageSlot) }

            binding.buttonDelete.setOnClickListener { onDeleteClick(bookmark) }
        }
    }

    class BookmarkDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
