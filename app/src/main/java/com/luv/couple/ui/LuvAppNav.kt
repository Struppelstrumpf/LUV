package com.luv.couple.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luv.couple.LuvApp
import com.luv.couple.data.Lobby
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Role
import com.luv.couple.data.RoomPreview
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.LockDrawActivity
import com.luv.couple.lock.LockScreenWidgetProvider
import com.luv.couple.net.AccountSession
import com.luv.couple.net.GoogleAuth
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairEvent
import com.luv.couple.net.PairSessionState
import com.luv.couple.net.PendingJoin
import com.luv.couple.net.PendingShop
import com.luv.couple.net.PendingShopReturn
import com.luv.couple.net.PendingSplashSkip
import com.luv.couple.net.PublicReportInfo
import com.luv.couple.net.ShopPack
import com.luv.couple.net.VoucherInfo
import com.luv.couple.ui.NoCoinsUi
import com.luv.couple.ui.PublicCanvasSplash
import com.luv.couple.ui.screens.AccountHomeScreen
import com.luv.couple.ui.screens.AdminHubScreen
import com.luv.couple.ui.screens.LiveNoticePopup
import com.luv.couple.net.LiveNoticeBus
import com.luv.couple.ui.screens.CreateLobbyScreen
import com.luv.couple.ui.screens.ForcedUpdateDialog
import com.luv.couple.ui.screens.HostShareScreen
import com.luv.couple.ui.screens.InviteLobbyDialog
import com.luv.couple.ui.screens.JoinPreviewScreen
import com.luv.couple.ui.screens.JoinScreen
import com.luv.couple.ui.screens.EmojiBarEditorDialog
import com.luv.couple.ui.screens.InventoryScreen
import com.luv.couple.ui.screens.LobbiesScreen
import com.luv.couple.ui.screens.MarketPanel
import com.luv.couple.ui.screens.MarketReturnTo
import com.luv.couple.ui.screens.MarketScreen
import com.luv.couple.ui.screens.NicknameScreen
import com.luv.couple.ui.screens.ProfileCanvasScreen
import com.luv.couple.ui.screens.QuietHoursScreen
import com.luv.couple.ui.screens.SettingsScreen
import com.luv.couple.ui.screens.RedeemScreen
import com.luv.couple.ui.screens.RenameLobbyScreen
import com.luv.couple.ui.screens.SimpleBottomBar
import com.luv.couple.ui.screens.SocialScreen
import com.luv.couple.ui.screens.TutorialFlow
import com.luv.couple.update.AppUpdater
import com.luv.couple.update.UpdateUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

object Routes {
    const val TUTORIAL = "tutorial"
    const val MAIN = "main"
    const val CREATE = "create"
    const val HOST_SHARE = "host_share"
    const val JOIN = "join"
    const val JOIN_PREVIEW = "join_preview"
    const val REDEEM = "redeem"
    const val ADMIN = "admin"
    const val NICKNAME = "nickname"
    const val PROFILE = "profile"
    const val PEER_PROFILE = "peer_profile/{userId}"
    fun peerProfile(userId: String) = "peer_profile/$userId"
    const val SETTINGS = "settings"
    const val QUIET_HOURS = "quiet_hours"
    const val RENAME = "rename/{lobbyId}"
    fun rename(lobbyId: String) = "rename/$lobbyId"
}

@Composable
fun LuvAppNav() {
    val context = LocalContext.current
    val prefs = LuvApp.instance.prefs
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val nickname by prefs.nicknameFlow.collectAsStateWithLifecycle(initialValue = null)
    val lobbies by prefs.lobbiesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeLobbyId by prefs.activeLobbyIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val lobbyStates by PairConnectionService.lobbyStates.collectAsStateWithLifecycle()
    val reconnectUi by PairConnectionService.reconnectUi.collectAsStateWithLifecycle()
    val account by AccountSession.account.collectAsStateWithLifecycle()
    val pendingJoin by PendingJoin.code.collectAsStateWithLifecycle()
    val pendingShopReturn by PendingShopReturn.pending.collectAsStateWithLifecycle()
    val pendingShop by PendingShop.open.collectAsStateWithLifecycle()
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()
    val focusUpdate by AppUpdater.focusRequest.collectAsStateWithLifecycle()
    var googleEnabled by remember { mutableStateOf(false) }
    var googleBusy by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf<String?>(null) }
    var shareLobby by remember { mutableStateOf<Lobby?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var joinPreview by remember { mutableStateOf<RoomPreview?>(null) }
    var joinPreviewLoading by remember { mutableStateOf(false) }
    var joinPreviewCode by remember { mutableStateOf<String?>(null) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var openMarketCoinShop by remember { mutableStateOf(false) }
    var openMarketPanel by remember { mutableStateOf<MarketPanel?>(null) }
    var marketReturnTo by remember { mutableStateOf<MarketReturnTo>(MarketReturnTo.None) }
    var inventorySubTab by remember { mutableIntStateOf(0) }
    var reopenProfileChest by remember { mutableStateOf(false) }
    var profileChestTab by remember { mutableIntStateOf(0) }
    var showEmojiBarEditor by remember { mutableStateOf(false) }
    var shopEnabled by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf<List<ShopPack>>(emptyList()) }
    var vouchers by remember { mutableStateOf<List<VoucherInfo>>(emptyList()) }
    var publicReports by remember { mutableStateOf<List<PublicReportInfo>>(emptyList()) }
    var showNoCoins by remember { mutableStateOf(false) }
    var inviteLobby by remember { mutableStateOf<Lobby?>(null) }
    var showPublicSplash by remember { mutableStateOf(false) }
    var tutorialReplay by remember { mutableStateOf(false) }
    fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Einladung teilen").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }.onFailure {
            Toast.makeText(context, "Teilen gerade nicht möglich", Toast.LENGTH_SHORT).show()
        }
    }

    fun inviteMessage(lobby: Lobby): String {
        val who = nickname?.takeIf { it.isNotBlank() }
            ?: lobby.hostNickname.takeIf { it.isNotBlank() }
            ?: "Jemand"
        // Titelzeile fest — der cozy Spruch kommt in der Link-Vorschau (OG) random
        return "$who will mit dir verbunden sein\n${lobby.joinUrl}"
    }

    fun openJoinPreview(code: String) {
        joinPreviewCode = code
        joinPreview = null
        joinPreviewLoading = true
        joinError = null
        navController.navigate(Routes.JOIN_PREVIEW) {
            launchSingleTop = true
        }
        scope.launch {
            try {
                joinPreview = LuvApiClient.roomPreview(code)
            } catch (e: Exception) {
                joinError = e.message ?: "Lobby nicht gefunden."
                joinPreview = null
            } finally {
                joinPreviewLoading = false
            }
        }
    }

    suspend fun ensureAuth(nick: String): Boolean {
        return try {
            val (id, secret) = prefs.ensureInstallCredentials()
            val result = LuvApiClient.authDevice(id, secret, nick)
            prefs.saveSession(result.sessionToken, result.user)
            AccountSession.setAccount(result.user)
            colorIndex = prefs.snapshot().colorIndex
            CanvasStore.updateProfile(result.user.nickname, colorIndex)
            true
        } catch (e: Exception) {
            joinError = e.message ?: "Server nicht erreichbar"
            false
        }
    }

    suspend fun refreshAccount() {
        runCatching {
            val user = LuvApiClient.me()
            prefs.updateAccount(user)
            AccountSession.setAccount(user)
            colorIndex = prefs.snapshot().colorIndex
            CanvasStore.updateProfile(user.nickname, colorIndex)
            if (user.dailyGrantedJustNow) {
                val day = user.lastDailyGrantDate
                    ?: java.time.LocalDate.now().toString()
                com.luv.couple.notify.LuvAlertNotifier.onDailyCoins(
                    context,
                    amount = user.dailyCoins,
                    dayKey = day
                )
            }
            val (enabled, list) = LuvApiClient.shopPacks()
            shopEnabled = enabled
            packs = list
        }
    }

    fun isChosenNickname(nick: String?): Boolean {
        val n = nick?.trim().orEmpty()
        return n.length >= 2 && !n.equals("Luv", ignoreCase = true)
    }

    suspend fun syncInventory() {
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

    var lastCloudSyncAt by remember { mutableStateOf(0L) }

    /**
     * Google-Konto: Lobbys (Host+Join), Einstellungen, Inventar und Profil
     * von der Cloud auf dieses Gerät spiegeln.
     */
    suspend fun syncCloudAccount(
        hostedHint: List<com.luv.couple.net.RemoteLobby> = emptyList(),
        joinedHint: List<com.luv.couple.net.RemoteLobby> = emptyList(),
        settingsHint: com.luv.couple.net.CloudSettings? = null,
        force: Boolean = false
    ) {
        if (LuvApiClient.sessionToken.isNullOrBlank()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastCloudSyncAt < 20_000L) return
        lastCloudSyncAt = now
        val bundle = runCatching { LuvApiClient.myLobbyBundle() }.getOrNull()
        val hosted = hostedHint.ifEmpty { bundle?.hosted.orEmpty() }
        val joined = joinedHint.ifEmpty { bundle?.joined.orEmpty() }
        if (hosted.isNotEmpty() || joined.isNotEmpty() || bundle != null) {
            prefs.replaceCloudLobbiesFromRemote(
                hosted = hosted,
                joined = joined,
                dropUnknownJoins = true
            )
            CanvasStore.updateKnownLobbies(prefs.snapshot().lobbies.map { it.id })
            if (prefs.snapshot().hasLobbies) {
                CanvasStore.setActiveLobby(prefs.snapshot().activeLobbyId)
                PairConnectionService.startAll(context)
            }
        }
        val remoteSettings = settingsHint
            ?: runCatching { LuvApiClient.fetchSettings() }.getOrNull()
        if (remoteSettings != null) {
            val localQuiet = prefs.quietHours()
            val serverEmpty = remoteSettings.updatedAt <= 0L &&
                remoteSettings.quietHours.byDay.isEmpty()
            if (serverEmpty && localQuiet.byDay.isNotEmpty()) {
                runCatching { LuvApiClient.putSettings(prefs.buildCloudSettings()) }
            } else {
                prefs.applySettingsBag(remoteSettings)
            }
        }
        syncInventory()
        runCatching {
            val (_, state) = LuvApiClient.fetchMyProfileCanvas()
            prefs.setProfileCanvasJson(
                com.luv.couple.profile.ProfileCatalog.encode(state)
            )
        }
    }

    suspend fun applyAuthResult(
        result: com.luv.couple.net.AuthResult,
        fromGoogle: Boolean,
        finishOnboarding: Boolean = true,
        applyNickname: Boolean = true
    ) {
        prefs.saveSession(result.sessionToken, result.user, applyNickname = applyNickname)
        AccountSession.setAccount(result.user)
        if (applyNickname) {
            prefs.setNickname(result.user.nickname)
        } else {
            prefs.clearNickname()
        }
        if (finishOnboarding) {
            prefs.setTutorialDone(true)
        }
        if (fromGoogle) {
            syncCloudAccount(
                hostedHint = result.remoteLobbies,
                joinedHint = result.joinedLobbies,
                settingsHint = result.settings,
                force = true
            )
        } else if (result.remoteLobbies.isNotEmpty()) {
            prefs.replaceHostedLobbiesFromRemote(result.remoteLobbies)
        }
        colorIndex = prefs.snapshot().colorIndex
        val profileNick = if (applyNickname) result.user.nickname else (prefs.snapshot().nickname ?: "")
        CanvasStore.updateProfile(profileNick, colorIndex)
        CanvasStore.updateKnownLobbies(prefs.snapshot().lobbies.map { it.id })
        if (prefs.snapshot().hasLobbies) {
            CanvasStore.setActiveLobby(prefs.snapshot().activeLobbyId)
            PairConnectionService.startAll(context)
        }
        if (finishOnboarding) {
            refreshAccount()
        }
    }

    fun connectGoogle() {
        if (googleBusy) return
        scope.launch {
            googleBusy = true
            busy = true
            joinError = null
            accountMessage = null
            try {
                val google = GoogleAuth.signIn(context)
                val result = LuvApiClient.authGoogle(google.idToken)
                val inTutorial = navController.currentDestination?.route == Routes.TUTORIAL
                val returning =
                    !result.created && isChosenNickname(result.user.nickname)
                if (inTutorial && !returning) {
                    // Erstes Mal: Session sichern, Name selbst wählen, Tutorial weiter.
                    applyAuthResult(
                        result,
                        fromGoogle = true,
                        finishOnboarding = false,
                        applyNickname = false
                    )
                    accountMessage = "Angemeldet — wie sollen wir dich nennen?"
                    Toast.makeText(context, "Angemeldet", Toast.LENGTH_SHORT).show()
                } else {
                    applyAuthResult(result, fromGoogle = true)
                    accountMessage = if (result.linked) {
                        "Mit Google gesichert — Coins bleiben erhalten."
                    } else {
                        "Angemeldet. Willkommen zurück."
                    }
                    Toast.makeText(context, accountMessage, Toast.LENGTH_SHORT).show()
                    if (inTutorial) {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.TUTORIAL) { inclusive = true }
                        }
                    } else {
                        tab = 1
                    }
                }
            } catch (e: Exception) {
                if (e is LuvApiException && e.error == "cancelled") {
                    // still
                } else {
                    val msg = e.message ?: "Google-Anmeldung fehlgeschlagen."
                    joinError = msg
                    accountMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } finally {
                googleBusy = false
                busy = false
            }
        }
    }

    suspend fun clearLocalSession(toast: String) {
        runCatching { GoogleAuth.signOut(context) }
        PairConnectionService.stop(context)
        PairSessionState.reset()
        CanvasStore.clearAll(notifyPeer = false)
        prefs.clearForLogout()
        AccountSession.setAccount(null)
        LuvApiClient.sessionToken = null
        tab = 0
        accountMessage = null
        joinError = null
        navController.navigate(Routes.TUTORIAL) {
            popUpTo(0) { inclusive = true }
        }
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    fun logoutAccount() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                runCatching { LuvApiClient.logout() }
                clearLocalSession("Abgemeldet")
            } finally {
                busy = false
            }
        }
    }

    fun deleteAccountCompletely() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                LuvApiClient.deleteAccount()
                clearLocalSession("Konto gelöscht — du startest neu")
            } catch (e: Exception) {
                val msg = e.message ?: "Löschen fehlgeschlagen."
                accountMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    suspend fun joinWithCode(raw: String): Boolean {
        if (busy) return false
        busy = true
        joinError = null
        return try {
            val snap = prefs.snapshot()
            if (snap.lobbies.size >= PeerPalette.MAX_LOBBIES) {
                joinError = "Maximal ${PeerPalette.MAX_LOBBIES} Lobbys."
                false
            } else if (snap.lobbies.any {
                    it.code.equals(LuvApiClient.normalizeCode(raw).orEmpty(), ignoreCase = true)
                }
            ) {
                joinError = "Du bist schon in dieser Lobby."
                false
            } else {
                val room = LuvApiClient.joinRoom(raw)
                room.suggestedColorIndex?.let { suggested ->
                    prefs.setColorIndex(suggested)
                    colorIndex = suggested
                    CanvasStore.updateProfile(snap.nickname.orEmpty(), suggested)
                }
                val lobby = Lobby(
                    id = UUID.randomUUID().toString(),
                    name = room.name.ifBlank { "Lobby" },
                    role = Role.JOIN,
                    code = room.code,
                    token = room.token,
                    invite = room.invite,
                    capacity = room.capacity,
                    isFree = room.isFree,
                    hostNickname = room.hostNickname,
                    hostColorSide = room.hostColorSide
                )
                prefs.upsertLobby(lobby)
                PairSessionState.setCapacity(lobby.id, room.capacity)
                CanvasStore.setActiveLobby(lobby.id)
                PairConnectionService.startAll(context)
                LockScreenWidgetProvider.requestUpdate(context)
                refreshAccount()
                true
            }
        } catch (e: Exception) {
            joinError = when (e) {
                is LuvApiException -> e.message
                else -> "Beitreten fehlgeschlagen."
            }
            false
        } finally {
            busy = false
        }
    }

    fun inviteSeat(lobby: Lobby) {
        inviteLobby = lobby
    }

    fun startAppUpdate() {
        scope.launch {
            val ready = updateState as? UpdateUiState.Ready
            if (ready != null) {
                AppUpdater.installApkFile(context, ready.file)
            } else {
                AppUpdater.downloadAndInstall(context)
            }
        }
    }

    fun openShopTab() {
        tab = 3 // Markt
        openMarketCoinShop = true
        scope.launch { refreshAccount() }
    }

    fun buySeat(lobby: Lobby) {
        if (busy) return
        if ((account?.coins ?: 0) < PeerPalette.SLOT_COST) {
            showNoCoins = true
            return
        }
        scope.launch {
            busy = true
            joinError = null
            try {
                val room = LuvApiClient.buySlot(lobby.code)
                AccountSession.account.value?.let { prefs.updateAccount(it) }
                val updated = lobby.copy(
                    capacity = room.capacity,
                    isFree = room.isFree,
                    invite = room.invite.ifBlank { lobby.invite }
                )
                prefs.upsertLobby(updated)
                if (shareLobby?.id == lobby.id) shareLobby = updated
                PairSessionState.setCapacity(lobby.id, room.capacity)
                refreshAccount()
                Toast.makeText(
                    context,
                    "Platz freigeschaltet (−${PeerPalette.SLOT_COST} Coins)",
                    Toast.LENGTH_SHORT
                ).show()
                inviteSeat(updated)
            } catch (e: Exception) {
                if (e is LuvApiException && e.isNoCoins) {
                    showNoCoins = true
                } else {
                    joinError = e.message
                    Toast.makeText(context, e.message ?: "Kauf fehlgeschlagen", Toast.LENGTH_LONG).show()
                }
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        AccountSession.economyBlocks.collect {
            if (it.contains("coin", ignoreCase = true) || it.contains("Coin")) {
                showNoCoins = true
            } else {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
            refreshAccount()
        }
    }

    LaunchedEffect(Unit) {
        PairConnectionService.events.collect { event ->
            if (event is PairEvent.LobbyGone) {
                // Nicht mehr: „neu erstellen“. Lobby wird serverseitig wiederhergestellt.
                Toast.makeText(
                    context,
                    "„${event.name}“ wird wieder verbunden…",
                    Toast.LENGTH_SHORT
                ).show()
                PairConnectionService.reconnectNow(context, event.lobbyId)
            }
        }
    }

    LaunchedEffect(Unit) {
        val snapshot = prefs.snapshot()
        colorIndex = snapshot.colorIndex
        LuvApiClient.sessionToken = snapshot.sessionToken
        snapshot.account?.let { AccountSession.setAccount(it) }
        CanvasStore.updateProfile(snapshot.nickname, snapshot.colorIndex)
        CanvasStore.updateKnownLobbies(snapshot.lobbies.map { it.id })
        scope.launch {
            googleEnabled = runCatching { LuvApiClient.authConfig().googleEnabled }.getOrDefault(false)
        }
        startDestination = if (!snapshot.tutorialDone || !snapshot.hasNickname || snapshot.sessionToken.isNullOrBlank()) {
            Routes.TUTORIAL
        } else {
            if (snapshot.hasLobbies) {
                CanvasStore.setActiveLobby(snapshot.activeLobbyId)
                PairConnectionService.startAll(context)
            }
            scope.launch {
                runCatching { LuvApiClient.claimDaily(); refreshAccount() }
                if (snapshot.account?.googleLinked == true) {
                    runCatching { syncCloudAccount() }
                }
            }
            Routes.MAIN
        }
    }

    // Bei jedem App-Start / Zurück aus dem Hintergrund auf Pflicht-Update prüfen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            val notify = first
            first = false
            scope.launch {
                runCatching { AppUpdater.check(context, notify = notify) }
                runCatching { refreshAccount() }
                if (AccountSession.account.value?.googleLinked == true) {
                    runCatching { syncCloudAccount() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(focusUpdate) {
        if (!AppUpdater.consumeFocus()) return@LaunchedEffect
        if (updateState is UpdateUiState.Available || updateState is UpdateUiState.Ready) {
            startAppUpdate()
        } else {
            runCatching { AppUpdater.check(context, notify = false) }
        }
    }

    ForcedUpdateDialog(
        state = updateState,
        onUpdate = { startAppUpdate() }
    )

    LaunchedEffect(startDestination, nickname, pendingJoin) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (nickname.isNullOrBlank()) return@LaunchedEffect
        val code = PendingJoin.consume() ?: return@LaunchedEffect
        tab = 0
        openJoinPreview(code)
    }

    LaunchedEffect(startDestination, pendingShopReturn) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (!PendingShopReturn.consume()) return@LaunchedEffect
        tab = 3
        openMarketCoinShop = true
        refreshAccount()
        // Webhook kann kurz brauchen
        kotlinx.coroutines.delay(1200)
        refreshAccount()
        accountMessage = "Willkommen zurück — Coins sind aktualisiert ♥"
        Toast.makeText(context, "Zurück in LUV", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(pendingShop, startDestination) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (!PendingShop.consume()) return@LaunchedEffect
        openShopTab()
    }

    LaunchedEffect(startDestination) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        kotlinx.coroutines.delay(1800)
        com.luv.couple.lock.CanvasMemoryKeeper.checkAndNotify(context.applicationContext)
    }

    LaunchedEffect(startDestination) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (PendingSplashSkip.consume()) return@LaunchedEffect
        if (!PendingJoin.peek().isNullOrBlank()) return@LaunchedEffect
        showPublicSplash = true
    }

    NoCoinsUi.Dialog(
        visible = showNoCoins,
        onDismiss = { showNoCoins = false },
        onOpenShop = { openShopTab() }
    )

    inviteLobby?.let { lobby ->
        InviteLobbyDialog(
            lobby = lobby,
            onShare = {
                shareText(inviteMessage(lobby))
                inviteLobby = null
            },
            onOpen = {
                inviteLobby = null
                CanvasStore.setActiveLobby(lobby.id)
                CanvasStore.updateKnownLobbies(lobbies.map { it.id })
                context.startActivity(
                    Intent(context, LockDrawActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobby.id)
                    }
                )
                scope.launch {
                    prefs.setActiveLobby(lobby.id)
                    refreshAccount()
                }
            },
            onDismiss = { inviteLobby = null }
        )
    }

    val destination = startDestination ?: return

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = destination) {
        composable(Routes.TUTORIAL) {
            val googleReady =
                account?.googleLinked == true && !LuvApiClient.sessionToken.isNullOrBlank()
            val replaying = tutorialReplay
            TutorialFlow(
                busy = busy || googleBusy,
                error = joinError,
                googleEnabled = googleEnabled,
                googleSignedIn = googleReady,
                onGoogleSignIn = { connectGoogle() },
                replay = replaying,
                existingNickname = nickname.orEmpty(),
                onDismiss = if (replaying) {
                    {
                        tutorialReplay = false
                        joinError = null
                        navController.popBackStack()
                    }
                } else {
                    null
                },
                onFinished = { nick ->
                    scope.launch {
                        if (replaying) {
                            prefs.setTutorialDone(true)
                            tutorialReplay = false
                            joinError = null
                            navController.popBackStack()
                            return@launch
                        }
                        busy = true
                        joinError = null
                        try {
                            prefs.setNickname(nick)
                            val snap = prefs.snapshot()
                            val hasGoogleSession =
                                !snap.sessionToken.isNullOrBlank() &&
                                    (account?.googleLinked == true || snap.account?.googleLinked == true)
                            val ok = if (hasGoogleSession) {
                                val user = LuvApiClient.updateNickname(nick)
                                prefs.updateAccount(user)
                                AccountSession.setAccount(user)
                                colorIndex = prefs.snapshot().colorIndex
                                CanvasStore.updateProfile(user.nickname, colorIndex)
                                true
                            } else if (!googleEnabled) {
                                ensureAuth(nick)
                            } else {
                                joinError = "Bitte zuerst mit Google anmelden."
                                false
                            }
                            if (ok) {
                                prefs.setTutorialDone(true)
                                runCatching { LuvApiClient.claimDaily() }
                                refreshAccount()
                                navController.navigate(Routes.MAIN) {
                                    popUpTo(Routes.TUTORIAL) { inclusive = true }
                                }
                            }
                        } catch (e: Exception) {
                            joinError = e.message ?: "Speichern fehlgeschlagen."
                        } finally {
                            busy = false
                        }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        0 -> LobbiesScreen(
                            nickname = nickname ?: "Du",
                            colorIndex = colorIndex,
                            coins = account?.coins ?: 0,
                            freeLeft = account?.freeSessionsLeft ?: 0,
                            freeSessionsPerDay = account?.freeSessionsPerDay ?: 5,
                            canCreateFreeLobby = account?.canCreateFreeLobby != false,
                            lobbies = lobbies,
                            activeLobbyId = activeLobbyId,
                            lobbyStates = lobbyStates,
                            reconnectUi = reconnectUi,
                            error = joinError,
                            onOpenLobby = { lobby ->
                                CanvasStore.setActiveLobby(lobby.id)
                                CanvasStore.updateKnownLobbies(lobbies.map { it.id })
                                context.startActivity(
                                    Intent(context, LockDrawActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobby.id)
                                    }
                                )
                                scope.launch {
                                    prefs.setActiveLobby(lobby.id)
                                    refreshAccount()
                                }
                            },
                            onCreateLobby = {
                                joinError = null
                                navController.navigate(Routes.CREATE)
                            },
                            onJoinLobby = {
                                joinError = null
                                navController.navigate(Routes.JOIN)
                            },
                            onInviteSeat = { lobby -> inviteSeat(lobby) },
                            onBuySeat = { lobby -> buySeat(lobby) },
                            onRenameLobby = { lobby ->
                                navController.navigate(Routes.rename(lobby.id))
                            },
                            onLeaveLobby = { lobby ->
                                scope.launch {
                                    // Zuerst Leave-API (Host-Transfer), dann WS stoppen
                                    runCatching { LuvApiClient.leaveRoom(lobby.code) }
                                    PairConnectionService.stop(context, lobby.id)
                                    CanvasStore.clearLobby(lobby.id)
                                    prefs.removeLobby(lobby.id)
                                    LockScreenWidgetProvider.requestUpdate(context)
                                    Toast.makeText(context, "Lobby verlassen", Toast.LENGTH_SHORT).show()
                                    if (prefs.snapshot().lobbies.isEmpty()) {
                                        PairConnectionService.stop(context)
                                    }
                                    refreshAccount()
                                }
                            },
                            onReconnect = { lobby ->
                                PairConnectionService.reconnectNow(context, lobby.id)
                            },
                            onOpenProfile = { navController.navigate(Routes.PROFILE) },
                            updateState = updateState,
                            onUpdateApp = { startAppUpdate() }
                        )
                        1 -> SocialScreen(
                            onOpenFriendProfile = { userId, nick ->
                                navController.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("peer_nick", nick)
                                navController.navigate(Routes.peerProfile(userId))
                            }
                        )
                        2 -> InventoryScreen(
                            nickname = nickname ?: "Du",
                            selectedTab = inventorySubTab,
                            onTabChange = { inventorySubTab = it },
                            onOpenMarketplace = {
                                marketReturnTo = MarketReturnTo.Inventory(inventorySubTab)
                                tab = 3
                                openMarketPanel = MarketPanel.Marketplace
                            },
                            onOpenItemShop = {
                                marketReturnTo = MarketReturnTo.Inventory(inventorySubTab)
                                tab = 3
                                openMarketPanel = MarketPanel.ItemShop
                            },
                            onOpenProfileDesigner = { navController.navigate(Routes.PROFILE) }
                        )
                        3 -> MarketScreen(
                            shopEnabled = shopEnabled,
                            packs = packs,
                            startInCoinShop = openMarketCoinShop,
                            onStartInCoinShopConsumed = { openMarketCoinShop = false },
                            startPanel = openMarketPanel,
                            onStartPanelConsumed = { openMarketPanel = null },
                            onLeaveDeepLink = {
                                when (val ret = marketReturnTo) {
                                    is MarketReturnTo.Inventory -> {
                                        inventorySubTab = ret.subTab
                                        marketReturnTo = MarketReturnTo.None
                                        tab = 2
                                    }
                                    is MarketReturnTo.Profile -> {
                                        profileChestTab = ret.chestTab
                                        reopenProfileChest = true
                                        marketReturnTo = MarketReturnTo.None
                                        tab = 0
                                        navController.navigate(Routes.PROFILE)
                                    }
                                    MarketReturnTo.None -> Unit
                                }
                            },
                            onRefreshInventory = { syncInventory() },
                            onBuyPack = { pack ->
                                scope.launch {
                                    runCatching {
                                        val url = LuvApiClient.checkout(pack.id)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }.onFailure {
                                        accountMessage = it.message
                                        Toast.makeText(
                                            context,
                                            it.message ?: "Shop nicht erreichbar",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                        else -> AccountHomeScreen(
                            account = account,
                            colorIndex = colorIndex,
                            message = accountMessage,
                            updateState = updateState,
                            onUpdateApp = { startAppUpdate() },
                            googleEnabled = googleEnabled,
                            googleBusy = googleBusy,
                            onGoogleConnect = { connectGoogle() },
                            onLogout = { logoutAccount() },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onOpenRedeem = { navController.navigate(Routes.REDEEM) },
                            onReplayTutorial = {
                                joinError = null
                                tutorialReplay = true
                                navController.navigate(Routes.TUTORIAL)
                            },
                            onOpenAdmin = {
                                navController.navigate(Routes.ADMIN)
                            }
                        )
                    }
                }
                if (showEmojiBarEditor) {
                    EmojiBarEditorDialog(onDismiss = { showEmojiBarEditor = false })
                }
                SimpleBottomBar(
                    selected = tab,
                    onSelect = { next ->
                        if (next == 4) accountMessage = null
                        tab = next
                        scope.launch {
                            when (next) {
                                2 -> syncInventory()
                                3 -> {
                                    refreshAccount()
                                    syncInventory()
                                }
                                4 -> {
                                    googleEnabled = runCatching {
                                        LuvApiClient.authConfig().googleEnabled
                                    }.getOrDefault(googleEnabled)
                                    refreshAccount()
                                }
                                else -> refreshAccount()
                            }
                        }
                    }
                )
            }
        }

        composable(Routes.NICKNAME) {
            NicknameScreen(
                initial = nickname.orEmpty(),
                onBack = { navController.popBackStack() },
                onContinue = { selected ->
                    scope.launch {
                        prefs.setNickname(selected)
                        ensureAuth(selected)
                        colorIndex = prefs.snapshot().colorIndex
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(Routes.PROFILE) {
            ProfileCanvasScreen(
                nickname = nickname ?: "Du",
                colorIndex = colorIndex,
                editable = true,
                initialOpenChest = reopenProfileChest,
                initialChestTab = profileChestTab,
                onInitialChestConsumed = { reopenProfileChest = false },
                onClose = { navController.popBackStack() },
                onEditNickname = { navController.navigate(Routes.NICKNAME) },
                onOpenMarketplace = { chestTab ->
                    profileChestTab = chestTab
                    marketReturnTo = MarketReturnTo.Profile(chestTab)
                    navController.popBackStack()
                    tab = 3
                    openMarketPanel = MarketPanel.Marketplace
                    scope.launch {
                        refreshAccount()
                        syncInventory()
                    }
                },
                onOpenItemShop = { chestTab ->
                    profileChestTab = chestTab
                    marketReturnTo = MarketReturnTo.Profile(chestTab)
                    navController.popBackStack()
                    tab = 3
                    openMarketPanel = MarketPanel.ItemShop
                    scope.launch {
                        refreshAccount()
                        syncInventory()
                    }
                }
            )
        }

        composable(
            route = Routes.PEER_PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { entry ->
            val peerId = entry.arguments?.getString("userId").orEmpty()
            val peerNick = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>("peer_nick")
                ?: "Jemand"
            ProfileCanvasScreen(
                nickname = peerNick,
                colorIndex = PeerPalette.indexFor(peerNick),
                editable = false,
                userId = peerId,
                onClose = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenQuietHours = { navController.navigate(Routes.QUIET_HOURS) },
                onDeleteAccount = { deleteAccountCompletely() }
            )
        }

        composable(Routes.QUIET_HOURS) {
            QuietHoursScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CREATE) {
            CreateLobbyScreen(
                error = joinError,
                canCreateFree = account?.canCreateFreeLobby != false,
                lobbyCreateCost = account?.lobbyCreateCost ?: PeerPalette.LOBBY_CREATE_COST,
                onCreate = { name, hostColorSide ->
                    if (busy) return@CreateLobbyScreen
                    scope.launch {
                        busy = true
                        joinError = null
                        try {
                            val snap = prefs.snapshot()
                            if (snap.lobbies.size >= PeerPalette.MAX_LOBBIES) {
                                joinError = "Maximal ${PeerPalette.MAX_LOBBIES} Lobbys."
                                return@launch
                            }
                            val room = LuvApiClient.createRoom(name, hostColorSide)
                            AccountSession.account.value?.let { prefs.updateAccount(it) }
                            val myColor = PeerPalette.hostSideColor(hostColorSide)
                            prefs.setColorIndex(myColor)
                            colorIndex = myColor
                            CanvasStore.updateProfile(snap.nickname.orEmpty(), myColor)
                            val lobby = Lobby(
                                id = UUID.randomUUID().toString(),
                                name = room.name.ifBlank { name },
                                role = Role.HOST,
                                code = room.code,
                                token = room.token,
                                invite = room.invite,
                                capacity = room.capacity,
                                isFree = room.isFree,
                                hostNickname = room.hostNickname.ifBlank { nickname.orEmpty() },
                                hostColorSide = hostColorSide
                            )
                            prefs.upsertLobby(lobby)
                            PairSessionState.setCapacity(lobby.id, room.capacity)
                            CanvasStore.setActiveLobby(lobby.id)
                            PairConnectionService.startAll(context)
                            shareLobby = lobby
                            // Erst navigieren — sonst flackert kurz der „kostet Coins“-Text rot
                            navController.navigate(Routes.HOST_SHARE) {
                                popUpTo(Routes.CREATE) { inclusive = true }
                            }
                            refreshAccount()
                        } catch (e: Exception) {
                            if (e is LuvApiException && e.isNoCoins) {
                                showNoCoins = true
                                joinError = null
                            } else {
                                joinError = when (e) {
                                    is LuvApiException -> e.message
                                    else -> "API nicht erreichbar."
                                }
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HOST_SHARE) {
            val lobby = shareLobby ?: lobbies.lastOrNull()
            if (lobby == null) {
                LaunchedEffect(Unit) { navController.popBackStack(Routes.MAIN, false) }
                return@composable
            }
            HostShareScreen(
                lobby = lobby,
                onInviteSeat = { inviteSeat(lobby) },
                onBuySeat = { buySeat(lobby) },
                onOpen = {
                    CanvasStore.setActiveLobby(lobby.id)
                    CanvasStore.updateKnownLobbies(lobbies.map { it.id })
                    context.startActivity(
                        Intent(context, LockDrawActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobby.id)
                        }
                    )
                    scope.launch {
                        prefs.setActiveLobby(lobby.id)
                        refreshAccount()
                    }
                },
                onContinue = {
                    tab = 0
                    scope.launch { refreshAccount() }
                    navController.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                },
                onBack = {
                    tab = 0
                    scope.launch { refreshAccount() }
                    navController.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                }
            )
        }

        composable(Routes.JOIN) {
            JoinScreen(
                error = joinError,
                initialCode = PendingJoin.peek().orEmpty(),
                onPreview = { raw ->
                    val code = LuvApiClient.normalizeCode(raw)
                    if (code == null) {
                        joinError = "Ungültiger Link oder Code."
                    } else {
                        openJoinPreview(code)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.JOIN_PREVIEW) {
            JoinPreviewScreen(
                preview = joinPreview,
                loading = joinPreviewLoading,
                error = joinError,
                onJoin = {
                    val code = joinPreviewCode ?: joinPreview?.code ?: return@JoinPreviewScreen
                    scope.launch {
                        if (joinWithCode(code)) {
                            Toast.makeText(context, "Lobby beigetreten", Toast.LENGTH_SHORT).show()
                            tab = 0
                            navController.navigate(Routes.MAIN) {
                                popUpTo(Routes.MAIN) { inclusive = true }
                            }
                        }
                    }
                },
                onDecline = {
                    joinPreview = null
                    joinPreviewCode = null
                    joinError = null
                    PendingJoin.consume()
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.REDEEM) {
            RedeemScreen(
                error = accountMessage,
                onRedeem = { code ->
                    scope.launch {
                        accountMessage = null
                        runCatching {
                            val result = LuvApiClient.redeem(code)
                            prefs.updateAccount(result.user)
                            AccountSession.setAccount(result.user)
                            Toast.makeText(
                                context,
                                "+${result.coins} Coins eingelöst",
                                Toast.LENGTH_SHORT
                            ).show()
                            accountMessage = null
                            refreshAccount()
                            navController.popBackStack()
                            tab = 2
                        }.onFailure {
                            accountMessage = it.message
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN) {
            AdminHubScreen(
                account = account,
                onBack = {
                    accountMessage = null
                    navController.popBackStack()
                },
                onOpenProfile = { userId, nick ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("peer_nick", nick)
                    navController.navigate(Routes.peerProfile(userId))
                }
            )
        }

        composable(
            route = Routes.RENAME,
            arguments = listOf(navArgument("lobbyId") { type = NavType.StringType })
        ) { entry ->
            val lobbyId = entry.arguments?.getString("lobbyId")
            val lobby = lobbies.firstOrNull { it.id == lobbyId }
            if (lobby == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            RenameLobbyScreen(
                currentName = lobby.name,
                onSave = { name ->
                    scope.launch {
                        prefs.renameLobby(lobby.id, name)
                        runCatching { LuvApiClient.renameRoom(lobby.code, name) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    "Name lokal gespeichert — Sync später",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        LockScreenWidgetProvider.requestUpdate(context)
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }

    if (showPublicSplash) {
        PublicCanvasSplash(onFinished = { showPublicSplash = false })
    }

    // Live-Hinweise vom Team (WS + Poll)
    LaunchedEffect(Unit) {
        while (true) {
            if (!LuvApiClient.sessionToken.isNullOrBlank()) {
                runCatching {
                    LuvApiClient.fetchLiveNotice()?.let { LiveNoticeBus.offer(it) }
                }
            }
            delay(4000)
        }
    }
    LiveNoticePopup()
    }
}
