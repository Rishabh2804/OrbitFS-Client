# OrbitFS Client — Feature Specification & Capabilities

> *"Your Data, in Local Orbit."*

OrbitFS Client is a privacy-first, local-only, high-bandwidth peer-to-peer file sharing application built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)** for **Android** and **Desktop (macOS, Windows, Linux)**.

---

## 📋 Comprehensive Feature Overview

### 1. Dual-Role Satellite Node Architecture
- **Client & Server Integration**: Every device running OrbitFS functions both as a file browser client and as a satellite file server.
- **On-Demand Satellite Launch**: Spin up a local file server directly from a phone or laptop with custom port binding and root directory selection.
- **Foreground Service Persistence**: On Android, the satellite server executes inside an Android Foreground Service (`OrbitFSServerService`) with permanent notification controls to prevent OS process killing.

### 2. Zero-Config UDP Radar Discovery
- **UDP Broadcast Beaconing**: Broadcasts periodic discovery packets (`ORBITFS_BEACON|Name|Port|Avatar|NodeId`) over UDP Port 9999.
- **MulticastLock Support**: Android `WifiManager.MulticastLock` integration guarantees reliable hardware packet reception even during low-power Wi-Fi modes.
- **Dead-Man Timer Stale Pruning**: Background pruner task automatically purges offline peers after 5 seconds of inactivity.
- **Smart Deduplication**: Automatically filters out stale profiles on identical IP/ports when identity or profile resets occur.

### 3. Custom Binary-Framed RPC Protocol
- **Length-Prefixed TCP Framing**: Messages are prefixed with a 4-byte big-endian length header followed by minified JSON payload frames (`READ`, `WRITE`, `STAT`, `LIST`, `OPEN`, `CLOSE`).
- **Dynamic Chunk Buffering**: Configurable chunk transfer sizes (64KB to 256KB) for optimal TCP socket throughput and low latency.
- **Resilient Retry State Machine**: Automatic reconnect and state machine retry for transient network glitches (`OrbitFSClientWrapper.withRetry`).

### 4. Advanced Transfer Manager
- **Live Bandwidth Speed Tracking**: Time-delta sampling calculates real-time download speeds (`KB/s`, `MB/s`).
- **Notification Controls & Cancellation**:
  - Live system notification progress bar syncing on Android (`"14.5 MB / 250 MB"`).
  - Instant **Cancel / Stop Download** action button in notification shade and in-app transfer cards.
- **Swipe Gesture Tab Navigation**: Gesture-based `HorizontalPager` allowing smooth left/right swiping between **Active** and **History** tabs.
- **Show in Folder / Local Navigation**:
  - One-tap "Show in Folder" reveals/highlights the file in macOS Finder (`open -R`) or launches the native Android File Manager.
  - One-tap "Copy Path" button for local destination URIs.
- **Preserve Partial Downloads**: Partial downloads retain exact downloaded byte counts on network failure instead of resetting to 0B.

### 5. Ghost Launcher Runtime Interop & Path Security
- **Java 21 / ART Desugaring Patch**: Bypasses Android ART runtime incompatibilities with Java 21 Virtual Threads using `sun.misc.Unsafe` reflection patches (`SatelliteServerLauncher`).
- **Sandbox Guard Path Canonicalization**: Strict `SandboxGuard` path resolution prevents directory traversal attacks (`../`) outside designated share directories.
- **50MB Safety Warning Prompt**: Warns users before streaming large files directly into memory, prompting them to save to local disk instead.

### 6. Identity & Theme Customization
- **Randomized Identity Generation**: Auto-generates unique 12-character Node IDs, fun space-themed Pilot Names (`Solar-Falcon-4821`), and Pilot Avatars on first setup and on identity reset.
- **Theme Modes**: Supports Light Orbit, Dark Space, and System Default themes across Android and Desktop.
