package com.shortscap.app.screens.rank

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScChip
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScSkeleton
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.screens.web.formatWebDuration
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlin.math.roundToInt

// ============================================================
// UI PLACEHOLDER DATA — Rank is a UI/UX + navigation task at
// this stage. Every value below is mocked for layout development
// and is NOT connected to any backend, leaderboard database or
// score engine (those ship in a later milestone). The score
// concept used app-wide on this screen is "Your Score" — never
// "Focus Score" / "Focus Points" / "Focus Timing".
// ============================================================

/** One leaderboard row: rank, display name and score. */
private data class RankEntry(
    val rank: Int,
    val name: String,
    val score: Int,
    val isCurrentUser: Boolean = false,
)

/** Time filter on the Rank screen — UI-only selection for now. */
private enum class RankPeriod { WEEK, MONTH }

/** Current user's mocked rank summary shown on the status card. */
private data class RankUserSummary(
    val rank: Int = 12,
    val score: Int = 86,
    val positionChange: Int = 3, // "+3 positions this week"
)

/** Mocked full leaderboard (the "You" label reads from the active language). */
private fun mockLeaderboard(strings: AppStrings): List<RankEntry> = listOf(
    RankEntry(1, "Rahul", 94),
    RankEntry(2, "Aman", 91),
    RankEntry(3, "Priya", 89),
    RankEntry(4, "Neha", 88),
    RankEntry(5, "Sana", 87),
    RankEntry(6, "Vikram", 87),
    RankEntry(7, "Ishaan", 86),
    RankEntry(8, "Meera", 86),
    RankEntry(9, "Kabir", 85),
    RankEntry(10, "Ananya", 85),
    RankEntry(11, "Rohan", 84),
    RankEntry(12, strings.rankYou, 86, isCurrentUser = true),
)

/**
 * Rank tab UI state — UI placeholders ONLY (no backend calls yet).
 *
 * The screen renders [Data] with its mock leaderboard by default. The
 * [Loading] / [Empty] / [Error] branches are fully built so the future
 * backend plugs in by switching [RankScreen.uiState] — no UI redesign
 * needed when the ranking API arrives.
 */
sealed interface RankUiState {
    data object Loading : RankUiState
    data object Empty : RankUiState
    data class Error(val message: String) : RankUiState
    data object Data : RankUiState
}

/**
 * Rank — the dedicated leaderboard tab (Productivity + Competition +
 * Progress). Rendered inside the existing tab content (global top bar +
 * floating bottom nav stay fixed; the content scrolls with the tab).
 *
 * Everything reads the ACTIVE global systems:
 *  - theme: every color comes from [LocalScColors] (Dark / Light / System);
 *  - language: every label from [LocalAppStrings] (5 languages, RTL-aware);
 *  - font / text size: all text uses [ScTextStyles], which resolve the
 *    active global font family at use time;
 *  - icons: the Rank trophy icon resolves through the centralized icon
 *    system ([IconKey.RANK] + the active icon style).
 *
 * Animations are deliberately calm: score count-ups, staggered podium and
 * leaderboard entrances, and one animated progress bar — no gaming effects.
 */
@Composable
fun RankScreen(uiState: RankUiState = RankUiState.Data) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var period by remember { mutableStateOf(RankPeriod.WEEK) }
    // One-shot entrance flag — flips after first composition so every
    // staggered fade/slide animation plays once per tab visit.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val leaderboard = remember(strings) { mockLeaderboard(strings) }
    val topThree = leaderboard.take(3)
    val summary = RankUserSummary()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Screen title — same H1 treatment as the other tabs.
        Text(strings.rankTitle, color = colors.TextPrimary, style = ScTextStyles.H1)

        when (uiState) {
            RankUiState.Loading -> RankLoadingContent(strings)
            RankUiState.Empty -> RankEmptyContent(strings)
            is RankUiState.Error -> RankErrorContent(strings, uiState.message)
            RankUiState.Data -> {
                // Your Rank / Your Score — hero status card.
                RankUserStatusCard(
                    strings = strings,
                    rank = summary.rank,
                    score = summary.score,
                    positionChange = summary.positionChange,
                )

                // This Week | This Month — segmented pill selector (UI only).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScChip(
                        label = strings.rankThisWeek,
                        active = period == RankPeriod.WEEK,
                        onClick = { period = RankPeriod.WEEK },
                    )
                    ScChip(
                        label = strings.rankThisMonth,
                        active = period == RankPeriod.MONTH,
                        onClick = { period = RankPeriod.MONTH },
                    )
                }

                // Top 3 podium — 2nd | 1st | 3rd, compact and polished.
                RankPodium(strings = strings, topThree = topThree, entered = entered)

                // Full leaderboard — every entry, current user highlighted.
                RankLeaderboard(strings = strings, entries = leaderboard, entered = entered)

                // Your Progress — placeholder metrics (mock values only).
                RankProgressSection(strings = strings, entered = entered)
            }
        }
    }
}

/** Hero card: "Your Rank" + "Your Score" with the weekly movement badge. */
@Composable
private fun RankUserStatusCard(
    strings: AppStrings,
    rank: Int,
    score: Int,
    positionChange: Int,
) {
    val colors = LocalScColors.current
    val style = LocalIconStyle.current
    val shape = RoundedCornerShape(22.dp)
    val rankCount = rememberCountUp(rank, durationMillis = 650)
    val scoreCount = rememberCountUp(score, durationMillis = 650)
    val bigNumber = ScTextStyles.BigStat.copy(fontSize = 26.sp)
    // Trophy entrance animation — plays ONCE per Rank screen entry (this
    // card is composed when the Rank tab opens and disposed on leave, so
    // ordinary recomposition never restarts it; nothing loops). Soft
    // scale-in with a gentle bounce/settle (0.85 → 1.08 → 1.00) plus a
    // subtle one-shot glow pulse. Native Compose animation, works offline.
    val trophyScale = remember { Animatable(0.85f) }
    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        trophyScale.animateTo(1.08f, animationSpec = tween(380, easing = FastOutSlowInEasing))
        trophyScale.animateTo(1f, animationSpec = tween(240, easing = FastOutSlowInEasing))
        glowAlpha.animateTo(0.45f, animationSpec = tween(300))
        glowAlpha.animateTo(0f, animationSpec = tween(500))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(colors.SummaryCardGradientStart, colors.SummaryCardGradientEnd)),
            )
            .border(1.dp, colors.SummaryCardBorder, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Trophy tile — resolved through the centralized icon system
            // (same trophy visual language as the bottom navigation). The
            // one-shot entrance animation lives on this icon: scale-in +
            // gentle glow, then it settles — never loops.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.CardHover),
                contentAlignment = Alignment.Center,
            ) {
                // Soft one-shot glow halo behind the trophy (fades in and out).
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.Accent.copy(alpha = glowAlpha.value * 0.35f)),
                )
                Icon(
                    IconTheme.icon(style, IconKey.RANK),
                    contentDescription = null,
                    tint = IconTheme.tint(style, IconKey.RANK, colors.Accent),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = trophyScale.value
                            scaleY = trophyScale.value
                        },
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(strings.rankYourRank, color = colors.TextSecondary, style = ScTextStyles.Label)
                Text("$rankCount", color = colors.TextPrimary, style = bigNumber)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(strings.rankYourScore, color = colors.TextSecondary, style = ScTextStyles.Label)
                Text("$scoreCount", color = colors.Accent, style = bigNumber)
            }
        }
        ScDivider()
        // Weekly movement indicator — "+3 positions this week" (Success tint).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = colors.Success, modifier = Modifier.size(14.dp))
            Text(strings.rankPositionChange(positionChange), color = colors.Success, style = ScTextStyles.Caption)
        }
    }
}

/** Top 3 podium — 2nd | 1st | 3rd, bottom-aligned pedestals, staggered entrance. */
@Composable
private fun RankPodium(
    strings: AppStrings,
    topThree: List<RankEntry>,
    entered: Boolean,
) {
    val colors = LocalScColors.current
    val first = topThree[0]
    val second = topThree[1]
    val third = topThree[2]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        RankPodiumEntry(
            strings = strings,
            placeLabel = strings.rankSecondPlace,
            name = second.name,
            score = second.score,
            accent = colors.Accent2,
            pedestalHeight = 46.dp,
            delayMillis = 160,
            entered = entered,
            modifier = Modifier.weight(1f),
        )
        RankPodiumEntry(
            strings = strings,
            placeLabel = strings.rankFirstPlace,
            name = first.name,
            score = first.score,
            accent = colors.Accent,
            pedestalHeight = 62.dp,
            delayMillis = 80,
            emphasized = true,
            entered = entered,
            modifier = Modifier.weight(1f),
        )
        RankPodiumEntry(
            strings = strings,
            placeLabel = strings.rankThirdPlace,
            name = third.name,
            score = third.score,
            accent = colors.Warning,
            pedestalHeight = 38.dp,
            delayMillis = 260,
            entered = entered,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One podium column: place pill, avatar, name, count-up score, pedestal. */
@Composable
private fun RankPodiumEntry(
    strings: AppStrings,
    placeLabel: String,
    name: String,
    score: Int,
    accent: Color,
    pedestalHeight: Dp,
    delayMillis: Int,
    entered: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val colors = LocalScColors.current
    val scoreCount = rememberCountUp(score, durationMillis = 600, delayMillis = delayMillis)
    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(tween(360, delayMillis = delayMillis)) +
            slideInVertically(tween(360, delayMillis = delayMillis)) { it / 5 },
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Place pill ("1st Place" / "2nd Place" / "3rd Place").
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = if (emphasized) 0.18f else 0.10f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(placeLabel, color = accent, style = ScTextStyles.Caption)
            }
            Spacer(Modifier.height(8.dp))
            // Avatar — 1st place gets the strongest emphasis (bigger + ring).
            Box(
                modifier = Modifier
                    .size(if (emphasized) 52.dp else 46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (emphasized) 0.22f else 0.13f))
                    .then(
                        if (emphasized) Modifier.border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.firstOrNull()?.toString() ?: "?",
                    color = colors.TextPrimary,
                    style = ScTextStyles.BodySemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                name,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(strings.rankScoreValue(scoreCount), color = colors.TextSecondary, style = ScTextStyles.Caption)
            Spacer(Modifier.height(10.dp))
            // Pedestal — tallest for 1st, shorter for 2nd/3rd (bottom-aligned).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pedestalHeight)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(accent.copy(alpha = if (emphasized) 0.35f else 0.22f)),
            )
        }
    }
}

/** Full leaderboard — every row with rank / name / score, current user highlighted. */
@Composable
private fun RankLeaderboard(
    strings: AppStrings,
    entries: List<RankEntry>,
    entered: Boolean,
) {
    val colors = LocalScColors.current
    Column {
        Text(strings.rankLeaderboard, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
        Spacer(Modifier.height(12.dp))
        ScCard(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                AnimatedVisibility(
                    visible = entered,
                    enter = fadeIn(tween(320, delayMillis = 260 + index * 45)) +
                        slideInVertically(tween(320, delayMillis = 260 + index * 45)) { it / 6 },
                ) {
                    RankLeaderboardRow(entry = entry, strings = strings)
                }
                if (index < entries.size - 1) ScDivider()
            }
        }
    }
}

/** One leaderboard row; the current user gets a subtle highlighted card. */
@Composable
private fun RankLeaderboardRow(entry: RankEntry, strings: AppStrings) {
    val colors = LocalScColors.current
    val isUser = entry.isCurrentUser
    val shape = RoundedCornerShape(14.dp)
    val displayName = if (isUser) strings.rankYou else entry.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isUser) {
                    Modifier
                        .clip(shape)
                        .background(colors.ChipActiveBg, shape)
                        .border(1.dp, colors.Accent.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${entry.rank}",
            color = if (isUser) colors.Accent else colors.TextSecondary,
            style = ScTextStyles.BodySemiBold,
            modifier = Modifier.width(30.dp),
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (isUser) colors.CardHover else colors.CardHover.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                displayName.firstOrNull()?.toString() ?: "?",
                color = if (isUser) colors.Accent else colors.TextSecondary,
                style = ScTextStyles.Caption,
            )
        }
        Text(
            displayName,
            color = colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${entry.score}",
            color = if (isUser) colors.Accent else colors.TextPrimary,
            style = ScTextStyles.BodySemiBold,
        )
    }
}

/** "Your Progress" — 2x2 metric tiles with mock values and one animated bar. */
@Composable
private fun RankProgressSection(strings: AppStrings, entered: Boolean) {
    val colors = LocalScColors.current
    Column {
        Text(strings.rankYourProgress, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RankProgressTile(
                    label = strings.rankShorts,
                    countUpTarget = 23,
                    modifier = Modifier.weight(1f),
                )
                RankProgressTile(
                    label = strings.rankDistractingApps,
                    valueText = formatWebDuration(41, strings),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RankProgressTile(
                    label = strings.rankStudySessions,
                    countUpTarget = 8,
                    modifier = Modifier.weight(1f),
                )
                // The score tile carries the animated 0–100 progress bar.
                RankProgressTile(
                    label = strings.rankYourScore,
                    countUpTarget = 86,
                    progressFraction = 0.86f,
                    entered = entered,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One progress metric tile: count-up value (or text) + label + optional bar. */
@Composable
private fun RankProgressTile(
    label: String,
    modifier: Modifier = Modifier,
    valueText: String = "",
    countUpTarget: Int? = null,
    progressFraction: Float? = null,
    entered: Boolean = true,
) {
    val colors = LocalScColors.current
    val shape = RoundedCornerShape(18.dp)
    val displayed = countUpTarget?.let { rememberCountUp(it, durationMillis = 700).toString() } ?: valueText
    val barFraction by animateFloatAsState(
        targetValue = if (progressFraction != null && entered) progressFraction else 0f,
        animationSpec = tween(900, delayMillis = 350),
        label = "rankScoreBar",
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.Card, shape)
            .border(1.dp, colors.Divider, shape)
            .padding(14.dp),
    ) {
        Text(displayed, color = colors.TextPrimary, style = ScTextStyles.StatValue)
        Spacer(Modifier.height(2.dp))
        Text(label, color = colors.TextSecondary, style = ScTextStyles.Label, maxLines = 1)
        if (progressFraction != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.ProgressTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(barFraction)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.Accent),
                )
            }
        }
    }
}

// ---- Placeholder states (no backend yet — wired for the future API) ----

/** "Loading ranking..." — skeleton placeholders. */
@Composable
private fun RankLoadingContent(strings: AppStrings) {
    val colors = LocalScColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.rankLoading, color = colors.TextSecondary, style = ScTextStyles.Label)
        ScSkeleton(height = 130.dp)
        ScSkeleton(height = 150.dp)
        ScSkeleton(height = 90.dp)
    }
}

/** "No ranking data yet" — empty placeholder. */
@Composable
private fun RankEmptyContent(strings: AppStrings) {
    ScEmptyState(
        iconKey = IconKey.RANK,
        title = strings.rankEmpty,
        subtitle = "",
    )
}

/** "Unable to load ranking" — error placeholder. */
@Composable
private fun RankErrorContent(strings: AppStrings, message: String = "") {
    ScEmptyState(
        iconKey = IconKey.RANK,
        title = strings.rankError,
        subtitle = message,
    )
}

/**
 * Calm count-up animation: animates [target] from 0 over [durationMillis]
 * (with an optional stagger [delayMillis]) using the app's eased tween.
 */
@Composable
private fun rememberCountUp(target: Int, durationMillis: Int = 700, delayMillis: Int = 0): Int {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        )
    }
    return progress.value.roundToInt()
}
