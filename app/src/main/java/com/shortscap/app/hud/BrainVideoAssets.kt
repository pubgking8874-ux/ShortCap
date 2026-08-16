package com.shortscap.app.hud

/**
 * Maps each [BrainState] to the FINAL brain video asset provided by the user.
 *
 * The four videos were copied UNCHANGED (exact filenames preserved, including
 * the double `.mp4.mp4` extension) from the user's supplied `shorts_brain`
 * folder into the Android project's existing asset convention
 * (`app/src/main/assets/<category>/...`, same as `all_sounds/`), i.e.:
 *
 *   app/src/main/assets/shorts_brain/brain_1_healthy.mp4.mp4
 *   app/src/main/assets/shorts_brain/brain_2_tired.mp4.mp4
 *   app/src/main/assets/shorts_brain/brain_3_near_limit.mp4.mp4
 *   app/src/main/assets/shorts_brain/brain_4_limit_reached.mp4.mp4
 *
 * The filenames were CONFIRMED by inspecting the supplied folder — they are
 * not assumed. Do not regenerate, modify or rename the videos.
 */
object BrainVideoAssets {

    /** The assets subfolder holding the final brain videos. */
    const val ASSET_DIR = "shorts_brain"

    /** Resolves the local asset path for [state] — pure, unit-testable. */
    fun pathFor(state: BrainState): String = when (state) {
        BrainState.HEALTHY -> "$ASSET_DIR/brain_1_healthy.mp4.mp4"
        BrainState.TIRED -> "$ASSET_DIR/brain_2_tired.mp4.mp4"
        BrainState.NEAR_LIMIT -> "$ASSET_DIR/brain_3_near_limit.mp4.mp4"
        BrainState.LIMIT_REACHED -> "$ASSET_DIR/brain_4_limit_reached.mp4.mp4"
    }
}
