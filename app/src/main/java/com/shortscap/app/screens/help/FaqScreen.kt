package com.shortscap.app.screens.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** FAQ content comes from the language catalog — no hardcoded text here. */
private fun faqItems(strings: AppStrings) = listOf(
    strings.faqQ1 to strings.faqA1,
    strings.faqQ2 to strings.faqA2,
    strings.faqQ3 to strings.faqA3,
    strings.faqQ4 to strings.faqA4,
    strings.faqQ5 to strings.faqA5,
    strings.faqQ6 to strings.faqA6,
    strings.faqQ7 to strings.faqA7,
    strings.faqQ8 to strings.faqA8,
)

/**
 * Frequently Asked Questions — premium expandable accordion cards. Every
 * answer is collapsed by default; tapping a card expands it with a smooth
 * animation while the chevron rotates.
 */
@Composable
fun FaqScreen(
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = strings.faqTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            faqItems(strings).forEachIndexed { index, (question, answer) ->
                FaqAccordionCard(
                    question = question,
                    answer = answer,
                    expanded = expandedIndex == index,
                    onClick = { expandedIndex = if (expandedIndex == index) null else index },
                )
            }
        }
    }
}

@Composable
private fun FaqAccordionCard(
    question: String,
    answer: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalScColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(22.dp)

    val bg by animateColorAsState(
        targetValue = if (pressed) colors.Accent.copy(alpha = 0.08f) else colors.Card,
        label = "faqBg",
    )
    val border by animateColorAsState(
        targetValue = when {
            expanded -> colors.Accent.copy(alpha = 0.55f)
            pressed -> colors.Accent.copy(alpha = 0.40f)
            else -> colors.Divider
        },
        label = "faqBorder",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "faqChevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.CardHover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = colors.Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                question,
                color = colors.TextPrimary,
                style = ScTextStyles.BodySemiBold.copy(fontSize = 14.5.sp),
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (expanded) colors.Accent else colors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(240)) + fadeIn(tween(240)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
        ) {
            Text(
                answer,
                color = colors.TextSecondary,
                style = ScTextStyles.Body,
                modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
