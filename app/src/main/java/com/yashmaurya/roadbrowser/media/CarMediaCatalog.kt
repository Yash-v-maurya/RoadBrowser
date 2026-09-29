package com.yashmaurya.roadbrowser.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDescription
import android.media.browse.MediaBrowser.MediaItem
import android.net.Uri
import com.yashmaurya.roadbrowser.R
import com.yashmaurya.roadbrowser.bookmarks.BookmarkManager
import com.yashmaurya.roadbrowser.data.BrowserPreferences
import com.yashmaurya.roadbrowser.data.SiteIconCache

/**
 * The list Android Auto shows for RoadBrowser in its media screen: the page that last played,
 * the start-page quick links and the bookmarks, each playable in [BackgroundWebPlayer].
 *
 * Only pages in this list can be played by media ID, so a client holding the session token
 * can't make the hidden player load an arbitrary URL.
 */
internal object CarMediaCatalog {

    const val ROOT_ID = "root"
    private const val QUICK_LINKS_ID = "quick_links"
    private const val BOOKMARKS_ID = "bookmarks"
    private const val PAGE_PREFIX = "page:"
    private const val ICON_SIZE_PX = 128

    data class Page(val url: String, val title: String)

    /** Builds the children of [parentId]. Reads icons from disk, so call it off the main thread. */
    fun children(context: Context, parentId: String): List<MediaItem> {
        return when (parentId) {
            ROOT_ID -> listOf(
                category(QUICK_LINKS_ID, context.getString(R.string.car_media_quick_links)),
                category(BOOKMARKS_ID, context.getString(R.string.car_media_bookmarks))
            )
            QUICK_LINKS_ID -> quickLinks(context).map { playable(context, it) }
            BOOKMARKS_ID -> bookmarks(context).map { playable(context, it) }
            else -> emptyList()
        }
    }

    /** Resolves a media ID from [children] back to its page, or null if it isn't in the list. */
    fun pageFor(context: Context, mediaId: String?): Page? {
        val url = mediaId?.takeIf { it.startsWith(PAGE_PREFIX) }?.removePrefix(PAGE_PREFIX) ?: return null
        return (quickLinks(context) + bookmarks(context)).firstOrNull { it.url == url }
    }

    /**
     * Best listed page for a voice request ("play jazz radio on RoadBrowser"): the one whose
     * title and address contain the most words of [query]. Null when no word matches at all.
     */
    fun search(context: Context, query: String): Page? {
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        return (quickLinks(context) + bookmarks(context))
            .map { page -> page to "${page.title} ${page.url}".lowercase().let { text -> words.count { it in text } } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun lastPlayed(context: Context): Page? {
        val entry = BrowserPreferences.getLastMediaPage(context) ?: return null
        val url = entry.url ?: return null
        return Page(url, entry.title?.takeIf { it.isNotBlank() } ?: BookmarkManager.titleForUrl(url))
    }

    private fun quickLinks(context: Context): List<Page> {
        val last = lastPlayed(context)
        val sites = BrowserPreferences.getStartPageSites(context)
            .filter { it != last?.url }
            .map { Page(it, BookmarkManager.titleForUrl(it)) }
        return listOfNotNull(last) + sites
    }

    private fun bookmarks(context: Context): List<Page> {
        return BrowserPreferences.getBookmarks(context).map { Page(it, BookmarkManager.titleForUrl(it)) }
    }

    private fun category(id: String, title: String): MediaItem {
        val description = MediaDescription.Builder()
            .setMediaId(id)
            .setTitle(title)
            .build()
        return MediaItem(description, MediaItem.FLAG_BROWSABLE)
    }

    private fun playable(context: Context, page: Page): MediaItem {
        val description = MediaDescription.Builder()
            .setMediaId(PAGE_PREFIX + page.url)
            .setTitle(page.title)
            .setSubtitle(Uri.parse(page.url).host?.removePrefix("www.").orEmpty())
            .setIconBitmap(icon(context, page.url))
            .build()
        return MediaItem(description, MediaItem.FLAG_PLAYABLE)
    }

    private fun icon(context: Context, url: String): Bitmap? {
        val bitmap = SiteIconCache.getCachedIcon(context, url) ?: return null
        if (bitmap.width <= ICON_SIZE_PX && bitmap.height <= ICON_SIZE_PX) return bitmap
        return Bitmap.createScaledBitmap(bitmap, ICON_SIZE_PX, ICON_SIZE_PX, true)
    }
}
