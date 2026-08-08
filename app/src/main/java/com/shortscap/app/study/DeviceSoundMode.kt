package com.shortscap.app.study

import android.content.Context
import android.media.AudioManager
import com.shortscap.app.permissions.PermissionRepository

/**
 * Sound Mode → REAL Android device ringer mode.
 *
 * ShortsCap's Sound Mode options (Sound / Vibrate / Silent) change the actual
 * Android ringer mode via [AudioManager.setRingerMode] — they never mute app
 * sounds, never touch volume streams and never simulate anything.
 *
 * System requirement: changing the ringer mode needs the
 * `android.permission.ACCESS_NOTIFICATION_POLICY` manifest permission AND
 * Notification Policy Access granted by the user in system settings
 * (Settings → Notifications → Do Not Disturb / ShortsCap). Without that
 * access Android silently rejects the change, so [Context.applySoundMode]
 * refuses up front and returns [DeviceSoundModeResult.POLICY_ACCESS_REQUIRED].
 *
 * Android state is the source of truth: every ringer change is read back and
 * verified via [AudioManager.getRingerMode] before success is reported, and
 * [Context.currentSoundMode] re-reads the live state whenever the app resumes.
 */
enum class DeviceSoundModeResult {
    /** The Android ringer mode was changed AND verified via getRingerMode. */
    APPLIED,

    /** Notification Policy Access is missing — the user must grant it in Settings. */
    POLICY_ACCESS_REQUIRED,

    /** Android rejected the change (or the verified state did not actually change). */
    FAILED,
}

/** ShortsCap Sound Mode → Android ringer mode. */
// RINGER_MODE_SILENT is deprecated since API 28 but remains the official way
// to set the ringer to silent on every supported Android version (DND is a
// separate user-controlled feature and must not be used to fake silent mode).
// Caveat: on stock Android 10+ (API 29+) some OEMs remap SILENT onto DND or
// ignore it entirely — the read-back verification in [Context.applySoundMode]
// then reports FAILED honestly instead of pretending the change succeeded.
@Suppress("DEPRECATION")
fun StudySoundMode.toRingerMode(): Int = when (this) {
    StudySoundMode.SOUND -> AudioManager.RINGER_MODE_NORMAL
    StudySoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
    StudySoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
}

/** Android ringer mode → ShortsCap Sound Mode (SOUND for any unexpected value). */
fun Int.toStudySoundMode(): StudySoundMode = when (this) {
    AudioManager.RINGER_MODE_VIBRATE -> StudySoundMode.VIBRATE
    AudioManager.RINGER_MODE_SILENT -> StudySoundMode.SILENT
    else -> StudySoundMode.SOUND
}

/**
 * True once the user has granted Notification Policy Access — the system
 * authorization required for [Context.applySoundMode] to work. Delegates to
 * the centralized permissions layer ([PermissionRepository]), so Study Mode
 * and the Permissions screen (System Audio Access) always read the same OS
 * state.
 */
fun Context.hasSoundModeAccess(): Boolean = PermissionRepository.isNotificationPolicyAccessGranted(this)

/**
 * Changes the REAL Android ringer mode and verifies it by reading the mode
 * back. Returns [DeviceSoundModeResult.POLICY_ACCESS_REQUIRED] (without
 * touching anything) when policy access is missing, and
 * [DeviceSoundModeResult.FAILED] when Android does not confirm the new state —
 * success is never reported before the system confirms it.
 */
fun Context.applySoundMode(mode: StudySoundMode): DeviceSoundModeResult {
    if (!hasSoundModeAccess()) return DeviceSoundModeResult.POLICY_ACCESS_REQUIRED
    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return DeviceSoundModeResult.FAILED
    val target = mode.toRingerMode()
    val setOk = runCatching { am.ringerMode = target }.isSuccess
    return if (setOk && am.ringerMode == target) DeviceSoundModeResult.APPLIED else DeviceSoundModeResult.FAILED
}

/** The actual Android ringer mode right now (read-only, no special access needed). */
fun Context.currentSoundMode(): StudySoundMode {
    val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return StudySoundMode.SOUND
    return am.ringerMode.toStudySoundMode()
}
