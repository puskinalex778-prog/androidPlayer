@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.aura.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.aura.player.data.AudioQualityMode
import com.aura.player.data.OutputMode
import com.aura.player.data.Prefs
import com.aura.player.data.Quality
import com.aura.player.data.Station
import com.aura.player.data.StatsStore
import com.aura.player.data.StreamOption
import com.aura.player.data.StreamResolver
import com.aura.player.data.Track
import com.aura.player.data.TrackStat
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
    val balance: Float = 0f,
    val mono: Boolean = false,
    val normalize: Boolean = false,
    val smoothFade: Boolean = true,
    val autoCrossfade: Boolean = false,
    val quality: AudioQualityMode = AudioQualityMode.STUDIO_FLOAT,
)

data class OutputState(
    val mode: OutputMode = OutputMode.AUTO,
    val forceSpeakerCallMode: Boolean = false,
    val speakerAvailable: Boolean = true,
    val headphonesName: String? = null,
) {
    val activeLabel: String
        get() = when {
            mode == OutputMode.SPEAKER -> if (forceSpeakerCallMode) "Динамик (звонок)" else "Динамик"
            mode == OutputMode.HEADPHONES && headphonesName != null -> headphonesName
            headphonesName != null -> headphonesName
            else -> "Динамик"
        }
}

/** Повтор фрагмента A–B (как в AIMP) */
data class AbLoop(
    val aMs: Long? = null,
    val bMs: Long? = null,
) {
    val active: Boolean get() = aMs != null && bMs != null
}

/** Единый аудио-движок: плеер, очередь, радио, DSP, качество вывода, маршрутизация наушники/динамик, A-B повтор. */
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
    val output = MutableStateFlow(OutputState())
    val abLoop = MutableStateFlow(AbLoop())
    val art = MutableStateFlow<Bitmap?>(null)
    val accent = MutableStateFlow<Int?>(null)
    val sleepAt = MutableStateFlow(0L)
    val sleepAfterTrack = MutableStateFlow(false)
    val stats = MutableStateFlow<List<TrackStat>>(emptyList())

    private var queueTracks: List<Track> = emptyList()
    private var currentStation: Station? = null
    private var currentStream: StreamOption? = null
    private val triedStreams = HashSet<String>()
    private var lastId: String? = null
    private var lastRecordedId: String? = null
    private var sleepJob: Job? = null
    private var artJob: Job? = null
    private var fadeJob: Job? = null
    private var callModeEngaged = false

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
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AuraPlayer/1.3")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        player = ExoPlayer.Builder(app, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http)))
            .setAudioAttributes(mediaAttributes(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)

        restoreSound()
        setupOutputs()
        startLoops()
        scope.launch {
            stats.value = withContext(Dispatchers.IO) { StatsStore.sortedList(StatsStore.load(app)) }
        }
    }

    private fun mediaAttributes() = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private fun callAttributes() = AudioAttributes.Builder()
        .setUsage(C.USAGE_VOICE_COMMUNICATION)
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .build()

    /** Живая техническая строка потока (как в AIMP: «48.0 kHz · 32-bit DSP · Стерео») */
    fun techSummary(): String {
        val khz = "%.1f kHz".format(fx.liveSampleRate / 1000f)
        val bits = sound.value.quality.badge
        val ch = when {
            sound.value.mono || fx.liveChannels == 1 -> "Моно"
            fx.liveChannels == 2 -> "Стерео"
            else -> "${fx.liveChannels} кан."
        }
        return "$khz · $bits · $ch"
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
        abLoop.value = AbLoop()
        queueTracks = list
        player.volume = 1f
        player.setMediaItems(list.map { itemOf(it) }, index.coerceIn(0, list.lastIndex), startPos)
        player.prepare()
        if (autoplay) {
            applyOutput(forPlaying = true)
            player.play()
        }
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
            abLoop.value = AbLoop()
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
        abLoop.value = AbLoop()
        queueTracks = emptyList()
        error.value = null
        now.update { it.copy(isRadio = true, station = st, stream = s, title = st.name, artist = st.genre, buffering = true, streamTitle = null, track = null) }
        art.value = null
        accent.value = null
        applyRadioQuality()
        scope.launch {
            val url = try {
                withContext(Dispatchers.IO) { StreamResolver.resolve(s.url) }
            } catch (_: Exception) {
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
            player.volume = 1f
            player.setMediaItem(b.build())
            player.prepare()
            applyOutput(forPlaying = true)
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

    // ======================= Управление + плавное затухание (AIMP) =======================

    fun toggle() {
        if (player.mediaItemCount == 0) return
        fadeJob?.cancel()
        val smooth = sound.value.smoothFade && !now.value.isRadio
        if (player.isPlaying) {
            if (smooth) {
                fadeJob = scope.launch {
                    for (i in 8 downTo 0) {
                        player.volume = i / 8f
                        delay(20)
                    }
                    player.pause()
                    player.volume = 1f
                    releaseCallRoutingIfNeeded()
                }
            } else {
                player.pause()
                releaseCallRoutingIfNeeded()
            }
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(player.currentMediaItemIndex, 0L)
            applyOutput(forPlaying = true)
            if (smooth) {
                player.volume = 0f
                player.play()
                fadeJob = scope.launch {
                    for (i in 1..8) {
                        player.volume = i / 8f
                        delay(22)
                    }
                }
            } else {
                player.volume = 1f
                player.play()
            }
        }
    }

    fun next() { abLoop.value = AbLoop(); if (player.hasNextMediaItem()) player.seekToNextMediaItem() else if (player.mediaItemCount > 0) player.seekTo(0, 0L) }
    fun prev() { abLoop.value = AbLoop(); if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) player.seekTo(0L) else player.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0L))
    fun seekBy(ms: Long) {
        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, dur))
    }
    fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** A–B повтор фрагмента: 1-е нажатие = точка A, 2-е = точка B (зацикливает), 3-е = сброс */
    fun tapAbLoop() {
        if (now.value.isRadio || !now.value.hasMedia) return
        val cur = abLoop.value
        val pos = player.currentPosition.coerceAtLeast(0L)
        abLoop.value = when {
            cur.aMs == null -> AbLoop(aMs = pos, bMs = null)
            cur.bMs == null -> if (pos > cur.aMs + 800) AbLoop(aMs = cur.aMs, bMs = pos) else AbLoop()
            else -> AbLoop()
        }
    }

    fun clearAbLoop() { abLoop.value = AbLoop() }

    fun stop() {
        fadeJob?.cancel()
        abLoop.value = AbLoop()
        player.stop()
        player.clearMediaItems()
        queueTracks = emptyList()
        currentStation = null
        releaseCallRoutingIfNeeded()
        refresh()
    }

    // ======================= Таймер сна =======================

    fun setSleep(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { sleepAt.value = 0L; return }
        sleepAfterTrack.value = false
        val at = System.currentTimeMillis() + minutes * 60_000L
        sleepAt.value = at
        sleepJob = scope.launch {
            while (isActive && System.currentTimeMillis() < at) delay(1000)
            for (i in 10 downTo 1) { player.volume = i / 10f; delay(300) }
            player.pause()
            player.volume = 1f
            releaseCallRoutingIfNeeded()
            sleepAt.value = 0L
        }
    }

    fun setSleepAfterTrack(on: Boolean) {
        if (on) {
            sleepJob?.cancel()
            sleepAt.value = 0L
        }
        sleepAfterTrack.value = on
    }

    fun clearStats() {
        scope.launch {
            withContext(Dispatchers.IO) { StatsStore.clear(app) }
            stats.value = emptyList()
        }
    }

    // ======================= События плеера =======================

    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) = refresh()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (sleepAfterTrack.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                sleepAfterTrack.value = false
                player.pause()
                player.volume = 1f
                releaseCallRoutingIfNeeded()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && sleepAfterTrack.value) {
                sleepAfterTrack.value = false
            }
        }

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
                val alt = st.streams.firstOrNull { it.url !in triedStreams }
                if (alt != null) {
                    error.value = "Поток ${currentStream?.label ?: ""} не отвечает — пробую ${alt.label} кбит/с"
                    playStation(st, alt)
                    return
                }
                error.value = "Не удалось подключиться к «${st.name}». Попробуйте другой битрейт или онлайн-каталог."
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
            abLoop.value = AbLoop()
            applyPlayback()
            if (!isRadio) loadArt(track?.uri)
        }
        if (p.isPlaying && id != null && id != lastRecordedId) {
            lastRecordedId = id
            val curTitle = track?.title ?: md.title?.toString() ?: currentStation?.name ?: ""
            val curArtist = track?.artist ?: md.artist?.toString() ?: ""
            scope.launch {
                val updated = withContext(Dispatchers.IO) {
                    StatsStore.recordPlay(app, id, curTitle, curArtist, isRadio)
                }
                stats.value = updated
            }
        }
        if (p.isPlaying && error.value != null) error.value = null
        if (!p.isPlaying && !p.playWhenReady) releaseCallRoutingIfNeeded()
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

    // ======================= Голос / Эквалайзер / Качество DSP =======================

    fun setVoice(v: VoiceEffect) { sound.update { it.copy(voice = v) }; prefs.voice = v.name; applyPlayback() }
    fun setPitch(semis: Float) { sound.update { it.copy(pitchSemis = semis) }; prefs.pitchSemis = semis; applyPlayback() }
    fun setSpeed(s: Float) { sound.update { it.copy(speed = s) }; prefs.speed = s; applyPlayback() }

    private fun applyPlayback() {
        val st = sound.value
        fx.mode = st.voice.mode
        val pitch = (st.voice.pitch * 2.0.pow(st.pitchSemis / 12.0).toFloat()).coerceIn(0.25f, 4f)
        val speed = if (now.value.isRadio) 1f else (st.voice.speed * st.speed).coerceIn(0.25f, 3f)
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
    fun setBalance(v: Float) { sound.update { it.copy(balance = v) }; prefs.balance = v; fx.balance = v }
    fun setMono(v: Boolean) { sound.update { it.copy(mono = v) }; prefs.mono = v; fx.mono = v }
    fun setNormalize(v: Boolean) { sound.update { it.copy(normalize = v) }; prefs.normalize = v; fx.normalize = v }
    fun setSmoothFade(v: Boolean) { sound.update { it.copy(smoothFade = v) }; prefs.smoothFade = v }
    fun setAutoCrossfade(v: Boolean) {
        sound.update { it.copy(autoCrossfade = v) }
        prefs.autoCrossfade = v
        if (!v && ::player.isInitialized) player.volume = 1f
    }

    fun setAudioQuality(q: AudioQualityMode) {
        prefs.audioQuality = q.ordinal
        sound.update { it.copy(quality = q) }
        applyQualityToFx()
    }

    private fun applyQualityToFx() {
        val q = sound.value.quality
        fx.directBypass = q == AudioQualityMode.DIRECT_PURE
        fx.hiResDither = q == AudioQualityMode.STUDIO_FLOAT
    }

    fun resetSound() {
        setVoice(VoiceEffect.NORMAL); setPitch(0f); setSpeed(1f)
        applyPreset(EqPresets.all.first()); setBass(0f); setPreamp(0f); setWidth(1f)
        setBalance(0f); setMono(false); setNormalize(false)
    }

    private fun pushEq() {
        val s = sound.value
        prefs.eqGains = s.gains.joinToString(",")
        fx.setEq(s.eqOn, s.gains.toFloatArray(), s.bass, s.preamp)
    }

    private fun restoreSound() {
        val v = runCatching { VoiceEffect.valueOf(prefs.voice) }.getOrDefault(VoiceEffect.NORMAL)
        val gains = prefs.eqGains.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 10 } ?: List(10) { 0f }
        val q = AudioQualityMode.of(prefs.audioQuality)
        sound.value = SoundState(
            voice = v, pitchSemis = prefs.pitchSemis, speed = prefs.speed, eqOn = prefs.eqEnabled,
            gains = gains, preset = prefs.eqPreset, bass = prefs.bass, width = prefs.width, preamp = prefs.preamp,
            balance = prefs.balance, mono = prefs.mono, normalize = prefs.normalize,
            smoothFade = prefs.smoothFade, autoCrossfade = prefs.autoCrossfade,
            quality = q,
        )
        fx.width = prefs.width
        fx.balance = prefs.balance
        fx.mono = prefs.mono
        fx.normalize = prefs.normalize
        applyQualityToFx()
        pushEq()
        applyPlayback()
    }

    // ======================= Маршрутизация вывода: Авто / Динамик / Наушники (в т.ч. «как при звонке») =======================

    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        22, // TYPE_USB_HEADSET
        26, // TYPE_BLE_HEADSET
        23, // TYPE_HEARING_AID
    )

    private fun setupOutputs() {
        val savedMode = OutputMode.of(prefs.outputMode)
        output.update { it.copy(mode = savedMode, forceSpeakerCallMode = prefs.forceSpeakerCallMode) }
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshOutputs()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshOutputs()
        }, Handler(Looper.getMainLooper()))
        refreshOutputs()
    }

    private fun findDevice(mode: OutputMode): AudioDeviceInfo? {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when (mode) {
            OutputMode.AUTO -> null
            OutputMode.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            OutputMode.HEADPHONES -> devices.firstOrNull { it.type in headphoneTypes }
        }
    }

    fun refreshOutputs() {
        val hp = findDevice(OutputMode.HEADPHONES)
        val sp = findDevice(OutputMode.SPEAKER)
        output.update {
            it.copy(
                speakerAvailable = sp != null,
                headphonesName = hp?.let { d ->
                    val prod = d.productName?.toString()?.takeIf { n -> n.isNotBlank() && d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
                    prod ?: when (d.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26 -> "Bluetooth"
                        22 -> "USB-наушники"
                        else -> "Проводные наушники"
                    }
                },
            )
        }
        applyOutput(forPlaying = player.isPlaying)
    }

    fun setOutputMode(mode: OutputMode) {
        prefs.outputMode = mode.ordinal
        output.update { it.copy(mode = mode) }
        applyOutput(forPlaying = player.isPlaying || player.playWhenReady)
    }

    fun setForceSpeakerCallMode(on: Boolean) {
        prefs.forceSpeakerCallMode = on
        output.update { it.copy(forceSpeakerCallMode = on) }
        applyOutput(forPlaying = player.isPlaying || player.playWhenReady)
    }

    @Suppress("DEPRECATION")
    private fun applyOutput(forPlaying: Boolean) {
        val st = output.value
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val wantCallSpeaker = st.mode == OutputMode.SPEAKER && st.forceSpeakerCallMode && forPlaying

        if (wantCallSpeaker) {
            callModeEngaged = true
            runCatching {
                player.setAudioAttributes(callAttributes(), true)
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                val sp = findDevice(OutputMode.SPEAKER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sp != null) {
                    am.setCommunicationDevice(sp)
                } else {
                    am.isSpeakerphoneOn = true
                }
                player.setPreferredAudioDevice(sp)
            }
        } else {
            releaseCallRoutingIfNeeded()
            runCatching {
                player.setPreferredAudioDevice(findDevice(st.mode))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseCallRoutingIfNeeded() {
        if (!callModeEngaged) return
        callModeEngaged = false
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
            else am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
            player.setAudioAttributes(mediaAttributes(), true)
        }
    }

    // ======================= Фоновые циклы (позиция + A-B повтор) =======================

    private fun startLoops() {
        scope.launch {
            var saveTick = 0
            var statsTick = 0
            while (isActive) {
                delay(200)
                val playing = player.isPlaying
                val curNow = now.value

                // A–B повтор фрагмента
                val ab = abLoop.value
                if (ab.active && playing && !curNow.isRadio) {
                    val pos = player.currentPosition
                    if (pos >= ab.bMs!!) player.seekTo(ab.aMs!!)
                }

                // Плавные переходы между треками (Crossfade)
                if (sound.value.autoCrossfade && playing && !curNow.isRadio &&
                    fadeJob?.isActive != true && sleepJob?.isActive != true
                ) {
                    val dur = player.duration
                    val pos = player.currentPosition
                    if (dur > 15_000L) {
                        val vol = when {
                            pos >= dur - 2_000L -> ((dur - pos).coerceAtLeast(0L) / 2000f).coerceIn(0.05f, 1f)
                            pos in 0L..1_200L -> (pos / 1200f).coerceIn(0.15f, 1f)
                            else -> 1f
                        }
                        if (abs(player.volume - vol) > 0.02f) player.volume = vol
                    }
                }

                // сохранение позиции каждые 4 сек
                saveTick++
                if (saveTick >= 20) {
                    saveTick = 0
                    val t = curNow.track
                    if (t != null && !curNow.isRadio) {
                        prefs.lastUri = t.uri
                        prefs.lastPos = player.currentPosition
                    }
                }

                // статистика прослушивания каждые 5 сек
                if (playing) {
                    statsTick++
                    if (statsTick >= 25) {
                        statsTick = 0
                        val sid = curNow.track?.id ?: curNow.station?.radioId
                        if (sid != null) {
                            val tTitle = curNow.title
                            val tArtist = curNow.artist
                            val tRadio = curNow.isRadio
                            val updated = withContext(Dispatchers.IO) {
                                StatsStore.addListenTime(app, sid, tTitle, tArtist, tRadio, 5L)
                            }
                            stats.value = updated
                        }
                    }
                }
            }
        }
    }

    fun restoreLast(library: List<Track>) {
        if (player.mediaItemCount > 0) return
        val uri = prefs.lastUri
        if (uri.isBlank()) return
        val idx = library.indexOfFirst { it.uri == uri }
        if (idx < 0) return
        playTracks(library, idx, prefs.lastPos, autoplay = false)
    }
}
