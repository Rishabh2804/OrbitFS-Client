# OrbitFS Client — Cross-Platform P2P File System & Local Orbit Network

> *"Your Data, in Local Orbit."*

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)](#-installation--build-guide)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

OrbitFS Client is a privacy-first, local-only, high-bandwidth peer-to-peer file sharing application engineered for **Android** and **Desktop (macOS, Windows, Linux)**. Built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)**, OrbitFS operates completely independent of cloud servers, third-party proxies, and internet routing—delivering line-rate file streaming and sub-millisecond latencies across local area networks (LAN/WLAN).

---

## 📚 Table of Contents

- [Interface Preview](#-interface-preview)
- [Installation & Build Guide](#-installation--build-guide)
- [Key Features Overview](#-key-features-overview)
- [Architecture & Tech Stack](#%EF%B8%8F-architecture--tech-stack)
- [Low-Level Design (LLD) Summary](#-low-level-design-lld-summary)
- [Repository Structure](#-repository-structure)
- [Documentation Index](#-documentation-index)
- [License](#-license)

---

## 📱 Interface Preview

<p align="center">
  <img src="docs/screenshots/01_node_hub.png" width="180" alt="Node Hub"/>
  &nbsp;&nbsp;
  <img src="docs/screenshots/02_radar_discovery.png" width="180" alt="Radar Discovery"/>
  &nbsp;&nbsp;
  <img src="docs/screenshots/03_satellite_mode.png" width="180" alt="Satellite Mode"/>
  &nbsp;&nbsp;
  <img src="docs/screenshots/05_file_explorer.png" width="180" alt="File Explorer"/>
</p>

*For complete feature specifications and dark space screen gallery, see [docs/FEATURES.md](docs/FEATURES.md).*

---

## 🛠️ Installation & Build Guide

### Prerequisites
- **JDK 21** or higher (`java -version`)
- **Android SDK** (API Levels 26–35)
- **Gradle 8.x** (wrapper included)

### 1. Build & Run for Android
- **Build Debug APK:**
  ```bash
  ./gradlew :composeApp:assembleDebug
  ```
  *Output APK location:* `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

- **Install & Run on Connected Device / Emulator:**
  ```bash
  ./gradlew :composeApp:installDebug
  ```

### 2. Build & Run for Desktop (macOS, Windows, Linux)
- **Run Desktop App:**
  ```bash
  ./gradlew :composeApp:run
  ```

- **Package Standalone Executable / JAR:**
  ```bash
  ./gradlew :composeApp:desktopJar
  ```
  *Output location:* `composeApp/build/libs/composeApp-desktop.jar`

- **Package Native Installers (DMG / DEB / MSI):**
  ```bash
  ./gradlew :composeApp:package
  ```

---

## 🌟 Key Features Overview

Below is a brief summary of OrbitFS Client capabilities. For in-depth technical documentation, visit [docs/FEATURES.md](docs/FEATURES.md).

- **File Explorer Engine**: Remote directory navigation over TCP sockets, breadcrumb bar jumps, multi-select operations, multi-criteria sorting, and hidden file toggles.
- **Active & Historical Transfer Manager**: Live telemetry speed calculation (`KB/s`, `MB/s`), ongoing notification progress syncing, one-tap **Cancel** action, "Show in Folder" navigation, and gesture-based `HorizontalPager` tab swiping.
- **Zero-Config UDP Radar Discovery**: Automatic UDP broadcast discovery on Port 9999 with Android `MulticastLock`, dead-man timer stale-peer pruning, and profile deduplication.
- **Satellite Mode (Local File Server)**: Turn any device into an active file server with custom port selection, folder picker integration, and Android Foreground Service protection.
- **Profile & Identity Management**: Auto-generates space-themed Pilot Names and Avatars with one-tap seamless profile resets.
- **Ghost Launcher Runtime Interop**: Executes Java 21 server instances inside Android ART using `sun.misc.Unsafe` reflection desugaring patches.

---

## 🏗️ Architecture & Tech Stack

For full architectural patterns and threading details, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

![System Architecture](./docs/diagrams/architecture.svg)

| Layer | Technologies & Frameworks |
|---|---|
| **Languages** | Kotlin, Java 21 |
| **Multiplatform UI** | Compose Multiplatform, Jetpack Compose, Material 3 Design |
| **Async & State** | Kotlin Coroutines, Flow, StateFlow, Channels |
| **Networking & Protocols** | Custom RPC over TCP Sockets, UDP Broadcast (Port 9999), DatagramSocket |
| **Runtime Interop** | `sun.misc.Unsafe` reflection patches, Java NIO, Storage Access Framework (SAF) |
| **Build & CI** | Gradle Kotlin DSL, GitHub Actions |

---

## 📐 Low-Level Design (LLD) Summary

OrbitFS uses a framed TCP socket protocol with 4-byte big-endian length headers. For full sequence diagrams and state machine specifications, see [docs/DESIGN.md](docs/DESIGN.md).

![RPC Framing](./docs/diagrams/rpc_framing.svg)

---

## 📁 Repository Structure

```
OrbitFS-Client/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/      # Shared ViewModels, UI Screens, RPC Client Wrapper, Models
│   │   ├── androidMain/     # Android Foreground Service, SAF Repository, Notifications, MainActivity
│   │   └── desktopMain/     # Desktop JVM Launcher, AWT/ProcessBuilder Handlers, Desktop Repository
│   └── libs/
│       └── orbitfs-core-0.1.0.jar   # Core Pure-Java RPC & Transport Library
├── docs/                    # Technical sub-documentation, diagrams & screenshots
│   ├── diagrams/            # D2-generated SVG architecture & sequence diagrams
│   ├── screenshots/         # Dark space interface screenshots
│   ├── FEATURES.md          # Feature specification & visual showcase
│   ├── ARCHITECTURE.md      # System architecture & KMP patterns
│   └── DESIGN.md            # Low-Level Design (LLD) & protocol spec
├── AGENT.md                 # Technical flight recorder and architectural journal
└── README.md                # Project README
```

---

## 📑 Documentation Index

- [Feature Showcase & Capabilities (docs/FEATURES.md)](docs/FEATURES.md)
- [System Architecture (docs/ARCHITECTURE.md)](docs/ARCHITECTURE.md)
- [Low-Level Design (docs/DESIGN.md)](docs/DESIGN.md)
- [Technical Flight Recorder (AGENT.md)](AGENT.md)

---

## 📄 License
OrbitFS is distributed under the [Apache License 2.0](LICENSE).
