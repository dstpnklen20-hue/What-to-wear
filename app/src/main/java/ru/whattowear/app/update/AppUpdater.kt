package ru.whattowear.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.whattowear.app.BuildConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val versionCode: Long) : UpdateState
    /** APK скачан — можно предложить установку. */
    data class Ready(val versionCode: Long) : UpdateState
    data object Error : UpdateState
}

/**
 * Автообновление как в Tik Tak: CI публикует каждую сборку из main как релиз GitHub «build-N»
 * (N = versionCode). Приложение смотрит последний релиз, скачивает APK и передаёт его системному установщику.
 * Обращается только к api.github.com; сама проверяет только сборки из CI (BuildConfig.CI_BUILD).
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private class Release(val versionCode: Long, val apkUrl: String)

    val versionName: String get() = BuildConfig.VERSION_NAME

    private val currentCode: Long
        get() = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private val prefs get() = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private val apkFile get() = File(File(context.cacheDir, "updates").apply { mkdirs() }, "update.apk")

    /** [manual] — по кнопке в настройках: проверяет и локальные сборки. */
    suspend fun check(manual: Boolean) {
        if (!manual && !BuildConfig.CI_BUILD) return
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        _state.value = withContext(Dispatchers.IO) {
            runCatching {
                val downloaded = prefs.getLong(KEY_DOWNLOADED, 0L)
                if (downloaded <= currentCode) apkFile.delete()
                val release = fetchLatest()
                if (release == null || release.versionCode <= currentCode) return@runCatching UpdateState.UpToDate
                if (!(apkFile.exists() && downloaded == release.versionCode)) {
                    _state.value = UpdateState.Downloading(release.versionCode)
                    download(release.apkUrl)
                    prefs.edit().putLong(KEY_DOWNLOADED, release.versionCode).apply()
                }
                UpdateState.Ready(release.versionCode)
            }.getOrElse { UpdateState.Error }
        }
    }

    /** Пользователь отложил установку — не показываем диалог до следующей проверки. */
    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /** Установка через PackageInstaller; если установка из приложения не разрешена — открыть настройку. */
    fun install(): Boolean {
        val file = apkFile
        if (!file.exists()) return false
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
        _state.value = UpdateState.Idle
        return true
    }

    private fun fetchLatest(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body.string()
        }
        val release = json.parseToJsonElement(body).jsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return null
        if (!tag.startsWith(TAG_PREFIX)) return null
        val code = tag.removePrefix(TAG_PREFIX).toLongOrNull() ?: return null
        val apk = (release["assets"] as? JsonArray)
            ?.map { it as JsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?: return null
        val url = apk["browser_download_url"]?.jsonPrimitive?.content ?: return null
        return Release(code, url)
    }

    private fun download(url: String) {
        val part = File(apkFile.parentFile, "update.part")
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        }
        apkFile.delete()
        if (!part.renameTo(apkFile)) error("Не удалось сохранить обновление")
    }

    companion object {
        private const val TAG_PREFIX = "build-"
        private const val KEY_DOWNLOADED = "downloaded_code"
    }
}
