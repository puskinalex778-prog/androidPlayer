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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.AudioEngineMode
import com.aura.player.data.AudioQualityMode
import com.aura.player.data.DecoderPriority
import com.aura.player.data.OutputMode
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.EqPresets
import com.aura.player.playback.VoiceEffect
import kotlin.math.abs
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
    val out by AudioEngine.output.collectAsState()
    val now by AudioEngine.now.collectAsState()
    val uiSettings by vm.settings.collectAsState()

    Column(Modifier.fillMaxSize()) {
        FlatTopBar(
            title = "Звук",
            subtitle = when (section) {
                0 -> "Эффекты голоса · ${s.voice.title}"
                1 -> "10-полосный EQ · ${s.preset}"
                else -> "${s.quality.badge} · ${out.activeLabel}"
            },
            onMenu = onMenu,
            actions = {
                IconButton(onClick = { AudioEngine.resetSound() }) {
                    Icon(Icons.Rounded.RestartAlt, "Сбросить звук", tint = c.text)
                }
            },
        )

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            FlatSegmentedControl(
                options = listOf("Голос", "EQ", "Вывод"),
                selectedIndex = section.coerceIn(0, 2),
                onSelect = onSectionChange,
                icons = listOf(Icons.Rounded.RecordVoiceOver, Icons.Rounded.Equalizer, Icons.Rounded.SpeakerGroup),
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
                        AudioVisualizer(
                            type = uiSettings.visualizer,
                            playing = now.isPlaying,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            bands = 32,
                            large = false,
                            ledDeckSparks = uiSettings.ledDeckSparks,
                        )
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

            when (section) {
                0 -> {
                    // ===================== ЭФФЕКТЫ ГОЛОСА =====================
                    item {
                        if (s.quality == AudioQualityMode.DIRECT_PURE) {
                            FlatCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), color = c.accentSoft, padding = PaddingValues(12.dp)) {
                                Text(
                                    "Включён режим Direct — эффекты и EQ временно в обходе. Переключите качество на 32-bit DSP или 16-bit PCM во вкладке «Вывод».",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.text,
                                )
                            }
                        }
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
                }
                1 -> {
                    // ===================== ЭКВАЛАЙЗЕР + AIMP DSP =====================
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
                        SectionHeader("Сцена, баланс и динамика (AIMP Pro)")
                        FlatCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LabeledSlider("Усиление баса", "+${s.bass.roundToInt()} дБ", s.bass, 0f..12f, 11) {
                                    AudioEngine.setBass(it.roundToInt().toFloat())
                                }
                                LabeledSlider("Объёмный звук", "${(s.width * 100).roundToInt()}%", s.width, 0f..2.5f, 0) {
                                    AudioEngine.setWidth((it * 20).roundToInt() / 20f)
                                }
                                val balText = when {
                                    abs(s.balance) < 0.04f -> "Центр"
                                    s.balance < 0f -> "Л ${(-s.balance * 100).roundToInt()}%"
                                    else -> "П ${(s.balance * 100).roundToInt()}%"
                                }
                                LabeledSlider("Баланс Л / П", balText, s.balance, -1f..1f, 0) {
                                    val snapped = if (abs(it) < 0.06f) 0f else (it * 20).roundToInt() / 20f
                                    AudioEngine.setBalance(snapped)
                                }
                                LabeledSlider("Предусиление", (if (s.preamp > 0) "+" else "") + "${s.preamp.roundToInt()} дБ", s.preamp, -6f..6f, 11) {
                                    AudioEngine.setPreamp(it.roundToInt().toFloat())
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(10.dp))
                        FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                            Column {
                                SoundToggle("Автонормализация громкости", "Выравнивает тихие и громкие треки (Auto-Level)", s.normalize) {
                                    AudioEngine.setNormalize(it)
                                }
                                HorizontalDivider(color = c.border)
                                SoundToggle("Режим Моно", "Сводит левый и правый каналы (удобно для 1 наушника)", s.mono) {
                                    AudioEngine.setMono(it)
                                }
                                HorizontalDivider(color = c.border)
                                SoundToggle("Плавное затухание", "Мягкий старт и пауза без щелчков", s.smoothFade) {
                                    AudioEngine.setSmoothFade(it)
                                }
                            }
                        }
                    }
                }
                else -> {
                    // ===================== ВЫВОД И КАЧЕСТВО ЗВУКА =====================
                    item {
                        OutputAndQualityControls()
                    }
                }
            }
        }
    }
}

@Composable
fun OutputAndQualityControls() {
    val c = LocalAura.current
    val s by AudioEngine.sound.collectAsState()
    val out by AudioEngine.output.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Живая строка телеметрии (как в AIMP)
        FlatCard(Modifier.fillMaxWidth(), color = c.accentSoft, padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HighQuality, null, tint = c.accent, modifier = Modifier.size(26.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(AudioEngine.techSummary(), style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, color = c.text)
                    Text("Активный вывод: ${out.activeLabel}", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
            }
        }

        SectionHeader("Устройство вывода звука")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputOptionCard(
                icon = Icons.Rounded.AutoMode,
                title = "Авто (системный)",
                sub = if (out.headphonesName != null) "Сейчас подключено: ${out.headphonesName}" else "Играет через динамик телефона",
                selected = out.mode == OutputMode.AUTO,
            ) { AudioEngine.setOutputMode(OutputMode.AUTO) }

            OutputOptionCard(
                icon = Icons.Rounded.Speaker,
                title = "Динамик телефона",
                sub = "Вывод звука на основной громкоговоритель (даже если подключены наушники)",
                selected = out.mode == OutputMode.SPEAKER,
            ) { AudioEngine.setOutputMode(OutputMode.SPEAKER) }

            OutputOptionCard(
                icon = Icons.Rounded.Headphones,
                title = "Наушники / Bluetooth",
                sub = out.headphonesName?.let { "Подключено: $it" } ?: "Подключите проводные, USB или Bluetooth-наушники",
                selected = out.mode == OutputMode.HEADPHONES,
            ) { AudioEngine.setOutputMode(OutputMode.HEADPHONES) }
        }

        // Режим принудительного переключения на динамик «как при звонке»
        FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { AudioEngine.setForceSpeakerCallMode(!out.forceSpeakerCallMode) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PhoneInTalk, null, tint = c.accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Обход на динамик (как при звонке)", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(
                        "Использует режим громкой связи Android (MODE_IN_COMMUNICATION). Включите, если при подключённых наушниках обычный режим «Динамик» не срабатывает на вашей прошивке.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textDim,
                    )
                }
                Switch(
                    checked = out.forceSpeakerCallMode,
                    onCheckedChange = { AudioEngine.setForceSpeakerCallMode(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                )
            }
        }

        SectionHeader("Качество вывода звука")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioQualityMode.entries.forEach { q ->
                val sel = s.quality == q
                FlatCard(
                    Modifier.fillMaxWidth(),
                    color = if (sel) c.accentSoft else c.surface,
                    onClick = { AudioEngine.setAudioQuality(q) },
                    padding = PaddingValues(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(q.title, style = MaterialTheme.typography.titleSmall, color = if (sel) c.accent else c.text)
                                Text(
                                    q.badge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (sel) c.onAccent else c.textDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (sel) c.accent else c.surface2)
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(q.desc, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                        }
                        if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
                    }
                }
            }
        }

        SectionHeader("Аудио-движок и вывод")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioEngineMode.entries.forEach { eng ->
                val sel = s.engineMode == eng
                FlatCard(
                    Modifier.fillMaxWidth(),
                    color = if (sel) c.accentSoft else c.surface,
                    onClick = { AudioEngine.setAudioEngineMode(eng) },
                    padding = PaddingValues(13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(eng.title, style = MaterialTheme.typography.titleSmall, color = if (sel) c.accent else c.text)
                                Text(
                                    eng.badge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (sel) c.onAccent else c.textDim,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (sel) c.accent else c.surface2)
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(eng.desc, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                        }
                        if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
                    }
                }
            }
        }

        SectionHeader("Декодеры и кодеки")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DecoderPriority.entries.forEach { dec ->
                val sel = s.decoderPriority == dec
                FlatCard(
                    Modifier.fillMaxWidth(),
                    color = if (sel) c.accentSoft else c.surface,
                    onClick = { AudioEngine.setDecoderPriority(dec) },
                    padding = PaddingValues(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dec.title, style = MaterialTheme.typography.titleSmall, color = if (sel) c.accent else c.text)
                            Text(dec.desc, style = MaterialTheme.typography.bodySmall, color = c.textDim)
                        }
                        if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
                    }
                }
            }
            FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Поддерживаемые кодеки и потоки", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(
                        "MP3 · FLAC (Hi-Res) · WAV / PCM · OGG Vorbis · Opus · AAC · ALAC · AC-3 / E-AC-3 · AIFF · MKA · HLS · MPEG-DASH · RTSP",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = c.textDim,
                    )
                }
            }
        }

        SectionHeader("Защита звука и синхронизация визуала")
        FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
            Column {
                SoundToggle(
                    title = "Мягкий лимитер (Anti-Clipping)",
                    sub = "Защищает от хрипов при сильном басе и EQ",
                    checked = s.limiterOn,
                ) { AudioEngine.setLimiterEnabled(it) }
                HorizontalDivider(color = c.border)
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    val autoTotal = AudioEngine.effectiveVizDelayMs()
                    val offsetSign = if (s.vizSyncOffsetMs > 0) "+" else ""
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Синхронизация визуализации", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text(
                                "Авто-буфер + сдвиг: $autoTotal мс ($offsetSign${s.vizSyncOffsetMs} мс)",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textDim,
                            )
                        }
                        if (s.vizSyncOffsetMs != 0) {
                            TextButton(onClick = { AudioEngine.setVizSyncOffsetMs(0) }) { Text("Авто", color = c.accent) }
                        }
                    }
                    Slider(
                        value = s.vizSyncOffsetMs.toFloat(),
                        onValueChange = { AudioEngine.setVizSyncOffsetMs((it / 10f).roundToInt() * 10) },
                        valueRange = -150f..450f,
                        colors = SliderDefaults.colors(
                            thumbColor = c.accent,
                            activeTrackColor = c.accent,
                            inactiveTrackColor = c.surface2,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputOptionCard(
    icon: ImageVector,
    title: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAura.current
    FlatCard(
        Modifier.fillMaxWidth(),
        color = if (selected) c.accentSoft else c.surface,
        onClick = onClick,
        padding = PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(c.controlRadius)).background(if (selected) c.accent else c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (selected) c.onAccent else c.accent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) c.accent else c.text)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textDim)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSheet(onDismiss: () -> Unit) {
    val c = LocalAura.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 26.dp),
        ) {
            Text("Вывод и качество звука", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(bottom = 12.dp))
            OutputAndQualityControls()
        }
    }
}

@Composable
private fun SoundToggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAura.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textDim)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
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
