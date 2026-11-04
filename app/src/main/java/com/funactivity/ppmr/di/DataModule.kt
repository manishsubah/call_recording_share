package com.funactivity.ppmr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
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
import javax.inject.Singleton

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
    fun provideSyncDao(database: AppDatabase): SyncDao = database.syncDao()
}
