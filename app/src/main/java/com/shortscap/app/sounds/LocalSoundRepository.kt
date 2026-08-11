package com.shortscap.app.sounds

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LocalSoundRepository — reads the audio files bundled in the app under
 * "assets/all_sounds/<category>/" (a mirror of the user's
 * "Downloads/All sounds" folders) and remembers the per-category selection.
 *
 * Scanning is fully dynamic: no audio filenames are hardcoded. Every time a
 * category is opened the bundled folder is re-scanned via [AssetManager], so
 * files present in the source folders are always listed correctly. Only
 * supported audio extensions (mp3, wav, m4a, aac, ogg) are returned;
 * everything else (including mp4) is ignored.
 *
 * One deliberate exception: [CATEGORY_FILE_EXCLUSIONS] hides a specific file
 * from ONE category's list ("Gentle Chime" from Break Reminder) without
 * deleting the bundled asset or affecting any other category that uses it.
 *
 * Bundling keeps the sounds working after the app is built and installed —
 * there is NO runtime dependency on a Downloads path or a storage permission.
 */
object LocalSoundRepository {

    private const val PREFS_NAME = "shortscap_local_sounds"

    /** Supported audio extensions (lowercase, without the dot). */
    private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg")

    /**
     * Lists the category folder inside the bundled assets and returns every
     * supported audio file inside it, sorted by name. Runs on the IO
     * dispatcher — never call from the main thread.
     */
    suspend fun loadSounds(context: Context, category: SoundEffectCategory): List<LocalSound> =
        withContext(Dispatchers.IO) { availableSounds(context, category) }

    /**
     * Files deliberately hidden from ONE category's sound list while the asset
     * itself stays bundled (and keeps playing wherever else it is used).
     *
     * Currently "Gentle Chime.mp3" is excluded ONLY from Break Reminder (the
     * "Study break Reminder" folder) — it remains fully available in Study
     * Session End and anywhere else it is bundled.
     */
    private val CATEGORY_FILE_EXCLUSIONS: Map<SoundEffectCategory, Set<String>> = mapOf(
        SoundEffectCategory.BREAK_REMINDER to setOf("gentle chime.mp3"),
    )

    /**
     * Synchronous scan of the category folder (the same rules as [loadSounds];
     * a cheap AssetManager listing + extension filter, so callers that need an
     * instant answer — e.g. the event sound dispatcher — use this directly).
     * Applies [CATEGORY_FILE_EXCLUSIONS] so an excluded file never appears in
     * that category's list nor becomes its default sound.
     */
    fun availableSounds(context: Context, category: SoundEffectCategory): List<LocalSound> {
        val dir = SoundFolderMap.assetDir(category)
        val excluded = CATEGORY_FILE_EXCLUSIONS[category].orEmpty()
        val files = runCatching {
            context.assets.list(dir).orEmpty()
                .filter { isSupported(it) && it.lowercase() !in excluded }
                .sortedBy { it.lowercase() }
        }.getOrDefault(emptyList())

        return files.map { fileName ->
            val assetPath = "$dir/$fileName"
            LocalSound(
                id = assetPath,
                fileName = fileName,
                assetPath = assetPath,
            )
        }
    }

    /**
     * The sound that SHOULD play for [category]: the user's explicit
     * selection if it still exists in the bundle, otherwise the folder's
     * default (its first file), otherwise null when no playable audio exists.
     * This mirrors the "current sound" resolution on SoundConfigScreen, so the
     * event path and the visible UI always agree on the same single file.
     */
    fun selectedOrDefault(context: Context, category: SoundEffectCategory): LocalSound? {
        val available = availableSounds(context, category)
        val selectedId = selectedSound(context, category)
        return available.firstOrNull { it.id == selectedId } ?: available.firstOrNull()
    }

    private fun isSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

    // ---- Persisting the user's selection ----

    /** The last sound the user selected for a category (null = never chosen). */
    fun selectedSound(context: Context, category: SoundEffectCategory): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(category.name, null)

    /** Persists the user's chosen sound id for a category. */
    fun saveSelectedSound(context: Context, category: SoundEffectCategory, soundId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(category.name, soundId)
            .apply()
    }
}