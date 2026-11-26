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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.Track
import com.aura.player.playback.AudioEngine

private enum class LibTab(val title: String, val icon: ImageVector) {
    TRACKS("Треки", Icons.Rounded.LibraryMusic),
    FOLDERS("Папки", Icons.Rounded.Folder),
    ALBUMS("Альбомы", Icons.Rounded.Album),
    ARTISTS("Исполнители", Icons.Rounded.Person),
}

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onMenu: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenDevicePicker: () -> Unit,
    bottomPadding: Int,
) {
    val c = LocalAura.current
    val tracks by vm.tracks.collectAsState()
    val addProgress by vm.addProgress.collectAsState()
    val now by AudioEngine.now.collectAsState()
    val sort by vm.sort.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(LibTab.TRACKS) }
    var onlySd by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val sdCount = remember(tracks) { tracks.count { it.onSdCard } }
    val filtered = remember(tracks, query, onlySd) {
        tracks.filter { t ->
            (!onlySd || t.onSdCard) && (
                query.isBlank() ||
                    t.title.contains(query, true) ||
                    t.artist.contains(query, true) ||
                    t.album.contains(query, true) ||
                    t.folder.contains(query, true)
                )
        }
    }
    val groupKey: (Track) -> String = when (tab) {
        LibTab.ALBUMS -> { t -> t.album.ifBlank { "Без альбома" } }
        LibTab.ARTISTS -> { t -> t.artist }
        LibTab.FOLDERS -> { t -> t.folder.ifBlank { "Добавленные файлы" } }
        LibTab.TRACKS -> { _ -> "" }
    }
    val shown = remember(filtered, tab, group) {
        if (tab == LibTab.TRACKS || group == null) filtered else filtered.filter { groupKey(it) == group }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        FlatTopBar(
            title = "Моя музыка",
            subtitle = if (tracks.isEmpty()) "Плейлист пуст"
            else "${tracks.size} треков" + if (sdCount > 0) " · $sdCount на SD" else "",
            onMenu = onMenu,
            actions = {
                if (tracks.isNotEmpty()) {
                    IconButton(onClick = { vm.rescanFolders() }) {
                        Icon(Icons.Rounded.Refresh, "Обновить папки", tint = c.text)
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, "Сортировка", tint = c.text)
                        }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }, containerColor = c.surface) {
                            listOf("По названию", "По исполнителю", "По альбому", "По длительности", "По папке").forEachIndexed { i, s ->
                                DropdownMenuItem(
                                    text = { Text((if (i == sort) "✓ " else "   ") + s, color = if (i == sort) c.accent else c.text) },
                                    onClick = { vm.setSort(i); sortMenu = false },
                                )
                            }
                            HorizontalDivider(color = c.border)
                            DropdownMenuItem(
                                text = { Text("Очистить весь список", color = c.danger) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger) },
                                onClick = { sortMenu = false; confirmClear = true },
                            )
                        }
                    }
                }
            },
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = bottomPadding.dp),
        ) {
            // индикатор чтения файлов
            addProgress?.let { n ->
                item {
                    FlatCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent)
                            Spacer(Modifier.width(12.dp))
                            Text("Добавляю треки… найдено: $n", color = c.text, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                // ---- чистый экран первого запуска без мусорных 300 файлов ----
                item {
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.accentSoft)
                                    .border(1.dp, c.accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.LibraryMusic, null, tint = c.accent, modifier = Modifier.size(30.dp))
                            }
                            Text("Пока нет песен", style = MaterialTheme.typography.titleLarge, color = c.text)
                            Text(
                                "Добавьте только нужные песни или папки — плеер не загружает все аудиофайлы с телефона автоматически.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textDim,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            FlatPrimaryButton("Добавить файлы", Icons.Rounded.Add, onAddFiles, Modifier.fillMaxWidth())
                            FlatTonalButton("Добавить папку (телефон / SD)", Icons.Rounded.CreateNewFolder, onAddFolder, Modifier.fillMaxWidth())
                            FlatTonalButton("Выбрать папки с устройства", Icons.Rounded.PhoneAndroid, onOpenDevicePicker, Modifier.fillMaxWidth())
                        }
                    }
                    FlatCard(Modifier.fillMaxWidth().padding(top = 12.dp), padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.SdCard, null, tint = c.accent, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Карта памяти и любые форматы", style = MaterialTheme.typography.titleSmall, color = c.text)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "В окне выбора папки нажмите ☰ слева сверху, чтобы выбрать SD-карту. Поддерживаются MP3, FLAC, M4A, OGG, WAV и др.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textDim,
                                )
                            }
                        }
                    }
                }
            } else {
                // ---- компактная панель добавления ----
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactAddBtn("Файлы", Icons.Rounded.Add, Modifier.weight(1f), onAddFiles)
                        CompactAddBtn("Папка / SD", Icons.Rounded.CreateNewFolder, Modifier.weight(1f), onAddFolder)
                        CompactAddBtn("Импорт", Icons.Rounded.PhoneAndroid, Modifier.weight(1f), onOpenDevicePicker)
                    }
                    Spacer(Modifier.height(12.dp))
                    FlatSearchField(query, { query = it }, "Поиск по названию, исполнителю, папке…")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibTab.entries.forEach { t ->
                            FlatChip(t.title, tab == t, onClick = { tab = t; group = null }, icon = t.icon)
                        }
                        if (sdCount > 0) {
                            FlatChip("SD ($sdCount)", onlySd, onClick = { onlySd = !onlySd }, icon = Icons.Rounded.SdCard)
                        }
                    }
                }

                if (tab != LibTab.TRACKS && group == null) {
                    val groups = filtered.groupBy(groupKey).toList().sortedBy { it.first.lowercase() }
                    item { SectionHeader("${tab.title} · ${groups.size}") }
                    items(groups, key = { "g_" + it.first }) { (name, list) ->
                        FlatCard(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            onClick = { group = name },
                            padding = PaddingValues(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ArtThumb(list.first().uri, name, size = 48.dp, corner = 10.dp)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${list.size} треков", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                                }
                                IconButton(onClick = { vm.play(list, list.first()) }) {
                                    Icon(Icons.Rounded.PlayArrow, "Играть", tint = c.accent)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (group != null) {
                                IconButton(onClick = { group = null }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = c.text)
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                (group ?: "ТРЕКИ").uppercase() + " · ${shown.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textDim,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (shown.isNotEmpty()) {
                                FlatChip("Перемешать", selected = false, onClick = { vm.shuffle(shown) }, icon = Icons.Rounded.Shuffle)
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            FlatEmptyState(
                                icon = Icons.Rounded.FolderOpen,
                                title = "Ничего не найдено",
                                text = "Попробуйте изменить запрос или сбросить фильтр.",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(shown, key = { it.uri }) { t ->
                        TrackRow(
                            t = t,
                            playing = now.track?.uri == t.uri,
                            onClick = { vm.play(shown, t) },
                            onRemove = { vm.remove(t) },
                        )
                        HorizontalDivider(thickness = 1.dp, color = c.border.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.surface,
            title = { Text("Очистить список?", color = c.text) },
            text = { Text("Все треки будут убраны из плеера. Сами файлы на телефоне и карте памяти останутся на месте.", color = c.textDim) },
            confirmButton = {
                TextButton(onClick = { vm.clearLibrary(); confirmClear = false }) { Text("Очистить", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun CompactAddBtn(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackRow(t: Track, playing: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val c = LocalAura.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (playing) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtThumb(t.uri, t.album.ifBlank { t.title }, size = 46.dp, corner = 10.dp)
            if (playing) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
            Text(
                t.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (playing) c.accent else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.onSdCard) {
                    Icon(Icons.Rounded.SdCard, "Карта памяти", tint = c.accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    listOf(t.artist, fmtTime(t.durationMs).takeIf { t.durationMs > 0 }).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (t.format.isNotBlank()) {
            Text(
                t.format,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = c.textDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.MoreVert, "Ещё", tint = c.textDim, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                DropdownMenuItem(text = { Text("Играть следующим") }, onClick = { AudioEngine.playNext(t); menu = false })
                DropdownMenuItem(text = { Text("Добавить в очередь") }, onClick = { AudioEngine.addToQueue(t); menu = false })
                HorizontalDivider(color = c.border)
                DropdownMenuItem(text = { Text("Убрать из списка", color = c.danger) }, onClick = { onRemove(); menu = false })
            }
        }
    }
}

// ===================== Окно выборочного импорта папок с устройства =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFolderPickerSheet(
    vm: MainViewModel,
    onDismiss: () -> Unit,
) {
    val c = LocalAura.current
    val folders by vm.pickerFolders.collectAsState()
    val loading by vm.pickerLoading.collectAsState()
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }

    val selectedTracks = remember(folders, selectedKeys) {
        folders.filter { it.key in selectedKeys }.flatMap { it.tracks }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Выбрать папки с устройства", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text("Отметьте только нужные папки с музыкой", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
                IconButton(onClick = { vm.loadDeviceFolders() }) {
                    Icon(Icons.Rounded.Refresh, "Обновить", tint = c.text)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = c.accent)
                        Text("Ищу папки с музыкой…", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (folders.isEmpty()) {
                FlatEmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Папки не найдены",
                    text = "На устройстве не найдено аудиофайлов, либо доступ к памяти не выдан. Можно выбрать файлы кнопкой «Добавить папку».",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders, key = { it.key }) { f ->
                        val checked = f.key in selectedKeys
                        FlatCard(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (checked) c.accentSoft else c.surface2,
                            onClick = {
                                selectedKeys = if (checked) selectedKeys - f.key else selectedKeys + f.key
                            },
                            padding = PaddingValues(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    null,
                                    tint = if (checked) c.accent else c.textDim,
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    if (f.onSdCard) Icons.Rounded.SdCard else Icons.Rounded.Folder,
                                    null,
                                    tint = if (f.onSdCard) c.accent else c.textDim,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(f.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${f.tracks.size} треков" + if (f.onSdCard) " · Карта памяти" else " · Телефон",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textDim,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                FlatPrimaryButton(
                    text = if (selectedTracks.isEmpty()) "Выберите папки выше" else "Добавить выбранные (${selectedTracks.size})",
                    icon = Icons.Rounded.Add,
                    onClick = {
                        if (selectedTracks.isNotEmpty()) {
                            vm.importTracks(selectedTracks)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
