package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.util.Log
import com.google.home.HomeDevice
import com.google.home.google.CameraAvStreamManagement
import javax.inject.Inject

/** Factory for creating [CameraAvStreamManagementController] instances. */
class CameraAvStreamManagementControllerFactory @Inject internal constructor() {

    /**
     * Creates a [CameraAvStreamManagementController] from a [HomeDevice].
     *
     * @param device The device to create the controller for.
     * @return The created [CameraAvStreamManagementController], or null if the device does not support the required trait.
     */
    suspend fun create(device: HomeDevice): CameraAvStreamManagementController? {
        if (device.has(CameraAvStreamManagement.Companion)) {
            return CameraAvStreamManagementControllerImpl(device)
        }

        Log.w(
            TAG,
            "CameraAvStreamManagementTrait not found on device ${device.id}, cannot create controller.",
        )
        return null
    }

    companion object {
        private const val TAG = "CameraAvStreamManagementControllerFactory"
    }
}