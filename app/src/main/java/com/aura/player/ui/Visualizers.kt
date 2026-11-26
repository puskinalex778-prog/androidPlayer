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
import androidx.compose.ui.graphics.lerp
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
    PIXELS("Пиксели"),
    LEDS_62("62 LED"),
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
    val sparkY = FloatArray(bands) // плавающие пиксельные искры
    val sparkV = FloatArray(bands)
    val wave = FloatArray(96)
    val leds62 = FloatArray(62)
    val ledPeaks62 = FloatArray(62)
    var bass = 0f
    var mid = 0f
    var high = 0f
    var energy = 0f
    var peakEnergy = 0f
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
                val tilt = (b.toFloat() / bands) * 7f
                target = ((db + 11f + tilt) / 44f).coerceIn(0f, 1f)
            }
            val cur = if (target > levels[b]) levels[b] * 0.35f + target * 0.65f else levels[b] * 0.85f + target * 0.15f
            levels[b] = cur
            peaks[b] = if (cur >= peaks[b]) cur else (peaks[b] - 0.014f).coerceAtLeast(0f)

            // физика пиксельных искр
            if (cur > sparkY[b] + 0.04f) {
                sparkY[b] = cur
                sparkV[b] = 0.018f + (cur - sparkY[b]).coerceAtLeast(0f) * 0.05f
            } else {
                sparkY[b] += sparkV[b]
                sparkV[b] -= 0.0022f
                if (sparkY[b] < cur) { sparkY[b] = cur; sparkV[b] = 0f }
                if (sparkY[b] > 1f) { sparkY[b] = 1f; sparkV[b] = -0.008f }
            }

            when {
                b < bEnd -> bSum += cur
                b < mEnd -> mSum += cur
                else -> hSum += cur
            }
        }
        bass = bSum / bEnd
        mid = mSum / (mEnd - bEnd)
        high = hSum / max(1, bands - mEnd)
        energy = (bass * 0.5f + mid * 0.35f + high * 0.15f).coerceIn(0f, 1f)
        peakEnergy = if (energy >= peakEnergy) energy else (peakEnergy - 0.012f).coerceAtLeast(0f)

        // обновляем 62 индивидуальных светодиода (симметрично по 31 на канал/полукольцо + частотный отклик)
        for (i in 0 until 62) {
            val halfIdx = if (i < 31) i else (61 - i)
            val normPos = halfIdx / 30f
            val bandIdx = ((normPos) * (bands - 1)).toInt().coerceIn(0, bands - 1)
            val beatLit = if (normPos <= energy * 1.15f) 1f else 0f
            val freqLit = levels[bandIdx]
            val target = max(beatLit * 0.92f, freqLit * 1.05f).coerceIn(0f, 1f)
            leds62[i] = if (target > leds62[i]) leds62[i] * 0.3f + target * 0.7f else leds62[i] * 0.80f + target * 0.20f
            ledPeaks62[i] = if (leds62[i] >= ledPeaks62[i]) leds62[i] else (ledPeaks62[i] - 0.02f).coerceAtLeast(0f)
        }

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
            if (!playing && analyzer.levels.all { it < 0.01f } && analyzer.peaks.all { it < 0.01f } && analyzer.leds62.all { it < 0.01f }) break
        }
    }

    Canvas(modifier) {
        tick
        when (type) {
            VisualizerType.BARS -> drawBarsWithPeaks(analyzer, c.accent, c.accent2, c.text, large)
            VisualizerType.PIXELS -> drawPixels(analyzer, c.accent, c.accent2, c.surface2, c.text, large)
            VisualizerType.LEDS_62 -> draw62Leds(analyzer, c.accent, c.accent2, c.surface2, c.border, large)
            VisualizerType.WAVE -> drawFrequencyWave(analyzer, c.accent, c.accent2, large)
            VisualizerType.RADIAL -> drawRadialRing(analyzer, c.accent, c.accent2, c.border, large)
            VisualizerType.LED_VU -> drawLedMatrix(analyzer, c.surface2, large)
            VisualizerType.PARTICLES -> drawEnergyParticles(analyzer, c.accent, c.accent2, large)
            VisualizerType.MIRROR -> drawMirrorSkyline(analyzer, c.accent, c.accent2, c.border)
        }
    }
}

// 1. Спектр с пиками
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

// 2. ПИКСЕЛИ — ретро пиксельная мозаика с подпрыгивающими пикселями-искрами
private fun DrawScope.drawPixels(a: FrequencyAnalyzer, accent: Color, accent2: Color, offBg: Color, peakCol: Color, large: Boolean) {
    val cols = if (large) 24 else 28
    val cell = size.width / cols
    val rows = max(4, (size.height / cell).toInt())
    val gap = (cell * 0.18f).coerceAtLeast(1.5f)
    val pxSize = (cell - gap).coerceAtLeast(2f)
    val yOffset = size.height - rows * cell

    for (col in 0 until cols) {
        val bIdx = (col * a.bands / cols).coerceIn(0, a.bands - 1)
        val lvl = a.levels[bIdx]
        val litRows = (lvl * rows).toInt().coerceIn(0, rows)
        val sparkRow = (a.sparkY[bIdx] * (rows - 1)).toInt().coerceIn(0, rows - 1)

        for (r in 0 until rows) {
            val x = col * cell + gap * 0.5f
            val y = yOffset + (rows - 1 - r) * cell + gap * 0.5f
            val frac = r.toFloat() / max(1, rows - 1)
            val isLit = r < litRows
            val isSpark = a.sparkY[bIdx] > 0.08f && r == sparkRow

            val color = when {
                isSpark -> peakCol
                isLit -> when {
                    frac > 0.80f -> Color(0xFFF43F5E)
                    frac > 0.55f -> lerp(accent2, Color(0xFFFACC15), (frac - 0.55f) / 0.25f)
                    else -> lerp(accent, accent2, frac / 0.55f)
                }
                else -> offBg.copy(alpha = 0.42f)
            }
            // Строгие квадратные пиксели без скругления!
            drawRect(color, Offset(x, y), Size(pxSize, pxSize))
        }
    }
}

// 3. 62 СВЕТОДИОДА — ровно 62 физических диода, загорающихся в такт музыки
private fun DrawScope.draw62Leds(a: FrequencyAnalyzer, accent: Color, accent2: Color, socketBg: Color, border: Color, large: Boolean) {
    val total = 62
    val emerald = Color(0xFF10B981)
    val cyan = Color(0xFF06B6D4)
    val amber = Color(0xFFF59E0B)
    val hotRed = Color(0xFFEF4444)

    fun ledColorFor(pos01: Float): Color = when {
        pos01 >= 0.84f -> hotRed
        pos01 >= 0.64f -> amber
        pos01 >= 0.34f -> lerp(cyan, accent, (pos01 - 0.34f) / 0.30f)
        else -> lerp(emerald, cyan, pos01 / 0.34f)
    }

    if (large) {
        // Большая сцена: прецизионное 360° кольцо из РОВНО 62 круглых светодиодов (31 левая дуга + 31 правая дуга)
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val minDim = min(size.width, size.height)
        val ringR = minDim * 0.40f
        val ledR = (2 * PI * ringR / total * 0.36).toFloat().coerceIn(2.6f, 6.5f)

        // декоративный контур платы и центральное ядро, реагирующее на бас
        drawCircle(border.copy(alpha = 0.45f), ringR, Offset(cx, cy), style = Stroke(ledR * 2.3f))
        drawCircle(accent.copy(alpha = 0.12f + a.bass * 0.22f), ringR * (0.46f + a.bass * 0.12f), Offset(cx, cy))
        drawCircle(accent.copy(alpha = 0.65f), ringR * (0.46f + a.bass * 0.12f), Offset(cx, cy), style = Stroke(2.2f))

        // Внутренняя пульсирующая корона (низкие / средние / высокие)
        drawCircle(accent2.copy(alpha = 0.45f), ringR * (0.28f + a.mid * 0.16f), Offset(cx, cy), style = Stroke(2f))
        drawCircle(emerald.copy(alpha = 0.55f), ringR * (0.12f + a.high * 0.14f), Offset(cx, cy))

        for (i in 0 until total) {
            // Располагаем 62 диода равномерно по кругу, начиная снизу (90°)
            val halfIdx = if (i < 31) i else (61 - i)
            val pos01 = halfIdx / 30f
            val angle = if (i < 31) {
                (PI / 2.0) + (halfIdx + 0.5) / 31.0 * PI
            } else {
                (PI / 2.0) - (halfIdx + 0.5) / 31.0 * PI
            }
            val lx = cx + (cos(angle) * ringR).toFloat()
            val ly = cy + (sin(angle) * ringR).toFloat()
            val center = Offset(lx, ly)

            val intensity = a.leds62[i]
            val isLit = intensity > 0.22f
            val col = ledColorFor(pos01)

            // корпус неактивного диода
            drawCircle(socketBg, ledR, center)
            if (isLit) {
                val glowAlpha = (intensity * 0.45f).coerceIn(0.15f, 0.55f)
                drawCircle(col.copy(alpha = glowAlpha), ledR * 2.15f, center)
                drawCircle(col, ledR * 0.96f, center)
                drawCircle(Color.White.copy(alpha = 0.72f * intensity), ledR * 0.36f, center)
            } else {
                drawCircle(col.copy(alpha = 0.12f), ledR * 0.75f, center)
            }
        }
    } else {
        // Компактная полоса: РОВНО 62 светодиода в 2 аккуратных ряда по 31 диоду (31 × 2 = 62)
        val perRow = 31
        val stepX = size.width / perRow
        val ledR = min(stepX * 0.34f, size.height * 0.17f).coerceAtLeast(1.8f)
        val yTop = size.height * 0.30f
        val yBot = size.height * 0.72f

        val litTop = (a.energy * perRow * 1.12f).toInt().coerceIn(0, perRow)
        val peakTop = (a.peakEnergy * (perRow - 1)).toInt().coerceIn(0, perRow - 1)

        for (i in 0 until perRow) {
            val pos01 = i / 30f
            val x = (i + 0.5f) * stepX
            val col = ledColorFor(pos01)

            // Верхний ряд (31 LED): уровень в такт баса и общей громкости + пиковый диод
            val topOn = i < litTop || (a.peakEnergy > 0.08f && i == peakTop)
            drawCircle(socketBg, ledR, Offset(x, yTop))
            if (topOn) {
                drawCircle(col.copy(alpha = 0.35f), ledR * 1.85f, Offset(x, yTop))
                drawCircle(col, ledR, Offset(x, yTop))
                drawCircle(Color.White.copy(alpha = 0.65f), ledR * 0.35f, Offset(x, yTop))
            } else {
                drawCircle(col.copy(alpha = 0.10f), ledR * 0.75f, Offset(x, yTop))
            }

            // Нижний ряд (31 LED): частотный отклик по 31 полосе (итого 31 + 31 = 62 LED!)
            val bIdx = (i * a.bands / perRow).coerceIn(0, a.bands - 1)
            val botLvl = a.levels[bIdx]
            val botOn = botLvl > 0.24f
            drawCircle(socketBg, ledR, Offset(x, yBot))
            if (botOn) {
                drawCircle(col.copy(alpha = 0.32f * botLvl), ledR * 1.8f, Offset(x, yBot))
                drawCircle(col.copy(alpha = (0.45f + botLvl * 0.55f).coerceIn(0f, 1f)), ledR, Offset(x, yBot))
                drawCircle(Color.White.copy(alpha = 0.55f * botLvl), ledR * 0.32f, Offset(x, yBot))
            } else {
                drawCircle(col.copy(alpha = 0.10f), ledR * 0.75f, Offset(x, yBot))
            }
        }
    }
}

// 4. Волна + осциллограмма
private fun DrawScope.drawFrequencyWave(a: FrequencyAnalyzer, accent: Color, accent2: Color, large: Boolean) {
    val cy = size.height * 0.5f
    val n = a.bands
    val dx = size.width / max(1, n - 1)

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

    val wavePath = Path()
    val wDx = size.width / max(1, a.wave.size - 1)
    for (i in a.wave.indices) {
        val x = i * wDx
        val y = cy + a.wave[i] * size.height * (if (large) 0.42f else 0.45f)
        if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
    }
    drawPath(wavePath, accent2.copy(alpha = 0.9f), style = Stroke(width = if (large) 2.2f else 1.5f, cap = StrokeCap.Round))
}

// 5. Кольцо 360°
private fun DrawScope.drawRadialRing(a: FrequencyAnalyzer, accent: Color, accent2: Color, border: Color, large: Boolean) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val minDim = min(size.width, size.height)
    if (!large && size.height < size.width * 0.35f) {
        val half = a.bands / 2
        val gap = (size.width * 0.5f) / max(1, half)
        val bw = (gap * 0.55f).coerceAtLeast(2f)
        for (i in 0 until half) {
            val v = a.levels[i].coerceAtLeast(0.06f)
            val h = v * size.height * 0.9f
            drawRoundRect(accent, Offset(cx + i * gap, cy - h / 2), Size(bw, h), CornerRadius(bw))
            drawRoundRect(accent2, Offset(cx - (i + 1) * gap, cy - h / 2), Size(bw, h), CornerRadius(bw))
        }
        return
    }
    val baseR = minDim * (0.24f + a.bass * 0.06f)
    val maxBar = minDim * 0.22f

    drawCircle(accent.copy(alpha = 0.14f + a.bass * 0.2f), baseR * (1f + a.bass * 0.25f), Offset(cx, cy))
    drawCircle(border, baseR, Offset(cx, cy), style = Stroke(2f))
    drawCircle(accent, baseR * (0.45f + a.mid * 0.25f), Offset(cx, cy), style = Stroke(3f))

    val count = a.bands
    val strokeW = (2 * PI * baseR / count * 0.55).toFloat().coerceIn(2.5f, 9f)
    for (i in 0 until count) {
        val ang = (i.toFloat() / count) * 2f * PI.toFloat() - (PI / 2f).toFloat() + a.phase * 0.12f
        val len = (0.06f + a.levels[i]) * maxBar
        val ca = cos(ang); val sa = sin(ang)
        drawLine(
            if (i < count / 3) accent else accent2,
            Offset(cx + ca * (baseR + 4f), cy + sa * (baseR + 4f)),
            Offset(cx + ca * (baseR + 4f + len), cy + sa * (baseR + 4f + len)),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }
}

// 6. Hi-Fi LED-матрица
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

// 7. Энергия (частицы)
private fun DrawScope.drawEnergyParticles(a: FrequencyAnalyzer, accent: Color, accent2: Color, large: Boolean) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val minDim = min(size.width, size.height)

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
        } else (i + 0.5f) * (size.width / count)
        val y = if (large) {
            val ang = i * 2.39996f + a.phase * (0.25f + (i % 3) * 0.12f)
            val dist = minDim * (0.10f + (i.toFloat() / count) * 0.36f) * (0.85f + e * 0.45f)
            cy + sin(ang) * dist
        } else cy + sin(a.phase * 1.4f + i * 0.55f) * (size.height * 0.32f) * (0.3f + e)
        val radius = (if (large) 4f + e * 18f else 2.5f + e * 8f)
        val col = if (i % 2 == 0) accent else accent2
        drawCircle(col.copy(alpha = 0.25f + e * 0.45f), radius * 1.55f, Offset(x, y))
        drawCircle(col, radius, Offset(x, y))
    }
}

// 8. Горизонт
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
