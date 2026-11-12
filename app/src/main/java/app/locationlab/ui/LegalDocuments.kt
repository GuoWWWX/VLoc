package app.locationlab.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.locationlab.R
import java.io.IOException
import kotlinx.coroutines.launch

internal enum class LegalDocument(@StringRes val title: Int, val asset: String) {
    Terms(R.string.terms_title, "legal/TERMS.zh-CN.txt"),
    License(R.string.license_title, "VLoc-LICENSE.txt"),
}

@Composable
internal fun TermsConsentScreen(onAccept: suspend () -> Boolean, onExit: () -> Unit) {
    val terms = rememberLegalText(LegalDocument.Terms)
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 540.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.terms_title), style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary)
                    Text(stringResource(R.string.terms_intro), color = colors.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                        Text(
                            terms ?: stringResource(R.string.legal_document_unavailable),
                            Modifier.fillMaxWidth().padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (saveFailed) {
                    Text(stringResource(R.string.terms_save_failed), color = colors.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = terms != null && !saving,
                    onClick = {
                        saving = true
                        saveFailed = false
                        scope.launch {
                            try {
                                saveFailed = !onAccept()
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.terms_accept)) }
                TextButton(onClick = onExit, enabled = !saving, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.terms_exit))
                }
            }
        }
    }
}

@Composable
internal fun LegalDocumentDialog(document: LegalDocument, onClose: () -> Unit) {
    val text = rememberLegalText(document)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(document.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (document == LegalDocument.License) {
                    Text(stringResource(R.string.license_summary), color = MaterialTheme.colorScheme.onSurface)
                }
                Text(text ?: stringResource(R.string.legal_document_unavailable))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun rememberLegalText(document: LegalDocument): String? {
    val context = LocalContext.current
    return remember(context, document) {
        try {
            context.assets.open(document.asset).bufferedReader().use { it.readText().takeIf(String::isNotBlank) }
        } catch (_: IOException) {
            null
        }
    }
}
