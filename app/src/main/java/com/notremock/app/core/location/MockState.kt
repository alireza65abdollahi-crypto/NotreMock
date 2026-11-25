package com.notremock.app.core.location

import com.notremock.app.data.model.City
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MockErrorType {
    NONE,
    MOCK_APP_NOT_SELECTED,
    LOCATION_PERMISSION_MISSING,
    DEVICE_LOCATION_OFF
}

data class MockRuntimeState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val pausedByPackage: String? = null,
    val activeCity: City? = null,
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val errorType: MockErrorType = MockErrorType.NONE
)

object MockState {
    private val _state = MutableStateFlow(MockRuntimeState())
    val state: StateFlow<MockRuntimeState> = _state.asStateFlow()

    fun update(transform: (MockRuntimeState) -> MockRuntimeState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = MockRuntimeState()
    }
}
