package com.shortscap.app.hud

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView

/**
 * BrainVideoView — lightweight local MP4 playback for the Brain HUD mode.
 *
 * A [TextureView] + platform [MediaPlayer] (no new libraries — the project
 * already relies on the Android media stack for local assets, e.g. sounds),
 * playing the user's FINAL brain videos from the app's assets:
 *
 *   HEALTHY → brain_1_healthy · TIRED → brain_2_tired
 *   NEAR_LIMIT → brain_3_near_limit · LIMIT_REACHED → brain_4_limit_reached
 *
 * Behavior contract (runtime integration spec):
 *  - muted ([MediaPlayer.setVolume](0, 0)) — never audible;
 *  - plays locally from assets — no network requests ever;
 *  - loops while the state stays the same;
 *  - only the asset for the CURRENT [BrainState] is loaded (at most ONE
 *    [MediaPlayer] alive at a time — the others are never loaded);
 *  - [showState] is a no-op when the state (and thus the video) has not
 *    changed, so normal Compose recomposition never restarts the video;
 *  - [release] stops and frees the player + surface (called when the HUD
 *    hides, the mode changes, or the overlay is disposed) so no playback
 *    continues while the HUD is not visible;
 *  - aspect ratio is preserved (center-crop fit) via a surface transform.
 */
class BrainVideoView(context: Context) : TextureView(context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentAsset: String? = null
    private var surface: Surface? = null

    init {
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                surface = Surface(surfaceTexture)
                attachPlayer()
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                releasePlayer()
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
        }
    }

    /**
     * Ensures the video for [state] is playing. No-op when that state's video
     * is already attached (prepared or still preparing) — safe to call from
     * every Compose recomposition.
     */
    fun showState(state: BrainState) {
        if (state == currentState) return
        currentState = state
        attachPlayer()
    }

    private var currentState: BrainState? = null

    /** Starts playback when both a surface and a state are available. */
    private fun attachPlayer() {
        val targetSurface = surface ?: return
        val state = currentState ?: return
        val asset = BrainVideoAssets.pathFor(state)

        // Already attached for this state — never restart because of a
        // recomposition, and never replay the same video unnecessarily.
        if (mediaPlayer != null && currentAsset == asset) return

        releasePlayer()
        currentAsset = asset

        val player = MediaPlayer()
        mediaPlayer = player
        try {
            context.assets.openFd(asset).use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            // Muted always — the brain videos are a visual state indicator.
            player.setVolume(0f, 0f)
            player.isLooping = true
            player.setOnPreparedListener { it.start() }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer()
                true
            }
            player.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                applyCenterCropTransform(videoWidth, videoHeight)
            }
            player.setSurface(targetSurface)
            player.prepareAsync()
        } catch (t: Throwable) {
            // Never crash the HUD because of an asset problem — fail quietly.
            releasePlayer()
        }
    }

    /**
     * Scales the video to center-crop inside this view while preserving its
     * aspect ratio (a [TextureView] has no scaleType of its own).
     */
    private fun applyCenterCropTransform(videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0) return
        val viewWidth = width.coerceAtLeast(1)
        val viewHeight = height.coerceAtLeast(1)
        val scale = maxOf(
            viewWidth.toFloat() / videoWidth,
            viewHeight.toFloat() / videoHeight,
        )
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (viewWidth - videoWidth * scale) / 2f,
                (viewHeight - videoHeight * scale) / 2f,
            )
        }
        setTransform(matrix)
    }

    /** Stops and frees playback. Safe to call repeatedly. */
    fun release() {
        releasePlayer()
        surface?.release()
        surface = null
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        currentAsset = null
    }
}
