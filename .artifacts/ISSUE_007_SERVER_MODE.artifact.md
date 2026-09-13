# ISSUE: Support Starting an OrbitFS Server from Android App

## 📝 Description
Currently, OrbitFS Android is purely a client. This task involves embedding the `orbitfs-core` server logic into the Android application to allow the device to function as a **Satellite** (Node).

## 🎯 Functional Requirements
1.  **Server Lifecycle Control**: Start and stop the server via a dedicated UI (Hub or Settings).
2.  **Root Directory Selection**: Use the Storage Access Framework (SAF) to pick a local folder to share.
3.  **Network Configuration**:
    *   Dynamic port selection (default 9090).
    *   Auth Token configuration (Auto-generated or user-defined).
4.  **mDNS Discovery**: Broadcast the server's presence on the local WiFi network using Android's `NsdManager`.
5.  **Status Monitoring**: Display active connections and data transfer stats for the local node.

## ⚙️ Non-Functional Requirements
1.  **Persistence**: The server must run in an Android **Foreground Service** with a permanent notification to prevent the system from killing the process.
2.  **Performance**: Efficient mapping between SAF `DocumentFile` and the `java.io.File` API expected by the core.
3.  **Security**: Root directory must be strictly enforced. Files outside the user-selected folder must be inaccessible.

## 🔄 Flow & Logic
1.  **Initialization**:
    *   User toggles "Launch Satellite".
    *   App prompts for "Root Folder" access (SAF).
    *   App generates a unique "Pairing Token".
2.  **Service Start**:
    *   Foreground Service starts with notification: "OrbitFS Satellite Active".
    *   OrbitFS Java core initializes with parameters: `Port`, `Token`, `RootPath`.
    *   Server begins listening on all local interfaces.
3.  **Discovery**:
    *   App registers service `_orbitfs._tcp` via mDNS.
4.  **Client Connection**:
    *   External client (e.g., Laptop) sees phone on network.
    *   Client connects and enters pairing token.
    *   Android app notification updates: "1 Client Connected".

## 🎨 Design Considerations
- **Hub UI**: Add a "Satellite" tab or a large toggle button at the top of the Node Hub.
- **Permission Flow**: Detailed explanation dialog for *why* the app needs broad folder access.
- **Notification**: Action buttons for "Stop Server" and "Copy Pairing Link".

## 🚧 Backend Dependencies
- **Core Compatibility**: Verify if the current `.jar` supports standard `java.io` operations on Android-provided File descriptors if needed for performance.
- **Threading**: Ensure the server runs on a dedicated background thread pool within the service.
