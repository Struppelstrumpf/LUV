package com.luv.couple.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.luv.couple.net.LuvApiClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Cache für Custom-Item-Bilder (img_*): RAM + Disk.
 * Disk-Hit → Bitmap schon im ersten Compose-Frame (kein 🐾-Flash).
 */
object ItemImageCache {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(6)
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val inflight = ConcurrentHashMap.newKeySet<String>()
    private val waiters = ConcurrentHashMap<String, MutableList<() -> Unit>>()

    @Volatile
    private var diskDir: File? = null

    fun init(context: Context) {
        if (diskDir != null) return
        val dir = File(context.applicationContext.filesDir, "pet-images")
        runCatching { dir.mkdirs() }
        diskDir = dir
        LuvApiClient.initMediaCache(context.applicationContext)
    }

    private fun diskFile(key: String): File? {
        val dir = diskDir ?: return null
        val safe = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(64)
        return File(dir, "$safe.png")
    }

    private fun readDisk(key: String): Bitmap? {
        val f = diskFile(key) ?: return null
        if (!f.isFile || f.length() <= 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(f.absolutePath)
        }.getOrNull()
    }

    private fun writeDisk(key: String, bytes: ByteArray) {
        val f = diskFile(key) ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            FileOutputStream(tmp).use { it.write(bytes) }
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun get(id: String): Bitmap? {
        val key = id.trim()
        if (!isImagePetId(key)) return null
        bitmaps[key]?.let { return it }
        readDisk(key)?.let {
            bitmaps[key] = it
            return it
        }
        ensureLoading(key)
        return null
    }

    /** Prefetch many ids (inventar / shop grid). */
    fun preloadAll(ids: Collection<String>) {
        ids.forEach { preload(it) }
    }

    /** Synchrone Ladung (z. B. Screenshot/Widget) — Cache nutzen, sonst blockierend laden. */
    fun getOrLoadSync(id: String): Bitmap? {
        val key = id.trim()
        if (!isImagePetId(key)) return null
        bitmaps[key]?.let { return it }
        readDisk(key)?.let {
            bitmaps[key] = it
            return it
        }
        val url = petImageUrl(key) ?: return null
        val pair = downloadBytes(url) ?: return null
        val bmp = BitmapFactory.decodeByteArray(pair, 0, pair.size) ?: return null
        bitmaps[key] = bmp
        writeDisk(key, pair)
        return bmp
    }

    /** Lädt asynchron und ruft [onReady] auf dem Main-Thread auf (einmal). */
    fun preload(id: String, onReady: (() -> Unit)? = null) {
        val key = id.trim()
        if (!isImagePetId(key)) {
            onReady?.let { main.post(it) }
            return
        }
        bitmaps[key]?.let {
            onReady?.let { cb -> main.post(cb) }
            return
        }
        readDisk(key)?.let {
            bitmaps[key] = it
            onReady?.let { cb -> main.post(cb) }
            return
        }
        if (onReady != null) {
            waiters.getOrPut(key) { mutableListOf() }.add(onReady)
        }
        ensureLoading(key)
    }

    private fun ensureLoading(key: String) {
        if (bitmaps.containsKey(key)) return
        if (!inflight.add(key)) return
        val url = petImageUrl(key) ?: run {
            inflight.remove(key)
            flushWaiters(key)
            return
        }
        io.execute {
            // Disk nochmal (Race mit anderem Thread)
            readDisk(key)?.let {
                bitmaps[key] = it
                inflight.remove(key)
                flushWaiters(key)
                return@execute
            }
            val bytes = downloadBytes(url)
            val bmp =
                if (bytes != null) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    null
                }
            if (bmp != null && bytes != null) {
                bitmaps[key] = bmp
                writeDisk(key, bytes)
            }
            inflight.remove(key)
            flushWaiters(key)
        }
    }

    private fun downloadBytes(url: String): ByteArray? {
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.bytes()
            }
        }.getOrNull()
    }

    private fun flushWaiters(key: String) {
        val list = waiters.remove(key) ?: return
        main.post {
            list.forEach { runCatching { it.invoke() } }
        }
    }
}
