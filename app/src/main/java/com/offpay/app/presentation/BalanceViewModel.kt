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
import com.offpay.app.domain.SimInfo
import com.offpay.app.domain.UssdEnginePort
import com.offpay.app.platform.CarrierDetector
import com.offpay.app.platform.OverlayController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val isSessionActive: Boolean = false,
    val simPickerTitle: String = "Choose SIM"
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
    private val carrierDetector: CarrierDetector,
    private val ussdEngine: UssdEnginePort,
    private val overlayController: OverlayController? = null,
    private val onDialerFallback: (String) -> Unit = {},
    private val clipboardWriter: (String) -> Unit = {},
    /**
     * System-level Toast hook — shows over other apps including the
     * dialer once we hand off in MANUAL mode. Wired in MainScaffold.
     */
    private val systemToast: (String) -> Unit = {}
) : ViewModel() {
    private data class PendingBalance(val pin: String)

    private val _uiState = MutableStateFlow(BalanceUiState())
    val uiState: StateFlow<BalanceUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _simOptions = MutableStateFlow<List<SimInfo>?>(null)
    val simOptions: StateFlow<List<SimInfo>?> = _simOptions.asStateFlow()

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

    val upiPinLength: StateFlow<Int> = prefsRepo.upiPinLength
        .stateIn(viewModelScope, SharingStarted.Eagerly, 6)

    val defaultSimSlot: StateFlow<Int> = prefsRepo.defaultSimSlot
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    private var sessionJob: Job? = null
    private var pendingBalance: PendingBalance? = null

    init {
        // Shared logic with PayViewModel: pre-fetch only
        viewModelScope.launch { carrierDetector.getAvailableSims() }
    }

    private fun getSimSignature(sims: List<SimInfo>): String {
        return sims.sortedBy { it.slotIndex }.joinToString("|") { "${it.slotIndex}:${it.carrierName ?: "Unknown"}" }
    }

    fun onSimSelected(simInfo: SimInfo) {
        val currentTitle = _uiState.value.simPickerTitle
        _simOptions.value = null
        viewModelScope.launch {
            if (currentTitle.contains("change", ignoreCase = true)) {
                val sims = carrierDetector.getAvailableSims()
                prefsRepo.setDefaultSimSlot(simInfo.slotIndex)
                prefsRepo.setSelectedSimCarrier(simInfo.carrierName ?: "Unknown")
                prefsRepo.setLastKnownSimIds(getSimSignature(sims))
            }
            val pending = pendingBalance
            if (pending != null) {
                pendingBalance = null
                ussdEngine.setPreferredSim(simInfo)
                runCheck(pending.pin)
            }
        }
    }

    fun onAskEveryTimeSelected() {
        _simOptions.value = null
        viewModelScope.launch {
            val sims = carrierDetector.getAvailableSims()
            prefsRepo.setDefaultSimSlot(-1)
            prefsRepo.setSelectedSimCarrier(null)
            prefsRepo.setLastKnownSimIds(getSimSignature(sims))
            val pending = pendingBalance
            if (pending != null) {
                pendingBalance = null
                _uiState.update { it.copy(simPickerTitle = "Choose SIM") }
                _simOptions.value = sims
            }
        }
    }

    fun dismissSimSelection() { pendingBalance = null; _simOptions.value = null }

    private suspend fun handleSimDetection(sims: List<SimInfo>): SimInfo? {
        val defaultSlot = prefsRepo.defaultSimSlot.first()
        val selectedCarrier = prefsRepo.selectedSimCarrier.first()
        val lastSig = prefsRepo.lastKnownSimIds.first()
        val currentSig = getSimSignature(sims)

        if (sims.size == 1) return sims.first()

        if (defaultSlot != -1 && selectedCarrier != null) {
            if (lastSig != currentSig) {
                _uiState.update { it.copy(simPickerTitle = "SIM change detected: Choose SIM") }
                return null
            }
            val currentInSlot = sims.find { it.slotIndex == defaultSlot }
            if (currentInSlot != null && currentInSlot.carrierName == selectedCarrier) return currentInSlot
            _uiState.update { it.copy(simPickerTitle = "SIM change detected: Choose SIM") }
            return null
        }
        _uiState.update { it.copy(simPickerTitle = "Choose SIM") }
        return null
    }

    fun onPinChanged(pin: String) {
        val maxLength = upiPinLength.value
        val digits = pin.filter { it.isDigit() }.take(maxLength)
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
        if (mode == OperationMode.MANUAL) { onDialerFallback("*99*3#"); _sessionState.value = SessionState.Idle; return }
        if (!actionRunner.isServiceEnabled()) { _sessionState.value = SessionState.Failed(message = "Accessibility service is disabled.", resultText = ""); return }
        val validation = InputValidator.validatePin(_uiState.value.pin)
        if (!validation.isValid) { _uiState.update { it.copy(pinError = validation.errorMessage) }; return }
        maybeRequestSimThenRun(_uiState.value.pin)
    }

    private fun maybeRequestSimThenRun(pin: String) {
        viewModelScope.launch {
            val sims = carrierDetector.getAvailableSims()
            if (sims.isEmpty()) { runCheck(pin); return@launch }
            if (operationMode.value == OperationMode.MANUAL) { runCheck(pin); return@launch }
            val targetSim = handleSimDetection(sims)
            if (targetSim != null) { ussdEngine.setPreferredSim(targetSim); runCheck(pin) }
            else { pendingBalance = PendingBalance(pin); _simOptions.value = sims }
        }
    }

    private fun runCheck(pin: String) {
        _uiState.update { it.copy(pinError = null, isSessionActive = true) }
        _sessionState.value = SessionState.Running(label = "Checking balance", stepIndex = 0, total = Actions.CheckBalance.steps.size, carrierText = "Initializing...")
        if (operationMode.value != OperationMode.MANUAL) {
            overlayController?.show(title = "Checking balance", subtitle = "OffPay is asking your bank…", stepLabel = "STARTING")
        }
        overlayController?.onCancel = { cancelSession() }
        val vars = mapOf("pin" to pin)
        val actionRun = actionRunner.runAction(Actions.CheckBalance, vars, viewModelScope)
        sessionJob = viewModelScope.launch {
            launch {
                var lastCarrierPrompt: String? = null
                actionRun.events.collect { event ->
                    if (_sessionState.value !is SessionState.Running) return@collect
                    when (event) {
                        is ActionEvent.Frame -> lastCarrierPrompt = cleanCarrierText(event.frame.text)
                        is ActionEvent.Progress -> {
                            _sessionState.value = SessionState.Running(label = event.label ?: "Processing", stepIndex = event.stepIndex, total = event.total, carrierText = lastCarrierPrompt)
                            overlayController?.update(title = "Checking balance", subtitle = lastCarrierPrompt ?: "OffPay is asking your bank…", stepLabel = (event.label ?: "Processing").uppercase())
                        }
                        is ActionEvent.Done -> { _sessionState.value = SessionState.Success(event.resultText); overlayController?.hide(); launch { prefsRepo.setLastBalance(event.resultText, System.currentTimeMillis()) }; onSessionEnd() }
                        is ActionEvent.Error -> { _sessionState.value = SessionState.Failed(event.message, event.resultText); overlayController?.showError("Check failed", event.message); onSessionEnd() }
                        else -> Unit
                    }
                }
            }
            val result = actionRun.result.await()
            if (!result.success && _sessionState.value is SessionState.Running) { _sessionState.value = SessionState.Failed("Balance check failed", result.resultText); onSessionEnd() }
        }
    }

    private fun cleanCarrierText(text: String): String {
        val lines = text.split("\n").filter { it.isNotBlank() && !it.contains(Regex("^\\d+[.)]")) }
        return if (lines.isEmpty()) text.trim() else lines.joinToString("\n").trim()
    }

    fun cancelSession() { sessionJob?.cancel(); sessionJob = null; overlayController?.hide(); _sessionState.value = SessionState.Idle; onSessionEnd() }
    fun dismissSession() { _sessionState.value = SessionState.Idle; _uiState.update { it.copy(pin = "") } }
    fun dismissSnackbar() { _snackbar.value = null }
    override fun onCleared() { super.onCleared(); _uiState.update { it.copy(pin = "") } }
    private fun onSessionEnd() { _uiState.update { it.copy(isSessionActive = false) }; pendingBalance = null; _simOptions.value = null; ussdEngine.setPreferredSim(null); viewModelScope.launch { delay(500); _uiState.update { it.copy(pin = "") } } }
}
