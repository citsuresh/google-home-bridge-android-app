/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.camera

import android.util.Log
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.camera.CameraStreamState.ERROR
import com.example.googlehomeapisampleapp.camera.CameraStreamState.INITIALIZED
import com.example.googlehomeapisampleapp.camera.CameraStreamState.NOT_STARTED
import com.example.googlehomeapisampleapp.camera.CameraStreamState.READY_OFF
import com.example.googlehomeapisampleapp.camera.CameraStreamState.READY_ON
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STARTING
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STOPPING
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STREAMING_WITHOUT_TALKBACK
import com.example.googlehomeapisampleapp.camera.CameraStreamState.STREAMING_WITH_TALKBACK
import com.google.home.google.CameraAvStreamManagement
import com.example.googlehomeapisampleapp.camera.livestreamplayer.CameraAvStreamManagementController
import com.example.googlehomeapisampleapp.camera.livestreamplayer.CameraAvStreamManagementControllerFactory
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayer
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayerFactory
import com.example.googlehomeapisampleapp.camera.livestreamplayer.LiveStreamPlayerState
import com.example.googlehomeapisampleapp.camera.livestreamplayer.OnOffController
import com.example.googlehomeapisampleapp.camera.livestreamplayer.OnOffControllerFactory
import com.google.home.HomeClient
import com.google.home.HomeDevice
import com.google.home.Id
import com.google.home.google.GoogleCameraDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** ViewModel for the camera stream view. */
@HiltViewModel
class CameraStreamViewModel
@Inject
internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val homeClient: HomeClient,
    private val liveStreamPlayerFactory: LiveStreamPlayerFactory,
    private val onOffControllerFactory: OnOffControllerFactory,
    // Dependency for Audio Control
    private val cameraAvStreamManagementControllerFactory: CameraAvStreamManagementControllerFactory,
) : ViewModel() {

    private val TAG = "CameraStreamViewModel"
    private val _targetDeviceId = MutableStateFlow<String?>(null)
    private val _microphonePermissionGranted = MutableStateFlow(false)

    fun initialize(id: String, microphonePermissionGranted: Boolean) {
        _targetDeviceId.value = id
        _microphonePermissionGranted.value = microphonePermissionGranted
    }

    private var surface: Surface? = null

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _liveStreamPlayer = MutableStateFlow<LiveStreamPlayer?>(null)
    private val liveStreamPlayer: StateFlow<LiveStreamPlayer?> = _liveStreamPlayer

    private val _onOffController = MutableStateFlow<OnOffController?>(null)
    private val onOffController: StateFlow<OnOffController?> = _onOffController

    // Audio Controller Flow
    private val _cameraAvStreamManagementController = MutableStateFlow<CameraAvStreamManagementController?>(null)
    private val cameraAvStreamManagementController: StateFlow<CameraAvStreamManagementController?> = _cameraAvStreamManagementController

    @OptIn(ExperimentalCoroutinesApi::class) val isRecording: StateFlow<Boolean> =
        onOffController
            .flatMapLatest { it?.isRecording ?: flowOf(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Audio Recording State Flows (Stabilized)
    @OptIn(ExperimentalCoroutinesApi::class)
    val isAudioRecording: StateFlow<Boolean> =
        cameraAvStreamManagementController
            .flatMapLatest { controller ->
                // Muted=True means Recording=False. So we invert it.
                controller?.isRecordingMicrophoneMuted?.map { !it } ?: flowOf(false)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _isToggleAudioRecordingInProgress = MutableStateFlow(false)
    val isToggleAudioRecordingInProgress: StateFlow<Boolean> = _isToggleAudioRecordingInProgress

    val supportsTalkback: StateFlow<Boolean> =
        liveStreamPlayer
            .map { it?.supportsTalkback == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val isTalkbackEnabled: Flow<Boolean> =
        liveStreamPlayer.flatMapLatest { it?.isTalkbackEnabled ?: flowOf(false) }

    private val _isToggleRecordingInProgress = MutableStateFlow(false)
    val isToggleRecordingInProgress: StateFlow<Boolean> = _isToggleRecordingInProgress

    private val _isToggleTalkbackInProgress = MutableStateFlow(false)
    val isToggleTalkbackInProgress: StateFlow<Boolean> = _isToggleTalkbackInProgress

    private val isPaused = MutableStateFlow(false)

    private val isBackgroundPaused: Flow<Boolean> =
        isPaused
            .map {
                if (it) {
                    backgroundStop()
                }
                it
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow(NOT_STARTED)
    val state: StateFlow<CameraStreamState> = _state

    @OptIn(ExperimentalCoroutinesApi::class)
    private val livestreamPlayerState: Flow<LiveStreamPlayerState> =
        liveStreamPlayer.flatMapLatest { it?.state ?: flowOf(LiveStreamPlayerState.NOT_STARTED) }
    private var playerJob: Job? = null
    private var startJob: Job? = null

    init {
        viewModelScope.launch {
            _targetDeviceId
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { id ->
                    Log.d(TAG, "New targetDeviceId received: $id. Initializing resources.")
                    val success = setupDeviceResources(id)
                    if (success) {
                        _state
                            .map { state -> handleCameraStreamState(state, id) }
                            .collect { newState ->
                                _state.value = newState
                            }
                    }
                }
        }
    }

    private suspend fun setupDeviceResources(deviceId: String): Boolean {
        _state.value = NOT_STARTED
        stopPlayer()
        _onOffController.value = null
        _cameraAvStreamManagementController.value = null

        val device = getCameraDevice(deviceId)
        if (device == null) {
            _errorMessage.value = "Device not found for ID: $deviceId"
            _state.value = ERROR
            return false
        }

        val player = liveStreamPlayerFactory.createPlayerFromDevice(
            device,
            viewModelScope,
            _microphonePermissionGranted.value
        )
        if (player == null) {
            _errorMessage.value = "Failed to create player for device"
            _state.value = ERROR
            return false
        }
        _liveStreamPlayer.value = player

        val controller = onOffControllerFactory.create(device)
        if (controller == null) {
            _errorMessage.value = "Failed to create on/off controller for device"
            _state.value = ERROR
            return false
        }
        _onOffController.value = controller
        viewModelScope.launch { controller.isRecording.collect { handleIsRecordingChange(it) } }

        // Initialize Audio Controller
        val audioController = cameraAvStreamManagementControllerFactory.create(device)
        if (audioController == null) {
            Log.w(TAG, "Failed to create CameraAvStreamManagementController, audio setting will be unavailable.")
        }
        _cameraAvStreamManagementController.value = audioController

        _state.value = INITIALIZED
        return true
    }

    private suspend fun initializeForDevice(deviceId: String) {
        _state.value = NOT_STARTED
        stopPlayer()
        _onOffController.value = null
        _cameraAvStreamManagementController.value = null

        val device = getCameraDevice(deviceId)
        if (device == null) {
            _errorMessage.value = "Device not found for ID: $deviceId"
            _state.value = ERROR
            return
        }

        val player = liveStreamPlayerFactory.createPlayerFromDevice(
            device,
            viewModelScope,
            _microphonePermissionGranted.value
        )
        if (player == null) {
            _errorMessage.value = "Failed to create player for device"
            _state.value = ERROR
            return
        }
        _liveStreamPlayer.value = player

        val controller = onOffControllerFactory.create(device)
        if (controller == null) {
            _errorMessage.value = "Failed to create on/off controller for device"
            _state.value = ERROR
            return
        }
        _onOffController.value = controller
        viewModelScope.launch { controller.isRecording.collect { handleIsRecordingChange(it) } }

        val audioController = cameraAvStreamManagementControllerFactory.create(device)
        if (audioController == null) {
            Log.w(TAG, "Failed to create CameraAvStreamManagementController. Audio settings will be unavailable, but not erroring out as video stream is still possible.")
        }
        _cameraAvStreamManagementController.value = audioController

        _state.value = INITIALIZED
    }

    private suspend fun handleLiveStreamPlayerState(state: LiveStreamPlayerState) {
        Log.d(TAG, "liveStreamPlayer state: $state")
        when (state) {
            LiveStreamPlayerState.STREAMING -> {
                val viewModelState = _state.value
                val isValidState =
                    viewModelState == STREAMING_WITHOUT_TALKBACK ||
                            viewModelState == STREAMING_WITH_TALKBACK ||
                            viewModelState == STARTING
                if (isValidState) {
                    if (isTalkbackEnabled.first()) {
                        _state.value = STREAMING_WITH_TALKBACK
                    } else {
                        _state.value = STREAMING_WITHOUT_TALKBACK
                    }
                }
            }
            LiveStreamPlayerState.DISPOSED -> {
                if (_state.value != ERROR && _state.value != STOPPING) {
                    _state.value = STOPPING
                }
            }
            else -> {}
        }
    }

    private suspend fun handleCameraStreamState(state: CameraStreamState, deviceId: String): CameraStreamState {
        Log.d(TAG, "handleCameraStreamState: $state")
        return when (state) {
            NOT_STARTED -> {
                if (!initializeOnOffController(deviceId)) {
                    ERROR
                } else {
                    INITIALIZED
                }
            }
            INITIALIZED -> {
                isBackgroundPaused.first { !it }

                if (isRecording.first()) {
                    READY_ON
                } else {
                    READY_OFF
                }
            }
            READY_OFF -> {
                if (isRecording.first()) {
                    READY_ON
                } else {
                    READY_OFF
                }
            }
            READY_ON -> {
                if (startPlayer(deviceId)) {
                    STARTING
                } else {
                    ERROR
                }
            }
            STOPPING -> {
                stopPlayer()
                INITIALIZED
            }
            ERROR -> {
                stopPlayer()
                ERROR
            }
            else -> {
                state
            }
        }
    }

    private suspend fun initializeOnOffController(deviceId:String): Boolean {
        val device = getCameraDevice(deviceId)
        val controller = onOffControllerFactory.create(device)
        if (controller == null) {
            _errorMessage.value = "Failed to create on/off controller for device"
            _state.value = ERROR
            return false
        }
        _onOffController.value = controller
        viewModelScope.launch { isRecording.collect { handleIsRecordingChange(it) } }

        // Initialize Audio Controller
        val audioController = cameraAvStreamManagementControllerFactory.create(device)
        if (audioController == null) {
            Log.w(TAG, "Failed to create CameraAvStreamManagementController, audio setting will be unavailable.")
        }
        _cameraAvStreamManagementController.value = audioController

        return true
    }

    private fun handleIsRecordingChange(isRecording: Boolean) {
        if (isRecording) {
            if (_state.value == READY_OFF) {
                _state.value = READY_ON
            }
        } else {
            if (
                _state.value != NOT_STARTED &&
                _state.value != ERROR &&
                _state.value != READY_OFF &&
                _state.value != STOPPING
            ) {
                _state.value = STOPPING
            }
        }
    }

    private suspend fun startPlayer(deviceId: String): Boolean {
        stopPlayer()
        Log.d(TAG, "startPlayer for ID: $deviceId")
        val device = getCameraDevice(deviceId)

        val player = liveStreamPlayerFactory.createPlayerFromDevice(
            device,
            viewModelScope,
            _microphonePermissionGranted.value
        )
        if (player == null) {
            _errorMessage.value = "Failed to create player for device"
            _state.value = ERROR
            return false
        }
        _liveStreamPlayer.value = player
        surface?.let { player.attachRenderer(it) }

        playerJob =
            viewModelScope.launch { livestreamPlayerState.collect { handleLiveStreamPlayerState(it) } }

        startJob = viewModelScope.launch { player.start() }
        return true
    }

    private suspend fun stopPlayer() {
        Log.d(TAG, "stopPlayer")
        startJob?.cancelAndJoin()
        playerJob?.cancelAndJoin()
        liveStreamPlayer.value?.dispose()
        _liveStreamPlayer.value = null
        playerJob = null
    }

    private fun backgroundStop() {
        if (
            _state.value == READY_ON ||
            _state.value == STARTING ||
            _state.value == STREAMING_WITHOUT_TALKBACK ||
            _state.value == STREAMING_WITH_TALKBACK
        ) {
            Log.d(TAG, "Background: stopping player")
            _state.value = STOPPING
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(NonCancellable) { stopPlayer() }
    }

    fun onPause() {
        isPaused.value = true
        if (_state.value == READY_OFF) {
            _state.value = INITIALIZED
        }
    }

    fun onResume() {
        isPaused.value = false
    }

    fun onSurfaceCreated(surface: Surface) {
        if (this.surface != null) {
            _liveStreamPlayer.value?.detachRenderer()
        }
        this.surface = surface
        viewModelScope.launch { _liveStreamPlayer.filterNotNull().first().attachRenderer(surface) }
    }

    fun onSurfaceDestroyed() {
        _liveStreamPlayer.value?.detachRenderer()
        this.surface = null
    }

    private suspend fun getCameraDevice(deviceId: String): HomeDevice {
        return requireNotNull(homeClient.devices().get(Id(deviceId)))
    }

    fun setTalkback(enabled: Boolean) {
        val player = liveStreamPlayer.value ?: return
        if (
            _state.value == STREAMING_WITHOUT_TALKBACK ||
            _state.value == STREAMING_WITH_TALKBACK
        ) {
            viewModelScope.launch {
                _isToggleTalkbackInProgress.value = true
                player.toggleTalkback(enabled)
                if (enabled) {
                    _state.value = STREAMING_WITH_TALKBACK
                } else {
                    _state.value = STREAMING_WITHOUT_TALKBACK
                }
                _isToggleTalkbackInProgress.value = false
            }
        }
    }

    /**
     * Set the main camera recording (ON/OFF) state.
     * This handles video power and streaming state machine transition.
     * * @param enabled Whether to enable or disable recording.
     */
    fun setRecording(enabled: Boolean) {
        if (_state.value == NOT_STARTED || _state.value == ERROR) {
            return
        }
        val onOffController = onOffController.value ?: return
        viewModelScope.launch {
            _isToggleRecordingInProgress.value = true
            withTimeoutOrNull(TOGGLE_RECORDING_WAIT_TIME_MILLISECONDS) {

                val toggleSuccess = onOffController.setRecording(enabled)

                if (!toggleSuccess) {
                    _errorMessage.value = "Failed to toggle recording"
                } else if (!enabled) {
                    // Stop player optimistically to clear video feed
                    stopPlayer()
                }
                // Wait for the cloud to confirm the recording state change
                isRecording.first { it == enabled }
            }
            _isToggleRecordingInProgress.value = false
        }
    }

    /**
     * Set audio recording on or off (microphone for clips).
     */
    fun setAudioRecording(enabled: Boolean) {
        if (_state.value == NOT_STARTED || _state.value == ERROR) return
        val audioController = cameraAvStreamManagementController.value ?: return

        viewModelScope.launch {
            _isToggleAudioRecordingInProgress.value = true

            val initialUiState = isAudioRecording.value
            Log.i(TAG, "Toggle Audio: Initial UI State: $initialUiState, Target: $enabled")

            withTimeoutOrNull(TOGGLE_RECORDING_WAIT_TIME_MILLISECONDS) {
                // Translate ON=false/OFF=true (Muted)
                val muteState = !enabled
                Log.i(TAG, "Audio Command: Sending Muted state: $muteState")
                val toggleSuccess = audioController.setRecordingMicrophoneMuted(muteState)

                if (!toggleSuccess) {
                    _errorMessage.value = "Failed to toggle audio recording (API Call Failed)"
                    Log.e(TAG, "Audio Command: FAILED to send command to controller.")
                } else {
                    Log.d(TAG, "Audio Command: SUCCESS. Waiting for state update.")
                    // Wait for the cloud to confirm the recording state change
                    isAudioRecording.first { it == enabled }
                }
            }
            _isToggleAudioRecordingInProgress.value = false
        }
    }


    fun errorShown() {
        _errorMessage.value = null
    }

    companion object {
        private const val TOGGLE_RECORDING_WAIT_TIME_MILLISECONDS = 4000L
    }
}

/**
 * Enum class for the camera stream state.
 *
 * @property NOT_STARTED The camera stream has not been initialized yet.
 * @property INITIALIZED The camera stream has been initialized.
 * @property READY_OFF The player is ready to be initialized and the camera is off.
 * @property READY_ON The player is ready to be initialized and the camera is on.
 * @property STARTING The player is starting.
 * @property STREAMING_WITHOUT_TALKBACK The player is streaming without talkback.
 * @property STREAMING_WITH_TALKBACK The player is streaming with talkback.
 * @property STOPPING The camera stream is stopping.
 * @property ERROR The camera stream has encountered an error. Can happen at any state.
 */
enum class CameraStreamState {
    NOT_STARTED,
    INITIALIZED,
    READY_OFF,
    READY_ON,
    STARTING,
    STREAMING_WITHOUT_TALKBACK,
    STREAMING_WITH_TALKBACK,
    STOPPING,
    ERROR,
}