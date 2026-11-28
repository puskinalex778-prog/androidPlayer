package com.aura.player.ui

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.aura.player.data.OutputMode
import com.aura.player.data.PlayerDockAction
import com.aura.player.data.Track
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

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

// ===================== Адаптивный мини-плеер =====================

@Composable
fun MiniPlayer(now: NowPlaying, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAura.current
    val (pos, dur) = rememberProgress()
    val art by AudioEngine.art.collectAsState()
    val bg = if (c.style == UiStyle.GLASS) c.surfaceSolid.copy(alpha = 0.88f) else c.surface
    var dragY by remember { mutableFloatStateOf(0f) }
    Column(
        modifier
            .fillMaxWidth()
            .background(bg)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY < -55f) onOpen()
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                ) { _, dy -> dragY += dy }
            }
            .clickable(onClick = onOpen),
    ) {
        if (!now.isRadio && dur > 0) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.border)) {
                Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).height(2.dp).background(c.accent))
            }
        } else {
            HorizontalDivider(thickness = c.borderWidth, color = c.border)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val st = now.station
            if (now.isRadio && st != null) {
                StationLogo(st, size = 44.dp)
            } else {
                val shape = RoundedCornerShape(if (c.style == UiStyle.RETRO) 4.dp else 10.dp)
                Box(Modifier.size(44.dp).clip(shape).border(c.borderWidth, c.border, shape)) {
                    val a = art
                    if (a != null) Image(a.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else ArtThumb(null, now.title, size = 44.dp)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    now.title.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (now.isRadio) (now.streamTitle ?: now.artist) else now.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (now.buffering) {
                CircularProgressIndicator(Modifier.size(20.dp).padding(end = 6.dp), strokeWidth = 2.dp, color = c.accent)
            }
            if (!now.isRadio) {
                IconButton(onClick = { AudioEngine.prev() }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Назад", tint = c.text, modifier = Modifier.size(22.dp))
                }
            }
            FlatPlayButton(now.isPlaying, size = 40, onClick = { AudioEngine.toggle() })
            if (!now.isRadio) {
                IconButton(onClick = { AudioEngine.next() }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Далее", tint = c.text, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun FlatPlayButton(playing: Boolean, size: Int, onClick: () -> Unit) {
    val c = LocalAura.current
    val targetCorner = when (c.style) {
        UiStyle.RETRO -> if (playing) 14f else 22f
        else -> if (playing) 28f else 50f
    }
    val corner by animateFloatAsState(targetCorner, spring(), label = "corner")
    val shape = RoundedCornerShape(corner.toInt())
    val bg = if (c.style == UiStyle.GLASS) Brush.linearGradient(listOf(c.accent, c.accent2)) else SolidColor(c.accent)
    Box(
        Modifier
            .size(size.dp)
            .then(if (c.style == UiStyle.SOFT) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(playing, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "pp") { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (p) "Пауза" else "Играть",
                tint = c.onAccent,
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
    onOpenVoice: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenRadio: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onOpenCarMode: () -> Unit = {},
    onCustomizeDock: () -> Unit = {},
    onDeletePhysical: (Track) -> Unit = {},
) {
    val c = LocalAura.current
    val now by AudioEngine.now.collectAsState()
    val sound by AudioEngine.sound.collectAsState()
    val out by AudioEngine.output.collectAsState()
    val ab by AudioEngine.abLoop.collectAsState()
    val settings by vm.settings.collectAsState()
    val favs by vm.favorites.collectAsState()
    val favTracks by vm.favoriteTracks.collectAsState()
    val allBookmarks by vm.bookmarks.collectAsState()
    val trackNotes by vm.trackNotes.collectAsState()
    val sleepAt by AudioEngine.sleepAt.collectAsState()
    val sleepAfterTrack by AudioEngine.sleepAfterTrack.collectAsState()
    val artBmp by AudioEngine.art.collectAsState()
    val art: ImageBitmap? = remember(artBmp) { artBmp?.asImageBitmap() }
    val (pos, dur) = rememberProgress()
    var seeking by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }
    var showPlaylistAdd by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showVizPicker by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showTrackInfo by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var deleteConfirmTrack by remember { mutableStateOf<Track?>(null) }
    var beat by remember { mutableFloatStateOf(0f) }
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    val animDismissY by animateFloatAsState(dismissDragY, spring(stiffness = 700f), label = "dismissY")

    val stage = settings.playerStage
    val vizType = settings.visualizer

    Box(Modifier.fillMaxSize()) {
        StyleBackdrop()

        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animDismissY.roundToInt()) }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- верхняя область со свайпом вниз, индикатором таймера справа вверху и переключателем Кассета | Обложка | Визуал ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dismissDragY > 110f) onClose()
                                dismissDragY = 0f
                            },
                            onDragCancel = { dismissDragY = 0f },
                        ) { _, dy ->
                            dismissDragY = (dismissDragY + dy).coerceIn(0f, 360f)
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(c.border.copy(alpha = 0.9f)),
                    )
                    if (sleepAt > 0 || sleepAfterTrack) {
                        val timerBadge = if (sleepAfterTrack) "трек"
                        else "${((sleepAt - System.currentTimeMillis()) / 60000).coerceAtLeast(1)} мин"
                        Row(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .clip(RoundedCornerShape(50))
                                .background(c.accentSoft)
                                .border(c.borderWidth, c.accent, RoundedCornerShape(50))
                                .clickable { showSleep = true }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(Icons.Rounded.Bedtime, "Таймер", tint = c.accent, modifier = Modifier.size(12.dp))
                            Text(timerBadge, style = MaterialTheme.typography.labelSmall, color = c.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", tint = c.text, modifier = Modifier.size(30.dp))
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        FlatSegmentedControl(
                            options = listOf("Кассета", "Обложка", "Визуал"),
                            selectedIndex = stage,
                            onSelect = { vm.setPlayerStage(it) },
                        )
                    }
                    IconButton(onClick = { if (!now.isRadio) showQueue = true else onClose() }) {
                        Icon(
                            if (!now.isRadio) Icons.AutoMirrored.Rounded.QueueMusic else Icons.Rounded.Radio,
                            "Очередь",
                            tint = c.text,
                        )
                    }
                }
            }

            // ---- центральная сцена (горизонтальный свайп = трек, свайп вниз = свернуть) ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(now.isRadio) {
                        var dx = 0f
                        var lockedAxis = 0
                        detectDragGestures(
                            onDragStart = { dx = 0f; lockedAxis = 0 },
                            onDragEnd = {
                                when (lockedAxis) {
                                    1 -> if (!now.isRadio) {
                                        if (dx < -110f) AudioEngine.next() else if (dx > 110f) AudioEngine.prev()
                                    }
                                    2 -> if (dismissDragY > 110f) onClose()
                                }
                                dx = 0f
                                lockedAxis = 0
                                dismissDragY = 0f
                            },
                            onDragCancel = {
                                dx = 0f
                                lockedAxis = 0
                                dismissDragY = 0f
                            },
                        ) { change, drag ->
                            if (lockedAxis == 0) {
                                if (abs(drag.y) > abs(drag.x) && drag.y > 0f) lockedAxis = 2
                                else if (!now.isRadio && abs(drag.x) > abs(drag.y)) lockedAxis = 1
                            }
                            when (lockedAxis) {
                                1 -> {
                                    change.consume()
                                    dx += drag.x
                                }
                                2 -> {
                                    change.consume()
                                    dismissDragY = (dismissDragY + drag.y).coerceIn(0f, 360f)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val progress = if (dur > 0) pos.toFloat() / dur else if (now.isRadio) 0.5f else 0f
                val pulse by animateFloatAsState(1f + beat * 0.025f, spring(stiffness = 600f), label = "pulse")
                AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { stg ->
                    when (stg) {
                        0 -> Cassette(
                            art = art,
                            title = now.title,
                            subtitle = if (now.isRadio) (now.streamTitle ?: now.artist)
                            else listOf(now.artist, now.album).filter { it.isNotBlank() }.joinToString(" · "),
                            progress = progress,
                            playing = now.isPlaying,
                            badge = if (now.isRadio) "LIVE" else fmtTime(pos),
                            modifier = Modifier.fillMaxWidth().scale(pulse),
                            seed = now.station?.name ?: now.title,
                        )
                        1 -> {
                            val shape = RoundedCornerShape(c.cardRadius + 4.dp)
                            val st = now.station
                            Box(
                                Modifier
                                    .fillMaxWidth(0.86f)
                                    .aspectRatio(1f)
                                    .scale(pulse)
                                    .clip(shape)
                                    .background(c.surface)
                                    .border(c.borderWidth, c.border, shape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (art != null) {
                                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else if (now.isRadio && st != null) {
                                    StationLogo(st, Modifier.fillMaxSize(), size = 300.dp)
                                } else {
                                    val base = flatSeedColor(now.title)
                                    Box(Modifier.fillMaxSize().background(base.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Album, null, tint = base, modifier = Modifier.size(96.dp))
                                    }
                                }
                            }
                        }
                        else -> {
                            // Большой полноразмерный частотный визуализатор + переключатель 9 режимов
                            Column(
                                Modifier.fillMaxSize().padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                FlatCard(
                                    Modifier.weight(1f).fillMaxWidth(),
                                    onClick = { vm.cycleVisualizer() },
                                    padding = PaddingValues(6.dp),
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        AudioVisualizer(
                                            type = vizType,
                                            playing = now.isPlaying,
                                            modifier = Modifier.fillMaxSize(),
                                            bands = 40,
                                            large = true,
                                            ledDeckSparks = settings.ledDeckSparks,
                                            onBeat = { beat = it },
                                        )
                                        if (vizType == VisualizerType.LED_VU) {
                                            Row(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xCC0D1322))
                                                    .border(1.dp, c.border, RoundedCornerShape(50))
                                                    .clickable { vm.setLedDeckSparks(!settings.ledDeckSparks) }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    if (settings.ledDeckSparks) "Режим: Искры" else "Режим: Спектр",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = c.accent,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    VisualizerType.entries.forEach { vt ->
                                        FlatChip(vt.title, vizType == vt, onClick = { vm.setVisualizer(vt) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- заголовок трека + «В плейлист» / Избранное ----
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        now.title.ifBlank { "Ничего не играет" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.text,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (now.isRadio) (now.streamTitle ?: now.artist) else now.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textDim,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
                val favSt = now.station
                if (now.isRadio && favSt != null) {
                    val fav = favSt.id in favs
                    IconButton(onClick = { vm.toggleFav(favSt) }) {
                        Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Избранное", tint = if (fav) c.accent else c.textDim)
                    }
                } else if (!now.isRadio && now.track != null) {
                    val tr = now.track!!
                    val liked = tr.uri in favTracks
                    IconButton(onClick = { vm.toggleFavoriteTrack(tr.uri) }, modifier = Modifier.size(40.dp)) {
                        Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное", tint = if (liked) c.danger else c.textDim)
                    }
                    IconButton(onClick = { showPlaylistAdd = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "В плейлист", tint = c.accent)
                    }
                }
            }

            // ---- строка качества AIMP + быстрый выбор вывода (Динамик / Наушники) ----
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surface)
                        .border(c.borderWidth, c.border, RoundedCornerShape(8.dp))
                        .clickable { showOutput = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        if (out.mode == com.aura.player.data.OutputMode.SPEAKER) Icons.Rounded.Speaker else Icons.Rounded.Headphones,
                        null,
                        tint = c.accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        "${out.activeLabel} · ${sound.quality.badge}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!now.isRadio) {
                    val aStart = ab.aMs
                    val abLabel = when {
                        ab.active -> "A–B ✓"
                        aStart != null -> "A: ${fmtTime(aStart)} → B?"
                        else -> "A–B"
                    }
                    Text(
                        abLabel,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (ab.aMs != null) c.onAccent else c.textDim,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ab.aMs != null) c.accent else c.surface)
                            .border(c.borderWidth, if (ab.aMs != null) c.accent else c.border, RoundedCornerShape(8.dp))
                            .clickable { AudioEngine.tapAbLoop() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // ---- компактный частотный визуализатор (в режимах Кассета и Обложка) ----
            if (stage != 2) {
                Spacer(Modifier.height(10.dp))
                FlatCard(
                    Modifier.fillMaxWidth(),
                    onClick = { vm.cycleVisualizer() },
                    padding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        AudioVisualizer(
                            type = vizType,
                            playing = now.isPlaying,
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            bands = 40,
                            large = false,
                            ledDeckSparks = settings.ledDeckSparks,
                            onBeat = { beat = it },
                        )
                        Text(
                            vizType.title,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textDim.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ---- прогресс или битрейт радио ----
            if (now.isRadio) {
                RadioBitrateRow(now)
            } else {
                val value = seeking ?: if (dur > 0) pos.toFloat() / dur else 0f
                Slider(
                    value = value.coerceIn(0f, 1f),
                    onValueChange = { seeking = it },
                    onValueChangeFinished = { seeking?.let { AudioEngine.seekTo((it * dur).toLong()) }; seeking = null },
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.surface2,
                    ),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val curMs = (seeking?.let { (it * dur).toLong() }) ?: pos
                    Text(fmtTime(curMs), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = c.textDim)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { AudioEngine.seekBy(-10_000L) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Replay10, "-10 сек", tint = c.textDim, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { AudioEngine.seekBy(10_000L) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Forward10, "+10 сек", tint = c.textDim, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (dur > 0) fmtTime(dur) else "0:00", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = c.textDim)
                }
            }

            // ---- транспорт ----
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { AudioEngine.toggleShuffle() }, enabled = !now.isRadio) {
                    Icon(
                        Icons.Rounded.Shuffle, "Перемешать",
                        tint = if (now.shuffle && !now.isRadio) c.accent else c.textDim.copy(alpha = if (now.isRadio) 0.3f else 1f),
                    )
                }
                IconButton(onClick = { AudioEngine.prev() }, enabled = !now.isRadio, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Rounded.SkipPrevious, "Назад",
                        tint = c.text.copy(alpha = if (now.isRadio) 0.25f else 1f),
                        modifier = Modifier.size(34.dp),
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    FlatPlayButton(now.isPlaying, size = 70, onClick = { AudioEngine.toggle() })
                    if (now.buffering) CircularProgressIndicator(Modifier.size(82.dp), strokeWidth = 2.dp, color = c.accent)
                }
                IconButton(onClick = { AudioEngine.next() }, enabled = !now.isRadio, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext, "Далее",
                        tint = c.text.copy(alpha = if (now.isRadio) 0.25f else 1f),
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = { AudioEngine.cycleRepeat() }, enabled = !now.isRadio) {
                    Icon(
                        if (now.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Повтор",
                        tint = if (now.repeat != Player.REPEAT_MODE_OFF && !now.isRadio) c.accent else c.textDim.copy(alpha = if (now.isRadio) 0.3f else 1f),
                    )
                }
            }

            // ---- настраиваемая нижняя панель инструментов (удержание = настроить) ----
            val voiceActive = sound.voice.ordinal != 0 || sound.pitchSemis != 0f || sound.speed != 1f
            val stemActive = sound.stemMode != com.aura.player.data.StemIsolationMode.OFF
            val devEqActive = sound.deviceEqEnabled && sound.activeDeviceProfile != null
            val eqActive = stemActive || devEqActive || (sound.eqOn && (sound.gains.any { it != 0f } || sound.bass > 0f))
            val trackUri = now.track?.uri
            val hasBookmarks = trackUri != null && allBookmarks.any { it.trackUri == trackUri }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(if (settings.dockActions.size >= 5) 6.dp else 8.dp),
            ) {
                settings.dockActions.forEach { action ->
                    val (label, active) = when (action) {
                        PlayerDockAction.VOICE -> (if (sound.voice.ordinal != 0) sound.voice.title else action.title) to voiceActive
                        PlayerDockAction.EQ -> {
                            val lbl = when {
                                stemActive -> sound.stemMode.shortTitle
                                sound.eqOn && (sound.gains.any { it != 0f } || sound.bass > 0f) -> sound.preset
                                devEqActive -> "AutoEQ"
                                else -> action.title
                            }
                            lbl to eqActive
                        }
                        PlayerDockAction.TIMER -> {
                            val lbl = when {
                                sleepAfterTrack -> "Трек"
                                sleepAt > 0 -> "${((sleepAt - System.currentTimeMillis()) / 60000).coerceAtLeast(1)} м"
                                else -> action.title
                            }
                            lbl to (sleepAt > 0 || sleepAfterTrack)
                        }
                        PlayerDockAction.OUTPUT -> action.title to (out.mode != OutputMode.AUTO)
                        PlayerDockAction.RADIO -> action.title to now.isRadio
                        PlayerDockAction.PLAYLISTS -> action.title to false
                        PlayerDockAction.QUEUE -> action.title to showQueue
                        PlayerDockAction.LYRICS -> action.title to showLyrics
                        PlayerDockAction.SPEED -> {
                            val spd = (sound.speed * 100).roundToInt() / 100f
                            val lbl = if (sound.speed != 1f) "${spd}x" else action.title
                            lbl to (sound.speed != 1f || sound.pitchSemis != 0f)
                        }
                        PlayerDockAction.VISUALIZER -> action.title to (stage == 2)
                        PlayerDockAction.BOOKMARK -> action.title to hasBookmarks
                        PlayerDockAction.NOTE -> action.title to (trackUri != null && !trackNotes[trackUri].isNullOrBlank())
                        PlayerDockAction.CAR_MODE -> action.title to false
                        PlayerDockAction.INFO -> action.title to false
                    }
                    ToolButton(
                        icon = dockActionIcon(action, out.mode),
                        label = label,
                        active = active,
                        compact = settings.dockActions.size >= 5,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (action) {
                                PlayerDockAction.VOICE -> onOpenVoice()
                                PlayerDockAction.EQ -> onOpenEq()
                                PlayerDockAction.TIMER -> showSleep = true
                                PlayerDockAction.OUTPUT -> showOutput = true
                                PlayerDockAction.RADIO -> { onClose(); onOpenRadio() }
                                PlayerDockAction.PLAYLISTS -> { onClose(); onOpenPlaylists() }
                                PlayerDockAction.QUEUE -> if (!now.isRadio) showQueue = true else showOutput = true
                                PlayerDockAction.LYRICS -> showLyrics = true
                                PlayerDockAction.SPEED -> showSpeed = true
                                PlayerDockAction.VISUALIZER -> { vm.setPlayerStage(2); showVizPicker = true }
                                PlayerDockAction.BOOKMARK -> showBookmarks = true
                                PlayerDockAction.NOTE -> if (now.track != null) showNote = true else showLyrics = true
                                PlayerDockAction.CAR_MODE -> onOpenCarMode()
                                PlayerDockAction.INFO -> if (now.track != null) showTrackInfo = true else showOutput = true
                            }
                        },
                        onLongClick = onCustomizeDock,
                    )
                }
            }
        }
    }

    if (showOutput) AudioOutputSheet { showOutput = false }
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = c.surfaceSolid) {
            QueueList()
        }
    }
    if (showSleep) SleepDialog(sleepAt) { showSleep = false }
    if (showPlaylistAdd) {
        now.track?.let { tr ->
            AddToPlaylistSheet(vm = vm, track = tr, onDismiss = { showPlaylistAdd = false })
        }
    }
    if (showLyrics) LyricsSheet(vm = vm, track = now.track, posMs = pos, onDismiss = { showLyrics = false })
    if (showSpeed) QuickSpeedPitchSheet(onDismiss = { showSpeed = false })
    if (showVizPicker) VisualizerPickerSheet(vm = vm, onDismiss = { showVizPicker = false })
    if (showBookmarks) BookmarksSheet(vm = vm, track = now.track, posMs = pos, onDismiss = { showBookmarks = false })
    if (showTrackInfo) {
        now.track?.let { tr ->
            TrackInfoSheet(
                vm = vm,
                track = tr,
                onDeletePhysical = { deleteConfirmTrack = tr },
                onDismiss = { showTrackInfo = false },
            )
        }
    }
    if (showNote) {
        now.track?.let { tr ->
            TrackNoteDialog(
                track = tr,
                initialNote = trackNotes[tr.uri].orEmpty(),
                onDismiss = { showNote = false },
                onSave = { txt -> vm.saveTrackNote(tr.uri, txt); showNote = false },
            )
        }
    }
    deleteConfirmTrack?.let { tr ->
        ConfirmPhysicalDeleteDialog(
            track = tr,
            onDismiss = { deleteConfirmTrack = null },
            onConfirm = {
                deleteConfirmTrack = null
                onClose()
                onDeletePhysical(tr)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.controlRadius)
    Column(
        modifier
            .clip(shape)
            .background(if (active) c.accentSoft else c.surface)
            .border(c.borderWidth, if (active) c.accent else c.border, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, label, tint = if (active) c.accent else c.textDim, modifier = Modifier.size(if (compact) 18.dp else 19.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (compact) 10.sp else 11.sp,
            color = if (active) c.accent else c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RadioBitrateRow(now: NowPlaying) {
    val c = LocalAura.current
    val st = now.station ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFDC2626))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusDot(Color.White, size = 6.dp)
                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Text("Битрейт потока:", style = MaterialTheme.typography.labelMedium, color = c.textDim)
        }
        Row(
            Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            st.streams.forEach { s ->
                val text = (if (s.bitrate > 0) "${s.bitrate} кбит/с" else "Авто") +
                    if (s.codec.isNotBlank()) " · ${s.codec}" else ""
                FlatChip(
                    text = text,
                    selected = s.url == now.stream?.url,
                    onClick = { if (s.url != now.stream?.url) AudioEngine.playStation(st, s) },
                )
            }
        }
    }
}

@Composable
private fun QueueList() {
    val c = LocalAura.current
    val now by AudioEngine.now.collectAsState()
    val q = remember(now.count, now.index) { AudioEngine.queue() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (now.index - 2).coerceAtLeast(0))
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Очередь воспроизведения · ${q.size}", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(bottom = 10.dp))
        LazyColumn(state = state, modifier = Modifier.height(440.dp)) {
            itemsIndexed(q) { i, t ->
                val cur = i == now.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(c.controlRadius))
                        .background(if (cur) c.accentSoft else Color.Transparent)
                        .clickable { AudioEngine.jumpTo(i) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtThumb(t.uri, t.title, size = 42.dp)
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
    val sleepAfterTrack by AudioEngine.sleepAfterTrack.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceSolid,
        title = { Text("Таймер сна", color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    sleepAfterTrack -> Text("Пауза сразу после окончания текущего трека", color = c.accent, fontWeight = FontWeight.SemiBold)
                    sleepAt > 0 -> {
                        val left = ((sleepAt - System.currentTimeMillis()) / 60000).coerceAtLeast(1)
                        Text("Остановка через ~$left мин", color = c.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Музыка плавно затихнет и встанет на паузу.", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                        FlatChip("$m мин", false, onClick = { AudioEngine.setSleep(m); onDismiss() })
                    }
                }
                FlatChip(
                    text = "В конце текущего трека",
                    selected = sleepAfterTrack,
                    onClick = { AudioEngine.setSleepAfterTrack(!sleepAfterTrack); onDismiss() },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = {
            if (sleepAt > 0 || sleepAfterTrack) {
                TextButton(onClick = {
                    AudioEngine.setSleep(0)
                    AudioEngine.setSleepAfterTrack(false)
                    onDismiss()
                }) { Text("Выключить") }
            }
        },
    )
}
