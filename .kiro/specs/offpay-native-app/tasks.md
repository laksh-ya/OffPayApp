# Implementation Plan: OffPay Native Android App

## Overview

This plan implements the OffPay native Android app from project scaffolding through full integration. Tasks are ordered to build foundational layers first (project structure, data models, pure domain logic), then platform services (AccessibilityService, Overlay, Camera), then UI/ViewModel wiring, and finally integration testing. Each task builds on the previous ones with no orphaned code.

## Tasks

- [x] 1. Project scaffolding and core data models
  - [x] 1.1 Create Android project structure with Gradle dependencies
    - Initialize Kotlin Android project with single-activity architecture
    - Configure build.gradle with dependencies: Jetpack Compose (Material 3), Navigation Compose, CameraX, ML Kit Barcode, Room, SQLCipher, DataStore, Coroutines, Kotest
    - Set up package structure: `presentation`, `domain`, `data`, `platform`
    - Configure dark theme colors and Material 3 theme in `ui/theme/`
    - _Requirements: 13.1, 14.1_

  - [x] 1.2 Define core domain data models and enums
    - Create `UssdFrame`, `Action`, `ActionStep`, `ActionEvent`, `ActionResult` data classes
    - Create `OperationMode` enum (DIALER, ADVANCED, OVERLAY)
    - Create `SessionState` sealed class (Idle, Running, Success, Failed)
    - Create `UpiData`, `ValidationResult`, `FormValidationResult`, `FormField` types
    - Create `SimInfo` data class
    - _Requirements: 2.1, 3.1, 4.1_

  - [x] 1.3 Define pre-built Actions (SendUpi, CheckBalance) with step regexes and failure patterns
    - Create `Actions` object with `SendUpi` action (code `*99*1*3#`, 6 steps, 25s timeout)
    - Create `CheckBalance` action (code `*99*3#`, 2 steps, 18s timeout)
    - Define `COMMON_FAILURES` regex list for shared failure patterns
    - _Requirements: 2.2, 2.3, 2.4, 3.2, 3.3, 3.4_

- [x] 2. Pure domain logic — UPI parsing and input validation
  - [x] 2.1 Implement UpiParser object (parse, isValidVpa, extractVpaFromText)
    - Parse `upi://pay?` URIs extracting pa, pn, am, tn parameters
    - Validate VPA format with regex `[a-zA-Z0-9.\-_]{3,}@[a-zA-Z0-9.\-_]{3,}`
    - Extract VPA from arbitrary text strings
    - Return null for invalid/non-UPI input
    - _Requirements: 1.2, 1.4, 1.6_

  - [x] 2.2 Write property test for UPI URI parsing round-trip
    - **Property 1: UPI URI Parsing Round-Trip**
    - **Validates: Requirements 1.2**

  - [x] 2.3 Write property test for invalid input rejection
    - **Property 2: Invalid Input Rejection**
    - **Validates: Requirements 1.4**

  - [x] 2.4 Implement InputValidator object (validateVpa, validateAmount, validatePin, validatePaymentForm)
    - VPA: non-empty, trimmed, matches `[a-zA-Z0-9._-]+@[a-zA-Z0-9]+`, max 50 chars
    - Amount: 0.01–5000, max 2 decimal places
    - PIN: 4–6 digits only
    - Composite validation returns errors for all failing fields simultaneously
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.6_

  - [x] 2.5 Write property test for input validation composite correctness
    - **Property 12: Input Validation Composite Correctness**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**

- [x] 3. Pure domain logic — ActionRunner and frame processing
  - [x] 3.1 Implement ActionRunner (runAction, matchStep, fillTemplate, matchesUniversalSuccess, matchesFailurePattern)
    - Step matching walks ordered step list, replies with templated values
    - Universal success detection for terminal frames
    - Failure pattern matching with immediate bail-out
    - Unmatched terminal frame → failure with carrier text
    - Per-step pacing delay (250ms default)
    - Service-alive check before each reply
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.7, 2.8, 3.2, 3.3, 3.4_

  - [x] 3.2 Write property test for action step matching correctness
    - **Property 4: Action Step Matching Correctness**
    - **Validates: Requirements 2.2, 3.2, 3.3**

  - [x] 3.3 Write property test for universal success detection
    - **Property 5: Universal Success Detection**
    - **Validates: Requirements 2.3**

  - [x] 3.4 Write property test for failure pattern detection
    - **Property 6: Failure Pattern Detection**
    - **Validates: Requirements 2.4, 3.4**

  - [x] 3.5 Write property test for unmatched terminal frame causes failure
    - **Property 7: Unmatched Terminal Frame Causes Failure**
    - **Validates: Requirements 2.5, 6.4**

  - [x] 3.6 Implement frame deduplication and filler suppression logic
    - Deduplicate consecutive frames with identical joined text
    - Suppress frames matching system placeholder patterns (please wait, processing, loading, connecting, ussd code running)
    - _Requirements: 6.2, 6.3_

  - [x] 3.7 Write property test for frame deduplication and filler suppression
    - **Property 11: Frame Deduplication and Filler Suppression**
    - **Validates: Requirements 6.2, 6.3**

  - [x] 3.8 Implement PIN masking utility (maskReply function)
    - Return "••••" when reply value equals the PIN
    - Never return a string containing original PIN digits
    - _Requirements: 9.3_

  - [x] 3.9 Write property test for PIN masking correctness
    - **Property 15: PIN Masking Correctness**
    - **Validates: Requirements 9.3**

  - [x] 3.10 Write property test for PIN regex no false positives
    - **Property 16: PIN Regex No False Positives**
    - **Validates: Requirements 9.5**

- [x] 4. Checkpoint — Core domain logic
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Data layer — Room database and DataStore
  - [x] 5.1 Implement Room database with SQLCipher encryption
    - Create `TransactionEntity` with Room annotations
    - Create `TransactionDao` with getAll (Flow), insert, trimOldest, deleteAll, count
    - Configure `AppDatabase` with SQLCipher `SupportFactory`
    - _Requirements: 11.1, 11.5_

  - [x] 5.2 Implement HistoryRepository with 200-record cap enforcement
    - Insert triggers trimOldest when count exceeds 200
    - Expose Flow<List<TransactionRecord>> for UI consumption
    - _Requirements: 11.1, 11.2, 11.3_

  - [x] 5.3 Write property test for transaction persistence round-trip
    - **Property 18: Transaction Persistence Round-Trip**
    - **Validates: Requirements 11.1**

  - [x] 5.4 Write property test for transaction history cap invariant
    - **Property 19: Transaction History Cap Invariant**
    - **Validates: Requirements 11.2**

  - [x] 5.5 Write property test for transaction history ordering
    - **Property 20: Transaction History Ordering**
    - **Validates: Requirements 11.3**

  - [x] 5.6 Implement PreferencesRepository with DataStore
    - Store/retrieve OperationMode, battery warning dismissed, first launch complete
    - _Requirements: 4.5, 14.3_

  - [x] 5.7 Write property test for operation mode persistence round-trip
    - **Property 8: Operation Mode Persistence Round-Trip**
    - **Validates: Requirements 4.5**

- [x] 6. Platform layer — UssdEngine and AccessibilityService
  - [x] 6.1 Implement UssdAccessibilityService
    - Watch USSD dialog packages (com.android.phone, samsung, google dialer, etc.)
    - Extract visible text, classify frames as menu/terminal
    - Implement sendReply (set text + click Send), dismissDialog, resetForNewSession
    - Filter system placeholder text (isSystemPlaceholder)
    - Deduplicate frames by comparing joined text to lastEmittedText
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 6.2 Implement UssdEngine (session coordinator)
    - Manage sessionId (monotonically increasing), sessionActive flag
    - Dial via ACTION_CALL intent with sessionId increment
    - Emit frames via MutableSharedFlow with stale-frame filtering
    - Implement Slow_Watch (12s inactivity timer) and Hard_Timeout (25s/18s)
    - Double-tap protection (2s cooldown between dial calls)
    - Dismiss leftover dialogs and reset state before each new session
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [x] 6.3 Write property test for session ID monotonicity
    - **Property 9: Session ID Monotonicity**
    - **Validates: Requirements 5.1**

  - [x] 6.4 Write property test for stale frame filtering
    - **Property 10: Stale Frame Filtering**
    - **Validates: Requirements 5.2**

  - [x] 6.5 Implement CarrierDetector (getActiveSimInfo, isUnsupportedCarrier, isNotRegisteredError)
    - Read active SIM info via SubscriptionManager/TelephonyManager
    - Match Jio pattern `/jio|reliance/i` for unsupported carrier detection
    - Match "not registered" patterns for onboarding routing
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [x] 6.6 Write property test for carrier detection correctness
    - **Property 17: Carrier Detection Correctness**
    - **Validates: Requirements 10.3, 10.4**

- [x] 7. Platform layer — Overlay and Camera
  - [x] 7.1 Implement OverlayController (TYPE_APPLICATION_OVERLAY window management)
    - Show/update/hide overlay with FLAG_NOT_FOCUSABLE
    - In-place text updates (title, subtitle, step label) within 100ms
    - Error display with 1200ms hold before hide
    - Cancel button with onCancel callback
    - Touch pass-through for non-interactive areas
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

  - [x] 7.2 Implement QrScannerManager (CameraX + ML Kit barcode scanning)
    - Bind camera preview to lifecycle with rear-facing camera
    - Configure ML Kit BarcodeScanning for QR format
    - Implement zoom ratio control (1.0x to 3.0x)
    - Implement gallery image decoding (JPEG, PNG, GIF, WebP)
    - _Requirements: 1.1, 1.3, 1.5_

- [x] 8. Checkpoint — Platform layer complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Presentation layer — ViewModels
  - [x] 9.1 Implement PayViewModel
    - Expose PayUiState and SessionState via StateFlow
    - Handle QR scanned data → form autofill (all non-null fields populated)
    - Validate inputs before starting payment session
    - Wire ActionRunner for send-money flow
    - Clear PIN within 500ms on any session end (success, failure, timeout, cancel)
    - Clear PIN on navigation away or backgrounding
    - Clear individual field errors on edit
    - _Requirements: 1.6, 2.1, 8.4, 8.5, 9.1, 9.2_

  - [x] 9.2 Write property test for QR autofill completeness
    - **Property 3: QR Autofill Completeness**
    - **Validates: Requirements 1.6**

  - [x] 9.3 Write property test for error highlight clearing on edit
    - **Property 13: Error Highlight Clearing on Edit**
    - **Validates: Requirements 8.5**

  - [x] 9.4 Write property test for PIN cleared after session completion
    - **Property 14: PIN Cleared After Session Completion**
    - **Validates: Requirements 9.1**

  - [x] 9.5 Implement BalanceViewModel
    - Expose BalanceUiState and SessionState via StateFlow
    - Validate PIN before starting balance check
    - Wire ActionRunner for check-balance flow
    - Clear PIN on session end
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 9.1_

  - [x] 9.6 Implement HistoryViewModel
    - Expose transactions list via StateFlow from HistoryRepository
    - Implement clearHistory action
    - Handle empty state
    - _Requirements: 11.3, 11.4_

- [x] 10. Presentation layer — Compose UI screens
  - [x] 10.1 Implement Navigation setup and screen routes
    - Configure NavHost with all Screen routes (Home, Pay, Balance, QrScanner, History, Faq, About, NotRegistered, Settings)
    - Animated transitions completing within 300ms
    - Back button handling to cancel active sessions
    - _Requirements: 13.2, 5.7_

  - [x] 10.2 Implement Pay screen (form, validation highlights, session progress/result display)
    - Dark themed form with glassy card elements, 12dp+ border radius
    - VPA, amount, note, PIN input fields with error highlights
    - Session progress indicator and result display (success/failure with carrier text)
    - Touch feedback within 100ms on interactive elements
    - _Requirements: 8.4, 8.5, 13.1, 13.3, 13.6_

  - [x] 10.3 Implement QR Scanner screen (CameraX preview, viewfinder, zoom slider, gallery import)
    - Viewfinder with corner markers and animated scan beam
    - Zoom slider 1.0x–3.0x in 0.1 increments
    - Gallery import button (available even when camera denied)
    - Error states for camera denied/unavailable
    - _Requirements: 1.1, 1.3, 1.5_

  - [x] 10.4 Implement Balance screen (PIN input, result display)
    - PIN input field with validation
    - Balance result display with carrier text
    - Error/timeout state display
    - _Requirements: 3.1, 3.3, 3.4, 13.3_

  - [x] 10.5 Implement History screen (transaction list, empty state, clear action)
    - Reverse chronological list showing VPA, amount, date
    - Empty state message when no transactions
    - Clear history action
    - _Requirements: 11.3, 11.4_

  - [x] 10.6 Implement Settings screen (operation mode toggle) and permission flows
    - Mode toggle (Dialer/Advanced/Overlay) disabled during active session
    - Permission prompts for Accessibility, SYSTEM_ALERT_WINDOW, CALL_PHONE, CAMERA, READ_PHONE_STATE
    - Step-by-step accessibility guide (≤3 numbered steps) with deep-link button
    - Battery optimization warning for known aggressive OEMs (Samsung, Xiaomi, Huawei, OnePlus, Oppo)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6, 4.7, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 15.2, 15.3_

  - [x] 10.7 Implement FAQ, About, and NotRegistered screens
    - FAQ: description of *99# service, explanation of three modes, step-by-step payment instructions
    - About: "Made by Lakshya & Harsh"
    - NotRegistered: onboarding instructions to link bank account, preserves form data on return
    - _Requirements: 13.4, 13.5, 10.4, 10.5_

- [x] 11. Checkpoint — UI screens complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Integration wiring and final testing
  - [x] 12.1 Wire all components together in Application class and dependency injection
    - Create Application class with singleton instances (UssdEngine, OverlayController, database, repositories)
    - Wire ViewModels with dependencies (manual DI or simple service locator)
    - Register AccessibilityService in AndroidManifest with correct config XML
    - Configure all permissions in AndroidManifest
    - _Requirements: 14.1, 14.2, 15.1_

  - [x] 12.2 Implement AccessibilityService lifecycle detection and error handling
    - Check service status on Pay/Balance screen entry, show persistent banner if disabled
    - Block session start if service not running
    - Abort session mid-run if service killed, show re-enable prompt
    - _Requirements: 15.1, 15.2, 15.4_

  - [x] 12.3 Wire operation modes end-to-end
    - Dialer mode: open system dialer with prefilled USSD code only
    - Advanced mode: automate session with carrier dialog visible
    - Overlay mode: show branded overlay covering carrier dialog
    - _Requirements: 4.2, 4.3, 4.4_

  - [x] 12.4 Write integration tests for Room + SQLCipher CRUD operations
    - Test insert, query, trim, delete with encrypted database
    - Verify data not readable as plaintext
    - _Requirements: 11.1, 11.5_

  - [x] 12.5 Write integration tests for DataStore read/write and CameraX + ML Kit QR decoding
    - Test preference persistence across simulated restarts
    - Test QR decoding with sample UPI QR images
    - _Requirements: 4.5, 1.1, 1.2_

- [x] 13. Final checkpoint — Full integration
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using Kotest (minimum 100 iterations)
- Unit tests validate specific examples and edge cases
- The implementation language is Kotlin throughout, with Jetpack Compose for UI
- All 20 correctness properties from the design document are covered as property test tasks
- The app makes zero network requests after installation (Requirement 14.2)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "2.4", "3.6", "3.8"] },
    { "id": 3, "tasks": ["2.2", "2.3", "2.5", "3.1", "3.7", "3.9", "3.10"] },
    { "id": 4, "tasks": ["3.2", "3.3", "3.4", "3.5"] },
    { "id": 5, "tasks": ["5.1", "5.6"] },
    { "id": 6, "tasks": ["5.2", "5.3", "5.4", "5.5", "5.7"] },
    { "id": 7, "tasks": ["6.1", "6.5", "7.2"] },
    { "id": 8, "tasks": ["6.2", "6.6", "7.1"] },
    { "id": 9, "tasks": ["6.3", "6.4"] },
    { "id": 10, "tasks": ["9.1", "9.5", "9.6"] },
    { "id": 11, "tasks": ["9.2", "9.3", "9.4", "10.1"] },
    { "id": 12, "tasks": ["10.2", "10.3", "10.4", "10.5", "10.6", "10.7"] },
    { "id": 13, "tasks": ["12.1"] },
    { "id": 14, "tasks": ["12.2", "12.3"] },
    { "id": 15, "tasks": ["12.4", "12.5"] }
  ]
}
```
