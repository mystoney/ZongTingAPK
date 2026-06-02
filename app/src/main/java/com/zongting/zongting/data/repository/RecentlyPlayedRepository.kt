package com.zongting.zongting.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zongting.zongting.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentlyPlayedDataStore: DataStore<Preferences> by preferencesDataStore(name = "recently_played")

/**
 * 最近播放持久化仓库
 * - DataStore Preferences + Gson 序列化（与 FavoriteRepository 一致）
 * - 容量上限由调用方控制（MainViewModel 限制 30 首）
 */
@Singleton
class RecentlyPlayedRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private object Keys {
        val RECENTLY_PLAYED = stringPreferencesKey("recently_played_songs_json")
    }

    /** 最近播放列表的 Flow */
    val recentlyPlayed: Flow<List<Song>> = context.recentlyPlayedDataStore.data.map { prefs ->
        val json = prefs[Keys.RECENTLY_PLAYED] ?: "[]"
        try {
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 保存完整最近播放列表（调用方需自行去重 + 限制长度） */
    suspend fun saveRecentlyPlayed(songs: List<Song>) {
        context.recentlyPlayedDataStore.edit { prefs ->
            prefs[Keys.RECENTLY_PLAYED] = gson.toJson(songs)
        }
    }
}
