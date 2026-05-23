package com.zongting.zongting.data.model

import com.google.gson.annotations.SerializedName

// ==================== 歌单相关 ====================

data class PlaylistResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: PlaylistData,
    @SerializedName("msg") val msg: String
)

data class PlaylistData(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("img") val img: String,
    @SerializedName("img500") val img500: String,
    @SerializedName("img300") val img300: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("listencnt") val listencnt: Long,
    @SerializedName("total") val total: Int,
    @SerializedName("tag") val tag: String,
    @SerializedName("desc") val desc: String,
    @SerializedName("info") val info: String,
    @SerializedName("musicList") val musicList: List<Song>
)

data class RecommendPlaylistResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: RecommendData,
    @SerializedName("msg") val msg: String
)

data class RecommendData(
    @SerializedName("list") val list: List<Playlist>
)

data class Playlist(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("img") val img: String,
    @SerializedName("img300") val img300: String,
    @SerializedName("img500") val img500: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("total") val total: Int,
    @SerializedName("listencnt") val listencnt: Long,
    @SerializedName("info") val info: String,
    @SerializedName("musicList") val musicList: List<Song> = emptyList()
)

// ==================== 歌曲相关 ====================

data class Song(
    @SerializedName("rid") val rid: Long,
    @SerializedName("musicrid") val musicrid: String,
    @SerializedName("name") val name: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("artistid") val artistid: Long,
    @SerializedName("album") val album: String,
    @SerializedName("albumid") val albumid: Long,
    @SerializedName("duration") val duration: Int,
    @SerializedName("songTimeMinutes") val songTimeMinutes: String,
    @SerializedName("pic") val pic: String,
    @SerializedName("pic120") val pic120: String,
    @SerializedName("coverUrl") val coverUrl: String? = null,  // 搜索结果封面URL（处理过的）
    @SerializedName("releaseDate") val releaseDate: String,
    @SerializedName("hasmv") val hasmv: Int,
    @SerializedName("hasLossless") val hasLossless: Boolean,
    @SerializedName("payInfo") val payInfo: PayInfo?,
    @SerializedName("source") val source: String = "kuwo",  // "kuwo" 或 "netease"
    @SerializedName("playable") val playable: Boolean = true,  // false = 版权限制无法播放
    @SerializedName("fee") val fee: Int = 0  // 0: 免费, 1: VIP, 4: 包月, 8: 需要购买
) {
    val id: Long get() = rid
    // 兼容没有 songTimeMinutes 的情况（某些 Kuwo API 返回空）
    val displayDuration: String get() {
        val t = songTimeMinutes
        if (t.isNotBlank() && t != "0:00") return t
        if (duration > 0) {
            val m = duration / 60000
            val s = (duration % 60000) / 1000
            return String.format("%d:%02d", m, s)
        }
        return "0:00"
    }
    // 是否为 VIP 歌曲（可播放但需要VIP）
    val isVip: Boolean get() = !playable && fee == 1
    // 是否为需购买的歌曲
    val isBuyRequired: Boolean get() = !playable && fee == 8
}

// ==================== 播放地址相关 ====================

data class PlayUrlResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: PlayUrlData?,
    @SerializedName("msg") val msg: String,
    @SerializedName("locationid") val locationid: String
)

data class PlayUrlData(
    @SerializedName("url") val url: String?,
    @SerializedName("format") val format: String?,
    @SerializedName("bitrate") val bitrate: Int?,
    @SerializedName("sig") val sig: String?
)

// ==================== 歌词相关 ====================

data class LyricResponse(
    @SerializedName("data") val data: LyricData,
    @SerializedName("status") val status: Int
)

data class LyricData(
    @SerializedName("lrclist") val lrclist: List<LyricLineRaw>?,
    @SerializedName("songinfo") val songinfo: SongInfo?
)

data class LyricLineRaw(
    // Gson 按声明顺序解析，JSON 格式为 {"lineLyric":"歌词文本","time":"0.0"}
    // 所以先声明 lineLyric 字段映射 JSON 的第一个字段
    @SerializedName("lineLyric") val lineLyric: String,
    @SerializedName("time") val time: String
)

data class SongInfo(
    @SerializedName("name") val name: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String,
    @SerializedName("pic") val pic: String?
)

// ==================== 搜索相关 ====================

/**
 * 酷我搜索原始响应 (www.kuwo.cn/search/searchMusicBykeyWord)
 * 响应为 GBK 编码，需在 NetworkModule 中转换
 */
data class KuwoSearchRawResponse(
    @SerializedName("TOTAL") val total: String,
    @SerializedName("RN") val rn: String,
    @SerializedName("abslist") val abslist: List<KuwoRawSong>
)

data class KuwoRawSong(
    @SerializedName("MUSICRID") val musicRid: String,       // "MUSIC_474953591"
    @SerializedName("NAME") val name: String,
    @SerializedName("ARTIST") val artist: String,
    @SerializedName("ARTISTID") val artistId: String,
    @SerializedName("ALBUM") val album: String,
    @SerializedName("ALBUMID") val albumId: String,
    @SerializedName("DURATION") val duration: String,
    @SerializedName("pic") val pic: String,
    @SerializedName("pic120") val pic120: String,
    @SerializedName("releaseDate") val releaseDate: String,
    @SerializedName("HAS_MV") val hasMv: Int,
    @SerializedName("N_MINFO") val nMinfo: String?,
    @SerializedName("payInfo") val payInfo: PayInfoRaw?
) {
    // 解析 rid: "MUSIC_474953591" -> 474953591
    fun parseRid(): Long = musicRid.removePrefix("MUSIC_").toLongOrNull() ?: 0L

    // 解析专辑图
    fun parseAlbumId(): Long = albumId.toLongOrNull() ?: 0L

    // 解析时长（秒）
    fun parseDuration(): Int = duration.toIntOrNull() ?: 0

    // 解析歌手ID
    fun parseArtistId(): Long = artistId.toLongOrNull() ?: 0L

    // 判断是否可播放（版权检查）
    fun parsePlayable(): Boolean {
        val p = payInfo
        return p?.nplay != "000000000000"
    }
}

data class PayInfoRaw(
    @SerializedName("nplay") val nplay: String?,        // "000000000000" = 不可播放
    @SerializedName("ndown") val ndown: String?,
    @SerializedName("feeType") val feeType: FeeTypeRaw?,
    @SerializedName("paytype") val paytype: Int?
)

data class FeeTypeRaw(
    @SerializedName("song") val song: String?,
    @SerializedName("vip") val vip: String?
)

data class SearchSuggestResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: List<String>,
    @SerializedName("msg") val msg: String
)

data class SearchResultResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: SearchData,
    @SerializedName("msg") val msg: String
)

data class SearchData(
    @SerializedName("list") val list: List<Song>,
    @SerializedName("total") val total: String
)

// ==================== 排行榜相关 ====================

data class BangMenuResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: List<BangCategory>,
    @SerializedName("msg") val msg: String
)

data class BangCategory(
    @SerializedName("name") val name: String,
    @SerializedName("list") val list: List<Bang>
)

data class Bang(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("pic") val pic: String,
    @SerializedName("intro") val intro: String,
    @SerializedName("pub") val pub: String,
    @SerializedName("source") val source: String,
    @SerializedName("sourceid") val sourceId: String = ""
)

data class BangMusicListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: BangMusicData,
    @SerializedName("msg") val msg: String
)

data class BangMusicData(
    @SerializedName("num") val num: String,
    @SerializedName("musicList") val musicList: List<Song>
)

// ==================== 轮播图相关 ====================

data class BannerResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: List<Banner>,
    @SerializedName("msg") val msg: String
)

data class Banner(
    @SerializedName("id") val id: Int,
    @SerializedName("pic") val pic: String,
    @SerializedName("newPic") val newPic: String,
    @SerializedName("newPicText") val newPicText: String,
    @SerializedName("url") val url: String,
    @SerializedName("priority") val priority: Int
)

// ==================== 歌手/专辑相关 ====================

data class ArtistListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: ArtistData,
    @SerializedName("msg") val msg: String
)

data class ArtistData(
    @SerializedName("total") val total: String,
    @SerializedName("artistList") val artistList: List<Artist>
)

data class Artist(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("aartist") val aartist: String,
    @SerializedName("pic") val pic: String,
    @SerializedName("pic120") val pic120: String,
    @SerializedName("artistFans") val artistFans: Long,
    @SerializedName("musicNum") val musicNum: Int,
    @SerializedName("albumNum") val albumNum: Int
)

data class AlbumInfoResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: AlbumInfo,
    @SerializedName("msg") val msg: String
)

data class AlbumInfo(
    @SerializedName("albumid") val albumid: Long,
    @SerializedName("album") val album: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("artistid") val artistid: Long,
    @SerializedName("pic") val pic: String,
    @SerializedName("releaseDate") val releaseDate: String,
    @SerializedName("lang") val lang: String,
    @SerializedName("playCnt") val playCnt: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("albuminfo") val albuminfo: String,
    @SerializedName("musicList") val musicList: List<Song>
)

// ==================== 辅助类 ====================

data class PayInfo(
    @SerializedName("play") val play: String,
    @SerializedName("feeType") val feeType: FeeType?
)

data class FeeType(
    @SerializedName("song") val song: String,
    @SerializedName("vip") val vip: String
)

// ==================== 用户自定义歌单 ====================

data class UserPlaylist(
    val id: String,           // UUID
    val name: String,
    val songs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
