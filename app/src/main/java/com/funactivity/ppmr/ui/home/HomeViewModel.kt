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
import com.funactivity.ppmr.domain.usecase.SelectSourceFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val folders: RecordingFolderRepository,
    private val selectSourceFolder: SelectSourceFolderUseCase,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val appState: StateFlow<AppState> = settingsRepository.appState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    private val _folderStatus = MutableStateFlow<FolderStatus>(FolderStatus.Checking)
    val folderStatus: StateFlow<FolderStatus> = _folderStatus.asStateFlow()

    val recentActivity: StateFlow<List<SyncItem>> = syncRepository.observeRecentActivity(ACTIVITY_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshFolder() {
        viewModelScope.launch {
            val treeUri = settingsRepository.appState.first().sourceTreeUri
            if (treeUri == null) {
                _folderStatus.value = FolderStatus.NotSelected
                return@launch
            }
            val settings: AppSettings = settingsRepository.settings.first()
            _folderStatus.value = when (val listing = folders.listRecordings(treeUri, settings.includeSubfolders)) {
                is FolderListing.Success -> FolderStatus.Ready(listing.files.size)
                FolderListing.PermissionLost -> FolderStatus.PermissionLost
                is FolderListing.Failed -> FolderStatus.ReadFailed
            }
        }
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            selectSourceFolder(uri.toString())
            refreshFolder()
        }
    }

    fun retry(identityKey: String) {
        viewModelScope.launch { syncRepository.retry(identityKey) }
    }

    private companion object {
        const val ACTIVITY_LIMIT = 20
    }
}
