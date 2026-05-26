# Requirements Document

## Introduction

OffPay is a native Android application (Kotlin) that enables fully offline UPI payments using India's *99# USSD infrastructure. The app combines a modern, dark-themed UI with an AccessibilityService-driven USSD automation engine to execute multi-step carrier dialogs autonomously. It supports QR code scanning, send money, and balance check flows across three operation modes: Dialer (non-autonomous), Advanced (live dialog visible), and Overlay (full app UI covers system dialog).

## Glossary

- **USSD_Engine**: The core subsystem that drives multi-step USSD sessions by reading and writing to the carrier's system dialog via AccessibilityService
- **Action_Runner**: The component that executes a scripted sequence of match/reply steps against carrier frames, handling pacing, timeouts, and failure detection
- **QR_Scanner**: The camera-based module that decodes UPI QR codes and extracts payment parameters
- **Overlay_Controller**: The TYPE_APPLICATION_OVERLAY window manager that paints app UI above the system USSD dialog
- **UPI_Parser**: The module that parses `upi://` deep links and extracts VPA, amount, and note fields
- **Carrier_Frame**: A single text message displayed by the carrier in its USSD dialog, captured by the AccessibilityService
- **VPA**: Virtual Payment Address — the UPI identifier in format `user@provider`
- **Operation_Mode**: One of three runtime modes controlling how the USSD session is presented to the user (Dialer, Advanced, Overlay)
- **Session_ID**: A monotonically increasing integer assigned on each dial() call, used to discard stale frames from prior sessions
- **Slow_Watch**: A 12-second inactivity timer that resets on every frame or reply, detecting stuck sessions
- **Hard_Timeout**: A 25-second absolute cap on any single USSD session

## Requirements

### Requirement 1: QR Code Scanning

**User Story:** As a user, I want to scan UPI QR codes with my camera or import them from my gallery, so that I can extract payment details without typing.

#### Acceptance Criteria

1. WHEN the user opens the QR scanner, THE QR_Scanner SHALL activate the rear-facing device camera, display a viewfinder with corner markers and an animated scan beam, and provide a zoom slider ranging from 1.0x to 3.0x in 0.1 increments
2. WHEN a valid UPI QR code is detected in the camera frame, THE UPI_Parser SHALL extract the VPA (pa), payee name (pn, if present), amount (am, if present), and transaction note (tn, if present) from the decoded `upi://pay?` URI within 500 milliseconds of detection
3. WHEN the user selects an image from the device gallery, THE QR_Scanner SHALL accept image files (JPEG, PNG, GIF, WebP), decode any QR code present in the image, and pass the decoded string to the UPI_Parser
4. IF the scanned or imported data does not contain a valid `upi://pay?` URI or a VPA matching the pattern `[a-zA-Z0-9.\-_]{3,}@[a-zA-Z0-9.\-_]{3,}`, THEN THE QR_Scanner SHALL display an error message indicating no valid UPI code was found and remain ready for the next scan attempt
5. IF camera access is denied or unavailable, THEN THE QR_Scanner SHALL display an error message indicating the specific reason (permission denied, no camera found, or camera in use by another application) and disable the live scanning control while keeping the gallery import option available
6. WHEN a QR code is successfully parsed, THE QR_Scanner SHALL auto-fill the payment form with the extracted VPA, payee name, amount, and note fields, preserving any field that was not present in the QR data as empty

### Requirement 2: Send Money via USSD

**User Story:** As a user, I want to send money to a UPI ID using *99*1*3# without needing internet, so that I can make payments in areas with no data connectivity.

#### Acceptance Criteria

1. WHEN the user submits a valid payment form, THE Action_Runner SHALL initiate a USSD session by dialing `*99*1*3#` via ACTION_CALL intent with a new sessionId, dismissing any pre-existing carrier dialog before dialing
2. WHILE a send-money session is active, THE USSD_Engine SHALL match each Carrier_Frame against the step sequence (VPA → Amount → Remarks → PIN → Confirm), discard frames whose sessionId does not match the current run, ignore system placeholder text (e.g. "USSD code running…", "Please wait..."), and reply with the corresponding user-provided value
3. WHEN the carrier emits a frame matching the success pattern (containing "successfully", "payment sent", "reference number", or "thank you for using"), THE Action_Runner SHALL dismiss the dialog, record the transaction, and display a success state with the carrier's confirmation text
4. IF the carrier emits a frame matching a defined failure pattern (wrong PIN, invalid VPA, insufficient balance, service unavailable, or sender-receiver same), THEN THE Action_Runner SHALL dismiss the dialog and display the carrier's error text to the user
5. IF a carrier frame is terminal (no EditText present, only dismiss buttons) and does not match any expected step or success pattern, THEN THE Action_Runner SHALL treat the session as failed, dismiss the dialog, and display the carrier's text as the error message
6. IF no carrier frame or reply activity occurs for 12 seconds after the last frame or reply, THEN THE Action_Runner SHALL abort the session, dismiss the dialog, and display a timeout error to the user
7. THE Action_Runner SHALL enforce a per-step pacing delay of 250 milliseconds before sending each reply to prevent carrier packet drops
8. IF the AccessibilityService is found disabled before a reply attempt, THEN THE Action_Runner SHALL abort the session and display an error indicating the accessibility service was interrupted

### Requirement 3: Check Balance via USSD

**User Story:** As a user, I want to check my bank balance using *99*3# without internet, so that I can verify my available funds offline.

#### Acceptance Criteria

1. WHEN the user initiates a balance check, THE Action_Runner SHALL dismiss any leftover USSD dialog, increment the sessionId, and dial `*99*3#` via ACTION_CALL intent
2. WHEN the carrier emits a frame matching a PIN-prompt pattern (containing an EditText input field and text requesting PIN entry), THE USSD_Engine SHALL reply with the user's UPI PIN within 250 ms of frame receipt
3. WHEN the carrier emits a terminal frame matching balance-related keywords (balance, avail, rs, ₹, amount is, ledger, a/c bal), THE Action_Runner SHALL dismiss the dialog and display the extracted balance text to the user within 1 second
4. IF the carrier emits a frame matching a failure pattern (wrong PIN, service unavailable, PSP not registered, or an unmatched terminal frame with no EditText), THEN THE Action_Runner SHALL dismiss the dialog, clear the stored PIN from memory, and display an error message indicating the carrier's rejection reason
5. IF no carrier frame is received for 12 seconds after the last sent reply or received frame, THEN THE Action_Runner SHALL cancel the session, dismiss the dialog, and display an error message indicating a session timeout
6. THE Action_Runner SHALL enforce an 18-second hard timeout for balance-check sessions, after which it SHALL cancel the session, dismiss the dialog, and display an error message indicating the operation timed out
7. IF the active SIM carrier is identified as unsupported for *99# (e.g., Jio or CDMA), THEN THE Action_Runner SHALL prevent dialing and display an error message indicating carrier incompatibility

### Requirement 4: Three Operation Modes

**User Story:** As a user, I want to choose how the USSD session is presented to me, so that I can pick the level of automation that suits my comfort.

#### Acceptance Criteria

1. THE OffPay app SHALL provide a mode toggle allowing the user to switch between Dialer, Advanced, and Overlay operation modes, with Dialer selected as the default on first launch
2. WHILE Operation_Mode is set to Dialer, THE OffPay app SHALL open the system dialer with the prefilled USSD code and take no further autonomous action
3. WHILE Operation_Mode is set to Advanced, THE USSD_Engine SHALL automate the session while keeping the carrier's system dialog visible on screen
4. WHILE Operation_Mode is set to Overlay, THE Overlay_Controller SHALL display a full-screen app UI covering the system dialog, showing only branded progress and result states
5. WHEN the user changes Operation_Mode, THE OffPay app SHALL persist the selection to local storage and apply it to all subsequent sessions, including after app restart
6. IF the user selects Advanced or Overlay mode and the required permissions (Accessibility Service for Advanced; Accessibility Service and SYSTEM_ALERT_WINDOW for Overlay) are not granted, THEN THE OffPay app SHALL display an inline prompt directing the user to the relevant system settings page and SHALL NOT persist the mode change until permissions are confirmed
7. IF the user attempts to change Operation_Mode while a USSD session is in progress, THEN THE OffPay app SHALL disable the mode toggle until the current session completes or is cancelled

### Requirement 5: USSD Session Management

**User Story:** As a user, I want USSD sessions to be reliable and never leave me stuck, so that I can trust the app to handle carrier interactions safely.

#### Acceptance Criteria

1. WHEN a new session is initiated, THE USSD_Engine SHALL dismiss any leftover dialog from a prior session, increment the Session_ID, hide any visible overlay, and reset the frame deduplication state before dialing
2. WHILE a session is active, THE USSD_Engine SHALL discard any Carrier_Frame whose Session_ID does not match the current session
3. WHILE a session is active, THE Slow_Watch SHALL reset its 12-second timer on every received Carrier_Frame or sent reply
4. IF the Slow_Watch timer expires without any frame or reply activity, THEN THE USSD_Engine SHALL terminate the session, dismiss the carrier dialog, hide the overlay, clear any stored PIN from memory, and display a timeout error message to the user within 1 second of expiry
5. IF the Hard_Timeout of 25 seconds elapses for a send-money session, THEN THE USSD_Engine SHALL terminate the session, dismiss the carrier dialog, hide the overlay, clear any stored PIN from memory, and display a timeout error message to the user within 1 second of expiry
6. IF a session of any type other than send-money exceeds a Hard_Timeout of 30 seconds, THEN THE USSD_Engine SHALL terminate the session, dismiss the carrier dialog, and display a timeout error message to the user
7. WHEN the user presses the back button during an active session, THE USSD_Engine SHALL cancel the session, dismiss the carrier dialog, hide the overlay, and clear any stored PIN from memory
8. IF a session initiation is attempted within 2 seconds of the previous session initiation, THEN THE USSD_Engine SHALL ignore the attempt and not start a new session

### Requirement 6: Carrier Frame Processing

**User Story:** As a developer, I want the engine to correctly classify and filter carrier frames, so that the automation responds only to meaningful prompts.

#### Acceptance Criteria

1. WHEN the AccessibilityService captures a window event from a known USSD dialog package, THE USSD_Engine SHALL extract the visible text and classify the frame as menu (has EditText) or terminal (only close buttons)
2. THE USSD_Engine SHALL deduplicate consecutive frames by comparing joined text content and emitting only when the text differs from the last emitted frame
3. WHEN a Carrier_Frame contains only filler text (e.g., "Please wait...", "Processing...", "USSD code running...", "Connecting…", "Loading"), THE USSD_Engine SHALL suppress the frame and not forward it to the Action_Runner
4. WHEN a Carrier_Frame is classified as terminal and no Action_Runner step matches it, THE USSD_Engine SHALL treat the frame as an unexpected failure and surface the carrier text to the user

### Requirement 7: Overlay Window Management

**User Story:** As a user in Overlay mode, I want to see only the app's UI during a USSD session, so that the experience feels native and polished.

#### Acceptance Criteria

1. WHILE Operation_Mode is Overlay and a session is active, THE Overlay_Controller SHALL display a TYPE_APPLICATION_OVERLAY window with FLAG_NOT_FOCUSABLE positioned above the carrier dialog in the window layer order
2. THE Overlay_Controller SHALL provide in-place text updates (title, subtitle, step label) without recreating the window, completing each update within 100ms of receiving new frame data
3. WHEN an error occurs during a session, THE Overlay_Controller SHALL display an error message indicating the failure reason for 1200ms and then hide the overlay window
4. WHEN the Cancel button on the overlay is tapped, THE Overlay_Controller SHALL emit a cancel event to the USSD_Engine and hide the overlay within 200ms
5. IF SYSTEM_ALERT_WINDOW permission is not granted, THEN THE OffPay app SHALL navigate the user to the system "Display over other apps" settings page and shall not allow Overlay mode to be activated until the permission is confirmed granted
6. WHILE the overlay is displayed, THE Overlay_Controller SHALL pass touch events outside of its interactive elements (Cancel button) through to the underlying window layer via FLAG_NOT_FOCUSABLE so that the AccessibilityService can continue operating on the carrier dialog

### Requirement 8: Input Validation

**User Story:** As a user, I want the app to validate my inputs before initiating a session, so that I don't waste time on sessions that will fail due to bad data.

#### Acceptance Criteria

1. THE OffPay app SHALL validate VPA format as a non-empty, whitespace-trimmed string matching the pattern `[a-zA-Z0-9._-]+@[a-zA-Z0-9]+` with a maximum length of 50 characters
2. THE OffPay app SHALL validate amount as a number greater than or equal to 0.01 and less than or equal to 5000, with at most 2 decimal places
3. THE OffPay app SHALL validate UPI PIN as a 4-to-6 digit numeric string containing only the characters 0-9
4. WHEN the user initiates a payment, THE OffPay app SHALL validate all fields before dialing and highlight all invalid fields simultaneously, displaying an error message indicating the specific validation failure reason for each invalid field
5. WHEN the user edits a highlighted field, THE OffPay app SHALL clear the error highlight for that field before the next frame renders, without affecting error highlights on other fields
6. IF all fields fail validation, THEN THE OffPay app SHALL prevent session initiation and keep the user on the input form with all error highlights visible

### Requirement 9: Security and PIN Handling

**User Story:** As a user, I want my UPI PIN to be handled securely, so that it is never exposed or persisted beyond the active session.

#### Acceptance Criteria

1. THE OffPay app SHALL hold the UPI PIN only in volatile memory (component state) and SHALL clear the PIN value within 500 milliseconds of session completion, whether the session ends in success, failure, timeout, or user cancellation
2. IF the user navigates away from the payment screen or the app moves to background during an active session, THEN THE OffPay app SHALL clear the PIN value from volatile memory within 500 milliseconds
3. THE OffPay app SHALL mask the PIN value in any UI element, overlay display, or stream log using a fixed-length placeholder of 4 bullet characters ("••••") regardless of actual PIN length, ensuring the original PIN digit count is not revealed
4. THE OffPay app SHALL never write the PIN value to persistent storage including local files, system logs, or clipboard
5. THE OffPay app SHALL use word-boundary regex patterns (e.g., `\bPIN\b`) when matching PIN-entry prompts in carrier USSD frames, to avoid false matches on frames where "PIN" appears as a substring within VPA addresses, amount text, or other non-PIN-prompt content

### Requirement 10: Carrier Detection and Fail-Fast

**User Story:** As a user, I want to be informed immediately if my carrier doesn't support *99#, so that I don't attempt sessions that will always fail.

#### Acceptance Criteria

1. WHEN the app launches and READ_PHONE_STATE permission is granted, THE OffPay app SHALL read the active SIM's carrier information within 2 seconds of app start
2. IF READ_PHONE_STATE permission is denied or no SIM is detected, THEN THE OffPay app SHALL allow the user to proceed without carrier validation but display a warning that carrier compatibility cannot be verified
3. IF the detected carrier matches Jio (pattern: `/jio|reliance/i`), THEN THE OffPay app SHALL refuse to initiate any USSD session and display an explanation that Jio does not reliably support *99#
4. IF the carrier returns a "not registered for *99#" response (matching patterns: "could not find your/ur bank", "is not a valid selection", "please enter the correct no"), THEN THE Action_Runner SHALL dismiss the dialog and route the user to an onboarding screen with instructions to link their bank account via their bank's mobile-banking app
5. WHEN routing to the onboarding screen after a "not registered" error, THE OffPay app SHALL preserve any previously entered form data so the user does not lose their input upon returning

### Requirement 11: Transaction History

**User Story:** As a user, I want to see a history of my successful payments, so that I can reference past transactions.

#### Acceptance Criteria

1. WHEN a send-money session completes successfully, THE OffPay app SHALL persist a record containing the recipient VPA, amount, timestamp (epoch milliseconds), and verbatim carrier confirmation text to local storage
2. THE OffPay app SHALL retain a maximum of 200 transaction records in local storage, trimming the oldest entries when the limit is exceeded
3. THE OffPay app SHALL display the transaction history in reverse chronological order on the History screen, showing VPA, amount, and date for each entry
4. IF no transaction records exist, THEN THE OffPay app SHALL display an empty-state message indicating that no past transactions are available
5. THE OffPay app SHALL store transaction history using encrypted local storage to protect any PII present in carrier responses, such that stored data is not readable as plaintext from the device filesystem

### Requirement 12: Permissions Management

**User Story:** As a user, I want to be guided through granting necessary permissions, so that the app functions correctly without confusion.

#### Acceptance Criteria

1. WHEN the user first attempts to send money or check balance and CALL_PHONE permission is not already granted, THE OffPay app SHALL display a rationale explaining that the permission is needed to dial USSD codes, and then request CALL_PHONE permission
2. IF the user denies CALL_PHONE or CAMERA permission, THEN THE OffPay app SHALL display a message indicating the denied permission is required for the attempted feature and SHALL NOT proceed with the action
3. WHEN the user opens the QR scanner for the first time and CAMERA permission is not already granted, THE OffPay app SHALL request CAMERA permission
4. IF the AccessibilityService is not enabled and the user selects Advanced or Overlay mode, THEN THE OffPay app SHALL display a step-by-step guide with no more than 3 numbered steps and a button that deep-links to the Accessibility settings page
5. IF SYSTEM_ALERT_WINDOW is not granted and the user selects Overlay mode, THEN THE OffPay app SHALL deep-link to the "Display over other apps" settings page
6. WHEN the app launches and READ_PHONE_STATE permission is not already granted, THE OffPay app SHALL request READ_PHONE_STATE permission for carrier detection
7. IF the user returns from the Accessibility or Overlay settings page without granting the required permission, THEN THE OffPay app SHALL continue displaying the permission guide and SHALL NOT proceed with the selected mode

### Requirement 13: UI Design and Navigation

**User Story:** As a user, I want a modern, dark-themed, smooth interface, so that the app feels professional and intuitive.

#### Acceptance Criteria

1. THE OffPay app SHALL use a dark color scheme with glassy/translucent card elements and border radius of at least 12dp as the default and only theme
2. WHEN the user navigates between screens (Pay, Balance, QR Scan, History, FAQ, About), THE OffPay app SHALL play an animated transition that completes within 300 milliseconds
3. WHEN a USSD action completes successfully, THE OffPay app SHALL display the carrier's confirmation message text as the success state; IF a USSD action fails, THEN THE OffPay app SHALL display the carrier's exact failure text as the error state and keep it visible for at least 1.2 seconds
4. THE OffPay app SHALL include an About page displaying "Made by Lakshya & Harsh"
5. THE OffPay app SHALL include a FAQ/How-to section containing at minimum: a description of the *99# USSD service, an explanation of the three operation modes, and step-by-step instructions for completing a payment
6. WHEN the user taps any interactive element, THE OffPay app SHALL provide visual touch feedback within 100 milliseconds

### Requirement 14: Offline Operation

**User Story:** As a user, I want the app to work entirely without internet connectivity, so that I can make payments in areas with no data service.

#### Acceptance Criteria

1. THE OffPay app SHALL function for all core operations (QR scan, send money, check balance, view history) without any network data connection
2. THE OffPay app SHALL not make any HTTP/HTTPS requests after the initial app installation
3. THE OffPay app SHALL store all application data (transaction history, user preferences, mode selection) locally on the device, retaining data across app restarts and device reboots
4. IF local storage capacity is exhausted, THEN THE OffPay app SHALL display an error message indicating insufficient storage and SHALL not lose previously saved data
5. WHEN the app is launched without a network connection, THE OffPay app SHALL load all screens and UI assets within 3 seconds from local storage without displaying any network-related error indicators

### Requirement 15: AccessibilityService Lifecycle

**User Story:** As a user, I want the app to detect when the AccessibilityService is killed and inform me, so that I can re-enable it and avoid silent failures.

#### Acceptance Criteria

1. WHEN the USSD_Engine attempts to send a reply and the AccessibilityService is not running, THE OffPay app SHALL dismiss any active overlay, abort the USSD session within 2 seconds, and display an error indicating that the AccessibilityService was stopped and directing the user to re-enable it via a button that opens the device Accessibility Settings screen
2. WHEN the user navigates to the Pay or Balance screen, THE OffPay app SHALL check AccessibilityService status and, if the service is disabled, display a persistent banner containing a button that opens the device Accessibility Settings screen
3. IF the device manufacturer is identified as one known to aggressively kill background services (detected by matching the device manufacturer string against a maintained list including Samsung, Xiaomi, Huawei, OnePlus, and Oppo), THEN THE OffPay app SHALL display a dismissible recommendation on first app launch directing the user to whitelist the app in battery optimization settings
4. IF the AccessibilityService is not running when the user taps the Pay or Send action, THEN THE OffPay app SHALL prevent the USSD session from starting and display an error indicating that the service must be re-enabled before a transaction can proceed
