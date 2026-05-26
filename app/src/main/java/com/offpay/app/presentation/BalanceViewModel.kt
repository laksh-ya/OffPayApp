package com.offpay.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offpay.app.data.PreferencesRepository
import com.offpay.app.domain.ActionEvent
import com.offpay.app.domain.ActionRunner
import com.offpay.app.domain.Actions
import com.offpay.app.domain.InputValidator
import com.offpay.app.domain.OperationMode
import com.offpay.app.domain.SessionState
import com.offpay.app.platform.OverlayController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The most recent successful balance check, persisted across app kills.
 */
data class BalanceResult(val text: String, val timestamp: Long)

/**
 * UI state for the Balance check screen.
 *
 * PIN is captured inline on the form (a 6-box section is the screen's
 * hero) and lives here so it survives recompositions.
 */
data class BalanceUiState(
    val pin: String = "",
    val pinError: String? = null,
    val isSessionActive: Boolean = false
)

/**
 * ViewModel for the Balance check screen.
 *
 * Flow:
 *  1. User enters their PIN inline → screen calls [attemptCheckBalance]
 *     either on the 6th digit (auto-fire) or via the explicit "Check Now"
 *     CTA at 4-5 digits.
 *  2. [attemptCheckBalance] validates the PIN and starts the CheckBalance
 *     action via [runCheck].
 *  3. On success the carrier reply is persisted to PreferencesRepository
 *     so the last result survives app kill.
 *
 * @param clipboardWriter only used by MANUAL mode where the screen falls
 *   back to the dialer with *99*3# prefilled. Unused in ADVANCED/AUTO.
 */
class BalanceViewModel(
    private val actionRunner: ActionRunner,
    private val prefsRepo: PreferencesRepository,
    private val overlayController: OverlayController? = null,
    private val onDialerFallback: (String) -> Unit = {},
    private val clipboardWriter: (String) -> Unit = {},
    /**
     * System-level Toast hook — shows over other apps including the
     * dialer once we hand off in MANUAL mode. Wired in MainScaffold.
     */
    private val systemToast: (String) -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(BalanceUiState())
    val uiState: StateFlow<BalanceUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    /**
     * Last persisted balance result. Hydrated from DataStore so the screen
     * renders the prior balance immediately on launch.
     */
    val lastResult: StateFlow<BalanceResult?> = combine(
        prefsRepo.lastBalanceText,
        prefsRepo.lastBalanceTimestamp
    ) { text, ts ->
        if (text != null && ts != null) BalanceResult(text, ts) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val operationMode: StateFlow<OperationMode> = prefsRepo.operationMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, OperationMode.AUTO)

    private var sessionJob: Job? = null

    /** Inline PIN editing — strips non-digits, caps at 6, clears any error. */
    fun onPinChanged(pin: String) {
        val digits = pin.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(pin = digits, pinError = null) }
    }

    /**
     * Validates the entered PIN and starts a balance-check session.
     *
     *  - Manual mode: opens the dialer with *99*3# prefilled. PIN entry is
     *    deferred to the carrier dialog itself.
     *  - Advanced/Auto: requires a valid 4-6 digit PIN, then runs the
     *    CheckBalance action via [runCheck].
     */
    fun attemptCheckBalance() {
        val mode = operationMode.value

        if (mode == OperationMode.MANUAL) {
            _snackbar.value = "Opening dialer for *99*3#"
            onDialerFallback("*99*3#")
            // System Toast — visible on top of the dialer so the user
            // remembers what's queued. The in-app snackbar above only
            // renders inside our activity which is about to lose focus.
            systemToast("Opening dialer for *99*3#")
            _sessionState.value = SessionState.Idle
            return
        }

        if (!actionRunner.isServiceEnabled()) {
            _sessionState.value = SessionState.Failed(
                message = "Accessibility service is disabled. Please enable it in Settings.",
                resultText = ""
            )
            return
        }

        val pin = _uiState.value.pin
        val validation = InputValidator.validatePin(pin)
        if (!validation.isValid) {
            _uiState.update { it.copy(pinError = validation.errorMessage) }
            return
        }

        runCheck(pin)
    }

    /**
     * Runs the CheckBalance action with the captured PIN.
     */
    private fun runCheck(pin: String) {
        val mode = operationMode.value

        _uiState.update { it.copy(pinError = null, isSessionActive = true) }
        _sessionState.value = SessionState.Running(
            label = "Checking balance",
            stepIndex = 0,
            total = Actions.CheckBalance.steps.size
        )

        when (mode) {
            OperationMode.AUTO -> overlayController?.show(
                title = "Checking balance",
                subtitle = "OffPay is asking your bank…",
                stepLabel = "STARTING"
            )
            OperationMode.ADVANCED -> overlayController?.showMinimal(
                progress = 0,
                total = Actions.CheckBalance.steps.size,
                label = "CHECKING BALANCE"
            )
            OperationMode.MANUAL -> Unit // not reached
        }
        overlayController?.onCancel = { cancelSession() }

        val vars = mapOf("pin" to pin)
        val actionRun = actionRunner.runAction(Actions.CheckBalance, vars, viewModelScope)

        sessionJob = viewModelScope.launch {
            launch {
                actionRun.events.collect { event ->
                    // Drop any straggler events once we've already reached
                    // a terminal session state (Success/Failed). Mirrors
                    // the guard in PayViewModel and protects against a
                    // synthetic "User cancelled" frame racing through
                    // after engine.cancel() inside the runner.
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
                                    title = "Checking balance",
                                    subtitle = "OffPay is asking your bank…",
                                    stepLabel = (event.label ?: "Processing").uppercase()
                                )
                                OperationMode.ADVANCED -> overlayController?.updateMinimal(
                                    progress = event.stepIndex,
                                    total = event.total,
                                    label = "CHECKING BALANCE"
                                )
                                else -> Unit
                            }
                        }
                        is ActionEvent.Done -> {
                            _sessionState.value = SessionState.Success(resultText = event.resultText)
                            overlayController?.hide()
                            launch {
                                prefsRepo.setLastBalance(
                                    text = event.resultText,
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                            onSessionEnd()
                        }
                        is ActionEvent.Error -> {
                            _sessionState.value = SessionState.Failed(
                                message = event.message,
                                resultText = event.resultText
                            )
                            overlayController?.showError(
                                title = "Check failed",
                                message = event.message
                            )
                            onSessionEnd()
                        }
                        else -> { /* Frame and Reply events — no UI state change */ }
                    }
                }
            }

            val result = actionRun.result.await()
            if (!result.success && _sessionState.value is SessionState.Running) {
                _sessionState.value = SessionState.Failed(
                    message = "Balance check failed",
                    resultText = result.resultText
                )
                onSessionEnd()
            }
        }
    }

    /**
     * Cancels the current balance check session.
     */
    fun cancelSession() {
        sessionJob?.cancel()
        sessionJob = null
        overlayController?.hide()
        _sessionState.value = SessionState.Idle
        onSessionEnd()
    }

    fun dismissSession() {
        _sessionState.value = SessionState.Idle
        // Clear PIN so a retry forces fresh entry.
        _uiState.update { it.copy(pin = "") }
    }

    fun dismissSnackbar() {
        _snackbar.value = null
    }

    private fun onSessionEnd() {
        _uiState.update { it.copy(isSessionActive = false) }
        viewModelScope.launch {
            delay(500)
            _uiState.update { it.copy(pin = "") }
        }
    }
}
