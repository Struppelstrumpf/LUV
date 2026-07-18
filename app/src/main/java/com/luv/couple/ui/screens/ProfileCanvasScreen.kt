package com.luv.couple.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
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
import com.luv.couple.net.PendingProfilePlace
import com.luv.couple.net.PendingShop
import com.luv.couple.net.ProfilePlaceAction
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.profile.ProfileThemeBackdrop
import com.luv.couple.profile.ProfileFont
import com.luv.couple.profile.ProfileLayoutEl
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import android.os.SystemClock

@Composable
fun ProfileCanvasScreen(
    nickname: String,
    colorIndex: Int,
    editable: Boolean,
    userId: String? = null,
    initialOpenChest: Boolean = false,
    initialChestTab: Int = 0,
    onInitialChestConsumed: () -> Unit = {},
    onClose: () -> Unit,
    onEditNickname: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onOpenMarketplace: ((chestTab: Int) -> Unit)? = null,
    onOpenItemShop: ((chestTab: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LuvApp.instance.prefs
    val account by AccountSession.account.collectAsStateWithLifecycle()
    val myCoins = account?.coins ?: 0

    var state by remember {
        mutableStateOf(ProfileState(layout = ProfileCatalog.defaultLayout(nickname)).normalized(nickname))
    }
    var savedSnapshot by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showChest by remember { mutableStateOf(false) }
    var chestTab by remember { mutableIntStateOf(initialChestTab) }
    var editElId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loadedNick by remember { mutableStateOf(nickname) }
    var ownedStickers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ownedThemes by remember {
        mutableStateOf(listOf(ProfileCatalog.DEFAULT_THEME_ID))
    }
    var ownedPets by remember { mutableStateOf(listOf(com.luv.couple.shop.ShopCatalog.DEFAULT_PET)) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var displayCoins by remember { mutableIntStateOf(myCoins) }
    var tipPopIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var friendStatus by remember { mutableStateOf("none") }
    var canPetKraul by remember { mutableStateOf(false) }
    var canTipGlass by remember { mutableStateOf(true) }
    var glassTipsRemaining by remember { mutableIntStateOf(10) }
    var tipBusy by remember { mutableStateOf(false) }
    var peerPetEmoji by remember { mutableStateOf("🐣") }
    var showPetKraul by remember { mutableStateOf(false) }
    var petKraulBusy by remember { mutableStateOf(false) }
    // Fremdprofil: kein Default-Flash — erst Loader, dann fertiges Layout
    var profileReady by remember(userId, editable) {
        mutableStateOf(editable)
    }

    LaunchedEffect(initialOpenChest) {
        if (initialOpenChest && editable) {
            chestTab = initialChestTab
            showChest = true
            onInitialChestConsumed()
        }
    }

    LaunchedEffect(userId, editable, nickname) {
        if (!editable) profileReady = false
        val started = SystemClock.elapsedRealtime()
        if (editable) {
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
            withContext(Dispatchers.IO) {
                runCatching {
                    val remote = LuvApiClient.fetchInventory()
                    prefs.applyInventoryBag(
                        emojis = remote.emojis,
                        themes = remote.themes,
                        stickers = remote.stickers,
                        pets = remote.pets,
                        equippedPet = remote.equippedPet
                    )
                }
            }
            ownedStickers = withContext(Dispatchers.IO) {
                prefs.ownedStickers().keys
            }
            ownedThemes = withContext(Dispatchers.IO) { prefs.ownedThemes() }
            ownedPets = withContext(Dispatchers.IO) { prefs.ownedPets() }
            val equipped = withContext(Dispatchers.IO) { prefs.equippedPet() }
            if (state.companionEmoji != equipped) {
                state = state.copy(companionEmoji = equipped)
            }
            displayCoins = AccountSession.account.value?.coins ?: myCoins
            profileReady = true
        } else if (!userId.isNullOrBlank()) {
            val remote = LuvApiClient.fetchUserProfileCanvas(userId)
            if (remote != null) {
                loadedNick = remote.nickname
                state = remote.state.normalized(remote.nickname)
                displayCoins = remote.coins
                friendStatus = remote.friendStatus
                canPetKraul = remote.canPetKraul
                canTipGlass = remote.canTipGlass
                glassTipsRemaining = remote.glassTipsRemaining
                peerPetEmoji = remote.petEmoji.ifBlank { remote.state.companionEmoji.ifBlank { "🐣" } }
            } else {
                loadedNick = nickname
                state = ProfileState(layout = ProfileCatalog.defaultLayout(nickname)).normalized(nickname)
                displayCoins = 0
                friendStatus = "none"
                canPetKraul = false
                canTipGlass = false
                glassTipsRemaining = 0
                peerPetEmoji = "🐣"
            }
            savedSnapshot = state.snapshotKey()
            val minMs = 1100L
            val elapsed = SystemClock.elapsedRealtime() - started
            if (elapsed < minMs) delay(minMs - elapsed)
            profileReady = true
        } else {
            loadedNick = nickname
            state = ProfileState(layout = ProfileCatalog.defaultLayout(nickname)).normalized(nickname)
            savedSnapshot = state.snapshotKey()
            profileReady = true
        }
    }

    LaunchedEffect(myCoins, editable) {
        if (editable) displayCoins = myCoins
    }

    fun tipGlassOnce() {
        val uid = userId ?: return
        if (editable || tipBusy || !canTipGlass) return
        tipBusy = true
        scope.launch {
            try {
                val result = LuvApiClient.tipGlass(uid)
                displayCoins = result.toCoins
                glassTipsRemaining = result.remaining
                canTipGlass = result.remaining > 0
                val id = SystemClock.elapsedRealtimeNanos()
                tipPopIds = tipPopIds + id
                delay(900)
                tipPopIds = tipPopIds.filter { it != id }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.message ?: "Spenden fehlgeschlagen",
                    Toast.LENGTH_SHORT
                ).show()
                if (e is com.luv.couple.net.LuvApiException && e.error == "daily_tip_limit") {
                    canTipGlass = false
                    glassTipsRemaining = 0
                }
            } finally {
                tipBusy = false
            }
        }
    }

    fun startPetKraul() {
        if (userId == null) return
        if (editable || !canPetKraul || petKraulBusy) return
        showPetKraul = true
    }

    /** Layout-Patch ohne Normalize — verhindert Zurücksetzen anderer Elemente. */
    fun patchLayout(next: List<ProfileLayoutEl>) {
        state = state.copy(layout = next)
    }

    fun updateEl(updated: ProfileLayoutEl) {
        patchLayout(state.layout.map { if (it.id == updated.id) updated else it })
    }

    fun setTheme(theme: ProfileTheme) {
        if (!editable) return
        state = state.copy(themeId = theme.id)
        showChest = false
    }

    fun placeCompanion(emoji: String) {
        if (!editable) return
        // Begleiter sitzt mittig im Avatar-Kreis — kein separates Pet-Element mehr
        val nextLayout = state.layout.filterNot { it.type == ProfileElType.Pet }
        state = state.copy(companionEmoji = emoji, layout = nextLayout)
        selectedId = nextLayout.firstOrNull { it.type == ProfileElType.Avatar }?.id
        showChest = false
        scope.launch {
            runCatching {
                val eq = LuvApiClient.equipPet(emoji)
                withContext(Dispatchers.IO) { prefs.setEquippedPet(eq) }
            }
        }
    }

    fun placeGlass() {
        if (!editable) return
        val existing = state.layout.firstOrNull { it.type == ProfileElType.Glass }
        if (existing != null) {
            selectedId = existing.id
            showChest = false
            return
        }
        val el = ProfileCatalog.newGlass()
        patchLayout(state.layout + el)
        selectedId = el.id
        showChest = false
    }

    fun placeBio() {
        if (!editable) return
        val existing = state.layout.firstOrNull { it.type == ProfileElType.Bio }
        if (existing != null) {
            selectedId = existing.id
            showChest = false
            return
        }
        val el = ProfileCatalog.newBio(state.bio)
        patchLayout(state.layout + el)
        selectedId = el.id
        editElId = el.id
        showChest = false
    }

    fun placeSticker(emoji: String) {
        if (!editable) return
        if (state.layout.count { it.type == ProfileElType.Sticker } >= ProfileCatalog.MAX_DECOR) {
            Toast.makeText(context, "Maximal ${ProfileCatalog.MAX_DECOR} Sticker", Toast.LENGTH_SHORT).show()
            return
        }
        val el = ProfileCatalog.newSticker(emoji, state.layout)
        patchLayout(state.layout + el)
        selectedId = el.id
        showChest = false
    }

    fun applyPendingPlace(action: ProfilePlaceAction) {
        when (action) {
            is ProfilePlaceAction.Theme -> setTheme(ProfileCatalog.theme(action.themeId))
            is ProfilePlaceAction.Sticker -> placeSticker(action.emoji)
            is ProfilePlaceAction.Buddy -> placeCompanion(action.emoji)
            ProfilePlaceAction.Glass -> placeGlass()
            ProfilePlaceAction.Bio -> placeBio()
        }
    }

    // Nach dem Laden: Item aus Menü-Inventar platzieren
    LaunchedEffect(editable, savedSnapshot) {
        if (!editable || savedSnapshot.isEmpty()) return@LaunchedEffect
        val action = PendingProfilePlace.consume() ?: return@LaunchedEffect
        applyPendingPlace(action)
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
        if (!profileReady) {
            ProfileBrushHeartLoader(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.12f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = TextMuted, fontSize = 16.sp)
            }
            return@Box
        }

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

            Box(modifier = Modifier.fillMaxWidth()) {
                ProfileCanvasBoard(
                    state = state,
                    nickname = loadedNick,
                    colorIndex = colorIndex,
                    coins = displayCoins,
                    editable = editable,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onLayoutChange = { patchLayout(it) },
                    onOpenChest = { if (editable) showChest = true },
                    onEdit = { if (editable) editElId = it },
                    onTipGlass = if (!editable && !userId.isNullOrBlank() && canTipGlass && !tipBusy) {
                        { tipGlassOnce() }
                    } else {
                        null
                    },
                    onPetKraul = if (!editable && !userId.isNullOrBlank() && canPetKraul) {
                        { startPetKraul() }
                    } else {
                        null
                    }
                )
                tipPopIds.forEach { popId ->
                    key(popId) {
                        TipCoinPop(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }

            Text(
                if (editable) "FÜR ALLE SICHTBAR" else "PROFIL",
                color = TextMuted,
                fontFamily = BodyFont,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp)
            )
            if (!editable && state.layout.any { it.type == ProfileElType.Glass }) {
                Text(
                    if (canTipGlass) {
                        "Münzglas heute noch $glassTipsRemaining / 10 · Tippen zum Spenden"
                    } else {
                        "Münzglas heute voll · ab 0 Uhr MEZ wieder"
                    },
                    color = TextMuted,
                    fontFamily = BodyFont,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (editable) {
                Text("Bio", color = TextMuted, fontFamily = BodyFont, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = state.bio,
                    onValueChange = { raw ->
                        val next = raw.take(ProfileCatalog.MAX_BIO)
                        state = state.copy(bio = next)
                        val bioEl = state.layout.firstOrNull { it.type == ProfileElType.Bio }
                        if (bioEl != null) {
                            patchLayout(
                                state.layout.map {
                                    if (it.type == ProfileElType.Bio) it.copy(text = next) else it
                                }
                            )
                        }
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
            } else {
                if (state.bio.isNotBlank()) {
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
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (!userId.isNullOrBlank()) {
                    when (friendStatus) {
                        "friends" -> Text(
                            "Ihr seid Freunde 💛",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        "outgoing" -> Text(
                            "Freundschaftsanfrage gesendet…",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        "incoming" -> {
                            Text(
                                "Möchte befreundet sein",
                                color = TextPrimary,
                                fontFamily = DisplayFont,
                                fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(23.dp))
                                        .background(AccentRose.copy(0.28f))
                                        .clickable {
                                            val uid = userId ?: return@clickable
                                            scope.launch {
                                                runCatching { LuvApiClient.acceptFriend(uid) }
                                                    .onSuccess { friendStatus = "friends" }
                                                    .onFailure {
                                                        Toast.makeText(
                                                            context,
                                                            it.message ?: "Fehler",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Annehmen", color = AccentRose, fontFamily = DisplayFont)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(23.dp))
                                        .background(BgSoft)
                                        .clickable {
                                            val uid = userId ?: return@clickable
                                            scope.launch {
                                                runCatching { LuvApiClient.declineFriend(uid) }
                                                    .onSuccess { friendStatus = "none" }
                                                    .onFailure {
                                                        Toast.makeText(
                                                            context,
                                                            it.message ?: "Fehler",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Ablehnen", color = TextMuted, fontFamily = BodyFont)
                                }
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(AccentRose, FemalePurple.copy(0.85f))
                                        )
                                    )
                                    .clickable {
                                        val uid = userId ?: return@clickable
                                        scope.launch {
                                            runCatching { LuvApiClient.sendFriendRequest(uid) }
                                                .onSuccess { friendStatus = it }
                                                .onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        it.message ?: "Anfrage fehlgeschlagen",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Freundschaftsanfrage senden",
                                    color = Color.White,
                                    fontFamily = DisplayFont,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                if (canPetKraul) Color(0xFFFFF0F4) else BgSoft
                            )
                            .border(
                                1.dp,
                                if (canPetKraul) AccentRose.copy(0.45f) else Color.White.copy(0.08f),
                                RoundedCornerShape(26.dp)
                            )
                            .clickable(enabled = canPetKraul && !petKraulBusy) { startPetKraul() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (canPetKraul) "$peerPetEmoji  Begleiter kraulen"
                            else "$peerPetEmoji  Heute schon gekrault",
                            color = if (canPetKraul) Color(0xFF5A3040) else TextMuted,
                            fontFamily = DisplayFont,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        if (showPetKraul) {
            val kraulUid = userId
            if (kraulUid != null) {
                PetKraulOverlay(
                    petEmoji = peerPetEmoji.ifBlank { state.companionEmoji.ifBlank { "🐣" } },
                    onCredit = {
                        petKraulBusy = true
                        val result = LuvApiClient.petKraul(kraulUid)
                        displayCoins = result.toCoins
                        peerPetEmoji = result.petEmoji
                        canPetKraul = false
                        result.amount
                    },
                    onFinished = { ok, message ->
                        showPetKraul = false
                        petKraulBusy = false
                        if (!ok && !message.isNullOrBlank()) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        if (showChest && editable) {
            ProfileChestDialog(
                ownedStickers = ownedStickers.toList().sorted(),
                ownedThemes = ownedThemes,
                ownedPets = ownedPets,
                currentThemeId = state.themeId,
                currentCompanion = state.companionEmoji,
                hasGlass = state.layout.any { it.type == ProfileElType.Glass },
                hasBio = state.layout.any { it.type == ProfileElType.Bio },
                onTheme = { setTheme(it) },
                onSticker = { placeSticker(it) },
                onCompanion = { placeCompanion(it) },
                onGlass = { placeGlass() },
                onBio = { placeBio() },
                selectedTab = chestTab,
                onTabChange = { chestTab = it },
                onOpenMarketplace = {
                    showChest = false
                    if (onOpenMarketplace != null) onOpenMarketplace(chestTab)
                    else {
                        PendingShop.offer()
                        Toast.makeText(context, "Öffne den Marktplatz im Menü", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                },
                onOpenItemShop = {
                    showChest = false
                    if (onOpenItemShop != null) onOpenItemShop(chestTab)
                    else {
                        PendingShop.offer()
                        Toast.makeText(context, "Öffne den Itemshop im Menü", Toast.LENGTH_SHORT).show()
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
                        state = state.copy(bio = next.text.orEmpty())
                        updateEl(next)
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
private fun TipCoinPop(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700),
        label = "tipAlpha"
    )
    val rise by animateFloatAsState(
        targetValue = if (visible) -36f else 0f,
        animationSpec = tween(700),
        label = "tipRise"
    )
    LaunchedEffect(Unit) {
        delay(40)
        visible = false
    }
    Text(
        "+1 Coin",
        color = AccentRose,
        fontFamily = DisplayFont,
        fontSize = 22.sp,
        modifier = modifier
            .offset(y = rise.dp)
            .alpha(alpha)
    )
}

private enum class PetKraulPhase { Stroke, Fade, Credit, Coin, Done }

@Composable
private fun PetKraulOverlay(
    petEmoji: String,
    onCredit: suspend () -> Int,
    onFinished: (ok: Boolean, message: String?) -> Unit
) {
    var phase by remember { mutableStateOf(PetKraulPhase.Stroke) }
    var isStroking by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var handOffset by remember { mutableStateOf(Offset(28f, 8f)) }
    var showHand by remember { mutableStateOf(false) }
    var creditError by remember { mutableStateOf<String?>(null) }
    var creditedAmount by remember { mutableIntStateOf(1) }
    var finishedOnce by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "kraul")
    val hintWave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hintWave"
    )
    val petBounce by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "petBounce"
    )
    val heartPulse by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    val petVisible = phase == PetKraulPhase.Stroke || phase == PetKraulPhase.Fade
    val petAlpha by animateFloatAsState(
        targetValue = when (phase) {
            PetKraulPhase.Stroke -> 1f
            PetKraulPhase.Fade -> 0f
            else -> 0f
        },
        animationSpec = tween(480),
        label = "petAlpha"
    )
    val showCoin = phase == PetKraulPhase.Coin
    val coinAlpha by animateFloatAsState(
        targetValue = if (showCoin) 1f else 0f,
        animationSpec = tween(380),
        label = "coinAlpha"
    )
    val coinRise by animateFloatAsState(
        targetValue = if (showCoin) -36f else 12f,
        animationSpec = tween(520),
        label = "coinRise"
    )

    // 2 Sekunden streichen (nur während Finger bewegt)
    LaunchedEffect(isStroking, phase) {
        if (phase != PetKraulPhase.Stroke) return@LaunchedEffect
        while (isStroking && progress < 1f) {
            delay(16)
            progress = (progress + 16f / 2000f).coerceAtMost(1f)
        }
        if (progress >= 1f && phase == PetKraulPhase.Stroke) {
            isStroking = false
            showHand = false
            phase = PetKraulPhase.Fade
        }
    }

    LaunchedEffect(phase) {
        when (phase) {
            PetKraulPhase.Fade -> {
                delay(520)
                phase = PetKraulPhase.Credit
            }
            PetKraulPhase.Credit -> {
                try {
                    creditedAmount = onCredit().coerceAtLeast(1)
                    phase = PetKraulPhase.Coin
                } catch (e: Exception) {
                    creditError = e.message ?: "Kraulen fehlgeschlagen"
                    phase = PetKraulPhase.Done
                }
            }
            PetKraulPhase.Coin -> {
                delay(1400)
                phase = PetKraulPhase.Done
            }
            PetKraulPhase.Done -> {
                if (!finishedOnce) {
                    finishedOnce = true
                    onFinished(creditError == null, creditError)
                }
            }
            else -> Unit
        }
    }

    Dialog(
        onDismissRequest = {
            if (phase == PetKraulPhase.Stroke) {
                onFinished(false, null)
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = phase == PetKraulPhase.Stroke,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC1A1420))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(280.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x66FF8FAB), Color.Transparent)
                        )
                    )
                    .alpha(petAlpha * 0.9f)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(if (petVisible) petAlpha else 0f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .pointerInput(phase) {
                            if (phase != PetKraulPhase.Stroke) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isStroking = true
                                    showHand = true
                                    handOffset = Offset(
                                        offset.x - size.width / 2f,
                                        offset.y - size.height / 2f
                                    )
                                },
                                onDragEnd = {
                                    isStroking = false
                                    showHand = false
                                },
                                onDragCancel = {
                                    isStroking = false
                                    showHand = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    isStroking = true
                                    showHand = true
                                    handOffset = Offset(
                                        change.position.x - size.width / 2f,
                                        change.position.y - size.height / 2f
                                    )
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "💕",
                        fontSize = 28.sp,
                        modifier = Modifier
                            .offset(x = (-58).dp, y = (-40).dp)
                            .graphicsLayer {
                                scaleX = heartPulse
                                scaleY = heartPulse
                                alpha = 0.85f
                            }
                    )
                    Text(
                        "💖",
                        fontSize = 22.sp,
                        modifier = Modifier
                            .offset(x = 62.dp, y = (-28).dp)
                            .graphicsLayer {
                                scaleX = heartPulse * 0.9f
                                scaleY = heartPulse * 0.9f
                                alpha = 0.75f
                            }
                    )
                    Text(
                        petEmoji,
                        fontSize = 72.sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = petBounce
                            scaleY = petBounce
                        }
                    )
                    if (showHand || isStroking) {
                        val density = LocalDensity.current
                        Text(
                            "🤚",
                            fontSize = 42.sp,
                            modifier = Modifier
                                .offset(
                                    x = with(density) { handOffset.x.toDp() },
                                    y = with(density) { handOffset.y.toDp() }
                                )
                                .graphicsLayer {
                                    rotationZ = -16f
                                }
                        )
                    }
                }

                if (phase == PetKraulPhase.Stroke) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .width(160.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = AccentRose,
                        trackColor = Color.White.copy(0.2f)
                    )
                }
            }

            // +1 Coin erst wenn Begleiter weg ist und Gutschrift ok
            if (phase == PetKraulPhase.Coin || coinAlpha > 0.01f) {
                Text(
                    "+$creditedAmount Coin",
                    color = AccentRose,
                    fontFamily = DisplayFont,
                    fontSize = 32.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = coinRise.dp)
                        .alpha(coinAlpha)
                )
            }

            // Unten: Hinweis mit Strich-Linien
            if (phase == PetKraulPhase.Stroke) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp, start = 28.dp, end = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(28.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val path = Path()
                        val amp = h * 0.28f
                        val mid = h * 0.55f
                        path.moveTo(0f, mid)
                        var x = 0f
                        while (x <= w) {
                            val t = x / w + hintWave
                            val y = mid + sin(t * Math.PI * 4).toFloat() * amp
                            path.lineTo(x, y)
                            x += 4f
                        }
                        drawPath(
                            path,
                            color = Color.White.copy(0.55f),
                            style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                        )
                        // Zweite, leichtere Strichspur
                        val path2 = Path()
                        path2.moveTo(0f, mid + 6f)
                        x = 0f
                        while (x <= w) {
                            val t = x / w + hintWave + 0.15f
                            val y = mid + 6f + sin(t * Math.PI * 4).toFloat() * (amp * 0.7f)
                            path2.lineTo(x, y)
                            x += 4f
                        }
                        drawPath(
                            path2,
                            color = Color.White.copy(0.28f),
                            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Streiche über den Begleiter",
                        color = Color.White.copy(0.88f),
                        fontFamily = DisplayFont,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "2 Sekunden kraulen",
                        color = Color.White.copy(0.55f),
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
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
    onEdit: (String) -> Unit,
    onTipGlass: (() -> Unit)? = null,
    onPetKraul: (() -> Unit)? = null
) {
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

        ProfileThemeBackdrop(
            themeId = state.themeId,
            modifier = Modifier.fillMaxSize()
        )

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
                companionEmoji = state.companionEmoji,
                selected = selected && editable,
                boardW = boardW,
                boardH = boardH,
                editable = editable,
                onSelect = {
                    if (!editable) return@ProfileElementView
                    onSelect(el.id)
                    if (el.type == ProfileElType.Sticker) {
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
                    // Hart entfernen — wieder hinzufügen über die Truhe
                    if (el.type == ProfileElType.Avatar || el.type == ProfileElType.Name) {
                        // Kern bleibt, nur ausblenden nicht — Kern nicht löschen
                        return@ProfileElementView
                    }
                    onLayoutChange(state.layout.filterNot { it.id == el.id })
                    onSelect(null)
                },
                onEdit = { onEdit(el.id) },
                onTipGlass = if (el.type == ProfileElType.Glass) onTipGlass else null,
                onPetKraul = if (el.type == ProfileElType.Avatar || el.type == ProfileElType.Pet) {
                    onPetKraul
                } else null
            )
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
private fun ProfileElementView(
    el: ProfileLayoutEl,
    nickname: String,
    colorIndex: Int,
    coins: Int,
    companionEmoji: String,
    selected: Boolean,
    boardW: Float,
    boardH: Float,
    editable: Boolean,
    onSelect: () -> Unit,
    onChange: (ProfileLayoutEl) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onTipGlass: (() -> Unit)? = null,
    onPetKraul: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    var dragEl by remember(el.id) { mutableStateOf(el) }
    LaunchedEffect(el) { dragEl = el }
    // Feste Basisgröße — Skalierung läuft über graphicsLayer, damit Emoji/Text mitwachsen
    // Name: breiter Kasten bis zum rechten Rand, Schrift schrumpft bei langen Namen
    val baseW = with(density) {
        when (el.type) {
            ProfileElType.Bio -> 120.dp.toPx()
            ProfileElType.Name -> (boardW * 0.78f).coerceIn(120.dp.toPx(), boardW * 0.92f)
            ProfileElType.Glass -> 72.dp.toPx()
            ProfileElType.Pet -> 64.dp.toPx()
            ProfileElType.Avatar -> 56.dp.toPx()
            else -> 52.dp.toPx()
        }
    }
    val baseH = with(density) {
        when (el.type) {
            ProfileElType.Bio -> 120.dp.toPx()
            ProfileElType.Name -> 40.dp.toPx()
            ProfileElType.Glass -> 72.dp.toPx()
            ProfileElType.Pet -> 64.dp.toPx()
            ProfileElType.Avatar -> 56.dp.toPx()
            else -> 52.dp.toPx()
        }
    }
    val baseSize = maxOf(baseW, baseH) // für clampPos / Handles
    val dash = with(density) { 8.dp.toPx() }
    val gap = with(density) { 5.dp.toPx() }
    val strokeW = with(density) { 2.dp.toPx() }
    val canEdit = el.type in ProfileCatalog.EDITABLE_TYPES
    val canRemove = el.type != ProfileElType.Avatar && el.type != ProfileElType.Name
    val invScale = 1f / dragEl.scale.coerceIn(0.35f, 2.5f)

    Box(
        modifier = Modifier
            .zIndex(el.z.toFloat() + if (selected) 50f else 0f)
            // Position per Offset (wie nasebär %-Koordinaten), Drag außerhalb von Scale
            .offset {
                IntOffset(
                    (boardW * (dragEl.x / 100f) - baseW / 2f).roundToInt(),
                    (boardH * (dragEl.y / 100f) - baseH / 2f).roundToInt()
                )
            }
            .width(with(density) { baseW.toDp() })
            .height(with(density) { baseH.toDp() })
            .then(
                if (editable) {
                    Modifier.pointerInput(el.id, boardW, boardH, baseW) {
                        // Origin-basiert wie nasebär — keine inkrementellen Drift-Fehler
                        var origX = dragEl.x
                        var origY = dragEl.y
                        var accX = 0f
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                origX = dragEl.x
                                origY = dragEl.y
                                accX = 0f
                                accY = 0f
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                accX += drag.x
                                accY += drag.y
                                val nx = origX + accX / boardW * 100f
                                val ny = origY + accY / boardH * 100f
                                val (cx, cy) = ProfileCatalog.clampPos(
                                    dragEl, nx, ny, boardW, boardH, baseW,
                                    nameText = nickname
                                )
                                val next = dragEl.copy(x = cx, y = cy)
                                dragEl = next
                                onChange(next)
                            }
                        )
                    }
                } else Modifier
            )
            .graphicsLayer {
                val s = dragEl.scale
                rotationZ = dragEl.rotation
                scaleX = s * (if (dragEl.flipX) -1f else 1f)
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clickable(
                enabled = editable ||
                    (el.type == ProfileElType.Glass && onTipGlass != null) ||
                    ((el.type == ProfileElType.Pet || el.type == ProfileElType.Avatar) &&
                        onPetKraul != null)
            ) {
                if (editable) onSelect()
                else when (el.type) {
                    ProfileElType.Glass -> onTipGlass?.invoke()
                    ProfileElType.Pet, ProfileElType.Avatar -> onPetKraul?.invoke()
                    else -> Unit
                }
            }
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
            contentAlignment = when (el.type) {
                ProfileElType.Name -> Alignment.CenterStart
                else -> Alignment.Center
            }
        ) {
            ElementContent(dragEl, nickname, colorIndex, coins, companionEmoji)
        }

        if (selected && editable) {
            // Handles visuell gegen Parent-Scale gegenrechnen — Position in lokaler
            // Box-Space (vor Scale), damit die Mitte immer auf der Ecke der Strichelung bleibt
            val handleMod = Modifier.graphicsLayer {
                scaleX = invScale * (if (dragEl.flipX) -1f else 1f)
                scaleY = invScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            // Halbe Layout-Größe (28.dp/2) — nicht mit invScale multiplizieren,
            // sonst wandern die Handles bei Scale>1 nach innen
            val cornerPad = 14.dp

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -cornerPad, y = -cornerPad)
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
                        .offset(x = cornerPad, y = -cornerPad)
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
                        .offset(x = -cornerPad, y = cornerPad)
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
                        .offset(x = -cornerPad, y = cornerPad)
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
                    .offset(x = cornerPad, y = cornerPad)
                    .then(handleMod)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color.Black.copy(0.15f), CircleShape)
                    .pointerInput(el.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
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
    coins: Int,
    companionEmoji: String = "🐣"
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
    val textMeasurer = rememberTextMeasurer()

    when (el.type) {
        ProfileElType.Avatar -> {
            val fill = Color(PeerPalette.strokeColor(colorIndex))
            val pet = companionEmoji.trim().ifBlank {
                el.emoji?.takeIf { it.isNotBlank() } ?: "🐣"
            }
            Box(
                modifier = Modifier
                    .fillMaxSize(0.88f)
                    .clip(CircleShape)
                    .background(fill)
                    .border(2.dp, Color.White.copy(0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(pet, fontSize = 28.sp)
            }
        }
        ProfileElType.Name -> {
            val label = el.text ?: nickname
            val maxSp = (el.fontSize ?: 18f).coerceIn(8f, 28f)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                val maxW = constraints.maxWidth
                val fittedSp = remember(label, maxW, maxSp, font) {
                    var sp = maxSp
                    while (sp > 8f) {
                        val result = textMeasurer.measure(
                            text = label,
                            style = TextStyle(
                                fontFamily = font,
                                fontSize = sp.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (result.size.width <= maxW) break
                        sp -= 0.5f
                    }
                    sp
                }
                Text(
                    label,
                    color = color,
                    fontFamily = font,
                    fontSize = fittedSp.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Start,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        ProfileElType.Status -> Text(el.emoji ?: el.text ?: "😊", fontSize = 30.sp)
        ProfileElType.Bio -> {
            val body = el.text.orEmpty()
            if (body.isBlank()) {
                // Kein „…“ — leerer Trefferbereich, per ✕ entfernbar
                Box(modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    body,
                    color = color,
                    fontFamily = font,
                    fontSize = sizeSp,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
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

            if (el.type == ProfileElType.Name ||
                el.type == ProfileElType.Glass ||
                el.type == ProfileElType.Bio ||
                el.type == ProfileElType.Text
            ) {
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
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                f.label,
                                color = TextPrimary,
                                fontFamily = BodyFont,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
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
