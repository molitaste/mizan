package com.mizan.app.data

import android.content.Context
import com.mizan.app.core.MeasureSystem
import com.mizan.app.core.UnitPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val arabic: Boolean = false,
    val eink: Boolean = false,
    val einkTwoTone: Boolean = true,        // strict black/white photo previews in E-Ink mode
    val units: UnitPrefs = UnitPrefs.metric(),
    val autoConvertImports: Boolean = false,
    val showOriginals: Boolean = true,
    val voiceLang: String = "",             // "" = follow UI language
    val forcePdfOcr: Boolean = false,
)

object Settings {
    private lateinit var app: Context
    private val _s = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _s.asStateFlow()
    val value: AppSettings get() = _s.value

    private fun prefs() = app.getSharedPreferences("mizan", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val p = prefs()
        val sys = runCatching { MeasureSystem.valueOf(p.getString("system", "METRIC")!!) }.getOrDefault(MeasureSystem.METRIC)
        _s.value = AppSettings(
            arabic = p.getBoolean("arabic", java.util.Locale.getDefault().language == "ar"),
            eink = p.getBoolean("eink", false),
            einkTwoTone = p.getBoolean("einkTwoTone", true),
            units = UnitPrefs(
                system = sys,
                weightUnit = p.getString("weight", "g") ?: "g",
                volumeUnit = p.getString("volume", "ml") ?: "ml",
                fahrenheit = p.getBoolean("fahrenheit", false),
                volumeToWeight = p.getBoolean("volToWeight", false),
            ),
            autoConvertImports = p.getBoolean("autoConvert", false),
            showOriginals = p.getBoolean("showOriginals", true),
            voiceLang = p.getString("voiceLang", "") ?: "",
            forcePdfOcr = p.getBoolean("forcePdfOcr", false),
        )
    }

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_s.value)
        _s.value = n
        prefs().edit()
            .putBoolean("arabic", n.arabic)
            .putBoolean("eink", n.eink)
            .putBoolean("einkTwoTone", n.einkTwoTone)
            .putString("system", n.units.system.name)
            .putString("weight", n.units.weightUnit)
            .putString("volume", n.units.volumeUnit)
            .putBoolean("fahrenheit", n.units.fahrenheit)
            .putBoolean("volToWeight", n.units.volumeToWeight)
            .putBoolean("autoConvert", n.autoConvertImports)
            .putBoolean("showOriginals", n.showOriginals)
            .putString("voiceLang", n.voiceLang)
            .putBoolean("forcePdfOcr", n.forcePdfOcr)
            .apply()
    }

    fun voiceLanguage(): String = value.voiceLang.ifEmpty { if (value.arabic) "ar-SA" else "en-US" }
}
