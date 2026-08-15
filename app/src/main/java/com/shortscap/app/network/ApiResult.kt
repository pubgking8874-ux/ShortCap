package com.shortscap.app.network

/**
 * The outcome of one backend API call. Every [BackendApi] method returns
 * [ApiResult] — never throws for HTTP/network failures, so callers (syncers,
 * read clients, UI) always handle an explicit state and never show a fake
 * success.
 */
sealed interface ApiResult<out T> {

    /** HTTP 2xx with a parsed body (or null when the endpoint returns none). */
    data class Success<T>(val data: T) : ApiResult<T>

    /** HTTP error (4xx/5xx). [status] is the code; [body] the raw detail
     * text (already stripped of anything sensitive by the client). */
    data class HttpError(val status: Int, val body: String? = null) : ApiResult<Nothing>

    /** The request never completed: timeout, DNS, connection refused, IO. */
    data class NetworkError(val message: String) : ApiResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    /** True for transient failures worth retrying (5xx / network) — NOT 4xx
     * (those are permanent: validation, not found, forbidden). Phase 16 §13. */
    val isTransient: Boolean
        get() = when (this) {
            is Success -> false
            is HttpError -> status >= 500
            is NetworkError -> true
        }
}
