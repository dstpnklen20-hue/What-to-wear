package ru.whattowear.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.whattowear.app.MainActivity
import ru.whattowear.app.R
import ru.whattowear.app.ui.components.weatherIconRes
import ru.whattowear.domain.outfit.Role
import ru.whattowear.domain.text.Fmt
import ru.whattowear.domain.usecase.CurrentRecommendation
import ru.whattowear.domain.weather.DaySummary
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifications @Inject constructor(@ApplicationContext private val context: Context) {

    fun createChannels() {
        val channel = NotificationChannel(
            MORNING_CHANNEL,
            context.getString(R.string.notification_channel_morning),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_morning_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Утреннее уведомление: погода на время вне дома и что надеть. */
    fun showMorning(data: CurrentRecommendation) {
        if (!canPost()) return
        val rec = data.recommendation
        val zone = data.forecast.zone
        val nowSec = System.currentTimeMillis() / 1000
        val current = data.forecast.hours.lastOrNull { it.time <= nowSec } ?: data.forecast.hours.firstOrNull() ?: return

        val title = buildString {
            if (rec != null) {
                append("${Fmt.temp(rec.conditions.feelsMin)}…${Fmt.temp(rec.conditions.feelsMax)}")
                append(", ")
                append(rec.conditions.precipPhrase?.substringBefore(",") ?: "без осадков")
            } else {
                append("Сейчас ${Fmt.temp(current.temperature)}")
            }
            append(" · ${data.location.name}")
        }
        val body = buildString {
            val outfit = rec?.outfit
            if (outfit != null) {
                val main = outfit.pieces.filter { it.role != Role.UMBRELLA && it.role != Role.SUNGLASSES }
                append("Надеть: ")
                append(main.joinToString(", ") { it.item.name.replaceFirstChar(Char::lowercase) })
                append(".")
            } else if (rec?.genericAdvice != null) {
                append(rec.genericAdvice)
            }
            rec?.tips?.firstOrNull()?.let { append("\n").append(it) }
            rec?.warnings?.firstOrNull()?.let { append("\n").append(it) }
            if (rec == null) {
                val today = Instant.ofEpochSecond(current.time).atZone(zone).toLocalDate()
                DaySummary.summarize(data.forecast.hours.filter { Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate() == today }, zone)
                    ?.let { append(it) }
            }
        }

        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, MORNING_CHANNEL)
            .setSmallIcon(weatherIconRes(current.kind, current.isDay))
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(MORNING_ID, notification)
        } catch (e: SecurityException) {
            // Разрешение отозвали между проверкой и показом — просто пропускаем
        }
    }

    companion object {
        const val MORNING_CHANNEL = "morning"
        private const val MORNING_ID = 1001
    }
}
