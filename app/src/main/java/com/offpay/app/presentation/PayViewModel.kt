package com.offpay.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offpay.app.data.HistoryRepository
import com.offpay.app.data.PreferencesRepository
import com.offpay.app.domain.ActionEvent
import com.offpay.app.domain.ActionRun
import com.offpay.app.domain.ActionRunner
import com.offpay.app.domain.Actions
import com.offpay.app.domain.FormField
import com.offpay.app.domain.InputValidator
import com.offpay.app.domain.OperationMode
import com.offpay.app.domain.SessionState
import com.offpay.app.domain.UpiData
import com.offpay.app.domain.UpiParser
import com.offpay.app.platform.CarrierDetector
import com.offpay.app.platform.OverlayController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Pay screen form. PIN is captured inline on the form
 * itself in a highlighted "ENTER UPI PIN" section — no dedicated screen.
 */
data class PayUiState(
    val vpa: String = "",
    val payeeName: String = "",
    val amount: String = "",
    val note: String = "",
    val pin: String = "",
    val errors: Map<FormField, String> = emptyMap(),
    val isSessionActive: Boolean = false
)

/**
 * ViewModel for the Pay screen. Manages form state, QR autofill,
 * input validation, USSD session lifecycle, and PIN security.
 *
 * Pay flow:
 *  1. User fills VPA + amount + inline PIN.
 *  2. Either auto-fires when 6 digits are typed (the screen debounces and
 *     calls [attemptPayment]) or the user taps the Pay button at 4-5 digits.
 *  3. [attemptPayment] validates everything; in MANUAL mode it copies the
 *     VPA to clipboard and opens the dialer; in ADVANCED/AUTO it runs the
 *     ActionRunner.
 *  4. PIN is held in volatile memory only and cleared <500ms after session
 *     end (security guarantee from the original spec).
 *
 * @param clipboardWriter callback used by MANUAL mode to copy the VPA to
 *   the system clipboard. Wired in the activity layer so the ViewModel
 *   itself stays free of Android Context dependencies.
 */
class PayViewModel(
    private val actionRunner: ActionRunner,
    private val historyRepo: HistoryRepository,
    private val prefsRepo: PreferencesRepository,
    private val carrierDetector: CarrierDetector,
    private val overlayController: OverlayController? = null,
    private val onDialerFallback: (String) -> Unit = {},
    private val clipboardWriter: (String) -> Unit = {},
    /**
     * System-level Toast hook — shows over other apps, including the
     * native dialer once we hand off in MANUAL mode. Snackbars only
     * render inside our own activity, so they're useless once the user
     * is on the carrier dialog. Wired in MainScaffold via the activity
     * context.
     */
    private val systemToast: (String) -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayUiState())
    val uiState: StateFlow<PayUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /** Currently selected operation mode, observed for routing payments. */
    val operationMode: StateFlow<OperationMode> = prefsRepo.operationMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, OperationMode.AUTO)

    private var activeRun: ActionRun? = null
    private var sessionJob: Job? = null

    /**
     * Re-prefills the form from a past transaction (used by "Pay again"
     * in the History screen). Drops any in-memory PIN so the user must
     * re-enter it; we never silently re-execute payments.
     */
    fun prefillFromTransaction(
        vpa: String,
        amount: String,
        note: String?
    ) {
        _uiState.update { current ->
            current.copy(
                vpa = vpa,
                amount = amount,
                note = note ?: "",
                pin = "",
                errors = emptyMap()
            )
        }
    }

    /**
     * Parses QR scanned data and autofills form fields for all non-null values.
     */
    fun onQrScanned(raw: String) {
        val upiData: UpiData = UpiParser.parse(raw) ?: return
        _uiState.update { current ->
            current.copy(
                vpa = upiData.vpa,
                payeeName = upiData.payeeName ?: current.payeeName,
                amount = upiData.amount ?: current.amount,
                note = upiData.transactionNote ?: current.note,
                errors = emptyMap()
            )
        }
    }

    /**
     * Updates one or more form fields. Pass `null` to leave a field unchanged.
     * Editing a field clears its validation error so the highlight goes away
     * as soon as the user starts fixing it.
     */
    fun onFormFieldChanged(
        vpa: String? = null,
        amount: String? = null,
        note: String? = null
    ) {
        _uiState.update { current ->
            val newErrors = current.errors.toMutableMap()
            if (vpa != null) newErrors.remove(FormField.VPA)
            if (amount != null) newErrors.remove(FormField.AMOUNT)
            current.copy(
                vpa = vpa ?: current.vpa,
                amount = amount ?: current.amount,
                note = note ?: current.note,
                errors = newErrors
            )
        }
    }

    /**
     * Inline PIN editing. Strips non-digits and caps at 6. Clears any
     * existing PIN error so the user sees the highlight clear as they type.
     */
    fun onPinChanged(pin: String) {
        val digits = pin.filter { it.isDigit() }.take(6)
        _uiState.update { current ->
            current.copy(
                pin = digits,
                errors = current.errors - FormField.PIN
            )
        }
    }

    /**
     * Attempts to start a payment. Validates the form, then either:
     *  - Manual mode: copies VPA to clipboard, opens dialer, resets state.
     *  - Advanced/Auto: runs the ActionRunner via [runPayment].
     *
     * Auto-fires once the user types a 6-digit PIN (the screen calls this).
     * Manual taps of the Pay button at 4-5 digits also reach here.
     */
    fun attemptPayment() {
        val state = _uiState.value
        val mode = operationMode.value

        // For non-manual modes the accessibility service must be alive.
        if (mode != OperationMode.MANUAL && !actionRunner.isServiceEnabled()) {
            _sessionState.value = SessionState.Failed(
                message = "Accessibility service is disabled. Enable it in Settings.",
                resultText = ""
            )
            return
        }

        // Validate VPA and amount up front for every mode (manual still
        // needs a valid VPA — that's what we copy to the clipboard).
        val errors = mutableMapOf<FormField, String>()
        InputValidator.validateVpa(state.vpa).also {
            if (!it.isValid) errors[FormField.VPA] = it.errorMessage!!
        }
        InputValidator.validateAmount(state.amount).also {
            if (!it.isValid) errors[FormField.AMOUNT] = it.errorMessage!!
        }

        // PIN is required only for automated modes; manual mode lets the
        // user enter the PIN themselves in the dialer.
        if (mode != OperationMode.MANUAL) {
            InputValidator.validatePin(state.pin).also {
                if (!it.isValid) errors[FormField.PIN] = it.errorMessage!!
            }
        }

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(errors = emptyMap()) }

        val cleanedVpa = state.vpa.trim()
        val cleanedAmount = state.amount.trim()
        val cleanedNote = state.note

        if (mode == OperationMode.MANUAL) {
            // Copy the recipient VPA so the user can paste it after the
            // dialer opens (most carriers' *99# UI accepts a paste into the
            // first prompt). Then fire the dialer with *99*1*3# prefilled.
            clipboardWriter(cleanedVpa)
            // In-app snackbar (visible only on the brief moment before the
            // dialer takes the foreground).
            _snackbar.value = "UPI ID copied — paste it on the *99# prompt"
            onDialerFallback("*99*1*3#")
            // System-level Toast so the same nudge actually surfaces on
            // top of the dialer, where the in-app snackbar can't reach.
            // We fire two toasts in sequence so the user sees one when
            // the dialer first appears, and one a couple seconds later
            // in case they missed the first.
            systemToast("UPI ID copied — please paste it from the clipboard on the *99# prompt")
            viewModelScope.launch {
                delay(1_800)
                systemToast("Paste the UPI ID from clipboard when the carrier asks for it")
            }
            // Reset transient session state — the user owns the dialer flow.
            _sessionState.value = SessionState.Idle
            return
        }

        runPayment(cleanedVpa, cleanedAmount, cleanedNote, state.pin)
    }

    /**
     * Validates inputs and starts the USSD payment session if valid.
     * Routes through ADVANCED or AUTO based on the user's preference.
     */
    private fun runPayment(vpa: String, amount: String, note: String, pin: String) {
        val mode = operationMode.value

        _uiState.update { it.copy(errors = emptyMap(), isSessionActive = true) }
        _sessionState.value = SessionState.Running(
            label = "Starting payment",
            stepIndex = 0,
            total = Actions.SendUpi.steps.size
        )

        // Show the appropriate overlay based on mode.
        when (mode) {
            OperationMode.AUTO -> {
                overlayController?.show(
                    title = "Paying ₹$amount",
                    subtitle = "to $vpa",
                    stepLabel = "STARTING"
                )
            }
            OperationMode.ADVANCED -> {
                overlayController?.showMinimal(
                    progress = 0,
                    total = Actions.SendUpi.steps.size,
                    label = "PAYING"
                )
            }
            OperationMode.MANUAL -> {
                // Shouldn't reach here; attemptPayment short-circuits MANUAL.
            }
        }
        overlayController?.onCancel = { cancelSession() }

        val vars = mapOf(
            "vpa" to vpa,
            "amount" to amount,
            "note" to note.ifBlank { "Payment" },
            "pin" to pin
        )

        val run = actionRunner.runAction(Actions.SendUpi, vars, viewModelScope)
        activeRun = run

        sessionJob = viewModelScope.launch {
            launch {
                run.events.collect { event ->
                    // Once we've reached a terminal session state (Success
                    // or Failed), don't let any straggling events from the
                    // runner overwrite it. ActionRunner already guards
                    // its own re-entrancy, but this is belt-and-braces for
                    // any synthetic frame that races through.
                    val current = _sessionState.value
                    if (current is SessionState.Success || current is SessionState.Failed) {
                        return@collect
                    }
                    when (event) {
                        is ActionEvent.Progress -> {
                            _sessionState.value = SessionState.Running(
                                label = event.label ?: "Processing",
                                stepIndex = event.stepIndex,
                                total = event.total
                            )
                            when (mode) {
                                OperationMode.AUTO -> overlayController?.update(
                                    title = "Paying ₹$amount",
                                    subtitle = "to $vpa",
                                    stepLabel = (event.label ?: "Processing").uppercase()
                                )
                                OperationMode.ADVANCED -> overlayController?.updateMinimal(
                                    progress = event.stepIndex,
                                    total = event.total,
                                    label = "PAYING"
                                )
                                else -> Unit
                            }
                        }
                        is ActionEvent.Done -> {
                            _sessionState.value = SessionState.Success(resultText = event.resultText)
                            overlayController?.hide()
                            onSessionEnded()
                            launch {
                                historyRepo.recordTransaction(
                                    vpa = vpa,
                                    payeeName = _uiState.value.payeeName.ifBlank { null },
                                    amount = amount,
                                    note = note.ifBlank { null },
                                    carrierReply = event.resultText
                                )
                            }
                        }
                        is ActionEvent.Error -> {
                            _sessionState.value = SessionState.Failed(
                                message = event.message,
                                resultText = event.resultText
                            )
                            overlayController?.showError(
                                title = "Payment failed",
                                message = event.message
                            )
                            onSessionEnded()
                        }
                        else -> { /* Frame/Reply — no UI state change needed */ }
                    }
                }
            }

            val result = run.result.await()
            if (!result.success && _sessionState.value is SessionState.Running) {
                _sessionState.value = SessionState.Failed(
                    message = result.resultText,
                    resultText = result.resultText
                )
                overlayController?.showError(title = "Payment failed", message = result.resultText)
                onSessionEnded()
            }
        }
    }

    /** Cancel the active session. */
    fun cancelSession() {
        viewModelScope.launch {
            activeRun?.cancel?.invoke()
            overlayController?.hide()
            _sessionState.value = SessionState.Idle
            onSessionEnded()
        }
    }

    /** Dismiss a terminal (success/failed) session card and return to the form. */
    fun dismissSession() {
        val wasSuccess = _sessionState.value is SessionState.Success
        _sessionState.value = SessionState.Idle
        if (wasSuccess) {
            // After success, clear the form so the user starts fresh.
            _uiState.update { PayUiState() }
        } else {
            // On failure, just clear the PIN so the user can re-enter it.
            _uiState.update { it.copy(pin = "") }
        }
    }

    fun clearFieldError(field: FormField) {
        _uiState.update { current ->
            if (current.errors.containsKey(field)) {
                current.copy(errors = current.errors - field)
            } else current
        }
    }

    fun dismissSnackbar() {
        _snackbar.value = null
    }

    fun onNavigateAway() {
        // Defensive: if the user backs out mid-form, drop the in-memory PIN.
        _uiState.update { it.copy(pin = "") }
    }

    fun onBackground() {
        _uiState.update { it.copy(pin = "") }
    }

    /**
     * Called whenever the session reaches a terminal state. Marks the
     * session as inactive and schedules a wipe of the PIN within 500ms so
     * it never lingers in memory longer than necessary.
     */
    private fun onSessionEnded() {
        _uiState.update { it.copy(isSessionActive = false) }
        activeRun = null
        viewModelScope.launch {
            delay(500)
            _uiState.update { it.copy(pin = "") }
        }
    }
}
