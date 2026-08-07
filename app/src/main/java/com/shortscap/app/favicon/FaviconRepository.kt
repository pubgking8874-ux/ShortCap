package com.shortscap.app.favicon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * FaviconRepository — the app-wide website favicon/logo system.
 *
 * Independent from website blocking/analytics logic so any screen can request
 * a website's official logo by domain. Resolution order:
 *
 *  1. In-memory cache (per process)
 *  2. Disk cache (app cache dir — favicons are tiny; image bytes are NEVER
 *     stored in the data model, only the URL reference + cache key)
 *  3. Network — the domain's official `favicon.ico` first, then a public
 *     favicon lookup service when the site does not serve one at that path
 *
 * On any failure the caller shows a professional fallback icon — the UI never
 * breaks. [refresh] and [clearCache] provide cache refresh/update and reset.
 *
 * Future backend: a synced rule would carry `faviconUrl` / `localIconPath`
 * (cache key) as references; the actual pixels always come from this cache,
 * so no image data ever crosses the backend/database.
 */
object FaviconRepository {

    private const val MEMORY_CACHE_ENTRIES = 64
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_DOWNLOAD_BYTES = 512 * 1024 // favicons are tiny; cap hostile responses
    private const val FAILURE_TTL_MS = 10 * 60 * 1000L // retry a failed domain after 10 minutes
    private const val CACHE_DIR = "favicons"
    private const val USER_AGENT = "ShortsCap/1.1 (website favicon loader)"

    private val memoryCache = LruCache<String, Bitmap>(MEMORY_CACHE_ENTRIES)
    // Domain -> last-failure time. Failures expire (self-healing) so one
    // transient network blip does not pin the fallback for the whole session.
    private val failedDomains = ConcurrentHashMap<String, Long>()

    /** Primary official favicon URL — the site's own `/favicon.ico`. */
    fun faviconUrl(domain: String): String = "https://${mainDomain(domain)}/favicon.ico"

    /** Fallback lookup service used when the official URL is unavailable. */
    fun fallbackFaviconUrl(domain: String): String =
        "https://www.google.com/s2/favicons?domain=${mainDomain(domain)}&sz=128"

    /** Stable cache key (also stored on [com.shortscap.app.web.WebRule.localIconPath]). */
    fun cacheKey(domain: String): String = domain.trim().lowercase()

    /**
     * Extracts the main domain for favicon lookups ("m.youtube.com" ->
     * "youtube.com") so the logo comes from the site's root, which is where
     * the official favicon lives. [cacheKey] keeps the full domain.
     */
    private fun mainDomain(domain: String): String {
        val parts = cacheKey(domain).split(".")
        return if (parts.size > 2) parts.takeLast(2).joinToString(".") else cacheKey(domain)
    }

    /**
     * Loads the favicon for [domain], scaled to at most [targetPx] (never
     * upscaled). Returns null when it cannot be obtained — callers render
     * their fallback. Safe to call from any thread; network/disk IO runs on
     * [Dispatchers.IO].
     */
    suspend fun load(context: Context, domain: String, targetPx: Int): Bitmap? {
        val key = cacheKey(domain)
        val failedAt = failedDomains[key]
        if (failedAt != null) {
            if (System.currentTimeMillis() - failedAt < FAILURE_TTL_MS) return null
            failedDomains.remove(key) // TTL expired — retry once
        }
        return withContext(Dispatchers.IO) {
            memoryCache.get(key)?.let { return@withContext it }

            val disk = diskFile(context, key)
            if (disk.exists()) {
                decodeScaled(disk, targetPx)?.let {
                    memoryCache.put(key, it)
                    return@withContext it
                }
            }

            fetch(domain, targetPx)?.also { bitmap ->
                memoryCache.put(key, bitmap)
                saveToDisk(disk, bitmap)
            } ?: run {
                // Remember the miss so we do not re-hit an unreachable site on
                // every recomposition; expires after FAILURE_TTL_MS. refresh()
                // clears it immediately.
                failedDomains[key] = System.currentTimeMillis()
                null
            }
        }
    }

    /**
     * Cache refresh/update: drops the memory + disk copies (and any failure
     * marker) for [domain], then reloads from the network if reachable.
     */
    suspend fun refresh(context: Context, domain: String, targetPx: Int): Bitmap? {
        val key = cacheKey(domain)
        memoryCache.remove(key)
        diskFile(context, key).delete()
        failedDomains.remove(key)
        return load(context, domain, targetPx)
    }

    /** Clears every cached favicon (memory + disk) and all failure markers. */
    fun clearCache(context: Context) {
        memoryCache.evictAll()
        failedDomains.clear()
        File(context.cacheDir, CACHE_DIR).deleteRecursively()
    }

    /**
     * Downloads up to [MAX_DOWNLOAD_BYTES] — oversized or malformed responses
     * are rejected so a hostile/broken domain cannot exhaust memory.
     */
    private fun download(urlString: String): ByteArray? {
        val connection = runCatching {
            (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
        }.getOrNull() ?: return null
        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val limited = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                connection.inputStream.use { input ->
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_DOWNLOAD_BYTES) {
                            limited.close()
                            return null
                        }
                        limited.write(buffer, 0, read)
                    }
                }
                limited.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun diskFile(context: Context, key: String): File =
        File(File(context.cacheDir, CACHE_DIR), "$key.png")

    private fun decodeScaled(file: File, targetPx: Int): Bitmap? =
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()
            ?.let { scaleToFit(it, targetPx) }

    private fun fetch(domain: String, targetPx: Int): Bitmap? {
        val candidates = listOf(faviconUrl(domain), fallbackFaviconUrl(domain))
        for (url in candidates) {
            val bytes = download(url) ?: continue
            val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .getOrNull() ?: continue
            return scaleToFit(bitmap, targetPx)
        }
        return null
    }

    private fun scaleToFit(bitmap: Bitmap, targetPx: Int): Bitmap {
        if (targetPx <= 0) return bitmap
        val largest = bitmap.width.coerceAtLeast(bitmap.height)
        val scale = targetPx.toFloat() / largest
        if (scale >= 1f) return bitmap // never upscale
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun saveToDisk(file: File, bitmap: Bitmap) {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
