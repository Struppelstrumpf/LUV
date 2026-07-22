package com.luv.couple.ui.wedding

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.luv.couple.R
import com.luv.couple.net.LuvApiClient
import kotlin.math.roundToInt

/**
 * Musik:
 * - Orgel (Ambient), solange man nicht am Altar sitzt
 * - Music-Box einmal, wenn Brautpaar sich auf Altar-Platz setzt
 * - wieder Orgel beim Aufstehen
 * - Music-Box erneut, wenn man wieder sitzt und der 30s-Timer startet
 */
class WeddingChapelMusic(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var player: MediaPlayer? = null
    private var mode: Mode = Mode.NONE
    private var pausedByLifecycle = false
    private var wasOnAltar = false
    private var timerBridalPlayed = false

    var volume: Float = prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)
        private set
    var muted: Boolean = prefs.getBoolean(KEY_MUTED, false)
        private set

    fun start() {
        pausedByLifecycle = false
        ensureAmbient()
    }

    fun pause() {
        pausedByLifecycle = true
        runCatching {
            val p = player ?: return
            if (p.isPlaying) p.pause()
        }
    }

    fun resume() {
        pausedByLifecycle = false
        applyVolume()
        runCatching {
            val p = player ?: return
            if (mode != Mode.SILENT && !p.isPlaying) p.start()
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        prefs.edit().putBoolean(KEY_MUTED, value).apply()
        applyVolume()
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        if (volume > 0.01f && muted) {
            muted = false
            prefs.edit().putBoolean(KEY_MUTED, false).apply()
        }
        prefs.edit().putFloat(KEY_VOLUME, volume).apply()
        applyVolume()
    }

    fun nudgeVolume(delta: Float) {
        setVolume(volume + delta)
    }

    fun release() {
        releasePlayer()
        mode = Mode.NONE
        wasOnAltar = false
        timerBridalPlayed = false
    }

    /**
     * @param coupleOnAltar lokaler Spieler ist Brautpaar und sitzt auf gelbem Altar-Platz
     */
    fun sync(ceremony: LuvApiClient.CeremonyInfo?, coupleOnAltar: Boolean) {
        if (pausedByLifecycle) return

        if (!coupleOnAltar) {
            wasOnAltar = false
            timerBridalPlayed = false
            ensureAmbient()
            return
        }

        val timerOn = ceremony?.altarHoldActive == true

        // Gerade hingesetzt → Music-Box
        if (!wasOnAltar) {
            wasOnAltar = true
            playBridalOnce()
            return
        }

        // Timer startet (erneut) → Music-Box nochmal von vorn
        if (timerOn && !timerBridalPlayed) {
            timerBridalPlayed = true
            playBridalOnce()
            return
        }

        // Music-Box läuft noch — nicht unterbrechen
        if (mode == Mode.BRIDAL && player?.isPlaying == true) return

        // Nach Music-Box am Altar: kurz Stille (Pastor), sonst Ambient wenn Empfang
        val afterPastor =
            ceremony?.pastorPhase == "reception" ||
                ceremony?.phase == "reception" ||
                ceremony?.pastorPhase == "vows"
        if (afterPastor && mode != Mode.BRIDAL) {
            ensureAmbient()
        }
    }

    private fun playBridalOnce() {
        releasePlayer()
        mode = Mode.BRIDAL
        runCatching {
            val p = MediaPlayer.create(appContext, R.raw.wedding_march_music_box) ?: run {
                mode = Mode.SILENT
                return
            }
            p.isLooping = false
            p.setAudioAttributes(audioAttrs())
            p.setOnCompletionListener {
                mode = Mode.SILENT
                releasePlayer()
            }
            player = p
            applyVolume()
            if (!pausedByLifecycle) p.start()
        }.onFailure {
            mode = Mode.SILENT
        }
    }

    private fun ensureAmbient() {
        if (mode == Mode.AMBIENT && player != null) {
            applyVolume()
            if (!pausedByLifecycle) {
                runCatching {
                    val p = player ?: return
                    if (!p.isPlaying) p.start()
                }
            }
            return
        }
        releasePlayer()
        mode = Mode.AMBIENT
        runCatching {
            val p = MediaPlayer.create(appContext, R.raw.wedding_bridal_march) ?: return
            p.isLooping = true
            p.setAudioAttributes(audioAttrs())
            player = p
            applyVolume()
            if (!pausedByLifecycle) p.start()
        }
    }

    private fun releasePlayer() {
        runCatching {
            player?.setOnCompletionListener(null)
            player?.stop()
            player?.release()
        }
        player = null
    }

    private fun audioAttrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private fun applyVolume() {
        val v = if (muted || mode == Mode.SILENT) 0f else volume.coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
    }

    fun volumePercent(): Int = (volume * 100f).roundToInt().coerceIn(0, 100)

    private enum class Mode { NONE, AMBIENT, BRIDAL, SILENT }

    companion object {
        private const val PREFS = "luv_wedding_chapel_music"
        private const val KEY_VOLUME = "volume"
        private const val KEY_MUTED = "muted"
        const val DEFAULT_VOLUME = 0.28f
    }
}
