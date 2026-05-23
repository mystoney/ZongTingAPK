package com.zongting.zongting.data.repository

import com.zongting.zongting.data.api.KuwoService
import com.zongting.zongting.data.api.KuwoApi
import com.zongting.zongting.data.api.NetworkModule
import com.zongting.zongting.data.model.*
import kotlinx.coroutines.Dispatchers
import com.google.gson.JsonParser
import com.google.gson.JsonElement
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import kotlin.text.Regex
import java.nio.charset.Charset
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor() {

    // 歌单详情内存缓存（LRU，最多缓存20个）
    private val playlistCache = object : LinkedHashMap<Long, PlaylistData>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Long, PlaylistData>?): Boolean = size > 20
    }
    private val cacheLock = Any()

    private val api = NetworkModule.kuwoApi
    private val nmobiRetrofit = NetworkModule.nmobiRetrofit
    private val mRetrofit = NetworkModule.mRetrofit
    private val httpClient = NetworkModule.okHttpClient
    private val wwwOkHttp = NetworkModule.wwwOkHttp

    // ==================== 首页数据 ====================

    /** 获取推荐歌单 */
    suspend fun getRecommendPlaylists(id: Int = 0, pn: Int = 1, rn: Int = 30): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getRecommendPlaylists(id, pn, rn)
            if (response.code == 200) {
                Result.success(response.data.list)
            } else {
                Result.failure(Exception("获取推荐歌单失败: ${response.msg}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取轮播图 */
    suspend fun getBanners(): Result<List<Banner>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBanners()
            if (response.code == 200) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("获取轮播图失败: ${response.msg}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 歌单 ====================

    /** 获取歌单详情（带内存缓存） */
    suspend fun getPlaylistDetail(pid: Long, pn: Int = 1, rn: Int = 30): Result<PlaylistData> = withContext(Dispatchers.IO) {
        // 缓存命中（仅第一页走缓存）
        if (pn == 1) {
            synchronized(cacheLock) {
                playlistCache[pid]?.let { cached ->
                    android.util.Log.d("HomeDebug", "getPlaylistDetail: CACHE HIT pid=$pid songs=${cached.musicList.size}")
                    return@withContext Result.success(cached)
                }
            }
        }
        try {
            val response = api.getPlaylistDetail(pid, pn, rn)
            if (response.code == 200) {
                val data = response.data
                // 缓存第一页
                if (pn == 1) {
                    synchronized(cacheLock) {
                        playlistCache[pid] = data
                    }
                }
                Result.success(data)
            } else {
                Result.failure(Exception("获取歌单详情失败: ${response.msg}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 预取歌单详情（后台缓存，不阻塞） */
    suspend fun prefetchPlaylistDetail(pid: Long) {
        withContext(Dispatchers.IO) {
            synchronized(cacheLock) {
                if (playlistCache.containsKey(pid)) return@withContext
            }
            try {
                val response = api.getPlaylistDetail(pid, 1, 30)
                if (response.code == 200) {
                    synchronized(cacheLock) {
                        playlistCache[pid] = response.data
                    }
                    android.util.Log.d("HomeDebug", "prefetchPlaylistDetail: cached pid=$pid songs=${response.data.musicList.size}")
                }
            } catch (_: Exception) {}
        }
    }

    // ==================== 搜索 ====================

    /** 搜索建议词 - 使用 search.kuwo.cn */
    suspend fun searchSuggest(key: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val encodedKey = URLEncoder.encode(key, "UTF-8")
            val url = "http://search.kuwo.cn/r.s?key=$encodedKey&spu=&all=1&pn=0&rn=10&hid=0&type=resp"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "http://www.kuwo.cn/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.success(emptyList())

            // 从搜索结果中提取建议词（使用重复的词作为建议）
            val lines = body.split("\r\n", "\n")
            val suggestions = mutableSetOf<String>()
            for (line in lines) {
                val idx = line.indexOf('=')
                if (idx > 0) {
                    val k = line.substring(0, idx).trim()
                    val v = line.substring(idx + 1).trim()
                    if (k == "NAME" || k == "SONGNAME" || k == "ARTIST") {
                        if (v.isNotBlank()) suggestions.add(v)
                    }
                }
            }
            Result.success(suggestions.toList().take(5))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 搜索歌曲 - 支持指定来源（kuwo 或 netease） */
    suspend fun searchMusic(key: String, source: String = "kuwo", pn: Int = 1, rn: Int = 30): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            when (source) {
                "netease" -> searchMusicNetease(key, pn, rn)
                else -> searchMusicKuwo(key, pn, rn)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 搜索歌曲 - 酷我音乐 API (www.kuwo.cn/search/searchMusicBykeyWord) */
    private suspend fun searchMusicKuwo(key: String, pn: Int = 1, rn: Int = 30): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val encodedKey = URLEncoder.encode(key, "UTF-8")
            val url = "https://www.kuwo.cn/search/searchMusicBykeyWord?vipver=1&client=kt&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&mobi=1&issubtitle=1&show_copyright_off=1&pn=${pn - 1}&rn=$rn&all=$encodedKey"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://www.kuwo.cn/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/96.0.4664.110 Safari/537.36")
                .build()

            val response = wwwOkHttp.newCall(request).execute()
            // 用 bytes() 而不是 byteString().toByteArray()，避免截断
            val bodyBytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("空响应"))
            val body = String(bodyBytes, Charsets.UTF_8)

            android.util.Log.d("KuwoSearch", "status=200 len=${bodyBytes.size} body=${body.take(200)}")

            val jsonObj = try {
                JsonParser().parse(body).asJsonObject
            } catch (e: Exception) {
                // JSON 截断时，截掉末尾损坏部分再试
                val trunc = body.take(61000)
                try {
                    JsonParser().parse(trunc).asJsonObject
                } catch (e2: Exception) {
                    android.util.Log.e("KuwoSearch", "JSON parse failed: ${e2.message}")
                    return@withContext Result.failure(Exception("JSON解析失败: ${e2.message}"))
                }
            }
            val abslist = jsonObj.getAsJsonArray("abslist") ?: run {
                android.util.Log.e("KuwoSearch", "abslist null, keys=${jsonObj.keySet()}")
                return@withContext Result.failure(Exception("未找到相关歌曲"))
            }
            if (abslist.size() == 0) {
                android.util.Log.e("KuwoSearch", "abslist empty, TOTAL=${jsonObj.get("TOTAL")}")
                return@withContext Result.failure(Exception("未找到相关歌曲"))
            }
            android.util.Log.d("KuwoSearch", "abslist size=${abslist.size()}")

            val songs = mutableListOf<Song>()
            for (i in 0 until abslist.size()) {
                try {
                    val s = abslist[i].asJsonObject
                    val ridStr = s.get("MUSICRID")?.asString?.removePrefix("MUSIC_") ?: continue
                    val rid = ridStr.toLongOrNull() ?: continue
                    val duration = s.get("DURATION")?.asString?.toIntOrNull() ?: 0
                    val artistId = s.get("ARTISTID")?.asString?.toLongOrNull() ?: 0L
                    val albumId = s.get("ALBUMID")?.asString?.toLongOrNull() ?: 0L
                    val payInfoJson = s.get("payInfo")?.asJsonObject
                    val nplay = payInfoJson?.get("nplay")?.asString
                    val playable = nplay != "000000000000"

                    val webAlbumpic = s.get("web_albumpic_short")?.asString?.takeIf { it.isNotBlank() }
                    val webArtistpic = s.get("web_artistpic_short")?.asString?.takeIf { it.isNotBlank() }
                    val coverUrl = if (webAlbumpic != null) {
                        "https://img2.kuwo.cn/star/albumcover/$webAlbumpic"
                    } else if (webArtistpic != null) {
                        "https://img2.kuwo.cn/star/starheads/$webArtistpic"
                    } else {
                        s.get("pic120")?.asString ?: ""
                    }

                    songs.add(Song(
                        rid = rid,
                        musicrid = s.get("MUSICRID")?.asString ?: "",
                        name = s.get("NAME")?.asString ?: "",
                        artist = s.get("ARTIST")?.asString ?: "",
                        artistid = artistId,
                        album = s.get("ALBUM")?.asString ?: "",
                        albumid = albumId,
                        duration = duration,
                        songTimeMinutes = "",
                        pic = s.get("pic")?.asString ?: "",
                        pic120 = s.get("pic120")?.asString ?: "",
                        coverUrl = coverUrl,
                        releaseDate = s.get("releaseDate")?.asString ?: "",
                        hasmv = s.get("HAS_MV")?.asInt ?: 0,
                        hasLossless = false,
                        payInfo = null,
                        source = "kuwo",
                        playable = playable,
                        fee = 0
                    ))
                } catch (e: Exception) {
                    android.util.Log.e("KuwoSearch", "song parse error: ${e.message} at i=$i")
                }
            }
            android.util.Log.d("KuwoSearch", "songs parsed=${songs.size}")
            val withArtwork = enrichWithItunesArtwork(songs)
            Result.success(withArtwork)
        } catch (e: Exception) {
            android.util.Log.e("KuwoSearch", "SEARCH FAILED: ${e.javaClass.simpleName} ${e.message}")
            Result.failure(e)
        }
    }

    /** 搜索歌曲 - 网易云音乐 API */
    private suspend fun searchMusicNetease(key: String, pn: Int = 1, rn: Int = 30): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val encodedKey = URLEncoder.encode(key, "UTF-8")
            val pnOffset = (pn - 1) * rn
            val url = "http://music.163.com/api/search/get/web?s=$encodedKey&type=1&offset=$pnOffset&total=true&limit=$rn"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "http://music.163.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))

            val json = JsonParser().parse(body).asJsonObject
            val songsArray = json
                .getAsJsonObject("result")
                ?.getAsJsonArray("songs") ?: return@withContext Result.failure(Exception("未找到相关歌曲"))

            val songs = mutableListOf<Song>()
            val ids = mutableListOf<Long>()
            for (i in 0 until songsArray.size()) {
                val elem = songsArray[i].asJsonObject
                val id = elem.get("id").asLong
                val name = elem.get("name").asString
                val artistsJson = elem.getAsJsonArray("artists")
                val artistName = (0 until artistsJson.size()).joinToString("/") { j ->
                    artistsJson[j].asJsonObject.get("name").asString
                }
                val artistId = if (artistsJson.size() > 0) artistsJson[0].asJsonObject.get("id").asLong else 0L
                val albumObj = elem.getAsJsonObject("album")
                val albumName = albumObj?.get("name")?.asString ?: ""
                val albumId = albumObj?.get("id")?.asLong ?: 0L
                val duration = elem.get("duration")?.asInt ?: 0

                ids.add(id)
                songs.add(Song(
                    rid = id, musicrid = "MUSIC_$id", name = name, artist = artistName,
                    artistid = artistId, album = albumName, albumid = albumId,
                    duration = duration, songTimeMinutes = "", pic = "", pic120 = "",
                    releaseDate = "", hasmv = 0, hasLossless = false, payInfo = null,
                    source = "netease", playable = true
                ))
            }

            if (songs.isEmpty()) {
                return@withContext Result.failure(Exception("未找到相关歌曲"))
            }

            // 批量获取专辑图
            val picMap = mutableMapOf<Long, String>()
            if (ids.isNotEmpty()) {
                try {
                    val batchSize = 20
                    for (batch in ids.chunked(batchSize)) {
                        val idsParam = batch.joinToString(",")
                        val picUrl = "http://music.163.com/api/song/detail?ids=%5B$idsParam%5D"
                        val picRequest = Request.Builder()
                            .url(picUrl)
                            .header("Referer", "http://music.163.com/")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                            .build()
                        val picResponse = httpClient.newCall(picRequest).execute()
                        val picBody = picResponse.body?.string()
                        if (!picBody.isNullOrBlank()) {
                            val picJson = JsonParser().parse(picBody).asJsonObject
                            val detailSongs = picJson.getAsJsonArray("songs")
                            if (detailSongs != null) {
                                for (k in 0 until detailSongs.size()) {
                                    val ds = detailSongs[k].asJsonObject
                                    val did = ds.get("id").asLong
                                    val album = ds.getAsJsonObject("album")
                                    val albumPicUrl = album?.get("picUrl")?.asString ?: ""
                                    if (albumPicUrl.isNotBlank()) {
                                        picMap[did] = albumPicUrl
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val resultSongs = songs.map { song ->
                val picUrl = picMap[song.rid] ?: ""
                song.copy(pic = picUrl, pic120 = picUrl)
            }

            // 批量获取版权信息
            var feeInfoMap = emptyMap<Long, Int>()
            if (ids.isNotEmpty()) {
                try {
                    val idsParam = ids.joinToString(",")
                    val feeUrl = "http://music.163.com/api/song/enhance/player/url?ids=%5B$idsParam%5D&br=320000"
                    val feeRequest = Request.Builder()
                        .url(feeUrl)
                        .header("Referer", "http://music.163.com/")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                        .build()
                    val feeResponse = httpClient.newCall(feeRequest).execute()
                    val feeBody = feeResponse.body?.string()
                    if (!feeBody.isNullOrBlank()) {
                        val feeJson = JsonParser().parse(feeBody).asJsonObject
                        val feeData = feeJson.getAsJsonArray("data")
                        if (feeData != null) {
                            feeInfoMap = (0 until feeData.size()).associate { i ->
                                val item = feeData[i].asJsonObject
                                (item.get("id")?.asLong ?: 0L) to (item.get("fee")?.asInt ?: 0)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val finalSongs = resultSongs.map { song ->
                val songFee = feeInfoMap[song.rid] ?: 0
                song.copy(playable = songFee != 1 && songFee != 8, fee = songFee)
            }

            val withArtwork = enrichWithItunesArtwork(finalSongs)
            Result.success(withArtwork)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    // ==================== 播放地址 ====================

    /** 获取播放地址 - 支持酷我和网易云音乐，失败后重试一次（间隔1秒） */
    suspend fun getPlayUrl(rid: Long, br: String = "320kmp3", source: String = "kuwo"): Result<String> = withContext(Dispatchers.IO) {
        android.util.Log.d("KuwoDebug", "MusicRepository.getPlayUrl ENTER: rid=$rid source=$source br=$br")
        repeat(2) { attempt ->
            if (attempt > 0) {
                android.util.Log.d("KuwoDebug", "getPlayUrl retry $attempt/1 rid=$rid source=$source")
                kotlinx.coroutines.delay(1000)
            }
            try {
                // 网易云音乐歌曲
                if (source == "netease") {
                    val request = Request.Builder()
                        .url("http://music.163.com/api/song/enhance/player/url?ids=%5B$rid%5D&br=320000")
                        .header("Referer", "http://music.163.com/")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: run {
                        if (attempt == 1) return@withContext Result.failure(Exception("空响应"))
                        return@repeat
                    }
                    val json = JsonParser().parse(body).asJsonObject
                    val dataArray = json.getAsJsonArray("data")
                    if (dataArray != null && dataArray.size() > 0) {
                        val songData = dataArray[0].asJsonObject
                        val fee = songData.get("fee")?.asInt ?: 0
                        if (fee == 8) {
                            return@withContext Result.failure(Exception("需要付费才能播放"))
                        }
                        val songUrlElem = songData.get("url")
                        if (songUrlElem != null && !songUrlElem.isJsonNull) {
                            val songUrl = songUrlElem.asString
                            if (songUrl.isNotEmpty()) {
                                return@withContext Result.success(songUrl)
                            }
                        }
                        val code = songData.get("code")?.asInt ?: 0
                        if (attempt == 1) return@withContext Result.failure(Exception("无法获取播放地址 (code=$code)"))
                        return@repeat
                    }
                    if (attempt == 1) return@withContext Result.failure(Exception("无法获取播放地址"))
                    return@repeat
                }

                // 酷我歌曲 - 使用 KuwoService.getPlayUrl（原始工作代码）
                val musicId = rid.toString()
                android.util.Log.d("KuwoDebug", "getPlayUrl kuwo: rid=$musicId attempt=$attempt")
                val songUrl = KuwoService().getPlayUrl(musicId)
                if (songUrl != null && songUrl.startsWith("http")) {
                    android.util.Log.d("KuwoDebug", "getPlayUrl success: $songUrl")
                    return@withContext Result.success(songUrl)
                } else {
                    android.util.Log.d("KuwoDebug", "getPlayUrl failed: url=$songUrl")
                    if (attempt == 1) return@withContext Result.failure(Exception("获取播放地址失败（酷我nmobi返回为空）"))
                    // 第一次失败，进入重试
                }
            } catch (e: Exception) {
                android.util.Log.d("KuwoDebug", "getPlayUrl exception: ${e.message}")
                if (attempt == 1) return@withContext Result.failure(e)
                // 第一次异常，进入重试
            }
        }
        Result.failure(Exception("获取播放地址失败"))
    }

    // ==================== 歌词 ====================

    // 将毫秒数转为 Kuwo 歌词时间格式（如 "420.87" 表示 420秒87厘秒）
    private fun msToKuwoTime(timeMs: Long): String {
        val totalCs = (timeMs / 10).toInt()  // 转为厘秒（1/100秒）
        val seconds = totalCs / 100
        val cs = totalCs % 100
        return "$seconds.${cs.toString().padStart(2, '0')}"
    }

    /** 获取歌词 - source=kuwo 失败时自动用 name+artist 搜索网易云取歌词，失败后重试一次（间隔1秒） */
    suspend fun getLyric(musicId: Long, source: String = "kuwo", name: String = "", artist: String = ""): Result<List<LyricLineRaw>> = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            if (attempt > 0) {
                android.util.Log.d("KuwoDebug", "getLyric retry $attempt/1 musicId=$musicId")
                kotlinx.coroutines.delay(1000)
            }
            try {
                // 网易云音乐歌词（直接用 musicId）
                if (source == "netease") {
                    val result = fetchNeteaseLyric(musicId)
                    if (result.isSuccess) return@withContext result
                    if (attempt == 1) return@withContext result  // 重试后仍失败
                    return@withContext Result.failure(result.exceptionOrNull() ?: Exception("暂无歌词"))
                }

                // 酷我歌词（优先）
                android.util.Log.d("KuwoDebug", "getLyric kuwo: musicId=$musicId name=$name artist=$artist")
                val mApi = mRetrofit.create(KuwoApi::class.java)
                val response = mApi.getLyric(musicId)
                android.util.Log.d("KuwoDebug", "getLyric kuwo response: status=${response.status} data=${response.data}")
                if (response.status == 200) {
                    val lyricData = response.data
                    if (lyricData != null) {
                        val lines = lyricData.lrclist
                        if (!lines.isNullOrEmpty()) {
                            return@withContext Result.success(lines)
                        }
                    }
                }

                // 酷我歌词为空或失败，尝试用歌名+歌手去网易云搜索取歌词
                if (name.isNotBlank() && artist.isNotBlank()) {
                    val searchKeyword = "$name $artist".trim()
                    try {
                        val encodedKey = URLEncoder.encode(searchKeyword, "UTF-8")
                        val pnOffset = 0
                        val url = "http://music.163.com/api/search/get/web?s=$encodedKey&type=1&offset=$pnOffset&total=true&limit=3"

                        val request = Request.Builder()
                            .url(url)
                            .header("Referer", "http://music.163.com/")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                            .build()

                        val searchResponse = httpClient.newCall(request).execute()
                        val searchBody = searchResponse.body?.string()
                        if (!searchBody.isNullOrBlank()) {
                            val searchJson = JsonParser().parse(searchBody).asJsonObject
                            val songsArray = searchJson
                                .getAsJsonObject("result")
                                ?.getAsJsonArray("songs")

                            if (songsArray != null && songsArray.size() > 0) {
                                val firstSong = songsArray[0].asJsonObject
                                val neteaseId = firstSong.get("id").asLong
                                val neteaseResult = fetchNeteaseLyric(neteaseId)
                                if (neteaseResult.isSuccess) return@withContext neteaseResult
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (attempt == 1) {
                    // 重试后仍失败
                    return@withContext Result.failure(Exception("暂无歌词"))
                }
                // 第一次失败，记录日志后进入重试
                android.util.Log.d("KuwoDebug", "getLyric attempt 0 failed, will retry musicId=$musicId")
            } catch (e: Exception) {
                android.util.Log.d("KuwoDebug", "getLyric exception: ${e.message}")
                if (attempt == 1) {
                    return@withContext Result.failure(e)
                }
                // 第一次异常，进入重试
            }
        }
        Result.failure(Exception("获取歌词失败"))
    }

    /** 从网易云获取歌词（给定 songId） */
    private suspend fun fetchNeteaseLyric(musicId: Long): Result<List<LyricLineRaw>> = withContext(Dispatchers.IO) {
        val url = "http://music.163.com/api/song/lyric?id=$musicId&lv=1"
        val request = Request.Builder()
            .url(url)
            .header("Referer", "http://music.163.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
            .build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: run {
            android.util.Log.e("KuwoDebug", "fetchNeteaseLyric: empty body for id=$musicId")
            return@withContext Result.failure(Exception("空响应"))
        }
        android.util.Log.d("KuwoDebug", "fetchNeteaseLyric raw body (${body.length} chars): ${body.take(200)}")
        val json = JsonParser().parse(body).asJsonObject
        val lrcObj = json.get("lrc")?.asJsonObject
        if (lrcObj == null) {
            android.util.Log.e("KuwoDebug", "fetchNeteaseLyric: lrcObj is null. json keys: ${json.keySet()}")
            return@withContext Result.failure(Exception("暂无歌词"))
        }
        val lyricText = lrcObj.get("lyric")?.asString ?: run {
            android.util.Log.e("KuwoDebug", "fetchNeteaseLyric: lyric text is null in lrcObj")
            return@withContext Result.failure(Exception("暂无歌词"))
        }
        if (lyricText.isBlank()) {
            android.util.Log.e("KuwoDebug", "fetchNeteaseLyric: lyric text is blank")
            return@withContext Result.failure(Exception("暂无歌词"))
        }

        val lines = lyricText.lines()
            .filter { it.contains("[") && it.contains("]") }
            .mapNotNull { line ->
                val match = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""").find(line)
                if (match != null) {
                    val (min, sec, ms) = match.destructured
                    val timeMs = min.toInt() * 60 * 1000 + sec.toInt() * 1000 + ms.padEnd(3, '0').toInt()
                    val text = line.substringAfter("]").trim()
                    val timeStr = msToKuwoTime(timeMs.toLong())
                    LyricLineRaw(text, timeStr)
                } else null
            }
        android.util.Log.d("KuwoDebug", "fetchNeteaseLyric parsed ${lines.size} lines")
        if (lines.isEmpty()) return@withContext Result.failure(Exception("暂无歌词"))
        return@withContext Result.success(lines)
    }

    // ==================== 排行榜 ====================

    /** 获取排行榜菜单 - 支持指定来源 */
    suspend fun getBangMenu(source: String = "kuwo"): Result<List<BangCategory>> = withContext(Dispatchers.IO) {
        try {
            when (source) {
                "netease" -> Result.success(getNeteaseBangMenu())
                else -> {
                    val response = api.getBangMenu()
                    if (response.code == 200) {
                        Result.success(response.data)
                    } else {
                        Result.failure(Exception("获取排行榜失败: ${response.msg}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 网易云音乐排行榜菜单 */
    private fun getNeteaseBangMenu() = listOf(
        BangCategory("官方", listOf(
            Bang("3778678", "热歌榜", "http://p4.music.126.net/pcYHpMkdC69VVvWiynNklA==/109951166952853966.jpg", "每日更新", "", ""),
            Bang("3779629", "新歌榜", "http://p3.music.126.net/AiC3XCLJ7rW14_JKV33mJA==/109951166952842444.jpg", "每周更新", "", ""),
            Bang("19723756", "飙升榜", "http://p4.music.126.net/Ei3xBFGv7PnIV9vV8WzdEQ==/109951166952853970.jpg", "实时更新", "", ""),
        )),
        BangCategory("华语", listOf(
            Bang("6691452091", "华语热歌榜", "http://p4.music.126.net/YQqZtc41IQCCrNtJQlK1PQ==/109951169161176385.jpg", "华语歌曲排行", "", ""),
        )),
        BangCategory("全球", listOf(
            Bang("60198", "美国Billboard榜", "http://p4.music.126.net/7aLjMXRu7pZV8O8S6fX7YQ==/109951163249754643.jpg", "每周更新", "", ""),
        )),
        BangCategory("电音/说唱", listOf(
            Bang("3812781", "云音乐电音榜", "http://p4.music.126.net/DdJiNsM1t-VAjBNzRVGf0g==/109951164187474700.jpg", "电音歌曲排行", "", ""),
            Bang("5080386226", "云音乐说唱榜", "http://p4.music.126.net/Hv4IvGhVmaIwYcR0L-B-Ew==/109951166952869404.jpg", "说唱歌曲排行", "", ""),
        )),
    )

    /** 获取排行榜歌曲 - 支持指定来源 */
    suspend fun getBangMusicList(bangId: String, source: String = "kuwo", pn: Int = 1, rn: Int = 30, sourceId: String = ""): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            when (source) {
                "netease" -> getBangMusicListNetease(bangId, pn, rn)
                else -> getBangMusicListKuwo(bangId, pn, rn, sourceId.ifEmpty { bangId })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取排行榜歌曲 - 酷我音乐 API */
    private suspend fun getBangMusicListKuwo(bangId: String, pn: Int = 1, rn: Int = 30, apiBangId: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getBangMusicList(apiBangId, pn, rn)
            if (response.code == 200) {
                val songs = response.data.musicList.map { it.copy(source = "kuwo") }
                Result.success(songs)
            } else {
                Result.failure(Exception("获取排行榜歌曲失败: ${response.msg}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取排行榜歌曲 - 网易云音乐 API */
    private suspend fun getBangMusicListNetease(bangId: String, pn: Int = 1, rn: Int = 50): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val url = "http://music.163.com/api/playlist/detail?id=$bangId&updateTime=&n=$rn"
            val request = Request.Builder()
                .url(url)
                .header("Referer", "http://music.163.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))

            val json = JsonParser().parse(body).asJsonObject
            val result = json.getAsJsonObject("result") ?: return@withContext Result.failure(Exception("数据格式错误"))

            val tracks = result.getAsJsonArray("tracks")
            if (tracks == null || tracks.size() == 0) {
                return@withContext Result.failure(Exception("暂无歌曲"))
            }

            // 获取版权信息
            val privileges = json.getAsJsonArray("privileges") ?: json.getAsJsonArray("privilegeList")
            val feeMap = mutableMapOf<Long, Int>()
            if (privileges != null) {
                for (i in 0 until privileges.size()) {
                    val priv = privileges[i].asJsonObject
                    val privId = priv.get("id")?.asLong ?: continue
                    val fee = priv.get("fee")?.asInt ?: 0
                    feeMap[privId] = fee
                }
            }

            val songs = mutableListOf<Song>()
            for (i in 0 until tracks.size()) {
                val obj = tracks[i].asJsonObject
                val id = obj.get("id").asLong
                val name = obj.get("name").asString
                val duration = obj.get("duration").asInt
                val minutes = duration / 60000
                val seconds = (duration % 60000) / 1000
                val timeStr = String.format("%d:%02d", minutes, seconds)

                val artistsJson = obj.getAsJsonArray("artists") ?: obj.getAsJsonArray("ar")
                val artistName = (0 until artistsJson.size()).joinToString("/") { j ->
                    artistsJson[j].asJsonObject.get("name").asString
                }
                val artistId = if (artistsJson.size() > 0) artistsJson[0].asJsonObject.get("id").asLong else 0L

                val album = obj.getAsJsonObject("album") ?: obj.getAsJsonObject("al")
                val albumName = album?.get("name")?.asString ?: ""
                val albumId = album?.get("id")?.asLong ?: 0L
                val albumPic = album?.get("picUrl")?.asString ?: ""

                val mvid = try { obj.get("mvid")?.asLong ?: 0L } catch (e: Exception) { 0L }
                val fee = feeMap[id] ?: obj.get("fee")?.asInt ?: 0
                val playable = fee != 1 && fee != 8

                songs.add(Song(
                    rid = id, musicrid = "MUSIC_$id", name = name, artist = artistName,
                    artistid = artistId, album = albumName, albumid = albumId,
                    duration = duration, songTimeMinutes = timeStr,
                    pic = albumPic, pic120 = albumPic,
                    releaseDate = "", hasmv = if (mvid > 0) 1 else 0,
                    hasLossless = false, payInfo = null,
                    source = "netease", playable = playable, fee = fee
                ))
            }

            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 歌手 ====================

    /** 获取歌手列表 */
    suspend fun getArtistList(category: Int = 0, pn: Int = 1, rn: Int = 20): Result<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getArtistList(category, rn, pn)
            if (response.code == 200) {
                Result.success(response.data.artistList)
            } else {
                Result.failure(Exception("获取歌手列表失败: ${response.msg}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 专辑 ====================

    /** 获取专辑详情 */
    suspend fun getAlbumInfo(albumId: Long): Result<AlbumInfo> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAlbumInfo(albumId)
            if (response.code == 200) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("获取专辑详情失败: ${response.msg}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== iTunes 封面搜索（优先级：iTunes > 网易云 > 酷我） ====================

    /**
     * 通过 iTunes Search API 获取歌曲封面（600x600高清图）
     * @return iTunes高清封面URL，失败返回null
     */
    private fun fetchItunesArtwork(songName: String, artist: String): String? {
        return try {
            val encoded = URLEncoder.encode("$songName $artist", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=1&country=CN"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JsonParser().parse(body).asJsonObject
            val results = json.getAsJsonArray("results") ?: return null
            if (results.size() == 0) return null
            val artworkUrl = results[0].asJsonObject.get("artworkUrl100")?.asString ?: return null
            // 100x100 -> 600x600 高清封面
            artworkUrl.replace("100x100", "600x600")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 批量从 iTunes 获取封面，优先覆盖 songs 列表中每首歌的 coverUrl
     * 优先级：iTunes > 原有封面（网易云/酷我）
     * @param songs 歌曲列表
     * @param batchSize 每批请求数量（避免限流）
     * @return 封面已更新的歌曲列表
     */
    private suspend fun enrichWithItunesArtwork(songs: List<Song>, batchSize: Int = 10): List<Song> {
        if (songs.isEmpty()) return songs
        val result = songs.toMutableList()
        for (batch in result.chunked(batchSize)) {
            batch.forEach { song ->
                if (song.coverUrl.isNullOrBlank()) {
                    val artwork = fetchItunesArtwork(song.name, song.artist)
                    if (artwork != null) {
                        result[result.indexOf(song)] = song.copy(coverUrl = artwork)
                    }
                }
            }
            kotlinx.coroutines.delay(200) // 避免iTunes限流
        }
        return result
    }
}
