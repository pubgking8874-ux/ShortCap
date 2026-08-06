package com.shortscap.app.screens.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScChip
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.components.ScEntityRow
import com.shortscap.app.components.ScSwitch
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.model.ScEntity
import com.shortscap.app.model.ScEntityType
import com.shortscap.app.model.SiteEntry
import com.shortscap.app.model.WebTab
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles

/** Mirrors function WebScreen({ toast }) { ... } */
@Composable
fun WebScreen(
    tab: WebTab,
    onTabChange: (WebTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sites: List<SiteEntry>,
    onToggleSite: (String) -> Unit,
    onAddWebsite: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(strings.webTitle, color = colors.TextPrimary, style = ScTextStyles.H1)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.Card, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = colors.TextPrimary, fontSize = 13.5.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(strings.webSearchPlaceholder, color = colors.TextSecondary, fontSize = 13.5.sp)
                    }
                    inner()
                },
            )
        }

        ScCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                WebStat(value = "9", label = strings.webBlocked)
                Box(modifier = Modifier.width(1.dp).height(28.dp).background(colors.Divider))
                WebStat(value = "24", label = strings.webAllowed)
                Box(modifier = Modifier.width(1.dp).height(28.dp).background(colors.Divider))
                WebStat(value = "1h 05m", label = strings.webWebToday)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScChip(label = strings.webBlocked, active = tab == WebTab.BLOCKED, onClick = { onTabChange(WebTab.BLOCKED) })
            ScChip(label = strings.webAllowed, active = tab == WebTab.ALLOWED, onClick = { onTabChange(WebTab.ALLOWED) })
            ScChip(label = strings.webRecent, active = tab == WebTab.RECENT, onClick = { onTabChange(WebTab.RECENT) })
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.Card, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filter", tint = colors.TextSecondary, modifier = Modifier.size(13.dp))
            }
        }

        val filtered = sites.filter { it.name.contains(query, ignoreCase = true) }
        ScCard(modifier = Modifier.fillMaxWidth()) {
            if (filtered.isEmpty()) {
                ScEmptyState(
                    icon = Icons.Filled.Language,
                    title = strings.webNoSites,
                    subtitle = strings.webNoSitesSubtitle,
                )
            } else {
                filtered.forEachIndexed { index, site ->
                    ScEntityRow(
                        entity = ScEntity(
                            id = site.name,
                            title = site.name,
                            type = ScEntityType.WEBSITE,
                            websiteUrl = site.url,
                            fallbackColor = colors.TextSecondary,
                        ),
                        subtitle = site.url,
                        trailing = { ScSwitch(on = site.on, onToggle = { onToggleSite(site.name) }) },
                    )
                    if (index < filtered.size - 1) ScDivider()
                }
            }
        }

        ScButton(
            label = strings.webAddWebsite,
            variant = ScButtonVariant.PRIMARY,
            icon = Icons.Filled.Add,
            onClick = onAddWebsite,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WebStat(value: String, label: String) {
    val colors = LocalScColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = colors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = colors.TextSecondary, fontSize = 11.sp)
    }
}


