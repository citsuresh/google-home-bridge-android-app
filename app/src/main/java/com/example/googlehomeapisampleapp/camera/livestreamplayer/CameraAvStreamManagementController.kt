/* Copyright 2025 Google LLC */

package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.util.Log
import com.google.home.DeviceType
import com.google.home.DeviceTypeFactory
import com.google.home.HomeDevice
import com.google.home.google.CameraAvStreamManagement
import com.google.home.google.GoogleCameraDevice
import com.google.home.google.GoogleDoorbellDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

/**
 * Interface for controlling Audio Recording settings using CameraAvStreamManagement.
 */
interface CameraAvStreamManagementController {
    val isRecordingMicrophoneMuted: Flow<Boolean>
    suspend fun setRecordingMicrophoneMuted(muted: Boolean): Boolean
}

/**
 * Implementation of [CameraAvStreamManagementController] that avoids ClassCastException
 * by using standard SDK device type accessors.
 */
class CameraAvStreamManagementControllerImpl(private val device: HomeDevice) :
    CameraAvStreamManagementController {

    // Identifies if the device is a Camera or a Doorbell to ensure trait availability
    private val supportedType: DeviceTypeFactory<out DeviceType>? =
        listOf(GoogleCameraDevice, GoogleDoorbellDevice).firstOrNull { device.has(it) }

    /**
     * Observe the microphone mute status.
     * Logic: TRUE = Muted, FALSE = Not Muted (Recording).
     */
    override val isRecordingMicrophoneMuted: Flow<Boolean> = if (supportedType != null) {
        device.type(supportedType)
            .transform { typeInstance ->
                // Safely emit the trait flow from the device type instance
                typeInstance?.trait(CameraAvStreamManagement)?.let { emit(it) }
            }
            .map { trait: CameraAvStreamManagement ->
                trait.recordingMicrophoneMuted ?: false
            }
            .distinctUntilChanged()
    } else {
        flowOf(false)
    }

    /**
     * Updates the microphone mute status.
     * @param muted TRUE to mute the mic, FALSE to enable recording.
     * @return true if the command was sent successfully.
     */
    override suspend fun setRecordingMicrophoneMuted(muted: Boolean): Boolean {
        Log.d(TAG, "Attempting to set recordingMicrophoneMuted to: $muted")

        return try {
            val typeInstance = device.types().first().firstOrNull {
                it is GoogleCameraDevice || it is GoogleDoorbellDevice
            }

            val trait = typeInstance?.trait(CameraAvStreamManagement)

            if (trait == null) {
                Log.w(TAG, "CameraAvStreamManagement trait not found on this device instance.")
                return false
            }

            // Execute the update within the trait's update block (SDK standard)
            trait.update {
                setRecordingMicrophoneMuted(muted)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update microphone mute state.", e)
            false
        }
    }

    companion object {
        private const val TAG = "CameraAvStreamCtrl"
    }
}