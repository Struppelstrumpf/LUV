package com.luv.couple.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairSessionState
import com.luv.couple.net.PendingJoin
import com.luv.couple.net.PendingShopReturn
import com.luv.couple.net.ShopPack
import com.luv.couple.net.VoucherInfo
import com.luv.couple.ui.screens.AccountHomeScreen
import com.luv.couple.ui.screens.AdminScreen
import com.luv.couple.ui.screens.CreateLobbyScreen
import com.luv.couple.ui.screens.HostShareScreen
import com.luv.couple.ui.screens.JoinPreviewScreen
import com.luv.couple.ui.screens.JoinScreen
import com.luv.couple.ui.screens.LobbiesScreen
import com.luv.couple.ui.screens.NicknameScreen
import com.luv.couple.ui.screens.RedeemScreen
import com.luv.couple.ui.screens.RenameLobbyScreen
import com.luv.couple.ui.screens.SimpleBottomBar
import com.luv.couple.ui.screens.TutorialFlow
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
    val partnerNotify by prefs.partnerDrawNotifyFlow.collectAsStateWithLifecycle(initialValue = true)
    val partnerHaptic by prefs.partnerHapticFlow.collectAsStateWithLifecycle(initialValue = true)
    val lobbyStates by PairConnectionService.lobbyStates.collectAsStateWithLifecycle()
    val reconnectUi by PairConnectionService.reconnectUi.collectAsStateWithLifecycle()
    val account by AccountSession.account.collectAsStateWithLifecycle()
    val pendingJoin by PendingJoin.code.collectAsStateWithLifecycle()
    val pendingShopReturn by PendingShopReturn.pending.collectAsStateWithLifecycle()

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
    var shopEnabled by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf<List<ShopPack>>(emptyList()) }
    var vouchers by remember { mutableStateOf<List<VoucherInfo>>(emptyList()) }
    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(fallback, "Einladung teilen"))
        }
    }

    fun inviteMessage(lobby: Lobby): String =
        "Einladung zur LUV-Lobby „${lobby.name}“:\n\n${lobby.joinUrl}\n\n" +
            "App: https://reineke.pro/luv/"

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
            val (enabled, list) = LuvApiClient.shopPacks()
            shopEnabled = enabled
            packs = list
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
                val lobby = Lobby(
                    id = UUID.randomUUID().toString(),
                    name = room.name.ifBlank { "Lobby" },
                    role = Role.JOIN,
                    code = room.code,
                    token = room.token,
                    invite = room.invite,
                    capacity = room.capacity,
                    isFree = room.isFree,
                    hostNickname = room.hostNickname
                )
                prefs.upsertLobby(lobby)
                PairSessionState.setCapacity(lobby.id, room.capacity)
                CanvasStore.setActiveLobby(lobby.id)
                PairConnectionService.startAll(context)
                LockScreenWidgetProvider.requestUpdate(context)
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
        shareText(inviteMessage(lobby))
    }

    fun buySeat(lobby: Lobby) {
        if (busy) return
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
                joinError = e.message
                Toast.makeText(context, e.message ?: "Kauf fehlgeschlagen", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        AccountSession.economyBlocks.collect {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            refreshAccount()
        }
    }

    LaunchedEffect(Unit) {
        val snapshot = prefs.snapshot()
        colorIndex = snapshot.colorIndex
        LuvApiClient.sessionToken = snapshot.sessionToken
        snapshot.account?.let { AccountSession.setAccount(it) }
        CanvasStore.updateProfile(snapshot.nickname, snapshot.colorIndex)
        CanvasStore.updateKnownLobbies(snapshot.lobbies.map { it.id })
        startDestination = if (!snapshot.tutorialDone || !snapshot.hasNickname || snapshot.sessionToken.isNullOrBlank()) {
            Routes.TUTORIAL
        } else {
            if (snapshot.hasLobbies) {
                CanvasStore.setActiveLobby(snapshot.activeLobbyId)
                PairConnectionService.startAll(context)
            }
            scope.launch { runCatching { LuvApiClient.claimDaily(); refreshAccount() } }
            Routes.MAIN
        }
    }

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
        tab = 1
        refreshAccount()
        // Webhook kann kurz brauchen
        kotlinx.coroutines.delay(1200)
        refreshAccount()
        accountMessage = "Willkommen zurück — Coins sind aktualisiert ♥"
        Toast.makeText(context, "Zurück in LUV", Toast.LENGTH_SHORT).show()
    }

    val destination = startDestination ?: return

    NavHost(navController = navController, startDestination = destination) {
        composable(Routes.TUTORIAL) {
            TutorialFlow(
                busy = busy,
                error = joinError,
                onFinished = { nick ->
                    scope.launch {
                        busy = true
                        joinError = null
                        prefs.setNickname(nick)
                        if (ensureAuth(nick)) {
                            prefs.setTutorialDone(true)
                            runCatching { LuvApiClient.claimDaily() }
                            refreshAccount()
                            navController.navigate(Routes.MAIN) {
                                popUpTo(Routes.TUTORIAL) { inclusive = true }
                            }
                        }
                        busy = false
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
                            lobbies = lobbies,
                            activeLobbyId = activeLobbyId,
                            lobbyStates = lobbyStates,
                            reconnectUi = reconnectUi,
                            partnerNotifyEnabled = partnerNotify,
                            onPartnerNotifyChange = { enabled ->
                                scope.launch { prefs.setPartnerDrawNotifyEnabled(enabled) }
                            },
                            partnerHapticEnabled = partnerHaptic,
                            onPartnerHapticChange = { enabled ->
                                scope.launch { prefs.setPartnerHapticEnabled(enabled) }
                            },
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
                                scope.launch { prefs.setActiveLobby(lobby.id) }
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
                                    if (lobby.role == Role.HOST) {
                                        runCatching { LuvApiClient.abandonRoom(lobby.code) }
                                    }
                                    PairConnectionService.stop(context, lobby.id)
                                    CanvasStore.clearLobby(lobby.id)
                                    prefs.removeLobby(lobby.id)
                                    LockScreenWidgetProvider.requestUpdate(context)
                                    if (prefs.snapshot().lobbies.isEmpty()) {
                                        PairConnectionService.stop(context)
                                    }
                                    refreshAccount()
                                }
                            },
                            onReconnect = { lobby ->
                                PairConnectionService.reconnectNow(context, lobby.id)
                            },
                            onEditNickname = { navController.navigate(Routes.NICKNAME) }
                        )
                        else -> AccountHomeScreen(
                            account = account,
                            colorIndex = colorIndex,
                            message = accountMessage,
                            shopEnabled = shopEnabled,
                            packs = packs,
                            onClaimDaily = {
                                scope.launch {
                                    runCatching {
                                        val (user, claimed) = LuvApiClient.claimDaily()
                                        prefs.updateAccount(user)
                                        AccountSession.setAccount(user)
                                        accountMessage = if (claimed) "+${user.dailyCoins} Coins — schön, dass du da bist ♥" else "Heute schon abgeholt"
                                    }.onFailure {
                                        accountMessage = it.message
                                    }
                                }
                            },
                            onOpenRedeem = { navController.navigate(Routes.REDEEM) },
                            onOpenAdmin = {
                                scope.launch {
                                    vouchers = runCatching { LuvApiClient.listVouchers() }.getOrDefault(emptyList())
                                    navController.navigate(Routes.ADMIN)
                                }
                            },
                            onBuyPack = { pack ->
                                scope.launch {
                                    runCatching {
                                        val url = LuvApiClient.checkout(pack.id)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }.onFailure {
                                        accountMessage = it.message
                                    }
                                }
                            },
                            onRefresh = { scope.launch { refreshAccount(); accountMessage = "Aktualisiert" } }
                        )
                    }
                }
                SimpleBottomBar(
                    selected = tab,
                    onSelect = {
                        if (it == 1) accountMessage = null
                        tab = it
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

        composable(Routes.CREATE) {
            CreateLobbyScreen(
                error = joinError,
                onCreate = { name ->
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
                            val room = LuvApiClient.createRoom(name)
                            AccountSession.account.value?.let { prefs.updateAccount(it) }
                            val lobby = Lobby(
                                id = UUID.randomUUID().toString(),
                                name = room.name.ifBlank { name },
                                role = Role.HOST,
                                code = room.code,
                                token = room.token,
                                invite = room.invite,
                                capacity = room.capacity,
                                isFree = room.isFree,
                                hostNickname = room.hostNickname.ifBlank { nickname.orEmpty() }
                            )
                            prefs.upsertLobby(lobby)
                            PairSessionState.setCapacity(lobby.id, room.capacity)
                            CanvasStore.setActiveLobby(lobby.id)
                            PairConnectionService.startAll(context)
                            shareLobby = lobby
                            refreshAccount()
                            navController.navigate(Routes.HOST_SHARE) {
                                popUpTo(Routes.CREATE) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            joinError = when (e) {
                                is LuvApiException -> e.message
                                else -> "API nicht erreichbar."
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
                connectionState = lobbyStates[lobby.id] ?: PairConnectionService.state.value,
                onInviteSeat = { inviteSeat(lobby) },
                onBuySeat = { buySeat(lobby) },
                onContinue = {
                    tab = 0
                    navController.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } }
                },
                onBack = {
                    tab = 0
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
                            if (result.type == "admin") {
                                Toast.makeText(context, "Admin freigeschaltet", Toast.LENGTH_SHORT).show()
                                accountMessage = null
                                vouchers = runCatching { LuvApiClient.listVouchers() }.getOrDefault(emptyList())
                                navController.navigate(Routes.ADMIN) {
                                    popUpTo(Routes.REDEEM) { inclusive = true }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "+${result.coins} Coins eingelöst",
                                    Toast.LENGTH_SHORT
                                ).show()
                                accountMessage = null
                                navController.popBackStack()
                                tab = 1
                            }
                        }.onFailure {
                            accountMessage = it.message
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN) {
            AdminScreen(
                vouchers = vouchers,
                message = accountMessage,
                onCreate = { draft ->
                    scope.launch {
                        accountMessage = null
                        runCatching {
                            val v = LuvApiClient.createVoucher(
                                coins = draft.coins,
                                maxRedeems = draft.maxPeople,
                                validDays = draft.validDays,
                                forever = draft.forever,
                                code = draft.code
                            )
                            vouchers = listOf(v) + vouchers
                            Toast.makeText(context, "Code ${v.code} erstellt", Toast.LENGTH_SHORT).show()
                        }.onFailure { accountMessage = it.message }
                    }
                },
                onBack = {
                    accountMessage = null
                    navController.popBackStack()
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
                        LockScreenWidgetProvider.requestUpdate(context)
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
