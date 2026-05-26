package com.offpay.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offpay.app.data.PreferencesRepository
import com.offpay.app.domain.ActionEvent
import com.offpay.app.domain.ActionRunner
import com.offpay.app.domain.Actions
import com.offpay.app.domain.InputValidator
import com.offpay.app.domain.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Balance check screen.
 */
data class BalanceUiState(
    val pin: String = "",
    val pinError: String? = null,
    val isSessionActive: Boolean = false
)

/**
 * ViewModel for the Balance check screen.
 * Validates PIN before starting balance check,
 * wires ActionRunner for the check-balance flow,
 * and clears PIN on session end.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 9.1
 */
class BalanceViewModel(
    private val actionRunner: ActionRunner,
    private val prefsRepo: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BalanceUiState())
    val uiState: StateFlow<BalanceUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var sessionJob: Job? = null

    /**
     * Validates the PIN and initiates a balance check session if valid.
     * If PIN is invalid, sets the error on uiState without starting a session.
     */
    fun checkBalance(pin: String) {
        // Guard: block session if accessibility service is not running
        // Validates: Requirement 15.2
        if (!actionRunner.isServiceEnabled()) {
            _sessionState.value = SessionState.Failed(
                message = "Accessibility service is disabled. Please enable it in Settings.",
                resultText = ""
            )
            return
        }

        // Validate PIN
        val validationResult = InputValidator.validatePin(pin)
        if (!validationResult.isValid) {
            _uiState.update { it.copy(pinError = validationResult.errorMessage) }
            return
        }

        // Clear any previous error and mark session active
        _uiState.update { it.copy(pin = pin, pinError = null, isSessionActive = true) }
        _sessionState.value = SessionState.Running(
            label = "Checking balance",
            stepIndex = 0,
            total = Actions.CheckBalance.steps.size
        )

        // Run CheckBalance action via ActionRunner
        val vars = mapOf("pin" to pin)
        val actionRun = actionRunner.runAction(Actions.CheckBalance, vars, viewModelScope)

        sessionJob = viewModelScope.launch {
            // Collect events to update session state
            launch {
                actionRun.events.collect { event ->
                    when (event) {
                        is ActionEvent.Progress -> {
                            _sessionState.value = SessionState.Running(
                                label = event.label ?: "Processing",
                                stepIndex = event.stepIndex,
                                total = event.total
                            )
                        }
                        is ActionEvent.Done -> {
                            _sessionState.value = SessionState.Success(resultText = event.resultText)
                            onSessionEnd()
                        }
                        is ActionEvent.Error -> {
                            _sessionState.value = SessionState.Failed(
                                message = event.message,
                                resultText = event.resultText
                            )
                            onSessionEnd()
                        }
                        else -> { /* Frame and Reply events — no UI state change needed */ }
                    }
                }
            }

            // Also await the result deferred to handle edge cases (e.g. cancellation)
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
        _sessionState.value = SessionState.Idle
        onSessionEnd()
    }

    /**
     * Clears PIN from volatile memory within 500ms of session end.
     * Requirement 9.1: PIN cleared on success, failure, timeout, or cancellation.
     */
    private fun onSessionEnd() {
        _uiState.update { it.copy(isSessionActive = false) }
        viewModelScope.launch {
            // Clear PIN within 500ms as required by Requirement 9.1
            delay(100)
            _uiState.update { it.copy(pin = "") }
        }
    }
}
