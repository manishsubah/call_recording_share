package com.funactivity.ppmr.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funactivity.ppmr.domain.model.AppState
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.usecase.SelectSourceFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val selectSourceFolder: SelectSourceFolderUseCase,
) : ViewModel() {
    val appState: StateFlow<AppState> = settings.appState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    private val _folderGrantFailed = MutableStateFlow(false)
    val folderGrantFailed: StateFlow<Boolean> = _folderGrantFailed.asStateFlow()

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch { _folderGrantFailed.value = !selectSourceFolder(uri.toString()) }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone()
            onDone()
        }
    }
}
