# OrbitFS Android - Backlog & Non-Blocking Issues

This document tracks identified bugs and feature requests that are non-blocking but should be addressed in future development cycles.

## 🐛 Bug Fixes

### 1. [Transfer] Notification & Transfer Screen Sync
- **Description**: Ensure cancellation from a system notification immediately reflects in the app's "Transfers" history and correctly dismisses the notification.
- **Current State**: Improvements made to `NotificationActionReceiver`, but further edge-case testing is required for background synchronization.

### 2. [Reliability] Extreme Large File Streaming (1GB+)
- **Description**: Stress test the `streamFile` implementation for files exceeding 1GB to ensure memory usage remains constant and network reconnections are handled mid-stream.
- **Status**: Milestone 2 streaming is functional, but robustness against prolonged network instability needs verification.

### 3. [UX] Nested Horizontal Pager Interference
- **Description**: Swiping within the `TransfersScreen` (between Active and History) sometimes triggers the outer page navigation to `Explorer` or `Configuration`.
- **Mitigation**: Outer scroll has been disabled when on the Transfers page, but a more granular gesture priority system is needed.

---

## 🚀 Feature Requests

### 4. [Feature] Local Server Launch
- **Description**: Allow the Android app to act as an OrbitFS Satellite (server). This would enable other devices (laptops, phones) to connect to the Android device and browse its shared storage.
- **Priority**: High (Next Milestone).

### 5. [Identity] Animated Pilot Emblems
- **Description**: Support for animated vectors or shared profile pictures across peers. Every "Pilot" on a node should see the animated emblem of others.
- **Note**: Replaces static system icons with a more dynamic design language.

### 6. [UX] Persistent Transfer History across Sessions
- **Description**: Persist the transfer history log in a local database (Room) so users can see previous downloads even after restarting the app.

---

## ✅ Verified Improvements (Milestone 2 Final)
- **Deep Space Theme**: Dynamic switching between Light/Dark modes fully functional.
- **Smart Paging**: Node Hub swipe logic updated (Profile ↔ Node Hub ↔ Settings).
- **Session Configuration**: Dedicated tab for node-specific network and safety tuning.
- **Download Cancellation**: Explicit job termination and local cleanup implemented.
