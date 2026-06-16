# Security Policy

We take the security of OffPay seriously. Because OffPay handles offline payment automations, protecting user input and PIN lifecycle is a core priority.

## Supported Versions

Only the latest active version of OffPay receives security updates. If you find a security issue, please ensure you can reproduce it on the latest release before reporting.

| Version | Supported |
| ------- | --------- |
| >= 1.0  | Yes       |
| < 1.0   | No        |

## Threat Model & Out-of-Scope Risks

OffPay operates completely offline to safeguard privacy, meaning it has zero network permissions (`android.permission.INTERNET`). However, users and security researchers must be aware of risks inherent to the device and cellular network environment that are outside the scope of OffPay's control:

1. **USSD Network Security:** USSD signaling is transmitted over the GSM cellular network voice channel without end-to-end encryption. It remains susceptible to carrier-level interception, SIM swapping, and base station spoofing (e.g. IMSI catching).
2. **Device-Level Clipboard Access:** In Manual Mode, OffPay copies details to the system clipboard. If a user runs malicious apps with clipboard monitoring/hijacking capabilities, copied data may be read or modified.
3. **Screen Recording and Remote Mirroring:** OffPay runs offline and cannot monitor or prevent third-party background applications from recording, casting, or mirroring the screen. Users must ensure no screen capture is active when entering their PIN.
4. **Android OS Custom Skins:** Layouts and dialog structures of standard carrier USSD interfaces may vary on vendor-specific skins (e.g. HyperOS, MIUI, ColorOS). This can cause automated parsing anomalies. Manual Mode acts as the safe fallback.

## Reporting a Vulnerability

**Please do not report security vulnerabilities via public GitHub issues.**

If you discover a security vulnerability (such as a memory leak exposing the UPI PIN, storage decryption flaws, or bypasses of the Jio carrier block), please report it responsibly:

1. Send a private report to the maintainers (either via email or by opening a draft security advisory on GitHub if enabled).
2. Include a detailed description of the issue, step-by-step reproduction instructions, device environment, and a proof of concept if available.
3. Allow the maintainers reasonable time to review and deploy a fix before making details public.

We appreciate the efforts of security researchers who help keep OffPay secure for offline transaction users.
