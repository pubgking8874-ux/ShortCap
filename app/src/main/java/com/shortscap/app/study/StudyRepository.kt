package com.shortscap.app.study

/**
 * StudyRepository — backend seam for all Study Mode data, mirroring the
 * SettingsRepository pattern. Today the ViewModel holds local state and the
 * UI never calls these directly; each function documents the future API it
 * will call. Swapping the data source (Python/Firebase/AWS backend, or a
 * local database) requires **no UI changes**.
 *
 * Study Mode data is deliberately isolated here — it never mixes with
 * Monitoring, Shorts, Activity or History data (see StudyModels.kt).
 */
object StudyRepository {

    /** GET Study Settings — load the full [StudyModeSettings] model. */
    suspend fun getStudySettings(): StudyModeSettings = StudyModeSettings()

    /** UPDATE Study Settings — persist the whole model (cloud sync). */
    suspend fun updateStudySettings(settings: StudyModeSettings) {
        // TODO: POST /study/settings — duration, break, sound, schedule, allowed lists.
    }

    /** POST Study Session — record one completed session (timestamp-based). */
    suspend fun createStudySession(session: StudySession) {
        // TODO: POST /study/sessions — startTime/endTime/currentTime/remainingDuration.
    }

    /** GET Study Summary — aggregated session statistics. */
    suspend fun getStudySummary(): StudySummary = StudySummary()

    // Future cloud integration: Firebase Firestore, AWS AppSync / API Gateway,
    // and a local database (Room) cache — all behind these same functions.
}
