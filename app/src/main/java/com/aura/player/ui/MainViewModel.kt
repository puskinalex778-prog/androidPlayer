package com.aura.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.player.data.CustomTheme
import com.aura.player.data.Library
import com.aura.player.data.Playlist
import com.aura.player.data.RadioBrowser
import com.aura.player.data.RadioCatalog
import com.aura.player.data.SafEntry
import com.aura.player.data.SafListing
import com.aura.player.data.Station
import com.aura.player.data.StreamOption
import com.aura.player.data.Track
import com.aura.player.playback.AudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiSettings(
    val theme: AuraTheme = AuraTheme.COBALT,
    val activeCustomThemeId: String = "",
    val style: UiStyle = UiStyle.FLAT,
    val visualizer: VisualizerType = VisualizerType.BARS,
    val darkMode: Int = 0,
    val amoled: Boolean = false,
    val adaptive: Boolean = false,
    val playerStage: Int = 0, // 0 = Кассета, 1 = Обложка, 2 = Визуал
    val quality: Int = 1,
)

data class DeviceFolder(
    val key: String,
    val name: String,
    val onSdCard: Boolean,
    val tracks: List<Track>,
)

data class SafBrowserState(
    val treeUri: Uri,
    val stack: List<Pair<String, String>>, // (docId, folderName)
    val listing: SafListing?,
    val loading: Boolean = false,
    val targetPlaylistId: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AudioEngine.prefs
    private val ctx get() = getApplication<Application>()

    // ---------- медиатека (только добавленные пользователем треки; файлы не копируются) ----------
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(Library.loadPlaylists(ctx))
    val favoriteTracks = MutableStateFlow(prefs.favoriteTracks)
    val customThemes = MutableStateFlow(prefs.customThemes)
    val previewCustomTheme = MutableStateFlow<CustomTheme?>(null)
    val addProgress = MutableStateFlow<Int?>(null)
    val toast = MutableStateFlow<String?>(null)
    val sort = MutableStateFlow(prefs.sort)
    private var userTracks: List<Track> = Library.loadUser(ctx)

    // выборочный импорт песен и папок с телефона (MediaStore — только музыка, без png/jpg/m4a/mp4!)
    val pickerFolders = MutableStateFlow<List<DeviceFolder>>(emptyList())
    val pickerAllTracks = MutableStateFlow<List<Track>>(emptyList())
    val pickerLoading = MutableStateFlow(false)
    val pickerTargetPlaylistId = MutableStateFlow<String?>(null)

    // встроенный проводник по выбранному дереву SD / памяти (не сканирует всю карту!)
    val safBrowser = MutableStateFlow<SafBrowserState?>(null)

    // ---------- настройки ----------
    val settings = MutableStateFlow(
        UiSettings(
            theme = AuraTheme.fromName(prefs.theme),
            activeCustomThemeId = prefs.activeCustomThemeId,
            style = UiStyle.fromName(prefs.uiStyle),
            visualizer = VisualizerType.fromName(prefs.visualizer),
            darkMode = prefs.darkMode,
            amoled = prefs.amoled,
            adaptive = prefs.adaptiveColor,
            playerStage = prefs.playerStage.coerceIn(0, 2),
            quality = prefs.quality,
        )
    )

    // ---------- радио ----------
    val favorites = MutableStateFlow(prefs.favorites)
    val customStations = MutableStateFlow(prefs.customStations)
    val savedOnline = MutableStateFlow(prefs.savedOnline)
    val country = MutableStateFlow(prefs.lastCountry)
    val online = MutableStateFlow<List<Station>>(emptyList())
    val onlineLoading = MutableStateFlow(false)
    val onlineError = MutableStateFlow<String?>(null)
    private var onlineJob: Job? = null

    init {
        publish()
        if (tracks.value.isNotEmpty()) {
            AudioEngine.restoreLast(tracks.value)
        }
    }

    // ===================== Медиатека =====================

    fun loadDeviceFolders(targetPlaylistId: String? = null) {
        pickerTargetPlaylistId.value = targetPlaylistId
        if (pickerLoading.value) return
        pickerLoading.value = true
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Library.scanDevice(ctx) }
            val sortedAll = found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            val grouped = sortedAll
                .groupBy { (if (it.onSdCard) "sd:" else "in:") + it.folder.ifBlank { "Без папки" } }
                .map { (k, list) ->
                    val first = list.first()
                    DeviceFolder(
                        key = k,
                        name = first.folder.ifBlank { if (first.onSdCard) "Карта памяти" else "Внутренняя память" },
                        onSdCard = first.onSdCard,
                        tracks = list,
                    )
                }
                .sortedWith(compareByDescending<DeviceFolder> { it.onSdCard }.thenByDescending { it.tracks.size })
            pickerAllTracks.value = sortedAll
            pickerFolders.value = grouped
            pickerLoading.value = false
        }
    }

    fun importTracks(list: List<Track>, targetPlaylistId: String? = null) {
        if (list.isEmpty()) return
        val marked = list.map { it.copy(userAdded = true) }
        val before = userTracks.size
        mergeUser(marked)
        if (targetPlaylistId != null) {
            addTracksToPlaylist(targetPlaylistId, marked.map { it.uri })
        }
        val diff = (userTracks.size - before).coerceAtLeast(0)
        toast.value = when {
            targetPlaylistId != null -> "Добавлено в плейлист: ${marked.size}"
            diff > 0 -> "Добавлено треков: $diff"
            else -> "Эти треки уже есть в списке"
        }
    }

    private fun publish() {
        tracks.value = sorted(userTracks, sort.value)
    }

    private fun sorted(list: List<Track>, s: Int): List<Track> = when (s) {
        1 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
        2 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
        3 -> list.sortedByDescending { it.durationMs }
        4 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.folder })
        else -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    fun setSort(s: Int) { sort.value = s; prefs.sort = s; publish() }

    fun addFiles(uris: List<Uri>, targetPlaylistId: String? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            addProgress.value = 0
            var skipped = 0
            val list = withContext(Dispatchers.IO) {
                val res = ArrayList<Track>()
                uris.forEach { u ->
                    if (!Library.isAllowedUri(ctx, u)) {
                        skipped++
                        return@forEach
                    }
                    Library.persist(ctx, u)
                    res += Library.readTrack(ctx, u)
                    addProgress.value = res.size
                }
                res
            }
            val before = userTracks.size
            mergeUser(list)
            if (targetPlaylistId != null && list.isNotEmpty()) {
                addTracksToPlaylist(targetPlaylistId, list.map { it.uri })
            }
            addProgress.value = null
            val diff = (userTracks.size - before).coerceAtLeast(0)
            toast.value = when {
                list.isEmpty() && skipped > 0 -> "Пропущено неподдерживаемых файлов (m4a/mp4): $skipped"
                targetPlaylistId != null -> "Добавлено в плейлист: ${list.size}" + if (skipped > 0) " (пропущено m4a/mp4: $skipped)" else ""
                diff > 0 -> "Добавлено треков: $diff" + if (skipped > 0) " (пропущено m4a/mp4: $skipped)" else ""
                else -> "Файлы уже есть в списке"
            }
        }
    }

    // ===================== Безопасный выбор папки (в т.ч. SD — не сканирует всю карту!) =====================

    fun openSafTree(treeUri: Uri, targetPlaylistId: String? = null) {
        Library.persist(ctx, treeUri)
        viewModelScope.launch {
            safBrowser.value = SafBrowserState(treeUri, emptyList(), null, loading = true, targetPlaylistId = targetPlaylistId)
            val listing = withContext(Dispatchers.IO) { Library.listSafDirectory(ctx, treeUri) }
            // Если внутри выбранной папки нет подпапок и есть музыка — добавляем сразу только эту папку
            if (listing.folders.isEmpty() && listing.files.isNotEmpty()) {
                safBrowser.value = null
                importSafFiles(treeUri, listing.docId, listing.folderName, recursive = false, targetPlaylistId = targetPlaylistId)
            } else {
                // Иначе открываем быстрый проводник, чтобы пользователь выбрал конкретную нужную папку!
                safBrowser.value = SafBrowserState(
                    treeUri = treeUri,
                    stack = listOf(listing.docId to listing.folderName),
                    listing = listing,
                    loading = false,
                    targetPlaylistId = targetPlaylistId,
                )
            }
        }
    }

    fun safNavigateInto(folder: SafEntry) {
        val st = safBrowser.value ?: return
        viewModelScope.launch {
            safBrowser.value = st.copy(loading = true)
            val listing = withContext(Dispatchers.IO) {
                Library.listSafDirectory(ctx, st.treeUri, folder.docId, folder.name)
            }
            safBrowser.value = st.copy(
                stack = st.stack + (folder.docId to folder.name),
                listing = listing,
                loading = false,
            )
        }
    }

    fun safNavigateUp() {
        val st = safBrowser.value ?: return
        if (st.stack.size <= 1) {
            safBrowser.value = null
            return
        }
        val newStack = st.stack.dropLast(1)
        val (docId, name) = newStack.last()
        viewModelScope.launch {
            safBrowser.value = st.copy(loading = true)
            val listing = withContext(Dispatchers.IO) {
                Library.listSafDirectory(ctx, st.treeUri, docId, name)
            }
            safBrowser.value = st.copy(stack = newStack, listing = listing, loading = false)
        }
    }

    fun safClose() { safBrowser.value = null }

    fun safImportCurrent(recursive: Boolean) {
        val st = safBrowser.value ?: return
        val cur = st.listing ?: return
        safBrowser.value = null
        importSafFiles(st.treeUri, cur.docId, cur.folderName, recursive = recursive, targetPlaylistId = st.targetPlaylistId)
    }

    fun safImportSelected(entries: List<SafEntry>) {
        val st = safBrowser.value ?: return
        val cur = st.listing ?: return
        if (entries.isEmpty()) return
        val plId = st.targetPlaylistId
        safBrowser.value = null
        viewModelScope.launch {
            addProgress.value = 0
            val list = withContext(Dispatchers.IO) {
                entries.mapIndexed { i, e ->
                    addProgress.value = i + 1
                    Library.readTrack(ctx, e.uri, userAdded = true, fallbackFolder = cur.folderName)
                }
            }
            val before = userTracks.size
            mergeUser(list)
            if (plId != null && list.isNotEmpty()) addTracksToPlaylist(plId, list.map { it.uri })
            addProgress.value = null
            val diff = (userTracks.size - before).coerceAtLeast(0)
            toast.value = when {
                plId != null -> "Добавлено в плейлист: ${list.size}"
                diff > 0 -> "Добавлено треков: $diff"
                else -> "Выбранные треки уже есть в списке"
            }
        }
    }

    private fun importSafFiles(
        treeUri: Uri,
        docId: String,
        folderName: String,
        recursive: Boolean,
        targetPlaylistId: String?,
    ) {
        viewModelScope.launch {
            addProgress.value = 0
            val list = withContext(Dispatchers.IO) {
                Library.scanSafFolder(ctx, treeUri, docId, folderName, recursive = recursive) { n -> addProgress.value = n }
            }
            val before = userTracks.size
            mergeUser(list)
            if (targetPlaylistId != null && list.isNotEmpty()) {
                addTracksToPlaylist(targetPlaylistId, list.map { it.uri })
            }
            addProgress.value = null
            val diff = (userTracks.size - before).coerceAtLeast(0)
            toast.value = when {
                list.isEmpty() -> "В папке «$folderName» не найдено музыкальных файлов"
                targetPlaylistId != null -> "Добавлено в плейлист из «$folderName»: ${list.size}"
                diff > 0 -> "Добавлено из «$folderName»: $diff"
                else -> "Треки из «$folderName» уже есть в списке"
            }
        }
    }

    private fun mergeUser(list: List<Track>) {
        if (list.isEmpty()) return
        val map = LinkedHashMap<String, Track>()
        userTracks.forEach { map[it.uri] = it }
        list.forEach { map[it.uri] = it.copy(userAdded = true) }
        userTracks = map.values.toList()
        Library.saveUser(ctx, userTracks)
        publish()
    }

    fun remove(t: Track) {
        userTracks = userTracks.filterNot { it.uri == t.uri }
        Library.saveUser(ctx, userTracks)
        // чистим ссылку из плейлистов
        val updatedPl = playlists.value.map { p -> p.copy(uris = p.uris.filterNot { it == t.uri }) }
        playlists.value = updatedPl
        Library.savePlaylists(ctx, updatedPl)
        publish()
    }

    fun clearLibrary() {
        userTracks = emptyList()
        Library.saveUser(ctx, emptyList())
        if (!AudioEngine.now.value.isRadio) AudioEngine.stop()
        prefs.lastUri = ""
        publish()
        toast.value = "Список музыки очищен"
    }

    fun openExternal(uri: Uri) {
        viewModelScope.launch {
            if (!Library.isAllowedUri(ctx, uri)) {
                toast.value = "Формат m4a/mp4 отключён в плеере"
                return@launch
            }
            Library.persist(ctx, uri)
            val t = withContext(Dispatchers.IO) { Library.readTrack(ctx, uri) }
            val persisted = ctx.contentResolver.persistedUriPermissions.any { it.uri == uri }
            if (persisted || uri.scheme == "file") mergeUser(listOf(t))
            AudioEngine.playTracks(listOf(t), 0)
        }
    }

    fun play(list: List<Track>, t: Track) {
        val i = list.indexOfFirst { it.uri == t.uri }.coerceAtLeast(0)
        AudioEngine.playTracks(list, i)
    }

    fun shuffle(list: List<Track>) {
        if (list.isEmpty()) return
        AudioEngine.player.shuffleModeEnabled = true
        AudioEngine.playTracks(list, list.indices.random())
    }

    // ===================== Плейлисты =====================

    fun createPlaylist(name: String, initialUris: List<String> = emptyList()): Playlist {
        val clean = name.trim().ifBlank { "Плейлист ${playlists.value.size + 1}" }
        val p = Playlist("pl_" + System.currentTimeMillis(), clean, initialUris.distinct())
        val next = playlists.value + p
        playlists.value = next
        Library.savePlaylists(ctx, next)
        toast.value = "Создан плейлист «$clean»"
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val next = playlists.value.map { if (it.id == id) it.copy(name = clean) else it }
        playlists.value = next
        Library.savePlaylists(ctx, next)
    }

    fun deletePlaylist(id: String) {
        val next = playlists.value.filterNot { it.id == id }
        playlists.value = next
        Library.savePlaylists(ctx, next)
        toast.value = "Плейлист удалён"
    }

    fun toggleTrackInPlaylist(playlistId: String, uri: String) {
        val next = playlists.value.map { p ->
            if (p.id != playlistId) p
            else if (uri in p.uris) p.copy(uris = p.uris.filterNot { it == uri })
            else p.copy(uris = p.uris + uri)
        }
        playlists.value = next
        Library.savePlaylists(ctx, next)
    }

    fun addTracksToPlaylist(playlistId: String, uris: List<String>) {
        if (uris.isEmpty()) return
        val next = playlists.value.map { p ->
            if (p.id != playlistId) p else p.copy(uris = (p.uris + uris).distinct())
        }
        playlists.value = next
        Library.savePlaylists(ctx, next)
    }

    fun removeTrackFromPlaylist(playlistId: String, uri: String) {
        val next = playlists.value.map { p ->
            if (p.id != playlistId) p else p.copy(uris = p.uris.filterNot { it == uri })
        }
        playlists.value = next
        Library.savePlaylists(ctx, next)
    }

    fun tracksOf(p: Playlist): List<Track> {
        val map = userTracks.associateBy { it.uri }
        return p.uris.mapNotNull { map[it] }
    }

    // ===================== Избранные треки =====================

    fun toggleFavoriteTrack(uri: String) {
        val next = favoriteTracks.value.toMutableSet()
        if (!next.add(uri)) next.remove(uri)
        favoriteTracks.value = next
        prefs.favoriteTracks = next
    }

    // ===================== Настройки и пользовательские темы =====================

    fun setTheme(t: AuraTheme) {
        prefs.theme = t.name
        prefs.activeCustomThemeId = ""
        prefs.adaptiveColor = false
        previewCustomTheme.value = null
        settings.update { it.copy(theme = t, activeCustomThemeId = "", adaptive = false) }
    }

    fun selectCustomTheme(id: String) {
        prefs.activeCustomThemeId = id
        prefs.adaptiveColor = false
        previewCustomTheme.value = null
        settings.update { it.copy(activeCustomThemeId = id, adaptive = false) }
    }

    fun saveCustomTheme(ct: CustomTheme) {
        val clean = ct.copy(name = ct.name.trim().ifBlank { "Моя тема ${customThemes.value.size + 1}" })
        val list = customThemes.value.filterNot { it.id == clean.id } + clean
        customThemes.value = list
        prefs.customThemes = list
        selectCustomTheme(clean.id)
        toast.value = "Тема «${clean.name}» сохранена"
    }

    fun deleteCustomTheme(id: String) {
        val list = customThemes.value.filterNot { it.id == id }
        customThemes.value = list
        prefs.customThemes = list
        if (settings.value.activeCustomThemeId == id) {
            prefs.activeCustomThemeId = ""
            settings.update { it.copy(activeCustomThemeId = "") }
        }
    }

    fun setUiStyle(s: UiStyle) { prefs.uiStyle = s.name; settings.update { it.copy(style = s) } }
    fun setVisualizer(v: VisualizerType) { prefs.visualizer = v.name; settings.update { it.copy(visualizer = v) } }
    fun cycleVisualizer() { setVisualizer(settings.value.visualizer.next()) }
    fun setDarkMode(m: Int) { prefs.darkMode = m; settings.update { it.copy(darkMode = m) } }
    fun setAmoled(v: Boolean) { prefs.amoled = v; settings.update { it.copy(amoled = v) } }
    fun setAdaptive(v: Boolean) {
        prefs.adaptiveColor = v
        if (v) {
            prefs.activeCustomThemeId = ""
            settings.update { it.copy(adaptive = true, activeCustomThemeId = "") }
        } else {
            settings.update { it.copy(adaptive = false) }
        }
    }
    fun setPlayerStage(stage: Int) {
        val s = stage.coerceIn(0, 2)
        prefs.playerStage = s
        settings.update { it.copy(playerStage = s) }
    }
    fun setQuality(q: Int) { prefs.quality = q; settings.update { it.copy(quality = q) } }

    // ===================== Радио =====================

    fun stationsFor(code: String): List<Station> = when (code) {
        "FAV" -> allKnown().filter { it.id in favorites.value }
        "MY" -> customStations.value
        else -> RadioCatalog.defaults.filter { it.country == code }
    }

    private fun allKnown(): List<Station> =
        (RadioCatalog.defaults + customStations.value + savedOnline.value + online.value).distinctBy { it.id }

    fun setCountry(c: String) {
        country.value = c
        prefs.lastCountry = c
        online.value = emptyList()
        onlineError.value = null
    }

    fun toggleFav(st: Station) {
        val f = favorites.value.toMutableSet()
        if (!f.add(st.id)) f.remove(st.id)
        favorites.value = f
        prefs.favorites = f
        if (st.id.startsWith("rb_")) {
            val list = savedOnline.value.filterNot { it.id == st.id }.let { if (st.id in f) it + st else it }
            savedOnline.value = list
            prefs.savedOnline = list
        }
    }

    fun loadOnline(code: String?, query: String?) {
        onlineJob?.cancel()
        onlineLoading.value = true
        onlineError.value = null
        onlineJob = viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    RadioBrowser.search(code?.takeIf { it.length == 2 }, query)
                }
                val known = RadioCatalog.defaults.map { it.name.lowercase() }.toSet()
                online.value = list.filter { it.name.lowercase() !in known }
                if (list.isEmpty()) onlineError.value = "Ничего не найдено"
            } catch (_: Exception) {
                onlineError.value = "Онлайн-каталог недоступен. Проверьте интернет."
            }
            onlineLoading.value = false
        }
    }

    fun addCustom(name: String, url: String) {
        val u = url.trim()
        if (u.isBlank()) return
        val st = Station("my_" + System.currentTimeMillis(), name.ifBlank { "Моя станция" }, "", "Своя станция",
            listOf(StreamOption(0, u, if (u.contains("m3u8")) "HLS" else "")), custom = true)
        val list = customStations.value + st
        customStations.value = list
        prefs.customStations = list
    }

    fun removeCustom(st: Station) {
        val list = customStations.value.filterNot { it.id == st.id }
        customStations.value = list
        prefs.customStations = list
    }
}
