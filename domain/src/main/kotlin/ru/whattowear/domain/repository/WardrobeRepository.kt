package ru.whattowear.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.whattowear.domain.model.ClothingItem

interface WardrobeRepository {
    fun observeItems(): Flow<List<ClothingItem>>
    suspend fun getItems(): List<ClothingItem>
    suspend fun getItem(id: Long): ClothingItem?

    /** Создать (id = 0) или обновить вещь; возвращает id. */
    suspend fun save(item: ClothingItem): Long

    /** Удалить вещь вместе с её фото. */
    suspend fun delete(id: Long)

    /** «В стирке / недоступно» — такие вещи не предлагаются в подборе. */
    suspend fun setAvailable(id: Long, available: Boolean)
}

/** Фото вещей во внутреннем хранилище приложения. URI передаются строками, чтобы домен не зависел от Android. */
interface PhotoStorage {
    /** Скопировать фото (из галереи или с камеры) к себе: уменьшить, повернуть по EXIF, сжать. Возвращает путь к файлу. */
    suspend fun importPhoto(sourceUri: String): String?

    /** Временный файл для снимка камерой (content:// URI для системной камеры). */
    fun createCameraUri(): String

    suspend fun delete(path: String)
}

/** Экспорт и импорт гардероба в JSON (фото внутри файла, в base64). */
interface WardrobeBackup {
    /** Записать резервную копию в выбранный пользователем файл; возвращает число вещей. */
    suspend fun exportTo(destinationUri: String): Int

    /**
     * Загрузить вещи из файла. [replace] = true — текущий гардероб заменяется, иначе вещи добавляются
     * (точные дубликаты пропускаются). Возвращает число добавленных вещей; бросает исключение при неверном файле.
     */
    suspend fun importFrom(sourceUri: String, replace: Boolean): Int
}
