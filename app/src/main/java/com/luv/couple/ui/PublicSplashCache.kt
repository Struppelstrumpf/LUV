package com.luv.couple.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.luv.couple.LuvApp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PublicCanvasPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Letztes Splash-Bild auf Disk — beim nächsten Start sofort sichtbar,
 * während parallel ein neues Random-Bild geholt wird.
 */
object PublicSplashCache {
    data class Entry(
        val preview: PublicCanvasPreview,
        val bitmap: Bitmap
    )

    private val mutex = Mutex()
    private val memory = AtomicReference<Entry?>(null)
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun dir(context: Context): File =
        File(context.cacheDir, "public_splash").also { it.mkdirs() }

    private fun imageFile(context: Context): File = File(dir(context), "last.jpg")
    private fun metaFile(context: Context): File = File(dir(context), "last.txt")

    fun memoryEntry(): Entry? = memory.get()

    suspend fun loadLast(context: Context): Entry? = withContext(Dispatchers.IO) {
        mutex.withLock {
            memory.get()?.let { return@withContext it }
            val img = imageFile(context)
            val meta = metaFile(context)
            if (!img.isFile || !meta.isFile) return@withContext null
            val lines = runCatching { meta.readText().lines() }.getOrNull() ?: return@withContext null
            if (lines.size < 4) return@withContext null
            val bmp = runCatching {
                BitmapFactory.decodeFile(img.absolutePath)
            }.getOrNull() ?: return@withContext null
            val entry = Entry(
                preview = PublicCanvasPreview(
                    id = lines[0],
                    lobbyName = "Lobby",
                    hostNickname = lines[1],
                    memberNicknames = emptyList(),
                    nameLine = lines[2].ifBlank { lines[1] },
                    imageUrl = lines[3]
                ),
                bitmap = bmp
            )
            memory.set(entry)
            entry
        }
    }

    suspend fun save(context: Context, preview: PublicCanvasPreview, bitmap: Bitmap) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val img = imageFile(context)
                    val meta = metaFile(context)
                    img.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    meta.writeText(
                        listOf(
                            preview.id,
                            preview.hostNickname,
                            preview.nameLine.ifBlank { preview.hostNickname },
                            preview.imageUrl
                        ).joinToString("\n")
                    )
                    memory.set(Entry(preview, bitmap))
                    runCatching {
                        LuvApp.instance.prefs.setLastPublicCanvas(
                            id = preview.id,
                            nameLine = preview.nameLine.ifBlank { preview.hostNickname },
                            imageUrl = preview.imageUrl,
                            hostNickname = preview.hostNickname
                        )
                    }
                }
            }
        }

    suspend fun downloadBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                LuvApiClient.baseUrl().trimEnd('/') + imageUrl
            }
            http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    /** Im Hintergrund: Random holen + cachen, damit der nächste Kaltstart sofort Bild hat. */
    suspend fun warmup(context: Context) {
        if (loadLast(context) != null) return
        val fetched = LuvApiClient.fetchRandomPublicCanvas() ?: return
        val bmp = downloadBitmap(fetched.imageUrl) ?: return
        save(context, fetched, bmp)
    }
}
