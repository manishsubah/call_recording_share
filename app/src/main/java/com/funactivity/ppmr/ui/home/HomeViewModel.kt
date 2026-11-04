package com.funactivity.ppmr.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.model.AppState
import com.funactivity.ppmr.domain.model.FolderListing
import com.funactivity.ppmr.domain.model.SyncItem
import com.funactivity.ppmr.domain.repository.RecordingFolderRepository
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.usecase.ManualSyncUseCase
import com.funactivity.ppmr.domain.usecase.ObserveSyncEventsUseCase
import com.funactivity.ppmr.domain.usecase.RetryUploadUseCase
import com.funactivity.ppmr.domain.usecase.SelectSourceFolderUseCase
import com.funactivity.ppmr.domain.usecase.SignOutUseCase
import com.funactivity.ppmr.sync.ConnectivityMonitor
import com.funactivity.ppmr.sync.NetworkState
import com.funactivity.ppmr.sync.SyncProgress
import com.funactivity.ppmr.sync.SyncProgressTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SyncNotice { NONE, NO_NETWORK, WAITING_FOR_WIFI }

data class HomeUiState(
    val appState: AppState = AppState(),
    val settings: AppSettings = AppSettings(),
    val folderStatus: FolderStatus = FolderStatus.Checking,
    val activity: List<SyncItem> = emptyList(),
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val progress: SyncProgress = SyncProgress.Idle,
    val notice: SyncNotice = SyncNotice.NONE,
) {
    val signedIn: Boolean get() = appState.accountEmail != null
    val canSync: Boolean
        get() = signedIn && !appState.authRequired && appState.sourceTreeUri != null &&
            folderStatus !is FolderStatus.PermissionLost
}

private data class Counts(val pending: Int, val failed: Int)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val folders: RecordingFolderRepository,
    private val selectSourceFolder: SelectSourceFolderUseCase,
    private val manualSync: ManualSyncUseCase,
    private val retryUpload: RetryUploadUseCase,
    private val signOutUseCase: SignOutUseCase,
    observeSyncEvents: ObserveSyncEventsUseCase,
    syncRepository: SyncRepository,
    tracker: SyncProgressTracker,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    private val folderStatus = MutableStateFlow<FolderStatus>(FolderStatus.Checking)

    private val folderGrantFailures = Channel<Unit>(Channel.CONFLATED)
    val folderGrantFailed: Flow<Unit> = folderGrantFailures.receiveAsFlow()

    private val counts = combine(
        syncRepository.observePendingCount(),
        syncRepository.observeFailedCount(),
        ::Counts,
    )

    private val base = combine(
        settingsRepository.appState,
        settingsRepository.settings,
        folderStatus,
        observeSyncEvents(ACTIVITY_LIMIT),
        counts,
    ) { appState, settings, status, activity, counts ->
        HomeUiState(
            appState = appState,
            settings = settings,
            folderStatus = status,
            activity = activity,
            pendingCount = counts.pending,
            failedCount = counts.failed,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(base, tracker.progress, connectivity.observe()) { state, progress, network ->
        state.copy(progress = progress, notice = noticeFor(state, network))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun noticeFor(state: HomeUiState, network: NetworkState): SyncNotice = when {
        state.pendingCount == 0 -> SyncNotice.NONE
        !network.connected -> SyncNotice.NO_NETWORK
        !network.allowsUpload(state.settings.wifiOnly) -> SyncNotice.WAITING_FOR_WIFI
        else -> SyncNotice.NONE
    }

    fun refreshFolder() {
        viewModelScope.launch {
            val treeUri = settingsRepository.appState.first().sourceTreeUri
            if (treeUri == null) {
                folderStatus.value = FolderStatus.NotSelected
                return@launch
            }
            val includeSubfolders = settingsRepository.settings.first().includeSubfolders
            folderStatus.value = when (val listing = folders.listRecordings(treeUri, includeSubfolders)) {
                is FolderListing.Success -> FolderStatus.Ready(listing.files.size)
                FolderListing.PermissionLost -> FolderStatus.PermissionLost
                is FolderListing.Failed -> FolderStatus.ReadFailed
            }
        }
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            if (!selectSourceFolder(uri.toString())) folderGrantFailures.send(Unit)
            refreshFolder()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            manualSync()
            refreshFolder()
        }
    }

    fun retry(identityKey: String) {
        viewModelScope.launch { retryUpload(identityKey) }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSync(enabled) }
    }

    fun signOut() {
        viewModelScope.launch { signOutUseCase(revokeDriveAccess = false) }
    }

    private companion object {
        const val ACTIVITY_LIMIT = 20
    }
}
