package com.zongting.zongting.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zongting.zongting.data.model.Bang
import com.zongting.zongting.data.model.BangCategory
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RankingsViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingsUiState())
    val uiState: StateFlow<RankingsUiState> = _uiState.asStateFlow()

    fun loadRankings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val source = _uiState.value.source
            repository.getBangMenu(source)
                .onSuccess { categories ->
                    _uiState.value = _uiState.value.copy(
                        bangCategories = categories,
                        isLoading = false
                    )
                    // 默认加载第一个榜单
                    categories.firstOrNull()?.list?.firstOrNull()?.let {
                        loadBangSongs(it.id)
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message,
                        isLoading = false
                    )
                }
        }
    }

    fun loadBangSongs(bangId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedBangId = bangId
            )

            val source = _uiState.value.source
            repository.getBangMusicList(bangId, source)
                .onSuccess { songs ->
                    val selectedBang = _uiState.value.bangCategories
                        .flatMap { it.list }
                        .find { it.id == bangId }

                    _uiState.value = _uiState.value.copy(
                        bangSongs = songs,
                        selectedBang = selectedBang,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message,
                        isLoading = false,
                        bangSongs = emptyList()
                    )
                }
        }
    }

    fun setSource(source: String) {
        _uiState.value = _uiState.value.copy(source = source, filters = emptySet())
        loadRankings()
    }

    fun setFilter(filter: String, selected: Boolean) {
        val current = _uiState.value.filters.toMutableSet()
        if (selected) current.add(filter) else current.remove(filter)
        _uiState.value = _uiState.value.copy(filters = current)
    }
}

data class RankingsUiState(
    val isLoading: Boolean = false,
    val bangCategories: List<BangCategory> = emptyList(),
    val selectedBangId: String? = null,
    val selectedBang: Bang? = null,
    val bangSongs: List<Song> = emptyList(),
    val error: String? = null,
    val source: String = "netease", // "kuwo" 或 "netease"
    val filters: Set<String> = emptySet() // "free", "vip", "lock"
) {
    val displayedSongs: List<Song>
        get() = if (filters.isEmpty()) bangSongs
        else bangSongs.filter { song ->
            when {
                filters.contains("free") && song.fee == 0 -> true
                filters.contains("vip") && song.fee == 1 -> true
                filters.contains("lock") && song.fee == 8 -> true
                else -> false
            }
        }
}
