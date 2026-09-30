<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="MeshLink Logo" width="100"/>

# MeshLink — Offline Mesh Messaging

**Peer-to-peer encrypted chat that works without internet, cellular signal, or central servers.**  
Built with Android Nearby Connections API, Jetpack Compose, and Clean Architecture.

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20Multi--Module-blue)](PROJECT.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 📖 Table of Contents

- [About](#-about)
- [Key Features](#-key-features)
- [Architecture & Modules](#-architecture--modules)
- [Developer & Update Guide](#-developer--update-guide)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [Project Structure](#-project-structure)
- [Testing](#-testing)
- [Permissions](#-permissions)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌐 About

**MeshLink** is an offline-first Android application that enables real-time peer-to-peer messaging between nearby devices using **Wi-Fi Direct** and **Bluetooth Low Energy** — no Wi-Fi router, no mobile data, and no SIM card required.

Designed for off-grid communications, natural disasters, remote expeditions, protests, and localized private communication, MeshLink automatically discovers nearby devices and relays packets across multiple intermediate nodes, forming a decentralized, self-healing **mesh network**.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 📡 **Multi-Hop Mesh Networking** | Messages hop across intermediate devices automatically when destination is out of direct range |
| 🔒 **End-to-End Encryption (E2EE)** | ECIES (EC P-256 + AES-256-GCM + SHA-256 HKDF) ensures relay nodes cannot read message contents |
| 💬 **Offline Direct Chat** | Real-time one-to-one encrypted messaging with auto-connect |
| 📢 **Broadcast Channels** | Send emergency announcements or notifications to all reachable peers |
| 🚨 **SOS Emergency Beacon** | One-tap distress beacon broadcasting emergency alerts with local medical profile data |
| 🏥 **Local Medical Profile** | Securely store blood type, allergies, medications, and emergency contacts on-device |
| 📬 **Store & Forward Queue** | Offline messages are queued locally in Room DB and automatically flushed upon reconnection |
| 🔋 **Adaptive Battery Throttling** | Dynamic scan controllers back off discovery and advertising intervals when idle to preserve power |
| 🛡️ **Cryptographic Identity** | EC P-256 identity keys protected via Android Keystore & EncryptedSharedPreferences |
| 🗃️ **Local Persistence** | Message history, peer metadata, and pending queues backed by Room SQLite |

---

## 🏛️ Architecture & Modules

MeshLink follows **Clean Architecture** principles across 5 decoupled Gradle modules:

```
┌─────────────────────────────────────────────────────────┐
│                          :app                           │  ← Jetpack Compose UI, ViewModels, Service
├────────────────────────────┬────────────────────────────┤
│        :core:data          │         :core:mesh         │  ← Room SQLite DB │ Nearby Connections API
├────────────────────────────┴────────────────────────────┤
│                       :core:domain                      │  ← Pure Kotlin models & repository contracts
├─────────────────────────────────────────────────────────┤
│                       :core:crypto                      │  ← ECIES, ECDH Handshake, Keystore Manager
└─────────────────────────────────────────────────────────┘
```

- **`:app`**: Jetpack Compose UI, navigation graphs, ViewModels, `NearbyService` foreground service, and `MeshCleanupWorker`.
- **`:core:domain`**: Pure Kotlin library containing business domain models (`Message`, `MeshPacket`, `KnownDevice`) and repository contracts.
- **`:core:crypto`**: Android Keystore integration, ECDH key agreements, ECIES ciphers, and AES-256-GCM authenticated encryption.
- **`:core:mesh`**: Google Play Services Nearby Connections layer, multi-hop `MeshRouter`, routing tables, packet validators, and battery controllers.
- **`:core:data`**: Room SQLite persistence, entity mappers, DAOs, and repository implementations.

---

## 📘 Developer & Update Guide

> 💡 **Looking to modify or extend the codebase?**  
> Check out the complete in-depth developer guide: **[PROJECT.md](PROJECT.md)**
>
> It contains:
> - Detailed module breakdown and data flow sequence diagrams
> - Step-by-step guides for adding new screens, navigation routes, and features
> - Cryptography upgrade procedures (PFS, key rotation, QR verification)
> - Routing algorithm extensions and database migration workflows
> - Common gotchas and debugging tips

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin 2.2 |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture + Multi-Module |
| **Dependency Injection** | Hilt 2.59+ |
| **Mesh Transport** | Google Nearby Connections API (`P2P_CLUSTER`) |
| **Security & Crypto** | ECIES (Curve P-256 + AES-256-GCM), Android Keystore, Jetpack Security |
| **Local Database** | Room 2.8+ with KSP code generation |
| **Asynchronous & Reactive** | Kotlin Coroutines, StateFlow, SharedFlow |
| **Background Processing** | Foreground Services, Android WorkManager |
| **Build System** | Gradle Kotlin DSL with Version Catalog (`libs.versions.toml`) |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio Ladybug / Hedgehog** or newer
- **JDK 17**
- **Android Device running API 26+** (Android 8.0+)
- *Note:* Google Nearby Connections requires physical Bluetooth and Wi-Fi Direct hardware. **Two physical devices are recommended for mesh testing.**

### Installation & Run

```bash
# 1. Clone the repository
git clone https://github.com/BariaHarshh/Meshlink--Offline-messaging.git
cd Meshlink--Offline-messaging

# 2. Build the project
./gradlew assembleDebug

# 3. Install on connected physical device
./gradlew installDebug
```

---

## 📁 Project Structure

```
MeshLink/
├── app/                             # UI layer, Navigation & Services
│   └── src/main/java/com/meshlink/app/
│       ├── service/                 # NearbyService foreground service
│       ├── ui/                      # Compose UI (Home, Chat, Discovery, SOS, etc.)
│       └── worker/                  # MeshCleanupWorker WorkManager
│
├── core/
│   ├── domain/                      # Pure Kotlin models & repository interfaces
│   ├── crypto/                      # ECIES encryption, KeyManager, ECDH Handshake
│   ├── mesh/                        # Nearby Connections, MeshRouter, AdaptiveScan
│   └── data/                        # Room Database, DAOs, Entities, Mappers
│
├── gradle/
│   ├── libs.versions.toml           # Version catalog (Single Source of Truth)
│   └── wrapper/                     # Gradle wrapper
│
├── PROJECT.md                       # Comprehensive Architecture & Developer Guide
├── README.md                        # Project documentation
├── build.gradle.kts                 # Root build configuration
└── settings.gradle.kts              # Module configuration
```

---

## 🧪 Testing

```bash
# Run unit tests across all modules
./gradlew test

# Run module-specific unit tests
./gradlew :core:crypto:test
./gradlew :core:mesh:test
./gradlew :app:test

# Run instrumented UI / Room tests on a connected device
./gradlew connectedAndroidTest
```

---

## 🔒 Permissions

MeshLink uses the following runtime permissions (declared in `AndroidManifest.xml`):

```xml
<!-- Bluetooth Discovery & Connections -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Wi-Fi & Nearby -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Background Services & Notifications -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

All permissions are requested upon first app launch via the permission onboarding gate in `MainActivity`.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Review **[PROJECT.md](PROJECT.md)** to understand module conventions
3. Create your feature branch: `git checkout -b feature/amazing-feature`
4. Commit your changes: `git commit -m "feat: add amazing feature"`
5. Push to the branch: `git push origin feature/amazing-feature`
6. Open a Pull Request

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for more information.

Copyright (c) 2026 Harsh Baria.
