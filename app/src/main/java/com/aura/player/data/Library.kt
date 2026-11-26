package com.aura.player.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Медиатека: сканирует ВСЕ хранилища (внутреннюю память и карту памяти),
 * а также файлы и папки, которые пользователь добавил вручную через системный выбор файлов.
 *
 * Почему раньше файлы с карты памяти не добавлялись:
 *  1) запрашивался только основной том MediaStore — SD-карта на Android 10+ это отдельный том;
 *  2) фильтр пропускал только MP3/WAV, а на карте часто FLAC/M4A/OGG;
 *  3) не сохранялось постоянное разрешение на выбранный файл — после перезапуска он «пропадал».
 */
object Library {

    const val UNKNOWN_ARTIST = "Неизвестный исполнитель"

    val audioExt = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "amr", "3gp", "mka", "webm", "mp4", "m4b", "wave", "aif", "aiff"
    )

    fun isAudioName(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return n.substringAfterLast('.', "") in audioExt
    }

    // ===================== Сканирование MediaStore =====================

    fun scanDevice(context: Context): List<Track> {
        val out = LinkedHashMap<String, Track>()
        val volumes: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getExternalVolumeNames(context).toList() }.getOrDefault(emptyList())
                .ifEmpty { listOf(MediaStore.VOLUME_EXTERNAL) }
        } else listOf("external")

        for (vol in volumes) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Audio.Media.getContentUri(vol)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val isSdVolume = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                vol != MediaStore.VOLUME_EXTERNAL_PRIMARY && vol != MediaStore.VOLUME_EXTERNAL
            queryCollection(context, collection, isSdVolume).forEach { out.putIfAbsent(it.uri, it) }
        }
        return out.values.toList()
    }

    @Suppress("DEPRECATION")
    private fun queryCollection(context: Context, collection: Uri, sdVolume: Boolean): List<Track> {
        val result = ArrayList<Track>()
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cols += MediaStore.Audio.Media.RELATIVE_PATH
        // Без фильтра IS_MUSIC: на карте памяти файлы часто лежат вне папки Music и не помечены как музыка
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND (${MediaStore.Audio.Media.DURATION} >= 20000 OR ${MediaStore.Audio.Media.DURATION} IS NULL OR ${MediaStore.Audio.Media.DURATION} = 0)"
        val primaryRoot = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrDefault("/storage/emulated/0")
        try {
            context.contentResolver.query(collection, cols.toTypedArray(), selection, null, null)?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val tC = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val aC = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val alC = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val dC = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val nC = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val mC = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val pC = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val rC = c.getColumnIndex("relative_path")
                while (c.moveToNext()) {
                    val name = c.str(nC)
                    val mime = c.str(mC)
                    if (!mime.startsWith("audio") && !isAudioName(name) && mime != "application/ogg") continue
                    val id = c.getLong(idC)
                    val data = c.str(pC)
                    val rel = c.str(rC)
                    val folder = when {
                        rel.isNotBlank() -> rel.trimEnd('/').substringAfterLast('/')
                        data.isNotBlank() -> File(data).parentFile?.name ?: ""
                        else -> ""
                    }
                    val onSd = sdVolume || (data.isNotBlank() && !data.startsWith(primaryRoot) && data.startsWith("/storage/"))
                    val artist = c.str(aC)
                    result += Track(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        title = c.str(tC).ifBlank { name.substringBeforeLast('.') },
                        artist = if (artist.isBlank() || artist == "<unknown>") UNKNOWN_ARTIST else artist,
                        album = c.str(alC).let { if (it == "<unknown>") "" else it },
                        durationMs = if (dC >= 0) c.getLong(dC) else 0L,
                        format = formatOf(name, mime),
                        folder = folder,
                        onSdCard = onSd,
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun Cursor.str(i: Int): String = if (i < 0) "" else (runCatching { getString(i) }.getOrNull() ?: "")

    // ===================== Ручное добавление (SAF) =====================

    /** Сохраняем разрешение на файл/папку навсегда, иначе после перезапуска доступ теряется */
    fun persist(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun readTrack(context: Context, uri: Uri, userAdded: Boolean = true): Track {
        val name = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Трек"
        var title = name.substringBeforeLast('.')
        var artist = UNKNOWN_ARTIST
        var album = ""
        var duration = 0L
        // «Исполнитель - Название.mp3»
        if (title.contains(" - ")) {
            artist = title.substringBefore(" - ").trim()
            title = title.substringAfter(" - ").trim()
        }
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }
        val sd = isSdUri(uri)
        return Track(uri.toString(), title, artist, album, duration,
            formatOf(name, runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: ""),
            folder = "", userAdded = userAdded, onSdCard = sd)
    }

    private fun isSdUri(uri: Uri): Boolean {
        val s = uri.toString()
        if (!s.contains("externalstorage.documents")) return false
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        return !id.startsWith("primary:") && !id.startsWith("home:")
    }

    /** Быстрый рекурсивный обход папки (в т.ч. на карте памяти) через DocumentsContract */
    fun scanTree(context: Context, treeUri: Uri, onProgress: (Int) -> Unit = {}): List<Track> {
        val out = ArrayList<Track>()
        val cr: ContentResolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return out
        val folderName = rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Карта памяти" }
        val stack = ArrayDeque<Pair<String, String>>()
        stack.add(rootId to folderName)
        var depthGuard = 0
        while (stack.isNotEmpty() && depthGuard < 20000) {
            depthGuard++
            val (docId, fName) = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            try {
                cr.query(children, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: ""
                        val mime = c.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) stack.add(id to name)
                        } else if (mime.startsWith("audio") || isAudioName(name) || mime == "application/ogg") {
                            val u = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            out += readTrack(context, u).copy(folder = fName)
                            if (out.size % 10 == 0) onProgress(out.size)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        onProgress(out.size)
        return out
    }

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    fun formatOf(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext.isNotBlank() && ext.length <= 4 -> ext.uppercase()
            mime.contains("mpeg") -> "MP3"
            mime.contains("flac") -> "FLAC"
            mime.contains("wav") -> "WAV"
            mime.contains("ogg") -> "OGG"
            mime.contains("mp4") || mime.contains("aac") -> "M4A"
            else -> "AUDIO"
        }
    }

    // ===================== Хранение добавленных вручную =====================

    private fun file(context: Context) = File(context.filesDir, "user_tracks.json")

    fun loadUser(context: Context): List<Track> = try {
        val f = file(context)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Track(
                    uri = o.getString("u"), title = o.optString("t"), artist = o.optString("a", UNKNOWN_ARTIST),
                    album = o.optString("al"), durationMs = o.optLong("d"), format = o.optString("f"),
                    folder = o.optString("fo"), userAdded = true, onSdCard = o.optBoolean("sd"),
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    fun saveUser(context: Context, list: List<Track>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("u", t.uri); put("t", t.title); put("a", t.artist); put("al", t.album)
                put("d", t.durationMs); put("f", t.format); put("fo", t.folder); put("sd", t.onSdCard)
            })
        }
        runCatching { file(context).writeText(arr.toString()) }
    }

    fun loadTrees(context: Context): List<String> =
        context.getSharedPreferences("aura", Context.MODE_PRIVATE).getStringSet("trees", emptySet())?.toList() ?: emptyList()

    fun saveTrees(context: Context, trees: Collection<String>) {
        context.getSharedPreferences("aura", Context.MODE_PRIVATE).edit().putStringSet("trees", trees.toSet()).apply()
    }
}
