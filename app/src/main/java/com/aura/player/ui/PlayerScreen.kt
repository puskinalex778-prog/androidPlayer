package com.aura.player.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.aura.player.data.Country
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.NowPlaying
import kotlinx.coroutines.delay

/** Позиция/длительность текущего трека, обновляется 4 раза в секунду */
@Composable
fun rememberProgress(): Pair<Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val p = AudioEngine.player
            pos = p.currentPosition.coerceAtLeast(0L)
            dur = p.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
    }
    return pos to dur
}

// ===================== Мини-плеер =====================

@Composable
fun MiniPlayer(now: NowPlaying, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAura.current
    val (pos, dur) = rememberProgress()
    val art by AudioEngine.art.collectAsState()
    Glass(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), onClick = onOpen, padding = PaddingValues(0.dp)) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (now.isRadio && now.station != null) StationLogo(now.station, size = 46.dp)
                else Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))) {
                    val a = art
                    if (a != null) Image(a.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else ArtThumb(null, now.title, size = 46.dp, corner = 12.dp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(now.title.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (now.isRadio) (now.streamTitle ?: now.artist) else now.artist,
                        style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (now.buffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = c.accent)
                PlayButton(now.isPlaying, size = 44, onClick = { AudioEngine.toggle() })
                if (!now.isRadio) IconButton(onClick = { AudioEngine.next() }) { Icon(Icons.Rounded.SkipNext, "Далее", tint = c.text) }
            }
            if (!now.isRadio && dur > 0) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.text.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).height(3.dp).background(Brush.horizontalGradient(listOf(c.accent, c.accent2))))
                }
            }
        }
    }
}

@Composable
fun PlayButton(playing: Boolean, size: Int, onClick: () -> Unit) {
    val c = LocalAura.current
    val corner by animateFloatAsState(if (playing) 30f else 50f, spring(), label = "corner")
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.toInt()))
            .background(Brush.linearGradient(listOf(c.accent, c.accent2)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(playing, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "pp") { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (p) "Пауза" else "Играть",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

// ===================== Полноэкранный плеер =====================

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    vm: MainViewModel,
    onClose: () -> Unit,
    onOpenSound: () -> Unit,
) {
    val c = LocalAura.current
    val now by AudioEngine.now.collectAsState()
    val sound by AudioEngine.sound.collectAsState()
    val settings by vm.settings.collectAsState()
    val favs by vm.favorites.collectAsState()
    val sleepAt by AudioEngine.sleepAt.collectAsState()
    val artBmp by AudioEngine.art.collectAsState()
    val art: ImageBitmap? = remember(artBmp) { artBmp?.asImageBitmap() }
    val (pos, dur) = rememberProgress()
    var seeking by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var beat by remember { mutableFloatStateOf(0f) }
    val cassette = settings.cassette

    Box(Modifier.fillMaxSize().background(c.bg)) {
        // ---- фон: размытая обложка или живой градиент ----
        if (art != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(art, null, Modifier.fillMaxSize().scale(1.4f).blur(70.dp), contentScale = ContentScale.Crop, alpha = if (c.dark) 0.55f else 0.45f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.35f), c.bg.copy(alpha = 0.92f)))))
        } else {
            AuraBackground(intensity = 1.5f)
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- верхняя панель ----
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", tint = c.text, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (now.isRadio) "ОНЛАЙН-РАДИО" else "СЕЙЧАС ИГРАЕТ", style = MaterialTheme.typography.labelSmall, color = c.textDim)
                    Text(
                        if (now.isRadio) (Country.of(now.station?.country)?.let { "${it.flag} ${it.title}" } ?: "Радио")
                        else if (now.count > 1) "${now.index + 1} из ${now.count}" else (now.track?.format ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = c.text,
                    )
                }
                IconButton(onClick = { vm.setCassette(!cassette) }) {
                    Text(if (cassette) "🖼️" else "📼", fontSize = 22.sp)
                }
            }

            // ---- кассета / обложка ----
            Box(
                Modifier.weight(1f).fillMaxWidth().pointerInput(now.isRadio) {
                    if (now.isRadio) return@pointerInput
                    var dx = 0f
                    detectHorizontalDragGestures(onDragEnd = {
                        if (dx < -120) AudioEngine.next() else if (dx > 120) AudioEngine.prev()
                        dx = 0f
                    }) { _, d -> dx += d }
                },
                contentAlignment = Alignment.Center,
            ) {
                val progress = if (dur > 0) pos.toFloat() / dur else if (now.isRadio) 0.5f else 0f
                val pulse by animateFloatAsState(1f + beat * 0.035f, spring(stiffness = 600f), label = "pulse")
                AnimatedContent(cassette, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "view") { cas ->
                    if (cas) {
                        Cassette(
                            art = art,
                            title = now.title,
                            subtitle = if (now.isRadio) (now.streamTitle ?: now.artist) else listOf(now.artist, now.album).filter { it.isNotBlank() }.joinToString(" · "),
                            progress = progress,
                            playing = now.isPlaying,
                            modifier = Modifier.fillMaxWidth().scale(pulse),
                            seed = now.station?.name ?: now.title,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth(0.88f).aspectRatio(1f).scale(pulse).clip(RoundedCornerShape(32.dp))
                                .border(1.dp, c.glassBorder, RoundedCornerShape(32.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else if (now.isRadio && now.station != null) StationLogo(now.station!!, Modifier.fillMaxSize(), size = 320.dp)
                            else ArtThumb(null, now.title, Modifier.fillMaxSize(), size = 320.dp, corner = 32.dp)
                        }
                    }
                }
            }

            // ---- название ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        now.title.ifBlank { "Ничего не играет" },
                        style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    Text(
                        if (now.isRadio) (now.streamTitle ?: now.artist) else now.artist,
                        style = MaterialTheme.typography.bodyLarge, color = c.textDim, maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
                val favSt = now.station
                if (now.isRadio && favSt != null) {
                    val fav = favSt.id in favs
                    IconButton(onClick = { vm.toggleFav(favSt) }) {
                        Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Избранное", tint = if (fav) c.accent else c.text)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SpectrumBars(now.isPlaying, Modifier.fillMaxWidth().height(44.dp), bands = 48, onBeat = { beat = it })
            Spacer(Modifier.height(4.dp))

            // ---- прогресс ----
            if (now.isRadio) {
                RadioBitrateRow(now)
            } else {
                val value = seeking ?: if (dur > 0) pos.toFloat() / dur else 0f
                Slider(
                    value = value.coerceIn(0f, 1f),
                    onValueChange = { seeking = it },
                    onValueChangeFinished = { seeking?.let { AudioEngine.seekTo((it * dur).toLong()) }; seeking = null },
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.text.copy(alpha = 0.15f)),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(fmtTime(((seeking ?: -1f).takeIf { it >= 0 }?.let { (it * dur).toLong() }) ?: pos), style = MaterialTheme.typography.labelMedium, color = c.textDim)
                    Spacer(Modifier.weight(1f))
                    Text("-" + fmtTime((dur - pos).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = c.textDim)
                }
            }

            // ---- кнопки ----
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { AudioEngine.toggleShuffle() }, enabled = !now.isRadio) {
                    Icon(Icons.Rounded.Shuffle, "Перемешать", tint = if (now.shuffle) c.accent else c.text.copy(alpha = if (now.isRadio) 0.25f else 0.8f))
                }
                IconButton(onClick = { AudioEngine.prev() }, enabled = !now.isRadio, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Назад", tint = c.text.copy(alpha = if (now.isRadio) 0.25f else 1f), modifier = Modifier.size(38.dp))
                }
                Box(contentAlignment = Alignment.Center) {
                    PlayButton(now.isPlaying, size = 78, onClick = { AudioEngine.toggle() })
                    if (now.buffering) CircularProgressIndicator(Modifier.size(90.dp), strokeWidth = 2.dp, color = c.accent2)
                }
                IconButton(onClick = { AudioEngine.next() }, enabled = !now.isRadio, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Далее", tint = c.text.copy(alpha = if (now.isRadio) 0.25f else 1f), modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { AudioEngine.cycleRepeat() }, enabled = !now.isRadio) {
                    Icon(
                        if (now.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Повтор",
                        tint = if (now.repeat != Player.REPEAT_MODE_OFF) c.accent else c.text.copy(alpha = if (now.isRadio) 0.25f else 0.8f),
                    )
                }
            }

            // ---- нижние действия ----
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionChip("${sound.voice.emoji} ${sound.voice.title}", sound.voice.ordinal != 0, onOpenSound)
                ActionIcon(Icons.Rounded.Tune, sound.eqOn && sound.gains.any { it != 0f } || sound.bass > 0, onOpenSound)
                ActionIcon(Icons.Rounded.Bedtime, sleepAt > 0) { showSleep = true }
                if (!now.isRadio) ActionIcon(Icons.AutoMirrored.Rounded.QueueMusic, false) { showQueue = true }
                else ActionIcon(Icons.Rounded.Radio, false, onClose)
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = MaterialTheme.colorScheme.surface) {
            QueueList()
        }
    }
    if (showSleep) SleepDialog(sleepAt) { showSleep = false }
}

@Composable
private fun RadioBitrateRow(now: NowPlaying) {
    val c = LocalAura.current
    val st = now.station ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFE53935)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("● LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text("Битрейт, кбит/с:", style = MaterialTheme.typography.labelMedium, color = c.textDim)
        }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            st.streams.forEach { s ->
                Pill(
                    s.label + (if (s.codec.isNotBlank() && s.codec != "HLS") " ${s.codec}" else if (s.codec == "HLS") " HLS" else ""),
                    selected = s.url == now.stream?.url,
                    onClick = { if (s.url != now.stream?.url) AudioEngine.playStation(st, s) },
                )
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalAura.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (active) c.accent.copy(alpha = 0.25f) else c.glass)
            .border(1.dp, if (active) c.accent else c.glassBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text(text, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    val c = LocalAura.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (active) c.accent.copy(alpha = 0.25f) else c.glass)
            .border(1.dp, if (active) c.accent else c.glassBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (active) c.accent else c.text, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun QueueList() {
    val c = LocalAura.current
    val now by AudioEngine.now.collectAsState()
    val q = remember(now.count, now.index) { AudioEngine.queue() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (now.index - 2).coerceAtLeast(0))
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Очередь · ${q.size}", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(state = state, modifier = Modifier.height(480.dp)) {
            itemsIndexed(q) { i, t ->
                val cur = i == now.index
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (cur) c.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { AudioEngine.jumpTo(i) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtThumb(t.uri, t.title, size = 42.dp, corner = 10.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(t.title, color = if (cur) c.accent else c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (cur) Icon(Icons.Rounded.GraphicEq, null, tint = c.accent)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SleepDialog(sleepAt: Long, onDismiss: () -> Unit) {
    val c = LocalAura.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sleepAt > 0) {
                    val left = ((sleepAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                    Text("Музыка остановится через ~$left мин", color = c.accent)
                }
                Text("Музыка плавно затихнет и остановится", color = c.textDim, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                        Pill("$m мин", false, onClick = { AudioEngine.setSleep(m); onDismiss() })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = {
            if (sleepAt > 0) TextButton(onClick = { AudioEngine.setSleep(0); onDismiss() }) { Text("Выключить таймер") }
        },
    )
}
