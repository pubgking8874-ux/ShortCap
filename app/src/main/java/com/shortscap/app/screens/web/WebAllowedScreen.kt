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
 * Allowed Websites — dedicated full page for the website allow list.
 *
 * Every allowed website shows its icon, name and domain with Block and
 * Remove actions, so a website can be switched between Allowed and Blocked
 * states right from here. Search and Add Website are included; data comes
 * from the ViewModel (WebRepository), never hardcoded.
 */
@Composable
fun WebAllowedScreen(
    rules: List<WebRule>,
    existingDomains: Set<String>,
    onBlock: (WebRule) -> Unit,
    onRemove: (WebRule) -> Unit,
    onAdd: (domain: String, status: WebRuleStatus) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<WebRule?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WebSubScreenTopBar(title = strings.webAllowedTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WebSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = strings.webSearchAllowed,
            )

            val filtered = rules.filter {
                it.displayName.contains(query, ignoreCase = true) || it.domain.contains(query, ignoreCase = true)
            }
            ScCard(modifier = Modifier.fillMaxWidth()) {
                when {
                    rules.isEmpty() -> ScEmptyState(
                        iconKey = IconKey.WEB_ALLOWED,
                        title = strings.webEmptyAllowedTitle,
                        subtitle = strings.webEmptyAllowedDesc,
                    )
                    filtered.isEmpty() -> ScEmptyState(
                        iconKey = IconKey.WEB_ALLOWED,
                        title = strings.webNoSearchResults,
                        subtitle = strings.webEmptyAllowedDesc,
                    )
                    else -> filtered.forEachIndexed { index, rule ->
                        WebRuleRow(
                            rule = rule,
                            primaryLabel = strings.webBlockAction,
                            primaryTint = colors.Danger,
                            onPrimary = { onBlock(rule) },
                            onDelete = { pendingRemove = rule },
                            deleteLabel = strings.webRemove,
                            statusLabel = strings.webAllowed,
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
            defaultStatus = WebRuleStatus.ALLOWED,
            existingDomains = existingDomains,
            onDismiss = { showAdd = false },
            onAdd = { domain, status ->
                onAdd(domain, status)
                showAdd = false
            },
        )
    }

    pendingRemove?.let { rule ->
        WebRemoveConfirmDialog(
            domain = rule.domain,
            onDismiss = { pendingRemove = null },
            onConfirm = {
                onRemove(rule)
                pendingRemove = null
            },
        )
    }
}
