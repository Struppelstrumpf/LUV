package com.luv.couple.ui.wedding

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.luv.couple.R
import kotlin.math.roundToInt

/**
 * Seichte Kapellen-Hintergrundmusik — lokal, loop, mit Stumm/Lautstärke.
 */
class WeddingChapelMusic(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var player: MediaPlayer? = null

    var volume: Float = prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)
        private set
    var muted: Boolean = prefs.getBoolean(KEY_MUTED, false)
        private set

    fun start() {
        if (player != null) {
            applyVolume()
            resume()
            return
        }
        runCatching {
            val p = MediaPlayer.create(appContext, R.raw.wedding_bridal_march) ?: return
            p.isLooping = true
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player = p
            applyVolume()
            p.start()
        }
    }

    fun pause() {
        runCatching {
            val p = player ?: return
            if (p.isPlaying) p.pause()
        }
    }

    fun resume() {
        runCatching {
            val p = player ?: return
            if (!p.isPlaying) p.start()
            applyVolume()
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
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    private fun applyVolume() {
        val v = if (muted) 0f else volume.coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
    }

    fun volumePercent(): Int = (volume * 100f).roundToInt().coerceIn(0, 100)

    companion object {
        private const val PREFS = "luv_wedding_chapel_music"
        private const val KEY_VOLUME = "volume"
        private const val KEY_MUTED = "muted"
        /** Seicht im Hintergrund */
        const val DEFAULT_VOLUME = 0.28f
    }
}
