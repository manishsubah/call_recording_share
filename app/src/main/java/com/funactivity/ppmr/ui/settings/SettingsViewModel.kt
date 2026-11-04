package com.funactivity.ppmr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funactivity.ppmr.domain.model.AppSettings
import com.funactivity.ppmr.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setWifiOnly(enabled: Boolean) = launch { settingsRepository.setWifiOnly(enabled) }
    fun setAutoSync(enabled: Boolean) = launch { settingsRepository.setAutoSync(enabled) }
    fun setIncludeSubfolders(enabled: Boolean) = launch { settingsRepository.setIncludeSubfolders(enabled) }
    fun setSuccessNotifications(enabled: Boolean) = launch { settingsRepository.setSuccessNotifications(enabled) }
    fun setMinFileAge(seconds: Int) = launch { settingsRepository.setMinFileAgeSeconds(seconds) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
