package com.mizan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.mizan.app.core.Num
import com.mizan.app.core.Units
import com.mizan.app.data.Media
import com.mizan.app.data.Settings
import com.mizan.app.data.Store
import com.mizan.app.voice.Dictation
import com.mizan.app.voice.DictationError
import com.mizan.app.voice.DictationListener
import java.io.File

val pal: Palette @Composable get() = LocalPalette.current
val str: Str @Composable get() = LocalStr.current

fun toast(ctx: android.content.Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

// ------------------------------------------------------------------------------------------- header

@Composable
fun HeaderBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = pal
    Column(Modifier.background(p.header).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str.back, tint = p.onHeader)
                }
            } else Spacer(Modifier.width(8.dp))
            MizanMark(p.onHeader, if (p.eink) p.text else p.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                title, color = p.onHeader, style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            actions()
        }
        if (p.eink) Box(Modifier.fillMaxWidth().height(2.dp).background(Color.Black))
        else Box(Modifier.fillMaxWidth().height(3.dp).background(p.accent))
    }
}

/** Scale-balance mark + bilingual name. */
@Composable
fun MizanMark(textColor: Color, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Balance, contentDescription = null, tint = iconColor, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(6.dp))
        Text("MIZAN ميزان", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

// ------------------------------------------------------------------------------------------- buttons

@Composable
fun AccentButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = pal
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent,
            disabledContainerColor = if (p.eink) Color.White else p.border, disabledContentColor = p.muted),
        border = if (p.eink && !enabled) BorderStroke(1.dp, Color.Black) else null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = pal
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.primary, contentColor = p.onPrimary,
            disabledContainerColor = if (p.eink) Color.White else p.border, disabledContentColor = p.muted),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun OutlineButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = pal
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 54.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (p.eink) 2.dp else 1.5.dp, if (p.eink) Color.Black else p.primary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = p.surface, contentColor = p.primary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SmallIconButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val p = pal
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
            .border(if (p.eink) 2.dp else 1.5.dp, if (enabled) p.primary else p.border, RoundedCornerShape(10.dp))
            .background(p.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) p.primary else p.border, modifier = Modifier.size(26.dp)) }
}

// ------------------------------------------------------------------------------------------- containers

@Composable
fun MCard(modifier: Modifier = Modifier, warn: Boolean = false, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = pal
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (warn) p.warnBg else p.surface,
        border = BorderStroke(if (warn || p.eink) 2.dp else 1.dp, if (warn) p.warnBorder else p.border),
    ) { Column(Modifier.padding(padding), content = content) }
}

/** "Check" badge – text label, so meaning never depends on colour. */
@Composable
fun CheckBadge(text: String = str.needsCheck) {
    val p = pal
    Text(
        "⚠ $text", color = if (p.eink) Color.Black else p.warnBorder, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.border(1.5.dp, p.warnBorder, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val p = pal
    @Composable
    fun item(v: T, label: String, m: Modifier) {
        val on = v == selected
        Box(
            m.heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp))
                .background(if (on) p.primary else p.surface)
                .border(if (p.eink || on) 2.dp else 1.dp, if (on) p.primary else p.border, RoundedCornerShape(10.dp))
                .clickable { onSelect(v) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (if (on && p.eink) "● " else "") + label,
                color = if (on) p.onPrimary else p.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val minItem = 96.dp
        val fits = maxWidth >= minItem * options.size + 6.dp * (options.size - 1)
        if (fits) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (v, label) -> item(v, label, Modifier.weight(1f)) }
            }
        } else {
            // narrow screens (phones): wrap onto more lines instead of squeezing or scrolling sideways
            val perRow = (maxWidth / (minItem + 6.dp)).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.chunked(perRow).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { (v, label) -> item(v, label, Modifier.weight(1f)) }
                        repeat(perRow - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val p = pal
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).border(2.dp, if (p.eink) Color.Black else p.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (p.eink) Color.Black else p.accent, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.headlineSmall, color = p.primary)
    }
}

/** Centres content and caps its width on large tablets. */
@Composable
fun ContentColumn(modifier: Modifier = Modifier, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        val m = Modifier.widthIn(max = 1100.dp).fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = 16.dp, vertical = 16.dp)
        Column(m, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

// ------------------------------------------------------------------------------------------- inputs

/**
 * Numeric field that keeps what the user is typing (so "1." or "1 1/2" isn't reformatted mid-entry).
 * Calls [onValue] with the parsed value or null when invalid.
 */
@Composable
fun NumberField(
    value: Double?,
    onValue: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    error: Boolean = false,
    suffix: String? = null,
    decimals: Int? = null,
) {
    var text by remember { mutableStateOf(Num.fmt(value, decimals)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused) text = Num.fmt(value, decimals)
    }
    // numbers always read left-to-right, also in Arabic layouts
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = text,
            onValueChange = { t -> text = t; onValue(Num.parse(t)) },
            modifier = modifier.onFocusChanged { focused = it.isFocused },
            enabled = enabled,
            isError = error,
            singleLine = true,
            label = label?.let { { Text(it, maxLines = 1) } },
            suffix = suffix?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
            shape = RoundedCornerShape(10.dp),
        )
    }
}

/**
 * Compact amount box for ingredient tables: number centred, sized to fit, no label padding.
 * Keeps the text being typed (so "1." or "1 1/2" isn't reformatted mid-entry).
 */
@Composable
fun AmountBox(
    value: Double?,
    onValue: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: Boolean = false,
) {
    val p = pal
    var text by remember { mutableStateOf(Num.fmt(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = Num.fmt(value) }
    val borderColor = when {
        error -> p.warnBorder
        focused -> p.primary
        else -> p.border
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { t -> text = t; onValue(Num.parse(t)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                textAlign = TextAlign.Center, color = if (enabled) p.text else p.muted,
                fontFeatureSettings = "tnum",
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.primary),
            modifier = modifier.heightIn(min = 46.dp).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp))
                        .background(p.surface)
                        .border(if (focused || error || p.eink) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { inner() }
            },
        )
    }
}

@Composable
fun UnitPicker(code: String, onPick: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val s = str
    var open by remember { mutableStateOf(false) }
    val p = pal
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp))
                .border(if (p.eink) 2.dp else 1.dp, if (Units.get(code).ambiguous) p.warnBorder else p.border, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { open = true }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Units.label(code, s.ar), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (enabled) Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Units.pickable.forEach { c ->
                DropdownMenuItem(text = { Text(Units.label(c, s.ar)) }, onClick = { open = false; onPick(c) })
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- dialogs

@Composable
fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

@Composable
fun TextPromptDialog(title: String, label: String, initial: String = "", confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm(t) }) { Text(confirm, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

// ------------------------------------------------------------------------------------------- images

private class TwoTone : Transformation {
    override val cacheKey: String = "mizan-twotone"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = Media.twoTone(input)
}

private class Gray : Transformation {
    override val cacheKey: String = "mizan-gray"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = Media.grayscale(input)
}

/** Shows a stored photo. In E-Ink mode with strict two-tone, the preview is dithered to pure black & white. */
@Composable
fun StoredImage(file: File, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val settings by Settings.state.collectAsState()
    val p = pal
    val tf = when {
        p.eink && settings.einkTwoTone -> listOf<Transformation>(TwoTone())
        else -> emptyList()
    }
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(file).allowHardware(tf.isEmpty()).transformations(tf).crossfade(!p.eink).build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.background(if (p.eink) Color.White else p.primarySoft),
    )
}

@Composable
fun mediaFile(name: String): File = remember(name) { Store.media(name) }

// ------------------------------------------------------------------------------------------- camera / gallery

/** System camera into a persistent media file. No CAMERA permission is declared, so none is asked. */
@Composable
fun rememberCamera(intoSources: Boolean = false, onPhoto: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending
        pending = null
        if (ok && f != null && Media.normalize(f)) onPhoto(f.name) else f?.delete()
    }
    return {
        val f = if (intoSources) Media.newSourceFile("jpg") else Media.newMediaFile()
        pending = f
        try {
            launcher.launch(Media.uriFor(ctx, f))
        } catch (_: Exception) {
            pending = null
            toast(ctx, "No camera app")
        }
    }
}

@Composable
fun rememberGallery(multiple: Boolean = true, intoSources: Boolean = false, onPhotos: (List<String>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val many = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        onPhotos(uris.mapNotNull { Media.importImage(ctx, it, intoSources) })
    }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { Media.importImage(ctx, it, intoSources) }?.let { onPhotos(listOf(it)) }
    }
    return { if (multiple) many.launch("image/*") else one.launch("image/*") }
}

// ------------------------------------------------------------------------------------------- dictation

@Composable
fun MicButton(onText: (String) -> Unit, accent: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val p = pal
    if (accent) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(p.accent).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Mic, str.dictate, tint = p.onAccent) }
    } else SmallIconButton(Icons.Filled.Mic, str.dictate, { open = true })
    if (open) DictationDialog(onDismiss = { open = false }, onText = { open = false; onText(it) })
}

@Composable
fun DictationDialog(onDismiss: () -> Unit, onText: (String) -> Unit) {
    val ctx = LocalContext.current
    val s = str
    val p = pal
    var lang by remember { mutableStateOf(Settings.voiceLanguage()) }
    var text by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val dict = remember {
        Dictation(ctx, object : DictationListener {
            override fun onPartial(text: String) { partial = text }
            override fun onFinal(t: String) {
                text = (text.trimEnd() + " " + t).trim()
                partial = ""
            }
            override fun onError(code: DictationError) {
                error = when (code) {
                    DictationError.NO_ENGINE -> s.errNoEngine
                    DictationError.NETWORK -> s.errNetwork
                    DictationError.PERMISSION -> s.errPermission
                    DictationError.LANGUAGE -> s.errLanguage
                    DictationError.OTHER -> s.errOther
                }
            }
            override fun onStopped() { listening = false; partial = "" }
        })
    }
    DisposableEffect(Unit) { onDispose { dict.release() } }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { error = null; listening = true; dict.start(lang) } else error = s.errPermission
    }
    fun toggle() {
        if (listening) { dict.stop(); return }
        error = null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listening = true
            dict.start(lang)
        } else perm.launch(Manifest.permission.RECORD_AUDIO)
    }

    AlertDialog(
        onDismissRequest = { dict.stop(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 560.dp).padding(16.dp),
        title = { Text(s.dictate) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.voiceLanguage, style = MaterialTheme.typography.labelMedium)
                Segmented(listOf("ar-SA" to "العربية", "en-US" to "English"), lang, { if (!listening) lang = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (listening) p.accent else p.primary)
                            .clickable { toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, if (listening) s.stop else s.dictate,
                            tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(if (listening) s.listening else s.tapToStart, style = MaterialTheme.typography.bodyLarge)
                }
                if (partial.isNotEmpty()) Text(partial, color = p.muted)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    label = { Text(s.voiceReview) },
                )
                error?.let { Text(it, color = if (p.eink) Color.Black else p.warnBorder, fontWeight = FontWeight.SemiBold) }
                Text(s.voiceInfo, style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { dict.stop(); onText(text.trim()) }) { Text(s.insert, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { dict.stop(); onDismiss() }) { Text(s.cancel) } },
    )
}
