# Distributed Encrypted Chat Application
### Master's Level Core Java Project

A fully-featured, distributed, AES-256 encrypted chat system with group chat, private messaging, file sharing, and a multi-threaded server architecture — built in **pure Core Java** with a Swing GUI.

---

## Abstract

This project demonstrates advanced Java programming concepts including multithreading, socket programming, AES-256 encryption, Java serialization, Swing GUI design, and a distributed server architecture. All messages are encrypted end-to-end using AES-256 in CBC mode with random IVs. User data and chat history are persisted using Java object serialization and plain text files — no database required.

---

## Features

| Feature | Details |
|---|---|
| **AES-256 Encryption** | Every message encrypted before transmission; random IV per message |
| **Multi-threaded Server** | Separate thread per client via `ExecutorService.newCachedThreadPool()` |
| **Private Messaging** | One-to-one encrypted direct messages |
| **Group Chat** | Create groups, join/leave, fan-out delivery to all members |
| **Broadcast** | Send to all connected users simultaneously |
| **File Transfer** | Peer-assisted file transfer with progress reporting |
| **Distributed Nodes** | Multiple server nodes with message forwarding between them |
| **User Authentication** | SHA-256 password hashing, login/logout session management |
| **Chat History** | Per-conversation history files (last 50 messages on demand) |
| **Modern Swing GUI** | Dark-themed login, registration, and full chat dashboard |
| **Heartbeat** | Keep-alive pings prevent stale connections |

---

## Project Structure

```
java-chat-app/
├── Main.java                         ← Entry point (server or client mode)
├── compile.sh                        ← One-command build script
├── run_server.sh                     ← Start the server
├── run_client.sh                     ← Start a GUI client
│
├── src/
│   ├── models/
│   │   ├── User.java                 ← User entity (Serializable)
│   │   ├── Message.java              ← Message entity with Type enum
│   │   └── Group.java                ← Group entity (thread-safe)
│   │
│   ├── common/
│   │   └── Protocol.java             ← Shared constants (ports, tokens, paths)
│   │
│   ├── encryption/
│   │   └── EncryptionUtil.java       ← AES-256 CBC, SHA-256 password hashing
│   │
│   ├── config/
│   │   └── ServerConfig.java         ← Properties-file based server config
│   │
│   ├── storage/
│   │   └── FileStorage.java          ← Serialization-based persistence layer
│   │
│   ├── server/
│   │   ├── ChatServer.java           ← Multi-threaded TCP server, client registry
│   │   ├── ClientHandler.java        ← Per-client thread, message dispatcher
│   │   ├── AuthenticationManager.java← Registration, login, session tracking
│   │   ├── ChatHistoryManager.java   ← History recording and retrieval
│   │   ├── GroupManager.java         ← Group lifecycle and membership
│   │   └── ServerNode.java           ← Peer server connection (distributed)
│   │
│   ├── client/
│   │   └── ChatClient.java           ← TCP client, AES decrypt, send helpers
│   │
│   ├── filetransfer/
│   │   └── FileTransferManager.java  ← Ephemeral port file send/receive
│   │
│   └── gui/
│       ├── LoginWindow.java          ← Dark-themed login screen
│       ├── RegistrationWindow.java   ← Account registration screen
│       └── ChatDashboard.java        ← Full chat UI (messages, users, groups)
│
└── data/                             ← Auto-created at runtime
    ├── users/users.dat               ← Serialized user accounts
    ├── groups/groups.dat             ← Serialized group data
    ├── history/                      ← Per-conversation text history files
    ├── files/                        ← Received files saved here
    └── config/server.properties      ← Server configuration
```

---

## System Architecture

```
 ┌───────────────────────────────────────────────────────┐
 │  CLIENT A          CLIENT B          CLIENT C         │
 │  ChatClient        ChatClient        ChatClient        │
 │  LoginWindow  ←→  ChatDashboard ←→  FileTransferMgr  │
 └──────┬──────────────────┬──────────────────┬──────────┘
        │  TCP Socket       │                  │
        ▼                  ▼                  ▼
 ┌──────────────────────────────────────────────────────┐
 │               SERVER NODE 1 (port 9000)              │
 │  ChatServer (accept loop + CachedThreadPool)         │
 │  ┌────────────┐ ┌────────────┐ ┌────────────┐        │
 │  │ClientHandle│ │ClientHandle│ │ClientHandle│  …     │
 │  └────────────┘ └────────────┘ └────────────┘        │
 │  AuthenticationManager  GroupManager  HistoryManager  │
 └────────────────────────┬─────────────────────────────┘
                          │ TCP (ServerNode)
                          ▼
 ┌──────────────────────────────────────────────────────┐
 │               SERVER NODE 2 (port 9001)              │
 │  (mirrors same structure — messages forwarded)       │
 └──────────────────────────────────────────────────────┘
```

### Encryption Flow

```
Sender                               Receiver
  │                                     │
  ├─ plainText                          │
  ├─ generate random IV (16 bytes)      │
  ├─ AES-256-CBC(plainText, key, IV)    │
  ├─ Base64(IV + cipherText) ──────────►├─ Base64.decode
  │                                     ├─ extract IV (first 16 bytes)
  │                                     ├─ AES-256-CBC.decrypt(cipherText, key, IV)
  │                                     └─ plainText
```

---

## Quick Start

### Prerequisites

- Java 17+ (OpenJDK recommended)
- A terminal (bash)

Check your Java version:
```bash
java -version
```

### Step 1 — Compile

```bash
cd java-chat-app
chmod +x compile.sh run_server.sh run_client.sh
./compile.sh
```

### Step 2 — Start the Server

Open a terminal:
```bash
./run_server.sh
```

### Step 3 — Start Clients

Open one or more additional terminals:
```bash
./run_client.sh
```

### Step 4 — Use the Application

1. **Register** — click "Register", fill in details, submit
2. **Login** — enter credentials, click "Login"
3. **Private chat** — double-click a username in the Online Users panel
4. **Group chat** — click "+ Create Group" or "Join Group", then double-click the group
5. **Broadcast** — click "Broadcast" to message everyone
6. **Send file** — select a user for private chat, click "📎 File"

---

## Running Two Server Nodes (Distributed Mode)

```bash
# Terminal 1 — primary node
./run_server.sh 9000 SERVER-1

# Terminal 2 — secondary node (configure it to peer with SERVER-1)
./run_server.sh 9001 SERVER-2
```

Edit `data/config/server.properties` on SERVER-2 to add:
```properties
server.peers=localhost:9000
```

---

## OOP Design Principles Demonstrated

| Principle | Where |
|---|---|
| **Encapsulation** | All model fields private with getters/setters |
| **Inheritance** | `JFrame` extended by all GUI windows |
| **Polymorphism** | `Message.Type` enum dispatched via switch |
| **Abstraction** | `FileStorage` hides serialization details from callers |
| **Interface-like design** | `Consumer<Message>` callback decouples network from GUI |
| **Thread safety** | `synchronized` methods in `GroupManager`, `AuthenticationManager`; `ConcurrentHashMap` throughout |

---

## Algorithms Used

| Algorithm | Purpose |
|---|---|
| AES-256 CBC | Symmetric message encryption |
| SHA-256 | Password hashing (constant-time comparison) |
| Random IV generation | Prevents identical ciphertexts for identical plaintexts |
| Object serialization | User and group persistence |
| CachedThreadPool | Efficient thread reuse for client handlers |

---

## Key Classes

| Class | Role |
|---|---|
| `ChatServer` | Accept loop, client registry, broadcast, peer node management |
| `ClientHandler` | Reads and dispatches messages for one connected client |
| `AuthenticationManager` | Register/login/logout with SHA-256 and session tracking |
| `GroupManager` | Create/join/leave groups with thread-safe membership |
| `ChatHistoryManager` | Thin persistence wrapper for conversation history |
| `ServerNode` | Bidirectional connection to a peer server node |
| `ChatClient` | Manages the client-side socket, decrypts inbound messages |
| `FileTransferManager` | Ephemeral-port file send/receive with progress callbacks |
| `EncryptionUtil` | AES-256 encrypt/decrypt, key derivation, SHA-256 password hash |
| `FileStorage` | Atomic serialized read/write for users and groups |
| `LoginWindow` | Swing login screen with async SwingWorker network call |
| `RegistrationWindow` | Swing account creation screen |
| `ChatDashboard` | Full-featured Swing chat UI |

---

## Test Cases

| Test | Steps | Expected |
|---|---|---|
| **User Registration** | Open client, click Register, fill form | "Account created!" message |
| **Login** | Enter credentials, click Login | Dashboard opens |
| **Wrong password** | Enter bad password | "Login failed" message |
| **Private message** | Double-click user, type message, Send | Other client receives message |
| **AES verification** | Capture network traffic (Wireshark) | Only Base64 ciphertext visible |
| **Group create** | Click "+ Create Group", fill form | Group appears in list |
| **Group message** | Select group, send message | All group members receive it |
| **File transfer** | Select user, click 📎, choose file | File appears in data/files/ |
| **Broadcast** | Click Broadcast, send message | All online users receive it |
| **Multi-client** | Run 3+ clients simultaneously | All see each other in Online Users |
| **Server restart** | Stop/restart server | Users/groups persist (loaded from files) |

---

## Future Scope

- Diffie-Hellman key exchange for perfect forward secrecy
- End-to-end encryption (recipient's public key)
- Message delivery receipts (read receipts)
- Push notifications
- Voice/video chat via RTP
- Mobile client (Android)
- LDAP/OAuth2 authentication
- Message search
- Emoji and rich text

---

## Conclusion

This project demonstrates a production-quality architecture using only the Java Standard Library. The layered design (models → storage → server logic → network → GUI) makes each component independently testable and extensible. AES-256 encryption ensures all transmitted data is confidential, and the distributed server architecture provides horizontal scalability and basic fault tolerance.
