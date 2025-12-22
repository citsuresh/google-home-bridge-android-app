package com.example.googlehomeapisampleapp.repository

import com.example.googlehomeapisampleapp.viewmodel.automations.DraftViewModel
import com.example.googlehomeapisampleapp.viewmodel.devices.DeviceViewModel
import com.google.home.automation.DraftAutomation
import com.google.home.automation.automation
import com.google.home.automation.equals
import com.google.home.matter.standard.*
import com.google.home.matter.standard.OnOff.Companion.onOff
import com.google.home.matter.standard.DoorLock.Companion.lockState
import com.google.home.matter.standard.DoorLockTrait
import com.google.home.google.SimplifiedThermostat
import com.google.home.google.SimplifiedThermostatTrait

class AutomationsRepository {

    fun hasEnoughLights(deviceVMs: List<DeviceViewModel>): Boolean {
        val onOffLights = getOnOffCapableLights(deviceVMs)
        return onOffLights.size >= 2
    }

    /**
     * Check if we have the required devices for the "Speaker and Fan" automation
     */
    fun hasRequiredDevicesForSleepAutomation(deviceVMs: List<DeviceViewModel>): Boolean {
        val hasSpeaker = deviceVMs.any { it.type.value.factory == SpeakerDevice }
        val hasFan = deviceVMs.any { it.type.value.factory == FanDevice }
        val hasPlug = deviceVMs.any { it.type.value.factory == OnOffPluginUnitDevice }
        return hasSpeaker && hasFan && hasPlug
    }

    /**
     * Creates an OnOff light automation draft
     * This should only be called when the user actually selects the predefined automation
     */
    fun createOnOffLightAutomationDraft(deviceVMs: List<DeviceViewModel>): DraftViewModel? {
        val onOffLights = getOnOffCapableLights(deviceVMs)
        if (onOffLights.size < 2) return null

        val deviceA = onOffLights[0]
        val deviceB = onOffLights[1]

        val presetDraft = createOnOffDraftAutomation(deviceA, deviceB)

        // Return a locked DraftViewModel that uses the preset
        return DraftViewModel(
            candidateVM = null,
            presetDraft = presetDraft,
            isLocked = true,
            automationType = DraftViewModel.AutomationType.ON_OFF
        )
    }

    /**
     * Creates a "Speaker and Fan" automation draft
     * Plays ocean wave sounds on speaker, turns on fan and plug when user says "Hey Google, I can't sleep"
     */
    fun createSpeakerAndFanAutomationDraft(
        deviceVMs: List<DeviceViewModel>,
        structure: com.google.home.Structure
    ): DraftViewModel? {
        var speaker: DeviceViewModel? = null
        var fan: DeviceViewModel? = null
        var plug: DeviceViewModel? = null

        for (vm in deviceVMs) {
            when (vm.type.value.factory) {
                SpeakerDevice -> speaker = vm
                FanDevice -> fan = vm
                OnOffPluginUnitDevice -> plug = vm
            }
            if (speaker != null && fan != null && plug != null) break
        }

        // Need at least speaker, fan, and plug
        if (speaker == null || fan == null || plug == null) return null

        // Use the passed structure instead of getting it from device
        val presetDraft = createSpeakerAndFanDraftAutomation(structure, speaker, fan, plug)

        return DraftViewModel(
            candidateVM = null,
            presetDraft = presetDraft,
            isLocked = true,
            automationType = DraftViewModel.AutomationType.SPEAKER_AND_FAN
        )
    }

    private fun createOnOffDraftAutomation(deviceA: DeviceViewModel, deviceB: DeviceViewModel): DraftAutomation {
        return automation {
            name = "On/Off Light Automation"
            description = "Turn off ${deviceB.name.value} when ${deviceA.name.value} turns off"
            isActive = true

            sequential {
                select {
                    sequential {
                        val onOffStarter = starter(deviceA.device, deviceA.type.value.factory, OnOff)
                        condition {
                            expression = onOffStarter.onOff equals false
                        }
                    }
                    manualStarter()
                }

                parallel {
                    action(deviceB.device, deviceB.type.value.factory) {
                        command(OnOff.off())
                    }
                }
            }
        }
    }

    private fun createSpeakerAndFanDraftAutomation(
        structure: com.google.home.Structure,
        speaker: DeviceViewModel,
        fan: DeviceViewModel,
        plug: DeviceViewModel
    ): DraftAutomation {
        return automation {
            name = "Speaker and Fan Automation"
            description = "Play ocean sounds on ${speaker.name.value}, turn on ${fan.name.value} and ${plug.name.value}"
            isActive = true

            sequential {
                // Voice starter: "Hey Google, I can't sleep"
                select {
                    // Use the event-based starter with parameters builder
                    // The query() returns a Parameter that goes in the parameters block
                    starter(
                        structure,
                        com.google.home.google.VoiceStarter.OkGoogleEvent
                    ) {
                        parameter(com.google.home.google.VoiceStarter.OkGoogleEvent.query("I can't sleep"))
                    }
                    manualStarter()
                }

                // Execute actions in parallel
                parallel {
                    // Tell speaker to play ocean wave sounds using Assistant
                    action(speaker.device, speaker.type.value.factory) {
                        command(com.google.home.google.AssistantFulfillment.okGoogle("Play ocean wave sounds"))
                    }

                    // Turn on the fan
                    action(fan.device, fan.type.value.factory) {
                        command(OnOff.on())
                    }

                    // Turn on the plug
                    action(plug.device, plug.type.value.factory) {
                        command(OnOff.on())
                    }
                }
            }
        }
    }

    /**
     * Checks if there are enough devices to create the light and thermostat automation
     * Requires: 1 door lock, 1 thermostat, and at least 1 light
     */
    fun hasLightsAndThermostat(deviceVMs: List<DeviceViewModel>): Boolean {
        var hasDoorLock = false
        var hasThermostat = false
        var hasLights = false

        for (vm in deviceVMs) {
            val factory = vm.type.value.factory
            when (factory) {
                DoorLockDevice -> hasDoorLock = true
                ThermostatDevice -> hasThermostat = true
                OnOffLightDevice, DimmableLightDevice,
                ColorTemperatureLightDevice, ExtendedColorLightDevice -> hasLights = true
            }
            if (hasDoorLock && hasThermostat && hasLights) {
                return true
            }
        }
        return false
    }

    /**
     * Creates a light and thermostat automation draft
     * Turn on lights and set thermostat to Auto mode when door is unlocked
     */
    fun createLightAndThermostatAutomationDraft(deviceVMs: List<DeviceViewModel>): DraftViewModel? {
        var doorLock: DeviceViewModel? = null
        var thermostat: DeviceViewModel? = null
        val lights = mutableListOf<DeviceViewModel>()

        // Single iteration to find all required devices
        for (vm in deviceVMs) {
            val factory = vm.type.value.factory
            when (factory) {
                DoorLockDevice -> if (doorLock == null) doorLock = vm
                ThermostatDevice -> if (thermostat == null) thermostat = vm
                OnOffLightDevice, DimmableLightDevice,
                ColorTemperatureLightDevice, ExtendedColorLightDevice -> lights.add(vm)
            }
        }

        if (doorLock == null || thermostat == null || lights.isEmpty()) {
            return null
        }

        val presetDraft = createLightAndThermostatDraftAutomation(doorLock, thermostat, lights)

        return DraftViewModel(
            candidateVM = null,
            presetDraft = presetDraft,
            isLocked = true,
            automationType = DraftViewModel.AutomationType.LIGHT_AND_THERMOSTAT
        )
    }

    private fun createLightAndThermostatDraftAutomation(
        doorLock: DeviceViewModel,
        thermostat: DeviceViewModel,
        lights: List<DeviceViewModel>
    ): DraftAutomation {
        return automation {
            name = "Turn on lights and thermostat"
            description = "Turn on lights and set thermostat to Auto mode when the door is unlocked."
            isActive = true

            sequential {
                val doorLockStarter = starter(doorLock.device, doorLock.type.value.factory, DoorLock)

                condition {
                    expression = doorLockStarter.lockState equals DoorLockTrait.DlLockState.Unlocked
                }
                parallel {
                    // Turn on each light
                    for (light in lights) {
                        action(light.device, light.type.value.factory) {
                            command(OnOff.on())
                        }
                    }

                    // Set thermostat to auto mode
                    action(thermostat.device, thermostat.type.value.factory) {
                        command(SimplifiedThermostat.setSystemMode(SimplifiedThermostatTrait.SystemModeEnum.Auto))
                    }
                }
            }
        }
    }
    private fun getOnOffCapableLights(deviceVMs: List<DeviceViewModel>): List<DeviceViewModel> {
        return deviceVMs.filter { vm ->
            val factory = vm.type.value.factory
            factory == OnOffLightDevice ||
                    factory == DimmableLightDevice ||
                    factory == ColorTemperatureLightDevice ||
                    factory == ExtendedColorLightDevice
        }
    }
}