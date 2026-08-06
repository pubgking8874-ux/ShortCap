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
import androidx.compose.material.icons.filled.ChevronRight
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
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

private val faqItems = listOf(
    "How does ShortsCap work?" to
        "ShortsCap monitors your app usage and helps you build healthier digital habits. It tracks screen time, shows usage analytics on your dashboard, and lets you block distracting apps or enter Focus Mode to stay productive.",
    "Why is Accessibility Permission required?" to
        "Accessibility permission lets ShortsCap power App Blocking and Focus Mode on your Android device. It is used only for features you intentionally enable and never to read messages, passwords, or other personal content.",
    "Why is Usage Access Permission required?" to
        "Usage Access permission gives ShortsCap read access to your device's app-usage statistics so it can show accurate screen-time and usage reports on the dashboard. You can revoke it anytime from Android Settings.",
    "How do I block Shorts?" to
        "Open the Web screen from the bottom navigation, select the Blocked tab, and add the app or site you want to restrict. Blocking applies based on the preferences and permissions you grant.",
    "How do I reset my password?" to
        "On the Sign In screen, tap 'Forgot Password?', enter your registered email, and use the verification code we send to you to create a new password.",
    "Why am I not receiving OTP?" to
        "Check that you entered the correct registered email or mobile number, that you have a stable internet connection, and that our message did not land in spam. If it still does not arrive, contact support.",
    "How do I update my profile?" to
        "Tap your profile avatar in the top bar, open Edit Profile, and update your name, email, or other details. Changes are saved to your account.",
    "How do I delete my account?" to
        "Account deletion is available from your profile/account settings. After confirmation, your account and associated data will be removed in line with our Privacy Policy.",
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
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "Frequently Asked Questions", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            faqItems.forEachIndexed { index, (question, answer) ->
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
                Icons.Filled.ChevronRight,
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
