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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.aura.player.playback.AudioEngine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

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

/**
 * Единая сдержанная цветовая иерархия для всех визуализаций:
 * глубокий базовый цвет -> основной тон -> бирюзовый/светлый акцент -> редкий почти белый пик.
 */
private class VizPalette(accent: Color, accent2: Color) {
    val bgDeep = Color(0xFF060911)
    val deep: Color
    val mid: Color
    val bright: Color
    val peak: Color

    init {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        if (hsv[1] < 0.08f) {
            // Монохромная тема
            deep = Color(0xFF334155)
            mid = Color(0xFF94A3B8)
            bright = Color(0xFFCBD5E1)
            peak = Color(0xFFF8FAFC)
        } else {
            val baseHue = hsv[0]
            val deepHue = if (baseHue in 190f..265f) (baseHue + 8f) % 360f else baseHue
            val brightHue = (baseHue - 24f + 360f) % 360f
            deep = Color.hsv(deepHue, hsv[1].coerceAtLeast(0.84f), 0.66f)
            mid = lerp(accent, accent2, 0.35f)
            bright = Color.hsv(brightHue, (hsv[1] * 0.92f).coerceIn(0.68f, 0.92f), 0.96f)
            peak = lerp(bright, Color.White, 0.85f)
        }
    }

    /** Цвет по частотной оси: 0 = бас (глубокий), 0.5 = середина, 1 = верх (светлый/бирюзовый). */
    fun freq(pos01: Float): Color {
        val t = pos01.coerceIn(0f, 1f)
        return if (t < 0.48f) lerp(deep, mid, t / 0.48f) else lerp(mid, bright, (t - 0.48f) / 0.52f)
    }

    /** Цвет по амплитуде с редким почти белым акцентом на самых пиках. */
    fun amp(level01: Float): Color {
        val t = level01.coerceIn(0f, 1f)
        return when {
            t < 0.48f -> lerp(deep, mid, t / 0.48f)
            t < 0.86f -> lerp(mid, bright, (t - 0.48f) / 0.38f)
            else -> lerp(bright, peak, (t - 0.86f) / 0.14f)
        }
    }
}

private class EnergyParticle(
    val layer: Int, // 0 = крупные (бас), 1 = средние (середина), 2 = пыль/искры (высокие)
    val baseRadiusFrac: Float,
    var angle: Float,
    val orbitSpeed: Float,
    val phaseOffset: Float,
    val sizeSeed: Float,
) {
    var radOffset = 0f
    var radVel = 0f
    var glow = 0f
    val trailX = FloatArray(4)
    val trailY = FloatArray(4)
    var trailCount = 0
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
    private val peakHold = FloatArray(bands)
    private val peakVel = FloatArray(bands)

    // Точная 24-полосная матрица для LED-деки (24x12)
    val bands24 = 24
    private val raw24 = FloatArray(bands24)
    val levels24 = FloatArray(bands24)
    val peaks24 = FloatArray(bands24)
    private val peakHold24 = FloatArray(bands24)
    private val peakVel24 = FloatArray(bands24)
    val sparkMatrix = FloatArray(bands24 * 12)

    // Физика пиксельных искр
    val sparkY = FloatArray(bands)
    val sparkTrailY = FloatArray(bands)
    val sparkV = FloatArray(bands)

    // 62 светодиода с аналоговым послесвечением
    val leds62 = FloatArray(62)
    val ledAfterglow62 = FloatArray(62)
    val ledPeaks62 = FloatArray(62)

    // Горизонтально текущая история звуковой волны (для «Волны»)
    val streamLen = 132
    val streamMain = FloatArray(streamLen)
    private var streamScrollAcc = 0f
    var streamFrac = 0f
        private set
    private var waveSampleFilter = 0f
    private var waveSubPhase = 0f

    // Переиспользуемые Path для отрисовки без лишних аллокаций
    val pathMain = Path()
    val pathFill = Path()
    val pathBack1 = Path()
    val pathBack2 = Path()

    // Три слоя частиц для «Энергии»
    val particles: List<EnergyParticle> = buildList {
        val rnd = Random(42)
        // 7 крупных частиц (управляются басом)
        for (i in 0 until 7) {
            add(
                EnergyParticle(
                    layer = 0,
                    baseRadiusFrac = 0.20f + rnd.nextFloat() * 0.36f,
                    angle = (i.toFloat() / 7f) * 2f * PI.toFloat() + rnd.nextFloat() * 0.45f,
                    orbitSpeed = (if (i % 2 == 0) 1f else -1f) * (0.08f + rnd.nextFloat() * 0.06f),
                    phaseOffset = rnd.nextFloat() * 6.28f,
                    sizeSeed = 0.85f + rnd.nextFloat() * 0.30f,
                )
            )
        }
        // 16 средних частиц (закручиваются серединой)
        for (i in 0 until 16) {
            add(
                EnergyParticle(
                    layer = 1,
                    baseRadiusFrac = 0.26f + rnd.nextFloat() * 0.56f,
                    angle = (i.toFloat() / 16f) * 2f * PI.toFloat() + rnd.nextFloat() * 0.35f,
                    orbitSpeed = 0.16f + rnd.nextFloat() * 0.16f,
                    phaseOffset = rnd.nextFloat() * 6.28f,
                    sizeSeed = 0.8f + rnd.nextFloat() * 0.4f,
                )
            )
        }
        // 28 мелких светящихся искр-пылинок (подпитываются высокими частотами)
        for (i in 0 until 28) {
            add(
                EnergyParticle(
                    layer = 2,
                    baseRadiusFrac = 0.18f + rnd.nextFloat() * 0.72f,
                    angle = rnd.nextFloat() * 6.28f,
                    orbitSpeed = (if (i % 3 == 0) -1f else 1f) * (0.28f + rnd.nextFloat() * 0.36f),
                    phaseOffset = rnd.nextFloat() * 6.28f,
                    sizeSeed = 0.7f + rnd.nextFloat() * 0.6f,
                )
            )
        }
    }

    var bass = 0f
    var mid = 0f
    var high = 0f
    var energy = 0f
    var peakEnergy = 0f
    var phase = 0f
    var bassHit = 0f
    var bassPulse = 0f
    var strobeFlash = 0f
    var partyHue = 205f

    private var agcPeak = 0.55f
    private var agc24 = 0.55f
    private var bassAvg = 0f
    private var prevHigh = 0f
    private var prevTimeNs = 0L
    private var trailTickAcc = 0f
    private val sparkRnd = Random(1337)

    private val edges = buildEdges(bands)
    private val edges24 = buildEdges(bands24)

    private fun buildEdges(count: Int) = IntArray(count + 1) { i ->
        val fMin = 38.0
        val fMax = 15500.0
        val f = fMin * (fMax / fMin).pow(i / count.toDouble())
        (f / 44100.0 * n).toInt().coerceIn(1, n / 2 - 1)
    }

    fun update(active: Boolean, frameNs: Long) {
        val dt = if (prevTimeNs == 0L) 0.016f else ((frameNs - prevTimeNs) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
        prevTimeNs = frameNs

        var rms = 0f
        if (active) {
            AudioEngine.fx.snapshot(buf)
            var sumSq = 0f
            for (i in 0 until n) {
                val s = buf[i]
                sumSq += s * s
                re[i] = s * window[i]
                im[i] = 0f
            }
            rms = kotlin.math.sqrt(sumSq / n)
            fft(re, im)
        }

        // Динамический коэффициент: в тишине не задираем громкость в 100%, на пиках оставляем запас
        val loudnessScale = if (active) (0.35f + (rms * 5.2f).coerceIn(0f, 0.72f)).coerceIn(0.35f, 1.0f) else 0f

        var rawMax = 0f
        for (b in 0 until bands) {
            var raw = 0f
            if (active) {
                val a = edges[b]
                val e = max(edges[b + 1], a + 1)
                var peak = 0f
                for (k in a until e) peak = max(peak, hypot(re[k], im[k]))
                val db = 20f * ln(peak + 1e-6f) / ln(10f)
                val tilt = (b.toFloat() / bands) * 6.0f
                raw = ((db + 12f + tilt) / 42f).coerceIn(0f, 1f)
            }
            rawLevels[b] = raw
            if (raw > rawMax) rawMax = raw
        }
        agcPeak = max(rawMax, agcPeak * exp(-dt * 0.45f)).coerceAtLeast(0.36f)

        var rawMax24 = 0f
        for (b in 0 until bands24) {
            var raw = 0f
            if (active) {
                val a = edges24[b]
                val e = max(edges24[b + 1], a + 1)
                var peak = 0f
                for (k in a until e) peak = max(peak, hypot(re[k], im[k]))
                val db = 20f * ln(peak + 1e-6f) / ln(10f)
                val tilt = (b.toFloat() / bands24) * 6.0f
                raw = ((db + 12f + tilt) / 42f).coerceIn(0f, 1f)
            }
            raw24[b] = raw
            if (raw > rawMax24) rawMax24 = raw
        }
        agc24 = max(rawMax24, agc24 * exp(-dt * 0.45f)).coerceAtLeast(0.36f)

        val atkFactor = 1f - exp(-dt * 30f)
        val decFactor = 1f - exp(-dt * 7.8f)

        var bSum = 0f
        var mSum = 0f
        var hSum = 0f
        val bEnd = (bands * 0.22f).toInt().coerceAtLeast(1)
        val mEnd = (bands * 0.65f).toInt().coerceAtLeast(bEnd + 1)

        for (b in 0 until bands) {
            val norm = if (active) {
                val x = ((rawLevels[b] / agcPeak).coerceIn(0f, 1f)).pow(1.32f) * loudnessScale
                softCap(x * 1.12f)
            } else 0f

            val cur = if (norm > levels[b]) {
                levels[b] + (norm - levels[b]) * atkFactor
            } else {
                levels[b] + (norm - levels[b]) * decFactor
            }
            levels[b] = cur

            // Баллистика пиков с коротким зависанием и плавным падением
            if (cur >= peaks[b]) {
                peaks[b] = cur
                peakHold[b] = 0.16f
                peakVel[b] = 0f
            } else if (peakHold[b] > 0f) {
                peakHold[b] = (peakHold[b] - dt).coerceAtLeast(0f)
            } else {
                peakVel[b] += dt * 1.45f
                peaks[b] = (peaks[b] - peakVel[b] * dt).coerceAtLeast(cur)
            }

            // Физика пиксельных искр
            sparkTrailY[b] = sparkTrailY[b] * exp(-dt * 12f) + sparkY[b] * (1f - exp(-dt * 12f))
            if (cur > sparkY[b] + 0.035f) {
                sparkY[b] = cur
                sparkV[b] = 0.45f + (cur - sparkY[b]).coerceAtLeast(0f) * 1.1f
            } else {
                sparkY[b] += sparkV[b] * dt
                sparkV[b] -= 2.2f * dt
                if (sparkY[b] < cur) {
                    sparkY[b] = cur
                    sparkV[b] = 0f
                }
                if (sparkY[b] > 0.98f) {
                    sparkY[b] = 0.98f
                    sparkV[b] = -0.25f
                }
            }

            when {
                b < bEnd -> bSum += cur
                b < mEnd -> mSum += cur
                else -> hSum += cur
            }
        }

        // Обновление 24 полос и матрицы искр для LED-деки
        for (b in 0 until bands24) {
            val norm = if (active) {
                val x = ((raw24[b] / agc24).coerceIn(0f, 1f)).pow(1.28f) * loudnessScale
                softCap(x * 1.15f)
            } else 0f
            levels24[b] = if (norm > levels24[b]) {
                levels24[b] + (norm - levels24[b]) * atkFactor
            } else {
                levels24[b] + (norm - levels24[b]) * decFactor
            }
            val lvl = levels24[b]
            if (lvl >= peaks24[b]) {
                peaks24[b] = lvl
                peakHold24[b] = 0.15f
                peakVel24[b] = 0f
            } else if (peakHold24[b] > 0f) {
                peakHold24[b] = (peakHold24[b] - dt).coerceAtLeast(0f)
            } else {
                peakVel24[b] += dt * 1.35f
                peaks24[b] = (peaks24[b] - peakVel24[b] * dt).coerceAtLeast(lvl)
            }

            // Плавное затухание и зажигание искр в этой частотной полосе
            val sparkDecay = exp(-dt * 5.8f)
            for (r in 0 until 12) {
                val idx = b * 12 + r
                sparkMatrix[idx] *= sparkDecay
            }
            if (active && lvl > 0.09f) {
                val maxRow = (lvl * 11.5f).toInt().coerceIn(0, 11)
                if (sparkRnd.nextFloat() < lvl * 0.72f + (if (b < 8) bassHit * 0.35f else 0f)) {
                    val row = sparkRnd.nextInt(maxRow + 1)
                    sparkMatrix[b * 12 + row] = (0.65f + lvl * 0.35f).coerceIn(0f, 1f)
                }
                if (peaks24[b] > 0.32f && sparkRnd.nextFloat() < 0.22f) {
                    val pRow = (peaks24[b] * 11f).toInt().coerceIn(0, 11)
                    sparkMatrix[b * 12 + pRow] = 1f
                }
            }
        }

        bass = bSum / bEnd
        mid = mSum / (mEnd - bEnd)
        high = hSum / max(1, bands - mEnd)
        energy = (bass * 0.50f + mid * 0.35f + high * 0.15f).coerceIn(0f, 1f)
        peakEnergy = if (energy >= peakEnergy) energy else (peakEnergy - dt * 0.48f).coerceAtLeast(0f)

        // Детектор басового удара (быстрая атака, мягкий спад)
        bassAvg = bassAvg * exp(-dt * 3.5f) + bass * (1f - exp(-dt * 3.5f))
        val bassJump = bass - bassAvg
        if (active && bassJump > 0.065f && bass > 0.22f) {
            bassHit = max(bassHit, ((bassJump * 5.5f).coerceIn(0.45f, 1f)))
            partyHue = (partyHue + 28f) % 360f
        } else {
            bassHit *= exp(-dt * 7.5f)
        }
        bassPulse = bassPulse + ((bass * 0.6f + bassHit * 0.4f) - bassPulse) * (1f - exp(-dt * 16f))

        val highDelta = high - prevHigh
        if (active && (bassJump > 0.11f || highDelta > 0.13f) && energy > 0.38f) {
            strobeFlash = 1f
        } else {
            strobeFlash *= exp(-dt * 12f)
        }
        prevHigh = high

        // 62 LED с аналоговым послесвечением
        for (i in 0 until 62) {
            val halfIdx = if (i < 31) i else (61 - i)
            val normPos = halfIdx / 30f
            val bandIdx = (normPos * (bands - 1)).toInt().coerceIn(0, bands - 1)
            val beatLit = if (normPos <= energy * 1.05f) 0.86f else 0f
            val target = max(beatLit, levels[bandIdx]).coerceIn(0f, 1f)
            leds62[i] = if (target > leds62[i]) {
                leds62[i] + (target - leds62[i]) * atkFactor
            } else {
                leds62[i] + (target - leds62[i]) * (1f - exp(-dt * 5.2f))
            }
            ledAfterglow62[i] = max(leds62[i], ledAfterglow62[i] * exp(-dt * 2.4f))
            ledPeaks62[i] = if (leds62[i] >= ledPeaks62[i]) leds62[i] else (ledPeaks62[i] - dt * 0.55f).coerceAtLeast(0f)
        }

        // Обновление горизонтально текущей истории звука для «Волны»
        updateWaveStream(active, dt, rms)

        // Обновление 3 слоёв частиц для «Энергии»
        updateParticles(active, dt)

        phase += dt * (1.25f + bass * 2.1f)
        if (active) partyHue = (partyHue + dt * (12f + mid * 38f)) % 360f
    }

    private fun updateWaveStream(active: Boolean, dt: Float, rms: Float) {
        val flowSpeed = if (active) 44f + energy * 18f else 22f
        streamScrollAcc += dt * flowSpeed

        // Формируем свежее плавное сглаженное значение звукового сигнала для ленты
        var freshVal = 0f
        if (active) {
            var acc = 0f
            val span = 48
            val start = n - span
            for (i in start until n) acc += buf[i]
            val meanRecent = acc / span
            waveSubPhase += dt * (11f + mid * 24f + bass * 14f)
            val ampEnv = softCap((rms * 3.1f + energy * 0.45f)).coerceIn(0f, 0.92f)
            val harmonic = sin(waveSubPhase) * 0.62f + sin(waveSubPhase * 1.9f + 0.7f) * 0.25f + meanRecent * 2.6f
            freshVal = tanh(harmonic * (0.45f + ampEnv * 1.15f)) * 0.85f
        }
        waveSampleFilter = waveSampleFilter * 0.55f + freshVal * 0.45f

        var steps = streamScrollAcc.toInt()
        if (steps > 4) steps = 4
        streamScrollAcc -= steps
        streamFrac = streamScrollAcc.coerceIn(0f, 1f)

        repeat(steps) {
            for (i in 0 until streamLen - 1) {
                streamMain[i] = streamMain[i + 1]
            }
            streamMain[streamLen - 1] = waveSampleFilter
        }
        if (steps == 0 && !active) {
            for (i in 0 until streamLen) streamMain[i] *= exp(-dt * 4f)
        }
    }

    private fun updateParticles(active: Boolean, dt: Float) {
        trailTickAcc += dt
        val pushTrail = trailTickAcc >= 0.022f
        if (pushTrail) trailTickAcc = 0f

        for (p in particles) {
            when (p.layer) {
                0 -> {
                    // Крупные — реагируют на удар баса (небольшой разлёт от центра + возврат)
                    if (active && bassHit > 0.35f) {
                        p.radVel += bassHit * 0.42f * dt * 8f
                    }
                    val springForce = -p.radOffset * 14f
                    p.radVel = (p.radVel + springForce * dt) * exp(-dt * 4.5f)
                    p.radOffset = (p.radOffset + p.radVel * dt).coerceIn(-0.08f, 0.26f)
                    p.angle += p.orbitSpeed * dt * (if (active) 1f + bass * 0.6f else 0.25f)
                    p.glow = if (active) (0.35f + bass * 0.55f + bassHit * 0.25f).coerceIn(0.25f, 1f) else p.glow * exp(-dt * 3f)
                }
                1 -> {
                    // Средние — закручиваются потоком середины
                    val spin = if (active) (0.35f + mid * 2.2f) else 0.15f
                    p.angle += p.orbitSpeed * spin * dt
                    p.radOffset = sin(phase * 0.7f + p.phaseOffset) * (0.04f + mid * 0.06f)
                    p.glow = if (active) (0.28f + mid * 0.62f).coerceIn(0.2f, 0.95f) else p.glow * exp(-dt * 3.5f)
                }
                else -> {
                    // Мелкая пыль/искры — активируются высокими частотами
                    val speed = if (active) (0.45f + high * 2.8f + mid * 0.6f) else 0.12f
                    p.angle += p.orbitSpeed * speed * dt
                    p.radOffset = cos(phase * 1.1f + p.phaseOffset) * (0.03f + high * 0.09f)
                    val targetGlow = if (active) (0.12f + high * 0.88f).coerceIn(0.10f, 1f) else 0f
                    p.glow += (targetGlow - p.glow) * (1f - exp(-dt * 12f))
                }
            }

            val r = (p.baseRadiusFrac + p.radOffset).coerceIn(0.06f, 0.94f)
            val nx = cos(p.angle) * r
            val ny = sin(p.angle) * r

            if (!p.trailReadyInit) {
                for (k in 0 until 4) {
                    p.trailX[k] = nx
                    p.trailY[k] = ny
                }
                p.trailCount = 4
            } else if (pushTrail) {
                for (k in 3 downTo 1) {
                    p.trailX[k] = p.trailX[k - 1]
                    p.trailY[k] = p.trailY[k - 1]
                }
                p.trailX[0] = nx
                p.trailY[0] = ny
            } else {
                p.trailX[0] = nx
                p.trailY[0] = ny
            }
        }
    }

    private val EnergyParticle.trailReadyInit: Boolean
        get() = trailCount >= 4

    private fun softCap(x: Float): Float = if (x <= 0f) 0f else x / (1f + x * 0.22f)

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
    ledDeckSparks: Boolean = false,
    onBeat: (Float) -> Unit = {},
) {
    val c = LocalAura.current
    val analyzer = remember(bands) { FrequencyAnalyzer(bands) }
    val palette = remember(c.accent, c.accent2) { VizPalette(c.accent, c.accent2) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(playing, bands) {
        while (true) {
            withFrameNanos { frameNs ->
                tick = frameNs
                val fresh = System.nanoTime() - AudioEngine.fx.lastWriteNs < 420_000_000L
                analyzer.update(playing && fresh, frameNs)
                onBeat(analyzer.bassPulse)
            }
            if (!playing && analyzer.levels.all { it < 0.008f } && analyzer.peaks.all { it < 0.008f } &&
                analyzer.leds62.all { it < 0.008f } && analyzer.ledAfterglow62.all { it < 0.008f } &&
                analyzer.strobeFlash < 0.008f && analyzer.bassHit < 0.008f
            ) break
        }
    }

    Canvas(modifier) {
        tick
        if (large) {
            // Единый чистый глубокий тёмный фон с минимальной атмосферной глубиной в центре
            drawRoundRect(palette.bgDeep, Offset.Zero, size, CornerRadius(14f))
            val bgCenter = Offset(size.width * 0.5f, size.height * 0.5f)
            val rad = max(size.width, size.height) * 0.62f
            drawCircle(
                Brush.radialGradient(
                    listOf(palette.deep.copy(alpha = 0.08f + analyzer.bassPulse * 0.04f), Color.Transparent),
                    bgCenter,
                    rad,
                ),
                rad,
                bgCenter,
            )
        }
        clipRect {
            when (type) {
                VisualizerType.BARS -> drawBarsPro(analyzer, palette, large)
                VisualizerType.PARTY -> drawPartyPro(analyzer, palette, large)
                VisualizerType.LEDS_62 -> draw62LedsPro(analyzer, palette, large)
                VisualizerType.PIXELS -> drawPixelsPro(analyzer, palette, large)
                VisualizerType.WAVE -> drawFlowingWave(analyzer, palette, large)
                VisualizerType.RADIAL -> drawRadialPro(analyzer, palette, large)
                VisualizerType.LED_VU -> drawLedDeckPro(analyzer, palette, large, ledDeckSparks)
                VisualizerType.PARTICLES -> drawEnergyCloud(analyzer, palette, large)
                VisualizerType.MIRROR -> drawHorizonPro(analyzer, palette, large)
            }
        }
    }
}

// 1. «Спектр» — изящные капсулы с локальным свечением вершин, пиками и тонким стеклянным отражением
private fun DrawScope.drawBarsPro(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val count = a.bands
    val padX = if (large) size.width * 0.05f else 2f
    val usableW = (size.width - padX * 2f).coerceAtLeast(10f)
    val gap = usableW / count
    val bw = (gap * 0.54f).coerceIn(2f, if (large) 12f else 6f)
    val baseLineY = if (large) size.height * 0.84f else size.height * 0.94f
    val maxH = baseLineY - (if (large) size.height * 0.12f else 4f)

    // Тонкая опорная линия
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, p.mid.copy(alpha = 0.22f), Color.Transparent)),
        Offset(padX, baseLineY),
        Offset(size.width - padX, baseLineY),
        if (large) 1.2f else 0.8f,
    )

    for (i in 0 until count) {
        val pos01 = i.toFloat() / max(1, count - 1)
        val col = p.freq(pos01)
        val lvl = a.levels[i]
        val h = (lvl * maxH).coerceAtLeast(if (large) 3f else 2f)
        val x = padX + i * gap + (gap - bw) * 0.5f
        val topY = baseLineY - h

        // Локальное свечение только у активной вершины
        if (lvl > 0.10f) {
            val glowR = bw * (if (large) 2.2f else 1.8f)
            drawCircle(
                Brush.radialGradient(
                    listOf(col.copy(alpha = 0.26f * lvl), Color.Transparent),
                    Offset(x + bw * 0.5f, topY + bw * 0.4f),
                    glowR,
                ),
                glowR,
                Offset(x + bw * 0.5f, topY + bw * 0.4f),
            )
        }

        // Капсула с вертикальным затуханием к основанию
        drawRoundRect(
            Brush.verticalGradient(
                listOf(lerp(col, p.peak, lvl * 0.35f), col.copy(alpha = 0.78f), p.deep.copy(alpha = 0.35f)),
                startY = topY,
                endY = baseLineY,
            ),
            Offset(x, topY),
            Size(bw, h),
            CornerRadius(bw * 0.5f),
        )

        // Лёгкое отражение на стекле снизу
        if (large && lvl > 0.04f) {
            val refH = (h * 0.22f).coerceAtMost(size.height - baseLineY - 4f)
            if (refH > 1f) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(col.copy(alpha = 0.16f), Color.Transparent), baseLineY + 2f, baseLineY + 2f + refH),
                    Offset(x, baseLineY + 2f),
                    Size(bw, refH),
                    CornerRadius(bw * 0.5f),
                )
            }
        }

        // Почти белая отметка пика
        val pk = a.peaks[i]
        if (pk > 0.06f) {
            val py = (baseLineY - pk * maxH - (if (large) 5f else 2.5f)).coerceAtLeast(2f)
            drawRoundRect(
                p.peak.copy(alpha = (0.45f + pk * 0.55f).coerceIn(0f, 1f)),
                Offset(x, py),
                Size(bw, if (large) 2.4f else 1.5f),
                CornerRadius(1.5f),
            )
        }
    }
}

// 2. «Party» — концертный свет DMX с мягкими объёмными лучами, лазерами и точечными бликами
private fun DrawScope.drawPartyPro(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val w = size.width
    val h = size.height
    val floorY = if (large) h * 0.85f else h * 0.90f
    val trussY = if (large) 18f else 5f

    // Мягкая верхняя ферма
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent)),
        Offset(w * 0.06f, trussY),
        Offset(w * 0.94f, trussY),
        if (large) 2f else 1f,
    )

    val fixtureXs = if (large) floatArrayOf(0.14f, 0.34f, 0.50f, 0.66f, 0.86f) else floatArrayOf(0.16f, 0.39f, 0.61f, 0.84f)
    val beamColors = arrayOf(p.deep, p.mid, p.bright, p.mid, p.deep)

    // Многослойные мягкие прожекторные лучи (без резких жёстких треугольников)
    fixtureXs.forEachIndexed { idx, xf ->
        val fx = w * xf
        val fy = trussY
        val baseCol = if (idx == 2 && a.bassHit > 0.4f) Color.hsv((a.partyHue + 35f) % 360f, 0.72f, 1f)
        else lerp(beamColors[idx % beamColors.size], Color.hsv((a.partyHue + idx * 24f) % 360f, 0.75f, 1f), 0.32f)

        val bandIdx = (idx * a.bands / fixtureXs.size).coerceIn(0, a.bands - 1)
        val intensity = (0.28f + a.levels[bandIdx] * 0.45f + a.bassHit * 0.30f).coerceIn(0.2f, 1f)
        val sweep = sin(a.phase * (0.72f + idx * 0.14f) + idx * 1.35f) * w * (if (large) 0.34f else 0.28f)
        val targetX = (fx + sweep).coerceIn(-w * 0.1f, w * 1.1f)
        val spread = w * (if (large) 0.075f + a.bass * 0.055f else 0.055f + a.bass * 0.04f)
        val lensHalf = if (large) 5.5f else 2.8f

        // 3 вложенных слоя луча: широкий туман, среднее тело, яркая сердцевина
        for (pass in 0..2) {
            val scale = when (pass) { 0 -> 1.45f; 1 -> 0.95f; else -> 0.45f }
            val alpha = when (pass) { 0 -> 0.10f; 1 -> 0.20f; else -> 0.32f } * intensity
            a.pathMain.reset()
            a.pathMain.moveTo(fx - lensHalf * scale * 0.6f, fy)
            a.pathMain.lineTo(fx + lensHalf * scale * 0.6f, fy)
            a.pathMain.lineTo(targetX + spread * scale, floorY)
            a.pathMain.lineTo(targetX - spread * scale, floorY)
            a.pathMain.close()
            drawPath(
                a.pathMain,
                Brush.verticalGradient(listOf(baseCol.copy(alpha = alpha), Color.Transparent), fy, floorY),
            )
        }

        // Отражение на полу сцены
        if (large) {
            drawOval(
                Brush.radialGradient(listOf(baseCol.copy(alpha = 0.24f * intensity), Color.Transparent), Offset(targetX, floorY), spread * 1.1f),
                Offset(targetX - spread * 1.1f, floorY - 8f),
                Size(spread * 2.2f, 16f),
            )
        }

        // Линза прожектора (без грязной заливки)
        drawCircle(Brush.radialGradient(listOf(baseCol.copy(alpha = 0.42f * intensity), Color.Transparent), Offset(fx, fy), lensHalf * 3.2f), lensHalf * 3.2f, Offset(fx, fy))
        drawCircle(p.peak.copy(alpha = 0.85f * intensity), lensHalf * 0.65f, Offset(fx, fy))
    }

    // Лазерный веер на высоких/средних
    if (a.mid > 0.18f || a.high > 0.18f) {
        val origin = Offset(w * 0.5f, trussY + 2f)
        val laserCount = if (large) 7 else 5
        val laserCol = p.bright
        for (i in 0 until laserCount) {
            val frac = i.toFloat() / (laserCount - 1) - 0.5f
            val swing = sin(a.phase * 1.45f + i * 0.7f) * 0.14f
            val endX = w * (0.5f + (frac + swing) * 1.05f)
            val lAlpha = (0.14f + a.high * 0.45f).coerceIn(0f, 0.62f)
            drawLine(
                Brush.linearGradient(listOf(p.peak.copy(alpha = lAlpha), laserCol.copy(alpha = lAlpha * 0.45f), Color.Transparent), origin, Offset(endX, floorY)),
                origin,
                Offset(endX, floorY),
                if (large) 1.6f else 1.0f,
            )
        }
    }

    // Локальные блики стробоскопа на пиках (точки + горизонтальный анаморфный блик)
    if (a.strobeFlash > 0.18f) {
        val st = a.strobeFlash
        val pods = if (large) listOf(Offset(w * 0.22f, trussY + 6f), Offset(w * 0.78f, trussY + 6f))
        else listOf(Offset(w * 0.26f, h * 0.3f), Offset(w * 0.74f, h * 0.3f))
        val pr = if (large) w * 0.14f else w * 0.10f
        pods.forEach { pt ->
            drawCircle(
                Brush.radialGradient(listOf(p.peak.copy(alpha = 0.75f * st), p.bright.copy(alpha = 0.22f * st), Color.Transparent), pt, pr),
                pr,
                pt,
            )
            val flareW = pr * 1.6f * st
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, p.peak.copy(alpha = 0.75f * st), Color.Transparent), pt.x - flareW, pt.x + flareW),
                Offset(pt.x - flareW, pt.y),
                Offset(pt.x + flareW, pt.y),
                if (large) 2.2f else 1.2f,
            )
        }
    }

    // Линия пола и аккуратный нижний спектр сцены
    val bars = if (large) 32 else 24
    val pad = if (large) w * 0.06f else 4f
    val step = (w - pad * 2f) / bars
    val bw = (step * 0.5f).coerceAtLeast(1.8f)
    for (i in 0 until bars) {
        val bIdx = (i * a.bands / bars).coerceIn(0, a.bands - 1)
        val lvl = a.levels[bIdx]
        val bh = (lvl * (floorY * if (large) 0.22f else 0.38f)).coerceAtLeast(1.5f)
        val col = p.freq(i.toFloat() / max(1, bars - 1))
        val bx = pad + i * step + (step - bw) * 0.5f
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(col, p.peak, lvl * 0.4f), col.copy(alpha = 0.25f)), floorY - bh, floorY),
            Offset(bx, floorY - bh),
            Size(bw, bh),
            CornerRadius(bw * 0.5f),
        )
    }
}

// 3. «62 LED» — 62 скруглённых диода рядами с аналоговым послесвечением и сдержанной палитрой
private fun DrawScope.draw62LedsPro(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    fun drawCleanLed(center: Offset, side: Float, col: Color, glowLvl: Float, peakSpot: Boolean) {
        val half = side * 0.5f
        val rad = CornerRadius(side * 0.28f)
        val topLeft = Offset(center.x - half, center.y - half)

        // Незажжённый диод едва виден — решётка сохраняется в тишине
        drawRoundRect(Color.White.copy(alpha = 0.045f), topLeft, Size(side, side), rad)

        if (glowLvl > 0.04f || peakSpot) {
            val strength = if (peakSpot) 0.92f else glowLvl.coerceIn(0f, 1f)
            val bodyCol = if (peakSpot) p.peak else col
            // Небольшой контролируемый ореол (не склеивает соседние диоды)
            val haloR = side * 1.15f
            drawCircle(
                Brush.radialGradient(listOf(col.copy(alpha = 0.26f * strength), Color.Transparent), center, haloR),
                haloR,
                center,
            )
            // Тело диода
            drawRoundRect(bodyCol.copy(alpha = (0.22f + 0.78f * strength)), topLeft, Size(side, side), rad)
            // Чёткая светлая сердцевина
            val core = side * 0.48f
            drawRoundRect(
                p.peak.copy(alpha = (0.35f + 0.55f * strength)),
                Offset(center.x - core * 0.5f, center.y - core * 0.5f),
                Size(core, core),
                CornerRadius(core * 0.30f),
            )
        }
    }

    if (large) {
        // Большая сцена: ровно 62 светодиода рядами (12 + 13 + 12 + 13 + 12 = 62)
        val rowCounts = intArrayOf(12, 13, 12, 13, 12)
        val numRows = rowCounts.size
        val padX = size.width * 0.04f
        val padY = size.height * 0.10f
        val usableW = size.width - padX * 2f
        val usableH = size.height - padY * 2f
        val rowH = usableH / numRows
        val stepX = usableW / 13f
        val ledSide = min(stepX * 0.62f, rowH * 0.54f).coerceIn(7f, 22f)

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
            val level = (zoneLvl * 0.88f + a.bassHit * (if (freqZone == 0) 0.14f else 0.04f)).coerceIn(0f, 1f)
            val y = padY + (rowIdx + 0.5f) * rowH
            val rowW = count * stepX
            val startX = (size.width - rowW) * 0.5f

            // Тонкая монтажная дорожка ряда
            drawRoundRect(
                Color.White.copy(alpha = 0.025f),
                Offset(startX + stepX * 0.1f, y - ledSide * 0.78f),
                Size(rowW - stepX * 0.2f, ledSide * 1.56f),
                CornerRadius(ledSide * 0.45f),
            )

            val litCount = (level * count * 1.02f).toInt().coerceIn(0, count)
            val peakIdx = (max(level, zonePeak) * (count - 1)).toInt().coerceIn(0, count - 1)

            for (i in 0 until count) {
                val x = startX + (i + 0.5f) * stepX
                val pos01 = i.toFloat() / max(1, count - 1)
                val col = p.freq(pos01)
                val isPeak = zonePeak > 0.12f && i == peakIdx
                val activeStrength = when {
                    i < litCount -> (0.72f + 0.28f * level)
                    isPeak -> 0.90f
                    else -> {
                        val fade = (level * count - i + 0.65f).coerceIn(0f, 0.45f)
                        fade
                    }
                }
                drawCleanLed(Offset(x, y), ledSide, col, activeStrength, isPeak && i >= litCount)
            }
        }
    } else {
        // Компактная полоса: ровно 62 диода в 2 ряда (2 × 31 = 62)
        val perRow = 31
        val stepX = size.width / perRow
        val ledSide = min(stepX * 0.62f, size.height * 0.34f).coerceAtLeast(3.2f)
        val yTop = size.height * 0.30f
        val yBot = size.height * 0.72f

        val litTop = (a.energy * perRow * 1.02f).toInt().coerceIn(0, perRow)
        val peakTop = (a.peakEnergy * (perRow - 1)).toInt().coerceIn(0, perRow - 1)

        for (i in 0 until perRow) {
            val pos01 = i / 30f
            val x = (i + 0.5f) * stepX
            val col = p.freq(pos01)
            val isPeakTop = a.peakEnergy > 0.12f && i == peakTop
            val topStrength = if (i < litTop) 0.9f else if (isPeakTop) 0.9f else 0f
            drawCleanLed(Offset(x, yTop), ledSide, col, topStrength, isPeakTop && i >= litTop)

            val bIdx = (i * a.bands / perRow).coerceIn(0, a.bands - 1)
            val botLvl = a.levels[bIdx]
            val botGlow = ((botLvl - 0.12f) * 1.15f).coerceIn(0f, 1f)
            drawCleanLed(Offset(x, yBot), ledSide, col, botGlow, false)
        }
    }
}

// 4. «Пиксели» — чёткая OLED-матрица с микробликом, закреплёнными цветами и живыми искрами
private fun DrawScope.drawPixelsPro(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val cols = if (large) 24 else 28
    val padX = if (large) size.width * 0.04f else 2f
    val padY = if (large) size.height * 0.07f else 2f
    val usableW = (size.width - padX * 2f).coerceAtLeast(10f)
    val usableH = (size.height - padY * 2f).coerceAtLeast(8f)

    val cell = usableW / cols
    val rows = max(4, (usableH / cell).toInt())
    val gap = (cell * 0.22f).coerceAtLeast(1.5f)
    val pxSize = (cell - gap).coerceAtLeast(2f)
    val yOffset = padY + (usableH - rows * cell) * 0.5f

    for (col in 0 until cols) {
        val pos01 = col.toFloat() / max(1, cols - 1)
        val baseCol = p.freq(pos01)
        val bIdx = (col * a.bands / cols).coerceIn(0, a.bands - 1)
        val lvl = a.levels[bIdx]
        val litRows = (lvl * rows).toInt().coerceIn(0, rows)
        val sparkRow = (a.sparkY[bIdx] * (rows - 1)).toInt().coerceIn(0, rows - 1)
        val trailRow = (a.sparkTrailY[bIdx] * (rows - 1)).toInt().coerceIn(0, rows - 1)

        for (r in 0 until rows) {
            val x = padX + col * cell + gap * 0.5f
            val y = yOffset + (rows - 1 - r) * cell + gap * 0.5f
            val hFrac = r.toFloat() / max(1, rows - 1)
            val isLit = r < litRows
            val isSpark = a.sparkY[bIdx] > 0.09f && r == sparkRow
            val isTrail = a.sparkTrailY[bIdx] > 0.11f && r == trailRow && r >= litRows && r != sparkRow

            when {
                isSpark -> {
                    val cSpot = Offset(x + pxSize * 0.5f, y + pxSize * 0.5f)
                    drawCircle(Brush.radialGradient(listOf(p.bright.copy(alpha = 0.35f), Color.Transparent), cSpot, pxSize * 1.4f), pxSize * 1.4f, cSpot)
                    drawRect(p.peak, Offset(x, y), Size(pxSize, pxSize))
                }
                isTrail -> {
                    drawRect(baseCol.copy(alpha = 0.42f), Offset(x, y), Size(pxSize, pxSize))
                }
                isLit -> {
                    val colCell = lerp(baseCol, p.bright, hFrac * 0.45f)
                    drawRect(colCell.copy(alpha = 0.85f + hFrac * 0.15f), Offset(x, y), Size(pxSize, pxSize))
                    // Тонкая верхняя грань-блик
                    val bevel = (pxSize * 0.24f).coerceAtLeast(1f)
                    drawRect(Color.White.copy(alpha = 0.24f), Offset(x, y), Size(pxSize, bevel))
                }
                else -> {
                    // Незажжённые пиксели едва видны
                    drawRect(Color.White.copy(alpha = 0.035f), Offset(x, y), Size(pxSize, pxSize))
                }
            }
        }
    }
}

// 5. «Волна» — течение: 1 главная чёткая светящаяся лента + 2 мягкие позади, мягкое исчезновение у краёв
private fun DrawScope.drawFlowingWave(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val w = size.width
    val h = size.height
    val cy = h * 0.5f
    val maxAmp = h * (if (large) 0.36f else 0.40f)
    val nPts = a.streamLen

    fun edgeFade(t01: Float): Float {
        // Плавное исчезновение краёв (первые и последние 16% ширины), чтобы рисунок не обрывался прямоугольником
        val edge = 0.16f
        val left = (t01 / edge).coerceIn(0f, 1f)
        val right = ((1f - t01) / edge).coerceIn(0f, 1f)
        val lSmooth = left * left * (3f - 2f * left)
        val rSmooth = right * right * (3f - 2f * right)
        return lSmooth * rSmooth
    }

    fun buildSmoothRibbon(path: Path, fill: Path?, shiftIdx: Int, ampScale: Float, waveBias: Float) {
        path.reset()
        fill?.reset()
        var prevX = 0f
        var prevY = cy

        val baseStart = 20
        val visibleCount = nPts - baseStart - 2
        for (i in 0 until visibleCount) {
            val t = i.toFloat() / max(1, visibleCount - 1)
            val fade = edgeFade(t)
            val fIdx = (baseStart + i + a.streamFrac + shiftIdx).coerceIn(0f, (nPts - 2).toFloat())
            val i0 = fIdx.toInt()
            val mu = fIdx - i0
            val s0 = a.streamMain[i0]
            val s1 = a.streamMain[min(nPts - 1, i0 + 1)]
            val rawSample = s0 + (s1 - s0) * mu
            val shaped = (rawSample * ampScale + sin(t * 7f + a.phase * 0.6f) * waveBias) * fade
            val x = t * w
            val y = cy + shaped * maxAmp

            if (i == 0) {
                path.moveTo(x, y)
                fill?.moveTo(x, cy)
                fill?.lineTo(x, y)
            } else {
                val mx = (prevX + x) * 0.5f
                path.cubicTo(mx, prevY, mx, y, x, y)
                fill?.cubicTo(mx, prevY, mx, y, x, y)
            }
            prevX = x
            prevY = y
        }
        if (fill != null) {
            fill.lineTo(w, cy)
            fill.close()
        }
    }

    // Две задние полупрозрачные волны (мягче и темнее -> глубина пространства)
    buildSmoothRibbon(a.pathBack2, null, shiftIdx = -14, ampScale = 0.68f, waveBias = 0.04f)
    drawPath(
        a.pathBack2,
        Brush.horizontalGradient(listOf(Color.Transparent, p.deep.copy(alpha = 0.34f), p.deep.copy(alpha = 0.34f), Color.Transparent)),
        style = Stroke(width = if (large) 1.8f else 1.1f, cap = StrokeCap.Round),
    )

    buildSmoothRibbon(a.pathBack1, null, shiftIdx = -6, ampScale = 0.84f, waveBias = -0.025f)
    drawPath(
        a.pathBack1,
        Brush.horizontalGradient(listOf(Color.Transparent, p.mid.copy(alpha = 0.45f), p.bright.copy(alpha = 0.40f), Color.Transparent)),
        style = Stroke(width = if (large) 2.2f else 1.4f, cap = StrokeCap.Round),
    )

    // Главная светящаяся лента + мягкий градиент, растворяющийся в фоне (без чёрных треугольников)
    buildSmoothRibbon(a.pathMain, a.pathFill, shiftIdx = 0, ampScale = 1.0f, waveBias = 0f)
    drawPath(
        a.pathFill,
        Brush.verticalGradient(
            listOf(p.mid.copy(alpha = 0.16f + a.energy * 0.08f), Color.Transparent),
            startY = cy - maxAmp,
            endY = cy + maxAmp,
        ),
    )

    // Мягкое локальное свечение только вдоль главной линии
    drawPath(
        a.pathMain,
        Brush.horizontalGradient(listOf(Color.Transparent, p.mid.copy(alpha = 0.24f), p.bright.copy(alpha = 0.28f), Color.Transparent)),
        style = Stroke(width = if (large) 9f else 5.5f, cap = StrokeCap.Round),
    )
    // Чёткая яркая звуковая нить
    drawPath(
        a.pathMain,
        Brush.horizontalGradient(listOf(Color.Transparent, p.bright, p.peak.copy(alpha = 0.90f), p.bright, Color.Transparent)),
        style = Stroke(width = if (large) 2.6f else 1.7f, cap = StrokeCap.Round),
    )
}

// 6. «Кольцо» — пульсация: чистый круглый силуэт, внутренний басовый пульс и световые пики
private fun DrawScope.drawRadialPro(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f

    if (!large && size.height < size.width * 0.45f) {
        // Компактная версия в полосе: аккуратный симметричный радиально-центральный спектр с точками пиков
        val count = 28
        val halfW = size.width * 0.48f
        val step = halfW / count
        val bw = (step * 0.42f).coerceAtLeast(1.5f)
        val maxH = size.height * 0.42f
        for (i in 0 until count) {
            val bIdx = (i * a.bands / count).coerceIn(0, a.bands - 1)
            val lvl = a.levels[bIdx]
            val h = (lvl * maxH).coerceAtLeast(2f)
            val col = p.freq(i.toFloat() / max(1, count - 1))
            val xR = cx + i * step + step * 0.3f
            val xL = cx - (i + 1) * step + step * 0.3f
            drawLine(col, Offset(xR, cy - h), Offset(xR, cy + h), bw, StrokeCap.Round)
            drawLine(col, Offset(xL, cy - h), Offset(xL, cy + h), bw, StrokeCap.Round)
        }
        return
    }

    val minDim = min(size.width, size.height)
    val baseR = minDim * 0.255f
    val maxBar = minDim * 0.165f // Ограниченная длина, чтобы силуэт оставался круглым и цельным

    // Едва заметное внутреннее ядро без тяжёлой заливки
    drawCircle(
        Brush.radialGradient(listOf(p.deep.copy(alpha = 0.08f + a.bassPulse * 0.06f), Color.Transparent), Offset(cx, cy), baseR),
        baseR,
        Offset(cx, cy),
    )

    // Внутренний круг = деликатный басовый пульс (чуть расширяется + локальное свечение)
    val innerR = baseR * (0.55f + a.bassPulse * 0.055f)
    val innerAlpha = (0.30f + a.bassPulse * 0.55f).coerceIn(0.25f, 0.92f)
    drawCircle(p.mid.copy(alpha = innerAlpha * 0.22f), innerR, Offset(cx, cy), style = Stroke(7.5f))
    drawCircle(lerp(p.mid, p.peak, a.bassHit * 0.45f).copy(alpha = innerAlpha), innerR, Offset(cx, cy), style = Stroke(1.8f))

    // Тонкое светящееся основание внешнего кольца
    drawCircle(p.mid.copy(alpha = 0.18f), baseR, Offset(cx, cy), style = Stroke(5f))
    drawCircle(p.bright.copy(alpha = 0.45f), baseR, Offset(cx, cy), style = Stroke(1.5f))

    // 56 тонких столбиков с равномерными промежутками (симметричное/непрерывное распределение по кругу)
    val count = 56
    val barWidth = (2 * PI * baseR / count * 0.42f).toFloat().coerceIn(2.0f, 3.6f)
    val startR = baseR + 5f

    for (i in 0 until count) {
        // Зеркалим спектр по кругу, чтобы не было разрыва стыка 0..bands-1
        val halfPos = if (i < count / 2) i.toFloat() / (count / 2 - 1) else (count - 1 - i).toFloat() / (count / 2 - 1)
        val exactB = halfPos * (a.bands - 1)
        val b0 = exactB.toInt().coerceIn(0, a.bands - 1)
        val b1 = (b0 + 1).coerceIn(0, a.bands - 1)
        val frac = exactB - b0
        val lvl = (a.levels[b0] + (a.levels[b1] - a.levels[b0]) * frac).coerceIn(0f, 1f)
        val pk = (a.peaks[b0] + (a.peaks[b1] - a.peaks[b0]) * frac).coerceIn(0f, 1f)

        val ang = (i.toFloat() / count) * 2f * PI.toFloat() - (PI / 2f).toFloat() + a.phase * 0.08f
        val ca = cos(ang)
        val sa = sin(ang)
        val len = (0.04f + lvl * 0.96f) * maxBar
        val tipR = startR + len
        val pStart = Offset(cx + ca * startR, cy + sa * startR)
        val pTip = Offset(cx + ca * tipR, cy + sa * tipR)

        val colBase = p.deep
        val colTip = p.amp(0.35f + lvl * 0.65f)

        // Лёгкое локальное свечение кончика активных лучей
        if (lvl > 0.25f) {
            drawCircle(colTip.copy(alpha = 0.18f * lvl), barWidth * 2.2f, pTip)
        }
        drawLine(
            Brush.linearGradient(listOf(colBase, colTip), pStart, pTip),
            pStart,
            pTip,
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
        )

        // Маленькая почти белая световая отметка пика
        if (pk > 0.08f) {
            val pkR = startR + pk * maxBar + 4.5f
            val pkPt = Offset(cx + ca * pkR, cy + sa * pkR)
            drawCircle(p.peak.copy(alpha = (0.45f + pk * 0.55f).coerceIn(0f, 1f)), barWidth * 0.55f, pkPt)
        }
    }
}

// 7. «LED-дека» — ритм и спектр: матрица 24×12, закреплённые цвета (бас-синий, центр-голубой, верх-бирюза) + режим «Искры»
private fun DrawScope.drawLedDeckPro(a: FrequencyAnalyzer, p: VizPalette, large: Boolean, sparksMode: Boolean) {
    val cols = 24
    val rows = if (large) 12 else 6
    val padX = if (large) size.width * 0.045f else 2f
    val padY = if (large) size.height * 0.09f else 2f
    val usableW = (size.width - padX * 2f).coerceAtLeast(10f)
    val usableH = (size.height - padY * 2f).coerceAtLeast(8f)

    val cw = usableW / cols
    val rh = usableH / rows
    val gapX = cw * 0.24f
    val gapY = rh * 0.24f
    val sqW = (cw - gapX).coerceAtLeast(2f)
    val sqH = (rh - gapY).coerceAtLeast(2f)
    val side = if (large) min(sqW, sqH) else sqW
    val cellW = if (large) side else sqW
    val cellH = if (large) side else sqH
    val radius = CornerRadius(min(cellW, cellH) * 0.24f)

    val gridW = cols * cw
    val gridH = rows * rh
    val offsetX = (size.width - gridW) * 0.5f + (cw - cellW) * 0.5f
    val offsetY = (size.height - gridH) * 0.5f + (rh - cellH) * 0.5f

    for (col in 0 until cols) {
        // Цвет строго закреплён за частотным диапазоном: слева бас (deep), центр середина (mid), справа верх (bright)
        val freqPos = col.toFloat() / (cols - 1)
        val bandCol = p.freq(freqPos)
        val lvl = a.levels24[col]
        val pk = a.peaks24[col]
        val litRows = (lvl * rows).toInt().coerceIn(0, rows)
        val peakRow = (pk * (rows - 1)).toInt().coerceIn(0, rows - 1)
        val bassBoost = if (col < 8) a.bassHit * 0.28f else a.bassHit * 0.08f

        for (r in 0 until rows) {
            val x = offsetX + col * cw
            val y = offsetY + (rows - 1 - r) * rh
            val pos = Offset(x, y)
            val sz = Size(cellW, cellH)
            val center = Offset(x + cellW * 0.5f, y + cellH * 0.5f)

            // Базовая матрица сохраняется едва видной даже в тишине
            drawRoundRect(Color.White.copy(alpha = 0.045f), pos, sz, radius)

            val cellActive: Float
            var isPeakCell = false

            if (sparksMode) {
                val srcRow = (r * 12 / rows).coerceIn(0, 11)
                cellActive = a.sparkMatrix[col * 12 + srcRow]
                isPeakCell = cellActive > 0.85f
            } else {
                isPeakCell = pk > 0.09f && r == peakRow
                cellActive = when {
                    r < litRows -> (0.72f + (r.toFloat() / rows) * 0.28f + bassBoost).coerceIn(0f, 1f)
                    isPeakCell -> 0.95f
                    else -> 0f
                }
            }

            if (cellActive > 0.06f) {
                val haloR = max(cellW, cellH) * 1.05f
                drawCircle(
                    Brush.radialGradient(listOf(bandCol.copy(alpha = 0.22f * cellActive), Color.Transparent), center, haloR),
                    haloR,
                    center,
                )
                val bodyCol = if (isPeakCell && (sparksMode || r >= litRows)) p.peak else lerp(bandCol, p.peak, bassBoost * 0.45f)
                drawRoundRect(bodyCol.copy(alpha = (0.30f + 0.70f * cellActive).coerceIn(0f, 1f)), pos, sz, radius)

                // Чёткая яркая сердцевина без размытого пятна
                val coreW = cellW * 0.48f
                val coreH = cellH * 0.48f
                drawRoundRect(
                    p.peak.copy(alpha = (0.38f + 0.52f * cellActive).coerceIn(0f, 1f)),
                    Offset(center.x - coreW * 0.5f, center.y - coreH * 0.5f),
                    Size(coreW, coreH),
                    CornerRadius(min(coreW, coreH) * 0.25f),
                )
            }
        }
    }
}

// 8. «Энергия» — движение: 3 слоя частиц с короткими следами, без заметной обводки и схем атома
private fun DrawScope.drawEnergyCloud(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val scaleR = if (large) min(size.width, size.height) * 0.44f else size.height * 0.46f

    // 1. Почти убранные орбиты: лишь 2 тончайшие, местами исчезающие дуги на большой сцене
    if (large) {
        val arcR1 = scaleR * (0.46f + a.bassPulse * 0.03f)
        val arcR2 = scaleR * 0.78f
        val rot1 = (a.phase * 14f) % 360f
        val rot2 = (-a.phase * 9f + 120f) % 360f
        drawArc(
            Brush.sweepGradient(listOf(Color.Transparent, p.mid.copy(alpha = 0.11f), Color.Transparent), Offset(cx, cy)),
            startAngle = rot1,
            sweepAngle = 115f,
            useCenter = false,
            topLeft = Offset(cx - arcR1, cy - arcR1),
            size = Size(arcR1 * 2f, arcR1 * 2f),
            style = Stroke(width = 1.1f, cap = StrokeCap.Round),
        )
        drawArc(
            Brush.sweepGradient(listOf(Color.Transparent, p.bright.copy(alpha = 0.08f), Color.Transparent), Offset(cx, cy)),
            startAngle = rot2,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = Offset(cx - arcR2, cy - arcR2),
            size = Size(arcR2 * 2f, arcR2 * 2f),
            style = Stroke(width = 1.0f, cap = StrokeCap.Round),
        )
    }

    // 2. Три слоя частиц с короткими затухающими следами, ярким ядром и мягким свечением
    val sx = if (large) scaleR else size.width * 0.45f
    val sy = if (large) scaleR else size.height * 0.42f

    for (pt in a.particles) {
        val g = pt.glow
        if (g < 0.03f && pt.layer == 2) continue

        val col = when (pt.layer) {
            0 -> p.deep // крупные (бас)
            1 -> p.mid // средние (середина)
            else -> p.bright // мелкие искры (верх)
        }
        val baseRadius = when (pt.layer) {
            0 -> (if (large) 7.2f else 3.8f) * pt.sizeSeed
            1 -> (if (large) 3.8f else 2.2f) * pt.sizeSeed
            else -> (if (large) 1.7f else 1.2f) * pt.sizeSeed
        }

        // Короткие затухающие следы (индексы 3 -> 1)
        for (tIdx in 3 downTo 1) {
            val tx = cx + pt.trailX[tIdx] * sx
            val ty = cy + pt.trailY[tIdx] * sy
            val factor = (4 - tIdx) / 4f
            val trailAlpha = g * factor * factor * (when (pt.layer) { 0 -> 0.22f; 1 -> 0.26f; else -> 0.30f })
            if (trailAlpha > 0.015f) {
                drawCircle(col.copy(alpha = trailAlpha), baseRadius * (0.45f + factor * 0.40f), Offset(tx, ty))
            }
        }

        // Актуальная позиция частицы (без обводки: мягкий ореол + яркое ядро)
        val px = cx + pt.trailX[0] * sx
        val py = cy + pt.trailY[0] * sy
        val pos = Offset(px, py)
        val haloR = baseRadius * (when (pt.layer) { 0 -> 3.0f; 1 -> 2.7f; else -> 2.4f })
        val alphaMain = (0.20f + g * 0.80f).coerceIn(0f, 1f)

        drawCircle(
            Brush.radialGradient(listOf(col.copy(alpha = 0.42f * alphaMain), Color.Transparent), pos, haloR),
            haloR,
            pos,
        )
        drawCircle(col.copy(alpha = 0.85f * alphaMain), baseRadius, pos)
        drawCircle(p.peak.copy(alpha = 0.90f * alphaMain), baseRadius * 0.44f, pos)
    }
}

// 9. «Горизонт» — светящийся зеркальный спектр с водной рябью и мягкими краями
private fun DrawScope.drawHorizonPro(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val w = size.width
    val h = size.height
    val cy = h * 0.54f
    val maxUp = cy * (if (large) 0.78f else 0.85f)
    val maxDown = (h - cy) * (if (large) 0.74f else 0.78f)
    val n = a.bands
    val dx = w / max(1, n - 1)

    // Тонкая светящаяся линия горизонта с затуханием к краям
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, p.bright.copy(alpha = 0.45f), p.bright.copy(alpha = 0.45f), Color.Transparent)),
        Offset(0f, cy),
        Offset(w, cy),
        if (large) 1.5f else 1f,
    )

    // Плавный двуслойный силуэт вверх
    a.pathMain.reset()
    a.pathFill.reset()
    a.pathBack1.reset()

    var prevX = 0f
    var prevY = cy
    var prevBY = cy
    for (i in 0 until n) {
        val t = i.toFloat() / max(1, n - 1)
        val edge = min((t / 0.14f).coerceIn(0f, 1f), ((1f - t) / 0.14f).coerceIn(0f, 1f))
        val amp = a.levels[i] * maxUp * edge
        val ampBack = a.peaks[i] * maxUp * 0.86f * edge
        val x = i * dx
        val y = cy - amp
        val by = cy - ampBack
        if (i == 0) {
            a.pathMain.moveTo(x, y)
            a.pathFill.moveTo(x, cy)
            a.pathFill.lineTo(x, y)
            a.pathBack1.moveTo(x, by)
        } else {
            val mx = (prevX + x) * 0.5f
            a.pathMain.cubicTo(mx, prevY, mx, y, x, y)
            a.pathFill.cubicTo(mx, prevY, mx, y, x, y)
            a.pathBack1.cubicTo(mx, prevBY, mx, by, x, by)
        }
        prevX = x
        prevY = y
        prevBY = by
    }
    a.pathFill.lineTo(w, cy)
    a.pathFill.close()

    drawPath(a.pathBack1, Brush.horizontalGradient(listOf(Color.Transparent, p.deep.copy(alpha = 0.45f), Color.Transparent)), style = Stroke(if (large) 1.6f else 1f))
    drawPath(a.pathFill, Brush.verticalGradient(listOf(p.mid.copy(alpha = 0.26f), Color.Transparent), cy - maxUp, cy))
    drawPath(a.pathMain, Brush.horizontalGradient(listOf(Color.Transparent, p.mid, p.bright, p.mid, Color.Transparent)), style = Stroke(if (large) 2.4f else 1.5f, cap = StrokeCap.Round))

    // Отражение внизу с горизонтальной водной рябью
    val rippleRows = if (large) 7 else 3
    val rowStep = maxDown / rippleRows
    for (r in 0 until rippleRows) {
        val rf = r.toFloat() / rippleRows
        val yLine = cy + 3f + r * rowStep
        val rippleAlpha = (1f - rf) * (0.28f + a.energy * 0.12f)
        val rippleShift = sin(a.phase * 2.2f + r * 1.1f) * (2f + a.bassPulse * 5f)
        for (i in 0 until n step 2) {
            val t = i.toFloat() / max(1, n - 1)
            val edge = min((t / 0.14f).coerceIn(0f, 1f), ((1f - t) / 0.14f).coerceIn(0f, 1f))
            if (a.levels[i] * edge > rf * 0.72f + 0.08f) {
                val x0 = (i * dx + rippleShift).coerceIn(0f, w)
                val segW = dx * 1.4f
                drawLine(
                    p.freq(t).copy(alpha = rippleAlpha),
                    Offset(x0, yLine),
                    Offset((x0 + segW).coerceAtMost(w), yLine),
                    strokeWidth = if (large) 2.0f else 1.3f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
