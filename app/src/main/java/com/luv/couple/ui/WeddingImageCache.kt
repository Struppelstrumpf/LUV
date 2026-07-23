package com.luv.couple.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.luv.couple.net.LuvApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/** Memory + Disk-Cache für Hochzeitsbilder — downsampled, eigener Media-HTTP-Pool. */
object WeddingImageCache {
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var diskDir: File? = null

    fun init(context: Context) {
        if (diskDir != null) return
        val dir = File(context.applicationContext.filesDir, "wedding-images")
        runCatching { dir.mkdirs() }
        diskDir = dir
    }

    private fun diskFile(key: String): File? {
        val dir = diskDir ?: return null
        val safe = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(64)
        return File(dir, "$safe.jpg")
    }

    fun peek(userId: String): Bitmap? {
        val key = userId.trim().lowercase()
        bitmaps[key]?.let { return it }
        val f = diskFile(key) ?: return null
        if (!f.isFile || f.length() <= 0L) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()?.also {
            bitmaps[key] = it
        }
    }

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
                    val decoded = decodeSampled(bytes, maxSide)
                    if (decoded != null) {
                        diskFile(key)?.let { f ->
                            runCatching {
                                val tmp = File(f.parentFile, f.name + ".tmp")
                                FileOutputStream(tmp).use { it.write(bytes) }
                                if (!tmp.renameTo(f)) {
                                    tmp.copyTo(f, overwrite = true)
                                    tmp.delete()
                                }
                            }
                        }
                    }
                    decoded
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
