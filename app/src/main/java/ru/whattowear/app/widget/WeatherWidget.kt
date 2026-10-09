package ru.whattowear.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ru.whattowear.app.MainActivity
import ru.whattowear.app.ui.theme.DarkColors
import ru.whattowear.app.ui.theme.LightColors

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetData(): WidgetDataSource
}

private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** Виджет: температура, иконка погоды и главная вещь дня. Нажатие открывает приложение. */
class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetData()
        provideContent {
            // Данные наблюдаются внутри provideContent — так живая сессия виджета обновляется сама
            val data by remember { source.observe() }.collectAsState(initial = WidgetData(message = "Загрузка…"))
            GlanceTheme(colors = WidgetColors) { Content(data) }
        }
    }

    @Composable
    private fun Content(data: WidgetData) {
        val colors = GlanceTheme.colors
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.background)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (data.message != null && data.temperature.isEmpty()) {
                if (data.locationName.isNotEmpty()) {
                    Text(data.locationName, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                }
                Text(data.message, style = TextStyle(color = colors.onBackground, fontSize = 14.sp))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.temperature,
                    style = TextStyle(color = colors.onBackground, fontSize = 34.sp, fontWeight = FontWeight.Normal),
                )
                Spacer(GlanceModifier.width(10.dp))
                Image(
                    provider = ImageProvider(data.weatherIcon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.onBackground),
                    modifier = GlanceModifier.size(30.dp),
                )
            }
            Text(
                "${data.locationName} · ощущается ${data.feels}",
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            if (data.mainItemName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val photo = data.mainItemPhoto
                    val icon = data.mainItemIcon
                    if (photo != null) {
                        Image(ImageProvider(photo), contentDescription = null, modifier = GlanceModifier.size(40.dp).cornerRadius(10.dp))
                        Spacer(GlanceModifier.width(10.dp))
                    } else if (icon != null) {
                        Image(
                            ImageProvider(icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                            modifier = GlanceModifier.size(28.dp),
                        )
                        Spacer(GlanceModifier.width(10.dp))
                    }
                    Column {
                        data.mainItemCaption?.let {
                            Text(it, style = TextStyle(color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                        }
                        Text(
                            data.mainItemName,
                            style = TextStyle(color = colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            maxLines = 2,
                        )
                    }
                }
            } else {
                Spacer(GlanceModifier.height(1.dp))
            }
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
