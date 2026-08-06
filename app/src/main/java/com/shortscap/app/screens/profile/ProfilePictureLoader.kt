package com.shortscap.app.screens.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Profile picture image pipeline.
 *
 * Today: decode (downscaled) -> read EXIF Orientation -> rotate/flip so the
 * picture matches the Gallery exactly. Future steps slot in here without any
 * UI changes:
 *
 *   Gallery / Camera -> Read EXIF -> Correct Orientation -> Crop (future)
 *   -> Compress (future) -> Upload to backend (ProfileRepository.uploadProfilePicture)
 */
object ProfilePictureLoader {

    /** Longest edge (px) the decoded bitmap is downscaled to — plenty for a 112dp avatar. */
    private const val MAX_EDGE = 512

    /**
     * Decodes [uriString] downscaled, applies its EXIF Orientation (all common
     * rotations 0/90/180/270 plus mirrored variants) and returns the corrected
     * bitmap — or null when the source can't be read (placeholder is shown).
     *
     * Works for content:// URIs from the Gallery, Google Photos and the Files
     * app (and future Camera capture). The image is never stretched, squashed
     * or zoomed — only rotated/flipped as its own metadata requires, keeping
     * the original aspect ratio. The UI renders it with a circle crop.
     *
     * Note: BitmapFactory cannot decode HEIC below API 28, so a HEIC pick on
     * API 26-27 falls back to the placeholder — the future crop/upload
     * pipeline should add codec handling if that matters.
     */
    fun load(context: Context, uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        return runCatching {
            // 1. Bounds-only decode -> power-of-two sample size (no OOM on huge photos).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            // 2. Read the EXIF Orientation (Android-recommended ExifInterface,
            //    read straight from the content stream).
            val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }

            // 3. Apply the combined EXIF transform. getRotationDegrees() returns
            //    the fully-combined rotation for ALL 8 orientation values
            //    (normal 0, 90, 180, 270 and mirrored variants) and isFlipped()
            //    reports the mirror — so no manual orientation mapping is needed.
            val matrix = Matrix().apply {
                exif?.getRotationDegrees()?.let { postRotate(it.toFloat()) }
                if (exif?.isFlipped() == true) postScale(-1f, 1f)
            }
            val corrected = if (matrix.isIdentity) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
            if (corrected !== decoded) decoded.recycle()
            corrected
        }.getOrNull()
    }

    /** Standard power-of-two downscale so large photos decode without OOM. */
    private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        if (height > maxSize || width > maxSize) {
            var halfH = height / 2
            var halfW = width / 2
            while (halfH / sample >= maxSize || halfW / sample >= maxSize) sample *= 2
        }
        return sample
    }
}
