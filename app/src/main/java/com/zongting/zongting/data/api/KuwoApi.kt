package com.zongting.zongting.data.api

import com.zongting.zongting.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface KuwoApi {

    companion object {
        const val BASE_URL_WAPI = "http://wapi.kuwo.cn/"
        const val BASE_URL_NMOBI = "https://nmobi.kuwo.cn/"
        const val BASE_URL_M = "http://m.kuwo.cn/"
        const val BASE_URL_SEARCH = "http://search.kuwo.cn/"
    }

    // ==================== 歌单 ====================

    /**
     * 获取推荐歌单列表
     */
    @GET("api/www/rcm/index/playlist")
    suspend fun getRecommendPlaylists(
        @Query("id") id: Int = 0,
        @Query("pn") pn: Int = 1,
        @Query("rn") rn: Int = 30
    ): RecommendPlaylistResponse

    /**
     * 获取歌单详情
     */
    @GET("api/www/playlist/playListInfo")
    suspend fun getPlaylistDetail(
        @Query("pid") pid: Long,
        @Query("pn") pn: Int = 1,
        @Query("rn") rn: Int = 30
    ): PlaylistResponse

    // ==================== 搜索 ====================

    /**
     * 搜索关键词提示
     */
    @GET("api/www/search/searchKey")
    suspend fun searchSuggest(
        @Query("key") key: String
    ): SearchSuggestResponse

    // ==================== 播放地址 ====================

    /**
     * 获取播放地址（免加密方案A）
     * 音质选项: 320kmp3, 192kmp3, 128kmp3, 2000kflac, ape
     */
    @GET("mobi.s")
    suspend fun getPlayUrl(
        @Query("f") f: String = "web",
        @Query("source") source: String = "kwplayer_ar_8.5.5.0_keluze.apk",
        @Query("type") type: String = "convert_url_with_sign",
        @Query("rid") rid: Long,
        @Query("br") br: String = "320kmp3",
        @Query("user") user: String = "10082"
    ): PlayUrlResponse

    // ==================== 歌词 ====================

    /**
     * 获取歌词（备用接口，无需解密）
     */
    @GET("newh5/singles/songinfoandlrc")
    suspend fun getLyric(
        @Query("musicId") musicId: Long,
        @Query("httpsStatus") httpsStatus: Int = 1
    ): LyricResponse

    // ==================== 排行榜 ====================

    /**
     * 获取排行榜菜单
     */
    @GET("api/www/bang/bang/bangMenu")
    suspend fun getBangMenu(): BangMenuResponse

    /**
     * 获取排行榜歌曲列表
     */
    @GET("api/www/bang/bang/musicList")
    suspend fun getBangMusicList(
        @Query("bangId") bangId: String,
        @Query("pn") pn: Int = 1,
        @Query("rn") rn: Int = 50
    ): BangMusicListResponse

    // ==================== 轮播图 ====================

    /**
     * 获取首页轮播图
     */
    @GET("api/www/banner/index/bannerList")
    suspend fun getBanners(): BannerResponse

    // ==================== 歌手 ====================

    /**
     * 获取歌手列表
     * category: 0=全部 1=华语男 2=华语女 3=华语组合 4=日韩男 5=日韩女 6=日韩组合 7=欧美男 8=欧美女 9=欧美组合
     */
    @GET("api/www/artist/artistInfo")
    suspend fun getArtistList(
        @Query("category") category: Int = 0,
        @Query("rn") rn: Int = 20,
        @Query("pn") pn: Int = 1
    ): ArtistListResponse

    // ==================== 专辑 ====================

    /**
     * 获取专辑详情
     */
    @GET("api/www/album/albumInfo")
    suspend fun getAlbumInfo(
        @Query("albumId") albumId: Long
    ): AlbumInfoResponse
}
