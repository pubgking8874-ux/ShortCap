package com.shortscap.app.study

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Allow Apps / Website icon — \"allowed access to apps AND websites\".
 *
 * One clean mark combining the three ideas the section is about:
 *   • a rounded app-window (APPS),
 *   • a globe inside it (WEBSITES),
 *   • a check badge on the window (ALLOWED / permitted).
 *
 * The items in this list stay accessible while Study Mode is active, so the
 * icon communicates permission (check) + apps + web rather than a generic
 * list or a lone padlock. Clean stroke geometry stays legible at small
 * sizes; the tint comes from `Icon(tint = …)`, so it renders correctly on
 * Dark and Light themes in both icon styles.
 */
val AllowedAppsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AllowedApps",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Rounded app-window outline (APPS).
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6.0f, 3.2f)
            curveTo(4.6f, 3.2f, 3.2f, 4.6f, 3.2f, 6.0f)
            lineTo(3.2f, 18.0f)
            curveTo(3.2f, 19.4f, 4.6f, 20.8f, 6.0f, 20.8f)
            lineTo(18.0f, 20.8f)
            curveTo(19.4f, 20.8f, 20.8f, 19.4f, 20.8f, 18.0f)
            lineTo(20.8f, 6.0f)
            curveTo(20.8f, 4.6f, 19.4f, 3.2f, 18.0f, 3.2f)
            close()
        }
        // Globe (WEBSITES) — circle + meridian + equator.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(14.6f, 10.6f)
            arcTo(4.2f, 4.2f, 0f, false, true, 6.2f, 10.6f)
            arcTo(4.2f, 4.2f, 0f, false, true, 14.6f, 10.6f)
            moveTo(10.4f, 6.4f)
            lineTo(10.4f, 14.8f)
            moveTo(14.6f, 10.6f)
            arcTo(4.2f, 1.5f, 0f, false, true, 6.2f, 10.6f)
            arcTo(4.2f, 1.5f, 0f, false, true, 14.6f, 10.6f)
        }
        // Allowed badge — circled check on the window corner (ALLOWED).
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(19.9f, 17.3f)
            arcTo(3.0f, 3.0f, 0f, false, true, 13.9f, 17.3f)
            arcTo(3.0f, 3.0f, 0f, false, true, 19.9f, 17.3f)
            moveTo(15.4f, 17.3f)
            lineTo(16.5f, 18.4f)
            lineTo(18.5f, 16.1f)
        }
    }.build()
}
