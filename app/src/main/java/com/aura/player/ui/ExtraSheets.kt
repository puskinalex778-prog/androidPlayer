package com.aura.player.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.LyricsStore
import com.aura.player.data.OutputMode
import com.aura.player.data.PlayerDockAction
import com.aura.player.data.Track
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.VoiceEffect
import kotlin.math.roundToInt

fun dockActionIcon(action: PlayerDockAction, outputMode: OutputMode = OutputMode.AUTO): ImageVector = when (action) {
    PlayerDockAction.VOICE -> Icons.Rounded.RecordVoiceOver
    PlayerDockAction.EQ -> Icons.Rounded.Equalizer
    PlayerDockAction.TIMER -> Icons.Rounded.Bedtime
    PlayerDockAction.OUTPUT -> if (outputMode == OutputMode.SPEAKER) Icons.Rounded.Speaker else Icons.Rounded.Headphones
    PlayerDockAction.RADIO -> Icons.Rounded.Radio
    PlayerDockAction.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
    PlayerDockAction.QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
    PlayerDockAction.LYRICS -> Icons.Rounded.Lyrics
    PlayerDockAction.SPEED -> Icons.Rounded.Speed
    PlayerDockAction.VISUALIZER -> Icons.Rounded.AutoAwesome
    PlayerDockAction.BOOKMARK -> Icons.Rounded.BookmarkAdd
    PlayerDockAction.CAR_MODE -> Icons.Rounded.DirectionsCar
    PlayerDockAction.INFO -> Icons.Rounded.Info
}

// ===================== 1. Настройка нижней панели плеера =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockCustomizerSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = LocalAura.current
    val settings by vm.settings.collectAsState()
    val active = settings.dockActions
    val inactive = PlayerDockAction.entries.filter { it !in active }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Нижняя панель плеера", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text("Выберите от 2 до 5 кнопок и их порядок", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
                TextButton(onClick = { vm.setDockActions(PlayerDockAction.defaultDock) }) {
                    Text("Сброс", color = c.accent)
                }
            }

            // Живое превью нижней панели
            FlatCard(Modifier.fillMaxWidth(), color = c.surface2, padding = PaddingValues(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    active.forEach { act ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(c.controlRadius))
                                .background(c.surfaceSolid)
                                .border(c.borderWidth, c.border, RoundedCornerShape(c.controlRadius))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(dockActionIcon(act), null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Text(act.title, fontSize = 11.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            SectionHeader("Активные кнопки (${active.size}/5)")
            active.forEachIndexed { idx, act ->
                FlatCard(Modifier.fillMaxWidth(), color = c.accentSoft, padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(dockActionIcon(act), null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Text(act.title, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val list = active.toMutableList()
                                    list[idx] = list[idx - 1].also { list[idx - 1] = list[idx] }
                                    vm.setDockActions(list)
                                }
                            },
                            enabled = idx > 0,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, "Вверх", tint = if (idx > 0) c.text else c.textDim.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (idx < active.lastIndex) {
                                    val list = active.toMutableList()
                                    list[idx] = list[idx + 1].also { list[idx + 1] = list[idx] }
                                    vm.setDockActions(list)
                                }
                            },
                            enabled = idx < active.lastIndex,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, "Вниз", tint = if (idx < active.lastIndex) c.text else c.textDim.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { if (active.size > 2) vm.setDockActions(active - act) },
                            enabled = active.size > 2,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Rounded.Close, "Убрать", tint = if (active.size > 2) c.danger else c.textDim.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (inactive.isNotEmpty()) {
                SectionHeader("Доступные кнопки — нажмите, чтобы добавить")
                inactive.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { act ->
                            FlatCard(
                                Modifier.weight(1f),
                                onClick = {
                                    if (active.size < 5) vm.setDockActions(active + act)
                                    else vm.toast.value = "Максимум 5 кнопок на нижней панели"
                                },
                                padding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
                            ) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(dockActionIcon(act), null, tint = c.accent, modifier = Modifier.size(19.dp))
                                    Text(act.title, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ===================== 2. Текст песни / LRC-караоке =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(vm: MainViewModel, track: Track?, posMs: Long, onDismiss: () -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val key = track?.uri ?: ""
    var rawText by remember(key) { mutableStateOf(if (key.isNotBlank()) vm.loadLyrics(key) else "") }
    var editing by remember(key) { mutableStateOf(false) }
    val lines = remember(rawText) { LyricsStore.parse(rawText) }
    val hasSync = remember(lines) { lines.any { it.timeMs != null } }

    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && key.isNotBlank()) {
            val loaded = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!loaded.isNullOrBlank()) {
                rawText = loaded
                vm.saveLyrics(key, loaded)
                editing = false
            }
        }
    }

    val activeIdx = remember(lines, posMs, hasSync) {
        if (!hasSync) -1 else lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= posMs }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0 && !editing) {
            runCatching { listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0)) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Текст песни", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text(track?.title ?: "Только для локальных треков", style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (key.isNotBlank()) {
                    IconButton(onClick = { runCatching { lrcPicker.launch(arrayOf("text/*", "*/*")) } }) {
                        Icon(Icons.Rounded.UploadFile, "Загрузить .lrc", tint = c.accent)
                    }
                    IconButton(onClick = { editing = !editing }) {
                        Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, "Редактировать", tint = c.accent)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (key.isBlank()) {
                FlatEmptyState(Icons.Rounded.Lyrics, "Недоступно для радио", "Тексты и синхронизация .lrc работают для добавленных треков.", Modifier.fillMaxWidth())
            } else if (editing) {
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Текст или LRC ([00:15.20] Строка)") },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                Spacer(Modifier.height(10.dp))
                FlatPrimaryButton("Сохранить текст", Icons.Rounded.Check, {
                    vm.saveLyrics(key, rawText)
                    editing = false
                }, Modifier.fillMaxWidth())
            } else if (lines.isEmpty()) {
                FlatEmptyState(
                    icon = Icons.Rounded.Lyrics,
                    title = "Текст не добавлен",
                    text = "Нажмите кнопку выше, чтобы вставить текст или загрузить файл .lrc для караоке-синхронизации.",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlatPrimaryButton("Вставить текст", Icons.Rounded.Edit, { editing = true })
                        FlatTonalButton(".LRC файл", Icons.Rounded.UploadFile, {
                            runCatching { lrcPicker.launch(arrayOf("text/*", "*/*")) }
                        })
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(lines) { i, line ->
                        val active = i == activeIdx
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(c.controlRadius))
                                .background(if (active) c.accentSoft else Color.Transparent)
                                .clickable(enabled = line.timeMs != null) { line.timeMs?.let { AudioEngine.seekTo(it) } }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (line.timeMs != null) {
                                Text(
                                    fmtTime(line.timeMs),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (active) c.accent else c.textDim,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                            }
                            Text(
                                line.text,
                                style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) c.accent else c.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===================== 3. Закладки времени внутри трека =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(vm: MainViewModel, track: Track?, posMs: Long, onDismiss: () -> Unit) {
    val c = LocalAura.current
    val allBookmarks by vm.bookmarks.collectAsState()
    val trackUri = track?.uri ?: ""
    val list = remember(allBookmarks, trackUri) { allBookmarks.filter { it.trackUri == trackUri }.sortedBy { it.positionMs } }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Закладки времени", style = MaterialTheme.typography.titleLarge, color = c.text)
            if (trackUri.isBlank()) {
                FlatEmptyState(Icons.Rounded.Bookmark, "Только для треков", "Закладки запоминают точные моменты в песнях, миксах или аудиокнигах.", Modifier.fillMaxWidth())
            } else {
                Text(track?.title ?: "", style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Подпись (например, Припев)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FlatPrimaryButton("+ ${fmtTime(posMs)}", Icons.Rounded.BookmarkAdd, {
                        vm.addBookmark(trackUri, posMs, note)
                        note = ""
                    })
                }
                if (list.isEmpty()) {
                    FlatEmptyState(Icons.Rounded.BookmarkAdd, "Закладок пока нет", "Нажмите кнопку выше, чтобы запомнить текущий момент трека.", Modifier.fillMaxWidth())
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(list, key = { it.id }) { bm ->
                            FlatCard(
                                Modifier.fillMaxWidth(),
                                color = c.surface2,
                                onClick = { AudioEngine.seekTo(bm.positionMs); onDismiss() },
                                padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        fmtTime(bm.positionMs),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = c.accent,
                                    )
                                    Text(
                                        bm.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.text,
                                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { vm.deleteBookmark(bm.id) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = c.danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== 4. Быстрая скорость + Slowed+Reverb / Nightcore =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSpeedPitchSheet(onDismiss: () -> Unit) {
    val c = LocalAura.current
    val s by AudioEngine.sound.collectAsState()
    val now by AudioEngine.now.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Скорость и тон", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    AudioEngine.setVoice(VoiceEffect.NORMAL)
                    AudioEngine.setSpeed(1f)
                    AudioEngine.setPitch(0f)
                }) { Text("Сброс", color = c.accent) }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatChip("Обычно", s.voice == VoiceEffect.NORMAL && s.speed == 1f && s.pitchSemis == 0f, onClick = {
                    AudioEngine.setVoice(VoiceEffect.NORMAL); AudioEngine.setSpeed(1f); AudioEngine.setPitch(0f)
                })
                FlatChip("Slowed+Reverb", s.voice == VoiceEffect.SLOWED_REVERB, onClick = {
                    AudioEngine.setSpeed(1f); AudioEngine.setPitch(0f); AudioEngine.setVoice(VoiceEffect.SLOWED_REVERB)
                })
                FlatChip("Nightcore", s.voice == VoiceEffect.NIGHTCORE, onClick = {
                    AudioEngine.setSpeed(1f); AudioEngine.setPitch(0f); AudioEngine.setVoice(VoiceEffect.NIGHTCORE)
                })
                FlatChip("8D Звук", s.voice == VoiceEffect.AUDIO_8D, onClick = {
                    AudioEngine.setVoice(VoiceEffect.AUDIO_8D)
                })
                FlatChip("×1.25", s.voice == VoiceEffect.NORMAL && s.speed == 1.25f, onClick = {
                    AudioEngine.setVoice(VoiceEffect.NORMAL); AudioEngine.setSpeed(1.25f)
                })
                FlatChip("×1.50", s.voice == VoiceEffect.NORMAL && s.speed == 1.5f, onClick = {
                    AudioEngine.setVoice(VoiceEffect.NORMAL); AudioEngine.setSpeed(1.5f)
                })
            }

            FlatCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledSlider("Скорость", "×" + "%.2f".format(s.speed), s.speed, 0.5f..2f, 0) {
                        AudioEngine.setSpeed((it * 20).roundToInt() / 20f)
                    }
                    LabeledSlider("Тон", (if (s.pitchSemis > 0) "+" else "") + "${s.pitchSemis.roundToInt()} полут.", s.pitchSemis, -12f..12f, 23) {
                        AudioEngine.setPitch(it.roundToInt().toFloat())
                    }
                    if (now.isRadio) {
                        Text("Скорость для радио фиксирована ×1.00 (прямой эфир)", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                    }
                }
            }
        }
    }
}

// ===================== 5. Выбор визуализации прямо из плеера =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerPickerSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = LocalAura.current
    val settings by vm.settings.collectAsState()
    val now by AudioEngine.now.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Визуализация частот", style = MaterialTheme.typography.titleLarge, color = c.text)
            FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                AudioVisualizer(settings.visualizer, now.isPlaying, Modifier.fillMaxWidth().height(90.dp), bands = 36, large = true)
            }
            VisualizerType.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { vt ->
                        val sel = settings.visualizer == vt
                        FlatCard(
                            Modifier.weight(1f),
                            color = if (sel) c.accent else c.surface2,
                            onClick = { vm.setVisualizer(vt) },
                            padding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
                        ) {
                            Text(
                                vt.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (sel) c.onAccent else c.text,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ===================== 6. Инфо о файле + Переименование + Поделиться =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoSheet(
    vm: MainViewModel,
    track: Track,
    onDismiss: () -> Unit,
) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val stats by AudioEngine.stats.collectAsState()
    val stat = remember(stats, track.id) { stats.firstOrNull { it.id == track.id } }
    val sizeBytes = remember(track.uri) { querySizeBytes(ctx, Uri.parse(track.uri)) }
    var renameOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtThumb(track.uri, track.title, size = 56.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Формат", track.format.ifBlank { "Аудио" })
                    InfoRow("Длительность", if (track.durationMs > 0) fmtTime(track.durationMs) else "—")
                    InfoRow("Размер файла", if (sizeBytes > 0) "%.2f МБ".format(sizeBytes / (1024.0 * 1024.0)) else "—")
                    InfoRow("Память", if (track.onSdCard) "SD-карта" else "Телефон")
                    if (track.folder.isNotBlank()) InfoRow("Папка", track.folder)
                    if (track.album.isNotBlank()) InfoRow("Альбом", track.album)
                    InfoRow("Прослушиваний", "${stat?.plays ?: 0}")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatTonalButton("Переименовать", Icons.Rounded.Edit, { renameOpen = true }, Modifier.weight(1f))
                FlatPrimaryButton("Поделиться", Icons.Rounded.Share, {
                    shareAudio(ctx, track)
                }, Modifier.weight(1f))
            }
        }
    }

    if (renameOpen) {
        RenameTrackDialog(
            track = track,
            onDismiss = { renameOpen = false },
            onSave = { t, a ->
                vm.editTrackMeta(track.uri, t, a)
                renameOpen = false
                onDismiss()
            },
        )
    }
}

@Composable
fun RenameTrackDialog(track: Track, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val c = LocalAura.current
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceSolid,
        title = { Text("Переименовать в списке", color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Исполнитель") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun InfoRow(k: String, v: String) {
    val c = LocalAura.current
    Row(Modifier.fillMaxWidth()) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = c.textDim, modifier = Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun querySizeBytes(context: Context, uri: Uri): Long = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0) else 0L
    } ?: 0L
} catch (_: Exception) {
    0L
}

private fun shareAudio(context: Context, track: Track) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(track.uri))
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться треком").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ===================== 7. Статистика прослушиваний =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = LocalAura.current
    val stats by AudioEngine.stats.collectAsState()
    val totalPlays = remember(stats) { stats.sumOf { it.plays } }
    val totalSec = remember(stats) { stats.sumOf { it.listenSec } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Статистика прослушиваний", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text("Ваш личный хит-парад в PoliSANPlayer", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
                if (stats.isNotEmpty()) {
                    IconButton(onClick = { AudioEngine.clearStats() }) {
                        Icon(Icons.Rounded.RestartAlt, "Сбросить", tint = c.danger)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatCard(Modifier.weight(1f), color = c.accentSoft, padding = PaddingValues(12.dp)) {
                    Column {
                        Text("$totalPlays", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, color = c.accent)
                        Text("Включений", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                    }
                }
                FlatCard(Modifier.weight(1f), color = c.surface2, padding = PaddingValues(12.dp)) {
                    Column {
                        val mins = totalSec / 60
                        val timeText = if (mins >= 60) "${mins / 60}ч ${mins % 60}м" else "${mins} мин"
                        Text(timeText, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, color = c.text)
                        Text("Прослушано", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (stats.isEmpty()) {
                FlatEmptyState(Icons.Rounded.BarChart, "Пока пусто", "Включите любую песню или радио — здесь появится ваш топ.", Modifier.fillMaxWidth())
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(stats.take(20)) { idx, st ->
                        FlatCard(
                            Modifier.fillMaxWidth(),
                            color = c.surface2,
                            onClick = { vm.playFromStat(st.id); onDismiss() },
                            padding = PaddingValues(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "#${idx + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (idx < 3) c.accent else c.textDim,
                                    modifier = Modifier.width(34.dp),
                                )
                                Icon(
                                    if (st.isRadio) Icons.Rounded.Radio else Icons.Rounded.MusicNote,
                                    null,
                                    tint = c.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(st.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${st.plays} раз · ${st.listenSec / 60} мин" + if (st.artist.isNotBlank()) " · ${st.artist}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== 8. Режим Авто (крупные кнопки, экран не гаснет) =====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarModeOverlay(vm: MainViewModel, onClose: () -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val now by AudioEngine.now.collectAsState()
    val settings by vm.settings.collectAsState()
    val (pos, dur) = rememberProgress()

    DisposableEffect(Unit) {
        val win = (ctx as? Activity)?.window
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.accentSoft)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Text("РЕЖИМ АВТО", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
            Spacer(Modifier.weight(1f))
            FlatChip("Выйти", false, onClick = onClose, icon = Icons.Rounded.Close)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                now.title.ifBlank { "Ничего не играет" },
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = c.text,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (now.isRadio) (now.streamTitle ?: now.artist) else now.artist,
                fontSize = 18.sp,
                color = c.textDim,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(Modifier.height(22.dp))
            FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                AudioVisualizer(settings.visualizer, now.isPlaying, Modifier.fillMaxWidth().height(110.dp), bands = 32, large = true)
            }
            if (!now.isRadio && dur > 0) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2)) {
                    Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).height(8.dp).background(c.accent))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(fmtTime(pos), fontFamily = FontFamily.Monospace, color = c.textDim)
                    Spacer(Modifier.weight(1f))
                    Text(fmtTime(dur), fontFamily = FontFamily.Monospace, color = c.textDim)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surface)
                    .border(c.borderWidth, c.border, RoundedCornerShape(24.dp))
                    .clickable(enabled = !now.isRadio) { AudioEngine.prev() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SkipPrevious, "Назад", tint = c.text.copy(alpha = if (now.isRadio) 0.25f else 1f), modifier = Modifier.size(48.dp))
            }

            Box(
                Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(c.accent)
                    .clickable { AudioEngine.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(now.isPlaying, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "carPlay") { p ->
                    Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = c.onAccent, modifier = Modifier.size(58.dp))
                }
            }

            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surface)
                    .border(c.borderWidth, c.border, RoundedCornerShape(24.dp))
                    .clickable(enabled = !now.isRadio) { AudioEngine.next() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SkipNext, "Далее", tint = c.text.copy(alpha = if (now.isRadio) 0.25f else 1f), modifier = Modifier.size(48.dp))
            }
        }
    }
}
