# Google Home API Android Service Application

Forked from [google-home-api-sample-app-android](https://github.com/google-home/google-home-api-sample-app-android)

## Overview

The primary objective of this project is to create a persistent, background-capable bridge service on an Android device. This service connects to the Google Home API to discover and control local smart home devices (plugs, lights, etc.) and exposes this functionality over the local network via a WebSocket server. This allows other non-Android clients, scripts, or home automation systems on the same network to monitor and control Google Home devices through a simple, standardized JSON-based protocol.

To achieve this, the application was enhanced to run a `Foreground Service` that remains active even when the app's UI is closed or the device screen is off. Key features include automatic service start-on-boot and a user-friendly UI for managing the service's state.

---

### 1. Core Service Implementation

-   **Background Service**: Added `GhBridgeService`, a long-running foreground service that hosts a Ktor WebSocket server for client communication and uses mDNS for network discovery.
-   **Data Models**: Created a separate `BridgeModels.kt` file for clean code organization, defining the data structures used for WebSocket communication.
-   **Dependencies**: Added necessary libraries to `app/build.gradle.kts` to support this functionality, including Ktor, Timber, and Gson.

### 2. UI & User Experience

-   **Service Status Indicator**: Implemented a dynamic service status indicator in the top bar of both the `DevicesView` and `AutomationsView`.
-   **Visual State Feedback**: The indicator consists of a cloud icon and a text label. The color (Green for Running, Red for Stopped) and text change dynamically to reflect the current state of the background service. The entire area is clickable, providing a large, user-friendly touch target.
-   **Accessibility**: A dynamic `contentDescription` was added to the icon to act as a tooltip, explaining the icon's purpose and current state (e.g., "Service is running. Click to stop.").

### 3. Background Execution & Stability

-   **Auto-Start on Boot**: Implemented a `BootReceiver` and added the `RECEIVE_BOOT_COMPLETED` permission to automatically start the `GhBridgeService` when the device boots up. This ensures the service is available without needing to manually open the app.
-   **Battery Optimization Prompt**: To ensure reliable background execution on modern Android devices, the app now checks if it has been exempted from battery optimizations. If not, it displays a helpful dialog that provides a one-click shortcut to the correct system settings page, prompting the user to set the app to "Unrestricted."
-   **Correct Service Lifecycle**: The interaction between `MainActivity` and `GhBridgeService` now uses the standard Android `bindService` and `unbindService` pattern. This ensures the UI can reliably communicate with and control the service, fixing a critical bug where the service could not be stopped correctly.
-   **Modern Android Compatibility**: Addressed runtime crashes on Android 14+ by declaring the correct `foregroundServiceType="dataSync"` and adding the `FOREGROUND_SERVICE_DATA_SYNC` permission in `AndroidManifest.xml`.

### 4. Code Quality & Maintainability

-   **Refactored for Clarity**: The UI logic for the new service indicator was refactored into a clean, reusable `ServiceStatusIndicator` composable in `DevicesView.kt` and `AutomationsView.kt`. Similarly, the battery optimization dialog in `MainActivity.kt` was grouped into its own composable.
-   **Improved Code Organization**: New logic in `MainActivity`, `DevicesView`, and `AutomationsView` has been grouped into collapsible `<editor-fold>` regions, making the changes easy to find and manage during future code maintenance.
-   **Version Control Cleanup**: A standard `.gitignore` file was added to the project root to prevent local IDE configuration files from being committed. Unnecessary backup rule XML files and their manifest references were removed to simplify the project structure.


Forked from [google-home-api-sample-app-android](https://github.com/google-home/google-home-api-sample-app-android)

Google Home API - Android Sample App
====================================

Google Home APIs allow developers to integrate with Google Home Ecosystem through mobile applications.

For more info: https://developers.home.google.com/apis

# Features

- Permission API: Authenticate your application seamlessly through Authentication API, which provides a set of standardized screens and functions to request access to structures and devices.

- Device API: Retrieve states of smart home devices on a structure, modify attributes, and issue commands.

- Structure API: Retrieve the representational graph for a structure, with rooms and assigned devices.

- Commissioning API: Add new matter devices to Google Home Ecosystem.

- Automation API: Create and schedule household routines that trigger device commands based on defined triggers and conditions.

- Discovery API: Retrieve a list of automations that can be created on a structure given the set of devices.

# Google Home API Knowledge Base

This project includes `tools/google-home-api-knowledge-base.txt`, a "source of truth" file with detailed API specs and code examples. Providing this file as context to an LLM (like Gemini) allows it to act as a "cheat sheet" and answer your specific Google Home API questions with greater precision. This is highly recommended for use with **Gemini in Android Studio** but also works with other LLMs (like the Gemini web UI).
