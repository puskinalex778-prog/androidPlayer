package com.aura.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aura.player.playback.AudioEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class VisualizerType(val title: String) {
    BARS("Спектр"),
    WAVE("Волна"),
    RADIAL("Кольцо"),
    LED_VU("LED-дека"),
    PARTICLES("Энергия"),
    MIRROR("Горизонт");

    fun next(): VisualizerType = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String): VisualizerType = entries.firstOrNull { it.name == name } ?: BARS
    }
}

private class FrequencyAnalyzer(val bands: Int) {
    private val n = 1024
    private val buf = FloatArray(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { (0.5f - 0.5f * cos(2 * PI * it / (n - 1))).toFloat() }

    val levels = FloatArray(bands)
    val peaks = FloatArray(bands)
    val wave = FloatArray(96)
    var bass = 0f
    var mid = 0f
    var high = 0f
    var phase = 0f

    private val edges = IntArray(bands + 1) { i ->
        val fMin = 35.0
        val fMax = 15500.0
        val f = fMin * (fMax / fMin).pow(i / bands.toDouble())
        (f / 44100.0 * n).toInt().coerceIn(1, n / 2 - 1)
    }

    fun update(active: Boolean) {
        if (active) {
            AudioEngine.fx.snapshot(buf)
            // прореженная осциллограмма
            val step = n / wave.size
            for (i in wave.indices) {
                wave[i] = wave[i] * 0.45f + buf[i * step] * 0.55f
            }
            for (i in 0 until n) {
                re[i] = buf[i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
        } else {
            for (i in wave.indices) wave[i] *= 0.85f
        }

        var bSum = 0f
        var mSum = 0f
        var hSum = 0f
        val bEnd = (bands * 0.2f).toInt().coerceAtLeast(1)
        val mEnd = (bands * 0.65f).toInt().coerceAtLeast(bEnd + 1)

        for (b in 0 until bands) {
            var target = 0f
            if (active) {
                val a = edges[b]
                val e = max(edges[b + 1], a + 1)
                var peak = 0f
                for (k in a until e) peak = max(peak, hypot(re[k], im[k]))
                val db = 20f * ln(peak + 1e-6f) / ln(10f)
                // лёгкая компенсация высоких частот, чтобы верхний спектр тоже «жил»
                val tilt = (b.toFloat() / bands) * 7f
                target = ((db + 11f + tilt) / 44f).coerceIn(0f, 1f)
            }
            val cur = if (target > levels[b]) levels[b] * 0.35f + target * 0.65f else levels[b] * 0.85f + target * 0.15f
            levels[b] = cur
            peaks[b] = if (cur >= peaks[b]) cur else (peaks[b] - 0.014f).coerceAtLeast(0f)

            when {
                b < bEnd -> bSum += cur
                b < mEnd -> mSum += cur
                else -> hSum += cur
            }
        }
        bass = bSum / bEnd
        mid = mSum / (mEnd - bEnd)
        high = hSum / max(1, bands - mEnd)
        phase += 0.025f + bass * 0.07f
    }

    private fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = x[i]; x[i] = x[j]; x[j] = t
                t = y[i]; y[i] = y[j]; y[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
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

/**
 * Универсальный визуализатор: работает и в компактной полосе, и на большой сцене плеера.
 */
@Composable
fun AudioVisualizer(
    type: VisualizerType,
    playing: Boolean,
    modifier: Modifier = Modifier,
    bands: Int = 36,
    large: Boolean = false,
    onBeat: (Float) -> Unit = {},
) {
    val c = LocalAura.current
    val analyzer = remember(bands) { FrequencyAnalyzer(bands) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(playing, bands) {
        while (true) {
            withFrameNanos { tick = it }
            val fresh = System.nanoTime() - AudioEngine.fx.lastWriteNs < 420_000_000L
            analyzer.update(playing && fresh)
            onBeat(analyzer.bass)
            if (!playing && analyzer.levels.all { it < 0.01f } && analyzer.peaks.all { it < 0.01f }) break
        }
    }

    Canvas(modifier) {
        tick
        when (type) {
            VisualizerType.BARS -> drawBarsWithPeaks(analyzer, c.accent, c.accent2, c.text, large)
            VisualizerType.WAVE -> drawFrequencyWave(analyzer, c.accent, c.accent2, large)
            VisualizerType.RADIAL -> drawRadialRing(analyzer, c.accent, c.accent2, c.border, large)
            VisualizerType.LED_VU -> drawLedMatrix(analyzer, c.surface2, large)
            VisualizerType.PARTICLES -> drawEnergyParticles(analyzer, c.accent, c.accent2, large)
            VisualizerType.MIRROR -> drawMirrorSkyline(analyzer, c.accent, c.accent2, c.border)
        }
    }
}

// 1. Спектр с пиковыми индикаторами
private fun DrawScope.drawBarsWithPeaks(a: FrequencyAnalyzer, accent: Color, accent2: Color, text: Color, large: Boolean) {
    val count = a.bands
    val gap = size.width / count
    val bw = (gap * 0.56f).coerceAtLeast(2f)
    val brush = Brush.verticalGradient(listOf(accent2, accent))
    val capH = if (large) 3.5f else 2f

    for (i in 0 until count) {
        val v = a.levels[i].coerceAtLeast(0.03f)
        val h = v * size.height * 0.92f
        val x = i * gap + (gap - bw) / 2
        val y = size.height - h
        drawRoundRect(brush, Offset(x, y), Size(bw, h), CornerRadius(bw / 2))

        val pk = a.peaks[i]
        if (pk > 0.05f) {
            val py = (size.height - pk * size.height * 0.92f - capH * 1.6f).coerceAtLeast(0f)
            drawRoundRect(text.copy(alpha = 0.85f), Offset(x, py), Size(bw, capH), CornerRadius(capH))
        }
    }
}

// 2. Плавная частотная волна + осциллограмма
private fun DrawScope.drawFrequencyWave(a: FrequencyAnalyzer, accent: Color, accent2: Color, large: Boolean) {
    val cy = size.height * 0.5f
    val n = a.bands
    val dx = size.width / max(1, n - 1)

    // верхняя частотная огибающая
    val topPath = Path()
    val fillPath = Path()
    for (i in 0 until n) {
        val x = i * dx
        val amp = a.levels[i] * size.height * (if (large) 0.44f else 0.46f)
        val y = cy - amp
        if (i == 0) {
            topPath.moveTo(x, y)
            fillPath.moveTo(x, cy)
            fillPath.lineTo(x, y)
        } else {
            val px = (i - 1) * dx
            val py = cy - a.levels[i - 1] * size.height * (if (large) 0.44f else 0.46f)
            val mx = (px + x) * 0.5f
            topPath.cubicTo(mx, py, mx, y, x, y)
            fillPath.cubicTo(mx, py, mx, y, x, y)
        }
    }
    for (i in n - 1 downTo 0) {
        val x = i * dx
        val y = cy + a.levels[i] * size.height * (if (large) 0.34f else 0.38f)
        fillPath.lineTo(x, y)
    }
    fillPath.close()

    drawPath(fillPath, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent2.copy(alpha = 0.08f))))
    drawPath(topPath, accent, style = Stroke(width = if (large) 3.5f else 2.2f, cap = StrokeCap.Round))

    // живая осциллограмма по центру
    val wavePath = Path()
    val wDx = size.width / max(1, a.wave.size - 1)
    for (i in a.wave.indices) {
        val x = i * wDx
        val y = cy + a.wave[i] * size.height * (if (large) 0.42f else 0.45f)
        if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
    }
    drawPath(wavePath, accent2.copy(alpha = 0.9f), style = Stroke(width = if (large) 2.2f else 1.5f, cap = StrokeCap.Round))
}

// 3. Кольцо 360° (в компактном режиме рисует два кольца или широкую дугу)
private fun DrawScope.drawRadialRing(a: FrequencyAnalyzer, accent: Color, accent2: Color, border: Color, large: Boolean) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val minDim = min(size.width, size.height)
    if (!large && size.height < size.width * 0.35f) {
        // в узкой полосе — симметричный частотный пульс из центра
        val half = a.bands / 2
        val gap = (size.width * 0.5f) / max(1, half)
        val bw = (gap * 0.55f).coerceAtLeast(2f)
        for (i in 0 until half) {
            val v = a.levels[i].coerceAtLeast(0.06f)
            val h = v * size.height * 0.9f
            val xR = cx + i * gap
            val xL = cx - (i + 1) * gap
            drawRoundRect(accent, Offset(xR, cy - h / 2), Size(bw, h), CornerRadius(bw))
            drawRoundRect(accent2, Offset(xL, cy - h / 2), Size(bw, h), CornerRadius(bw))
        }
        return
    }
    val baseR = minDim * (0.24f + a.bass * 0.06f)
    val maxBar = minDim * 0.22f

    // пульсирующее басовое ядро
    drawCircle(accent.copy(alpha = 0.14f + a.bass * 0.2f), baseR * (1f + a.bass * 0.25f), Offset(cx, cy))
    drawCircle(border, baseR, Offset(cx, cy), style = Stroke(2f))
    drawCircle(accent, baseR * (0.45f + a.mid * 0.25f), Offset(cx, cy), style = Stroke(3f))

    val count = a.bands
    val strokeW = (2 * PI * baseR / count * 0.55).toFloat().coerceIn(2.5f, 9f)
    for (i in 0 until count) {
        val ang = (i.toFloat() / count) * 2f * PI.toFloat() - (PI / 2f).toFloat() + a.phase * 0.12f
        val len = (0.06f + a.levels[i]) * maxBar
        val ca = cos(ang)
        val sa = sin(ang)
        val p1 = Offset(cx + ca * (baseR + 4f), cy + sa * (baseR + 4f))
        val p2 = Offset(cx + ca * (baseR + 4f + len), cy + sa * (baseR + 4f + len))
        val col = if (i < count / 3) accent else accent2
        drawLine(col, p1, p2, strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

// 4. Hi-Fi LED-матрица (зелёный -> янтарный -> красный)
private fun DrawScope.drawLedMatrix(a: FrequencyAnalyzer, trackBg: Color, large: Boolean) {
    val cols = if (large) 20 else 24
    val rows = if (large) 14 else 6
    val cw = size.width / cols
    val rh = size.height / rows
    val padX = cw * 0.18f
    val padY = rh * 0.20f

    val green = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val red = Color(0xFFEF4444)

    for (col in 0 until cols) {
        val bandIdx = (col * a.bands / cols).coerceIn(0, a.bands - 1)
        val lvl = a.levels[bandIdx]
        val pk = a.peaks[bandIdx]
        val lit = (lvl * rows).toInt().coerceIn(0, rows)
        val peakRow = (pk * rows).toInt().coerceIn(0, rows - 1)

        for (r in 0 until rows) {
            val y = size.height - (r + 1) * rh + padY * 0.5f
            val x = col * cw + padX * 0.5f
            val w = (cw - padX).coerceAtLeast(2f)
            val h = (rh - padY).coerceAtLeast(2f)
            val frac = r.toFloat() / rows
            val ledCol = when {
                frac >= 0.78f -> red
                frac >= 0.52f -> amber
                else -> green
            }
            val active = r < lit || (pk > 0.08f && r == peakRow)
            drawRoundRect(
                if (active) ledCol else trackBg.copy(alpha = 0.65f),
                Offset(x, y),
                Size(w, h),
                CornerRadius(2.5f),
            )
        }
    }
}

// 5. Частотные частицы и басовые волны
private fun DrawScope.drawEnergyParticles(a: FrequencyAnalyzer, accent: Color, accent2: Color, large: Boolean) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val minDim = min(size.width, size.height)

    // басовые кольца
    if (large) {
        for (k in 0..2) {
            val prog = ((a.phase * 0.35f + k * 0.33f) % 1f)
            val rad = minDim * (0.12f + prog * 0.42f) * (0.9f + a.bass * 0.25f)
            drawCircle(accent.copy(alpha = (1f - prog) * (0.25f + a.bass * 0.5f)), rad, Offset(cx, cy), style = Stroke(3f))
        }
    }

    val count = if (large) 28 else 20
    for (i in 0 until count) {
        val band = (i * a.bands / count).coerceIn(0, a.bands - 1)
        val e = a.levels[band]
        val x = if (large) {
            val ang = i * 2.39996f + a.phase * (0.25f + (i % 3) * 0.12f)
            val dist = minDim * (0.10f + (i.toFloat() / count) * 0.36f) * (0.85f + e * 0.45f)
            cx + cos(ang) * dist
        } else {
            (i + 0.5f) * (size.width / count)
        }
        val y = if (large) {
            val ang = i * 2.39996f + a.phase * (0.25f + (i % 3) * 0.12f)
            val dist = minDim * (0.10f + (i.toFloat() / count) * 0.36f) * (0.85f + e * 0.45f)
            cy + sin(ang) * dist
        } else {
            cy + sin(a.phase * 1.4f + i * 0.55f) * (size.height * 0.32f) * (0.3f + e)
        }
        val radius = (if (large) 4f + e * 18f else 2.5f + e * 8f)
        val col = if (i % 2 == 0) accent else accent2
        drawCircle(col.copy(alpha = 0.25f + e * 0.45f), radius * 1.55f, Offset(x, y))
        drawCircle(col, radius, Offset(x, y))
    }
}

// 6. Зеркальный горизонт
private fun DrawScope.drawMirrorSkyline(a: FrequencyAnalyzer, accent: Color, accent2: Color, border: Color) {
    val cy = size.height * 0.55f
    val count = a.bands
    val gap = size.width / count
    val bw = (gap * 0.62f).coerceAtLeast(2f)

    drawLine(border, Offset(0f, cy), Offset(size.width, cy), 1.5f)

    for (i in 0 until count) {
        val v = a.levels[i].coerceAtLeast(0.03f)
        val hUp = v * cy * 0.92f
        val hDown = v * (size.height - cy) * 0.85f
        val x = i * gap + (gap - bw) / 2
        drawRoundRect(accent, Offset(x, cy - hUp - 1f), Size(bw, hUp), CornerRadius(bw * 0.4f))
        drawRoundRect(accent2.copy(alpha = 0.32f), Offset(x, cy + 2f), Size(bw, hDown), CornerRadius(bw * 0.4f))
    }
}
