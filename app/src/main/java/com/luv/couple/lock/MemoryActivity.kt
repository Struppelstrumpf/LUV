package com.luv.couple.lock

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.luv.couple.LuvApp
import com.luv.couple.data.LocalMoments
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Vollbild-Erinnerung: Abbild einer stillen Leinwand (24h auf dem Server).
 * Schließen → Lobby-Leinwand · Screenshot · WhatsApp teilen.
 */
class MemoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lobbyCode = intent.getStringExtra(EXTRA_LOBBY_CODE).orEmpty()
        val lobbyName = intent.getStringExtra(EXTRA_LOBBY_NAME).orEmpty().ifBlank { "Lobby" }
        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty()
        setContent {
            LuvTheme {
                MemoryScreen(
                    lobbyName = lobbyName,
                    imageUrl = imageUrl,
                    onClose = { openLobby(lobbyCode) },
                    onSaved = {
                        Toast.makeText(this, "In der Galerie gespeichert", Toast.LENGTH_SHORT).show()
                    },
                    onShareFailed = {
                        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun openLobby(code: String) {
        if (code.isBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val lobbyId = withContext(Dispatchers.IO) {
                val snap = LuvApp.instance.prefs.snapshot()
                snap.lobbies.firstOrNull {
                    it.code.equals(code, ignoreCase = true) || it.id.equals(code, ignoreCase = true)
                }?.id ?: snap.activeLobbyId
            }
            if (!lobbyId.isNullOrBlank()) {
                startActivity(
                    Intent(this@MemoryActivity, LockDrawActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobbyId)
                    }
                )
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_LOBBY_CODE = "lobby_code"
        const val EXTRA_LOBBY_NAME = "lobby_name"
        const val EXTRA_IMAGE_URL = "image_url"
    }
}

@Composable
private fun MemoryScreen(
    lobbyName: String,
    imageUrl: String,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onShareFailed: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(imageUrl) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val url = CanvasMemoryKeeper.absoluteImageUrl(imageUrl)
                val client = OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
        bitmap = loaded
        loading = false
        // Einmal in die Galerie — unabhängig vom Konto
        if (loaded != null) {
            runCatching { LocalMoments.save(context, loaded, "LUV_memory_") }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF12151C), Color(0xFF1A1520), Color(0xFF0E1116))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Erinnerung", fontFamily = DisplayFont, fontSize = 28.sp, color = TextPrimary)
                Text(lobbyName, fontFamily = BodyFont, fontSize = 15.sp, color = TextMuted)
                Text(
                    "Eure Leinwand, still geworden — nur für 24 Stunden hier.",
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    color = TextMuted.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgDeep),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                when {
                    loading -> Text("Lädt…", color = TextMuted, fontFamily = BodyFont)
                    bmp == null -> Text(
                        text = "Dieses Abbild ist nicht mehr verfügbar.",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                    else -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Leinwand-Erinnerung",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MemoryActionButton("In Lobby öffnen", AccentRose, bordered = false, onClick = onClose)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        MemoryActionButton("Screenshot", BgSoft, bordered = true) {
                            val bmp = bitmap
                            if (bmp == null) {
                                onShareFailed("Noch kein Bild")
                                return@MemoryActionButton
                            }
                            scope.launch {
                                val ok = LocalMoments.save(context, bmp, "LUV_memory_").isSuccess
                                if (ok) onSaved() else onShareFailed("Speichern fehlgeschlagen")
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        MemoryActionButton("WhatsApp", BgSoft, bordered = true) {
                            val bmp = bitmap
                            if (bmp == null) {
                                onShareFailed("Noch kein Bild")
                                return@MemoryActionButton
                            }
                            scope.launch {
                                // Immer auch in die Galerie — teilen darf nichts „verlieren“
                                runCatching {
                                    LocalMoments.save(context, bmp, "LUV_memory_")
                                }
                                val err = withContext(Dispatchers.IO) {
                                    shareWhatsApp(context, bmp, lobbyName)
                                }
                                if (err != null) onShareFailed(err)
                            }
                        }
                    }
                }
                MemoryActionButton("Schließen", BgDeep, bordered = true, onClick = onClose)
            }
        }
    }
}

@Composable
private fun MemoryActionButton(
    label: String,
    color: Color,
    bordered: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .then(
                if (bordered) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = DisplayFont,
            fontSize = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun shareWhatsApp(
    context: android.content.Context,
    bitmap: Bitmap,
    lobbyName: String
): String? {
    return runCatching {
        val dir = File(context.cacheDir, "memories").apply { mkdirs() }
        val file = File(dir, "share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 92, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Unsere Leinwand aus „$lobbyName“ — LUV")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(send)
        } catch (_: Exception) {
            send.setPackage(null)
            context.startActivity(Intent.createChooser(send, "Teilen"))
        }
        null
    }.exceptionOrNull()?.message
}
