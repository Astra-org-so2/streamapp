# StreamApp — Ultra Low-Latency Cloud & Game Streaming Client for Android

**StreamApp** is an enterprise-grade, modern Android application engineered for high-performance, ultra-low-latency game and desktop application streaming. Built using a Clean Architecture + MVI/MVVM hybrid paradigm with 100% Jetpack Compose and Material 3 design system.

---

## 🌟 Key Features

- **Decoupled Real-Time Streaming Architecture:** Universal `StreamingClient` abstraction supporting hardware-accelerated video decoding (AV1, HEVC, H.264, VP9) and zero-latency audio routing.
- **Unified Normalized Input System:** 
  - On-screen customizable Virtual Gamepad (dual analog thumbsticks, D-Pad, ABXY, analog triggers) with radial deadzone filtering.
  - Full Android HID physical gamepad mapping (Xbox, DualSense, DualShock, Switch Pro).
  - Virtual Keyboard Bridge for remote text input and key shortcuts.
  - Multi-touch and trackpad gestures.
- **Adaptive Quality Engine (QoE):** Telemetry-driven adaptation with strict hysteresis and debouncing (filters out transient packet loss spikes, protects against quality flapping).
- **Resilient Connection State Machine:** Automatic ICE restart, exponential backoff reconnect flow with retry countdown, and seamless network handover (Wi-Fi $\leftrightarrow$ 5G).
- **Server Management & Catalog:** Add, edit, test latency, and manage multiple remote streaming hosts with Room database persistence.
- **Dark-First Gaming Aesthetic:** Obsidian & Slate surfaces with subtle cyan glows, responsive adaptive layouts for phones (portrait/landscape), tablets, and foldables.
- **Multi-language Support:** Externalized string resources with English and Russian localizations.

---

## 🏗️ Architecture Stack

- **UI Framework:** Jetpack Compose (BOM 2024.09.00) + Material 3 + WindowSizeClass
- **Architecture:** Clean Architecture + MVI/MVVM Hybrid (Single Source of Truth, Unidirectional Data Flow)
- **Dependency Injection:** Google Hilt 2.51+
- **Local Persistence:** Room Database 2.6+ & Jetpack DataStore Preferences
- **Security:** Android Keystore & EncryptedSharedPreferences (Zero plain-text credentials or secrets)
- **Networking:** Retrofit 2.11+, OkHttp 4.12+, Kotlinx Serialization JSON
- **Image Pipeline:** Coil Compose 2.7+
- **Build System:** Gradle 8.14, Version Catalogs (`libs.versions.toml`), Android SDK 35 (minSdk 26)

---

## 🚀 Getting Started

### Prerequisites
- JDK 17+
- Android SDK 35 (Build tools 35.0.0)

### Build and Run
```bash
# Clone and enter project directory
cd streamapp

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```
