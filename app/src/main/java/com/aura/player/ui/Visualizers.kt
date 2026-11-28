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
import androidx.compose.ui.graphics.drawscope.rotate
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
    MIRROR("Горизонт"),
    BAND("Рок-банда");

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

    // 3 частотных канала для сцены «Рок-банда»: барабаны (Таракан), вокал (Кот), гитара (Белый крыс)
    var drumsLevel = 0f
    var drumKick = 0f
    var drumSnare = 0f
    var drumLeftStick = 0f
    var drumRightStick = 0f
    private var drumSideToggle = false
    var vocalLevel = 0f
    var vocalMouth = 0f
    var vocalNotePhase = 0f
    var guitarLevel = 0f
    var guitarStrum = 0f
    var guitarPhase = 0f
    private var prevSnareRaw = 0f
    private var prevGuitarRaw = 0f

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
            AudioEngine.fx.snapshot(buf, AudioEngine.effectiveVizDelayMs())
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

        // Шумовой порог + реальная динамика громкости: в тишине колонки опускаются в 0, на пиках сохраняется запас
        val rmsGate = ((rms - 0.004f) * 8.5f).coerceIn(0f, 1f)
        val loudnessScale = if (active && rms > 0.004f) rmsGate.pow(0.52f) else 0f

        var rawMax = 0f
        for (b in 0 until bands) {
            var raw = 0f
            if (active && loudnessScale > 0.01f) {
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
        agcPeak = max(rawMax, agcPeak * exp(-dt * 0.45f)).coerceAtLeast(0.42f)

        var rawMax24 = 0f
        for (b in 0 until bands24) {
            var raw = 0f
            if (active && loudnessScale > 0.01f) {
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
        agc24 = max(rawMax24, agc24 * exp(-dt * 0.45f)).coerceAtLeast(0.42f)

        val atkFactor = 1f - exp(-dt * 36f)
        val decFactor = 1f - exp(-dt * 14.5f)

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

            // Баллистика пиков с коротким зависанием и быстрым плавным падением
            if (cur >= peaks[b]) {
                peaks[b] = cur
                peakHold[b] = 0.08f
                peakVel[b] = 0f
            } else if (peakHold[b] > 0f) {
                peakHold[b] = (peakHold[b] - dt).coerceAtLeast(0f)
            } else {
                peakVel[b] += dt * 3.4f
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
                peakHold24[b] = 0.08f
                peakVel24[b] = 0f
            } else if (peakHold24[b] > 0f) {
                peakHold24[b] = (peakHold24[b] - dt).coerceAtLeast(0f)
            } else {
                peakVel24[b] += dt * 3.2f
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

        // Разделение на 3 инструментальные партии для сцены «Рок-банда» (Барабаны / Вокал / Гитара)
        updateBandChannels(active && loudnessScale > 0.02f, dt, loudnessScale)

        phase += dt * (1.25f + bass * 2.1f)
        if (active) partyHue = (partyHue + dt * (12f + mid * 38f)) % 360f
    }

    private fun updateBandChannels(hasSound: Boolean, dt: Float, loud: Float) {
        if (!hasSound) {
            val dec = exp(-dt * 9f)
            drumsLevel *= dec; drumKick *= dec; drumSnare *= dec
            drumLeftStick *= dec; drumRightStick *= dec
            vocalLevel *= dec; vocalMouth *= dec
            guitarLevel *= dec; guitarStrum *= dec
            return
        }

        // 1. Барабаны и бас (43..172 Гц бочка + транзиенты 2200..6500 Гц малый/тарелки)
        var kickBin = 0f
        for (k in 1..4) kickBin = max(kickBin, hypot(re[k], im[k]))
        var snareBin = 0f
        for (k in 50..150) snareBin += hypot(re[k], im[k])
        snareBin /= 100f

        val kickRaw = (((20f * ln(kickBin + 1e-6f) / ln(10f) + 10f) / 40f).coerceIn(0f, 1f) * loud)
        val snareRaw = (((20f * ln(snareBin + 1e-6f) / ln(10f) + 18f) / 38f).coerceIn(0f, 1f) * loud)
        val snareJump = (snareRaw - prevSnareRaw).coerceAtLeast(0f)
        prevSnareRaw = prevSnareRaw * 0.65f + snareRaw * 0.35f

        val kickTrigger = bassHit > 0.30f || kickRaw > drumsLevel + 0.14f
        if (kickTrigger && kickRaw > 0.22f) {
            drumKick = max(drumKick, (0.55f + kickRaw * 0.45f).coerceIn(0f, 1f))
            drumSideToggle = !drumSideToggle
            if (drumSideToggle) drumLeftStick = 1f else drumRightStick = 1f
        } else {
            drumKick *= exp(-dt * 12f)
        }

        if (snareJump > 0.08f && snareRaw > 0.18f) {
            drumSnare = max(drumSnare, (0.55f + snareJump * 3.5f).coerceIn(0f, 1f))
            drumRightStick = max(drumRightStick, 0.95f)
            if (drumKick > 0.4f) drumLeftStick = max(drumLeftStick, 0.9f)
        } else {
            drumSnare *= exp(-dt * 13f)
        }
        drumLeftStick *= exp(-dt * 10f)
        drumRightStick *= exp(-dt * 10f)
        val targetDrums = max(kickRaw * 0.92f, max(drumKick, drumSnare * 0.85f)).coerceIn(0f, 1f)
        drumsLevel += (targetDrums - drumsLevel) * (1f - exp(-dt * (if (targetDrums > drumsLevel) 24f else 10f)))

        // 2. Вокальный диапазон (300..1150 Гц основная форманта + 1800..3300 Гц присутствие голоса)
        var vLow = 0f
        for (k in 7..26) vLow += hypot(re[k], im[k])
        vLow /= 20f
        var vPres = 0f
        for (k in 42..76) vPres += hypot(re[k], im[k])
        vPres /= 35f
        val vCore = (((20f * ln(vLow + 1e-6f) / ln(10f) + 14f) / 38f).coerceIn(0f, 1f))
        val vAir = (((20f * ln(vPres + 1e-6f) / ln(10f) + 20f) / 36f).coerceIn(0f, 1f))
        // Изоляция вокала: голос проявляется, когда форманты выражены относительно глухого баса
        val vocalFocus = (vCore * 0.62f + vAir * 0.38f) * loud
        val vocalGate = ((vocalFocus - kickRaw * 0.22f - 0.14f) * 1.55f).coerceIn(0f, 1f)
        vocalLevel += (vocalGate - vocalLevel) * (1f - exp(-dt * (if (vocalGate > vocalLevel) 20f else 11f)))
        val mouthTarget = if (vocalLevel > 0.12f) (vocalLevel * (0.75f + 0.25f * sin(phase * 6.5f))).coerceIn(0f, 1f) else 0f
        vocalMouth += (mouthTarget - vocalMouth) * (1f - exp(-dt * 22f))
        if (vocalLevel > 0.14f) vocalNotePhase += dt * (1.2f + vocalLevel * 1.6f)

        // 3. Гитарный диапазон (170..650 Гц корпус/рифф + 1100..3800 Гц струнные гармоники)
        var gBody = 0f
        for (k in 4..15) gBody += hypot(re[k], im[k])
        gBody /= 12f
        var gHarm = 0f
        for (k in 26..88) gHarm += hypot(re[k], im[k])
        gHarm /= 63f
        val gRaw = ((((20f * ln(gBody + 1e-6f) / ln(10f) + 13f) / 38f).coerceIn(0f, 1f) * 0.50f) +
            (((20f * ln(gHarm + 1e-6f) / ln(10f) + 19f) / 36f).coerceIn(0f, 1f) * 0.50f)) * loud
        val gJump = (gRaw - prevGuitarRaw).coerceAtLeast(0f)
        prevGuitarRaw = prevGuitarRaw * 0.72f + gRaw * 0.28f
        val guitarGate = ((gRaw - 0.13f) * 1.45f).coerceIn(0f, 1f)
        guitarLevel += (guitarGate - guitarLevel) * (1f - exp(-dt * (if (guitarGate > guitarLevel) 22f else 10f)))
        if (gJump > 0.045f && guitarLevel > 0.14f) {
            guitarStrum = max(guitarStrum, (0.55f + gJump * 5.5f).coerceIn(0f, 1f))
        } else {
            guitarStrum *= exp(-dt * 9.5f)
        }
        if (guitarLevel > 0.12f) guitarPhase += dt * (4.5f + guitarLevel * 8.5f + guitarStrum * 6f)
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
                VisualizerType.BAND -> drawRockBand(analyzer, palette, large)
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

// 10. «Рок-банда» — Таракан на барабанах (бас/ударные), Шотландский вислоухий кот с микрофоном (вокал), Белый крыс с гитарой (гитара)
private fun DrawScope.drawRockBand(a: FrequencyAnalyzer, p: VizPalette, large: Boolean) {
    val w = size.width
    val h = size.height

    val vocal = a.vocalLevel
    val drums = a.drumsLevel
    val guitar = a.guitarLevel

    val catCol = Color(0xFF38BDF8)   // прожектор на кота (вокал)
    val drumCol = Color(0xFFF59E0B)  // прожектор на таракана (барабаны)
    val ratCol = Color(0xFFA78BFA)   // прожектор на крыса (гитара)

    if (!large) {
        // Компактная полоса (38dp): три живых блока-индикатора (Кот · Таракан · Крыс)
        val sections = listOf(
            Triple(w * 0.18f, vocal, catCol),
            Triple(w * 0.50f, drums, drumCol),
            Triple(w * 0.82f, guitar, ratCol),
        )
        val cy = h * 0.52f
        val rBase = min(h * 0.34f, w * 0.06f)
        sections.forEachIndexed { idx, (sx, lvl, col) ->
            // Мягкое свечение активного участника
            if (lvl > 0.08f) {
                drawCircle(
                    Brush.radialGradient(listOf(col.copy(alpha = 0.35f * lvl), Color.Transparent), Offset(sx, cy), rBase * 2.4f),
                    rBase * 2.4f,
                    Offset(sx, cy),
                )
            }
            when (idx) {
                0 -> {
                    // Вислоухий кот (мини)
                    drawCircle(Color(0xFF8B98AB), rBase * 0.85f, Offset(sx, cy))
                    drawOval(Color(0xFF6B778C), Offset(sx - rBase * 1.05f, cy - rBase * 0.65f), Size(rBase * 0.55f, rBase * 0.45f))
                    drawOval(Color(0xFF6B778C), Offset(sx + rBase * 0.50f, cy - rBase * 0.65f), Size(rBase * 0.55f, rBase * 0.45f))
                    val mouthH = (rBase * 0.12f + a.vocalMouth * rBase * 0.42f)
                    drawOval(Color(0xFFF472B6), Offset(sx - rBase * 0.22f, cy + rBase * 0.12f), Size(rBase * 0.44f, mouthH))
                }
                1 -> {
                    // Барабан + палочки таракана (мини)
                    val br = rBase * (0.90f + a.drumKick * 0.18f)
                    drawCircle(Color(0xFF1E293B), br, Offset(sx, cy))
                    drawCircle(drumCol.copy(alpha = 0.4f + drums * 0.6f), br, Offset(sx, cy), style = Stroke(2f))
                    val lLift = a.drumLeftStick * rBase * 0.6f
                    val rLift = a.drumRightStick * rBase * 0.6f
                    drawLine(Color(0xFFFDE68A), Offset(sx - br * 1.2f, cy - br * 0.9f + lLift), Offset(sx - br * 0.25f, cy - br * 0.15f), 2f, StrokeCap.Round)
                    drawLine(Color(0xFFFDE68A), Offset(sx + br * 1.2f, cy - br * 0.9f + rLift), Offset(sx + br * 0.25f, cy - br * 0.15f), 2f, StrokeCap.Round)
                }
                else -> {
                    // Белый крыс + гитара (мини)
                    drawCircle(Color(0xFFF8FAFC), rBase * 0.78f, Offset(sx, cy))
                    drawCircle(Color(0xFFFDA4AF), rBase * 0.36f, Offset(sx - rBase * 0.55f, cy - rBase * 0.68f))
                    drawCircle(Color(0xFFFDA4AF), rBase * 0.36f, Offset(sx + rBase * 0.55f, cy - rBase * 0.68f))
                    drawLine(
                        Color(0xFFEF4444),
                        Offset(sx - rBase * 0.9f, cy + rBase * 0.55f),
                        Offset(sx + rBase * 1.15f, cy - rBase * 0.35f + sin(a.guitarPhase) * a.guitarStrum * 3f),
                        3f,
                        StrokeCap.Round,
                    )
                }
            }
            // Индикаторная полоска уровня под участником
            val barW = w * 0.22f
            drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(sx - barW * 0.5f, h - 4f), Size(barW, 2.5f), CornerRadius(2f))
            drawRoundRect(col.copy(alpha = 0.35f + lvl * 0.65f), Offset(sx - barW * 0.5f, h - 4f), Size((barW * lvl).coerceAtLeast(2f), 2.5f), CornerRadius(2f))
        }
        return
    }

    // ===================== БОЛЬШАЯ КОНЦЕРТНАЯ СЦЕНА =====================
    val u = min(w, h) / 300f // базовая единица масштаба
    val trussY = h * 0.06f
    val podiumY = h * 0.62f
    val stageFloorY = h * 0.79f

    // 1. Сценический пол с глубиной и отражением света
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF0E1320), Color(0xFF070A12)), stageFloorY - 26f * u, h),
        Offset(0f, stageFloorY - 26f * u),
        Size(w, h - (stageFloorY - 26f * u)),
    )
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, p.mid.copy(alpha = 0.30f), Color.Transparent)),
        Offset(0f, stageFloorY - 26f * u),
        Offset(w, stageFloorY - 26f * u),
        1.5f * u,
    )

    // Подиум барабанщика в центре
    val podW = w * 0.44f
    val podH = 14f * u
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF1A2235), Color(0xFF0D111C)), podiumY, podiumY + podH),
        Offset((w - podW) * 0.5f, podiumY),
        Size(podW, podH),
        CornerRadius(5f * u),
    )
    drawLine(
        drumCol.copy(alpha = 0.22f + drums * 0.55f),
        Offset((w - podW) * 0.5f + 6f * u, podiumY + 1f * u),
        Offset((w + podW) * 0.5f - 6f * u, podiumY + 1f * u),
        2f * u,
        StrokeCap.Round,
    )

    // 2. Верхняя балка и 3 прожектора (каждый горит ярко, когда играет его участник!)
    drawLine(Color.White.copy(alpha = 0.14f), Offset(w * 0.06f, trussY), Offset(w * 0.94f, trussY), 2.5f * u)

    fun drawSpotBeam(srcX: Float, dstX: Float, dstY: Float, col: Color, lvl: Float) {
        val alpha = 0.05f + lvl * 0.30f
        val halfW = (28f + lvl * 22f) * u
        a.pathMain.reset()
        a.pathMain.moveTo(srcX - 4f * u, trussY)
        a.pathMain.lineTo(srcX + 4f * u, trussY)
        a.pathMain.lineTo(dstX + halfW, dstY)
        a.pathMain.lineTo(dstX - halfW, dstY)
        a.pathMain.close()
        drawPath(a.pathMain, Brush.verticalGradient(listOf(col.copy(alpha = alpha), Color.Transparent), trussY, dstY))
        // Блик на полу под музыкантом
        drawOval(
            Brush.radialGradient(listOf(col.copy(alpha = 0.12f + lvl * 0.32f), Color.Transparent), Offset(dstX, dstY), halfW),
            Offset(dstX - halfW, dstY - 10f * u),
            Size(halfW * 2f, 20f * u),
        )
        // Линза прожектора
        drawCircle(col.copy(alpha = 0.35f + lvl * 0.65f), 5.5f * u, Offset(srcX, trussY))
        drawCircle(Color.White.copy(alpha = 0.5f + lvl * 0.5f), 2.2f * u, Offset(srcX, trussY))
    }

    drawSpotBeam(w * 0.22f, w * 0.21f, stageFloorY + 6f * u, catCol, vocal)
    drawSpotBeam(w * 0.50f, w * 0.50f, podiumY + 2f * u, drumCol, drums)
    drawSpotBeam(w * 0.78f, w * 0.79f, stageFloorY + 6f * u, ratCol, guitar)

    // ===================== 3. ТАРАКАН ЗА БАРАБАННОЙ УСТАНОВКОЙ (центр-задний план) =====================
    run {
        val bx = w * 0.50f
        val by = podiumY - 2f * u
        val bounce = a.drumKick * 4.5f * u
        val headBob = sin(a.phase * 2.2f) * (1.2f + drums * 2.6f) * u

        // Тело таракана (выглядывает из-за барабанов): глянцевый каштановый сегментированный панцирь
        val bodyCenterY = by - 58f * u - bounce * 0.4f + headBob * 0.3f
        val shellDark = Color(0xFF4A210D)
        val shellMid = Color(0xFF853E1B)
        val shellHi = Color(0xFFB45A28)
        drawOval(
            Brush.radialGradient(listOf(shellHi, shellMid, shellDark), Offset(bx - 5f * u, bodyCenterY - 8f * u), 28f * u),
            Offset(bx - 19f * u, bodyCenterY - 22f * u),
            Size(38f * u, 44f * u),
        )
        // Сегменты панциря
        for (seg in -1..1) {
            val sy = bodyCenterY + seg * 8f * u
            drawArc(
                Color(0xFF2D1206).copy(alpha = 0.55f),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(bx - 16f * u, sy - 6f * u),
                size = Size(32f * u, 12f * u),
                style = Stroke(1.6f * u),
            )
        }

        // Голова таракана + выразительные глаза + подвижные длинные усики
        val hx = bx
        val hy = bodyCenterY - 23f * u + headBob * 0.6f
        drawOval(
            Brush.radialGradient(listOf(shellHi, shellMid), Offset(hx - 3f * u, hy - 3f * u), 16f * u),
            Offset(hx - 13f * u, hy - 11f * u),
            Size(26f * u, 21f * u),
        )
        // Глаза таракана
        drawCircle(Color.White, 4.2f * u, Offset(hx - 5.5f * u, hy - 2f * u))
        drawCircle(Color.White, 4.2f * u, Offset(hx + 5.5f * u, hy - 2f * u))
        drawCircle(Color(0xFF111827), 2.1f * u, Offset(hx - 5.0f * u, hy - 1.5f * u))
        drawCircle(Color(0xFF111827), 2.1f * u, Offset(hx + 5.0f * u, hy - 1.5f * u))
        drawCircle(Color.White, 0.9f * u, Offset(hx - 5.8f * u, hy - 2.6f * u))
        drawCircle(Color.White, 0.9f * u, Offset(hx + 4.2f * u, hy - 2.6f * u))
        // Забавная довольная ухмылка на бите
        drawArc(
            Color(0xFF1F0C04),
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(hx - 5f * u, hy + 1.5f * u),
            size = Size(10f * u, (4f + drums * 3f) * u),
            style = Stroke(1.5f * u, cap = StrokeCap.Round),
        )
        // Длинные гнутые усики таракана (качаются в такт)
        val antWave = sin(a.phase * 4.2f) * (3f + drums * 8f) * u
        a.pathBack1.reset()
        a.pathBack1.moveTo(hx - 6f * u, hy - 9f * u)
        a.pathBack1.cubicTo(hx - 18f * u, hy - 30f * u, hx - 34f * u, hy - 28f * u + antWave, hx - 42f * u, hy - 16f * u - antWave)
        drawPath(a.pathBack1, Color(0xFF9A4E24), style = Stroke(1.8f * u, cap = StrokeCap.Round))
        a.pathBack2.reset()
        a.pathBack2.moveTo(hx + 6f * u, hy - 9f * u)
        a.pathBack2.cubicTo(hx + 18f * u, hy - 30f * u, hx + 34f * u, hy - 28f * u - antWave, hx + 42f * u, hy - 16f * u + antWave)
        drawPath(a.pathBack2, Color(0xFF9A4E24), style = Stroke(1.8f * u, cap = StrokeCap.Round))

        // Нижние суставчатые лапки по бокам панциря
        for (side in listOf(-1f, 1f)) {
            val shoulder = Offset(bx + side * 16f * u, bodyCenterY + 2f * u)
            val knee = Offset(bx + side * 28f * u, bodyCenterY - 4f * u)
            val foot = Offset(bx + side * 34f * u, bodyCenterY + 12f * u)
            drawLine(shellDark, shoulder, knee, 2.2f * u, StrokeCap.Round)
            drawLine(shellDark, knee, foot, 2.0f * u, StrokeCap.Round)
        }

        // ---- Барабанная установка ----
        val cymbalTiltL = -8f - a.drumSnare * 18f
        val cymbalTiltR = 8f + a.drumSnare * 18f
        val standCol = Color(0xFF94A3B8)
        val cymbalGold = Color(0xFFFBBF24)
        val cymbalHi = Color(0xFFFEF08A)

        // Левая тарелка (Hi-Hat) и правая тарелка (Crash) на стойках
        val cymLX = bx - 48f * u
        val cymLY = by - 58f * u
        val cymRX = bx + 48f * u
        val cymRY = by - 64f * u
        drawLine(standCol, Offset(cymLX, by), Offset(cymLX, cymLY), 2.2f * u)
        drawLine(standCol, Offset(cymRX, by), Offset(cymRX, cymRY), 2.2f * u)
        // Треноги стоек
        drawLine(standCol, Offset(cymLX, by - 8f * u), Offset(cymLX - 9f * u, by), 1.8f * u)
        drawLine(standCol, Offset(cymLX, by - 8f * u), Offset(cymLX + 9f * u, by), 1.8f * u)
        drawLine(standCol, Offset(cymRX, by - 8f * u), Offset(cymRX - 9f * u, by), 1.8f * u)
        drawLine(standCol, Offset(cymRX, by - 8f * u), Offset(cymRX + 9f * u, by), 1.8f * u)

        rotate(cymbalTiltL, Offset(cymLX, cymLY)) {
            if (a.drumSnare > 0.25f) {
                drawCircle(Brush.radialGradient(listOf(cymbalHi.copy(alpha = 0.45f * a.drumSnare), Color.Transparent), Offset(cymLX, cymLY), 22f * u), 22f * u, Offset(cymLX, cymLY))
            }
            drawOval(Brush.horizontalGradient(listOf(Color(0xFFD97706), cymbalHi, cymbalGold)), Offset(cymLX - 19f * u, cymLY - 4.5f * u), Size(38f * u, 9f * u))
        }
        rotate(cymbalTiltR, Offset(cymRX, cymRY)) {
            if (a.drumSnare > 0.25f) {
                drawCircle(Brush.radialGradient(listOf(cymbalHi.copy(alpha = 0.50f * a.drumSnare), Color.Transparent), Offset(cymRX, cymRY), 25f * u), 25f * u, Offset(cymRX, cymRY))
            }
            drawOval(Brush.horizontalGradient(listOf(cymbalGold, cymbalHi, Color(0xFFD97706))), Offset(cymRX - 22f * u, cymRY - 5f * u), Size(44f * u, 10f * u))
        }

        // Малый барабан (слева) и напольный том (справа)
        val snarePos = Offset(bx - 30f * u, by - 30f * u)
        drawRoundRect(Color(0xFF1E3A8A), Offset(snarePos.x - 13f * u, snarePos.y), Size(26f * u, 16f * u), CornerRadius(4f * u))
        drawOval(Color(0xFFE2E8F0), Offset(snarePos.x - 13f * u, snarePos.y - 4f * u), Size(26f * u, 8f * u))
        drawOval(Color(0xFF94A3B8), Offset(snarePos.x - 13f * u, snarePos.y - 4f * u), Size(26f * u, 8f * u), style = Stroke(1.6f * u))

        val tomPos = Offset(bx + 30f * u, by - 28f * u)
        drawRoundRect(Color(0xFF1E3A8A), Offset(tomPos.x - 14f * u, tomPos.y), Size(28f * u, 20f * u), CornerRadius(4f * u))
        drawOval(Color(0xFFE2E8F0), Offset(tomPos.x - 14f * u, tomPos.y - 4.5f * u), Size(28f * u, 9f * u))
        drawOval(Color(0xFF94A3B8), Offset(tomPos.x - 14f * u, tomPos.y - 4.5f * u), Size(28f * u, 9f * u), style = Stroke(1.6f * u))

        // Большая бочка (Bass Drum) по центру — пульсирует и вибрирует от удара!
        val kickR = (24f + a.drumKick * 3.8f) * u
        val kickCenter = Offset(bx, by - kickR + 2f * u)
        if (a.drumKick > 0.15f) {
            drawCircle(
                Brush.radialGradient(listOf(drumCol.copy(alpha = 0.42f * a.drumKick), Color.Transparent), kickCenter, kickR * 1.65f),
                kickR * 1.65f,
                kickCenter,
            )
            drawCircle(drumCol.copy(alpha = 0.55f * a.drumKick), kickR * (1.08f + a.drumKick * 0.25f), kickCenter, style = Stroke(2.2f * u))
        }
        drawCircle(Color(0xFF0F172A), kickR, kickCenter)
        drawCircle(Color(0xFF1E293B), kickR * 0.88f, kickCenter)
        drawCircle(Color(0xFFE2E8F0), kickR, kickCenter, style = Stroke(3.2f * u))
        drawCircle(drumCol.copy(alpha = 0.55f + a.drumKick * 0.45f), kickR * 0.72f, kickCenter, style = Stroke(1.5f * u))
        // Молния на мембране бочки
        a.pathMain.reset()
        a.pathMain.moveTo(kickCenter.x + 2f * u, kickCenter.y - 10f * u)
        a.pathMain.lineTo(kickCenter.x - 5f * u, kickCenter.y + 1f * u)
        a.pathMain.lineTo(kickCenter.x + 1f * u, kickCenter.y + 1f * u)
        a.pathMain.lineTo(kickCenter.x - 3f * u, kickCenter.y + 10f * u)
        a.pathMain.lineTo(kickCenter.x + 6f * u, kickCenter.y - 2f * u)
        a.pathMain.lineTo(kickCenter.x, kickCenter.y - 2f * u)
        a.pathMain.close()
        drawPath(a.pathMain, Color(0xFFFACC15).copy(alpha = 0.75f + a.drumKick * 0.25f))

        // Верхние лапы таракана + барабанные палочки, реально стучащие в такт!
        val lArmBase = Offset(bx - 14f * u, bodyCenterY - 8f * u)
        val rArmBase = Offset(bx + 14f * u, bodyCenterY - 8f * u)
        val lHand = Offset(bx - 26f * u, bodyCenterY + (4f - a.drumLeftStick * 12f) * u)
        val rHand = Offset(bx + 26f * u, bodyCenterY + (4f - a.drumRightStick * 12f) * u)
        drawLine(shellMid, lArmBase, lHand, 2.8f * u, StrokeCap.Round)
        drawLine(shellMid, rArmBase, rHand, 2.8f * u, StrokeCap.Round)

        // Палочки (при ударе опускаются точно к мембране барабана/тарелки и высекают искру!)
        val stickCol = Color(0xFFFDE68A)
        val lTip = Offset(
            snarePos.x + (1f - a.drumLeftStick) * 4f * u,
            snarePos.y - (18f - a.drumLeftStick * 16f) * u,
        )
        val rTip = Offset(
            if (a.drumSnare > 0.35f) cymRX - 10f * u else tomPos.x - 4f * u,
            if (a.drumSnare > 0.35f) cymRY + 2f * u else tomPos.y - (18f - a.drumRightStick * 16f) * u,
        )
        drawLine(stickCol, lHand, lTip, 2.4f * u, StrokeCap.Round)
        drawLine(stickCol, rHand, rTip, 2.4f * u, StrokeCap.Round)
        drawCircle(Color.White, 2.2f * u, lTip)
        drawCircle(Color.White, 2.2f * u, rTip)

        // Искры при ударе палочкой
        if (a.drumLeftStick > 0.55f) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * a.drumLeftStick), drumCol.copy(alpha = 0.3f), Color.Transparent), lTip, 12f * u), 12f * u, lTip)
        }
        if (a.drumRightStick > 0.55f) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * a.drumRightStick), drumCol.copy(alpha = 0.3f), Color.Transparent), rTip, 13f * u), 13f * u, rTip)
        }
    }

    // ===================== 4. ШОТЛАНДСКИЙ ВИСЛОУХИЙ КОТ С МИКРОФОНОМ (слева-впереди) =====================
    run {
        val cx = w * 0.21f
        val floor = stageFloorY + 4f * u
        val sway = sin(a.phase * 1.8f) * (1.2f + vocal * 3.5f) * u
        val catFur = Color(0xFF8C99AE)
        val catFurDark = Color(0xFF5D697D)
        val catBelly = Color(0xFFDCE3EE)

        // Пушистый хвостик позади кота
        val tailWag = sin(a.phase * 2.6f) * (5f + vocal * 7f) * u
        a.pathBack1.reset()
        a.pathBack1.moveTo(cx - 14f * u, floor - 14f * u)
        a.pathBack1.cubicTo(cx - 34f * u, floor - 16f * u, cx - 40f * u, floor - 36f * u + tailWag, cx - 30f * u, floor - 48f * u + tailWag)
        drawPath(a.pathBack1, catFurDark, style = Stroke(7.5f * u, cap = StrokeCap.Round))
        drawPath(a.pathBack1, catFur, style = Stroke(5.0f * u, cap = StrokeCap.Round))

        // Плюшевое тельце кота
        val bodyY = floor - 28f * u
        drawOval(
            Brush.radialGradient(listOf(catFur, catFurDark), Offset(cx - 4f * u, bodyY - 8f * u), 30f * u),
            Offset(cx - 20f * u, bodyY - 22f * u),
            Size(40f * u, 46f * u),
        )
        // Светлая грудка
        drawOval(catBelly.copy(alpha = 0.85f), Offset(cx - 10f * u, bodyY - 12f * u), Size(20f * u, 28f * u))
        // Лапки внизу
        drawOval(catFur, Offset(cx - 16f * u, floor - 9f * u), Size(13f * u, 9f * u))
        drawOval(catFur, Offset(cx + 3f * u, floor - 9f * u), Size(13f * u, 9f * u))

        // Круглая голова шотландского вислоухого кота (наклоняется к микрофону при пении!)
        val hx = cx + sway * 0.7f + vocal * 3.5f * u
        val hy = bodyY - 34f * u - vocal * 2.5f * u
        val headR = 23f * u

        // Характерные ВИСЯЧИЕ УШКИ шотландского вислоухого кота (сложены вперёд и вниз по бокам макушки!)
        a.pathMain.reset()
        a.pathMain.moveTo(hx - headR * 0.72f, hy - headR * 0.58f)
        a.pathMain.cubicTo(hx - headR * 1.18f, hy - headR * 0.82f, hx - headR * 1.24f, hy - headR * 0.12f, hx - headR * 0.82f, hy + headR * 0.02f)
        a.pathMain.close()
        drawPath(a.pathMain, catFurDark)
        a.pathMain.reset()
        a.pathMain.moveTo(hx + headR * 0.72f, hy - headR * 0.58f)
        a.pathMain.cubicTo(hx + headR * 1.18f, hy - headR * 0.82f, hx + headR * 1.24f, hy - headR * 0.12f, hx + headR * 0.82f, hy + headR * 0.02f)
        a.pathMain.close()
        drawPath(a.pathMain, catFurDark)

        // Круглая плюшевая голова
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFA7B3C7), catFur, catFurDark), Offset(hx - 6f * u, hy - 8f * u), headR * 1.2f),
            headR,
            Offset(hx, hy),
        )
        // Повторно кончики висячих ушек чуть поверх края головы (узнаваемый сгиб Scottish Fold!)
        drawOval(catFurDark, Offset(hx - headR * 1.12f, hy - headR * 0.52f), Size(11f * u, 13f * u))
        drawOval(catFurDark, Offset(hx + headR * 0.65f, hy - headR * 0.52f), Size(11f * u, 13f * u))

        // Большие круглые янтарные глаза (при громком пении счастливо прищуриваются!)
        val eyeLX = hx - 8.5f * u
        val eyeRX = hx + 8.5f * u
        val eyeY = hy - 2f * u
        if (vocal > 0.62f) {
            drawArc(Color(0xFF1E293B), 200f, 140f, false, Offset(eyeLX - 5.5f * u, eyeY - 3f * u), Size(11f * u, 8f * u), style = Stroke(2.2f * u, cap = StrokeCap.Round))
            drawArc(Color(0xFF1E293B), 200f, 140f, false, Offset(eyeRX - 5.5f * u, eyeY - 3f * u), Size(11f * u, 8f * u), style = Stroke(2.2f * u, cap = StrokeCap.Round))
        } else {
            drawCircle(Color(0xFFF59E0B), 5.6f * u, Offset(eyeLX, eyeY))
            drawCircle(Color(0xFFF59E0B), 5.6f * u, Offset(eyeRX, eyeY))
            drawCircle(Color(0xFF0F172A), 3.2f * u, Offset(eyeLX + 0.5f * u, eyeY))
            drawCircle(Color(0xFF0F172A), 3.2f * u, Offset(eyeRX + 0.5f * u, eyeY))
            drawCircle(Color.White, 1.4f * u, Offset(eyeLX - 1.4f * u, eyeY - 1.6f * u))
            drawCircle(Color.White, 1.4f * u, Offset(eyeRX - 1.4f * u, eyeY - 1.6f * u))
        }

        // Пухлые светлые щёчки и розовый носик
        drawCircle(catBelly, 5.2f * u, Offset(hx - 4.5f * u, hy + 7.5f * u))
        drawCircle(catBelly, 5.2f * u, Offset(hx + 4.5f * u, hy + 7.5f * u))
        a.pathMain.reset()
        a.pathMain.moveTo(hx - 3.2f * u, hy + 4.2f * u)
        a.pathMain.lineTo(hx + 3.2f * u, hy + 4.2f * u)
        a.pathMain.lineTo(hx, hy + 7.4f * u)
        a.pathMain.close()
        drawPath(a.pathMain, Color(0xFFF472B6))

        // Усики кота
        val wCol = Color(0xFFE2E8F0).copy(alpha = 0.7f)
        for (k in -1..1) {
            drawLine(wCol, Offset(hx - 10f * u, hy + (6 + k * 2.5f) * u), Offset(hx - 24f * u, hy + (5 + k * 4.5f) * u), 1.1f * u)
            drawLine(wCol, Offset(hx + 10f * u, hy + (6 + k * 2.5f) * u), Offset(hx + 24f * u, hy + (5 + k * 4.5f) * u), 1.1f * u)
        }

        // Ротик кота: открывается ТОЛЬКО когда звучат вокальные частоты!
        if (a.vocalMouth > 0.08f) {
            val mw = (6f + a.vocalMouth * 5.5f) * u
            val mh = (3.5f + a.vocalMouth * 10f) * u
            drawOval(Color(0xFF881337), Offset(hx - mw * 0.5f, hy + 8.5f * u), Size(mw, mh))
            drawOval(Color(0xFFFB7185), Offset(hx - mw * 0.34f, hy + 8.5f * u + mh * 0.55f), Size(mw * 0.68f, mh * 0.42f))
        } else {
            drawArc(Color(0xFF334155), 15f, 150f, false, Offset(hx - 4.5f * u, hy + 7f * u), Size(9f * u, 5f * u), style = Stroke(1.4f * u, cap = StrokeCap.Round))
        }

        // Стойка и ретро-микрофон перед котом
        val micX = cx + 28f * u
        val micHeadY = hy + 6f * u
        drawLine(Color(0xFF64748B), Offset(micX, floor), Offset(micX, micHeadY + 6f * u), 2.4f * u)
        drawOval(Color(0xFF475569), Offset(micX - 11f * u, floor - 3f * u), Size(22f * u, 6f * u))
        // Лапка кота на микрофонной стойке
        drawLine(catFur, Offset(cx + 12f * u, bodyY - 4f * u), Offset(micX - 1f * u, micHeadY + 14f * u), 5.5f * u, StrokeCap.Round)
        // Капсюль ретро-микрофона
        if (vocal > 0.14f) {
            drawCircle(
                Brush.radialGradient(listOf(catCol.copy(alpha = 0.48f * vocal), Color.Transparent), Offset(micX, micHeadY), 18f * u),
                18f * u,
                Offset(micX, micHeadY),
            )
        }
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8)), micHeadY - 8f * u, micHeadY + 6f * u),
            Offset(micX - 5.5f * u, micHeadY - 8f * u),
            Size(11f * u, 14f * u),
            CornerRadius(5.5f * u),
        )
        drawLine(Color(0xFF334155), Offset(micX - 5f * u, micHeadY - 1f * u), Offset(micX + 5f * u, micHeadY - 1f * u), 1.3f * u)

        // Летящие вверх светящиеся музыкальные нотки ♪ пока кот поёт!
        if (vocal > 0.14f) {
            for (nIdx in 0..2) {
                val prog = ((a.vocalNotePhase * 0.55f + nIdx * 0.33f) % 1f)
                val nAlpha = sin(prog * PI.toFloat()) * (0.35f + vocal * 0.65f)
                val nx = micX + (6f + nIdx * 6f + sin(prog * 5f + nIdx) * 8f) * u
                val ny = micHeadY - (12f + prog * 54f) * u
                val nCol = lerp(catCol, Color.White, 0.35f).copy(alpha = nAlpha.coerceIn(0f, 1f))
                drawOval(nCol, Offset(nx - 4f * u, ny - 2.5f * u), Size(6.5f * u, 5f * u))
                drawLine(nCol, Offset(nx + 2f * u, ny), Offset(nx + 2f * u, ny - 12f * u), 1.8f * u, StrokeCap.Round)
                drawLine(nCol, Offset(nx + 2f * u, ny - 12f * u), Offset(nx + 7.5f * u, ny - 9f * u), 1.8f * u, StrokeCap.Round)
            }
        }
    }

    // ===================== 5. БЕЛЫЙ КРЫС С ЭЛЕКТРОГИТАРОЙ (справа-впереди) =====================
    run {
        val rx = w * 0.79f
        val floor = stageFloorY + 4f * u
        val groove = sin(a.guitarPhase * 0.5f) * (1.2f + guitar * 4f) * u
        val ratWhite = Color(0xFFF8FAFC)
        val ratShade = Color(0xFFCBD5E1)
        val ratPink = Color(0xFFFDA4AF)

        // Длинный изогнутый розовый хвост белого крыса (ритмично виляет на гитарных риффах!)
        val tailSwing = sin(a.guitarPhase * 0.6f + 1f) * (4f + guitar * 9f) * u
        a.pathBack1.reset()
        a.pathBack1.moveTo(rx + 14f * u, floor - 14f * u)
        a.pathBack1.cubicTo(rx + 36f * u, floor - 8f * u, rx + 42f * u, floor - 34f * u + tailSwing, rx + 28f * u, floor - 52f * u + tailSwing)
        drawPath(a.pathBack1, ratPink, style = Stroke(3.6f * u, cap = StrokeCap.Round))

        // Белое тельце крыса
        val bodyY = floor - 28f * u
        drawOval(
            Brush.radialGradient(listOf(ratWhite, ratShade), Offset(rx - 4f * u, bodyY - 8f * u), 28f * u),
            Offset(rx - 18f * u, bodyY - 22f * u),
            Size(36f * u, 46f * u),
        )
        // Розовые лапки внизу
        drawOval(ratPink, Offset(rx - 14f * u, floor - 8f * u), Size(12f * u, 7.5f * u))
        drawOval(ratPink, Offset(rx + 2f * u, floor - 8f * u), Size(12f * u, 7.5f * u))

        // Голова белого крыса: вытянутая острая мордочка, круглые розовые ушки, розовый носик!
        val hx = rx - 2f * u + groove * 0.5f
        val hy = bodyY - 33f * u - a.guitarStrum * 3f * u

        // Большие круглые розовые ушки
        drawCircle(ratShade, 10.5f * u, Offset(hx + 2f * u, hy - 14f * u))
        drawCircle(ratPink, 7.2f * u, Offset(hx + 2f * u, hy - 14f * u))
        drawCircle(ratWhite, 10.5f * u, Offset(hx + 14f * u, hy - 10f * u))
        drawCircle(ratPink, 7.2f * u, Offset(hx + 14f * u, hy - 10f * u))

        // Вытянутая клиновидная белая мордочка (смотрит влево к центру сцены)
        a.pathMain.reset()
        a.pathMain.moveTo(hx - 26f * u, hy + 3f * u) // острый кончик носа
        a.pathMain.cubicTo(hx - 14f * u, hy - 14f * u, hx + 14f * u, hy - 16f * u, hx + 17f * u, hy + 2f * u)
        a.pathMain.cubicTo(hx + 16f * u, hy + 16f * u, hx - 8f * u, hy + 14f * u, hx - 26f * u, hy + 3f * u)
        a.pathMain.close()
        drawPath(a.pathMain, Brush.radialGradient(listOf(ratWhite, ratShade), Offset(hx - 4f * u, hy - 4f * u), 28f * u))

        // Розовый носик на самом кончике + усики + выразительный глазик
        drawCircle(Color(0xFFFB7185), 3.4f * u, Offset(hx - 26f * u, hy + 3f * u))
        drawCircle(Color(0xFF1E1B4B), 3.4f * u, Offset(hx - 5f * u, hy - 2.5f * u))
        drawCircle(Color.White, 1.2f * u, Offset(hx - 6f * u, hy - 3.6f * u))
        val rwCol = Color(0xFF94A3B8).copy(alpha = 0.7f)
        drawLine(rwCol, Offset(hx - 22f * u, hy + 4f * u), Offset(hx - 35f * u, hy), 1.1f * u)
        drawLine(rwCol, Offset(hx - 22f * u, hy + 5f * u), Offset(hx - 34f * u, hy + 7f * u), 1.1f * u)

        // ---- Электрогитара в лапах белого крыса ----
        val gCenter = Offset(rx - 4f * u, bodyY + 2f * u)
        val gAngle = -24f - a.guitarStrum * 8f + sin(a.guitarPhase * 0.5f) * guitar * 4f
        rotate(gAngle, gCenter) {
            // Электрическое свечение деки на гитарных частотах
            if (guitar > 0.14f) {
                drawCircle(
                    Brush.radialGradient(listOf(ratCol.copy(alpha = 0.45f * guitar), Color.Transparent), gCenter, 34f * u),
                    34f * u,
                    gCenter,
                )
            }
            // Корпус электрогитары (стильная красная дека + белый пикгард)
            drawOval(Color(0xFFDC2626), Offset(gCenter.x - 4f * u, gCenter.y - 14f * u), Size(28f * u, 28f * u))
            drawOval(Color(0xFFEF4444), Offset(gCenter.x - 16f * u, gCenter.y - 11f * u), Size(22f * u, 22f * u))
            drawOval(Color(0xFFF8FAFC).copy(alpha = 0.92f), Offset(gCenter.x - 6f * u, gCenter.y - 8f * u), Size(16f * u, 16f * u))
            // Длинный гриф влево + голова грифа с колками
            drawRoundRect(Color(0xFF78350F), Offset(gCenter.x - 52f * u, gCenter.y - 3.6f * u), Size(44f * u, 7.2f * u), CornerRadius(2f * u))
            drawRoundRect(Color(0xFFDC2626), Offset(gCenter.x - 62f * u, gCenter.y - 5.5f * u), Size(12f * u, 10.5f * u), CornerRadius(3f * u))
            // Звукосниматель и бридж
            drawRect(Color(0xFF0F172A), Offset(gCenter.x + 2f * u, gCenter.y - 6f * u), Size(4.5f * u, 12f * u))
            drawRect(Color(0xFF94A3B8), Offset(gCenter.x + 11f * u, gCenter.y - 5f * u), Size(3f * u, 10f * u))

            // 4 струны — вибрируют и светятся при гитарных частотах!
            val vib = sin(a.guitarPhase * 3.5f) * a.guitarStrum * 1.8f * u
            val strCol = lerp(Color(0xFFE2E8F0), ratCol, guitar * 0.7f)
            for (s in 0..3) {
                val sy = gCenter.y - 2.4f * u + s * 1.6f * u
                drawLine(strCol, Offset(gCenter.x - 56f * u, sy), Offset(gCenter.x + 12f * u, sy + (if (s % 2 == 0) vib else -vib)), 0.9f * u)
            }

            // Левая лапка на грифе (скользит вдоль ладов!)
            val fretX = gCenter.x - (22f + sin(a.guitarPhase * 0.7f) * guitar * 14f) * u
            drawCircle(ratPink, 4.2f * u, Offset(fretX, gCenter.y + 3f * u))

            // Правая лапка (ритмично бьёт по струнам!)
            val strumY = gCenter.y + sin(a.guitarPhase * 1.6f) * (2f + guitar * 7f + a.guitarStrum * 4f) * u
            drawCircle(ratWhite, 4.5f * u, Offset(gCenter.x + 5f * u, strumY))
            drawCircle(ratPink, 3.2f * u, Offset(gCenter.x + 4f * u, strumY))

            // Искры/волны от гитары на аккордах
            if (a.guitarStrum > 0.25f) {
                drawArc(
                    ratCol.copy(alpha = 0.75f * a.guitarStrum),
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(gCenter.x - 18f * u, gCenter.y - 22f * u),
                    size = Size(44f * u, 44f * u),
                    style = Stroke(2.2f * u, cap = StrokeCap.Round),
                )
            }
        }
    }

    // ===================== 6. Три тонких частотных индикатора на переднем крае сцены =====================
    val indY = h * 0.93f
    val indW = w * 0.22f
    listOf(
        Triple(w * 0.21f, vocal, catCol),
        Triple(w * 0.50f, drums, drumCol),
        Triple(w * 0.79f, guitar, ratCol),
    ).forEach { (ix, lvl, col) ->
        drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(ix - indW * 0.5f, indY), Size(indW, 3.5f * u), CornerRadius(2f * u))
        if (lvl > 0.03f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(col.copy(alpha = 0.6f), Color.White.copy(alpha = 0.9f)), ix - indW * 0.5f, ix - indW * 0.5f + indW * lvl),
                Offset(ix - indW * 0.5f, indY),
                Size((indW * lvl).coerceAtLeast(3f * u), 3.5f * u),
                CornerRadius(2f * u),
            )
        }
    }
}
