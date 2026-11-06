package com.mizan.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mizan.app.core.MeasureSystem
import com.mizan.app.core.UnitPrefs
import com.mizan.app.core.Units
import com.mizan.app.data.Backup
import com.mizan.app.data.Settings
import com.mizan.app.export.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s = str
    val p = pal
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Settings.state.collectAsState()
    val u = st.units
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { Backup.restore(ctx, uri) }.getOrNull() }
            toast(ctx, if (n == null) s.notBackup else s.restoredCount(n))
        }
    }
    fun setUnits(f: (UnitPrefs) -> UnitPrefs) = Settings.update { it.copy(units = f(it.units)) }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        HeaderBar(s.settings, onBack)
        ContentColumn(Modifier.weight(1f).navigationBarsPadding()) {
            SectionTitle(Icons.Filled.Settings, s.unitsLanguage)
            SettingRow(s.measurementSystem) {
                Segmented(listOf(MeasureSystem.METRIC to s.metric, MeasureSystem.US to s.usCustomary, MeasureSystem.CUSTOM to s.custom), u.system, { sys ->
                    setUnits {
                        when (sys) {
                            MeasureSystem.METRIC -> it.copy(system = sys, weightUnit = "g", volumeUnit = "ml", fahrenheit = false)
                            MeasureSystem.US -> it.copy(system = sys, weightUnit = "oz", volumeUnit = "cup_us", fahrenheit = true)
                            MeasureSystem.CUSTOM -> it.copy(system = sys)
                        }
                    }
                })
            }
            SettingRow(s.weightUnits) {
                Segmented(listOf("g", "kg", "oz", "lb").map { it to Units.label(it, false) }, u.weightUnit, { w ->
                    setUnits { it.copy(weightUnit = w, system = if (it.system == MeasureSystem.METRIC && w in listOf("g", "kg") || it.system == MeasureSystem.US && w in listOf("oz", "lb")) it.system else MeasureSystem.CUSTOM) }
                })
            }
            SettingRow(s.volumeUnits) {
                val vols = listOf("ml", "l", "cup_us", "tbsp_us", "tsp_us", "floz_us")
                Segmented(vols.map { it to Units.label(it, false) }, u.volumeUnit, { v ->
                    setUnits { it.copy(volumeUnit = v, system = if (it.system == MeasureSystem.METRIC && v in listOf("ml", "l") || it.system == MeasureSystem.US && v.endsWith("_us")) it.system else MeasureSystem.CUSTOM) }
                })
                if (u.system == MeasureSystem.CUSTOM) {
                    Spacer(Modifier.width(8.dp))
                    Segmented((Units.impVolume.take(4) + Units.metricCulinary).map { it to Units.label(it, false) }, u.volumeUnit, { v -> setUnits { it.copy(volumeUnit = v) } })
                }
            }
            SettingRow(s.temperatureUnits) {
                Segmented(listOf(false to "°C", true to "°F"), u.fahrenheit, { f -> setUnits { it.copy(fahrenheit = f) } })
            }
            ToggleRow(s.autoConvert, s.autoConvertSub, st.autoConvertImports) { v -> Settings.update { it.copy(autoConvertImports = v) } }
            ToggleRow(s.preserveOriginals, s.preserveOriginalsSub, st.showOriginals) { v -> Settings.update { it.copy(showOriginals = v) } }
            ToggleRow(s.volToWeight, s.volToWeightSub, u.volumeToWeight) { v -> setUnits { it.copy(volumeToWeight = v) } }
            SettingRow(s.language) {
                Segmented(listOf(false to "English", true to "العربية"), st.arabic, { a -> Settings.update { it.copy(arabic = a) } })
            }
            SettingRow(s.voiceLanguage) {
                Segmented(listOf("" to s.speechFollowUi, "ar-SA" to "العربية", "en-US" to "English"), st.voiceLang, { v -> Settings.update { it.copy(voiceLang = v) } })
            }
            SettingRow(s.textSize) {
                Segmented(listOf(0.85f to s.small, 1f to s.normal, 1.15f to s.large), st.textScale, { v -> Settings.update { it.copy(textScale = v) } })
            }
            SettingRow(s.displayMode) {
                Segmented(listOf(false to s.color, true to s.eink), st.eink, { e -> Settings.update { it.copy(eink = e) } })
            }
            if (st.eink) {
                SettingRow(s.einkPhotos) {
                    Segmented(listOf(true to s.twoTone, false to s.grayPhotos), st.einkTwoTone, { t -> Settings.update { it.copy(einkTwoTone = t) } })
                }
            }
            InfoBox(s.densityInfo)
            InfoBox(s.einkInfo)

            Text(s.storage, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            InfoBox(s.storageInfo)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(s.makeBackup, Icons.Filled.Backup) {
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { runCatching { Exporter.backup(ctx) }.getOrNull() }
                        if (f != null) Exporter.share(ctx, f, Exporter.MIME_ZIP, s.share) else toast(ctx, s.exportFailed)
                    }
                }
                OutlineButton(s.restoreBackup, Icons.Filled.Restore) { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            }
            Text(s.about + ": Mizan 1.0 · " + s.storageInfo.substringBefore('.') + ".", color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    BoxWithConstraints {
        if (maxWidth > 700.dp) {
            MCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(220.dp))
                    Column(Modifier.weight(1f)) { content() }
                }
            }
        } else MCard(Modifier.fillMaxWidth()) {
            Text(label, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = pal
    MCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(sub, color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            if (p.eink) {
                Segmented(listOf(true to "ON", false to "OFF"), value, onChange, Modifier.width(150.dp))
            } else Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.primary))
        }
    }
}

@Composable
fun InfoBox(text: String) {
    val p = pal
    MCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = p.primary)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (p.eink) Color.Black else p.text)
        }
    }
}
