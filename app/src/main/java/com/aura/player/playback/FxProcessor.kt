package com.aura.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Профессиональный DSP-процессор в аудио-цепочке ExoPlayer (в духе AIMP):
 *  - поддерживает как 16-bit PCM, так и Hi-Res 32-bit Float (без потерь динамического диапазона);
 *  - режим Direct (чистый обход без обработки);
 *  - тембровые эффекты голоса (робот, эхо, зал, телефон, винил, караоке…);
 *  - 10-полосный параметрический эквалайзер + усиление баса;
 *  - стерео-баланс Л/П, расширение стереобазы, режим Моно;
 *  - мягкая автонормализация громкости (Auto-Level / RMS);
 *  - отвод сигнала в реальном времени для визуализаторов (без микрофона).
 */
@UnstableApi
class FxProcessor : BaseAudioProcessor() {

    enum class Mode { NONE, ROBOT, ECHO, HALL, DISTORT, TELEPHONE, UNDERWATER, DEMON, VINYL, KARAOKE, AUDIO_8D }
    enum class StemMode { OFF, VOCAL, BASS, INSTRUMENTAL }

    // ---------- параметры ----------
    @Volatile var mode: Mode = Mode.NONE
    @Volatile var stemMode: StemMode = StemMode.OFF
    @Volatile var directBypass: Boolean = false
    @Volatile var hiResDither: Boolean = true
    @Volatile var limiterEnabled: Boolean = true
    @Volatile private var gains = FloatArray(10)
    @Volatile private var eqOn = true
    @Volatile private var bassDb = 0f
    @Volatile private var deviceGains = FloatArray(10)
    @Volatile private var deviceEqActive = false
    @Volatile private var devicePreampDb = 0f
    @Volatile var width = 1f
    @Volatile var balance = 0f        // -1 (Л) .. 0 .. +1 (П)
    @Volatile var mono = false
    @Volatile var normalize = false
    @Volatile private var preampDb = 0f
    @Volatile private var version = 1
    private var applied = 0

    // живая телеметрия для бейджа качества (как в AIMP)
    @Volatile var liveSampleRate = 44100
        private set
    @Volatile var liveChannels = 2
        private set
    @Volatile var liveIsFloat = false
        private set

    fun setEq(enabled: Boolean, g: FloatArray, bass: Float, preamp: Float) {
        gains = g.copyOf()
        eqOn = enabled
        bassDb = bass
        preampDb = preamp
        version++
    }

    /** Отдельный 10-полосный AutoEQ слой калибровки под наушники/колонку (суммируется с пользовательским EQ). */
    fun setDeviceEq(enabled: Boolean, g: FloatArray, preamp: Float) {
        deviceEqActive = enabled
        deviceGains = g.copyOf()
        devicePreampDb = preamp
        version++
    }

    // ---------- визуализация (кольцевой буфер ~740 мс для точной синхронизации с AudioTrack и Bluetooth) ----------
    private val ring = FloatArray(32768)
    @Volatile private var ringPos = 0
    @Volatile var lastWriteNs = 0L
        private set

    fun snapshot(out: FloatArray, delayMs: Int = 0) {
        val p = ringPos
        val n = out.size
        val delaySamples = ((delayMs.coerceIn(0, 620).toLong() * liveSampleRate) / 1000L).toInt()
            .coerceIn(0, ring.size - n - 1)
        val base = p - n - delaySamples
        for (i in 0 until n) {
            var idx = (base + i) % ring.size
            if (idx < 0) idx += ring.size
            out[i] = ring[idx]
        }
    }

    fun clearRing() {
        ring.fill(0f)
        lastWriteNs = 0L
    }

    // ---------- состояние ----------
    private var sampleRate = 44100
    private var channels = 2
    private var inFloat = false
    private var outFloat = false

    private val nFilters = 11
    private val coef = DoubleArray(nFilters * 5)
    private var zs = DoubleArray(nFilters * 2 * 2)
    private var activeFilters = IntArray(0)
    private var gainLin = 1f

    // Выделенные 24 dB/oct фильтры для DSP-изоляции дорожек (Голос / Бас / Инструментал)
    // 0,1 = HP 230 Гц (вокал); 2,3 = LP 3700 Гц (вокал/воздух); 4,5 = LP 175 Гц (бас)
    private val stemCoef = DoubleArray(6 * 5)
    private val stemZ = DoubleArray(6 * 2 * 2)
    private var stemMidEnv = 0.05f
    private var stemSideEnv = 0.05f

    private var delay: Array<FloatArray> = emptyArray()
    private var delayLen = 0
    private var pos = 0
    private val lpA = FloatArray(8)
    private val lpB = FloatArray(8)
    private val hpLp = FloatArray(8)
    private var phase = 0.0
    private var lfo = 0.0
    private var panPhase = 0.0
    private var crackle = 0f
    private var rnd = 22222
    private var rmsEnv = 0.2f

    private val combBase = intArrayOf(1116, 1188, 1277, 1356)
    private val apBase = intArrayOf(556, 441)
    private var combs: Array<Array<FloatArray>> = emptyArray()
    private var combPos: Array<IntArray> = emptyArray()
    private var combLp: Array<FloatArray> = emptyArray()
    private var aps: Array<Array<FloatArray>> = emptyArray()
    private var apPos: Array<IntArray> = emptyArray()

    private val frame = FloatArray(8)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Всегда сохраняем формат входа (PCM 16-bit или PCM Float), чтобы не ломать SonicAudioProcessor
        // и Bluetooth AudioTrack на прошивках Huawei/Xiaomi/Samsung!
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        channels = inputAudioFormat.channelCount.coerceIn(1, 8)
        inFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        outFloat = outputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        liveSampleRate = sampleRate
        liveChannels = channels
        liveIsFloat = outFloat || hiResDither
        ring.fill(0f)

        delayLen = sampleRate
        delay = Array(channels) { FloatArray(delayLen) }
        pos = 0
        lpA.fill(0f); lpB.fill(0f); hpLp.fill(0f)
        zs = DoubleArray(nFilters * channels * 2)
        stemZ.fill(0.0)
        stemMidEnv = 0.05f
        stemSideEnv = 0.05f
        rmsEnv = 0.2f

        val k = sampleRate / 44100.0
        combs = Array(channels) { c -> Array(4) { i -> FloatArray(((combBase[i] + c * 23) * k).toInt().coerceAtLeast(16)) } }
        combPos = Array(channels) { IntArray(4) }
        combLp = Array(channels) { FloatArray(4) }
        aps = Array(channels) { c -> Array(2) { i -> FloatArray(((apBase[i] + c * 23) * k).toInt().coerceAtLeast(16)) } }
        apPos = Array(channels) { IntArray(2) }
        applied = 0
    }

    override fun onReset() {
        delay = emptyArray()
        delayLen = 0
        combs = emptyArray()
        aps = emptyArray()
    }

    private fun recompute() {
        applied = version
        val g = gains
        val dg = deviceGains
        val devOn = deviceEqActive
        val list = ArrayList<Int>()
        val sr = sampleRate.toDouble()
        var maxBoost = 0f
        if (eqOn || devOn) {
            for (i in 0 until 10) {
                val f = EqPresets.freqs[i].toDouble()
                val userDb = if (eqOn) g.getOrElse(i) { 0f } else 0f
                val devDb = if (devOn) dg.getOrElse(i) { 0f } else 0f
                val db = (userDb + devDb).coerceIn(-12f, 12f)
                if (abs(db) < 0.05f || f >= sr * 0.45) continue
                peaking(i, f, 1.41, db.toDouble(), sr)
                list += i
                maxBoost = max(maxBoost, db)
            }
            if (eqOn && bassDb > 0.05f) {
                lowShelf(10, 105.0, bassDb.toDouble(), sr)
                list += 10
                maxBoost = max(maxBoost, bassDb * 0.8f)
            }
        }
        activeFilters = list.toIntArray()
        val pre = (if (eqOn) preampDb else 0f) + (if (devOn) devicePreampDb else 0f) - maxBoost * 0.45f
        gainLin = 10.0.pow(pre / 20.0).toFloat()
        zs.fill(0.0)

        // Коэффициенты 24 dB/oct фильтров для изоляции дорожек (Голос / Бас / Инструментал)
        setStemBiquad(0, hp = true, f = 230.0, q = 0.7071, sr = sr)
        setStemBiquad(1, hp = true, f = 230.0, q = 0.7071, sr = sr)
        setStemBiquad(2, hp = false, f = 3700.0, q = 0.7071, sr = sr)
        setStemBiquad(3, hp = false, f = 3700.0, q = 0.7071, sr = sr)
        setStemBiquad(4, hp = false, f = 175.0, q = 0.7071, sr = sr)
        setStemBiquad(5, hp = false, f = 175.0, q = 0.7071, sr = sr)
    }

    private fun setStemBiquad(idx: Int, hp: Boolean, f: Double, q: Double, sr: Double) {
        val clampedF = f.coerceIn(20.0, sr * 0.44)
        val w0 = 2.0 * PI * clampedF / sr
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        val b0 = (if (hp) (1.0 + cw) else (1.0 - cw)) * 0.5 / a0
        val b1 = (if (hp) -(1.0 + cw) else (1.0 - cw)) / a0
        val b2 = b0
        val a1 = (-2.0 * cw) / a0
        val a2 = (1.0 - alpha) / a0
        val o = idx * 5
        stemCoef[o] = b0; stemCoef[o + 1] = b1; stemCoef[o + 2] = b2; stemCoef[o + 3] = a1; stemCoef[o + 4] = a2
    }

    private fun runStemFilter(idx: Int, ch: Int, input: Double): Double {
        val o = idx * 5
        val zi = (idx * 2 + ch) * 2
        val y = stemCoef[o] * input + stemZ[zi]
        stemZ[zi] = stemCoef[o + 1] * input - stemCoef[o + 3] * y + stemZ[zi + 1]
        stemZ[zi + 1] = stemCoef[o + 2] * input - stemCoef[o + 4] * y
        return y
    }

    private fun peaking(i: Int, f: Double, q: Double, db: Double, sr: Double) {
        val a = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f / sr
        val alpha = sin(w0) / (2 * q)
        val cw = cos(w0)
        val a0 = 1 + alpha / a
        set(i, (1 + alpha * a) / a0, (-2 * cw) / a0, (1 - alpha * a) / a0, (-2 * cw) / a0, (1 - alpha / a) / a0)
    }

    private fun lowShelf(i: Int, f: Double, db: Double, sr: Double) {
        val a = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f / sr
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cw + sa
        set(i,
            a * ((a + 1) - (a - 1) * cw + sa) / a0,
            2 * a * ((a - 1) - (a + 1) * cw) / a0,
            a * ((a + 1) - (a - 1) * cw - sa) / a0,
            -2 * ((a - 1) + (a + 1) * cw) / a0,
            ((a + 1) + (a - 1) * cw - sa) / a0)
    }

    private fun set(i: Int, b0: Double, b1: Double, b2: Double, a1: Double, a2: Double) {
        val o = i * 5
        coef[o] = b0; coef[o + 1] = b1; coef[o + 2] = b2; coef[o + 3] = a1; coef[o + 4] = a2
    }

    private fun coefLp(fc: Float): Float = (1.0 - exp(-2.0 * PI * fc / sampleRate)).toFloat()

    private fun noise(): Float {
        rnd = rnd * 1103515245 + 12345
        return ((rnd ushr 8) and 0xFFFF) / 32768f - 1f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (applied != version) recompute()

        val ch = channels
        val inFl = inFloat
        val outFl = outFloat
        val inBps = if (inFl) 4 else 2
        val outBps = if (outFl) 4 else 2
        val inFrameBytes = inBps * ch
        val frames = size / inFrameBytes
        if (frames <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val out = replaceOutputBuffer(frames * ch * outBps)

        val m = mode
        val stem = stemMode
        val filters = activeFilters
        val g = gainLin
        val w = width
        val bal = balance
        val isMono = mono
        val isNorm = normalize
        val stereo = ch == 2
        val dither = hiResDither && !outFl && !directBypass
        val bypass = directBypass || (
            !dither && m == Mode.NONE && stem == StemMode.OFF && filters.isEmpty() && abs(g - 1f) < 0.001f &&
                (!stereo || (abs(w - 1f) < 0.01f && abs(bal) < 0.01f && !isMono)) && !isNorm
            )
        val ready = delay.size == ch && delayLen > 0

        if (bypass || !ready) {
            for (f in 0 until frames) {
                var s = 0f
                for (c in 0 until ch) {
                    val v = if (inFl) inputBuffer.float else inputBuffer.short / 32768f
                    s += v
                    if (outFl) out.putFloat(v) else out.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                }
                tap(s / ch)
            }
            out.flip()
            lastWriteNs = System.nanoTime()
            return
        }

        val sr = sampleRate
        val combLen = (0.006f * sr).toInt()
        val echoLen = (0.28f * sr).toInt()
        val demonLen = (0.16f * sr).toInt()
        val ringInc = 2.0 * PI * 70.0 / sr
        val lfoInc = 2.0 * PI * 0.5 / sr
        val panInc = 2.0 * PI * 0.16 / sr
        val aTel1 = coefLp(450f)
        val aTel2 = coefLp(3200f)
        val aSoft = coefLp(5000f)
        val aDemon = coefLp(2400f)
        val aVinylHp = coefLp(90f)
        val aVinylLp = coefLp(5500f)
        val aKaraokeLp = coefLp(160f)

        val balL = if (bal > 0f) 1f - bal else 1f
        val balR = if (bal < 0f) 1f + bal else 1f
        val normAlpha = 1f / (sr * 0.45f)
        val stemEnvAlpha = 1f / (sr * 0.018f)

        for (fIdx in 0 until frames) {
            if (inFl) {
                for (c in 0 until ch) frame[c] = inputBuffer.float
            } else {
                for (c in 0 until ch) frame[c] = inputBuffer.short / 32768f
            }

            // ---- 1. эффект голоса ----
            if (m == Mode.KARAOKE && stereo) {
                val l = frame[0]; val r = frame[1]
                val mid = (l + r) * 0.5f
                lpA[0] += aKaraokeLp * (mid - lpA[0])
                val side = (l - r) * 0.5f
                frame[0] = side * 1.4f + lpA[0]
                frame[1] = -side * 1.4f + lpA[0]
            } else if (m == Mode.AUDIO_8D) {
                if (stereo) {
                    val l = frame[0] * 0.78f + reverb(0, frame[0]) * 0.34f
                    val r = frame[1] * 0.78f + reverb(1, frame[1]) * 0.34f
                    val pan = 0.5f + 0.45f * sin(panPhase).toFloat()
                    val angle = pan * (PI / 2.0)
                    frame[0] = l * cos(angle).toFloat() * 1.35f
                    frame[1] = r * sin(angle).toFloat() * 1.35f
                } else {
                    for (c in 0 until ch) frame[c] = frame[c] * 0.78f + reverb(c, frame[c]) * 0.34f
                }
            } else if (m != Mode.NONE && m != Mode.KARAOKE) {
                if (m == Mode.VINYL) {
                    if (crackle > 0.001f) crackle *= 0.93f
                    if (noise() > 0.9993f) crackle = 0.15f + 0.25f * abs(noise())
                }
                for (c in 0 until ch) {
                    val x = frame[c]
                    val buf = delay[c]
                    frame[c] = when (m) {
                        Mode.ROBOT -> {
                            val rm = x * sin(phase).toFloat()
                            val d = buf[(pos - combLen + delayLen) % delayLen]
                            val v = rm * 1.3f + d * 0.55f
                            buf[pos] = v
                            v * 0.85f
                        }
                        Mode.ECHO -> {
                            val d = buf[(pos - echoLen + delayLen) % delayLen]
                            buf[pos] = x + d * 0.45f
                            x * 0.8f + d * 0.55f
                        }
                        Mode.HALL -> x * 0.75f + reverb(c, x) * 0.45f
                        Mode.DISTORT -> {
                            val drive = (tanh(x * 6.0) / tanh(6.0)).toFloat()
                            lpA[c] += aSoft * (drive - lpA[c])
                            lpA[c] * 0.75f
                        }
                        Mode.TELEPHONE -> {
                            hpLp[c] += aTel1 * (x - hpLp[c])
                            val hp = x - hpLp[c]
                            lpA[c] += aTel2 * (hp - lpA[c])
                            lpB[c] += aTel2 * (lpA[c] - lpB[c])
                            (tanh(lpB[c] * 3.0) * 0.8).toFloat()
                        }
                        Mode.UNDERWATER -> {
                            val fc = 380f + 260f * sin(lfo).toFloat()
                            val a = coefLp(fc)
                            lpA[c] += a * (x - lpA[c])
                            lpB[c] += a * (lpA[c] - lpB[c])
                            lpB[c] * 1.6f
                        }
                        Mode.DEMON -> {
                            val drive = (tanh(x * 4.0) / tanh(4.0)).toFloat()
                            lpA[c] += aDemon * (drive - lpA[c])
                            val d = buf[(pos - demonLen + delayLen) % delayLen]
                            buf[pos] = lpA[c] + d * 0.35f
                            lpA[c] * 0.75f + d * 0.4f
                        }
                        Mode.VINYL -> {
                            hpLp[c] += aVinylHp * (x - hpLp[c])
                            val hp = x - hpLp[c]
                            lpA[c] += aVinylLp * (hp - lpA[c])
                            val warm = (tanh(lpA[c] * 1.6) / 1.3).toFloat()
                            warm + crackle * (if (c == 0) 1f else 0.8f) * noise() + noise() * 0.003f
                        }
                        else -> x
                    }
                }
            }

            // ---- 1.5. DSP-изоляция дорожки (Только голос / Только бас / Только инструментал) ----
            when (stem) {
                StemMode.BASS -> {
                    for (c in 0 until minOf(ch, 2)) {
                        val lp1 = runStemFilter(4, c, frame[c].toDouble())
                        val lp2 = runStemFilter(5, c, lp1)
                        frame[c] = (lp2 * 1.45).toFloat()
                    }
                }
                StemMode.VOCAL -> {
                    val mid = if (stereo) (frame[0] + frame[1]) * 0.5 else frame[0].toDouble()
                    val side = if (stereo) (frame[0] - frame[1]) * 0.5 else 0.0
                    // Полосовой 24 дБ/окт (230..3700 Гц) для центрального вокала и бокового канала
                    val vMid = runStemFilter(3, 0, runStemFilter(2, 0, runStemFilter(1, 0, runStemFilter(0, 0, mid))))
                    val vSide = if (stereo) runStemFilter(3, 1, runStemFilter(2, 1, runStemFilter(1, 1, runStemFilter(0, 1, side)))) else 0.0
                    stemMidEnv += stemEnvAlpha * (abs(vMid.toFloat()) - stemMidEnv)
                    stemSideEnv += stemEnvAlpha * (abs(vSide.toFloat()) - stemSideEnv)
                    val centerRatio = if (stereo) {
                        val r = stemMidEnv / (stemMidEnv + stemSideEnv * 1.45f + 1e-4f)
                        (r * r).coerceIn(0f, 1f)
                    } else 1f
                    val vocalOut = (vMid * (0.22 + 1.45 * centerRatio)).toFloat()
                    for (c in 0 until ch) frame[c] = vocalOut
                }
                StemMode.INSTRUMENTAL -> {
                    if (stereo) {
                        val mid = (frame[0] + frame[1]) * 0.5
                        val side = (frame[0] - frame[1]) * 0.5
                        // Сохраняем плотный бас (LP 175 Гц) + верхний воздух (>3700 Гц) + стерео-инструменты (side)
                        val midBass = runStemFilter(5, 0, runStemFilter(4, 0, mid))
                        val midLoMid = runStemFilter(3, 0, runStemFilter(2, 0, mid))
                        val midAir = (mid - midLoMid) * 0.72
                        val instSide = side * 1.65
                        frame[0] = (midBass + midAir + instSide).toFloat()
                        frame[1] = (midBass + midAir - instSide).toFloat()
                    } else {
                        // Для моно-трека оставляем бас + верх, подавляя середину вокала
                        val x = frame[0].toDouble()
                        val bassOnly = runStemFilter(5, 0, runStemFilter(4, 0, x))
                        val hiOnly = x - runStemFilter(3, 0, runStemFilter(2, 0, x))
                        frame[0] = (bassOnly + hiOnly * 0.85).toFloat()
                    }
                }
                StemMode.OFF -> Unit
            }

            // ---- 2. эквалайзер (пользовательский + AutoEQ калибровка устройства) ----
            if (filters.isNotEmpty()) {
                for (c in 0 until ch) {
                    var v = frame[c].toDouble()
                    for (f in filters) {
                        val o = f * 5
                        val zi = (f * ch + c) * 2
                        val y = coef[o] * v + zs[zi]
                        zs[zi] = coef[o + 1] * v - coef[o + 3] * y + zs[zi + 1]
                        zs[zi + 1] = coef[o + 2] * v - coef[o + 4] * y
                        v = y
                    }
                    frame[c] = v.toFloat()
                }
            }

            // ---- 3. моно / стереобаза / баланс Л-П ----
            if (stereo) {
                if (isMono) {
                    val mVal = (frame[0] + frame[1]) * 0.5f
                    frame[0] = mVal
                    frame[1] = mVal
                } else if (abs(w - 1f) >= 0.01f) {
                    val mid = (frame[0] + frame[1]) * 0.5f
                    val side = (frame[0] - frame[1]) * 0.5f * w
                    frame[0] = mid + side
                    frame[1] = mid - side
                }
                if (abs(bal) >= 0.01f) {
                    frame[0] *= balL
                    frame[1] *= balR
                }
            }

            // ---- 4. мягкая автонормализация громкости (AIMP Auto-Level) ----
            var normGain = 1f
            if (isNorm) {
                var energy = 0f
                for (c in 0 until ch) energy += abs(frame[c])
                energy /= ch
                rmsEnv += normAlpha * (energy - rmsEnv)
                normGain = (0.22f / rmsEnv.coerceAtLeast(0.03f)).coerceIn(0.65f, 2.2f)
            }

            var monoSum = 0f
            val totalGain = g * normGain
            val useLimiter = limiterEnabled
            for (c in 0 until ch) {
                val rawOut = frame[c] * totalGain
                val v = if (useLimiter) softClip(rawOut) else rawOut.coerceIn(-1f, 1f)
                monoSum += v
                if (outFl) {
                    out.putFloat(v)
                } else {
                    // В режиме Hi-Res 32-bit DSP применяем треугольный TPDF-дизеринг перед квантованием в 16 бит
                    val d = if (dither) (noise() + noise()) * (0.5f / 32768f) else 0f
                    val s16 = ((v + d).coerceIn(-1f, 1f) * 32767f).toInt()
                    out.putShort(s16.toShort())
                }
            }
            tap(monoSum / ch)

            pos++
            if (pos >= delayLen) pos = 0
            phase += ringInc
            if (phase > 2 * PI) phase -= 2 * PI
            lfo += lfoInc
            if (lfo > 2 * PI) lfo -= 2 * PI
            panPhase += panInc
            if (panPhase > 2 * PI) panPhase -= 2 * PI
        }
        out.flip()
        lastWriteNs = System.nanoTime()
    }

    private fun reverb(c: Int, x: Float): Float {
        if (combs.size <= c) return 0f
        var acc = 0f
        val cb = combs[c]
        val cp = combPos[c]
        val lp = combLp[c]
        for (i in 0 until 4) {
            val b = cb[i]
            val p = cp[i]
            val y = b[p]
            lp[i] = y * 0.8f + lp[i] * 0.2f
            b[p] = x * 0.3f + lp[i] * 0.84f
            cp[i] = if (p + 1 >= b.size) 0 else p + 1
            acc += y
        }
        val ab = aps[c]
        val ap = apPos[c]
        for (i in 0 until 2) {
            val b = ab[i]
            val p = ap[i]
            val bo = b[p]
            val y = -acc + bo
            b[p] = acc + bo * 0.5f
            ap[i] = if (p + 1 >= b.size) 0 else p + 1
            acc = y
        }
        return acc * 0.5f
    }

    private fun tap(v: Float) {
        val p = ringPos
        ring[p] = v
        ringPos = if (p + 1 >= ring.size) 0 else p + 1
    }

    private fun softClip(v: Float): Float {
        val a = abs(v)
        if (a <= 0.9f) return v
        val s = 0.9f + 0.1f * tanh(((a - 0.9f) / 0.1f).toDouble()).toFloat()
        return if (v < 0) -s else s
    }
}
