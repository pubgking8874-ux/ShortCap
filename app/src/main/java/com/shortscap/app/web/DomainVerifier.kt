package com.shortscap.app.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

/**
 * Layer 2 + Layer 3 — local network verification of a (already format-valid)
 * hostname, using the device's own internet connection. No backend, no API.
 *
 * Layer 2 (DNS, the primary existence check): resolves the hostname with a
 * hard timeout. A definitive NXDOMAIN means the domain does not exist; a
 * timeout or transient failure is NOT treated as proof of non-existence.
 * Note: withTimeout cancels the coroutine, but the underlying blocking
 * resolver call itself cannot be interrupted — the IO thread stays occupied
 * until the OS DNS returns. Accepted trade-off for a 4s worst-case bound.
 *
 * Layer 3 (optional lightweight reachability): after DNS succeeds, sends a
 * single HEAD request (HTTPS first, HTTP fallback) with a short timeout. Any
 * HTTP response — even 4xx/5xx — proves the host answers. Connection-level
 * failures (timeout, refused, SSL) are mapped to a temporary "could not
 * verify right now" outcome, never to "domain not found".
 *
 * All network work runs on [Dispatchers.IO]; the UI thread is never blocked.
 */
object DomainVerifier {

    private const val DNS_TIMEOUT_MS = 4_000L
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_000

    /** Outcome of a verification attempt. */
    sealed interface Result {
        /** DNS resolved AND the host answered a lightweight request. */
        data object Verified : Result

        /** DNS definitively failed — the domain does not resolve. */
        data object NotFound : Result

        /** Verification could not be completed right now (offline, DNS timeout, reachability failure). */
        data object TemporaryFailure : Result
    }

    /**
     * Verifies [hostname] end-to-end. Safe to call from any coroutine; the
     * blocking DNS/socket work is confined to [Dispatchers.IO].
     */
    suspend fun verify(context: Context, hostname: String): Result = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(context)) return@withContext Result.TemporaryFailure

        // ---- Layer 2: DNS resolution (primary existence check) ----
        val dnsOutcome: Boolean? = try {
            withTimeout(DNS_TIMEOUT_MS) { InetAddress.getAllByName(hostname) }
            true
        } catch (e: TimeoutCancellationException) {
            null // DNS timed out — transient, not proof of non-existence
        } catch (e: UnknownHostException) {
            false // domain does not resolve
        } catch (e: SecurityException) {
            false
        } catch (e: CancellationException) {
            throw e // preserve real cancellation
        } catch (e: Exception) {
            null // transient DNS failure
        }

        when (dnsOutcome) {
            false -> Result.NotFound
            null -> Result.TemporaryFailure
            true -> {
                // ---- Layer 3: lightweight reachability (best effort) ----
                if (checkReachable(hostname)) Result.Verified else Result.TemporaryFailure
            }
        }
    }

    /** HEAD request to https:// then http:// — any HTTP response means reachable. */
    private fun checkReachable(hostname: String): Boolean {
        val candidates = listOf("https://$hostname", "http://$hostname")
        for (url in candidates) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as? HttpURLConnection ?: continue
                conn.requestMethod = "HEAD"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.instanceFollowRedirects = true
                // Any status code (2xx/3xx/4xx/5xx) proves the host answers;
                // a temporary connection/SSL failure falls through to the next
                // candidate (HTTP fallback) and then to TemporaryFailure.
                conn.responseCode
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // try the next candidate
            } finally {
                conn?.disconnect()
            }
        }
        return false
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
