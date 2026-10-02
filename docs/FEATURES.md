# OrbitFS Client — Features & UI Capabilities

> *"Your Data, in Local Orbit."*

OrbitFS Client is a privacy-first, local-only, high-bandwidth peer-to-peer file sharing application built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)** for **Android** and **Desktop (macOS, Windows, Linux)**.

---

## 📸 Feature Showcase & Interface Tour

### 1. Node Hub & Connection Management
The primary landing screen displaying saved servers, real-time connection status pills, active pilot profile, and quick connection cards.

<p align="left">
  <img src="screenshots/01_node_hub.png" width="280" alt="Node Hub Screen"/>
</p>

- **Saved Connection Cards**: Save remote servers with custom socket timeouts and chunk buffer sizes.
- **Active Pilot Profile**: Displays the local node's space-themed Pilot Name and Avatar.

---

### 2. Zero-Config UDP Radar Discovery
Discover nearby OrbitFS satellites instantly without manually typing IP addresses.

<p align="left">
  <img src="screenshots/02_radar_discovery.png" width="280" alt="Orbit Radar Discovery Screen"/>
</p>

- **UDP Broadcast Beaconing**: Broadcasts periodic discovery packets over UDP Port 9999.
- **Hardware MulticastLock**: Android `WifiManager.MulticastLock` integration ensures reliable packet reception during low-power Wi-Fi states.
- **Dead-Man Timer Stale Pruning**: Automatically purges offline nodes after 5 seconds of inactivity.
- **Smart Deduplication**: Replaces stale profiles on identical IP/ports when identity resets occur.

---

### 3. Satellite Server Mode
Turn any device into an active file server directly from a phone or laptop.

<p align="left">
  <img src="screenshots/03_satellite_mode.png" width="280" alt="Satellite Mode Screen"/>
</p>

- **On-Demand Server Launch**: Launch a local file server with custom port selection and directory picker integration.
- **Android Foreground Service**: Runs inside `OrbitFSServerService` with permanent notification controls to prevent OS process termination.
- **Single-Port Constraint**: Stops existing satellite instances before launching new ones to prevent port conflicts.

---

### 4. File Explorer Screen
Browse remote directories with line-rate TCP socket streaming.

<p align="left">
  <img src="screenshots/05_file_explorer.png" width="280" alt="File Explorer Screen"/>
</p>

- **Breadcrumb Navigation**: LazyRow breadcrumb bar for instant parent folder navigation.
- **Per-Card Loading Indicator**: Displays a `CircularProgressIndicator` on file cards while streaming remote items.
- **Multi-Select & Sorting**: Multi-select files for batch deletion or sort items by name, size, and date modified.

---

### 5. Advanced Transfer Engine & History
Track active and historical file transfers with live bandwidth telemetry.

- **Dynamic Speed Calculations**: Time-delta sampling computes live transfer speeds (`KB/s`, `MB/s`).
- **Notification Controls & Cancel Action**: Syncs progress bar live to Android notifications with a one-tap **Cancel** action button.
- **Show in Folder / Local Navigation**: Opens/reveals downloaded files in macOS Finder (`open -R`) or Android File Manager.
- **Swipe Gesture Navigation**: Gesture-based `HorizontalPager` allowing smooth left/right swiping between **Active** and **History** tabs.

---

### 6. App Preferences & Identity Customization
Customize themes, hardware permissions, and pilot profiles.

<p align="left">
  <img src="screenshots/04_settings.png" width="280" alt="Settings Screen"/>
</p>

- **Theme Selection**: Toggle between **Dark Space**, **Light Orbit**, or **System Default**.
- **Randomized Identity Generator**: Auto-generates unique Node IDs, space-themed Pilot Names, and Pilot Avatars on setup and reset.
