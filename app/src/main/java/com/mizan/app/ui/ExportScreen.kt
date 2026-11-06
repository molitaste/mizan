package com.mizan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mizan.app.Session
import com.mizan.app.core.Num
import com.mizan.app.data.Settings
import com.mizan.app.data.Store
import com.mizan.app.export.ExportDoc
import com.mizan.app.export.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ExportScreen(id: String, versionId: String, preferKind: String, onBack: () -> Unit) {
    val s = str
    val p = pal
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Settings.state.collectAsState()
    val recipes by Store.recipes.collectAsState()
    val r = recipes.find { it.id == id }
    // "" = current draft at the current batch factor, otherwise a saved version id
    var source by rememberSaveable { mutableStateOf(versionId) }
    var color by rememberSaveable { mutableStateOf(!settings.eink) }
    var photos by rememberSaveable { mutableStateOf(true) }
    var docAr by rememberSaveable { mutableStateOf(settings.arabic) }
    var busy by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    var mime by remember { mutableStateOf("") }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = file
        if (uri != null && f != null) {
            runCatching { Exporter.copyTo(ctx, f, uri) }.onSuccess { toast(ctx, s.saved) }.onFailure { toast(ctx, s.exportFailed) }
        }
    }
    if (r == null) { Column(Modifier.fillMaxSize().background(p.bg)) { HeaderBar(s.exportTitle, onBack) }; return }
    val factor = Session.factor(id)

    fun doc(): ExportDoc {
        val v = r.versions.find { it.id == source }
        return if (v != null) ExportDoc(r, v.content, "v${v.number}" + (if (v.label.isNotBlank()) " · ${v.label}" else ""), v.number, 1.0, docAr, settings.units, settings.showOriginals)
        else {
            val latest = r.latest
            val label = if (latest != null && !r.hasUnsavedChanges) "v${latest.number}" + (if (latest.label.isNotBlank()) " · ${latest.label}" else "")
                else com.mizan.app.export.DocText(docAr).draft
            ExportDoc(r, r.current, label, if (r.hasUnsavedChanges) null else latest?.number, factor, docAr, settings.units, settings.showOriginals)
        }
    }

    fun run(kind: String) {
        busy = true; file = null
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching { if (kind == "pdf") Exporter.pdf(ctx, doc(), color, photos) else Exporter.xlsx(ctx, doc()) }.getOrNull()
            }
            busy = false
            if (f == null) toast(ctx, s.exportFailed) else {
                file = f
                mime = if (kind == "pdf") Exporter.MIME_PDF else Exporter.MIME_XLSX
            }
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(r.current.title.ifBlank { s.untitled }, onBack)
        ContentColumn(Modifier.weight(1f).navigationBarsPadding()) {
            SectionTitle(Icons.Filled.Share, s.exportTitle)
            MCard(Modifier.fillMaxWidth()) {
                Text(s.whatToExport, style = MaterialTheme.typography.titleSmall)
                Choice(source == "", s.currentScaled(Num.fmt(factor)) + (if (r.hasUnsavedChanges) " · " + s.unsaved else "")) { source = "" }
                r.versions.sortedByDescending { it.number }.forEach { v ->
                    Choice(source == v.id, s.version(v.number) + (if (v.label.isNotBlank()) " · ${v.label}" else "") + " · " + fmtDate(v.at) + " (1×)") { source = v.id }
                }
            }
            MCard(Modifier.fillMaxWidth()) {
                Text(s.docDirection, style = MaterialTheme.typography.titleSmall)
                Segmented(listOf(false to "English", true to "العربية"), docAr, { docAr = it })
                Spacer(Modifier.heightIn(min = 10.dp))
                Text(s.pdfOptions, style = MaterialTheme.typography.titleSmall)
                Segmented(listOf(true to s.colorPrint, false to s.bwPrint), color, { color = it })
                Row(Modifier.clickable { photos = !photos }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(photos, { photos = it }); Text(s.includePhotos)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val pdfFirst = preferKind != "xlsx"
                if (pdfFirst) {
                    AccentButton(s.makePdf, Icons.Filled.PictureAsPdf, Modifier.weight(1f), enabled = !busy) { run("pdf") }
                    OutlineButton(s.makeExcel, Icons.Filled.TableChart, Modifier.weight(1f), enabled = !busy) { run("xlsx") }
                } else {
                    AccentButton(s.makeExcel, Icons.Filled.TableChart, Modifier.weight(1f), enabled = !busy) { run("xlsx") }
                    OutlineButton(s.makePdf, Icons.Filled.PictureAsPdf, Modifier.weight(1f), enabled = !busy) { run("pdf") }
                }
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                if (!p.eink) CircularProgressIndicator(Modifier.size(26.dp)); Spacer(Modifier.width(10.dp)); Text(s.reading)
            }
            file?.let { f ->
                MCard(Modifier.fillMaxWidth()) {
                    Text(s.exportReady + ": " + f.name, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(s.share, Icons.Filled.Share) { Exporter.share(ctx, f, mime, s.share) }
                        OutlineButton(s.saveToDevice, Icons.Filled.Download) { saver.launch(f.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label)
    }
}
