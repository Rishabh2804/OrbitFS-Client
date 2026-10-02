# OrbitFS — Cross-Platform P2P File System & Local Orbit Network

> *"Your Data, in Local Orbit."*

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)](#building--running)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

OrbitFS is a privacy-first, local-only, high-bandwidth peer-to-peer file sharing and storage infrastructure engineered for **Android** and **Desktop (macOS, Windows, Linux)**. Built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)**, OrbitFS operates completely independent of cloud servers, third-party proxies, and internet routing—delivering line-rate file streaming and sub-millisecond latencies across local area networks (LAN/WLAN).

---

## 🌟 Key Technical Highlights

- **Custom Binary-Framed RPC Protocol**: Length-prefixed 4-byte big-endian JSON framing protocol operating over raw TCP sockets (`READ`, `WRITE`, `STAT`, `LIST`), optimized with dynamic 64KB–256KB chunk buffering.
- **Dual-Role Satellite Node Architecture**: Every instance functions both as an active file explorer client and as a background satellite file server.
- **Zero-Config UDP Radar Discovery**: High-performance UDP broadcast beaconing on Port 9999 with Android `MulticastLock` integration and dead-man timer stale-peer pruning for instant peer discovery.
- **Ghost Launcher Runtime Interop**: Bypasses Android ART runtime incompatibilities with Java 21 Virtual Threads using `sun.misc.Unsafe` reflection patches to execute server instances natively inside Android.
- **Strict Sandbox Path Canonicalization**: Enforces strict `SandboxGuard` path canonicalization to eliminate directory traversal vulnerability vectors (`../`).
- **Complete Transfer Engine**:
  - Live bandwidth speed calculations (`KB/s`, `MB/s`) using time-delta sampling.
  - Notification progress tracking with instant **Cancel / Stop Download** action buttons.
  - Native **Show in Folder** / **Navigate to Location** support across Android SAF and macOS Finder (`open -R`).
  - Gesture-based `HorizontalPager` tab swiping for active and historical transfers.

---

## 🏗️ Tech Stack & Architecture

```
                       ┌────────────────────────────────────────┐
                       │     Shared Presentation & Logic        │
                       │             (commonMain)               │
                       │  - FileBrowserViewModel & StateFlows   │
                       │  - OrbitFSClientWrapper (RPC)          │
                       │  - Shared Compose Material 3 UI        │
                       └───────────────────┬────────────────────┘
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    │                                             │
                    ▼                                             ▼
  ┌───────────────────────────────────┐         ┌───────────────────────────────────┐
  │       Android Implementation      │         │      Desktop Implementation       │
  │            (androidMain)          │         │           (desktopMain)           │
  │ - OrbitFSServerService (Foreground)│        │ - Desktop JVM Launcher (main.kt)  │
  │ - Storage Access Framework (SAF)  │         │ - ProcessBuilder / macOS Finder   │
  │ - Android Notification Controls   │         │ - Swing JFileChooser Dialogs      │
  │ - WifiManager MulticastLock       │         │ - Standalone Desktop JAR Engine   │
  └───────────────────────────────────┘         └───────────────────────────────────┘
```

| Layer | Technologies & Frameworks |
|---|---|
| **Core Languages** | Kotlin, Java 21 |
| **Multiplatform UI** | Compose Multiplatform, Jetpack Compose, Material 3 Design |
| **Concurrency & Async** | Kotlin Coroutines, Flow, StateFlow, Channels |
| **Networking & Protocols** | Custom RPC over TCP Sockets, UDP Broadcast (Port 9999), DatagramSocket |
| **Runtime Interop** | `sun.misc.Unsafe` reflection patches, Java NIO, Storage Access Framework (SAF) |
| **Build & CI** | Gradle Kotlin DSL, GitHub Actions |

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21** or higher
- **Android SDK** (API Level 26–35)
- **Gradle 8.x**

---

### Building & Running

#### 1. Android Debug Build
```bash
./gradlew :composeApp:assembleDebug
```
*Output APK:* `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

#### 2. Run Android App on Connected Device / Emulator
```bash
./gradlew :composeApp:installDebug
```

#### 3. Run Desktop App (macOS, Windows, Linux)
```bash
./gradlew :composeApp:run
```

#### 4. Package Desktop Distribution (JAR / DMG / DEB)
```bash
./gradlew :composeApp:desktopJar
```

---

## 📁 Repository Structure

```
OrbitFS-Android/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/      # Shared ViewModels, UI Screens, RPC Client Wrapper, Models
│   │   ├── androidMain/     # Android Foreground Service, SAF Repository, Notifications, MainActivity
│   │   └── desktopMain/     # Desktop JVM Launcher, AWT/ProcessBuilder Handlers, Desktop Repository
│   └── libs/
│       └── orbitfs-core-0.1.0.jar   # Core Pure-Java RPC & Transport Library
├── AGENT.md                 # Technical flight recorder and architectural journal
├── DESIGN.md                # System design specification
└── README.md                # Project documentation
```

---

## 📄 License
OrbitFS is distributed under the [Apache License 2.0](LICENSE).
