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
 * UI state for the Pay screen form.
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
 */
class PayViewModel(
    private val actionRunner: ActionRunner,
    private val historyRepo: HistoryRepository,
    private val prefsRepo: PreferencesRepository,
    private val carrierDetector: CarrierDetector,
    private val overlayController: OverlayController? = null,
    private val onDialerFallback: (String) -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayUiState())
    val uiState: StateFlow<PayUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /** Currently selected operation mode, observed for routing payments. */
    val operationMode: StateFlow<OperationMode> = prefsRepo.operationMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, OperationMode.OVERLAY)

    private var activeRun: ActionRun? = null
    private var sessionJob: Job? = null

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
        note: String? = null,
        pin: String? = null
    ) {
        _uiState.update { current ->
            val newErrors = current.errors.toMutableMap()
            if (vpa != null) newErrors.remove(FormField.VPA)
            if (amount != null) newErrors.remove(FormField.AMOUNT)
            if (pin != null) newErrors.remove(FormField.PIN)
            current.copy(
                vpa = vpa ?: current.vpa,
                amount = amount ?: current.amount,
                note = note ?: current.note,
                pin = pin ?: current.pin,
                errors = newErrors
            )
        }
    }

    /**
     * Validates inputs and starts the USSD payment session if valid.
     * Routes through DIALER mode (Intent.ACTION_DIAL) when the user has
     * picked the manual fallback in settings; otherwise runs the automated
     * ActionRunner with the branded overlay.
     */
    fun startPayment(vpa: String, amount: String, note: String, pin: String) {
        val mode = operationMode.value

        if (mode != OperationMode.DIALER && !actionRunner.isServiceEnabled()) {
            _sessionState.value = SessionState.Failed(
                message = "Accessibility service is disabled. Enable it in Settings.",
                resultText = ""
            )
            return
        }

        // For dialer mode we skip PIN/amount validation strictly — user types
        // those into the system dialer themselves. We still want a VPA though.
        if (mode == OperationMode.DIALER) {
            onDialerFallback("*99*1*3#")
            return
        }

        val validationResult = InputValidator.validatePaymentForm(vpa, amount, pin)
        if (validationResult.errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = validationResult.errors) }
            return
        }

        _uiState.update { it.copy(errors = emptyMap(), isSessionActive = true, pin = pin) }
        _sessionState.value = SessionState.Running(
            label = "Starting payment",
            stepIndex = 0,
            total = Actions.SendUpi.steps.size
        )

        // Show overlay at session start
        overlayController?.show(
            title = "Paying ₹${amount.trim()}",
            subtitle = "to ${vpa.trim()}",
            stepLabel = "STARTING"
        )
        overlayController?.onCancel = { cancelSession() }

        val vars = mapOf(
            "vpa" to vpa.trim(),
            "amount" to amount.trim(),
            "note" to note.ifBlank { "Payment" },
            "pin" to pin
        )

        val run = actionRunner.runAction(Actions.SendUpi, vars, viewModelScope)
        activeRun = run

        sessionJob = viewModelScope.launch {
            launch {
                run.events.collect { event ->
                    when (event) {
                        is ActionEvent.Progress -> {
                            _sessionState.value = SessionState.Running(
                                label = event.label ?: "Processing",
                                stepIndex = event.stepIndex,
                                total = event.total
                            )
                            overlayController?.update(
                                title = "Paying ₹${amount.trim()}",
                                subtitle = "to ${vpa.trim()}",
                                stepLabel = (event.label ?: "Processing").uppercase()
                            )
                        }
                        is ActionEvent.Done -> {
                            _sessionState.value = SessionState.Success(resultText = event.resultText)
                            overlayController?.hide()
                            onSessionEnded()
                            launch {
                                historyRepo.recordTransaction(
                                    vpa = vpa.trim(),
                                    payeeName = _uiState.value.payeeName.ifBlank { null },
                                    amount = amount.trim(),
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
        _sessionState.value = SessionState.Idle
        if (_sessionState.value is SessionState.Idle) {
            // After success, clear the form so the user starts fresh.
            _uiState.update { PayUiState() }
        }
    }

    fun clearFieldError(field: FormField) {
        _uiState.update { current ->
            if (current.errors.containsKey(field)) {
                current.copy(errors = current.errors - field)
            } else current
        }
    }

    fun onNavigateAway() {
        clearPin()
    }

    fun onBackground() {
        clearPin()
    }

    private fun onSessionEnded() {
        _uiState.update { it.copy(isSessionActive = false) }
        activeRun = null
        viewModelScope.launch {
            delay(100L)
            clearPin()
        }
    }

    private fun clearPin() {
        _uiState.update { it.copy(pin = "") }
    }
}
