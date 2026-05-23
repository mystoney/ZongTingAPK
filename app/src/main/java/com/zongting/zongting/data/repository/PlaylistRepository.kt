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
import com.zongting.zongting.data.model.UserPlaylist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_playlists")

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private object Keys {
        val PLAYLISTS = stringPreferencesKey("playlists_json")
    }

    /** 所有用户歌单的 Flow */
    val playlists: Flow<List<UserPlaylist>> = context.playlistDataStore.data.map { prefs ->
        val json = prefs[Keys.PLAYLISTS] ?: "[]"
        try {
            val type = object : TypeToken<List<UserPlaylist>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 创建新歌单 */
    suspend fun createPlaylist(name: String): UserPlaylist {
        val playlist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            songs = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        context.playlistDataStore.edit { prefs ->
            val json = prefs[Keys.PLAYLISTS] ?: "[]"
            val type = object : TypeToken<MutableList<UserPlaylist>>() {}.type
            val list: MutableList<UserPlaylist> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.add(0, playlist)
            prefs[Keys.PLAYLISTS] = gson.toJson(list)
        }
        return playlist
    }

    /** 创建歌单并返回其 ID */
    suspend fun createPlaylistAndGetId(name: String): String {
        val id = UUID.randomUUID().toString()
        val playlist = UserPlaylist(
            id = id,
            name = name.trim(),
            songs = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        context.playlistDataStore.edit { prefs ->
            val json = prefs[Keys.PLAYLISTS] ?: "[]"
            val type = object : TypeToken<MutableList<UserPlaylist>>() {}.type
            val list: MutableList<UserPlaylist> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.add(0, playlist)
            prefs[Keys.PLAYLISTS] = gson.toJson(list)
        }
        return id
    }

    /** 重命名歌单 */
    suspend fun renamePlaylist(id: String, newName: String) {
        context.playlistDataStore.edit { prefs ->
            val json = prefs[Keys.PLAYLISTS] ?: "[]"
            val type = object : TypeToken<MutableList<UserPlaylist>>() {}.type
            val list: MutableList<UserPlaylist> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(name = newName.trim())
            }
            prefs[Keys.PLAYLISTS] = gson.toJson(list)
        }
    }

    /** 删除歌单 */
    suspend fun deletePlaylist(id: String) {
        context.playlistDataStore.edit { prefs ->
            val json = prefs[Keys.PLAYLISTS] ?: "[]"
            val type = object : TypeToken<MutableList<UserPlaylist>>() {}.type
            val list: MutableList<UserPlaylist> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            list.removeAll { it.id == id }
            prefs[Keys.PLAYLISTS] = gson.toJson(list)
        }
    }

    /** 向歌单添加歌曲 */
    suspend fun addSongToPlaylist(playlistId: String, song: Song) {
        context.playlistDataStore.edit { prefs ->
            val json = prefs[Keys.PLAYLISTS] ?: "[]"
            val type = object : TypeToken<MutableList<UserPlaylist>>() {}.type
            val list: MutableList<UserPlaylist> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            val idx = list.indexOfFirst { it.id == playlistId }
            if (idx >= 0) {
                val current = list[idx].songs.toMutableList()
                if (current.none { it.rid == song.rid }) {
                    current.add(0, song)
                    list[idx] = list[idx].copy(songs = current)
                }
            }
            prefs[Keys.PLAYLISTS] = gson.toJson(list)
        }
    }

    /** 向歌单添加多首歌曲 */
    suspend fun addSongsToPlaylist(playlistId: String, songs: List<Song>) {
        context.playlistDataStore.edit { prefs ->
            val json = prefs[Keys.PLAYLISTS] ?: "[]"
            val type = object : TypeToken<MutableList<UserPlaylist>>() {}.type
            val list: MutableList<UserPlaylist> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            val idx = list.indexOfFirst { it.id == playlistId }
            if (idx >= 0) {
                val current = list[idx].songs.toMutableList()
                songs.forEach { song ->
                    if (current.none { it.rid == song.rid }) {
                        current.add(0, song)
                    }
                }
                list[idx] = list[idx].copy(songs = current)
            }
            prefs[Keys.PLAYLISTS] = gson.toJson(list)
        }
    }

    /** 从歌单移除歌曲 */
    suspend fun removeSongFromPlaylist(playlistId: String, songRid: Long) {
        context.playlistDataStore.edit { prefs ->
            val json = prefs[Keys.PLAYLISTS] ?: "[]"
            val type = object : TypeToken<MutableList<UserPlaylist>>() {}.type
            val list: MutableList<UserPlaylist> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            val idx = list.indexOfFirst { it.id == playlistId }
            if (idx >= 0) {
                val current = list[idx].songs.toMutableList()
                current.removeAll { it.rid == songRid }
                list[idx] = list[idx].copy(songs = current)
            }
            prefs[Keys.PLAYLISTS] = gson.toJson(list)
        }
    }
}
