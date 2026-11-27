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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.Playlist
import com.aura.player.data.Track
import com.aura.player.playback.AudioEngine

enum class LibTab(val title: String, val icon: ImageVector) {
    TRACKS("Треки", Icons.Rounded.LibraryMusic),
    PLAYLISTS("Плейлисты", Icons.AutoMirrored.Rounded.QueueMusic),
    FOLDERS("Папки", Icons.Rounded.Folder),
    ALBUMS("Альбомы", Icons.Rounded.Album),
    ARTISTS("Исполнители", Icons.Rounded.Person),
}

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    tab: LibTab,
    onTabChange: (LibTab) -> Unit,
    onMenu: () -> Unit,
    onAddFiles: (playlistId: String?) -> Unit,
    onAddFolder: (playlistId: String?) -> Unit,
    onOpenDevicePicker: () -> Unit,
    bottomPadding: Int,
) {
    val c = LocalAura.current
    val tracks by vm.tracks.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favTracks by vm.favoriteTracks.collectAsState()
    val addProgress by vm.addProgress.collectAsState()
    val now by AudioEngine.now.collectAsState()
    val sort by vm.sort.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var onlySd by rememberSaveable { mutableStateOf(false) }
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }

    var sortMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var createPlDialog by remember { mutableStateOf(false) }
    var renamePl by remember { mutableStateOf<Playlist?>(null) }
    var addTrackToPl by remember { mutableStateOf<Track?>(null) }
    var pickExistingForPl by remember { mutableStateOf<Playlist?>(null) }
    var renameTrack by remember { mutableStateOf<Track?>(null) }
    var infoTrack by remember { mutableStateOf<Track?>(null) }

    val openPlaylist = remember(playlists, openPlaylistId) { playlists.firstOrNull { it.id == openPlaylistId } }
    val sdCount = remember(tracks) { tracks.count { it.onSdCard } }
    val favCount = remember(tracks, favTracks) { tracks.count { it.uri in favTracks } }

    val filtered = remember(tracks, query, onlySd, onlyFav, favTracks) {
        tracks.filter { t ->
            (!onlySd || t.onSdCard) && (!onlyFav || t.uri in favTracks) && (
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
        else -> { _ -> "" }
    }
    val shown = remember(filtered, tab, group) {
        if (tab == LibTab.TRACKS || group == null) filtered else filtered.filter { groupKey(it) == group }
    }

    Column(Modifier.fillMaxSize()) {
        FlatTopBar(
            title = if (tab == LibTab.PLAYLISTS) (openPlaylist?.name ?: "Плейлисты") else "Моя музыка",
            subtitle = when {
                tab == LibTab.PLAYLISTS && openPlaylist != null -> "${vm.tracksOf(openPlaylist).size} треков в плейлисте"
                tab == LibTab.PLAYLISTS -> "Плейлистов: ${playlists.size}"
                tracks.isEmpty() -> "Плейлист пуст"
                else -> "${tracks.size} треков" + if (sdCount > 0) " · $sdCount на SD" else ""
            },
            onMenu = onMenu,
            actions = {
                if (tab == LibTab.PLAYLISTS) {
                    IconButton(onClick = { createPlDialog = true }) {
                        Icon(Icons.Rounded.Add, "Создать плейлист", tint = c.text)
                    }
                } else if (tracks.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, "Сортировка", tint = c.text)
                        }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }, containerColor = c.surfaceSolid) {
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding.dp),
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

            // верхняя строка вкладок (видна всегда, чтобы можно было сразу открыть Плейлисты)
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibTab.entries.forEach { t ->
                        val label = if (t == LibTab.PLAYLISTS && playlists.isNotEmpty()) "${t.title} (${playlists.size})" else t.title
                        FlatChip(label, tab == t, onClick = { onTabChange(t); group = null; openPlaylistId = null }, icon = t.icon)
                    }
                    if (favCount > 0 && tab != LibTab.PLAYLISTS) {
                        FlatChip("Любимые ($favCount)", onlyFav, onClick = { onlyFav = !onlyFav }, icon = Icons.Rounded.Favorite)
                    }
                    if (sdCount > 0 && tab != LibTab.PLAYLISTS) {
                        FlatChip("SD ($sdCount)", onlySd, onClick = { onlySd = !onlySd }, icon = Icons.Rounded.SdCard)
                    }
                }
            }

            // ===================== ПЛЕЙЛИСТЫ =====================
            if (tab == LibTab.PLAYLISTS) {
                if (openPlaylist != null) {
                    val plTracks = vm.tracksOf(openPlaylist)
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(onClick = { openPlaylistId = null }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "К плейлистам", tint = c.text)
                            }
                            Text(openPlaylist.name, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (plTracks.isNotEmpty()) {
                                FlatChip("Перемешать", false, onClick = { vm.shuffle(plTracks) }, icon = Icons.Rounded.Shuffle)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                            if (tracks.isNotEmpty()) {
                                CompactAddBtn("Из списка", Icons.AutoMirrored.Rounded.PlaylistAdd, Modifier.weight(1f)) { pickExistingForPl = openPlaylist }
                            }
                            CompactAddBtn("Файлы", Icons.Rounded.Add, Modifier.weight(1f)) { onAddFiles(openPlaylist.id) }
                            CompactAddBtn("Папка / SD", Icons.Rounded.CreateNewFolder, Modifier.weight(1f)) { onAddFolder(openPlaylist.id) }
                        }
                    }
                    if (plTracks.isEmpty()) {
                        item {
                            FlatEmptyState(
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                title = "Плейлист пуст",
                                text = "Добавьте треки из вашей медиатеки или выберите файлы/папку кнопками выше.",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(plTracks, key = { "pl_${openPlaylist.id}_${it.uri}" }) { t ->
                        TrackRow(
                            t = t,
                            playing = now.track?.uri == t.uri,
                            liked = t.uri in favTracks,
                            onToggleLike = { vm.toggleFavoriteTrack(t.uri) },
                            onClick = { vm.play(plTracks, t) },
                            onAddToPlaylist = { addTrackToPl = t },
                            onRename = { renameTrack = t },
                            onInfo = { infoTrack = t },
                            removeLabel = "Убрать из плейлиста",
                            onRemove = { vm.removeTrackFromPlaylist(openPlaylist.id, t.uri) },
                        )
                        HorizontalDivider(thickness = 1.dp, color = c.border.copy(alpha = 0.55f))
                    }
                } else {
                    item {
                        FlatPrimaryButton(
                            text = "Создать плейлист",
                            icon = Icons.Rounded.Add,
                            onClick = { createPlDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        )
                    }
                    if (playlists.isEmpty()) {
                        item {
                            FlatEmptyState(
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                title = "Нет плейлистов",
                                text = "Создайте плейлист для дороги, тренировок или любимых альбомов. Хранятся только ссылки — память не расходуется.",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(playlists, key = { it.id }) { pl ->
                        val plTracks = vm.tracksOf(pl)
                        PlaylistCard(
                            pl = pl,
                            count = plTracks.size,
                            coverUri = plTracks.firstOrNull()?.uri,
                            onOpen = { openPlaylistId = pl.id },
                            onPlay = { if (plTracks.isNotEmpty()) vm.play(plTracks, plTracks.first()) },
                            onRename = { renamePl = pl },
                            onDelete = { vm.deletePlaylist(pl.id) },
                        )
                    }
                }
            } else if (tracks.isEmpty()) {
                // ===================== ПУСТАЯ МЕДИАТЕКА =====================
                item {
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(c.cardRadius))
                                    .background(c.accentSoft)
                                    .border(c.borderWidth, c.accent.copy(alpha = 0.25f), RoundedCornerShape(c.cardRadius)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.LibraryMusic, null, tint = c.accent, modifier = Modifier.size(28.dp))
                            }
                            Text("Пока нет песен", style = MaterialTheme.typography.titleLarge, color = c.text)
                            Text(
                                "Плеер не загружает все файлы с телефона сам и не копирует их — сохраняются только пути. Форматы m4a/mp4 исключены.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textDim,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            FlatPrimaryButton("Добавить файлы", Icons.Rounded.Add, { onAddFiles(null) }, Modifier.fillMaxWidth())
                            FlatTonalButton("Выбрать папку (телефон / SD)", Icons.Rounded.CreateNewFolder, { onAddFolder(null) }, Modifier.fillMaxWidth())
                            FlatTonalButton("Импорт папок с устройства", Icons.Rounded.PhoneAndroid, onOpenDevicePicker, Modifier.fillMaxWidth())
                        }
                    }
                }
            } else {
                // ===================== МЕДИАТЕКА С ТРЕКАМИ =====================
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactAddBtn("Файлы", Icons.Rounded.Add, Modifier.weight(1f)) { onAddFiles(null) }
                        CompactAddBtn("Папка / SD", Icons.Rounded.CreateNewFolder, Modifier.weight(1f)) { onAddFolder(null) }
                        CompactAddBtn("Импорт", Icons.Rounded.PhoneAndroid, Modifier.weight(1f), onOpenDevicePicker)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlatSearchField(query, { query = it }, "Поиск по названию, исполнителю, папке…")
                }

                if (tab != LibTab.TRACKS && group == null) {
                    val groups = filtered.groupBy(groupKey).toList().sortedBy { it.first.lowercase() }
                    item { SectionHeader("${tab.title} · ${groups.size}") }
                    items(groups, key = { "g_" + it.first }) { (name, list) ->
                        FlatCard(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            onClick = { group = name },
                            padding = PaddingValues(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ArtThumb(list.first().uri, name, size = 48.dp)
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
                            liked = t.uri in favTracks,
                            onToggleLike = { vm.toggleFavoriteTrack(t.uri) },
                            onClick = { vm.play(shown, t) },
                            onAddToPlaylist = { addTrackToPl = t },
                            onRename = { renameTrack = t },
                            onInfo = { infoTrack = t },
                            onRemove = { vm.remove(t) },
                        )
                        HorizontalDivider(thickness = 1.dp, color = c.border.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }

    // ---- диалоги ----
    if (createPlDialog) {
        PlaylistNameDialog(
            title = "Новый плейлист",
            initial = "",
            confirmText = "Создать",
            onDismiss = { createPlDialog = false },
            onConfirm = { name ->
                val created = vm.createPlaylist(name)
                createPlDialog = false
                if (tab == LibTab.PLAYLISTS) openPlaylistId = created.id
            },
        )
    }

    renamePl?.let { pl ->
        PlaylistNameDialog(
            title = "Переименовать плейлист",
            initial = pl.name,
            confirmText = "Сохранить",
            onDismiss = { renamePl = null },
            onConfirm = { name -> vm.renamePlaylist(pl.id, name); renamePl = null },
        )
    }

    addTrackToPl?.let { tr ->
        AddToPlaylistSheet(
            vm = vm,
            track = tr,
            onDismiss = { addTrackToPl = null },
        )
    }

    pickExistingForPl?.let { pl ->
        PickTracksForPlaylistSheet(
            vm = vm,
            playlist = pl,
            onDismiss = { pickExistingForPl = null },
        )
    }

    renameTrack?.let { tr ->
        RenameTrackDialog(
            track = tr,
            onDismiss = { renameTrack = null },
            onSave = { title, artist ->
                vm.editTrackMeta(tr.uri, title, artist)
                renameTrack = null
            },
        )
    }

    infoTrack?.let { tr ->
        TrackInfoSheet(
            vm = vm,
            track = tr,
            onDismiss = { infoTrack = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.surfaceSolid,
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
private fun PlaylistCard(
    pl: Playlist,
    count: Int,
    coverUri: String?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalAura.current
    var menu by remember { mutableStateOf(false) }
    FlatCard(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        onClick = onOpen,
        padding = PaddingValues(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtThumb(coverUri, pl.name, size = 50.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(pl.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count треков", style = MaterialTheme.typography.bodySmall, color = c.textDim)
            }
            if (count > 0) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, "Играть плейлист", tint = c.accent)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, "Меню", tint = c.textDim)
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = c.surfaceSolid) {
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить плейлист", color = c.danger) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = LocalAura.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceSolid,
        title = { Text(title, color = c.text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Название плейлиста") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(vm: MainViewModel, track: Track, onDismiss: () -> Unit) {
    val c = LocalAura.current
    val playlists by vm.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Добавить в плейлист", style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(track.title, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Новый плейлист…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FlatPrimaryButton("Создать", Icons.Rounded.Add, {
                    vm.createPlaylist(newName, listOf(track.uri))
                    newName = ""
                })
            }

            if (playlists.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(playlists, key = { it.id }) { pl ->
                        val inside = track.uri in pl.uris
                        FlatCard(
                            Modifier.fillMaxWidth(),
                            color = if (inside) c.accentSoft else c.surface2,
                            onClick = { vm.toggleTrackInPlaylist(pl.id, track.uri) },
                            padding = PaddingValues(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (inside) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    null,
                                    tint = if (inside) c.accent else c.textDim,
                                )
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(pl.name, style = MaterialTheme.typography.titleSmall, color = c.text)
                                    Text("${pl.uris.size} треков", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickTracksForPlaylistSheet(vm: MainViewModel, playlist: Playlist, onDismiss: () -> Unit) {
    val c = LocalAura.current
    val allTracks by vm.tracks.collectAsState()
    var selected by remember { mutableStateOf(playlist.uris.toSet()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp)) {
            Text("Выбрать треки в «${playlist.name}»", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth().height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(allTracks, key = { it.uri }) { t ->
                    val checked = t.uri in selected
                    FlatCard(
                        Modifier.fillMaxWidth(),
                        color = if (checked) c.accentSoft else c.surface2,
                        onClick = { selected = if (checked) selected - t.uri else selected + t.uri },
                        padding = PaddingValues(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                null,
                                tint = if (checked) c.accent else c.textDim,
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlatPrimaryButton("Сохранить (${selected.size})", Icons.Rounded.Add, {
                val added = selected - playlist.uris.toSet()
                val removed = playlist.uris.toSet() - selected
                if (added.isNotEmpty()) vm.addTracksToPlaylist(playlist.id, added.toList())
                removed.forEach { vm.removeTrackFromPlaylist(playlist.id, it) }
                onDismiss()
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CompactAddBtn(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.controlRadius)
    Row(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(c.borderWidth, c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackRow(
    t: Track,
    playing: Boolean,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: () -> Unit,
    removeLabel: String = "Убрать из списка",
    onRename: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
) {
    val c = LocalAura.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(c.controlRadius))
            .background(if (playing) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtThumb(t.uri, t.album.ifBlank { t.title }, size = 46.dp)
            if (playing) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(if (c.style == UiStyle.RETRO) 4.dp else 10.dp)).background(Color.Black.copy(alpha = 0.5f)),
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
        if (liked) {
            Icon(Icons.Rounded.Favorite, null, tint = c.danger, modifier = Modifier.size(15.dp).padding(end = 2.dp))
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
            DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = c.surfaceSolid) {
                DropdownMenuItem(
                    text = { Text(if (liked) "Убрать из любимых" else "В любимые") },
                    leadingIcon = { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) c.danger else c.text) },
                    onClick = { menu = false; onToggleLike() },
                )
                DropdownMenuItem(
                    text = { Text("Добавить в плейлист") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    onClick = { menu = false; onAddToPlaylist() },
                )
                DropdownMenuItem(text = { Text("Играть следующим") }, onClick = { AudioEngine.playNext(t); menu = false })
                DropdownMenuItem(text = { Text("Добавить в очередь") }, onClick = { AudioEngine.addToQueue(t); menu = false })
                if (onRename != null) {
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onRename() },
                    )
                }
                if (onInfo != null) {
                    DropdownMenuItem(
                        text = { Text("Инфо о файле") },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        onClick = { menu = false; onInfo() },
                    )
                }
                HorizontalDivider(color = c.border)
                DropdownMenuItem(text = { Text(removeLabel, color = c.danger) }, onClick = { onRemove(); menu = false })
            }
        }
    }
}

// ===================== Встроенный проводник по папке SD / памяти (НЕ сканирует всю карту!) =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafFolderBrowserSheet(
    vm: MainViewModel,
    state: SafBrowserState,
) {
    val c = LocalAura.current
    var includeSubfolders by remember { mutableStateOf(false) }
    var selectedDocIds by remember(state.listing?.docId) { mutableStateOf(setOf<String>()) }
    val listing = state.listing
    val breadcrumb = state.stack.joinToString(" / ") { it.second }

    ModalBottomSheet(onDismissRequest = { vm.safClose() }, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.stack.size > 1) {
                    IconButton(onClick = { vm.safNavigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = c.text)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        listing?.folderName ?: "Выбор папки",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        breadcrumb.ifBlank { "Показаны только музыкальные форматы" },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.loading || listing == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (listing.folders.isNotEmpty()) {
                        item { SectionHeader("Папки (${listing.folders.size}) — нажмите, чтобы открыть") }
                        items(listing.folders, key = { it.docId }) { f ->
                            FlatCard(
                                Modifier.fillMaxWidth(),
                                color = c.surface2,
                                onClick = { vm.safNavigateInto(f) },
                                padding = PaddingValues(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Folder, null, tint = c.accent, modifier = Modifier.size(22.dp))
                                    Text(
                                        f.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = c.text,
                                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(Icons.Rounded.ChevronRight, null, tint = c.textDim)
                                }
                            }
                        }
                    }

                    if (listing.files.isNotEmpty()) {
                        item { SectionHeader("Музыка в этой папке (${listing.files.size}) — можно отметить отдельные файлы") }
                        items(listing.files, key = { it.docId }) { f ->
                            val checked = f.docId in selectedDocIds
                            FlatCard(
                                Modifier.fillMaxWidth(),
                                color = if (checked) c.accentSoft else c.surface2,
                                onClick = {
                                    selectedDocIds = if (checked) selectedDocIds - f.docId else selectedDocIds + f.docId
                                },
                                padding = PaddingValues(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                        null,
                                        tint = if (checked) c.accent else c.textDim,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        f.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.text,
                                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    if (listing.folders.isEmpty() && listing.files.isEmpty()) {
                        item {
                            FlatEmptyState(
                                icon = Icons.Rounded.FolderOpen,
                                title = "Нет музыки в папке",
                                text = "Показываются только музыкальные форматы (MP3, FLAC, WAV, OGG, OPUS, AAC). Картинки и m4a/mp4 скрыты.",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (listing.folders.isNotEmpty() && selectedDocIds.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { includeSubfolders = !includeSubfolders }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = includeSubfolders,
                            onCheckedChange = { includeSubfolders = it },
                            colors = CheckboxDefaults.colors(checkedColor = c.accent),
                        )
                        Text("Включая вложенные подпапки", style = MaterialTheme.typography.bodyMedium, color = c.text)
                    }
                }

                Spacer(Modifier.height(8.dp))
                val pickedEntries = listing.files.filter { it.docId in selectedDocIds }
                val canImport = pickedEntries.isNotEmpty() || listing.files.isNotEmpty() || (includeSubfolders && listing.folders.isNotEmpty())
                FlatPrimaryButton(
                    text = when {
                        pickedEntries.isNotEmpty() -> "Добавить выбранные файлы (${pickedEntries.size})"
                        includeSubfolders -> "Добавить «${listing.folderName}» с подпапками"
                        listing.files.isNotEmpty() -> "Добавить только эту папку (${listing.files.size})"
                        else -> "Откройте папку с музыкой выше"
                    },
                    icon = Icons.Rounded.Add,
                    onClick = {
                        if (pickedEntries.isNotEmpty()) vm.safImportSelected(pickedEntries)
                        else if (canImport) vm.safImportCurrent(recursive = includeSubfolders)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ===================== Встроенный выбор песен и папок (ТОЛЬКО музыка, без PNG/JPG/M4A/MP4!) =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFolderPickerSheet(
    vm: MainViewModel,
    initialMode: Int = 0, // 0 = Файлы (песни), 1 = Папки
    onOpenSdFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAura.current
    val folders by vm.pickerFolders.collectAsState()
    val allTracks by vm.pickerAllTracks.collectAsState()
    val loading by vm.pickerLoading.collectAsState()
    val targetPl by vm.pickerTargetPlaylistId.collectAsState()

    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var search by remember { mutableStateOf("") }
    var activeFolderKey by remember { mutableStateOf<String?>(null) }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }
    var selectedFolderKeys by remember { mutableStateOf(setOf<String>()) }

    val visibleTracks = remember(allTracks, folders, activeFolderKey, search) {
        val base = if (activeFolderKey == null) allTracks
        else folders.firstOrNull { it.key == activeFolderKey }?.tracks ?: emptyList()
        if (search.isBlank()) base
        else base.filter {
            it.title.contains(search, true) || it.artist.contains(search, true) || it.folder.contains(search, true)
        }
    }

    val chosenTracks = remember(mode, allTracks, folders, selectedUris, selectedFolderKeys) {
        if (mode == 0) allTracks.filter { it.uri in selectedUris }
        else folders.filter { it.key in selectedFolderKeys }.flatMap { it.tracks }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (mode == 0) "Добавить песни" else "Выбрать папки", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text("Только музыка: MP3, FLAC, WAV, OGG, OPUS, AAC", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                }
                IconButton(onClick = { onDismiss(); onOpenSdFolder() }) {
                    Icon(Icons.Rounded.CreateNewFolder, "Открыть папку SD", tint = c.accent)
                }
                IconButton(onClick = { vm.loadDeviceFolders(targetPl) }) {
                    Icon(Icons.Rounded.Refresh, "Обновить", tint = c.text)
                }
            }

            Spacer(Modifier.height(10.dp))
            FlatSegmentedControl(
                options = listOf("Файлы (${allTracks.size})", "Папки (${folders.size})"),
                selectedIndex = mode,
                onSelect = { mode = it },
            )
            Spacer(Modifier.height(10.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = c.accent)
                        Text("Ищу музыкальные файлы…", color = c.textDim, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (allTracks.isEmpty()) {
                FlatEmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Музыка не найдена",
                    text = "Подходящих аудиофайлов в памяти не найдено. Если музыка на SD-карте — нажмите кнопку ниже, чтобы открыть папку напрямую.",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FlatTonalButton("Открыть папку на SD-карте", Icons.Rounded.SdCard, { onDismiss(); onOpenSdFolder() })
                }
            } else if (mode == 0) {
                // ---- Режим выбора конкретных песен (БЕЗ картинок и m4a/mp4) ----
                FlatSearchField(search, { search = it }, "Поиск песни или папки…")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FlatChip("Все", activeFolderKey == null, onClick = { activeFolderKey = null })
                    folders.forEach { f ->
                        FlatChip(
                            text = "${f.name} (${f.tracks.size})",
                            selected = activeFolderKey == f.key,
                            onClick = { activeFolderKey = if (activeFolderKey == f.key) null else f.key },
                            icon = if (f.onSdCard) Icons.Rounded.SdCard else null,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Найдено: ${visibleTracks.size}", style = MaterialTheme.typography.bodySmall, color = c.textDim, modifier = Modifier.weight(1f))
                    val allVisibleSelected = visibleTracks.isNotEmpty() && visibleTracks.all { it.uri in selectedUris }
                    TextButton(onClick = {
                        selectedUris = if (allVisibleSelected) selectedUris - visibleTracks.map { it.uri }.toSet()
                        else selectedUris + visibleTracks.map { it.uri }
                    }) {
                        Text(if (allVisibleSelected) "Снять все" else "Выбрать все", color = c.accent)
                    }
                }

                LazyColumn(Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visibleTracks, key = { it.uri }) { t ->
                        val checked = t.uri in selectedUris
                        FlatCard(
                            Modifier.fillMaxWidth(),
                            color = if (checked) c.accentSoft else c.surface2,
                            onClick = { selectedUris = if (checked) selectedUris - t.uri else selectedUris + t.uri },
                            padding = PaddingValues(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    null,
                                    tint = if (checked) c.accent else c.textDim,
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(t.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(t.artist, t.folder.takeIf { it.isNotBlank() }, fmtTime(t.durationMs).takeIf { t.durationMs > 0 })
                                            .filterNotNull().joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (t.format.isNotBlank()) {
                                    Text(
                                        t.format,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(c.surfaceSolid)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ---- Режим выбора папок ----
                LazyColumn(Modifier.fillMaxWidth().height(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders, key = { it.key }) { f ->
                        val checked = f.key in selectedFolderKeys
                        FlatCard(
                            Modifier.fillMaxWidth(),
                            color = if (checked) c.accentSoft else c.surface2,
                            onClick = { selectedFolderKeys = if (checked) selectedFolderKeys - f.key else selectedFolderKeys + f.key },
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
            }

            if (allTracks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlatPrimaryButton(
                    text = if (chosenTracks.isEmpty()) "Отметьте нужные треки" else "Добавить выбранные (${chosenTracks.size})",
                    icon = Icons.Rounded.Add,
                    onClick = {
                        if (chosenTracks.isNotEmpty()) {
                            vm.importTracks(chosenTracks, targetPlaylistId = targetPl)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
