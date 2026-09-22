package com.yashmaurya.roadbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yashmaurya.roadbrowser.data.BrowserPreferences

class ShareBookmarkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        finish()
    }

    private fun handleShareIntent(intent: Intent?) {
        val url = extractSharedUrl(intent)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.bookmark_share_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        if (BrowserPreferences.addBookmark(this, url)) {
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.bookmark_exists, Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null) return null

        if (intent.action == Intent.ACTION_SEND) {
            extractFirstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { return it }
            extractFirstUrl(intent.getStringExtra(Intent.EXTRA_SUBJECT))?.let { return it }
        }

        val dataUri = intent.data
        if (dataUri?.scheme?.lowercase() in listOf("http", "https")) {
            return dataUri.toString()
        }

        return null
    }

    private fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group().trim()
            if (candidate.isNotEmpty()) {
                val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: continue
                val scheme = parsed.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    return candidate
                }
            }
        }
        return null
    }
}