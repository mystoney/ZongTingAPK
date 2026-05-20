package com.zongting.zongting.data.api

import android.util.Base64
import android.util.Log
import com.zongting.zongting.data.model.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 酷我 kwDES 加解密
 * 完整移植自 kuwo_des.py (yeyuchen198/kuwo_des)
 * 密钥: ylzsxkwm
 */
class KwDES {
    companion object {
        private val KEY = byteArrayOf(121, 108, 122, 115, 120, 107, 119, 109)

        private val MASK = LongArray(64) { 1L shl it }

        private val IP = intArrayOf(
            57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
            61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
            56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
            60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6
        )

        private val E = intArrayOf(
            31, 0, 1, 2, 3, 4, -1, -1, 3, 4, 5, 6, 7, 8, -1, -1,
            7, 8, 9, 10, 11, 12, -1, -1, 11, 12, 13, 14, 15, 16, -1, -1,
            15, 16, 17, 18, 19, 20, -1, -1, 19, 20, 21, 22, 23, 24, -1, -1,
            23, 24, 25, 26, 27, 28, -1, -1, 27, 28, 29, 30, 31, 30, -1, -1
        )

        private val S0 = intArrayOf(14,4,3,15,2,13,5,3,13,14,6,9,11,2,0,5,4,1,10,12,15,6,9,10,1,8,12,7,8,11,7,0,0,15,10,5,14,4,9,10,7,8,12,3,13,1,3,6,15,12,6,11,2,9,5,0,4,2,11,14,1,7,8,13)
        private val S1 = intArrayOf(15,0,9,5,6,10,12,9,8,7,2,12,3,13,5,2,1,14,7,8,11,4,0,3,14,11,13,6,4,1,10,15,3,13,12,11,15,3,6,0,4,10,1,7,8,4,11,14,13,8,0,6,2,15,9,5,7,1,10,12,14,2,5,9)
        private val S2 = intArrayOf(10,13,1,11,6,8,11,5,9,4,12,2,15,3,2,14,0,6,13,1,3,15,4,10,14,9,7,12,5,0,8,7,13,1,2,4,3,6,12,11,0,13,5,14,6,8,15,2,7,10,8,15,4,9,11,5,9,0,14,3,10,7,1,12)
        private val S3 = intArrayOf(7,10,1,15,0,12,11,5,14,9,8,3,9,7,4,8,13,6,2,1,6,11,12,2,3,0,5,14,10,13,15,4,13,3,4,9,6,10,1,12,11,0,2,5,0,13,14,2,8,15,7,4,15,1,10,7,5,6,12,11,3,8,9,14)
        private val S4 = intArrayOf(2,4,8,15,7,10,13,6,4,1,3,12,11,7,14,0,12,2,5,9,10,13,0,3,1,11,15,5,6,8,9,14,14,11,5,6,4,1,3,10,2,12,15,0,13,2,8,5,11,8,0,15,7,14,9,4,12,7,10,9,1,13,6,3)
        private val S5 = intArrayOf(12,9,0,7,9,2,14,1,10,15,3,4,6,12,5,11,1,14,13,0,2,8,7,13,15,5,4,10,8,3,11,6,10,4,6,11,7,9,0,6,4,2,13,1,9,15,3,8,15,3,1,14,12,5,11,0,2,12,14,7,5,10,8,13)
        private val S6 = intArrayOf(4,1,3,10,15,12,5,0,2,11,9,6,8,7,6,9,11,4,12,15,0,3,10,5,14,13,7,8,13,14,1,2,13,6,14,9,4,1,2,14,11,13,5,0,1,10,8,3,0,11,3,5,9,4,15,2,7,8,12,15,10,7,6,12)
        private val S7 = intArrayOf(13,7,10,0,6,9,5,15,8,4,3,10,11,14,12,5,2,11,9,6,15,12,0,3,4,1,14,13,1,2,7,8,1,2,12,15,10,4,0,3,13,14,6,9,7,8,9,6,15,1,5,12,3,10,14,5,8,7,11,0,4,13,2,11)
        private val S_BOXES = arrayOf(S0, S1, S2, S3, S4, S5, S6, S7)

        private val P = intArrayOf(15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24)

        private val IP1 = intArrayOf(
            39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30,
            37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28,
            35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26,
            33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24
        )

        private val PC1 = intArrayOf(
            56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
            58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38,
            30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36,
            28, 20, 12, 4, 27, 19, 11, 3
        )

        private val PC2 = intArrayOf(
            13, 16, 10, 23, 0, 4, -1, -1, 2, 27, 14, 5, 20, 9, -1, -1,
            22, 18, 11, 3, 25, 7, -1, -1, 15, 6, 26, 19, 12, 1, -1, -1,
            40, 51, 30, 36, 46, 54, -1, -1, 29, 39, 50, 44, 32, 47, -1, -1,
            43, 48, 38, 55, 33, 52, -1, -1, 45, 41, 49, 35, 28, 31, -1, -1
        )

        private val LS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
        private val LS_MASK = longArrayOf(0x0L, 0x100001L, 0x300003L)
        private const val MASK32 = 0xFFFFFFFFL

        private fun bitTransform(array: IntArray, length: Int, source: Long): Long {
            var dest = 0L
            for (i in 0 until length) {
                if (array[i] >= 0 && (source and MASK[array[i]]) != 0L) {
                    dest = dest or MASK[i]
                }
            }
            return dest
        }

        private fun desSubKeys(key: Long, k: LongArray, decrypt: Boolean) {
            var temp = bitTransform(PC1, 56, key)
            for (j in 0..15) {
                val lsVal = LS[j]
                val mask = LS_MASK[lsVal]
                val left = (temp and mask) shl (28 - lsVal)
                val right = ((temp and mask.inv()) and ((1L shl 56) - 1)) ushr lsVal
                temp = left or right
                k[j] = bitTransform(PC2, 64, temp)
            }
            if (decrypt) {
                for (j in 0..7) {
                    val tmp = k[j]; k[j] = k[15 - j]; k[15 - j] = tmp
                }
            }
        }

        private fun des64(subKeys: LongArray, data: Long): Long {
            var out = bitTransform(IP, 64, data)
            var left = out and MASK32
            var right = (out ushr 32) and MASK32
            for (i in 0..15) {
                val origRight = right
                var r = bitTransform(E, 64, origRight)
                r = r xor subKeys[i]
                val pR = LongArray(8) { k -> (r ushr (k * 8)) and 0xFF }
                var sOut = 0L
                for (sbi in 7 downTo 0) {
                    sOut = (sOut shl 4) or S_BOXES[sbi][pR[sbi].toInt()].toLong()
                }
                r = bitTransform(P, 32, sOut)
                val newRight = left xor r
                left = origRight
                right = newRight
            }
            val tmp = left; left = right; right = tmp
            val combined = ((right and MASK32) shl 32) or (left and MASK32)
            return bitTransform(IP1, 64, combined)
        }

        private fun bytesToBlock(data: ByteArray, offset: Int): Long {
            var block = 0L
            for (j in 0..7) {
                block = block or ((data[offset + j].toLong() and 0xFFL) shl (j * 8))
            }
            return block
        }

        private fun blockToBytes(block: Long, out: ByteArray, offset: Int) {
            for (j in 0..7) {
                out[offset + j] = ((block ushr (j * 8)) and 0xFFL).toByte()
            }
        }

        fun encryptBytes(plaintext: ByteArray): ByteArray {
            val padLen = if (plaintext.size % 8 != 0) 8 - (plaintext.size % 8) else 0
            val padded = if (padLen > 0) plaintext + ByteArray(padLen) else plaintext
            val num = padded.size / 8
            var keyLong = 0L
            for (i in 0..7) keyLong = keyLong or ((KEY[i].toLong() and 0xFFL) shl (i * 8))
            val subKey = LongArray(16)
            desSubKeys(keyLong, subKey, decrypt = false)
            val encrypted = ByteArray(num * 8)
            for (i in 0 until num) {
                val block = bytesToBlock(padded, i * 8)
                val cipher = des64(subKey, block)
                blockToBytes(cipher, encrypted, i * 8)
            }
            return encrypted
        }

        fun decryptBytes(ciphertext: ByteArray): ByteArray {
            val num = ciphertext.size / 8
            var keyLong = 0L
            for (i in 0..7) keyLong = keyLong or ((KEY[i].toLong() and 0xFFL) shl (i * 8))
            val subKey = LongArray(16)
            desSubKeys(keyLong, subKey, decrypt = true)
            val decrypted = ByteArray(num * 8)
            for (i in 0 until num) {
                val block = bytesToBlock(ciphertext, i * 8)
                val plain = des64(subKey, block)
                blockToBytes(plain, decrypted, i * 8)
            }
            return decrypted
        }

        fun encrypt(plaintext: String): String {
            return Base64.encodeToString(encryptBytes(plaintext.toByteArray(Charsets.UTF_8)), Base64.DEFAULT)
        }

        fun decrypt(ciphertextB64: String): String {
            val raw = Base64.decode(ciphertextB64, Base64.NO_WRAP)
            val decrypted = decryptBytes(raw)
            var end = decrypted.size - 1
            while (end >= 0 && decrypted[end] == 0.toByte()) end--
            return String(decrypted, 0, end + 1, Charsets.UTF_8)
        }
    }
}

/**
 * 酷我音乐 API 服务
 * 基于 KuwoApiService.kt (用户提供的完整参考实现) 适配到 ZongTing
 */
class KuwoService {

    companion object {
        private const val REFERER = "http://www.kuwo.cn/"
        private val KUWO_UA = "kwplayerhd_ar_4.3.0.8_tianbao_T1A_qirui"
        private val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        init {
            // kwDES 自测验证
            val params = "user=0&android_id=0&prod=kwplayerhd_ar_4.3.0.8&corp=kuwo&vipver=4.3.0.8&source=kwplayerhd_ar_4.3.0.8_tianbao_T1A_qirui.apk&notrace=0&type=convert_url2&br=320&format=mp3&sig=0&rid=29530546&priority=bitrate&loginUid=0&network=WIFI&loginSid=0&mode=down"
            val encrypted = KwDES.encrypt(params)
            val decrypted = KwDES.decrypt(encrypted)
            Log.d("KuwoApi", "kwDES encrypt: $encrypted")
            Log.d("KuwoApi", "kwDES decrypt roundtrip OK: ${params == decrypted}")
        }
    }

    private val browserClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val playerClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", KUWO_UA)
                .addHeader("Referer", REFERER)
                .build()
            chain.proceed(request)
        }
        .build()

    private fun get(urlStr: String): String {
        val request = Request.Builder()
            .url(urlStr)
            .addHeader("User-Agent", BROWSER_UA)
            .addHeader("Referer", REFERER)
            .build()
        return browserClient.newCall(request).execute().body?.string() ?: ""
    }

    private fun getWithKuwoUA(urlStr: String): String {
        val request = Request.Builder()
            .url(urlStr)
            .addHeader("User-Agent", KUWO_UA)
            .addHeader("Referer", REFERER)
            .build()
        return playerClient.newCall(request).execute().body?.string() ?: ""
    }

    // ==================== 推荐歌单 ====================

    /**
     * 获取推荐歌单
     */
    fun getRecommendPlaylists(pn: Int = 1, rn: Int = 20): List<Playlist> {
        return try {
            val url = "http://wapi.kuwo.cn/api/www/rcm/index/playlist?id=0&pn=$pn&rn=$rn"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return emptyList()
            val list = data.optJSONArray("list") ?: return emptyList()
            (0 until list.length()).mapNotNull { i ->
                playlistFromJson(list.getJSONObject(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== 歌单详情 ====================

    /**
     * 获取歌单详情
     */
    fun getPlaylistDetail(pid: String, pn: Int = 1, rn: Int = 50): Playlist? {
        return try {
            val url = "http://wapi.kuwo.cn/api/www/playlist/playListInfo?pid=$pid&pn=$pn&rn=$rn"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return null
            val playlist = playlistFromJson(data) ?: return null
            val musicListArray = data.optJSONArray("musicList") ?: return playlist
            val songs = (0 until musicListArray.length()).mapNotNull { i ->
                songFromPlaylistJson(musicListArray.getJSONObject(i))
            }
            playlist.copy(musicList = songs)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== 搜索 ====================

    /**
     * 搜索歌曲
     */
    fun searchSongs(keyword: String, pn: Int = 0, rn: Int = 30): List<Song> {
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://www.kuwo.cn/search/searchMusicBykeyWord?all=$encoded&pn=$pn&rn=$rn&strategy=2012&rformat=json&client=kt&mobi=1&ft=music"
            val response = get(url)
            val json = JSONObject(response)
            val abslist = json.optJSONArray("abslist")
            if (abslist != null && abslist.length() > 0) {
                return (0 until abslist.length()).mapNotNull { i ->
                    songFromSearchJson(abslist.getJSONObject(i))
                }
            }
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ==================== 搜索联想 ====================

    /**
     * 获取搜索联想词
     */
    fun getSearchSuggestions(keyword: String): List<String> {
        return try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "http://wapi.kuwo.cn/api/www/search/searchKey?key=$encoded"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: return emptyList()
            (0 until data.length()).mapNotNull { i ->
                data.optJSONObject(i)?.optString("word")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== 播放地址 ====================

    /**
     * 获取播放地址 — 使用完整参数 + kwDES 加密
     * 对齐 Python get_media_url() 实现
     * 关键：必须使用完整参数串，否则 API 只返回 bitrate=1 的12秒样本
     */
    fun getPlayUrl(rid: String): String? {
        return try {
            val musicId = rid.removePrefix("MUSIC_")
            val qParams = "user=0&android_id=0&prod=kwplayerhd_ar_4.3.0.8&corp=kuwo&vipver=4.3.0.8" +
                    "&source=kwplayerhd_ar_4.3.0.8_tianbao_T1A_qirui.apk&notrace=0" +
                    "&type=convert_url2&br=320&format=mp3&sig=0&rid=$musicId" +
                    "&priority=bitrate&loginUid=0&network=WIFI&loginSid=0&mode=down"
            val encrypted = KwDES.encrypt(qParams)
            val encoded = URLEncoder.encode(encrypted, "UTF-8")
            val url = "https://nmobi.kuwo.cn/mobi.s?f=kuwo&q=$encoded"
            Log.d("KuwoApi", "getPlayUrl rid=$musicId")
            Log.d("KuwoApi", "qParams=$qParams")
            Log.d("KuwoApi", "encrypted_q=$encrypted")
            Log.d("KuwoApi", "full_url=$url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", KUWO_UA)
                .addHeader("Icy-MetaData", "1")
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .addHeader("Host", "nmobi.kuwo.cn")
                .addHeader("Connection", "Keep-Alive")
                .addHeader("Accept-Encoding", "identity")
                .addHeader("Referer", REFERER)
                .build()
            val response = playerClient.newCall(request).execute().body?.string() ?: ""
            Log.d("KuwoApi", "response=$response")

            val match = Regex("url=(https?://[^\\s]+mp3)\\?").find(response)
            if (match != null) {
                match.groupValues[1]
            } else if (response.trim().startsWith("http")) {
                response.trim()
            } else null
        } catch (e: Exception) {
            Log.e("KuwoApi", "getPlayUrl error", e)
            null
        }
    }

    // ==================== 歌词 ====================

    /**
     * 获取歌词
     */
    fun getLyrics(musicId: String): List<LyricLineRaw> {
        return try {
            val url = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$musicId&httpsStatus=1"
            val response = get(url)
            val json = JSONObject(response)
            if (json.optInt("status") != 200) return emptyList()
            val data = json.optJSONObject("data") ?: return emptyList()
            val lrclist = data.optJSONArray("lrclist") ?: return emptyList()
            (0 until lrclist.length()).mapNotNull { i ->
                val item = lrclist.getJSONObject(i)
                val time = item.optString("time", "0")
                val text = item.optString("lineLyric", "").trim()
                if (text.isNotEmpty()) LyricLineRaw(text, time) else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== 排行榜 ====================

    /**
     * 获取排行榜菜单
     */
    fun getBangMenu(): List<Playlist> {
        return try {
            val url = "http://wapi.kuwo.cn/api/www/bang/bang/bangMenu"
            val response = get(url)
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: return emptyList()
            val playlists = mutableListOf<Playlist>()
            for (i in 0 until data.length()) {
                val category = data.getJSONObject(i)
                val list = category.optJSONArray("list") ?: continue
                for (j in 0 until list.length()) {
                    playlistFromJson(list.getJSONObject(j))?.let { playlists.add(it) }
                }
            }
            playlists
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== 模型解析 ====================

    private fun playlistFromJson(json: JSONObject): Playlist? {
        return try {
            Playlist(
                id = json.optLong("id", 0),
                name = json.optString("name", ""),
                img = json.optString("img", ""),
                img300 = json.optString("img300", ""),
                img500 = json.optString("img500", ""),
                userName = json.optString("userName", json.optString("nickname", "")),
                total = json.optInt("total", 0),
                listencnt = json.optLong("listencnt", 0),
                info = json.optString("info", ""),
                musicList = emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun songFromSearchJson(json: JSONObject): Song? {
        return try {
            val musicRid = json.optString("MUSICRID", "")
            val rid = musicRid.removePrefix("MUSIC_").toLongOrNull() ?: 0L
            val duration = json.optString("DURATION", "0").toIntOrNull() ?: 0
            val pic = json.optString("pic", "")
            val payInfoJson = json.optJSONObject("payInfo")
            val playable = payInfoJson?.optString("nplay", "000000000000") != "000000000000"
            Song(
                rid = rid,
                musicrid = musicRid,
                name = json.optString("NAME", ""),
                artist = json.optString("ARTIST", ""),
                artistid = json.optString("ARTISTID", "0").toLongOrNull() ?: 0L,
                album = json.optString("ALBUM", ""),
                albumid = json.optString("ALBUMID", "0").toLongOrNull() ?: 0L,
                duration = duration,
                songTimeMinutes = "${duration / 60}:${(duration % 60).toString().padStart(2, '0')}",
                pic = pic,
                pic120 = json.optString("pic120", pic),
                releaseDate = json.optString("releaseDate", ""),
                hasmv = json.optInt("HAS_MV", 0),
                hasLossless = false,
                payInfo = null,
                source = "kuwo",
                playable = playable,
                fee = 0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun songFromPlaylistJson(json: JSONObject): Song? {
        return try {
            val musicRid = json.optString("musicrid", json.optString("MUSICRID", ""))
            val rid = musicRid.removePrefix("MUSIC_").toLongOrNull()
                ?: json.optLong("rid", 0)
            Song(
                rid = rid,
                musicrid = musicRid,
                name = json.optString("name", json.optString("NAME", "")),
                artist = json.optString("artist", json.optString("ARTIST", "")),
                artistid = json.optLong("artistid", json.optLong("ARTISTID", 0)),
                album = json.optString("album", json.optString("ALBUM", "")),
                albumid = json.optLong("albumid", json.optLong("ALBUMID", 0)),
                duration = json.optInt("duration", json.optString("DURATION", "0").toIntOrNull() ?: 0),
                songTimeMinutes = json.optString("songTimeMinutes", json.optString("songTimeSeconds", "0:00")),
                pic = json.optString("pic", ""),
                pic120 = json.optString("pic120", json.optString("pic", "")),
                releaseDate = "",
                hasmv = 0,
                hasLossless = false,
                payInfo = null,
                source = "kuwo",
                playable = true,
                fee = 0
            )
        } catch (e: Exception) {
            null
        }
    }
}
