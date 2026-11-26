@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.aura.player.playback

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.aura.player.data.ArtLoader
import com.aura.player.data.Prefs
import com.aura.player.data.Quality
import com.aura.player.data.Station
import com.aura.player.data.StreamOption
import com.aura.player.data.StreamResolver
import com.aura.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val track: Track? = null,
    val isRadio: Boolean = false,
    val station: Station? = null,
    val stream: StreamOption? = null,
    val streamTitle: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val hasMedia: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val count: Int = 0,
)

data class SoundState(
    val voice: VoiceEffect = VoiceEffect.NORMAL,
    val pitchSemis: Float = 0f,
    val speed: Float = 1f,
    val eqOn: Boolean = true,
    val gains: List<Float> = List(10) { 0f },
    val preset: String = "Ровно",
    val bass: Float = 0f,
    val width: Float = 1f,
    val preamp: Float = 0f,
)

/** Единый аудио-движок: плеер, очередь, радио, эффекты голоса, эквалайзер, таймер сна. */
object AudioEngine {

    lateinit var player: ExoPlayer
        private set
    val fx = FxProcessor()

    private lateinit var app: Context
    lateinit var prefs: Prefs
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val now = MutableStateFlow(NowPlaying())
    val error = MutableStateFlow<String?>(null)
    val sound = MutableStateFlow(SoundState())
    val art = MutableStateFlow<Bitmap?>(null)
    val accent = MutableStateFlow<Int?>(null)
    val sleepAt = MutableStateFlow(0L)

    private var queueTracks: List<Track> = emptyList()
    private var currentStation: Station? = null
    private var currentStream: StreamOption? = null
    private val triedStreams = HashSet<String>()
    private var lastId: String? = null
    private var sleepJob: Job? = null
    private var artJob: Job? = null

    fun init(context: Context) {
        if (::player.isInitialized) return
        app = context.applicationContext
        prefs = Prefs(app)

        val renderers = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(fx))
                .build()
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AuraPlayer/1.0")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        player = ExoPlayer.Builder(app, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // аудиофокус: пауза при звонке и т.п.
            )
            .setHandleAudioBecomingNoisy(true) // пауза при отключении наушников
            .setWakeMode(C.WAKE_MODE_NETWORK)   // не засыпать с выключенным экраном
            .build()
        player.addListener(listener)

        restoreSound()
        startPositionSaver()
    }

    // ======================= Локальные треки =======================

    private fun itemOf(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id)
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album.ifBlank { null })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()

    fun playTracks(list: List<Track>, index: Int, startPos: Long = 0L, autoplay: Boolean = true) {
        if (list.isEmpty()) return
        currentStation = null
        currentStream = null
        queueTracks = list
        player.setMediaItems(list.map { itemOf(it) }, index.coerceIn(0, list.lastIndex), startPos)
        player.prepare()
        if (autoplay) player.play()
        refresh()
    }

    fun playNext(t: Track) {
        if (now.value.isRadio || queueTracks.isEmpty()) { playTracks(listOf(t), 0); return }
        val at = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        queueTracks = queueTracks.toMutableList().also { it.add(at, t) }
        player.addMediaItem(at, itemOf(t))
        refresh()
    }

    fun addToQueue(t: Track) {
        if (now.value.isRadio || queueTracks.isEmpty()) { playTracks(listOf(t), 0); return }
        queueTracks = queueTracks + t
        player.addMediaItem(itemOf(t))
        refresh()
    }

    fun queue(): List<Track> = queueTracks

    fun jumpTo(index: Int) {
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.play()
        }
    }

    // ======================= Радио =======================

    fun pickStream(st: Station): StreamOption {
        val saved = prefs.stationBitrate(st.id)
        st.streams.firstOrNull { it.bitrate == saved }?.let { return it }
        val sorted = st.streams.sortedBy { it.bitrate }
        return when (Quality.entries.getOrElse(prefs.quality) { Quality.MEDIUM }) {
            Quality.LOW -> sorted.first()
            Quality.HIGH -> sorted.last()
            Quality.MEDIUM -> sorted.minByOrNull { abs(it.bitrate - 128) } ?: sorted.first()
        }
    }

    fun playStation(st: Station, stream: StreamOption? = null) {
        val s = stream ?: pickStream(st)
        if (stream != null) prefs.setStationBitrate(st.id, stream.bitrate)
        if (currentStation?.id != st.id) triedStreams.clear()
        triedStreams += s.url
        currentStation = st
        currentStream = s
        queueTracks = emptyList()
        error.value = null
        now.update { it.copy(isRadio = true, station = st, stream = s, title = st.name, artist = st.genre, buffering = true, streamTitle = null, track = null) }
        art.value = null
        accent.value = null
        applyRadioQuality()
        scope.launch {
            val url = try {
                withContext(Dispatchers.IO) { StreamResolver.resolve(s.url) }
            } catch (e: Exception) {
                error.value = "Станция «${st.name}» сейчас недоступна"
                now.update { it.copy(buffering = false) }
                return@launch
            }
            if (currentStation?.id != st.id) return@launch
            val b = MediaItem.Builder()
                .setMediaId(st.radioId)
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(st.name)
                        .setArtist(st.genre.ifBlank { "Онлайн-радио" })
                        .setStation(st.name)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build()
                )
            if (url.contains(".m3u8", true) || s.codec == "HLS") b.setMimeType(MimeTypes.APPLICATION_M3U8)
            player.setMediaItem(b.build())
            player.prepare()
            player.play()
        }
    }

    private fun applyRadioQuality() {
        val max = when (Quality.entries.getOrElse(prefs.quality) { Quality.MEDIUM }) {
            Quality.LOW -> 72_000
            Quality.MEDIUM -> 160_000
            Quality.HIGH -> Int.MAX_VALUE
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxAudioBitrate(max)
            .build()
    }

    // ======================= Управление =======================

    fun toggle() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(player.currentMediaItemIndex, 0L)
            player.play()
        }
    }

    fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() else if (player.mediaItemCount > 0) player.seekTo(0, 0L) }
    fun prev() { if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) player.seekTo(0L) else player.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun seekBy(ms: Long) = player.seekTo((player.currentPosition + ms).coerceAtLeast(0L))
    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        queueTracks = emptyList()
        currentStation = null
        refresh()
    }

    // ======================= Таймер сна =======================

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { sleepAt.value = 0L; return }
        val at = System.currentTimeMillis() + minutes * 60_000L
        sleepAt.value = at
        sleepJob = scope.launch {
            while (isActive && System.currentTimeMillis() < at) delay(1000)
            // плавное затухание
            for (i in 10 downTo 1) { player.volume = i / 10f; delay(300) }
            player.pause()
            player.volume = 1f
            sleepAt.value = 0L
        }
    }

    // ======================= События плеера =======================

    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) = refresh()

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val e = metadata.get(i)
                if (e is IcyInfo && !e.title.isNullOrBlank()) {
                    now.update { it.copy(streamTitle = e.title) }
                } else if (e is TextInformationFrame && now.value.isRadio && (e.id == "TIT2" || e.id == "TPE1")) {
                    val v = e.values.firstOrNull()
                    if (!v.isNullOrBlank()) now.update { it.copy(streamTitle = v) }
                }
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            val st = currentStation
            if (now.value.isRadio && st != null) {
                // пробуем другой битрейт этой же станции
                val alt = st.streams.firstOrNull { it.url !in triedStreams }
                if (alt != null) {
                    error.value = "Поток ${currentStream?.label ?: ""} не отвечает — пробую ${alt.label} кбит/с"
                    playStation(st, alt)
                    return
                }
                error.value = "Не удалось подключиться к «${st.name}». Попробуйте позже или найдите станцию в онлайн-каталоге."
            } else {
                error.value = "Не удалось воспроизвести файл (${e.errorCodeName})"
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private fun refresh() {
        val p = player
        val item = p.currentMediaItem
        val md = p.mediaMetadata
        val id = item?.mediaId
        val isRadio = id?.startsWith("radio:") == true
        val changed = id != lastId
        lastId = id
        val idx = p.currentMediaItemIndex
        val track = if (!isRadio) queueTracks.getOrNull(idx)?.takeIf { it.id == id } ?: queueTracks.firstOrNull { it.id == id } else null
        now.update {
            it.copy(
                title = track?.title ?: md.title?.toString() ?: item?.mediaMetadata?.title?.toString() ?: "",
                artist = track?.artist ?: md.artist?.toString() ?: "",
                album = track?.album ?: md.albumTitle?.toString() ?: "",
                track = track,
                isRadio = isRadio,
                station = if (isRadio) currentStation else null,
                stream = if (isRadio) currentStream else null,
                streamTitle = if (isRadio && !changed) it.streamTitle else null,
                isPlaying = p.isPlaying,
                buffering = p.playbackState == Player.STATE_BUFFERING,
                hasMedia = item != null,
                shuffle = p.shuffleModeEnabled,
                repeat = p.repeatMode,
                index = idx,
                count = p.mediaItemCount,
            )
        }
        if (changed) {
            applyPlayback()
            if (!isRadio) loadArt(track?.uri)
        }
        if (p.isPlaying && error.value != null) error.value = null
    }

    private fun loadArt(uri: String?) {
        artJob?.cancel()
        if (uri == null) { art.value = null; accent.value = null; return }
        artJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { ArtLoader.load(app, uri, 720) }
            art.value = bmp
            accent.value = bmp?.let { withContext(Dispatchers.Default) { ArtLoader.accentOf(it) } }
        }
    }

    // ======================= Голос / скорость / эквалайзер =======================

    fun setVoice(v: VoiceEffect) {
        sound.update { it.copy(voice = v) }
        prefs.voice = v.name
        applyPlayback()
    }

    fun setPitch(semis: Float) {
        sound.update { it.copy(pitchSemis = semis) }
        prefs.pitchSemis = semis
        applyPlayback()
    }

    fun setSpeed(s: Float) {
        sound.update { it.copy(speed = s) }
        prefs.speed = s
        applyPlayback()
    }

    private fun applyPlayback() {
        val st = sound.value
        fx.mode = st.voice.mode
        val pitch = (st.voice.pitch * 2.0.pow(st.pitchSemis / 12.0).toFloat()).coerceIn(0.25f, 4f)
        val speed = if (now.value.isRadio) 1f else st.speed.coerceIn(0.25f, 3f)
        runCatching { player.playbackParameters = PlaybackParameters(speed, pitch) }
    }

    fun setEqEnabled(on: Boolean) { sound.update { it.copy(eqOn = on) }; prefs.eqEnabled = on; pushEq() }

    fun setBand(i: Int, db: Float) {
        sound.update { s -> s.copy(gains = s.gains.toMutableList().also { if (i in it.indices) it[i] = db }, preset = "Свой") }
        prefs.eqPreset = "Свой"
        pushEq()
    }

    fun applyPreset(p: EqPreset) {
        sound.update { it.copy(gains = p.gains.toList(), preset = p.name, eqOn = true) }
        prefs.eqPreset = p.name
        prefs.eqEnabled = true
        pushEq()
    }

    fun setBass(v: Float) { sound.update { it.copy(bass = v) }; prefs.bass = v; pushEq() }
    fun setPreamp(v: Float) { sound.update { it.copy(preamp = v) }; prefs.preamp = v; pushEq() }
    fun setWidth(v: Float) { sound.update { it.copy(width = v) }; prefs.width = v; fx.width = v }

    fun resetSound() {
        setVoice(VoiceEffect.NORMAL); setPitch(0f); setSpeed(1f)
        applyPreset(EqPresets.all.first()); setBass(0f); setPreamp(0f); setWidth(1f)
    }

    private fun pushEq() {
        val s = sound.value
        prefs.eqGains = s.gains.joinToString(",")
        fx.setEq(s.eqOn, s.gains.toFloatArray(), s.bass, s.preamp)
    }

    private fun restoreSound() {
        val v = runCatching { VoiceEffect.valueOf(prefs.voice) }.getOrDefault(VoiceEffect.NORMAL)
        val gains = prefs.eqGains.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 10 } ?: List(10) { 0f }
        sound.value = SoundState(
            voice = v, pitchSemis = prefs.pitchSemis, speed = prefs.speed, eqOn = prefs.eqEnabled,
            gains = gains, preset = prefs.eqPreset, bass = prefs.bass, width = prefs.width, preamp = prefs.preamp,
        )
        fx.width = prefs.width
        pushEq()
        applyPlayback()
    }

    // ======================= Запоминание позиции =======================

    private fun startPositionSaver() {
        scope.launch {
            while (isActive) {
                delay(4000)
                val t = now.value.track
                if (t != null && !now.value.isRadio) {
                    prefs.lastUri = t.uri
                    prefs.lastPos = player.currentPosition
                }
            }
        }
    }

    /** После запуска приложения — восстановить последний трек на паузе */
    fun restoreLast(library: List<Track>) {
        if (player.mediaItemCount > 0) return
        val uri = prefs.lastUri
        if (uri.isBlank()) return
        val idx = library.indexOfFirst { it.uri == uri }
        if (idx < 0) return
        playTracks(library, idx, prefs.lastPos, autoplay = false)
    }
}
