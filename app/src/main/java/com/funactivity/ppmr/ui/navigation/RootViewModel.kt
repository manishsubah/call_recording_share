package com.funactivity.ppmr.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funactivity.ppmr.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Read once: the start destination must not change underneath the running nav graph. */
    val onboardingDone: StateFlow<Boolean?> = flow<Boolean?> {
        emit(settingsRepository.appState.first().onboardingDone)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
