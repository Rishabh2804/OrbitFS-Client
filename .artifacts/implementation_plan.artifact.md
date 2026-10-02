# Implementation Plan - Milestone 3: Android Satellite (Server Mode)

This plan outlines the steps to enable OrbitFS server functionality directly on the Android device, allowing it to act as a host for other peers.

## User Review Required

> [!IMPORTANT]
> To run a reliable server, OrbitFS will require **Foreground Service** permissions and **Scoped Storage** (SAF) access to a directory you choose to share.
> A persistent notification will be displayed whenever the Satellite is active to ensure the system doesn't kill the server process.

## Proposed Changes

### 1. Data Layer Enhancements
- **[MODIFY] [SettingsRepository.kt](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/java/org/orbitfs/android/data/SettingsRepository.kt)**: Add persistence for Satellite settings (port, last shared root URI, active state).
- **[NEW] [SatelliteState.kt](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/java/org/orbitfs/android/model/SatelliteState.kt)**: Data class to track the local server's status (Running/Stopped, Clients Connected, Active Root).

### 2. Service Layer (The Core)
- **[NEW] [OrbitFSServerService.kt](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/java/org/orbitfs/android/service/OrbitFSServerService.kt)**: A Foreground Service that:
    - Instantiates and manages the lifecycle of `org.orbitfs.server.OrbitServerImpl`.
    - Broadcasts the server via `NsdManager` (mDNS).
    - Maintains an ongoing notification with "Stop" and "Configure" actions.
- **[MODIFY] [AndroidManifest.xml](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/AndroidManifest.xml)**: Register the new service and add `FOREGROUND_SERVICE` permissions.

### 3. UI Layer (Satellite Control Center)
- **[NEW] [SatelliteScreen.kt](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/java/org/orbitfs/android/ui/screens/SatelliteScreen.kt)**: A new screen accessible from the Node Hub to:
    - Toggle the Satellite state.
    - Select the root folder via Storage Access Framework.
    - Display the device's local IP and pairing token.
- **[MODIFY] [NodeHubScreen.kt](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/java/org/orbitfs/android/ui/screens/NodeHubScreen.kt)**: Add a "Launch Satellite" entry point/card.
- **[MODIFY] [OrbitFSClientApp.kt](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/java/org/orbitfs/android/ui/OrbitFSClientApp.kt)**: Wire navigation and handle SAF result callbacks.

### 4. Integration & Networking
- **[NEW] [NsdHelper.kt](file:///Users/rishabh/Projects/OrbitFS-Android/app/src/main/java/org/orbitfs/android/client/NsdHelper.kt)**: Utility class for service discovery and registration.

---

## Verification Plan

### Automated Tests
- Unit tests for `SatelliteState` transitions.
- Mock tests for `SettingsRepository` persistence of SAF URIs.

### Manual Verification
1.  **Launch Flow**: Open Node Hub -> Click "Launch Satellite" -> Pick Folder -> Confirm Notification appears.
2.  **Connectivity**: On a laptop on the same WiFi, attempt to connect to the Android IP:Port.
3.  **Persistence**: Move app to background, browse files from laptop, ensure no disconnects.
4.  **Discovery**: Ensure the Android phone appears in the "Available Nodes" list of another device running OrbitFS.
