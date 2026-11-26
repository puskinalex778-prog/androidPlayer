package com.aura.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.ArtLoader
import com.aura.player.data.Country
import com.aura.player.data.Station
import com.aura.player.playback.AudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ===================== Фон «живой градиент» =====================

@Composable
fun AuraBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val c = LocalAura.current
    val tr = rememberInfiniteTransition(label = "bg")
    val t by tr.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "t")
    val alpha = (if (c.dark) 0.33f else 0.22f) * intensity
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.bg)
        val r = max(size.width, size.height) * 0.75f
        fun blob(color: Color, cx: Float, cy: Float, rad: Float) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), rad), rad, Offset(cx, cy))
        }
        blob(c.accent, size.width * (0.2f + 0.15f * cos(t)), size.height * (0.12f + 0.08f * sin(t * 2)), r)
        blob(c.accent2, size.width * (0.9f + 0.1f * sin(t)), size.height * (0.45f + 0.12f * cos(t)), r * 0.8f)
        blob(c.accent3, size.width * (0.3f + 0.2f * sin(t + 1)), size.height * (0.95f + 0.05f * cos(t * 2)), r * 0.9f)
    }
}

// ===================== Стекло =====================

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAura.current
    var m = modifier.clip(shape).background(c.glass).border(1.dp, c.glassBorder, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m.padding(padding), content = content)
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: String? = null) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(50)
    val bg = if (selected) Brush.horizontalGradient(listOf(c.accent, c.accent2)) else Brush.linearGradient(listOf(c.glass, c.glass))
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else c.glassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Text(leading, fontSize = 15.sp)
        Text(
            text, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else c.text,
            maxLines = 1,
        )
    }
}

// ===================== Обложки =====================

@Composable
fun rememberArt(uri: String?, size: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = uri?.let { ArtLoader.cached(it, size)?.asImageBitmap() }, uri, size) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { ArtLoader.load(ctx, uri, size)?.asImageBitmap() }
    }
    return state.value
}

@Composable
fun ArtThumb(uri: String?, seed: String, modifier: Modifier = Modifier, size: Dp = 52.dp, corner: Dp = 14.dp) {
    val art = rememberArt(uri, 160)
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (art != null) {
            Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(seedGradient(seed)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size * 0.45f))
            }
        }
    }
}

fun seedGradient(seed: String): Brush {
    val h = (seed.hashCode() and 0x7fffffff) % 360
    val c1 = Color.hsv(h.toFloat(), 0.65f, 0.85f)
    val c2 = Color.hsv(((h + 50) % 360).toFloat(), 0.75f, 0.55f)
    return Brush.linearGradient(listOf(c1, c2))
}

@Composable
fun StationLogo(st: Station, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val shape = RoundedCornerShape(18.dp)
    val initials = st.name.split(' ', '-', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
    Box(modifier.size(size).clip(shape).background(seedGradient(st.name)), contentAlignment = Alignment.Center) {
        Text(initials.ifBlank { "FM" }, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.32f).sp, maxLines = 1)
        Text(
            Country.of(st.country)?.flag ?: "📻",
            fontSize = (size.value * 0.22f).sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp),
        )
    }
}

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ===================== Вертикальный слайдер (эквалайзер) =====================

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
        val w = 6.dp.toPx()
        val x = size.width / 2
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val h = bottom - top
        drawRoundRect(c.text.copy(alpha = 0.12f), Offset(x - w / 2, top), Size(w, h), CornerRadius(w))
        val zeroY = top + h * (1f - (0f - range.start) / span)
        val vy = top + h * (1f - (value - range.start) / span)
        val alpha = if (enabled) 1f else 0.35f
        drawRoundRect(
            Brush.verticalGradient(listOf(c.accent2.copy(alpha = alpha), c.accent.copy(alpha = alpha))),
            Offset(x - w / 2, min(zeroY, vy)), Size(w, kotlin.math.abs(zeroY - vy).coerceAtLeast(1f)), CornerRadius(w),
        )
        drawCircle(Color.White.copy(alpha = alpha), 9.dp.toPx(), Offset(x, vy))
        drawCircle(c.accent.copy(alpha = alpha), 5.dp.toPx(), Offset(x, vy))
    }
}

// ===================== Визуализатор спектра =====================

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

/** Полоски спектра. Возвращает «энергию» баса через onBeat — для пульсации обложки */
@Composable
fun SpectrumBars(playing: Boolean, modifier: Modifier = Modifier, bands: Int = 40, mirrored: Boolean = true, onBeat: (Float) -> Unit = {}) {
    val c = LocalAura.current
    val spec = remember { Spectrum(bands) }
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(playing) {
        while (true) {
            withFrameNanos { tick = it }
            val fresh = System.nanoTime() - AudioEngine.fx.lastWriteNs < 400_000_000L
            spec.update(playing && fresh)
            onBeat((spec.levels[0] + spec.levels[1] + spec.levels[2]) / 3f)
            if (!playing && spec.levels.all { it < 0.01f }) break
        }
    }
    Canvas(modifier) {
        tick // перерисовка каждый кадр
        val gap = size.width / bands
        val bw = gap * 0.55f
        val brush = Brush.verticalGradient(listOf(c.accent2, c.accent, c.accent3.copy(alpha = 0.6f)))
        for (i in 0 until bands) {
            val v = spec.levels[i].coerceAtLeast(0.03f)
            val h = v * size.height * (if (mirrored) 0.5f else 1f)
            val x = i * gap + (gap - bw) / 2
            val y = if (mirrored) size.height / 2 - h else size.height - h
            drawRoundRect(brush, Offset(x, y), Size(bw, if (mirrored) h * 2 else h), CornerRadius(bw / 2))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = LocalAura.current.text, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        action?.invoke()
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(emoji, fontSize = 52.sp)
        Text(title, style = MaterialTheme.typography.titleLarge, color = LocalAura.current.text)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalAura.current.textDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Suppress("unused")
private fun sq(x: Float) = sqrt(x)
