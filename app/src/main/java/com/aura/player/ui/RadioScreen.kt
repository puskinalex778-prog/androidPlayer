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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.data.Country
import com.aura.player.data.Quality
import com.aura.player.data.Station
import com.aura.player.playback.AudioEngine

@Composable
fun RadioScreen(vm: MainViewModel, onMenu: () -> Unit, bottomPadding: Int) {
    val c = LocalAura.current
    val country by vm.country.collectAsState()
    val favs by vm.favorites.collectAsState()
    val custom by vm.customStations.collectAsState()
    val savedOnline by vm.savedOnline.collectAsState()
    val online by vm.online.collectAsState()
    val loading by vm.onlineLoading.collectAsState()
    val onlineErr by vm.onlineError.collectAsState()
    val settings by vm.settings.collectAsState()
    val now by AudioEngine.now.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var addDialog by remember { mutableStateOf(false) }

    val stations = remember(country, favs, custom, savedOnline) { vm.stationsFor(country) }

    Column(Modifier.fillMaxSize()) {
        FlatTopBar(
            title = "Онлайн-радио",
            subtitle = "5 стран · выбор битрейта",
            onMenu = onMenu,
            actions = {
                IconButton(onClick = { addDialog = true }) {
                    Icon(Icons.Rounded.Add, "Своя станция", tint = c.text)
                }
            },
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = bottomPadding.dp),
        ) {
            item {
                FlatSearchField(query, { query = it }, "Найти любую станцию мира…", onSubmit = {
                    if (query.isNotBlank()) vm.loadOnline(null, query)
                })
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlatChip("Избранное", country == "FAV", onClick = { vm.setCountry("FAV") }, icon = Icons.Rounded.Star)
                    Country.entries.forEach { co ->
                        FlatChip(co.title, country == co.code, onClick = { vm.setCountry(co.code) }, prefix = co.flag)
                    }
                    FlatChip("Мои", country == "MY", onClick = { vm.setCountry("MY") }, icon = Icons.Rounded.Radio)
                }
                Spacer(Modifier.height(12.dp))
                FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Качество по умолчанию", style = MaterialTheme.typography.titleSmall, color = c.text)
                        FlatSegmentedControl(
                            options = Quality.entries.map { it.title },
                            selectedIndex = settings.quality,
                            onSelect = { vm.setQuality(it) },
                        )
                    }
                }
            }

            val title = when (country) {
                "FAV" -> "Избранное"
                "MY" -> "Мои станции"
                else -> Country.of(country)?.title ?: ""
            }
            item { SectionHeader("$title · ${stations.size}") }

            if (stations.isEmpty()) {
                item {
                    FlatEmptyState(
                        icon = if (country == "FAV") Icons.Rounded.StarBorder else Icons.Rounded.Radio,
                        title = if (country == "FAV") "Нет избранных" else "Нет своих станций",
                        text = if (country == "FAV") "Нажмите звёздочку у любой станции, чтобы сохранить её здесь."
                        else "Нажмите «+» в верхнем правом углу, чтобы добавить станцию по ссылке.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(stations, key = { "s_" + it.id }) { st ->
                StationCard(
                    st = st,
                    fav = st.id in favs,
                    current = now.station?.id == st.id,
                    currentUrl = now.stream?.url,
                    playing = now.isPlaying,
                    onFav = { vm.toggleFav(st) },
                    onRemove = if (st.custom) ({ vm.removeCustom(st) }) else null,
                )
            }

            item {
                SectionHeader("Онлайн-каталог") {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
                }
                if (online.isEmpty() && !loading) {
                    FlatCard(
                        Modifier.fillMaxWidth(),
                        onClick = { vm.loadOnline(country.takeIf { it.length == 2 }, query.ifBlank { null }) },
                        padding = PaddingValues(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Public, null, tint = c.accent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (country.length == 2) "Ещё станции: топ ${Country.of(country)?.title ?: ""}" else "Топ станций мира",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = c.text,
                                )
                                Text("Тысячи станций из radio-browser.info", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                            }
                        }
                    }
                }
                onlineErr?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }

            items(online, key = { "o_" + it.id }) { st ->
                StationCard(
                    st = st,
                    fav = st.id in favs,
                    current = now.station?.id == st.id,
                    currentUrl = now.stream?.url,
                    playing = now.isPlaying,
                    onFav = { vm.toggleFav(st) },
                    onRemove = null,
                )
            }
        }
    }

    if (addDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            containerColor = c.surfaceSolid,
            title = { Text("Своя станция", color = c.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("Ссылка на поток (MP3/AAC/HLS/M3U)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.addCustom(name, url); addDialog = false; vm.setCountry("MY") },
                    enabled = url.startsWith("http"),
                ) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StationCard(
    st: Station,
    fav: Boolean,
    current: Boolean,
    currentUrl: String?,
    playing: Boolean,
    onFav: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.cardRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(shape)
            .background(if (current) c.accentSoft else c.surface)
            .border(c.borderWidth, if (current) c.accent else c.border, shape)
            .clickable { if (current) AudioEngine.toggle() else AudioEngine.playStation(st) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                StationLogo(st, size = 48.dp)
                if (current && playing) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    st.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) c.accent else c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    st.genre.ifBlank { "Радио" },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = c.textDim)
                }
            }
            IconButton(onClick = onFav, modifier = Modifier.size(36.dp)) {
                Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Избранное", tint = if (fav) c.accent else c.textDim)
            }
        }
        // кнопки выбора битрейта
        Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            st.streams.forEach { s ->
                val sel = current && s.url == currentUrl
                val chipShape = RoundedCornerShape(8.dp)
                Text(
                    (if (s.bitrate > 0) "${s.bitrate} кбит/с" else "Авто") + if (s.codec.isNotBlank()) " · ${s.codec}" else "",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sel) c.onAccent else c.text,
                    modifier = Modifier
                        .clip(chipShape)
                        .background(if (sel) c.accent else c.surface2)
                        .border(1.dp, if (sel) c.accent else c.border, chipShape)
                        .clickable { AudioEngine.playStation(st, s) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
