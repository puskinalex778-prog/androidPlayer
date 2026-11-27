package com.aura.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("aura", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) {
            is String -> e.putString(k, v)
            is Int -> e.putInt(k, v)
            is Float -> e.putFloat(k, v)
            is Boolean -> e.putBoolean(k, v)
            is Long -> e.putLong(k, v)
        }
        e.apply()
    }

    // ---- оформление ----
    var theme: String get() = str("theme", "COBALT"); set(v) = put("theme", v)
    var activeCustomThemeId: String get() = str("customThemeId", ""); set(v) = put("customThemeId", v)
    var uiStyle: String get() = str("uiStyle", "FLAT"); set(v) = put("uiStyle", v)
    var visualizer: String get() = str("visualizer", "BARS"); set(v) = put("visualizer", v)
    var darkMode: Int get() = sp.getInt("dark", 0); set(v) = put("dark", v)
    // По умолчанию выключено, чтобы выбранная пользователем цветовая схема всегда применялась мгновенно!
    var adaptiveColor: Boolean get() = sp.getBoolean("adaptive_v2", false); set(v) = put("adaptive_v2", v)
    var playerStage: Int get() = sp.getInt("stage", if (sp.getBoolean("cassette", true)) 0 else 1); set(v) = put("stage", v)
    var amoled: Boolean get() = sp.getBoolean("amoled", false); set(v) = put("amoled", v)
    var playerDockActions: String get() = str("playerDockActions", "VOICE,EQ,TIMER,OUTPUT"); set(v) = put("playerDockActions", v)
    var shakeSensitivity: Int get() = sp.getInt("shakeSens", 0); set(v) = put("shakeSens", v)

    var customThemes: List<CustomTheme>
        get() = try {
            val arr = JSONArray(str("customThemes", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomTheme(
                    id = o.getString("id"),
                    name = o.getString("n"),
                    accentArgb = o.getInt("a"),
                    secondaryArgb = o.getInt("s"),
                    bgArgb = o.getInt("b"),
                    isDark = o.optBoolean("d", true),
                )
            }
        } catch (_: Exception) { emptyList() }
        set(list) {
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id); put("n", t.name)
                    put("a", t.accentArgb); put("s", t.secondaryArgb)
                    put("b", t.bgArgb); put("d", t.isDark)
                })
            }
            put("customThemes", arr.toString())
        }

    // ---- звук и вывод ----
    var voice: String get() = str("voice", "NORMAL"); set(v) = put("voice", v)
    var pitchSemis: Float get() = sp.getFloat("pitchSemis", 0f); set(v) = put("pitchSemis", v)
    var speed: Float get() = sp.getFloat("speed", 1f); set(v) = put("speed", v)
    var eqEnabled: Boolean get() = sp.getBoolean("eqOn", true); set(v) = put("eqOn", v)
    var eqGains: String get() = str("eqGains", ""); set(v) = put("eqGains", v)
    var eqPreset: String get() = str("eqPreset", "Ровно"); set(v) = put("eqPreset", v)
    var bass: Float get() = sp.getFloat("bass", 0f); set(v) = put("bass", v)
    var width: Float get() = sp.getFloat("width", 1f); set(v) = put("width", v)
    var preamp: Float get() = sp.getFloat("preamp", 0f); set(v) = put("preamp", v)
    var balance: Float get() = sp.getFloat("balance", 0f); set(v) = put("balance", v)
    var mono: Boolean get() = sp.getBoolean("mono", false); set(v) = put("mono", v)
    var normalize: Boolean get() = sp.getBoolean("normalize", false); set(v) = put("normalize", v)
    var smoothFade: Boolean get() = sp.getBoolean("smoothFade", true); set(v) = put("smoothFade", v)
    var autoCrossfade: Boolean get() = sp.getBoolean("autoCrossfade", false); set(v) = put("autoCrossfade", v)
    var outputMode: Int get() = sp.getInt("outputMode", 0); set(v) = put("outputMode", v)
    var forceSpeakerCallMode: Boolean get() = sp.getBoolean("forceSpeakerCall", false); set(v) = put("forceSpeakerCall", v)
    var audioQuality: Int get() = sp.getInt("audioQuality", 0); set(v) = put("audioQuality", v)

    // ---- радио ----
    var quality: Int get() = sp.getInt("quality", 1); set(v) = put("quality", v)
    var favorites: Set<String>
        get() = sp.getStringSet("favs", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("favs", v).apply() }
    var lastCountry: String get() = str("country", "RU"); set(v) = put("country", v)
    fun stationBitrate(id: String): Int = sp.getInt("br_$id", -1)
    fun setStationBitrate(id: String, br: Int) = put("br_$id", br)

    var customStations: List<Station>
        get() = try {
            val arr = JSONArray(str("custom", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Station(o.getString("id"), o.getString("name"), o.optString("country"), o.optString("genre"),
                    listOf(StreamOption(o.optInt("br"), o.getString("url"))), custom = true)
            }
        } catch (_: Exception) { emptyList() }
        set(list) {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id); put("name", s.name); put("country", s.country); put("genre", s.genre)
                    put("br", s.streams.firstOrNull()?.bitrate ?: 0); put("url", s.streams.firstOrNull()?.url ?: "")
                })
            }
            put("custom", arr.toString())
        }

    var savedOnline: List<Station>
        get() = try {
            val arr = JSONArray(str("savedOnline", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sa = o.getJSONArray("s")
                Station(o.getString("id"), o.getString("name"), o.optString("country"), o.optString("genre"),
                    (0 until sa.length()).map { j -> val so = sa.getJSONObject(j); StreamOption(so.optInt("b"), so.optString("u"), so.optString("c")) })
            }
        } catch (_: Exception) { emptyList() }
        set(list) {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id); put("name", s.name); put("country", s.country); put("genre", s.genre)
                    put("s", JSONArray().apply { s.streams.forEach { put(JSONObject().put("b", it.bitrate).put("u", it.url).put("c", it.codec)) } })
                })
            }
            put("savedOnline", arr.toString())
        }

    // ---- медиатека ----
    var sort: Int get() = sp.getInt("sort", 0); set(v) = put("sort", v)
    var lastUri: String get() = str("lastUri", ""); set(v) = put("lastUri", v)
    var lastPos: Long get() = sp.getLong("lastPos", 0L); set(v) = put("lastPos", v)
    var favoriteTracks: Set<String>
        get() = sp.getStringSet("favTracks", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("favTracks", v).apply() }
}
