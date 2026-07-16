package com.luv.couple.lock

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.text.InputFilter
import android.text.format.DateFormat
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
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
    private var pulseAnimator: ObjectAnimator? = null

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

        lifecycleScope.launch {
            val gender = LuvApp.instance.prefs.snapshot().gender
            root.setBackgroundColor(CanvasStore.backgroundFor(gender))
            drawingView.myGender = gender
        }

        MidnightClear.checkAndClearIfNewDay(this)
        updateClock()
        drawingView.setStrokes(CanvasStore.snapshot(), animateNew = false)

        drawingView.onStrokeFinished = { points ->
            CanvasStore.addLocalStroke(points)
            drawingView.setStrokes(CanvasStore.snapshot(), animateNew = true)
        }
        drawingView.onLongPressClear = {
            CanvasStore.clear(notifyPeer = true)
            drawingView.clearCanvas()
            statusView.text = "Leinwand gelöscht"
        }
        drawingView.onDoubleTapUndo = {
            if (CanvasStore.undoLastLocalStroke()) {
                drawingView.setStrokes(CanvasStore.snapshot(), animateNew = false)
                statusView.text = "Linie rückgängig"
            }
        }
        drawingView.onDotPlaced = { point ->
            CanvasStore.addLocalDot(point.x, point.y)
            drawingView.setStrokes(CanvasStore.snapshot(), animateNew = true)
        }

        findViewById<TextView>(R.id.btnNote).setOnClickListener { showNoteDialog() }
        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            lifecycleScope.launch {
                CanvasCapture.saveMoment(this@LockDrawActivity)
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
                drawingView.setStrokes(CanvasStore.snapshot(), animateNew = true)
            }
        }
        lifecycleScope.launch {
            PairConnectionService.events.collectLatest { event ->
                when (event) {
                    is PairEvent.StrokeReceived -> {
                        drawingView.addStroke(event.stroke, fadeIn = true)
                        maybeHaptic()
                    }
                    is PairEvent.StrokeUndone -> {
                        drawingView.setStrokes(CanvasStore.snapshot(), animateNew = false)
                    }
                    PairEvent.Cleared -> drawingView.clearCanvas()
                }
            }
        }
        lifecycleScope.launch {
            PairConnectionService.state.collectLatest { state ->
                statusView.text = when (state) {
                    ConnectionState.CONNECTED -> "Verbunden · zeichnen"
                    ConnectionState.HOSTING -> "Warte auf Partner…"
                    ConnectionState.RECONNECTING, ConnectionState.CONNECTING -> "Verbinde erneut…"
                    ConnectionState.IDLE -> "Nicht verbunden"
                }
            }
        }
        lifecycleScope.launch {
            PairSessionState.partnerPresent.collectLatest { present ->
                updatePresence(present)
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
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        MidnightClear.checkAndClearIfNewDay(this)
        drawingView.setStrokes(CanvasStore.snapshot(), animateNew = false)
        PairConnectionService.sendPresence(this, active = true)
    }

    override fun onPause() {
        PairConnectionService.sendPresence(this, active = false)
        super.onPause()
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
                interpolator = AccelerateDecelerateInterpolator()
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
                    PairConnectionService.sendNote(this, text)
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
}
