package ru.whattowear.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.whattowear.data.local.db.ClothingDao
import ru.whattowear.data.local.db.toDomain
import ru.whattowear.data.local.db.toEntity
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.repository.PhotoStorage
import ru.whattowear.domain.repository.WardrobeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WardrobeRepositoryImpl @Inject constructor(
    private val dao: ClothingDao,
    private val photos: PhotoStorage,
) : WardrobeRepository {

    override fun observeItems(): Flow<List<ClothingItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getItems(): List<ClothingItem> = dao.getAll().map { it.toDomain() }

    override suspend fun getItem(id: Long): ClothingItem? = dao.get(id)?.toDomain()

    override suspend fun save(item: ClothingItem): Long {
        val now = System.currentTimeMillis()
        return if (item.id == 0L) {
            dao.insert(item.copy(createdAt = now, updatedAt = now).toEntity())
        } else {
            val old = dao.get(item.id)
            dao.update(item.copy(createdAt = old?.createdAt ?: now, updatedAt = now).toEntity())
            // Фото заменили или убрали — старый файл больше не нужен
            old?.photoPath?.takeIf { it != item.photoPath }?.let { photos.delete(it) }
            item.id
        }
    }

    override suspend fun delete(id: Long) {
        val old = dao.get(id) ?: return
        dao.delete(id)
        old.photoPath?.let { photos.delete(it) }
    }

    override suspend fun setAvailable(id: Long, available: Boolean) {
        dao.setAvailable(id, available, System.currentTimeMillis())
    }
}
