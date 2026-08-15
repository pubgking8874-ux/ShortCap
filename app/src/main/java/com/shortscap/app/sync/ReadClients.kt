package com.shortscap.app.sync

import com.shortscap.app.network.ApiResult
import com.shortscap.app.network.BackendApi
import com.shortscap.app.network.RankDto
import com.shortscap.app.network.ReportDto
import com.shortscap.app.network.ScoreDto

/**
 * Read-only retrieval clients for the server-authoritative data:
 * Reports (Phase 13), Your Score (Phase 14B) and Rank (Phase 15B).
 *
 * Android NEVER recomputes these — the backend is the authority
 * (Phase 16 §16/§17/§18). Each client caches the last successful response
 * in memory so the UI can render instantly while a refresh is in flight,
 * and every method returns [ApiResult] so Loading / Success / Empty /
 * Error states are handled explicitly (Phase 16 §19).
 *
 * Cache policy: cached data is clearly local and marked as such in the
 * returned envelope — it is never presented as a fresh server response.
 */
class ReadClients(private val api: BackendApi) {

    /** Cache of the last successful server response per (kind, period, date). */
    private val cache = mutableMapOf<String, Any>()

    // ---- Reports ---------------------------------------------------------

    suspend fun report(period: String, date: String? = null): ApiResult<ReportDto> {
        val key = "report:$period:$date"
        return when (val result = api.getReport(period, date)) {
            is ApiResult.Success -> {
                cache[key] = result.data
                result
            }
            else -> result
        }
    }

    // ---- Your Score ------------------------------------------------------

    suspend fun score(period: String, date: String? = null): ApiResult<ScoreDto> {
        val key = "score:$period:$date"
        return when (val result = api.getScore(period, date)) {
            is ApiResult.Success -> {
                cache[key] = result.data
                result
            }
            else -> result
        }
    }

    // ---- Rank / Leaderboard ----------------------------------------------

    suspend fun rank(period: String, date: String? = null, page: Int = 1, pageSize: Int = 20): ApiResult<RankDto> {
        val key = "rank:$period:$date:$page:$pageSize"
        return when (val result = api.getRank(period, date, page, pageSize)) {
            is ApiResult.Success -> {
                cache[key] = result.data
                result
            }
            else -> result
        }
    }

    /** Last successful cached value for the key, if any (never stale-by-design
     * when a fresh fetch just succeeded — the fetch updates it first). */
    @Suppress("UNCHECKED_CAST")
    fun cached(key: String): Any? = cache[key]
}
