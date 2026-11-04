package com.funactivity.ppmr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.repository.SettingsRepository
import com.funactivity.ppmr.domain.repository.SyncRepository
import com.funactivity.ppmr.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val signOut: SignOutUseCase,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val accountEmail: StateFlow<String?> = settingsRepository.appState
        .map { it.accountEmail }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWifiOnly(enabled: Boolean) = launch { settingsRepository.setWifiOnly(enabled) }
    fun setAutoSync(enabled: Boolean) = launch { settingsRepository.setAutoSync(enabled) }
    fun setIncludeSubfolders(enabled: Boolean) = launch { settingsRepository.setIncludeSubfolders(enabled) }
    fun setSuccessNotifications(enabled: Boolean) = launch { settingsRepository.setSuccessNotifications(enabled) }
    fun setMinFileAge(seconds: Int) = launch { settingsRepository.setMinFileAgeSeconds(seconds) }
    fun clearHistory() = launch { syncRepository.clearHistory() }
    fun disconnect() = launch { signOut(revokeDriveAccess = true) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
