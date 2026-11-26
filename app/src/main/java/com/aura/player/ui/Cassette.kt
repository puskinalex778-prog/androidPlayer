package com.aura.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ретро-аудиокассета. На наклейке — обложка альбома из проигрываемого файла (если она есть),
 * катушки крутятся во время воспроизведения, а плёнка «перематывается» с левой катушки на правую по мере прогресса.
 */
@Composable
fun Cassette(
    art: ImageBitmap?,
    title: String,
    subtitle: String,
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    seed: String = title,
) {
    val c = LocalAura.current
    var angleL by remember { mutableFloatStateOf(0f) }
    var angleR by remember { mutableFloatStateOf(0f) }
    val p = progress.coerceIn(0f, 1f)

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    // угловая скорость обратно пропорциональна радиусу намотки — как у настоящей кассеты
                    val rl = 0.45f + 0.55f * (1f - p)
                    val rr = 0.45f + 0.55f * p
                    angleL = (angleL + dt * 160f / rl) % 360f
                    angleR = (angleR + dt * 160f / rr) % 360f
                }
                last = now
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val labelBrush = remember(seed) { seedGradient(seed) }

    Canvas(modifier.aspectRatio(1f / 0.64f)) {
        val w = size.width
        val h = size.height
        val corner = w * 0.045f

        // ---- тень и корпус ----
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(0f, h * 0.025f), Size(w, h), CornerRadius(corner))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF34343F), Color(0xFF1B1B23), Color(0xFF121218))),
            Offset.Zero, Size(w, h), CornerRadius(corner),
        )
        // лёгкий цветной отлив корпуса в цвет темы
        drawRoundRect(
            Brush.linearGradient(listOf(c.accent.copy(alpha = 0.18f), Color.Transparent, c.accent2.copy(alpha = 0.14f))),
            Offset.Zero, Size(w, h), CornerRadius(corner),
        )
        drawRoundRect(Color.White.copy(alpha = 0.12f), Offset.Zero, Size(w, h), CornerRadius(corner), style = Stroke(w * 0.004f))

        // ---- винты ----
        val sr = w * 0.016f
        listOf(Offset(w * 0.035f, h * 0.05f), Offset(w * 0.965f, h * 0.05f), Offset(w * 0.035f, h * 0.95f), Offset(w * 0.965f, h * 0.95f), Offset(w * 0.5f, h * 0.9f))
            .forEach { o -> screw(o, sr) }

        // ---- наклейка ----
        val lx = w * 0.065f
        val ly = h * 0.065f
        val lw = w * 0.87f
        val lh = h * 0.64f
        val lc = w * 0.022f
        val labelPath = Path().apply { addRoundRect(RoundRect(lx, ly, lx + lw, ly + lh, CornerRadius(lc))) }
        clipPath(labelPath) {
            if (art != null) {
                // обложка «по центру с обрезкой»
                val iw = art.width.toFloat(); val ih = art.height.toFloat()
                val scale = maxOf(lw / iw, lh / ih)
                val sw = lw / scale; val sh = lh / scale
                val sx = (iw - sw) / 2; val sy = (ih - sh) / 2
                drawImage(
                    art,
                    srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                    srcSize = IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
                    dstOffset = IntOffset(lx.roundToInt(), ly.roundToInt()),
                    dstSize = IntSize(lw.roundToInt(), lh.roundToInt()),
                )
            } else {
                // ретро-наклейка с полосками
                drawRect(Color(0xFFF4EBDD), Offset(lx, ly), Size(lw, lh))
                val stripes = listOf(c.accent, c.accent2, c.accent3)
                stripes.forEachIndexed { i, col ->
                    drawRect(col, Offset(lx, ly + lh * (0.58f + i * 0.075f)), Size(lw, lh * 0.06f))
                }
                drawRect(labelBrush, Offset(lx, ly + lh * 0.82f), Size(lw, lh * 0.18f), alpha = 0.85f)
            }
            // затемнение сверху для текста
            drawRect(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (art != null) 0.65f else 0f), Color.Transparent), ly, ly + lh * 0.38f),
                Offset(lx, ly), Size(lw, lh * 0.38f),
            )
        }
        drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(lx, ly), Size(lw, lh), CornerRadius(lc), style = Stroke(w * 0.003f))

        // ---- сторона «A» и название ----
        val textColor = if (art != null) Color.White else Color(0xFF2A2230)
        val sideSize = lh * 0.17f
        drawRoundRect(c.accent, Offset(lx + lw * 0.025f, ly + lh * 0.05f), Size(sideSize, sideSize), CornerRadius(sideSize * 0.25f))
        val sideLayout = measurer.measure("A", TextStyle(color = Color.White, fontSize = (sideSize * 0.62f).toSp(), fontWeight = FontWeight.Black))
        drawText(sideLayout, topLeft = Offset(lx + lw * 0.025f + (sideSize - sideLayout.size.width) / 2, ly + lh * 0.05f + (sideSize - sideLayout.size.height) / 2))

        val tx = lx + lw * 0.05f + sideSize
        val tWidth = (lw - (tx - lx) - lw * 0.04f).roundToInt().coerceAtLeast(10)
        val titleLayout = measurer.measure(
            title.ifBlank { "Aura Player" },
            TextStyle(color = textColor, fontSize = (lh * 0.095f).toSp(), fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif),
            maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = tWidth),
        )
        drawText(titleLayout, topLeft = Offset(tx, ly + lh * 0.04f))
        val subLayout = measurer.measure(
            subtitle,
            TextStyle(color = textColor.copy(alpha = 0.8f), fontSize = (lh * 0.07f).toSp(), fontWeight = FontWeight.Medium),
            maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = tWidth),
        )
        drawText(subLayout, topLeft = Offset(tx, ly + lh * 0.04f + titleLayout.size.height))

        // ---- окошко с плёнкой ----
        val ww = w * 0.56f
        val wh = h * 0.22f
        val wx = (w - ww) / 2
        val wy = ly + lh * 0.50f
        val cy = wy + wh / 2
        val lcx = w / 2 - w * 0.165f
        val rcx = w / 2 + w * 0.165f
        val hub = w * 0.045f
        val maxR = w * 0.118f
        val rL = hub + (maxR - hub) * (1f - p)
        val rR = hub + (maxR - hub) * p
        val winPath = Path().apply { addRoundRect(RoundRect(wx, wy, wx + ww, wy + wh, CornerRadius(wh * 0.5f))) }
        drawPath(winPath, Color(0xFF0B0B10).copy(alpha = 0.88f))
        clipPath(winPath) {
            // намотанная плёнка
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5A3A22), Color(0xFF2E1C10)), Offset(lcx, cy), rL), rL, Offset(lcx, cy))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5A3A22), Color(0xFF2E1C10)), Offset(rcx, cy), rR), rR, Offset(rcx, cy))
            // шкала посередине
            for (i in 0..6) {
                val x = w / 2 - w * 0.05f + i * w * 0.0167f
                drawLine(Color.White.copy(alpha = 0.25f), Offset(x, cy - wh * 0.18f), Offset(x, cy + wh * 0.18f), w * 0.002f)
            }
            // блик стекла
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), wy, wy + wh * 0.5f), Offset(wx, wy), Size(ww, wh * 0.5f))
        }
        drawPath(winPath, Color.White.copy(alpha = 0.22f), style = Stroke(w * 0.003f))

        reel(Offset(lcx, cy), hub, angleL, c.accent)
        reel(Offset(rcx, cy), hub, angleR, c.accent)

        // ---- нижняя трапеция с отверстиями ----
        val tp = Path().apply {
            moveTo(w * 0.17f, h)
            lineTo(w * 0.23f, h * 0.80f)
            lineTo(w * 0.77f, h * 0.80f)
            lineTo(w * 0.83f, h)
            close()
        }
        drawPath(tp, Color.White.copy(alpha = 0.06f))
        drawPath(tp, Color.White.copy(alpha = 0.10f), style = Stroke(w * 0.003f))
        listOf(0.30f, 0.70f).forEach { fx -> drawCircle(Color.Black.copy(alpha = 0.8f), w * 0.02f, Offset(w * fx, h * 0.9f)) }
        listOf(0.42f, 0.56f).forEach { fx -> drawRect(Color.Black.copy(alpha = 0.75f), Offset(w * fx, h * 0.86f), Size(w * 0.025f, h * 0.06f)) }
        // видимая плёнка у головки
        drawLine(Color(0xFF4A2E1A), Offset(w * 0.25f, h * 0.985f), Offset(w * 0.75f, h * 0.985f), h * 0.012f)

        // мелкий текст на корпусе
        val brand = measurer.measure("AURA  ·  C-60  ·  TYPE II  HIGH BIAS", TextStyle(color = Color.White.copy(alpha = 0.45f), fontSize = (h * 0.032f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
        drawText(brand, topLeft = Offset((w - brand.size.width) / 2, ly + lh + h * 0.02f))
    }
}

private fun DrawScope.screw(o: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9A9AA6), Color(0xFF4A4A55)), o, r), r, o)
    drawLine(Color(0xFF2A2A33), Offset(o.x - r * 0.6f, o.y), Offset(o.x + r * 0.6f, o.y), r * 0.3f)
    drawLine(Color(0xFF2A2A33), Offset(o.x, o.y - r * 0.6f), Offset(o.x, o.y + r * 0.6f), r * 0.3f)
}

private fun DrawScope.reel(center: Offset, r: Float, angle: Float, accent: Color) {
    drawCircle(Color(0xFFF2F2F5), r, center)
    drawCircle(Color.Black.copy(alpha = 0.2f), r, center, style = Stroke(r * 0.06f))
    withTransform({ rotate(angle, center) }) {
        // спицы
        for (i in 0 until 3) {
            val a = (i * 120.0) * PI / 180.0
            val dx = (cos(a) * r * 0.82f).toFloat()
            val dy = (sin(a) * r * 0.82f).toFloat()
            drawLine(Color(0xFFBDBDC6), center, Offset(center.x + dx, center.y + dy), r * 0.16f)
        }
        drawCircle(Color(0xFF15151B), r * 0.45f, center)
        // зубцы
        for (i in 0 until 6) {
            val a = (i * 60.0) * PI / 180.0
            val p1 = Offset(center.x + (cos(a) * r * 0.45f).toFloat(), center.y + (sin(a) * r * 0.45f).toFloat())
            val p2 = Offset(center.x + (cos(a) * r * 0.26f).toFloat(), center.y + (sin(a) * r * 0.26f).toFloat())
            drawLine(Color(0xFFF2F2F5), p1, p2, r * 0.12f)
        }
    }
    drawCircle(accent, r * 0.12f, center)
}
