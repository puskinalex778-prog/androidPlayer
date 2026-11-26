package com.aura.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.player.data.Library
import com.aura.player.data.RadioBrowser
import com.aura.player.data.RadioCatalog
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
    val theme: AuraTheme = AuraTheme.AURORA,
    val darkMode: Int = 0,
    val amoled: Boolean = false,
    val adaptive: Boolean = true,
    val cassette: Boolean = true,
    val quality: Int = 1,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AudioEngine.prefs
    private val ctx get() = getApplication<Application>()

    // ---------- медиатека ----------
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    val scanning = MutableStateFlow(false)
    val addProgress = MutableStateFlow<Int?>(null)
    val toast = MutableStateFlow<String?>(null)
    val sort = MutableStateFlow(prefs.sort)
    private var deviceTracks: List<Track> = emptyList()
    private var userTracks: List<Track> = Library.loadUser(ctx)
    private var restored = false

    // ---------- настройки ----------
    val settings = MutableStateFlow(
        UiSettings(
            theme = runCatching { AuraTheme.valueOf(prefs.theme) }.getOrDefault(AuraTheme.AURORA),
            darkMode = prefs.darkMode, amoled = prefs.amoled, adaptive = prefs.adaptiveColor,
            cassette = prefs.cassetteView, quality = prefs.quality,
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
    }

    // ===================== Медиатека =====================

    fun rescan() {
        if (scanning.value) return
        scanning.value = true
        viewModelScope.launch {
            deviceTracks = withContext(Dispatchers.IO) { Library.scanDevice(ctx) }
            publish()
            scanning.value = false
            if (!restored) {
                restored = true
                AudioEngine.restoreLast(tracks.value)
            }
        }
    }

    /** Пересканировать ранее добавленные папки (вдруг там появились новые файлы) */
    fun rescanFolders() {
        val trees = Library.loadTrees(ctx)
        if (trees.isEmpty()) { rescan(); return }
        viewModelScope.launch {
            addProgress.value = 0
            val found = withContext(Dispatchers.IO) {
                trees.flatMap { Library.scanTree(ctx, Uri.parse(it)) { n -> addProgress.value = n } }
            }
            mergeUser(found)
            addProgress.value = null
            rescan()
        }
    }

    private fun publish() {
        val hidden = prefs.hiddenUris
        val all = LinkedHashMap<String, Track>()
        deviceTracks.forEach { if (it.uri !in hidden) all[it.uri] = it }
        userTracks.forEach { if (it.uri !in hidden) all.putIfAbsent(it.uri, it) }
        tracks.value = sorted(all.values.toList(), sort.value)
    }

    private fun sorted(list: List<Track>, s: Int): List<Track> = when (s) {
        1 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
        2 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
        3 -> list.sortedByDescending { it.durationMs }
        4 -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.folder })
        else -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    fun setSort(s: Int) { sort.value = s; prefs.sort = s; publish() }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            addProgress.value = 0
            val list = withContext(Dispatchers.IO) {
                uris.mapIndexed { i, u ->
                    Library.persist(ctx, u)
                    addProgress.value = i + 1
                    Library.readTrack(ctx, u)
                }
            }
            mergeUser(list)
            addProgress.value = null
            toast.value = "Добавлено треков: ${list.size}"
        }
    }

    fun addFolder(tree: Uri) {
        viewModelScope.launch {
            Library.persist(ctx, tree)
            Library.saveTrees(ctx, Library.loadTrees(ctx) + tree.toString())
            addProgress.value = 0
            val list = withContext(Dispatchers.IO) { Library.scanTree(ctx, tree) { n -> addProgress.value = n } }
            mergeUser(list)
            addProgress.value = null
            toast.value = if (list.isEmpty()) "В папке не найдено аудиофайлов" else "Добавлено треков: ${list.size}"
        }
    }

    private fun mergeUser(list: List<Track>) {
        if (list.isEmpty()) return
        val map = LinkedHashMap<String, Track>()
        userTracks.forEach { map[it.uri] = it }
        list.forEach { map[it.uri] = it }
        userTracks = map.values.toList()
        // если трек был скрыт ранее — снова показываем
        prefs.hiddenUris = prefs.hiddenUris - list.map { it.uri }.toSet()
        Library.saveUser(ctx, userTracks)
        publish()
    }

    fun remove(t: Track) {
        if (t.userAdded) {
            userTracks = userTracks.filterNot { it.uri == t.uri }
            Library.saveUser(ctx, userTracks)
        } else {
            prefs.hiddenUris = prefs.hiddenUris + t.uri
        }
        publish()
    }

    fun restoreHidden() { prefs.hiddenUris = emptySet(); publish() }

    /** Файл, открытый из файлового менеджера («Открыть с помощью…») */
    fun openExternal(uri: Uri) {
        viewModelScope.launch {
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
        AudioEngine.playTracks(list, (list.indices).random())
    }

    // ===================== Настройки =====================

    fun setTheme(t: AuraTheme) { prefs.theme = t.name; settings.update { it.copy(theme = t) } }
    fun setDarkMode(m: Int) { prefs.darkMode = m; settings.update { it.copy(darkMode = m) } }
    fun setAmoled(v: Boolean) { prefs.amoled = v; settings.update { it.copy(amoled = v) } }
    fun setAdaptive(v: Boolean) { prefs.adaptiveColor = v; settings.update { it.copy(adaptive = v) } }
    fun setCassette(v: Boolean) { prefs.cassetteView = v; settings.update { it.copy(cassette = v) } }
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
        // станции из онлайн-каталога запоминаем целиком
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
            } catch (e: Exception) {
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
