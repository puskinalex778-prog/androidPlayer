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
    var darkMode: Int get() = sp.getInt("dark", 0); set(v) = put("dark", v) // 0 системная, 1 тёмная, 2 светлая
    var adaptiveColor: Boolean get() = sp.getBoolean("adaptive", true); set(v) = put("adaptive", v)
    var cassetteView: Boolean get() = sp.getBoolean("cassette", true); set(v) = put("cassette", v)
    var amoled: Boolean get() = sp.getBoolean("amoled", false); set(v) = put("amoled", v)

    // ---- звук ----
    var voice: String get() = str("voice", "NORMAL"); set(v) = put("voice", v)
    var pitchSemis: Float get() = sp.getFloat("pitchSemis", 0f); set(v) = put("pitchSemis", v)
    var speed: Float get() = sp.getFloat("speed", 1f); set(v) = put("speed", v)
    var eqEnabled: Boolean get() = sp.getBoolean("eqOn", true); set(v) = put("eqOn", v)
    var eqGains: String get() = str("eqGains", ""); set(v) = put("eqGains", v)
    var eqPreset: String get() = str("eqPreset", "Ровно"); set(v) = put("eqPreset", v)
    var bass: Float get() = sp.getFloat("bass", 0f); set(v) = put("bass", v)
    var width: Float get() = sp.getFloat("width", 1f); set(v) = put("width", v)
    var preamp: Float get() = sp.getFloat("preamp", 0f); set(v) = put("preamp", v)

    // ---- радио ----
    var quality: Int get() = sp.getInt("quality", 1); set(v) = put("quality", v)
    var favorites: Set<String>
        get() = sp.getStringSet("favs", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("favs", v).apply() }
    var lastCountry: String get() = str("country", "RU"); set(v) = put("country", v)
    /** выбранный пользователем битрейт для станции */
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

    /** станции из онлайн-каталога, добавленные в избранное (их нет во встроенном списке) */
    var savedOnline: List<Station>
        get() = try {
            val arr = JSONArray(str("savedOnline", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sa = o.getJSONArray("s")
                Station(o.getString("id"), o.getString("name"), o.optString("country"), o.optString("genre"),
                    (0 until sa.length()).map { j -> val so = sa.getJSONObject(j); StreamOption(so.optInt("b"), so.getString("u"), so.optString("c")) })
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
    var hiddenUris: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("hidden", v).apply() }
}
