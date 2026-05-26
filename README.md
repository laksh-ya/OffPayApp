<div align="center">

<img src="artifacts/icon.png" alt="OffPay" width="128" height="128" />

# OffPay

**UPI payments that work without the internet.**

Send money, check your bank balance, and scan UPI QR codes — all over plain *99# USSD on your SIM. No data plan, no Wi-Fi, no app server, no account.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Min SDK](https://img.shields.io/badge/min%20SDK-26-blue)]()
[![Target SDK](https://img.shields.io/badge/target%20SDK-34-blue)]()
[![Language](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)]()
[![License](https://img.shields.io/badge/license-MIT-success)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen)](CONTRIBUTING.md)

</div>

---

## Why OffPay exists

Every UPI app on the Play Store assumes you have a working internet connection. In huge parts of India, that assumption breaks down: village outskirts, basements, packed railway stations, after a power cut, or the moment you cross a state border with a flaky data plan. The *99# USSD service has been sitting on every SIM card since 2012 specifically to solve this — but the user experience of dialling raw codes, navigating cryptic carrier menus, and typing UPI IDs on a number pad is so painful that almost nobody uses it.

OffPay closes that gap. You use it the way you'd use any modern UPI app — type an amount, scan a QR, hit pay — and OffPay drives the *99# session for you in the background. The result is the same online-feel-good UX, with zero data usage.

> **Built by Lakshya & Harsh.** OffPay is a side project, not a registered payment service. We don't see your PIN, your balance, or your transactions. Every byte stays on your device.

---

## Highlights

- **Works fully offline.** Aeroplane mode, dead Wi-Fi, no cell data — none of it stops a payment. Only your SIM's voice channel is needed.
- **Send money to any UPI ID** with a clean, modern form. Type the VPA, the amount, your PIN, done.
- **Scan UPI QR codes** with the camera or import any QR image from your gallery. Form fields are auto-filled instantly.
- **Check your bank balance** without leaving the app or talking to a carrier menu.
- **Three operation modes** so you decide how much OffPay does for you (see below).
- **Encrypted transaction history.** The last 200 successful payments are kept on-device, in an encrypted database, sorted by date.
- **Zero accounts, zero servers.** No sign-up, no email, no phone-number verification. Install the APK and pay.
- **Zero tracking.** No analytics, no crash reporters, no ads. The app makes no network requests after install.
- **Unique animated success and failure screens** so you always know exactly what happened.

---

## How it works (the short version)

1. You open OffPay and fill in a UPI ID, an amount, and your UPI PIN.
2. OffPay dials `*99*1*3#` for you and walks the carrier's menu — answering each prompt automatically, in order.
3. The bank either confirms the transaction with a reference number or sends back an error.
4. OffPay shows you the carrier's exact reply, saves a record locally if it succeeded, and you're done.

You never have to read the system USSD dialog or type a single number into it. If you'd rather watch the dialog work, you can — see Operation Modes below.

---

## Features in detail

### Pay anyone over *99#
- Inline UPI PIN entry with masked boxes that fill as you type.
- Amount range from ₹1 to ₹5,000 (the RBI cap on *99# transactions).
- Optional payment note. UPI ID, amount, and note can all be auto-filled from a scanned QR.
- Auto-fires the moment you finish typing a 6-digit PIN — no extra tap needed.
- The session has a hard 25-second cap, so a stuck dialog never leaves you hanging.
- The payment-complete screen has its own custom animation: a hero square draws in, the checkmark strokes itself onto the surface, and a small geometric burst confirms the success. The failure screen has its own tense entry shake plus an animated X.

### Check balance over *99#
- Same flow as Pay, but for `*99*3#`.
- The last successful balance is cached locally so you can glance at it later without re-running the check.
- 18-second timeout per balance session.

### QR scanning
- Live rear-camera scanner with a viewfinder, animated scan beam, and zoom slider (1.0× to 3.0×).
- Tap-to-focus, with on-screen feedback when a code is detected.
- Gallery import — pick any image, OffPay decodes the QR and fills the form. Useful when somebody messages you a payment QR on WhatsApp or saves one as a screenshot.
- Validation up front: invalid or non-UPI codes are rejected with a clear error so you don't waste a session.

### Three operation modes
You can switch any time from Settings. The mode is saved across launches.

| Mode         | What you see during a payment                                                                                              | Needs                                                                  |
|--------------|----------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| **Auto**     | A clean OffPay screen covers the carrier dialog from start to finish. You only see branded progress and the result.        | Accessibility service + display-over-other-apps permission             |
| **Advanced** | The carrier dialog stays visible. OffPay automates every reply, with a small floating progress chip pinned to the top.     | Accessibility service                                                  |
| **Manual**   | OffPay copies the UPI ID to your clipboard and opens the system dialer with `*99*1*3#` already typed. You drive the rest.  | Nothing — works on any phone, even without granting the extra services |

Auto is the default and the most polished. Manual is a safety net for devices where granting the accessibility service is awkward.

### Transaction history
- Last 200 successful payments saved locally.
- Reverse chronological order with VPA, amount, and date.
- One-tap "Pay again" to pre-fill the form from any past entry (PIN is **never** kept — you re-enter it).
- Stored in an encrypted SQLite database. The raw file on disk is unreadable without the app's key.

### Privacy and security
- **PIN handling.** Your UPI PIN lives only in volatile memory. It is wiped within 500 ms of a session ending — success, failure, timeout, or you backing out. It is never written to disk, never logged, never sent over the wire, and is masked as `••••` in any UI surface that might display it.
- **No tracking.** No analytics SDK, no crash reporter, no advertising ID. The app makes zero outbound HTTP requests after install. You can verify this with a network monitor.
- **Encrypted history.** Transaction records are stored in an encrypted SQLite database via SQLCipher. Inspecting the file directly shows random bytes, not your payment list.
- **Bank-side security still applies.** *99# uses your operator's signalling channel and your bank's UPI infrastructure — exactly the same security path NPCI provides to BHIM, GPay, PhonePe, etc. OffPay just automates the UI on top.

### Carrier handling
- **Airtel, Vi (Vodafone Idea), and BSNL** are fully supported.
- **Jio** is detected on launch and the app politely refuses to dial — Jio's network does not reliably support *99#.
- If the carrier reports "your bank isn't linked to *99#", OffPay routes you to a one-time onboarding guide instead of leaving you guessing.
- Stuck or unresponsive sessions are auto-cancelled after 12 seconds of carrier silence so you never lose a UPI PIN attempt to a hang.

### Polish you'll actually notice
- Edge-to-edge dark UI throughout.
- Custom animations on the payment-complete and payment-failed screens.
- Haptic feedback on every button tap, every PIN digit, every QR detect.
- Five-tap easter egg on the wordmark. Find it.
- Onboarding flow on first launch that walks you through the few permissions you'll need.

---

## Screens at a glance

| Screen        | What it does                                                                          |
|---------------|---------------------------------------------------------------------------------------|
| **Pay**       | Amount, UPI ID, optional note, inline PIN entry, primary "Pay" CTA, history shortcut. |
| **Scan**      | Live QR camera with zoom + gallery import.                                            |
| **Balance**   | One-tap balance check with the last result shown above the form.                      |
| **History**   | Reverse-chronological list of successful payments.                                    |
| **Settings**  | Operation mode toggle, permission status, share-the-app, about page.                  |
| **FAQ**       | Plain-English answers to "which carriers", "what if it fails", "is this safe".        |

---

## Permissions, and why each one is asked for

| Permission                       | Why                                                                                              | Required?                                |
|----------------------------------|--------------------------------------------------------------------------------------------------|------------------------------------------|
| `CALL_PHONE`                     | To dial the `*99#` USSD code over the voice channel.                                             | Yes, always.                             |
| `CAMERA`                         | To scan UPI QR codes live.                                                                       | Only if you use the QR scanner.          |
| `READ_PHONE_STATE`               | To detect your active SIM's carrier and refuse to dial on Jio.                                   | Recommended.                             |
| Accessibility Service            | To read the carrier USSD dialog and answer each prompt automatically.                            | Required for **Auto** and **Advanced**.  |
| `SYSTEM_ALERT_WINDOW` (overlay)  | To paint the OffPay UI over the carrier dialog in **Auto** mode.                                 | Required for **Auto** only.              |

If you don't grant the optional ones, **Manual** mode still works on any device. Nothing here exfiltrates data — the accessibility service only reads your operator's USSD dialog, never anything else.

---

## Installation

OffPay is not on the Play Store. It is distributed as a signed APK.

### From a release APK
1. Download the latest `OffPay.apk` from the [Releases](../../releases) page.
2. Open the file on your phone. You may need to allow "install from unknown sources" for your browser or file manager — this is one-time and standard for sideloading.
3. Open OffPay. The first-launch flow walks you through enabling the accessibility service and overlay permission for **Auto** mode. Skip any of these to fall back to **Manual** mode.

### From source
See [ARCHITECTURE.md](ARCHITECTURE.md) for the build steps and project layout.

---

## Compatibility

- **Android 8.0 (API 26)** and up. Tested on stock Android, OneUI, MIUI, and ColorOS.
- Phones with a working voice SIM. Wi-Fi-only tablets cannot use *99#.
- **Carriers**: Airtel, Vi, BSNL — works. Jio — does not, by design of the network.
- The accessibility service is sometimes killed by aggressive battery optimisation on Samsung, Xiaomi, OnePlus, and Oppo devices. The app detects this on launch and prompts you to whitelist OffPay if needed.

---

## Frequently asked questions

**Is OffPay an official UPI app?**  
No. OffPay drives the public *99# USSD service that NPCI and your bank already operate. It is an unofficial client, not affiliated with NPCI, your bank, or any payment service provider.

**Why does it need the accessibility service?**  
The carrier USSD dialog is a system window that no normal app can read or write. The Android accessibility framework is the only sanctioned way for an app to interact with that dialog. OffPay uses the service strictly for that purpose — it ignores every other window on your device.

**Will it work on Jio?**  
Not reliably. Jio's network does not support *99# end-to-end. OffPay refuses to dial on Jio so that you don't burn a PIN attempt for nothing.

**Is my PIN safe?**  
The PIN is never written to disk and never sent over a network. It is held in process memory for the duration of one session and wiped within 500 ms of that session ending. The carrier flow itself is the same one BHIM, GPay, and your bank's mobile-banking app use over USSD.

**What happens if a payment fails halfway through?**  
The carrier never debits you for an aborted USSD session. If the carrier confirms the payment, OffPay shows you the reference number and saves the entry to history. If it fails, OffPay shows you the carrier's exact error so you know whether to retry, change the PIN, or contact your bank.

**Where does my transaction history live?**  
On your phone, in an encrypted SQLite database. Uninstalling the app deletes it. Nothing is uploaded anywhere.

**Is there a daily limit?**  
Yes — `*99#` itself caps each transaction at ₹5,000 and your bank may apply its own daily cap. OffPay enforces the per-transaction ₹1 to ₹5,000 range up front so you can't hit the cap by accident.

---

## Roadmap

Things on the table for future versions:

- Multi-language UI (Hindi, Tamil, Telugu, Bengali, Marathi to start).
- Per-bank quirk handling (some banks vary the prompt order).
- "Pay contact" — pull a UPI ID from a contact's stored note.
- Automatic backup of encrypted history to the user's own Drive (off by default).
- Wider device-maker workarounds for over-aggressive battery killers.

PRs are welcome on any of the above. See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Credits

OffPay is built by [Lakshya](#) and [Harsh](#). The core USSD automation was prototyped on a small reference test harness before being ported into the production app.

---

## License

OffPay is released under the [MIT License](LICENSE).
