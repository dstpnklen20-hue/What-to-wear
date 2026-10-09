package ru.whattowear.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.whattowear.data.local.db.AppDatabase
import ru.whattowear.data.remote.MetNoApi
import ru.whattowear.data.remote.OpenMeteoApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** MET Norway требует User-Agent с названием приложения; без него запросы блокируются (403). */
    private const val USER_AGENT = "WhatToWear/1.0 (Android; ru.whattowear.app)"

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun openMeteo(retrofit: Retrofit): OpenMeteoApi = retrofit.create(OpenMeteoApi::class.java)

    @Provides
    @Singleton
    fun metNo(retrofit: Retrofit): MetNoApi = retrofit.create(MetNoApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun locationDao(db: AppDatabase) = db.locationDao()
    @Provides fun forecastDao(db: AppDatabase) = db.forecastDao()
    @Provides fun clothingDao(db: AppDatabase) = db.clothingDao()
    @Provides fun outfitLogDao(db: AppDatabase) = db.outfitLogDao()
    @Provides fun feedbackDao(db: AppDatabase) = db.feedbackDao()
}
