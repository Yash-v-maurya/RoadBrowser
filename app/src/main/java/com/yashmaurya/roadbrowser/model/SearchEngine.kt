package com.yashmaurya.roadbrowser.model

import android.net.Uri
import com.yashmaurya.roadbrowser.R

enum class SearchEngine(
    val storageKey: String,
    val titleRes: Int,
    val homeUrl: String,
    private val searchTemplate: String
) {
    BRAVE(
        storageKey = "brave",
        titleRes = R.string.settings_search_engine_brave,
        homeUrl = "https://search.brave.com",
        searchTemplate = "https://search.brave.com/search?q=%s"
    ),
    GOOGLE(
        storageKey = "google",
        titleRes = R.string.settings_search_engine_google,
        homeUrl = "https://www.google.com",
        searchTemplate = "https://www.google.com/search?q=%s"
    ),
    DUCKDUCKGO(
        storageKey = "duckduckgo",
        titleRes = R.string.settings_search_engine_duckduckgo,
        homeUrl = "https://duckduckgo.com",
        searchTemplate = "https://duckduckgo.com/?q=%s"
    );

    fun buildSearchUrl(query: String): String = searchTemplate.format(Uri.encode(query))

    companion object {
        val DEFAULT = BRAVE

        fun fromKey(key: String?): SearchEngine {
            return entries.firstOrNull { it.storageKey == key } ?: DEFAULT
        }
    }
}
