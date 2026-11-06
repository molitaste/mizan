package com.mizan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mizan.app.core.Converter
import com.mizan.app.core.Ingredient
import com.mizan.app.core.Note
import com.mizan.app.core.NoteKind
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.RecipeParser
import com.mizan.app.core.Step
import com.mizan.app.core.Units
import com.mizan.app.core.VolumeStd
import com.mizan.app.data.Settings
import com.mizan.app.data.Store

@Composable
fun ReviewScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val s = str
    val p = pal
    val settings by Settings.state.collectAsState()
    val d = ImportSession.draft
    if (d == null) { Column(Modifier.fillMaxSize().background(p.bg)) { HeaderBar(s.reviewTitle, onBack) }; return }

    var title by remember { mutableStateOf(d.title) }
    var titleWarn by remember { mutableStateOf(d.titleUncertain || d.title.isBlank()) }
    var category by remember { mutableStateOf("") }
    var ings by remember { mutableStateOf(d.ingredients) }
    var steps by remember { mutableStateOf(d.steps) }
    var notes by remember { mutableStateOf(d.notes) }
    var unassigned by remember { mutableStateOf(d.unassigned) }
    var std by remember { mutableStateOf<VolumeStd?>(null) }
    var convert by remember { mutableStateOf(settings.autoConvertImports) }
    var showRaw by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var askGenerate by remember { mutableStateOf(false) }
    val manual = ImportSession.manual

    fun built(): RecipeContent {
        var c = RecipeContent(
            title = title.trim(), category = category.trim(),
            ingredients = ings.map { i -> std?.let { Converter.applyStandard(i, it) } ?: i },
            steps = steps,
            notes = if (notes.isNotBlank()) listOf(Note(kind = NoteKind.GENERAL, text = notes.trim())) else emptyList(),
        )
        if (convert) c = Converter.content(c, settings.units).content
        return c
    }
    val hasAmbiguous = ings.any { Units.get(it.unit).ambiguous }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(s.reviewTitle, onBack)
        ContentColumn(Modifier.weight(1f).imePadding()) {
            SectionTitle(Icons.Filled.Check, s.reviewTitle)
            Text(s.reviewHint, style = MaterialTheme.typography.bodyLarge)
            ImportSession.meanConfidence?.let { Text("OCR ≈ $it%", color = p.muted) }

            MCard(warn = titleWarn) {
                OutlinedTextField(title, { title = it; titleWarn = false }, Modifier.fillMaxWidth(), label = { Text(s.title) },
                    singleLine = true, textStyle = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.heightIn(min = 8.dp))
                CategoryField(category, { category = it }, Modifier.fillMaxWidth())
            }

            // type / paste
            MCard {
                Text(s.typeOrPaste, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(pasteText, { pasteText = it }, Modifier.fillMaxWidth().padding(top = 6.dp),
                    placeholder = { Text(s.typeHint) }, minLines = if (manual) 6 else 3)
                PrimaryButton(s.readText, Icons.Filled.Check, Modifier.padding(top = 8.dp), enabled = pasteText.isNotBlank()) {
                    val pd = RecipeParser.parseText(pasteText)
                    if (title.isBlank() && pd.title.isNotBlank()) { title = pd.title; titleWarn = false }
                    ings = ings + pd.ingredients
                    steps = steps + pd.steps
                    if (pd.notes.isNotBlank()) notes = (notes + "\n" + pd.notes).trim()
                    unassigned = unassigned + pd.unassigned
                    pasteText = ""
                }
            }

            // ingredients – the list to check and confirm
            val previewPct = com.mizan.app.core.Scaling.percentages(RecipeContent(ingredients = ings.map { i -> std?.let { Converter.applyStandard(i, it) } ?: i }))
            val previewTotal = com.mizan.app.core.Scaling.totals(ings).totalG
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.ingredientsHeader + " (${ings.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (ings.any { it.uncertain }) TextButton(onClick = {
                    ings = ings.map { if (Units.get(it.unit).ambiguous || it.amount == null) it else it.copy(uncertain = false) }
                }) { Text("✓ " + s.confirmAll, fontWeight = FontWeight.Bold) }
            }
            if (ings.isEmpty()) Text(s.emptyList, color = p.muted)
            ings.forEachIndexed { i, ing ->
                ReviewIngredient(ing, i, ings.size, previewPct[ing.id],
                    onChange = { n -> ings = ings.map { if (it.id == ing.id) n else it } },
                    onMove = { dlt -> ings = ings.moved(i, i + dlt) },
                    onDelete = { ings = ings.filter { it.id != ing.id } })
            }
            OutlineButton(s.addIngredient, Icons.Filled.Add) { ings = ings + Ingredient(unit = "g") }
            if (previewTotal != null) Text(s.totalBatch + ": " + com.mizan.app.core.Num.fmt(previewTotal) + " g · 100%",
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            // units
            MCard(warn = hasAmbiguous && std == null) {
                if (hasAmbiguous) {
                    Text(s.sourceStandard, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.heightIn(min = 6.dp))
                    Segmented(listOf(null to s.stdUnknown, VolumeStd.US to s.stdUs, VolumeStd.IMPERIAL to s.stdImp, VolumeStd.METRIC to s.stdMetric), std, { std = it })
                    Spacer(Modifier.heightIn(min = 10.dp))
                }
                Segmented(listOf(false to s.keepSource, true to s.convertToMine), convert, { convert = it })
                if (convert) {
                    Spacer(Modifier.heightIn(min = 10.dp))
                    Text(s.preview, style = MaterialTheme.typography.titleSmall)
                    val before = RecipeContent(ingredients = ings.map { i -> std?.let { Converter.applyStandard(i, it) } ?: i }, steps = steps)
                    ConversionPreview(Converter.content(before, settings.units), before)
                }
                Text(s.densityInfo, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
            }


            // steps
            Text(s.steps + " (${steps.size})", style = MaterialTheme.typography.titleLarge)
            steps.forEachIndexed { i, st ->
                MCard(Modifier.fillMaxWidth(), warn = st.uncertain) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = p.accent)
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(st.title, { v -> steps = steps.map { if (it.id == st.id) it.copy(title = v) else it } },
                            Modifier.weight(1f), placeholder = { Text(s.stepTitle) }, singleLine = true)
                        if (st.uncertain) TextButton(onClick = { steps = steps.map { if (it.id == st.id) it.copy(uncertain = false) else it } }) {
                            Text("⚠ " + s.confirmed)
                        }
                    }
                    OutlinedTextField(st.text, { v -> steps = steps.map { if (it.id == st.id) it.copy(text = v) else it } },
                        Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 2)
                    Row {
                        IconButton(onClick = { steps = steps.moved(i, i - 1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, s.moveUp) }
                        IconButton(onClick = { steps = steps.moved(i, i + 1) }, enabled = i < steps.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, s.moveDown) }
                        IconButton(onClick = {
                            if (i + 1 < steps.size) {
                                val b = steps[i + 1]
                                val merged = st.copy(text = (st.text + " " + (if (b.title.isNotBlank()) b.title + ": " else "") + b.text).trim(),
                                    tempC = st.tempC ?: b.tempC, minutes = st.minutes ?: b.minutes)
                                steps = steps.filterIndexed { k, _ -> k != i + 1 }.map { if (it.id == st.id) merged else it }
                            }
                        }, enabled = i < steps.size - 1) { Icon(Icons.Filled.CallMerge, s.splitHint) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { steps = steps.filter { it.id != st.id } }) { Icon(Icons.Filled.Delete, s.delete) }
                    }
                }
            }
            OutlineButton(s.addStep, Icons.Filled.Add) { steps = steps + Step() }

            // notes
            Text(s.notes, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2)

            if (unassigned.isNotEmpty()) {
                MCard(warn = true) {
                    Text(s.unassigned, style = MaterialTheme.typography.titleSmall)
                    unassigned.forEachIndexed { k, line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(line, Modifier.weight(1f))
                            TextButton(onClick = {
                                ings = ings + RecipeParser.parseIngredient(line); unassigned = unassigned.filterIndexed { j, _ -> j != k }
                            }) { Text("+ " + s.ingredient) }
                            TextButton(onClick = {
                                steps = steps + Step(text = line); unassigned = unassigned.filterIndexed { j, _ -> j != k }
                            }) { Text("+ " + s.step(steps.size + 1)) }
                            IconButton(onClick = { unassigned = unassigned.filterIndexed { j, _ -> j != k } }) { Icon(Icons.Filled.Delete, s.remove) }
                        }
                    }
                }
            }

            // raw OCR text
            if (!manual) MCard {
                Row(Modifier.fillMaxWidth().clickable { showRaw = !showRaw }, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.rawText, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(if (showRaw) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                if (showRaw) {
                    if (d.lowConfidenceWords.isNotEmpty()) Text(s.lowConfidence + ": " + d.lowConfidenceWords.take(60).joinToString("  "),
                        color = p.warnBorder, style = MaterialTheme.typography.bodySmall)
                    Text(d.rawText.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(p.surface).navigationBarsPadding().padding(16.dp, 10.dp)) {
            AccentButton(s.generateRecipe, Icons.Filled.Check, Modifier.fillMaxWidth(), enabled = ings.isNotEmpty()) {
                val open = ings.count { it.uncertain || it.amount == null || Units.get(it.unit).ambiguous && std == null }
                if (open > 0) askGenerate = true else {
                    val r = Store.create(built(), ImportSession.pages.toList(), label = if (manual) s.createdLabel else s.importedLabel)
                    ImportSession.clear()
                    onCreated(r.id)
                }
            }
        }
    }
    if (askGenerate) {
        val open = ings.count { it.uncertain || it.amount == null || Units.get(it.unit).ambiguous && std == null }
        ConfirmDialog(s.unconfirmedWarn(open), s.generateRecipe, onConfirm = {
            val r = Store.create(built(), ImportSession.pages.toList(), label = if (manual) s.createdLabel else s.importedLabel)
            ImportSession.clear()
            onCreated(r.id)
        }, onDismiss = { askGenerate = false })
    }
}

@Composable
private fun ReviewIngredient(ing: Ingredient, index: Int, count: Int, pct: Double?, onChange: (Ingredient) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    val s = str
    val p = pal
    val amb = Units.get(ing.unit).ambiguous
    val warn = ing.uncertain || amb || ing.amount == null
    MCard(Modifier.fillMaxWidth(), warn = warn, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(ing.name, { onChange(ing.copy(name = it)) }, Modifier.weight(1.6f), singleLine = true,
                label = { Text(s.ingredientName) })
            NumberField(ing.amount, { v -> onChange(ing.copy(amount = v?.takeIf { it >= 0 })) }, Modifier.weight(0.9f), label = s.amount)
            UnitPicker(ing.unit, { onChange(ing.copy(unit = it)) }, Modifier.weight(0.9f))
            Text(pct?.let { com.mizan.app.core.Num.pct(it) + "%" } ?: "—", Modifier.weight(0.5f),
                style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                amb -> CheckBadge(s.ambiguousUnit)
                ing.amount == null -> CheckBadge(s.noQuantity)
                ing.uncertain -> CheckBadge()
            }
            ing.original?.text?.takeIf { it.isNotBlank() }?.let { Text("“$it”", color = p.muted, style = MaterialTheme.typography.bodySmall) }
            if (ing.note.isNotBlank()) Text(ing.note, color = p.muted, style = MaterialTheme.typography.bodySmall)
            if (ing.uncertain && !amb && ing.amount != null) TextButton(onClick = { onChange(ing.copy(uncertain = false)) }) { Text(s.confirmed) }
            IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, s.moveUp) }
            IconButton(onClick = { onMove(1) }, enabled = index < count - 1) { Icon(Icons.Filled.KeyboardArrowDown, s.moveDown) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, s.delete) }
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    val l = toMutableList(); val t = l.removeAt(from); l.add(to, t); return l
}
