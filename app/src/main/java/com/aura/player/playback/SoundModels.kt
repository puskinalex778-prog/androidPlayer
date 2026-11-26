package com.aura.player.playback

/** Эффекты голоса: высота тона (через Sonic в ExoPlayer) + тембровая обработка (FxProcessor). */
enum class VoiceEffect(val title: String, val emoji: String, val pitch: Float, val mode: FxProcessor.Mode) {
    NORMAL("Обычный", "🎵", 1f, FxProcessor.Mode.NONE),
    CHILD("Детский", "👶", 1.35f, FxProcessor.Mode.NONE),
    HELIUM("Гелий", "🎈", 1.8f, FxProcessor.Mode.NONE),
    CHIPMUNK("Бурундук", "🐿️", 2.2f, FxProcessor.Mode.NONE),
    ANGRY("Злой", "😡", 0.78f, FxProcessor.Mode.DISTORT),
    DEMON("Демон", "😈", 0.6f, FxProcessor.Mode.DEMON),
    GIANT("Великан", "🗿", 0.7f, FxProcessor.Mode.NONE),
    ROBOT("Робот", "🤖", 1f, FxProcessor.Mode.ROBOT),
    CAVE("Пещера", "🏔️", 1f, FxProcessor.Mode.ECHO),
    HALL("Концертный зал", "🏛️", 1f, FxProcessor.Mode.HALL),
    PHONE("Телефон", "📞", 1f, FxProcessor.Mode.TELEPHONE),
    UNDERWATER("Под водой", "🌊", 0.95f, FxProcessor.Mode.UNDERWATER),
    VINYL("Винил", "💿", 1f, FxProcessor.Mode.VINYL),
    KARAOKE("Караоке", "🎤", 1f, FxProcessor.Mode.KARAOKE),
}

/** Пресеты эквалайзера: 10 полос 31 Гц … 16 кГц, усиление в дБ */
data class EqPreset(val name: String, val gains: FloatArray)

object EqPresets {
    val freqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val labels = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    val all = listOf(
        EqPreset("Ровно", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Бас+", floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Рок", floatArrayOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)),
        EqPreset("Поп", floatArrayOf(-1f, 1f, 3f, 4f, 3f, 1f, -1f, -1f, 1f, 2f)),
        EqPreset("Электро", floatArrayOf(6f, 5f, 2f, 0f, -2f, 1f, 0f, 2f, 5f, 6f)),
        EqPreset("Хип-хоп", floatArrayOf(6f, 5f, 2f, 3f, -1f, -1f, 1f, 0f, 2f, 3f)),
        EqPreset("Джаз", floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 3f, 4f)),
        EqPreset("Классика", floatArrayOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
        EqPreset("Вокал", floatArrayOf(-2f, -3f, -2f, 1f, 4f, 5f, 4f, 2f, 0f, -1f)),
        EqPreset("Акустика", floatArrayOf(3f, 3f, 2f, 1f, 2f, 2f, 3f, 3f, 2f, 1f)),
        EqPreset("Танцевальная", floatArrayOf(5f, 7f, 4f, 0f, 1f, 3f, 4f, 3f, 2f, 0f)),
        EqPreset("Наушники", floatArrayOf(4f, 3f, 1f, 0f, -1f, 0f, 1f, 3f, 4f, 3f)),
        EqPreset("Ночь (тише бас)", floatArrayOf(-4f, -3f, -2f, 0f, 1f, 2f, 2f, 1f, 0f, -1f)),
        EqPreset("Кассета 80-х", floatArrayOf(3f, 4f, 3f, 1f, 0f, 1f, 0f, -2f, -5f, -8f)),
    )
}
