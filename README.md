# 📱 ALauncher: Minimal Smart Launcher

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2+-7F52FF.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline-success.svg)](SECURITY.md)

A premium, distraction-free Android launcher built with Jetpack Compose. 100% local, private, and offline.

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.alauncher.app">
    <img src="https://img.shields.io/badge/Google_Play-Download-00875A?style=for-the-badge&logo=googleplay&logoColor=white" alt="Google Play Store" />
  </a>
</p>

| Minimal Home & OTP Island | Smart Notification Center | Offline Math & Search | Universal Thumb Dock |
| :---: | :---: | :---: | :---: |
| <img src="https://play-lh.googleusercontent.com/EsjfZJCZ4lq5T0oGKMJm6gcgksu3MNuFf7w8Dp0DQw-3EL3B_SG-DzB9di8KeTAPSrf_lN1VIBjiXNtGrwzT_Q=w720" width="180" alt="Minimal Home Screen with OTP Island" /> | <img src="https://play-lh.googleusercontent.com/vA8Te7COX3HPF82EzZA1Oy5Xcq_iXtAF-StAFRIog44xaogYBlnftaYcStkUuIndowyFhbl9Xe5Ve4mfoZmf=w720" width="180" alt="Smart Notification Center" /> | <img src="https://play-lh.googleusercontent.com/vETh__hB6o0NmuY3Wv3dvaW4qvQ6wBxcXnJUuBom4Tmng9BUChcySl-Ptz34HU8bST4h5NfJouQCSTCmc5_rkw=w720" width="180" alt="Offline Math Calculator" /> | <img src="https://play-lh.googleusercontent.com/PouLlm2Jz-HdNn7D2kY860QQ9I6s_fWb0Bq1zc61kbgZPM4qGyhWf4n6o2uY0Ntq9Rs4CtjyfGeTUVzi7Sfx=w720" width="180" alt="Universal Thumb Dock" /> |

---

### 📌 Quick Navigation
* [🛠️ Developer Guide](#️-developer-guide)
* [🤝 Get Involved](#-get-involved)
* [🤝 Contributing Guidelines](CONTRIBUTING.md)
* [🏗️ Architecture Documentation](ARCHITECTURE.md)
* [🛡️ Trademark & Brand Guidelines](TRADEMARK.md)
* [🔒 Security Policy](SECURITY.md)
* [📜 Code of Conduct](CODE_OF_CONDUCT.md)
* [📜 License (GPL-3.0)](LICENSE)
* [🏪 Store Listing Metadata](fastlane/metadata/android/en-US/)

---

## 🛠️ Developer Guide

### ✨ Feature Overview

#### 1. Ultra-Minimalist Home Screen
- Typography-first interface with a clean serif clock and date header.
- Focus Grid: cap favorites to 10 icons, remove text clutter, reduce impulse taps.
- Context menus: swipe right (or left in RTL) on any app to access shortcuts, deep links, or hide.
- Gesture navigation:
  - Swipe UP → Open App Search
  - Swipe DOWN → Open Notification Center
  - Swipe RIGHT on an app → App Shortcuts

#### 2. Dynamic Contextual Island (Real-Time Status)
- Live Tracker widget surfaces the most relevant info exactly when you need it.
- Granular category toggles: OTPs, Critical Alerts, Meetings, Tasks, Rides/Deliveries, Travel, Bill & Transactional Alerts, Ambient States, Ambient Info.
- Bill & Transactional Alerts: debits, credits, refunds, low-balance warnings, EMI alerts, bill due dates.
- Strict Mode vs Live Tracking: delivery/ride apps show only actionable states by default. Enable Live Delivery Tracking to see transit/cooking updates.
- Bluetooth & Peripheral Status: Two-stage connection alerts with live battery percentage and proactive low-battery alerts. Tap to open Bluetooth Settings.
- Hotspot & Background Timers: Active hotspot tracking with 30-minute timeout notification, and live ticking timer countdowns with 1-tap jump to the clock app.
- Completed-state expiration: passive events (Delivered, Trip Completed) auto-dismiss after 5 minutes.
- Smart exclusions: calendar alerts restricted to T-30 to T+5 min, email prefixes (FW:, RE:) filtered.
- Swipe-to-snooze: swipe the island left/right to dismiss until the next unique event.

#### 3. Smart Notification Center & Distraction-Free Filtering
- Distraction-Free Filtering: Everyday notifications are cleared from your phone's top status bar and organized into ALauncher's custom Notification Center for mindful batch review.
- In-Memory Notification Search: Instant, real-time search across active and stashed notifications by keyword, app, or sender.
- Immediate Alerts: Selected Immediate Apps, time-sensitive calendar meetings, and critical OTPs continue to appear in the system tray.
- Ongoing Services: Active phone calls, navigation, and music playback stay in your system tray for quick access.
- Advanced filtering into Priority, Normal, and Muted.
- App Notification Stacks: group multiple notifications into clean, collapsible 3D cards.
- Focus Mode & Quiet Hours with optional meeting/OTP bypass.

#### 4. Command Palette & Global Search
- Trie-based search engine for O(k) lookup — zero lag.
- Instant On-Device Math & Conversions: Evaluate arithmetic expressions (`45 * 12`, `(120 + 35) * 1.18`, `2^10`, `15% of 850`) and common unit conversions (`10 km in miles`, `70 kg in lbs`, `37 c in f`, `16 gb in mb`) 100% offline with zero typing lag.
- Action shortcuts: manage settings or create notification rules from the search bar.

#### 5. Productivity Tools
- Thumb-reachable dock with universal clipboard history, instant notes, and Gemini Assistant.
- Digital Wellbeing: daily app limits and gentle "take a break" reminders.

---

### 🏗️ Architecture & Tech Stack
- **UI:** Jetpack Compose (declarative, modern Android UI).
- **State:** Kotlin Coroutines & Flows for decoupled reactive updates.
- **Storage:** Room SQLite + DataStore Preferences (fully on-device).
- **Search:** Pure on-device Trie engine (`AppTrieSearchIndex`) and math parser (`MathExpressionEvaluator`).
- **Memory Safety:** LeakCanary integration in debug builds; lifecycle-aware flows (`SharingStarted.WhileSubscribed(5000)`).

---

### 🚀 Running Locally & Verification

#### Prerequisites
- JDK 17 or JDK 21.
- Android SDK (API 26 – API 36).
- *(Optional)* Any IDE or text editor of your choice (VS Code, IntelliJ IDEA, Android Studio, Cursor, or pure CLI).

#### Build & Verification Commands
- Build debug APK:
  ```bash
  ./gradlew assembleDebug
  ```
- Install to connected device or running emulator:
  ```bash
  ./gradlew installDebug
  ```
- Run all pre-commit checks (unit tests + Play Console policy audit + Android lint):
  ```bash
  ./gradlew preCommitCheck
  ```
- Run unit tests:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- Install git pre-commit hook:
  ```bash
  ./scripts/install-hooks.sh
  ```

---

### 🔐 Privacy & Safety
- **100% Local:** No `android.permission.INTERNET` declared in the manifest.
- **Play Store Ready:** Compliant with Google Play Data Safety and `QUERY_ALL_PACKAGES` home app exception.
- **Zero Telemetry:** No analytics, crash loggers, or trackers.

---

### 🤝 Get Involved

We welcome contributions! Check out our [Contributing Guide](CONTRIBUTING.md) and look for [`good first issue`](https://github.com/nallupradeepreddy/ALauncher/issues?q=label%3A%22good+first+issue%22) labels to get started.

If ALauncher helps you reclaim your focus, consider giving it a ⭐ on GitHub!
