package com.luv.couple.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.luv.couple.net.LuvApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** Memory-Cache für Hochzeitsbilder — downsampled, eigener Media-HTTP-Pool. */
object WeddingImageCache {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun peek(userId: String): Bitmap? =
        bitmaps[userId.trim().lowercase()]

    suspend fun getOrLoad(userId: String, maxSide: Int = 1080): Bitmap? {
        val key = userId.trim().lowercase()
        if (key.isBlank()) return null
        peek(key)?.let { return it }
        val mutex = locks.getOrPut(key) { Mutex() }
        return mutex.withLock {
            peek(key)?.let { return@withLock it }
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = LuvApiClient.downloadWeddingImage(key)
                    decodeSampled(bytes, maxSide)
                }.getOrNull()
            }
            if (bmp != null) bitmaps[key] = bmp
            bmp
        }
    }

    private fun decodeSampled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (largest / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
