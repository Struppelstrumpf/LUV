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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luv.couple.LuvApp
import com.luv.couple.data.Gender
import com.luv.couple.data.Role
import com.luv.couple.lock.LockDrawActivity
import com.luv.couple.lock.LockScreenWidgetProvider
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.LuvApiException
import com.luv.couple.net.PairConnectionService
import com.luv.couple.ui.screens.GenderScreen
import com.luv.couple.ui.screens.HomeScreen
import com.luv.couple.ui.screens.HostScreen
import com.luv.couple.ui.screens.JoinScreen
import com.luv.couple.ui.screens.RoleScreen
import com.luv.couple.update.AppUpdater
import kotlinx.coroutines.launch

object Routes {
    const val GENDER = "gender"
    const val ROLE = "role"
    const val HOST = "host"
    const val JOIN = "join"
    const val HOME = "home"
}

@Composable
fun LuvAppNav() {
    val context = LocalContext.current
    val prefs = LuvApp.instance.prefs
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val gender by prefs.genderFlow.collectAsStateWithLifecycle(initialValue = null)
    val paired by prefs.pairedFlow.collectAsStateWithLifecycle(initialValue = false)
    val inviteCode by prefs.inviteCodeFlow.collectAsStateWithLifecycle(initialValue = null)
    val connectionState by PairConnectionService.state.collectAsStateWithLifecycle()

    var startDestination by remember { mutableStateOf<String?>(null) }
    var hostCode by remember { mutableStateOf<String?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val versionLabel = remember { AppUpdater.versionLabel() }

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

    LaunchedEffect(Unit) {
        val snapshot = prefs.snapshot()
        startDestination = when {
            snapshot.gender == null -> Routes.GENDER
            !snapshot.paired -> Routes.ROLE
            else -> {
                val started = PairConnectionService.start(context)
                if (started) {
                    Routes.HOME
                } else {
                    prefs.clearPairing()
                    Routes.ROLE
                }
            }
        }
    }

    val destination = startDestination ?: return

    NavHost(navController = navController, startDestination = destination) {
        composable(Routes.GENDER) {
            GenderScreen(
                onSelect = { selected ->
                    scope.launch {
                        prefs.setGender(selected)
                        LockScreenWidgetProvider.requestUpdate(context)
                        navController.navigate(Routes.ROLE) {
                            popUpTo(Routes.GENDER) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.ROLE) {
            RoleScreen(
                gender = gender ?: Gender.MALE,
                hostError = joinError,
                versionLabel = versionLabel,
                onInstallUpdate = { startUpdateFlow() },
                onHost = {
                    if (busy) return@RoleScreen
                    scope.launch {
                        busy = true
                        joinError = null
                        try {
                            val room = LuvApiClient.createRoom()
                            prefs.savePairing(
                                role = Role.HOST,
                                token = room.token,
                                inviteCode = room.invite
                            )
                            hostCode = room.invite
                            val started = PairConnectionService.start(context)
                            if (!started) {
                                prefs.clearPairing()
                                joinError = "Verbindungsdienst konnte nicht gestartet werden."
                                return@launch
                            }
                            navController.navigate(Routes.HOST) {
                                popUpTo(Routes.ROLE) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            joinError = when (e) {
                                is LuvApiException -> e.message
                                else -> "API nicht erreichbar. URL/Server prüfen."
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                onJoin = {
                    navController.navigate(Routes.JOIN)
                }
            )
        }

        composable(Routes.HOST) {
            val code = hostCode ?: inviteCode.orEmpty()
            HostScreen(
                code = code,
                connectionState = connectionState,
                onShareWhatsApp = {
                    val text =
                        "Öffne LUV und tippe auf Beitreten. Unser Code:\n\n$code"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        setPackage("com.whatsapp")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching {
                        context.startActivity(intent)
                    }.onFailure {
                        val fallback = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(fallback, "Code teilen"))
                    }
                },
                onContinue = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOST) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.JOIN) {
            JoinScreen(
                error = joinError,
                onJoin = { raw ->
                    if (busy) return@JoinScreen
                    scope.launch {
                        busy = true
                        joinError = null
                        try {
                            val room = LuvApiClient.joinRoom(raw)
                            prefs.savePairing(
                                role = Role.JOIN,
                                token = room.token,
                                inviteCode = room.invite
                            )
                            val started = PairConnectionService.start(context)
                            if (!started) {
                                prefs.clearPairing()
                                joinError = "Verbindungsdienst konnte nicht gestartet werden."
                                return@launch
                            }
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ROLE) { inclusive = true }
                            }
                        } catch (e: Exception) {
                            joinError = when (e) {
                                is LuvApiException -> e.message
                                else -> "Beitreten fehlgeschlagen. Code/Server prüfen."
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                gender = gender ?: Gender.MALE,
                paired = paired,
                connectionState = connectionState,
                inviteCode = inviteCode,
                versionLabel = versionLabel,
                onOpenCanvas = {
                    context.startActivity(
                        Intent(context, LockDrawActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                },
                onShareCode = {
                    val code = inviteCode ?: return@HomeScreen
                    val text =
                        "Öffne LUV und tippe auf Beitreten. Unser Code:\n\n$code"
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
                        context.startActivity(Intent.createChooser(fallback, "Code teilen"))
                    }
                },
                onAddWidgetHelp = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                },
                onInstallUpdate = { startUpdateFlow() },
                onDisconnect = {
                    scope.launch {
                        PairConnectionService.stop(context)
                        prefs.clearPairing()
                        navController.navigate(Routes.ROLE) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
