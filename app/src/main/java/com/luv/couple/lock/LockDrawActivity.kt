package com.luv.couple.lock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.luv.couple.ui.applyPortraitOnPhonesOnly
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luv.couple.LuvApp
import com.luv.couple.R
import com.luv.couple.net.GoogleAuth
import com.luv.couple.ui.security.PlayIntegrityGate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.luv.couple.data.DrawTemplate
import com.luv.couple.data.LocalMoment
import com.luv.couple.data.LocalMoments
import com.luv.couple.data.PeerInfo
import com.luv.couple.data.PeerPalette
import com.luv.couple.data.TemplateStrokePart
import com.luv.couple.data.asCleanJsonString
import com.luv.couple.net.AccountSession
import com.luv.couple.net.ClearVoteEvent
import com.luv.couple.net.LuvApiClient
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairEvent
import com.luv.couple.net.PairSessionState
import com.luv.couple.net.PendingInviteRejoin
import com.luv.couple.net.PendingOnboardingRestart
import com.luv.couple.net.PendingTutorialKeepAuth
import com.luv.couple.net.PublicVoteEvent
import com.luv.couple.notify.LiveProximity
import com.luv.couple.shop.ShopCatalog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luv.couple.ui.ItemImageCache
import com.luv.couple.ui.isImagePetId
import com.luv.couple.ui.screens.BrushStudioMode
import com.luv.couple.ui.screens.BrushStudioSheet
import com.luv.couple.ui.screens.EmojiBarEditorDialog
import com.luv.couple.ui.screens.ForcedUpdateDialog
import com.luv.couple.ui.screens.ForcedMaintenanceDialog
import com.luv.couple.ui.screens.ProfileCanvasScreen
import com.luv.couple.ui.screens.requiresForcedUpdate
import com.luv.couple.ui.theme.LuvTheme
import com.luv.couple.update.AppUpdater
import com.luv.couple.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class LockDrawActivity : ComponentActivity() {
    private lateinit var drawingView: DrawingView
    private lateinit var statusView: TextView
    private lateinit var missedBanner: TextView
    private lateinit var reactionBurst: TextView
    private lateinit var legendRow: LinearLayout
    private lateinit var lobbyTitle: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnSave: TextView
    private lateinit var btnTemplates: TextView
    private lateinit var btnClear: TextView
    private lateinit var btnEraser: TextView
    private lateinit var btnColor: TextView
    private lateinit var btnBrush: TextView
    private lateinit var bottomDock: View
    private lateinit var reactionPanel: View
    private lateinit var btnReactionToggle: TextView
    private lateinit var reactionFlyout: View
    private lateinit var reactionEmojiList: LinearLayout
    private var trialOverlay: FrameLayout? = null
    private var trialDrawUntil: Long = 0L
    private var trialGateActive: Boolean = false
    private var trialExitDone: Boolean = false
    private val trialGoogleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        lifecycleScope.launch {
            try {
                val google = GoogleAuth.parseSignInIntentResult(result.data, result.resultCode)
                val attestation = runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                        PlayIntegrityGate.attestForSignup(this@LockDrawActivity)
                    }
                }.getOrNull()
                val auth = LuvApiClient.authGoogle(
                    idToken = google.idToken,
                    integrityToken = attestation?.integrityToken,
                    integrityNonce = attestation?.nonce
                )
                LuvApiClient.sessionToken = auth.sessionToken
                val nick = auth.user.nickname.trim()
                val returning = !auth.created &&
                    nick.length >= 2 &&
                    !nick.equals("Gast", ignoreCase = true) &&
                    !nick.equals("Luv", ignoreCase = true)
                LuvApp.instance.prefs.saveSession(
                    auth.sessionToken,
                    auth.user,
                    applyNickname = returning
                )
                AccountSession.setAccount(auth.user)
                runCatching { performTrialExit() }
                val code = currentLobbyCode()
                if (!code.isNullOrBlank()) PendingInviteRejoin.offer(code)
                // Nach Probezeit nicht wieder freigeben — Overlay weg, Zeichnen bleibt aus
                trialGateActive = true
                drawingView.inputBlocked = true
                trialOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                trialOverlay = null
                if (returning) {
                    LuvApp.instance.prefs.setTutorialDone(true)
                    Toast.makeText(
                        this@LockDrawActivity,
                        "Angemeldet — wir holen dich zurück ♥",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    LuvApp.instance.prefs.setTutorialDone(false)
                    PendingTutorialKeepAuth.offer()
                    Toast.makeText(
                        this@LockDrawActivity,
                        "Kurz Name wählen — dann geht’s weiter ♥",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                finish()
            } catch (e: Exception) {
                if (e is com.luv.couple.net.LuvApiException && e.error == "cancelled") {
                    Toast.makeText(this@LockDrawActivity, "Abgebrochen", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@LockDrawActivity,
                        e.message ?: "Google-Anmeldung fehlgeschlagen",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private lateinit var btnEmojiEdit: TextView
    private lateinit var btnWeddingConfirm: TextView
    private lateinit var stickerOverlay: FrameLayout
    private var reactionExpanded = false
    private var eraserOn = false
    private var weddingRetakeActive = false
    private var weddingConfirmMine = false
    private var weddingConfirmPartner = false
    private var weddingConfirmBusy = false
    private var eventLobbyActive = false
    /** Hochzeitsbild: Farben frei wechseln wie Event-Lobby. */
    private var weddingLobbyActive = false
    private var eventEndsAtMs: Long? = null
    private var eventPromptText: String? = null
    private var eventPromptChoices: List<String> = emptyList()
    private var eventPromptPickShown = false
    private var emojiEditorHost: FrameLayout? = null
    private var brushStudioHost: FrameLayout? = null
    private var profileHost: FrameLayout? = null
    private var templatesHost: FrameLayout? = null
    private var templateEditorHost: FrameLayout? = null
    private var templatePlacementView: TemplatePlacementView? = null
    private lateinit var gameHud: LinearLayout
    private lateinit var gameHudTitle: TextView
    private lateinit var gameHudSubtitle: TextView
    private lateinit var btnGuess: TextView
    private lateinit var gamePlayOverlay: GamePlayOverlay
    private var voteOverlay: View? = null
    private var voteTitleView: TextView? = null
    private var voteByView: TextView? = null
    private var voteProgressView: TextView? = null
    private var voteButtonsRow: View? = null
    private var confirmClearOverlay: View? = null
    private var lobbyId: String? = null
    private var activeProposalId: String? = null
    /** "clear" oder "public" */
    private var voteKind: String = "clear"
    private var rootView: FrameLayout? = null
    private var activeOverlayGame: String? = null
    private var playGameActive = false
    private var playGameType: String? = null
    private var wordsGameActive = false
    private var wordsStatus: String = ""
    private var drawerNickname: String? = null
    private var iAmDrawer = false
    private var secretWord: String? = null
    private var roundEndsAt: Long = 0L
    /** nickname → Tipps (älteste zuerst, neueste oben in der Blase) */
    private val guessBubbles = linkedMapOf<String, MutableList<Pair<String, Boolean>>>()
    private val correctGuessers = mutableSetOf<String>()
    private var hasVotedClear = false
    private var forcedUpdateHost: FrameLayout? = null
    private var maintenanceHost: FrameLayout? = null
    private var maintenanceHold: Boolean = false
    private val maintenanceUi = mutableStateOf<LuvApiClient.MaintenanceStatus?>(null)
    private var strokeMemoryView: TextView? = null
    private var myPetEmoji: String = ShopCatalog.DEFAULT_PET
    private val statusHideRunnable = Runnable {
        statusView.animate().alpha(0f).setDuration(180).withEndAction {
            statusView.visibility = View.GONE
        }.start()
    }
    private val wordsTimerTick = object : Runnable {
        override fun run() {
            if (!wordsGameActive || wordsStatus != "draw" || roundEndsAt <= 0L) return
            refreshGameHud()
            if (System.currentTimeMillis() < roundEndsAt) {
                gameHud.postDelayed(this, 250L)
            }
        }
    }
    private val playClockTick = object : Runnable {
        override fun run() {
            if (!playGameActive) return
            gamePlayOverlay.tickClock()
            gamePlayOverlay.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyPortraitOnPhonesOnly()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        setContentView(R.layout.activity_lock_draw)

        val root = findViewById<android.widget.FrameLayout>(R.id.lockRoot)
        rootView = root
        drawingView = findViewById(R.id.drawingView)
        statusView = findViewById(R.id.statusDot)
        missedBanner = findViewById(R.id.missedBanner)
        reactionBurst = findViewById(R.id.reactionBurst)
        legendRow = findViewById(R.id.legendRow)
        lobbyTitle = findViewById(R.id.lobbyTitle)
        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        btnTemplates = findViewById(R.id.btnTemplates)
        btnClear = findViewById(R.id.btnClear)
        btnEraser = findViewById(R.id.btnEraser)
        btnColor = findViewById(R.id.btnColor)
        btnBrush = findViewById(R.id.btnBrush)
        bottomDock = findViewById(R.id.bottomDock)
        reactionPanel = findViewById(R.id.reactionPanel)
        btnReactionToggle = findViewById(R.id.btnReactionToggle)
        reactionFlyout = findViewById(R.id.reactionFlyout)
        reactionEmojiList = findViewById(R.id.reactionEmojiList)
        btnEmojiEdit = findViewById(R.id.btnEmojiEdit)
        btnWeddingConfirm = findViewById(R.id.btnWeddingConfirm)
        stickerOverlay = findViewById(R.id.stickerOverlay)
        stickerOverlay.isClickable = false
        stickerOverlay.isFocusable = false
        gamePlayOverlay = findViewById(R.id.gamePlayOverlay)
        gamePlayOverlay.onAction = { action, payload ->
            PairConnectionService.sendGameAction(this, action, payload, lobbyId)
        }
        gamePlayOverlay.onClose = {
            PairConnectionService.sendGameStop(this, lobbyId)
            stopAllGamesLocal()
        }
        gameHud = findViewById(R.id.gameHud)
        gameHudTitle = findViewById(R.id.gameHudTitle)
        gameHudSubtitle = findViewById(R.id.gameHudSubtitle)
        btnGuess = findViewById(R.id.btnGuess)

        lobbyId = intent.getStringExtra(EXTRA_LOBBY_ID)
        trialDrawUntil = intent.getLongExtra(EXTRA_TRIAL_DRAW_UNTIL, 0L)
        if (trialDrawUntil <= 0L) {
            trialDrawUntil = AccountSession.account.value?.trialDrawUntil ?: 0L
        }
        lifecycleScope.launch {
            runCatching { LuvApiClient.pingAchievement("lobby_opens") }
        }
            ?: CanvasStore.activeLobbyId.value
        lobbyId?.let { CanvasStore.setActiveLobby(it) }

        val bg = CanvasStore.backgroundFor(CanvasStore.cachedColorIndex)
        root.setBackgroundColor(bg)
        drawingView.canvasBackground = bg
        drawingView.myColorIndex = CanvasStore.cachedColorIndex
        drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
        paintEraserButton()
        setupTrialGate()

        btnBack.setOnClickListener { leaveCanvas() }
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    leaveCanvas()
                }
            }
        )
        btnColor.setOnClickListener {
            if (eraserOn) setEraserEnabled(false)
            openBrushStudio(BrushStudioMode.COLOR)
        }
        btnBrush.setOnClickListener {
            if (eraserOn) setEraserEnabled(false)
            openBrushStudio(BrushStudioMode.THICKNESS)
        }
        btnEraser.setOnClickListener { setEraserEnabled(!eraserOn) }
        btnSave.setOnClickListener {
            lifecycleScope.launch {
                CanvasCapture.saveMoment(this@LockDrawActivity, lobbyId)
                    .onSuccess {
                        flashStatus(getString(R.string.moment_saved))
                        runCatching { LuvApiClient.pingAchievement("moments_saved") }
                    }
                    .onFailure {
                        flashStatus(it.message ?: "Fehler")
                    }
            }
        }
        btnTemplates.setOnClickListener { openTemplatesBrowser() }
        btnClear.setOnClickListener { confirmClearCanvas() }
        btnGuess.setOnClickListener {
            CanvasGameUi.showGuessDialog(this) { text ->
                PairConnectionService.sendGameGuess(this, text, lobbyId)
            }
        }
        btnReactionToggle.setOnClickListener {
            reactionExpanded = !reactionExpanded
            reactionFlyout.visibility = if (reactionExpanded) View.VISIBLE else View.GONE
            if (reactionExpanded) bindReactionBar()
        }
        btnEmojiEdit.setOnClickListener { openEmojiBarEditor() }
        bindReactionBar()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val dp = resources.displayMetrics.density
            val pad = (12 * dp).toInt()
            (lobbyTitle.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = bars.top + pad
                lobbyTitle.layoutParams = this
            }
            (reactionPanel.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = bars.top + (8 * dp).toInt()
                reactionPanel.layoutParams = this
            }
            if (::btnWeddingConfirm.isInitialized) {
                (btnWeddingConfirm.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin = bars.top + (8 * dp).toInt()
                    btnWeddingConfirm.layoutParams = this
                }
            }
            (gameHud.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = bars.top + (48 * dp).toInt()
                gameHud.layoutParams = this
            }
            bottomDock.setPadding(
                (10 * dp).toInt(),
                (8 * dp).toInt(),
                (10 * dp).toInt(),
                bars.bottom + (8 * dp).toInt()
            )
            statusView.visibility = View.GONE
            // Leinwand und Avatare enden über dem Button-Kasten
            bottomDock.post { applyPortraitCanvasLetterbox(root) }
            insets
        }
        // Querformat / Größenwechsel: künstliches Hochformat neu berechnen
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (::bottomDock.isInitialized && ::drawingView.isInitialized) {
                bottomDock.post { applyPortraitCanvasLetterbox(root) }
            }
        }

        lifecycleScope.launch {
            val snap = withContext(Dispatchers.IO) { LuvApp.instance.prefs.snapshot() }
            if (lobbyId == null) {
                lobbyId = snap.activeLobbyId ?: snap.lobbies.firstOrNull()?.id
                lobbyId?.let { CanvasStore.setActiveLobby(it) }
            }
            lobbyId?.let { id ->
                withContext(Dispatchers.IO) { LuvApp.instance.prefs.setActiveLobby(id) }
            }
            val brushW = withContext(Dispatchers.IO) { LuvApp.instance.prefs.brushWidth() }
            CanvasStore.updateBrushWidth(brushW)
            if (::drawingView.isInitialized) drawingView.myBrushWidth = brushW
            CanvasStore.updateKnownLobbies(snap.lobbies.map { it.id })
            val lobby = snap.lobbies.firstOrNull { it.id == lobbyId }
            // Farbe je Lobby (Code) — nie die Farbe einer anderen Lobby übernehmen
            val lobbyKey = lobby?.code?.takeIf { it.isNotBlank() } ?: lobbyId
            val myColor = withContext(Dispatchers.IO) {
                val saved = lobbyKey?.let { LuvApp.instance.prefs.colorIndexForLobby(it) }
                when {
                    saved != null -> saved
                    else -> {
                        val seed = snap.colorIndex
                        lobbyKey?.let { LuvApp.instance.prefs.setColorIndexForLobby(it, seed) }
                        seed
                    }
                }
            }
            CanvasStore.updateProfile(snap.nickname, myColor)
            eventLobbyActive = lobby?.isEventLobby == true
            weddingLobbyActive = lobby?.isWedding == true
            eventEndsAtMs = lobby?.eventEndsAt.asCleanJsonString()?.let { iso ->
                runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
            }
            eventPromptText = lobby?.eventPrompt.asCleanJsonString()
            eventPromptChoices = lobby?.eventPromptChoices.orEmpty()
            lobbyTitle.text = when {
                eventLobbyActive -> "Event"
                weddingLobbyActive -> lobby?.name?.ifBlank { "Hochzeitsbild" } ?: "Hochzeitsbild"
                else -> lobby?.name.orEmpty()
            }
            lobbyTitle.maxLines = if (eventLobbyActive) 2 else 1
            lobbyTitle.ellipsize = android.text.TextUtils.TruncateAt.END
            if (eventLobbyActive) {
                btnTemplates.visibility = View.GONE
                setupEventLobbyUi()
                maybeShowEventPromptPick(lobby)
            }
            weddingRetakeActive = lobby?.isWeddingRetake == true ||
                (lobby?.isWedding == true && lobby.name.contains("Hochzeitsbild", ignoreCase = true))
            setupWeddingConfirmUi()
            applyMyColor(myColor, persist = false, sync = false)
            // Eigene Historie an aktuelle Farbe anpassen (sonst gemischte Farben bis zum Picker)
            CanvasStore.recolorOwnStrokes(myColor, lobbyId, broadcast = false)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
            refreshLegend()
            showLastStrokeMemory(lobbyId, lobby)
            lobby?.let { CanvasMemoryKeeper.touch(it) }
        }

        MidnightClear.checkAndClearIfNewDay(this)
        refreshLegend()
        observeForcedUpdates()
        observeMaintenance()

        drawingView.onStrokeFinished = { points ->
            val shortSide = min(drawingView.width, drawingView.height)
                .toFloat()
                .coerceAtLeast(1f)
            CanvasStore.addLocalStroke(
                points,
                width = drawingView.myBrushWidth,
                lobbyId = lobbyId,
                shortSidePx = shortSide
            )
            // Kein Fade: eigener Strich sofort sichtbar (sonst wirkt die Leinwand kurz „schwarz“)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
            refreshLegend()
        }
        drawingView.onDoubleTapUndo = {
            if (CanvasStore.undoLastLocalStroke(lobbyId)) {
                drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
                flashStatus("Rückgängig")
            }
        }
        drawingView.onDotPlaced = { point ->
            val shortSide = min(drawingView.width, drawingView.height)
                .toFloat()
                .coerceAtLeast(1f)
            CanvasStore.addLocalDot(
                point.x,
                point.y,
                lobbyId = lobbyId,
                shortSidePx = shortSide
            )
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
        }
        drawingView.onEraseGestureStart = {
            CanvasStore.beginEraseSession(lobbyId)
        }
        drawingView.onErasePath = { brush ->
            val radius = CanvasStore.eraseRadiusForBrush(drawingView.myBrushWidth)
            if (CanvasStore.eraseLocalAlong(brush, radius = radius, lobbyId = lobbyId, broadcast = false)) {
                drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
                refreshLegend()
            }
        }
        drawingView.onEraseGestureEnd = {
            CanvasStore.endEraseSession(lobbyId)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
            refreshLegend()
        }

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        lifecycleScope.launch {
            CanvasStore.revision.collectLatest {
                val id = lobbyId ?: return@collectLatest
                drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                refreshLegend()
            }
        }
        lifecycleScope.launch {
            // collect (nicht collectLatest): Striche dürfen nicht verworfen werden,
            // wenn der nächste Event schon ankommt.
            var lastRemoteUndoAt = 0L
            PairConnectionService.events.collect { event ->
                val id = lobbyId ?: return@collect
                when (event) {
                    is PairEvent.StrokeReceived -> if (event.lobbyId == id) {
                        // Nach Undo/Erase-Batch: kein Fade/Haptic — sonst Flackern + Vibrations-Spam
                        val quiet =
                            SystemClock.uptimeMillis() - lastRemoteUndoAt < 1600L
                        if (quiet) {
                            drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                        } else {
                            drawingView.addStroke(event.stroke, fadeIn = true)
                            maybeHaptic()
                        }
                        refreshLegend()
                    }
                    is PairEvent.StrokeUndone -> if (event.lobbyId == id) {
                        lastRemoteUndoAt = SystemClock.uptimeMillis()
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                    }
                    is PairEvent.EraseCommitReceived -> if (event.lobbyId == id) {
                        lastRemoteUndoAt = SystemClock.uptimeMillis()
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                        refreshLegend()
                    }
                    is PairEvent.HistoryApplied -> if (event.lobbyId == id) {
                        // Nur umfärben falls nötig — kein zweites Full-Redraw
                        // (revision-Collector hat die History schon gezeichnet, wenn sie sich änderte)
                        CanvasStore.recolorOwnStrokes(
                            CanvasStore.cachedColorIndex,
                            id,
                            broadcast = false
                        )
                        refreshLegend()
                    }
                    is PairEvent.ColorAssigned -> if (event.lobbyId == id) {
                        // Nur UI/Sync — Persistenz schon pro Lobby in PairConnectionService
                        applyMyColor(event.colorIndex, persist = false, sync = false)
                    }
                    is PairEvent.Cleared -> if (event.lobbyId == id) {
                        drawingView.clearCanvas()
                        stopAllGamesLocal()
                    }
                    is PairEvent.RecolorReceived -> if (event.lobbyId == id) {
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                        refreshLegend()
                    }
                    is PairEvent.ReactionReceived -> if (event.lobbyId == id) {
                        showReaction(event.emoji)
                    }
                    is PairEvent.StickerPlaced -> if (event.lobbyId == id) {
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = true)
                    }
                    is PairEvent.StickerRemoved -> if (event.lobbyId == id) {
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                    }
                    is PairEvent.StickersHistory -> if (event.lobbyId == id) {
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                    }
                    is PairEvent.GameBoardReceived -> if (event.lobbyId == id) {
                        applyOverlayBoard(event.game, event.visible)
                    }
                    is PairEvent.GameState -> if (event.lobbyId == id) {
                        onGameState(event)
                    }
                    is PairEvent.GamePlay -> if (event.lobbyId == id) {
                        onGamePlay(event.game)
                    }
                    is PairEvent.GameStopped -> if (event.lobbyId == id) {
                        stopAllGamesLocal()
                    }
                    is PairEvent.GameWordsPick -> if (event.lobbyId == id) {
                        clearWordsRoundUi()
                        iAmDrawer = true
                        wordsGameActive = true
                        wordsStatus = "pick"
                        drawerNickname = event.drawerNickname
                        secretWord = null
                        roundEndsAt = 0L
                        refreshGameHud()
                        refreshLegend()
                        CanvasGameUi.showWordPick(this@LockDrawActivity, event.options) { word ->
                            PairConnectionService.sendGamePick(this@LockDrawActivity, word, lobbyId)
                        }
                    }
                    is PairEvent.GameWordsSecret -> if (event.lobbyId == id) {
                        secretWord = event.word
                        iAmDrawer = true
                        wordsGameActive = true
                        wordsStatus = "draw"
                        if (event.endsAt > 0L) roundEndsAt = event.endsAt
                        startWordsTimer()
                        refreshGameHud()
                        flashStatus("Wort: ${event.word}")
                    }
                    is PairEvent.GameWordsCorrect -> if (event.lobbyId == id) {
                        wordsStatus = "done"
                        roundEndsAt = 0L
                        gameHud.removeCallbacks(wordsTimerTick)
                        val winner = event.winner.trim()
                        if (winner.isNotBlank()) correctGuessers.add(winner.lowercase(Locale.getDefault()))
                        flashStatus("${event.winner} hat „${event.word}“ erraten!")
                        refreshGameHud()
                        refreshLegend()
                    }
                    is PairEvent.GameWordsTimeout -> if (event.lobbyId == id) {
                        wordsStatus = "timeout"
                        roundEndsAt = 0L
                        gameHud.removeCallbacks(wordsTimerTick)
                        flashStatus("Zeit um! Wort war: ${event.word}")
                        refreshGameHud()
                    }
                    is PairEvent.GameGuessChat -> if (event.lobbyId == id) {
                        onGuessChat(event.nickname, event.text, event.correct)
                    }
                    is PairEvent.GameGuessResult -> if (event.lobbyId == id) {
                        // Chat-Blasen kommen über game_guess_chat; hier nur kurze Bestätigung
                        if (event.ok) flashStatus("Richtig!")
                    }
                    is PairEvent.LobbyGone -> if (event.lobbyId == id) {
                        if (weddingRetakeActive) {
                            Toast.makeText(
                                this@LockDrawActivity,
                                "Hochzeitsbild gespeichert",
                                Toast.LENGTH_SHORT
                            ).show()
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { LuvApp.instance.prefs.removeLobby(id) }
                                }
                                finish()
                            }
                        } else {
                            flashStatus("Verbindung wird wiederhergestellt…")
                            PairConnectionService.reconnectNow(this@LockDrawActivity, id)
                        }
                    }
                    is PairEvent.CanvasTaken -> if (event.lobbyId == id) {
                        // Nur Leinwand verlassen — Lobby-WS bleibt verbunden
                        finish()
                    }
                    is PairEvent.WeddingConfirm -> if (event.lobbyId == id) {
                        val myId = AccountSession.account.value?.id
                        if (myId != null && event.confirms.isNotEmpty()) {
                            weddingConfirmMine = event.confirms[myId] == true
                            weddingConfirmPartner = event.confirms.any { (uid, ok) ->
                                uid != myId && ok
                            }
                            paintWeddingConfirmButton()
                            if (weddingConfirmMine && weddingConfirmPartner) {
                                flashStatus("Beide bestätigt — speichere Bild…")
                            } else if (event.fromUserId != null && event.fromUserId != myId) {
                                flashStatus(
                                    if (weddingConfirmPartner) "Partner hat bestätigt ✓"
                                    else "Partner wartet auf dich"
                                )
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            var id = lobbyId
            var tries = 0
            while (id == null && tries < 20) {
                kotlinx.coroutines.delay(50)
                id = lobbyId
                tries++
            }
            val readyId = id ?: return@launch
            PairSessionState.peers(readyId).collectLatest {
                refreshLegend()
            }
        }
        // Begleiter-Wechsel aus Profil/Inventar sofort in der Legende
        lifecycleScope.launch {
            LuvApp.instance.prefs.equippedPetFlow.collectLatest { pet ->
                val next = pet.trim().ifBlank { ShopCatalog.DEFAULT_PET }
                if (next != myPetEmoji) {
                    myPetEmoji = next
                    refreshLegend()
                }
            }
        }
        lifecycleScope.launch {
            var id = lobbyId
            var tries = 0
            while (id == null && tries < 20) {
                kotlinx.coroutines.delay(50)
                id = lobbyId
                tries++
            }
            val readyId = id ?: return@launch
            var previousPeers = PairSessionState.peerCount(readyId).value
            PairSessionState.peerCount(readyId).collectLatest { peers ->
                // Solo → zu zweit: bisherige Farben einfrieren
                if (previousPeers <= 1 && peers > 1) {
                    CanvasStore.lockOwnStrokeColors(readyId)
                    if (::drawingView.isInitialized) {
                        drawingView.setStrokes(CanvasStore.snapshot(readyId), animateNew = false)
                    }
                }
                previousPeers = peers
            }
        }
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1200)
                if (LiveProximity.isLobbyHot(lobbyId)) refreshLegend()
            }
        }
        lifecycleScope.launch {
            PairSessionState.missedYou.collectLatest {
                showBanner(missedBanner, getString(R.string.missed_you), 2800)
            }
        }
        lifecycleScope.launch {
            AccountSession.clearVotes.collectLatest { event ->
                val id = lobbyId ?: return@collectLatest
                when (event) {
                    is ClearVoteEvent.Open -> if (event.lobbyId == id) {
                        handleClearVoteOpen(event)
                    }
                    is ClearVoteEvent.Update -> if (event.lobbyId == id && voteKind == "clear") {
                        if (voteOverlay?.visibility == View.VISIBLE) {
                            updateClearVote(event.yes, event.no, event.total)
                        }
                    }
                    is ClearVoteEvent.Result -> if (event.lobbyId == id) {
                        if (voteKind == "clear") hideClearVote()
                        flashStatus(if (event.approved) "Leinwand leer" else "Abgelehnt")
                        if (event.approved) {
                            // Auch ohne separate {type:clear}-Message lokal leeren
                            CanvasStore.clear(localOnly = true, lobbyId = id)
                            drawingView.clearCanvas()
                            stopAllGamesLocal()
                        }
                        if (voteKind == "clear") {
                            activeProposalId = null
                            hasVotedClear = false
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            AccountSession.publicVotes.collectLatest { event ->
                val id = lobbyId ?: return@collectLatest
                when (event) {
                    is PublicVoteEvent.Open -> if (event.lobbyId == id) {
                        handlePublicVoteOpen(event)
                    }
                    is PublicVoteEvent.Update -> if (event.lobbyId == id && voteKind == "public") {
                        // Initiator sieht kein Overlay — nur Fortschritt im Status optional
                        if (voteOverlay?.visibility == View.VISIBLE) {
                            updateClearVote(event.yes, event.no, event.total)
                        }
                    }
                    is PublicVoteEvent.Result -> if (event.lobbyId == id) {
                        if (voteKind == "public") hideClearVote()
                        val restarted = event.reason.equals("restarted", ignoreCase = true)
                        if (!restarted) {
                            flashStatus(
                                when {
                                    event.approved && event.rewardCoins > 0 ->
                                        "Öffentlich · +${event.rewardCoins} Coins"
                                    event.approved -> "Öffentlich geteilt"
                                    else -> "Abgelehnt"
                                }
                            )
                        }
                        if (voteKind == "public") {
                            activeProposalId = null
                            hasVotedClear = false
                        }
                    }
                    is PublicVoteEvent.CaptureRequest -> Unit
                }
            }
        }
        lifecycleScope.launch {
            AccountSession.economyBlocks.collectLatest { msg ->
                if (msg.contains("coin", ignoreCase = true) || msg.contains("Coin")) {
                    com.luv.couple.ui.NoCoinsUi.show(this@LockDrawActivity)
                } else {
                    Toast.makeText(this@LockDrawActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupEventLobbyUi() {
        updateEventLobbyTitle()
        val ends = eventEndsAtMs ?: return
        lifecycleScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= ends) {
                    flashStatus("Event beendet")
                    val id = lobbyId
                    withContext(Dispatchers.IO) {
                        if (id != null) {
                            val lobby = LuvApp.instance.prefs.snapshot()
                                .lobbies.firstOrNull { it.id == id }
                            if (lobby != null) {
                                // Snapshot zuerst, dann Server schließt Contest (kein leaveRoom vorher)
                                runCatching { CanvasMemoryKeeper.uploadSnapshot(lobby) }
                                runCatching {
                                    LuvApiClient.closeEventLobby(lobby.code, lobby.token)
                                }
                                runCatching { LuvApp.instance.prefs.removeLobby(id) }
                            }
                        }
                    }
                    finish()
                    return@launch
                }
                updateEventLobbyTitle()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun maybeShowEventPromptPick(lobby: com.luv.couple.data.Lobby?) {
        if (!eventLobbyActive || lobby == null) return
        if (eventPromptPickShown) return
        val existing = eventPromptText?.trim().orEmpty()
        if (existing.isNotBlank()) return
        val choices = eventPromptChoices.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (choices.size < 2) return
        eventPromptPickShown = true
        if (::drawingView.isInitialized) drawingView.inputBlocked = true
        CanvasGameUi.showWordPick(
            this,
            choices,
            title = "Wähle einen Begriff"
        ) { word ->
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        LuvApiClient.setEventPrompt(lobby.code, lobby.token, word)
                    }.getOrNull()
                }
                if (ok.isNullOrBlank()) {
                    eventPromptPickShown = false
                    Toast.makeText(
                        this@LockDrawActivity,
                        "Begriff konnte nicht gespeichert werden",
                        Toast.LENGTH_SHORT
                    ).show()
                    maybeShowEventPromptPick(lobby)
                    return@launch
                }
                eventPromptText = ok
                eventPromptChoices = emptyList()
                withContext(Dispatchers.IO) {
                    LuvApp.instance.prefs.upsertLobby(
                        lobby.copy(
                            eventPrompt = ok,
                            eventPromptChoices = emptyList()
                        )
                    )
                }
                if (::drawingView.isInitialized) drawingView.inputBlocked = false
                updateEventLobbyTitle()
                flashStatus("Begriff: $ok")
            }
        }
    }

    private fun updateEventLobbyTitle() {
        if (!eventLobbyActive) return
        lobbyTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        val prompt = eventPromptText?.trim().orEmpty()
        val countdown = formatEventLobbyCountdownLabel(eventEndsAtMs)
        lobbyTitle.text = if (prompt.isNotBlank()) {
            "Event · $countdown\nBegriff · $prompt"
        } else {
            "Event · $countdown"
        }
    }

    private fun formatEventLobbyCountdownLabel(endsMs: Long?): String {
        if (endsMs == null) return "aktiv"
        val diff = endsMs - System.currentTimeMillis()
        if (diff <= 0L) return "beendet"
        val totalMin = (diff / 60_000L).coerceAtLeast(0L)
        val days = totalMin / (60 * 24)
        val hours = (totalMin % (60 * 24)) / 60
        val mins = totalMin % 60
        return when {
            days >= 1L -> "${days}t ${hours}h ${mins}m"
            hours >= 1L -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    private fun setupWeddingConfirmUi() {
        if (!::btnWeddingConfirm.isInitialized) return
        if (!weddingRetakeActive) {
            btnWeddingConfirm.visibility = View.GONE
            return
        }
        btnWeddingConfirm.visibility = View.VISIBLE
        btnWeddingConfirm.setOnClickListener { onWeddingConfirmTap() }
        paintWeddingConfirmButton()
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) {
                runCatching { LuvApiClient.fetchWeddingImageConfirm() }.getOrNull()
            } ?: return@launch
            if (!state.weddingImageRetake && !weddingRetakeActive) {
                btnWeddingConfirm.visibility = View.GONE
                return@launch
            }
            weddingRetakeActive = true
            weddingConfirmMine = state.weddingConfirmMine
            weddingConfirmPartner = state.weddingConfirmPartner
            btnWeddingConfirm.visibility = View.VISIBLE
            paintWeddingConfirmButton()
            if (state.done) {
                Toast.makeText(this@LockDrawActivity, "Hochzeitsbild gespeichert", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun paintWeddingConfirmButton() {
        if (!::btnWeddingConfirm.isInitialized) return
        val on = weddingConfirmMine
        btnWeddingConfirm.setBackgroundResource(
            if (on) R.drawable.wedding_confirm_circle_on else R.drawable.wedding_confirm_circle
        )
        btnWeddingConfirm.setTextColor(
            if (on) 0xFFFFFFFF.toInt() else 0xFF43A047.toInt()
        )
        btnWeddingConfirm.alpha = if (weddingConfirmBusy) 0.55f else 1f
        btnWeddingConfirm.contentDescription = when {
            weddingConfirmMine && weddingConfirmPartner -> "Beide bestätigt"
            weddingConfirmMine -> "Du hast bestätigt — warte auf Partner"
            else -> "Hochzeitsbild bestätigen"
        }
    }

    private fun onWeddingConfirmTap() {
        if (!weddingRetakeActive || weddingConfirmBusy) return
        val id = lobbyId ?: return
        weddingConfirmBusy = true
        paintWeddingConfirmButton()
        lifecycleScope.launch {
            try {
                val lobby = withContext(Dispatchers.IO) {
                    LuvApp.instance.prefs.snapshot().lobbies.firstOrNull { it.id == id }
                }
                if (lobby != null) {
                    CanvasMemoryKeeper.uploadSnapshot(lobby)
                }
                val result = withContext(Dispatchers.IO) {
                    LuvApiClient.confirmWeddingImage(confirm = true)
                }
                weddingConfirmMine = result.weddingConfirmMine
                weddingConfirmPartner = result.weddingConfirmPartner
                paintWeddingConfirmButton()
                when {
                    result.done -> {
                        Toast.makeText(
                            this@LockDrawActivity,
                            result.message ?: "Hochzeitsbild gespeichert",
                            Toast.LENGTH_SHORT
                        ).show()
                        withContext(Dispatchers.IO) {
                            runCatching { LuvApp.instance.prefs.removeLobby(id) }
                        }
                        finish()
                    }
                    result.weddingConfirmMine && !result.weddingConfirmPartner -> {
                        flashStatus("Warte auf Partner-Bestätigung…")
                    }
                    else -> flashStatus(result.message ?: "Bestätigt")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LockDrawActivity,
                    e.message ?: "Bestätigen fehlgeschlagen",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                weddingConfirmBusy = false
                paintWeddingConfirmButton()
            }
        }
    }

    private fun bindReactionBar() {
        lifecycleScope.launch {
            myPetEmoji = withContext(Dispatchers.IO) {
                runCatching { LuvApp.instance.prefs.equippedPet() }
                    .getOrDefault(ShopCatalog.DEFAULT_PET)
            }
            val bar = withContext(Dispatchers.IO) {
                runCatching { LuvApp.instance.prefs.emojiBar() }.getOrDefault(ShopCatalog.DEFAULT_BAR)
            }
            reactionEmojiList.removeAllViews()
            val dp = resources.displayMetrics.density
            bar.forEachIndexed { index, emoji ->
                val cell = createReactionBarCell(emoji, index, dp)
                attachEmojiGesture(cell, emoji)
                reactionEmojiList.addView(cell)
            }
        }
    }

    private fun createReactionBarCell(emoji: String, index: Int, dp: Float): View {
        val lp = LinearLayout.LayoutParams(
            (44 * dp).toInt(),
            (40 * dp).toInt()
        ).apply {
            topMargin = if (index == 0) (4 * dp).toInt() else 0
            bottomMargin = (3 * dp).toInt()
        }
        return if (isImagePetId(emoji)) {
            FrameLayout(this).apply {
                layoutParams = lp
                setBackgroundResource(R.drawable.lock_emoji_btn)
                val pad = (6 * dp).toInt()
                val img = ImageView(this@LockDrawActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ).apply { setMargins(pad, pad, pad, pad) }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
                addView(img)
                bindItemImage(img, emoji)
            }
        } else {
            TextView(this).apply {
                text = emoji
                gravity = Gravity.CENTER
                textSize = 22f
                layoutParams = lp
                setBackgroundResource(R.drawable.lock_emoji_btn)
            }
        }
    }

    private fun bindItemImage(img: ImageView, itemId: String) {
        val cached = ItemImageCache.get(itemId)
        if (cached != null) {
            img.setImageBitmap(cached)
            return
        }
        ItemImageCache.preload(itemId) {
            ItemImageCache.get(itemId)?.let { img.setImageBitmap(it) }
        }
    }

    private fun attachEmojiGesture(view: View, emoji: String) {
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false
        var ghost: View? = null
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    v.isPressed = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && dx * dx + dy * dy > slop * slop) {
                        dragging = true
                        v.isPressed = false
                        v.alpha = 0.35f
                        ghost = createBarDragGhost(emoji, event.rawX, event.rawY)
                    }
                    if (dragging) {
                        moveBarDragGhost(ghost, event.rawX, event.rawY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    v.alpha = 1f
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (dragging) {
                        placeEmojiAtScreen(emoji, event.rawX, event.rawY)
                    } else {
                        sendReaction(emoji)
                    }
                    removeBarDragGhost(ghost)
                    ghost = null
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.alpha = 1f
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    removeBarDragGhost(ghost)
                    ghost = null
                    dragging = false
                    true
                }
                else -> true
            }
        }
    }

    private fun createBarDragGhost(emoji: String, rawX: Float, rawY: Float): View? {
        val root = rootView ?: return null
        val size = stickerSizePx()
        val ghost: View = if (isImagePetId(emoji)) {
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                elevation = 48f
                alpha = 0.95f
                layoutParams = FrameLayout.LayoutParams(size, size)
                bindItemImage(this, emoji)
            }
        } else {
            TextView(this).apply {
                text = emoji
                gravity = Gravity.CENTER
                textSize = 36f
                elevation = 48f
                alpha = 0.95f
                setShadowLayer(12f, 0f, 4f, 0x66000000)
                layoutParams = FrameLayout.LayoutParams(size, size)
            }
        }
        root.addView(ghost)
        moveBarDragGhost(ghost, rawX, rawY)
        return ghost
    }

    private fun moveBarDragGhost(ghost: View?, rawX: Float, rawY: Float) {
        val root = rootView ?: return
        val g = ghost ?: return
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val size = g.layoutParams?.width?.takeIf { it > 0 } ?: stickerSizePx()
        g.x = rawX - loc[0] - size / 2f
        g.y = rawY - loc[1] - size / 2f
    }

    private fun removeBarDragGhost(ghost: View?) {
        val g = ghost ?: return
        (g.parent as? ViewGroup)?.removeView(g)
    }

    private fun placeEmojiAtScreen(emoji: String, rawX: Float, rawY: Float) {
        if (!::stickerOverlay.isInitialized) return
        val loc = IntArray(2)
        stickerOverlay.getLocationOnScreen(loc)
        val w = stickerOverlay.width.coerceAtLeast(1)
        val h = stickerOverlay.height.coerceAtLeast(1)
        val localX = rawX - loc[0]
        val localY = rawY - loc[1]
        val nx = (localX / w).coerceIn(0.05f, 0.95f)
        val ny = (localY / h).coerceIn(0.05f, 0.95f)
        placeEmojiNormalized(emoji, nx, ny)
    }

    private fun placeEmojiNormalized(emoji: String, nx: Float, ny: Float) {
        CanvasStore.addLocalSticker(emoji, nx, ny, lobbyId) ?: return
        drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
    }

    private fun openEmojiBarEditor() {
        val root = rootView ?: return
        if (emojiEditorHost != null) return
        val host = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 40f
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
        }
        val compose = ComposeView(this).apply {
            setContent {
                EmojiBarEditorDialog(
                    onDismiss = {
                        root.removeView(host)
                        emojiEditorHost = null
                        bindReactionBar()
                    }
                )
            }
        }
        host.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(host)
        emojiEditorHost = host
    }

    private fun stickerSizePx(): Int =
        (56f * resources.displayMetrics.density).toInt()

    private fun sendReaction(emoji: String) {
        showReaction(emoji)
        PairConnectionService.sendReaction(this, emoji, lobbyId)
        lifecycleScope.launch {
            runCatching { LuvApiClient.pingAchievement("reactions_sent") }
        }
    }

    private fun fullscreenComposeHost(): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 46f
            isClickable = true
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val bars = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.navigationBars() or
                        WindowInsetsCompat.Type.systemGestures() or
                        WindowInsetsCompat.Type.mandatorySystemGestures() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                val minBottom = (28f * resources.displayMetrics.density).toInt()
                v.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    maxOf(bars.bottom, minBottom)
                )
                insets
            }
            ViewCompat.requestApplyInsets(this)
        }
    }

    private fun openTemplatesBrowser() {
        val root = rootView ?: return
        if (templatesHost != null) return
        dismissTemplatePlacement()
        val host = fullscreenComposeHost()
        val compose = ComposeView(this).apply {
            setContent {
                var templates by remember { mutableStateOf<List<DrawTemplate>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    loading = true
                    templates = runCatching { LuvApiClient.fetchDrawTemplates() }.getOrDefault(emptyList())
                    loading = false
                }
                LuvTheme {
                    TemplatesBrowserSheet(
                        templates = templates,
                        loading = loading,
                        onRefresh = {
                            lifecycleScope.launch {
                                loading = true
                                templates = runCatching { LuvApiClient.fetchDrawTemplates() }
                                    .getOrDefault(emptyList())
                                loading = false
                            }
                        },
                        onCreate = {
                            root.removeView(host)
                            templatesHost = null
                            openTemplateEditor()
                        },
                        onSelect = { tpl ->
                            root.removeView(host)
                            templatesHost = null
                            beginTemplatePlacement(tpl.strokes, tpl.coordSpace)
                        },
                        onEdit = { tpl ->
                            root.removeView(host)
                            templatesHost = null
                            openTemplateEditor(tpl)
                        },
                        onDelete = { tpl ->
                            lifecycleScope.launch {
                                runCatching { LuvApiClient.deleteDrawTemplate(tpl.id) }
                                    .onSuccess {
                                        templates = templates.filter { it.id != tpl.id }
                                        flashStatus("Vorlage gelöscht")
                                    }
                                    .onFailure {
                                        flashStatus(it.message ?: "Fehler")
                                    }
                            }
                        },
                        onDismiss = {
                            root.removeView(host)
                            templatesHost = null
                        }
                    )
                }
            }
        }
        host.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(host)
        templatesHost = host
    }

    private fun openTemplateEditor(existing: DrawTemplate? = null) {
        val root = rootView ?: return
        if (templateEditorHost != null) return
        val host = fullscreenComposeHost()
        val compose = ComposeView(this).apply {
            setContent {
                LuvTheme {
                    TemplateEditorSheet(
                        initialParts = existing?.strokes.orEmpty(),
                        editing = existing != null,
                        onSave = { parts ->
                            lifecycleScope.launch {
                                runCatching {
                                    if (existing != null) {
                                        LuvApiClient.updateDrawTemplate(existing.id, parts)
                                    } else {
                                        LuvApiClient.saveDrawTemplate(parts)
                                    }
                                }
                                    .onSuccess {
                                        flashStatus(
                                            if (existing != null) "Vorlage aktualisiert"
                                            else "Vorlage gespeichert"
                                        )
                                        root.removeView(host)
                                        templateEditorHost = null
                                        openTemplatesBrowser()
                                    }
                                    .onFailure {
                                        flashStatus(it.message ?: "Speichern fehlgeschlagen")
                                    }
                            }
                        },
                        onDismiss = {
                            root.removeView(host)
                            templateEditorHost = null
                        }
                    )
                }
            }
        }
        host.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(host)
        templateEditorHost = host
    }

    private fun beginTemplatePlacement(parts: List<TemplateStrokePart>, coordSpace: String = "canvas") {
        if (!::stickerOverlay.isInitialized || !::drawingView.isInitialized) return
        dismissTemplatePlacement()
        if (parts.isEmpty()) return
        val space = CanvasStore.templateCoordSpace(parts, coordSpace)
        // Overlay genau über der Leinwand (nicht Fullscreen) — sonst Y-Versatz beim Platzieren
        val view = TemplatePlacementView(this).apply {
            this.parts = parts
            this.coordSpace = space
            centerXNorm = 0.5f
            centerYNorm = 0.5f
            scaleFactor = 1f
            rotationDeg = 0f
            onConfirm = { cx, cy, scale, rot ->
                val mapped = mapTemplateNormToDrawingView(cx, cy)
                CanvasStore.addLocalTemplate(
                    parts = parts,
                    x = mapped.first,
                    y = mapped.second,
                    scale = scale,
                    rotation = rot,
                    lobbyId = lobbyId,
                    coordSpace = space
                )
                dismissTemplatePlacement()
                flashStatus("Vorlage platziert")
            }
            onCancel = { dismissTemplatePlacement() }
        }
        stickerOverlay.isClickable = true
        stickerOverlay.isFocusable = true
        stickerOverlay.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        templatePlacementView = view
    }

    /** Normierte Overlay-Koordinaten → DrawingView (gleiche Fläche, Fallback 1:1). */
    private fun mapTemplateNormToDrawingView(nx: Float, ny: Float): Pair<Float, Float> {
        if (!::drawingView.isInitialized || !::stickerOverlay.isInitialized) {
            return nx.coerceIn(0.05f, 0.95f) to ny.coerceIn(0.05f, 0.95f)
        }
        val overlayLoc = IntArray(2)
        val drawLoc = IntArray(2)
        stickerOverlay.getLocationOnScreen(overlayLoc)
        drawingView.getLocationOnScreen(drawLoc)
        val ow = stickerOverlay.width.coerceAtLeast(1).toFloat()
        val oh = stickerOverlay.height.coerceAtLeast(1).toFloat()
        val dw = drawingView.width.coerceAtLeast(1).toFloat()
        val dh = drawingView.height.coerceAtLeast(1).toFloat()
        val screenX = overlayLoc[0] + nx * ow
        val screenY = overlayLoc[1] + ny * oh
        val localX = ((screenX - drawLoc[0]) / dw).coerceIn(0.05f, 0.95f)
        val localY = ((screenY - drawLoc[1]) / dh).coerceIn(0.05f, 0.95f)
        return localX to localY
    }

    private fun dismissTemplatePlacement() {
        templatePlacementView?.let { v ->
            if (::stickerOverlay.isInitialized) {
                stickerOverlay.removeView(v)
                stickerOverlay.isClickable = false
                stickerOverlay.isFocusable = false
            } else {
                rootView?.removeView(v)
            }
        }
        templatePlacementView = null
    }

    private fun applyOverlayBoard(game: String, visible: Boolean) {
        when (game) {
            "ttt", "" -> {
                drawingView.showTicTacToe = visible
                if (visible) {
                    drawingView.showDotGrid = false
                    activeOverlayGame = "ttt"
                } else if (activeOverlayGame == "ttt") {
                    activeOverlayGame = null
                }
            }
            else -> {
                // Punkteraster & Legacy-Overlays entfernt
                drawingView.showDotGrid = false
                if (!visible && activeOverlayGame == game) activeOverlayGame = null
            }
        }
        paintGameButton()
    }

    private fun onGamePlay(game: JSONObject) {
        wordsGameActive = false
        gameHud.visibility = View.GONE
        drawingView.showTicTacToe = false
        drawingView.showDotGrid = false
        activeOverlayGame = null
        playGameActive = true
        playGameType = game.optString("type").ifBlank { playGameType }
        gamePlayOverlay.myPeerHint = CanvasStore.cachedNickname
        gamePlayOverlay.showState(game)
        gamePlayOverlay.removeCallbacks(playClockTick)
        gamePlayOverlay.post(playClockTick)
        paintGameButton()
    }

    private fun onGameState(event: PairEvent.GameState) {
        when (event.gameType) {
            "ttt" -> {
                wordsGameActive = false
                playGameActive = false
                playGameType = null
                gamePlayOverlay.hideGame()
                gamePlayOverlay.removeCallbacks(playClockTick)
                applyOverlayBoard(event.gameType, event.overlay || event.status == "active")
            }
            "words" -> {
                playGameActive = false
                playGameType = null
                gamePlayOverlay.hideGame()
                gamePlayOverlay.removeCallbacks(playClockTick)
                wordsGameActive = true
                wordsStatus = event.status
                drawerNickname = event.drawerNickname
                activeOverlayGame = null
                drawingView.showTicTacToe = false
                drawingView.showDotGrid = false
                val nick = CanvasStore.cachedNickname.orEmpty()
                iAmDrawer = !event.drawerNickname.isNullOrBlank() &&
                    event.drawerNickname.equals(nick, ignoreCase = true)
                if (event.endsAt > 0L) {
                    roundEndsAt = event.endsAt
                    if (event.status == "draw") startWordsTimer()
                } else if (event.status != "draw") {
                    roundEndsAt = 0L
                    gameHud.removeCallbacks(wordsTimerTick)
                }
                if (event.status != "draw" && !iAmDrawer) {
                    secretWord = null
                }
                if (event.status == "pick") {
                    clearWordsRoundUi()
                }
                refreshGameHud()
            }
        }
        paintGameButton()
    }

    private fun clearWordsRoundUi() {
        guessBubbles.clear()
        correctGuessers.clear()
        roundEndsAt = 0L
        gameHud.removeCallbacks(wordsTimerTick)
    }

    private fun startWordsTimer() {
        gameHud.removeCallbacks(wordsTimerTick)
        if (roundEndsAt > System.currentTimeMillis()) {
            gameHud.post(wordsTimerTick)
        }
    }

    private fun onGuessChat(nickname: String, text: String, correct: Boolean) {
        val nick = nickname.trim().ifBlank { "Jemand" }
        val key = nick.lowercase(Locale.getDefault())
        val list = guessBubbles.getOrPut(key) { mutableListOf() }
        list.add(text.trim().take(40) to correct)
        while (list.size > 6) list.removeAt(0)
        if (correct) correctGuessers.add(key)
        refreshLegend()
    }

    private fun formatWordsCountdown(): String {
        if (roundEndsAt <= 0L) return ""
        val left = ((roundEndsAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
        val m = left / 60
        val s = left % 60
        return "%d:%02d".format(m, s)
    }

    private fun stopAllGamesLocal() {
        drawingView.showTicTacToe = false
        drawingView.showDotGrid = false
        activeOverlayGame = null
        playGameActive = false
        playGameType = null
        gamePlayOverlay.removeCallbacks(playClockTick)
        gamePlayOverlay.hideGame()
        wordsGameActive = false
        wordsStatus = ""
        drawerNickname = null
        iAmDrawer = false
        secretWord = null
        clearWordsRoundUi()
        gameHud.visibility = View.GONE
        paintGameButton()
        refreshLegend()
    }

    private fun refreshGameHud() {
        if (!wordsGameActive) {
            gameHud.visibility = View.GONE
            return
        }
        gameHud.visibility = View.VISIBLE
        val drawer = drawerNickname?.takeIf { it.isNotBlank() } ?: "Jemand"
        val clock = formatWordsCountdown()
        when {
            wordsStatus == "done" -> {
                gameHudTitle.text = "Erraten!"
                gameHudSubtitle.text = "Super — tippe 🎮 zum Beenden oder neu starten"
                btnGuess.visibility = View.GONE
            }
            wordsStatus == "timeout" -> {
                gameHudTitle.text = "Zeit abgelaufen"
                gameHudSubtitle.text = "Nächste Runde über 🎮 starten"
                btnGuess.visibility = View.GONE
            }
            iAmDrawer && !secretWord.isNullOrBlank() -> {
                gameHudTitle.text = getString(R.string.game_you_draw)
                gameHudSubtitle.text = if (clock.isNotBlank()) {
                    "Wort: $secretWord · $clock"
                } else {
                    "Wort: $secretWord"
                }
                btnGuess.visibility = View.GONE
            }
            iAmDrawer -> {
                gameHudTitle.text = getString(R.string.game_you_draw)
                gameHudSubtitle.text = "Wähle dein Wort — du malst, die anderen raten"
                btnGuess.visibility = View.GONE
            }
            wordsStatus == "draw" -> {
                gameHudTitle.text = "$drawer malt"
                gameHudSubtitle.text = if (clock.isNotBlank()) {
                    "Rate das Wort · $clock"
                } else {
                    "Erkennst du das Wort? Tippe deinen Tipp."
                }
                val me = CanvasStore.cachedNickname?.lowercase(Locale.getDefault())
                val alreadyWon = me != null && me in correctGuessers
                btnGuess.visibility = if (alreadyWon) View.GONE else View.VISIBLE
            }
            else -> {
                gameHudTitle.text = "Wörter malen"
                gameHudSubtitle.text = "$drawer wählt gerade ein Wort…"
                btnGuess.visibility = View.GONE
            }
        }
    }

    private fun paintGameButton() {
        // Spiele-Button entfernt — Vorlagen-Button bleibt unverändert
    }

    /** Mittige, kurze Meldung — blockiert Zeichnen nicht, ~1 s sichtbar. */
    private fun flashStatus(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        when (trimmed) {
            "Ja",
            "Nein",
            "Kurze Abstimmung…" -> return
        }
        statusView.removeCallbacks(statusHideRunnable)
        statusView.text = trimmed
        statusView.isClickable = false
        statusView.isFocusable = false
        statusView.visibility = View.VISIBLE
        statusView.alpha = 0f
        statusView.animate().cancel()
        statusView.animate().alpha(1f).setDuration(120).start()
        statusView.postDelayed(statusHideRunnable, 1000L)
    }

    private fun showReaction(emoji: String) {
        val root = rootView ?: return
        // Altes Burst-View bleibt unsichtbar — neue Emojis schweben mittig als eigene Views
        reactionBurst.alpha = 0f

        val dp = resources.displayMetrics.density
        val floaterSize = (96 * dp).toInt()
        val floater: View = if (isImagePetId(emoji)) {
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                elevation = 24f
                alpha = 0f
                layoutParams = FrameLayout.LayoutParams(floaterSize, floaterSize, Gravity.CENTER)
                bindItemImage(this, emoji)
            }
        } else {
            TextView(this).apply {
                text = emoji
                textSize = 64f
                elevation = 24f
                alpha = 0f
                // Leichter Schatten, damit es auf bunten Leinwänden sitzt
                setShadowLayer(12f * dp, 0f, 4f * dp, 0x66000000)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
        }
        root.addView(floater)

        // Start etwas unter der Mitte, schwebt ~2s sachte nach oben
        val startY = 56f * dp
        val endY = -140f * dp
        // Sanftes Wobbeln — wie eine Feder, wenig Amplitude
        val wobbleAmp = (10f + Random.nextFloat() * 4f) * dp
        val wobblePhase = Random.nextFloat() * (Math.PI.toFloat() * 2f)
        val wobbleCycles = 1.6f

        floater.translationY = startY
        floater.translationX = 0f
        floater.scaleX = 0.82f
        floater.scaleY = 0.82f

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            interpolator = PathInterpolator(0.22f, 0.9f, 0.28f, 1f)
            addUpdateListener { animator ->
                val t = animator.animatedFraction
                floater.translationY = startY + (endY - startY) * t
                // Sinus-Wobble, leicht abgedämpft nach oben
                val damp = 1f - t * 0.45f
                floater.translationX =
                    sin((t * wobbleCycles * Math.PI * 2 + wobblePhase).toDouble()).toFloat() *
                        wobbleAmp * damp
                floater.alpha = when {
                    t < 0.1f -> (t / 0.1f).coerceIn(0f, 1f)
                    t > 0.72f -> ((1f - t) / 0.28f).coerceIn(0f, 1f)
                    else -> 1f
                }
                val pulse = 0.88f + 0.14f * sin((t * Math.PI).toDouble()).toFloat()
                floater.scaleX = pulse
                floater.scaleY = pulse
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (floater.parent as? ViewGroup)?.removeView(floater)
                }

                override fun onAnimationCancel(animation: Animator) {
                    (floater.parent as? ViewGroup)?.removeView(floater)
                }
            })
            start()
        }
    }

    private fun setEraserEnabled(on: Boolean) {
        eraserOn = on
        drawingView.eraserEnabled = on
        paintEraserButton()
        flashStatus(if (on) getString(R.string.eraser_on) else getString(R.string.eraser_off))
    }

    private fun paintEraserButton() {
        val gold = 0xFFFFD56A.toInt()
        btnEraser.alpha = 1f
        if (eraserOn) {
            // Nur gelbe Umrandung wenn aktiv — kein graues Kästchen
            btnEraser.background = GradientDrawable().apply {
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke((2f * resources.displayMetrics.density).toInt(), gold)
            }
        } else {
            btnEraser.background = null
        }
    }

    private fun applyMyColor(index: Int, persist: Boolean, sync: Boolean) {
        val safe = index.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        CanvasStore.updateProfile(CanvasStore.cachedNickname, safe)
        drawingView.myColorIndex = safe
        val bg = CanvasStore.backgroundFor(safe)
        rootView?.setBackgroundColor(bg)
        drawingView.canvasBackground = bg
        if (persist) {
            val key = lobbyId
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val snap = LuvApp.instance.prefs.snapshot()
                    val code = snap.lobbies.firstOrNull { it.id == key }?.code
                    val lobbyKey = code?.takeIf { it.isNotBlank() } ?: key
                    if (!lobbyKey.isNullOrBlank()) {
                        LuvApp.instance.prefs.setColorIndexForLobby(lobbyKey, safe)
                    } else {
                        LuvApp.instance.prefs.setColorIndex(safe)
                    }
                }
            }
        }
        // Solo: nur Pinsel/Hintergrund wechseln — alte Striche behalten Farben.
        // Zu zweit+: freigegebene (nach Join gemalte) Striche mitumfärben.
        // Farbe immer live an alle (Avatar + offene Striche).
        CanvasStore.recolorOwnStrokes(safe, lobbyId, broadcast = sync)
        drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
        if (sync) {
            PairConnectionService.sendPresence(this, active = true, lobbyId = lobbyId)
            PairConnectionService.sendRecolor(
                this,
                CanvasStore.cachedNickname,
                safe,
                lobbyId
            )
        }
        refreshLegend()
    }

    private fun openBrushStudio(mode: BrushStudioMode) {
        val root = rootView ?: return
        if (brushStudioHost != null) return
        val id = lobbyId ?: return
        val mine = CanvasStore.cachedColorIndex
        val myUserId = AccountSession.account.value?.id
        val taken = if (
            eventLobbyActive ||
            weddingLobbyActive ||
            mode == BrushStudioMode.THICKNESS
        ) {
            emptySet()
        } else {
            PairSessionState.takenColorIndices(
                id,
                CanvasStore.cachedNickname,
                myUserId
            ) +
                CanvasStore.snapshot(id)
                    .filter {
                        !it.isLocal &&
                            !it.nickname.equals(CanvasStore.cachedNickname, ignoreCase = true)
                    }
                    .map { it.colorIndex }
                    .toSet()
        }
        val host = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 42f
            isClickable = true
            // Immersive Lock blendet Bars aus → IgnoringVisibility, sonst bottom=0
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val bars = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.navigationBars() or
                        WindowInsetsCompat.Type.systemGestures() or
                        WindowInsetsCompat.Type.mandatorySystemGestures() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                // Mindestens ~28dp unten (Pixel/Samsung Gesture-Leiste)
                val minBottom = (28f * resources.displayMetrics.density).toInt()
                v.setPadding(
                    bars.left,
                    bars.top,
                    bars.right,
                    maxOf(bars.bottom, minBottom)
                )
                insets
            }
            ViewCompat.requestApplyInsets(this)
        }
        val compose = ComposeView(this).apply {
            setContent {
                BrushStudioSheet(
                    mode = mode,
                    selectedColor = mine,
                    takenColors = taken,
                    brushWidth = drawingView.myBrushWidth,
                    onColorPick = { applyMyColor(it, persist = true, sync = true) },
                    onBrushWidthChange = { w ->
                        drawingView.myBrushWidth = w
                        CanvasStore.updateBrushWidth(w)
                        lifecycleScope.launch(Dispatchers.IO) {
                            LuvApp.instance.prefs.setBrushWidth(w)
                        }
                    },
                    onDismiss = {
                        root.removeView(host)
                        brushStudioHost = null
                    }
                )
            }
        }
        host.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(host)
        brushStudioHost = host
    }

    private fun confirmClearCanvas() {
        showConfirmClearDialog()
    }

    private fun showConfirmPublicDialog() {
        val root = rootView ?: return
        confirmClearOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val dp = resources.displayMetrics.density
        val accentDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE8F4FF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams((14 * dp).toInt(), (14 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (18 * dp).toInt()
            }
        }
        val title = TextView(this).apply {
            text = getString(R.string.public_confirm_title)
            setTextColor(0xFFF7F2EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val body = TextView(this).apply {
            text = getString(R.string.public_confirm_body)
            setTextColor(0xFF9AA3B2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, (10 * dp).toInt(), 0, (28 * dp).toInt())
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnNo = TextView(this).apply {
            text = getString(R.string.public_confirm_no)
            gravity = Gravity.CENTER
            setTextColor(0xFFF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setBackgroundResource(R.drawable.vote_btn_no)
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * dp).toInt()
            }
            setOnClickListener { dismissConfirmClearDialog() }
        }
        val btnYes = TextView(this).apply {
            text = getString(R.string.public_confirm_yes)
            gravity = Gravity.CENTER
            setTextColor(0xFFF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setBackgroundResource(R.drawable.vote_btn_yes)
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * dp).toInt()
            }
            setOnClickListener {
                dismissConfirmClearDialog()
                submitPublicShare()
            }
        }
        buttons.addView(btnNo)
        buttons.addView(btnYes)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.vote_card_bg)
            elevation = 24f
            setPadding((28 * dp).toInt(), (30 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                marginStart = (36 * dp).toInt()
                marginEnd = (36 * dp).toInt()
            }
            addView(accentDot)
            addView(title)
            addView(body)
            addView(buttons)
            scaleX = 0.94f
            scaleY = 0.94f
            alpha = 0f
        }
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xD90B0E14.toInt())
            isClickable = true
            isFocusable = true
            alpha = 0f
            addView(card)
            setOnClickListener { dismissConfirmClearDialog() }
        }
        confirmClearOverlay = overlay
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        overlay.animate().alpha(1f).setDuration(220).start()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
            .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.2f, 1f)).start()
    }

    private fun submitPublicShare() {
        PairConnectionService.sendPublicPropose(this, lobbyId)
        flashStatus(getString(R.string.public_ask_started))
    }

    private fun handleClearVoteOpen(event: ClearVoteEvent.Open) {
        voteKind = "clear"
        activeProposalId = event.proposalId
        if (event.isInitiator || event.alreadyVoted || AccountSession.hasVotedClear(event.proposalId)) {
            hasVotedClear = true
            return
        }
        hasVotedClear = false
        showRoomVote("Leeren?", event.by, event.yes, 0, event.total)
    }

    private fun maybeShowPendingClearVote() {
        val id = lobbyId ?: return
        val pending = AccountSession.pendingClearVote.value ?: return
        if (pending.lobbyId != id) return
        if (pending.isInitiator || pending.alreadyVoted || AccountSession.hasVotedClear(pending.proposalId)) {
            return
        }
        if (voteOverlay?.visibility == View.VISIBLE && voteKind == "clear") return
        handleClearVoteOpen(pending)
    }

    private fun handlePublicVoteOpen(event: PublicVoteEvent.Open) {
        voteKind = "public"
        activeProposalId = event.proposalId
        if (event.isInitiator || event.alreadyVoted || AccountSession.hasVotedPublic(event.proposalId)) {
            hasVotedClear = true
            // Initiator / schon abgestimmt: kein Blockieren, weiter malen
            return
        }
        hasVotedClear = false
        val title = getString(R.string.public_vote_title) +
            if (event.rewardCoins > 0) " · +${event.rewardCoins}" else ""
        showRoomVote(title, event.by, event.yes, 0, event.total)
    }

    private fun maybeShowPendingPublicVote() {
        val id = lobbyId ?: return
        val pending = AccountSession.pendingPublicVote.value ?: return
        if (pending.lobbyId != id) return
        if (pending.isInitiator || pending.alreadyVoted || AccountSession.hasVotedPublic(pending.proposalId)) {
            return
        }
        if (voteOverlay?.visibility == View.VISIBLE && voteKind == "public") return
        handlePublicVoteOpen(pending)
    }

    private fun dismissConfirmClearDialog() {
        val overlay = confirmClearOverlay ?: return
        overlay.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                if (confirmClearOverlay === overlay) confirmClearOverlay = null
            }
            .start()
    }

    private fun showConfirmClearDialog() {
        val root = rootView ?: return
        confirmClearOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        val dp = resources.displayMetrics.density

        val accentDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF6B8A.toInt())
            }
            layoutParams = LinearLayout.LayoutParams((14 * dp).toInt(), (14 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (18 * dp).toInt()
            }
        }
        val title = TextView(this).apply {
            text = getString(R.string.clear_confirm_title)
            setTextColor(0xFFF7F2EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.01f
        }
        val body = TextView(this).apply {
            text = getString(R.string.clear_confirm_body)
            setTextColor(0xFF9AA3B2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, (10 * dp).toInt(), 0, (28 * dp).toInt())
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val btnNo = TextView(this).apply {
            text = getString(R.string.clear_confirm_no)
            gravity = Gravity.CENTER
            setTextColor(0xFFF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setBackgroundResource(R.drawable.vote_btn_no)
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * dp).toInt()
            }
            setOnClickListener { dismissConfirmClearDialog() }
        }
        val btnYes = TextView(this).apply {
            text = getString(R.string.clear_confirm_yes)
            gravity = Gravity.CENTER
            setTextColor(0xFFF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setBackgroundResource(R.drawable.vote_btn_yes)
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * dp).toInt()
            }
            setOnClickListener {
                dismissConfirmClearDialog()
                PairConnectionService.sendClear(this@LockDrawActivity, lobbyId)
                flashStatus(getString(R.string.clear_ask_started))
            }
        }
        buttons.addView(btnNo)
        buttons.addView(btnYes)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.vote_card_bg)
            elevation = 24f
            setPadding((28 * dp).toInt(), (30 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                marginStart = (36 * dp).toInt()
                marginEnd = (36 * dp).toInt()
            }
            addView(accentDot)
            addView(title)
            addView(body)
            addView(buttons)
            scaleX = 0.94f
            scaleY = 0.94f
            alpha = 0f
        }

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xD90B0E14.toInt())
            isClickable = true
            isFocusable = true
            alpha = 0f
            addView(card)
            setOnClickListener { dismissConfirmClearDialog() }
        }
        confirmClearOverlay = overlay
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        overlay.animate().alpha(1f).setDuration(220).start()
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260)
            .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.2f, 1f))
            .start()
    }

    private fun showRoomVote(titleText: String, by: String, yes: Int, no: Int, total: Int) {
        val root = rootView ?: return
        val dp = resources.displayMetrics.density
        if (voteOverlay == null) {
            val accentDot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFFF6B8A.toInt())
                }
                layoutParams = LinearLayout.LayoutParams((12 * dp).toInt(), (12 * dp).toInt()).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = (16 * dp).toInt()
                }
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.vote_card_bg)
                elevation = 24f
                setPadding((28 * dp).toInt(), (28 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                    marginStart = (36 * dp).toInt()
                    marginEnd = (36 * dp).toInt()
                }
            }

            voteTitleView = TextView(this).apply {
                text = titleText
                setTextColor(0xFFF7F2EC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                typeface = Typeface.create("serif", Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            voteByView = TextView(this).apply {
                setTextColor(0xFFFF6B8A.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
            }
            voteProgressView = TextView(this).apply {
                setTextColor(0xFF9AA3B2.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (24 * dp).toInt())
            }

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val btnNo = TextView(this).apply {
                text = "Nein"
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F1EC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setBackgroundResource(R.drawable.vote_btn_no)
                setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * dp).toInt()
                }
                setOnClickListener { castRoomVote(yes = false) }
            }
            val btnYes = TextView(this).apply {
                text = "Ja"
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F1EC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setBackgroundResource(R.drawable.vote_btn_yes)
                setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (8 * dp).toInt()
                }
                setOnClickListener { castRoomVote(yes = true) }
            }
            buttons.addView(btnNo)
            buttons.addView(btnYes)
            voteButtonsRow = buttons

            card.addView(accentDot)
            card.addView(voteTitleView)
            card.addView(voteByView)
            card.addView(voteProgressView)
            card.addView(buttons)

            voteOverlay = FrameLayout(this).apply {
                setBackgroundColor(0xD90B0E14.toInt())
                isClickable = true
                isFocusable = true
                alpha = 0f
                addView(card)
                setOnClickListener { /* block touches behind */ }
            }
            root.addView(
                voteOverlay,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        voteTitleView?.text = titleText
        voteByView?.text = if (hasVotedClear) {
            "Du hast gefragt"
        } else {
            by.trim().ifBlank { "Jemand" }
        }
        voteButtonsRow?.visibility = if (hasVotedClear) View.GONE else View.VISIBLE
        updateClearVote(yes, no, total)
        voteOverlay?.visibility = View.VISIBLE
        voteOverlay?.animate()?.alpha(1f)?.setDuration(220)?.start()
    }

    private fun updateClearVote(yes: Int, no: Int, total: Int) {
        val safeTotal = total.coerceAtLeast(1)
        voteProgressView?.text = when {
            hasVotedClear -> "Warte…  $yes/$safeTotal"
            else -> "$yes/$safeTotal"
        }
    }

    private fun castRoomVote(yes: Boolean) {
        val proposalId = activeProposalId ?: return
        if (hasVotedClear) return
        hasVotedClear = true
        if (voteKind == "public") {
            AccountSession.markPublicVoted(proposalId)
            PairConnectionService.sendPublicVote(this, proposalId, yes = yes, lobbyId = lobbyId)
        } else {
            AccountSession.markClearVoted(proposalId)
            PairConnectionService.sendClearVote(this, proposalId, yes = yes, lobbyId = lobbyId)
        }
        flashStatus(if (yes) "Ja" else "Nein")
        // Nach der Entscheidung weiter malen — kein „Bitte warten“
        hideClearVote()
    }

    private fun hideClearVote() {
        val overlay = voteOverlay ?: return
        overlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { overlay.visibility = View.GONE }
            .start()
    }

    override fun onResume() {
        super.onResume()
        foregroundLobbyId = lobbyId
        MidnightClear.checkAndClearIfNewDay(this)
        drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
        PairConnectionService.sendPresence(this, active = true, lobbyId = lobbyId)
        lifecycleScope.launch {
            myPetEmoji = withContext(Dispatchers.IO) {
                runCatching { LuvApp.instance.prefs.equippedPet() }
                    .getOrDefault(ShopCatalog.DEFAULT_PET)
            }
            refreshLegend()
        }
        maybeShowPendingClearVote()
        maybeShowPendingPublicVote()
        lifecycleScope.launch {
            runCatching { AppUpdater.checkOnNavigate(this@LockDrawActivity) }
            val id = lobbyId ?: return@launch
            val lobby = withContext(Dispatchers.IO) {
                LuvApp.instance.prefs.snapshot().lobbies.firstOrNull { it.id == id }
            } ?: return@launch
            CanvasMemoryKeeper.touch(lobby)
        }
    }

    override fun onPause() {
        if (foregroundLobbyId == lobbyId) foregroundLobbyId = null
        if (::gameHud.isInitialized) gameHud.removeCallbacks(wordsTimerTick)
        PairConnectionService.sendPresence(this, active = false, lobbyId = lobbyId)
        val id = lobbyId
        if (id != null) {
            lifecycleScope.launch {
                val lobby = withContext(Dispatchers.IO) {
                    LuvApp.instance.prefs.snapshot().lobbies.firstOrNull { it.id == id }
                } ?: return@launch
                CanvasMemoryKeeper.uploadSnapshot(lobby)
            }
        }
        super.onPause()
    }

    private fun observeForcedUpdates() {
        lifecycleScope.launch {
            AppUpdater.state.collectLatest { state ->
                if (state.requiresForcedUpdate()) {
                    showForcedUpdateOverlay()
                } else {
                    dismissForcedUpdateOverlay()
                }
            }
        }
    }

    /** Gleicher Update-Dialog wie im Hauptmenü (lesbare dunkle Karte). */
    private fun showForcedUpdateOverlay() {
        val root = rootView ?: return
        if (forcedUpdateHost != null) return
        val host = fullscreenComposeHost().apply { elevation = 90f }
        val compose = ComposeView(this).apply {
            setContent {
                val state by AppUpdater.state.collectAsStateWithLifecycle()
                LuvTheme {
                    ForcedUpdateDialog(
                        state = state,
                        onUpdate = {
                            if (AppUpdater.state.value is UpdateUiState.Downloading) return@ForcedUpdateDialog
                            lifecycleScope.launch {
                                val ready = AppUpdater.state.value as? UpdateUiState.Ready
                                if (ready != null) {
                                    AppUpdater.installApkFile(this@LockDrawActivity, ready.file)
                                } else {
                                    AppUpdater.downloadAndInstall(this@LockDrawActivity)
                                }
                            }
                        }
                    )
                }
            }
        }
        host.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(host)
        forcedUpdateHost = host
    }

    private fun dismissForcedUpdateOverlay() {
        forcedUpdateHost?.let { rootView?.removeView(it) }
        forcedUpdateHost = null
    }

    private fun observeMaintenance() {
        lifecycleScope.launch {
            while (true) {
                val st = runCatching { LuvApiClient.fetchMaintenanceStatus() }.getOrNull()
                if (st != null) {
                    if (st.active) {
                        maintenanceHold = true
                        showMaintenanceOverlay(st)
                    } else if (maintenanceHold) {
                        showMaintenanceOverlay(st)
                    } else {
                        dismissMaintenanceOverlay()
                    }
                }
                delay(if (maintenanceHold) 4_000 else 12_000)
            }
        }
    }

    private fun showMaintenanceOverlay(status: LuvApiClient.MaintenanceStatus) {
        val root = rootView ?: return
        maintenanceUi.value = status
        if (maintenanceHost != null) return
        val host = fullscreenComposeHost().apply { elevation = 95f }
        val compose = ComposeView(this).apply {
            setContent {
                val st by maintenanceUi
                LuvTheme {
                    st?.let { current ->
                        ForcedMaintenanceDialog(
                            status = current,
                            onDismiss = {
                                maintenanceHold = false
                                dismissMaintenanceOverlay()
                            },
                            onClaimed = { }
                        )
                    }
                }
            }
        }
        host.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(host)
        maintenanceHost = host
    }

    private fun dismissMaintenanceOverlay() {
        maintenanceUi.value = null
        maintenanceHost?.let { rootView?.removeView(it) }
        maintenanceHost = null
    }

    private fun refreshLegend() {
        val id = lobbyId ?: return
        val nickname = CanvasStore.cachedNickname
            ?: AccountSession.account.value?.nickname
        val myUserId = AccountSession.account.value?.id
        val myColor = CanvasStore.cachedColorIndex
        // Wer noch Striche auf der Leinwand hat — Nickname ist die stabile Anzeige-ID
        // (authorId "local" von alten Clients darf Ghosts nicht zusammenwerfen)
        val drawColorByNick = linkedMapOf<String, Int>()
        val drawAuthorByNick = linkedMapOf<String, String?>()
        val drawDisplayNick = linkedMapOf<String, String>()
        CanvasStore.snapshot(id).forEach { stroke ->
            val nick = when {
                !stroke.nickname.isNullOrBlank() -> stroke.nickname.trim()
                stroke.isLocal && !nickname.isNullOrBlank() -> nickname.trim()
                else -> return@forEach
            }
            val key = nick.lowercase(Locale.getDefault())
            drawColorByNick[key] = stroke.colorIndex
            drawDisplayNick.putIfAbsent(key, nick)
            val aid = stroke.authorId?.trim()?.takeIf {
                it.isNotBlank() && !it.equals("local", ignoreCase = true) && it != "null"
            }
            if (aid != null) drawAuthorByNick[key] = aid
        }
        val live = PairSessionState.legendPeers(
            id, nickname, myColor, myUserId, myPetEmoji
        ).map { peer ->
            val isMe = peer.peerKey == "me" ||
                (!myUserId.isNullOrBlank() && peer.userId == myUserId) ||
                (!nickname.isNullOrBlank() &&
                    peer.nickname.equals(nickname, ignoreCase = true))
            if (isMe) {
                peer.copy(colorIndex = myColor, petEmoji = myPetEmoji)
            } else {
                val drawn = drawColorByNick[peer.nickname.trim().lowercase(Locale.getDefault())]
                if (drawn != null) peer.copy(colorIndex = drawn) else peer
            }
        }
        val liveKeys = live.map { it.nickname.trim().lowercase(Locale.getDefault()) }.toSet()
        val myKey = nickname?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        // Jeder Zeichner mit Strichen bleibt sichtbar — ausgegraut wenn nicht mehr live
        val ghosts = drawColorByNick
            .filterKeys { key ->
                key !in liveKeys &&
                    key != myKey &&
                    key.isNotBlank() &&
                    PairSessionState.isKnownDisplayNickname(drawDisplayNick[key] ?: key)
            }
            .map { (key, color) ->
                PeerInfo(
                    peerKey = "gone:$key",
                    nickname = drawDisplayNick[key] ?: key,
                    colorIndex = color,
                    active = false,
                    userId = drawAuthorByNick[key],
                    online = false,
                    departed = true
                )
            }
        // Nach Nickname mergen — nie über authorId "local" kollabieren
        val merged = LinkedHashMap<String, PeerInfo>()
        for (peer in live + ghosts) {
            val key = peer.nickname.trim().lowercase(Locale.getDefault())
            if (key.isBlank()) continue
            val prev = merged[key]
            if (prev == null) {
                merged[key] = peer
            } else if (prev.departed && !peer.departed) {
                merged[key] = peer
            }
        }

        legendRow.removeAllViews()
        legendRow.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt()
        merged.values.take(PeerPalette.MAX_PEERS).forEachIndexed { index, peer ->
            if (index > 0) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(gap, 1)
                }
                legendRow.addView(spacer)
            }
            legendRow.addView(buildLegendColumn(peer))
        }
    }

    private fun buildLegendColumn(peer: PeerInfo): View {
        val dp = resources.displayMetrics.density
        val size = (34 * dp).toInt()
        val key = peer.nickname.trim().lowercase(Locale.getDefault())
        val bubbles = guessBubbles[key].orEmpty()
        val hasCheck = key in correctGuessers

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Tipps aufsteigend: neueste oben
        bubbles.asReversed().forEach { (text, correct) ->
            column.addView(buildGuessBubble(text, correct, dp))
        }

        if (hasCheck) {
            column.addView(
                TextView(this).apply {
                    text = "✓"
                    gravity = Gravity.CENTER
                    setTextColor(0xFF2EE68A.toInt())
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (2 * dp).toInt() }
                }
            )
        }

        val painting = LiveProximity.isPeerPainting(lobbyId, peer.nickname)
        val myId = AccountSession.account.value?.id
        val myNick = CanvasStore.cachedNickname
            ?: AccountSession.account.value?.nickname
        val isMeLegend = peer.peerKey == "me" ||
            (!myId.isNullOrBlank() && peer.userId == myId) ||
            (!myNick.isNullOrBlank() && peer.nickname.equals(myNick, ignoreCase = true))
        val onCanvas = !peer.departed && (peer.active || isMeLegend || painting)
        val ringPad = if (onCanvas) (3 * dp).toInt() else 0
        val wrap = FrameLayout(this).apply {
            clipChildren = true
            clipToPadding = true
            layoutParams = LinearLayout.LayoutParams(size + ringPad * 2, size + ringPad * 2)
            elevation = 0f
            if (onCanvas) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x00000000)
                    setStroke((2.5f * dp).toInt(), 0xFFFFFFFF.toInt())
                }
            }
        }
        val fillColor = if (peer.departed) {
            0xFF6B7280.toInt()
        } else {
            PeerPalette.strokeColor(peer.colorIndex)
        }
        val pet = peer.petEmoji.trim().ifBlank { ShopCatalog.DEFAULT_PET }
        val ovalBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(
                (2 * dp).toInt(),
                if (hasCheck) 0xFF2EE68A.toInt() else 0x00000000
            )
        }
        val circleAlpha = when {
            peer.departed -> 0.45f
            onCanvas -> 1f
            else -> 0.65f
        }
        val circle: View = if (isImagePetId(pet)) {
            FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
                background = ovalBg
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                alpha = circleAlpha
                contentDescription = peer.nickname
                elevation = 0f
                val img = ImageView(this@LockDrawActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        (size * 0.72f).toInt(),
                        (size * 0.72f).toInt()
                    ).apply { gravity = Gravity.CENTER }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
                addView(img)
                // Cache nutzen — sonst blinkt img_*-Begleiter bei jedem Legend-Refresh (Strich)
                bindItemImage(img, pet)
            }
        } else {
            TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.CENTER
                }
                gravity = Gravity.CENTER
                text = pet
                includeFontPadding = false
                setTextColor(if (peer.departed) 0xFFD1D5DB.toInt() else 0xFF1A1F2E.toInt())
                textSize = 16f
                background = ovalBg
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                alpha = circleAlpha
                contentDescription = peer.nickname
                elevation = 0f
            }
        }
        wrap.addView(circle)
        wrap.isClickable = true
        wrap.isFocusable = true
        wrap.setOnClickListener { showPeerProfileDialog(peer) }
        column.addView(wrap)

        // Name nur unter dem Avatar, der gerade Tipps zeigt
        if (bubbles.isNotEmpty()) {
            column.addView(
                TextView(this).apply {
                    text = peer.nickname
                    setTextColor(0xE6FFFFFF.toInt())
                    textSize = 11f
                    gravity = Gravity.CENTER
                    maxWidth = (72 * dp).toInt()
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, (3 * dp).toInt(), 0, 0)
                    setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
                }
            )
        }
        return column
    }

    private fun showPeerProfileDialog(peer: PeerInfo) {
        val root = rootView ?: return
        if (profileHost != null) return
        val myId = AccountSession.account.value?.id
        val isMe = peer.peerKey == "me" ||
            (!myId.isNullOrBlank() && peer.userId == myId)
        val host = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = 48f
            isClickable = true
            setBackgroundColor(0xEE0E1117.toInt())
        }
        fun dismissProfile() {
            root.removeView(host)
            profileHost = null
        }
        val resolvedUserId = when {
            // Eigenes Profil auch per userId laden — sonst erscheint nur die Default-Leinwand
            isMe -> myId
            !peer.userId.isNullOrBlank() -> peer.userId
            else -> {
                val id = lobbyId
                if (id.isNullOrBlank()) null
                else PairSessionState.peers(id).value.values
                    .firstOrNull {
                        !it.userId.isNullOrBlank() &&
                            it.nickname.equals(peer.nickname, ignoreCase = true)
                    }?.userId
            }
        }
        val compose = ComposeView(this).apply {
            setContent {
                LuvTheme {
                    ProfileCanvasScreen(
                        nickname = peer.nickname.trim().ifBlank { "Jemand" },
                        colorIndex = peer.colorIndex,
                        // Eigenes Avatar in der Lobby = Ansicht wie für andere (kein Bearbeiten)
                        editable = false,
                        userId = resolvedUserId,
                        onClose = { dismissProfile() },
                        onReport = if (!isMe) {
                            {
                                dismissProfile()
                                showReportPeerDialog(peer)
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
        host.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(host)
        profileHost = host
    }

    private fun showReportPeerDialog(peer: PeerInfo) {
        lifecycleScope.launch {
            val snap = LuvApp.instance.prefs.snapshot()
            val lobbyCode = snap.lobbies.firstOrNull { it.id == lobbyId }?.code
            if (lobbyCode.isNullOrBlank()) {
                Toast.makeText(this@LockDrawActivity, "Lobby unbekannt.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(this@LockDrawActivity)
                    .setTitle("${peer.nickname} melden?")
                    .setMessage("Optional kannst du einen Screenshot aus deiner Galerie anhängen.")
                    .setNeutralButton("Ohne Bild melden") { _, _ ->
                        submitPeerReport(lobbyCode, peer, imageBase64 = null)
                    }
                    .setPositiveButton("Screenshot wählen") { _, _ ->
                        showGalleryPickForReport(lobbyCode, peer)
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }
    }

    private fun showGalleryPickForReport(lobbyCode: String, peer: PeerInfo) {
        lifecycleScope.launch {
            val moments = runCatching { LocalMoments.list(this@LockDrawActivity) }
                .getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (moments.isEmpty()) {
                    MaterialAlertDialogBuilder(this@LockDrawActivity)
                        .setTitle("Galerie leer")
                        .setMessage("Keine gespeicherten Momente. Ohne Bild melden?")
                        .setPositiveButton("Melden") { _, _ ->
                            submitPeerReport(lobbyCode, peer, null)
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                    return@withContext
                }
                val dp = resources.displayMetrics.density
                val row = LinearLayout(this@LockDrawActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                }
                val scroll = HorizontalScrollView(this@LockDrawActivity).apply {
                    addView(row)
                    isHorizontalScrollBarEnabled = false
                }
                val dialog = MaterialAlertDialogBuilder(this@LockDrawActivity)
                    .setTitle("Screenshot wählen")
                    .setView(scroll)
                    .setNegativeButton("Abbrechen", null)
                    .create()
                moments.take(24).forEach { moment ->
                    val thumb = ImageView(this@LockDrawActivity).apply {
                        layoutParams = LinearLayout.LayoutParams((72 * dp).toInt(), (72 * dp).toInt()).apply {
                            marginEnd = (8 * dp).toInt()
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply {
                            cornerRadius = 12 * dp
                            setColor(0x33FFFFFF)
                        }
                        clipToOutline = true
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, 12 * dp)
                            }
                        }
                        setOnClickListener {
                            dialog.dismiss()
                            lifecycleScope.launch {
                                val full = LocalMoments.loadFull(moment)
                                val b64 = full?.let { bitmapToJpegBase64(it) }
                                submitPeerReport(lobbyCode, peer, b64)
                            }
                        }
                    }
                    row.addView(thumb)
                    lifecycleScope.launch {
                        val bmp = LocalMoments.loadThumbnail(this@LockDrawActivity, moment, 160)
                        withContext(Dispatchers.Main) {
                            if (bmp != null) thumb.setImageBitmap(bmp)
                        }
                    }
                }
                dialog.show()
            }
        }
    }

    private fun submitPeerReport(lobbyCode: String, peer: PeerInfo, imageBase64: String?) {
        lifecycleScope.launch {
            val ok = runCatching {
                LuvApiClient.reportPeer(
                    lobbyCode = lobbyCode,
                    userId = peer.userId,
                    nickname = peer.nickname,
                    imageBase64 = imageBase64
                )
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@LockDrawActivity,
                    if (ok) "Danke — Meldung ist angekommen." else "Melden fehlgeschlagen.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun bitmapToJpegBase64(bitmap: Bitmap): String {
        val scaled = if (bitmap.width > 1280 || bitmap.height > 1280) {
            val scale = 1280f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildGuessBubble(text: String, correct: Boolean, dp: Float): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextColor(if (correct) 0xFF063D28.toInt() else 0xFF1A1F2E.toInt())
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(if (correct) 0xFF8CFFC2.toInt() else 0xFFF4F1EC.toInt())
            }
            val padH = (8 * dp).toInt()
            val padV = (4 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            maxWidth = (96 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * dp).toInt() }
            // Leichtes Aufsteigen beim Erscheinen
            alpha = 0f
            translationY = 10f * dp
            animate().alpha(1f).translationY(0f).setDuration(280).start()
        }
    }

    private fun maybeHaptic() {
        lifecycleScope.launch {
            if (com.luv.couple.data.QuietHoursGate.isQuietNow()) return@launch
            val enabled = withContext(Dispatchers.IO) {
                LuvApp.instance.prefs.isPartnerHapticEnabled()
            }
            if (enabled) {
                drawingView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    private fun showBanner(view: TextView, text: String, durationMs: Long) {
        view.text = text
        view.animate().alpha(1f).setDuration(220).start()
        view.postDelayed({
            view.animate().alpha(0f).setDuration(280).start()
        }, durationMs)
    }

    private fun showLastStrokeMemory(id: String?, lobby: com.luv.couple.data.Lobby? = null) {
        val root = rootView ?: return
        val who = CanvasStore.lastOtherStrokeByValue(id)?.takeIf { it.isNotBlank() } ?: return
        val myId = AccountSession.account.value?.id
        val lobbyAt = lobby?.takeIf {
            it.lastCanvasAt > 0L &&
                !it.lastCanvasActorId.isNullOrBlank() &&
                (myId.isNullOrBlank() || it.lastCanvasActorId != myId)
        }?.lastCanvasAt ?: 0L
        val storeAt = CanvasStore.lastOtherStrokeAtValue(id)
        val at = when {
            lobbyAt > 0L -> lobbyAt
            storeAt > 0L -> storeAt
            else -> return
        }
        val ageMs = System.currentTimeMillis() - at
        if (ageMs > TimeUnit.DAYS.toMillis(7) || ageMs < 0L) return
        val whenText = when {
            ageMs < TimeUnit.MINUTES.toMillis(2) -> "gerade eben"
            ageMs < TimeUnit.HOURS.toMillis(1) -> "vor ${ageMs / TimeUnit.MINUTES.toMillis(1)} Min."
            ageMs < TimeUnit.DAYS.toMillis(1) -> "vor ${ageMs / TimeUnit.HOURS.toMillis(1)} Std."
            else -> "vor ${ageMs / TimeUnit.DAYS.toMillis(1)} Tag(en)"
        }
        val line = "$who war zuletzt hier · $whenText"
        strokeMemoryView?.let { root.removeView(it) }
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            text = line
            setTextColor(0xF2FFFFFF.toInt())
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding((18 * dp).toInt(), (10 * dp).toInt(), (18 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 999f * dp
                setColor(0x990A0D14.toInt())
                setStroke((1 * dp).toInt(), 0x55FFFFFF)
            }
            alpha = 0f
            elevation = 12f * dp
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = (72 * dp).toInt()
            }
        }
        root.addView(tv)
        strokeMemoryView = tv
        tv.animate().alpha(1f).setDuration(420).start()
        tv.postDelayed({
            tv.animate().alpha(0f).setDuration(480).withEndAction {
                root.removeView(tv)
                if (strokeMemoryView === tv) strokeMemoryView = null
            }.start()
        }, 3200L)
    }

    /**
     * Leinwand endet über den Avatar-Kreisen und ist immer 9:16 (Letterbox),
     * damit Striche und Vorlagen auf allen Geräten gleich aussehen — ohne Verzerrung.
     */
    private fun applyPortraitCanvasLetterbox(root: FrameLayout) {
        if (!::drawingView.isInitialized || !::bottomDock.isInitialized) return
        val density = resources.displayMetrics.density
        val dockH = bottomDock.height.coerceAtLeast(0)
        val legendScroll = findViewById<View>(R.id.legendScroll)
        val legendGap = (4 * density).toInt()
        val legendH = when {
            legendScroll.height > 0 -> legendScroll.height
            else -> (56 * density).toInt()
        }
        // Avatare sitzen knapp über dem Dock; Leinwand endet direkt darüber.
        val bottomReserve = dockH + legendH + legendGap
        val availW = root.width.coerceAtLeast(1)
        val availH = (root.height - bottomReserve).coerceAtLeast(1)
        val targetAr = CanvasStore.TEMPLATE_CANVAS_AR
        val availAr = availW.toFloat() / availH.toFloat()
        val canvasW: Int
        val canvasH: Int
        if (availAr > targetAr) {
            canvasH = availH
            canvasW = (canvasH * targetAr).toInt().coerceAtLeast(1)
        } else {
            canvasW = availW
            canvasH = (canvasW / targetAr).toInt().coerceAtLeast(1)
        }
        val drawLp = drawingView.layoutParams as FrameLayout.LayoutParams
        drawLp.width = canvasW
        drawLp.height = canvasH
        drawLp.bottomMargin = bottomReserve
        drawLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        drawingView.layoutParams = drawLp
        if (::stickerOverlay.isInitialized) {
            val stickerLp = stickerOverlay.layoutParams as FrameLayout.LayoutParams
            stickerLp.width = canvasW
            stickerLp.height = canvasH
            stickerLp.bottomMargin = bottomReserve
            stickerLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            stickerOverlay.layoutParams = stickerLp
        }
        val lp = legendScroll.layoutParams as FrameLayout.LayoutParams
        lp.bottomMargin = dockH + legendGap
        legendScroll.layoutParams = lp
    }

    private fun isTrialSession(): Boolean {
        val acc = AccountSession.account.value
        if (acc?.isTrial == true) return true
        if (acc?.googleLinked != true && trialDrawUntil > 0L) return true
        return trialDrawUntil > System.currentTimeMillis()
    }

    private suspend fun currentLobbyCode(): String? {
        val id = lobbyId
        val fromPrefs = if (!id.isNullOrBlank()) {
            LuvApp.instance.prefs.snapshot().lobbies
                .firstOrNull { it.id == id }
                ?.code
        } else null
        return fromPrefs?.takeIf { it.isNotBlank() }
            ?: AccountSession.account.value?.trialRoomCode?.takeIf { it.isNotBlank() }
    }

    /**
     * Gast-Zeichnung entfernen + aus Lobby kicken; Code für späteren Rejoin merken.
     */
    private suspend fun performTrialExit() {
        if (trialExitDone) return
        trialExitDone = true
        val code = currentLobbyCode()
        val uid = AccountSession.account.value?.id
        val lid = lobbyId
        if (!code.isNullOrBlank()) {
            PendingInviteRejoin.offer(code)
            runCatching { LuvApiClient.trialExitRoom(code) }
        }
        if (!uid.isNullOrBlank() && !lid.isNullOrBlank()) {
            CanvasStore.removeStrokesByAuthor(uid, lid)
        }
        drawingView.setStrokes(CanvasStore.snapshot(lid), animateNew = false)
    }

    /** Zurück: bei Trial → Exit + Onboarding (Name/Tutorial/Google), sonst Home. */
    private fun leaveCanvas() {
        if (isTrialSession()) {
            lifecycleScope.launch {
                performTrialExit()
                PendingOnboardingRestart.offer()
                finish()
            }
            return
        }
        finish()
    }

    private fun setupTrialGate() {
        val until = trialDrawUntil
        val isTrial = isTrialSession() || until > System.currentTimeMillis()
        if (!isTrial) return
        lifecycleScope.launch {
            AccountSession.trialExpired.collect {
                showTrialGate()
            }
        }
        if (until > System.currentTimeMillis()) {
            val delayMs = until - System.currentTimeMillis()
            lifecycleScope.launch {
                kotlinx.coroutines.delay(delayMs)
                val acc = AccountSession.account.value
                if (acc?.googleLinked == true && acc.isTrial != true) return@launch
                showTrialGate()
            }
        } else if (until > 0L) {
            showTrialGate()
        }
    }

    private fun showTrialGate() {
        if (trialGateActive) return
        if (AccountSession.account.value?.googleLinked == true &&
            AccountSession.account.value?.isTrial != true
        ) {
            return
        }
        trialGateActive = true
        drawingView.inputBlocked = true
        lifecycleScope.launch {
            performTrialExit()
        }
        val root = rootView ?: return
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xCC0B0E14.toInt())
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Probezeit vorbei"
            setTextColor(0xFFF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }
        val lede = TextView(this).apply {
            text = "Melde dich mit Google an. Hast du schon ein Konto, " +
                "bist du gleich wieder mit deinem Namen dabei — sonst wählst du kurz einen Namen."
            setTextColor(0xCCF4F1EC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 36)
        }
        val btn = TextView(this).apply {
            text = "Mit Google anmelden"
            setTextColor(0xFF111111.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(48, 28, 48, 28)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(0xFFFFFFFF.toInt())
            }
            setOnClickListener { launchTrialGoogle() }
        }
        col.addView(title)
        col.addView(lede)
        col.addView(btn)
        overlay.addView(
            col,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        root.addView(overlay)
        trialOverlay = overlay
    }

    private fun dismissTrialGate() {
        trialGateActive = false
        drawingView.inputBlocked = false
        trialOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        trialOverlay = null
        trialDrawUntil = 0L
    }

    private fun launchTrialGoogle() {
        lifecycleScope.launch {
            try {
                val webClientId = GoogleAuth.fetchWebClientId()
                    ?: throw com.luv.couple.net.LuvApiException(
                        "Google-Login ist noch nicht eingerichtet.",
                        error = "google_disabled"
                    )
                trialGoogleLauncher.launch(GoogleAuth.signInIntent(this@LockDrawActivity, webClientId))
            } catch (e: Exception) {
                Toast.makeText(
                    this@LockDrawActivity,
                    e.message ?: "Google-Anmeldung fehlgeschlagen",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val EXTRA_LOBBY_ID = "lobby_id"
        /** Epoch-ms: Probezeichnen bis dahin (Invite-Trial). */
        const val EXTRA_TRIAL_DRAW_UNTIL = "trial_draw_until"
        /** Intensiv-Modus: von Nähe-Notification geweckt. */
        const val EXTRA_WAKE_NEAR = "wake_near"

        @Volatile
        private var foregroundLobbyId: String? = null

        fun isCanvasForeground(lobbyId: String): Boolean =
            foregroundLobbyId != null && foregroundLobbyId == lobbyId
    }
}
