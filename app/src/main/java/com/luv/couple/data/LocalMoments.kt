package com.luv.couple.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class LocalMoment(
    val id: String,
    val file: File,
    val name: String,
    val dateAddedSec: Long
)

object LocalMoments {
    private const val DIR = "moments"
    private const val LEGACY_FOLDER = "LUV"
    private const val TOMBSTONE_FILE = ".deleted_names"

    fun momentsDir(context: Context): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun save(context: Context, bitmap: Bitmap, namePrefix: String = "LUV_"): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                val suffix = UUID.randomUUID().toString().take(6)
                val name = "${namePrefix}${stamp}_$suffix.png"
                val file = File(momentsDir(context), name)
                file.outputStream().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("Speichern fehlgeschlagen")
                    }
                }
                // Falls früher gelöscht und neu gespeichert: Tombstone entfernen
                clearTombstone(context, name)
                name
            }
        }

    suspend fun list(context: Context): List<LocalMoment> = withContext(Dispatchers.IO) {
        importLegacyMediaStoreIfPossible(context)
        val dir = momentsDir(context)
        val blocked = loadTombstones(context)
        dir.listFiles { f ->
            f.isFile &&
                f.name.endsWith(".png", ignoreCase = true) &&
                f.name !in blocked
        }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { file ->
                LocalMoment(
                    id = file.name,
                    file = file,
                    name = file.name,
                    dateAddedSec = (file.lastModified() / 1000L).coerceAtLeast(0L)
                )
            }
    }

    /** Ohne Rechte: nur eigene Beiträge aus Pictures/LUV in den App-Ordner kopieren. */
    private fun importLegacyMediaStoreIfPossible(context: Context) {
        runCatching {
            val blocked = loadTombstones(context)
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection: String
            val args: Array<String>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                args = arrayOf("%${Environment.DIRECTORY_PICTURES}/$LEGACY_FOLDER%")
            } else {
                selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                args = arrayOf("LUV_%")
            }
            val dir = momentsDir(context)
            resolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol).orEmpty()
                    if (!name.startsWith("LUV_", ignoreCase = true)) continue
                    if (name in blocked) continue
                    val target = File(dir, name)
                    if (target.exists()) continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    val addedSec = cursor.getLong(dateCol)
                    if (addedSec > 0L) target.setLastModified(addedSec * 1000L)
                }
            }
        }
    }

    suspend fun loadThumbnail(context: Context, moment: LocalMoment, sizePx: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(moment.file.absolutePath, bounds)
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                while (maxSide / sample > sizePx * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(moment.file.absolutePath, opts)
            }.getOrNull()
        }

    suspend fun loadFull(moment: LocalMoment): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(moment.file.absolutePath) }.getOrNull()
    }

    suspend fun delete(context: Context, moments: Collection<LocalMoment>): Int =
        withContext(Dispatchers.IO) {
            var n = 0
            moments.forEach { m ->
                val gone = !m.file.exists() || m.file.delete()
                deleteMediaStoreByName(context, m.name)
                addTombstone(context, m.name)
                if (gone) n += 1
            }
            n
        }

    private fun deleteMediaStoreByName(context: Context, displayName: String) {
        runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val selection: String
            val args: Array<String>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection =
                    "${MediaStore.Images.Media.DISPLAY_NAME}=? AND " +
                        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                args = arrayOf(displayName, "%${Environment.DIRECTORY_PICTURES}/$LEGACY_FOLDER%")
            } else {
                selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
                args = arrayOf(displayName)
            }
            resolver.delete(collection, selection, args)
        }
    }

    private fun tombstoneFile(context: Context): File =
        File(momentsDir(context), TOMBSTONE_FILE)

    private fun loadTombstones(context: Context): Set<String> {
        val f = tombstoneFile(context)
        if (!f.exists()) return emptySet()
        return runCatching {
            f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun addTombstone(context: Context, name: String) {
        val current = loadTombstones(context).toMutableSet()
        if (!current.add(name)) return
        tombstoneFile(context).writeText(current.sorted().joinToString("\n"))
    }

    private fun clearTombstone(context: Context, name: String) {
        val current = loadTombstones(context).toMutableSet()
        if (!current.remove(name)) return
        val f = tombstoneFile(context)
        if (current.isEmpty()) f.delete()
        else f.writeText(current.sorted().joinToString("\n"))
    }

    fun shareUri(context: Context, moment: LocalMoment): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            moment.file
        )
    }
}
