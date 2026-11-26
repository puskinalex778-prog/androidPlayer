package com.aura.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.EqPresets
import com.aura.player.playback.VoiceEffect
import kotlin.math.roundToInt

@Composable
fun SoundScreen(bottomPadding: Int) {
    val c = LocalAura.current
    val s by AudioEngine.sound.collectAsState()
    val now by AudioEngine.now.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomPadding.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Звук", style = MaterialTheme.typography.displaySmall, color = c.text)
                    Text("Эффекты голоса и эквалайзер — для песен и радио", style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                }
                TextButton(onClick = { AudioEngine.resetSound() }) { Text("Сброс", color = c.accent) }
            }
            Spacer(Modifier.height(10.dp))
            SpectrumBars(now.isPlaying, Modifier.fillMaxWidth().height(70.dp), bands = 32, mirrored = false)
        }

        // ---- эффекты голоса ----
        item { SectionTitle("🎙️ Эффекты голоса") }
        item {
            val effects = VoiceEffect.entries
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                effects.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { v -> VoiceTile(v, s.voice == v, Modifier.weight(1f)) { AudioEngine.setVoice(v) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Glass(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column {
                    LabeledSlider("Тон", (if (s.pitchSemis > 0) "+" else "") + "${s.pitchSemis.roundToInt()} полутонов", s.pitchSemis, -12f..12f, 23) {
                        AudioEngine.setPitch(it.roundToInt().toFloat())
                    }
                    LabeledSlider("Скорость", "×" + "%.2f".format(s.speed), s.speed, 0.5f..2f, 0) {
                        AudioEngine.setSpeed((it * 20).roundToInt() / 20f)
                    }
                    if (now.isRadio) Text("Скорость для радио не меняется (прямой эфир)", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
            }
        }

        // ---- эквалайзер ----
        item {
            SectionTitle("🎚️ Эквалайзер") {
                Switch(
                    s.eqOn, { AudioEngine.setEqEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPresets.all.forEach { p -> Pill(p.name, s.preset == p.name, onClick = { AudioEngine.applyPreset(p) }) }
                if (s.preset == "Свой") Pill("Свой", true, onClick = {})
            }
        }
        item {
            Glass(Modifier.fillMaxWidth().padding(top = 12.dp), padding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().height(220.dp)) {
                        EqPresets.labels.forEachIndexed { i, label ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                val g = s.gains.getOrElse(i) { 0f }
                                Text((if (g > 0) "+" else "") + g.roundToInt(), fontSize = 10.sp, color = c.textDim)
                                VerticalSlider(g, -12f..12f, { AudioEngine.setBand(i, (it * 2).roundToInt() / 2f) },
                                    Modifier.weight(1f).fillMaxWidth(), enabled = s.eqOn)
                                Text(label, fontSize = 10.sp, color = c.textDim, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text("Двойное касание полосы — сброс в 0 дБ", style = MaterialTheme.typography.bodySmall, color = c.textDim,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
                }
            }
        }
        item {
            Glass(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column {
                    LabeledSlider("Усиление баса", "+${s.bass.roundToInt()} дБ", s.bass, 0f..12f, 11) { AudioEngine.setBass(it.roundToInt().toFloat()) }
                    LabeledSlider("Объёмный звук", "${(s.width * 100).roundToInt()}%", s.width, 0f..2.5f, 0) { AudioEngine.setWidth((it * 20).roundToInt() / 20f) }
                    LabeledSlider("Предусиление", (if (s.preamp > 0) "+" else "") + "${s.preamp.roundToInt()} дБ", s.preamp, -6f..6f, 11) { AudioEngine.setPreamp(it.roundToInt().toFloat()) }
                }
            }
        }
    }
}

@Composable
private fun VoiceTile(v: VoiceEffect, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.aspectRatio(0.9f).clip(shape)
            .background(if (selected) Brush.linearGradient(listOf(c.accent, c.accent2)) else Brush.linearGradient(listOf(c.glass, c.glass)))
            .border(1.dp, if (selected) c.accent else c.glassBorder, shape)
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(v.emoji, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(v.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else c.text)
    }
}

@Composable
fun LabeledSlider(title: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    val c = LocalAura.current
    Column {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = c.accent)
        }
        Slider(
            v.coerceIn(range), onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.text.copy(alpha = 0.12f),
                activeTickColor = c.accent.copy(alpha = 0f), inactiveTickColor = c.text.copy(alpha = 0f)),
        )
    }
}

@Suppress("unused")
@Composable
private fun Gap() = Box(Modifier.width(1.dp))
