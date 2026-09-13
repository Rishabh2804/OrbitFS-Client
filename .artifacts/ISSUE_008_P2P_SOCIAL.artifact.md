# ISSUE: Peer Chat & Ad-hoc File Sharing System

## 📝 Description
Implement a social/P2P layer within OrbitFS. Peers nearby should be able to discover each other, chat in real-time, and securely share specific files or directories via ephemeral server instances.

## 🎯 Functional Requirements
1.  **Peer Discovery**: Automatic listing of nearby "Orbit Pilots" on the same network or within range.
2.  **Encrypted Chat**: Real-time message exchange between peers.
3.  **Restricted Sharing**:
    *   **Sender**: Selects specific files/folders to share.
    *   **Logic**: The app stages these files in a secure "Vault" path: `orbitfs/peers/<peer_id>/<session_id>/`.
4.  **Ephemeral Provisoning**:
    *   Spawns a **new OrbitFS server instance** specifically for the chat session.
    *   Server Root is set to the Vault path mentioned above.
5.  **Access Controls**: Shared root can be marked as read-only or read-write per session.
6.  **Transfer Integration**: Shared files appear in the receiver's Explorer view as a temporary "Peer Satellite".

## ⚙️ Non-Functional Requirements
1.  **Security**: Session IDs must be non-guessable (UUIDs) to prevent reverse engineering of the directory structure.
2.  **Battery Efficiency**: Peer discovery should use low-power modes (Bluetooth LE) when idle.
3.  **Concurrency**: The app must handle multiple simultaneous local server instances (one global, one or more per active sharing session).

## 🔄 Complete Flow
1.  **Handshake**:
    *   Pilot A swipes to "Nearby Pilots" and sees Pilot B.
    *   A sends a "Join Orbit" request. B accepts.
2.  **Chat & Request**:
    *   Pilot A: "Can you send the project spec?"
    *   Pilot B clicks "Share Files" -> Picks `spec.pdf`.
3.  **Dynamic Provisioning (Sender - Pilot B)**:
    *   B's app creates a symlink or copies `spec.pdf` to `/internal/orbitfs/peers/PilotA_ID/Session123/`.
    *   B's app starts a new `OrbitFS Server` on a high port (e.g., 9091) with Root = `/Session123/`.
    *   B's app sends the Connection Metadata (Port, IP, One-time Token) through the chat.
4.  **Retrieval (Receiver - Pilot A)**:
    *   A's app receives metadata and automatically mounts the temporary satellite.
    *   A downloads the file via the standard `Transfers` system.
5.  **Cleanup**:
    *   Once A confirms receipt or the session expires, B's app kills the temporary server and clears the Vault directory.

## 🎨 UI/UX Design
- **Social Tab**: A new primary navigation tab for "Pilots" or "Social".
- **Chat Interface**: Simple bubble-based UI with a "+" action for file attachment.
- **Sharing Modal**: A picker that looks like the File Explorer but is for *selecting* what to grant access to.

## 🛡️ Future Sandbox Integration
- Once the Sandbox system is ready, files received via "Peer Sharing" will be opened **within the Sandbox** by default to protect the host device from untrusted data.
