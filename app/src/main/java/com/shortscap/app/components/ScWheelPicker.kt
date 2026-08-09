package com.shortscap.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.i18n.LocalAppStrings
import kotlinx.coroutines.launch
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlin.math.abs

/**
 * ShortsCap's ONE time/duration selection system.
 *
 * Every place the user picks a time, hour, minute, duration or reminder uses
 * this single scroll-wheel component (plus the two dialog wrappers below):
 *   • [ScClockWheelDialog]  — 12-hour clock: Hour + Minute + AM/PM wheels.
 *   • [ScDurationWheelDialog] — Hours + Minutes wheels (0 is valid).
 *
 * Swipe up/down to scroll, flick to spin, tap a value to jump to it. The
 * centered value is the selection — highlighted, with neighbours peeking
 * above and below like a modern wheel. Values only commit on the dialog's
 * confirm action; an accidental scroll can always be cancelled.
 */

/**
 * A single snapping wheel of string values. [initialIndex] selects the value
 * shown centered when the wheel opens; [onIndexChange] reports the value
 * centered at any moment (live preview). Values above/below stay partially
 * visible to create the natural wheel look.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScWheelPicker(
    values: List<String>,
    initialIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
    visibleCount: Int = 5,
    contentDescription: String? = null,
) {
    if (values.isEmpty()) return
    val colors = LocalScColors.current

    // Empty spacers above/below so every real value can be centered (and the
    // wheel looks like it rolls past the top/bottom edge).
    val pad = (visibleCount - 1) / 2
    val paddedValues = remember(values, pad) { List(pad) { "" } + values + List(pad) { "" } }
    val clampedInitial = initialIndex.coerceIn(0, values.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = clampedInitial)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState, snapPosition = SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val currentOnIndexChange by rememberUpdatedState(onIndexChange)

    // The real value currently aligned with the viewport center — derived from
    // layout so it stays exact while dragging and after a fling settles.
    val selectedIndex by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            if (layout.visibleItemsInfo.isEmpty()) return@derivedStateOf -1
            val center = layout.viewportStartOffset + layout.viewportSize.height / 2f
            layout.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2f) - center) }
                ?.let { item -> (item.index - pad).coerceIn(0, values.lastIndex) }
                ?: -1
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) currentOnIndexChange(selectedIndex)
    }

    val desc = contentDescription
    val pickerModifier = if (desc != null) {
        modifier
            .semantics { this.contentDescription = desc }
            .height(itemHeight * visibleCount)
    } else {
        modifier.height(itemHeight * visibleCount)
    }

    Box(modifier = pickerModifier) {
        // Center selection highlight.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.ChipActiveBg),
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(paddedValues, key = { i, _ -> i }) { index, label ->
                val isReal = label.isNotEmpty()
                val isSelected = index == selectedIndex + pad
                if (isReal) {
                    WheelItem(
                        label = label,
                        selected = isSelected,
                        onClick = { scope.launch { listState.animateScrollToItem(index - pad) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                    )
                } else {
                    Spacer(Modifier.fillMaxWidth().height(itemHeight))
                }
            }
        }
    }
}

/** One wheel row — strong emphasis on the centered selection. */
@Composable
private fun WheelItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalScColors.current
    val labelColor by animateColorAsState(
        if (selected) colors.TextPrimary else colors.TextSecondary,
        label = "wheelItemColor",
    )
    val sizeScale by animateFloatAsState(if (selected) 1f else 0.72f, label = "wheelItemSize")
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = labelColor,
            fontSize = (22.sp.value * sizeScale).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 12-hour wheel clock — Hour wheel (12, 1–11), Minute wheel (00–59) and a
 * separate AM/PM wheel, all sharing the same wheel interaction. Confirms with
 * minutes-since-midnight (0–1439), the same unit the schedule stores.
 */
@Composable
fun ScClockWheelDialog(
    title: String,
    initialMinutesOfDay: Int,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val hourValues = remember { listOf("12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11") }
    val minuteValues = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val amPmValues = remember(strings) { listOf(strings.studyTimeAm, strings.studyTimePm) }

    val initialHour24 = ((initialMinutesOfDay / 60) % 24 + 24) % 24
    val initialDisplayHour = if (initialHour24 % 12 == 0) 12 else initialHour24 % 12
    val initialHourIndex = if (initialDisplayHour == 12) 0 else initialDisplayHour
    val initialMinuteIndex = initialMinutesOfDay % 60
    val initialAmPmIndex = if (initialHour24 < 12) 0 else 1

    var hourIndex by remember { mutableIntStateOf(initialHourIndex) }
    var minuteIndex by remember { mutableIntStateOf(initialMinuteIndex) }
    var amPmIndex by remember { mutableIntStateOf(initialAmPmIndex) }

    val displayHour = hourValues[hourIndex]
    val displayMinute = minuteValues[minuteIndex]
    val displayPeriod = amPmValues[amPmIndex]
    val selectedMinutesOfDay = remember(hourIndex, minuteIndex, amPmIndex) {
        val hour24 = if (amPmIndex == 0) displayHour.toInt() % 12 else (displayHour.toInt() % 12) + 12
        hour24 * 60 + minuteIndex
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScWheelPicker(
                        values = hourValues,
                        initialIndex = initialHourIndex,
                        onIndexChange = { hourIndex = it },
                        modifier = Modifier.width(72.dp),
                        contentDescription = strings.studyDurationHours,
                    )
                    Text(
                        ":",
                        color = colors.TextPrimary,
                        style = ScTextStyles.BodySemiBold.copy(fontSize = 24.sp),
                    )
                    ScWheelPicker(
                        values = minuteValues,
                        initialIndex = initialMinuteIndex,
                        onIndexChange = { minuteIndex = it },
                        modifier = Modifier.width(76.dp),
                        contentDescription = strings.minutesLabel,
                    )
                    Spacer(Modifier.width(4.dp))
                    // Same wheel height as the hour/minute wheels so the three
                    // columns align into one uniform clock.
                    ScWheelPicker(
                        values = amPmValues,
                        initialIndex = initialAmPmIndex,
                        onIndexChange = { amPmIndex = it },
                        modifier = Modifier.width(72.dp),
                        visibleCount = 5,
                        contentDescription = "${strings.studyTimeAm} / ${strings.studyTimePm}",
                    )
                }
                // Live preview — the exact time as it will be saved.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.CardHover)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "$displayHour : $displayMinute $displayPeriod",
                        color = colors.TextPrimary,
                        style = ScTextStyles.BodySemiBold.copy(fontSize = 18.sp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMinutesOfDay) }) {
                Text(strings.studyDurationSave, color = colors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

/**
 * Hours + Minutes wheel selector — the SAME picker used for Study Duration,
 * Custom Duration, Break Duration and Reminder. Hours starts at 0 (0h 45m is
 * valid), minutes 00–59, both by smooth scrolling only — no +/− controls.
 * [valueLabel] renders the live total (e.g. "45 Minutes" / "1 Hour 30 Minutes"
 * / "15 Minutes Before"). Pass [clearLabel]/[onClear] to offer a "no value"
 * action (e.g. "No Reminder"); Save is disabled at 0h 0m.
 */
@Composable
fun ScDurationWheelDialog(
    title: String,
    initialMinutes: Int,
    valueLabel: (Int) -> String,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    val hourValues = remember { (0..24).map { it.toString() } }
    val minuteValues = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val initHours = (initialMinutes / 60).coerceIn(0, 24)
    val initMinutes = (initialMinutes % 60).coerceIn(0, 59)
    var hours by remember { mutableIntStateOf(initHours) }
    var minutes by remember { mutableIntStateOf(initMinutes) }
    val totalMinutes = hours * 60 + minutes

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = colors.Card,
        titleContentColor = colors.TextPrimary,
        textContentColor = colors.TextSecondary,
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WheelUnit(caption = strings.studyDurationHours, width = 88.dp) {
                        ScWheelPicker(
                            values = hourValues,
                            initialIndex = initHours,
                            onIndexChange = { hours = it },
                            modifier = Modifier.width(88.dp),
                            contentDescription = strings.studyDurationHours,
                        )
                    }
                    WheelUnit(caption = strings.minutesLabel, width = 88.dp) {
                        ScWheelPicker(
                            values = minuteValues,
                            initialIndex = initMinutes,
                            onIndexChange = { minutes = it },
                            modifier = Modifier.width(88.dp),
                            contentDescription = strings.minutesLabel,
                        )
                    }
                }
                // Live preview — the exact duration before saving.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.CardHover)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.studyDurationLabel, color = colors.TextSecondary, style = ScTextStyles.Body)
                    Text(valueLabel(totalMinutes), color = colors.TextPrimary, style = ScTextStyles.BodySemiBold)
                }
                // 0h 0m cannot be saved — small validation message under the
                // live preview (Save stays disabled until a positive duration).
                if (totalMinutes == 0) {
                    Text(
                        strings.studyDurationRequired,
                        color = colors.Danger,
                        style = ScTextStyles.Caption,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (clearLabel != null && onClear != null) {
                    TextButton(onClick = onClear) {
                        Text(clearLabel, color = colors.TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = totalMinutes > 0,
                onClick = { onConfirm(totalMinutes) },
            ) {
                Text(strings.studyDurationSave, color = if (totalMinutes > 0) colors.Accent else colors.TextDisabled)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(strings.cancel, color = colors.TextSecondary)
            }
        },
    )
}

/** Caption + wheel column (keeps the label aligned under its wheel). */
@Composable
private fun WheelUnit(
    caption: String,
    width: Dp,
    wheel: @Composable () -> Unit,
) {
    val colors = LocalScColors.current
    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(caption, color = colors.TextSecondary, style = ScTextStyles.Caption)
        wheel()
    }
}
