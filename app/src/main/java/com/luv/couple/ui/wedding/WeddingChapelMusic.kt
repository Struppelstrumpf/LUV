package com.luv.couple.ui.wedding

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.luv.couple.R
import com.luv.couple.net.LuvApiClient
import kotlin.math.roundToInt

/**
 * Kapellen-Musik: erst wenn **beide** vom Brautpaar auf den Altar-Markierungen sitzen.
 * Davor Stille (kein Autoplay beim Betreten).
 */
class WeddingChapelMusic(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var player: MediaPlayer? = null
    private var mode: Mode = Mode.NONE
    private var pausedByLifecycle = false
    private var bothWereOnAltar = false

    var volume: Float = prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)
        private set
    var muted: Boolean = prefs.getBoolean(KEY_MUTED, false)
        private set

    fun start() {
        pausedByLifecycle = false
        // Bewusst still — Musik startet erst bei beiden am Altar
        mode = Mode.SILENT
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
            if (mode != Mode.SILENT && mode != Mode.NONE && !p.isPlaying) p.start()
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
        bothWereOnAltar = false
    }

    /**
     * @param bothCoupleOnAltar beide Brautpaar-Personen sitzen auf Altar-Markierung
     */
    fun sync(ceremony: LuvApiClient.CeremonyInfo?, bothCoupleOnAltar: Boolean) {
        if (pausedByLifecycle) return

        if (!bothCoupleOnAltar) {
            bothWereOnAltar = false
            stopToSilent()
            return
        }

        // Gerade erst beide am Altar → Musik starten
        if (!bothWereOnAltar) {
            bothWereOnAltar = true
            playBridalMarch()
            return
        }

        // Läuft noch
        if (mode == Mode.BRIDAL && player?.isPlaying == true) return
        if (mode != Mode.BRIDAL || player == null) {
            playBridalMarch()
        }
    }

    private fun playBridalMarch() {
        releasePlayer()
        mode = Mode.BRIDAL
        runCatching {
            val p = MediaPlayer.create(appContext, R.raw.wedding_chapel_music) ?: run {
                mode = Mode.SILENT
                return
            }
            p.isLooping = true
            p.setAudioAttributes(audioAttrs())
            player = p
            applyVolume()
            if (!pausedByLifecycle) p.start()
        }.onFailure {
            mode = Mode.SILENT
        }
    }

    private fun stopToSilent() {
        if (mode == Mode.SILENT && player == null) return
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

    private enum class Mode { NONE, BRIDAL, SILENT }

    companion object {
        private const val PREFS = "luv_wedding_chapel_music"
        private const val KEY_VOLUME = "volume"
        private const val KEY_MUTED = "muted"
        const val DEFAULT_VOLUME = 0.28f
    }
}
