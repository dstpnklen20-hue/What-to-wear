package ru.whattowear.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.whattowear.app.ui.components.Dimens
import ru.whattowear.app.ui.components.ScreenHeader
import ru.whattowear.app.ui.components.SectionLabel
import ru.whattowear.app.ui.components.ThinDivider
import ru.whattowear.app.update.UpdateState
import ru.whattowear.app.widget.WidgetPinning
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.ThemeMode
import ru.whattowear.domain.text.Fmt
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SettingsScreen(onOpenLocations: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    var picker by remember { mutableStateOf<TimeTarget?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    picker?.let { target ->
        TimePickerDialog(
            title = target.title,
            initial = when (target) {
                TimeTarget.LEAVE -> s.leaveTime
                TimeTarget.RETURN -> s.returnTime
                TimeTarget.NOTIFY -> s.notificationTime
            },
            onDismiss = { picker = null },
            onConfirm = { time ->
                when (target) {
                    TimeTarget.LEAVE -> viewModel.setLeave(time)
                    TimeTarget.RETURN -> viewModel.setReturn(time)
                    TimeTarget.NOTIFY -> viewModel.setNotificationTime(time)
                }
                picker = null
            },
        )
    }

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader("Настройки")
            if (!state.loaded) return@Column

            Section("Места") {
                ValueRow("Место для прогноза", state.location?.name ?: "не выбрано", onClick = onOpenLocations, chevron = true)
            }

            Section("Время вне дома") {
                ValueRow("Выхожу из дома", s.leaveTime.format(timeFormat), onClick = { picker = TimeTarget.LEAVE })
                ValueRow("Возвращаюсь", s.returnTime.format(timeFormat), onClick = { picker = TimeTarget.RETURN })
                Hint("Одежда подбирается по погоде именно на это время, с учётом перепада температуры.")
            }

            Section("Утреннее уведомление") {
                SwitchRow(
                    title = "Погода и что надеть",
                    checked = s.notificationsEnabled,
                    onChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.setNotificationsEnabled(enabled)
                        }
                    },
                )
                if (s.notificationsEnabled) {
                    ValueRow("Время уведомления", s.notificationTime.format(timeFormat), onClick = { picker = TimeTarget.NOTIFY })
                }
            }

            Section("Личные ощущения") {
                ComfortSection(
                    manual = s.manualOffset,
                    learned = s.learnedOffset,
                    feedbackCount = s.feedbackCount,
                    onManualChange = viewModel::setManualOffset,
                    onReset = viewModel::resetLearned,
                )
                StyleSection(s.preferredStyle, viewModel::setPreferredStyle)
            }

            Section("Виджет") {
                val context = LocalContext.current
                val pinSupported = remember { WidgetPinning.isSupported(context) }
                if (pinSupported) {
                    ValueRow("Добавить на рабочий стол", "Добавить", onClick = { WidgetPinning.request(context) })
                }
                Hint("Виджет показывает температуру, погоду и главную вещь дня. " + if (pinSupported) "" else "Добавьте его через меню виджетов на рабочем столе.")
            }

            Section("Оформление") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = s.theme == mode,
                            onClick = { viewModel.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            icon = {},
                        ) { Text(mode.title, maxLines = 1) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Цвета обоев (Dynamic Color)", s.dynamicColor, viewModel::setDynamicColor)
                }
            }

            Section("О приложении") {
                val update by viewModel.update.collectAsStateWithLifecycle()
                val status = when (val u = update) {
                    UpdateState.Checking -> "Проверяем…"
                    is UpdateState.Downloading -> "Скачиваем сборку ${u.versionCode}…"
                    is UpdateState.Ready -> "Сборка ${u.versionCode} готова к установке"
                    UpdateState.UpToDate -> "Установлена последняя версия"
                    UpdateState.Error -> "Не удалось проверить — нет сети?"
                    UpdateState.Idle -> "Проверить обновления"
                }
                ValueRow("Версия ${viewModel.versionName}", status, onClick = viewModel::checkUpdates)
                Hint(
                    "Прогноз: Open-Meteo (модели ECMWF, ICON, GFS) и MET Norway, лицензия CC BY 4.0. " +
                        "Итог — взвешенное среднее моделей.\n\n" +
                        "Гардероб, настройки и история хранятся только на этом устройстве. " +
                        "Аналитики и трекеров нет: в сеть уходят только координаты (для прогноза) " +
                        "и запрос к GitHub — проверка новой версии.\n\n" +
                        "Шрифт Inter (SIL Open Font License).",
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private enum class TimeTarget(val title: String) {
    LEAVE("Выхожу из дома"),
    RETURN("Возвращаюсь"),
    NOTIFY("Время уведомления"),
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        SectionLabel(title, Modifier.padding(horizontal = Dimens.screenPadding, vertical = 6.dp))
        content()
        ThinDivider(Modifier.padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 12.dp))
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit, chevron: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dimens.screenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = 4.dp),
    )
}

private fun offsetText(value: Double): String = when {
    abs(value) < 0.05 -> "0°"
    value > 0 -> "+${Fmt.number(value)}°"
    else -> "−${Fmt.number(abs(value))}°"
}

@Composable
private fun ComfortSection(
    manual: Double,
    learned: Double,
    feedbackCount: Int,
    onManualChange: (Double) -> Unit,
    onReset: () -> Unit,
) {
    // Ползунок двигается плавно, а в настройки значение пишется по отпусканию
    var sliderValue by remember(manual) { mutableFloatStateOf(manual.toFloat()) }
    Column(Modifier.padding(horizontal = Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Насколько вы мерзлявы", style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val presets = listOf(3.0 to "Мерзлявый", 0.0 to "Обычно", -3.0 to "Мне жарко")
            presets.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = abs(manual - value) < 0.05,
                    onClick = { onManualChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, presets.size),
                    icon = {},
                ) { Text(label, maxLines = 1) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Поправка", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(offsetText(sliderValue.toDouble()), style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onManualChange(sliderValue.toDouble()) },
            valueRange = -5f..5f,
            steps = 19,
            // Деления не рисуем — 20 точек на полоске выглядят шумно
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
        Text(
            "Плюс — вам холоднее, чем большинству: подбор будет теплее. Минус — наоборот.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Выучено по отзывам: ${offsetText(learned)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (feedbackCount == 0) "Отвечайте вечером «холодно / нормально / жарко»"
                    else "$feedbackCount ${Fmt.plural(feedbackCount, "отзыв", "отзыва", "отзывов")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (feedbackCount > 0 || abs(learned) > 0.05) TextButton(onClick = onReset) { Text("Сбросить") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleSection(selected: Style?, onSelect: (Style?) -> Unit) {
    Column(Modifier.padding(horizontal = Dimens.screenPadding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Предпочитаемый стиль", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Любой") })
            Style.entries.forEach { style ->
                FilterChip(selected = selected == style, onClick = { onSelect(style) }, label = { Text(style.title) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
