package com.offpay.app.presentation.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

/**
 * Effective date stamped on every legal screen. Bump on every material
 * edit so users can see when terms last changed.
 */
private const val EFFECTIVE_DATE = "16 June 2026"

/**
 * Privacy policy screen — reachable from Settings → "Legal" → "Privacy
 * Policy" and from the onboarding flow's final step.
 *
 * The content here is the same plain-language summary we maintain in the
 * repository's PRIVACY.md, tailored for on-device viewing. Anything that
 * changes in our handling of user data MUST be reflected here AND the
 * effective date bumped.
 */
@Composable
fun PrivacyScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    LegalDocumentScaffold(
        title = "Privacy Policy",
        effectiveDate = EFFECTIVE_DATE,
        onClose = onClose,
        modifier = modifier
    ) {
        LegalSection(
            heading = "The short version",
            body = "OffPay does not collect, transmit, sell, share or back up any of your data to us or any third party. There is no OffPay account, no analytics, no advertising. Everything you do in OffPay stays on your device. After install, the app makes zero outbound network requests."
        )
        LegalSection(
            heading = "What OffPay handles",
            body = "OffPay needs a few pieces of information to dial *99# on your behalf and answer the carrier's prompts. Your UPI ID, payment amount and optional note live in app memory during a session, and on success are saved to an encrypted on-device history. The carrier's reply text is shown to you and recorded for successful payments. Your active SIM's carrier name is read once on launch to detect Jio (which doesn't reliably support *99#). Your operation-mode preference and last balance are saved in a private on-device key-value store. Scanned or imported QR codes are decoded in memory only — they are never stored."
        )
        LegalSection(
            heading = "What OffPay does NOT collect",
            body = "We do not read your name, email, phone number, contacts, location, IMEI, IMSI, advertising ID, or device ID. We do not have access to your bank account. The accessibility service is restricted by configuration to the system USSD dialog packages and ignores every other app and screen on your device."
        )
        LegalSection(
            heading = "Your UPI PIN",
            body = "Your UPI PIN lives only in volatile memory and is wiped within 500 milliseconds of any session ending success, failure, timeout or cancel and on app backgrounding. It is never written to storage, never logged, never sent over the network, and is masked as four bullets in any UI surface that might display it. The carrier-side handling of your PIN is the same path BHIM, GPay and your bank's own app use."
        )
        LegalSection(
            heading = "Encrypted history",
            body = "Successful payments are saved to a local SQLite database encrypted with SQLCipher. The raw file on disk is unreadable without the app's key. The database is capped at 200 most-recent records and you can clear it any time from the History screen. Uninstalling OffPay deletes everything."
        )
        LegalSection(
            heading = "Clipboard Privacy",
            body = "OffPay accesses your system clipboard strictly to copy the recipient's VPA / UPI ID (in Manual Mode) or read imported QR code texts. Clipboard content is processed transiently in volatile memory on-device and is never written to disk, stored, or transmitted to any server."
        )
        LegalSection(
            heading = "Screen Capture and Recording Privacy",
            body = "OffPay operates completely offline and lacks permission to query other running apps or monitor your operating system for active background screen recorders, casting services (e.g. Chromecast, Miracast), or remote control tools. To protect your financial data, ensure your screen is not being shared, cast, or recorded by other apps when entering your UPI PIN."
        )
        LegalSection(
            heading = "Permissions",
            body = "Phone (CALL_PHONE): only ever used to dial *99# codes, never regular numbers. Camera: live QR scanning, processed on-device by ML Kit, frames are not stored or uploaded. Phone state (READ_PHONE_STATE): reads the carrier name to apply the Jio fail-fast rule. Accessibility service: reads the carrier USSD dialog and types replies for you, restricted to known carrier dialog packages. Display over other apps (SYSTEM_ALERT_WINDOW): paints the OffPay UI over the carrier dialog in Auto mode. Denying any optional permission still leaves Manual mode fully usable."
        )
        LegalSection(
            heading = "Children",
            body = "OffPay is intended for users old enough to operate a personal UPI account. We do not knowingly direct OffPay at children, and we do not collect any data that would let us identify a child or anyone else."
        )
        LegalSection(
            heading = "Changes",
            body = "If we ever change how OffPay handles data, this screen will be updated, the effective date will be bumped and the change will be highlighted in the corresponding release notes. The repository's git history is the canonical record of every revision."
        )
        LegalSection(
            heading = "Contact",
            body = "Questions or concerns about privacy? Open an issue on the project's repository on GitHub."
        )
    }
}

/**
 * Terms of Use screen  reachable from Settings → "Legal" → "Terms of
 * Use". Plain-language version of the boilerplate in TERMS.md.
 *
 * NOTE: This is a personal side-project app. We are not a registered
 * payment service. Liability is disclaimed to the maximum extent
 * permitted by law. If your jurisdiction makes any of this unenforceable
 * the rest still applies.
 */
@Composable
fun TermsScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    LegalDocumentScaffold(
        title = "Terms of Use",
        effectiveDate = EFFECTIVE_DATE,
        onClose = onClose,
        modifier = modifier
    ) {
        LegalSection(
            heading = "About OffPay",
            body = "OffPay is a side project published by Lakshya and Harsh. It is a free, unofficial Android client that automates India's *99# USSD service so you can use UPI without an internet connection. OffPay is NOT a registered payment service and is NOT affiliated with NPCI, your bank, your telecom carrier, or any payment service provider. Every transaction is processed by your bank and your carrier through the same *99# infrastructure that powers BHIM and other licensed apps OffPay only automates the on-screen interaction."
        )
        LegalSection(
            heading = "Eligibility",
            body = "By using OffPay you confirm that you are old enough to operate a personal bank account in India (typically 18+), that you own and control the SIM in the device, that the UPI ID and PIN you enter are yours, and that you are using OffPay in compliance with applicable laws and your bank's terms."
        )
        LegalSection(
            heading = "What you can expect",
            body = "OffPay is provided AS IS, on a best-effort basis. We try to make it work reliably across carriers, devices and Android versions, but we cannot guarantee that every payment will succeed every time. Carrier networks, bank PSPs and Android device makers can change behaviour without notice. If a payment fails for any reason, the carrier will surface the error and OffPay will pass it on to you verbatim but we have no ability to reverse, refund or compensate any transaction."
        )
        LegalSection(
            heading = "Your responsibilities",
            body = "You are solely responsible for the accuracy of every UPI ID and amount you enter. *99# transactions are typically irreversible once your bank confirms them. Always double-check the recipient before tapping Pay. You are also responsible for keeping your UPI PIN private and your device secure. Do not share screenshots that show a PIN being entered."
        )
        LegalSection(
            heading = "Network Security and Carrier Risks",
            body = "OffPay relies entirely on the cellular network's USSD (*99#) signaling channel to execute transactions. You acknowledge and agree that USSD communications are not encrypted end-to-end in the same manner as internet-based (TCP/IP) protocols. They are subject to carrier-level vulnerabilities, including but not limited to base station spoofing (IMSI catching), SIM swapping, and cellular traffic interception. You use the service at your own risk. OffPay is not liable for financial loss, identity theft, or unauthorized transactions resulting from carrier network security failures or device-level compromises."
        )
        LegalSection(
            heading = "Clipboard Integrity",
            body = "To facilitate manual operations, OffPay may copy payment details (such as UPI IDs) to your device's system clipboard. If your device is infected with malware or malicious third-party applications, clipboard data may be read, intercepted, or altered (clipboard hijacking). You are solely responsible for ensuring your device is free from malicious software. Always verify the recipient's details (VPA and Name) as shown on the final carrier confirmation screen before entering your UPI PIN."
        )
        LegalSection(
            heading = "Screen Recording and Casting Risks",
            body = "Your UPI PIN is sensitive financial data. You must ensure that no screen recording, screen casting, mirroring, or remote-access applications (such as TeamViewer, AnyDesk, Discord, or Zoom) are actively capturing or transmitting your screen while using OffPay. Entering your UPI PIN during an active capture session could expose your credentials to third parties. OffPay disclaims all liability for credentials compromised due to visual or capture-based recording."
        )
        LegalSection(
            heading = "Android OS Customizations and Accessibility Limitations",
            body = "OffPay's automated mode uses Android's Accessibility Service to read and respond to standard system USSD dialogs. However, custom Android distributions and vendor-specific skins (including but not limited to Xiaomi's HyperOS/MIUI, OPPO/Realme's ColorOS, and vivo's Funtouch OS) frequently alter the rendering, layout, and structure of these dialogs. These customizations may cause the automation logic to fail, time out, or input data incorrectly. Users are advised to double-check all inputs before final authorization or switch to Manual Mode if dialog behavior is inconsistent."
        )
        LegalSection(
            heading = "Carrier and bank charges",
            body = "Your telecom carrier may charge a small per-session fee for *99# usage as defined by TRAI tariffs (typically up to ₹0.50 per session). OffPay does not see, control or share in any such fee. Your bank's UPI transaction limits and rules apply unchanged."
        )
        LegalSection(
            heading = "Things you must not do",
            body = "You must not use OffPay to send payments to anyone other than the intended recipient, to attempt fraud, money laundering, or any activity that violates Indian law or your bank's terms. You must not reverse-engineer, repackage or redistribute OffPay in modified form without complying with the project's open-source licence (MIT). You must not use the accessibility service for anything other than the intended USSD automation flow within the app itself."
        )
        LegalSection(
            heading = "Limitation of liability",
            body = "To the maximum extent permitted by law, OffPay's authors are not liable for any direct, indirect, incidental or consequential loss arising from your use of the app — including lost funds, missed payments, carrier downtime, locked PINs, device-specific quirks, or third-party fees. Your sole remedy if you are unhappy with OffPay is to stop using it and uninstall it."
        )
        LegalSection(
            heading = "Open source and warranty",
            body = "OffPay is open source under the MIT licence. The full source is published on GitHub. The MIT licence text governs your right to copy, modify and redistribute the code, and explicitly disclaims warranties on the software."
        )
        LegalSection(
            heading = "Changes to these terms",
            body = "We may update these terms over time. Material changes will be reflected here, with the effective date bumped, and called out in release notes. Continued use of the app after a change means you accept the updated terms."
        )
        LegalSection(
            heading = "Governing law",
            body = "These terms are governed by the laws of India. Any disputes arising from the use of OffPay will be subject to the courts of competent jurisdiction in India."
        )
        LegalSection(
            heading = "Contact",
            body = "For questions about these terms, open an issue on the project's GitHub repository or email the maintainers via the contact info on the repo."
        )
    }
}

/**
 * Merged legal screen — Privacy Policy + Terms of Use in a single
 * screen with a toggle at the top to switch between them.
 */
@Composable
fun LegalScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var showTerms by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloseChip(onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Legal",
                style = NeoPopType.HeadlineLarge,
                color = NeoPopColors.TextPrimary
            )
        }

        // Tab toggle
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegalTab(
                label = "Privacy Policy",
                selected = !showTerms,
                onClick = { showTerms = false },
                modifier = Modifier.weight(1f)
            )
            LegalTab(
                label = "Terms of Use",
                selected = showTerms,
                onClick = { showTerms = true },
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Effective $EFFECTIVE_DATE",
                style = NeoPopType.LabelSmall,
                color = NeoPopColors.TextMuted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Plain-language version. Read this in full — it covers what we do and don't do with your data, and what you're agreeing to when you use OffPay.",
                style = NeoPopType.BodyMedium,
                color = NeoPopColors.TextSecondary
            )
            Spacer(Modifier.height(24.dp))

            if (!showTerms) {
                PrivacyContent()
            } else {
                TermsContent()
            }
        }
    }
}

@Composable
private fun LegalTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Box(
        modifier
            .background(if (selected) NeoPopColors.Accent else NeoPopColors.SurfaceHigh)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = NeoPopType.LabelMedium,
            color = if (selected) NeoPopColors.Black else NeoPopColors.TextSecondary
        )
    }
}

@Composable
private fun PrivacyContent() {
    LegalSection(
        heading = "The short version",
        body = "OffPay does not collect, transmit, sell, share or back up any of your data to us or any third party. There is no OffPay account, no analytics, no advertising. Everything you do in OffPay stays on your device. After install, the app makes zero outbound network requests."
    )
    LegalSection(
        heading = "What OffPay handles",
        body = "OffPay needs a few pieces of information to dial *99# on your behalf and answer the carrier's prompts. Your UPI ID, payment amount and optional note live in app memory during a session, and on success are saved to an encrypted on-device history. The carrier's reply text is shown to you and recorded for successful payments. Your active SIM's carrier name is read once on launch to detect Jio (which doesn't reliably support *99#). Your operation-mode preference and last balance are saved in a private on-device key-value store. Scanned or imported QR codes are decoded in memory only — they are never stored."
    )
    LegalSection(
        heading = "What OffPay does NOT collect",
        body = "We do not read your name, email, phone number, contacts, location, IMEI, IMSI, advertising ID, or device ID. We do not have access to your bank account. The accessibility service is restricted by configuration to the system USSD dialog packages and ignores every other app and screen on your device."
    )
    LegalSection(
        heading = "Your UPI PIN",
        body = "Your UPI PIN lives only in volatile memory and is wiped within 500 milliseconds of any session ending success, failure, timeout or cancel and on app backgrounding. It is never written to storage, never logged, never sent over the network, and is masked as four bullets in any UI surface that might display it. The carrier-side handling of your PIN is the same path BHIM, GPay and your bank's own app use."
    )
    LegalSection(
        heading = "Encrypted history",
        body = "Successful payments are saved to a local SQLite database encrypted with SQLCipher. The raw file on disk is unreadable without the app's key. The database is capped at 200 most-recent records and you can clear it any time from the History screen. Uninstalling OffPay deletes everything."
    )
    LegalSection(
        heading = "Clipboard Privacy",
        body = "OffPay accesses your system clipboard strictly to copy the recipient's VPA / UPI ID (in Manual Mode) or read imported QR code texts. Clipboard content is processed transiently in volatile memory on-device and is never written to disk, stored, or transmitted to any server."
    )
    LegalSection(
        heading = "Screen Capture and Recording Privacy",
        body = "OffPay operates completely offline and lacks permission to query other running apps or monitor your operating system for active background screen recorders, casting services (e.g. Chromecast, Miracast), or remote control tools. To protect your financial data, ensure your screen is not being shared, cast, or recorded by other apps when entering your UPI PIN."
    )
    LegalSection(
        heading = "Permissions",
        body = "Phone (CALL_PHONE): only ever used to dial *99# codes, never regular numbers. Camera: live QR scanning, processed on-device by ML Kit, frames are not stored or uploaded. Phone state (READ_PHONE_STATE): reads the carrier name to apply the Jio fail-fast rule. Accessibility service: reads the carrier USSD dialog and types replies for you, restricted to known carrier dialog packages. Display over other apps (SYSTEM_ALERT_WINDOW): paints the OffPay UI over the carrier dialog in Auto mode. Denying any optional permission still leaves Manual mode fully usable."
    )
    LegalSection(
        heading = "Children",
        body = "OffPay is intended for users old enough to operate a personal UPI account. We do not knowingly direct OffPay at children, and we do not collect any data that would let us identify a child or anyone else."
    )
    LegalSection(
        heading = "Changes",
        body = "If we ever change how OffPay handles data, this screen will be updated, the effective date will be bumped and the change will be highlighted in the corresponding release notes. The repository's git history is the canonical record of every revision."
    )
    LegalSection(
        heading = "Contact",
        body = "Questions or concerns about privacy? Open an issue on the project's repository on GitHub."
    )
}

@Composable
private fun TermsContent() {
    LegalSection(
        heading = "About OffPay",
        body = "OffPay is a side project published by Lakshya and Harsh. It is a free, unofficial Android client that automates India's *99# USSD service so you can use UPI without an internet connection. OffPay is NOT a registered payment service and is NOT affiliated with NPCI, your bank, your telecom carrier, or any payment service provider. Every transaction is processed by your bank and your carrier through the same *99# infrastructure that powers BHIM and other licensed apps OffPay only automates the on-screen interaction."
    )
    LegalSection(
        heading = "Eligibility",
        body = "By using OffPay you confirm that you are old enough to operate a personal bank account in India (typically 18+), that you own and control the SIM in the device, that the UPI ID and PIN you enter are yours, and that you are using OffPay in compliance with applicable laws and your bank's terms."
    )
    LegalSection(
        heading = "What you can expect",
        body = "OffPay is provided AS IS, on a best-effort basis. We try to make it work reliably across carriers, devices and Android versions, but we cannot guarantee that every payment will succeed every time. Carrier networks, bank PSPs and Android device makers can change behaviour without notice. If a payment fails for any reason, the carrier will surface the error and OffPay will pass it on to you verbatim but we have no ability to reverse, refund or compensate any transaction."
    )
    LegalSection(
        heading = "Your responsibilities",
        body = "You are solely responsible for the accuracy of every UPI ID and amount you enter. *99# transactions are typically irreversible once your bank confirms them. Always double-check the recipient before tapping Pay. You are also responsible for keeping your UPI PIN private and your device secure. Do not share screenshots that show a PIN being entered."
    )
    LegalSection(
        heading = "Network Security and Carrier Risks",
        body = "OffPay relies entirely on the cellular network's USSD (*99#) signaling channel to execute transactions. You acknowledge and agree that USSD communications are not encrypted end-to-end in the same manner as internet-based (TCP/IP) protocols. They are subject to carrier-level vulnerabilities, including but not limited to base station spoofing (IMSI catching), SIM swapping, and cellular traffic interception. You use the service at your own risk. OffPay is not liable for financial loss, identity theft, or unauthorized transactions resulting from carrier network security failures or device-level compromises."
    )
    LegalSection(
        heading = "Clipboard Integrity",
        body = "To facilitate manual operations, OffPay may copy payment details (such as UPI IDs) to your device's system clipboard. If your device is infected with malware or malicious third-party applications, clipboard data may be read, intercepted, or altered (clipboard hijacking). You are solely responsible for ensuring your device is free from malicious software. Always verify the recipient's details (VPA and Name) as shown on the final carrier confirmation screen before entering your UPI PIN."
    )
    LegalSection(
        heading = "Screen Recording and Casting Risks",
        body = "Your UPI PIN is sensitive financial data. You must ensure that no screen recording, screen casting, mirroring, or remote-access applications (such as TeamViewer, AnyDesk, Discord, or Zoom) are actively capturing or transmitting your screen while using OffPay. Entering your UPI PIN during an active capture session could expose your credentials to third parties. OffPay disclaims all liability for credentials compromised due to visual or capture-based recording."
    )
    LegalSection(
        heading = "Android OS Customizations and Accessibility Limitations",
        body = "OffPay's automated mode uses Android's Accessibility Service to read and respond to standard system USSD dialogs. However, custom Android distributions and vendor-specific skins (including but not limited to Xiaomi's HyperOS/MIUI, OPPO/Realme's ColorOS, and vivo's Funtouch OS) frequently alter the rendering, layout, and structure of these dialogs. These customizations may cause the automation logic to fail, time out, or input data incorrectly. Users are advised to double-check all inputs before final authorization or switch to Manual Mode if dialog behavior is inconsistent."
    )
    LegalSection(
        heading = "Carrier and bank charges",
        body = "Your telecom carrier may charge a small per-session fee for *99# usage as defined by TRAI tariffs (typically up to ₹0.50 per session). OffPay does not see, control or share in any such fee. Your bank's UPI transaction limits and rules apply unchanged."
    )
    LegalSection(
        heading = "Things you must not do",
        body = "You must not use OffPay to send payments to anyone other than the intended recipient, to attempt fraud, money laundering, or any activity that violates Indian law or your bank's terms. You must not reverse-engineer, repackage or redistribute OffPay in modified form without complying with the project's open-source licence (MIT). You must not use the accessibility service for anything other than the intended USSD automation flow within the app itself."
    )
    LegalSection(
        heading = "Limitation of liability",
        body = "To the maximum extent permitted by law, OffPay's authors are not liable for any direct, indirect, incidental or consequential loss arising from your use of the app — including lost funds, missed payments, carrier downtime, locked PINs, device-specific quirks, or third-party fees. Your sole remedy if you are unhappy with OffPay is to stop using it and uninstall it."
    )
    LegalSection(
        heading = "Open source and warranty",
        body = "OffPay is open source under the MIT licence. The full source is published on GitHub. The MIT licence text governs your right to copy, modify and redistribute the code, and explicitly disclaims warranties on the software."
    )
    LegalSection(
        heading = "Changes to these terms",
        body = "We may update these terms over time. Material changes will be reflected here, with the effective date bumped, and called out in release notes. Continued use of the app after a change means you accept the updated terms."
    )
    LegalSection(
        heading = "Governing law",
        body = "These terms are governed by the laws of India. Any disputes arising from the use of OffPay will be subject to the courts of competent jurisdiction in India."
    )
    LegalSection(
        heading = "Contact",
        body = "For questions about these terms, open an issue on the project's GitHub repository or email the maintainers via the contact info on the repo."
    )
}

// ─── Internal layout primitives ────────────────────────────────────────────────

@Composable
private fun LegalDocumentScaffold(
    title: String,
    effectiveDate: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CloseChip(onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = NeoPopType.HeadlineLarge,
                color = NeoPopColors.TextPrimary
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Effective $effectiveDate",
                style = NeoPopType.LabelSmall,
                color = NeoPopColors.TextMuted
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Plain-language version. Read this in full it covers what we do and don't do with your data, and what you're agreeing to when you use OffPay.",
                style = NeoPopType.BodyMedium,
                color = NeoPopColors.TextSecondary
            )
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun LegalSection(heading: String, body: String) {
    NeoPopCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = heading.uppercase(),
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = NeoPopType.BodyMedium,
                color = NeoPopColors.TextPrimary
            )
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun CloseChip(onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier
            .size(40.dp)
            .background(NeoPopColors.Surface)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = NeoPopColors.TextPrimary
        )
    }
}
