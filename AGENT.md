# AGENT.md — OrbitFS-Android Development Guide

> If you're working on OrbitFS-Android, read this first. It defines the process, conventions, and workflow.

---

## Project

**OrbitFS-Android** — Android app that connects to an OrbitFS server over TCP to browse and stream files from a remote filesystem.

- **Repo**: `~/Projects/OrbitFS-Android` (separate repo)  
- **Parent**: `~/Projects/OrbitFS`  
- **Build**: Gradle (Android Gradle Plugin)  
- **Language**: Kotlin (primary) + Java (interop where needed)  
- **UI**: Jetpack Compose (single-activity architecture)

---

## Current State
- ✅ Project skeleton created (empty)
- ⬜ UI scaffolding — main activity, file browser screen
- ⬜ OrbitFS client integration — `CachingOrbitFSClient` + `NetworkTransportClient`
- ⬜ Network connection management
- ⬜ File browsing — list/stat files via OrbitFS protocol
- ⬜ File streaming — chunked reads via `LRUChunkCache`
- ⬜ Authentication — JWT/API key support (depends on Capsule)

---

## Architecture

The Android app is a client for the OrbitFS server. It does **not** run a server itself — it connects to one.

```
User
  → Compose UI (Jetpack Compose)
  → ViewModel (state management)
  → OrbitFSClient (Kotlin interface)
    → CachingOrbitFSClient (LRU chunk cache, 64KB chunks)
    → NetworkTransportClient (TCP, framed JSON protocol)
  → OrbitFS Server (remote)
```

### Key interfaces
The Android app uses OrbitFS core JAR:
```kotlin
interface OrbitFSClient {
    suspend fun stat(path: String): FileStat
    suspend fun list(path: String): List<DirEntry>
    suspend fun read(path: String, offset: Long, count: Int): ByteArray
    suspend fun write(path: String, data: ByteArray): Int
}
```

### Dependency on OrbitFS core
- JitPack: `com.github.Rishabh2804.OrbitFS:orbitfs-core:0.1.0`  
- Or local JAR: `../OrbitFS/build/libs/orbitfs-core-0.1.0.jar` (dev)

---

## Quick Start

```bash
# Build & run (requires Android SDK)
./gradlew installDebug

# Or run tests
./gradlew test

# Debug build
./gradlew assembleDebug
```

---

## Workflow

### 1. Find Work
```bash
gh issue list --state open
```

### 2. Code with TDD
- Write test first in `app/src/test/java/org/orbitfs/android/`  
- Run single test: `./gradlew test --tests "*.<ClassName>.<methodName>"`  
- Implement in `app/src/main/java/org/orbitfs/android/`  
- Run all tests: `./gradlew test`

### 3. Commit Locally
```bash
git add -A
git commit -m "feat: <description>

- What changed
- Why"
```

### 4. Push to Branch
```bash
git checkout -b feat/<feature-name>
git push -u origin feat/<feature-name>
```

### 5. Create PR
```bash
gh pr create --title "feat: <description>" --body "..."
```

### 6. CI & Merge
- Wait for CI green  
- Merge with: `gh pr merge --squash`  
- Sync local: `git checkout main && git pull --ff-only`

---

## Coding Conventions

### Branch naming
- `feat/<feature-name>` — new features  
- `fix/<bug-description>` — bug fixes  
- `chore/<maintenance>` — refactoring, deps

### Commit messages
```
type: brief description

- bullet point of change
```

### Code style
- Kotlin primary, Java for interop  
- Jetpack Compose for UI  
- Coroutines for async (replace virtual threads from server side)  
- SLF4K or Android Logger for logging  
- Tests in JUnit 5 (JVM) / JUnit4 (instrumented)

---

## Testing Approach
```bash
# Unit tests
./gradlew test

# Instrumented tests (device required)
./gradlew connectedAndroidTest
```

---

## Implementation Priority

1. **UI scaffolding** — Main activity, file browser composable
2. **OrbitFSClient integration** — Kotlin interface, network transport
3. **Connection management** — host/port config, lifecycle-aware
4. **File browsing** — stat + list via OrbitFS protocol
5. **File streaming** — chunked read with LRU cache
6. **Authentication** — JWT/API key token management
7. **Caching** — `LRUChunkCache` integration
