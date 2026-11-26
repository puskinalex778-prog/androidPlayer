package com.aura.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import java.util.Collections

/** Загрузка обложки альбома, встроенной в аудиофайл (ID3 APIC / FLAC PICTURE / MP4 covr) */
object ArtLoader {

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val misses = Collections.synchronizedSet(HashSet<String>())
    private val bytesCache = LruCache<String, ByteArray>(6)

    fun cached(uri: String, size: Int): Bitmap? = cache.get("$uri@$size")

    fun load(context: Context, uri: String, size: Int = 512): Bitmap? {
        val key = "$uri@$size"
        cache.get(key)?.let { return it }
        if (misses.contains(key)) return null
        val bmp = decode(context, uri, size)
        if (bmp != null) cache.put(key, bmp) else misses.add(key)
        return bmp
    }

    /** Сырые байты картинки — для уведомления и экрана блокировки */
    fun bytes(context: Context, uri: String): ByteArray? {
        bytesCache.get(uri)?.let { return it }
        val b = embedded(context, uri) ?: return null
        bytesCache.put(uri, b)
        return b
    }

    private fun embedded(context: Context, uri: String): ByteArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, Uri.parse(uri))
            mmr.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun decode(context: Context, uri: String, size: Int): Bitmap? {
        val data = embedded(context, uri)
        if (data != null) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            runCatching { BitmapFactory.decodeByteArray(data, 0, data.size, opts) }.getOrNull()?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.startsWith("content://media/")) {
            runCatching { context.contentResolver.loadThumbnail(Uri.parse(uri), Size(size, size), null) }.getOrNull()?.let { return it }
        }
        return null
    }

    /** Яркий «доминирующий» цвет обложки — им подкрашивается интерфейс */
    fun accentOf(bmp: Bitmap): Int? {
        val small = runCatching { Bitmap.createScaledBitmap(bmp, 24, 24, true) }.getOrNull() ?: return null
        val hsv = FloatArray(3)
        var best = -1f
        var bestColor: Int? = null
        val buckets = HashMap<Int, Float>()
        for (x in 0 until small.width) for (y in 0 until small.height) {
            val c = small.getPixel(x, y)
            Color.colorToHSV(c, hsv)
            if (hsv[2] < 0.25f || hsv[1] < 0.25f) continue
            val bucket = (hsv[0] / 20f).toInt()
            buckets[bucket] = (buckets[bucket] ?: 0f) + hsv[1] * hsv[2]
        }
        val topBucket = buckets.maxByOrNull { it.value }?.key ?: return null
        for (x in 0 until small.width) for (y in 0 until small.height) {
            val c = small.getPixel(x, y)
            Color.colorToHSV(c, hsv)
            if ((hsv[0] / 20f).toInt() != topBucket) continue
            val score = hsv[1] * hsv[2]
            if (score > best) { best = score; bestColor = c }
        }
        return bestColor?.let {
            Color.colorToHSV(it, hsv)
            hsv[1] = hsv[1].coerceIn(0.55f, 0.9f)
            hsv[2] = hsv[2].coerceIn(0.75f, 1f)
            Color.HSVToColor(hsv)
        }
    }
}
