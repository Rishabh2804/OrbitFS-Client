# ISSUE: Build OrbitFS Desktop (Mac, Windows, Linux)

## 📝 Description
Create a cross-platform desktop application using Electron or Tauri. This app will mirror the Android app's functionality: acting as both a client (to browse other nodes) and a satellite (to share local desktop folders). It must use the same `orbitfs-core.jar` as the Android app to ensure protocol compatibility.

## 🎯 Functional Requirements
1.  **Universal UI**: A responsive web-based interface that mirrors the "Deep Space" theme.
2.  **Node Hub**: Manage saved connections and view real-time node health.
3.  **Local Satellite**:
    *   Select any desktop folder to share as an OrbitFS root.
    *   Launch/Stop the server instance (bridge to the JAR).
4.  **Radar**: Integrated mDNS discovery to see Android phones and other laptops instantly.
5.  **File Explorer**: Browse remote node files with list/grid views and basic file operations.

## ⚙️ Non-Functional Requirements
1.  **Shared Engine**: The desktop app must wrap the `orbitfs-core.jar`.
2.  **Platform Independence**: One codebase for Mac (Intel/Apple Silicon), Windows, and Linux (AppImage/Snap).
3.  **Resource Efficiency**: The background JAR process should be throttled when the UI is minimized.

## 🏗️ Technical Architecture (Electron + Java Bridge)
1.  **The Wrapper**: Electron (Node.js) manages the window and system tray.
2.  **The Bridge**: Electron spawns the `orbitfs-core.jar` as a child process.
3.  **Communication**: Electron talks to the JAR via a local TCP/Unix socket or a small local REST bridge provided by the JAR's CLI.
4.  **Networking**: Use `node-dns-sd` or similar for the Radar (mDNS) to ensure consistency with Android's NSD.

## 🔄 Maintenance Plan
- **Protocol Updates**: Any change to the OrbitFS TCP protocol must be implemented in the Core JAR. Both Android and Desktop apps will receive the update by simply replacing the JAR file.
- **Milestone 4 (Chat)**: The chat state machine will live in the JAR. Desktop will only need to implement the chat bubble UI components.
