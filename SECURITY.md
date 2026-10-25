# 🔒 Security Policy

The security and privacy of our users is of paramount importance. Because ALauncher functions as an Android home screen launcher and interacts with sensitive platform capabilities (such as the Android Notification Listener, Accessibility Services, and overlay windows), we treat all potential security reports with the highest priority.

---

## Supported Versions

Only the latest release version on the `main` branch and the current production build on Google Play receive active security updates.

| Version | Supported |
| :--- | :--- |
| Latest release on `main` | ✅ Yes |
| Production Play Store build | ✅ Yes |
| Older releases / forks | ❌ No |

---

## Core Privacy & Security Guarantees

ALauncher is engineered around strict privacy-first principles:
1. **100% On-Device & Offline:** Notification parsing, app search indices, calendar queries, and Room database storage operate entirely on your physical device.
2. **Zero Telemetry & Zero Trackers:** ALauncher contains no third-party tracking SDKs, ad networks, or background telemetry.
3. **No Unsolicited Network Operations:** Core launcher services never transmit your notification content, contacts, calendar entries, or clipboard contents over the network.
4. **Isolated AI Capabilities:** Any optional generative AI features (e.g., Gemini) require explicit user opt-in and utilize user-configured API keys.

---

## Reporting a Vulnerability

If you discover a security vulnerability or privacy flaw within ALauncher:

> **⚠️ DO NOT create a public GitHub Issue for security vulnerabilities.**

Please report all security concerns responsibly:
1. **Email:** Send full technical details, proof-of-concept steps, and affected device details to:
   **`pradeep.alauncher@gmail.com`**
2. **Details to Include:**
   * Detailed description of the vulnerability.
   * Reproduction steps or a minimal test case / ADB command.
   * Potential impact (e.g., unauthorized data exposure, overlay hijacking, privilege escalation).
   * Device model, Android OS version, and launcher version tested.
3. **Acknowledgment & Resolution Window:**
   * We will acknowledge receipt of your vulnerability report within **48 hours**.
   * We will work directly with you to investigate, validate, and develop a patch.
   * We kindly request a standard **90-day responsible disclosure period** before public disclosure, allowing sufficient time to verify the fix and distribute an update to active users.

Thank you for helping keep ALauncher safe and private for everyone!

