package ru.whattowear.app.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.repository.WardrobeBackup
import ru.whattowear.domain.repository.WardrobeRepository
import ru.whattowear.domain.text.Fmt
import javax.inject.Inject

sealed interface WardrobeFilter {
    data object All : WardrobeFilter
    data class ByCategory(val category: Category) : WardrobeFilter
    data object Unavailable : WardrobeFilter
}

data class WardrobeUiState(
    val initialized: Boolean = false,
    val items: List<ClothingItem> = emptyList(),
    val totalCount: Int = 0,
    val unavailableCount: Int = 0,
    val categoriesInUse: Set<Category> = emptySet(),
    val query: String = "",
    val filter: WardrobeFilter = WardrobeFilter.All,
    val busy: Boolean = false,
)

@HiltViewModel
class WardrobeViewModel @Inject constructor(
    private val repository: WardrobeRepository,
    private val backup: WardrobeBackup,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<WardrobeFilter>(WardrobeFilter.All)
    private val busy = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    val state: StateFlow<WardrobeUiState> = combine(repository.observeItems(), query, filter, busy) { all, q, f, b ->
        val needle = q.trim().lowercase()
        val filtered = all
            .filter {
                when (f) {
                    WardrobeFilter.All -> true
                    is WardrobeFilter.ByCategory -> it.category == f.category
                    WardrobeFilter.Unavailable -> !it.isAvailable
                }
            }
            .filter { needle.isEmpty() || needle in it.name.lowercase() || needle in it.category.title.lowercase() || needle in it.color.title.lowercase() }
            .sortedWith(compareBy<ClothingItem> { it.category.ordinal }.thenBy { it.name.lowercase() })
        WardrobeUiState(
            initialized = true,
            items = filtered,
            totalCount = all.size,
            unavailableCount = all.count { !it.isAvailable },
            categoriesInUse = all.map { it.category }.toSet(),
            query = q,
            filter = f,
            busy = b,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WardrobeUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: WardrobeFilter) {
        filter.value = value
    }

    fun toggleAvailable(item: ClothingItem) {
        viewModelScope.launch {
            repository.setAvailable(item.id, !item.isAvailable)
            messages.send(if (item.isAvailable) "«${item.name}» в стирке — не будет в подборе" else "«${item.name}» снова доступна")
        }
    }

    fun export(uri: String) {
        viewModelScope.launch {
            busy.value = true
            val text = runCatching { backup.exportTo(uri) }.fold(
                onSuccess = { "Сохранено: $it ${Fmt.plural(it, "вещь", "вещи", "вещей")}" },
                onFailure = { "Не удалось сохранить файл" },
            )
            busy.value = false
            messages.send(text)
        }
    }

    fun import(uri: String, replace: Boolean) {
        viewModelScope.launch {
            busy.value = true
            val text = runCatching { backup.importFrom(uri, replace) }.fold(
                onSuccess = { if (it == 0) "Новых вещей в файле нет" else "Добавлено: $it ${Fmt.plural(it, "вещь", "вещи", "вещей")}" },
                onFailure = { it.message?.takeIf { m -> m.isNotBlank() } ?: "Не удалось прочитать файл" },
            )
            busy.value = false
            messages.send(text)
        }
    }
}
