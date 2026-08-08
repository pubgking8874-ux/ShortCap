package com.shortscap.app.study

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Exit Passcode icon — \"use a passcode to EXIT Study Mode.\"
 *
 * An open book (Study) with a bold arrow exiting up-right (Exit) — the
 * feature gates LEAVING an active Study Mode session, so the icon
 * deliberately communicates EXIT rather than LOCK. Clean geometry keeps it
 * recognizable at small sizes (14dp hints and 24dp tiles); the tint comes
 * from `Icon(tint = …)`, so it works on both Dark and Light themes.
 */
val FocusPasscodeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocusPasscodeExit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Open book — two pages meeting at the center spine (V at the top,
        // pages splaying slightly at the bottom).
        path(fill = SolidColor(Color.Black)) {
            // Left page
            moveTo(3.5f, 6.2f)
            lineTo(12f, 8.4f) // top edge → spine
            lineTo(12f, 17.6f) // spine down
            curveTo(9f, 16.5f, 5.8f, 16.6f, 3.5f, 17.3f) // bottom splay
            close()
            // Right page
            moveTo(20.5f, 6.2f)
            lineTo(12f, 8.4f)
            lineTo(12f, 17.6f)
            curveTo(15f, 16.5f, 18.2f, 16.6f, 20.5f, 17.3f)
            close()
        }
        // Outward exit arrow — emerges from the book toward the top-right
        // corner. (The segment inside the book is the same tint, so the
        // arrow visually starts at the book and points out.)
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(11.8f, 12.2f) // shaft from the book center
            lineTo(21f, 2.6f) // …to the tip
            moveTo(16.8f, 3.2f) // upper head barb
            lineTo(21f, 2.6f)
            moveTo(21f, 2.6f) // lower head barb (kept clear of the book edge)
            lineTo(20.5f, 5.2f)
        }
    }.build()
}
