# OrbitFS Client — Low-Level Design (LLD)

## 📐 Low-Level Design Specification

### 1. RPC Message Framing & Socket Protocol

OrbitFS communicates over raw TCP sockets using a 4-byte length-prefixed binary header followed by a minified JSON command payload.

```
┌───────────────────────────┬──────────────────────────────────────────┐
│   Length Header (4 bytes) │         JSON Payload (Variable)          │
│   Big-Endian 32-bit Int   │   {"id":1, "method":"READ", ...}         │
└───────────────────────────┴──────────────────────────────────────────┘
```

#### Protocol Opcodes / Methods:
- **`PING`**: Health check and round-trip latency measurement.
- **`OPEN`**: Validates remote path and returns a session file handle.
- **`LIST`**: Lists directory contents with metadata (`size`, `isDir`, `lastModified`, `extension`, `permissions`).
- **`READ`**: Reads raw file bytes at offset `N` up to count `C` (Base64 encoded).
- **`WRITE`**: Writes Base64 encoded payload to offset `N`.
- **`STAT`**: Returns file metadata and canonical sandbox check.
- **`CLOSE`**: Closes active file handles on server.

---

### 2. State Machine Diagrams

#### Connection Lifecycle
```
[ Disconnected ] ──────( User Connects )──────► [ Connecting ]
       ▲                                               │
       │                                       ( Success / Error )
       │                                               │
       └─────────────────( Disconnect )───────────────┤
                                                       ▼
                                              [ Connected / Error ]
```

#### File Download State Machine
```
[ NOT_STARTED ] ───( Start Stream )───► [ IN_PROGRESS ] ───( Finished )───► [ COMPLETE ]
                                              │
                                     ( Cancel / Failure )
                                              │
                                              ▼
                                    [ CANCELLED / FAILED ]
```

---

### 3. Data Transfer Sequence Diagram

```
User (App)            FileBrowserViewModel          OrbitFSClientWrapper           Satellite Server
    │                           │                            │                            │
    │ ─── 1. Download File ───► │                            │                            │
    │                           │ ─── 2. getOutputStream ──► │                            │
    │                           │ ─── 3. streamFile() ─────► │                            │
    │                           │                            │ ─── 4. OPEN RPC ─────────► │
    │                           │                            │ ◄── Handle Result ──────── │
    │                           │                            │                            │
    │                           │                            │ ┌─ 5. READ Loop (Chunk) ─┐ │
    │                           │                            │ │   READ RPC ──────────► │ │
    │                           │                            │ │   Base64 Chunk ◄────── │ │
    │                           │ ◄── 6. Progress (p, t) ─── │ └────────────────────────┘ │
    │                           │                            │                            │
    │ ◄── 7. UI Update (B/s) ── │                            │ ─── 8. CLOSE RPC ────────► │
```

---

### 4. Security & Path Canonicalization (`SandboxGuard`)

To protect the host device from malicious directory traversal attempts (`../../system/hosts`), all path requests pass through `SandboxGuard`:

```kotlin
fun resolveSafely(sandbox: SandboxGuard, rawPath: String?): Path {
    val input = rawPath?.trim() ?: ""
    if (input.isEmpty() || input == "/" || input == ".") return sandbox.root

    val relative = input.trimStart('/')
    val resolved = sandbox.root.resolve(relative).normalize()
    
    if (!resolved.startsWith(sandbox.root)) {
        throw SecurityException("Access outside sandbox forbidden")
    }
    return resolved
}
```

If any resolved path escapes `sandbox.root`, a `SecurityException` is thrown and the RPC request is aborted.
