package com.luv.couple.ui.wedding

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.luv.couple.R
import com.luv.couple.net.LuvApiClient
import kotlin.math.roundToInt

/**
 * Hochzeits-Musikphasen:
 * - Ambient-Orgel (loop) vor dem 30s-Altar-Timer und wieder nach der Pastor-Rede
 * - Music-Box (einmal) wenn der 30s-Timer startet
 * - danach Stille bis Pastor fertig (vows / Empfang)
 */
class WeddingChapelMusic(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var player: MediaPlayer? = null
    private var mode: Mode = Mode.NONE
    private var bridalStarted = false
    private var bridalFinished = false
    private var pausedByLifecycle = false

    var volume: Float = prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)
        private set
    var muted: Boolean = prefs.getBoolean(KEY_MUTED, false)
        private set

    fun start() {
        pausedByLifecycle = false
        sync(null)
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
    }

    /** An Ceremony-Status koppeln (poll). */
    fun sync(ceremony: LuvApiClient.CeremonyInfo?) {
        if (pausedByLifecycle) return
        val c = ceremony
        val startBridal =
            c != null &&
                !bridalStarted &&
                (
                    c.altarHoldActive ||
                        (c.seatingLocked && c.pastorPhase == "dots")
                    )
        if (startBridal) {
            bridalStarted = true
            playBridalOnce()
            return
        }
        if (bridalStarted && !bridalFinished) {
            // Music-Box läuft noch
            return
        }
        val pastorDone =
            c != null &&
                (
                    c.pastorPhase == "vows" ||
                        c.pastorPhase == "reception" ||
                        c.phase == "reception"
                    )
        if (bridalFinished && !pastorDone) {
            goSilent()
            return
        }
        ensureAmbient()
    }

    private fun playBridalOnce() {
        releasePlayer()
        mode = Mode.BRIDAL
        runCatching {
            val p = MediaPlayer.create(appContext, R.raw.wedding_march_music_box) ?: run {
                bridalFinished = true
                goSilent()
                return
            }
            p.isLooping = false
            p.setAudioAttributes(audioAttrs())
            p.setOnCompletionListener {
                bridalFinished = true
                goSilent()
            }
            player = p
            applyVolume()
            p.start()
        }.onFailure {
            bridalFinished = true
            goSilent()
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

    private fun goSilent() {
        releasePlayer()
        mode = Mode.SILENT
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
