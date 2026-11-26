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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.ui.graphics.Brush
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
fun RadioScreen(vm: MainViewModel, bottomPadding: Int) {
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomPadding.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Радио", style = MaterialTheme.typography.displaySmall, color = c.text)
                    Text("Популярные станции 5 стран + мировой каталог", style = MaterialTheme.typography.bodyMedium, color = c.textDim)
                }
                IconButton(onClick = { addDialog = true }) { Icon(Icons.Rounded.Add, "Своя станция", tint = c.text) }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            SearchField(query, { query = it }, "Найти любую станцию мира…", onSubmit = {
                if (query.isNotBlank()) vm.loadOnline(null, query)
            })
        }
        item {
            Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Избранное", country == "FAV", onClick = { vm.setCountry("FAV") }, leading = "⭐")
                Country.entries.forEach { co -> Pill(co.title, country == co.code, onClick = { vm.setCountry(co.code) }, leading = co.flag) }
                Pill("Мои", country == "MY", onClick = { vm.setCountry("MY") }, leading = "📡")
            }
        }
        item {
            Glass(Modifier.fillMaxWidth().padding(top = 14.dp), padding = PaddingValues(14.dp)) {
                Column {
                    Text("Качество по умолчанию", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text("Битрейт каждой станции можно выбрать отдельно — кнопки под названием", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Quality.entries.forEachIndexed { i, q ->
                            Pill(q.title, settings.quality == i, onClick = { vm.setQuality(i) }, leading = when (i) { 0 -> "🪫"; 1 -> "📶"; else -> "💎" })
                        }
                    }
                }
            }
        }

        val title = when (country) {
            "FAV" -> "Избранное"
            "MY" -> "Мои станции"
            else -> Country.of(country)?.let { "${it.flag} ${it.title}" } ?: ""
        }
        item { SectionTitle("$title · ${stations.size}") }
        if (stations.isEmpty()) {
            item {
                EmptyState(
                    if (country == "FAV") "⭐" else "📡",
                    if (country == "FAV") "Нет избранных" else "Нет своих станций",
                    if (country == "FAV") "Нажмите звёздочку у станции, чтобы она появилась здесь" else "Нажмите «+» вверху, чтобы добавить станцию по ссылке",
                    Modifier.fillMaxWidth(),
                )
            }
        }
        items(stations, key = { "s_" + it.id }) { st ->
            StationCard(st, st.id in favs, now.station?.id == st.id, now.stream?.url, now.isPlaying,
                onFav = { vm.toggleFav(st) },
                onRemove = if (st.custom) ({ vm.removeCustom(st) }) else null)
        }

        // ---- онлайн-каталог ----
        item {
            SectionTitle("🌐 Онлайн-каталог") {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent)
            }
            if (online.isEmpty() && !loading) {
                Glass(
                    Modifier.fillMaxWidth(),
                    onClick = { vm.loadOnline(country.takeIf { it.length == 2 }, query.ifBlank { null }) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Public, null, tint = c.accent)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (country.length == 2) "Ещё станции: топ ${Country.of(country)?.title ?: ""}" else "Топ станций мира",
                                style = MaterialTheme.typography.titleSmall, color = c.text,
                            )
                            Text("Тысячи станций radio-browser.info с реальными битрейтами", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                        }
                    }
                }
            }
            onlineErr?.let { Text(it, color = c.accent3, modifier = Modifier.padding(top = 8.dp)) }
        }
        items(online, key = { "o_" + it.id }) { st ->
            StationCard(st, st.id in favs, now.station?.id == st.id, now.stream?.url, now.isPlaying, onFav = { vm.toggleFav(st) }, onRemove = null)
        }
    }

    if (addDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Своя станция") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("Ссылка на поток (MP3/AAC/HLS/PLS/M3U)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.addCustom(name, url); addDialog = false; vm.setCountry("MY") }, enabled = url.startsWith("http")) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StationCard(
    st: Station, fav: Boolean, current: Boolean, currentUrl: String?, playing: Boolean,
    onFav: () -> Unit, onRemove: (() -> Unit)?,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape)
            .background(if (current) Brush.linearGradient(listOf(c.accent.copy(alpha = 0.28f), c.accent2.copy(alpha = 0.12f))) else Brush.linearGradient(listOf(c.glass, c.glass)))
            .border(1.dp, if (current) c.accent.copy(alpha = 0.6f) else c.glassBorder, shape)
            .clickable { if (current) AudioEngine.toggle() else AudioEngine.playStation(st) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                StationLogo(st)
                if (current && playing) Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Color.White)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(st.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.genre.ifBlank { "Радио" }, style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, "Удалить", tint = c.textDim) }
            IconButton(onClick = onFav) { Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Избранное", tint = if (fav) c.accent else c.textDim) }
        }
        // ---- выбор битрейта ----
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            st.streams.forEach { s ->
                val sel = current && s.url == currentUrl
                Text(
                    (if (s.bitrate > 0) "${s.bitrate} кбит/с" else "Авто") + (if (s.codec.isNotBlank()) " · ${s.codec}" else ""),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else c.text.copy(alpha = 0.8f),
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) c.accent else c.text.copy(alpha = 0.07f))
                        .clickable { AudioEngine.playStation(st, s) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
