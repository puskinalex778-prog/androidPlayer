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

enum class AuraTheme(val title: String, val primary: Color, val secondary: Color, val tertiary: Color) {
    AURORA("Аврора", Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFF472B6)),
    NEON("Неон", Color(0xFF00F5D4), Color(0xFFF15BB5), Color(0xFFFEE440)),
    SUNSET("Закат", Color(0xFFFF6B6B), Color(0xFFFFB347), Color(0xFFC06C84)),
    OCEAN("Океан", Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFFA5F3FC)),
    FOREST("Лес", Color(0xFF22C55E), Color(0xFFA3E635), Color(0xFF14B8A6)),
    SAKURA("Сакура", Color(0xFFF9A8D4), Color(0xFFF472B6), Color(0xFFC4B5FD)),
    RETRO("Ретро-кассета", Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFFDE68A)),
    CYBER("Киберпанк", Color(0xFFFCEE0A), Color(0xFF00F0FF), Color(0xFFFF003C)),
    MONO("Графит", Color(0xFFE5E7EB), Color(0xFF9CA3AF), Color(0xFFF3F4F6)),
    DYNAMIC("Material You", Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFF472B6));
}

@Immutable
data class AuraColors(
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    val bg: Color,
    val glass: Color,
    val glassBorder: Color,
    val text: Color,
    val textDim: Color,
    val dark: Boolean,
)

val LocalAura = staticCompositionLocalOf {
    AuraColors(Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFF472B6), Color(0xFF0B0B14),
        Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.12f), Color.White, Color.White.copy(alpha = 0.6f), true)
}

private val AuraType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun AuraAppTheme(
    theme: AuraTheme,
    darkMode: Int,
    amoled: Boolean,
    adaptiveAccent: Color?,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val dyn: ColorScheme? = if (theme == AuraTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else null

    var p = dyn?.primary ?: theme.primary
    var s = dyn?.secondary ?: theme.secondary
    var t = dyn?.tertiary ?: theme.tertiary
    if (adaptiveAccent != null) { p = adaptiveAccent; t = theme.primary }
    // в светлой теме слишком светлые акценты затемняем, чтобы были читаемы
    if (!dark && p.luminance() > 0.6f) p = p.copy(red = p.red * 0.6f, green = p.green * 0.6f, blue = p.blue * 0.6f)
    if (!dark && s.luminance() > 0.6f) s = s.copy(red = s.red * 0.6f, green = s.green * 0.6f, blue = s.blue * 0.6f)

    val accent by animateColorAsState(p, tween(900), label = "accent")
    val accent2 by animateColorAsState(s, tween(900), label = "accent2")
    val accent3 by animateColorAsState(t, tween(900), label = "accent3")

    val bg = when {
        dark && amoled -> Color.Black
        dark -> Color(0xFF0B0B14)
        else -> Color(0xFFF5F4FA)
    }
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent2, tertiary = accent3,
        background = bg, surface = Color(0xFF15151F), surfaceVariant = Color(0xFF1E1E2A),
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        surfaceContainer = Color(0xFF181822), surfaceContainerHigh = Color(0xFF1F1F2B), outline = Color.White.copy(alpha = 0.2f),
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent2, tertiary = accent3,
        background = bg, surface = Color.White, surfaceVariant = Color(0xFFECEAF4),
        onBackground = Color(0xFF14131A), onSurface = Color(0xFF14131A), onSurfaceVariant = Color(0xFF55536A),
        surfaceContainer = Color(0xFFF0EEF7), surfaceContainerHigh = Color(0xFFE9E6F2), outline = Color.Black.copy(alpha = 0.15f),
    )
    val colors = AuraColors(
        accent = accent, accent2 = accent2, accent3 = accent3, bg = bg,
        glass = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.72f),
        glassBorder = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
        text = if (dark) Color.White else Color(0xFF14131A),
        textDim = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF14131A).copy(alpha = 0.55f),
        dark = dark,
    )
    CompositionLocalProvider(LocalAura provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AuraType, content = content)
    }
}
