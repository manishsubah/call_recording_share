package com.funactivity.ppmr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.funactivity.ppmr.data.auth.GoogleAuthRepository
import com.funactivity.ppmr.data.files.FileSharedRecordingStore
import com.funactivity.ppmr.data.files.StreamingContentHasher
import com.funactivity.ppmr.domain.repository.SharedRecordingStore
import com.funactivity.ppmr.domain.repository.ContentHasher
import com.funactivity.ppmr.domain.repository.SyncScheduler
import com.funactivity.ppmr.domain.usecase.SyncMessages
import com.funactivity.ppmr.sync.ResourceSyncMessages
import com.funactivity.ppmr.sync.WorkManagerSyncScheduler
import com.funactivity.ppmr.data.remote.DriveBaseUrl
import com.funactivity.ppmr.data.remote.DriveRestRepository
import com.funactivity.ppmr.domain.repository.AccessTokenProvider
import com.funactivity.ppmr.domain.repository.AuthRepository
import com.funactivity.ppmr.domain.repository.DriveRepository
import com.funactivity.ppmr.data.local.DataStoreSettingsRepository
import com.funactivity.ppmr.data.local.db.AppDatabase
import com.funactivity.ppmr.data.local.db.RoomSyncRepository
import com.funactivity.ppmr.data.local.db.SyncDao
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.data.files.SafRecordingFolderRepository
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindRecordingFolderRepository(
        impl: SafRecordingFolderRepository,
    ): RecordingFolderRepository

    @Binds
    abstract fun bindSyncRepository(impl: RoomSyncRepository): SyncRepository

    @Binds
    abstract fun bindAuthRepository(impl: GoogleAuthRepository): AuthRepository

    @Binds
    abstract fun bindAccessTokenProvider(impl: GoogleAuthRepository): AccessTokenProvider

    @Binds
    abstract fun bindSharedRecordingStore(impl: FileSharedRecordingStore): SharedRecordingStore

    @Binds
    abstract fun bindContentHasher(impl: StreamingContentHasher): ContentHasher

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindSyncMessages(impl: ResourceSyncMessages): SyncMessages

    @Binds
    abstract fun bindDriveRepository(impl: DriveRestRepository): DriveRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fun_activity.db").build()

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @DriveBaseUrl
    fun provideDriveBaseUrl(): String = "https://www.googleapis.com"

    @Provides
    fun provideSyncDao(database: AppDatabase): SyncDao = database.syncDao()
}
