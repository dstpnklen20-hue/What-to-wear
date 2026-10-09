package ru.whattowear.app.ui.itemeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingColor
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.model.Layer
import ru.whattowear.domain.model.Season
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.Subtype
import ru.whattowear.domain.repository.PhotoStorage
import ru.whattowear.domain.repository.WardrobeRepository
import javax.inject.Inject

data class ItemEditorState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val nameError: Boolean = false,
    val category: Category = Category.TOP,
    val subtype: Subtype = Subtype.NONE,
    val photoPath: String? = null,
    val warmth: Int = Category.TOP.defaultWarmth,
    val waterproof: Boolean = false,
    val windproof: Boolean = false,
    val layer: Layer = Category.TOP.defaultLayer,
    val style: Style = Style.CASUAL,
    val color: ClothingColor = ClothingColor.BLACK,
    val seasons: Set<Season> = emptySet(),
    val available: Boolean = true,
    val photoBusy: Boolean = false,
    val photoFailed: Boolean = false,
    val saving: Boolean = false,
    /** Сохранено или удалено — экран закрывается. */
    val done: Boolean = false,
)

@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    private val repository: WardrobeRepository,
    private val photos: PhotoStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemEditorState())
    val state: StateFlow<ItemEditorState> = _state.asStateFlow()

    private var itemId = 0L
    private var original: ClothingItem? = null
    private var loadedFor: Long? = null
    private var warmthTouched = false
    private var layerTouched = false
    /** Фото, загруженные в этом сеансе: если не сохранить вещь, их нужно удалить. */
    private val sessionPhotos = mutableSetOf<String>()
    private var cameraUri: String? = null

    fun load(id: Long) {
        if (loadedFor == id) return
        loadedFor = id
        itemId = id
        if (id == 0L) {
            _state.value = ItemEditorState(loaded = true)
            return
        }
        viewModelScope.launch {
            val item = repository.getItem(id)
            original = item
            _state.value = if (item == null) {
                ItemEditorState(loaded = true)
            } else {
                warmthTouched = true
                layerTouched = true
                ItemEditorState(
                    loaded = true,
                    isNew = false,
                    name = item.name,
                    category = item.category,
                    subtype = item.subtype,
                    photoPath = item.photoPath,
                    warmth = item.warmth,
                    waterproof = item.waterproof,
                    windproof = item.windproof,
                    layer = item.layer,
                    style = item.style,
                    color = item.color,
                    seasons = item.seasons,
                    available = item.isAvailable,
                )
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = false) }

    fun onCategory(category: Category) = _state.update {
        it.copy(
            category = category,
            // Разумные значения по умолчанию, пока пользователь не менял их сам
            warmth = if (warmthTouched) it.warmth else category.defaultWarmth,
            layer = if (layerTouched) it.layer else category.defaultLayer,
            subtype = if (it.subtype in category.subtypes) it.subtype else category.subtypes.firstOrNull() ?: Subtype.NONE,
        )
    }

    fun onSubtype(value: Subtype) = _state.update { it.copy(subtype = value) }
    fun onWarmth(value: Int) {
        warmthTouched = true
        _state.update { it.copy(warmth = value.coerceIn(1, 5)) }
    }
    fun onLayer(value: Layer) {
        layerTouched = true
        _state.update { it.copy(layer = value) }
    }
    fun onWaterproof(value: Boolean) = _state.update { it.copy(waterproof = value) }
    fun onWindproof(value: Boolean) = _state.update { it.copy(windproof = value) }
    fun onStyle(value: Style) = _state.update { it.copy(style = value) }
    fun onColor(value: ClothingColor) = _state.update { it.copy(color = value) }
    fun onSeason(season: Season) = _state.update {
        it.copy(seasons = if (season in it.seasons) it.seasons - season else it.seasons + season)
    }
    fun onAvailable(value: Boolean) = _state.update { it.copy(available = value) }

    /** URI для системной камеры; хранится во ViewModel, чтобы пережить поворот экрана. */
    fun newCameraUri(): String = photos.createCameraUri().also { cameraUri = it }

    fun onCameraResult(success: Boolean) {
        val uri = cameraUri ?: return
        cameraUri = null
        if (success) onPhotoPicked(uri)
    }

    fun onPhotoPicked(uri: String) {
        viewModelScope.launch {
            _state.update { it.copy(photoBusy = true, photoFailed = false) }
            val path = photos.importPhoto(uri)
            if (path != null) sessionPhotos += path
            _state.update { if (path != null) it.copy(photoPath = path, photoBusy = false) else it.copy(photoBusy = false, photoFailed = true) }
        }
    }

    fun removePhoto() = _state.update { it.copy(photoPath = null) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val item = ClothingItem(
                id = itemId,
                name = s.name.trim(),
                category = s.category,
                subtype = if (s.category.subtypes.isEmpty()) Subtype.NONE else s.subtype,
                photoPath = s.photoPath,
                warmth = s.warmth,
                waterproof = s.waterproof,
                windproof = s.windproof,
                layer = if (s.category.isTorso) s.layer else s.category.defaultLayer,
                style = s.style,
                color = s.color,
                seasons = s.seasons,
                isAvailable = s.available,
                createdAt = original?.createdAt ?: 0,
            )
            repository.save(item)
            // Промежуточные фото, которые в итоге не пригодились
            (sessionPhotos - setOfNotNull(s.photoPath)).forEach { photos.delete(it) }
            sessionPhotos.clear()
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() {
        if (itemId == 0L) return
        viewModelScope.launch {
            repository.delete(itemId)
            sessionPhotos.forEach { photos.delete(it) }
            sessionPhotos.clear()
            _state.update { it.copy(done = true) }
        }
    }

    /** Экран закрыт после сохранения: при следующем открытии (в двухпанельном режиме VM живёт дольше) — загрузить заново. */
    fun consumeDone() {
        loadedFor = null
        original = null
        warmthTouched = false
        layerTouched = false
        _state.value = ItemEditorState()
    }

    override fun onCleared() {
        // Вышли без сохранения — загруженные фото не нужны
        val leftovers = sessionPhotos.toList()
        if (leftovers.isNotEmpty()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                withContext(NonCancellable) { leftovers.forEach { photos.delete(it) } }
            }
        }
    }
}
