package ru.whattowear.app.ui.locations

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.whattowear.app.ui.components.Dimens
import ru.whattowear.app.ui.components.SectionLabel
import ru.whattowear.app.ui.components.ThinDivider
import ru.whattowear.domain.model.Location

@Composable
fun LocationsScreen(onBack: () -> Unit, viewModel: LocationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onPermissionResult(result.values.any { it })
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад") }
                Text("Места", style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Найти город") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Очистить") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
            )

            if (state.query.isNotBlank()) {
                SearchResults(state.search, onPick = viewModel::addPlace)
            } else {
                SavedLocations(
                    state = state,
                    onSelect = viewModel::select,
                    onDelete = viewModel::delete,
                    onLocate = {
                        if (state.hasPermission) viewModel.locateNow()
                        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResults(search: SearchState, onPick: (ru.whattowear.domain.model.GeoPlace) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (search) {
        SearchState.Idle -> Hint("Введите хотя бы две буквы названия.")
        SearchState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
        is SearchState.Error -> Hint(if (search.noNetwork) "Нет подключения к интернету." else "Поиск сейчас недоступен, попробуйте позже.")
        is SearchState.Results -> if (search.places.isEmpty()) {
            Hint("Ничего не найдено. Проверьте написание.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(search.places, key = { "${it.latitude},${it.longitude},${it.name}" }) { place ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onPick(place) }
                            .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
                    ) {
                        Text(place.name, style = MaterialTheme.typography.bodyLarge)
                        if (place.subtitle.isNotEmpty()) Text(place.subtitle, style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
    )
}

@Composable
private fun SavedLocations(
    state: LocationsUiState,
    onSelect: (Long) -> Unit,
    onDelete: (Location) -> Unit,
    onLocate: () -> Unit,
) {
    val device = state.locations.firstOrNull { it.isDeviceLocation }
    val saved = state.locations.filter { !it.isDeviceLocation }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionLabel("Текущее местоположение", Modifier.padding(horizontal = Dimens.screenPadding, vertical = 8.dp)) }
        item {
            if (device != null) {
                LocationRow(device, selected = device.id == state.selectedId, onSelect = { onSelect(device.id) }, onDelete = null, trailing = {
                    TextButton(onClick = onLocate, enabled = !state.locating) { Text(if (state.locating) "Ищем…" else "Обновить") }
                })
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !state.locating, onClick = onLocate)
                        .padding(horizontal = Dimens.screenPadding, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (state.locating) "Определяем…" else "Определять автоматически",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
            if (state.locateFailed) {
                Hint("Не удалось определить местоположение. Проверьте, что геолокация включена, или выберите город поиском.")
            }
        }
        item { SectionLabel("Сохранённые города", Modifier.padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 20.dp, bottom = 8.dp)) }
        if (saved.isEmpty()) {
            item { Hint("Найдите город через поиск выше — он сохранится в этом списке.") }
        }
        items(saved, key = { it.id }) { location ->
            LocationRow(location, selected = location.id == state.selectedId, onSelect = { onSelect(location.id) }, onDelete = { onDelete(location) })
            ThinDivider(Modifier.padding(start = 64.dp, end = Dimens.screenPadding))
        }
    }
}

@Composable
private fun LocationRow(
    location: Location,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (location.isDeviceLocation) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(location.name, style = MaterialTheme.typography.bodyLarge)
            }
            if (location.subtitle.isNotEmpty()) {
                Text(location.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить ${location.name}") }
        }
    }
}
