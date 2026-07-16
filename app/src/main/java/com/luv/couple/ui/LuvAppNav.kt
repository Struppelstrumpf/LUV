package com.luv.couple.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.luv.couple.lock.CanvasStore
import com.luv.couple.lock.LockDrawActivity
import com.luv.couple.lock.LockScreenWidgetProvider
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairSessionState
import com.luv.couple.net.PendingJoin
import com.luv.couple.ui.screens.CreateLobbyScreen
import com.luv.couple.ui.screens.HostShareScreen
import com.luv.couple.ui.screens.JoinScreen
import com.luv.couple.ui.screens.LobbiesScreen
import com.luv.couple.ui.screens.NicknameScreen
import com.luv.couple.ui.screens.RenameLobbyScreen
import com.luv.couple.update.AppUpdater
import kotlinx.coroutines.launch
import java.util.UUID

object Routes {
    const val NICKNAME = "nickname"
    const val LOBBIES = "lobbies"
    const val CREATE = "create"
    const val HOST_SHARE = "host_share"
    const val JOIN = "join"
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
    val pendingJoin by PendingJoin.code.collectAsStateWithLifecycle()

    var startDestination by remember { mutableStateOf<String?>(null) }
    var shareLobby by remember { mutableStateOf<Lobby?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val versionLabel = remember { AppUpdater.versionLabel() }
    var colorIndex by remember { mutableStateOf(0) }

    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        AppUpdater.installUpdate(context, uri).onFailure { error ->
            Toast.makeText(
                context,
                error.message ?: "Update fehlgeschlagen",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun startUpdateFlow() {
        if (!AppUpdater.canRequestPackageInstalls(context)) {
            Toast.makeText(
                context,
                "Bitte erlaube LUV, Apps zu installieren — dann erneut tippen.",
                Toast.LENGTH_LONG
            ).show()
            AppUpdater.openInstallPermissionSettings(context)
            return
        }
        apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
    }

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
            context.startActivity(Intent.createChooser(fallback, "Link teilen"))
        }
    }

    fun inviteMessage(lobby: Lobby): String =
        "Tritt meiner LUV-Lobby „${lobby.name}“ bei:\n\n${lobby.joinUrl}\n\n" +
            "App öffnen oder herunterladen: https://reineke.pro/love/"

    suspend fun joinWithCode(raw: String, suggestedName: String? = null): Boolean {
        if (busy) return false
        busy = true
        joinError = null
        return try {
            val snap = prefs.snapshot()
            if (snap.lobbies.size >= PeerPalette.MAX_LOBBIES) {
                joinError = "Maximal ${PeerPalette.MAX_LOBBIES} Lobbys."
                false
            } else {
                val room = LuvApiClient.joinRoom(raw)
                val lobby = Lobby(
                    id = UUID.randomUUID().toString(),
                    name = suggestedName?.takeIf { it.isNotBlank() } ?: "Lobby",
                    role = Role.JOIN,
                    code = room.code,
                    token = room.token,
                    invite = room.invite
                )
                prefs.upsertLobby(lobby)
                CanvasStore.setActiveLobby(lobby.id)
                PairConnectionService.startAll(context)
                LockScreenWidgetProvider.requestUpdate(context)
                true
            }
        } catch (e: Exception) {
            joinError = when (e) {
                is LuvApiException -> e.message
                else -> "Beitreten fehlgeschlagen. Link/Server prüfen."
            }
            false
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        PairSessionState.notes.collect { text ->
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        PairSessionState.missedYou.collect {
            Toast.makeText(context, context.getString(com.luv.couple.R.string.missed_you), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val snapshot = prefs.snapshot()
        colorIndex = snapshot.colorIndex
        startDestination = when {
            !snapshot.hasNickname -> Routes.NICKNAME
            else -> {
                if (snapshot.hasLobbies) {
                    CanvasStore.setActiveLobby(snapshot.activeLobbyId)
                    PairConnectionService.startAll(context)
                }
                Routes.LOBBIES
            }
        }
    }

    // Deep-Link: nach Nickname automatisch joinen
    LaunchedEffect(startDestination, nickname, pendingJoin) {
        if (startDestination == null) return@LaunchedEffect
        if (nickname.isNullOrBlank()) return@LaunchedEffect
        val code = PendingJoin.consume() ?: return@LaunchedEffect
        val ok = joinWithCode(code)
        if (ok) {
            Toast.makeText(context, "Lobby beigetreten", Toast.LENGTH_SHORT).show()
            navController.navigate(Routes.LOBBIES) {
                popUpTo(0) { inclusive = true }
            }
        } else if (joinError != null) {
            navController.navigate(Routes.JOIN)
        }
    }

    val destination = startDestination ?: return

    NavHost(navController = navController, startDestination = destination) {
        composable(Routes.NICKNAME) {
            val canGoBack = navController.previousBackStackEntry != null
            NicknameScreen(
                initial = nickname.orEmpty(),
                onBack = if (canGoBack) ({ navController.popBackStack() }) else null,
                onContinue = { selected ->
                    scope.launch {
                        prefs.setNickname(selected)
                        colorIndex = PeerPalette.indexFor(selected.lowercase())
                        LockScreenWidgetProvider.requestUpdate(context)
                        if (canGoBack) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.LOBBIES) {
                                popUpTo(Routes.NICKNAME) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        composable(Routes.LOBBIES) {
            LobbiesScreen(
                nickname = nickname ?: "Du",
                colorIndex = colorIndex,
                lobbies = lobbies,
                activeLobbyId = activeLobbyId,
                lobbyStates = lobbyStates,
                versionLabel = versionLabel,
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
                    scope.launch {
                        prefs.setActiveLobby(lobby.id)
                        CanvasStore.setActiveLobby(lobby.id)
                        context.startActivity(
                            Intent(context, LockDrawActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra(LockDrawActivity.EXTRA_LOBBY_ID, lobby.id)
                            }
                        )
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
                onShareLobby = { lobby -> shareText(inviteMessage(lobby)) },
                onRenameLobby = { lobby ->
                    navController.navigate(Routes.rename(lobby.id))
                },
                onLeaveLobby = { lobby ->
                    scope.launch {
                        PairConnectionService.stop(context, lobby.id)
                        CanvasStore.clearLobby(lobby.id)
                        prefs.removeLobby(lobby.id)
                        LockScreenWidgetProvider.requestUpdate(context)
                        if (prefs.snapshot().lobbies.isEmpty()) {
                            PairConnectionService.stop(context)
                        }
                    }
                },
                onInstallUpdate = { startUpdateFlow() },
                onAddWidgetHelp = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                },
                onEditNickname = {
                    navController.navigate(Routes.NICKNAME)
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
                            val room = LuvApiClient.createRoom()
                            val lobby = Lobby(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                role = Role.HOST,
                                code = room.code,
                                token = room.token,
                                invite = room.invite
                            )
                            prefs.upsertLobby(lobby)
                            CanvasStore.setActiveLobby(lobby.id)
                            PairConnectionService.startAll(context)
                            shareLobby = lobby
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
                LaunchedEffect(Unit) { navController.popBackStack(Routes.LOBBIES, false) }
                return@composable
            }
            HostShareScreen(
                lobbyName = lobby.name,
                joinUrl = lobby.joinUrl,
                connectionState = lobbyStates[lobby.id] ?: PairConnectionService.state.value,
                onShare = { shareText(inviteMessage(lobby)) },
                onContinue = {
                    navController.navigate(Routes.LOBBIES) {
                        popUpTo(Routes.LOBBIES) { inclusive = true }
                    }
                },
                onBack = {
                    navController.navigate(Routes.LOBBIES) {
                        popUpTo(Routes.LOBBIES) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.JOIN) {
            JoinScreen(
                error = joinError,
                initialCode = PendingJoin.peek().orEmpty(),
                onJoin = { raw ->
                    scope.launch {
                        if (joinWithCode(raw)) {
                            navController.navigate(Routes.LOBBIES) {
                                popUpTo(Routes.LOBBIES) { inclusive = true }
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
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
