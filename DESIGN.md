# OrbitFS-Android — Technical Design

Full context: `README.md` for overview, `AGENT.md` for workflow, OrbitFS `DESIGN.md` for server/protocol details.

---

## 1. Overview

OrbitFS-Android is a client app that connects to a remote OrbitFS server over TCP to browse and stream files. It uses the OrbitFS core client library (`orbitfs-core`) which provides the `OrbitFSClient` interface, `NetworkTransportClient`, and `LRUChunkCache`.

---

## 2. Architecture

```
User
  → Jetpack Compose UI (single-activity)
  → ViewModel (state + coroutines)
  → OrbitFSClient (Kotlin interface from orbitfs-core)
    → CachingOrbitFSClient (LRU 64KB chunk cache)
    → NetworkTransportClient (TCP, ORBT framed JSON protocol)
  → OrbitFS Server (remote, over TCP)
```

### Layer Responsibilities

| Layer | Responsibility |
|-------|---------------|
| **UI (Compose)** | File browser list, file reader, connection settings |
| **ViewModel** | Holds UI state, manages coroutines, delegates to client |
| **OrbitFSClient** | Protocol interface — stat/list/read/write/mkdir/delete/rename |
| **CachingOrbitFSClient** | Wraps transport, adds LRU chunk cache (64KB chunks, 1024 max) |
| **NetworkTransportClient** | TCP socket, frame encode/decode (ORBT magic + length prefix + JSON) |

### Key difference from server
- Server uses Java virtual threads (JVM 21) per connection
- Android app uses **Kotlin coroutines** for async I/O — single persistent TCP connection, multiplexed via `requestId` + `CompletableFuture` (from orbitfs-core Java layer)
- Coroutines bridge: `suspend` functions wrap blocking `OrbitFSClient` calls into `Dispatchers.IO`

---

## 3. Connection Management

The app must manage the TCP connection lifecycle according to Android activity lifecycle:

```
onStart() → connect()            (or reconnect)
onResume() → refresh file list
onPause() → pause ongoing reads
onStop() → disconnect()          (or keep alive if background allowed)
```

### Connection configuration
- Host + port stored in `Settings` (SharedPreferences)
- Default: `192.168.1.100:9090` (user-configurable)
- Optional auth token (JWT/API key) — passed via `AuthContext` (future)

### Reconnection strategy
If `connect()` fails:
1. Show error in UI ("Server unreachable")
2. Retry every 5s (exponential backoff up to 60s)
3. If 5 failures → "Give up" message

---

## 4. Caching Strategy

Uses `LRUChunkCache` from orbitfs-core. Each `read(path, offset, count)`:

1. Check cache for chunk containing `offset`
   - **HIT** → return from cache (0 RPC)
   - **MISS** → fetch from server → cache chunk + return

### Cache behavior on Android
- Cache is **not** automatically flushed on `onStop()` — preserve for quick resume
- `flush()` is called only when user explicitly writes/closes a file
- `invalidate(path)` called when server data changes (e.g. after `write`)

### Memory consideration
- 64KB × 1024 chunks = max 64MB cache
- This is acceptable for modern phones/tablets
- For TV devices (often less RAM), consider reducing via config

---

## 5. File Streaming

Large files are streamed in 64KB chunks:
- `READ` request sends `offset` + `count`
- Server responds with data (up to 64KB)
- Cache stores each chunk by `path:chunkIndex`
- For continuous read: prefetch next chunk while processing current

### Media playback
For video/audio:
- Use `ExoPlayer` with a custom `DataSource` that calls `OrbitFSClient.read()`
- ExoPlayer requests `LOAD_CHUNK` → our `DataSource` fetches via cache/network

---

## 6. Known Issues

1. **Coroutines → CompletableFuture bridge**: orbitfs-core uses Java `CompletableFuture`; must convert to `suspend` via `suspendCancellableCoroutine` on `Dispatchers.IO`. Avoid context leaks.
2. **AuthContext threading**: `AuthContext` objects are created per-request — must include token from settings in each call.
3. **Large file stat**: orbitfs-core `stat()` returns basic info (size, isDir, modTime). No thumbnails/previews on server side.
4. **TV UI**: 10ft UI requires D-pad navigation — Compose `LazyColumn` with focus modifiers.

---

## 7. Testing

- **Unit tests** (JVM): `./gradlew test` — ViewModel logic, path formatting, connection retry logic
- **Instrumented tests** (device): `./gradlew connectedAndroidTest` — UI flow, file browser navigation
- **Mocking**: Mock `OrbitFSClient` for ViewModel tests (no network needed)

Run OrbitFS server in emulator for integration tests:
```bash
# On host, forward port to emulator
adb forward tcp:9090 tcp:9090
# Run OrbitFS server on host pointing to test directory
java -jar orbitfs-server.jar --port 9090 --root ~/test-files
```

---

## 8. Roadmap

1. **Scaffold UI** — main activity, file browser list, connection dialog
2. **Wire OrbitFSClient** — stat + list endpoints
3. **File streaming** — read endpoint with chunk caching
4. **Authentication** — JWT/API key token management
5. **Write support** — upload/create files (depends on orbitfs `write` command)
6. **ExoPlayer integration** — for video/audio playback
7. **Background sync** — download for offline access
8. **TV UI polish** — D-pad navigation, 10ft design

---

## 9. Mobile Architecture Decision

- **Jetpack Compose** — modern, declarative, works well with TV (10ft UI)
- **Single-activity** — no Fragments, simpler lifecycle
- **ViewModel + Coroutines** — standard architecture components
- **orbitfs-core** as library — no bundling server code into app (server stays separate)
- **No native code** (JNI) — pure Kotlin/Java, all via orbitfs-core Java API
