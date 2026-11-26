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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.Track
import com.aura.player.playback.AudioEngine

private enum class LibTab(val title: String) { TRACKS("Треки"), ALBUMS("Альбомы"), ARTISTS("Исполнители"), FOLDERS("Папки") }
private enum class Source(val title: String) { ALL("Все"), SD("Карта памяти"), ADDED("Добавленные") }

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    bottomPadding: Int,
) {
    val c = LocalAura.current
    val tracks by vm.tracks.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val addProgress by vm.addProgress.collectAsState()
    val now by AudioEngine.now.collectAsState()
    val sort by vm.sort.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(LibTab.TRACKS) }
    var source by rememberSaveable { mutableStateOf(Source.ALL) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    val filtered = remember(tracks, query, source) {
        tracks.filter { t ->
            (query.isBlank() || t.title.contains(query, true) || t.artist.contains(query, true) || t.album.contains(query, true) || t.folder.contains(query, true)) &&
                when (source) { Source.ALL -> true; Source.SD -> t.onSdCard; Source.ADDED -> t.userAdded }
        }
    }
    val groupKey: (Track) -> String = when (tab) {
        LibTab.ALBUMS -> { t -> t.album.ifBlank { "Без альбома" } }
        LibTab.ARTISTS -> { t -> t.artist }
        LibTab.FOLDERS -> { t -> t.folder.ifBlank { "Без папки" } }
        LibTab.TRACKS -> { t -> "" }
    }
    val shown = remember(filtered, tab, group) {
        if (tab == LibTab.TRACKS || group == null) filtered else filtered.filter { groupKey(it) == group }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomPadding.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Медиатека", style = MaterialTheme.typography.displaySmall, color = c.text)
                    Text("${tracks.size} треков" + (if (tracks.any { it.onSdCard }) " · ${tracks.count { it.onSdCard }} на карте памяти" else ""),
                        style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                }
                IconButton(onClick = { vm.rescanFolders() }) {
                    if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = c.accent)
                    else Icon(Icons.Rounded.Refresh, "Обновить", tint = c.text)
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Сортировка", tint = c.text) }
                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("По названию", "По исполнителю", "По альбому", "По длительности", "По папке").forEachIndexed { i, s ->
                            DropdownMenuItem(text = { Text((if (i == sort) "✓ " else "    ") + s) }, onClick = { vm.setSort(i); sortMenu = false })
                        }
                        DropdownMenuItem(text = { Text("    Вернуть удалённые из списка") }, onClick = { vm.restoreHidden(); sortMenu = false })
                    }
                }
            }
        }

        // ---- поиск ----
        item {
            Spacer(Modifier.height(14.dp))
            SearchField(query, { query = it }, "Песня, исполнитель, альбом, папка")
        }

        // ---- добавление файлов ----
        item {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddCard("Добавить файлы", "MP3, FLAC, M4A, OGG, WAV…", Icons.Rounded.Add, Modifier.weight(1f), onAddFiles)
                AddCard("Добавить папку", "в т.ч. с карты памяти", Icons.Rounded.CreateNewFolder, Modifier.weight(1f), onAddFolder)
            }
            addProgress?.let { n ->
                Glass(Modifier.fillMaxWidth().padding(top = 10.dp), padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("Читаю файлы… найдено: $n", color = c.text)
                    }
                }
            }
        }

        if (!hasPermission) {
            item {
                Glass(Modifier.fillMaxWidth().padding(top = 14.dp), onClick = onRequestPermission) {
                    Column {
                        Text("🔓 Разрешите доступ к музыке", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text("Чтобы автоматически найти все песни в памяти телефона и на карте памяти. Либо добавьте файлы и папки вручную кнопками выше.",
                            style = MaterialTheme.typography.bodySmall, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // ---- вкладки ----
        item {
            Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibTab.entries.forEach { t -> Pill(t.title, tab == t, onClick = { tab = t; group = null }) }
            }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Source.entries.forEach { s ->
                    Pill(s.title, source == s, onClick = { source = s }, leading = when (s) { Source.SD -> "💾"; Source.ADDED -> "➕"; else -> "🎧" })
                }
            }
        }

        if (tab != LibTab.TRACKS && group == null) {
            // ---- список групп ----
            val groups = filtered.groupBy(groupKey).toList().sortedBy { it.first.lowercase() }
            item { SectionTitle("${tab.title} · ${groups.size}") }
            items(groups, key = { "g_" + it.first }) { (name, list) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { group = name }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtThumb(list.first().uri, name, size = 58.dp, corner = 16.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${list.size} треков", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                    }
                    IconButton(onClick = { vm.play(list, list.first()) }) { Icon(Icons.Rounded.PlayArrow, "Играть", tint = c.accent) }
                }
            }
        } else {
            item {
                Row(Modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (group != null) {
                        IconButton(onClick = { group = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = c.text) }
                    }
                    Text(group ?: "Все треки", style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (shown.isNotEmpty()) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(c.accent, c.accent2)))
                                .clickable { vm.shuffle(shown) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Shuffle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Перемешать", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty() && !scanning) {
                item {
                    EmptyState(
                        if (query.isNotBlank()) "🔍" else "🎶",
                        if (query.isNotBlank()) "Ничего не найдено" else "Пока пусто",
                        if (source == Source.SD) "Если песни с карты памяти не появились — нажмите «Добавить папку» и выберите карту памяти в боковом меню выбора файлов."
                        else "Нажмите «Добавить файлы» или «Добавить папку», чтобы выбрать песни из памяти телефона или с карты памяти.",
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            items(shown, key = { it.uri }) { t ->
                TrackRow(t, playing = now.track?.uri == t.uri, onClick = { vm.play(shown, t) }, onRemove = { vm.remove(t) })
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, onSubmit: (() -> Unit)? = null) {
    val c = LocalAura.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = c.textDim)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(hint, color = c.textDim, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                BasicTextField(
                    value, onChange, singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit?.invoke() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(Icons.Rounded.Close, "Очистить", tint = c.textDim, modifier = Modifier.size(20.dp).clickable { onChange("") })
            }
        }
    }
}

@Composable
private fun AddCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    Glass(modifier, onClick = onClick, padding = PaddingValues(14.dp)) {
        Column {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(c.accent, c.accent2))),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TrackRow(t: Track, playing: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val c = LocalAura.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (playing) c.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 7.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtThumb(t.uri, t.album.ifBlank { t.title })
            if (playing) Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, null, tint = Color.White)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (playing) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.onSdCard) {
                    Icon(Icons.Rounded.SdCard, "Карта памяти", tint = c.accent2, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOf(t.artist, fmtTime(t.durationMs).takeIf { t.durationMs > 0 }).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (t.format.isNotBlank()) {
            Text(
                t.format, fontSize = 10.sp, color = c.textDim,
                modifier = Modifier.border(1.dp, c.glassBorder, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё", tint = c.textDim) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Играть следующим") }, onClick = { AudioEngine.playNext(t); menu = false })
                DropdownMenuItem(text = { Text("Добавить в очередь") }, onClick = { AudioEngine.addToQueue(t); menu = false })
                DropdownMenuItem(text = { Text("Убрать из медиатеки") }, onClick = { onRemove(); menu = false })
            }
        }
    }
}
