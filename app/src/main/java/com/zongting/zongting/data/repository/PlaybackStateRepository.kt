package com.zongting.zongting.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zongting.zongting.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_state")

/** 播放状态持久化：保存播放列表和当前播放位置 */
@Singleton
class PlaybackStateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private object Keys {
        val PLAYLIST_JSON = stringPreferencesKey("playlist_json")
        val CURRENT_INDEX = intPreferencesKey("current_index")
    }

    /** 保存播放列表和当前索引（应在 onPause / onStop 时调用） */
    suspend fun savePlaybackState(playlist: List<Song>, currentIndex: Int) {
        context.playbackDataStore.edit { prefs ->
            prefs[Keys.PLAYLIST_JSON] = gson.toJson(playlist)
            prefs[Keys.CURRENT_INDEX] = currentIndex
        }
    }

    /** 加载保存的播放状态 Flow */
    val playbackStateFlow: Flow<PlaybackStateData> = context.playbackDataStore.data.map { prefs ->
        val json = prefs[Keys.PLAYLIST_JSON] ?: "[]"
        val index = prefs[Keys.CURRENT_INDEX] ?: 0
        val songs: List<Song> = try {
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        PlaybackStateData(songs, index.coerceIn(0, (songs.size - 1).coerceAtLeast(0)))
    }

    /** 清除保存的播放状态 */
    suspend fun clearPlaybackState() {
        context.playbackDataStore.edit { prefs ->
            prefs.remove(Keys.PLAYLIST_JSON)
            prefs.remove(Keys.CURRENT_INDEX)
        }
    }
}

/** 播放状态数据结构 */
data class PlaybackStateData(
    val playlist: List<Song>,
    val currentIndex: Int
)
