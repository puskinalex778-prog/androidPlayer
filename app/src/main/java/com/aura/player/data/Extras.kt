package com.aura.player.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long?, val text: String)

/** Хранилище текстов песен и LRC-караоке (в т.ч. чтение встроенного ID3v2 USLT из MP3). */
object LyricsStore {
    private val lrcTag = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val metaTag = Regex("^\\[[a-zA-Z]+:.*]$")

    private fun file(context: Context) = File(context.filesDir, "lyrics_store.json")

    fun load(context: Context, uri: String): String? = try {
        val f = file(context)
        if (!f.exists()) null else JSONObject(f.readText()).optString(uri).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun save(context: Context, uri: String, text: String) {
        runCatching {
            val f = file(context)
            val obj = if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject()) else JSONObject()
            if (text.isBlank()) obj.remove(uri) else obj.put(uri, text)
            f.writeText(obj.toString())
        }
    }

    fun parse(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        val rawLines = raw.replace("\r\n", "\n").split('\n')
        for (line in rawLines) {
            val trimmed = line.trim()
            val matches = lrcTag.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricText = trimmed.replace(lrcTag, "").trim()
                if (lyricText.isBlank()) continue
                for (m in matches) {
                    val min = m.groupValues[1].toLongOrNull() ?: 0L
                    val sec = m.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = m.groupValues[3]
                    val ms = when (fracStr.length) {
                        0 -> 0L
                        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                        else -> fracStr.take(3).toLongOrNull() ?: 0L
                    }
                    out += LyricLine(min * 60_000L + sec * 1000L + ms, lyricText)
                }
            } else if (!metaTag.matches(trimmed)) {
                out += LyricLine(null, line.trimEnd())
            }
        }
        return if (out.any { it.timeMs != null }) {
            out.filter { it.timeMs != null }.sortedBy { it.timeMs }
        } else {
            out
        }
    }

    /** Безопасно читает встроенный текст (ID3v2 USLT) из первых 256 КБ аудиофайла. */
    fun readEmbedded(context: Context, uriStr: String): String? = try {
        val uri = Uri.parse(uriStr)
        val buf = context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(10)
            if (stream.read(header) < 10) return@use null
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return@use null
            }
            val ver = header[3].toInt() and 0xFF
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            val toRead = tagSize.coerceIn(0, 262_144)
            val data = ByteArray(toRead)
            var off = 0
            while (off < toRead) {
                val r = stream.read(data, off, toRead - off)
                if (r <= 0) break
                off += r
            }
            parseId3Uslt(data, off, ver)
        }
        buf?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun parseId3Uslt(data: ByteArray, len: Int, ver: Int): String? {
        if (ver < 3) return null
        var i = 0
        while (i + 10 <= len) {
            if (data[i] == 0.toByte()) break
            val id = String(data, i, 4, Charsets.ISO_8859_1)
            val fSize = if (ver >= 4) {
                ((data[i + 4].toInt() and 0x7F) shl 21) or
                    ((data[i + 5].toInt() and 0x7F) shl 14) or
                    ((data[i + 6].toInt() and 0x7F) shl 7) or
                    (data[i + 7].toInt() and 0x7F)
            } else {
                ((data[i + 4].toInt() and 0xFF) shl 24) or
                    ((data[i + 5].toInt() and 0xFF) shl 16) or
                    ((data[i + 6].toInt() and 0xFF) shl 8) or
                    (data[i + 7].toInt() and 0xFF)
            }
            if (fSize <= 0 || i + 10 + fSize > len) break
            if (id == "USLT" && fSize > 4) {
                val start = i + 10
                val enc = data[start].toInt() and 0xFF
                val charset = when (enc) {
                    1 -> Charsets.UTF_16
                    2 -> Charsets.UTF_16BE
                    3 -> Charsets.UTF_8
                    else -> Charset.forName("windows-1251")
                }
                val isWide = enc == 1 || enc == 2
                var p = start + 4 // пропуск encoding (1) + language (3)
                val end = start + fSize
                if (isWide) {
                    while (p + 1 < end) {
                        if (data[p] == 0.toByte() && data[p + 1] == 0.toByte()) {
                            p += 2
                            break
                        }
                        p += 2
                    }
                } else {
                    while (p < end) {
                        if (data[p] == 0.toByte()) {
                            p++
                            break
                        }
                        p++
                    }
                }
                if (p < end) {
                    return String(data, p, end - p, charset).replace("\u0000", "").trim()
                }
            }
            i += 10 + fSize
        }
        return null
    }
}

/** Хранилище закладок внутри треков (только позиции в мс и подписи). */
object BookmarkStore {
    private fun file(context: Context) = File(context.filesDir, "bookmarks.json")

    fun load(context: Context): List<TrackBookmark> = try {
        val f = file(context)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrackBookmark(
                    id = o.getString("id"),
                    trackUri = o.getString("u"),
                    positionMs = o.getLong("p"),
                    label = o.optString("l"),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(context: Context, list: List<TrackBookmark>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("u", b.trackUri)
                put("p", b.positionMs)
                put("l", b.label)
            })
        }
        runCatching { file(context).writeText(arr.toString()) }
    }
}

/** Хранилище статистики прослушиваний. */
object StatsStore {
    private fun file(context: Context) = File(context.filesDir, "stats.json")

    @Synchronized
    fun load(context: Context): MutableMap<String, TrackStat> {
        val out = LinkedHashMap<String, TrackStat>()
        try {
            val f = file(context)
            if (!f.exists()) return out
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val st = TrackStat(
                    id = o.getString("id"),
                    title = o.optString("t"),
                    artist = o.optString("a"),
                    isRadio = o.optBoolean("r"),
                    plays = o.optInt("p"),
                    listenSec = o.optLong("s"),
                )
                out[st.id] = st
            }
        } catch (_: Exception) {
        }
        return out
    }

    @Synchronized
    fun save(context: Context, map: Map<String, TrackStat>) {
        val arr = JSONArray()
        map.values.forEach { st ->
            arr.put(JSONObject().apply {
                put("id", st.id)
                put("t", st.title)
                put("a", st.artist)
                put("r", st.isRadio)
                put("p", st.plays)
                put("s", st.listenSec)
            })
        }
        runCatching { file(context).writeText(arr.toString()) }
    }

    @Synchronized
    fun recordPlay(context: Context, id: String, title: String, artist: String, isRadio: Boolean): List<TrackStat> {
        val map = load(context)
        val cur = map[id]
        map[id] = TrackStat(
            id = id,
            title = title.ifBlank { cur?.title ?: "Без названия" },
            artist = artist.ifBlank { cur?.artist ?: "" },
            isRadio = isRadio,
            plays = (cur?.plays ?: 0) + 1,
            listenSec = cur?.listenSec ?: 0L,
        )
        save(context, map)
        return sortedList(map)
    }

    @Synchronized
    fun addListenTime(context: Context, id: String, title: String, artist: String, isRadio: Boolean, seconds: Long): List<TrackStat> {
        val map = load(context)
        val cur = map[id]
        map[id] = TrackStat(
            id = id,
            title = title.ifBlank { cur?.title ?: "Без названия" },
            artist = artist.ifBlank { cur?.artist ?: "" },
            isRadio = isRadio,
            plays = cur?.plays ?: 1,
            listenSec = (cur?.listenSec ?: 0L) + seconds,
        )
        save(context, map)
        return sortedList(map)
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun sortedList(map: Map<String, TrackStat>): List<TrackStat> =
        map.values.sortedWith(compareByDescending<TrackStat> { it.plays }.thenByDescending { it.listenSec })
}
