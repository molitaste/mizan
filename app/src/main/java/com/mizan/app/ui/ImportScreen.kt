@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mizan.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mizan.app.core.ImportDraft
import com.mizan.app.core.RecipeParser
import com.mizan.app.core.SourceFile
import com.mizan.app.core.SrcLine
import com.mizan.app.data.Media
import com.mizan.app.data.Settings
import com.mizan.app.data.Store
import com.mizan.app.ocr.Ocr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Files picked for one import, and the parsed draft waiting for review. Kept in memory between the two screens. */
object ImportSession {
    val pages = mutableStateListOf<SourceFile>()
    var draft: ImportDraft? = null
    var meanConfidence: Int? = null
    var manual = false
    fun clear() { pages.clear(); draft = null; meanConfidence = null; manual = false }
}

@Composable
fun ImportScreen(mode: String, onBack: () -> Unit, onReview: () -> Unit) {
    val s = str
    val p = pal
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Settings.state.collectAsState()
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }

    val camera = rememberCamera(intoSources = true) { name ->
        ImportSession.pages += SourceFile(name, "image/jpeg", s.camera + " " + (ImportSession.pages.size + 1))
    }
    val gallery = rememberGallery(multiple = true, intoSources = true) { names ->
        names.forEach { ImportSession.pages += SourceFile(it, "image/jpeg", s.images + " " + (ImportSession.pages.size + 1)) }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            val name = Media.displayName(ctx, uri)
            Media.importRaw(ctx, uri, "pdf")?.let { ImportSession.pages += SourceFile(it, "application/pdf", name) }
        }
    }
    fun pickPdf() = pdfPicker.launch(arrayOf("application/pdf"))

    LaunchedEffect(Unit) {
        if (!autoStarted && ImportSession.pages.isEmpty()) {
            autoStarted = true
            when (mode) {
                "camera" -> camera()
                "images" -> gallery()
                "pdf" -> pickPdf()
            }
        }
    }

    fun read() {
        error = null
        scope.launch {
            val lines = mutableListOf<SrcLine>()
            val withText = mutableListOf<SourceFile>()
            var confSum = 0
            var confN = 0
            try {
                ImportSession.pages.toList().forEachIndexed { idx, src ->
                    val f = Store.source(src.file)
                    val pages = if (src.mime.contains("pdf")) {
                        Ocr.readPdf(ctx, f, settings.forcePdfOcr) { i, n -> busy = s.readingPage(i, n) }
                    } else {
                        busy = s.readingPage(idx + 1, ImportSession.pages.size)
                        listOf(Ocr.readImage(ctx, f))
                    }
                    pages.forEach { pt -> pt.meanConfidence?.let { confSum += it; confN++ } }
                    val pl = pages.flatMap { it.lines }
                    lines += pl
                    withText += src.copy(ocrText = pl.joinToString("\n") { it.text })
                }
                val draft = withContext(Dispatchers.Default) { RecipeParser.parse(lines) }
                ImportSession.pages.clear()
                ImportSession.pages.addAll(withText)
                ImportSession.draft = draft
                ImportSession.meanConfidence = if (confN > 0) confSum / confN else null
                busy = null
                if (lines.isEmpty()) toast(ctx, s.nothingRead)
                onReview()
            } catch (_: Ocr.MissingModels) {
                busy = null; error = s.ocrMissing
            } catch (e: Exception) {
                busy = null; error = s.ocrFailed + " " + (e.message ?: "")
            }
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(s.importTitle, onBack = { if (busy == null) { ImportSession.clear(); onBack() } })
        ContentColumn(Modifier.weight(1f)) {
            SectionTitle(Icons.Filled.CameraAlt, s.importTitle)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(if (ImportSession.pages.isEmpty()) s.camera else s.addPage, Icons.Filled.CameraAlt, enabled = busy == null, onClick = camera)
                OutlineButton(s.images, Icons.Filled.AddPhotoAlternate, enabled = busy == null, onClick = gallery)
                OutlineButton(s.pdf, Icons.Filled.PictureAsPdf, enabled = busy == null) { pickPdf() }
            }
            Text(s.ocrInfo, color = p.muted, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { Settings.update { it.copy(forcePdfOcr = !it.forcePdfOcr) } }) {
                Checkbox(settings.forcePdfOcr, { v -> Settings.update { it.copy(forcePdfOcr = v) } })
                Text(s.forceOcr)
            }
            if (ImportSession.pages.isNotEmpty()) {
                Text(s.pages + " (${ImportSession.pages.size})", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImportSession.pages.forEachIndexed { i, src ->
                        Column(Modifier.width(170.dp)) {
                            Box(Modifier.size(170.dp, 220.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)) {
                                PageThumb(src)
                                IconButton(onClick = { if (busy == null) ImportSession.pages.removeAt(i) }, modifier = Modifier.align(Alignment.TopEnd)
                                    .background(Color.White, RoundedCornerShape(20.dp))) { Icon(Icons.Filled.Close, s.remove, tint = Color.Black) }
                            }
                            Text("${i + 1}. ${src.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            error?.let { Text(it, color = p.warnBorder, fontWeight = FontWeight.Bold) }
            if (busy != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (!p.eink) CircularProgressIndicator(Modifier.size(28.dp)) else Text("⏳")
                Spacer(Modifier.width(12.dp))
                Text(busy ?: s.reading, style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.fillMaxWidth().background(p.surface).navigationBarsPadding().padding(16.dp, 10.dp)) {
            AccentButton(s.readRecipe, Icons.Filled.Search, Modifier.fillMaxWidth(),
                enabled = busy == null && ImportSession.pages.isNotEmpty()) { read() }
        }
    }
}

@Composable
private fun BoxScope.PageThumb(src: SourceFile) {
    val f = remember(src.file) { Store.source(src.file) }
    if (src.mime.contains("pdf")) {
        var bmp by remember(src.file) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(src.file) { bmp = withContext(Dispatchers.IO) { Ocr.pdfThumb(f) } }
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(48.dp)) }
        Text("PDF · ${remember(src.file) { Ocr.pdfPageCount(f) }}", Modifier.align(Alignment.BottomStart).background(Color.White).padding(4.dp), color = Color.Black)
    } else StoredImage(f, Modifier.fillMaxSize(), ContentScale.Fit)
}
