# OrbitFS Android - Milestone 3 & 4 Roadmap

This document outlines the strategic evolution of OrbitFS from a dedicated client into a peer-to-peer sharing and social file management ecosystem.

## 🎯 Milestone 3: The Android Satellite (Server Mode)
*Turning your device into a host.*

### High-Level Goal
Enable the Android app to act as an OrbitFS Satellite. This allows laptops and other phones to connect to the Android device to browse and download its local files.

### Key Components
1.  **Foreground Service**: A persistent service to keep the OrbitFS Java core running even when the app is in the background.
2.  **Scoped Storage Bridge**: Mapping the OrbitFS "Root" to an Android directory using the Storage Access Framework (SAF).
3.  **Local Discovery**: Implementation of mDNS (Network Service Discovery) so other clients see the Android phone automatically as "Local Phone [Name]".

---

## 🚀 Milestone 4: Peer Orbit (Social & P2P Sharing)
*Ad-hoc sharing through chat and dynamic provisioning.*

### High-Level Goal
Implement a chat-based interface where peers can discover each other, chat, and request/grant access to specific files via ephemeral server instances.

### Key Components
1.  **Peer Discovery**: Local network and proximity-based (Bluetooth/WiFi-Direct) discovery.
2.  **Ephemeral Servers**: Spawning a new OrbitFS server instance per sharing session.
3.  **The "Vault" Logic**: A secure, isolated directory structure for staged sharing to prevent exposing the real filesystem.
4.  **Sandbox Integration**: (Planned) A security layer to execute/preview shared files in isolation.

---

## 🛠️ Detailed Issue Trackers

- [**ISSUE_007: Android Satellite Mode**](file:///Users/rishabh/Projects/OrbitFS-Android/.artifacts/ISSUE_007_SERVER_MODE.artifact.md)
- [**ISSUE_008: Peer Chat & Ad-hoc Sharing**](file:///Users/rishabh/Projects/OrbitFS-Android/.artifacts/ISSUE_008_P2P_SOCIAL.artifact.md)
