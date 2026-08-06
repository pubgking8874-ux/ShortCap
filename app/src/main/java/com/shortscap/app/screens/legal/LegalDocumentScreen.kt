package com.shortscap.app.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shortscap.app.components.ScSubScreenTopBar
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScTextStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * The legal documents available in the Dashboard drawer.
 *
 * DEV-PHASE ONLY: each entry points at a local text asset checked into the
 * app. Later these will be replaced by backend-hosted HTML pages — swap the
 * [assetPath] source in [LegalDocumentLoader] without touching [LegalDocumentScreen]
 * or the drawer navigation.
 */
enum class LegalDocument(
    val title: String,
    val assetPath: String,
) {
    PRIVACY_POLICY("Privacy Policy", "privacy/PrivacyPolicy.txt"),
    TERMS_CONDITIONS("Terms & Conditions", "terms/TermsConditions.txt"),
}

/** Reads a [LegalDocument]'s local text asset; modular seam for a future backend fetch. */
object LegalDocumentLoader {
    suspend fun load(document: LegalDocument, app: android.content.Context): String =
        withContext(Dispatchers.IO) {
            app.assets.open(document.assetPath).use { input ->
                BufferedReader(input.reader()).use { it.readText() }
            }
        }
}

/**
 * Full-screen read-only legal document reader.
 *
 * Shows a back button in a compact top bar, then the entire (possibly long)
 * document in a vertically scrollable column with comfortable left/right
 * padding. Uses the active ShortsCap dark/light palette and existing
 * typography. The content is loaded once from the local asset and displayed
 * verbatim — no editing controls, no modification of the document.
 */
@Composable
fun LegalDocumentScreen(
    document: LegalDocument,
    onBack: () -> Unit,
) {
    val colors = LocalScColors.current
    val context = LocalContext.current
    var content by remember(document) { mutableStateOf<String?>(null) }

    LaunchedEffect(document) {
        content = LegalDocumentLoader.load(document, context.applicationContext)
    }

    val body = content ?: "Loading..."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Bg),
    ) {
        ScSubScreenTopBar(title = document.title, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = body,
                color = colors.TextSecondary,
                style = ScTextStyles.Body,
            )
        }
    }
}