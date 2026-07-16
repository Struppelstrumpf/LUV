package com.luv.couple.lock

import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.luv.couple.LuvApp
import com.luv.couple.R
import com.luv.couple.data.ConnectionState
import com.luv.couple.net.PairConnectionService
import com.luv.couple.net.PairEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class LockDrawActivity : ComponentActivity() {
    private lateinit var drawingView: DrawingView
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var statusView: TextView

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

        lifecycleScope.launch {
            val gender = LuvApp.instance.prefs.snapshot().gender
            root.setBackgroundColor(CanvasStore.backgroundFor(gender))
        }

        updateClock()
        drawingView.setStrokes(CanvasStore.snapshot())
        drawingView.onStrokeFinished = { points ->
            CanvasStore.addLocalStroke(points)
            drawingView.setStrokes(CanvasStore.snapshot())
        }
        drawingView.onLongPressClear = {
            CanvasStore.clear(notifyPeer = true)
            drawingView.clearCanvas()
            statusView.text = "Leinwand gelöscht"
        }

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        lifecycleScope.launch {
            CanvasStore.revision.collectLatest {
                drawingView.setStrokes(CanvasStore.snapshot())
            }
        }
        lifecycleScope.launch {
            PairConnectionService.events.collectLatest { event ->
                when (event) {
                    is PairEvent.StrokeReceived -> drawingView.addStroke(event.stroke)
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
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        drawingView.setStrokes(CanvasStore.snapshot())
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        clockView.text = DateFormat.format("H:mm", now)
        dateView.text = DateFormat.format("EEEE, d. MMMM", now)
            .toString()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
