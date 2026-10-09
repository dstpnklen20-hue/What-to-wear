package ru.whattowear.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.whattowear.data.backup.WardrobeBackupImpl
import ru.whattowear.data.local.photos.PhotoStorageImpl
import ru.whattowear.data.local.prefs.SettingsRepositoryImpl
import ru.whattowear.data.location.FusedDeviceLocationSource
import ru.whattowear.data.repository.LocationRepositoryImpl
import ru.whattowear.data.repository.OutfitHistoryRepositoryImpl
import ru.whattowear.data.repository.WardrobeRepositoryImpl
import ru.whattowear.data.repository.WeatherRepositoryImpl
import ru.whattowear.domain.repository.DeviceLocationSource
import ru.whattowear.domain.repository.LocationRepository
import ru.whattowear.domain.repository.OutfitHistoryRepository
import ru.whattowear.domain.repository.PhotoStorage
import ru.whattowear.domain.repository.SettingsRepository
import ru.whattowear.domain.repository.WardrobeBackup
import ru.whattowear.domain.repository.WardrobeRepository
import ru.whattowear.domain.repository.WeatherRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun weather(impl: WeatherRepositoryImpl): WeatherRepository
    @Binds abstract fun locations(impl: LocationRepositoryImpl): LocationRepository
    @Binds abstract fun deviceLocation(impl: FusedDeviceLocationSource): DeviceLocationSource
    @Binds abstract fun wardrobe(impl: WardrobeRepositoryImpl): WardrobeRepository
    @Binds abstract fun photos(impl: PhotoStorageImpl): PhotoStorage
    @Binds abstract fun backup(impl: WardrobeBackupImpl): WardrobeBackup
    @Binds abstract fun history(impl: OutfitHistoryRepositoryImpl): OutfitHistoryRepository
}
