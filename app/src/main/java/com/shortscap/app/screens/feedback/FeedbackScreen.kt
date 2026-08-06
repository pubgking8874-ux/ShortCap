package com.shortscap.app.screens.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Mirrors the Feedback page — star rating, feedback box, submit. */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    onFeedbackSubmitted: () -> Unit,
) {
    val colors = LocalScColors.current
    var rating by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "Feedback", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("How was your experience?", color = colors.TextPrimary, style = ScTextStyles.H1)

            ScCard(modifier = Modifier.fillMaxWidth()) {
                Text("Rate ShortsCap", color = colors.TextPrimary, style = ScTextStyles.SectionTitle)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..5).forEach { star ->
                        val filled = star <= rating
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (filled) colors.ChipActiveBg else colors.CardHover)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { rating = star },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "$star star${if (star > 1) "s" else ""}",
                                tint = if (filled) colors.Warning else colors.TextSecondary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        rating == 0 -> "Tap a star to rate"
                        rating <= 2 -> "Sorry to hear that."
                        rating <= 4 -> "Thanks — we're glad you're with us."
                        else -> "Excellent! Thank you."
                    },
                    color = colors.TextSecondary,
                    style = ScTextStyles.Caption,
                )
            }

            ScCard(modifier = Modifier.fillMaxWidth()) {
                Text("Your Feedback", color = colors.TextPrimary, style = ScTextStyles.SectionTitle)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.CardHover, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    BasicTextField(
                        value = feedback,
                        onValueChange = { feedback = it },
                        textStyle = TextStyle(color = colors.TextPrimary, fontSize = 13.5.sp),
                        modifier = Modifier.fillMaxSize(),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.TopStart) {
                                if (feedback.isEmpty()) {
                                    Text(
                                        "Write your feedback...",
                                        color = colors.TextDisabled,
                                        fontSize = 13.5.sp,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            ScButton(
                label = "Submit",
                variant = ScButtonVariant.PRIMARY,
                enabled = rating > 0 && feedback.isNotBlank(),
                onClick = {
                    submitted = true
                    onFeedbackSubmitted()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (submitted) {
                Text(
                    "Thank you for your feedback.",
                    color = colors.Success,
                    style = ScTextStyles.BodySemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}