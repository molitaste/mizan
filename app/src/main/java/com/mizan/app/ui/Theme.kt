package com.mizan.app.ui

import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

@Immutable
data class Palette(
    val eink: Boolean,
    val bg: Color,
    val surface: Color,
    val header: Color,
    val onHeader: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val warnBorder: Color,
    val warnBg: Color,
)

val ColorPalette = Palette(
    eink = false,
    bg = Color(0xFFFAF6EC),
    surface = Color(0xFFFFFDF7),
    header = Color(0xFF0D4D48),
    onHeader = Color(0xFFFFFFFF),
    primary = Color(0xFF0F6B64),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFFD6EEE9),
    accent = Color(0xFFEE7A1A),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFDEBD8),
    text = Color(0xFF1C2726),
    muted = Color(0xFF55615F),
    border = Color(0xFFE2D9C6),
    warnBorder = Color(0xFFE06A00),
    warnBg = Color(0xFFFFF3E3),
)

/** Solid black & white only – no greys, no transparency, no colour-dependent meaning. */
val EinkPalette = Palette(
    eink = true,
    bg = Color.White,
    surface = Color.White,
    header = Color.White,
    onHeader = Color.Black,
    primary = Color.Black,
    onPrimary = Color.White,
    primarySoft = Color.White,
    accent = Color.Black,
    onAccent = Color.White,
    accentSoft = Color.White,
    text = Color.Black,
    muted = Color.Black,
    border = Color.Black,
    warnBorder = Color.Black,
    warnBg = Color.White,
)

val LocalPalette = staticCompositionLocalOf { ColorPalette }

private object NoRipple : RippleTheme {
    @Composable
    override fun defaultColor() = Color.Transparent

    @Composable
    override fun rippleAlpha() = RippleAlpha(0f, 0f, 0f, 0f)
}

@Composable
fun MizanTheme(arabic: Boolean, eink: Boolean, textScale: Float = 1f, content: @Composable () -> Unit) {
    val p = if (eink) EinkPalette else ColorPalette
    val scheme = lightColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primarySoft,
        onPrimaryContainer = p.text,
        secondary = p.accent,
        onSecondary = p.onAccent,
        secondaryContainer = p.accentSoft,
        onSecondaryContainer = p.text,
        tertiary = p.primary,
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surface,
        onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent,
        outline = p.border,
        outlineVariant = p.border,
        error = if (eink) Color.Black else Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = if (eink) Color.White else Color(0xFFFCE4E2),
        scrim = if (eink) Color.White else Color(0x99000000),
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface,
        surfaceContainerLow = p.surface,
        surfaceContainerLowest = p.surface,
        surfaceBright = p.surface,
        surfaceDim = p.surface,
        inverseSurface = p.text,
        inverseOnSurface = p.surface,
    )
    // larger default type for kitchen use and e-ink legibility
    val base = Typography()
    val w = if (eink) FontWeight.Medium else FontWeight.Normal
    val typo = Typography(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = w),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = w),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = w),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    )
    MaterialTheme(colorScheme = scheme, typography = typo) {
        val locals = mutableListOf<ProvidedValue<*>>(
            LocalPalette provides p,
            LocalStr provides Str(arabic),
            LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalTextStyle provides TextStyle(color = p.text, fontSize = 14.sp),
            // app-controlled text size (ignores a very large system font setting)
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                androidx.compose.ui.platform.LocalDensity.current.density, textScale),
        )
        if (eink) {
            locals += LocalRippleTheme provides NoRipple
        }
        CompositionLocalProvider(*locals.toTypedArray(), content = content)
    }
}
