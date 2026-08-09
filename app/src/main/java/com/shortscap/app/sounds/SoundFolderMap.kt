package com.shortscap.app.sounds

/**
 * Maps every Sound & Effects category to its folder inside the app's bundled
 * "all_sounds" asset tree (mirroring the "Downloads/All sounds" source
 * folders exactly — same names, same capitalization).
 *
 * Only the folder per category is mapped — individual audio filenames are
 * NEVER hardcoded; scanning is fully dynamic (AssetManager), so sounds can be
 * added or removed in the source folders and re-bundled without touching code.
 */
object SoundFolderMap {

    /** Root folder of the bundled sound assets (under app/assets). */
    const val ASSET_ROOT = "all_sounds"

    /** Exact folder name for a category — matching the real "All sounds"
     *  folder structure, verified against the actual source folders. */
    fun folderName(category: SoundEffectCategory): String = when (category) {
        SoundEffectCategory.STUDY_SESSION_START -> "Study Session Start"
        SoundEffectCategory.STUDY_SESSION_END -> "Study Session End"
        SoundEffectCategory.BREAK_REMINDER -> "Study break Reminder"
        SoundEffectCategory.BREAK_START -> "Break Session Start"
        SoundEffectCategory.BREAK_END -> "Break Session End"
        SoundEffectCategory.SCHEDULE_REMINDER -> "Study Schedule"
        SoundEffectCategory.SHORTS_LIMIT_WARNING -> "Study limit"
        SoundEffectCategory.SHORTS_LIMIT_REACHED -> "Reach limit"
        SoundEffectCategory.NOTIFICATION_SOUND -> "Notification"
    }

    /** Full asset directory path for a category, e.g. "all_sounds/Study limit". */
    fun assetDir(category: SoundEffectCategory): String =
        "$ASSET_ROOT/${folderName(category)}"
}
