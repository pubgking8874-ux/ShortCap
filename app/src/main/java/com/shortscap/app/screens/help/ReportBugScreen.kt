package com.shortscap.app.screens.help

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Report a Bug — Subject + Description + Submit, styled with the premium card
 * language (icon tile header, generous spacing, rounded fields).
 * Current stage: shows a success message. Future: posts to a backend bug
 * report API via [onSubmitted] — the UI stays identical.
 */
@Composable
fun ReportBugScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val colors = LocalScColors.current
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = "Report a Bug", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp),
        ) {
            ScCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(colors.StatIconBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = colors.Accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            "Report an issue",
                            color = colors.TextPrimary,
                            style = ScTextStyles.BodySemiBold.copy(fontSize = 15.sp),
                        )
                    }
                    BugField(
                        label = "Subject",
                        value = subject,
                        onValueChange = { subject = it },
                        placeholder = "Briefly describe the issue",
                        minHeight = 54.dp,
                    )
                    BugField(
                        label = "Describe your issue",
                        value = description,
                        onValueChange = { description = it },
                        placeholder = "Steps to reproduce, expected vs actual behaviour…",
                        minHeight = 140.dp,
                    )
                    ScButton(
                        label = "Submit",
                        variant = ScButtonVariant.PRIMARY,
                        enabled = subject.isNotBlank() && description.isNotBlank(),
                        onClick = {
                            submitted = true
                            onSubmitted()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (submitted) {
                        Text(
                            "Bug report submitted. Thank you!",
                            color = colors.Success,
                            style = ScTextStyles.BodySemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BugField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Dp,
) {
    val colors = LocalScColors.current
    Column {
        Text(label, color = colors.TextSecondary, style = ScTextStyles.SectionTitle)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.CardHover, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = colors.TextPrimary, fontSize = 13.5.sp),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.TopStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = colors.TextDisabled, fontSize = 13.5.sp)
                        }
                        inner()
                    }
                },
            )
        }
    }
}
