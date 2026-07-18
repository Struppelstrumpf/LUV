package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.LuvApp
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.AccountSession
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PendingShop
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.profile.ProfileFont
import com.luv.couple.profile.ProfileLayoutEl
import com.luv.couple.profile.ProfileMood
import com.luv.couple.profile.ProfileState
import com.luv.couple.profile.ProfileTheme
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import com.luv.couple.ui.theme.BgSoft
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.DisplayFont
import com.luv.couple.ui.theme.FemalePurple
import com.luv.couple.ui.theme.MaleBlue
import com.luv.couple.ui.theme.TextMuted
import com.luv.couple.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ProfileCanvasScreen(
    nickname: String,
    colorIndex: Int,
    editable: Boolean,
    userId: String? = null,
    onClose: () -> Unit,
    onEditNickname: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onOpenShop: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    val account by AccountSession.account.collectAsStateWithLifecycle()
    val coins = account?.coins ?: 0

    var state by remember {
        mutableStateOf(ProfileState(layout = ProfileCatalog.defaultLayout(nickname)).normalized(nickname))
    }
    var savedSnapshot by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showChest by remember { mutableStateOf(false) }
    var editElId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loadedNick by remember { mutableStateOf(nickname) }
    var ownedStickers by remember { mutableStateOf<Set<String>>(ProfileCatalog.FREE_STICKERS.toSet()) }
    var confirmDiscard by remember { mutableStateOf(false) }

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
            savedSnapshot = state.snapshotKey()
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
            savedSnapshot = state.snapshotKey()
        }
    }

    fun updateLayout(next: List<ProfileLayoutEl>) {
        state = state.copy(layout = next).normalized(loadedNick)
    }

    fun updateEl(updated: ProfileLayoutEl) {
        updateLayout(state.layout.map { if (it.id == updated.id) updated else it })
    }

    fun setMood(mood: ProfileMood) {
        if (!editable) return
        state = state.copy(statusEmoji = mood.emoji).normalized(loadedNick)
        selectedId = "el-status"
        showChest = false
    }

    fun setTheme(theme: ProfileTheme) {
        if (!editable) return
        state = state.copy(themeId = theme.id)
        showChest = false
    }

    fun setCompanion(emoji: String) {
        if (!editable) return
        state = state.copy(companionEmoji = emoji).normalized(loadedNick)
        selectedId = "el-pet"
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

    fun placeText(color: String) {
        if (!editable) return
        val el = ProfileCatalog.newText(color, state.layout)
        updateLayout(state.layout + el)
        selectedId = el.id
        editElId = el.id
        showChest = false
    }

    fun tryClose() {
        if (editable && state.snapshotKey() != savedSnapshot) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }

    fun save() {
        if (!editable || saving) return
        saving = true
        scope.launch {
            val clean = state.normalized(loadedNick).let { s ->
                s.copy(
                    layout = s.layout.map {
                        it.copy(rotation = ProfileCatalog.repairRotation(it.rotation))
                    }
                )
            }
            withContext(Dispatchers.IO) {
                prefs.setProfileCanvasJson(ProfileCatalog.encode(clean))
            }
            val ok = runCatching { LuvApiClient.saveMyProfileCanvas(clean) }.getOrDefault(false)
            saving = false
            savedSnapshot = clean.snapshotKey()
            Toast.makeText(
                context,
                if (ok) "Profil gespeichert" else "Lokal gespeichert",
                Toast.LENGTH_SHORT
            ).show()
            onClose()
        }
    }

    val editEl = state.layout.firstOrNull { it.id == editElId }

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
                .verticalScroll(rememberScrollState())
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
                        .clickable(onClick = { tryClose() }),
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
                coins = coins,
                editable = editable,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onLayoutChange = { updateLayout(it) },
                onOpenChest = { if (editable) showChest = true },
                onEdit = { if (editable) editElId = it }
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
                Text("Bio", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = state.bio,
                    onValueChange = {
                        state = state.copy(bio = it.take(ProfileCatalog.MAX_BIO)).normalized(loadedNick)
                    },
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
                                "Erzähl von euch, euren Momenten …",
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
                            Brush.horizontalGradient(listOf(AccentRose, FemalePurple.copy(0.85f)))
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
                Spacer(modifier = Modifier.height(12.dp))
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
                currentMood = state.statusEmoji,
                currentCompanion = state.companionEmoji,
                onTheme = { setTheme(it) },
                onMood = { setMood(it) },
                onSticker = { placeSticker(it) },
                onTextColor = { placeText(it) },
                onCompanion = { setCompanion(it) },
                onOpenShop = {
                    showChest = false
                    if (onOpenShop != null) onOpenShop()
                    else {
                        PendingShop.offer()
                        Toast.makeText(context, "Öffne das Lädchen im Menü", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                },
                onDismiss = { showChest = false }
            )
        }

        if (editEl != null && editable) {
            ProfileEditSheet(
                el = editEl,
                nickname = loadedNick,
                onChange = { next ->
                    if (next.type == ProfileElType.Bio) {
                        state = state.copy(bio = next.text.orEmpty()).normalized(loadedNick)
                    } else {
                        updateEl(next)
                    }
                },
                onDismiss = { editElId = null }
            )
        }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = {
                    Text("Änderungen verwerfen?", color = TextPrimary, fontFamily = DisplayFont)
                },
                text = {
                    Text(
                        "Dein Profil wurde noch nicht gespeichert.",
                        color = TextMuted,
                        fontFamily = BodyFont
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        onClose()
                    }) {
                        Text("Verwerfen", color = AccentRose)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) {
                        Text("Weiter bearbeiten", color = TextMuted)
                    }
                },
                containerColor = BgSoft
            )
        }
    }
}

@Composable
private fun ProfileCanvasBoard(
    state: ProfileState,
    nickname: String,
    colorIndex: Int,
    coins: Int,
    editable: Boolean,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onLayoutChange: (List<ProfileLayoutEl>) -> Unit,
    onOpenChest: () -> Unit,
    onEdit: (String) -> Unit
) {
    val theme = ProfileCatalog.theme(state.themeId)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.12f)
            .shadow(16.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(22.dp))
    ) {
        val boardW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boardH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

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

        ThemeFxOverlay(theme.effect)

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
                coins = coins,
                selected = selected && editable,
                boardW = boardW,
                boardH = boardH,
                editable = editable,
                onSelect = {
                    if (!editable) return@ProfileElementView
                    onSelect(el.id)
                    if (el.type == ProfileElType.Sticker || el.type == ProfileElType.Text) {
                        val maxZ = state.layout.maxOfOrNull { it.z } ?: 20
                        onLayoutChange(
                            state.layout.map {
                                if (it.id == el.id) it.copy(z = (maxZ + 1).coerceAtMost(ProfileCatalog.MAX_DECOR_Z))
                                else it
                            }
                        )
                    }
                },
                onChange = { updated ->
                    onLayoutChange(state.layout.map { if (it.id == updated.id) updated else it })
                },
                onRemove = {
                    when {
                        el.type == ProfileElType.Bio -> Unit
                        el.type == ProfileElType.Sticker || el.type == ProfileElType.Text -> {
                            onLayoutChange(state.layout.filterNot { it.id == el.id })
                            onSelect(null)
                        }
                        el.type in ProfileCatalog.CORE_HIDE_TYPES -> {
                            onLayoutChange(
                                state.layout.map {
                                    if (it.id == el.id) it.copy(visible = false) else it
                                }
                            )
                            onSelect(null)
                        }
                    }
                },
                onEdit = { onEdit(el.id) }
            )
        }

        // Versteckte Core-Elemente wiederherstellen
        if (editable) {
            val hidden = state.layout.filter { !it.visible && it.type in ProfileCatalog.CORE_HIDE_TYPES }
            if (hidden.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .zIndex(180f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hidden.forEach { el ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(0.45f))
                                .clickable {
                                    onLayoutChange(
                                        state.layout.map {
                                            if (it.id == el.id) it.copy(visible = true) else it
                                        }
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                when (el.type) {
                                    ProfileElType.Avatar -> "Avatar +"
                                    ProfileElType.Name -> "Name +"
                                    ProfileElType.Status -> "Stimmung +"
                                    ProfileElType.Glass -> "Münzglas +"
                                    ProfileElType.Pet -> "Begleiter +"
                                    else -> "+"
                                },
                                color = Color.White,
                                fontFamily = BodyFont,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(44.dp)
                    .zIndex(200f)
                    .shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF3E2A22), Color(0xFF2A1C16)))
                    )
                    .border(1.dp, Color(0xFFD4A574).copy(0.55f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenChest),
                contentAlignment = Alignment.Center
            ) {
                Text("🧰", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun ThemeFxOverlay(effect: String) {
    if (effect == "none") return
    val t = rememberInfiniteTransition(label = "fx")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val seeds = remember(effect) { List(28) { Random(it * 17 + effect.hashCode()).nextFloat() } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (effect) {
            "rain" -> seeds.forEachIndexed { i, s ->
                val x = size.width * s
                val y = ((phase + s) % 1f) * size.height
                drawLine(
                    Color.White.copy(0.22f),
                    Offset(x, y),
                    Offset(x - 4f, y + 18f),
                    strokeWidth = 1.6f
                )
            }
            "snow" -> seeds.forEach { s ->
                val x = size.width * ((s + phase * 0.15f) % 1f)
                val y = size.height * ((s * 0.7f + phase) % 1f)
                drawCircle(Color.White.copy(0.55f), radius = 2.2f + s * 2.5f, center = Offset(x, y))
            }
            "stars" -> seeds.take(18).forEachIndexed { i, s ->
                val twinkle = 0.35f + 0.65f * absSin(phase * 6.28f + i)
                drawCircle(
                    Color.White.copy(twinkle),
                    radius = 1.4f + s * 2f,
                    center = Offset(size.width * s, size.height * ((s * 1.7f) % 0.55f))
                )
            }
        }
    }
}

private fun absSin(v: Float): Float = abs(sin(v.toDouble())).toFloat()

@Composable
private fun ProfileElementView(
    el: ProfileLayoutEl,
    nickname: String,
    colorIndex: Int,
    coins: Int,
    selected: Boolean,
    boardW: Float,
    boardH: Float,
    editable: Boolean,
    onSelect: () -> Unit,
    onChange: (ProfileLayoutEl) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    val density = LocalDensity.current
    var dragEl by remember(el.id) { mutableStateOf(el) }
    LaunchedEffect(el) { dragEl = el }
    // Feste Basisgröße — Skalierung läuft über graphicsLayer, damit Emoji/Text mitwachsen
    val baseSize = with(density) {
        when (el.type) {
            ProfileElType.Bio -> 120.dp.toPx()
            ProfileElType.Name -> 110.dp.toPx()
            ProfileElType.Glass -> 72.dp.toPx()
            ProfileElType.Pet -> 64.dp.toPx()
            ProfileElType.Avatar -> 58.dp.toPx()
            else -> 52.dp.toPx()
        }
    }
    val dash = with(density) { 8.dp.toPx() }
    val gap = with(density) { 5.dp.toPx() }
    val strokeW = with(density) { 2.dp.toPx() }
    val canEdit = el.type in ProfileCatalog.EDITABLE_TYPES
    val canRemove = el.type != ProfileElType.Bio
    val invScale = 1f / dragEl.scale.coerceIn(0.35f, 2.5f)

    Box(
        modifier = Modifier
            .zIndex(el.z.toFloat() + if (selected) 50f else 0f)
            .graphicsLayer {
                val s = dragEl.scale
                translationX = boardW * (dragEl.x / 100f) - baseSize / 2f
                translationY = boardH * (dragEl.y / 100f) - baseSize / 2f
                rotationZ = dragEl.rotation
                scaleX = s * (if (dragEl.flipX) -1f else 1f)
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .size(with(density) { baseSize.toDp() })
            .then(
                if (editable) {
                    Modifier.pointerInput(el.id, boardW, boardH) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, drag ->
                                change.consume()
                                val nx = dragEl.x + drag.x / boardW * 100f
                                val ny = dragEl.y + drag.y / boardH * 100f
                                val (cx, cy) = ProfileCatalog.clampPos(dragEl, nx, ny)
                                val next = dragEl.copy(x = cx, y = cy)
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
            ElementContent(dragEl, nickname, colorIndex, coins)
        }

        if (selected && editable) {
            // Handles gegen Element-Scale gegenrechnen, damit sie bedienbar bleiben
            val handleMod = Modifier.graphicsLayer {
                scaleX = invScale * (if (dragEl.flipX) -1f else 1f)
                scaleY = invScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .then(handleMod)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(0.15f), CircleShape)
                    .pointerInput(el.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val next = dragEl.copy(
                                rotation = ProfileCatalog.repairRotation(
                                    dragEl.rotation + (drag.x + drag.y) * 0.4f
                                )
                            )
                            dragEl = next
                            onChange(next)
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("↻", fontSize = 14.sp, color = Color.Black) }

            if (canRemove) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .then(handleMod)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 13.sp) }
            }

            if (canEdit) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .then(handleMod)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaleBlue)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) { Text("✎", color = Color.White, fontSize = 13.sp) }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .then(handleMod)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(FemalePurple)
                        .clickable {
                            val next = dragEl.copy(flipX = !dragEl.flipX)
                            dragEl = next
                            onChange(next)
                        },
                    contentAlignment = Alignment.Center
                ) { Text("⇄", color = Color.White, fontSize = 13.sp) }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(handleMod)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(0.15f), CircleShape)
                    .pointerInput(el.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            // Gegen parent-scale: Drag-Delta optisch stabil halten
                            val next = dragEl.copy(
                                scale = (dragEl.scale + drag.x * 0.008f * invScale)
                                    .coerceIn(0.35f, 2.5f)
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
private fun ElementContent(
    el: ProfileLayoutEl,
    nickname: String,
    colorIndex: Int,
    coins: Int
) {
    val font = when (el.fontFamily) {
        ProfileFont.Playful -> DisplayFont
        ProfileFont.Classic -> FontFamily.Serif
        else -> BodyFont
    }
    val color = ProfileCatalog.parseColor(el.color)
    val sizeSp = (el.fontSize ?: when (el.type) {
        ProfileElType.Name -> 18f
        ProfileElType.Bio -> 12f
        ProfileElType.Glass -> 11f
        else -> 14f
    }).sp

    when (el.type) {
        ProfileElType.Avatar -> {
            val fill = Color(PeerPalette.strokeColor(colorIndex))
            Box(
                modifier = Modifier
                    .fillMaxSize(0.88f)
                    .clip(CircleShape)
                    .background(fill)
                    .border(2.dp, Color.White.copy(0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val face = el.emoji?.takeIf { it.isNotBlank() }
                if (face != null) {
                    Text(face, fontSize = 26.sp)
                } else {
                    Text(
                        nickname.take(1).uppercase(),
                        color = Color(0xFF1A1F2E),
                        fontFamily = DisplayFont,
                        fontSize = 22.sp
                    )
                }
            }
        }
        ProfileElType.Name -> Text(
            el.text ?: nickname,
            color = color,
            fontFamily = font,
            fontSize = sizeSp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ProfileElType.Status -> Text(el.emoji ?: el.text ?: "😊", fontSize = 30.sp)
        ProfileElType.Bio -> Text(
            el.text?.takeIf { it.isNotBlank() } ?: "…",
            color = color.copy(alpha = if (el.text.isNullOrBlank()) 0.55f else 1f),
            fontFamily = font,
            fontSize = sizeSp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        ProfileElType.Pet -> {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.18f))
                    .border(1.dp, Color.White.copy(0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(el.emoji ?: "💕", fontSize = 32.sp)
            }
        }
        ProfileElType.Glass -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏺", fontSize = 28.sp)
                Text(
                    "$coins",
                    color = color,
                    fontFamily = font,
                    fontSize = sizeSp,
                    maxLines = 1
                )
                Text("Coins", color = color.copy(0.85f), fontFamily = BodyFont, fontSize = 9.sp)
            }
        }
        ProfileElType.Sticker -> Text(el.emoji ?: "✨", fontSize = 34.sp)
        ProfileElType.Text -> Text(
            el.text ?: "",
            color = color,
            fontFamily = font,
            fontSize = sizeSp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileChestDialog(
    ownedStickers: List<String>,
    currentThemeId: String,
    currentMood: String,
    currentCompanion: String,
    onTheme: (ProfileTheme) -> Unit,
    onMood: (ProfileMood) -> Unit,
    onSticker: (String) -> Unit,
    onTextColor: (String) -> Unit,
    onCompanion: (String) -> Unit,
    onOpenShop: () -> Unit,
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
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1C2433), BgDeep))
                )
                .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(28.dp))
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧰", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("TRUHE", color = TextMuted, fontFamily = BodyFont, fontSize = 11.sp)
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
                ShopLinkChip("🛒 Lädchen", onOpenShop, Modifier.weight(1f))
                ShopLinkChip("✨ Mehr Sticker", onOpenShop, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Hintergrund", "Stimmung", "Sticker", "Text", "Begleiter").forEachIndexed { i, label ->
                    val on = tab == i
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) AccentRose.copy(0.28f) else Color.White.copy(0.06f))
                            .clickable { tab = i }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
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
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProfileCatalog.THEMES, key = { it.id }) { theme ->
                        val on = theme.id == currentThemeId
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) AccentRose.copy(0.2f) else BgSoft)
                                .border(
                                    1.dp,
                                    if (on) AccentRose.copy(0.65f) else Color.White.copy(0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onTheme(theme) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(theme.emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(theme.label, color = TextPrimary, fontFamily = BodyFont, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                1 -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProfileCatalog.MOODS, key = { it.id }) { mood ->
                        val on = mood.emoji == currentMood
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) AccentRose.copy(0.2f) else BgSoft)
                                .border(
                                    1.dp,
                                    if (on) AccentRose.copy(0.65f) else Color.White.copy(0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onMood(mood) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(mood.emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(mood.label, color = TextPrimary, fontFamily = BodyFont, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                2 -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ownedStickers, key = { it }) { emoji ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BgSoft)
                                .clickable { onSticker(emoji) },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 28.sp) }
                    }
                }
                3 -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProfileCatalog.TEXT_COLORS, key = { it }) { hex ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ProfileCatalog.parseColor(hex))
                                .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(14.dp))
                                .clickable { onTextColor(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aa", color = if (hex == "#FFFFFF" || hex == "#FFD54F") Color.Black else Color.White, fontSize = 14.sp)
                        }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(72.dp),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProfileCatalog.COMPANIONS, key = { it }) { emoji ->
                        val on = emoji == currentCompanion
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) AccentRose.copy(0.22f) else BgSoft)
                                .border(
                                    1.dp,
                                    if (on) AccentRose.copy(0.65f) else Color.White.copy(0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onCompanion(emoji) },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 30.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopLinkChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AccentRose.copy(0.16f))
            .border(1.dp, AccentRose.copy(0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextPrimary, fontFamily = BodyFont, fontSize = 13.sp)
    }
}

@Composable
private fun ProfileEditSheet(
    el: ProfileLayoutEl,
    nickname: String,
    onChange: (ProfileLayoutEl) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(el.id) { mutableStateOf(el) }
    LaunchedEffect(el) { draft = el }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2433), BgDeep)))
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Text(
                when (el.type) {
                    ProfileElType.Avatar -> "Avatar gestalten"
                    ProfileElType.Name -> "Name gestalten"
                    ProfileElType.Glass -> "Münzglas gestalten"
                    ProfileElType.Bio -> "Bio auf der Leinwand"
                    ProfileElType.Text -> "Text gestalten"
                    else -> "Bearbeiten"
                },
                color = TextPrimary,
                fontFamily = DisplayFont,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (el.type == ProfileElType.Avatar) {
                Text("Gesicht (optional)", color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileCatalog.AVATAR_FACES.forEach { face ->
                        val on = (draft.emoji ?: "") == face
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (on) AccentRose.copy(0.3f) else BgSoft)
                                .clickable {
                                    draft = draft.copy(emoji = face.ifBlank { null })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (face.isBlank()) nickname.take(1).uppercase() else face, fontSize = 18.sp, color = TextPrimary)
                        }
                    }
                }
            }

            if (el.type == ProfileElType.Text || el.type == ProfileElType.Bio) {
                BasicTextField(
                    value = draft.text.orEmpty(),
                    onValueChange = {
                        draft = draft.copy(text = it.take(if (el.type == ProfileElType.Bio) ProfileCatalog.MAX_BIO else 80))
                    },
                    textStyle = TextStyle(color = TextPrimary, fontFamily = BodyFont, fontSize = 15.sp),
                    cursorBrush = SolidColor(AccentRose),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (el.type == ProfileElType.Bio) 100.dp else 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgSoft)
                        .padding(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (el.type != ProfileElType.Avatar) {
                Text("Schrift", color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileFont.entries.forEach { f ->
                        val on = (draft.fontFamily ?: ProfileFont.Cozy) == f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) AccentRose.copy(0.25f) else BgSoft)
                                .clickable { draft = draft.copy(fontFamily = f) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(f.label, color = TextPrimary, fontFamily = BodyFont, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Größe", color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(11f, 14f, 18f, 22f, 28f).forEach { sz ->
                        val on = (draft.fontSize ?: 14f) == sz
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) AccentRose.copy(0.25f) else BgSoft)
                                .clickable { draft = draft.copy(fontSize = sz) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("${sz.toInt()}", color = TextPrimary, fontFamily = BodyFont, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Farbe", color = TextMuted, fontFamily = BodyFont, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileCatalog.TEXT_COLORS.forEach { hex ->
                        val on = draft.color == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ProfileCatalog.parseColor(hex))
                                .border(
                                    if (on) 2.dp else 1.dp,
                                    if (on) AccentRose else Color.White.copy(0.25f),
                                    CircleShape
                                )
                                .clickable { draft = draft.copy(color = hex) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text("Abbrechen", color = TextMuted, fontFamily = BodyFont) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(AccentRose)
                        .clickable {
                            onChange(draft)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) { Text("Übernehmen", color = Color.White, fontFamily = DisplayFont) }
            }
        }
    }
}
