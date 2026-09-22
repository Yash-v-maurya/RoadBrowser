package com.yashmaurya.roadbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

object SiteIconCache {
    private const val ICON_DIR = "site-icons"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlightCallbacks = Collections.synchronizedMap(mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>())

    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 16
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    fun getCachedIcon(context: Context, url: String?): Bitmap? {
        val hostKey = hostKey(url) ?: return null
        val memBitmap = memoryCache.get(hostKey)
        if (memBitmap != null) return memBitmap

        val file = iconFile(context, url) ?: return null
        if (!file.exists()) return null
        val diskBitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (diskBitmap != null) {
            memoryCache.put(hostKey, diskBitmap)
        }
        return diskBitmap
    }

    fun cacheIcon(context: Context, pageUrl: String?, bitmap: Bitmap?) {
        if (bitmap == null) return
        val hostKey = hostKey(pageUrl)
        if (hostKey != null) {
            memoryCache.put(hostKey, bitmap)
        }
        val file = iconFile(context, pageUrl) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    fun prefetchIconIfNeeded(
        context: Context,
        pageUrl: String?,
        onComplete: (Bitmap?) -> Unit = {}
    ) {
        val cached = getCachedIcon(context, pageUrl)
        if (cached != null) {
            onComplete(cached)
            return
        }

        val hostKey = hostKey(pageUrl) ?: run {
            onComplete(null)
            return
        }

        var isFirstRequest = false
        val listToNotify = synchronized(inFlightCallbacks) {
            val list = inFlightCallbacks[hostKey]
            if (list == null) {
                isFirstRequest = true
                val newList = mutableListOf(onComplete)
                inFlightCallbacks[hostKey] = newList
                newList
            } else {
                list.add(onComplete)
                null
            }
        }

        if (!isFirstRequest) return

        Thread {
            val bitmap = runCatching {
                val request = Request.Builder()
                    .url(resolveIconUrl(pageUrl) ?: return@runCatching null)
                    .header("User-Agent", "RoadBrowser Icon Fetcher")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body
                    body.byteStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            }.getOrNull()

            if (bitmap != null) {
                cacheIcon(context, pageUrl, bitmap)
            }

            val pending = synchronized(inFlightCallbacks) {
                inFlightCallbacks.remove(hostKey)
            }

            if (pending != null) {
                mainHandler.post {
                    pending.forEach { callback ->
                        callback(bitmap)
                    }
                }
            }
        }.start()
    }

    private fun iconFile(context: Context, url: String?): File? {
        val hostKey = hostKey(url) ?: return null
        return File(File(context.cacheDir, ICON_DIR), "$hostKey.png")
    }

    private fun hostKey(url: String?): String? {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
            ?.replace(Regex("[^a-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
        return host
    }

    private fun resolveIconUrl(pageUrl: String?): String? {
        val uri = runCatching { Uri.parse(pageUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        return when (host) {
            "m.youtube.com",
            "www.youtube.com",
            "youtube.com" -> "https://www.youtube.com/favicon.ico"
            "www.google.com",
            "google.com" -> "https://www.google.com/favicon.ico"
            else -> {
                val scheme = uri.scheme?.takeIf { it.equals("http", true) || it.equals("https", true) } ?: "https"
                "$scheme://$host/favicon.ico"
            }
        }
    }
}
