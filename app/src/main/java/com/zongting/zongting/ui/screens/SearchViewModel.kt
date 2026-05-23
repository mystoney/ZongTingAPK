package com.zongting.zongting.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zongting.zongting.data.model.Song
import com.zongting.zongting.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // 按来源缓存搜索结果
    private var kuwoResults: List<Song> = emptyList()
    private var neteaseResults: List<Song> = emptyList()

    fun searchSuggest(query: String): Job {
        return viewModelScope.launch {
            delay(300) // 防抖
            repository.searchSuggest(query)
                .onSuccess { suggestions ->
                    _uiState.value = _uiState.value.copy(suggestions = suggestions)
                }
                .onFailure {
                    // 静默失败
                }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                suggestions = emptyList(),
                currentKeyword = query
            )

            val source = _uiState.value.source
            repository.searchMusic(query, source)
                .onSuccess { songs ->
                    // 缓存到对应来源
                    if (source == "kuwo") kuwoResults = songs else neteaseResults = songs
                    _uiState.value = _uiState.value.copy(
                        searchResults = songs,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "搜索失败",
                        isLoading = false,
                        searchResults = emptyList()
                    )
                }
        }
    }

    fun setSource(source: String) {
        if (source == _uiState.value.source) return
        val currentKeyword = _uiState.value.currentKeyword
        val cachedResults = if (source == "kuwo") kuwoResults else neteaseResults

        if (cachedResults.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                source = source,
                searchResults = cachedResults,
                filters = emptySet()
            )
        } else if (currentKeyword.isNotBlank()) {
            // 无缓存但有关键字，自动搜索
            search(currentKeyword)
            _uiState.value = _uiState.value.copy(source = source, filters = emptySet())
        } else {
            _uiState.value = _uiState.value.copy(
                source = source,
                searchResults = emptyList(),
                filters = emptySet()
            )
        }
    }

    fun setFilter(filter: String, selected: Boolean) {
        val current = _uiState.value.filters.toMutableSet()
        if (selected) current.add(filter) else current.remove(filter)
        _uiState.value = _uiState.value.copy(filters = current)
    }

    fun clearSearch() {
        _uiState.value = SearchUiState(source = _uiState.value.source, currentKeyword = "")
    }
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val searchResults: List<Song> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val error: String? = null,
    val source: String = "kuwo", // "kuwo" 或 "netease"
    val filters: Set<String> = emptySet(), // "free", "vip", "lock"
    val currentKeyword: String = "" // 记录当前搜索关键字
) {
    val displayedResults: List<Song>
        get() {
            val base = if (filters.isEmpty()) {
                // 默认：过滤单曲购买(fee=4)和需购买(fee=8)，最多显示100条
                searchResults.filter { it.fee != 4 && it.fee != 8 }.take(100)
            } else {
                searchResults.filter { song ->
                    when {
                        filters.contains("free") && song.fee == 0 -> true
                        filters.contains("vip") && song.fee == 1 -> true
                        filters.contains("lock") && song.fee == 8 -> true
                        else -> false
                    }
                }
            }
            return base
        }
}
