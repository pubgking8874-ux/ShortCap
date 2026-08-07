package com.shortscap.app.screens.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScButton
import com.shortscap.app.components.ScButtonVariant
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.web.WebRule
import com.shortscap.app.web.WebRuleStatus

/**
 * Blocked Websites — dedicated full page for the website block list.
 *
 * Every blocked website shows its icon, name and domain with a single
 * Unblock action (the rule is never deleted — only its state changes);
 * search and Add Website are included. The list is fed from the ViewModel
 * (WebRepository), never hardcoded, and an empty state keeps the page
 * polished when there is nothing to show.
 */
@Composable
fun WebBlockedScreen(
    rules: List<WebRule>,
    existingDomains: Set<String>,
    onUnblock: (WebRule) -> Unit,
    onAdd: (domain: String, status: WebRuleStatus) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        WebSubScreenTopBar(title = strings.webBlockedTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WebSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = strings.webSearchBlocked,
            )

            val filtered = rules.filter {
                it.displayName.contains(query, ignoreCase = true) || it.domain.contains(query, ignoreCase = true)
            }
            ScCard(modifier = Modifier.fillMaxWidth()) {
                when {
                    rules.isEmpty() -> ScEmptyState(
                        iconKey = IconKey.WEB_BLOCKED,
                        title = strings.webEmptyBlockedTitle,
                        subtitle = strings.webEmptyBlockedDesc,
                    )
                    filtered.isEmpty() -> ScEmptyState(
                        iconKey = IconKey.WEB_BLOCKED,
                        title = strings.webNoSearchResults,
                        subtitle = strings.webEmptyBlockedDesc,
                    )
                    else -> filtered.forEachIndexed { index, rule ->
                        WebRuleRow(
                            rule = rule,
                            primaryLabel = strings.webUnblock,
                            primaryTint = colors.Accent,
                            onPrimary = { onUnblock(rule) },
                            statusLabel = strings.webBlocked,
                        )
                        if (index < filtered.size - 1) ScDivider(modifier = Modifier.padding(start = 62.dp))
                    }
                }
            }

            ScButton(
                label = strings.webAddWebsite,
                variant = ScButtonVariant.PRIMARY,
                icon = Icons.Filled.Add,
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showAdd) {
        AddWebsiteDialog(
            defaultStatus = WebRuleStatus.BLOCKED,
            existingDomains = existingDomains,
            onDismiss = { showAdd = false },
            onAdd = { domain, status ->
                onAdd(domain, status)
                showAdd = false
            },
        )
    }

}
