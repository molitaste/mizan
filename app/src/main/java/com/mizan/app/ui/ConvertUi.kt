package com.mizan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mizan.app.core.Converter
import com.mizan.app.core.Num
import com.mizan.app.core.RecipeContent
import com.mizan.app.core.Units
import com.mizan.app.data.Settings

fun amountText(amount: Double?, unit: String, ar: Boolean): String =
    if (amount == null) "—" else "${Num.fmt(amount)} ${Units.label(unit, ar)}".trim()

@Composable
fun reasonText(r: Converter.Reason?): String? {
    val s = str
    return when (r) {
        Converter.Reason.AMBIGUOUS_STANDARD -> s.reasonStd
        Converter.Reason.NEEDS_DENSITY -> s.reasonDensity
        Converter.Reason.NEEDS_PIECE_WEIGHT -> s.reasonPiece
        else -> null
    }
}

/** Original → converted table, used by the review screen and the editor's Convert dialog. */
@Composable
fun ConversionPreview(result: Converter.ContentResult, before: RecipeContent) {
    val s = str
    val p = pal
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        result.rows.forEachIndexed { i, r ->
            val b = before.ingredients.getOrNull(i) ?: return@forEachIndexed
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp)) {
                Text(b.name, Modifier.weight(1.3f), maxLines = 2)
                Text(amountText(b.amount, b.unit, s.ar), Modifier.weight(1f), color = p.muted)
                Text("→", Modifier.padding(horizontal = 6.dp))
                Column(Modifier.weight(1.2f)) {
                    Text(amountText(r.ingredient.amount, r.ingredient.unit, s.ar), fontWeight = if (r.changed) FontWeight.Bold else FontWeight.Normal)
                    reasonText(r.reason)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.warnBorder) }
                }
            }
            HorizontalDivider(color = p.border)
        }
        val changedSteps = result.content.steps.zip(before.steps).count { (a, b) -> a.text != b.text || a.title != b.title }
        if (changedSteps > 0) Text("°C/°F: $changedSteps × ${s.steps}", color = p.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ConvertDialog(content: RecipeContent, onApply: (RecipeContent) -> Unit, onDismiss: () -> Unit) {
    val s = str
    val settings by Settings.state.collectAsState()
    val result = Converter.content(content, settings.units)
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 720.dp).padding(16.dp),
        title = { Text(s.convertUnits + " · " + s.preview) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ConversionPreview(result, content)
                Text(s.densityInfo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onApply(result.content) }) { Text(s.convertToMine, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
