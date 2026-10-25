# 🏗️ ALauncher Architecture & System Design

ALauncher is designed as an ultra-fast, offline-first, battery-efficient Android launcher built exclusively with **Kotlin** and **Jetpack Compose**. 

This document provides a high-level technical overview of how ALauncher is structured to help contributors navigate the codebase.

---

## 📐 High-Level Architecture Overview

ALauncher follows modern Android Clean Architecture and Unidirectional Data Flow (UDF) patterns:

```
┌─────────────────────────────────────────────────────────────┐
│                       UI Layer                              │
│  Jetpack Compose Screens:                                   │
│  • LauncherHomeScreen (Clock, Focus Grid, Dynamic Island)   │
│  • CommandPaletteScreen (Trie App Search, Math Evaluator)   │
│  • NotificationCenterScreen (Stacked Mindful Alerts)        │
│  • SettingsScreen (Granular Island/Notification Toggles)    │
│                                                             │
│  ViewModels: StateFlow<UiState> + Intent Handlers           │
└──────────────────────────────▲──────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────┐
│                      Domain Layer                           │
│  • AppTrieSearchIndex (O(k) in-memory Trie Search)          │
│  • MathExpressionEvaluator (Offline arithmetic/conversions) │
│  • NotificationFilterEngine & NotificationParser            │
│  • IslandTrustedApps & Classification Rules                 │
└──────────────────────────────▲──────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────┐
│                       Data Layer                            │
│  • AppRepository (Installed apps, icon cache, usage)        │
│  • NotificationRepository (In-memory & SQLite cache)        │
│  • SettingsRepository (DataStore Preferences)               │
│  • CalendarRepository (Local content provider queries)      │
│  • LauncherDatabase (Room SQLite: AppMetadata, Stashes)     │
└──────────────────────────────▲──────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────┐
│                    Platform Services                        │
│  • NotificationListener (Android NotificationListenerService)│
│  • LauncherAccessibilityService (Gestures & Back actions)   │
│  • ThumbDockService (Quick overlay tools)                   │
│  • PackageChangeReceiver (Live package add/remove events)   │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧩 Core Subsystems & Components

### 1. Ultra-Fast Search Engine (`com.alauncher.app.domain`)
* **`AppTrieSearchIndex.kt`**: An in-memory Prefix Trie that provides $O(k)$ lookup speed (where $k$ is query length) regardless of how many hundreds of applications are installed on the device. Supports character normalization and fuzzy matches without blocking the main UI thread.
* **`MathExpressionEvaluator.kt`**: A zero-dependency, recursive-descent math parser and unit converter (`10 km in miles`, `2^8`, `15% of 850`, `37 c in f`) that evaluates expressions instantly as the user types in the search bar.

### 2. Dynamic Contextual Island (`com.alauncher.app.ui.home`)
* **Real-time Event Surface:** Displays live contextual states (active timers, Bluetooth device battery percentages, OTP codes, food deliveries, cab rides, and transaction alerts) directly on the home screen.
* **Strict Expiration & Deduplication:** Passive events auto-dismiss after 5 minutes to prevent stale information. Events can be swiped away to snooze until the next state update.

### 3. Notification Classification & Filtering (`com.alauncher.app.domain.notification`)
* **`NotificationListener.kt`**: Binds to the system `NotificationListenerService` to receive status bar events.
* **`NotificationFilterEngine.kt`**: Evaluates incoming notifications against user-defined rules:
  * **Immediate:** Allowed to stay in system tray (active phone calls, navigation, music, OTPs, time-sensitive meetings).
  * **Batch / Stashed:** Cleared from the status bar and routed into ALauncher's custom Notification Center for mindful batch review.
* **`NotificationParser.kt`**: Extracts actionable text, tracking numbers, OTP codes, and amounts locally without external AI APIs.

### 4. Background Services & Overlays (`com.alauncher.app.service`)
* **`LauncherAccessibilityService.kt`**: Enables global gestures (swipe-down for notification shade, swipe-up for app drawer, predictive back).
* **`ThumbDockService.kt`**: Lightweight overlay dock providing one-hand reachable shortcuts, quick notes, and clipboard access. Uses lifecycle-aware `ComposeView` management to ensure window resources are completely reclaimed upon dismissal.

### 5. Reactive Data Flow & Storage (`com.alauncher.app.data`)
* **Room Database (`LauncherDatabase.kt`)**: Persists app metadata, hide/lock states, and stashed notification archives.
* **DataStore Preferences (`SettingsRepository.kt`)**: Atomic, thread-safe persistence of user toggles and preferences.
* **Flow Subscriptions:** All UI flows use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ...)` to automatically cancel upstream database and sensor observation when the launcher is in the background, minimizing battery drain.

---

## 🔒 Security & Privacy Invariants

Any contribution touching the architecture must uphold these strict invariant rules:
1. **Never make arbitrary HTTP requests.** Core components must remain 100% offline.
2. **Never log personal notification content.** Use `Timber.d` responsibly and ensure sensitive fields (SMS, OTPs, messages) are never outputted to plain logcat in release builds.
3. **No Unbounded Memory Caches:** In-memory notification stacks and icon drawables must implement memory trimming (`onTrimMemory`) and size caps.

