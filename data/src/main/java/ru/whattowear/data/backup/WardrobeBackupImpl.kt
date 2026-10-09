package ru.whattowear.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.whattowear.data.local.db.AppDatabase
import ru.whattowear.data.local.db.ClothingDao
import ru.whattowear.data.local.db.ClothingItemEntity
import ru.whattowear.data.local.db.enumOr
import ru.whattowear.data.local.db.toMask
import ru.whattowear.data.local.photos.PhotoStorageImpl
import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingColor
import ru.whattowear.domain.model.Layer
import ru.whattowear.domain.model.Season
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.Subtype
import ru.whattowear.domain.repository.WardrobeBackup
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Формат файла резервной копии. Поля — имена констант перечислений, чтобы файл был читаемым. */
@Serializable
data class WardrobeBackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: String,
    val items: List<BackupItem>,
) {
    companion object {
        const val FORMAT = "what-to-wear/wardrobe"
    }
}

@Serializable
data class BackupItem(
    val name: String,
    val category: String,
    val subtype: String = Subtype.NONE.name,
    val warmth: Int,
    val waterproof: Boolean = false,
    val windproof: Boolean = false,
    val layer: String = Layer.OUTER.name,
    val style: String = Style.CASUAL.name,
    val color: String = ClothingColor.BLACK.name,
    val seasons: List<String> = emptyList(),
    val available: Boolean = true,
    /** JPEG в base64; null — вещь без фото. */
    val photo: String? = null,
)

@Singleton
class WardrobeBackupImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val dao: ClothingDao,
    private val photos: PhotoStorageImpl,
    private val json: Json,
) : WardrobeBackup {

    override suspend fun exportTo(destinationUri: String): Int = withContext(Dispatchers.IO) {
        val items = dao.getAll().map { e ->
            BackupItem(
                name = e.name,
                category = e.category,
                subtype = e.subtype,
                warmth = e.warmth,
                waterproof = e.waterproof,
                windproof = e.windproof,
                layer = e.layer,
                style = e.style,
                color = e.color,
                seasons = Season.entries.filter { e.seasons and (1 shl it.ordinal) != 0 }.map { it.name },
                available = e.isAvailable,
                photo = e.photoPath?.let { photos.readBase64(it) },
            )
        }
        val file = WardrobeBackupFile(exportedAt = Instant.now().toString(), items = items)
        val stream = context.contentResolver.openOutputStream(Uri.parse(destinationUri), "wt")
            ?: error("Не удалось открыть файл для записи")
        stream.bufferedWriter().use { it.write(json.encodeToString(WardrobeBackupFile.serializer(), file)) }
        items.size
    }

    override suspend fun importFrom(sourceUri: String, replace: Boolean): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(Uri.parse(sourceUri))?.bufferedReader()?.use { it.readText() }
            ?: error("Не удалось открыть файл")
        val file = runCatching { json.decodeFromString(WardrobeBackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("Файл не похож на резервную копию гардероба", it) }
        require(file.format == WardrobeBackupFile.FORMAT) { "Неизвестный формат файла" }

        val now = System.currentTimeMillis()
        val existing = if (replace) emptyList() else dao.getAll()
        fun key(name: String, category: String, color: String, warmth: Int) = "$name|$category|$color|$warmth".lowercase()
        val existingKeys = existing.map { key(it.name, it.category, it.color, it.warmth) }.toMutableSet()

        val toInsert = file.items.mapNotNull { b ->
            val category = enumOr(b.category, Category.ACCESSORIES)
            val k = key(b.name, category.name, b.color, b.warmth)
            if (b.name.isBlank() || !existingKeys.add(k)) return@mapNotNull null
            ClothingItemEntity(
                name = b.name.trim(),
                category = category.name,
                subtype = enumOr(b.subtype, Subtype.NONE).name,
                photoPath = b.photo?.let { photos.writeBase64(it) },
                warmth = b.warmth.coerceIn(1, 5),
                waterproof = b.waterproof,
                windproof = b.windproof,
                layer = enumOr(b.layer, category.defaultLayer).name,
                style = enumOr(b.style, Style.CASUAL).name,
                color = enumOr(b.color, ClothingColor.MULTI).name,
                seasons = b.seasons.mapNotNull { runCatching { Season.valueOf(it) }.getOrNull() }.toSet().toMask(),
                isAvailable = b.available,
                createdAt = now,
                updatedAt = now,
            )
        }

        val oldPhotos = if (replace) dao.getAll().mapNotNull { it.photoPath } else emptyList()
        database.withTransaction {
            if (replace) dao.deleteAll()
            dao.insertAll(toInsert)
        }
        oldPhotos.forEach { photos.delete(it) }
        toInsert.size
    }
}
