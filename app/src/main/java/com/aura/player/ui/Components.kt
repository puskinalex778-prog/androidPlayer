package com.aura.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.ArtLoader
import com.aura.player.data.Station
import com.aura.player.playback.VoiceEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

// ===================== Адаптивная верхняя панель =====================

@Composable
fun FlatTopBar(
    title: String,
    subtitle: String? = null,
    onMenu: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAura.current
    val bgColor = if (c.style == UiStyle.GLASS) Color.Transparent else c.bg
    Column(Modifier.fillMaxWidth().background(bgColor)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Rounded.Menu, "Меню", tint = c.text)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    if (c.style == UiStyle.RETRO) title.uppercase() else title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = if (c.style == UiStyle.RETRO) FontFamily.Monospace else FontFamily.Default,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (c.style == UiStyle.RETRO) FontFamily.Monospace else FontFamily.Default,
                        color = c.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        HorizontalDivider(thickness = c.borderWidth, color = c.border)
    }
}

// ===================== Адаптивные карточки и элементы =====================

@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAura.current
    val s = shape ?: RoundedCornerShape(c.cardRadius)
    val borderBrush = when (c.style) {
        UiStyle.SOFT -> Brush.linearGradient(listOf(c.highlight, c.border))
        UiStyle.GLASS -> Brush.verticalGradient(listOf(c.highlight, c.border.copy(alpha = 0.35f)))
        else -> SolidColor(c.border)
    }
    var m = modifier
    if (c.style == UiStyle.SOFT) {
        m = m.shadow(elevation = if (c.dark) 6.dp else 5.dp, shape = s, clip = false)
    }
    m = m
        .clip(s)
        .background(color ?: c.surface)
        .border(c.borderWidth, borderBrush, s)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(padding), content = content)
}

@Composable
fun FlatChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    prefix: String? = null,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.controlRadius)
    val bg = when {
        selected && c.style == UiStyle.GLASS -> Brush.horizontalGradient(listOf(c.accent, c.accent2))
        selected -> SolidColor(c.accent)
        else -> SolidColor(c.surface2)
    }
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .border(c.borderWidth, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) c.onAccent else c.textDim, modifier = Modifier.size(16.dp))
        }
        if (prefix != null) {
            Text(prefix, fontSize = 13.sp)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = if (c.style == UiStyle.RETRO) FontFamily.Monospace else FontFamily.Default,
            color = if (selected) c.onAccent else c.text,
            maxLines = 1,
        )
    }
}

@Composable
fun FlatSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    val c = LocalAura.current
    val trackShape = RoundedCornerShape(c.controlRadius + 2.dp)
    val itemShape = RoundedCornerShape(c.controlRadius)
    Row(
        modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(c.surface2)
            .border(c.borderWidth, c.border, trackShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            val activeBg = if (c.style == UiStyle.RETRO) c.accent else c.surfaceSolid
            val activeText = if (c.style == UiStyle.RETRO) c.onAccent else c.text
            val activeIcon = if (c.style == UiStyle.RETRO) c.onAccent else c.accent
            Row(
                Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .background(if (sel) activeBg else Color.Transparent)
                    .then(if (sel && c.style != UiStyle.RETRO) Modifier.border(c.borderWidth, c.border, itemShape) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ic = icons?.getOrNull(i)
                if (ic != null) {
                    Icon(ic, null, tint = if (sel) activeIcon else c.textDim, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = if (c.style == UiStyle.RETRO) FontFamily.Monospace else FontFamily.Default,
                    color = if (sel) activeText else c.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun FlatPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.controlRadius + 1.dp)
    val bg = if (c.style == UiStyle.GLASS) Brush.horizontalGradient(listOf(c.accent, c.accent2)) else SolidColor(c.accent)
    Row(
        modifier
            .then(if (c.style == UiStyle.SOFT) Modifier.shadow(6.dp, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.onAccent, maxLines = 1)
    }
}

@Composable
fun FlatTonalButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.controlRadius + 1.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.surface2)
            .border(c.borderWidth, c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FlatSearchField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.controlRadius + 1.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface2)
            .border(c.borderWidth, c.border, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.textDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(hint, color = c.textDim, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Очистить", tint = c.textDim, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = LocalAura.current
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = if (c.style == UiStyle.RETRO) FontFamily.Monospace else FontFamily.Default,
            color = c.textDim,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        action?.invoke()
    }
}

@Composable
fun FlatEmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = LocalAura.current
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(c.cardRadius))
                .background(c.accentSoft)
                .border(c.borderWidth, c.accent.copy(alpha = 0.25f), RoundedCornerShape(c.cardRadius)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textDim, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

// ===================== Обложки и иконки =====================

@Composable
fun rememberArt(uri: String?, size: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = uri?.let { ArtLoader.cached(it, size)?.asImageBitmap() }, uri, size) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { ArtLoader.load(ctx, uri, size)?.asImageBitmap() }
    }
    return state.value
}

private val flatSeeds = listOf(
    Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFF059669), Color(0xFFDC2626),
    Color(0xFFD97706), Color(0xFF0891B2), Color(0xFFDB2777), Color(0xFF4F46E5),
)

fun flatSeedColor(seed: String): Color = flatSeeds[(seed.hashCode() and 0x7fffffff) % flatSeeds.size]

@Composable
fun ArtThumb(uri: String?, seed: String, modifier: Modifier = Modifier, size: Dp = 48.dp, corner: Dp? = null) {
    val c = LocalAura.current
    val art = rememberArt(uri, 160)
    val shape = RoundedCornerShape(corner ?: (if (c.style == UiStyle.RETRO) 4.dp else 10.dp))
    Box(
        modifier.size(size).clip(shape).border(c.borderWidth, c.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val base = flatSeedColor(seed)
            Box(Modifier.fillMaxSize().background(base.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = base, modifier = Modifier.size(size * 0.46f))
            }
        }
    }
}

@Composable
fun StationLogo(st: Station, modifier: Modifier = Modifier, size: Dp = 50.dp) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(if (c.style == UiStyle.RETRO) 5.dp else 12.dp)
    val base = flatSeedColor(st.name)
    val initials = st.name.split(' ', '-', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
    Box(
        modifier.size(size).clip(shape).background(base.copy(alpha = 0.16f)).border(c.borderWidth, base.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isNotBlank()) {
            Text(initials, color = base, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.34f).sp, maxLines = 1)
        } else {
            Icon(Icons.Rounded.Radio, null, tint = base, modifier = Modifier.size(size * 0.45f))
        }
        if (st.country.isNotBlank()) {
            Text(
                st.country,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(base)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

fun voiceIcon(v: VoiceEffect): ImageVector = when (v) {
    VoiceEffect.NORMAL -> Icons.Rounded.MusicNote
    VoiceEffect.CHILD -> Icons.Rounded.ChildCare
    VoiceEffect.HELIUM -> Icons.Rounded.Air
    VoiceEffect.CHIPMUNK -> Icons.Rounded.Pets
    VoiceEffect.ANGRY -> Icons.Rounded.Whatshot
    VoiceEffect.DEMON -> Icons.Rounded.LocalFireDepartment
    VoiceEffect.GIANT -> Icons.Rounded.Terrain
    VoiceEffect.ROBOT -> Icons.Rounded.SmartToy
    VoiceEffect.CAVE -> Icons.Rounded.Landscape
    VoiceEffect.HALL -> Icons.Rounded.AccountBalance
    VoiceEffect.PHONE -> Icons.Rounded.Call
    VoiceEffect.UNDERWATER -> Icons.Rounded.Water
    VoiceEffect.VINYL -> Icons.Rounded.Album
    VoiceEffect.KARAOKE -> Icons.Rounded.MicOff
}

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ===================== Вертикальный слайдер эквалайзера =====================

@Composable
fun VerticalSlider(
    value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    val c = LocalAura.current
    val span = range.endInclusive - range.start
    Canvas(
        modifier
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onChange(range.start + f * span)
                }
            }
            .pointerInput(enabled, range) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { onChange(0f.coerceIn(range)) }) { pos ->
                    val f = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                    onChange(range.start + f * span)
                }
            }
    ) {
        val w = 4.dp.toPx()
        val x = size.width / 2
        val top = 12.dp.toPx()
        val bottom = size.height - 12.dp.toPx()
        val h = bottom - top
        val zeroY = top + h * (1f - (0f - range.start) / span)
        val vy = top + h * (1f - (value - range.start) / span)
        val alpha = if (enabled) 1f else 0.35f

        drawLine(c.border, Offset(x - 10.dp.toPx(), zeroY), Offset(x + 10.dp.toPx(), zeroY), 1.dp.toPx())
        drawRoundRect(c.surface2, Offset(x - w / 2, top), Size(w, h), CornerRadius(w))
        drawRoundRect(
            c.accent.copy(alpha = alpha),
            Offset(x - w / 2, min(zeroY, vy)),
            Size(w, abs(zeroY - vy).coerceAtLeast(1f)),
            CornerRadius(w),
        )
        drawCircle(c.surfaceSolid, 8.dp.toPx(), Offset(x, vy))
        drawCircle(c.accent.copy(alpha = alpha), 6.dp.toPx(), Offset(x, vy))
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
