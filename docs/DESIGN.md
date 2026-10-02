# OrbitFS Client — Low-Level Design (LLD)

## 📐 Low-Level Design Specification

### 1. RPC Message Framing & Socket Protocol

OrbitFS communicates over raw TCP sockets using a 4-byte length-prefixed binary header followed by a minified JSON command payload.

```mermaid
graph LR
    subgraph Header["4-Byte Header (Big-Endian Int)"]
        H["Payload Length (e.g. 1024)"]
    end
    subgraph Payload["JSON RPC Payload"]
        P["{'id': 1, 'method': 'READ', 'fd': '...', 'offset': 0, 'count': 65536}"]
    end
    Header --> Payload
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
```mermaid
stateDiagram-v2
    [*] --> Disconnected
    Disconnected --> Connecting: Connect Request
    Connecting --> Connected: Handshake Success
    Connecting --> Error: Handshake Failed
    Connected --> Disconnected: User Disconnect
    Error --> Disconnected: Reset / Retry
```

#### File Download State Machine
```mermaid
stateDiagram-v2
    [*] --> NOT_STARTED
    NOT_STARTED --> IN_PROGRESS: Start Transfer
    IN_PROGRESS --> COMPLETE: Stream Completed
    IN_PROGRESS --> CANCELLED: User Cancelled / Socket Teardown
    IN_PROGRESS --> FAILED: Network / I/O Exception
```

---

### 3. Data Transfer Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User as User (App)
    participant VM as FileBrowserViewModel
    participant Client as OrbitFSClientWrapper
    participant Server as Satellite Server

    User->>VM: Download File (file)
    VM->>VM: getOutputStream()
    VM->>Client: streamFile(path, stream)
    Client->>Server: OPEN RPC (path)
    Server-->>Client: Handle Result
    loop Chunk Streaming
        Client->>Server: READ RPC (offset, count)
        Server-->>Client: Base64 Chunk Response
        Client->>VM: Progress Update (bytes, total)
        VM-->>User: Live UI Speed & Progress Update
    end
    Client->>Server: CLOSE RPC (handle)
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
