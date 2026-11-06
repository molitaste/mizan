package com.mizan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mizan.app.core.Note
import com.mizan.app.core.NoteKind
import com.mizan.app.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDate(t: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))

@Composable
fun NotesScreen(id: String, onBack: () -> Unit) {
    val s = str
    val p = pal
    val recipes by Store.recipes.collectAsState()
    val r = recipes.find { it.id == id }
    var draft by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(NoteKind.OBSERVATION) }
    var toDelete by remember { mutableStateOf<Note?>(null) }
    if (r == null) { Column(Modifier.fillMaxSize().background(p.bg)) { HeaderBar(s.notes, onBack) }; return }
    val c = r.current

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(c.title.ifBlank { s.untitled }, onBack)
        ContentColumn(Modifier.weight(1f).imePadding().navigationBarsPadding()) {
            SectionTitle(Icons.AutoMirrored.Filled.Notes, s.notes)
            MCard {
                Segmented(NoteKind.entries.map { it to s.kind(it) }, kind, { kind = it })
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), placeholder = { Text(s.noteHint) }, minLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MicButton(onText = { t -> draft = (draft.trimEnd() + " " + t).trim() })
                    Spacer(Modifier.weight(1f))
                    AccentButton(s.addNote, Icons.Filled.Add, enabled = draft.isNotBlank()) {
                        val n = Note(kind = kind, text = draft.trim())
                        Store.updateCurrent(id) { it.copy(notes = it.notes + n) }
                        draft = ""
                    }
                }
            }
            if (c.notes.isEmpty()) Text(s.noNotes, color = p.muted)
            c.notes.sortedByDescending { it.at }.forEach { n ->
                MCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.kind(n.kind), fontWeight = FontWeight.Bold, color = p.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(fmtDate(n.at), color = p.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { toDelete = n }) { Icon(Icons.Filled.Delete, s.delete) }
                    }
                    OutlinedTextField(n.text, { v ->
                        Store.updateCurrent(id) { cc -> cc.copy(notes = cc.notes.map { if (it.id == n.id) it.copy(text = v) else it }) }
                    }, Modifier.fillMaxWidth(), minLines = 2)
                    Segmented(NoteKind.entries.map { it to s.kind(it) }, n.kind, { k ->
                        Store.updateCurrent(id) { cc -> cc.copy(notes = cc.notes.map { if (it.id == n.id) it.copy(kind = k) else it }) }
                    })
                }
            }
        }
    }
    toDelete?.let { n ->
        ConfirmDialog(s.delete + "?", s.delete, onConfirm = {
            Store.updateCurrent(id) { cc -> cc.copy(notes = cc.notes.filter { it.id != n.id }) }
        }, onDismiss = { toDelete = null })
    }
}
