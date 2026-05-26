<div align="center">

<img src="artifacts/icon.png" alt="OffPay" width="120" height="120" />

# OffPay

### UPI payments. Without the internet.

Send money. Check your balance. Scan QR codes.<br/>
All over plain `*99#` USSD on your SIM. No data, no Wi-Fi, no account.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Min SDK](https://img.shields.io/badge/min%20SDK-26-blue)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)]()
[![License](https://img.shields.io/badge/license-MIT-success)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen)](CONTRIBUTING.md)

<br/>

<table>
  <tr>
    <td align="center">
      <img src="docs/screenshots/pay-form.jpeg" alt="Pay screen" width="240" /><br/>
      <sub><b>Type. Tap. Done.</b></sub>
    </td>
    <td align="center">
      <img src="docs/screenshots/payment-success-alt.jpeg" alt="Payment complete" width="240" /><br/>
      <sub><b>Real bank confirmation. Fully offline.</b></sub>
    </td>
  </tr>
</table>

</div>

---

## Why OffPay

Every other UPI app needs the internet. In half of India, that's a luxury.

`*99#` has been on every SIM since 2012, free, NPCI-backed. The catch: dialling raw codes and typing UPI IDs on a number pad is brutal. **OffPay puts a clean app on top of it.** Same modern feel as GPay or PhonePe — type, scan, tap. Zero bytes of data used.

> **Built by [Lakshya](https://github.com/laksh-ya) & [Harsh](https://github.com/harshtripathi272).** Side project, not a registered payment service. Your PIN, your data, your transactions — they never leave the device.

---

## What you get

 **Fully offline** &nbsp;·&nbsp; aeroplane mode, dead Wi-Fi, no signal — doesn't matter. SIM voice channel is enough.<br/>
 **Send money** &nbsp;·&nbsp; UPI ID, amount, optional note, your PIN. ₹1 to ₹5,000 per transaction.<br/>
 **Scan or import QR** &nbsp;·&nbsp; live camera with pinch-to-zoom, or pick any QR image from gallery.<br/>
 **Check balance** &nbsp;·&nbsp; one tap, straight from the bank.<br/>
 **Encrypted history** &nbsp;·&nbsp; last 200 successful payments, on-device, encrypted.<br/>
 **Zero tracking** &nbsp;·&nbsp; no analytics, no ads, no servers, no account, zero outbound requests.<br/>
 **PIN never persists** &nbsp;·&nbsp; held in memory only, wiped within 500 ms of every session.<br/>
✨**Polish** &nbsp;·&nbsp; custom success/failure animations, haptics on every tap.

---

## How a payment looks

<div align="center">

| Type the amount | Bank confirms | If something fails |
|:--:|:--:|:--:|
| <img src="docs/screenshots/pay-form.jpeg" width="220" /> | <img src="docs/screenshots/payment-success.jpeg" width="220" /> | <img src="docs/screenshots/payment-failed.jpeg" width="220" /> |
| Inline PIN, auto-fires on the 6th digit | Animated check, real ref id | Carrier's exact error, never silent |

</div>

---

## Two modes, one toggle

<table>
<tr>
<td width="50%" valign="top">

### 🟢 Auto &nbsp;<sub><i>(default)</i></sub>

Branded OffPay screen covers the carrier dialog start to finish. You only ever see OffPay.

<sub>Needs accessibility + display-over-other-apps.</sub>

</td>
<td width="50%" valign="top">

### ⚪ Manual

OffPay copies the UPI ID, opens the system dialer with `*99*1*3#` prefilled. You drive the rest.

<sub>Works on any Android. No extra permissions.</sub>

</td>
</tr>
</table>

<p align="center">
  <img src="docs/screenshots/settings.jpeg" alt="Settings — mode + permissions" width="240" />
</p>

---

## Carrier reality check

| Carrier         | Status                                       |
|-----------------|----------------------------------------------|
| Airtel          |  works                                     |
| Vi (Vodafone Idea) |  works                                  |
| BSNL            |  works                                     |
| Jio             |  network doesn't support `*99#`. App refuses to dial. |

Bank not linked to `*99#`? Built-in onboarding guide walks you through enabling it once in BHIM.

---

## Privacy, in three lines

- Your **UPI PIN** lives only in process memory, wiped within 500 ms of every session ending.
- Your **transaction history** is in an encrypted SQLite database (SQLCipher) on your device. Uninstall = gone.
- The app makes **zero outbound network requests** after install. Verify with a network monitor.

Full Privacy Policy and Terms of Use are inside the app at **Settings → Legal**.

---

## Install

1. Grab the latest `OffPay.apk` from [Releases](../../releases).
2. Open it on your phone, allow "install from unknown sources" if prompted.
3. First launch walks you through accessibility + overlay permission for Auto mode. Skip them and Manual mode still works.

> **iPhone, tablet, or just want to try it without installing?** Use the web PWA at **[offpay.vercel.app](https://offpay.vercel.app/)** — it runs Manual-mode flow in any modern browser.

---

## Design

OffPay's look is inspired by [CRED's NeoPOP design language](https://cred.club/neopop) — sharp surfaces, lime-on-black, geometric depth. Every animation is custom-built to feel native to that vocabulary, not a stock Lottie.

---

## Coming soon

- More languages (Hindi, Tamil, Telugu, Bengali, Marathi to start)
- Real video walkthroughs in onboarding
- Per-bank quirk handling for the long tail of `*99#` flavours

---

## Contribute

PRs welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) to get started, [ARCHITECTURE.md](ARCHITECTURE.md) if you want to dig into the internals.

---

## License

[MIT](LICENSE) · Built with care by Lakshya & Harsh.
