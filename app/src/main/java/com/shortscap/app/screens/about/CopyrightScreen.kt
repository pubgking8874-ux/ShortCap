package com.shortscap.app.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.IconTheme
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/**
 * Copyright — ©2026 ShortsCap, All Rights Reserved in a premium centered
 * card. Static legal notice today; wording can later come from a backend
 * legal endpoint without any UI change.
 */
@Composable
fun CopyrightScreen(
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = strings.copyrightTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 22.dp),
        ) {
            ScCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val style = LocalIconStyle.current
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.CardHover),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        IconTheme.icon(style, IconKey.ABOUT_COPYRIGHT),
                        contentDescription = null,
                        tint = IconTheme.tint(style, IconKey.ABOUT_COPYRIGHT, colors.Accent),
                        modifier = Modifier.size(28.dp),
                    )
                }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.copyrightLine,
                        color = colors.TextPrimary,
                        style = ScTextStyles.H1,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        strings.allRightsReserved,
                        color = colors.TextSecondary,
                        style = ScTextStyles.Body,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
