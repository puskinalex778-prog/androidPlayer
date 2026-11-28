package com.aura.player.data

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
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

data class SafEntry(
    val docId: String,
    val name: String,
    val uri: Uri,
    val isDir: Boolean,
)

data class SafListing(
    val docId: String,
    val folderName: String,
    val folders: List<SafEntry>,
    val files: List<SafEntry>,
)

/**
 * Медиатека и плейлисты.
 * Файлы НИКОГДА не копируются — сохраняются только URI-ссылки и теги.
 * Форматы m4a/mp4/m4b/mm4/3gp/amr исключены из сканирования и добавления.
 */
object Library {

    const val UNKNOWN_ARTIST = "Неизвестный исполнитель"

    // Только музыкальные форматы (без m4a / mp4 / m4b / mm4 / 3gp / amr / видео)
    val allowedExt = setOf(
        "mp3", "flac", "wav", "wave", "ogg", "oga", "opus", "aac", "mka", "aif", "aiff", "wma",
        "ac3", "eac3", "ape", "wv", "mpc", "dsf", "dff", "mid", "midi",
    )
    private val blockedExt = setOf(
        "mp4", "m4a", "m4b", "m4p", "m4v", "mm4", "3gp", "3gpp", "amr", "webm", "mkv", "avi", "mov"
    )

    fun isAllowedAudio(name: String?, mime: String? = null): Boolean {
        val ext = name?.lowercase()?.substringAfterLast('.', "") ?: ""
        if (ext in blockedExt) return false
        val m = mime?.lowercase() ?: ""
        if (m.startsWith("video/") || m.contains("mp4") || m.contains("m4a") || m.contains("mm4") || m.contains("3gpp") || m.contains("amr")) {
            return false
        }
        if (ext.isNotBlank()) return ext in allowedExt
        return m.startsWith("audio/") || m == "application/ogg" || m == "application/x-flac"
    }

    fun isAllowedUri(context: Context, uri: Uri): Boolean {
        val name = displayName(context, uri) ?: uri.lastPathSegment
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return isAllowedAudio(name, mime)
    }

    // ===================== Сканирование MediaStore (только по запросу пользователя) =====================

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
                    if (!isAllowedAudio(name, mime)) continue
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

    // ===================== Ручное добавление и проводник по SAF (без рекурсивной свалки) =====================

    fun persist(context: Context, uri: Uri) {
        val tookBoth = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        if (!tookBoth) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    sealed class DeleteFileResult {
        data object Deleted : DeleteFileResult()
        data class NeedSystemConfirm(val intentSender: IntentSender) : DeleteFileResult()
        data class Failed(val reason: String) : DeleteFileResult()
    }

    /**
     * Физически удаляет аудиофайл с телефона или SD-карты.
     * На Android 11+ для MediaStore возвращает системный запрос подтверждения (MediaStore.createDeleteRequest).
     */
    fun deletePhysicalFile(context: Context, uriStr: String): DeleteFileResult {
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return DeleteFileResult.Failed("Некорректный путь файла")
        val cr = context.contentResolver

        if (uri.scheme == "file") {
            val f = uri.path?.let { File(it) } ?: return DeleteFileResult.Failed("Файл не найден")
            return if (!f.exists() || f.delete()) DeleteFileResult.Deleted
            else DeleteFileResult.Failed("Нет прав на удаление файла")
        }

        // SAF-документ (SD-карта / выбранная папка)
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val ok = runCatching { DocumentsContract.deleteDocument(cr, uri) }.getOrDefault(false)
            if (ok) return DeleteFileResult.Deleted
            // Попытка через ContentResolver.delete
            val rows = runCatching { cr.delete(uri, null, null) }.getOrDefault(0)
            return if (rows > 0) DeleteFileResult.Deleted
            else DeleteFileResult.Failed("Android запретил удаление этого SAF-файла (выдайте доступ к папке заново или удалите через файловый менеджер)")
        }

        // MediaStore (Android 11+ / API 30+) — официальный системный диалог удаления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.authority == MediaStore.AUTHORITY) {
            val pi = runCatching { MediaStore.createDeleteRequest(cr, listOf(uri)) }.getOrNull()
            if (pi != null) {
                return DeleteFileResult.NeedSystemConfirm(pi.intentSender)
            }
        }

        // MediaStore на Android 7–10 (и запасной путь)
        return try {
            val rows = cr.delete(uri, null, null)
            if (rows > 0) DeleteFileResult.Deleted
            else DeleteFileResult.Failed("Файл не найден или защищён от удаления")
        } catch (se: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && se is RecoverableSecurityException) {
                DeleteFileResult.NeedSystemConfirm(se.userAction.actionIntent.intentSender)
            } else {
                DeleteFileResult.Failed("Нет системного разрешения на удаление этого файла")
            }
        } catch (e: Exception) {
            DeleteFileResult.Failed(e.message ?: "Не удалось удалить файл")
        }
    }

    fun readTrack(context: Context, uri: Uri, userAdded: Boolean = true, fallbackFolder: String = ""): Track {
        val name = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Трек"
        var title = name.substringBeforeLast('.')
        var artist = UNKNOWN_ARTIST
        var album = ""
        var duration = 0L
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
        return Track(
            uri = uri.toString(),
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            format = formatOf(name, runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: ""),
            folder = fallbackFolder,
            userAdded = userAdded,
            onSdCard = sd,
        )
    }

    fun isSdUri(uri: Uri): Boolean {
        val s = uri.toString()
        if (!s.contains("externalstorage.documents")) return false
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return false
        return !id.startsWith("primary:") && !id.startsWith("home:")
    }

    /** Читает ТОЛЬКО один уровень выбранной папки (подпапки + разрешённые аудиофайлы). Очень быстро! */
    fun listSafDirectory(context: Context, treeUri: Uri, docId: String? = null, folderLabel: String? = null): SafListing {
        val targetId = docId ?: runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: ""
        val name = folderLabel ?: targetId.substringAfterLast(':').substringAfterLast('/').ifBlank {
            if (isSdUri(treeUri)) "Карта памяти" else "Память"
        }
        val folders = ArrayList<SafEntry>()
        val files = ArrayList<SafEntry>()
        if (targetId.isBlank()) return SafListing(targetId, name, folders, files)

        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, targetId)
        try {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val n = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    if (n.startsWith(".")) continue
                    val u = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders += SafEntry(id, n, u, isDir = true)
                    } else if (isAllowedAudio(n, mime)) {
                        files += SafEntry(id, n, u, isDir = false)
                    }
                }
            }
        } catch (_: Exception) {
        }
        folders.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return SafListing(targetId, name, folders, files)
    }

    /**
     * Сканирует выбранную папку внутри treeUri.
     * По умолчанию recursive = false — добавляет ТОЛЬКО файлы прямо внутри этой папки, не залезая в подпапки!
     */
    fun scanSafFolder(
        context: Context,
        treeUri: Uri,
        docId: String,
        folderName: String,
        recursive: Boolean = false,
        onProgress: (Int) -> Unit = {},
    ): List<Track> {
        val out = ArrayList<Track>()
        val cr: ContentResolver = context.contentResolver
        val stack = ArrayDeque<Pair<String, String>>()
        stack.add(docId to folderName)
        var guard = 0
        while (stack.isNotEmpty() && guard < 10000) {
            guard++
            val (curId, curName) = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, curId)
            try {
                cr.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val n = c.getString(1) ?: ""
                        val mime = c.getString(2) ?: ""
                        if (n.startsWith(".")) continue
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive) stack.add(id to n)
                        } else if (isAllowedAudio(n, mime)) {
                            val u = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            out += readTrack(context, u, userAdded = true, fallbackFolder = curName)
                            if (out.size % 5 == 0) onProgress(out.size)
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
            mime.contains("opus") -> "OPUS"
            mime.contains("aac") -> "AAC"
            else -> "AUDIO"
        }
    }

    // ===================== Хранение треков и плейлистов (только ссылки, без копирования файлов) =====================

    private fun userFile(context: Context) = File(context.filesDir, "user_tracks.json")
    private fun playlistsFile(context: Context) = File(context.filesDir, "playlists.json")

    fun loadUser(context: Context): List<Track> = try {
        val f = userFile(context)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val fmt = o.optString("f")
                if (fmt.lowercase() in blockedExt) return@mapNotNull null
                Track(
                    uri = o.getString("u"), title = o.optString("t"), artist = o.optString("a", UNKNOWN_ARTIST),
                    album = o.optString("al"), durationMs = o.optLong("d"), format = fmt,
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
        runCatching { userFile(context).writeText(arr.toString()) }
    }

    fun loadPlaylists(context: Context): List<Playlist> = try {
        val f = playlistsFile(context)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ua = o.optJSONArray("u") ?: JSONArray()
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("n"),
                    uris = (0 until ua.length()).map { j -> ua.getString(j) },
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    fun savePlaylists(context: Context, list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("n", p.name)
                put("u", JSONArray().apply { p.uris.forEach { put(it) } })
            })
        }
        runCatching { playlistsFile(context).writeText(arr.toString()) }
    }
}
