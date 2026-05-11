<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="MeshLink Logo" width="100"/>

# MeshLink — Offline Mesh Messaging

**Peer-to-peer encrypted chat that works without internet or cellular signal.**  
Built with Android Nearby Connections API, Jetpack Compose, and Clean Architecture.

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![GitHub last commit](https://img.shields.io/github/last-commit/BariaHarshh/Meshlink--Offline-messaging)](https://github.com/BariaHarshh/Meshlink--Offline-messaging/commits/main)

</div>

---

## 📖 Table of Contents

- [About](#-about)
- [Features](#-features)
- [Architecture](#-architecture)
- [Module Structure](#-module-structure)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [Project Structure](#-project-structure)
- [Screenshots](#-screenshots)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌐 About

**MeshLink** is an offline-first Android application that enables real-time peer-to-peer messaging between nearby devices using **Wi-Fi Direct** and **Bluetooth** — no internet, no SIM card required.

It is designed for situations where traditional communication infrastructure is unavailable: disaster zones, remote areas, protests, or campus environments. Messages hop across multiple devices to extend reach beyond direct Bluetooth/Wi-Fi range, forming a true **mesh network**.

---

## ✨ Features

| Feature | Description |
|---|---|
| 📡 **Mesh Networking** | Messages are relayed hop-by-hop across multiple devices |
| 🔒 **End-to-End Encryption** | ECIES-based encryption per session via the `crypto` module |
| 💬 **Offline Chat** | Real-time messaging with zero internet dependency |
| 📢 **Broadcast** | Send a message to all nearby devices simultaneously |
| 🔍 **Device Discovery** | Auto-discover nearby MeshLink peers via Nearby Connections |
| 🚨 **SOS Mode** | Emergency broadcast with medical profile info |
| 🏥 **Medical Profile** | Store and share emergency health details locally |
| 🔋 **Adaptive Scanning** | Battery-aware scan controller reduces drain when idle |
| 📬 **Message Queuing** | Messages are queued and auto-delivered on reconnect |
| 🗃️ **Local Persistence** | All messages and devices stored in Room database |

---

## 🏛️ Architecture

MeshLink follows **Clean Architecture** with a strict separation of concerns across 5 Gradle modules:

```
┌──────────────────────────────────────────┐
│                  :app                    │  ← Jetpack Compose UI, ViewModels, DI root
├──────────────────────────────────────────┤
│                 :domain                  │  ← Models, Repository interfaces (pure Kotlin)
├────────────────┬─────────────────────────┤
│     :data      │        :mesh            │  ← Implementations: Room DB │ Nearby Connections
├────────────────┴─────────────────────────┤
│                :crypto                   │  ← ECIES encryption, KeyManager, HandshakeManager
└──────────────────────────────────────────┘
```

**Data flow:**
```
UI (Compose) → ViewModel → Domain Repository Interface
                                    ↓
                    ┌───────────────┴──────────────┐
                    :data (Room DB)           :mesh (Nearby)
                                    ↑
                               :crypto (ECIES)
```

---

## 📦 Module Structure

```
MeshLink/
│
├── 📱 app/                          # Main application module
│   └── src/main/java/.../ui/        # Feature-based UI components
│
├── 🧠 core/                         # Core logic and data modules (New Grouping)
│   ├── domain/                      # Business logic & pure models
│   ├── data/                        # Room DB & Repository implementations
│   ├── mesh/                        # Nearby Connections mesh layer
│   └── crypto/                      # ECIES Encryption module
│
├── gradle/                          # Gradle config & version catalog
├── build.gradle.kts                 # Root build script
├── settings.gradle.kts              # Module declarations (includes :core: prefix)
└── README.md

├── gradle/
│   ├── libs.versions.toml           # Version catalog (single source of truth)
│   └── wrapper/                     # Gradle wrapper
│
├── build.gradle.kts                 # Root build script
├── settings.gradle.kts              # Module declarations
├── gradle.properties                # Gradle/JVM flags
└── README.md
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt |
| **Mesh Networking** | Google Nearby Connections API |
| **Encryption** | ECIES (Elliptic Curve Integrated Encryption Scheme) |
| **Database** | Room (SQLite) |
| **Async** | Kotlin Coroutines + StateFlow |
| **Build** | Gradle Kotlin DSL + Version Catalog |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio **Hedgehog** or newer
- JDK **17**
- Android device or emulator running **API 26+**
- Two physical devices for mesh testing (emulators cannot use Nearby Connections)

### Setup

```bash
# 1. Clone the repository
git clone https://github.com/BariaHarshh/Meshlink--Offline-messaging.git
cd Meshlink--Offline-messaging

# 2. Open in Android Studio
# File → Open → select the MeshLink folder

# 3. Sync Gradle
# Android Studio will auto-sync. If not: File → Sync Project with Gradle Files

# 4. Run on a physical device
# Connect device via USB, enable Developer Options & USB Debugging
# Click ▶ Run
```

### Permissions Required

MeshLink requires the following permissions (declared in `AndroidManifest.xml`):

```
ACCESS_FINE_LOCATION
ACCESS_WIFI_STATE / CHANGE_WIFI_STATE
BLUETOOTH / BLUETOOTH_ADMIN / BLUETOOTH_SCAN / BLUETOOTH_ADVERTISE / BLUETOOTH_CONNECT
NEARBY_WIFI_DEVICES
```

> ⚠️ All permissions are runtime-requested. Deny any of them and mesh discovery will not function.

---

## 📸 Screenshots

> _Add screenshots here after building the app:_
> 
> | Discovery | Chat | SOS |
> |---|---|---|
> | _screenshot_ | _screenshot_ | _screenshot_ |

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](.github/CONTRIBUTING.md) before submitting a pull request.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "feat: add your feature"`
4. Push the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📄 License

```
MIT License

Copyright (c) 2026 Harsh Baria

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```
