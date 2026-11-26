package com.aura.player.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class UiStyle(val title: String, val desc: String) {
    FLAT("Flat 2026", "Чистые плоские формы и чёткие линии"),
    GLASS("Стекло", "Полупрозрачные карточки и мягкое свечение"),
    SOFT("Мягкий 3D", "Тактильный объём и плавные скругления"),
    RETRO("Hi-Fi Ретро", "Индустриальный стиль аудиодеки");

    companion object {
        fun fromName(name: String): UiStyle = entries.firstOrNull { it.name == name } ?: FLAT
    }
}

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
        fun fromName(name: String): AuraTheme = entries.firstOrNull { it.name == name } ?: COBALT
    }
}

@Immutable
data class AuraColors(
    val style: UiStyle,
    val accent: Color,
    val accentSoft: Color,
    val accent2: Color,
    val onAccent: Color,
    val bg: Color,
    val surface: Color,
    val surfaceSolid: Color,
    val surface2: Color,
    val border: Color,
    val highlight: Color,
    val text: Color,
    val textDim: Color,
    val danger: Color,
    val dark: Boolean,
    val cardRadius: Dp,
    val controlRadius: Dp,
    val borderWidth: Dp,
)

val LocalAura = staticCompositionLocalOf {
    AuraColors(
        style = UiStyle.FLAT,
        accent = Color(0xFF3B82F6),
        accentSoft = Color(0xFF3B82F6).copy(alpha = 0.14f),
        accent2 = Color(0xFF60A5FA),
        onAccent = Color.White,
        bg = Color(0xFF0D0E12),
        surface = Color(0xFF15171E),
        surfaceSolid = Color(0xFF15171E),
        surface2 = Color(0xFF1E212B),
        border = Color(0xFF292D3A),
        highlight = Color.White.copy(alpha = 0.08f),
        text = Color(0xFFF4F5F8),
        textDim = Color(0xFF8B90A3),
        danger = Color(0xFFEF4444),
        dark = true,
        cardRadius = 14.dp,
        controlRadius = 11.dp,
        borderWidth = 1.dp,
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
    style: UiStyle,
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
        dark && style == UiStyle.RETRO -> Color(0xFF12110F)
        dark && style == UiStyle.SOFT -> Color(0xFF141720)
        dark && style == UiStyle.GLASS -> Color(0xFF0A0C14)
        dark -> Color(0xFF0D0E12)
        style == UiStyle.RETRO -> Color(0xFFECE8DF)
        style == UiStyle.SOFT -> Color(0xFFE9ECF3)
        else -> Color(0xFFF3F4F7)
    }

    val surfaceSolid = when {
        dark && amoled -> Color(0xFF0D0E12)
        dark && style == UiStyle.RETRO -> Color(0xFF1C1A17)
        dark && style == UiStyle.SOFT -> Color(0xFF1C202B)
        dark -> Color(0xFF15171E)
        style == UiStyle.RETRO -> Color(0xFFF7F4EC)
        style == UiStyle.SOFT -> Color(0xFFF3F5FA)
        else -> Color(0xFFFFFFFF)
    }

    val surface = when (style) {
        UiStyle.GLASS -> if (dark) Color(0xFF1B1F2E).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.68f)
        else -> surfaceSolid
    }

    val surface2 = when {
        style == UiStyle.GLASS && dark -> Color.White.copy(alpha = 0.08f)
        style == UiStyle.GLASS && !dark -> Color.Black.copy(alpha = 0.05f)
        dark && amoled -> Color(0xFF171921)
        dark && style == UiStyle.RETRO -> Color(0xFF26231F)
        dark && style == UiStyle.SOFT -> Color(0xFF232836)
        dark -> Color(0xFF1F222C)
        style == UiStyle.RETRO -> Color(0xFFE3DEC9).copy(alpha = 0.7f)
        style == UiStyle.SOFT -> Color(0xFFE0E5F0)
        else -> Color(0xFFECEEF3)
    }

    val border = when {
        style == UiStyle.GLASS && dark -> Color.White.copy(alpha = 0.14f)
        style == UiStyle.GLASS && !dark -> Color.White.copy(alpha = 0.75f)
        style == UiStyle.RETRO && dark -> Color(0xFF3D382F)
        style == UiStyle.RETRO && !dark -> Color(0xFFC2BA9F)
        style == UiStyle.SOFT && dark -> Color(0xFF2D3344)
        style == UiStyle.SOFT && !dark -> Color(0xFFD4DAE8)
        dark -> Color(0xFF282C38)
        else -> Color(0xFFE0E3EC)
    }

    val highlight = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.9f)
    val text = if (dark) Color(0xFFF4F5F8) else Color(0xFF14151B)
    val textDim = if (dark) Color(0xFF8F94A8) else Color(0xFF60667A)
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF11131A) else Color.White

    val cardRadius = when (style) {
        UiStyle.FLAT -> 14.dp
        UiStyle.GLASS -> 18.dp
        UiStyle.SOFT -> 22.dp
        UiStyle.RETRO -> 6.dp
    }
    val controlRadius = when (style) {
        UiStyle.FLAT -> 11.dp
        UiStyle.GLASS -> 14.dp
        UiStyle.SOFT -> 16.dp
        UiStyle.RETRO -> 4.dp
    }
    val borderWidth = if (style == UiStyle.RETRO) 1.5.dp else 1.dp

    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent2,
        background = bg, surface = surfaceSolid, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = textDim,
        surfaceContainer = surfaceSolid, surfaceContainerHigh = surface2, outline = border,
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent2,
        background = bg, surface = surfaceSolid, surfaceVariant = surface2,
        onBackground = text, onSurface = text, onSurfaceVariant = textDim,
        surfaceContainer = surfaceSolid, surfaceContainerHigh = surface2, outline = border,
    )

    val colors = AuraColors(
        style = style,
        accent = accent,
        accentSoft = accent.copy(alpha = if (dark) 0.17f else 0.13f),
        accent2 = accent2,
        onAccent = onAccent,
        bg = bg,
        surface = surface,
        surfaceSolid = surfaceSolid,
        surface2 = surface2,
        border = border,
        highlight = highlight,
        text = text,
        textDim = textDim,
        danger = if (dark) Color(0xFFF87171) else Color(0xFFDC2626),
        dark = dark,
        cardRadius = cardRadius,
        controlRadius = controlRadius,
        borderWidth = borderWidth,
    )

    CompositionLocalProvider(LocalAura provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AuraType) {
            content(dark)
        }
    }
}

/** Тонкий фон-подложка: для Glass — живое свечение, для Retro — деликатная сетка, для Flat/Soft — чистый цвет. */
@Composable
fun StyleBackdrop(modifier: Modifier = Modifier) {
    val c = LocalAura.current
    when (c.style) {
        UiStyle.GLASS -> {
            val tr = rememberInfiniteTransition(label = "glassBg")
            val t by tr.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "t")
            val a = if (c.dark) 0.28f else 0.20f
            Canvas(modifier.fillMaxSize()) {
                drawRect(c.bg)
                val r = max(size.width, size.height) * 0.68f
                val p1 = Offset(size.width * (0.22f + 0.14f * cos(t)), size.height * (0.14f + 0.08f * sin(t)))
                val p2 = Offset(size.width * (0.86f + 0.10f * sin(t)), size.height * (0.52f + 0.12f * cos(t)))
                val p3 = Offset(size.width * (0.28f + 0.16f * sin(t + 1.5f)), size.height * (0.92f + 0.06f * cos(t)))
                drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = a), Color.Transparent), p1, r), r, p1)
                drawCircle(Brush.radialGradient(listOf(c.accent2.copy(alpha = a * 0.85f), Color.Transparent), p2, r * 0.85f), r * 0.85f, p2)
                drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = a * 0.65f), Color.Transparent), p3, r * 0.8f), r * 0.8f, p3)
            }
        }
        UiStyle.RETRO -> {
            Canvas(modifier.fillMaxSize()) {
                drawRect(c.bg)
                val step = 22.dp.toPx()
                val dot = c.border.copy(alpha = 0.32f)
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(dot, 1.dp.toPx(), Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
        }
        else -> {
            Canvas(modifier.fillMaxSize()) { drawRect(c.bg) }
        }
    }
}
