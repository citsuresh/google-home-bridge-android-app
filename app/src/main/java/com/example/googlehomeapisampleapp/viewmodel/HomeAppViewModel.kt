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

package com.example.googlehomeapisampleapp.viewmodel

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.BuildConfig
import com.example.googlehomeapisampleapp.FabricType
import com.example.googlehomeapisampleapp.HomeApp
import com.example.googlehomeapisampleapp.HomeModule_ProvideSupportedTraitsFactory
import com.example.googlehomeapisampleapp.MainActivity
import com.example.googlehomeapisampleapp.repository.AutomationsRepository
import com.example.googlehomeapisampleapp.viewmodel.automations.ActionViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.AutomationViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.CandidateViewModel
import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.example.googlehomeapisampleapp.viewmodel.hubs.HubDiscoveryViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.RoomViewModel
import com.example.googlehomeapisampleapp.viewmodel.structures.StructureViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.home.Structure
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.automation.CommandCandidate
import com.google.home.automation.DraftAutomation
import com.google.home.automation.NodeCandidate
import com.google.home.automation.UnknownDeviceType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class HomeAppViewModel (val homeApp: HomeApp) : ViewModel() {

    // Tabs showing main capabilities of the app:
    enum class NavigationTab {
        DEVICES,
        AUTOMATIONS
    }
    companion object {
      const val TAG = "HomeAppViewModel"
    }

    // Container tracking the active navigation tab:
    var selectedTab : MutableStateFlow<NavigationTab> = MutableStateFlow(NavigationTab.DEVICES)

    private val _showQrCodeScanner = MutableStateFlow(false)
    val showQrCodeScanner = _showQrCodeScanner.asStateFlow()

    // Containers tracking the active object being edited:
    var selectedStructureVM: MutableStateFlow<StructureViewModel?> = MutableStateFlow(null)
    var selectedDeviceVM: MutableStateFlow<DeviceViewModel?> = MutableStateFlow(null)
    var selectedAutomationVM: MutableStateFlow<AutomationViewModel?> = MutableStateFlow(null)
    var selectedDraftVM: MutableStateFlow<DraftViewModel?> = MutableStateFlow(null)
    var selectedCandidateVMs: MutableStateFlow<List<CandidateViewModel>?> = MutableStateFlow(null)

    // Container to store returned structures from the app:
    var structureVMs: MutableStateFlow<List<StructureViewModel>> = MutableStateFlow(mutableListOf())

    private var hubDiscoveryVM: HubDiscoveryViewModel? = null
    val hubDiscoveryViewModel: HubDiscoveryViewModel
        get() = hubDiscoveryVM!!

    private val _navigateToProxyActivity = Channel<Unit>(Channel.CONFLATED)
    val navigateToProxyActivity = _navigateToProxyActivity.receiveAsFlow()

    fun signInWithGoogleAccount(context: Context) {
        viewModelScope.launch {
            try {
                Log.d(TAG,"Initiating Google Sign-In flow...")
                // CredentialManager is responsible for interacting with various credential providers on the device
                val credentialManager = CredentialManager.create(context)
                // Your GCP console Web Client ID for Google Sign-In
                val serverClientId = BuildConfig.DEFAULT_WEB_CLIENT_ID
                // Build the request for Google ID token
                val googleIdOption =
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false) // Show all Google accounts on the device
                        .setServerClientId(serverClientId) // embed WebClientID in token
                        .build()
                // Build the GetCredentialRequest
                val request =
                    GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

                // Credential returns when user has selected an account and the getCredential call completes
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                Log.d(TAG,"get credential type: ${credential::class.java.simpleName}")
                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    try {
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        googleCredential.id.let { email ->
                            Log.i(TAG, "Email found in Google ID Token: $email")
                            /*
                             Why "com.google"?
                             The string "com.google" is a standard identifier used in Android's android.accounts.
                             Account system to represent accounts managed by Google. This is often used when
                             interacting with Android's Account Manager or when using Google-specific APIs. So,
                             even if the email ends in "@gmail.com", the underlying account type or provider is
                             still considered "com.google" within the Android system.
                            */
                            val account = Account(email, "com.google")
                            homeApp.homeClientProvider.switchAccount(account.name, serverClientId)
                            Log.d(TAG,"Switched to account to : $account")
                        }
                        Log.i(TAG, "Account switch complete. Emitting navigation event.")
                        // Send an event to the channel to signal the UI to navigate.
                        _navigateToProxyActivity.send(Unit)
                    } catch (e: Exception) {
                        Log.e(TAG,"Could not convert CustomCredential to Google ID Token", e)
                        MainActivity.showError(
                            this,
                            "Could not convert CustomCredential to Google ID Token" + e.message
                        )
                    }
                } else {
                    Log.e(TAG,"Google Sign-In failed: Unexpected result type ${credential::class.java.simpleName}")
                    MainActivity.showError(
                        this,
                        "Google Sign-In failed: Unexpected result type ${credential::class.java.simpleName}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed with unexpected error", e)
                MainActivity.showError(
                    this,
                    "Google Sign-In failed with unexpected error" + e.message
                )
            }
        }
    }

    private val selectedStructureFlow: Flow<Structure> = selectedStructureVM
        .filterNotNull()
        .map { it.structure }
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    init {
        Log.i(TAG, "HomeAppViewModel init")
        val errorsEmitter: MutableSharedFlow<Exception> = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 0
        )
        val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        // HubDiscoveryViewModel now consumes the derived selectedStructureFlow
        hubDiscoveryVM = HubDiscoveryViewModel(
            structureFlow = selectedStructureFlow,
            viewModelScope = viewModelScope,
            errorsEmitter = errorsEmitter,
            ioDispatcher = ioDispatcher
        )

        viewModelScope.launch {
            var structuresJob: Job? = null
            // Resubscribe or cancel subscription when permission is updated
            homeApp.permissionsManager.permissionUpdatedEvent.collect {
                val isSignedIn = homeApp.permissionsManager.isSignedIn.value
                Log.i(TAG, "isSignedIn = $isSignedIn")
                structuresJob?.cancel()
                if (isSignedIn) {
                    Log.d(TAG, "Recreate a job to subscribe to structure")
                    structuresJob = viewModelScope.launch { subscribeToStructures() }
                    Log.d(TAG, "Recreate a job to subscribe to structure ... done")
                } else {
                    Log.d(TAG, "Cancel the job to subscribe to structure")
                }
            }
        }
    }

    private suspend fun subscribeToStructures() {
        // Subscribe to structures returned by the Structures API:
        homeApp.homeClient.structures().collect { structureSet ->
            val structureVMList: MutableList<StructureViewModel> = mutableListOf()
            // Store structures in container ViewModels:
            for (structure in structureSet) {
                structureVMList.add(StructureViewModel(structure))
            }
            // Store the ViewModels:
            structureVMs.emit(structureVMList)

            // If a structure isn't selected yet, select the first structure from the list:
            if (selectedStructureVM.value == null && structureVMList.isNotEmpty())
                selectedStructureVM.emit(structureVMList.first())
        }
    }

    /**
     * Reports an error message to the UI layer via the main logger.
     * This is used when an error occurs outside of the standard flow emission (e.g., in onActivityResult).
     *
     * @param resultCode The result code of the failed activation.
     */
    fun handleActivationFailure(resultCode: Int) {
        val errorMessage = "Hub activation failed with result code: $resultCode"
        MainActivity.showError(this, errorMessage)
    }

    /**
     * Starts the hub discovery process.
     */
    fun startHubDiscovery() {
        hubDiscoveryVM?.startDiscovery()
    }

    fun openQrCodeScanner() {
        _showQrCodeScanner.value = true
    }

    fun closeQrCodeScanner() {
        _showQrCodeScanner.value = false
    }

    /**
     * Starts the commissioning flow with the selected fabric type and optional payload.
     * This function manages the view transitions (scanner -> commissioning client).
     */
    fun onCommissionCamera(payload: String? = null) {

        // 1. OPEN SCANNER: If payload is null (initial click), open the scanner UI.
        if (payload == null) {
            openQrCodeScanner()
            return // Stop execution here, wait for the scanner result
        }

        // 2. START API: If payload exists (result from MatterQrCodeScanner), close the UI and call the API.
        closeQrCodeScanner()
        homeApp.commissioningManager.requestCommissioning(FabricType.GOOGLE_CAMERA, payload)
    }

    /**
     * Shows automation candidates for the selected structure.
     */
    @OptIn(HomeExperimentalApi::class)
    fun showCandidates() {
        viewModelScope.launch {
            val candidateVMList: MutableList<CandidateViewModel> = mutableListOf()

            // Retrieve automation candidates for every device present in the selected structure:
            for (deviceVM in selectedStructureVM.value!!.deviceVMs.value) {

                // Check whether the device has a known type:
                if(deviceVM.type.value is UnknownDeviceType)
                    continue
                // Retrieve a set of initial automation candidates from the device:
                val candidates: Set<NodeCandidate> = deviceVM.device.candidates().first()

                for (candidate in candidates) {
                    // Check whether the candidate trait is supported:
                    if(candidate.trait !in HomeModule_ProvideSupportedTraitsFactory().get())
                        continue
                    // Check whether the candidate type is supported:
                    when (candidate) {
                        // Command candidate type:
                        is CommandCandidate -> {
                            // Check whether the command candidate has a supported command:
                            if (candidate.commandDescriptor !in ActionViewModel.commandMap)
                                continue
                        }
                        // Other candidate types are currently unsupported:
                        else -> { continue }
                    }
                    candidateVMList.add(CandidateViewModel(candidate, deviceVM))
                }
            }

            // Store the ViewModels:
            selectedCandidateVMs.emit(candidateVMList)
        }
    }

    /**
     * Creates an automation from the currently selected draft.
     *
     * @param isPending A [MutableState] to track if the automation creation is in progress.
     */
    fun createAutomation(isPending: MutableState<Boolean>) {
        viewModelScope.launch {
            val structure : Structure = selectedStructureVM.value?.structure!!
            val draft : DraftAutomation = selectedDraftVM.value?.getDraftAutomation()!!
            isPending.value = true

            // Call Automations API to create an automation from a draft:
            try {
                structure.createAutomation(draft)
            }
            catch (e: Exception) {
                MainActivity.showError(this, e.toString())
                isPending.value = false
                return@launch
            }

            // Scrap the draft and automation candidates used in the process:
            selectedCandidateVMs.emit(null)
            selectedDraftVM.emit(null)
            isPending.value = false
        }
    }

    /** Create a room on the currently selected structure. */
    fun createRoomInSelectedStructure(name: String): Job = viewModelScope.launch {
        val vm = selectedStructureVM.value ?: return@launch
        vm.createRoom(name)
    }

    /** Delete a room from the currently selected structure. */
    fun deleteRoomFromSelectedStructure(roomVM: RoomViewModel): Job = viewModelScope.launch {
        val structureVM = selectedStructureVM.value ?: return@launch
        structureVM.deleteRoom(roomVM)
    }

    /**
     * Move a device into the given (non-null) room for the selected structure.
     *
     * @param device The [DeviceViewModel] of the device to move.
     * @param room The [RoomViewModel] of the room to move the device to.
     */
    fun moveDeviceToRoom(device: DeviceViewModel, room: RoomViewModel): Job = viewModelScope.launch {
        val vm = selectedStructureVM.value ?: return@launch
        vm.moveDeviceToRoom(device, room)
    }
    /**
     * Creates and shows a predefined draft for an On/Off light automation.
     *
     * This draft requires at least two OnOff-capable lights in the selected structure.
     * If fewer than two are available, an error message is shown instead.
     */
    fun showPredefinedOnOffDraft() {
        viewModelScope.launch {
            val structureVM = selectedStructureVM.value ?: return@launch
            val repository = AutomationsRepository()

            val draftVM = repository.createOnOffLightAutomationDraft(structureVM.deviceVMs.value)

            if (draftVM == null) {
                MainActivity.showError(this, "Need at least two OnOff-capable lights in this structure.")
                return@launch
            }

            selectedDraftVM.emit(draftVM)
        }
    }

    /**
     * Creates and shows a predefined draft for the "Speaker and Fan" automation.
     *
     * This draft requires a speaker, fan, and plug in the selected structure.
     * If any required device is missing, an error message is shown instead.
     */
    fun showPredefinedSpeakerAndFanDraft() {
        viewModelScope.launch {
            val structureVM = selectedStructureVM.value ?: return@launch
            val repository = AutomationsRepository()

            // Pass the structure from selectedStructureVM
            val draftVM = repository.createSpeakerAndFanAutomationDraft(
                structureVM.deviceVMs.value,
                structureVM.structure
            )

            if (draftVM == null) {
                MainActivity.showError(this, "This automation requires:\n• 1 Speaker\n• 1 Fan\n• 1 Smart Outlet\n\nPlease add these devices and try again.")
                return@launch
            }

            selectedDraftVM.emit(draftVM)
        }
    }

    /**
     * Shows the predefined light and thermostat automation draft
     * This creates a draft that turns on lights and sets thermostat to auto when door is unlocked
     */
    suspend fun showPredefinedLightAndThermostatDraft() {
        val structureVM = selectedStructureVM.value ?: return
        val repository = AutomationsRepository()

        val draftVM = repository.createLightAndThermostatAutomationDraft(structureVM.deviceVMs.value)
        if (draftVM != null) {
            selectedDraftVM.emit(draftVM)
        }
    }
}