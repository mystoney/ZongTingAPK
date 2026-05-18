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

private val Context.favoriteDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")

@Singleton
class FavoriteRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private object Keys {
        val FAVORITES = stringPreferencesKey("favorite_songs_json")
    }

    /** 收藏歌曲列表的 Flow */
    val favoriteSongs: Flow<List<Song>> = context.favoriteDataStore.data.map { prefs ->
        val json = prefs[Keys.FAVORITES] ?: "[]"
        try {
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 保存全部收藏歌曲 */
    suspend fun saveFavorites(songs: List<Song>) {
        context.favoriteDataStore.edit { prefs ->
            prefs[Keys.FAVORITES] = gson.toJson(songs)
        }
    }

    /** 添加一首收藏歌曲 */
    suspend fun addFavorite(song: Song) {
        context.favoriteDataStore.edit { prefs ->
            val json = prefs[Keys.FAVORITES] ?: "[]"
            val type = object : TypeToken<MutableList<Song>>() {}.type
            val list: MutableList<Song> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            if (list.none { it.rid == song.rid }) {
                list.add(0, song)
            }
            prefs[Keys.FAVORITES] = gson.toJson(list)
        }
    }

    /** 移除一首收藏歌曲 */
    suspend fun removeFavorite(rid: Long) {
        context.favoriteDataStore.edit { prefs ->
            val json = prefs[Keys.FAVORITES] ?: "[]"
            val type = object : TypeToken<MutableList<Song>>() {}.type
            val list: MutableList<Song> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.removeAll { it.rid == rid }
            prefs[Keys.FAVORITES] = gson.toJson(list)
        }
    }
}
