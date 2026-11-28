package com.aura.player.data

/** Локальный трек (хранится только путь URI и теги, сам файл не копируется). */
data class Track(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val format: String = "",
    val folder: String = "",
    val userAdded: Boolean = false,
    val onSdCard: Boolean = false,
) {
    val id: String get() = "local:$uri"
}

/** Пользовательский плейлист (хранит только список URI-ссылок). */
data class Playlist(
    val id: String,
    val name: String,
    val uris: List<String> = emptyList(),
)

/** Пользовательская цветовая тема. */
data class CustomTheme(
    val id: String,
    val name: String,
    val accentArgb: Int,
    val secondaryArgb: Int,
    val bgArgb: Int,
    val isDark: Boolean = true,
)

/** Режим качества вывода звука (как в AIMP / Hi-Res плеерах). */
enum class AudioQualityMode(val title: String, val desc: String, val badge: String) {
    STUDIO_FLOAT("Hi-Res 32-bit DSP", "32-битная обработка + TPDF-дизеринг без искажений и клиппинга", "32-bit DSP"),
    HIFI_PCM("Стандарт 16-bit PCM", "Классическая обработка — стабильно на любом Bluetooth и авто", "16-bit PCM"),
    DIRECT_PURE("Direct (без обработки)", "Чистый сигнал в обход эквалайзера и эффектов, экономит заряд", "DIRECT");

    companion object {
        fun of(index: Int): AudioQualityMode = entries.getOrElse(index) { STUDIO_FLOAT }
    }
}

/** Режим маршрутизации вывода звука. */
enum class OutputMode(val title: String) {
    AUTO("Авто"),
    SPEAKER("Динамик"),
    HEADPHONES("Наушники / BT");

    companion object {
        fun of(index: Int): OutputMode = entries.getOrElse(index) { AUTO }
    }
}

/** Аудио-движок вывода (как в AIMP / Poweramp / Hi-Res плеерах). */
enum class AudioEngineMode(val title: String, val desc: String, val badge: String) {
    AUDIOTRACK_STD(
        "AudioTrack Hi-Fi",
        "Стандартный движок Media3 · 32-bit DSP, стабильная работа с Bluetooth и авто",
        "AudioTrack",
    ),
    AUDIOTRACK_LOW_LATENCY(
        "Low-Latency DSP",
        "Уменьшенный аудиобуфер · мгновенный отклик, быстрая перемотка и точный синхрон визуала",
        "Low-Latency",
    ),
    AUDIO_OFFLOAD(
        "Hardware Offload",
        "Аппаратное декодирование DSP-чипом телефона (активно в режиме Direct, экономит батарею)",
        "Offload DSP",
    );

    companion object {
        fun of(index: Int): AudioEngineMode = entries.getOrElse(index) { AUDIOTRACK_STD }
    }
}

/** Приоритет аудиодекодеров / кодеков. */
enum class DecoderPriority(val title: String, val desc: String) {
    AUTO("Авто (Аппаратный + Soft)", "Оптимальный кодек Android MediaCodec + резервные декодеры Media3"),
    HARDWARE("Приоритет аппаратного", "Максимальное использование системных кодеков (экономия CPU)"),
    SOFTWARE("Программный + Fallback", "Расширенная совместимость с редкими FLAC / OGG / Opus / WAV / AC-3");

    companion object {
        fun of(index: Int): DecoderPriority = entries.getOrElse(index) { AUTO }
    }
}

/** Изолированное прослушивание партий трека в реальном времени (32-bit DSP Mid/Side + 24 dB/oct кроссоверы). */
enum class StemIsolationMode(val title: String, val shortTitle: String, val desc: String) {
    OFF("Весь микс", "Микс", "Обычное полноценное звучание трека"),
    VOCAL("Только голос", "Голос", "Выделяет центральный вокал (240–3600 Гц) и подавляет бас и стерео-инструменты"),
    BASS("Только бас", "Бас", "Изолирует бочку и бас-гитару крутым фильтром 24 дБ/окт ниже 175 Гц"),
    INSTRUMENTAL("Только инструментал", "Минус", "Подавляет центральный вокал (180–4800 Гц), сохраняя бас, ударные и стерео-инструменты");
}

enum class DeviceCategory(val title: String) {
    TWS("TWS / Вкладыши"),
    OVER_EAR("Полноразмерные"),
    SPEAKER("Колонки"),
    UNIVERSAL("Общие"),
}

/** 10-полосный AutoEQ профиль калибровки под конкретные наушники или колонку. */
data class DeviceEqProfile(
    val id: String,
    val name: String,
    val year: String,
    val category: DeviceCategory,
    val flawFix: String,
    val gains: List<Float>, // 10 полос: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz
    val preampDb: Float,
    val matchKeys: List<String>,
)

/** Один поток станции — конкретный битрейт. */
data class StreamOption(
    val bitrate: Int,          // кбит/с; 0 = адаптивный (HLS) / неизвестно
    val url: String,
    val codec: String = "",
) {
    val label: String
        get() = when {
            bitrate <= 0 -> "Авто"
            else -> "$bitrate"
        }
}

data class Station(
    val id: String,
    val name: String,
    val country: String,      // RU, US, DE, FR, KR
    val genre: String,
    val streams: List<StreamOption>,
    val custom: Boolean = false,
) {
    val radioId: String get() = "radio:$id"
}

enum class Country(val code: String, val title: String, val flag: String) {
    RU("RU", "Россия", "🇷🇺"),
    US("US", "США", "🇺🇸"),
    DE("DE", "Германия", "🇩🇪"),
    FR("FR", "Франция", "🇫🇷"),
    KR("KR", "Южная Корея", "🇰🇷");

    companion object {
        fun of(code: String?): Country? = entries.firstOrNull { it.code.equals(code, true) }
    }
}

/** Кнопки настраиваемой нижней панели полноэкранного плеера. */
enum class PlayerDockAction(val title: String) {
    VOICE("Голос"),
    EQ("EQ"),
    TIMER("Таймер"),
    OUTPUT("Вывод"),
    RADIO("Радио"),
    PLAYLISTS("Плейлисты"),
    QUEUE("Очередь"),
    LYRICS("Текст"),
    SPEED("Скорость"),
    VISUALIZER("Визуал"),
    BOOKMARK("Закладки"),
    NOTE("Заметка"),
    CAR_MODE("Авто"),
    INFO("Инфо");

    companion object {
        val defaultDock = listOf(VOICE, EQ, TIMER, OUTPUT)

        fun parseList(csv: String): List<PlayerDockAction> {
            val parsed = csv.split(',')
                .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }
                .distinct()
                .take(5)
            return if (parsed.size >= 2) parsed else defaultDock
        }
    }
}

/** Закладка / метка времени внутри трека (удобно для миксов, лекций и аудиокниг). */
data class TrackBookmark(
    val id: String,
    val trackUri: String,
    val positionMs: Long,
    val label: String,
)

/** Статистика прослушиваний трека или радиостанции. */
data class TrackStat(
    val id: String,
    val title: String,
    val artist: String,
    val isRadio: Boolean,
    val plays: Int,
    val listenSec: Long,
)

/** Качество интернет-радио по умолчанию */
enum class Quality(val title: String) {
    LOW("Эконом"), MEDIUM("Стандарт"), HIGH("Максимум");
}
