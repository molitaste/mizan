@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mizan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.Temp
import com.mizan.app.core.Version
import com.mizan.app.data.Store

@Composable
fun HistoryScreen(id: String, onBack: () -> Unit, onOpenRecipe: (String) -> Unit, onExportVersion: (String) -> Unit) {
    val s = str
    val p = pal
    val ctx = LocalContext.current
    val recipes by Store.recipes.collectAsState()
    val r = recipes.find { it.id == id }
    var viewing by remember { mutableStateOf<Version?>(null) }
    var restoring by remember { mutableStateOf<Version?>(null) }
    if (r == null) { Column(Modifier.fillMaxSize().background(p.bg)) { HeaderBar(s.versionHistory, onBack) }; return }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(r.current.title.ifBlank { s.untitled }, onBack)
        ContentColumn(Modifier.weight(1f).navigationBarsPadding()) {
            SectionTitle(Icons.Filled.History, s.versionHistory)
            MCard(Modifier.fillMaxWidth(), warn = r.hasUnsavedChanges) {
                Text(s.current, style = MaterialTheme.typography.titleMedium)
                Text(summaryOf(r.current), color = p.muted)
                if (r.hasUnsavedChanges) Text(s.unsaved, fontWeight = FontWeight.Bold)
                Text(s.draftAutosaved + " · " + fmtDate(r.updatedAt), color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            r.versions.sortedByDescending { it.number }.forEach { v ->
                MCard(Modifier.fillMaxWidth()) {
                    Row {
                        Text(s.version(v.number), style = MaterialTheme.typography.titleMedium, color = p.primary)
                        if (v.label.isNotBlank()) Text("  ·  " + v.label, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(fmtDate(v.at), color = p.muted, style = MaterialTheme.typography.bodySmall)
                    Text(summaryOf(v.content), color = p.muted)
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton(s.view, Icons.Filled.Visibility) { viewing = v }
                        OutlineButton(s.restore, Icons.Filled.Restore) { restoring = v }
                        OutlineButton(s.duplicate, Icons.Filled.ContentCopy) {
                            val title = r.current.title.ifBlank { s.untitled }
                            val n = Store.duplicate(v.content, (v.content.title.ifBlank { title }) + " " + s.copySuffix, s.duplicatedFrom(title, v.number))
                            onOpenRecipe(n.id)
                        }
                        OutlineButton(s.export, Icons.Filled.Share) { onExportVersion(v.id) }
                    }
                }
            }
        }
    }

    restoring?.let { v ->
        ConfirmDialog(s.restoreConfirm(v.number), s.restore, onConfirm = {
            Store.restore(id, v.id, s.restoredFrom(v.number))?.let { toast(ctx, s.versionSaved(it.number)) }
        }, onDismiss = { restoring = null })
    }
    viewing?.let { v -> VersionViewer(v, onDismiss = { viewing = null }) }
}

@Composable
fun summaryOf(c: RecipeContent): String =
    str.summary(c.ingredients.size, c.steps.size, c.steps.sumOf { it.photos.size }, c.notes.size)

@Composable
private fun VersionViewer(v: Version, onDismiss: () -> Unit) {
    val s = str
    val p = pal
    val c = v.content
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 760.dp).padding(16.dp),
        title = { Text("${c.title.ifBlank { s.untitled }} · v${v.number}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.ingredientsHeader, style = MaterialTheme.typography.titleSmall)
                c.ingredients.forEach { i ->
                    Row { Text(i.name, Modifier.weight(1f)); Text(amountText(i.amount, i.unit, s.ar), fontWeight = FontWeight.SemiBold) }
                }
                HorizontalDivider(color = p.border)
                Text(s.steps, style = MaterialTheme.typography.titleSmall)
                c.steps.forEachIndexed { k, st ->
                    Text("${k + 1}. " + (if (st.title.isNotBlank()) st.title + ": " else "") + st.text +
                        (st.tempC?.let { "  [" + Temp.both(it) + "]" } ?: ""))
                    if (st.photos.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        st.photos.forEach { ph ->
                            Column(Modifier.width(120.dp)) {
                                StoredImage(mediaFile(ph.file), Modifier.size(120.dp, 90.dp).clip(RoundedCornerShape(8.dp)))
                                if (ph.caption.isNotBlank()) Text(ph.caption, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
                if (c.notes.isNotEmpty()) {
                    HorizontalDivider(color = p.border)
                    Text(s.notes, style = MaterialTheme.typography.titleSmall)
                    c.notes.forEach { n -> Text("${fmtDate(n.at)} · ${s.kind(n.kind)}: ${n.text}") }
                }
                Spacer(Modifier.heightIn(min = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
    )
}
