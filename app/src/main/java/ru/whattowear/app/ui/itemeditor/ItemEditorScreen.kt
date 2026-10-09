package ru.whattowear.app.ui.itemeditor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.whattowear.app.ui.components.Dimens
import ru.whattowear.app.ui.components.ItemPhoto
import ru.whattowear.app.ui.components.SectionLabel
import ru.whattowear.app.ui.components.warmthLabel
import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingColor
import ru.whattowear.domain.model.Layer
import ru.whattowear.domain.model.Season
import ru.whattowear.domain.model.Style
import kotlin.math.roundToInt

/**
 * Добавление и редактирование вещи. [embedded] — открыт в правой панели планшета (без своей кнопки «назад»).
 */
@Composable
fun ItemEditorScreen(
    itemId: Long,
    onDone: () -> Unit,
    embedded: Boolean = false,
    viewModel: ItemEditorViewModel = hiltViewModel(key = "item-editor-$itemId"),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.consumeDone()
            onDone()
        }
    }
    if (!embedded) BackHandler(onBack = onDone)

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.onPhotoPicked(it.toString()) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCameraResult(success)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить вещь?") },
            text = { Text("«${state.name}» исчезнет из гардероба вместе с фото.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    if (!state.loaded) return

    Box(
        Modifier.fillMaxSize().then(if (embedded) Modifier else Modifier.statusBarsPadding()).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxSize()) {
            // Шапка: закрыть, заголовок, удалить, сохранить
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) {
                    Icon(if (embedded) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Закрыть без сохранения")
                }
                Text(if (state.isNew) "Новая вещь" else "Вещь", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (!state.isNew) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить") }
                }
                Button(onClick = viewModel::save, enabled = !state.saving && !state.photoBusy) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Сохранить")
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                PhotoSection(
                    state = state,
                    onCamera = { cameraLauncher.launch(Uri.parse(viewModel.newCameraUri())) },
                    onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemove = viewModel::removePhoto,
                )

                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = { Text("Название") },
                    placeholder = { Text("Например, синяя ветровка") },
                    isError = state.nameError,
                    supportingText = if (state.nameError) ({ Text("Введите название") }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                ChipGroup("Категория", Category.entries, state.category, { it.title }, viewModel::onCategory)

                if (state.category.subtypes.isNotEmpty()) {
                    ChipGroup("Вид", state.category.subtypes, state.subtype, { it.title }, viewModel::onSubtype)
                }

                WarmthSection(state.warmth, viewModel::onWarmth)

                if (state.category.isTorso) {
                    Segmented(
                        "Слой", Layer.entries, state.layer, { it.title }, viewModel::onLayer,
                        hint = "Базовый — к телу (футболка), средний — утепление (свитер), внешний — защита (куртка).",
                    )
                }

                if (state.category != Category.ACCESSORIES) {
                    Column {
                        SectionLabel("Защита")
                        SwitchRow("Не промокает", "Подходит для дождя и мокрого снега", state.waterproof, viewModel::onWaterproof)
                        SwitchRow("Защищает от ветра", "Плотная ткань, мембрана", state.windproof, viewModel::onWindproof)
                    }
                }

                Segmented("Стиль", Style.entries, state.style, { it.title }, viewModel::onStyle)

                ColorSection(state.color, viewModel::onColor)

                SeasonSection(state.seasons, viewModel::onSeason)

                SwitchRow(
                    title = "Доступна",
                    subtitle = "Выключите, если вещь в стирке — она не попадёт в подбор",
                    checked = state.available,
                    onChange = viewModel::onAvailable,
                )
            }
        }
    }
}

@Composable
private fun PhotoSection(state: ItemEditorState, onCamera: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            ItemPhoto(state.photoPath, state.category, Modifier.size(132.dp), iconSize = 44.dp)
            if (state.photoBusy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCamera, enabled = !state.photoBusy) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Камера")
            }
            OutlinedButton(onClick = onGallery, enabled = !state.photoBusy) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Галерея")
            }
            if (state.photoPath != null) TextButton(onClick = onRemove) { Text("Убрать фото") }
            if (state.photoFailed) {
                Text("Не удалось загрузить фото", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}

@Composable
private fun <T> Segmented(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    hint: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {},
                ) { Text(label(option), maxLines = 1) }
            }
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WarmthSection(warmth: Int, onChange: (Int) -> Unit) {
    Column {
        SectionLabel("Теплота")
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("$warmth", style = MaterialTheme.typography.headlineMedium)
            Text(" из 5 · ${warmthLabel(warmth)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        Slider(
            value = warmth.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3,
        )
        Text(
            "1 — майка или шорты, 3 — свитер или ветровка, 5 — пуховик или зимние ботинки.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSection(selected: ClothingColor, onSelect: (ClothingColor) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            SectionLabel("Цвет")
            Text(" · ${selected.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClothingColor.entries.forEach { color ->
                val isSelected = color == selected
                Box(
                    Modifier
                        .size(36.dp)
                        .border(BorderStroke(if (isSelected) 2.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent), CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(color.argb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onSelect(color) }
                        .semantics {
                            contentDescription = color.title
                            role = Role.RadioButton
                            this.selected = isSelected
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonSection(selected: Set<Season>, onToggle: (Season) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Сезон")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Season.entries.forEach { season ->
                FilterChip(selected = season in selected, onClick = { onToggle(season) }, label = { Text(season.title) })
            }
        }
        Text(
            if (selected.isEmpty()) "Ничего не выбрано — подходит для любого сезона." else "Вне сезона вещь предлагается реже.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(0.dp))
    }
}
