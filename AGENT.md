# AGENT.md — OrbitFS Project Flight Recorder & Manifest

> "Your Data, in Local Orbit"

This document serves as the exhaustive technical journal, architectural blueprint, and project manifest for the OrbitFS project. It chronicles our architectural design patterns, previous engineering battles with the JVM/ART runtimes, current active issues, and our future technical roadmap.

---

## 1. The Motto & Spirit

### "Your Data, in Local Orbit."
In an era dominated by ubiquitous cloud architecture, centralized telemetry, and subscription-based remote storage models, OrbitFS asserts a radical return to local digital sovereignty. The philosophy is unyielding: your personal data should never leave your physical vicinity unless explicitly dictated by you. OrbitFS is engineered as a privacy-first, local-only, high-bandwidth data sharing infrastructure that operates entirely independent of the public internet. 

By utilizing peer-to-peer transport layers and bypassing intermediate cloud proxies, we eliminate bandwidth throttling, cloud subscription overheads, tracking vectors, and corporate data mining. Whether streaming high-definition media, transferring multi-gigabyte build artifacts, or synchronizing large repositories between a mobile device and a laptop, the transaction happens at line-rate over your local area network (LAN/WLAN), achieving sub-millisecond latencies and maximal throughput.

---

## 2. The Genesis: From Native to Multiplatform

### Core Orbit
The heartbeat of the system resides in `orbitfs-core-0.1.0.jar`. This library was initially designed as a standalone pure-Java service meant to run on high-performance desktop or server instances. It leans heavily into modern Java 21 capabilities, utilizing Java Records for lightweight, immutable data structures and pioneering the use of Virtual Threads (`java.lang.Thread.ofVirtual()`) to handle thousands of concurrent file chunk requests without the massive stack allocation overhead of platform threads. 

The underlying RPC protocol is a custom binary-framed JSON protocol. Messages are length-prefixed with a 4-byte big-endian integer indicating the payload size, followed by a minified JSON string representing the command structure (e.g., `READ`, `WRITE`, `STAT`, `LIST`). File transfers are broken down into a maximum of 64KB chunks to optimize socket buffering and ensure graceful recovery over flaky wireless connections.

### Orbit Android (Initial)
In its first iteration, Orbit Android was a native, siloed project built with a traditional Android view hierarchy and eventually migrating parts to early Jetpack Compose. Discovery was implemented using Android's native `NsdManager` for multicast DNS (mDNS). The implementation was notoriously fragile and plagued by Android lifecycle issues. The `NsdManager.DiscoveryListener` would routinely fail to unbind, throwing `IllegalArgumentException` on activity destruction or entering unrecoverable states where `onDiscoveryStarted` would fire but no services were ever resolved due to OEM-specific multicast filtering or aggressive battery saver modes.

### The Merger
To address the unsustainable fragmentation of maintaining separate Android and Desktop codebases, the project underwent an architectural restructuring into a Kotlin Multiplatform (KMP) project. The core responsibilities of connection state, configuration, and presentation logic were consolidated into `commonMain`. 

Components such as the `ConnectionManager` (handling the TCP socket state and retry state machines), `SettingsRepository` (abstracting key-value persistence via Multiplatform Settings), and the `FileBrowserViewModel` (managing UI states, breadcrumbs, and directory cache) were successfully migrated into shared Kotlin code. Platform-specific boundaries were cleanly drawn using Kotlin’s `expect`/`actual` mechanisms or interface injection, enabling the Desktop and Android variants to utilize the exact same state machine.

---

### 3. Architectural Deep-Dive

#### The "Ghost Launcher" (Critical)
The most complex engineering challenge arose when trying to run `orbitfs-core-0.1.0.jar` inside the Android runtime (ART). While modern Android toolchains support Java 21 syntax via desugaring, ART entirely lacks the runtime infrastructure for Java 21 Virtual Threads. Specifically, calling `Executors.newVirtualThreadPerTaskExecutor()` throws an immediate runtime exception on Android.

To circumvent this, we engineered the **Ghost Launcher** pattern in `SatelliteServerLauncher`. Using `sun.misc.Unsafe`, we bypass the constructor entirely, preventing the virtual thread setup from ever being initialized. We then manually inject safe components and handlers using reflection.

#### UDP Radar Discovery
Standard mDNS proved fragile. We moved to a custom **UDP Broadcast Beacon on Port 9999**. Both devices broadcast a structured packet: `ORBITFS_BEACON|Name|Port|Avatar|NodeId`. 

#### Identity & Session Security
Every installation generates a unique, persistent **Node ID** (alphanumeric string). This ID allows devices to recognize each other even if their IP address or Pilot Name changes, preventing duplicate entries in the Radar. We also implemented a **Session-Lock** logic: if a connection is severed, the UI immediately blocks navigation and returns the user to the home screen.

### Isolated Storage
Security within a peer-to-peer file server is paramount. The server uses a component called `SandboxGuard` to ensure that clients cannot traverse outside the designated `shared` directory. Before any file operation (`READ`, `WRITE`, `DELETE`) is executed, the `SandboxGuard` performs strict canonical path validation:

```java
File baseDir = new File("/storage/emulated/0/OrbitFS/shared").getCanonicalFile();
File targetFile = new File(baseDir, requestedPath).getCanonicalFile();

if (!targetFile.getPath().startsWith(baseDir.getPath())) {
    throw new SecurityException("Directory traversal attempt detected: " + requestedPath);
}
```

This jail ensures that even if a compromised or malicious client transmits a relative traversal string like `../../../../system/etc/hosts`, the underlying OS file handle will never be created, preserving device integrity.

---

## 4. The Bug Chronicles (Previous & Fixed)

### The "Black Screen" Regressions
During the initial migration of the UI layer to Compose Multiplatform, developers reported that navigating to the file browser layout caused the entire screen to turn black and freeze, rendering the interface completely unresponsive. Analysis via Layout Inspector revealed that a `HorizontalPager` component used for swiping between tabs was aggressively intercepting all touch target coordinates. 

Because the inner file explorer view relied on a lazy scrollable column, the pointer input dispatch chain entered a deadlock state. The `HorizontalPager` consumed all pointer down events without passing them down to children, giving the illusion of a crashed or dead UI. The fix involved applying custom nested scroll connection strategies and explicitly defining pointer bounds, forcing the pager to defer touch consumption when a child scroll gesture is active.

### The "Error Code 1" Handshake
Early iterations of the chunked file streaming engine frequently failed during the initial file verification phase, throwing a generic `RPCException: Error Code 1`. The root cause was traced to the framing layer of the custom TCP socket channel. The writer loop was sending the minified JSON command header but failing to flush the socket stream before writing the raw binary payload bytes:

```
com.orbitfs.core.RPCException: Error Code 1: Invalid packet header alignment
    at com.orbitfs.core.NetworkTransportClient.readResponse(NetworkTransportClient.java:88)
    at com.orbitfs.core.NetworkTransportClient.sendRPC(NetworkTransportClient.java:54)
```

Because the length-prefixed header buffer was not flushed immediately, the client read an incomplete header frame, causing buffer misalignment and throwing a fatal exception. The implementation was fixed by wrapping the outputs in a `BufferedOutputStream` and guaranteeing a strict `flush()` call immediately following header transmission.

### Android 12/13/14 Permissions
Hardening the background synchronization service across modern API levels required navigating a complex maze of permissions. On Android 13+, the system would silently kill the background foreground service unless `POST_NOTIFICATIONS` was explicitly requested and granted. Furthermore, due to platform modifications by specific OEMs, local network discovery sometimes triggers hardware-level scans that require `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permissions. The solution involved implementing an aggressive runtime permission workflow that explicitly requests these manifests sequentially, falling back to reduced-functionality modes gracefully if the user denies them.

### The Recursive Pathing Loop (Resolved)
*   **Symptoms:** When navigating deep into a directory, duplicate path segments accumulated (e.g. `/docs/docs/docs`).
*   **Root Cause:** In `orbitfs-core`, `NetworkTransportClient.listWithStat(...)` passes the requested directory path inside `request.fd()`, leaving `request.path()` as `null`. Previous monkey-patched server handlers in `main.kt` and `SatelliteServerLauncher.kt` were only reading `request.path()`, which always evaluated to null/empty string. The server repeatedly re-listed the root directory, causing the client UI to append the directory name upon every click.
*   **Resolution:** Implemented `OrbitServerPatcher.patch()`, which reads `(request.fd() ?: request.path())`, normalizes paths via `resolveSafely()`, and lists the correct subdirectory contents. Updated `FileBrowserViewModel.navigateTo()` to enforce canonical segment formatting.

### The "Access Denied" Sandbox Bug (Resolved)
*   **Symptoms:** Fetching root listings or navigating into subdirectories failed with `SecurityException: Directory traversal attempt detected`.
*   **Root Cause:** `SandboxGuard.resolve(path)` in `orbitfs-core` treats any string starting with `/` (other than `"/"`) as an absolute host filesystem path, and checks if it starts with the server's root path prefix. When the client UI passed paths with leading slashes (e.g. `"/docs"`), `SandboxGuard` rejected them.
*   **Resolution:** Created `OrbitServerPatcher.resolveSafely()`, which safely maps `""`, `"/"`, `"."`, and `null` to `sandbox.root`, checks absolute paths within the sandbox, and strips leading slashes for relative child paths before resolving against `sandbox.root`. Updated `OrbitFSClientWrapper.preparePath()` to sanitize paths prior to sending RPC requests.

### Self-Discovery Ghosting (Resolved)
*   **Symptoms:** On machines with multiple network interfaces (Wi-Fi, Ethernet, Docker bridges, VPNs), OrbitFS Desktop captured its own UDP Broadcast Beacon on Port 9999 and entered an infinite loopback handshake with itself.
*   **Root Cause:** `OrbitRadar` refreshed `localIPs` but never compared incoming packet IP addresses against `localIPs`, and `localNodeId` was uninitialized when discovery began. Furthermore, the UDP discovery socket did not configure `SO_REUSEADDR` before binding.
*   **Resolution:** In `OrbitRadar`, incoming packets are now checked against `packet.address.isLoopbackAddress`, `isAnyLocalAddress`, and `localIPs.contains(host)`. Set `reuseAddress = true` before binding in `DatagramSocket(null)`. Added a background pruner task with a 5-second dead-man timer to automatically drop offline peers.

---

## 5. Current Roadblocks (Active Bugs)

### High Priority
*   **Android Content URIs**: The backend core relies on `java.nio.file.Path`, which cannot directly handle Android `content://` URIs (SAF). Currently, selecting a non-standard folder on Android falls back to internal storage.
*   **Mac Discovery Performance**: Mac satellites are occasionally slow to appear on the Android Radar if they were running before the Android app launched.

### Low Priority
*   **Desktop Window Resizing**: Compose Desktop UI elements occasionally misalign when the window is scaled too small.

---

## 6. Vision: The BLE Handshake & Beyond

### ShareIt-Inspired Handshake
To maximize user adoption and seamless connectivity, OrbitFS is moving toward an advanced dual-radio bootstrap architecture. Traditional local networking apps fail in public spaces or corporate networks where wireless access points enforce Client Isolation, blocking local UDP and TCP communication entirely. 

Our future architecture introduces a Bluetooth Low Energy (BLE) peripheral mode for zero-config discovery. Nodes will advertise their connection tokens, cryptographic signatures, and local network profiles via BLE GATT characteristics. If client isolation is detected on the active Wi-Fi access point, the app will programmatically prompt the user to spin up an on-demand Wi-Fi Direct group (P2P hot-spot), allowing file transfers to reach line speeds exceeding 500Mbps without relying on router infrastructure.

### Milestone 4 Roadmap
1.  **Peer-to-Peer Multiplexed Chat:** We intend to utilize the existing framed RPC channel to allow small metadata and text exchanges alongside active file streams. By introducing a low-priority opcode (`CHAT_MSG`), users can send secure annotations, notes, or transfer descriptions without requiring an independent messaging framework.
2.  **Identity Verification & Cryptographic Pairings:** To prevent man-in-the-middle attacks on open Wi-Fi networks, connection establishment will require a one-time 6-digit pairing token generated using an ephemeral Diffie-Hellman key exchange. Once verified, the public keys are persisted in the secure storage layer of the respective operating system, ensuring future sessions are fully encrypted and trusted.

---

## 7. Maintenance Guide for the Next Developer

### Verifying the Build Pipeline
To run a complete clean compilation of the multiplatform target and ensure Android compliance, execute the following command from the project root:

```bash
./gradlew clean :composeApp:assembleDebug
```

Always monitor the build logs for any desugaring errors or configuration warnings relating to duplicate class definitions, as library linkages into `commonMain` can occasionally conflict with platform-specific dependencies.

### Managing the Ghost Instances
When editing or refactoring the server transport layer inside `SatelliteServerLauncher`, proceed with extreme caution. Since `sun.misc.Unsafe.allocateInstance()` completely bypasses class validation and constructors, any non-primitive member variables that are not explicitly initialized via reflection will contain a null reference. If you add a field to `OrbitServerImpl`, you must explicitly initialize it via reflection inside the launcher's manual initialization block, otherwise the application will throw silent, un-debuggable `NullPointerException` errors deep within the native thread scheduling loop.

### Standardizing the Path Normalization
To resolve ongoing routing bugs, any new logic handling file browsing must route its string parameters through a unified sanitizer function inside the client wrapper before submitting an RPC request. Never concatenate raw string paths using manual delimiters. Always leverage the platform-independent path resolution utility found in `commonMain` to guarantee proper canonical alignment before the packet crosses the socket bridge.
