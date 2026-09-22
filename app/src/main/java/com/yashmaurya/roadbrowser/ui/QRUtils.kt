package com.yashmaurya.roadbrowser.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for generating and caching QR codes.
 */
object QRUtils {

    private val qrCache = LruCache<String, Bitmap>(10)

    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        val cacheKey = "${content}_$size"
        val cached = qrCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val isBlack = bitMatrix.get(x, y)
                    bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
                }
            }
            
            qrCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        }
    }


    suspend fun generateQrCodeAsync(content: String, size: Int = 512): Bitmap? {
        val cacheKey = "${content}_$size"
        val cached = qrCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        return withContext(Dispatchers.Default) {
            generateQrCode(content, size)
        }
    }
}
