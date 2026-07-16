package com.luv.couple.lock

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
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
import kotlin.math.min

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
    private var voteOverlay: View? = null
    private var voteByView: TextView? = null
    private var voteProgressView: TextView? = null
    private var lobbyId: String? = null
    private var legendExpanded = false
    private var activeProposalId: String? = null
    private var rootView: FrameLayout? = null
    private var ticTacToeVisible = false
    private var hasVotedClear = false

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
            val dp = resources.displayMetrics.density
            val pad = (12 * dp).toInt()
            (lobbyTitle.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = bars.top + pad
                lobbyTitle.layoutParams = this
            }
            (btnColor.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = bars.top + pad
                btnColor.layoutParams = this
            }
            bottomDock.setPadding(
                bottomDock.paddingLeft,
                bottomDock.paddingTop,
                bottomDock.paddingRight,
                bars.bottom + (12 * dp).toInt()
            )
            // Emoji kleiner, wenn die Breite eng ist
            val emojiRow = findViewById<LinearLayout>(R.id.emojiRow)
            val per = (resources.displayMetrics.widthPixels - (36 * dp).toInt()) / 5f
            val emojiSize = when {
                per < 48 * dp -> 18f
                per < 56 * dp -> 20f
                else -> 22f
            }
            for (i in 0 until emojiRow.childCount) {
                (emojiRow.getChildAt(i) as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, emojiSize)
            }
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
            statusView.text = "Löschen? Kostet dich 1 Coin"
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
                        hasVotedClear = false
                        showClearVote(event.by, event.yes, 0, event.total)
                    }
                    is ClearVoteEvent.Update -> if (event.lobbyId == id) {
                        updateClearVote(event.yes, event.no, event.total)
                    }
                    is ClearVoteEvent.Result -> if (event.lobbyId == id) {
                        hideClearVote()
                        statusView.text = if (event.approved) "Leinwand leer" else "Abgelehnt"
                        activeProposalId = null
                        hasVotedClear = false
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
        val screenW = resources.displayMetrics.widthPixels
        // Dialog-Ränder + Innenabstand — Palette muss ohne Horizontal-Scroll passen
        val usable = (screenW - (56 * dp).toInt()).coerceAtLeast((240 * dp).toInt())
        val cols = 5
        val gap = (5 * dp).toInt()
        val sidePad = (10 * dp).toInt()
        val cell = min(
            (48 * dp).toInt(),
            ((usable - sidePad * 2 - gap * (cols - 1)) / cols).coerceAtLeast((30 * dp).toInt())
        )

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sidePad, (4 * dp).toInt(), sidePad, (8 * dp).toInt())
        }
        val hint = TextView(this).apply {
            text = "Nur freie Farben — Linien wechseln für alle mit."
            setTextColor(0xFF9AA3B2.toInt())
            textSize = 13f
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        val grid = GridLayout(this).apply {
            columnCount = cols
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Deine Farbe")
            .create()

        PeerPalette.allIndices().forEach { index ->
            val blocked = index in taken && index != mine
            val cellView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cell
                    height = cell
                    setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
                }
                alpha = if (blocked) 0.28f else 1f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(PeerPalette.strokeColor(index))
                    if (index == mine) {
                        setStroke((3 * dp).toInt(), 0xFFFFFFFF.toInt())
                    }
                }
                isClickable = !blocked
                isEnabled = !blocked
                setOnClickListener {
                    if (blocked) return@setOnClickListener
                    applyMyColor(index, persist = true, sync = true)
                    dialog.dismiss()
                }
            }
            grid.addView(cellView)
        }
        wrap.addView(hint)
        wrap.addView(grid)
        dialog.setView(wrap)
        dialog.show()
        dialog.window?.setLayout(
            min(screenW - (32 * dp).toInt(), usable + sidePad * 2 + (24 * dp).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showClearVote(by: String, yes: Int, no: Int, total: Int) {
        val root = rootView ?: return
        val dp = resources.displayMetrics.density
        if (voteOverlay == null) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.vote_card_bg)
                elevation = 20f
                setPadding((26 * dp).toInt(), (24 * dp).toInt(), (26 * dp).toInt(), (22 * dp).toInt())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                    marginStart = (28 * dp).toInt()
                    marginEnd = (28 * dp).toInt()
                }
            }

            val title = TextView(this).apply {
                text = "Leinwand leeren?"
                setTextColor(0xFFF4F1EC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
            }
            voteByView = TextView(this).apply {
                setTextColor(0xFFFF6B8A.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                setPadding(0, (10 * dp).toInt(), 0, (6 * dp).toInt())
            }
            voteProgressView = TextView(this).apply {
                setTextColor(0xFF9AA3B2.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, (20 * dp).toInt())
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
                text = "Ablehnen"
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F1EC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setBackgroundResource(R.drawable.vote_btn_no)
                setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * dp).toInt()
                }
                setOnClickListener { castClearVote(yes = false) }
            }
            val btnYes = TextView(this).apply {
                text = "Leeren"
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F1EC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setBackgroundResource(R.drawable.vote_btn_yes)
                setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (8 * dp).toInt()
                }
                setOnClickListener { castClearVote(yes = true) }
            }
            buttons.addView(btnNo)
            buttons.addView(btnYes)

            card.addView(title)
            card.addView(voteByView)
            card.addView(voteProgressView)
            card.addView(buttons)

            voteOverlay = FrameLayout(this).apply {
                setBackgroundColor(0xCC0B0E14.toInt())
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
        voteByView?.text = "$by möchte löschen"
        updateClearVote(yes, no, total)
        voteOverlay?.visibility = View.VISIBLE
        voteOverlay?.animate()?.alpha(1f)?.setDuration(220)?.start()
    }

    private fun updateClearVote(yes: Int, no: Int, total: Int) {
        val safeTotal = total.coerceAtLeast(1)
        voteProgressView?.text = when {
            hasVotedClear -> "Deine Stimme ist drin · $yes Zustimmen · $no dagegen"
            else -> "$yes von $safeTotal stimmen zu${if (no > 0) " · $no dagegen" else ""}"
        }
    }

    private fun castClearVote(yes: Boolean) {
        val proposalId = activeProposalId ?: return
        if (hasVotedClear) return
        hasVotedClear = true
        PairConnectionService.sendClearVote(
            this,
            proposalId,
            yes = yes,
            lobbyId = lobbyId
        )
        statusView.text = if (yes) "Zugestimmt" else "Abgelehnt"
        voteProgressView?.text = if (yes) {
            "Du hast zugestimmt — warte auf die anderen…"
        } else {
            "Du hast abgelehnt — warte auf die anderen…"
        }
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
