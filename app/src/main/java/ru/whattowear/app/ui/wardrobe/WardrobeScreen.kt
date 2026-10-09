package ru.whattowear.app.ui.wardrobe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.whattowear.app.ui.components.Dimens
import ru.whattowear.app.ui.components.EmptyState
import ru.whattowear.app.ui.components.ItemPhoto
import ru.whattowear.app.ui.components.ScreenHeader
import ru.whattowear.app.ui.itemeditor.ItemEditorScreen
import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.text.Fmt
import java.time.LocalDate

@Composable
fun WardrobeScreen(
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    viewModel: WardrobeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    var pendingImport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export(it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Пустой гардероб — спрашивать нечего
        if (state.totalCount == 0) viewModel.import(uri.toString(), replace = false) else pendingImport = uri.toString()
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Импорт гардероба") },
            text = { Text("Добавить вещи из файла к текущим или заменить ими весь гардероб?") },
            confirmButton = {
                TextButton(onClick = { viewModel.import(uri, replace = false); pendingImport = null }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.import(uri, replace = true); pendingImport = null }) { Text("Заменить") }
            },
        )
    }

    // На планшете: сетка слева, редактор справа (выбранная вещь или новая)
    var paneItemId by rememberSaveable { mutableStateOf<Long?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val twoPane = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                WardrobeList(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onFilterChange = viewModel::onFilterChange,
                    onOpen = { if (twoPane) paneItemId = it.id else onEditItem(it.id) },
                    onToggleAvailable = viewModel::toggleAvailable,
                    onAdd = { if (twoPane) paneItemId = 0L else onAddItem() },
                    onExport = { exportLauncher.launch("what-to-wear-wardrobe-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
            }
            if (twoPane) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.width(420.dp).fillMaxHeight()) {
                    val id = paneItemId
                    if (id == null) {
                        EmptyState(
                            icon = Icons.Outlined.Checkroom,
                            title = "Выберите вещь",
                            message = "Нажмите на карточку, чтобы изменить её, или добавьте новую вещь.",
                            primaryAction = "Добавить вещь" to { paneItemId = 0L },
                        )
                    } else {
                        ItemEditorScreen(itemId = id, onDone = { paneItemId = null }, embedded = true)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WardrobeList(
    state: WardrobeUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (WardrobeFilter) -> Unit,
    onOpen: (ClothingItem) -> Unit,
    onToggleAvailable: (ClothingItem) -> Unit,
    onAdd: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Гардероб",
                subtitle = if (state.totalCount > 0) {
                    "${state.totalCount} ${Fmt.plural(state.totalCount, "вещь", "вещи", "вещей")}" +
                        if (state.unavailableCount > 0) " · ${state.unavailableCount} в стирке" else ""
                } else null,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Ещё") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Экспорт в JSON") },
                                enabled = state.totalCount > 0,
                                onClick = { menuOpen = false; onExport() },
                            )
                            DropdownMenuItem(text = { Text("Импорт из JSON") }, onClick = { menuOpen = false; onImport() })
                        }
                    }
                },
            )
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding))

            when {
                !state.initialized -> Unit
                state.totalCount == 0 -> EmptyState(
                    icon = Icons.Outlined.Checkroom,
                    title = "Гардероб пуст",
                    message = "Добавьте вещи, которые носите: куртки, кофты, обувь, головные уборы. " +
                        "Чем полнее гардероб, тем точнее подбор под погоду.",
                    primaryAction = "Добавить первую вещь" to onAdd,
                    secondaryAction = "Импорт из файла" to onImport,
                )
                else -> {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Поиск по названию, цвету") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Очистить") }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = 4.dp),
                    )
                    FilterRow(state, onFilterChange)
                    if (state.items.isEmpty()) {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Dimens.screenPadding),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            ItemCard(
                                item = item,
                                onClick = { onOpen(item) },
                                onLongClick = { onToggleAvailable(item) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Долгое нажатие на вещь — «в стирке» и обратно.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (state.totalCount > 0) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Добавить") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.screenPadding),
            )
        }
    }
}

@Composable
private fun FilterRow(state: WardrobeUiState, onFilterChange: (WardrobeFilter) -> Unit) {
    val filters = buildList {
        add(WardrobeFilter.All to "Все")
        Category.entries.filter { it in state.categoriesInUse }.forEach { add(WardrobeFilter.ByCategory(it) to it.title) }
        if (state.unavailableCount > 0) add(WardrobeFilter.Unavailable to "В стирке")
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        items(filters.size) { i ->
            val (filter, label) = filters[i]
            FilterChip(
                selected = state.filter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(item: ClothingItem, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "В стирку или обратно")
            .semantics {
                contentDescription = "${item.name}, ${item.category.title}, теплота ${item.warmth} из 5" +
                    if (!item.isAvailable) ", в стирке" else ""
            },
    ) {
        Box {
            ItemPhoto(
                item,
                Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (item.isAvailable) 1f else 0.4f),
                iconSize = 40.dp,
            )
            if (!item.isAvailable) {
                Text(
                    "В стирке",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WarmthDots(item.warmth)
            if (item.waterproof) Icon(Icons.Outlined.WaterDrop, contentDescription = "Непромокаемая", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            if (item.windproof) Icon(Icons.Outlined.Air, contentDescription = "Ветрозащита", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

/** Теплота 1–5 точками: заполненные — onSurface, пустые — контур. */
@Composable
fun WarmthDots(warmth: Int, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.onSurface
    val off = MaterialTheme.colorScheme.outline
    androidx.compose.foundation.Canvas(modifier.width(46.dp).height(6.dp)) {
        val r = size.height / 2
        val step = (size.width - 2 * r) / 4
        for (i in 0 until 5) {
            val c = androidx.compose.ui.geometry.Offset(r + i * step, r)
            if (i < warmth) drawCircle(on, r, c)
            else drawCircle(off, r - 0.5f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
    }
}
