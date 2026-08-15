package com.shortscap.app.study

import com.shortscap.app.network.StudySessionStartDto
import com.shortscap.app.sync.StudySyncer
import com.shortscap.app.sync.SyncCoordinator

/**
 * StudyRepository — backend seam for all Study Mode data, mirroring the
 * SettingsRepository pattern. Since Phase 16, the seam is wired to the real
 * backend through [SyncCoordinator]:
 *
 *  - [createStudySession] enqueues the session START (durable history);
 *  - [endStudySession] enqueues the session END with the backend-assigned
 *    session id (resolved from the start response);
 *  - Android STILL runs the real-time timer — the backend only persists the
 *    timestamp-based history (Phase 16 §8). The backend never times a
 *    session.
 *
 * Swapping the data source requires **no UI changes**.
 */
object StudyRepository {

    /** GET Study Settings — load the full [StudyModeSettings] model. */
    suspend fun getStudySettings(): StudyModeSettings = StudyModeSettings()

    /** UPDATE Study Settings — persist the whole model (cloud sync). */
    suspend fun updateStudySettings(settings: StudyModeSettings) {
        // TODO: POST /study/settings — duration, break, sound, schedule, allowed lists.
    }

    /**
     * POST Study Session — record a session START (timestamp-based). The
     * Android timer stays local; only the durable history is pushed. The
     * backend assigns the session id — pass it to [endStudySession].
     */
    suspend fun createStudySession(session: StudySession) {
        SyncCoordinator.enqueue(
            StudySyncer.sessionStart(
                StudySessionStartDto(
                    plannedDurationSeconds = session.durationMinutes * 60,
                )
            )
        )
    }

    /**
     * POST Study Session end — complete a session on the server using the
     * backend-assigned [sessionId]. `cancelled = true` explicitly cancels
     * (status cancelled + STUDY_CANCELLED event).
     */
    suspend fun endStudySession(sessionId: Int, cancelled: Boolean = false) {
        SyncCoordinator.enqueue(StudySyncer.sessionEnd(sessionId, cancelled))
    }

    /** GET Study Summary — aggregated session statistics. */
    suspend fun getStudySummary(): StudySummary = StudySummary()

    // Future cloud integration: Firebase Firestore, AWS AppSync / API Gateway,
    // and a local database (Room) cache — all behind these same functions.
}
