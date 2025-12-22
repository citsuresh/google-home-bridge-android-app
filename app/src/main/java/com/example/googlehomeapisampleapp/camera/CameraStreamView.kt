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

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.googlehomeapisampleapp.R
import com.example.googlehomeapisampleapp.RuntimePermissionsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraStreamView(
    deviceId: String,
    paddingValues: PaddingValues,
    onShowSnackbar: (String) -> Unit,
    viewModel: CameraStreamViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var microphonePermissionGranted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            microphonePermissionGranted = isGranted
            if (!isGranted) onShowSnackbar("Microphone permission denied.")
        }
    )

    val activity = LocalActivity.current
    val permissionsManager = remember(activity) {
        activity?.let {
            RuntimePermissionsManager(it as ComponentActivity, launcher) { isGranted ->
                microphonePermissionGranted = isGranted
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
        permissionsManager?.checkMicrophonePermission()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }

    LaunchedEffect(deviceId, microphonePermissionGranted) {
        viewModel.initialize(deviceId, microphonePermissionGranted)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { onShowSnackbar(it); viewModel.errorShown() }
    }

    val currentViewModel by rememberUpdatedState(viewModel)
    DisposableEffect(currentViewModel) {
        onDispose { currentViewModel.onPause() }
    }

    Scaffold(
        modifier = Modifier.padding(paddingValues).fillMaxSize().testTag("CameraStreamScreen"),
        floatingActionButton = {
            FloatingActionButton(onClick = { showBottomSheet = true }, shape = CircleShape) {
                Icon(imageVector = Icons.Filled.Menu, contentDescription = "Open Menu")
            }
        },
    ) { innerPadding ->
        val supportsTalkback by viewModel.supportsTalkback.collectAsStateWithLifecycle()
        val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
        val isToggleRecordingInProgress by viewModel.isToggleRecordingInProgress.collectAsStateWithLifecycle()
        val isToggleTalkbackInProgress by viewModel.isToggleTalkbackInProgress.collectAsStateWithLifecycle()
        val playerState by viewModel.state.collectAsStateWithLifecycle()

        val isTalkbackEnabled by remember(playerState) {
            mutableStateOf(playerState == CameraStreamState.STREAMING_WITH_TALKBACK)
        }
        val isCurrentlyStreaming by
        remember(playerState, isToggleRecordingInProgress) {
            mutableStateOf(
                (playerState == CameraStreamState.STREAMING_WITH_TALKBACK ||
                        playerState == CameraStreamState.STREAMING_WITHOUT_TALKBACK) &&
                        !isToggleRecordingInProgress
            )
        }
        val isCameraToggleable =
            !isToggleRecordingInProgress &&
                    playerState != CameraStreamState.ERROR &&
                    playerState != CameraStreamState.NOT_STARTED

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            CameraStreamPlayer(
                playerState = playerState,
                onSurfaceCreated = { surface -> viewModel.onSurfaceCreated(surface) },
                onSurfaceDestroyed = { viewModel.onSurfaceDestroyed() },
                isCurrentlyStreaming = isCurrentlyStreaming,
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false;
                    showSettingsScreen = false
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                if (showSettingsScreen) {
                    CameraSettingsScreen(
                        viewModel = viewModel,
                        isLiveMicEnabled = isTalkbackEnabled,
                        isCameraOn = isRecording,
                    ) {
                        showSettingsScreen = false
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        // CAMERA POWER TOGGLE
                        ListItem(
                            headlineContent = { Text(text = "Camera Power") },
                            supportingContent = { Text(text = if (isRecording) "On" else "Off") },
                            trailingContent = {
                                if (isToggleRecordingInProgress) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Switch(
                                        checked = isRecording,
                                        onCheckedChange = { viewModel.setRecording(it) },
                                        enabled = isCameraToggleable,
                                    )
                                }
                            },
                        )

                        // MICROPHONE (TALKBACK) TOGGLE
                        if (supportsTalkback) {
                            ListItem(
                                headlineContent = { Text("Microphone (Talkback)") },
                                supportingContent = { Text(if (isTalkbackEnabled) "On" else "Off") },
                                trailingContent = {
                                    Switch(
                                        checked = isTalkbackEnabled,
                                        onCheckedChange = { isEnabled ->
                                            if (isEnabled) {
                                                if (microphonePermissionGranted) viewModel.setTalkback(true) else permissionsManager?.requestMicrophonePermission()
                                            } else viewModel.setTalkback(false)
                                        },
                                        enabled =
                                            !isToggleTalkbackInProgress &&
                                                    !isToggleRecordingInProgress &&
                                                    isCurrentlyStreaming,
                                    )
                                },
                            )
                        }


                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Navigate to Settings Screen (for Audio Recording)
                        ListItem(
                            headlineContent = { Text("Advanced Settings") },
                            modifier = Modifier.clickable { showSettingsScreen = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraSettingsScreen(
    viewModel: CameraStreamViewModel,
    isLiveMicEnabled: Boolean,
    isCameraOn: Boolean,
    onClose: () -> Unit,
) {
    // Intercept the Android Back Button when on this screen to go back to main menu
    BackHandler(enabled = true) {
        onClose()
    }

    // Audio Recording States
    val isAudioRecordingEnabled by viewModel.isAudioRecording.collectAsStateWithLifecycle()
    val isToggleAudioRecordingInProgress by viewModel.isToggleAudioRecordingInProgress.collectAsStateWithLifecycle()

    // Conditional enablement based on Camera ON/OFF and Live Mic status
    val isAudioToggleable = !isToggleAudioRecordingInProgress
    val isAudioRecordingToggleActive = isLiveMicEnabled && isAudioToggleable && isCameraOn

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Advanced Camera Settings") },
            modifier = Modifier.clickable { onClose() },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // AUDIO RECORDING SETTING
        ListItem(
            headlineContent = { Text(stringResource(R.string.audio_recording_setting)) },
            supportingContent = {
                Text(
                    when {
                        !isCameraOn -> "Disabled (Camera Power Off)"
                        !isLiveMicEnabled -> "Disabled (Talkback Mic Off)"
                        isAudioRecordingEnabled -> "On"
                        else -> "Off"
                    }
                )
            },
            trailingContent = {
                if (isToggleAudioRecordingInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Switch(
                        checked = isAudioRecordingEnabled,
                        onCheckedChange = { viewModel.setAudioRecording(it) },
                        enabled = isAudioRecordingToggleActive,
                    )
                }
            },
        )
    }
}

@Composable
fun CameraStreamPlayer(
    playerState: CameraStreamState,
    onSurfaceCreated: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    isCurrentlyStreaming: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val currentOnSurfaceCreated by rememberUpdatedState(onSurfaceCreated)
    val currentOnSurfaceDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    val callback = remember {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { currentOnSurfaceCreated(holder.surface) }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { currentOnSurfaceDestroyed() }
        }
    }
    val surfaceView: SurfaceView = remember { SurfaceView(context).apply { holder.addCallback(callback) } }
    surfaceView.visibility = if (isCurrentlyStreaming) View.VISIBLE else View.INVISIBLE

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black)) {
        AndroidView(factory = { surfaceView }, modifier = Modifier.fillMaxSize())
        if (playerState == CameraStreamState.ERROR) {
            Text(
                text = "Error starting camera stream. Please check your connection and try again.",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        } else if (playerState == CameraStreamState.READY_OFF) {
            Text("Camera is Off", modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else if (!isCurrentlyStreaming) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}