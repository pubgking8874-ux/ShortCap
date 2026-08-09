package com.shortscap.app.sounds

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * LocalSoundPlayer — previews a bundled or device audio file via MediaPlayer.
 *
 * Preview only: it plays the actual file, stops any previously playing
 * preview first, and never triggers reminders, notifications, Study Mode or
 * Shorts-limit behavior. Preparation is asynchronous ([MediaPlayer.prepareAsync])
 * so even large files never block the UI thread; a pending (still preparing)
 * player is tracked so stopping cancels it cleanly. Bundled asset files are
 * opened through [AssetFileDescriptor] (from [Context.assets]), so they play
 * identically after the app is built and installed.
 */
object LocalSoundPlayer {

    private var player: MediaPlayer? = null

    /** A player that is still preparing — stopped/cancelled by [stop]. */
    private var pending: MediaPlayer? = null

    /** Open descriptor of the currently playing/pending asset (closed on release). */
    private var openAfd: AssetFileDescriptor? = null

    /**
     * Plays a bundled asset (e.g. "all_sounds/Study limit/sound.mp3"); stops
     * any currently playing or pending preview first. [onComplete] (optional)
     * runs on the main thread when playback finishes, fails, or is superseded.
     */
    fun playAsset(context: Context, assetPath: String, onComplete: (() -> Unit)? = null) {
        stop()
        val mediaPlayer = MediaPlayer()
        pending = mediaPlayer
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            val afd = context.assets.openFd(assetPath)
            openAfd = afd
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            attachListeners(mediaPlayer, onComplete)
            mediaPlayer.prepareAsync()
        } catch (ignored: Exception) {
            if (pending === mediaPlayer) pending = null
            release(mediaPlayer)
            onComplete?.invoke()
        }
    }

    /**
     * Plays [uri]; stops any currently playing or pending preview first.
     * [onComplete] (optional) runs on the main thread when playback
     * finishes, fails, or is superseded by another play call.
     */
    fun play(context: Context, uri: Uri, onComplete: (() -> Unit)? = null) {
        stop()
        val mediaPlayer = MediaPlayer()
        pending = mediaPlayer
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mediaPlayer.setDataSource(context, uri)
            attachListeners(mediaPlayer, onComplete)
            mediaPlayer.prepareAsync()
        } catch (ignored: Exception) {
            if (pending === mediaPlayer) pending = null
            release(mediaPlayer)
            onComplete?.invoke()
        }
    }

    private fun attachListeners(mediaPlayer: MediaPlayer, onComplete: (() -> Unit)?) {
        mediaPlayer.setOnPreparedListener {
            if (pending === mediaPlayer) {
                pending = null
                player = mediaPlayer
                runCatching { mediaPlayer.start() }
            } else {
                // Stopped while preparing — never start it.
                release(mediaPlayer)
            }
        }
        mediaPlayer.setOnCompletionListener {
            release(mediaPlayer)
            onComplete?.invoke()
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            if (pending === mediaPlayer) pending = null
            release(mediaPlayer)
            onComplete?.invoke()
            true
        }
    }

    /** Stops the current preview immediately (if any) and cancels pending ones. */
    fun stop() {
        player?.let { mediaPlayer ->
            runCatching { mediaPlayer.stop() }
            release(mediaPlayer)
        }
        pending?.let { release(it) }
        pending = null
    }

    private fun release(mediaPlayer: MediaPlayer) {
        if (player === mediaPlayer) player = null
        if (pending === mediaPlayer) pending = null
        runCatching { mediaPlayer.release() }
        runCatching { openAfd?.close() }
        openAfd = null
    }
}
