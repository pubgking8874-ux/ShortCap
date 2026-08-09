package com.shortscap.app.sounds

import android.net.Uri

/**
 * One audio file inside a category folder of the bundled "all_sounds" assets
 * (which mirror the user's "Downloads/All sounds" structure exactly).
 *
 * [id] is the stable reference persisted when the user selects a sound — the
 * asset path on modern builds. [fileName] is the on-disk name and [assetPath]
 * is what playback opens via AssetManager (preferred); [uri] is kept for the
 * future "Add from your device" flow where the file lives outside the bundle.
 *
 * The scanner never hardcodes filenames — [id] / [assetPath] are always
 * derived from what is actually bundled, so files added to a source folder
 * and re-bundled are picked up automatically.
 */
data class LocalSound(
    val id: String,
    val fileName: String,
    val uri: Uri? = null,
    val assetPath: String? = null,
) {
    /** Display name without its extension, e.g. "gentle_chime" for gentle_chime.mp3. */
    val displayName: String
        get() = fileName.substringBeforeLast('.')
}
