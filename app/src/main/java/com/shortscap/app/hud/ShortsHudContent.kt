package com.shortscap.app.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shortscap.app.R
import com.shortscap.app.shorts.ShortFormSurfaceState
import com.shortscap.app.theme.LocalScColors

/**
 * ShortsHudContent — the floating HUD's Compose UI.
 *
 * Pure presentation: renders the shared [ShortsHudUiState] (a positively
 * detected short-form surface + global count/limit published by the HUD
 * controller from the existing detection/aggregation pipeline). Three
 * appearance modes:
 *
 *  - [ShortsHudAppearance.SHORTSCAP]: compact branded chip — ShortsCap logo
 *    + "count / limit" (primary accent).
 *  - [ShortsHudAppearance.BRAIN]: the user's FINAL brain videos, driven by
 *    usage states (HEALTHY / TIRED / NEAR_LIMIT / LIMIT_REACHED).
 *  - [ShortsHudAppearance.LIVE_COUNTER]: cleanest minimal counter.
 *
 * Animations are native Compose, event/state-driven and battery-friendly:
 * the HUD settles STATIC after entry; only count changes trigger a micro
 * bump. Brain video playback happens ONLY while the Brain mode is visible;
 * the player is released the moment the HUD hides or another mode is chosen
 * (see [BrainVideoView]).
 *
 * The whole chip is draggable via [onDrag] / [onDragEnd]; a tap without a
 * drag does nothing destructive.
 */
@Composable
fun ShortsHudContent(
    uiState: ShortsHudUiState,
    appearance: ShortsHudAppearance,
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
) {
    val colors = LocalScColors.current

    val count = uiState.count
    val limit = uiState.limit
    val visible = uiState.visible

    // Entry animation: alpha 0->1, scale 0.92->1.00, ~200ms — applied on the
    // very first frame after the overlay is added.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entryScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.92f,
        animationSpec = tween(durationMillis = 200),
        label = "hudEntryScale",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150)),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(colors.Card.copy(alpha = 0.94f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .graphicsLayer {
                    scaleX = entryScale
                    scaleY = entryScale
                },
        ) {
            when (appearance) {
                ShortsHudAppearance.SHORTSCAP -> ShortsCapChip(count, limit, colors.Accent)
                ShortsHudAppearance.BRAIN -> BrainChip(count = count, limit = limit)
                ShortsHudAppearance.LIVE_COUNTER -> LiveCounterChip(count, limit, colors.Accent)
            }
        }
    }
}

/**
 * The HUD's shared UI state, backed by Compose [mutableStateOf]s so the
 * overlay recomposes automatically when the controller publishes new values
 * (no re-setContent needed).
 */
class ShortsHudUiState(
    initialCount: Int = 0,
    initialLimit: Int = 200,
) {
    var visible by mutableStateOf(false)
        internal set

    var count by mutableStateOf(initialCount)
        internal set

    var limit by mutableStateOf(initialLimit)
        internal set
}

/** Branded chip: [logo] 4 / 200 — primary accent, compact. */
@Composable
private fun ShortsCapChip(count: Int, limit: Int, accent: Color) {
    val colors = LocalScColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.logo_pic),
            contentDescription = "ShortsCap",
            modifier = Modifier.size(22.dp),
        )
        // Tiny scale/fade on the count itself when it changes.
        key(count) {
            val countScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180),
                label = "countBump",
            )
            Text(
                text = "  $count / $limit",
                color = colors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.scale(countScale),
            )
        }
    }
}

/** Minimal live counter chip: "04 / 200". */
@Composable
private fun LiveCounterChip(count: Int, limit: Int, accent: Color) {
    key(count) {
        val countScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180),
            label = "liveCountBump",
        )
        Text(
            text = "${count.toString().padStart(2, '0')} / $limit",
            color = accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(countScale),
        )
    }
}

/**
 * Brain chip — plays the user's FINAL brain video for the current state
 * (HEALTHY / TIRED / NEAR_LIMIT / LIMIT_REACHED, derived from the
 * count/limit ratio), muted and looping, next to the count/limit text.
 *
 * The video is the ONLY brain animation: the videos are never recolored, and
 * playback happens only while this chip is composed (HUD visible in Brain
 * mode). [BrainVideoView.showState] is a no-op for an unchanged state, so
 * normal recomposition never restarts the video.
 */
@Composable
private fun BrainChip(count: Int, limit: Int) {
    val colors = LocalScColors.current
    val ratio = if (limit > 0) count.toFloat() / limit else 0f
    val brainState = BrainState.forRatio(ratio)

    var brainView by remember { mutableStateOf<BrainVideoView?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AndroidView(
            factory = { context ->
                BrainVideoView(context).also { brainView = it }
            },
            update = { it.showState(brainState) },
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Brain $brainState" },
        )
        key(count) {
            val countScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180),
                label = "brainCountBump",
            )
            Text(
                text = "  $count / $limit",
                color = colors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.scale(countScale),
            )
        }
    }

    // Release the player whenever the chip leaves composition (HUD hidden or
    // another appearance selected) so no video plays in the background.
    DisposableEffect(Unit) {
        onDispose { brainView?.release() }
    }
}
