package com.luv.couple.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.luv.couple.LuvApp
import com.luv.couple.net.LuvApiClient
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.profile.ProfileLayoutEl
import com.luv.couple.profile.ProfileState
import com.luv.couple.profile.ProfileTheme
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

@Composable
fun ProfileCanvasScreen(
    nickname: String,
    colorIndex: Int,
    editable: Boolean,
    userId: String? = null,
    onClose: () -> Unit,
    onEditNickname: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    var state by remember {
        mutableStateOf(ProfileState(layout = ProfileCatalog.defaultLayout(nickname)).normalized(nickname))
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showChest by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loadedNick by remember { mutableStateOf(nickname) }
    var ownedStickers by remember { mutableStateOf<Set<String>>(ProfileCatalog.FREE_STICKERS.toSet()) }

    LaunchedEffect(userId, editable) {
        if (editable || userId.isNullOrBlank()) {
            val local = withContext(Dispatchers.IO) {
                ProfileCatalog.decode(prefs.profileCanvasJson(), nickname)
            }
            state = local.normalized(nickname)
            loadedNick = nickname
            runCatching {
                val remote = LuvApiClient.fetchMyProfileCanvas()
                state = remote.second.normalized(remote.first)
                loadedNick = remote.first
                withContext(Dispatchers.IO) {
                    prefs.setProfileCanvasJson(ProfileCatalog.encode(state))
                }
            }
            val owned = withContext(Dispatchers.IO) {
                runCatching { prefs.ownedEmojis() }.getOrDefault(emptyMap())
            }
            ownedStickers = (ProfileCatalog.FREE_STICKERS + owned.keys).toSet()
        } else {
            val remote = LuvApiClient.fetchUserProfileCanvas(userId)
            if (remote != null) {
                loadedNick = remote.first
                state = remote.second.normalized(remote.first)
            } else {
                loadedNick = nickname
                state = ProfileState(layout = ProfileCatalog.defaultLayout(nickname)).normalized(nickname)
            }
        }
    }

    fun updateLayout(next: List<ProfileLayoutEl>) {
        state = state.copy(layout = next).normalized(loadedNick)
    }

    fun placeSticker(emoji: String) {
        if (!editable) return
        if (state.layout.count { it.type == ProfileElType.Sticker } >= ProfileCatalog.MAX_DECOR) {
            Toast.makeText(context, "Maximal ${ProfileCatalog.MAX_DECOR} Sticker", Toast.LENGTH_SHORT).show()
            return
        }
        val el = ProfileCatalog.newSticker(emoji, state.layout)
        updateLayout(state.layout + el)
        selectedId = el.id
        showChest = false
    }

    fun setTheme(theme: ProfileTheme) {
        if (!editable) return
        state = state.copy(themeId = theme.id)
        showChest = false
    }

    fun save() {
        if (!editable || saving) return
        saving = true
        scope.launch {
            val clean = state.normalized(loadedNick)
            withContext(Dispatchers.IO) {
                prefs.setProfileCanvasJson(ProfileCatalog.encode(clean))
            }
            val ok = runCatching { LuvApiClient.saveMyProfileCanvas(clean) }.getOrDefault(false)
            saving = false
            Toast.makeText(
                context,
                if (ok) "Profil gespeichert" else "Lokal gespeichert",
                Toast.LENGTH_SHORT
            ).show()
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (editable) "Profil gestalten" else loadedNick,
                        color = TextPrimary,
                        fontFamily = DisplayFont,
                        fontSize = 24.sp
                    )
                    if (editable && onEditNickname != null) {
                        Text(
                            "Name ändern",
                            color = AccentRose,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable(onClick = onEditNickname)
                        )
                    }
                }
                if (!editable && onReport != null) {
                    Text(
                        "Melden",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clickable(onClick = onReport)
                    )
                }
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
            Spacer(modifier = Modifier.height(12.dp))

            ProfileCanvasBoard(
                state = state,
                nickname = loadedNick,
                colorIndex = colorIndex,
                editable = editable,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onLayoutChange = { updateLayout(it) },
                onOpenChest = { if (editable) showChest = true }
            )

            Text(
                if (editable) "FÜR ALLE SICHTBAR" else "PROFIL",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp)
            )

            if (editable) {
                Text(
                    "Bio",
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = state.bio,
                    onValueChange = { state = state.copy(bio = it.take(280)) },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = BodyFont,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(AccentRose),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSoft)
                        .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    decorationBox = { inner ->
                        if (state.bio.isBlank()) {
                            Text(
                                "Erzähl kurz von euch …",
                                color = TextMuted.copy(0.7f),
                                fontFamily = BodyFont,
                                fontSize = 14.sp
                            )
                        }
                        inner()
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AccentRose, Color(0xFFFF8FA3))
                            )
                        )
                        .clickable(enabled = !saving, onClick = { save() }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (saving) "…" else "Profil speichern & veröffentlichen",
                        color = Color.White,
                        fontFamily = DisplayFont,
                        fontSize = 16.sp
                    )
                }
            } else if (state.bio.isNotBlank()) {
                Text(
                    state.bio,
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSoft)
                        .padding(14.dp)
                )
            }
        }

        if (showChest && editable) {
            ProfileChestDialog(
                ownedStickers = ownedStickers.toList().sorted(),
                currentThemeId = state.themeId,
                onTheme = { setTheme(it) },
                onSticker = { placeSticker(it) },
                onDismiss = { showChest = false }
            )
        }
    }
}

@Composable
private fun ProfileCanvasBoard(
    state: ProfileState,
    nickname: String,
    colorIndex: Int,
    editable: Boolean,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onLayoutChange: (List<ProfileLayoutEl>) -> Unit,
    onOpenChest: () -> Unit
) {
    val theme = ProfileCatalog.theme(state.themeId)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .shadow(16.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(22.dp))
    ) {
        val boardW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boardH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Hintergrund
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(theme.skyTop), Color(theme.skyBottom)),
                    startY = 0f,
                    endY = size.height * 0.55f
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(theme.groundTop), Color(theme.groundBottom))
                ),
                topLeft = Offset(0f, size.height * 0.48f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.52f)
            )
        }

        // Tap auf leere Fläche deselektiert (unter Elementen)
        if (editable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .pointerInput(Unit) {
                        detectTapGestures { onSelect(null) }
                    }
            )
        }

        val visible = state.layout.filter { it.visible }.sortedBy { it.z }
        visible.forEach { el ->
            val selected = el.id == selectedId
            ProfileElementView(
                el = el,
                nickname = nickname,
                colorIndex = colorIndex,
                selected = selected && editable,
                boardW = boardW,
                boardH = boardH,
                editable = editable,
                onSelect = {
                    if (!editable) return@ProfileElementView
                    onSelect(el.id)
                    // nach vorn holen
                    val maxZ = state.layout.maxOfOrNull { it.z } ?: 20
                    onLayoutChange(
                        state.layout.map {
                            if (it.id == el.id &&
                                (it.type == ProfileElType.Sticker || it.type == ProfileElType.Text)
                            ) it.copy(z = (maxZ + 1).coerceAtMost(85))
                            else it
                        }
                    )
                },
                onChange = { updated ->
                    onLayoutChange(state.layout.map { if (it.id == updated.id) updated else it })
                },
                onRemove = {
                    if (el.type == ProfileElType.Sticker || el.type == ProfileElType.Text) {
                        onLayoutChange(state.layout.filterNot { it.id == el.id })
                    } else {
                        onLayoutChange(
                            state.layout.map {
                                if (it.id == el.id) it.copy(visible = false) else it
                            }
                        )
                    }
                    onSelect(null)
                }
            )
        }

        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(42.dp)
                    .zIndex(200f)
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF5D4037))
                    .clickable(onClick = onOpenChest),
                contentAlignment = Alignment.Center
            ) {
                Text("🧰", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun ProfileElementView(
    el: ProfileLayoutEl,
    nickname: String,
    colorIndex: Int,
    selected: Boolean,
    boardW: Float,
    boardH: Float,
    editable: Boolean,
    onSelect: () -> Unit,
    onChange: (ProfileLayoutEl) -> Unit,
    onRemove: () -> Unit
) {
    val density = LocalDensity.current
    var dragEl by remember(el.id) { mutableStateOf(el) }
    LaunchedEffect(el) { dragEl = el }
    val baseSize = with(density) { 52.dp.toPx() } * dragEl.scale
    val dash = with(density) { 8.dp.toPx() }
    val gap = with(density) { 5.dp.toPx() }
    val strokeW = with(density) { 2.dp.toPx() }

    Box(
        modifier = Modifier
            .zIndex(el.z.toFloat() + if (selected) 50f else 0f)
            .graphicsLayer {
                translationX = boardW * (dragEl.x / 100f) - baseSize / 2f
                translationY = boardH * (dragEl.y / 100f) - baseSize / 2f
                rotationZ = dragEl.rotation
                scaleX = if (dragEl.flipX) -1f else 1f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .size(with(density) { baseSize.toDp() })
            .then(
                if (editable) {
                    Modifier.pointerInput(el.id, boardW, boardH) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, drag ->
                                change.consume()
                                val nx = (dragEl.x + drag.x / boardW * 100f).coerceIn(6f, 94f)
                                val ny = (dragEl.y + drag.y / boardH * 100f).coerceIn(8f, 92f)
                                val next = dragEl.copy(x = nx, y = ny)
                                dragEl = next
                                onChange(next)
                            }
                        )
                    }
                } else Modifier
            )
            .clickable(enabled = editable) { onSelect() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (selected) {
                        Modifier.drawBehind {
                            drawRoundRect(
                                color = Color(0xFFC6FF4A),
                                cornerRadius = CornerRadius(10.dp.toPx()),
                                style = Stroke(
                                    width = strokeW,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap))
                                )
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            when (el.type) {
                ProfileElType.Avatar -> {
                    val fill = Color(com.luv.couple.data.PeerPalette.strokeColor(colorIndex))
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.85f)
                            .clip(CircleShape)
                            .background(fill)
                            .border(2.dp, Color.White.copy(0.85f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            nickname.take(1).uppercase(),
                            color = Color(0xFF1A1F2E),
                            fontFamily = DisplayFont,
                            fontSize = 20.sp
                        )
                    }
                }
                ProfileElType.Name -> Text(
                    el.text ?: nickname,
                    color = Color.White,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp,
                    maxLines = 1
                )
                ProfileElType.Status -> Text(
                    el.emoji ?: el.text ?: "😊",
                    fontSize = 28.sp
                )
                ProfileElType.Sticker -> Text(
                    el.emoji ?: "✨",
                    fontSize = 34.sp
                )
                ProfileElType.Text -> Text(
                    el.text ?: "",
                    color = Color.White,
                    fontFamily = BodyFont,
                    fontSize = 14.sp
                )
            }
        }

        if (selected && editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(0.15f), CircleShape)
                    .pointerInput(el.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val next = dragEl.copy(rotation = dragEl.rotation + (drag.x + drag.y) * 0.35f)
                            dragEl = next
                            onChange(next)
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("↻", fontSize = 14.sp, color = Color.Black) }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 13.sp) }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7E57C2))
                    .clickable {
                        val next = dragEl.copy(flipX = !dragEl.flipX)
                        dragEl = next
                        onChange(next)
                    },
                contentAlignment = Alignment.Center
            ) { Text("⇄", color = Color.White, fontSize = 13.sp) }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(0.15f), CircleShape)
                    .pointerInput(el.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val next = dragEl.copy(
                                scale = (dragEl.scale + drag.x * 0.008f).coerceIn(0.35f, 2.5f)
                            )
                            dragEl = next
                            onChange(next)
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("⤡", fontSize = 13.sp, color = Color.Black) }
        }
    }
}

@Composable
private fun ProfileChestDialog(
    ownedStickers: List<String>,
    currentThemeId: String,
    onTheme: (ProfileTheme) -> Unit,
    onSticker: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF2A211C), Color(0xFF1A1411)))
                )
                .border(1.dp, Color(0xFF8D6E63).copy(0.5f), RoundedCornerShape(24.dp))
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧰", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("TRUHE", color = Color(0xFFD7CCC8), fontFamily = BodyFont, fontSize = 11.sp)
                    Text("Dein Inventar", color = TextPrimary, fontFamily = DisplayFont, fontSize = 22.sp)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.12f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = TextMuted, fontSize = 14.sp) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hintergrund", "Sticker").forEachIndexed { i, label ->
                    val on = tab == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) AccentRose.copy(0.28f) else Color.White.copy(0.06f))
                            .clickable { tab = i }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = TextPrimary,
                            fontFamily = if (on) DisplayFont else BodyFont,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            when (tab) {
                0 -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProfileCatalog.THEMES, key = { it.id }) { theme ->
                        val on = theme.id == currentThemeId
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) AccentRose.copy(0.2f) else Color.White.copy(0.06f))
                                .border(
                                    1.dp,
                                    if (on) AccentRose.copy(0.6f) else Color.White.copy(0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onTheme(theme) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(theme.emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                theme.label,
                                color = TextPrimary,
                                fontFamily = BodyFont,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ownedStickers, key = { it }) { emoji ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(0.07f))
                                .clickable { onSticker(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 28.sp)
                        }
                    }
                }
            }
        }
    }
}
