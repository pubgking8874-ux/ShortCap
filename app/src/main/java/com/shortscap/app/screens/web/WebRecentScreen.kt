package com.shortscap.app.screens.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScCard
import com.shortscap.app.components.ScDivider
import com.shortscap.app.components.ScEmptyState
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.web.WebRule
import com.shortscap.app.web.WebRuleStatus

/**
 * Recent Websites — recently added / managed website rules.
 *
 * Deliberately NO usage time here: this is only the recently added or
 * modified rules list, ordered by most recently updated. Each row shows the
 * website icon, name, domain and current status with a toggle (Block /
 * Unblock) and Delete action.
 */
@Composable
fun WebRecentScreen(
    rules: List<WebRule>,
    onToggleStatus: (WebRule) -> Unit,
    onDelete: (WebRule) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val strings = LocalAppStrings.current
    var pendingDelete by remember { mutableStateOf<WebRule?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WebSubScreenTopBar(title = strings.webRecentTitle, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScCard(modifier = Modifier.fillMaxWidth()) {
                if (rules.isEmpty()) {
                    ScEmptyState(
                        iconKey = IconKey.ACTIVITY,
                        title = strings.webEmptyRecentTitle,
                        subtitle = strings.webEmptyRecentDesc,
                    )
                } else {
                    rules.forEachIndexed { index, rule ->
                        val blocked = rule.status == WebRuleStatus.BLOCKED
                        WebRuleRow(
                            rule = rule,
                            primaryLabel = if (blocked) strings.webUnblock else strings.webBlockAction,
                            primaryTint = if (blocked) colors.Accent else colors.Danger,
                            onPrimary = { onToggleStatus(rule) },
                            onDelete = { pendingDelete = rule },
                            deleteLabel = strings.webDelete,
                            statusLabel = if (blocked) strings.webBlocked else strings.webAllowed,
                        )
                        if (index < rules.size - 1) ScDivider(modifier = Modifier.padding(start = 62.dp))
                    }
                }
            }
        }
    }

    pendingDelete?.let { rule ->
        WebRemoveConfirmDialog(
            domain = rule.domain,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDelete(rule)
                pendingDelete = null
            },
        )
    }
}
