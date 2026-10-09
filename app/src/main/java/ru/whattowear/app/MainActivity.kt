package ru.whattowear.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.whattowear.app.ui.navigation.AppRoot
import ru.whattowear.app.ui.theme.WhatToWearTheme
import ru.whattowear.app.update.UpdateState
import ru.whattowear.domain.model.UserSettings

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val loaded by viewModel.settings.collectAsStateWithLifecycle()
            val settings = loaded ?: UserSettings()

            // Первый запуск: на Android 13+ спрашиваем разрешение на утреннее уведомление один раз
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                viewModel.completeOnboarding(granted)
            }
            LaunchedEffect(loaded?.onboardingDone) {
                if (loaded?.onboardingDone == false) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.completeOnboarding(true)
                    }
                }
            }

            val update by viewModel.update.collectAsStateWithLifecycle()

            WhatToWearTheme(themeMode = settings.theme, dynamicColor = settings.dynamicColor) {
                AppRoot()
                (update as? UpdateState.Ready)?.let { ready ->
                    AlertDialog(
                        onDismissRequest = viewModel::postponeUpdate,
                        title = { Text("Доступна новая версия") },
                        text = { Text("Сборка ${ready.versionCode} уже скачана. Установить сейчас? Гардероб и настройки сохранятся.") },
                        confirmButton = { TextButton(onClick = viewModel::installUpdate) { Text("Установить") } },
                        dismissButton = { TextButton(onClick = viewModel::postponeUpdate) { Text("Позже") } },
                    )
                }
            }
        }
    }
}
