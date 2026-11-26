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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Плоская индустриальная ретро-кассета в стиле 2026 года.
 * На наклейке отображается обложка альбома, встроенная в аудиофайл (если она есть).
 * Катушки крутятся при проигрывании, а плёнка перематывается слева направо по мере прогресса.
 */
@Composable
fun Cassette(
    art: ImageBitmap?,
    title: String,
    subtitle: String,
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    badge: String = "C-90",
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
                    val rl = 0.45f + 0.55f * (1f - p)
                    val rr = 0.45f + 0.55f * p
                    angleL = (angleL + dt * 155f / rl) % 360f
                    angleR = (angleR + dt * 155f / rr) % 360f
                }
                last = now
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val seedCol = remember(seed) { flatSeedColor(seed) }

    Canvas(modifier.aspectRatio(1f / 0.64f)) {
        val w = size.width
        val h = size.height
        val corner = w * 0.042f

        val shellColor = if (c.dark) Color(0xFF1A1D26) else Color(0xFF20242E)
        val shellRim = if (c.dark) Color(0xFF323747) else Color(0xFF3A4052)

        // ---- плоский матовый корпус ----
        drawRoundRect(shellColor, Offset.Zero, Size(w, h), CornerRadius(corner))
        drawRoundRect(shellRim, Offset.Zero, Size(w, h), CornerRadius(corner), style = Stroke(w * 0.005f))

        // ---- винты ----
        val sr = w * 0.015f
        listOf(
            Offset(w * 0.036f, h * 0.055f),
            Offset(w * 0.964f, h * 0.055f),
            Offset(w * 0.036f, h * 0.945f),
            Offset(w * 0.964f, h * 0.945f),
            Offset(w * 0.5f, h * 0.905f),
        ).forEach { o -> screw(o, sr) }

        // ---- наклейка с обложкой альбома ----
        val lx = w * 0.068f
        val ly = h * 0.068f
        val lw = w * 0.864f
        val lh = h * 0.635f
        val lc = w * 0.022f
        val labelPath = Path().apply { addRoundRect(RoundRect(lx, ly, lx + lw, ly + lh, CornerRadius(lc))) }
        clipPath(labelPath) {
            if (art != null) {
                val iw = art.width.toFloat()
                val ih = art.height.toFloat()
                val scale = maxOf(lw / iw, lh / ih)
                val sw = lw / scale
                val sh = lh / scale
                val sx = (iw - sw) / 2
                val sy = (ih - sh) / 2
                drawImage(
                    art,
                    srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                    srcSize = IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
                    dstOffset = IntOffset(lx.roundToInt(), ly.roundToInt()),
                    dstSize = IntSize(lw.roundToInt(), lh.roundToInt()),
                )
                // плоская верхняя полоса для читаемости названия
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xCC0D0E12), Color.Transparent), ly, ly + lh * 0.42f),
                    Offset(lx, ly),
                    Size(lw, lh * 0.42f),
                )
            } else {
                // чистая плоская ретро-наклейка (в стиле Braun / Sony HF)
                drawRect(Color(0xFFF5F1E8), Offset(lx, ly), Size(lw, lh))
                // тонкие линии для записи сверху
                drawLine(Color(0x22000000), Offset(lx + lw * 0.03f, ly + lh * 0.28f), Offset(lx + lw * 0.97f, ly + lh * 0.28f), w * 0.002f)
                // плоские цветные полосы снизу
                drawRect(c.accent, Offset(lx, ly + lh * 0.66f), Size(lw, lh * 0.09f))
                drawRect(seedCol, Offset(lx, ly + lh * 0.77f), Size(lw, lh * 0.07f))
                drawRect(Color(0xFF1F232E), Offset(lx, ly + lh * 0.86f), Size(lw, lh * 0.14f))
            }
        }
        drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(lx, ly), Size(lw, lh), CornerRadius(lc), style = Stroke(w * 0.003f))

        // ---- значок «A» + название и исполнитель ----
        val textColor = if (art != null) Color.White else Color(0xFF181A22)
        val sideSize = lh * 0.175f
        drawRoundRect(c.accent, Offset(lx + lw * 0.028f, ly + lh * 0.055f), Size(sideSize, sideSize), CornerRadius(sideSize * 0.22f))
        val sideLayout = measurer.measure(
            "A",
            TextStyle(color = c.onAccent, fontSize = (sideSize * 0.6f).toSp(), fontWeight = FontWeight.Black),
        )
        drawText(
            sideLayout,
            topLeft = Offset(
                lx + lw * 0.028f + (sideSize - sideLayout.size.width) / 2,
                ly + lh * 0.055f + (sideSize - sideLayout.size.height) / 2,
            ),
        )

        val badgeLayout = measurer.measure(
            badge,
            TextStyle(
                color = if (art != null) Color.White.copy(alpha = 0.85f) else Color(0xFF181A22).copy(alpha = 0.65f),
                fontSize = (lh * 0.072f).toSp(),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )
        drawText(badgeLayout, topLeft = Offset(lx + lw - badgeLayout.size.width - lw * 0.03f, ly + lh * 0.075f))

        val tx = lx + lw * 0.052f + sideSize
        val tWidth = (lw - (tx - lx) - badgeLayout.size.width - lw * 0.06f).roundToInt().coerceAtLeast(10)
        val titleLayout = measurer.measure(
            title.ifBlank { "Aura Player" },
            TextStyle(color = textColor, fontSize = (lh * 0.095f).toSp(), fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = tWidth),
        )
        drawText(titleLayout, topLeft = Offset(tx, ly + lh * 0.045f))

        val subLayout = measurer.measure(
            subtitle,
            TextStyle(color = textColor.copy(alpha = 0.78f), fontSize = (lh * 0.068f).toSp(), fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = tWidth),
        )
        drawText(subLayout, topLeft = Offset(tx, ly + lh * 0.045f + titleLayout.size.height))

        // ---- окошко с плёнкой ----
        val ww = w * 0.56f
        val wh = h * 0.225f
        val wx = (w - ww) / 2
        val wy = ly + lh * 0.49f
        val cy = wy + wh / 2
        val lcx = w / 2 - w * 0.165f
        val rcx = w / 2 + w * 0.165f
        val hub = w * 0.046f
        val maxR = w * 0.118f
        val rL = hub + (maxR - hub) * (1f - p)
        val rR = hub + (maxR - hub) * p
        val winPath = Path().apply { addRoundRect(RoundRect(wx, wy, wx + ww, wy + wh, CornerRadius(wh * 0.5f))) }
        drawPath(winPath, Color(0xFF0D0F14))
        clipPath(winPath) {
            // плоские кольца плёнки
            drawCircle(Color(0xFF3E2719), rL, Offset(lcx, cy))
            drawCircle(Color(0xFF563723), rL * 0.85f, Offset(lcx, cy), style = Stroke(w * 0.004f))
            drawCircle(Color(0xFF3E2719), rR, Offset(rcx, cy))
            drawCircle(Color(0xFF563723), rR * 0.85f, Offset(rcx, cy), style = Stroke(w * 0.004f))
            // центральная мерная шкала
            for (i in 0..6) {
                val x = w / 2 - w * 0.048f + i * w * 0.016f
                val tall = if (i % 3 == 0) wh * 0.22f else wh * 0.13f
                drawLine(Color.White.copy(alpha = 0.28f), Offset(x, cy - tall), Offset(x, cy + tall), w * 0.0025f)
            }
        }
        drawPath(winPath, Color.White.copy(alpha = 0.18f), style = Stroke(w * 0.0035f))

        reel(Offset(lcx, cy), hub, angleL, c.accent)
        reel(Offset(rcx, cy), hub, angleR, c.accent)

        // ---- нижняя трапеция лентопротяга ----
        val tp = Path().apply {
            moveTo(w * 0.17f, h)
            lineTo(w * 0.23f, h * 0.80f)
            lineTo(w * 0.77f, h * 0.80f)
            lineTo(w * 0.83f, h)
            close()
        }
        drawPath(tp, Color(0xFF13151C))
        drawPath(tp, shellRim, style = Stroke(w * 0.0035f))
        listOf(0.30f, 0.70f).forEach { fx -> drawCircle(Color(0xFF090A0D), w * 0.02f, Offset(w * fx, h * 0.90f)) }
        listOf(0.42f, 0.56f).forEach { fx -> drawRect(Color(0xFF090A0D), Offset(w * fx, h * 0.86f), Size(w * 0.024f, h * 0.058f)) }
        drawLine(Color(0xFF5A3720), Offset(w * 0.25f, h * 0.985f), Offset(w * 0.75f, h * 0.985f), h * 0.012f)

        // ---- маркировка ----
        val brand = measurer.measure(
            "AURA  ·  HIGH BIAS  ·  TYPE II",
            TextStyle(
                color = Color.White.copy(alpha = 0.4f),
                fontSize = (h * 0.031f).toSp(),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            ),
        )
        drawText(brand, topLeft = Offset((w - brand.size.width) / 2, ly + lh + h * 0.022f))
    }
}

private fun DrawScope.screw(o: Offset, r: Float) {
    drawCircle(Color(0xFF454B5C), r, o)
    drawLine(Color(0xFF1B1E26), Offset(o.x - r * 0.55f, o.y), Offset(o.x + r * 0.55f, o.y), r * 0.28f)
    drawLine(Color(0xFF1B1E26), Offset(o.x, o.y - r * 0.55f), Offset(o.x, o.y + r * 0.55f), r * 0.28f)
}

private fun DrawScope.reel(center: Offset, r: Float, angle: Float, accent: Color) {
    drawCircle(Color(0xFFF0F1F5), r, center)
    withTransform({ rotate(angle, center) }) {
        for (i in 0 until 3) {
            val a = (i * 120.0) * PI / 180.0
            drawLine(
                Color(0xFFB5BAC7),
                center,
                Offset(center.x + (cos(a) * r * 0.82f).toFloat(), center.y + (sin(a) * r * 0.82f).toFloat()),
                r * 0.16f,
            )
        }
        drawCircle(Color(0xFF15171E), r * 0.46f, center)
        for (i in 0 until 6) {
            val a = (i * 60.0) * PI / 180.0
            val p1 = Offset(center.x + (cos(a) * r * 0.46f).toFloat(), center.y + (sin(a) * r * 0.46f).toFloat())
            val p2 = Offset(center.x + (cos(a) * r * 0.26f).toFloat(), center.y + (sin(a) * r * 0.26f).toFloat())
            drawLine(Color(0xFFF0F1F5), p1, p2, r * 0.12f)
        }
    }
    drawCircle(accent, r * 0.13f, center)
}
