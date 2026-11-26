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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.ArtLoader
import com.aura.player.data.Station
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.VoiceEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ===================== Flat Top Bar =====================

@Composable
fun FlatTopBar(
    title: String,
    subtitle: String? = null,
    onMenu: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAura.current
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Rounded.Menu, "Меню", tint = c.text)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        HorizontalDivider(thickness = 1.dp, color = c.border)
    }
}

// ===================== Flat Surfaces & Controls =====================

@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAura.current
    var m = modifier
        .clip(shape)
        .background(color ?: c.surface)
        .border(1.dp, c.border, shape)
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
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) c.accent else c.surface2)
            .border(1.dp, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = if (selected) c.onAccent else c.textDim,
                modifier = Modifier.size(16.dp),
            )
        }
        if (prefix != null) {
            Text(prefix, fontSize = 13.sp)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
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
    val trackShape = RoundedCornerShape(12.dp)
    val itemShape = RoundedCornerShape(9.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(c.surface2)
            .border(1.dp, c.border, trackShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Row(
                Modifier
                    .weight(1f)
                    .clip(itemShape)
                    .background(if (sel) c.surface else Color.Transparent)
                    .then(if (sel) Modifier.border(1.dp, c.border, itemShape) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ic = icons?.getOrNull(i)
                if (ic != null) {
                    Icon(ic, null, tint = if (sel) c.accent else c.textDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) c.text else c.textDim,
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
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.accent)
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
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.surface2)
            .border(1.dp, c.border, shape)
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
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface2)
            .border(1.dp, c.border, shape)
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
        modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.accentSoft)
                .border(1.dp, c.accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(30.dp))
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
fun ArtThumb(uri: String?, seed: String, modifier: Modifier = Modifier, size: Dp = 48.dp, corner: Dp = 10.dp) {
    val art = rememberArt(uri, 160)
    val shape = RoundedCornerShape(corner)
    val c = LocalAura.current
    Box(
        modifier.size(size).clip(shape).border(1.dp, c.border, shape),
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
    val shape = RoundedCornerShape(12.dp)
    val base = flatSeedColor(st.name)
    val initials = st.name.split(' ', '-', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
    Box(
        modifier.size(size).clip(shape).background(base.copy(alpha = 0.16f)).border(1.dp, base.copy(alpha = 0.35f), shape),
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

// ===================== Вертикальный flat-слайдер эквалайзера =====================

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

        // 0 дБ отметка
        drawLine(c.border, Offset(x - 10.dp.toPx(), zeroY), Offset(x + 10.dp.toPx(), zeroY), 1.dp.toPx())
        // трек
        drawRoundRect(c.surface2, Offset(x - w / 2, top), Size(w, h), CornerRadius(w))
        // активная часть
        drawRoundRect(
            c.accent.copy(alpha = alpha),
            Offset(x - w / 2, min(zeroY, vy)),
            Size(w, abs(zeroY - vy).coerceAtLeast(1f)),
            CornerRadius(w),
        )
        // плоский бегунок
        drawCircle(c.surface, 8.dp.toPx(), Offset(x, vy))
        drawCircle(c.accent.copy(alpha = alpha), 6.dp.toPx(), Offset(x, vy))
    }
}

// ===================== Flat-визуализатор спектра =====================

private class Spectrum(val bands: Int) {
    private val n = 1024
    private val buf = FloatArray(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { (0.5f - 0.5f * cos(2 * PI * it / (n - 1))).toFloat() }
    val levels = FloatArray(bands)
    private val edges = IntArray(bands + 1) { i ->
        val fMin = 40.0; val fMax = 16000.0
        val f = fMin * (fMax / fMin).pow(i / bands.toDouble())
        (f / 44100.0 * n).toInt().coerceIn(1, n / 2 - 1)
    }

    fun update(active: Boolean) {
        if (active) {
            AudioEngine.fx.snapshot(buf)
            for (i in 0 until n) { re[i] = buf[i] * window[i]; im[i] = 0f }
            fft(re, im)
        }
        for (b in 0 until bands) {
            var target = 0f
            if (active) {
                val a = edges[b]; val e = max(edges[b + 1], a + 1)
                var sum = 0f
                for (k in a until e) sum = max(sum, hypot(re[k], im[k]))
                val db = 20f * ln(sum + 1e-6f) / ln(10f)
                target = ((db + 10f) / 45f).coerceIn(0f, 1f)
            }
            levels[b] = if (target > levels[b]) levels[b] * 0.4f + target * 0.6f else levels[b] * 0.86f + target * 0.14f
        }
    }

    private fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = x[i]; x[i] = x[j]; x[j] = t; t = y[i]; y[i] = y[j]; y[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = x[i + k]; val ui = y[i + k]
                    val vr = x[i + k + len / 2] * cr - y[i + k + len / 2] * ci
                    val vi = x[i + k + len / 2] * ci + y[i + k + len / 2] * cr
                    x[i + k] = ur + vr; y[i + k] = ui + vi
                    x[i + k + len / 2] = ur - vr; y[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

@Composable
fun SpectrumBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    bands: Int = 36,
    mirrored: Boolean = true,
    onBeat: (Float) -> Unit = {},
) {
    val c = LocalAura.current
    val spec = remember(bands) { Spectrum(bands) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, bands) {
        while (true) {
            withFrameNanos { tick = it }
            val fresh = System.nanoTime() - AudioEngine.fx.lastWriteNs < 400_000_000L
            spec.update(playing && fresh)
            onBeat((spec.levels[0] + spec.levels[1] + spec.levels[2]) / 3f)
            if (!playing && spec.levels.all { it < 0.01f }) break
        }
    }
    Canvas(modifier) {
        tick
        val gap = size.width / bands
        val bw = (gap * 0.52f).coerceAtLeast(2f)
        for (i in 0 until bands) {
            val v = spec.levels[i].coerceAtLeast(0.05f)
            val h = v * size.height * (if (mirrored) 0.48f else 0.95f)
            val x = i * gap + (gap - bw) / 2
            val y = if (mirrored) size.height / 2 - h else size.height - h
            val col = if (v > 0.65f) c.accent else c.accent.copy(alpha = 0.45f + v * 0.5f)
            drawRoundRect(col, Offset(x, y), Size(bw, if (mirrored) h * 2 else h), CornerRadius(bw / 2))
        }
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
