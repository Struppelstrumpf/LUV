package com.luv.couple.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.luv.couple.net.LuvApiClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Gemeinsamer Cache für Custom-Item-Bilder (img_*).
 * Für Compose (über petImageUrl), Canvas-Zeichnung und View-Leisten.
 */
object ItemImageCache {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val inflight = ConcurrentHashMap.newKeySet<String>()
    private val waiters = ConcurrentHashMap<String, MutableList<() -> Unit>>()

    fun get(id: String): Bitmap? {
        val key = id.trim()
        if (!isImagePetId(key)) return null
        bitmaps[key]?.let { return it }
        ensureLoading(key)
        return null
    }

    /** Synchrone Ladung (z. B. Screenshot/Widget) — Cache nutzen, sonst blockierend laden. */
    fun getOrLoadSync(id: String): Bitmap? {
        val key = id.trim()
        if (!isImagePetId(key)) return null
        bitmaps[key]?.let { return it }
        val url = petImageUrl(key) ?: return null
        val bmp = runCatching {
            val req = Request.Builder().url(url).get().build()
            LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val bytes = resp.body?.bytes() ?: return@runCatching null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull() ?: return null
        bitmaps[key] = bmp
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
            val bmp = runCatching {
                val req = Request.Builder().url(url).get().build()
                LuvApiClient.httpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val bytes = resp.body?.bytes() ?: return@runCatching null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }.getOrNull()
            if (bmp != null) bitmaps[key] = bmp
            inflight.remove(key)
            flushWaiters(key)
        }
    }

    private fun flushWaiters(key: String) {
        val list = waiters.remove(key) ?: return
        main.post {
            list.forEach { runCatching { it.invoke() } }
        }
    }
}
