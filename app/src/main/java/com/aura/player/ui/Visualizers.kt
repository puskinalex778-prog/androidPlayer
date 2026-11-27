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
    PARTY("Party"),
    LEDS_62("62 LED"),
    PIXELS("Пиксели"),
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
    private val rawLevels = FloatArray(bands)

    val levels = FloatArray(bands)
    val peaks = FloatArray(bands)
    val sparkY = FloatArray(bands)
    val sparkTrailY = FloatArray(bands)
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

    // Адаптивное усиление (AGC) + детектор бита и стробоскопа
    private var agcPeak = 0.55f
    private var prevBass = 0f
    private var prevHigh = 0f
    var beatFlash = 0f
    var strobeFlash = 0f
    var partyHue = 195f

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
                wave[i] = wave[i] * 0.42f + buf[i * step] * 0.58f
            }
            for (i in 0 until n) {
                re[i] = buf[i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
        } else {
            for (i in wave.indices) wave[i] *= 0.85f
        }

        var rawMax = 0f
        for (b in 0 until bands) {
            var raw = 0f
            if (active) {
                val a = edges[b]
                val e = max(edges[b + 1], a + 1)
                var peak = 0f
                for (k in a until e) peak = max(peak, hypot(re[k], im[k]))
                val db = 20f * ln(peak + 1e-6f) / ln(10f)
                val tilt = (b.toFloat() / bands) * 6.5f
                raw = ((db + 12f + tilt) / 42f).coerceIn(0f, 1f)
            }
            rawLevels[b] = raw
            if (raw > rawMax) rawMax = raw
        }

        agcPeak = max(rawMax, agcPeak * 0.992f).coerceAtLeast(0.32f)

        var bSum = 0f
        var mSum = 0f
        var hSum = 0f
        val bEnd = (bands * 0.22f).toInt().coerceAtLeast(1)
        val mEnd = (bands * 0.65f).toInt().coerceAtLeast(bEnd + 1)

        for (b in 0 until bands) {
            val norm = if (active) (rawLevels[b] / agcPeak).coerceIn(0f, 1f).pow(1.30f) else 0f
            val cur = if (norm > levels[b]) levels[b] * 0.30f + norm * 0.70f else levels[b] * 0.82f + norm * 0.18f
            levels[b] = cur
            peaks[b] = if (cur >= peaks[b]) cur else (peaks[b] - 0.015f).coerceAtLeast(0f)

            // физика пиксельных искр + шлейф
            sparkTrailY[b] = sparkTrailY[b] * 0.68f + sparkY[b] * 0.32f
            if (cur > sparkY[b] + 0.03f) {
                sparkY[b] = cur
                sparkV[b] = 0.022f + (cur - sparkY[b]).coerceAtLeast(0f) * 0.06f
            } else {
                sparkY[b] += sparkV[b]
                sparkV[b] -= 0.0024f
                if (sparkY[b] < cur) { sparkY[b] = cur; sparkV[b] = 0f }
                if (sparkY[b] > 1f) { sparkY[b] = 1f; sparkV[b] = -0.01f }
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
        energy = (bass * 0.50f + mid * 0.35f + high * 0.15f).coerceIn(0f, 1f)
        peakEnergy = if (energy >= peakEnergy) energy else (peakEnergy - 0.013f).coerceAtLeast(0f)

        // Детектор удара (Beat) и стробоскопа (Strobe)
        val bassDelta = bass - prevBass
        val highDelta = high - prevHigh
        if (active && bassDelta > 0.09f && bass > 0.30f) {
            beatFlash = 1f
            partyHue = (partyHue + 38f) % 360f
        } else {
            beatFlash *= 0.84f
        }
        if (active && (bassDelta > 0.14f || highDelta > 0.14f) && energy > 0.42f) {
            strobeFlash = 1f
        } else {
            strobeFlash *= 0.72f
        }
        prevBass = bass
        prevHigh = high
        if (active) partyHue = (partyHue + 0.4f + mid * 1.4f) % 360f

        for (i in 0 until 62) {
            val halfIdx = if (i < 31) i else (61 - i)
            val normPos = halfIdx / 30f
            val bandIdx = (normPos * (bands - 1)).toInt().coerceIn(0, bands - 1)
            val beatLit = if (normPos <= energy * 1.12f) 1f else 0f
            val target = max(beatLit * 0.88f, levels[bandIdx]).coerceIn(0f, 1f)
            leds62[i] = if (target > leds62[i]) leds62[i] * 0.3f + target * 0.7f else leds62[i] * 0.78f + target * 0.22f
            ledPeaks62[i] = if (leds62[i] >= ledPeaks62[i]) leds62[i] else (ledPeaks62[i] - 0.02f).coerceAtLeast(0f)
        }

        phase += 0.028f + bass * 0.085f
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
            if (!playing && analyzer.levels.all { it < 0.01f } && analyzer.peaks.all { it < 0.01f } &&
                analyzer.leds62.all { it < 0.01f } && analyzer.strobeFlash < 0.01f
            ) break
        }
    }

    Canvas(modifier) {
        tick
        when (type) {
            VisualizerType.BARS -> drawBarsWithPeaks(analyzer, c.accent, c.accent2, c.text, large)
            VisualizerType.PARTY -> drawParty(analyzer, large)
            VisualizerType.LEDS_62 -> draw62Leds(analyzer, c.accent, c.surface2, c.border, large)
            VisualizerType.PIXELS -> drawPixels(analyzer, c.accent, c.accent2, c.surface2, c.text, large)
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

// 2. PARTY — клубная сцена с прожекторами, лазерами и стробоскопами в такт музыки
private fun DrawScope.drawParty(a: FrequencyAnalyzer, large: Boolean) {
    val w = size.width
    val h = size.height

    // Тёмный клубный фон + вспышка стробоскопа
    drawRoundRect(Color(0xFF070811), Offset.Zero, size, CornerRadius(10f))
    if (a.strobeFlash > 0.05f) {
        drawRoundRect(
            Color.White.copy(alpha = a.strobeFlash * (if (large) 0.24f else 0.20f)),
            Offset.Zero,
            size,
            CornerRadius(10f),
        )
    }

    val fixtureXs = if (large) floatArrayOf(0.11f, 0.30f, 0.50f, 0.70f, 0.89f) else floatArrayOf(0.14f, 0.38f, 0.62f, 0.86f)
    val trussY = if (large) 12f else 5f

    // Ферма (балка) с прожекторами наверху
    drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, trussY), Offset(w, trussY), if (large) 3f else 1.5f)

    // Прожекторы (объёмные световые лучи-конусы)
    fixtureXs.forEachIndexed { idx, xf ->
        val fx = w * xf
        val fy = trussY
        val hue = (a.partyHue + idx * 58f) % 360f
        val beamCol = Color.hsv(hue, 0.92f, 1f)
        val bandIdx = (idx * a.bands / fixtureXs.size).coerceIn(0, a.bands - 1)
        val intensity = (0.35f + a.levels[bandIdx] * 0.45f + a.beatFlash * 0.35f).coerceIn(0.2f, 1f)

        val sweep = sin(a.phase * (0.9f + idx * 0.18f) + idx * 1.45f) * w * (if (large) 0.42f else 0.34f)
        val targetX = (fx + sweep).coerceIn(-w * 0.15f, w * 1.15f)
        val halfSpread = w * (if (large) 0.08f + a.bass * 0.09f else 0.06f + a.bass * 0.07f)
        val lensHalf = if (large) 7f else 3.5f

        val cone = Path().apply {
            moveTo(fx - lensHalf, fy)
            lineTo(fx + lensHalf, fy)
            lineTo(targetX + halfSpread, h)
            lineTo(targetX - halfSpread, h)
            close()
        }
        drawPath(
            cone,
            Brush.verticalGradient(
                listOf(beamCol.copy(alpha = 0.55f * intensity), beamCol.copy(alpha = 0.04f)),
                startY = fy,
                endY = h,
            ),
        )
        // Блик на полу от луча
        if (large) {
            drawOval(
                Brush.radialGradient(listOf(beamCol.copy(alpha = 0.32f * intensity), Color.Transparent), Offset(targetX, h - 6f), halfSpread),
                Offset(targetX - halfSpread, h - 18f),
                Size(halfSpread * 2, 24f),
            )
        }
        // Светящаяся линза прожектора
        drawCircle(beamCol.copy(alpha = 0.45f), lensHalf * 2.2f, Offset(fx, fy))
        drawCircle(beamCol, lensHalf, Offset(fx, fy))
        drawCircle(Color.White.copy(alpha = 0.85f), lensHalf * 0.45f, Offset(fx, fy))
    }

    // Лазерные лучи на средних/высоких частотах
    if (a.mid > 0.25f || a.high > 0.25f) {
        val origin = Offset(w * 0.5f, trussY + 2f)
        val laserCount = if (large) 6 else 4
        val laserCol = Color.hsv((a.partyHue + 170f) % 360f, 0.95f, 1f)
        for (i in 0 until laserCount) {
            val frac = i.toFloat() / (laserCount - 1) - 0.5f
            val swing = sin(a.phase * 1.7f + i) * 0.18f
            val endX = w * (0.5f + (frac + swing) * 1.15f)
            drawLine(
                laserCol.copy(alpha = (0.28f + a.high * 0.65f).coerceIn(0f, 0.9f)),
                origin,
                Offset(endX, h),
                strokeWidth = if (large) 2.2f else 1.2f,
            )
        }
    }

    // Стробоскопы (мощные белые/голубые вспышки-звёзды в такт пикам)
    if (a.strobeFlash > 0.20f) {
        val stAlpha = a.strobeFlash
        val pods = if (large) listOf(Offset(w * 0.20f, trussY + 4f), Offset(w * 0.80f, trussY + 4f), Offset(w * 0.50f, h * 0.22f))
        else listOf(Offset(w * 0.26f, h * 0.35f), Offset(w * 0.74f, h * 0.35f))
        val podRadius = if (large) w * 0.24f else w * 0.16f
        pods.forEach { p ->
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * stAlpha), Color(0xFF38BDF8).copy(alpha = 0.4f * stAlpha), Color.Transparent), p, podRadius),
                podRadius,
                p,
            )
            // Крестообразные лучи блика
            val len = podRadius * 0.8f * stAlpha
            drawLine(Color.White.copy(alpha = 0.9f * stAlpha), Offset(p.x - len, p.y), Offset(p.x + len, p.y), if (large) 3f else 1.5f)
            drawLine(Color.White.copy(alpha = 0.9f * stAlpha), Offset(p.x, p.y - len), Offset(p.x, p.y + len), if (large) 3f else 1.5f)
        }
    }

    // Нижний клубный эквалайзер танцпола
    val bars = if (large) 28 else 24
    val bw = w / bars
    for (i in 0 until bars) {
        val bIdx = (i * a.bands / bars).coerceIn(0, a.bands - 1)
        val bh = a.levels[bIdx] * h * (if (large) 0.28f else 0.40f)
        val col = Color.hsv((a.partyHue + i * 9f) % 360f, 0.9f, 1f)
        drawRoundRect(
            col.copy(alpha = 0.88f),
            Offset(i * bw + bw * 0.15f, h - bh),
            Size(bw * 0.7f, bh.coerceAtLeast(2f)),
            CornerRadius(2f),
        )
    }
}

// 3. 62 СВЕТОДИОДА РЯДАМИ — ровно 62 реалистичных лампочки в горизонтальных рядах
private fun DrawScope.draw62Leds(a: FrequencyAnalyzer, accent: Color, socketBg: Color, border: Color, large: Boolean) {
    val emerald = Color(0xFF10B981)
    val cyan = Color(0xFF06B6D4)
    val amber = Color(0xFFF59E0B)
    val hotRed = Color(0xFFEF4444)

    fun ledColorFor(pos01: Float): Color = when {
        pos01 >= 0.84f -> hotRed
        pos01 >= 0.62f -> amber
        pos01 >= 0.34f -> lerp(cyan, accent, (pos01 - 0.34f) / 0.28f)
        else -> lerp(emerald, cyan, pos01 / 0.34f)
    }

    fun drawBulb(center: Offset, r: Float, col: Color, on: Boolean, isPeak: Boolean, strength: Float) {
        // металлический патрон-цоколь
        drawCircle(Color(0xFF0F1118), r * 1.06f, center)
        drawCircle(socketBg, r * 0.95f, center)
        drawCircle(border.copy(alpha = 0.55f), r, center, style = Stroke(r * 0.15f))

        if (on) {
            val s = strength.coerceIn(0.45f, 1f)
            val bulbCol = if (isPeak) Color.White else col
            // мягкий внешний ореол
            drawCircle(
                Brush.radialGradient(listOf(col.copy(alpha = 0.60f * s), Color.Transparent), center, r * 2.3f),
                r * 2.3f,
                center,
            )
            // светящийся купол лампочки
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), bulbCol.copy(alpha = s), col.copy(alpha = 0.78f * s)), center, r * 0.92f),
                r * 0.90f,
                center,
            )
            // стеклянный блик сверху слева
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.26f, Offset(center.x - r * 0.28f, center.y - r * 0.28f))
        } else {
            // погашенная цветная колба внутри патрона
            drawCircle(col.copy(alpha = 0.12f), r * 0.72f, center)
            drawCircle(Color.White.copy(alpha = 0.08f), r * 0.22f, Offset(center.x - r * 0.25f, center.y - r * 0.25f))
        }
    }

    if (large) {
        // Большая сцена: РОВНО 62 лампочки в 5 рядах (12 + 13 + 12 + 13 + 12 = 62)
        val rowCounts = intArrayOf(12, 13, 12, 13, 12)
        val numRows = rowCounts.size
        val rowH = size.height / numRows
        val maxCols = 13
        val baseStepX = size.width / maxCols
        val ledR = min(baseStepX * 0.35f, rowH * 0.31f).coerceIn(4.5f, 15f)

        for (rowIdx in 0 until numRows) {
            val count = rowCounts[rowIdx]
            val freqZone = numRows - 1 - rowIdx
            val bStart = (freqZone * a.bands) / numRows
            val bEnd = (((freqZone + 1) * a.bands) / numRows).coerceAtLeast(bStart + 1)
            var zoneLvl = 0f
            var zonePeak = 0f
            for (b in bStart until bEnd) {
                zoneLvl = max(zoneLvl, a.levels[b])
                zonePeak = max(zonePeak, a.peaks[b])
            }
            val level = (zoneLvl * 0.85f + a.beatFlash * 0.18f).coerceIn(0f, 1f)
            val peak = max(level, zonePeak)

            val y = (rowIdx + 0.5f) * rowH
            val rowWidth = count * baseStepX
            val startX = (size.width - rowWidth) * 0.5f

            // планка-корпус ряда
            val railLeft = startX + baseStepX * 0.12f
            val railW = rowWidth - baseStepX * 0.24f
            drawRoundRect(
                Color(0xFF10131C).copy(alpha = 0.55f),
                Offset(railLeft, y - ledR * 1.28f),
                Size(railW, ledR * 2.56f),
                CornerRadius(ledR * 1.28f),
            )
            drawRoundRect(
                border.copy(alpha = 0.5f),
                Offset(railLeft, y - ledR * 1.28f),
                Size(railW, ledR * 2.56f),
                CornerRadius(ledR * 1.28f),
                style = Stroke(1.2f),
            )

            val litCount = (level * count * 1.05f).toInt().coerceIn(0, count)
            val peakIdx = (peak * (count - 1)).toInt().coerceIn(0, count - 1)

            for (i in 0 until count) {
                val x = startX + (i + 0.5f) * baseStepX
                val pos01 = i.toFloat() / max(1, count - 1)
                val col = ledColorFor(pos01)
                val isPeakBulb = peak > 0.10f && i == peakIdx
                val isLit = i < litCount || isPeakBulb
                drawBulb(Offset(x, y), ledR, col, isLit, isPeakBulb && i >= litCount, 0.75f + 0.25f * level)
            }
        }
    } else {
        // Компактная полоса: РОВНО 62 лампочки в 2 ряда по 31 диоду (31 × 2 = 62)
        val perRow = 31
        val stepX = size.width / perRow
        val ledR = min(stepX * 0.34f, size.height * 0.18f).coerceAtLeast(1.9f)
        val yTop = size.height * 0.30f
        val yBot = size.height * 0.72f

        val litTop = (a.energy * perRow * 1.05f).toInt().coerceIn(0, perRow)
        val peakTop = (a.peakEnergy * (perRow - 1)).toInt().coerceIn(0, perRow - 1)

        for (i in 0 until perRow) {
            val pos01 = i / 30f
            val x = (i + 0.5f) * stepX
            val col = ledColorFor(pos01)

            val isPeakTop = a.peakEnergy > 0.10f && i == peakTop
            val topOn = i < litTop || isPeakTop
            drawBulb(Offset(x, yTop), ledR, col, topOn, isPeakTop && i >= litTop, 1f)

            val bIdx = (i * a.bands / perRow).coerceIn(0, a.bands - 1)
            val botLvl = a.levels[bIdx]
            drawBulb(Offset(x, yBot), ledR, col, botLvl > 0.22f, false, 0.55f + botLvl * 0.45f)
        }
    }
}

// 4. ПИКСЕЛИ — 16-bit Neo-Arcade мозаика с объёмным бликом, шлейфом искр и CRT-отражением
private fun DrawScope.drawPixels(a: FrequencyAnalyzer, accent: Color, accent2: Color, offBg: Color, peakCol: Color, large: Boolean) {
    val cols = if (large) 22 else 28
    val cell = size.width / cols
    val rows = max(4, (size.height / cell).toInt())
    val gap = (cell * 0.16f).coerceAtLeast(1.5f)
    val pxSize = (cell - gap).coerceAtLeast(2f)
    val yOffset = size.height - rows * cell

    for (col in 0 until cols) {
        val bIdx = (col * a.bands / cols).coerceIn(0, a.bands - 1)
        val lvl = a.levels[bIdx]
        val litRows = (lvl * rows).toInt().coerceIn(0, rows)
        val sparkRow = (a.sparkY[bIdx] * (rows - 1)).toInt().coerceIn(0, rows - 1)
        val trailRow = (a.sparkTrailY[bIdx] * (rows - 1)).toInt().coerceIn(0, rows - 1)

        for (r in 0 until rows) {
            val x = col * cell + gap * 0.5f
            val y = yOffset + (rows - 1 - r) * cell + gap * 0.5f
            val frac = r.toFloat() / max(1, rows - 1)
            val isLit = r < litRows
            val isSpark = a.sparkY[bIdx] > 0.08f && r == sparkRow
            val isTrail = a.sparkTrailY[bIdx] > 0.10f && r == trailRow && r >= litRows && r != sparkRow

            val color = when {
                isSpark -> peakCol
                isTrail -> Color(0xFFF43F5E).copy(alpha = 0.45f)
                isLit -> when {
                    frac > 0.80f -> Color(0xFFF43F5E)
                    frac > 0.58f -> lerp(Color(0xFFFACC15), Color(0xFFF43F5E), (frac - 0.58f) / 0.22f)
                    frac > 0.30f -> lerp(accent2, Color(0xFFFACC15), (frac - 0.30f) / 0.28f)
                    else -> lerp(accent, accent2, frac / 0.30f)
                }
                else -> offBg.copy(alpha = 0.32f)
            }

            if (isSpark) {
                // ореол вокруг подпрыгивающего пикселя-искры
                drawRect(accent2.copy(alpha = 0.42f), Offset(x - pxSize * 0.25f, y - pxSize * 0.25f), Size(pxSize * 1.5f, pxSize * 1.5f))
            }
            // строгий квадратный пиксель
            drawRect(color, Offset(x, y), Size(pxSize, pxSize))

            // 16-bit внутренний блик и тень на горящих пикселях
            if (isLit || isSpark) {
                val inset = (pxSize * 0.22f).coerceAtLeast(1f)
                drawRect(
                    Color.White.copy(alpha = if (isSpark) 0.85f else 0.26f),
                    Offset(x + inset * 0.4f, y + inset * 0.4f),
                    Size((pxSize - inset * 1.2f).coerceAtLeast(1f), (pxSize * 0.32f).coerceAtLeast(1f)),
                )
            }
        }
    }

    // Неоновое отражение от нижнего края на большой сцене
    if (large) {
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.14f + a.beatFlash * 0.12f))),
            Offset(0f, size.height * 0.78f),
            Size(size.width, size.height * 0.22f),
        )
    }
}

// 5. Волна + осциллограмма
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

// 6. Кольцо 360°
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

// 7. Hi-Fi LED-матрица
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

// 8. Энергия (частицы)
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

// 9. Горизонт
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
