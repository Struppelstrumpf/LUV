package com.luv.couple.lock

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luv.couple.LuvApp
import com.luv.couple.R
import com.luv.couple.data.ConnectionState
import com.luv.couple.data.PeerInfo
import com.luv.couple.data.PeerPalette
import com.luv.couple.net.AccountSession
import com.luv.couple.net.ClearVoteEvent
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairEvent
import com.luv.couple.net.PairSessionState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class LockDrawActivity : ComponentActivity() {
    private lateinit var drawingView: DrawingView
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var statusView: TextView
    private lateinit var presenceDot: View
    private lateinit var presenceLabel: TextView
    private lateinit var noteBanner: TextView
    private lateinit var missedBanner: TextView
    private lateinit var legendRow: LinearLayout
    private lateinit var lobbyTitle: TextView
    private var voteBanner: TextView? = null
    private var lobbyId: String? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var legendExpanded = false
    private var activeProposalId: String? = null

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
        drawingView = findViewById(R.id.drawingView)
        clockView = findViewById(R.id.lockClock)
        dateView = findViewById(R.id.lockDate)
        statusView = findViewById(R.id.statusDot)
        presenceDot = findViewById(R.id.presenceDot)
        presenceLabel = findViewById(R.id.presenceLabel)
        noteBanner = findViewById(R.id.noteBanner)
        missedBanner = findViewById(R.id.missedBanner)
        legendRow = findViewById(R.id.legendRow)
        lobbyTitle = findViewById(R.id.lobbyTitle)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        legendRow.setOnClickListener {
            legendExpanded = !legendExpanded
            refreshLegend()
        }

        lifecycleScope.launch {
            val snap = LuvApp.instance.prefs.snapshot()
            lobbyId = intent.getStringExtra(EXTRA_LOBBY_ID)
                ?: snap.activeLobbyId
                ?: snap.lobbies.firstOrNull()?.id
            lobbyId?.let {
                prefsSetActive(it)
                CanvasStore.setActiveLobby(it)
            }
            val lobby = snap.lobbies.firstOrNull { it.id == lobbyId }
            lobbyTitle.text = lobby?.name.orEmpty()
            root.setBackgroundColor(CanvasStore.backgroundFor(snap.colorIndex))
            drawingView.myColorIndex = snap.colorIndex
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = false)
            refreshLegend()
        }

        MidnightClear.checkAndClearIfNewDay(this)
        updateClock()

        drawingView.onStrokeFinished = { points ->
            CanvasStore.addLocalStroke(points, lobbyId = lobbyId)
            drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = true)
            refreshLegend()
        }
        drawingView.onLongPressClear = {
            // Nur Abstimmung starten — Clear erst nach Mehrheit
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

        findViewById<TextView>(R.id.btnNote).setOnClickListener { showNoteDialog() }
        findViewById<TextView>(R.id.btnSave).setOnClickListener {
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

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        lifecycleScope.launch {
            CanvasStore.revision.collectLatest {
                drawingView.setStrokes(CanvasStore.snapshot(lobbyId), animateNew = true)
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
                }
            }
        }
        lifecycleScope.launch {
            PairConnectionService.lobbyStates.collectLatest { map ->
                val id = lobbyId ?: return@collectLatest
                statusView.text = when (map[id] ?: ConnectionState.IDLE) {
                    ConnectionState.CONNECTED -> "Verbunden · zeichnen"
                    ConnectionState.HOSTING -> "Warte auf Leute…"
                    ConnectionState.RECONNECTING, ConnectionState.CONNECTING -> "Verbinde erneut…"
                    ConnectionState.IDLE -> "Nicht verbunden"
                }
            }
        }
        lifecycleScope.launch {
            val id = lobbyId ?: return@launch
            PairSessionState.peers(id).collectLatest {
                updatePresence(PairSessionState.anyonePresent(id))
                refreshLegend()
            }
        }
        lifecycleScope.launch {
            PairSessionState.notes.collectLatest { text ->
                showBanner(noteBanner, text, 3500)
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
                        statusView.text = "Abstimmung ${event.yes} Ja · ${event.no} Nein"
                    }
                    is ClearVoteEvent.Result -> if (event.lobbyId == id) {
                        hideClearVote()
                        statusView.text = if (event.approved) {
                            "Mehrheit sagt Ja — Leinwand leer"
                        } else {
                            "Nicht genug Stimmen"
                        }
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

    private fun showClearVote(text: String) {
        val root = findViewById<android.widget.FrameLayout>(R.id.lockRoot)
        if (voteBanner == null) {
            voteBanner = TextView(this).apply {
                setBackgroundResource(R.drawable.note_banner_bg)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                setPadding(36, 28, 36, 28)
                gravity = Gravity.CENTER
                setOnClickListener {
                    val proposalId = activeProposalId ?: return@setOnClickListener
                    PairConnectionService.sendClearVote(
                        this@LockDrawActivity,
                        proposalId,
                        yes = true,
                        lobbyId = lobbyId
                    )
                    statusView.text = "Du hast Ja gestimmt"
                }
                setOnLongClickListener {
                    val proposalId = activeProposalId ?: return@setOnLongClickListener true
                    PairConnectionService.sendClearVote(
                        this@LockDrawActivity,
                        proposalId,
                        yes = false,
                        lobbyId = lobbyId
                    )
                    statusView.text = "Du hast Nein gestimmt"
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

    private suspend fun prefsSetActive(id: String) {
        LuvApp.instance.prefs.setActiveLobby(id)
    }

    override fun onResume() {
        super.onResume()
        updateClock()
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
        lifecycleScope.launch {
            val snap = LuvApp.instance.prefs.snapshot()
            val peers = PairSessionState.legendPeers(id, snap.nickname, snap.colorIndex)
            // Auch aus Strokes ergänzen
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
                    val spacer = View(this@LockDrawActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(pad * 2, 1)
                    }
                    legendRow.addView(spacer)
                }
                legendRow.addView(buildLegendChip(peer))
            }
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
            if (LuvApp.instance.prefs.isPartnerHapticEnabled()) {
                drawingView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    private fun updatePresence(present: Boolean) {
        presenceLabel.text = getString(if (present) R.string.partner_here else R.string.partner_away)
        presenceDot.alpha = if (present) 1f else 0.35f
        pulseAnimator?.cancel()
        if (present) {
            pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                presenceDot,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f, 1f)
            ).apply {
                duration = 1200
                repeatCount = ObjectAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }
        } else {
            presenceDot.scaleX = 1f
            presenceDot.scaleY = 1f
        }
    }

    private fun showBanner(view: TextView, text: String, durationMs: Long) {
        view.text = text
        view.animate().alpha(1f).setDuration(220).start()
        view.postDelayed({
            view.animate().alpha(0f).setDuration(280).start()
        }, durationMs)
    }

    private fun showNoteDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.note_hint)
            filters = arrayOf(InputFilter.LengthFilter(80))
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.send_note)
            .setView(input)
            .setPositiveButton(R.string.note_send) { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    PairConnectionService.sendNote(this, text, lobbyId)
                    showBanner(noteBanner, text, 2500)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        clockView.text = DateFormat.format("H:mm", now)
        dateView.text = DateFormat.format("EEEE, d. MMMM", now)
            .toString()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    companion object {
        const val EXTRA_LOBBY_ID = "lobby_id"
    }
}
