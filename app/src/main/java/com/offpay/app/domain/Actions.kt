package com.offpay.app.domain

/**
 * Pre-built USSD action definitions for supported operations.
 * Each action defines the USSD code, step sequence with regex matchers,
 * failure patterns, and timeout configuration.
 *
 * Failure patterns are ported verbatim from the proven `UssdAction.ts`
 * reference. Tighten them with operator-specific phrases as you encounter
 * new wording — never loosen them past `\b` word boundaries or you will
 * false-positive on prompts that share words.
 */
object Actions {

    /**
     * Common failure patterns shared across all USSD actions, ported from the
     * `UssdAction.ts` reference. Run BEFORE step matching — if any of these
     * fire, the runner bails out with the carrier text as the error.
     *
     * Tip when reading these regexes:
     *   - `\s+` matches one or more spaces (so it bridges filler words like
     *     "is" only when paired with optional groups)
     *   - `\b` is a word-boundary so we don't match inside other words
     */
    val COMMON_FAILURES: List<Regex> = listOf(
        // Generic catch-all — handles "not a valid UPI ID", "is not registered",
        // "not found", "not a registered user", "not allowed", etc. This single
        // pattern covers the bulk of carrier rejections.
        Regex("\\bnot\\s+(a\\s+)?(valid|registered|recognised|recognized|allowed|found|active|enabled|known|supported)\\b", RegexOption.IGNORE_CASE),

        // Generic "didn't work" / decline
        Regex("\\bnot\\s+(debited|sent|processed|completed)\\b", RegexOption.IGNORE_CASE),
        Regex("transaction\\s+(declined|failed|cancelled|denied|aborted)", RegexOption.IGNORE_CASE),
        Regex("unable\\s+to\\s+process", RegexOption.IGNORE_CASE),

        // Wrong / invalid input — covers many phrasings
        Regex("\\b(invalid|incorrect|wrong|bad)\\s+(pin|upi|vpa|amount|input|entry|id|password|number|account)\\b", RegexOption.IGNORE_CASE),
        // Mirror: noun-then-adjective phrasing (carriers commonly say "UPI
        // PIN is incorrect or invalid"). The pattern above only catches
        // "incorrect PIN"; this catches "PIN is incorrect/invalid/wrong"
        // and "entered UPI PIN is incorrect/invalid" so AUTO mode actually
        // bails out on a wrong-PIN reply instead of falling through to a
        // raw carrier dialog.
        Regex("\\b(pin|upi\\s*pin|upi\\s*id|vpa|password)\\s+(is\\s+)?(incorrect|invalid|wrong|mismatch)\\b", RegexOption.IGNORE_CASE),
        Regex("entered\\s+(upi\\s+)?pin\\s+(is\\s+)?(incorrect|invalid|wrong)", RegexOption.IGNORE_CASE),
        Regex("enter\\s+(a\\s+)?(valid|correct)", RegexOption.IGNORE_CASE),
        Regex("please\\s+enter\\s+(correct|valid)", RegexOption.IGNORE_CASE),
        Regex("please\\s+check\\s+and\\s+try\\s+again", RegexOption.IGNORE_CASE),

        // Specific UPI / PSP phrases — kept separately for clarity even though
        // the generic catch-all above usually matches them too.
        Regex("psp\\s+(is\\s+)?not\\s+(registered|recognised|recognized)", RegexOption.IGNORE_CASE),
        Regex("vpa\\s+(does\\s+not\\s+exist|is\\s+not\\s+(registered|valid))", RegexOption.IGNORE_CASE),
        Regex("upi\\s*id\\s+(is\\s+)?(invalid|incorrect|wrong)", RegexOption.IGNORE_CASE),

        // Account / user not found
        Regex("(no|not\\s+a)\\s+(account|user|customer)\\s+(found|registered|exists)", RegexOption.IGNORE_CASE),
        Regex("account\\s+(not\\s+found|does\\s+not\\s+exist)", RegexOption.IGNORE_CASE),
        Regex("user\\s+not\\s+found", RegexOption.IGNORE_CASE),

        // Self-pay
        Regex("(sender|payer).*(receiver|payee).*same|(receiver|payee).*(sender|payer).*same", RegexOption.IGNORE_CASE),
        Regex("\\bsame\\s+(account|vpa|user)\\b", RegexOption.IGNORE_CASE),
        Regex("cannot\\s+(send|pay)\\s+to\\s+(self|yourself|same)", RegexOption.IGNORE_CASE),

        // Funds / limit
        Regex("insufficient\\s+(funds|balance)", RegexOption.IGNORE_CASE),
        Regex("exceed(s|ed)?\\s+(limit|amount)|over\\s+limit", RegexOption.IGNORE_CASE),

        // Service availability
        Regex("service\\s+(unavailable|not\\s+available|down)", RegexOption.IGNORE_CASE),
        Regex("try\\s+again\\s+later|temporarily\\s+unavailable", RegexOption.IGNORE_CASE),
        Regex("session\\s+(timed\\s+out|expired|terminated)", RegexOption.IGNORE_CASE),

        // *99# user-not-onboarded phrases — the carrier returns these when
        // the user's mobile number isn't linked to a bank account for *99#.
        // The UI routes these specifically to a "you're not registered" flow.
        Regex("could\\s+not\\s+find\\s+(your|ur)\\s+bank", RegexOption.IGNORE_CASE),
        Regex("is\\s+not\\s+a\\s+valid\\s+selection", RegexOption.IGNORE_CASE),
        Regex("please\\s+enter\\s+the\\s+correct\\s+no", RegexOption.IGNORE_CASE),
        Regex("bank\\s+not\\s+found|no\\s+bank\\s+(linked|found)", RegexOption.IGNORE_CASE)
    )

    /**
     * Send money via UPI using *99*1*3#.
     * 6-step flow: VPA → Amount → Remark → PIN → Confirm → Success.
     * 25-second hard timeout.
     */
    val SendUpi = Action(
        code = "*99*1*3#",
        steps = listOf(
            ActionStep(
                // VPA-only words. Do NOT include bare "enter" — that would
                // collide with "Enter amount" and "Enter UPI PIN".
                match = Regex("(receiver|payee|recipient|vpa|virtual.*payment|upi.*id)", RegexOption.IGNORE_CASE),
                reply = "{vpa}",
                label = "Sending UPI ID"
            ),
            ActionStep(
                match = Regex("\\bamount\\b", RegexOption.IGNORE_CASE),
                reply = "{amount}",
                label = "Sending amount"
            ),
            ActionStep(
                match = Regex("\\b(remark|comment|note)\\b", RegexOption.IGNORE_CASE),
                reply = "{note}",
                label = "Adding note"
            ),
            ActionStep(
                match = Regex("\\bupi\\s*pin\\b|\\b(enter|6\\s*digit).*pin\\b", RegexOption.IGNORE_CASE),
                reply = "{pin}",
                label = "Entering UPI PIN"
            ),
            ActionStep(
                match = Regex("\\b(confirm|press\\s*1|are you sure)\\b", RegexOption.IGNORE_CASE),
                reply = "1",
                label = "Confirming"
            ),
            ActionStep(
                // Success terminal frame. Most success frames hit
                // ActionRunner.UNIVERSAL_SUCCESS first, but we keep this as a
                // fallback / label source for the overlay.
                match = Regex("successful|payment\\s+(?:sent|completed|done)|thank\\s*you\\s*for\\s*using|reference\\s+(?:no|number|id)\\s*[:\\-]", RegexOption.IGNORE_CASE),
                done = true,
                label = "Payment complete"
            )
        ),
        failurePatterns = COMMON_FAILURES,
        timeoutMs = 25_000L
    )

    /**
     * Check balance via *99*3#.
     * 2-step flow: PIN → Balance display.
     * 18-second hard timeout.
     */
    val CheckBalance = Action(
        code = "*99*3#",
        steps = listOf(
            ActionStep(
                match = Regex("upi\\s*pin|enter.*pin|6\\s*digit.*pin", RegexOption.IGNORE_CASE),
                reply = "{pin}",
                label = "Entering UPI PIN"
            ),
            ActionStep(
                match = Regex("balance|avail(able)?|rs\\.?\\s*\\d|inr\\s*\\d|₹\\s*\\d|amount\\s*(is|:)|ledger|a/c\\s*bal", RegexOption.IGNORE_CASE),
                done = true,
                label = "Balance fetched"
            )
        ),
        failurePatterns = COMMON_FAILURES + listOf(
            // PIN-specific rejections that some carriers phrase outside the
            // common list.
            Regex("pin\\s*(does\\s*not|doesn['']?t)\\s*match", RegexOption.IGNORE_CASE),
            Regex("pin\\s*mismatch", RegexOption.IGNORE_CASE),
            Regex("authentication\\s*failed", RegexOption.IGNORE_CASE),
            Regex("max(imum)?\\s*(attempts|tries|retries)", RegexOption.IGNORE_CASE),
            Regex("pin\\s*(blocked|locked|expired)", RegexOption.IGNORE_CASE)
        ),
        timeoutMs = 18_000L
    )
}
