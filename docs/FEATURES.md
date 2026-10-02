# OrbitFS Client — Comprehensive Feature Specification

> *"Your Data, in Local Orbit."*

OrbitFS Client is a privacy-first, local-only, high-bandwidth peer-to-peer file sharing application engineered for **Android** and **Desktop (macOS, Windows, Linux)** using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)**.

---

## 🌟 Major Feature Pillars

### 1. File Explorer Engine
Browse and stream remote files over raw TCP sockets with near-local filesystem speed.

<p align="left">
  <img src="screenshots/05_file_explorer.png" width="280" alt="File Explorer Screen"/>
</p>

- **Remote Directory Navigation**: Browse nested directory hierarchies on remote satellite nodes over persistent TCP sockets.
- **Breadcrumb Bar Navigation**: Interactive `LazyRow` breadcrumb bar for instant multi-level parent directory jumps.
- **Per-Item Loading Indicators**: Displays a `CircularProgressIndicator` on file rows while items are streaming from the server.
- **Multi-Select & Bulk Operations**: Toggle selection mode to batch select items for bulk deletion or file information inspection.
- **Multi-Criteria Sorting**: Sort directory listings by Name, Date Modified, or File Size in Ascending or Descending order with folders grouped first.
- **Hidden Files Toggle**: Client-side filtering toggle for hidden dotfiles (`.git`, `.DS_Store`).

---

### 2. Active & Historical Transfer Manager
Track active file downloads and manage transfer history with live telemetry.

<p align="left">
  <img src="screenshots/01_node_hub.png" width="280" alt="Node Hub Screen"/>
</p>

- **Real-Time Bandwidth Speed Telemetry**: Time-delta sampling calculates live transfer speeds (`KB/s`, `MB/s`).
- **Notification Progress Sync & Cancel Action**: Syncs progress live to system notifications on Android with a one-tap **Cancel / Stop Download** button.
- **Show in Folder / Local Navigation**: One-tap "Show in Folder" reveals/highlights downloaded files in macOS Finder (`open -R`) or launches the native Android File Manager.
- **Completed File Opening**: Open completed downloads directly in default OS applications.
- **Retry & Partial Download Preservation**: Retry failed or cancelled downloads; retains exact `bytesDownloaded` on network interrupts instead of resetting to 0B.
- **Gesture-Based Swipe Navigation**: `HorizontalPager` swiping between **Active** transfers and **Transfer History** tabs.

---

### 3. Orbit Radar (Zero-Config Peer Discovery)
Discover nearby active OrbitFS satellite nodes automatically over local Wi-Fi.

<p align="left">
  <img src="screenshots/02_radar_discovery.png" width="280" alt="Orbit Radar Discovery Screen"/>
</p>

- **UDP Broadcast Beaconing**: Broadcasts periodic discovery packets (`ORBITFS_BEACON|Name|Port|Avatar|NodeId`) over UDP Port 9999.
- **Hardware MulticastLock**: Android `WifiManager.MulticastLock` integration guarantees reliable packet reception in low-power Wi-Fi states.
- **Dead-Man Timer Stale Pruning**: Background pruner task automatically purges offline nodes after 5 seconds of inactivity.
- **Smart Deduplication**: Automatically filters out stale profiles on identical IP/ports when a node renames or resets identity.

---

### 4. Satellite Mode (Local Server Launcher)
Turn any Android phone or Desktop laptop into a local OrbitFS file server.

<p align="left">
  <img src="screenshots/03_satellite_mode.png" width="280" alt="Satellite Mode Screen"/>
</p>

- **On-Demand Local Server**: Share local folders with custom port selection and root folder chooser integration.
- **Android Foreground Service**: Runs inside `OrbitFSServerService` with a permanent notification control to prevent OS process killing.
- **Single-Port Constraint & Pre-Launch Cleanup**: Restricts execution to 1 active server per device, automatically stopping existing instances before launching a new satellite.

---

### 5. Profile & Identity Management
Manage space-themed pilot profiles and node credentials.

<p align="left">
  <img src="screenshots/04_settings.png" width="280" alt="Settings Screen"/>
</p>

- **Randomized Identity Generator**: Auto-generates unique 12-character Node IDs, space-themed Pilot Names (`Vortex-Voyager-7764`), and Pilot Avatars on first setup and profile reset.
- **Seamless Profile Resets**: One-tap **Reset Pilot Identity** gracefully stops running satellite servers, updates credentials, and re-launches satellite with the new identity.

---

### 6. Custom RPC Protocol & Runtime Interop
The low-level networking and security engine driving cross-platform communication.

- **Length-Prefixed TCP Framing**: 4-byte big-endian length header followed by minified JSON payload frames (`READ`, `WRITE`, `STAT`, `LIST`, `OPEN`, `CLOSE`).
- **Ghost Launcher Runtime Interop**: Reflection desugaring patches (`sun.misc.Unsafe`) allowing Java 21 server instances to run inside Android ART.
- **Sandbox Path Canonicalization**: `SandboxGuard` path canonicalization preventing directory traversal security vulnerabilities (`../`).
