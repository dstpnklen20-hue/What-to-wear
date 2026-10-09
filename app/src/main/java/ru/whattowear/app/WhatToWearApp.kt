package ru.whattowear.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.whattowear.app.notification.Notifications
import ru.whattowear.app.widget.WidgetDataSource
import ru.whattowear.app.work.WidgetUpdater
import ru.whattowear.app.work.WorkScheduler
import ru.whattowear.domain.repository.SettingsRepository
import javax.inject.Inject

@HiltAndroidApp
class WhatToWearApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var widgetData: WidgetDataSource
    @Inject lateinit var widgetUpdater: WidgetUpdater

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        workScheduler.schedulePeriodicRefresh()

        // Утреннее уведомление перепланируется при каждом изменении его настроек
        appScope.launch {
            settingsRepository.settings
                .map { it.notificationsEnabled to it.notificationTime }
                .distinctUntilChanged()
                .collect { (enabled, time) -> workScheduler.scheduleMorningNotification(enabled, time) }
        }
        // Пока приложение запущено, виджет следует за данными (прогноз, гардероб, вариант комплекта)
        appScope.launch {
            widgetData.observe()
                .map { listOf(it.temperature, it.feels, it.weatherIcon, it.mainItemName, it.locationName, it.message) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(1_500)
                .collect { widgetUpdater.updateAll() }
        }
    }
}
