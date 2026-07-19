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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
    var ownedStickers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
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
    var friendshipLevel by remember { mutableIntStateOf(0) }
    var canProposeMarriage by remember { mutableStateOf(false) }
    var proposeUnlockCost by remember { mutableIntStateOf(0) }
    var marriageCooldownLabel by remember { mutableStateOf<String?>(null) }
    var marriageCooldownSkipCost by remember { mutableIntStateOf(0) }
    var partnerCooldownLabel by remember { mutableStateOf<String?>(null) }
    var canDivorce by remember { mutableStateOf(false) }
    var spouseExtraName by remember { mutableStateOf<String?>(null) }
    var engagedExtraName by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var showMarryInfo by remember { mutableStateOf(false) }
    var showDivorce by remember { mutableStateOf(false) }
    var divorceTyped by remember { mutableStateOf("") }
    var divorceStep2 by remember { mutableStateOf(false) }
    var showUnfriendConfirm by remember { mutableStateOf(false) }
    var showGuestbookFor by remember { mutableStateOf<String?>(null) }
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
            ownedStickers = withContext(Dispatchers.IO) { prefs.ownedStickers() }
            ownedThemes = withContext(Dispatchers.IO) { prefs.ownedThemes() }
            ownedPets = withContext(Dispatchers.IO) { prefs.ownedPets() }
            // Server-Profil ist Quelle der Wahrheit — lokales Equipped nicht darüber stülpen
            val companion = state.companionEmoji.trim()
            if (companion.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    runCatching { prefs.setEquippedPet(companion) }
                }
            }
            displayCoins = AccountSession.account.value?.coins ?: myCoins
            val myId = AccountSession.account.value?.id
            if (!myId.isNullOrBlank()) {
                runCatching { LuvApiClient.fetchUserProfileCanvas(myId) }.getOrNull()?.let { me ->
                    spouseExtraName = me.spousePublic?.nickname
                    engagedExtraName = me.fiancePublic?.nickname
                }
            } else {
                runCatching { LuvApiClient.fetchFriends() }.getOrNull()?.myMarriage?.let { m ->
                    when (m.status) {
                        "married" -> spouseExtraName = m.partnerNickname
                        "engaged", "wedding" -> engagedExtraName = m.partnerNickname
                    }
                }
            }
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
                friendshipLevel = remote.friendshipLevel
                canProposeMarriage = remote.canProposeMarriage
                proposeUnlockCost = remote.proposeUnlockCost
                marriageCooldownLabel = remote.marriageCooldownLabel
                marriageCooldownSkipCost = remote.marriageCooldownSkipCost
                partnerCooldownLabel = remote.partnerMarriageCooldownLabel
                canDivorce = remote.canDivorce
                spouseExtraName = remote.spousePublic?.nickname
                engagedExtraName = remote.fiancePublic?.nickname
            } else {
                loadedNick = nickname
                state = ProfileState(layout = ProfileCatalog.defaultLayout(nickname)).normalized(nickname)
                displayCoins = 0
                friendStatus = "none"
                canPetKraul = false
                canTipGlass = false
                glassTipsRemaining = 0
                peerPetEmoji = "🐣"
                friendshipLevel = 0
                canProposeMarriage = false
                proposeUnlockCost = 0
                marriageCooldownLabel = null
                marriageCooldownSkipCost = 0
                partnerCooldownLabel = null
                canDivorce = false
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

    fun stickerCounts(layout: List<ProfileLayoutEl>): Map<String, Int> =
        layout.filter { it.type == ProfileElType.Sticker }
            .mapNotNull { it.emoji?.trim()?.takeIf { e -> e.isNotBlank() } }
            .groupingBy { it }
            .eachCount()

    /** Layout setzen und freien Sticker-Bestand anpassen (Platzieren verbraucht, Entfernen gibt zurück). */
    fun applyLayoutWithStickerStock(next: List<ProfileLayoutEl>) {
        if (!editable) {
            patchLayout(next)
            return
        }
        val oldC = stickerCounts(state.layout)
        val newC = stickerCounts(next)
        val keys = oldC.keys + newC.keys
        val stock = ownedStickers.toMutableMap()
        for (e in keys) {
            val delta = (newC[e] ?: 0) - (oldC[e] ?: 0)
            if (delta > 0) {
                val have = stock[e] ?: 0
                if (have < delta) {
                    Toast.makeText(context, "Nicht genug $e im Inventar", Toast.LENGTH_SHORT).show()
                    return
                }
                val left = have - delta
                if (left <= 0) stock.remove(e) else stock[e] = left
            } else if (delta < 0) {
                stock[e] = (stock[e] ?: 0) - delta
            }
        }
        ownedStickers = stock
        patchLayout(next)
        scope.launch {
            val emojis = withContext(Dispatchers.IO) { prefs.ownedEmojis() }
            val equipped = withContext(Dispatchers.IO) { prefs.equippedPet() }
            prefs.applyInventoryBag(
                emojis = emojis,
                themes = ownedThemes,
                stickers = stock,
                pets = ownedPets,
                equippedPet = equipped
            )
        }
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

    fun placeSpouse() {
        if (!editable) return
        val nick = spouseExtraName ?: return
        val existing = state.layout.firstOrNull { it.type == ProfileElType.Spouse }
        if (existing != null) {
            selectedId = existing.id
            showChest = false
            return
        }
        val el = ProfileCatalog.newSpouse(nick)
        patchLayout(
            state.layout.filterNot {
                it.type == ProfileElType.Spouse || it.type == ProfileElType.Engaged
            } + el
        )
        selectedId = el.id
        showChest = false
    }

    fun placeEngaged() {
        if (!editable) return
        val nick = engagedExtraName ?: return
        val existing = state.layout.firstOrNull { it.type == ProfileElType.Engaged }
        if (existing != null) {
            selectedId = existing.id
            showChest = false
            return
        }
        val el = ProfileCatalog.newEngaged(nick)
        patchLayout(
            state.layout.filterNot {
                it.type == ProfileElType.Spouse || it.type == ProfileElType.Engaged
            } + el
        )
        selectedId = el.id
        showChest = false
    }

    fun placeSticker(emoji: String) {
        if (!editable) return
        if ((ownedStickers[emoji] ?: 0) < 1) {
            Toast.makeText(context, "Kein $emoji mehr im Inventar", Toast.LENGTH_SHORT).show()
            return
        }
        if (state.layout.count { it.type == ProfileElType.Sticker } >= ProfileCatalog.MAX_DECOR) {
            Toast.makeText(context, "Maximal ${ProfileCatalog.MAX_DECOR} Sticker", Toast.LENGTH_SHORT).show()
            return
        }
        val el = ProfileCatalog.newSticker(emoji, state.layout)
        applyLayoutWithStickerStock(state.layout + el)
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

    fun persistProfile(closeAfter: Boolean, silent: Boolean = false) {
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
            val saveResult = runCatching { LuvApiClient.saveMyProfileCanvas(clean) }
                .getOrElse { false to null }
            val ok = saveResult.first
            val inv = saveResult.second
            if (ok && inv != null) {
                ownedStickers = inv.stickers
                ownedThemes = inv.themes
                ownedPets = inv.pets
                withContext(Dispatchers.IO) {
                    prefs.applyInventoryBag(
                        emojis = inv.emojis,
                        themes = inv.themes,
                        stickers = inv.stickers,
                        pets = inv.pets,
                        equippedPet = inv.equippedPet
                    )
                }
            }
            saving = false
            savedSnapshot = clean.snapshotKey()
            if (!silent) {
                Toast.makeText(
                    context,
                    if (ok) "Profil gespeichert" else "Lokal gespeichert",
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (closeAfter) onClose()
        }
    }

    fun save() = persistProfile(closeAfter = true, silent = false)

    // Z-Reihenfolge / Größe still mit Google-Konto synchronisieren
    LaunchedEffect(editable, state.snapshotKey()) {
        if (!editable || savedSnapshot.isEmpty()) return@LaunchedEffect
        if (state.snapshotKey() == savedSnapshot) return@LaunchedEffect
        delay(1600)
        if (state.snapshotKey() == savedSnapshot || saving) return@LaunchedEffect
        persistProfile(closeAfter = false, silent = true)
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
                val isMarriedProfile = !spouseExtraName.isNullOrBlank() ||
                    state.layout.any { it.type == ProfileElType.Spouse }
                ProfileCanvasBoard(
                    state = state,
                    nickname = loadedNick,
                    colorIndex = colorIndex,
                    coins = displayCoins,
                    editable = editable,
                    selectedId = selectedId,
                    marriageCelebration = isMarriedProfile,
                    onSelect = { selectedId = it },
                    onLayoutChange = { applyLayoutWithStickerStock(it) },
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
                    },
                    onOpenSpouse = {
                        val id = userId?.takeIf { it.isNotBlank() }
                            ?: AccountSession.account.value?.id
                        if (!id.isNullOrBlank()) showGuestbookFor = id
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
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Profil ansehen",
                    color = AccentRose,
                    fontFamily = DisplayFont,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { showPreview = true }
                        .padding(8.dp)
                )
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
                val viewingSelf = friendStatus == "self" ||
                    (!userId.isNullOrBlank() && userId == AccountSession.account.value?.id)
                if (!userId.isNullOrBlank() && viewingSelf) {
                    Text(
                        "So sehen andere dich",
                        color = TextMuted,
                        fontFamily = BodyFont,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else if (!userId.isNullOrBlank()) {
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
                    if (friendStatus == "friends") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Freundschaftslevel $friendshipLevel / 100",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    // Kein Heiraten, wenn schon Verlobt/Ehe mit jemandem (auch bei API-Drift)
                    val alreadyBonded = !spouseExtraName.isNullOrBlank() ||
                        !engagedExtraName.isNullOrBlank()
                    if (canProposeMarriage && !alreadyBonded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val totalHint = proposeUnlockCost + marriageCooldownSkipCost
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFFFFD54F).copy(0.28f))
                                .border(1.dp, Color(0xFFFFD54F).copy(0.7f), RoundedCornerShape(24.dp))
                                .clickable { showMarryInfo = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when {
                                    friendshipLevel >= 100 && totalHint <= 0 ->
                                        "💍  Heiraten · Level 100 gratis"
                                    totalHint > 0 -> "💍  Heiraten · ab $totalHint Coins"
                                    else -> "💍  Heiraten"
                                },
                                color = Color.White,
                                fontFamily = DisplayFont
                            )
                        }
                    } else if (
                        friendStatus == "friends" &&
                        !alreadyBonded &&
                        canProposeMarriage.not() &&
                        !partnerCooldownLabel.isNullOrBlank() &&
                        partnerCooldownLabel != "null" &&
                        partnerCooldownLabel!!.any { it.isDigit() }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Partner hat noch Scheidungs-Wartezeit ($partnerCooldownLabel)",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    if (canDivorce) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Scheiden lassen",
                            color = TextMuted,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable {
                                    divorceTyped = ""
                                    divorceStep2 = false
                                    showDivorce = true
                                }
                                .padding(6.dp)
                        )
                    }
                    if (friendStatus == "friends" && !canDivorce) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Freundschaft beenden",
                            color = TextMuted.copy(0.85f),
                            fontFamily = BodyFont,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable { showUnfriendConfirm = true }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }

        if (showUnfriendConfirm) {
            HoldSlideConfirmDialog(
                title = "Freundschaft beenden",
                body = "Schiebe den Regler nach rechts und halte ihn 5 Sekunden. " +
                    "Loslassen setzt zurück. Freundschaftslevel wird zurückgesetzt.",
                holdSeconds = 5,
                accent = AccentRose,
                confirmHint = "Nach rechts schieben und halten",
                onDismiss = { showUnfriendConfirm = false },
                onConfirmed = {
                    val uid = userId ?: return@HoldSlideConfirmDialog
                    scope.launch {
                        runCatching { LuvApiClient.removeFriend(uid) }
                            .onSuccess {
                                friendStatus = "none"
                                friendshipLevel = 0
                                Toast.makeText(context, "Freundschaft beendet", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Fehler",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
            )
        }

        if (showMarryInfo) {
            val unlock = proposeUnlockCost
            val cool = marriageCooldownSkipCost
            val total = unlock + cool
            val coins = AccountSession.account.value?.coins ?: displayCoins
            val costLine = buildString {
                if (friendshipLevel >= 100) {
                    append("Freundschaftslevel 100 — Heiratsanfrage kostenlos. ")
                } else {
                    append("Level $friendshipLevel/100. Unter 100 kostet die Freischaltung $unlock Coins. ")
                }
                if (cool > 0) append("Offene Scheidungs-Wartezeit: +$cool Coins. ")
                if (total > 0) append("Jetzt $total Coins · du hast $coins. ")
                append(
                    "Nach Annahme: 7 Tage verlobt, bis die Hochzeitsleinwand öffnet. " +
                        "Dann 7 Tage gemeinsam malen — erst danach seid ihr verheiratet. " +
                        "Beide Wartezeiten könnt ihr mit Coins überspringen."
                )
            }
            HoldSlideConfirmDialog(
                title = if (total > 0) {
                    "Hochzeit anfragen · $total Coins"
                } else {
                    "Hochzeit anfragen · kostenlos"
                },
                body = costLine,
                holdSeconds = 10,
                accent = Color(0xFFFFD54F),
                confirmHint = "Nach rechts schieben und halten",
                enabled = total <= 0 || coins >= total,
                onDismiss = { showMarryInfo = false },
                onConfirmed = {
                    val uid = userId ?: return@HoldSlideConfirmDialog
                    scope.launch {
                        runCatching {
                            LuvApiClient.proposeMarriage(uid, unlockWithCoins = unlock > 0)
                        }
                            .onSuccess {
                                canProposeMarriage = false
                                proposeUnlockCost = 0
                                marriageCooldownSkipCost = 0
                                marriageCooldownLabel = null
                                displayCoins = AccountSession.account.value?.coins ?: displayCoins
                                Toast.makeText(
                                    context,
                                    if (total > 0) "Antrag gesendet (−$total Coins) 💍"
                                    else "Antrag gesendet 💍",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Antrag fehlgeschlagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
            )
        }

        if (showDivorce && !divorceStep2) {
            AlertDialog(
                onDismissRequest = { showDivorce = false },
                containerColor = BgSoft,
                title = { Text("Scheidung", fontFamily = DisplayFont, color = TextPrimary) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Tippe „scheiden“ zur Bestätigung. Partner muss nicht zustimmen. " +
                                "Ehepartner-Extra, Hochzeitsbild und Ehering entfallen. " +
                                "Danach 7 Tage Wartezeit (oder Coins), bevor wieder geheiratet werden kann.",
                            fontFamily = BodyFont,
                            color = TextMuted
                        )
                        BasicTextField(
                            value = divorceTyped,
                            onValueChange = { divorceTyped = it },
                            textStyle = TextStyle(color = TextPrimary, fontFamily = BodyFont),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgDeep, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (divorceTyped.trim().equals("scheiden", ignoreCase = true)) {
                            divorceStep2 = true
                        } else {
                            Toast.makeText(context, "Bitte „scheiden“ eingeben", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Weiter", color = AccentRose)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDivorce = false }) {
                        Text("Abbrechen", color = TextMuted)
                    }
                }
            )
        }

        if (showDivorce && divorceStep2) {
            HoldSlideConfirmDialog(
                title = "Wirklich scheiden?",
                body = "Schiebe nach rechts und halte 10 Sekunden. " +
                    "Das kann nicht rückgängig gemacht werden.",
                holdSeconds = 10,
                accent = AccentRose,
                confirmHint = "Nach rechts schieben und halten",
                onDismiss = {
                    showDivorce = false
                    divorceStep2 = false
                },
                onConfirmed = {
                    showDivorce = false
                    divorceStep2 = false
                    scope.launch {
                        runCatching { LuvApiClient.divorceMarriage() }
                            .onSuccess {
                                canDivorce = false
                                spouseExtraName = null
                                Toast.makeText(context, "Geschieden", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Fehler",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
            )
        }

        if (showPreview) {
            val myId = account?.id
            Dialog(
                onDismissRequest = { showPreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.55f))
                        .clickable { showPreview = false }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(20.dp))
                            .background(BgDeep)
                            .clickable(enabled = false) {}
                    ) {
                        if (!myId.isNullOrBlank()) {
                            ProfileCanvasScreen(
                                nickname = loadedNick,
                                colorIndex = colorIndex,
                                editable = false,
                                userId = myId,
                                onClose = { showPreview = false }
                            )
                        }
                    }
                }
            }
        }

        if (showGuestbookFor != null) {
            WeddingGuestbookDialog(
                profileUserId = showGuestbookFor!!,
                onDismiss = { showGuestbookFor = null }
            )
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
                ownedStickers = ownedStickers,
                ownedThemes = ownedThemes,
                ownedPets = ownedPets,
                currentThemeId = state.themeId,
                currentCompanion = state.companionEmoji,
                hasGlass = state.layout.any { it.type == ProfileElType.Glass },
                hasBio = state.layout.any { it.type == ProfileElType.Bio },
                spouseName = spouseExtraName,
                engagedName = engagedExtraName,
                hasSpouse = state.layout.any { it.type == ProfileElType.Spouse },
                hasEngaged = state.layout.any { it.type == ProfileElType.Engaged },
                onTheme = { setTheme(it) },
                onSticker = { placeSticker(it) },
                onCompanion = { placeCompanion(it) },
                onGlass = { placeGlass() },
                onBio = { placeBio() },
                onSpouse = { placeSpouse() },
                onEngaged = { placeEngaged() },
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
                        "2 Sekunden kraulen — einmal pro Tag möglich",
                        color = Color.White.copy(0.55f),
                        fontFamily = BodyFont,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Du und der Begleiter bekommt je 1 Coin.",
                        color = Color.White.copy(0.4f),
                        fontFamily = BodyFont,
                        fontSize = 11.sp,
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
    marriageCelebration: Boolean = false,
    onSelect: (String?) -> Unit,
    onLayoutChange: (List<ProfileLayoutEl>) -> Unit,
    onOpenChest: () -> Unit,
    onEdit: (String) -> Unit,
    onTipGlass: (() -> Unit)? = null,
    onPetKraul: (() -> Unit)? = null,
    onOpenSpouse: (() -> Unit)? = null
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
            modifier = Modifier.fillMaxSize(),
            marriageCelebration = marriageCelebration
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

        // Immer aktuelles Layout — verhindert, dass Drag-Gesten ein altes Layout
        // (Default-Avatar) zurückschreiben und andere Elemente zurücksetzen.
        val layoutLatest = rememberUpdatedState(state.layout)
        val onLayoutChangeLatest = rememberUpdatedState(onLayoutChange)
        val onSelectLatest = rememberUpdatedState(onSelect)

        val visible = state.layout.filter { it.visible }.sortedBy { it.z }
        visible.forEach { el ->
            key(el.id) {
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
                        onSelectLatest.value(el.id)
                        // Jedes Element nach vorne — Reihenfolge bleibt über Speichern/Google
                        onLayoutChangeLatest.value(
                            ProfileCatalog.bringToFront(layoutLatest.value, el.id)
                        )
                    },
                    onChange = { updated ->
                        val cur = layoutLatest.value
                        onLayoutChangeLatest.value(
                            cur.map { if (it.id == updated.id) updated else it }
                        )
                    },
                    onRemove = {
                        if (el.type == ProfileElType.Avatar || el.type == ProfileElType.Name) {
                            return@ProfileElementView
                        }
                        onLayoutChangeLatest.value(
                            layoutLatest.value.filterNot { it.id == el.id }
                        )
                        onSelectLatest.value(null)
                    },
                    onEdit = { onEdit(el.id) },
                    onTipGlass = if (el.type == ProfileElType.Glass) onTipGlass else null,
                    onPetKraul = if (el.type == ProfileElType.Avatar || el.type == ProfileElType.Pet) {
                        onPetKraul
                    } else null,
                    onOpenSpouse = if (el.type == ProfileElType.Spouse) onOpenSpouse else null
                )
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
                Text("🎒", fontSize = 22.sp)
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
    onPetKraul: (() -> Unit)? = null,
    onOpenSpouse: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var dragEl by remember(el.id) { mutableStateOf(el) }
    LaunchedEffect(el) { dragEl = el }
    val onChangeLatest = rememberUpdatedState(onChange)
    val onSelectLatest = rememberUpdatedState(onSelect)
    val onRemoveLatest = rememberUpdatedState(onRemove)

    val nameLabel = el.text?.takeIf { it.isNotBlank() } ?: nickname
    val nameFont = when (el.fontFamily) {
        ProfileFont.Playful -> DisplayFont
        ProfileFont.Classic -> FontFamily.Serif
        else -> BodyFont
    }
    // Größen relativ zur Board-Kurzseite — Phone/Tablet/Orientierung gleich
    val factor = ProfileCatalog.boardFactor(boardW, boardH, density.density)
    val nameSp = ((el.fontSize ?: 18f).coerceIn(8f, 28f) * factor)
    val baseSquare = ProfileCatalog.baseSizePx(el.type, boardW, boardH)
    val namePad = ProfileCatalog.padPx(boardW, boardH, 20f)
    val nameMinW = ProfileCatalog.padPx(boardW, boardH, 48f)
    val baseW = when (el.type) {
        ProfileElType.Name -> {
            val measured = textMeasurer.measure(
                text = nameLabel,
                style = TextStyle(fontFamily = nameFont, fontSize = nameSp.sp),
                maxLines = 1,
                softWrap = false
            ).size.width.toFloat()
            (measured + namePad).coerceIn(nameMinW, boardW * 0.92f)
        }
        else -> baseSquare
    }
    val baseH = baseSquare
    val nameFontPx = with(density) { nameSp.sp.toPx() }
    val dash = ProfileCatalog.padPx(boardW, boardH, 8f)
    val gap = ProfileCatalog.padPx(boardW, boardH, 5f)
    val strokeW = ProfileCatalog.padPx(boardW, boardH, 2f).coerceAtLeast(1f)
    val canEdit = el.type in ProfileCatalog.EDITABLE_TYPES
    val canRemove = el.type != ProfileElType.Avatar && el.type != ProfileElType.Name
    val invScale = 1f / dragEl.scale.coerceIn(
        ProfileCatalog.ELEMENT_SCALE_MIN,
        ProfileCatalog.ELEMENT_SCALE_MAX
    )

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
                        var origX = dragEl.x
                        var origY = dragEl.y
                        var accX = 0f
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = {
                                onSelectLatest.value()
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
                                    nameText = nickname,
                                    nameFontPx = nameFontPx
                                )
                                val next = dragEl.copy(x = cx, y = cy)
                                dragEl = next
                                onChangeLatest.value(next)
                            }
                        )
                    }
                } else Modifier
            )
            .graphicsLayer {
                val s = dragEl.scale
                rotationZ = dragEl.rotation
                // flipX nur am Inhalt — Handles/Icons bleiben lesbar
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clickable(
                enabled = editable ||
                    (el.type == ProfileElType.Glass && onTipGlass != null) ||
                    ((el.type == ProfileElType.Pet || el.type == ProfileElType.Avatar) &&
                        onPetKraul != null) ||
                    (el.type == ProfileElType.Spouse && onOpenSpouse != null)
            ) {
                when {
                    el.type == ProfileElType.Spouse && onOpenSpouse != null -> {
                        // Eigenes Profil (Edit): 1. Tippen = auswählen, 2. Tippen = Gästebuch
                        if (editable && !selected) onSelectLatest.value()
                        else onOpenSpouse.invoke()
                    }
                    editable -> onSelectLatest.value()
                    el.type == ProfileElType.Glass -> onTipGlass?.invoke()
                    el.type == ProfileElType.Pet || el.type == ProfileElType.Avatar ->
                        onPetKraul?.invoke()
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
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (dragEl.flipX) -1f else 1f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                contentAlignment = Alignment.Center
            ) {
                ElementContent(
                    el = dragEl,
                    nickname = nickname,
                    colorIndex = colorIndex,
                    coins = coins,
                    companionEmoji = companionEmoji,
                    boardFactor = factor
                )
            }
        }

        if (selected && editable) {
            // Handles gegen Parent-Scale gegenrechnen — ohne Flip
            val handleMod = Modifier.graphicsLayer {
                scaleX = invScale
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
                            onChangeLatest.value(next)
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
                        .clickable { onRemoveLatest.value() },
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
                            onChangeLatest.value(next)
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
                                scale = (dragEl.scale + drag.x * 0.01f * invScale)
                                    .coerceIn(
                                        ProfileCatalog.ELEMENT_SCALE_MIN,
                                        ProfileCatalog.ELEMENT_SCALE_MAX
                                    )
                            )
                            dragEl = next
                            onChangeLatest.value(next)
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
    companionEmoji: String = "🐣",
    boardFactor: Float = 1f
) {
    val font = when (el.fontFamily) {
        ProfileFont.Playful -> DisplayFont
        ProfileFont.Classic -> FontFamily.Serif
        else -> BodyFont
    }
    val color = ProfileCatalog.parseColor(el.color)
    val f = boardFactor.coerceIn(0.35f, 2.8f)
    fun sp(ref: Float) = (ref * f).sp
    val sizeSp = sp(
        el.fontSize ?: when (el.type) {
            ProfileElType.Name -> 18f
            ProfileElType.Bio -> 12f
            ProfileElType.Glass -> 11f
            else -> 14f
        }
    )
    val padH = (6f * f).dp

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
                    .border((2f * f).dp, Color.White.copy(0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                com.luv.couple.ui.CompanionGlyph(petId = pet, fontSize = sp(28f))
            }
        }
        ProfileElType.Name -> {
            val label = el.text ?: nickname
            Text(
                label,
                color = color,
                fontFamily = font,
                fontSize = sizeSp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                softWrap = false
            )
        }
        ProfileElType.Status -> Text(el.emoji ?: el.text ?: "😊", fontSize = sp(30f))
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
                    modifier = Modifier.padding(horizontal = padH)
                )
            }
        }
        ProfileElType.Pet -> {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.18f))
                    .border((1f * f).dp, Color.White.copy(0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(el.emoji ?: "💕", fontSize = sp(32f))
            }
        }
        ProfileElType.Glass -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏺", fontSize = sp(28f))
                Text(
                    "$coins",
                    color = color,
                    fontFamily = font,
                    fontSize = sizeSp,
                    maxLines = 1
                )
                Text(
                    "Coins",
                    color = color.copy(0.85f),
                    fontFamily = BodyFont,
                    fontSize = sp(9f)
                )
            }
        }
        ProfileElType.Spouse -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💍", fontSize = sp(26f))
                Text(
                    el.text ?: "Ehepartner",
                    color = Color(0xFFFFD54F),
                    fontFamily = font,
                    fontSize = sizeSp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        ProfileElType.Engaged -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💝", fontSize = sp(26f))
                Text(
                    el.text ?: "Verlobte:r",
                    color = AccentRose,
                    fontFamily = font,
                    fontSize = sizeSp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        ProfileElType.Sticker -> Text(el.emoji ?: "✨", fontSize = sp(34f))
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

private val WeddingIvory = Color(0xFFFFF6F0)
private val WeddingBlush = Color(0xFFFFE4EC)
private val WeddingRose = Color(0xFFE85A7A)
private val WeddingGold = Color(0xFFC9A24A)
private val WeddingInk = Color(0xFF4A2C35)

@Composable
private fun WeddingGuestbookDialog(
    profileUserId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wedding by remember { mutableStateOf<LuvApiClient.WeddingInfo?>(null) }
    var weddingBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showGuestbook by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(profileUserId) {
        loading = true
        wedding = LuvApiClient.fetchWedding(profileUserId)
        weddingBmp = null
        if (wedding?.hasImage == true) {
            weddingBmp = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = LuvApiClient.downloadWeddingImage(profileUserId)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        }
        loading = false
    }

    Dialog(
        onDismissRequest = {
            if (showGuestbook) showGuestbook = false else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.55f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (showGuestbook) showGuestbook = false else onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            WeddingPetalRain(modifier = Modifier.fillMaxSize())

            if (!showGuestbook) {
                WeddingOverviewCard(
                    loading = loading,
                    wedding = wedding,
                    weddingBmp = weddingBmp,
                    onOpenGuestbook = { showGuestbook = true },
                    onClose = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { }
                )
            } else {
                WeddingGuestbookBookCard(
                    wedding = wedding,
                    draft = draft,
                    onDraftChange = { draft = it.take(280) },
                    busy = busy,
                    onBack = { showGuestbook = false },
                    onClose = onDismiss,
                    onSubmit = {
                        if (draft.isBlank() || busy) return@WeddingGuestbookBookCard
                        busy = true
                        scope.launch {
                            runCatching { LuvApiClient.postGuestbook(profileUserId, draft) }
                                .onSuccess {
                                    draft = ""
                                    wedding = LuvApiClient.fetchWedding(profileUserId)
                                    Toast.makeText(context, "Eintrag gesendet 💕", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Senden fehlgeschlagen",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            busy = false
                        }
                    },
                    onDelete = { entryId ->
                        scope.launch {
                            runCatching { LuvApiClient.deleteGuestbook(profileUserId, entryId) }
                                .onSuccess { wedding = LuvApiClient.fetchWedding(profileUserId) }
                        }
                    },
                    onReport = { entryId ->
                        scope.launch {
                            runCatching {
                                LuvApiClient.reportGuestbook(
                                    profileUserId,
                                    entryId,
                                    "Gästebuch-Meldung"
                                )
                            }.onSuccess {
                                Toast.makeText(context, "Gemeldet", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.78f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { }
                )
            }
        }
    }
}

@Composable
private fun WeddingOverviewCard(
    loading: Boolean,
    wedding: LuvApiClient.WeddingInfo?,
    weddingBmp: android.graphics.Bitmap?,
    onOpenGuestbook: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val a = wedding?.coupleA
    val b = wedding?.coupleB
    val partner = wedding?.marriage
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(listOf(WeddingIvory, WeddingBlush, Color(0xFFFFF0F5)))
            )
            .border(1.5.dp, WeddingGold.copy(0.45f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("💒", fontSize = 28.sp)
        Text(
            "Hochzeit",
            color = WeddingInk,
            fontFamily = DisplayFont,
            fontSize = 22.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(a?.petEmoji ?: "💍", fontSize = 20.sp)
            Text(
                a?.nickname ?: partner?.partnerNickname ?: "…",
                color = WeddingInk,
                fontFamily = DisplayFont,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false
            )
            Text("💍", fontSize = 18.sp)
            Text(
                b?.nickname ?: "…",
                color = WeddingInk,
                fontFamily = DisplayFont,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false
            )
            Text(b?.petEmoji ?: "💕", fontSize = 20.sp)
        }

        val weddingAr = weddingBmp?.let { bmp ->
            bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
        } ?: 1f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(weddingAr.coerceIn(0.72f, 1.35f))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(0.55f))
                .border(1.dp, WeddingRose.copy(0.25f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                weddingBmp != null -> {
                    Image(
                        bitmap = weddingBmp.asImageBitmap(),
                        contentDescription = "Hochzeitsleinwand",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                loading -> Text("Lade…", color = WeddingInk.copy(0.5f), fontFamily = BodyFont)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖼️", fontSize = 36.sp)
                    Text(
                        "Noch kein Hochzeitsbild",
                        color = WeddingInk.copy(0.55f),
                        fontFamily = BodyFont,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(
                    Brush.horizontalGradient(listOf(WeddingRose, Color(0xFFFF8FA3)))
                )
                .clickable(onClick = onOpenGuestbook),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "📖  Gästebuch",
                color = Color.White,
                fontFamily = DisplayFont,
                fontSize = 17.sp
            )
        }
        Text(
            "Schließen",
            color = WeddingInk.copy(0.55f),
            fontFamily = BodyFont,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(6.dp)
        )
    }
}

@Composable
private fun WeddingGuestbookBookCard(
    wedding: LuvApiClient.WeddingInfo?,
    draft: String,
    onDraftChange: (String) -> Unit,
    busy: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    onDelete: (String) -> Unit,
    onReport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val a = wedding?.coupleA
    val b = wedding?.coupleB
    val entries = wedding?.guestbook.orEmpty()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFFFBF7), WeddingBlush, WeddingIvory))
            )
            .border(1.5.dp, WeddingGold.copy(0.5f), RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹ Zurück",
                color = WeddingRose,
                fontFamily = DisplayFont,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "✕",
                color = WeddingInk.copy(0.45f),
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(8.dp)
            )
        }
        Text(
            "Gästebuch",
            color = WeddingInk,
            fontFamily = DisplayFont,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(0.55f))
                .border(1.dp, WeddingGold.copy(0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(a?.petEmoji ?: "💍", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                a?.nickname ?: "…",
                color = WeddingInk,
                fontFamily = DisplayFont,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false
            )
            Text("  💕  ", fontSize = 14.sp)
            Text(
                b?.nickname ?: "…",
                color = WeddingInk,
                fontFamily = DisplayFont,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(b?.petEmoji ?: "💍", fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(0.4f))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entries.isEmpty()) {
                Text(
                    "Noch keine Einträge — sei der Erste und hinterlasse einen Wunsch 🌹",
                    color = WeddingInk.copy(0.55f),
                    fontFamily = BodyFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            } else {
                entries.asReversed().forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(0.75f))
                            .border(1.dp, WeddingRose.copy(0.18f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌸", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                entry.nickname,
                                color = WeddingInk,
                                fontFamily = DisplayFont,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            entry.text,
                            color = WeddingInk.copy(0.82f),
                            fontFamily = BodyFont,
                            fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (wedding?.canDeleteComments == true) {
                                Text(
                                    "Löschen",
                                    color = WeddingRose,
                                    fontFamily = BodyFont,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable { onDelete(entry.id) }
                                        .padding(top = 6.dp)
                                )
                            } else {
                                Text(
                                    "Melden",
                                    color = WeddingInk.copy(0.45f),
                                    fontFamily = BodyFont,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable { onReport(entry.id) }
                                        .padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Dein Eintrag",
            color = WeddingInk.copy(0.65f),
            fontFamily = BodyFont,
            fontSize = 12.sp
        )
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            textStyle = TextStyle(
                color = WeddingInk,
                fontFamily = BodyFont,
                fontSize = 15.sp
            ),
            cursorBrush = SolidColor(WeddingRose),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp, max = 88.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.8f))
                .border(1.dp, WeddingGold.copy(0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
        Text(
            "${draft.length}/280",
            color = WeddingInk.copy(0.4f),
            fontFamily = BodyFont,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (!busy && draft.isNotBlank()) {
                        Brush.horizontalGradient(listOf(WeddingRose, Color(0xFFFF8FA3)))
                    } else {
                        Brush.horizontalGradient(
                            listOf(Color.Gray.copy(0.35f), Color.Gray.copy(0.25f))
                        )
                    }
                )
                .clickable(enabled = !busy && draft.isNotBlank(), onClick = onSubmit),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (busy) "…" else "Absenden 💌",
                color = Color.White,
                fontFamily = DisplayFont,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun WeddingPetalRain(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "weddingRain")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weddingRainPhase"
    )
    val petals = remember {
        List(24) { i ->
            WeddingPetal(
                x = ((i * 37) % 100) / 100f,
                speed = 0.55f + (i % 5) * 0.1f,
                size = 14f + (i % 4) * 4f,
                symbol = if (i % 3 == 0) "🌹" else "❤",
                drift = if (i % 2 == 0) 0.05f else -0.04f,
                delay = (i * 0.07f) % 1f
            )
        }
    }
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        petals.forEach { petal ->
            val t = (phase + petal.delay) % 1f
            val yFrac = -0.1f + t * 1.25f
            val xFrac = (petal.x +
                kotlin.math.sin((phase + petal.delay) * Math.PI * 2).toFloat() * petal.drift)
                .coerceIn(0.02f, 0.95f)
            val alpha = when {
                t < 0.06f -> t / 0.06f
                t > 0.88f -> ((1f - t) / 0.12f).coerceIn(0f, 1f)
                else -> 0.8f
            }
            Text(
                petal.symbol,
                fontSize = petal.size.sp,
                modifier = Modifier
                    .offset(x = w * xFrac, y = h * yFrac * petal.speed.coerceIn(0.7f, 1.2f))
                    .graphicsLayer {
                        this.alpha = alpha
                        rotationZ = (phase + petal.delay) * 50f *
                            if (petal.drift > 0) 1f else -1f
                    }
            )
        }
    }
}

private data class WeddingPetal(
    val x: Float,
    val speed: Float,
    val size: Float,
    val symbol: String,
    val drift: Float,
    val delay: Float
)
