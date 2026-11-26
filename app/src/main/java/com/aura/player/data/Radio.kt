package com.aura.player.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Встроенные популярные станции 5 стран. У многих — несколько потоков разного битрейта.
 *
 * Особые адреса (крупные корейские вещатели выдают ссылку с одноразовым токеном):
 *   mbc:<канал>       — ссылка запрашивается у MBC перед воспроизведением
 *   sbs:<путь>        — ссылка запрашивается у SBS
 *   kbs:<код канала>  — ссылка запрашивается у KBS
 */
object RadioCatalog {

    private fun s(id: String, name: String, country: String, genre: String, vararg streams: Pair<Int, String>) =
        Station(id, name, country, genre, streams.map { StreamOption(it.first, it.second, codecOf(it.second)) })

    private fun codecOf(url: String) = when {
        url.contains("m3u8", true) || url.startsWith("mbc:") || url.startsWith("sbs:") || url.startsWith("kbs:") -> "HLS"
        url.contains("aac", true) -> "AAC"
        url.contains("opus", true) || url.contains("ogg", true) || url.endsWith("/stream") -> "OGG"
        else -> "MP3"
    }

    val defaults: List<Station> = listOf(
        // ---------------- Россия ----------------
        s("ru_europa", "Европа Плюс", "RU", "Поп · Хиты",
            128 to "https://ep128.hostingradio.ru:8030/ep128",
            256 to "https://ep256.hostingradio.ru:8052/europaplus256.mp3"),
        s("ru_europa_top", "Европа Плюс Top 40", "RU", "Чарт",
            256 to "https://europaplus.hostingradio.ru:8014/ep-top256.mp3"),
        s("ru_rusradio", "Русское Радио", "RU", "Русская поп-музыка",
            96 to "https://rusradio.hostingradio.ru/rusradio96.aacp",
            128 to "https://rusradio.hostingradio.ru/rusradio128.mp3"),
        s("ru_record", "Радио Record", "RU", "Электроника · Dance",
            96 to "https://radiorecord.hostingradio.ru/rr_main96.aacp"),
        s("ru_record_gold", "Record Russian Gold", "RU", "Русские хиты",
            96 to "https://radiorecord.hostingradio.ru/russiangold96.aacp"),
        s("ru_retro", "Ретро FM", "RU", "70-е · 80-е · 90-е",
            128 to "http://retroserver.streamr.ru:8043/retro128",
            256 to "https://retro.hostingradio.ru:8043/retro256.mp3",
            320 to "https://retro.hostingradio.ru:8014/retro320.mp3"),
        s("ru_dfm", "DFM", "RU", "Dance",
            96 to "https://dfm.hostingradio.ru/dfm96.aacp",
            128 to "https://dfm.hostingradio.ru/dfm128.mp3"),
        s("ru_hitfm", "Хит FM", "RU", "Хиты",
            96 to "https://hitfm.hostingradio.ru/hitfm96.aacp",
            128 to "https://hitfm.hostingradio.ru/hitfm128.mp3"),
        s("ru_maximum", "Радио Maximum", "RU", "Рок · Поп",
            96 to "https://maximum.hostingradio.ru/maximum96.aacp",
            128 to "https://maximum.hostingradio.ru/maximum128.mp3"),
        s("ru_montecarlo", "Радио Монте-Карло", "RU", "Lounge · Поп",
            96 to "https://montecarlo.hostingradio.ru/montecarlo96.aacp",
            128 to "https://montecarlo.hostingradio.ru/montecarlo128.mp3"),
        s("ru_nashe", "Наше Радио", "RU", "Русский рок",
            128 to "https://nashe1.hostingradio.ru/nashe-128.mp3",
            256 to "https://nashe1.hostingradio.ru/nashe-256"),
        s("ru_rock", "ROCK FM", "RU", "Рок",
            128 to "https://nashe1.hostingradio.ru/rock-128.mp3"),
        s("ru_jazz", "Радио JAZZ", "RU", "Джаз",
            128 to "https://nashe1.hostingradio.ru/jazz-128.mp3"),
        s("ru_dorognoe", "Дорожное радио", "RU", "Поп · Ретро",
            128 to "http://dorognoe.hostingradio.ru:8000/dorognoe"),
        s("ru_chanson", "Радио Шансон", "RU", "Шансон",
            128 to "http://chanson.hostingradio.ru:8041/chanson128.mp3",
            256 to "https://chanson.hostingradio.ru:8041/chanson256.mp3"),
        s("ru_mayak", "Маяк", "RU", "Разговорное · Музыка",
            64 to "https://icecast-vgtrk.cdnvideo.ru/mayakfm_aac_64kbps",
            192 to "https://icecast-vgtrk.cdnvideo.ru/mayakfm_mp3_192kbps",
            320 to "https://icecast-vgtrk.cdnvideo.ru/mayakfm"),
        s("ru_vesti", "Вести FM", "RU", "Новости",
            32 to "https://icecast-vgtrk.cdnvideo.ru/vestifm_aac_32kbps",
            64 to "https://icecast-vgtrk.cdnvideo.ru/vestifm_aac_64kbps",
            192 to "https://icecast-vgtrk.cdnvideo.ru/vestifm_mp3_192kbps"),

        // ---------------- США ----------------
        s("us_rp", "Radio Paradise", "US", "Rock · Eclectic",
            64 to "https://stream.radioparadise.com/aac-64",
            128 to "https://stream.radioparadise.com/mp3-128",
            192 to "https://stream.radioparadise.com/mp3-192",
            320 to "https://stream.radioparadise.com/aac-320"),
        s("us_groove", "SomaFM Groove Salad", "US", "Chillout · Ambient",
            64 to "https://ice1.somafm.com/groovesalad-64-aac",
            128 to "https://ice1.somafm.com/groovesalad-128-mp3",
            256 to "https://ice1.somafm.com/groovesalad-256-mp3"),
        s("us_80s", "SomaFM Underground 80s", "US", "80s · Synthpop",
            128 to "https://ice1.somafm.com/u80s-128-mp3",
            256 to "https://ice1.somafm.com/u80s-256-mp3"),
        s("us_defcon", "SomaFM DEF CON", "US", "Electronic",
            128 to "https://ice1.somafm.com/defcon-128-mp3",
            256 to "https://ice1.somafm.com/defcon-256-mp3"),
        s("us_drone", "SomaFM Drone Zone", "US", "Ambient",
            128 to "https://ice1.somafm.com/dronezone-128-mp3",
            256 to "https://ice1.somafm.com/dronezone-256-mp3"),
        s("us_lush", "SomaFM Lush", "US", "Vocal chill",
            128 to "https://ice1.somafm.com/lush-128-mp3"),
        s("us_kexp", "KEXP 90.3 Seattle", "US", "Indie · Alternative",
            64 to "https://kexp.streamguys1.com/kexp64.aac",
            128 to "https://kexp-mp3-128.streamguys1.com/kexp128.mp3",
            160 to "https://kexp.streamguys1.com/kexp160.aac"),
        s("us_npr", "NPR 24/7", "US", "News · Talk",
            96 to "http://npr-ice.streamguys1.com/live.mp3"),
        s("us_smooth", "101 Smooth Jazz", "US", "Smooth jazz",
            128 to "http://jking.cdnstream1.com/b22139_128mp3"),
        s("us_power181", "181.fm Power 181", "US", "Top 40",
            128 to "http://listen.181fm.com/181-power_128k.mp3"),
        s("us_vinyl", "Classic Vinyl HD", "US", "Oldies · 50s-70s",
            320 to "https://icecast.walmradio.com:8443/classic"),

        // ---------------- Германия ----------------
        s("de_1live", "1LIVE", "DE", "Pop · Hits",
            56 to "https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/56/stream.mp3",
            128 to "https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/128/stream.mp3"),
        s("de_wdr2", "WDR 2", "DE", "Pop · News",
            56 to "https://wdr-wdr2-rheinland.icecastssl.wdr.de/wdr/wdr2/rheinland/mp3/56/stream.mp3",
            128 to "https://wdr-wdr2-rheinland.icecastssl.wdr.de/wdr/wdr2/rheinland/mp3/128/stream.mp3"),
        s("de_bayern3", "Bayern 3", "DE", "Pop",
            56 to "https://dispatcher.rndfnk.com/br/br3/live/mp3/low",
            128 to "https://dispatcher.rndfnk.com/br/br3/live/mp3/mid",
            256 to "https://dispatcher.rndfnk.com/br/br3/live/mp3/high"),
        s("de_fritz", "Fritz", "DE", "Alternative",
            56 to "https://dispatcher.rndfnk.com/rbb/fritz/live/mp3/low",
            128 to "https://dispatcher.rndfnk.com/rbb/fritz/live/mp3/mid"),
        s("de_swr3", "SWR3", "DE", "Pop · Rock",
            128 to "https://liveradio.swr.de/sw282p3/swr3/play.mp3"),
        s("de_njoy", "N-JOY", "DE", "Charts",
            128 to "https://icecast.ndr.de/ndr/njoy/live/mp3/128/stream.mp3"),
        s("de_ndr2", "NDR 2", "DE", "Pop",
            128 to "https://icecast.ndr.de/ndr/ndr2/niedersachsen/mp3/128/stream.mp3"),
        s("de_antenne", "Antenne Bayern", "DE", "Hits",
            128 to "https://stream.antenne.de/antenne/stream/mp3"),
        s("de_ffh", "Hit Radio FFH", "DE", "Hits",
            128 to "http://mp3.ffh.de/radioffh/hqlivestream.mp3"),
        s("de_sunshine", "Sunshine Live", "DE", "Electronic · Dance",
            192 to "http://stream.sunshine-live.de/live/mp3-192/stream.sunshine-live.de/"),
        s("de_bigfm", "bigFM", "DE", "Hip-hop · Charts",
            128 to "https://streams.bigfm.de/bigfm-deutschland-128-mp3"),
        s("de_bob", "Radio BOB!", "DE", "Rock",
            192 to "https://streams.radiobob.de/bob-national/mp3-192/mediaplayer"),
        s("de_dlf", "Deutschlandfunk", "DE", "News · Kultur",
            24 to "https://st01.sslstream.dlf.de/dlf/01/low/opus/stream.opus",
            128 to "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3",
            256 to "https://st01.sslstream.dlf.de/dlf/01/high/aac/stream.aac"),

        // ---------------- Франция ----------------
        s("fr_fip", "FIP", "FR", "Eclectic",
            32 to "https://icecast.radiofrance.fr/fip-lofi.aac",
            128 to "https://icecast.radiofrance.fr/fip-midfi.mp3",
            192 to "https://icecast.radiofrance.fr/fip-hifi.aac"),
        s("fr_inter", "France Inter", "FR", "Généraliste",
            32 to "https://icecast.radiofrance.fr/franceinter-lofi.mp3",
            128 to "https://icecast.radiofrance.fr/franceinter-midfi.mp3",
            192 to "https://icecast.radiofrance.fr/franceinter-hifi.aac"),
        s("fr_nrj", "NRJ", "FR", "Hits",
            128 to "https://streaming.nrjaudio.fm/ou5k6tirv93h"),
        s("fr_nostalgie", "Nostalgie", "FR", "Oldies · 80s",
            320 to "https://streaming.nrjaudio.fm/oua8a3w2dqao"),
        s("fr_rire", "Rire & Chansons", "FR", "Humour · Pop",
            128 to "https://streaming.nrjaudio.fm/ousguym4z85f"),
        s("fr_cherie", "Chérie FM", "FR", "Pop · Love",
            128 to "https://cdnradio.streamakaci.com/cheriefm1.mp3"),
        s("fr_skyrock", "Skyrock", "FR", "Rap · R&B",
            64 to "http://icecast.skyrock.net/s/natio_aac_64k",
            128 to "http://icecast.skyrock.net/s/natio_mp3_128k"),
        s("fr_rtl", "RTL", "FR", "Généraliste",
            64 to "http://icecast.rtl.fr/rtl-1-44-64",
            128 to "http://icecast.rtl.fr/rtl-1-44-128"),
        s("fr_rtl2", "RTL2", "FR", "Pop · Rock",
            128 to "http://icecast.rtl2.fr/rtl2-1-44-128"),
        s("fr_fun", "Fun Radio", "FR", "Dance · Electro",
            128 to "http://icecast.funradio.fr/fun-1-44-128"),
        s("fr_europe1", "Europe 1", "FR", "Info · Talk",
            128 to "https://europe1.lmn.fm/europe1.mp3"),
        s("fr_europe2", "Europe 2", "FR", "Pop",
            128 to "http://europe2.lmn.fm/europe2.mp3"),
        s("fr_rfm", "RFM", "FR", "Pop · 80s",
            128 to "http://stream.rfm.fr/rfm.mp3"),
        s("fr_nova", "Radio Nova", "FR", "Groove · Electro",
            128 to "http://novazz.ice.infomaniak.ch/novazz-128.mp3"),
        s("fr_ouifm", "OUI FM", "FR", "Rock",
            128 to "https://ouifm.ice.infomaniak.ch/ouifm-high.mp3"),
        s("fr_classique", "Radio Classique", "FR", "Classique",
            128 to "https://radioclassique.ice.infomaniak.ch/radioclassique-high.mp3"),
        s("fr_jazz", "Jazz Radio", "FR", "Jazz",
            64 to "https://jazzradio.ice.infomaniak.ch/jazzradio-high.aac"),
        s("fr_musique", "France Musique", "FR", "Classique · Jazz",
            128 to "https://icecast.radiofrance.fr/francemusique-midfi.mp3",
            192 to "https://icecast.radiofrance.fr/francemusique-hifi.aac"),
        s("fr_mouv", "Mouv'", "FR", "Hip-hop",
            128 to "https://icecast.radiofrance.fr/mouv-midfi.mp3",
            192 to "https://icecast.radiofrance.fr/mouv-hifi.aac"),

        // ---------------- Южная Корея ----------------
        s("kr_kbs_cool", "KBS Cool FM", "KR", "K-pop · Хиты", 0 to "kbs:25"),
        s("kr_kbs_classic", "KBS Classic FM", "KR", "Классика", 0 to "kbs:24"),
        s("kr_mbc_fm4u", "MBC FM4U", "KR", "K-pop · Поп", 0 to "mbc:mfm"),
        s("kr_mbc_fm", "MBC 표준FM", "KR", "Разговорное · Музыка", 0 to "mbc:sfm"),
        s("kr_mbc_allthat", "MBC All That Music", "KR", "Музыка 24/7", 0 to "mbc:chm"),
        s("kr_sbs_power", "SBS Power FM", "KR", "K-pop · Хиты", 0 to "sbs:powerpc/powerfm"),
        s("kr_sbs_love", "SBS Love FM", "KR", "Поп · Баллады", 0 to "sbs:lovepc/lovefm"),
        s("kr_cbs_music", "CBS Music FM 93.9", "KR", "Поп · Джаз",
            0 to "https://m-aac.cbs.co.kr/cbs939/_definst_/cbs939.stream/playlist.m3u8"),
        s("kr_cbs_std", "CBS 98.1", "KR", "Разговорное",
            0 to "https://m-aac.cbs.co.kr/mweb_cbs981/_definst_/cbs981.stream/playlist.m3u8"),
        s("kr_ytn", "YTN Radio", "KR", "Новости",
            0 to "https://radiolive.ytn.co.kr/radio/_definst_/20211118_fmlive/playlist.m3u8"),
        s("kr_ebs", "EBS FM", "KR", "Образование · Английский",
            0 to "https://ebsonair.ebs.co.kr/fmradiofamilypc/familypc1m/playlist.m3u8"),
        s("kr_gugak", "Gugak FM", "KR", "Традиционная музыка",
            0 to "https://mgugaklive.nowcdn.co.kr/gugakradio/gugakradio.stream/playlist.m3u8"),
        s("kr_listenmoe", "LISTEN.moe K-pop", "KR", "K-pop 24/7",
            128 to "https://listen.moe/kpop/stream",
            192 to "https://listen.moe/kpop/fallback"),
        s("kr_bigb", "Big B Radio K-pop", "KR", "K-pop",
            192 to "https://antares.dribbcast.com/proxy/kpop?mp=/s"),
    )
}

/** Онлайн-каталог radio-browser.info — тысячи станций со всего мира с реальными битрейтами */
object RadioBrowser {
    private val hosts = listOf(
        "de1.api.radio-browser.info",
        "nl1.api.radio-browser.info",
        "at1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "all.api.radio-browser.info",
    )

    fun search(countryCode: String?, query: String?, limit: Int = 80): List<Station> {
        val params = buildString {
            append("order=clickcount&reverse=true&hidebroken=true&limit=").append(limit)
            if (!countryCode.isNullOrBlank()) append("&countrycode=").append(countryCode)
            if (!query.isNullOrBlank()) append("&name=").append(URLEncoder.encode(query.trim(), "UTF-8"))
        }
        var last: Exception? = null
        for (h in hosts) {
            try {
                return parse(Net.get("https://$h/json/stations/search?$params"))
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Каталог недоступен")
    }

    private fun parse(json: String): List<Station> {
        val arr = JSONArray(json)
        // Одинаковые станции с разным битрейтом объединяем → появляется выбор битрейта
        val groups = LinkedHashMap<String, MutableList<Pair<JSONObject, StreamOption>>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").trim()
            if (name.isBlank()) continue
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            if (url.isBlank()) continue
            val key = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
            var br = o.optInt("bitrate")
            if (br > 2000) br /= 1000
            groups.getOrPut(key) { mutableListOf() } += o to StreamOption(br, url, o.optString("codec").uppercase())
        }
        return groups.values.map { list ->
            val first = list.first().first
            Station(
                id = "rb_" + first.optString("stationuuid"),
                name = first.optString("name").trim(),
                country = first.optString("countrycode").uppercase(),
                genre = first.optString("tags").split(',').filter { it.isNotBlank() }.take(3).joinToString(" · "),
                streams = list.map { it.second }.distinctBy { it.url }.sortedBy { it.bitrate },
            )
        }
    }
}

object Net {
    fun get(url: String, timeout: Int = 10000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout + 5000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AuraPlayer/1.0")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/** Превращает «особые» адреса и плейлисты (.pls/.m3u) в прямую ссылку на поток */
object StreamResolver {
    fun resolve(url: String): String {
        return try {
            when {
                url.startsWith("mbc:") ->
                    Net.get("https://sminiplay.imbc.com/aacplay.ashx?agent=webapp&channel=" + url.removePrefix("mbc:")).trim()
                url.startsWith("sbs:") ->
                    Net.get("https://apis.sbs.co.kr/play-api/1.0/livestream/" + url.removePrefix("sbs:") + "?protocol=hls&ssl=Y").trim()
                url.startsWith("kbs:") -> {
                    val json = JSONObject(Net.get("https://cfpwwwapi.kbs.co.kr/api/v1/landing/live/channel_code/" + url.removePrefix("kbs:")))
                    json.getJSONArray("channel_item").getJSONObject(0).getString("service_url")
                }
                else -> resolvePlaylist(url)
            }
        } catch (e: Exception) {
            if (url.contains(':') && url.substringBefore(':') in setOf("mbc", "sbs", "kbs")) throw IOException("Станция недоступна", e)
            url
        }
    }

    private fun resolvePlaylist(url: String): String {
        val path = url.lowercase().substringBefore('?')
        if (!(path.endsWith(".pls") || path.endsWith(".m3u") || path.endsWith(".asx") || path.endsWith(".xspf"))) return url
        val body = Net.get(url, 8000)
        Regex("(https?://[^\\s\"'<>]+)").findAll(body).map { it.value }.firstOrNull()?.let { return it }
        return url
    }
}
