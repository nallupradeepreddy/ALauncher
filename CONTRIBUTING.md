# 🤝 Contributing to ALauncher

Thank you for your interest in contributing to **ALauncher**! 

ALauncher is a minimal, distraction-free Android launcher built with modern Jetpack Compose. We believe in simplicity, speed, privacy, and battery efficiency.

Before contributing, please read our [Code of Conduct](CODE_OF_CONDUCT.md) and review the guidelines below.

---

## 🧭 Core Philosophy & Guiding Principles

Every pull request is evaluated against these four core principles:

1. **Distraction-Free Minimalism:** If a feature adds visual clutter, unsolicited animations, or unnecessary complexity, it probably does not belong in ALauncher.
2. **100% Offline & Private:** ALauncher never transmits user data, notifications, or keystrokes to remote servers. No tracking, no analytics, no ad libraries.
3. **Instant Responsiveness (Zero Lag):** App launches, trie searches, and gestures must feel instantaneous. Heavy calculations must run off the main thread.
4. **Battery & Resource Respect:** No unthrottled background polling, memory leaks, or unnecessary recompositions.

---

## 🛠️ Setting Up Your Development Environment

### Prerequisites
* **JDK:** Java 17 or Java 21.
* **Android SDK:** API 26 (Min SDK) to API 36 (Compile/Target SDK).
* **Git:** Installed and configured.
* **Editor:** Any editor of your choice (VS Code, IntelliJ IDEA, Android Studio, Cursor, or pure CLI).

### Quick Start
1. **Fork & Clone:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/ALauncher.git
   cd ALauncher
   ```
2. **Install Local Pre-Commit Hooks:**
   We provide a pre-commit hook that automatically runs unit tests, Play Console audit, and Android Lint before each commit:
   ```bash
   chmod +x scripts/install-hooks.sh
   ./scripts/install-hooks.sh
   ```
3. **Build & Run:**
   * Build the debug APK directly via Gradle:
     ```bash
     ./gradlew assembleDebug
     ```
   * Install directly on your connected device or running emulator:
     ```bash
     ./gradlew installDebug
     ```
   * *(Optional)* If using an IDE (VS Code, IntelliJ IDEA, Android Studio, Cursor), open the root `ALauncher` folder and allow the project to sync dependencies.

---

## 🧪 Testing Guidelines

We take automated testing seriously. Every pull request must pass all tests before being merged.

* **Run Unit Tests:**
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Run Android Lint:**
  ```bash
  ./gradlew lintDebug
  ```
* **Generate Code Coverage Report:**
  ```bash
  ./gradlew jacocoTestReport
  # View results at: app/build/reports/jacoco/jacocoTestReport/html/index.html
  ```

### Writing Tests
* **New features must include unit tests.** Place tests in `app/src/test/java/com/alauncher/app/`.
* Test state changes in ViewModels using Kotlin coroutines `runTest` and `Turbine` or StateFlow assertions.
* Ensure Room database queries and schema changes have corresponding test coverage.

---

## 🎨 Code Style & Conventions

* **Language:** Kotlin 2.2+ idiomatic code.
* **Architecture:** Unidirectional Data Flow (UDF). ViewModels expose immutable `StateFlow<UiState>` collected with `collectAsStateWithLifecycle()`.
* **Jetpack Compose:**
  * Avoid heavy computations in `@Composable` functions without `remember`.
  * Pass stable lambdas/keys in LazyLists (`items(items, key = { it.packageName })`).
  * Never mix business logic into UI components.
* **Coroutines:** Always dispatch background work to `Dispatchers.IO` or `Dispatchers.Default`. Never block `Dispatchers.Main`.
* **Database Migrations:** If modifying Room entities in `Entities.kt`, you **must** increment the database version in `LauncherDatabase.kt`, provide an explicit `Migration`, and generate/verify the schema JSON under `app/schemas/`.

---

## 💬 Commit Message Format

We adhere to the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <short description in imperative mood>

[optional body]

[optional issue reference]
```

### Types:
* `feat`: A new feature or user-facing enhancement.
* `fix`: A bug fix.
* `perf`: A code change that improves performance or memory usage.
* `refactor`: Code change that neither fixes a bug nor adds a feature.
* `test`: Adding or updating tests.
* `docs`: Documentation changes only.
* `chore`: Build scripts, dependencies, or tool configuration.

### Examples:
* `feat(search): add exponential syntax support to MathExpressionEvaluator`
* `fix(island): dismiss completed ride status after 5-minute timeout`
* `perf(trie): optimize unicode character normalization in app search`

---

## 🚀 Pull Request (PR) Workflow

1. **Open an Issue First:** For major features or architectural changes, please open an issue to discuss your proposal before investing time into coding. Small bug fixes or typo corrections do not require a prior issue.
2. **Create a Dedicated Branch:**
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. **Keep PRs Focused:** Submit small, self-contained PRs. Avoid bundling multiple unrelated fixes or refactors into one pull request.
4. **Include Visual Proof:** If your PR alters any Compose UI or theme, attach before/after screenshots or screen recordings to the PR description.
5. **Verify Locally:** Ensure `./gradlew testDebugUnitTest` and `./gradlew lintDebug` pass with 0 errors.
6. **Submit PR:** Fill out the [Pull Request Template](.github/pull_request_template.md) completely.

---

## 🏷️ Looking for Where to Start?

Check out our issue tracker for:
* [`good first issue`](https://github.com/nallupradeepreddy/ALauncher/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) — beginner-friendly tasks (e.g., adding unit conversion metrics, localized string translations).
* [`help wanted`](https://github.com/nallupradeepreddy/ALauncher/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22) — planned features seeking community implementation.

We appreciate all your contributions and are excited to build a cleaner, faster Android experience together!

