# Architecture

This document describes how OffPay is organised, how its pieces talk to each other, and how to build it from source. It is aimed at contributors and curious readers — not end users. For features and screenshots, see the [README](README.md).

---

## Tech stack

| Concern                        | Choice                                              |
|--------------------------------|-----------------------------------------------------|
| Language                       | Kotlin (100%)                                       |
| UI toolkit                     | Jetpack Compose (Material 3)                        |
| Navigation                     | Navigation Compose (single-activity)                |
| Async                          | Kotlin Coroutines + Flow                            |
| Camera                         | CameraX                                             |
| QR decoding                    | Google ML Kit (on-device, offline)                  |
| Local database                 | Room + SQLCipher                                    |
| Preferences                    | Jetpack DataStore                                   |
| Build system                   | Gradle (Kotlin DSL)                                 |
| Min / target SDK               | 26 / 34                                             |
| JVM toolchain                  | Java 17                                             |
| Tests (unit + property-based)  | Kotest 5                                            |
| Tests (instrumented)           | AndroidX Test, JUnit 4                              |

The app makes **zero network requests at runtime**. Every dependency above runs purely on-device.

---

## Module layout

OffPay is a single Gradle module (`:app`) deliberately. The internal structure is a four-layer separation by package, not by module — the project is small enough that splitting it into multiple modules would slow builds without giving anything back.

```
com.offpay.app
├── domain/         ← Pure Kotlin, no Android dependencies. Testable in isolation.
├── data/           ← Local persistence (Room, DataStore). Wraps Android.
├── platform/       ← Anything that touches the OS: camera, dialer, accessibility,
│                     overlay window, SIM detection.
└── presentation/   ← Compose UI, ViewModels, navigation.
```

The general rule is: **dependencies point inward**. `presentation` and `data` may depend on `domain`. `platform` may depend on `domain`. `domain` depends on nothing app-specific. This keeps the core USSD logic, validation, and parsing testable as ordinary JVM code with no instrumentation.

---

## High-level dataflow

```
┌──────────────────────────────────────────────────────────────────────┐
│  Compose Screens (Pay / Balance / Scan / History / Settings / FAQ)  │
└──────┬─────────────────────────────────────────────────────┬─────────┘
       │ observes StateFlow                                  │ user input
       ▼                                                     ▼
┌──────────────────┐    runs domain logic    ┌────────────────────────┐
│   ViewModels     │ ──────────────────────▶ │  ActionRunner +        │
│ (Pay, Balance,   │                         │  InputValidator +      │
│  History)        │                         │  UpiParser             │
└──────┬───────────┘                         └──────────┬─────────────┘
       │ persists                                       │ talks to
       ▼                                                ▼
┌──────────────────┐                          ┌────────────────────────┐
│ Repositories     │                          │ UssdEngine (platform)  │
│ (History, Prefs) │                          │ ── AccessibilityService│
│ Room + DataStore │                          │ ── OverlayController   │
└──────────────────┘                          │ ── System dialer       │
                                              └────────────────────────┘
```

---

## A payment, end to end

A successful "send money" call moves through these moving parts. Reading the trace top-to-bottom is the fastest way to learn the codebase.

```
USER taps Pay
  │
  ▼
PayScreen (Compose)
  │ collects state from PayViewModel
  │
  ▼
PayViewModel.attemptPayment()
  │ ① validates VPA / amount / PIN via InputValidator
  │ ② chooses MANUAL vs AUTO based on persisted Operation Mode
  │
  ▼
ActionRunner.runAction(SendUpi, vars)
  │ ③ dismisses any leftover dialog
  │ ④ tells UssdEngine to dial *99*1*3#
  │
  ▼
UssdEngine.dial("*99*1*3#")
  │ ⑤ assigns a fresh sessionId and starts inactivity + hard-cap timers
  │ ⑥ fires an ACTION_CALL intent — the OS opens the carrier dialog
  │
  ▼
Carrier dialog opens
  │
  ▼
UssdAccessibilityService receives window events
  │ ⑦ reads the dialog text, classifies it as menu vs terminal
  │ ⑧ filters duplicates and "please wait..." placeholders
  │ ⑨ pushes a UssdFrame into the engine's SharedFlow
  │
  ▼
ActionRunner walks each frame
  │ ⑩ matches the next step's regex (VPA → amount → note → PIN → confirm)
  │ ⑪ asks the engine to type the reply into the dialog and tap "Send"
  │
  ▼
Bank-side carrier emits the success frame ("...is successful... ref no XXX")
  │
  ▼
ActionRunner detects a universal success pattern
  │ ⑫ emits ActionEvent.Done, completes its result
  │
  ▼
PayViewModel hears Done
  │ ⑬ flips SessionState to Success
  │ ⑭ asks HistoryRepository to record the transaction
  │ ⑮ wipes the in-memory PIN within 500 ms
  │
  ▼
PayScreen renders the Success card with its reveal animation
```

The Balance flow is the same shape with a 2-step action and a `*99*3#` code.

---

## The five things you should look at first

If you want to understand or change OffPay quickly, these are the entry points by importance.

| File                                          | What it does                                                                                  |
|-----------------------------------------------|-----------------------------------------------------------------------------------------------|
| `domain/ActionRunner.kt`                      | The state machine that walks a scripted USSD flow. Frame-by-frame matching + reply.            |
| `domain/Actions.kt`                           | The two scripted flows: SendUpi (6 steps) and CheckBalance (2 steps), plus failure regex.      |
| `platform/UssdEngine.kt`                      | Owns the session lifecycle: dial, timers, frame stream, cancel, dismiss.                       |
| `platform/UssdAccessibilityService.kt`        | Reads the system USSD dialog via the accessibility framework, fills text fields, taps Send.    |
| `presentation/PayViewModel.kt`                | Wires the UI to the runner. Validates input. Handles success/failure UI state and PIN wiping.  |

---

## Layer responsibilities

### `domain/`
Pure Kotlin. No `android.*` imports allowed. Houses:

- The USSD action engine (`ActionRunner`) and the action definitions (`Actions`).
- Frame deduplication and placeholder filtering (`FrameFilter`).
- UPI URI parsing (`UpiParser`).
- Form validation (`InputValidator`, `Validation`).
- PIN masking helpers (`PinMasking`).
- Domain models: `UssdFrame`, `Action`, `ActionStep`, `ActionEvent`, `SessionState`, `OperationMode`, `UpiData`, `SimInfo`, `FormField`, `ValidationResult`.

Anything that needs to be unit-tested without an emulator lives here.

### `data/`
Local persistence:

- `AppDatabase` — the Room database, opened with a SQLCipher passphrase at startup.
- `TransactionEntity` + `TransactionDao` — schema for the history table, with a 200-row cap maintained by a "trim oldest" query.
- `HistoryRepository` — thin wrapper over the DAO that exposes `Flow<List<TransactionEntity>>`.
- `PreferencesRepository` — DataStore-backed key-value store for operation mode, last-balance cache, onboarding flag, and battery-warning dismissal.

### `platform/`
The bridge between the pure domain and Android:

- `UssdEngine` (implements `UssdEnginePort`) — the session coordinator.
- `UssdAccessibilityService` — the system-level service that watches the carrier dialog package set and emits frames. Restricted via an `accessibility_service_config.xml` that explicitly enumerates supported dialog packages.
- `OverlayController` (interface) + `OverlayControllerImpl` — the system-overlay window for Auto mode. (A floating progress chip variant for the legacy Advanced mode also exists in code but is no longer surfaced in the UI.)
- `CarrierDetector` — reads the active SIM's carrier name and applies the Jio fail-fast rule.
- `QrScannerManager` — CameraX preview + ML Kit barcode binding, plus a gallery decode helper.
- `ApkShareUtil` — utility that exports the installed APK so users can share OffPay over Bluetooth or WhatsApp without a Play Store link.

### `presentation/`
Everything Compose. Subdivided:

- `presentation/` (top level) — three ViewModels (`PayViewModel`, `BalanceViewModel`, `HistoryViewModel`) and the single `MainActivity`.
- `presentation/navigation/` — `Screen` route enum and `MainScaffold` (the bottom nav + NavHost).
- `presentation/screens/` — one Composable per top-level destination plus a small onboarding subpackage.
- `presentation/permissions/` — runtime permission state + launchers (`PermissionState.kt`).
- `presentation/ui/components/` — reusable widgets (cards, buttons, PIN boxes, snackbar, money-rain easter egg, etc.).
- `presentation/ui/theme/` — colour palette, typography, shapes, theme wrapper.

The single-activity choice is intentional: a multi-activity setup races against the system USSD dialog window in unpredictable ways. With one activity, Navigation Compose handles all routing without any new windows being created mid-session.

---

## Permissions

| Permission                       | Why                                                                                              | Required?                                |
|----------------------------------|--------------------------------------------------------------------------------------------------|------------------------------------------|
| `CALL_PHONE`                     | Dial the `*99#` USSD code over the SIM voice channel.                                            | Yes, always.                             |
| `CAMERA`                         | Live QR scanner.                                                                                 | Only if the user opens Scan.             |
| `READ_PHONE_STATE`               | Read the active SIM's carrier name to apply the Jio fail-fast rule.                              | Recommended.                             |
| Accessibility Service            | Read the carrier USSD dialog and answer prompts. Restricted by config to known dialog packages.  | Required for **Auto** mode.              |
| `SYSTEM_ALERT_WINDOW`            | Paint the OffPay UI over the carrier dialog in **Auto** mode.                                    | Required for **Auto** mode.              |

If the user denies the optional permissions, **Manual** mode still works on any Android device. Nothing here exfiltrates data — the accessibility service is hard-restricted via `accessibility_service_config.xml` to the known dialer/USSD packages and ignores every other window.

---

## Compatibility and device support

- Min SDK 26 (Android 8.0). Tested on stock Android, OneUI, MIUI, ColorOS.
- Phone with a working voice SIM. Wi-Fi-only tablets cannot use `*99#`.
- **Carriers**: Airtel, Vi, BSNL — work. **Jio** — does not, by network design. The app detects Jio on launch and refuses to dial.
- The accessibility service is sometimes killed by aggressive battery optimisation on Samsung, Xiaomi, OnePlus, and Oppo devices. The app detects this on launch and prompts the user to whitelist OffPay if needed.

---

## Operation modes

The runtime behaviour during a session is selected by `OperationMode`, persisted in DataStore. The codebase ships **two user-selectable modes** today: `AUTO` (default) and `MANUAL`. A legacy `ADVANCED` enum value still exists in `OperationMode.kt` so older persisted preferences don't crash on read — at runtime it is treated identically to `AUTO`. The Settings UI only exposes Auto and Manual.

| Mode       | Auto-fills the dialog? | Hides the carrier dialog?   | Permissions beyond `CALL_PHONE`             |
|------------|------------------------|-----------------------------|---------------------------------------------|
| `AUTO`     | Yes                    | Yes (full overlay)          | Accessibility + `SYSTEM_ALERT_WINDOW`       |
| `ADVANCED` | Yes (legacy, runtime-aliased to AUTO) | n/a            | Same as AUTO                                |
| `MANUAL`   | No                     | n/a                         | None                                        |

`MANUAL` is the universal fallback — it copies the UPI ID to the clipboard, opens the system dialer with `*99*1*3#` already typed, and the user takes over from there. It does not require the accessibility service to be enabled and works on any Android device.

---

## Web PWA

A small companion PWA lives at **[offpay.vercel.app](https://offpay.vercel.app/)** for iOS users and anyone who can't install the APK. It implements the Manual-mode flow only — VPA + amount form, copy-to-clipboard, deep-link into the device dialer with `*99*1*3#` prefilled. The PWA is a separate codebase, not part of this Android repo.

---

## Session lifecycle and safety nets

A session is anything from `dial("*99...")` to a terminal event. The engine layers in three independent guards so a hung carrier never leaves the user stuck:

1. **Session ID guard.** Every `dial()` increments a monotonically growing integer. Any frame received with an older session ID is discarded as a leftover from a previous run.
2. **Inactivity timer (12 s).** Resets on every received frame and every sent reply. If nothing happens for 12 seconds, the session is force-cancelled with a "carrier unresponsive" error.
3. **Hard timeout.** 25 seconds for send-money, 18 seconds for balance check. Caps the absolute lifetime of a session regardless of activity.

In addition, `dial()` is double-tap-protected with a 2-second cooldown so an over-eager user cannot start two overlapping sessions.

When any terminal event fires (success, failure, user-cancel, timeout) the runner flips an internal `terminated` flag so any straggling frame from the carrier window is dropped instead of overwriting a confirmed result. The same guard exists in the ViewModels as a belt-and-braces safety net.

---

## PIN handling

The UPI PIN never leaves volatile process memory. Specifically:

- It lives in the relevant ViewModel's UI state (`PayUiState.pin` / `BalanceUiState.pin`).
- It is masked to `••••` (fixed length) anywhere it might be displayed or passed to the overlay layer.
- It is wiped within 500 ms of any session-ending event — success, failure, timeout, or cancellation — and on app backgrounding.
- It is **never** written to Room, DataStore, the system clipboard, log statements, or any analytics surface (there are no analytics).

The regex used to detect the PIN-entry frame deliberately uses word boundaries (`\bPIN\b`) so it cannot false-match a frame that merely contains "PIN" as a substring of an unrelated word.

---

## Carrier frame processing

Frames coming out of `UssdAccessibilityService` are noisy. Two filters tame them:

1. **Placeholder suppression.** Frames consisting only of phrases like `"please wait"`, `"processing..."`, `"USSD code running"`, `"connecting"`, or `"loading"` are dropped.
2. **Consecutive deduplication.** A frame whose trimmed text is identical to the previously emitted frame is dropped. This collapses the typical "frame fires three times for one redraw" pattern.

The runner then classifies each surviving frame in priority order:

1. **Universal success** — text matches one of the patterns in `ActionRunner.UNIVERSAL_SUCCESS_PATTERNS`. This catches "...is successful... ref no XXXXXX" even when the carrier appends a follow-up menu.
2. **Action-specific failure** — text matches one of the action's `failurePatterns` (wrong PIN, invalid VPA, insufficient balance, service unavailable, sender = receiver, etc.).
3. **Step match** — text matches the next expected step's regex; the templated reply (e.g. `{vpa}`, `{pin}`) is sent.
4. **Unmatched terminal** — the frame is terminal (only a Close button) but matched nothing above. The runner treats it as a failure and surfaces the carrier's verbatim text so the user always sees something honest.

---

## Testing

OffPay leans heavily on property-based testing because the surface area that matters most (regex matching, frame classification, validation) is exactly the kind of thing where hand-written examples miss edge cases.

```
app/src/
├── test/                     ← JVM unit tests (Kotest 5, property-based where useful)
└── androidTest/              ← Instrumented tests on a device or emulator
```

Notable suites:

| Suite                                    | What it pins down                                                          |
|------------------------------------------|----------------------------------------------------------------------------|
| `ActionRunnerSuccessPropertyTest`        | Every shape of success text the carrier might emit is detected.            |
| `ActionRunnerFailurePropertyTest`        | The failure regex never accidentally classifies a legitimate prompt.       |
| `ActionRunnerStepMatchPropertyTest`      | The step regex matches the right prompts in the right order.               |
| `ActionRunnerTerminalPropertyTest`       | An unmatched terminal frame always becomes a failure, never silent.        |
| `FrameFilterPropertyTest`                | Placeholder suppression and dedup are stable under arbitrary inputs.       |
| `InputValidatorPropertyTest`             | Composite form validation reports per-field errors correctly.              |
| `UpiParserPropertyTest`                  | Round-trips arbitrary `upi://pay?` URIs through parse → equivalent output. |
| `UpiParserInvalidInputPropertyTest`      | Garbage input is always rejected, never accepted.                          |
| `PinClearedPropertyTest`                 | The PIN is always wiped within the contracted window, on every exit path.  |
| `PinMaskingPropertyTest`                 | The PIN never leaks through `maskReply()`.                                 |
| `PinRegexPropertyTest`                   | The PIN-prompt regex never false-matches non-PIN frames.                   |
| `QrAutofillPropertyTest`                 | Every non-null field in a parsed QR ends up in the form.                   |
| `ErrorClearingPropertyTest`              | Editing a flagged field clears its error and only its error.               |
| `StaleFrameFilteringPropertyTest`        | Frames from a stale session ID are always discarded.                       |
| `SessionIdMonotonicityPropertyTest`      | Session IDs never go backwards across `dial()` calls.                      |
| `CarrierDetectorPropertyTest`            | Jio/Reliance is detected; "not registered" patterns route correctly.       |
| `TransactionOrderingPropertyTest`        | History stays in reverse-chronological order under arbitrary inserts.      |
| `TransactionCapPropertyTest`             | The 200-row cap holds and trims the oldest first.                          |
| `TransactionPersistencePropertyTest`     | Records survive close/reopen of the encrypted DB.                          |
| `OperationModePropertyTest`              | Mode persistence + legacy enum migration is correct.                       |

Instrumented tests cover the encrypted database (`RoomSqlCipherIntegrationTest`), the QR decoder against real images (`QrDecodingIntegrationTest`), and DataStore (`DataStoreIntegrationTest`).

---

## Build instructions

### Prerequisites

- **JDK 17** (Eclipse Temurin recommended).
- **Android SDK** with platform 34 + build-tools 34.x. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) accordingly.
- A real Android device (API 26+) or an emulator. The emulator cannot place real `*99#` calls, but the rest of the app — UI, parsing, validation, history, scanning gallery images — works fine on it.

### Setup

```bash
git clone https://github.com/<your-org>/OffPayApp.git
cd OffPayApp
```

Create a `local.properties` file at the repo root and point it at your Android SDK:

```properties
sdk.dir=/absolute/path/to/your/Android/Sdk
```

### Common Gradle commands

> The repo currently ships only the Unix `gradlew`. On Windows, use the bundled wrapper from Android Studio's terminal, or run `gradle wrapper --gradle-version 9.4.1` once to generate `gradlew.bat`.

```bash
# Build a debug APK
./gradlew :app:assembleDebug

# Run unit + property tests
./gradlew :app:test

# Run instrumented tests on a connected device or emulator
./gradlew :app:connectedAndroidTest

# Lint
./gradlew :app:lint

# Clean
./gradlew clean
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Android Studio
Open the repo root in Android Studio (Ladybug or newer). Sync Gradle and use the **app** run configuration. The IDE picks up the JDK 17 toolchain automatically from `build.gradle.kts`.

---

## Project structure (full)

```
OffPayApp/
├── app/
│   ├── build.gradle.kts            ← module-level Gradle config
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/offpay/app/
│       │   │   ├── OffPayApplication.kt
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   ├── platform/
│       │   │   └── presentation/
│       │   └── res/
│       │       ├── drawable/        ← logo, onboarding screenshots
│       │       ├── mipmap-*/        ← launcher icons
│       │       ├── values/          ← strings.xml, themes.xml
│       │       └── xml/             ← accessibility_service_config.xml, file_paths.xml
│       ├── test/                    ← JVM unit + property tests
│       └── androidTest/             ← instrumented tests
├── gradle/wrapper/
├── build.gradle.kts                 ← root Gradle config
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── ARCHITECTURE.md                  ← this file
├── CONTRIBUTING.md
└── LICENSE
```

---

## Design decisions worth knowing

### Why a single Gradle module?
The codebase is small (~30 source files in `main`). A multi-module split would add Gradle overhead without any concrete benefit — the package boundaries already enforce the layer rules, and pure-domain tests run fast as plain JVM tests.

### Why manual DI in `OffPayApplication`?
There is exactly one graph of singletons (database, repositories, engine, runner) and they are wired once at startup. Hilt or Koin would add a runtime dependency and configuration burden that pays off only at much larger scale.

### Why SQLCipher and not just file-system encryption?
Transaction history can include the carrier's raw confirmation text, which sometimes contains the recipient's masked phone number or a portion of the VPA. SQLCipher gives at-rest confidentiality even if the user backs up the app's data directory.

### Why Kotest over JUnit 5?
Property-based testing. The USSD regex surface is a near-perfect fit for `checkAll` generators — hand-written examples consistently miss bugs that property tests catch.

### Why no analytics?
A payment app that calls home contradicts its own offline-first promise. The app makes no outbound network requests after install and depends on no third-party SaaS at runtime.
