# OrbitFS-Android

Android client app for [OrbitFS](https://github.com/Rishabh2804/OrbitFS) — a lightweight filesystem served over TCP.

Browse and stream files from a remote OrbitFS server directly on your phone or Android TV.

## Features (Planned)
- Browse remote filesystem over TCP
- Stream large files with LRU chunk caching (64KB chunks)
- Jetpack Compose UI
- Authentication via JWT/API key (depends on [Capsule](https://github.com/Rishabh2804/Capsule))

## Build
```bash
./gradlew installDebug
```

## Dependencies
- `com.github.Rishabh2804.OrbitFS:orbitfs-core:0.1.0` — OrbitFS client + protocol
- Jetpack Compose — UI
- Kotlin Coroutines — async

## License
See [OrbitFS](https://github.com/Rishabh2804/OrbitFS) for license info.
