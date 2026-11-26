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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.EqPresets
import com.aura.player.playback.VoiceEffect
import kotlin.math.roundToInt

@Composable
fun SoundScreen(
    vm: MainViewModel,
    section: Int,
    onSectionChange: (Int) -> Unit,
    onMenu: () -> Unit,
    bottomPadding: Int,
) {
    val c = LocalAura.current
    val s by AudioEngine.sound.collectAsState()
    val now by AudioEngine.now.collectAsState()
    val uiSettings by vm.settings.collectAsState()

    Column(Modifier.fillMaxSize()) {
        FlatTopBar(
            title = "Звук",
            subtitle = if (section == 0) "Эффекты голоса · ${s.voice.title}" else "10-полосный EQ · ${s.preset}",
            onMenu = onMenu,
            actions = {
                IconButton(onClick = { AudioEngine.resetSound() }) {
                    Icon(Icons.Rounded.RestartAlt, "Сбросить звук", tint = c.text)
                }
            },
        )

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            FlatSegmentedControl(
                options = listOf("Эффекты голоса", "Эквалайзер"),
                selectedIndex = section,
                onSelect = onSectionChange,
                icons = listOf(Icons.Rounded.RecordVoiceOver, Icons.Rounded.Equalizer),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding.dp),
        ) {
            item {
                FlatCard(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    onClick = { vm.cycleVisualizer() },
                    padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        AudioVisualizer(uiSettings.visualizer, now.isPlaying, Modifier.fillMaxWidth().height(48.dp), bands = 32, large = false)
                        Text(
                            uiSettings.visualizer.title,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textDim,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }

            if (section == 0) {
                // ===================== ЭФФЕКТЫ ГОЛОСА =====================
                item {
                    SectionHeader("Пресеты голоса · работают для песен и радио")
                    val effects = VoiceEffect.entries
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        effects.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { v ->
                                    VoiceTile(v, s.voice == v, Modifier.weight(1f)) { AudioEngine.setVoice(v) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                item {
                    SectionHeader("Тон и скорость")
                    FlatCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LabeledSlider(
                                title = "Высота тона",
                                value = (if (s.pitchSemis > 0) "+" else "") + "${s.pitchSemis.roundToInt()} полут.",
                                v = s.pitchSemis,
                                range = -12f..12f,
                                steps = 23,
                            ) { AudioEngine.setPitch(it.roundToInt().toFloat()) }

                            LabeledSlider(
                                title = "Скорость",
                                value = "×" + "%.2f".format(s.speed),
                                v = s.speed,
                                range = 0.5f..2f,
                                steps = 0,
                            ) { AudioEngine.setSpeed((it * 20).roundToInt() / 20f) }

                            if (now.isRadio) {
                                Text("Для прямого эфира радио скорость фиксирована ×1.00", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                            }
                        }
                    }
                }
            } else {
                // ===================== ЭКВАЛАЙЗЕР =====================
                item {
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("10-полосный эквалайзер", style = MaterialTheme.typography.titleMedium, color = c.text)
                                Text(
                                    if (s.eqOn) "Активен · пресет «${s.preset}»" else "Выключен",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textDim,
                                )
                            }
                            Switch(
                                checked = s.eqOn,
                                onCheckedChange = { AudioEngine.setEqEnabled(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                            )
                        }
                    }
                    SectionHeader("Пресеты")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EqPresets.all.forEach { p ->
                            FlatChip(p.name, s.preset == p.name, onClick = { AudioEngine.applyPreset(p) })
                        }
                        if (s.preset == "Свой") FlatChip("Свой", true, onClick = {})
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 6.dp, vertical = 14.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth().height(210.dp)) {
                                EqPresets.labels.forEachIndexed { i, label ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        val g = s.gains.getOrElse(i) { 0f }
                                        Text(
                                            (if (g > 0) "+" else "") + g.roundToInt(),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (g != 0f && s.eqOn) c.accent else c.textDim,
                                        )
                                        VerticalSlider(
                                            value = g,
                                            range = -12f..12f,
                                            onChange = { AudioEngine.setBand(i, (it * 2).roundToInt() / 2f) },
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            enabled = s.eqOn,
                                        )
                                        Text(label, fontSize = 10.sp, color = c.textDim, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                "Двойное касание по полосе сбрасывает её в 0 дБ",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textDim,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                item {
                    SectionHeader("Дополнительно")
                    FlatCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LabeledSlider("Усиление баса", "+${s.bass.roundToInt()} дБ", s.bass, 0f..12f, 11) {
                                AudioEngine.setBass(it.roundToInt().toFloat())
                            }
                            LabeledSlider("Объёмный звук", "${(s.width * 100).roundToInt()}%", s.width, 0f..2.5f, 0) {
                                AudioEngine.setWidth((it * 20).roundToInt() / 20f)
                            }
                            LabeledSlider("Предусиление", (if (s.preamp > 0) "+" else "") + "${s.preamp.roundToInt()} дБ", s.preamp, -6f..6f, 11) {
                                AudioEngine.setPreamp(it.roundToInt().toFloat())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceTile(v: VoiceEffect, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.cardRadius)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.accent else c.surface)
            .border(c.borderWidth, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            voiceIcon(v),
            contentDescription = v.title,
            tint = if (selected) c.onAccent else c.accent,
            modifier = Modifier.size(22.dp),
        )
        Text(
            v.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) c.onAccent else c.text,
        )
    }
}

@Composable
fun LabeledSlider(
    title: String,
    value: String,
    v: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    val c = LocalAura.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, color = c.accent)
        }
        Slider(
            value = v.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.surface2,
                activeTickColor = c.accent.copy(alpha = 0f),
                inactiveTickColor = c.text.copy(alpha = 0f),
            ),
        )
    }
}
