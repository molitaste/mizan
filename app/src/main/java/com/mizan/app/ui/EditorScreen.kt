@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mizan.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mizan.app.Session
import com.mizan.app.core.Ingredient
import com.mizan.app.core.Num
import com.mizan.app.core.PctBasis
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.Scaling
import com.mizan.app.core.Temp
import com.mizan.app.core.Units
import com.mizan.app.data.Media
import com.mizan.app.data.Settings
import com.mizan.app.data.Store

@Composable
fun EditorScreen(
    id: String,
    startInEdit: Boolean,
    onBack: () -> Unit,
    onPrep: () -> Unit,
    onNotes: () -> Unit,
    onHistory: () -> Unit,
    onExport: (String) -> Unit,
) {
    val s = str
    val p = pal
    val ctx = LocalContext.current
    val recipes by Store.recipes.collectAsState()
    val settings by Settings.state.collectAsState()
    val r = recipes.find { it.id == id }
    if (r == null) {
        Column(Modifier.fillMaxSize().background(p.bg)) { HeaderBar(s.ingredientEditor, onBack) }
        return
    }
    val c = r.current
    var edit by rememberSaveable { mutableStateOf(startInEdit) }
    var saveDialog by remember { mutableStateOf(false) }
    var convert by remember { mutableStateOf(false) }
    val factor = Session.factor(id)
    fun setFactor(f: Double?) { if (Scaling.validFactor(f)) Session.factors[id] = f!! }
    fun update(f: (RecipeContent) -> RecipeContent) = Store.updateCurrent(id, f)

    val totals = Scaling.totals(c.ingredients)
    val pct = Scaling.percentages(c)
    val cover = coverOf(c)
    val coverCam = rememberCamera { name -> update { it.copy(coverPhoto = name) } }
    val coverPick = rememberGallery(multiple = false) { names -> names.firstOrNull()?.let { n -> update { it.copy(coverPhoto = n) } } }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(s.ingredientEditor, onBack) {
            if (r.hasUnsavedChanges && r.versions.isNotEmpty()) Text(s.unsaved, color = p.onHeader, style = MaterialTheme.typography.bodySmall)
        }
        BoxWithConstraints(Modifier.weight(1f).imePadding()) {
            val wide = maxWidth >= 720.dp
            val narrow = maxWidth < 560.dp
            ContentColumn(Modifier.fillMaxSize()) {
                // ---------------- recipe header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(if (wide) 120.dp else 84.dp).clip(RoundedCornerShape(14.dp))
                            .border(if (p.eink) 2.dp else 0.dp, p.border, RoundedCornerShape(14.dp))
                            .clickable(enabled = edit) { coverPick() },
                    ) {
                        if (cover != null) StoredImage(mediaFile(cover), Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize().background(if (p.eink) Color.White else p.primarySoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Restaurant, null, tint = p.primary, modifier = Modifier.size(40.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title.ifBlank { s.untitled }, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c.category.isNotBlank()) {
                                Text(c.category, color = p.primary, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.primarySoft)
                                        .border(if (p.eink) 1.dp else 0.dp, p.border, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            r.latest?.let { Text("v${it.number}", color = p.muted) }
                        }
                    }
                    if (wide) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineButton(s.history, Icons.Filled.History, onClick = onHistory)
                            OutlineButton(s.pdf, Icons.Filled.PictureAsPdf) { onExport("pdf") }
                            OutlineButton("Excel", Icons.Filled.TableChart) { onExport("xlsx") }
                        }
                        AccentButton(s.saveVersion, Icons.Filled.Save) { saveDialog = true }
                    }
                }
                if (!wide) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton(s.saveVersion, Icons.Filled.Save) { saveDialog = true }
                    OutlineButton(s.history, Icons.Filled.History, onClick = onHistory)
                    OutlineButton(s.pdf, Icons.Filled.PictureAsPdf) { onExport("pdf") }
                    OutlineButton("Excel", Icons.Filled.TableChart) { onExport("xlsx") }
                }

                // ---------------- mode switch
                ModeTabs(edit) { edit = it }

                if (edit) {
                    MCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(c.title, { v -> update { it.copy(title = v) } }, label = { Text(s.title) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CategoryField(c.category, { v -> update { it.copy(category = v) } }, Modifier.weight(1f))
                                OutlinedTextField(c.yieldText, { v -> update { it.copy(yieldText = v) } }, label = { Text(s.yieldText) },
                                    modifier = Modifier.weight(1f), singleLine = true)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(s.coverPhoto, fontWeight = FontWeight.SemiBold)
                                SmallIconButton(Icons.Filled.PhotoCamera, s.takePhoto, coverCam)
                                SmallIconButton(Icons.Filled.Image, s.choosePhoto, coverPick)
                                if (c.coverPhoto != null) SmallIconButton(Icons.Filled.Delete, s.remove, { update { it.copy(coverPhoto = null) } })
                                Spacer(Modifier.width(12.dp))
                                OutlineButton(s.convertUnits, Icons.Filled.SwapHoriz) { convert = true }
                            }
                        }
                    }
                } else {
                    Text("⚖ " + s.scaleHint, style = MaterialTheme.typography.titleSmall, color = p.primary)
                    ScalePanel(c, factor, totals, ::setFactor, wide, onMakeBaseline = {
                        val f = factor
                        update { Scaling.applyFactor(it, f) }
                        Session.factors[id] = 1.0
                        edit = true
                    })
                }

                // ---------------- ingredient table
                IngredientTable(
                    c = c, edit = edit, factor = factor, pct = pct, totals = totals,
                    showOriginals = settings.showOriginals, wide = wide, narrow = narrow,
                    onFactor = ::setFactor, onUpdate = ::update,
                )

                if (edit) PctBasisCard(c, ::update)

                if (r.sources.isNotEmpty()) {
                    MCard {
                        Text(s.sourceFiles, style = MaterialTheme.typography.titleSmall)
                        r.sources.forEach { src ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                                try {
                                    val f = Store.source(src.file)
                                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Media.uriFor(ctx, f), src.mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    ctx.startActivity(i)
                                } catch (_: Exception) { toast(ctx, s.ocrFailed) }
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (src.mime.contains("pdf")) Icons.Filled.PictureAsPdf else Icons.Filled.Image, null, tint = p.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(src.name.ifBlank { src.file }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(s.viewOriginal, color = p.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.heightIn(min = 8.dp))
            }
        }
        // ---------------- bottom actions
        Row(
            Modifier.fillMaxWidth().background(p.surface).border(if (p.eink) 2.dp else 0.dp, p.border).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentButton(s.prepMethod, Icons.Filled.Kitchen, Modifier.weight(1f), onClick = onPrep)
            Row(
                Modifier.weight(1f).heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp))
                    .border(if (p.eink) 2.dp else 1.5.dp, if (p.eink) Color.Black else p.primary, RoundedCornerShape(12.dp))
                    .background(p.surface).clickable(onClick = onNotes).padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Notes, null, tint = p.primary)
                Spacer(Modifier.width(8.dp))
                Text("${s.notes} (${c.notes.size})", color = p.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                MicButton(accent = true, onText = { t ->
                    update { it.copy(notes = it.notes + com.mizan.app.core.Note(text = t)) }
                    toast(ctx, s.saved)
                })
            }
        }
    }

    if (saveDialog) {
        TextPromptDialog(s.saveVersion, s.versionLabel, "", s.save, onConfirm = { label ->
            Store.saveVersion(id, label)?.let { toast(ctx, s.versionSaved(it.number)) }
        }, onDismiss = { saveDialog = false })
    }
    if (convert) ConvertDialog(c, onApply = { nc -> update { nc } }, onDismiss = { convert = false })
}

@Composable
private fun ModeTabs(edit: Boolean, onEdit: (Boolean) -> Unit) {
    val s = str
    val p = pal
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(if (p.eink) 2.dp else 1.dp, p.border, RoundedCornerShape(14.dp))
            .background(p.surface).padding(4.dp),
    ) {
        listOf(false to (Icons.Filled.Balance to s.scaleBatch), true to (Icons.Filled.Edit to s.editFormula)).forEach { (mode, pair) ->
            val on = edit == mode
            Row(
                Modifier.weight(1f).heightIn(min = 54.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (on) p.header.takeUnless { p.eink } ?: Color.Black else Color.Transparent)
                    .clickable { onEdit(mode) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(pair.first, null, tint = if (on) Color.White else p.text)
                Spacer(Modifier.width(8.dp))
                Text((if (on && p.eink) "● " else "") + pair.second, color = if (on) Color.White else p.text,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ScalePanel(c: RecipeContent, factor: Double, totals: Scaling.Totals, setFactor: (Double?) -> Unit, wide: Boolean, onMakeBaseline: () -> Unit) {
    val s = str
    val p = pal
    val maxTemp = c.steps.mapNotNull { it.tempC }.maxOrNull()
    var targetError by remember { mutableStateOf(false) }
    var multError by remember { mutableStateOf(false) }

    @Composable
    fun multiplierCard(m: Modifier) = MCard(m) {
        Text(s.multiplier, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallIconButton(Icons.Filled.Remove, "-", { setFactor(((factor * 4).let { kotlin.math.ceil(it - 1e-9) } - 1).coerceAtLeast(1.0) / 4) })
            NumberField(factor, { v -> multError = !Scaling.validFactor(v); setFactor(v) }, Modifier.weight(1f).padding(horizontal = 8.dp),
                error = multError, suffix = "×", decimals = 4)
            SmallIconButton(Icons.Filled.Add, "+", { setFactor((kotlin.math.floor(factor * 4 + 1e-9) + 1) / 4) })
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.5, 1.0, 1.5, 2.0, 3.0, 5.0).forEach { f -> Chip("${Num.fmt(f)}×", Num.approxEq(f, factor)) { setFactor(f) } }
        }
        if (multError) Text(s.invalidNumber, color = p.warnBorder, style = MaterialTheme.typography.bodySmall)
    }

    @Composable
    fun totalCard(m: Modifier) = MCard(m) {
        Text(s.totalBatch, fontWeight = FontWeight.SemiBold)
        val t = totals.totalG
        if (t != null && t > 0) {
            NumberField(t * factor, { v ->
                val f = Scaling.factorFromTotal(c.ingredients, v)
                targetError = f == null
                setFactor(f)
            }, Modifier.fillMaxWidth(), label = s.targetTotal, suffix = "g", error = targetError)
            if (targetError) Text(s.invalidNumber, color = p.warnBorder, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("—", style = MaterialTheme.typography.headlineMedium)
            Text(s.totalUnavailable, color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    fun tempCard(m: Modifier) = MCard(m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Thermostat, null, tint = if (p.eink) Color.Black else p.accent)
            Text(s.temperature, fontWeight = FontWeight.SemiBold)
        }
        if (maxTemp != null) Text(Temp.both(maxTemp), style = MaterialTheme.typography.headlineSmall)
        Text(s.scaleNote, style = MaterialTheme.typography.bodySmall, color = p.muted)
    }

    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        multiplierCard(Modifier.weight(1f)); totalCard(Modifier.weight(1f)); tempCard(Modifier.weight(1f))
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        multiplierCard(Modifier.fillMaxWidth()); totalCard(Modifier.fillMaxWidth()); tempCard(Modifier.fillMaxWidth())
    }
    if (!Num.approxEq(factor, 1.0)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton(s.resetScale, null) { setFactor(1.0) }
            OutlineButton(s.makeBaseline, Icons.Filled.Check, onClick = onMakeBaseline)
        }
    }
}

@Composable
private fun IngredientTable(
    c: RecipeContent,
    edit: Boolean,
    factor: Double,
    pct: Map<String, Double>,
    totals: Scaling.Totals,
    showOriginals: Boolean,
    wide: Boolean,
    narrow: Boolean = false,
    onFactor: (Double?) -> Unit,
    onUpdate: ((RecipeContent) -> RecipeContent) -> Unit,
) {
    val s = str
    val p = pal
    val basisName = if (c.pctBasis == PctBasis.INGREDIENT) c.ingredients.find { it.id == c.pctBaseIngredientId }?.name else null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(if (p.eink) 2.dp else 1.dp, p.border, RoundedCornerShape(14.dp)).background(p.surface),
    ) {
        // header
        Row(
            Modifier.fillMaxWidth().background(if (p.eink) Color.White else p.header)
                .let { if (p.eink) it.border(2.dp, Color.Black) else it }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hc = if (p.eink) Color.Black else Color.White
            val cw = colWidths(wide, edit)
            Text(s.ingredient, color = hc, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(s.amount, color = hc, fontWeight = FontWeight.Bold, modifier = Modifier.width(cw.amount + cw.unit + 6.dp), textAlign = TextAlign.Center)
            Text(if (basisName != null) "%" else s.percentage + " (%)", color = hc, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(cw.pct), textAlign = TextAlign.End)
        }
        if (c.ingredients.isEmpty() && !edit) Text("—", Modifier.padding(16.dp))
        c.ingredients.forEachIndexed { idx, ing ->
            IngredientRow(
                ing = ing, index = idx, count = c.ingredients.size, edit = edit, factor = factor, pct = pct[ing.id],
                showOriginals = showOriginals, wide = wide, narrow = narrow, onFactor = onFactor,
                onChange = { ni -> onUpdate { cc -> cc.copy(ingredients = cc.ingredients.map { if (it.id == ing.id) ni else it }) } },
                onMove = { d ->
                    onUpdate { cc ->
                        val l = cc.ingredients.toMutableList()
                        val j = idx + d
                        if (j in l.indices) { val t = l[idx]; l[idx] = l[j]; l[j] = t }
                        cc.copy(ingredients = l)
                    }
                },
                onDelete = { onUpdate { cc -> cc.copy(ingredients = cc.ingredients.filter { it.id != ing.id }) } },
            )
        }
        if (edit) {
            Box(Modifier.padding(12.dp)) {
                OutlineButton(s.addIngredient, Icons.Filled.Add, Modifier.fillMaxWidth()) {
                    onUpdate { it.copy(ingredients = it.ingredients + Ingredient(unit = it.ingredients.lastOrNull()?.unit ?: "g")) }
                }
            }
        }
        // total row
        Row(
            Modifier.fillMaxWidth().background(if (p.eink) Color.White else p.primarySoft)
                .let { if (p.eink) it.border(2.dp, Color.Black) else it }.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cw = colWidths(wide, edit)
            Text(s.totalBatch, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val t = totals.totalG
            Text(if (t != null) "${Num.fmt(t * (if (edit) 1.0 else factor))} g" else "—", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(cw.amount + cw.unit + 6.dp), textAlign = TextAlign.Center, maxLines = 1)
            Text(if (t != null && c.pctBasis == PctBasis.TOTAL) "100%" else "", fontWeight = FontWeight.Bold,
                modifier = Modifier.width(cw.pct), textAlign = TextAlign.End)
        }
        // basis label / missing weights
        val basisLabel = if (basisName != null) s.pctOf(basisName) else s.pctOfTotal
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(basisLabel, style = MaterialTheme.typography.bodySmall, color = p.muted)
            if (totals.missing.isNotEmpty() && c.ingredients.isNotEmpty()) {
                Text(s.pctUnavailable + " " + totals.missing.joinToString("، ".takeIf { s.ar } ?: ", ") { it.name.ifBlank { "?" } },
                    style = MaterialTheme.typography.bodySmall, color = p.muted)
                Text(s.densityInfo, style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
    }
}

@Composable
private fun IngredientRow(
    ing: Ingredient,
    index: Int,
    count: Int,
    edit: Boolean,
    factor: Double,
    pct: Double?,
    showOriginals: Boolean,
    wide: Boolean,
    narrow: Boolean,
    onFactor: (Double?) -> Unit,
    onChange: (Ingredient) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val s = str
    val p = pal
    var more by remember { mutableStateOf(false) }
    var rowError by remember { mutableStateOf<String?>(null) }
    val u = Units.get(ing.unit)
    val warn = ing.uncertain || u.ambiguous
    Column(
        Modifier.fillMaxWidth()
            .background(if (warn) p.warnBg else Color.Transparent)
            .let { if (warn) it.border(2.dp, p.warnBorder) else it }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val cw = colWidths(wide, edit)
        val nameCell: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                if (edit) {
                    OutlinedTextField(ing.name, { onChange(ing.copy(name = it)) }, singleLine = false, maxLines = 3,
                        placeholder = { Text(s.ingredientName) }, modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                        textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(10.dp))
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, null, tint = p.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(ing.name.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, maxLines = 4, modifier = Modifier.padding(end = 6.dp))
                }
            }
        }
        val amountCell: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (edit) {
                    AmountBox(ing.amount, { v ->
                        rowError = if (v == null || v < 0) s.invalidNumber else null
                        if (v != null && v >= 0) onChange(ing.copy(amount = v))
                    }, Modifier.width(cw.amount), error = rowError != null)
                    Spacer(Modifier.width(6.dp))
                    UnitPicker(ing.unit, { onChange(ing.copy(unit = it)) }, Modifier.width(cw.unit))
                } else {
                    val scaled = Scaling.scaled(ing.amount, factor)
                    AmountBox(scaled, { v ->
                        val f = Scaling.factorFromRow(ing.amount, v)
                        rowError = when {
                            ing.amount == null || ing.amount <= 0 -> s.baselineZero
                            f == null -> s.invalidNumber
                            else -> null
                        }
                        if (f != null) onFactor(f)
                    }, Modifier.width(cw.amount), enabled = ing.amount != null && ing.amount > 0, error = rowError != null)
                    Spacer(Modifier.width(6.dp))
                    Text(Units.label(ing.unit, s.ar), modifier = Modifier.width(cw.unit), maxLines = 2,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val pctCell: @Composable () -> Unit = {
            Text(pct?.let { Num.pct(it) + "%" } ?: "—", modifier = Modifier.width(cw.pct), textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        if (narrow) {
            // phones: name on its own full-width line, numbers underneath
            nameCell(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                amountCell(); Spacer(Modifier.weight(1f)); pctCell()
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            nameCell(Modifier.weight(1f)); amountCell(); pctCell()
        }
        rowError?.let { Text(it, color = p.warnBorder, style = MaterialTheme.typography.bodySmall) }
        // badges / originals
        val o = ing.original
        val showOrig = showOriginals && o != null && o.amount != null && (o.unit != ing.unit || !Num.approxEq(o.amount, ing.amount))
        if (warn || showOrig || ing.note.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (u.ambiguous) CheckBadge(s.ambiguousUnit)
                else if (ing.amount == null) CheckBadge(s.noQuantity)
                else if (ing.uncertain) CheckBadge()
                if (showOrig) Text("${s.original}: ${amountText(o!!.amount, o.unit, s.ar)}", color = p.muted, style = MaterialTheme.typography.bodySmall)
                if (ing.note.isNotEmpty()) Text(ing.note, color = p.muted, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (ing.uncertain && !u.ambiguous && ing.amount != null) {
                    Text(s.confirmed, color = p.primary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChange(ing.copy(uncertain = false)) }.padding(8.dp))
                }
            }
        }
        if (edit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, s.moveUp) }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) { Icon(Icons.Filled.KeyboardArrowDown, s.moveDown) }
                IconButton(onClick = { more = !more }) { Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) }
                Text(
                    listOfNotNull(
                        ing.densityGPerMl?.let { "${Num.fmt(it)} g/mL" },
                        ing.pieceWeightG?.let { "${Num.fmt(it)} g/pc" },
                    ).joinToString(" · "),
                    color = p.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, s.delete) }
            }
            if (more) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    NumberField(ing.densityGPerMl, { v -> onChange(ing.copy(densityGPerMl = v?.takeIf { it > 0 })) }, Modifier.weight(1f), label = s.density)
                    OutlinedTextField(ing.densitySource, { onChange(ing.copy(densitySource = it)) }, Modifier.weight(1f),
                        label = { Text(s.densitySource) }, singleLine = true)
                    NumberField(ing.pieceWeightG, { v -> onChange(ing.copy(pieceWeightG = v?.takeIf { it > 0 })) }, Modifier.weight(1f), label = s.pieceWeight)
                }
            }
        }
    }
}

@Composable
private fun PctBasisCard(c: RecipeContent, update: ((RecipeContent) -> RecipeContent) -> Unit) {
    val s = str
    var open by remember { mutableStateOf(false) }
    MCard {
        Text(s.pctBasis, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.heightIn(min = 8.dp))
        Segmented(listOf(PctBasis.TOTAL to s.pctTotalBasis, PctBasis.INGREDIENT to s.pctIngredientBasis), c.pctBasis, { b ->
            update { it.copy(pctBasis = b, pctBaseIngredientId = it.pctBaseIngredientId ?: it.ingredients.firstOrNull()?.id) }
        })
        if (c.pctBasis == PctBasis.INGREDIENT) {
            Box(Modifier.padding(top = 8.dp)) {
                OutlineButton(c.ingredients.find { it.id == c.pctBaseIngredientId }?.name?.ifBlank { "?" } ?: "?", null) { open = true }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    c.ingredients.forEach { i ->
                        DropdownMenuItem(text = { Text(i.name.ifBlank { "?" }) }, onClick = { open = false; update { it.copy(pctBaseIngredientId = i.id) } })
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val s = str
    var open by remember { mutableStateOf(false) }
    val options = (Store.categories() + s.categoriesDefault).distinct()
    Box(modifier) {
        OutlinedTextField(value, onChange, label = { Text(s.category) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Filled.ExpandMore, null) } })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onChange(o) }) }
        }
    }
}

/** Fixed column widths so numbers always fit; the ingredient name takes the rest of the row. */
private data class ColW(val amount: androidx.compose.ui.unit.Dp, val unit: androidx.compose.ui.unit.Dp, val pct: androidx.compose.ui.unit.Dp)

private fun colWidths(wide: Boolean, edit: Boolean) = ColW(
    amount = if (wide) 132.dp else 104.dp,
    unit = if (edit) (if (wide) 124.dp else 96.dp) else (if (wide) 72.dp else 54.dp),
    pct = if (wide) 84.dp else 64.dp,
)
