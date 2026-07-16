package com.luv.couple.lock

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luv.couple.LuvApp
import com.luv.couple.R
import com.luv.couple.data.PeerInfo
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.AccountSession
import com.luv.couple.net.ClearVoteEvent
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairEvent
import com.luv.couple.net.PairSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LockDrawActivity : ComponentActivity() {
    private lateinit var drawingView: DrawingView
    private lateinit var statusView: TextView
    private lateinit var missedBanner: TextView
    private lateinit var reactionBurst: TextView
    private lateinit var legendRow: LinearLayout
    private lateinit var lobbyTitle: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnSave: TextView
    private lateinit var btnGame: TextView
    private lateinit var btnColor: TextView
    private lateinit var bottomDock: View
    private var voteBanner: TextView? = null
    private var lobbyId: String? = null
    private var legendExpanded = false
    private var activeProposalId: String? = null
    private var rootView: android.widget.FrameLayout? = null
    private var ticTacToeVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        btnGame = findViewById(R.id.btnGame)
        btnColor = findViewById(R.id.btnColor)
        bottomDock = findViewById(R.id.bottomDock)

        lobbyId = intent.getStringExtra(EXTRA_LOBBY_ID)
            ?: CanvasStore.activeLobbyId.value
        lobbyId?.let { CanvasStore.setActiveLobby(it) }

        root.setBackgroundColor(CanvasStore.backgroundFor(CanvasStore.cachedColorIndex))
        drawingView.myColorIndex = CanvasStore.cachedColorIndex
        drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
        paintColorButton()

        btnBack.setOnClickListener { finish() }
        btnColor.setOnClickListener { showColorPicker() }
        btnSave.setOnClickListener {
            lifecycleScope.launch {
                CanvasCapture.saveMoment(this@LockDrawActivity, lobbyId)
                    .onSuccess {
                        Toast.makeText(this@LockDrawActivity, R.string.moment_saved, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(this@LockDrawActivity, it.message ?: "Fehler", Toast.LENGTH_SHORT).show()
                    }
            }
        }
        btnGame.setOnClickListener {
            setTicTacToeVisible(!ticTacToeVisible, sync = true)
        }

        listOf(
            R.id.emoji0 to "👍",
            R.id.emoji1 to "😮",
            R.id.emoji2 to "😍",
            R.id.emoji3 to "😢",
            R.id.emoji4 to "❤️"
        ).forEach { (viewId, emoji) ->
            findViewById<TextView>(viewId).setOnClickListener {
                sendReaction(emoji)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val pad = (12 * resources.displayMetrics.density).toInt()
            (lobbyTitle.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                topMargin = bars.top + pad
                lobbyTitle.layoutParams = this
            }
            (btnColor.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                topMargin = bars.top + pad
                btnColor.layoutParams = this
            }
            bottomDock.setPadding(
                bottomDock.paddingLeft,
                bottomDock.paddingTop,
                bottomDock.paddingRight,
                pad + (10 * resources.displayMetrics.density).toInt()
            )
            insets
        }

        legendRow.setOnClickListener {
            legendExpanded = !legendExpanded
            refreshLegend()
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
            CanvasStore.updateProfile(snap.nickname, snap.colorIndex)
            CanvasStore.updateKnownLobbies(snap.lobbies.map { it.id })
            val lobby = snap.lobbies.firstOrNull { it.id == lobbyId }
            lobbyTitle.text = lobby?.name.orEmpty()
            applyMyColor(snap.colorIndex, persist = false, sync = false)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
            refreshLegend()
        }

        MidnightClear.checkAndClearIfNewDay(this)
        refreshLegend()

        drawingView.onStrokeFinished = { points ->
            CanvasStore.addLocalStroke(points, lobbyId = lobbyId)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = true)
            refreshLegend()
        }
        drawingView.onLongPressClear = {
            PairConnectionService.sendClear(this, lobbyId)
            statusView.text = "Abstimmung: Leinwand löschen?"
        }
        drawingView.onDoubleTapUndo = {
            if (CanvasStore.undoLastLocalStroke(lobbyId)) {
                drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
                statusView.text = "Linie rückgängig"
            }
        }
        drawingView.onDotPlaced = { point ->
            CanvasStore.addLocalDot(point.x, point.y, lobbyId = lobbyId)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = true)
        }

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        lifecycleScope.launch {
            CanvasStore.revision.collectLatest {
                val id = lobbyId ?: return@collectLatest
                drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = true)
                refreshLegend()
            }
        }
        lifecycleScope.launch {
            PairConnectionService.events.collectLatest { event ->
                val id = lobbyId ?: return@collectLatest
                when (event) {
                    is PairEvent.StrokeReceived -> if (event.lobbyId == id) {
                        drawingView.addStroke(event.stroke, fadeIn = true)
                        maybeHaptic()
                        refreshLegend()
                    }
                    is PairEvent.StrokeUndone -> if (event.lobbyId == id) {
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                    }
                    is PairEvent.Cleared -> if (event.lobbyId == id) {
                        drawingView.clearCanvas()
                    }
                    is PairEvent.RecolorReceived -> if (event.lobbyId == id) {
                        drawingView.setStrokes(CanvasStore.snapshot(id), animateNew = false)
                        refreshLegend()
                    }
                    is PairEvent.ReactionReceived -> if (event.lobbyId == id) {
                        showReaction(event.emoji)
                    }
                    is PairEvent.GameBoardReceived -> if (event.lobbyId == id) {
                        if (event.game == "ttt" || event.game.isBlank()) {
                            setTicTacToeVisible(event.visible, sync = false)
                        }
                    }
                }
            }
        }
        // Reconnect läuft still im Hintergrund — Status nur in der Lobby-Übersicht
        statusView.text = getString(R.string.draw_hint)
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
                        activeProposalId = event.proposalId
                        showClearVote("${event.by} will löschen (${event.yes}/${event.total})")
                    }
                    is ClearVoteEvent.Update -> if (event.lobbyId == id) {
                        statusView.text = "Abstimmung ${event.yes}/${event.no}"
                    }
                    is ClearVoteEvent.Result -> if (event.lobbyId == id) {
                        hideClearVote()
                        statusView.text = if (event.approved) "Leinwand leer" else "Abgelehnt"
                        activeProposalId = null
                    }
                }
            }
        }
        lifecycleScope.launch {
            AccountSession.economyBlocks.collectLatest { msg ->
                Toast.makeText(this@LockDrawActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendReaction(emoji: String) {
        showReaction(emoji)
        PairConnectionService.sendReaction(this, emoji, lobbyId)
    }

    private fun setTicTacToeVisible(visible: Boolean, sync: Boolean) {
        ticTacToeVisible = visible
        drawingView.showTicTacToe = visible
        btnGame.alpha = if (visible) 1f else 0.72f
        btnGame.scaleX = if (visible) 1.08f else 1f
        btnGame.scaleY = if (visible) 1.08f else 1f
        if (sync) {
            PairConnectionService.sendGameBoard(this, game = "ttt", visible = visible, lobbyId = lobbyId)
        }
    }

    private fun showReaction(emoji: String) {
        reactionBurst.text = emoji
        reactionBurst.alpha = 0f
        reactionBurst.scaleX = 0.4f
        reactionBurst.scaleY = 0.4f
        reactionBurst.animate()
            .alpha(1f)
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(220)
            .withEndAction {
                reactionBurst.animate()
                    .alpha(0f)
                    .scaleX(1.4f)
                    .scaleY(1.4f)
                    .setStartDelay(500)
                    .setDuration(420)
                    .start()
            }
            .start()
    }

    private fun paintColorButton() {
        val color = PeerPalette.strokeColor(CanvasStore.cachedColorIndex)
        btnColor.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(
                (2 * resources.displayMetrics.density).toInt(),
                0x66FFFFFF
            )
        }
    }

    private fun applyMyColor(index: Int, persist: Boolean, sync: Boolean) {
        val safe = index.coerceIn(0, PeerPalette.COLOR_COUNT - 1)
        CanvasStore.updateProfile(CanvasStore.cachedNickname, safe)
        drawingView.myColorIndex = safe
        rootView?.setBackgroundColor(CanvasStore.backgroundFor(safe))
        paintColorButton()
        if (persist) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { LuvApp.instance.prefs.setColorIndex(safe) }
            }
        }
        if (sync) {
            CanvasStore.recolorOwnStrokes(safe, lobbyId)
            PairConnectionService.sendPresence(this, active = true, lobbyId = lobbyId)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
        }
        refreshLegend()
    }

    private fun showColorPicker() {
        val id = lobbyId ?: return
        val mine = CanvasStore.cachedColorIndex
        val taken = PairSessionState.takenColorIndices(id, CanvasStore.cachedNickname) +
            CanvasStore.snapshot(id)
                .filter { !it.isLocal && !it.nickname.equals(CanvasStore.cachedNickname, ignoreCase = true) }
                .map { it.colorIndex }
                .toSet()

        val dp = resources.displayMetrics.density
        val grid = GridLayout(this).apply {
            columnCount = 5
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Deine Farbe")
            .setMessage("Nur freie Farben — deine Linien wechseln für alle mit.")
            .create()

        PeerPalette.allIndices().forEach { index ->
            val blocked = index in taken && index != mine
            val cell = TextView(this).apply {
                val size = (48 * dp).toInt()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                }
                gravity = Gravity.CENTER
                alpha = if (blocked) 0.28f else 1f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(PeerPalette.strokeColor(index))
                    if (index == mine) {
                        setStroke((3 * dp).toInt(), 0xFFFFFFFF.toInt())
                    }
                }
                isEnabled = !blocked
                setOnClickListener {
                    if (blocked) return@setOnClickListener
                    applyMyColor(index, persist = true, sync = true)
                    dialog.dismiss()
                }
            }
            grid.addView(cell)
        }

        val scroll = ScrollView(this).apply {
            addView(grid)
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }
        dialog.setView(scroll)
        dialog.show()
    }

    private fun showClearVote(text: String) {
        val root = rootView ?: return
        if (voteBanner == null) {
            voteBanner = TextView(this).apply {
                setBackgroundResource(R.drawable.note_banner_bg)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                setPadding(36, 28, 36, 28)
                gravity = Gravity.CENTER
                elevation = 16f
                setOnClickListener {
                    val proposalId = activeProposalId ?: return@setOnClickListener
                    PairConnectionService.sendClearVote(
                        this@LockDrawActivity,
                        proposalId,
                        yes = true,
                        lobbyId = lobbyId
                    )
                    statusView.text = "Ja gestimmt"
                }
                setOnLongClickListener {
                    val proposalId = activeProposalId ?: return@setOnLongClickListener true
                    PairConnectionService.sendClearVote(
                        this@LockDrawActivity,
                        proposalId,
                        yes = false,
                        lobbyId = lobbyId
                    )
                    statusView.text = "Nein gestimmt"
                    true
                }
            }
            val lp = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                marginStart = 40
                marginEnd = 40
            }
            root.addView(voteBanner, lp)
        }
        voteBanner?.text = "$text\nTipp = Ja · Lang = Nein"
        voteBanner?.alpha = 1f
    }

    private fun hideClearVote() {
        voteBanner?.animate()?.alpha(0f)?.setDuration(200)?.start()
    }

    override fun onResume() {
        super.onResume()
        MidnightClear.checkAndClearIfNewDay(this)
        drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
        PairConnectionService.sendPresence(this, active = true, lobbyId = lobbyId)
        refreshLegend()
    }

    override fun onPause() {
        PairConnectionService.sendPresence(this, active = false, lobbyId = lobbyId)
        super.onPause()
    }

    private fun refreshLegend() {
        val id = lobbyId ?: return
        val nickname = CanvasStore.cachedNickname
        val color = CanvasStore.cachedColorIndex
        val peers = PairSessionState.legendPeers(id, nickname, color)
        val fromStrokes = CanvasStore.snapshot(id)
            .filter { !it.isLocal && !it.nickname.isNullOrBlank() }
            .map {
                PeerInfo(
                    peerKey = it.authorId ?: it.nickname!!,
                    nickname = it.nickname!!,
                    colorIndex = it.colorIndex,
                    active = false
                )
            }
        val merged = (peers + fromStrokes).distinctBy { it.nickname.lowercase() }.take(PeerPalette.MAX_PEERS)
        legendRow.removeAllViews()
        val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
        merged.forEachIndexed { index, peer ->
            if (index > 0) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(pad * 2, 1)
                }
                legendRow.addView(spacer)
            }
            legendRow.addView(buildLegendChip(peer))
        }
    }

    private fun buildLegendChip(peer: PeerInfo): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        }
        val circle = TextView(this).apply {
            val size = (22 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            gravity = Gravity.CENTER
            text = peer.nickname.trim().take(1).uppercase(Locale.getDefault())
            setTextColor(0xFF1A1F2E.toInt())
            textSize = 11f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(PeerPalette.strokeColor(peer.colorIndex))
            }
            alpha = if (peer.active || peer.peerKey == "me") 1f else 0.7f
        }
        row.addView(circle)
        if (legendExpanded) {
            val name = TextView(this).apply {
                text = peer.nickname
                setTextColor(0xE6FFFFFF.toInt())
                textSize = 12f
                setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }
            row.addView(name)
        }
        return row
    }

    private fun maybeHaptic() {
        lifecycleScope.launch {
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

    companion object {
        const val EXTRA_LOBBY_ID = "lobby_id"
    }
}
