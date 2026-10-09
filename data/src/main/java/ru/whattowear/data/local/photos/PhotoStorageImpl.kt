package ru.whattowear.data.local.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.whattowear.domain.repository.PhotoStorage
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class PhotoStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoStorage {

    private val photoDir: File get() = File(context.filesDir, "wardrobe").apply { mkdirs() }
    private val cameraDir: File get() = File(context.cacheDir, "camera").apply { mkdirs() }

    override suspend fun importPhoto(sourceUri: String): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        runCatching {
            val bitmap = decodeScaled(uri) ?: return@runCatching null
            val rotated = rotateByExif(uri, bitmap)
            val target = File(photoDir, "${UUID.randomUUID()}.jpg")
            target.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
            target.absolutePath
        }.getOrNull().also {
            // Временный снимок камеры больше не нужен
            if (uri.authority == authority) cameraDir.listFiles()?.forEach { f -> f.delete() }
        }
    }

    override fun createCameraUri(): String {
        val file = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority, file).toString()
    }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            val file = File(path)
            // Удаляем только свои файлы
            if (file.parentFile?.canonicalPath == photoDir.canonicalPath) file.delete()
        }
    }

    /** Фото в base64 для резервной копии; null — файла нет. */
    fun readBase64(path: String): String? = runCatching {
        Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    /** Сохранить фото из резервной копии; возвращает путь. */
    fun writeBase64(data: String): String? = runCatching {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        // Проверяем, что это действительно картинка
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0) return@runCatching null
        val target = File(photoDir, "${UUID.randomUUID()}.jpg")
        target.writeBytes(bytes)
        target.absolutePath
    }.getOrNull()

    private val authority get() = "${context.packageName}.fileprovider"

    private fun decodeScaled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val longest = max(decoded.width, decoded.height)
        if (longest <= MAX_SIDE) return decoded
        val scale = MAX_SIDE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun rotateByExif(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val MAX_SIDE = 1080
        private const val JPEG_QUALITY = 82
    }
}
