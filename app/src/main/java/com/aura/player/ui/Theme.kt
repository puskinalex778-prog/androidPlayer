package com.aura.player.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AuraTheme(val title: String, val accent: Color, val secondary: Color) {
    COBALT("Кобальт", Color(0xFF3B82F6), Color(0xFF60A5FA)),
    VIOLET("Индиго", Color(0xFF7C5CFF), Color(0xFFA78BFA)),
    EMERALD("Изумруд", Color(0xFF10B981), Color(0xFF34D399)),
    CORAL("Коралл", Color(0xFFF43F5E), Color(0xFFFB7185)),
    AMBER("Янтарь", Color(0xFFF59E0B), Color(0xFFFBBF24)),
    TEAL("Бирюза", Color(0xFF06B6D4), Color(0xFF22D3EE)),
    RETRO("Кассета 80-х", Color(0xFFEA580C), Color(0xFFFB923C)),
    CRIMSON("Рубин", Color(0xFFE11D48), Color(0xFFF43F5E)),
    MONO("Монохром", Color(0xFFE5E7EB), Color(0xFF9CA3AF)),
    DYNAMIC("Material You", Color(0xFF3B82F6), Color(0xFF60A5FA));

    companion object {
        fun fromName(name: String): AuraTheme =
            entries.firstOrNull { it.name == name } ?: COBALT
    }
}

@Immutable
data class AuraColors(
    val accent: Color,
    val accentSoft: Color,
    val accent2: Color,
    val onAccent: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val danger: Color,
    val dark: Boolean,
)

val LocalAura = staticCompositionLocalOf {
    AuraColors(
        accent = Color(0xFF3B82F6),
        accentSoft = Color(0xFF3B82F6).copy(alpha = 0.14f),
        accent2 = Color(0xFF60A5FA),
        onAccent = Color.White,
        bg = Color(0xFF0D0E12),
        surface = Color(0xFF15171E),
        surface2 = Color(0xFF1E212B),
        border = Color(0xFF292D3A),
        text = Color(0xFFF4F5F8),
        textDim = Color(0xFF8B90A3),
        danger = Color(0xFFEF4444),
        dark = true,
    )
}

private val AuraType = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

@Composable
fun AuraAppTheme(
    theme: AuraTheme,
    darkMode: Int,
    amoled: Boolean,
    adaptiveAccent: Color?,
    content: @Composable (dark: Boolean) -> Unit,
) {
    val dark = when (darkMode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val dyn: ColorScheme? = if (theme == AuraTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null

    var p = dyn?.primary ?: if (theme == AuraTheme.MONO) (if (dark) Color(0xFFF4F5F8) else Color(0xFF14161D)) else theme.accent
    var s = dyn?.secondary ?: theme.secondary
    if (adaptiveAccent != null && theme != AuraTheme.MONO) {
        p = adaptiveAccent
        s = adaptiveAccent
    }
    if (!dark && p.luminance() > 0.55f && theme != AuraTheme.MONO) {
        p = p.copy(red = p.red * 0.68f, green = p.green * 0.68f, blue = p.blue * 0.68f)
    }

    val accent by animateColorAsState(p, tween(450), label = "accent")
    val accent2 by animateColorAsState(s, tween(450), label = "accent2")

    val bg = when {
        dark && amoled -> Color(0xFF000000)
        dark -> Color(0xFF0D0E12)
        else -> Color(0xFFF3F4F7)
    }
    val surface = when {
        dark && amoled -> Color(0xFF0D0E12)
        dark -> Color(0xFF15171E)
        else -> Color(0xFFFFFFFF)
    }
    val surface2 = when {
        dark && amoled -> Color(0xFF171921)
        dark -> Color(0xFF1F222C)
        else -> Color(0xFFECEEF3)
    }
    val border = if (dark) Color(0xFF282C38) else Color(0xFFE0E3EC)
    val text = if (dark) Color(0xFFF4F5F8) else Color(0xFF11131A)
    val textDim = if (dark) Color(0xFF8C91A4) else Color(0xFF666C80)
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF11131A) else Color.White

    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent2,
        background = bg, surface = surface, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = textDim,
        surfaceContainer = surface, surfaceContainerHigh = surface2, outline = border,
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent2,
        background = bg, surface = surface, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = textDim,
        surfaceContainer = surface, surfaceContainerHigh = surface2, outline = border,
    )

    val colors = AuraColors(
        accent = accent,
        accentSoft = accent.copy(alpha = if (dark) 0.16f else 0.12f),
        accent2 = accent2,
        onAccent = onAccent,
        bg = bg,
        surface = surface,
        surface2 = surface2,
        border = border,
        text = text,
        textDim = textDim,
        danger = if (dark) Color(0xFFF87171) else Color(0xFFDC2626),
        dark = dark,
    )

    CompositionLocalProvider(LocalAura provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AuraType) {
            content(dark)
        }
    }
}
