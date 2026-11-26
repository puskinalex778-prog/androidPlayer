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

/** Качество потока по умолчанию */
enum class Quality(val title: String) {
    LOW("Эконом"), MEDIUM("Стандарт"), HIGH("Максимум");
}
