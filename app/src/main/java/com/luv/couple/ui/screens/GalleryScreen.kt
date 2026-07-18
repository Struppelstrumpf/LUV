package com.luv.couple.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luv.couple.data.LocalMoment
import com.luv.couple.data.LocalMoments
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PublishedBlue = Color(0xFF2563EB)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var moments by remember { mutableStateOf<List<LocalMoment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<LocalMoment?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf<List<LocalMoment>?>(null) }
    var confirmPublish by remember { mutableStateOf<LocalMoment?>(null) }
    var publishedInfo by remember { mutableStateOf<LocalMoment?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            // Veröffentlichte Bilder vom Konto auf dieses Gerät holen (Screenshots bleiben lokal)
            runCatching { LocalMoments.syncPublishedFromAccount(context) }
            moments = runCatching { LocalMoments.list(context) }.getOrDefault(emptyList())
            selectedIds = selectedIds.intersect(moments.map { it.id }.toSet())
            if (selectedIds.isEmpty()) selecting = false
            // Abgelaufene Veröffentlichungen lokal bereinigen
            moments.filter { !it.publicId.isNullOrBlank() }.forEach { m ->
                val still = runCatching {
                    LuvApiClient.publicCanvasStatus(m.publicId!!)
                }.getOrDefault(true)
                if (!still) {
                    LocalMoments.setPublicId(context, m.id, null)
                }
            }
            moments = runCatching { LocalMoments.list(context) }.getOrDefault(emptyList())
            preview = preview?.let { p -> moments.firstOrNull { it.id == p.id } }
            publishedInfo = publishedInfo?.let { p -> moments.firstOrNull { it.id == p.id } }
            loading = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshKey) { reload() }

    LaunchedEffect(Unit) {
        runCatching { LuvApiClient.pingAchievement("gallery_opens") }
    }

    fun toggleSelect(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selecting = false
    }

    MenuBackdrop(includeNavigationBars = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Galerie",
                    fontFamily = DisplayFont,
                    fontSize = 34.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (onClose != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.1f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = TextMuted, fontSize = 16.sp)
                    }
                }
            }
            if (selecting && selectedIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${selectedIds.size} ausgewählt",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            when {
                loading && moments.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Lade...", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                    }
                }
                moments.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(BgSoft)
                            .padding(22.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Noch keine Bilder",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 20.sp
                            )
                            Text(
                                "Auf der Leinwand speichern — dann erscheinen sie hier.",
                                color = TextMuted,
                                fontFamily = BodyFont,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(moments, key = { it.id }) { moment ->
                            val checked = moment.id in selectedIds
                            GalleryThumb(
                                moment = moment,
                                selecting = selecting,
                                selected = checked,
                                onClick = {
                                    if (selecting) {
                                        toggleSelect(moment.id)
                                    } else {
                                        preview = moment
                                    }
                                },
                                onLongClick = {
                                    selecting = true
                                    selectedIds = selectedIds + moment.id
                                },
                                onPublishedBadge = {
                                    publishedInfo = moment
                                }
                            )
                        }
                    }
                }
            }

            if (selecting && selectedIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryButton(
                    "Löschen (${selectedIds.size})",
                    AccentRose,
                    {
                        confirmDelete = moments.filter { it.id in selectedIds }
                    }
                )
            }
        }
    }

    val open = preview
    if (open != null) {
        GalleryDetailDialog(
            moment = open,
            busy = busy,
            onDismiss = { if (!busy) preview = null },
            onShare = { shareMoment(context, open) },
            onPublish = { confirmPublish = open },
            onPublishedBadge = { publishedInfo = open }
        )
    }

    val publishTarget = confirmPublish
    if (publishTarget != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmPublish = null },
            containerColor = BgSoft,
            title = {
                Text("Veröffentlichen?", color = TextPrimary, fontFamily = DisplayFont, fontSize = 20.sp)
            },
            text = {
                Text(
                    "Das Bild kann zufällig beim App-Start anderen LUV-Nutzer:innen gezeigt werden " +
                        "(mit Namen der Beteiligten), etwa 30 Tage lang.\n\n" +
                        "Mit „Veröffentlichen“ bestätigst du, dass du die AGB akzeptierst und " +
                        "berechtigt bist, dieses Bild öffentlich zu teilen.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        scope.launch {
                            runCatching {
                                val bmp = LocalMoments.loadFull(publishTarget)
                                    ?: error("Bild konnte nicht geladen werden")
                                val b64 = withContext(Dispatchers.IO) {
                                    val out = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.PNG, 92, out)
                                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                }
                                val nick = AccountSession.account.value?.nickname?.trim().orEmpty()
                                val result = LuvApiClient.publishPublicCanvas(
                                    imageBase64 = b64,
                                    memberNicknames = listOfNotNull(nick.takeIf { it.isNotBlank() }),
                                    lobbyName = "Galerie"
                                )
                                LocalMoments.setPublicId(context, publishTarget.id, result.id)
                                result
                            }.onSuccess {
                                confirmPublish = null
                                Toast.makeText(context, "Veröffentlicht", Toast.LENGTH_SHORT).show()
                                reload()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Veröffentlichen fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            busy = false
                        }
                    }
                ) {
                    Text(
                        if (busy) "…" else "Veröffentlichen",
                        color = AccentRose,
                        fontFamily = DisplayFont
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmPublish = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    val pub = publishedInfo
    if (pub != null && !pub.publicId.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { if (!busy) publishedInfo = null },
            containerColor = BgSoft,
            title = {
                Text("Veröffentlicht", color = TextPrimary, fontFamily = DisplayFont, fontSize = 20.sp)
            },
            text = {
                Text(
                    "Dieses Bild ist öffentlich und kann beim App-Start anderen gezeigt werden " +
                        "(ca. 30 Tage). Du kannst die Veröffentlichung jederzeit zurücknehmen.",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        scope.launch {
                            runCatching {
                                LuvApiClient.unpublishPublicCanvas(pub.publicId!!)
                                LocalMoments.setPublicId(context, pub.id, null)
                            }.onSuccess {
                                publishedInfo = null
                                Toast.makeText(context, "Zurückgenommen", Toast.LENGTH_SHORT).show()
                                reload()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Zurücknehmen fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            busy = false
                        }
                    }
                ) {
                    Text(
                        if (busy) "…" else "Zurücknehmen",
                        color = AccentRose,
                        fontFamily = DisplayFont
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { publishedInfo = null }) {
                    Text("Schließen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    val pending = confirmDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = BgSoft,
            title = {
                Text(
                    if (pending.size == 1) "Bild löschen?" else "${pending.size} Bilder löschen?",
                    color = TextPrimary,
                    fontFamily = DisplayFont
                )
            },
            text = {
                Text(
                    "Das lässt sich nicht rückgängig machen.",
                    color = TextMuted,
                    fontFamily = BodyFont
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = pending
                        confirmDelete = null
                        scope.launch {
                            val removed = LocalMoments.delete(context, toDelete)
                            if (preview != null && toDelete.any { it.id == preview?.id }) {
                                preview = null
                            }
                            selectedIds = emptySet()
                            selecting = false
                            reload()
                            Toast.makeText(
                                context,
                                when {
                                    removed <= 0 -> "Löschen fehlgeschlagen"
                                    removed == 1 -> "Gelöscht"
                                    else -> "$removed gelöscht"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("Löschen", color = AccentRose, fontFamily = DisplayFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryThumb(
    moment: LocalMoment,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPublishedBadge: () -> Unit
) {
    val context = LocalContext.current
    var thumb by remember(moment.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(moment.id) {
        thumb = LocalMoments.loadThumbnail(context, moment)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(18.dp))
            .background(BgSoft)
            .then(
                if (selected) {
                    Modifier.border(2.dp, AccentRose, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        val bmp = thumb
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = moment.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "...",
                color = TextMuted,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            text = formatMomentDate(moment.dateAddedSec),
            color = TextPrimary,
            fontFamily = BodyFont,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        if (!moment.publicId.isNullOrBlank() && !selecting) {
            PublishedBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clickable(onClick = onPublishedBadge)
            )
        }
        if (selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (selected) AccentRose else Color.Black.copy(alpha = 0.45f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("✓", color = Color.White, fontSize = 14.sp, fontFamily = DisplayFont)
                }
            }
        }
    }
}

@Composable
private fun PublishedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(PublishedBlue)
            .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("✓", color = Color.White, fontSize = 14.sp, fontFamily = DisplayFont)
    }
}

@Composable
private fun GalleryDetailDialog(
    moment: LocalMoment,
    busy: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onPublish: () -> Unit,
    onPublishedBadge: () -> Unit
) {
    var full by remember(moment.id) { mutableStateOf<Bitmap?>(null) }
    val published = !moment.publicId.isNullOrBlank()

    LaunchedEffect(moment.id) {
        full = LocalMoments.loadFull(moment)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B0F14))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Moment", color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
                    Text(
                        formatMomentDate(moment.dateAddedSec),
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
                if (published) {
                    PublishedBadge(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clickable(onClick = onPublishedBadge)
                    )
                }
                Text(
                    text = "Schließen",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !busy, onClick = onDismiss)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton("Teilen", AccentRose, onShare)
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (published) {
                        PrimaryButton("Veröffentlicht", PublishedBlue, onPublishedBadge)
                    } else {
                        PrimaryButton("Veröffentlichen", AccentRose, onPublish)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(BgSoft),
                contentAlignment = Alignment.Center
            ) {
                val bmp = full
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = moment.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Lade...", color = TextMuted, fontFamily = BodyFont)
                }
            }
        }
    }
}

private fun formatMomentDate(epochSec: Long): String {
    if (epochSec <= 0L) return ""
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        .format(Date(epochSec * 1000L))
}

private fun shareMoment(context: android.content.Context, moment: LocalMoment) {
    val uri = LocalMoments.shareUri(context, moment)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Ein Moment aus LUV")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Moment teilen").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }.onFailure {
        Toast.makeText(context, "Teilen gerade nicht möglich", Toast.LENGTH_SHORT).show()
    }
}
