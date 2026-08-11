package com.shortscap.app.sounds

import android.content.Context
import android.util.Log
import java.util.Collections

/**
 * SoundTriggerer — the central "APPLICATION EVENT → SOUND" dispatcher for the
 * Sound & Effects module.
 *
 * Every real product event that wants a sound calls
 * [play] with the [SoundEffectCategory] it maps to; the dispatcher resolves
 * the user's currently selected audio for that category (the exact single file
 * shown as "Current" on SoundConfigScreen — an explicit selection, else the
 * folder's default, else nothing) and plays it through [LocalSoundPlayer].
 *
 * Guarantees:
 *  - [SoundEffectsConfig.appSoundsEnabled] OFF silences every event (master
 *    switch read from the persisted config, the same single source the
 *    ViewModel writes).
 *  - One playback in flight per category at a time — a repeated event call
 *    never stacks/duplicates sound.
 *  - Never crashes and never blocks the underlying feature: any failure
 *    (missing audio, missing asset, player error) is logged and the event
 *    continues untouched.
 *
 * Preview is deliberately kept outside this path: SoundConfigScreen plays
 * directly via [LocalSoundPlayer] and can never trigger an application event.
 */
object SoundTriggerer {

    private const val TAG = "SoundTriggerer"

    /** Categories with a play currently in flight (per-event de-duplication). */
    private val inFlight: MutableSet<SoundEffectCategory> =
        Collections.synchronizedSet(mutableSetOf())

    /**
     * Plays the user's selected sound for [category] (see class docs for the
     * resolution + safety rules). Safe to call from any thread; all work is
     * cheap synchronous prefs/asset reads plus the player's async prepare.
     */
    fun play(context: Context, category: SoundEffectCategory) {
        if (!SoundEffectsRepository.loadSettings(context).appSoundsEnabled) return
        if (!inFlight.add(category)) return

        val sound = runCatching { LocalSoundRepository.selectedOrDefault(context, category) }.getOrNull()
        val assetPath = sound?.assetPath
        when {
            assetPath == null -> {
                inFlight.remove(category)
                Log.w(TAG, "No playable audio for $category — skipped (the event continues normally)")
            }
            else -> try {
                // onComplete always fires (finished / failed / superseded), so
                // the once-per-category guard always releases.
                LocalSoundPlayer.playAsset(context, assetPath) {
                    inFlight.remove(category)
                }
            } catch (t: Throwable) {
                inFlight.remove(category)
                Log.w(TAG, "Failed to play '$category': ${t.message}", t)
            }
        }
    }
}