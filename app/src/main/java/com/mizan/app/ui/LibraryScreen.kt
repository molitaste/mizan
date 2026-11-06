package com.mizan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mizan.app.core.Recipe
import com.mizan.app.core.RecipeContent
import com.mizan.app.data.Settings
import com.mizan.app.data.Store

private const val FAV = "\u0000fav"

fun coverOf(c: RecipeContent): String? = c.coverPhoto ?: c.steps.asReversed().firstNotNullOfOrNull { it.photos.firstOrNull()?.file }

@Composable
fun LanguageToggle() {
    val s by Settings.state.collectAsState()
    val p = pal
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).border(1.5.dp, if (p.eink) Color.Black else p.onHeader, RoundedCornerShape(20.dp)),
    ) {
        listOf(false to "English", true to "العربية").forEach { (ar, label) ->
            val on = s.arabic == ar
            Box(
                Modifier.heightIn(min = 40.dp).background(if (on) (if (p.eink) Color.Black else p.onHeader) else Color.Transparent)
                    .clickable { Settings.update { it.copy(arabic = ar) } }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) (if (p.eink) Color.White else p.header) else p.onHeader, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LibraryScreen(onOpen: (String) -> Unit, onImport: (String) -> Unit, onSettings: () -> Unit) {
    val s = str
    val p = pal
    val recipes by Store.recipes.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var grid by rememberSaveable { mutableStateOf(true) }
    var help by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Recipe?>(null) }

    fun newRecipe() {
        // typed recipes go through the same check-and-confirm list as scans
        ImportSession.clear()
        ImportSession.draft = com.mizan.app.core.ImportDraft()
        ImportSession.manual = true
        onImport("manual")
    }

    val cats = recipes.map { it.current.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val q = query.trim().lowercase()
    val shown = recipes.filter { r ->
        val c = r.current
        (filter.isEmpty() || (filter == FAV && r.favorite) || c.category.trim() == filter) &&
            (q.isEmpty() || c.title.lowercase().contains(q) || c.category.lowercase().contains(q) ||
                c.ingredients.any { it.name.lowercase().contains(q) })
    }.sortedWith(compareByDescending<Recipe> { it.favorite }.thenByDescending { it.updatedAt })

    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(
                    Modifier.width(196.dp).fillMaxHeight().background(p.header).statusBarsPadding().navigationBarsPadding()
                        .let { if (p.eink) it.border(2.dp, Color.Black) else it }
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.padding(6.dp, 4.dp, 6.dp, 18.dp)) { MizanMark(p.onHeader, if (p.eink) Color.Black else p.accent) }
                    RailItem(Icons.AutoMirrored.Filled.MenuBook, s.library, selected = true) { filter = "" }
                    RailItem(Icons.Filled.Add, s.newRecipe) { newRecipe() }
                    RailItem(Icons.Filled.CameraAlt, s.scanRecipe) { onImport("camera") }
                    RailItem(Icons.Filled.PictureAsPdf, s.importPdf) { onImport("pdf") }
                    RailItem(Icons.Filled.Star, s.favorites, selected = filter == FAV) { filter = if (filter == FAV) "" else FAV }
                    Spacer(Modifier.weight(1f))
                    RailItem(Icons.Filled.Settings, s.settings) { onSettings() }
                    RailItem(Icons.AutoMirrored.Filled.HelpOutline, s.help) { help = true }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // top bar
                Column(Modifier.background(if (wide) p.bg else p.header).statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!wide) MizanMark(p.onHeader, if (p.eink) Color.Black else p.accent)
                        Spacer(Modifier.weight(1f))
                        if (wide) {
                            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(p.header)) { LanguageToggle() }
                        } else LanguageToggle()
                        if (!wide) {
                            IconButton(onClick = { help = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, s.help, tint = p.onHeader) }
                            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, s.settings, tint = p.onHeader) }
                        }
                    }
                    if (!wide) Box(Modifier.fillMaxWidth().height(if (p.eink) 2.dp else 3.dp).background(if (p.eink) Color.Black else p.accent))
                }

                val cols = if (grid) GridCells.Adaptive(minSize = 210.dp) else GridCells.Fixed(1)
                LazyVerticalGrid(
                    columns = cols,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionTitle(Icons.AutoMirrored.Filled.MenuBook, s.library)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AccentButton(s.newRecipe, Icons.Filled.Add) { newRecipe() }
                                OutlineButton(s.scanPhoto, Icons.Filled.CameraAlt) { onImport("camera") }
                                OutlineButton(s.images, Icons.Filled.Restaurant) { onImport("images") }
                                OutlineButton(s.importPdf, Icons.Filled.PictureAsPdf) { onImport("pdf") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    placeholder = { Text(s.search) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                SmallIconButton(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, "", { grid = !grid })
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip(s.all, filter.isEmpty()) { filter = "" }
                                Chip("★ " + s.favorites, filter == FAV) { filter = if (filter == FAV) "" else FAV }
                                cats.forEach { c -> Chip(c, filter == c) { filter = if (filter == c) "" else c } }
                            }
                            if (shown.isEmpty()) {
                                Text(if (recipes.isEmpty()) s.noRecipes else s.noMatch, color = p.muted,
                                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 30.dp))
                            }
                        }
                    }
                    items(shown, key = { it.id }) { r ->
                        RecipeCard(r, grid, onOpen = { onOpen(r.id) }, onDelete = { toDelete = r })
                    }
                }
            }
        }
    }

    toDelete?.let { r ->
        ConfirmDialog(
            text = s.deleteConfirm(r.current.title.ifBlank { s.untitled }), confirm = s.delete,
            onConfirm = { Store.delete(r.id) }, onDismiss = { toDelete = null },
        )
    }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text(s.help) },
            text = { Text(s.helpText + "\n\n" + s.storageInfo, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { help = false }) { Text(s.ok) } },
        )
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val p = pal
    val bg = when {
        selected && p.eink -> Color.Black
        selected -> p.accent
        else -> Color.Transparent
    }
    val fg = if (selected) Color.White else p.onHeader
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = pal
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) (if (p.eink) Color.Black else p.accentSoft) else p.surface)
            .border(if (selected || p.eink) 2.dp else 1.dp, if (selected) p.accent else p.border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) (if (p.eink) Color.White else p.accent) else p.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RecipeCard(r: Recipe, grid: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val s = str
    val p = pal
    var menu by remember { mutableStateOf(false) }
    val c = r.current
    val cover = coverOf(c)
    MCard(Modifier.fillMaxWidth().clickable(onClick = onOpen), padding = 0.dp) {
        if (grid) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))) {
                if (cover != null) StoredImage(mediaFile(cover), Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(if (p.eink) Color.White else p.primarySoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Restaurant, null, tint = p.primary, modifier = Modifier.size(48.dp))
                }
            }
        }
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title.ifBlank { s.untitled }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(c.category.ifBlank { null }, r.latest?.let { "v${it.number}" }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            if (r.favorite) Icon(Icons.Filled.Star, s.favorite, tint = if (p.eink) Color.Black else p.accent)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (r.favorite) s.unfavorite else s.favorite) },
                        leadingIcon = { Icon(if (r.favorite) Icons.Filled.StarBorder else Icons.Filled.Star, null) },
                        onClick = { menu = false; Store.setFavorite(r.id, !r.favorite) },
                    )
                    DropdownMenuItem(text = { Text(s.duplicate) }, onClick = {
                        menu = false
                        Store.duplicate(c, (c.title.ifBlank { s.untitled }) + " " + s.copySuffix,
                            s.duplicatedFrom(c.title.ifBlank { s.untitled }, r.latest?.number ?: 0))
                    })
                    DropdownMenuItem(text = { Text(s.delete) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
