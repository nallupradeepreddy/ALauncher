## 📝 Description
<!-- Provide a clear, concise summary of your changes and why they are necessary. -->

Fixes # <!-- Add linked issue number here, e.g. Fixes #42 -->

---

## 🔍 Type of Change
<!-- Please check the options that apply: -->
- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] ⚡ Performance optimization (latency, battery, or memory improvement)
- [ ] ♻️ Code refactoring (no functional change)
- [ ] 📝 Documentation update
- [ ] 🧪 Tests (adding or updating tests)

---

## 📱 Visual Proof (Screenshots / Screen Recordings)
<!-- If this PR alters any UI, Jetpack Compose layouts, or themes, please attach before/after screenshots or video proof. -->
| Before | After |
| :---: | :---: |
| *(Image / Video)* | *(Image / Video)* |

---

## 🔒 Security & Offline Invariants
- [ ] **100% Offline:** No unexpected network calls or tracking SDKs have been added.
- [ ] **Privacy Respect:** No sensitive notification contents or OTPs are logged or exposed.
- [ ] **Battery & Performance:** No unthrottled background polling or memory leaks introduced.

---

## ✅ Quality Checklist
- [ ] My code adheres to the project's [Code Style](CONTRIBUTING.md#code-style--conventions).
- [ ] I have performed a self-review of my own code.
- [ ] I have added or updated unit tests covering my changes.
- [ ] All unit tests pass locally: `./gradlew testDebugUnitTest`
- [ ] Android Lint passes with 0 errors: `./gradlew lintDebug`
- [ ] Database schema migrations and tests are included (if Room entities were touched).

