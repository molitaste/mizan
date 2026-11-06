package com.mizan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.Step
import com.mizan.app.core.StepPhoto
import com.mizan.app.core.Temp
import com.mizan.app.data.Settings
import com.mizan.app.data.Store

@Composable
fun PrepScreen(id: String, onBack: () -> Unit) {
    val s = str
    val p = pal
    val ctx = LocalContext.current
    val recipes by Store.recipes.collectAsState()
    val r = recipes.find { it.id == id }
    var saveDialog by remember { mutableStateOf(false) }
    var deleteIdx by remember { mutableStateOf(-1) }
    var viewer by remember { mutableStateOf<String?>(null) }
    if (r == null) { Column(Modifier.fillMaxSize().background(p.bg)) { HeaderBar(s.prepMethod, onBack) }; return }
    val c = r.current
    fun update(f: (RecipeContent) -> RecipeContent) = Store.updateCurrent(id, f)
    fun updateStep(stepId: String, f: (Step) -> Step) = update { cc -> cc.copy(steps = cc.steps.map { if (it.id == stepId) f(it) else it }) }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(c.title.ifBlank { s.untitled }, onBack) {
            r.latest?.let { Text("v${it.number}", color = p.onHeader, modifier = Modifier.padding(horizontal = 12.dp)) }
        }
        ContentColumn(Modifier.weight(1f).imePadding()) {
            SectionTitle(Icons.Filled.Kitchen, s.prepMethod)
            if (c.steps.isEmpty()) Text(s.noSteps, color = p.muted, style = MaterialTheme.typography.bodyLarge)
            c.steps.forEachIndexed { i, st ->
                StepCard(
                    step = st, number = i + 1, count = c.steps.size,
                    onChange = { f -> updateStep(st.id, f) },
                    onMove = { d ->
                        update { cc ->
                            val l = cc.steps.toMutableList(); val j = i + d
                            if (j in l.indices) { val t = l[i]; l[i] = l[j]; l[j] = t }
                            cc.copy(steps = l)
                        }
                    },
                    onMergeNext = {
                        update { cc ->
                            val l = cc.steps.toMutableList()
                            if (i + 1 < l.size) {
                                val a = l[i]; val b = l[i + 1]
                                l[i] = a.copy(
                                    text = (a.text.trimEnd() + " " + (if (b.title.isNotBlank()) b.title + ": " else "") + b.text).trim(),
                                    photos = a.photos + b.photos,
                                    minutes = a.minutes ?: b.minutes, tempC = a.tempC ?: b.tempC,
                                )
                                l.removeAt(i + 1)
                            }
                            cc.copy(steps = l)
                        }
                    },
                    onDelete = { deleteIdx = i },
                    onView = { viewer = it },
                )
            }
            // dashed-style Add Step
            Box(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp))
                    .border(2.dp, if (p.eink) Color.Black else p.primary, RoundedCornerShape(14.dp))
                    .clickable { update { it.copy(steps = it.steps + Step()) } },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = p.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(s.addStep, color = p.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(p.surface).border(if (p.eink) 2.dp else 0.dp, p.border).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlineButton(s.backToIngredients, Icons.AutoMirrored.Filled.ArrowBack, Modifier.weight(1f), onClick = onBack)
            AccentButton(s.saveVersion, Icons.Filled.Save, Modifier.weight(1f)) { saveDialog = true }
        }
    }

    if (saveDialog) TextPromptDialog(s.saveVersion, s.versionLabel, "", s.save, onConfirm = { label ->
        Store.saveVersion(id, label)?.let { toast(ctx, s.versionSaved(it.number)) }
    }, onDismiss = { saveDialog = false })
    if (deleteIdx >= 0) ConfirmDialog(s.deleteStep, s.delete, onConfirm = {
        val idx = deleteIdx
        update { cc -> cc.copy(steps = cc.steps.filterIndexed { k, _ -> k != idx }) }
    }, onDismiss = { deleteIdx = -1 })
    viewer?.let { name ->
        AlertDialog(
            onDismissRequest = { viewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(12.dp),
            text = { StoredImage(mediaFile(name), Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 700.dp), ContentScale.Fit) },
            confirmButton = { TextButton(onClick = { viewer = null }) { Text(s.close) } },
        )
    }
}

@Composable
private fun StepCard(
    step: Step,
    number: Int,
    count: Int,
    onChange: ((Step) -> Step) -> Unit,
    onMove: (Int) -> Unit,
    onMergeNext: () -> Unit,
    onDelete: () -> Unit,
    onView: (String) -> Unit,
) {
    val s = str
    val p = pal
    val settings by Settings.state.collectAsState()
    val f = settings.units.fahrenheit
    var replaceIdx by remember { mutableStateOf(-1) }
    val camera = rememberCamera { name -> onChange { it.copy(photos = it.photos + StepPhoto(name)) } }
    val gallery = rememberGallery { names -> onChange { st -> st.copy(photos = st.photos + names.map { StepPhoto(it) }) } }
    val replacer = rememberGallery(multiple = false) { names ->
        val idx = replaceIdx
        val n = names.firstOrNull()
        if (n != null && idx >= 0) onChange { st -> st.copy(photos = st.photos.mapIndexed { k, ph -> if (k == idx) ph.copy(file = n) else ph }) }
        replaceIdx = -1
    }

    MCard(Modifier.fillMaxWidth(), warn = step.uncertain) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(p.accent)
                    .border(if (p.eink) 2.dp else 0.dp, Color.Black, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("$number", color = p.onAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(step.title, { v -> onChange { it.copy(title = v) } }, Modifier.weight(1f),
                placeholder = { Text(s.stepTitle) }, singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = p.primary, fontWeight = FontWeight.Bold))
            if (step.uncertain) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clickable { onChange { it.copy(uncertain = false) } }) { CheckBadge() }
            }
        }
        Spacer(Modifier.heightIn(min = 8.dp))
        OutlinedTextField(step.text, { v -> onChange { it.copy(text = v) } }, Modifier.fillMaxWidth(),
            label = { Text(s.stepText) }, minLines = 3)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(step.minutes, { v -> onChange { it.copy(minutes = v?.takeIf { x -> x >= 0 }) } }, Modifier.width(150.dp), label = s.minutes)
            NumberField(step.tempC?.let { if (f) Temp.cToF(it) else it }, { v ->
                onChange { it.copy(tempC = v?.let { x -> if (f) Temp.fToC(x) else x }) }
            }, Modifier.width(150.dp), label = s.tempField, suffix = if (f) "°F" else "°C", decimals = 1)
            if (step.tempC != null) Text(Temp.both(step.tempC), color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
        // photos
        if (step.photos.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                step.photos.forEachIndexed { k, ph ->
                    Column(Modifier.width(200.dp)) {
                        Box(Modifier.size(200.dp, 150.dp).clip(RoundedCornerShape(10.dp)).clickable { onView(ph.file) }) {
                            StoredImage(mediaFile(ph.file), Modifier.fillMaxSize())
                        }
                        OutlinedTextField(ph.caption, { v ->
                            onChange { st -> st.copy(photos = st.photos.mapIndexed { j, x -> if (j == k) x.copy(caption = v) else x }) }
                        }, Modifier.fillMaxWidth(), placeholder = { Text(s.caption) }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall)
                        Row {
                            IconButton(onClick = { onChange { st -> st.copy(photos = st.photos.swap(k, k - 1)) } }, enabled = k > 0) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, s.moveUp)
                            }
                            IconButton(onClick = { onChange { st -> st.copy(photos = st.photos.swap(k, k + 1)) } }, enabled = k < step.photos.size - 1) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, s.moveDown)
                            }
                            IconButton(onClick = { replaceIdx = k; replacer() }) { Icon(Icons.Filled.SwapHoriz, s.replace) }
                            IconButton(onClick = { onChange { st -> st.copy(photos = st.photos.filterIndexed { j, _ -> j != k }) } }) {
                                Icon(Icons.Filled.Close, s.remove)
                            }
                        }
                    }
                }
            }
        }
        // actions
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallIconButton(Icons.Filled.PhotoCamera, s.takePhoto, camera)
            SmallIconButton(Icons.Filled.Image, s.choosePhoto, gallery)
            MicButton(onText = { t -> onChange { it.copy(text = (it.text.trimEnd() + " " + t).trim()) } })
            Spacer(Modifier.width(16.dp))
            SmallIconButton(Icons.Filled.KeyboardArrowUp, s.moveUp, { onMove(-1) }, enabled = number > 1)
            SmallIconButton(Icons.Filled.KeyboardArrowDown, s.moveDown, { onMove(1) }, enabled = number < count)
            SmallIconButton(Icons.Filled.CallMerge, s.splitHint, onMergeNext, enabled = number < count)
            SmallIconButton(Icons.Filled.Delete, s.delete, onDelete)
        }
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> {
    if (a !in indices || b !in indices) return this
    val l = toMutableList(); val t = l[a]; l[a] = l[b]; l[b] = t; return l
}
