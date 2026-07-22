package com.luv.couple.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.luv.couple.data.Stroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.view.WindowManager
import com.luv.couple.ui.theme.AccentRose
import com.luv.couple.ui.theme.BgDeep
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.luv.couple.ui.theme.BodyFont
import com.luv.couple.ui.theme.TextMuted
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.luv.couple.LuvApp
import com.luv.couple.billing.PlayBilling
import com.luv.couple.billing.PlayBillingException
import com.luv.couple.billing.findActivity
import com.luv.couple.data.Lobby
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.Role
import com.luv.couple.data.RoomPreview
import com.luv.couple.data.asCleanJsonString
import com.luv.couple.lock.CanvasMemoryKeeper
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.LockDrawActivity
import com.luv.couple.lock.LockScreenWidgetProvider
import com.luv.couple.net.AccountSession
import com.luv.couple.net.AchievementsBadge
import com.luv.couple.net.EventSession
import com.luv.couple.net.GoogleAuth
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairEvent
import com.luv.couple.net.PairSessionState
import com.luv.couple.net.InstallReferrerJoin
import com.luv.couple.net.PendingInviteRejoin
import com.luv.couple.net.PendingJoin
import com.luv.couple.net.PendingOnboardingRestart
import com.luv.couple.net.PendingTutorialKeepAuth
import com.luv.couple.ui.screens.MarketHubCache
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
import com.luv.couple.ui.screens.StaffWarningPopup
import com.luv.couple.net.LiveNoticeBus
import com.luv.couple.net.StaffWarningBus
import com.luv.couple.ui.screens.CreateLobbyScreen
import com.luv.couple.ui.screens.ForcedUpdateDialog
import com.luv.couple.ui.screens.ForcedMaintenanceDialog
import com.luv.couple.ui.screens.HelpScreen
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
import com.luv.couple.ui.screens.TutorialFinishPayload
import com.luv.couple.ui.screens.TutorialFlow
import com.luv.couple.profile.ProfileCatalog
import com.luv.couple.profile.ProfileElType
import com.luv.couple.update.AppUpdater
import com.luv.couple.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    const val HELP = "help"
    const val RENAME = "rename/{lobbyId}"
    fun rename(lobbyId: String) = "rename/$lobbyId"
}

@Composable
fun LuvAppNav() {
    val context = LocalContext.current
    val view = LocalView.current
    // Bildschirm bleibt an, solange die App sichtbar ist
    DisposableEffect(view) {
        view.keepScreenOn = true
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            // Flag am Window bleibt — nur View-Flag zurücksetzen
            view.keepScreenOn = false
        }
    }
    val prefs = LuvApp.instance.prefs
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val playBilling = remember(context) { PlayBilling(context) }
    DisposableEffect(playBilling) {
        onDispose { playBilling.endConnection() }
    }

    val nickname by prefs.nicknameFlow.collectAsStateWithLifecycle(initialValue = null)
    val lobbies by prefs.lobbiesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeLobbyId by prefs.activeLobbyIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val lobbyStates by PairConnectionService.lobbyStates.collectAsStateWithLifecycle()
    val reconnectUi by PairConnectionService.reconnectUi.collectAsStateWithLifecycle()
    val account by AccountSession.account.collectAsStateWithLifecycle()
    val sozialDot by com.luv.couple.net.NotificationBadges.hasSozialDot.collectAsStateWithLifecycle()
    val marketDot by com.luv.couple.net.NotificationBadges.hasMarketDot.collectAsStateWithLifecycle()
    val inventarDot by com.luv.couple.net.NotificationBadges.hasInventoryDot.collectAsStateWithLifecycle()
    val pendingJoin by PendingJoin.code.collectAsStateWithLifecycle()
    val pendingInviteRejoin by PendingInviteRejoin.code.collectAsStateWithLifecycle()
    val pendingOnboardingRestart by PendingOnboardingRestart.pending.collectAsStateWithLifecycle()
    val pendingTutorialKeepAuth by PendingTutorialKeepAuth.pending.collectAsStateWithLifecycle()
    val pendingShopReturn by PendingShopReturn.pending.collectAsStateWithLifecycle()
    val pendingShop by PendingShop.open.collectAsStateWithLifecycle()
    val pendingMarketplace by com.luv.couple.net.PendingMarketplace.open.collectAsStateWithLifecycle()
    val pendingDeep by com.luv.couple.net.PendingDeepLink.target.collectAsStateWithLifecycle()
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()
    val focusUpdate by AppUpdater.focusRequest.collectAsStateWithLifecycle()
    var googleEnabled by remember { mutableStateOf(false) }
    var googleBusy by remember { mutableStateOf(false) }
    val needsGoogleGate = AccountSession.needsGoogleLogin(googleEnabled) ||
        account?.isTrial == true
    var startDestination by remember { mutableStateOf<String?>(null) }
    var shareLobby by remember { mutableStateOf<Lobby?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var joinPreview by remember { mutableStateOf<RoomPreview?>(null) }
    var joinPreviewLoading by remember { mutableStateOf(false) }
    var joinPreviewCode by remember { mutableStateOf<String?>(null) }
    /** Deep-Link: Beitreten-Popup über allem (ohne NavHost-Race). */
    var showInviteOverlay by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var openMarketCoinShop by remember { mutableStateOf(false) }
    var openMarketPanel by remember { mutableStateOf<MarketPanel?>(null) }
    var openMarketShopTab by remember { mutableIntStateOf(0) }
    var marketReturnTo by remember { mutableStateOf<MarketReturnTo>(MarketReturnTo.None) }
    var inventorySubTab by remember { mutableIntStateOf(0) }
    var sozialSubTab by remember { mutableIntStateOf(0) }
    var pendingCeremonyGathering by remember { mutableStateOf(false) }
    var coldFeetLobby by remember { mutableStateOf<Lobby?>(null) }
    var showCustomRoomPicker by remember { mutableStateOf(false) }
    var customRoomChoices by remember {
        mutableStateOf<List<LuvApiClient.CustomRoomCard>>(emptyList())
    }
    var customRoomPickerBusy by remember { mutableStateOf(false) }

    LaunchedEffect(showCustomRoomPicker) {
        if (!showCustomRoomPicker) return@LaunchedEffect
        customRoomPickerBusy = true
        customRoomChoices = runCatching { LuvApiClient.listCustomRooms() }.getOrElse { emptyList() }
        customRoomPickerBusy = false
    }
    var reopenProfileChest by remember { mutableStateOf(false) }
    var profileChestTab by remember { mutableIntStateOf(0) }
    var showEmojiBarEditor by remember { mutableStateOf(false) }
    var shopEnabled by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf<List<ShopPack>>(emptyList()) }
    var vouchers by remember { mutableStateOf<List<VoucherInfo>>(emptyList()) }
    var publicReports by remember { mutableStateOf<List<PublicReportInfo>>(emptyList()) }
    var showNoCoins by remember { mutableStateOf(false) }
    var inviteLobby by remember { mutableStateOf<Lobby?>(null) }
    /** Nach Trial: Lobby voll/weg → freundlicher Create-Dialog */
    var inviteRejoinDialog by remember { mutableStateOf<String?>(null) }
    // Sofort Splash — nicht erst nach Prefs/Nav, sonst Sekunden Schwarzbild
    var showPublicSplash by remember {
        mutableStateOf(
            !PendingSplashSkip.peek() && PendingJoin.peek().isNullOrBlank()
        )
    }
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
        // Kurz im Chat; Bild + Titel kommen aus der Link-Vorschau (OG)
        return "Das zeichnen wir gerade — komm mit rein!\n${lobby.joinUrl}"
    }

    fun shareInviteLink(lobby: Lobby) {
        scope.launch {
            CanvasStore.setActiveLobby(lobby.id)
            PairConnectionService.startAll(context)
            // History vom Server abwarten, falls lokal noch leer (Home → Teilen)
            if (CanvasStore.snapshot(lobby.id).isEmpty()) {
                for (i in 0 until 12) {
                    kotlinx.coroutines.delay(250)
                    if (CanvasStore.snapshot(lobby.id).isNotEmpty()) break
                }
            }
            var uploaded = runCatching {
                CanvasMemoryKeeper.uploadSnapshot(lobby, allowEmpty = false)
            }.getOrDefault(false)
            if (!uploaded && CanvasStore.snapshot(lobby.id).isNotEmpty()) {
                kotlinx.coroutines.delay(400)
                uploaded = runCatching {
                    CanvasMemoryKeeper.uploadSnapshot(lobby, allowEmpty = false)
                }.getOrDefault(false)
            }
            shareText(inviteMessage(lobby))
            inviteLobby = null
        }
    }

    fun openJoinPreview(code: String, asOverlay: Boolean = false) {
        val clean = LuvApiClient.normalizeCode(code) ?: code.trim().uppercase()
        if (clean.isBlank()) return
        joinPreviewCode = clean
        joinPreview = null
        joinPreviewLoading = true
        joinError = null
        showPublicSplash = false
        if (asOverlay) {
            showInviteOverlay = true
        } else {
            showInviteOverlay = false
            runCatching {
                navController.navigate(Routes.JOIN_PREVIEW) {
                    launchSingleTop = true
                }
            }
        }
        scope.launch {
            try {
                joinPreview = LuvApiClient.roomPreview(clean)
            } catch (e: Exception) {
                // Kurz und klar — UI zeigt bei fehlender Lobby nur diesen Text + Zurück
                joinError = "Lobby nicht gefunden."
                joinPreview = null
            } finally {
                joinPreviewLoading = false
            }
        }
    }

    fun dismissInviteConfirm() {
        showInviteOverlay = false
        joinPreview = null
        joinPreviewCode = null
        joinError = null
        PendingJoin.consume()
        // Nach toter/abgelehnter Einladung: Home wenn Google-Konto, sonst Name/Start
        scope.launch {
            val snap = prefs.snapshot()
            val angemeldet = !snap.sessionToken.isNullOrBlank() &&
                snap.account?.googleLinked == true &&
                snap.account?.isTrial != true
            if (angemeldet) {
                if (startDestination != Routes.MAIN) startDestination = Routes.MAIN
                val route = navController.currentDestination?.route
                if (route != null && route != Routes.MAIN) {
                    runCatching {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            } else {
                startDestination = Routes.TUTORIAL
                runCatching {
                    navController.navigate(Routes.TUTORIAL) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    /** Erstes Tutorial: Skizze als normale Host-Lobby anlegen (nicht bei Replay). */
    suspend fun createTutorialLobby(nick: String, strokes: List<Stroke>) {
        val snap = prefs.snapshot()
        if (snap.lobbies.size >= PeerPalette.MAX_LOBBIES) return
        val hostColorSide = if (colorIndex % 2 == 0) "blue" else "purple"
        val room = LuvApiClient.createRoom(
            name = "Meine Leinwand",
            hostColorSide = hostColorSide
        )
        AccountSession.account.value?.let { prefs.updateAccount(it) }
        val myColor = PeerPalette.hostSideColor(hostColorSide)
        prefs.setColorIndexForLobby(room.code, myColor)
        colorIndex = myColor
        CanvasStore.updateProfile(nick.ifBlank { snap.nickname.orEmpty() }, myColor)
        val lobby = Lobby(
            id = UUID.randomUUID().toString(),
            name = room.name.ifBlank { "Meine Leinwand" },
            role = Role.HOST,
            code = room.code,
            token = room.token,
            invite = room.invite,
            capacity = room.capacity,
            isFree = room.isFree,
            hostNickname = room.hostNickname.ifBlank { nick },
            hostColorSide = hostColorSide,
                                            createdByMe = true
                                        )
        prefs.upsertLobby(lobby)
        PairSessionState.setCapacity(lobby.id, room.capacity)
        CanvasStore.setActiveLobby(lobby.id)
        if (strokes.isNotEmpty()) {
            CanvasStore.seedLocalStrokes(
                lobby.id,
                strokes.map { it.copy(nickname = nick, colorIndex = myColor, colorLocked = true) }
            )
        }
        CanvasStore.updateKnownLobbies(prefs.snapshot().lobbies.map { it.id })
        PairConnectionService.startAll(context)
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
            // Katalog + Admin-Namen syncen (sonst bleiben lokale Hardcodes wie „Tiger“)
            runCatching { LuvApiClient.fetchShopCatalog() }
            val (enabled, list) = LuvApiClient.shopPacks()
            shopEnabled = enabled
            val playPrices = runCatching {
                playBilling.queryProducts(list.map { it.id }).let { playBilling.formattedPrices }
            }.getOrDefault(emptyMap())
            packs = list.map { pack ->
                pack.copy(displayPrice = playPrices[pack.id])
            }
        }
    }

    suspend fun fulfillPlayPurchase(
        productId: String,
        purchaseToken: String,
        orderId: String?,
        integrityToken: String? = null,
        integrityNonce: String? = null
    ): Boolean {
        val granted = LuvApiClient.confirmPlayPurchase(
            productId = productId,
            purchaseToken = purchaseToken,
            orderId = orderId,
            integrityToken = integrityToken,
            integrityNonce = integrityNonce
        )
        playBilling.consume(purchaseToken)
        refreshAccount()
        accountMessage = if (granted > 0) {
            "+$granted Coins gutgeschrieben ♥"
        } else {
            "Kauf bestätigt — Coins aktualisiert ♥"
        }
        return true
    }

    /**
     * Session sicherstellen — legt kein neues Geräte-Konto an, wenn schon eine Session
     * existiert oder Google-Login Pflicht ist (sonst würden Coins/Freunde „verschwinden“).
     */
    suspend fun ensureAuth(nick: String): Boolean {
        val snap = prefs.snapshot()
        if (!snap.sessionToken.isNullOrBlank()) {
            LuvApiClient.sessionToken = snap.sessionToken
            return try {
                val user = LuvApiClient.me()
                prefs.updateAccount(user)
                AccountSession.setAccount(user)
                colorIndex = prefs.snapshot().colorIndex
                CanvasStore.updateProfile(user.nickname, colorIndex)
                true
            } catch (e: Exception) {
                joinError = e.message ?: "Bitte mit Google anmelden."
                false
            }
        }
        if (googleEnabled) {
            joinError = "Bitte zuerst mit Google anmelden."
            return false
        }
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

    fun requireGoogleOrToast(): Boolean {
        if (!AccountSession.needsGoogleLogin(googleEnabled)) return true
        val msg = "Bitte zuerst mit Google anmelden."
        joinError = msg
        accountMessage = msg
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        return false
    }

    fun createEventLobby(event: com.luv.couple.net.SeasonEvent) {
        if (busy) return
        if (!requireGoogleOrToast()) return
        scope.launch {
            busy = true
            joinError = null
            try {
                val snap = prefs.snapshot()
                val existing = snap.lobbies.firstOrNull {
                    it.eventId.asCleanJsonString() == event.id
                }
                if (existing != null) {
                    context.startActivity(
                        Intent(context, LockDrawActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(LockDrawActivity.EXTRA_LOBBY_ID, existing.id)
                        }
                    )
                    return@launch
                }
                if (snap.lobbies.size >= PeerPalette.MAX_LOBBIES && event.canCreateLobby) {
                    joinError = "Maximal ${PeerPalette.MAX_LOBBIES} Lobbys."
                    return@launch
                }
                val hostColorSide = if (colorIndex % 2 == 0) "blue" else "purple"
                val room = LuvApiClient.createRoom(
                    name = "Event",
                    hostColorSide = hostColorSide,
                    eventId = event.id
                )
                AccountSession.account.value?.let { prefs.updateAccount(it) }
                val myColor = PeerPalette.hostSideColor(hostColorSide)
                prefs.setColorIndexForLobby(room.code, myColor)
                colorIndex = myColor
                CanvasStore.updateProfile(snap.nickname.orEmpty(), myColor)
                val lobby = Lobby(
                    id = UUID.randomUUID().toString(),
                    name = room.name.ifBlank { "Event" },
                    role = Role.HOST,
                    code = room.code,
                    token = room.token,
                    invite = room.invite,
                    capacity = room.capacity,
                    isFree = room.isFree,
                    hostNickname = room.hostNickname.ifBlank { nickname.orEmpty() },
                    hostColorSide = hostColorSide,
                    createdByMe = true,
                    eventId = (room.eventId ?: event.id).asCleanJsonString(),
                    eventPrompt = room.eventPrompt.asCleanJsonString(),
                    eventPromptChoices = room.eventPromptChoices,
                    eventEndsAt = room.eventEndsAt.asCleanJsonString(),
                )
                prefs.upsertLobby(lobby)
                PairSessionState.setCapacity(lobby.id, room.capacity)
                CanvasStore.setActiveLobby(lobby.id)
                PairConnectionService.startAll(context)
                refreshAccount()
                runCatching { LuvApiClient.fetchEvents() }
                CanvasStore.updateKnownLobbies(prefs.snapshot().lobbies.map { it.id })
                context.startActivity(
                    Intent(context, LockDrawActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobby.id)
                    }
                )
                prefs.setActiveLobby(lobby.id)
                prefs.markLobbyCanvasSeen(lobby.code)
                prefs.snoozeLobbyGlow(lobby.code)
            } catch (e: Exception) {
                if (e is LuvApiException && e.isNoCoins) {
                    showNoCoins = true
                    joinError = null
                } else if (e is LuvApiException && e.error == "event_lobby_exists") {
                    val again = prefs.snapshot().lobbies.firstOrNull {
                        it.eventId.asCleanJsonString() == event.id
                    }
                    if (again != null) {
                        context.startActivity(
                            Intent(context, LockDrawActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra(LockDrawActivity.EXTRA_LOBBY_ID, again.id)
                            }
                        )
                    } else {
                        Toast.makeText(
                            context,
                            e.message ?: "Event-Lobby existiert bereits.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    joinError = when (e) {
                        is LuvApiException -> e.message
                        else -> "API nicht erreichbar."
                    }
                    Toast.makeText(
                        context,
                        joinError ?: "Event-Lobby fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                busy = false
            }
        }
    }

    fun isChosenNickname(nick: String?): Boolean {
        val n = nick?.trim().orEmpty()
        return n.length >= 2 && !n.equals("Luv", ignoreCase = true)
    }

    suspend fun syncInventory() {
        runCatching {
            val remote = LuvApiClient.fetchInventory()
            prefs.applyInventorySnap(
                emojis = remote.emojis,
                themes = remote.themes,
                stickers = remote.stickers,
                pets = remote.pets,
                equippedPet = remote.equippedPet
            )
            com.luv.couple.net.NotificationBadges.syncInventoryUnseenFromPrefs()
            com.luv.couple.net.NotificationBadges.syncAppBadge(context)
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
        val bundleResult = runCatching { LuvApiClient.myLobbyBundle() }
        val bundle = bundleResult.getOrNull()
        if (bundle == null && hostedHint.isEmpty() && joinedHint.isEmpty()) {
            // Sync fehlgeschlagen — lokale Lobbys nicht anfassen
            Log.w("LuvCloudSync", "Lobby-Sync fehlgeschlagen: ${bundleResult.exceptionOrNull()?.message}")
            return
        }
        val hosted = hostedHint.ifEmpty { bundle?.hosted.orEmpty() }
        val joined = joinedHint.ifEmpty { bundle?.joined.orEmpty() }
        // Nur ersetzen wenn Server geantwortet hat (oder Auth-Hints da sind)
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
        val remoteSettings = settingsHint
            ?: runCatching { LuvApiClient.fetchSettings() }.getOrNull()
        if (remoteSettings != null) {
            val localQuiet = prefs.quietHours()
            val serverEmpty = remoteSettings.updatedAt <= 0L &&
                remoteSettings.quietHours.byDay.isEmpty()
            if (serverEmpty && localQuiet.byDay.isNotEmpty()) {
                runCatching { LuvApiClient.putSettings(prefs.buildCloudSettings()) }
            } else {
                prefs.applySettingsBlob(remoteSettings)
            }
        }
        // Glocke/Impulse: auf Lobby-Codes migrieren und fehlende Cloud-Keys nachziehen
        prefs.persistLobbyProximityMigration()
        val cloudSettings = prefs.buildCloudSettings()
        val remoteProxCodes = remoteSettings?.lobbyProximity?.keys
            ?.map { it.trim().uppercase().removePrefix("LUV-") }
            ?.filter { it.length in 3..16 }
            ?.toSet()
            .orEmpty()
        if (cloudSettings.lobbyProximity.keys != remoteProxCodes) {
            runCatching { LuvApiClient.putSettings(cloudSettings) }
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
        // Erfolge / Tagesfortschritt kommen vom Server — nach Login neu laden
        AchievementsBadge.refresh()
    }

    suspend fun completeGoogleLogin(google: GoogleAuth.Result) {
        val activity = context.findActivity()
        val attestation = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                com.luv.couple.ui.security.PlayIntegrityGate.attestForSignup(
                    activity ?: context
                )
            }
        }.getOrNull()
        val result = LuvApiClient.authGoogle(
            idToken = google.idToken,
            integrityToken = attestation?.integrityToken,
            integrityNonce = attestation?.nonce
        )
        val inTutorial = navController.currentDestination?.route == Routes.TUTORIAL
        val returning = !result.created && isChosenNickname(result.user.nickname)
        if (inTutorial && !returning) {
            // Google am Tutorial-Ende: Auth übernehmen, Nickname/Lobby kommen via onFinished
            applyAuthResult(
                result,
                fromGoogle = true,
                finishOnboarding = false,
                applyNickname = false
            )
            accountMessage = null
            Toast.makeText(context, "Angemeldet", Toast.LENGTH_SHORT).show()
        } else {
            applyAuthResult(result, fromGoogle = true)
            accountMessage = if (result.linked) {
                "Mit Google angemeldet — Konto wiederhergestellt."
            } else {
                "Angemeldet. Willkommen zurück."
            }
            Toast.makeText(context, accountMessage, Toast.LENGTH_SHORT).show()
            if (inTutorial) {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.TUTORIAL) { inclusive = true }
                }
            } else {
                tab = 0
            }
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        scope.launch {
            try {
                // Auch bei RESULT_CANCELED parsen — Google meldet SHA-Fehler oft so
                val google = GoogleAuth.parseSignInIntentResult(
                    activityResult.data,
                    activityResult.resultCode
                )
                completeGoogleLogin(google)
            } catch (e: Exception) {
                val msg = e.message ?: "Google-Anmeldung fehlgeschlagen."
                if (e is LuvApiException && e.error == "cancelled") {
                    Toast.makeText(context, "Abgebrochen", Toast.LENGTH_SHORT).show()
                } else {
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

    fun connectGoogle() {
        if (googleBusy) return
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(
                context,
                "Google-Anmeldung gerade nicht möglich — App kurz neu öffnen.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        scope.launch {
            googleBusy = true
            busy = true
            joinError = null
            accountMessage = null
            try {
                val webClientId = GoogleAuth.fetchWebClientId()
                    ?: throw LuvApiException(
                        "Google-Login ist noch nicht eingerichtet. Bitte später erneut versuchen.",
                        error = "google_disabled"
                    )
                // Kein signOut vorher — sonst oft „Abgebrochen“ nach E-Mail-Wahl
                googleSignInLauncher.launch(GoogleAuth.signInIntent(activity, webClientId))
            } catch (e: Exception) {
                val msg = e.message ?: "Google-Anmeldung fehlgeschlagen."
                joinError = msg
                accountMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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

    fun openLobbySpaceOrCanvas(lobby: Lobby) {
        if (lobby.isCustomRoom) {
            context.startActivity(
                Intent(context, com.luv.couple.ui.space.CustomRoomActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(com.luv.couple.ui.space.CustomRoomActivity.EXTRA_CODE, lobby.code)
                    putExtra(com.luv.couple.ui.space.CustomRoomActivity.EXTRA_TOKEN, lobby.token)
                    putExtra(com.luv.couple.ui.space.CustomRoomActivity.EXTRA_BELL, lobby.spaceBell)
                }
            )
            return
        }
        context.startActivity(
            Intent(context, LockDrawActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobby.id)
            }
        )
    }

    fun openLobbyCanvas(lobbyId: String, trialUntil: Long? = null) {
        val lobby = lobbies.firstOrNull { it.id == lobbyId }
        if (lobby?.isCustomRoom == true) {
            openLobbySpaceOrCanvas(lobby)
            return
        }
        context.startActivity(
            Intent(context, LockDrawActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobbyId)
                if (trialUntil != null && trialUntil > 0L) {
                    putExtra(LockDrawActivity.EXTRA_TRIAL_DRAW_UNTIL, trialUntil)
                }
            }
        )
    }

    /** joinWithCode-Kern ohne busy-Lock (für Rejoin). */
    suspend fun joinWithCodeUnlocked(raw: String): Boolean {
        val snap = prefs.snapshot()
        val normalized = LuvApiClient.normalizeCode(raw).orEmpty()
        val already = snap.lobbies.firstOrNull {
            it.code.equals(normalized, ignoreCase = true)
        }
        if (already != null) {
            prefs.setActiveLobby(already.id)
            CanvasStore.setActiveLobby(already.id)
            CanvasStore.updateKnownLobbies(snap.lobbies.map { it.id })
            PairConnectionService.startAll(context)
            return true
        }
        if (snap.lobbies.size >= PeerPalette.MAX_LOBBIES) {
            joinError = "Maximal ${PeerPalette.MAX_LOBBIES} Lobbys."
            return false
        }
        val room = LuvApiClient.joinRoom(raw)
        room.suggestedColorIndex?.let { suggested ->
            prefs.setColorIndexForLobby(room.code, suggested)
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
            isRandom = room.isRandom,
            isWedding = room.isWedding,
            isWeddingRetake = room.isWeddingRetake,
            isWeddingCeremony = room.isWeddingCeremony,
            isCustomRoom = room.isCustomRoom,
            customRoomId = room.customRoomId,
            customRoomImageUrl = room.customRoomImageUrl,
            ceremonyAt = room.ceremonyAt,
            coupleNameA = room.coupleNameA,
            coupleNameB = room.coupleNameB,
            hostNickname = room.hostNickname,
            hostColorSide = room.hostColorSide,
            createdByMe = false,
            eventId = room.eventId,
            eventPrompt = room.eventPrompt,
            eventPromptChoices = room.eventPromptChoices,
            eventEndsAt = room.eventEndsAt,
        )
        prefs.upsertLobby(lobby)
        PairSessionState.setCapacity(lobby.id, room.capacity)
        if (!lobby.isCustomRoom) {
            CanvasStore.setActiveLobby(lobby.id)
        }
        PairConnectionService.startAll(context)
        LockScreenWidgetProvider.requestUpdate(context)
        refreshAccount()
        return true
    }

    suspend fun joinWithCode(raw: String): Boolean {
        if (busy) return false
        busy = true
        joinError = null
        return try {
            joinWithCodeUnlocked(raw)
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

    /** Nach Trial-Anmeldung: mit echtem Namen zurück in die Einladungs-Lobby. */
    suspend fun tryInviteRejoin(openCanvas: Boolean = true): Boolean {
        val code = PendingInviteRejoin.peek() ?: return false
        if (busy) return false
        busy = true
        joinError = null
        PendingInviteRejoin.consume()
        return try {
            val ok = joinWithCodeUnlocked(code)
            if (ok) {
                val lobbyId = prefs.snapshot().lobbies.firstOrNull {
                    it.code.equals(code, ignoreCase = true)
                }?.id
                if (openCanvas && lobbyId != null) openLobbyCanvas(lobbyId)
                true
            } else {
                inviteRejoinDialog = "gone"
                false
            }
        } catch (e: LuvApiException) {
            joinError = e.message
            inviteRejoinDialog = when (e.error) {
                "room_full" -> "full"
                else -> "gone"
            }
            false
        } catch (e: Exception) {
            joinError = e.message ?: "Beitreten fehlgeschlagen."
            inviteRejoinDialog = "gone"
            false
        } finally {
            busy = false
        }
    }

    fun createInviteFallbackLobby() {
        if (busy) return
        if (!requireGoogleOrToast()) return
        scope.launch {
            busy = true
            try {
                refreshAccount()
                val snap = prefs.snapshot()
                if (snap.lobbies.size >= PeerPalette.MAX_LOBBIES) {
                    Toast.makeText(
                        context,
                        "Maximal ${PeerPalette.MAX_LOBBIES} Lobbys.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                val hostColorSide = if (colorIndex % 2 == 0) "blue" else "purple"
                val room = LuvApiClient.createRoom(
                    name = "Meine Leinwand",
                    hostColorSide = hostColorSide
                )
                AccountSession.account.value?.let { prefs.updateAccount(it) }
                val myColor = PeerPalette.hostSideColor(hostColorSide)
                prefs.setColorIndexForLobby(room.code, myColor)
                colorIndex = myColor
                val nick = snap.nickname.orEmpty().ifBlank { "Luv" }
                CanvasStore.updateProfile(nick, myColor)
                val lobby = Lobby(
                    id = UUID.randomUUID().toString(),
                    name = room.name.ifBlank { "Meine Leinwand" },
                    role = Role.HOST,
                    code = room.code,
                    token = room.token,
                    invite = room.invite,
                    capacity = room.capacity,
                    isFree = room.isFree,
                    hostNickname = room.hostNickname.ifBlank { nick },
                    hostColorSide = hostColorSide,
                    createdByMe = true
                )
                prefs.upsertLobby(lobby)
                PairSessionState.setCapacity(lobby.id, room.capacity)
                CanvasStore.setActiveLobby(lobby.id)
                CanvasStore.updateKnownLobbies(prefs.snapshot().lobbies.map { it.id })
                PairConnectionService.startAll(context)
                refreshAccount()
                inviteRejoinDialog = null
                openLobbyCanvas(lobby.id)
                Toast.makeText(
                    context,
                    if (room.isFree) "Deine Lobby ist bereit — lade Freunde ein ♥"
                    else "Lobby erstellt · ${PeerPalette.LOBBY_CREATE_COST} Coins",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                if (e is LuvApiException && e.isNoCoins) {
                    showNoCoins = true
                } else {
                    Toast.makeText(
                        context,
                        e.message ?: "Lobby erstellen fehlgeschlagen",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                busy = false
            }
        }
    }

    /** Invite ohne Google: 30s Probezeichnen, dann Gate in LockDraw. */
    suspend fun trialJoinWithCode(raw: String): Boolean {
        if (busy) return false
        busy = true
        joinError = null
        return try {
            val (installId, _) = prefs.ensureInstallCredentials()
            val room = LuvApiClient.trialJoinRoom(raw, installId)
            val token = room.sessionToken ?: LuvApiClient.sessionToken
            val user = AccountSession.account.value
            if (token.isNullOrBlank() || user == null) {
                joinError = "Probezeichnen nicht möglich."
                return false
            }
            prefs.saveSession(token, user, applyNickname = true)
            prefs.setTutorialDone(true)
            val nick = user.nickname.ifBlank { "Gast" }
            prefs.setNickname(nick)
            room.suggestedColorIndex?.let { suggested ->
                prefs.setColorIndexForLobby(room.code, suggested)
                colorIndex = suggested
                CanvasStore.updateProfile(nick, suggested)
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
                isRandom = room.isRandom,
                isWedding = room.isWedding,
                isWeddingRetake = room.isWeddingRetake,
                isWeddingCeremony = room.isWeddingCeremony,
                isCustomRoom = room.isCustomRoom,
                customRoomId = room.customRoomId,
                customRoomImageUrl = room.customRoomImageUrl,
                ceremonyAt = room.ceremonyAt,
                coupleNameA = room.coupleNameA,
                coupleNameB = room.coupleNameB,
                hostNickname = room.hostNickname,
                hostColorSide = room.hostColorSide,
                createdByMe = false,
                eventId = room.eventId,
                eventPrompt = room.eventPrompt,
                eventPromptChoices = room.eventPromptChoices,
                eventEndsAt = room.eventEndsAt,
            )
            prefs.resetInventoryToStarter()
            prefs.upsertLobby(lobby)
            prefs.setActiveLobby(lobby.id)
            PairSessionState.setCapacity(lobby.id, room.capacity)
            if (!lobby.isCustomRoom) {
                CanvasStore.setActiveLobby(lobby.id)
            }
            PairConnectionService.startAll(context)
            LockScreenWidgetProvider.requestUpdate(context)
            if (lobby.isCustomRoom) {
                context.startActivity(
                    Intent(context, com.luv.couple.ui.space.CustomRoomActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(com.luv.couple.ui.space.CustomRoomActivity.EXTRA_CODE, lobby.code)
                        putExtra(com.luv.couple.ui.space.CustomRoomActivity.EXTRA_TOKEN, lobby.token)
                        putExtra(com.luv.couple.ui.space.CustomRoomActivity.EXTRA_BELL, lobby.spaceBell)
                    }
                )
                refreshAccount()
                return true
            }
            context.startActivity(
                Intent(context, LockDrawActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobby.id)
                    putExtra(
                        LockDrawActivity.EXTRA_TRIAL_DRAW_UNTIL,
                        room.trialDrawUntil ?: (user.trialDrawUntil ?: 0L)
                    )
                }
            )
            true
        } catch (e: Exception) {
            joinError = when (e) {
                is LuvApiException -> e.message
                else -> "Einladung konnte nicht geöffnet werden."
            }
            false
        } finally {
            busy = false
        }
    }

    /** Trial-Zurück: Session leeren, Onboarding (Name → … → Google) starten. */
    suspend fun restartOnboardingAfterTrial() {
        runCatching { GoogleAuth.signOut(context) }
        PairConnectionService.stop(context)
        PairSessionState.reset()
        CanvasStore.clearAll(notifyPeer = false)
        prefs.clearForLogout()
        AccountSession.setAccount(null)
        LuvApiClient.sessionToken = null
        tab = 0
        joinError = null
        accountMessage = null
        showInviteOverlay = false
        tutorialReplay = false
        startDestination = Routes.TUTORIAL
        navController.navigate(Routes.TUTORIAL) {
            popUpTo(0) { inclusive = true }
        }
    }

    /** Trial-Gate + neues Google-Konto: Tutorial/Name, Session behalten, danach Rejoin. */
    suspend fun startTutorialKeepAuthAfterTrial() {
        PairConnectionService.stop(context)
        PairSessionState.reset()
        CanvasStore.clearAll(notifyPeer = false)
        prefs.snapshot().lobbies.toList().forEach { lobby ->
            runCatching { prefs.removeLobby(lobby.id) }
        }
        prefs.setTutorialDone(false)
        tab = 0
        joinError = null
        accountMessage = null
        showInviteOverlay = false
        tutorialReplay = false
        startDestination = Routes.TUTORIAL
        navController.navigate(Routes.TUTORIAL) {
            popUpTo(0) { inclusive = true }
        }
    }

    suspend fun confirmInviteJoin() {
        val code = joinPreviewCode ?: joinPreview?.code ?: return
        val linked = AccountSession.account.value?.googleLinked == true
        val hasSession = !LuvApiClient.sessionToken.isNullOrBlank()
        val ok = if (linked && hasSession) {
            joinWithCode(code)
        } else {
            trialJoinWithCode(code)
        }
        if (!ok) return
        val lobbyId = prefs.snapshot().lobbies.firstOrNull {
            it.code.equals(code, ignoreCase = true)
        }?.id
        dismissInviteConfirm()
        tab = 0
        if (startDestination != Routes.MAIN) startDestination = Routes.MAIN
        runCatching {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.MAIN) { inclusive = true }
            }
        }
        // trialJoin öffnet LockDraw selbst; Google-Join hier öffnen
        if (linked && hasSession && lobbyId != null) {
            openLobbyCanvas(lobbyId)
        }
    }

    fun inviteSeat(lobby: Lobby) {
        inviteLobby = lobby
    }

    fun startAppUpdate() {
        scope.launch {
            val release = AppUpdater.currentReleaseOrNull()
            val ready = updateState as? UpdateUiState.Ready
            if (ready != null &&
                ready.release.channel == com.luv.couple.update.UpdateChannel.WebsiteApk
            ) {
                AppUpdater.installApkFile(context, ready.file)
            } else {
                AppUpdater.downloadAndInstall(context, release)
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
        runCatching { InstallReferrerJoin.captureOnce(context) }
        val snapshot = prefs.snapshot()
        colorIndex = snapshot.colorIndex
        LuvApiClient.sessionToken = snapshot.sessionToken
        snapshot.account?.let { AccountSession.setAccount(it) }
        CanvasStore.updateProfile(snapshot.nickname, snapshot.colorIndex)
        CanvasStore.updateKnownLobbies(snapshot.lobbies.map { it.id })
        scope.launch {
            googleEnabled = runCatching { LuvApiClient.authConfig().googleEnabled }.getOrDefault(false)
        }
        // Kein inviteBoot → MAIN: sonst landet man ohne echte Einladung auf Home.
        // Einladungs-Overlay liegt ggf. darüber; ohne Session bleibt Start = Name/Tutorial.
        startDestination = if (!snapshot.tutorialDone || !snapshot.hasNickname || snapshot.sessionToken.isNullOrBlank()) {
            Routes.TUTORIAL
        } else {
            if (snapshot.hasLobbies) {
                CanvasStore.setActiveLobby(snapshot.activeLobbyId)
                PairConnectionService.startAll(context)
            }
            scope.launch {
                runCatching { LuvApiClient.claimDaily(); refreshAccount() }
                val sessionOk = runCatching {
                    val user = LuvApiClient.me()
                    prefs.updateAccount(user)
                    AccountSession.setAccount(user)
                    true
                }.getOrDefault(false)
                if (sessionOk && AccountSession.account.value?.googleLinked == true) {
                    runCatching { syncCloudAccount(force = true) }
                } else if (!sessionOk && !snapshot.sessionToken.isNullOrBlank()) {
                    accountMessage = "Bitte erneut mit Google anmelden, um Lobbys zu laden."
                }
            }
            Routes.MAIN
        }
        // Marktplatz/Itemshop-Vorschau schon beim Start laden (auch ohne Markt-Tab)
        scope.launch { runCatching { MarketHubCache.warm() } }
    }

    // Invite-Deep-Link / Install-Referrer → Overlay (über Tutorial oder Home)
    LaunchedEffect(pendingJoin, startDestination) {
        val code = PendingJoin.peek() ?: return@LaunchedEffect
        if (startDestination == null) return@LaunchedEffect
        PendingJoin.consume()
        showPublicSplash = false
        openJoinPreview(code, asOverlay = true)
    }

    // Zurück aus Trial-Leinwand → Name/Tutorial/Google
    LaunchedEffect(pendingOnboardingRestart, startDestination) {
        if (!PendingOnboardingRestart.peek()) return@LaunchedEffect
        if (startDestination == null) return@LaunchedEffect
        PendingOnboardingRestart.consume()
        showPublicSplash = false
        restartOnboardingAfterTrial()
    }

    // Trial-Gate + neues Google-Konto → Tutorial behalten Session
    LaunchedEffect(pendingTutorialKeepAuth, startDestination) {
        if (!PendingTutorialKeepAuth.peek()) return@LaunchedEffect
        if (startDestination == null) return@LaunchedEffect
        PendingTutorialKeepAuth.consume()
        showPublicSplash = false
        startTutorialKeepAuthAfterTrial()
    }

    // Nach Google (Trial-Gate): mit echtem Namen zurück in die Einladungs-Lobby
    LaunchedEffect(
        pendingInviteRejoin,
        account?.googleLinked,
        account?.id,
        startDestination,
        pendingTutorialKeepAuth
    ) {
        val code = PendingInviteRejoin.peek() ?: return@LaunchedEffect
        if (startDestination == null) return@LaunchedEffect
        if (account?.googleLinked != true) return@LaunchedEffect
        if (account?.isTrial == true) return@LaunchedEffect
        if (LuvApiClient.sessionToken.isNullOrBlank()) return@LaunchedEffect
        // Während Onboarding/Tutorial warten — dort wird nach Finish rejoined
        if (startDestination == Routes.TUTORIAL) return@LaunchedEffect
        if (PendingTutorialKeepAuth.peek()) return@LaunchedEffect
        if (navController.currentDestination?.route == Routes.TUTORIAL) return@LaunchedEffect
        showPublicSplash = false
        if (startDestination != Routes.MAIN) startDestination = Routes.MAIN
        tryInviteRejoin(openCanvas = true)
    }

    LaunchedEffect(needsGoogleGate) {
        if (needsGoogleGate) tab = 0
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
                runCatching { MarketHubCache.warm() }
                if (AccountSession.account.value?.googleLinked == true) {
                    runCatching { syncCloudAccount() }
                }
                if (!LuvApiClient.sessionToken.isNullOrBlank()) {
                    runCatching {
                        LuvApiClient.fetchLiveNotice()?.let { LiveNoticeBus.offer(it) }
                    }
                    runCatching {
                        val sn = LuvApiClient.fetchStaffNotices()
                        StaffWarningBus.offer(sn.pending, sn.warnings)
                    }
                    val prevSales = runCatching { prefs.pendingSalesKnownCount() }.getOrDefault(0)
                    val sales = com.luv.couple.net.NotificationBadges.refreshPendingSales(context)
                    com.luv.couple.net.NotificationBadges.refreshFriends(context)
                    AchievementsBadge.refresh()
                    if (sales != null && sales.count > prevSales) {
                        com.luv.couple.notify.LuvAlertNotifier.onMarketSale(
                            context,
                            itemCount = sales.count,
                            totalCoins = sales.totalCoins
                        )
                    }
                    if (sales != null) {
                        runCatching { prefs.setPendingSalesKnownCount(sales.count) }
                    }
                    com.luv.couple.net.NotificationBadges.syncAppBadge(context)
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

    var maintenanceStatus by remember {
        mutableStateOf<LuvApiClient.MaintenanceStatus?>(null)
    }
    var maintenanceHold by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val st = runCatching { LuvApiClient.fetchMaintenanceStatus() }.getOrNull()
            if (st != null) {
                if (st.active) {
                    maintenanceHold = true
                    maintenanceStatus = st
                } else if (maintenanceHold) {
                    // Bis Dialog dismiss (Claim/Weiter/Failsafe) — Status weiter aktualisieren
                    maintenanceStatus = st
                } else {
                    maintenanceStatus = null
                }
            }
            delay(if (maintenanceStatus?.active == true) 4_000 else 12_000)
        }
    }
    maintenanceStatus?.let { st ->
        if (st.active || maintenanceHold) {
            ForcedMaintenanceDialog(
                status = st,
                onDismiss = {
                    maintenanceHold = false
                    maintenanceStatus = null
                },
                onClaimed = {
                    scope.launch { runCatching { refreshAccount() } }
                }
            )
        }
    }

    LaunchedEffect(startDestination, pendingShopReturn) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (!PendingShopReturn.consume()) return@LaunchedEffect
        tab = 3
        openMarketCoinShop = true
        refreshAccount()
    }

    // Offene Play-Käufe nach Absturz / unterbrochener Bestätigung nachziehen
    LaunchedEffect(startDestination, account?.googleLinked) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (account?.googleLinked != true) return@LaunchedEffect
        if (LuvApiClient.sessionToken.isNullOrBlank()) return@LaunchedEffect
        val pending = runCatching { playBilling.queryUnconsumedPurchases() }.getOrDefault(emptyList())
        for (p in pending) {
            runCatching {
                val att = com.luv.couple.ui.security.PlayIntegrityGate.attestForPurchase(context)
                fulfillPlayPurchase(
                    p.productId,
                    p.purchaseToken,
                    p.orderId,
                    integrityToken = att?.integrityToken,
                    integrityNonce = att?.nonce
                )
            }
        }
    }

    LaunchedEffect(pendingShop, startDestination) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (!PendingShop.consume()) return@LaunchedEffect
        openShopTab()
    }

    LaunchedEffect(pendingMarketplace, startDestination) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (!com.luv.couple.net.PendingMarketplace.consume()) return@LaunchedEffect
        tab = 3
        openMarketPanel = MarketPanel.Marketplace
    }

    LaunchedEffect(pendingDeep, startDestination) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        val target = com.luv.couple.net.PendingDeepLink.consume() ?: return@LaunchedEffect
        showPublicSplash = false
        when (target) {
            com.luv.couple.net.DeepLinkTarget.Home -> tab = 0
            com.luv.couple.net.DeepLinkTarget.SozialWedding -> {
                sozialSubTab = 0
                tab = 1
                com.luv.couple.net.PendingWeddingCeremony.offer()
            }
            com.luv.couple.net.DeepLinkTarget.SozialFriends -> {
                sozialSubTab = 0
                tab = 1
            }
            com.luv.couple.net.DeepLinkTarget.SozialAchievements -> {
                sozialSubTab = 2
                tab = 1
            }
            com.luv.couple.net.DeepLinkTarget.Inventar -> tab = 2
            com.luv.couple.net.DeepLinkTarget.Marketplace -> {
                tab = 3
                openMarketPanel = MarketPanel.Marketplace
            }
            com.luv.couple.net.DeepLinkTarget.Shop -> {
                tab = 3
                openMarketPanel = MarketPanel.ItemShop
            }
            com.luv.couple.net.DeepLinkTarget.CoinShop -> openShopTab()
            com.luv.couple.net.DeepLinkTarget.Profile -> {
                tab = 0
                navController.navigate(Routes.PROFILE)
            }
            com.luv.couple.net.DeepLinkTarget.LastCanvas -> {
                tab = 0
                scope.launch {
                    val lobbyId = withContext(Dispatchers.IO) {
                        val snap = prefs.snapshot()
                        snap.activeLobbyId
                            ?: snap.lobbies.maxByOrNull { it.lastCanvasAt }?.id
                            ?: snap.lobbies.firstOrNull()?.id
                    }
                    if (!lobbyId.isNullOrBlank()) {
                        context.startActivity(
                            Intent(context, LockDrawActivity::class.java).apply {
                                putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobbyId)
                            }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(startDestination) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        kotlinx.coroutines.delay(1800)
        com.luv.couple.lock.CanvasMemoryKeeper.checkAndNotify(context.applicationContext)
    }

    LaunchedEffect(startDestination, account?.id) {
        if (startDestination != Routes.MAIN) return@LaunchedEffect
        if (account?.id.isNullOrBlank()) return@LaunchedEffect
        runCatching { LuvApiClient.fetchEvents() }
        AchievementsBadge.refresh()
    }

    LaunchedEffect(startDestination) {
        when (startDestination) {
            null -> Unit
            Routes.MAIN -> {
                if (PendingSplashSkip.consume() || !PendingJoin.peek().isNullOrBlank()) {
                    showPublicSplash = false
                } else {
                    showPublicSplash = true
                }
            }
            else -> showPublicSplash = false
        }
    }

    if (pendingCeremonyGathering) {
        com.luv.couple.ui.wedding.WeddingGatheringDialog(
            onDismiss = { pendingCeremonyGathering = false },
            onEnterAltar = {
                pendingCeremonyGathering = false
                context.startActivity(
                    Intent(context, com.luv.couple.ui.wedding.WeddingRoomActivity::class.java)
                )
            },
            onShareRemind = { text ->
                com.luv.couple.ui.wedding.shareWeddingText(context, text)
            },
            isCouple = true
        )
    }
    coldFeetLobby?.let { lobby ->
        com.luv.couple.ui.wedding.WeddingColdFeetDialog(
            onDismiss = { coldFeetLobby = null },
            onLeft = {
                coldFeetLobby = null
                scope.launch {
                    prefs.dismissLobbyCode(lobby.code)
                    prefs.removeLobby(lobby.id)
                    runCatching { syncCloudAccount(force = true) }
                    refreshAccount()
                    Toast.makeText(context, "Hochzeit verlassen", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showCustomRoomPicker) {
        AlertDialog(
            onDismissRequest = { showCustomRoomPicker = false },
            containerColor = BgDeep,
            title = {
                Text("Neuer Raum", fontFamily = BodyFont, color = Color.White, fontSize = 20.sp)
            },
            text = {
                Column {
                    when {
                        customRoomPickerBusy -> Text(
                            "Lade Räume…",
                            color = TextMuted,
                            fontFamily = BodyFont
                        )
                        customRoomChoices.isEmpty() -> Text(
                            "Noch keine Räume im Admin angelegt.",
                            color = TextMuted,
                            fontFamily = BodyFont
                        )
                        else -> customRoomChoices.forEach { card ->
                            TextButton(
                                onClick = {
                                    showCustomRoomPicker = false
                                    scope.launch {
                                        busy = true
                                        try {
                                            val room = LuvApiClient.createRoom(
                                                name = card.name,
                                                customRoomId = card.id
                                            )
                                            val lobby = Lobby(
                                                id = UUID.randomUUID().toString(),
                                                name = room.name.ifBlank { card.name },
                                                role = Role.HOST,
                                                code = room.code,
                                                token = room.token,
                                                invite = room.invite,
                                                capacity = room.capacity,
                                                isFree = room.isFree,
                                                isCustomRoom = true,
                                                customRoomId = room.customRoomId ?: card.id,
                                                customRoomImageUrl = room.customRoomImageUrl
                                                    ?: card.imageUrl,
                                                spaceBell = true,
                                                hostNickname = room.hostNickname,
                                                hostColorSide = room.hostColorSide,
                                                createdByMe = true
                                            )
                                            prefs.upsertLobby(lobby)
                                            PairSessionState.setCapacity(lobby.id, room.capacity)
                                            PairConnectionService.startAll(context)
                                            LockScreenWidgetProvider.requestUpdate(context)
                                            refreshAccount()
                                            Toast.makeText(
                                                context,
                                                "Raum „${lobby.name}“ bereit",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            joinError = when (e) {
                                                is LuvApiException -> e.message
                                                else -> "Raum konnte nicht erstellt werden."
                                            }
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            ) {
                                Text(card.name, color = AccentRose, fontFamily = BodyFont)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomRoomPicker = false }) {
                    Text("Abbrechen", color = TextMuted, fontFamily = BodyFont)
                }
            }
        )
    }

    NoCoinsUi.Dialog(
        visible = showNoCoins,
        onDismiss = { showNoCoins = false },
        onOpenShop = { openShopTab() }
    )

    inviteLobby?.let { lobby ->
        InviteLobbyDialog(
            lobby = lobby,
            onShare = { shareInviteLink(lobby) },
            onShareToFriend = { friend ->
                scope.launch {
                    runCatching { CanvasMemoryKeeper.uploadSnapshot(lobby) }
                    runCatching {
                        LuvApiClient.inviteFriendToLobby(friend.userId, lobby.code)
                    }.onSuccess {
                        inviteLobby = null
                    }
                    // Keine Toast-Einblendungen (verwirren beim Beitritt/Einladen)
                }
            },
            onOpen = {
                inviteLobby = null
                CanvasStore.setActiveLobby(lobby.id)
                CanvasStore.updateKnownLobbies(lobbies.map { it.id })
                openLobbySpaceOrCanvas(lobby)
                scope.launch {
                    prefs.setActiveLobby(lobby.id)
                    runCatching { LuvApiClient.pingAchievement("lobby_opens") }
                    refreshAccount()
                }
            },
            onDismiss = { inviteLobby = null }
        )
    }

    val destination = startDestination

    // Bei jeder Menü-/Screen-Navigation auf Updates prüfen (throttled)
    val navRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(navRoute, tab, destination) {
        if (destination == null) return@LaunchedEffect
        if (destination != Routes.MAIN && navRoute == null) return@LaunchedEffect
        runCatching { AppUpdater.checkOnNavigate(context) }
    }

    EventDecorHost(modifier = Modifier.fillMaxSize()) {
    // Splash liegt über allem und bleibt während Prefs/Nav gemountet (kein Remount-Schwarz)
    if (destination != null) {
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
                onFinished = { payload: TutorialFinishPayload ->
                    scope.launch {
                        if (replaying) {
                            // Replay: Zeichnung verwerfen, keine Lobby speichern
                            prefs.setTutorialDone(true)
                            runCatching { LuvApiClient.pingAchievement("tutorial_done") }
                            tutorialReplay = false
                            joinError = null
                            navController.popBackStack()
                            return@launch
                        }
                        busy = true
                        joinError = null
                        try {
                            val typedNick = payload.nickname.trim().ifBlank { "Luv" }
                            val snap = prefs.snapshot()
                            val hasGoogleSession =
                                !snap.sessionToken.isNullOrBlank() &&
                                    (account?.googleLinked == true || snap.account?.googleLinked == true)
                            // Bekanntes Konto: eingegebenen Tutorial-Namen verwerfen
                            val serverNick = (account?.nickname ?: snap.account?.nickname)?.trim().orEmpty()
                            val keepServerNick = hasGoogleSession && isChosenNickname(serverNick)
                            val nick = if (keepServerNick) serverNick else typedNick
                            if (!keepServerNick) {
                                prefs.setNickname(nick)
                            } else {
                                prefs.setNickname(serverNick)
                            }
                            val ok = if (hasGoogleSession) {
                                if (!keepServerNick) {
                                    val user = LuvApiClient.updateNickname(nick)
                                    prefs.updateAccount(user)
                                    AccountSession.setAccount(user)
                                    colorIndex = prefs.snapshot().colorIndex
                                    CanvasStore.updateProfile(user.nickname, colorIndex)
                                } else {
                                    colorIndex = prefs.snapshot().colorIndex
                                    CanvasStore.updateProfile(serverNick, colorIndex)
                                }
                                true
                            } else if (!googleEnabled) {
                                ensureAuth(nick)
                            } else {
                                joinError = "Bitte zuerst mit Google anmelden."
                                false
                            }
                            if (ok) {
                                // Tutorial-Profil nur wenn Cloud noch leer/Default
                                if (payload.profileJson.isNotBlank()) {
                                    val remoteRich = runCatching {
                                        val (_, state) = LuvApiClient.fetchMyProfileCanvas()
                                        state.layout.count {
                                            it.type != ProfileElType.Avatar &&
                                                it.type != ProfileElType.Name
                                        }
                                    }.getOrDefault(0)
                                    if (remoteRich <= 0) {
                                        prefs.setProfileCanvasJson(payload.profileJson)
                                        runCatching {
                                            val state = ProfileCatalog.decode(payload.profileJson, nick)
                                            LuvApiClient.saveMyProfileCanvas(state)
                                        }.onFailure { e ->
                                            Log.w("LuvAppNav", "Tutorial-Profil speichern fehlgeschlagen", e)
                                        }
                                    }
                                }
                                prefs.setTutorialDone(true)
                                prefs.setPendingHomeCoachmarks(true)
                                runCatching { LuvApiClient.pingAchievement("tutorial_done") }
                                runCatching { LuvApiClient.claimDaily() }
                                refreshAccount()
                                if (hasGoogleSession) {
                                    runCatching { syncCloudAccount(force = true) }
                                }
                                startDestination = Routes.MAIN
                                navController.navigate(Routes.MAIN) {
                                    popUpTo(Routes.TUTORIAL) { inclusive = true }
                                }
                                // Invite-Trial: LaunchedEffect rejoined; sonst Tutorial-Lobby
                                if (PendingInviteRejoin.peek() == null) {
                                    runCatching {
                                        createTutorialLobby(nick, payload.strokes)
                                    }.onFailure { e ->
                                        Log.w("LuvAppNav", "Tutorial-Lobby fehlgeschlagen", e)
                                    }
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
            val eventsUi by EventSession.state.collectAsStateWithLifecycle()
            val ambientDecor = eventsUi?.primaryDecor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF121821), BgDeep, Color(0xFF151A22))
                        )
                    )
            ) {
                // Rosa nur oben — kein farbiger Streifen hinter der Bottom-Bar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AccentRose.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.08f),
                                    radius = size.minDimension * 0.85f
                                )
                            )
                        }
                )
                // Seichte Ambient-Animation: Home / Sozial / Markt / Zahnrad — nicht Inventar
                if (tab != 2) {
                    MenuAmbientBackground(
                        eventDecor = ambientDecor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
                            requireGoogleLogin = needsGoogleGate,
                            googleBusy = googleBusy,
                            onGoogleSignIn = { connectGoogle() },
                            onOpenLobby = { lobby ->
                                if (requireGoogleOrToast()) {
                                    if (lobby.isWeddingCeremony) {
                                        val now = System.currentTimeMillis()
                                        val openAt = lobby.ceremonyAt - 10 * 60 * 1000L
                                        if (lobby.ceremonyAt > 0L && now < openAt) {
                                            Toast.makeText(
                                                context,
                                                "Noch ${com.luv.couple.ui.wedding.formatCountdown(openAt - now)} bis „Zur Hochzeit“",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            pendingCeremonyGathering = true
                                        }
                                    } else if (lobby.isCustomRoom) {
                                        context.startActivity(
                                            Intent(
                                                context,
                                                com.luv.couple.ui.space.CustomRoomActivity::class.java
                                            ).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                putExtra(
                                                    com.luv.couple.ui.space.CustomRoomActivity.EXTRA_CODE,
                                                    lobby.code
                                                )
                                                putExtra(
                                                    com.luv.couple.ui.space.CustomRoomActivity.EXTRA_TOKEN,
                                                    lobby.token
                                                )
                                                putExtra(
                                                    com.luv.couple.ui.space.CustomRoomActivity.EXTRA_BELL,
                                                    lobby.spaceBell
                                                )
                                            }
                                        )
                                        scope.launch {
                                            PairConnectionService.startAll(context)
                                            runCatching { LuvApiClient.pingAchievement("lobby_opens") }
                                        }
                                    } else {
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
                                            prefs.markLobbyCanvasSeen(lobby.code)
                                            prefs.snoozeLobbyGlow(lobby.code)
                                            runCatching { LuvApiClient.pingAchievement("lobby_opens") }
                                            refreshAccount()
                                        }
                                    }
                                }
                            },
                            onCreateLobby = {
                                if (requireGoogleOrToast()) {
                                    joinError = null
                                    navController.navigate(Routes.CREATE)
                                }
                            },
                            onCreateEventLobby = { event -> createEventLobby(event) },
                            onCreateCustomRoom = {
                                // Vorerst deaktiviert — Fokus Hochzeit
                            },
                            onToggleSpaceBell = { lobby ->
                                scope.launch {
                                    prefs.upsertLobby(lobby.copy(spaceBell = !lobby.spaceBell))
                                }
                            },
                            onJoinLobby = {
                                if (requireGoogleOrToast()) {
                                    joinError = null
                                    navController.navigate(Routes.JOIN)
                                }
                            },
                            onRandomLobby = {
                                joinError = null
                                scope.launch {
                                    if (!requireGoogleOrToast()) return@launch
                                    if (lobbies.any { it.isRandom }) {
                                        joinError =
                                            "Du bist schon in einer Random-Lobby. Bitte zuerst verlassen."
                                        return@launch
                                    }
                                    busy = true
                                    try {
                                        val nick = prefs.snapshot().nickname.orEmpty()
                                        if (LuvApiClient.sessionToken.isNullOrBlank()) {
                                            joinError = "Bitte zuerst mit Google anmelden."
                                            return@launch
                                        }
                                        val room = LuvApiClient.randomMatch()
                                        room.suggestedColorIndex?.let { suggested ->
                                            prefs.setColorIndexForLobby(room.code, suggested)
                                            colorIndex = suggested
                                            CanvasStore.updateProfile(nick, suggested)
                                        }
                                        val role = when (room.role?.uppercase()) {
                                            "JOIN" -> Role.JOIN
                                            else -> Role.HOST
                                        }
                                        val lobby = Lobby(
                                            id = UUID.randomUUID().toString(),
                                            name = "Random",
                                            role = role,
                                            code = room.code,
                                            token = room.token,
                                            invite = room.invite,
                                            capacity = room.capacity,
                                            isFree = room.isFree,
                                            isRandom = true,
                                            hostNickname = room.hostNickname,
                                            hostColorSide = room.hostColorSide,
                                            createdByMe = role == Role.HOST
                                        )
                                        prefs.upsertLobby(lobby)
                                        PairSessionState.setCapacity(lobby.id, room.capacity)
                                        CanvasStore.setActiveLobby(lobby.id)
                                        PairConnectionService.startAll(context)
                                        LockScreenWidgetProvider.requestUpdate(context)
                                        refreshAccount()
                                        Toast.makeText(
                                            context,
                                            "Random-Lobby bereit",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        joinError = when (e) {
                                            is LuvApiException -> e.message
                                            else -> "Random-Lobby fehlgeschlagen."
                                        }
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            onInviteSeat = { lobby -> inviteSeat(lobby) },
                            onBuySeat = { lobby -> buySeat(lobby) },
                            onRenameLobby = { lobby ->
                                navController.navigate(Routes.rename(lobby.id))
                            },
                            onLeaveLobby = { lobby ->
                                if (lobby.isWeddingCeremony) {
                                    coldFeetLobby = lobby
                                } else scope.launch {
                                    // Sofort lokal sperren — Cloud-Sync darf die Kachel nicht zurückholen
                                    prefs.dismissLobbyCode(lobby.code)
                                    if (lobby.isEventLobby) {
                                        runCatching {
                                            CanvasMemoryKeeper.uploadSnapshot(lobby)
                                        }
                                        runCatching {
                                            LuvApiClient.closeEventLobby(lobby.code, lobby.token)
                                        }.onFailure {
                                            runCatching { LuvApiClient.leaveRoom(lobby.code) }
                                        }
                                    } else {
                                        runCatching { LuvApiClient.leaveRoom(lobby.code) }
                                    }
                                    PairConnectionService.stop(context, lobby.id)
                                    CanvasStore.clearLobby(lobby.id)
                                    prefs.removeLobby(lobby.id)
                                    LockScreenWidgetProvider.requestUpdate(context)
                                    Toast.makeText(context, "Lobby verlassen", Toast.LENGTH_SHORT).show()
                                    if (prefs.snapshot().lobbies.isEmpty()) {
                                        PairConnectionService.stop(context)
                                    }
                                    runCatching { syncCloudAccount(force = true) }
                                    refreshAccount()
                                    if (lobby.isEventLobby) {
                                        runCatching { LuvApiClient.fetchEvents() }
                                    }
                                }
                            },
                            onReconnect = { lobby ->
                                PairConnectionService.reconnectNow(context, lobby.id)
                            },
                            onOpenProfile = { navController.navigate(Routes.PROFILE) },
                            onOpenEvents = {
                                sozialSubTab = 1
                                tab = 1
                            },
                            updateState = updateState,
                            onUpdateApp = { startAppUpdate() }
                        )
                        1 -> SocialScreen(
                            initialTab = sozialSubTab,
                            onOpenFriendProfile = { userId, nick ->
                                scope.launch {
                                    runCatching { LuvApiClient.pingAchievement("profile_views") }
                                }
                                navController.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("peer_nick", nick)
                                navController.navigate(Routes.peerProfile(userId))
                            },
                            onCreateEventLobby = { event -> createEventLobby(event) },
                            onSyncWeddingLobbies = {
                                scope.launch {
                                    runCatching { syncCloudAccount(force = true) }
                                }
                            },
                            onWeddingLobbyOpened = {
                                scope.launch {
                                    runCatching { syncCloudAccount(force = true) }
                                    tab = 0
                                }
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
                                openMarketShopTab = inventorySubTab.coerceIn(0, 3)
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
                            startShopTab = openMarketShopTab,
                            economyUnlocked = !AccountSession.needsGoogleLogin(googleEnabled),
                            onRequireGoogle = {
                                requireGoogleOrToast()
                                tab = 4
                            },
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
                                if (requireGoogleOrToast()) {
                                    scope.launch {
                                        val activity = context.findActivity()
                                        if (activity == null) {
                                            Toast.makeText(
                                                context,
                                                "Kauf gerade nicht möglich",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@launch
                                        }
                                        runCatching {
                                            val purchase = playBilling.launchPurchase(activity, pack.id)
                                            // Nach Play-Dialog: Integrity + Server-Gutschrift
                                            val att =
                                                com.luv.couple.ui.security.PlayIntegrityGate.attestForPurchase(
                                                    context
                                                )
                                            fulfillPlayPurchase(
                                                purchase.productId.ifBlank { pack.id },
                                                purchase.purchaseToken,
                                                purchase.orderId,
                                                integrityToken = att?.integrityToken,
                                                integrityNonce = att?.nonce
                                            )
                                            Toast.makeText(
                                                context,
                                                accountMessage ?: "Kauf erfolgreich",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure { err ->
                                            if (err is PlayBillingException &&
                                                err.message?.contains("abgebrochen", ignoreCase = true) == true
                                            ) {
                                                return@onFailure
                                            }
                                            val msg = err.message ?: "Kauf fehlgeschlagen"
                                            accountMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
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
                            onOpenHelp = { navController.navigate(Routes.HELP) },
                            onOpenAdmin = {
                                navController.navigate(Routes.ADMIN)
                            }
                        )
                    }
                }
                if (showEmojiBarEditor) {
                    EmojiBarEditorDialog(onDismiss = { showEmojiBarEditor = false })
                }
                if (!needsGoogleGate) {
                    SimpleBottomBar(
                        selected = tab,
                        sozialBadge = sozialDot,
                        marketBadge = marketDot,
                        inventarBadge = inventarDot,
                        onSelect = { next ->
                            if (next == 4) accountMessage = null
                            tab = next
                            scope.launch {
                                // Update-Check bei Tab-Wechsel (Käufe/API laufen weiter)
                                runCatching { AppUpdater.checkOnNavigate(context) }
                                when (next) {
                                    0 -> {
                                        // Hochzeitsbild-Lobby o.ä. ohne App-Neustart sichtbar
                                        runCatching { syncCloudAccount(force = true) }
                                    }
                                    1 -> {
                                        // Punkt sofort weg — Coins bleiben in Erfolge abholbar
                                        com.luv.couple.net.NotificationBadges.markSozialSeen()
                                        com.luv.couple.net.NotificationBadges.syncAppBadge(context)
                                        AchievementsBadge.refresh()
                                        com.luv.couple.net.NotificationBadges.refreshFriends(context)
                                        com.luv.couple.net.NotificationBadges.markSozialSeen()
                                        com.luv.couple.net.NotificationBadges.syncAppBadge(context)
                                    }
                                    2 -> syncInventory()
                                    3 -> {
                                        refreshAccount()
                                        syncInventory()
                                        com.luv.couple.net.NotificationBadges.refreshPendingSales(context)
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
                onEditNickname = null,
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
                    // Tabs 0–2 = Sticker/Hintergründe/Begleiter; Extras → Sticker-Shop
                    openMarketShopTab = chestTab.coerceIn(0, 2)
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

        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CREATE) {
            LaunchedEffect(googleEnabled, account?.googleLinked) {
                if (AccountSession.needsGoogleLogin(googleEnabled)) {
                    joinError = "Bitte zuerst mit Google anmelden."
                    navController.popBackStack()
                }
            }
            CreateLobbyScreen(
                error = joinError,
                canCreateFree = account?.canCreateFreeLobby != false,
                lobbyCreateCost = account?.lobbyCreateCost ?: PeerPalette.LOBBY_CREATE_COST,
                onCreate = { name, hostColorSide ->
                    if (busy) return@CreateLobbyScreen
                    if (!requireGoogleOrToast()) return@CreateLobbyScreen
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
                            prefs.setColorIndexForLobby(room.code, myColor)
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
                                hostColorSide = hostColorSide,
                                            createdByMe = true
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDeep),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Zurück …", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                }
                return@composable
            }
            HostShareScreen(
                lobby = lobby,
                onInviteSeat = { inviteSeat(lobby) },
                onBuySeat = { buySeat(lobby) },
                onOpen = {
                    CanvasStore.setActiveLobby(lobby.id)
                    CanvasStore.updateKnownLobbies(lobbies.map { it.id })
                    openLobbySpaceOrCanvas(lobby)
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
            LaunchedEffect(googleEnabled, account?.googleLinked) {
                if (AccountSession.needsGoogleLogin(googleEnabled)) {
                    joinError = "Bitte zuerst mit Google anmelden."
                    navController.popBackStack()
                }
            }
            JoinScreen(
                error = joinError,
                initialCode = PendingJoin.peek().orEmpty(),
                onPreview = { raw ->
                    if (!requireGoogleOrToast()) return@JoinScreen
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
                busy = busy,
                onJoin = {
                    scope.launch { confirmInviteJoin() }
                },
                onDecline = {
                    // dismiss navigiert selbst (Home oder Name/Start)
                    dismissInviteConfirm()
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDeep),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Zurück …", color = TextMuted, fontFamily = BodyFont, fontSize = 15.sp)
                }
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
    } // destination != null

    if (showPublicSplash && !showInviteOverlay) {
        PublicCanvasSplash(onFinished = { showPublicSplash = false })
    }

    // Deep-Link-Einladung: immer oben — unabhängig von NavHost/Splash
    if (showInviteOverlay && !joinPreviewCode.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize().zIndex(80f)) {
            JoinPreviewScreen(
                preview = joinPreview,
                loading = joinPreviewLoading,
                error = joinError,
                busy = busy,
                onJoin = { scope.launch { confirmInviteJoin() } },
                onDecline = { dismissInviteConfirm() }
            )
        }
    }

    inviteRejoinDialog?.let { reason ->
        val free = account?.canCreateFreeLobby != false
        val cost = account?.lobbyCreateCost ?: PeerPalette.LOBBY_CREATE_COST
        AlertDialog(
            onDismissRequest = { inviteRejoinDialog = null },
            title = {
                Text(
                    when (reason) {
                        "full" -> "Lobby ist voll"
                        else -> "Lobby nicht mehr da"
                    }
                )
            },
            text = {
                Text(
                    when (reason) {
                        "full" ->
                            "Kein Platz mehr — fang doch selbst an zu zeichnen und lade " +
                                "deinen Partner oder Freunde ein."
                        else ->
                            "Die Lobby gibt’s nicht mehr. Fang doch selbst an zu zeichnen und lade " +
                                "deinen Partner oder Freunde ein."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { createInviteFallbackLobby() },
                    enabled = !busy
                ) {
                    Text(
                        if (free) "Lobby erstellen"
                        else "Lobby erstellen · $cost Coins"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { inviteRejoinDialog = null }) {
                    Text("Später")
                }
            }
        )
    }

    // Live-Hinweise / Verwarnungen vom Team (WS + Poll)
    LaunchedEffect(Unit) {
        while (true) {
            if (!LuvApiClient.sessionToken.isNullOrBlank()) {
                runCatching {
                    LuvApiClient.fetchLiveNotice()?.let { LiveNoticeBus.offer(it) }
                }
                runCatching {
                    val sn = LuvApiClient.fetchStaffNotices()
                    StaffWarningBus.offer(sn.pending, sn.warnings)
                }
            }
            delay(4000)
        }
    }

    // Heartbeat: zählt für die Website, wie viele die App gerade offen haben
    val appLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        while (true) {
            val foreground = appLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (foreground && !LuvApiClient.sessionToken.isNullOrBlank()) {
                runCatching { LuvApiClient.heartbeat() }
            }
            delay(45_000)
        }
    }
    LiveNoticePopup(
        onOpenWeddingGuestbook = { uid ->
            com.luv.couple.net.PendingWeddingGuestbook.offer(uid)
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set("peer_nick", "Ehepaar")
            navController.navigate(Routes.peerProfile(uid)) {
                launchSingleTop = true
            }
        }
    )
    StaffWarningPopup()
    }
}
