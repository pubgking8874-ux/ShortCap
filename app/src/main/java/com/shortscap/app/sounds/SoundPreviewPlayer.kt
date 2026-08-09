package com.shortscap.app.sounds

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * SoundPreviewPlayer — plays ONLY the preview of a single [AppSound] when the
 * user taps ▶ in the Sound & Effects page.
 *
 * Preview never triggers a reminder, notification, Study Mode action or
 * Shorts-limit behavior — it is purely the short, non-intrusive sound itself,
 * generated locally with ToneGenerator (no audio assets, no permissions).
 * Each sound maps to a distinct standard tone; the tone stops naturally after
 * ~450 ms and is released right after.
 */
object SoundPreviewPlayer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var active: ToneGenerator? = null
    private var releaseRunnable: Runnable? = null

    /** Plays [sound] once, stopping any currently previewing tone. */
    fun play(sound: AppSound) {
        stop()
        val tone = toneFor(sound) ?: return
        val generator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
        } catch (ignored: RuntimeException) {
            return // audio device unavailable right now — skip silently
        }
        active = generator
        generator.startTone(tone, 450)
        releaseRunnable = Runnable { release(generator) }
        mainHandler.postDelayed(releaseRunnable!!, 650)
    }

    /** Stops the current preview immediately (if any). */
    fun stop() {
        releaseRunnable?.let { mainHandler.removeCallbacks(it) }
        releaseRunnable = null
        active?.let { release(it) }
    }

    private fun release(generator: ToneGenerator) {
        if (active === generator) active = null
        runCatching { generator.release() }
    }

    /** Distinct, recognizable tone per sound (all short / non-intrusive). */
    private fun toneFor(sound: AppSound): Int? = when (sound) {
        AppSound.DEFAULT -> ToneGenerator.TONE_PROP_BEEP
        AppSound.GENTLE_CHIME -> ToneGenerator.TONE_PROP_PROMPT
        AppSound.SOFT_BELL -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
        AppSound.CALM_TONE -> ToneGenerator.TONE_PROP_BEEP2
        AppSound.FOCUS_TONE -> ToneGenerator.TONE_SUP_ERROR
        AppSound.WARNING_PULSE -> ToneGenerator.TONE_PROP_NACK
        AppSound.LIMIT_ALERT -> ToneGenerator.TONE_CDMA_ABBR_ALERT
        AppSound.SUCCESS_CHIME -> ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE
    }
}
