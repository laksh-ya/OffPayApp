package com.offpay.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offpay.app.data.Contact
import com.offpay.app.data.ContactRepository
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
import com.offpay.app.domain.SimInfo
import com.offpay.app.domain.UssdEnginePort
import com.offpay.app.domain.UpiParser
import com.offpay.app.platform.CarrierDetector
import com.offpay.app.platform.OverlayController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PayTargetType { VPA, MOBILE_NUMBER }

data class PayUiState(
    val targetType: PayTargetType = PayTargetType.VPA,
    val vpa: String = "",
    val payeeName: String = "",
    val amount: String = "",
    val note: String = "",
    val pin: String = "",
    val mobileNumber: String = "",
    val errors: Map<FormField, String> = emptyMap(),
    val isSessionActive: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val contactSearchQuery: String = "",
    val simPickerTitle: String = "Choose SIM"
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
    private val contactRepo: ContactRepository,
    private val carrierDetector: CarrierDetector,
    private val ussdEngine: UssdEnginePort,
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
    private data class PendingPayment(val recipient: String, val amount: String, val note: String, val pin: String)

    private val _uiState = MutableStateFlow(PayUiState())
    val uiState: StateFlow<PayUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _simOptions = MutableStateFlow<List<SimInfo>?>(null)
    val simOptions: StateFlow<List<SimInfo>?> = _simOptions.asStateFlow()

    val operationMode: StateFlow<OperationMode> = prefsRepo.operationMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, OperationMode.AUTO)

    val upiPinLength: StateFlow<Int> = prefsRepo.upiPinLength
        .stateIn(viewModelScope, SharingStarted.Eagerly, 6)

    val defaultSimSlot: StateFlow<Int> = prefsRepo.defaultSimSlot
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    private var activeRun: ActionRun? = null
    private var sessionJob: Job? = null
    private var pendingPayment: PendingPayment? = null

    init {
        // Startup Hardware Sync
        viewModelScope.launch {
            val sims = carrierDetector.getAvailableSims()
            if (sims.isEmpty()) return@launch

            val defaultSlot = prefsRepo.defaultSimSlot.first()
            val selectedCarrier = prefsRepo.selectedSimCarrier.first()
            val currentSig = getSimSignature(sims)
            val lastKnownSig = prefsRepo.lastKnownSimIds.first()

            if (sims.size == 1) {
                val onlySim = sims.first()
                if (lastKnownSig != currentSig) {
                    _snackbar.value = "Using only available SIM (${onlySim.carrierName})"
                    prefsRepo.setDefaultSimSlot(onlySim.slotIndex)
                    prefsRepo.setSelectedSimCarrier(onlySim.carrierName ?: "Unknown")
                    prefsRepo.setLastKnownSimIds(currentSig)
                }
                return@launch
            }

            // If a default was set but hardware changed, ask immediately on startup.
            if (defaultSlot != -1 && selectedCarrier != null && lastKnownSig != currentSig) {
                _uiState.update { it.copy(simPickerTitle = "SIM change detected: Choose SIM") }
                _simOptions.value = sims
            }
        }
    }

    private fun getSimSignature(sims: List<SimInfo>): String {
        return sims.sortedBy { it.slotIndex }
            .joinToString("|") { "${it.slotIndex}:${it.carrierName ?: "Unknown"}" }
    }

    fun onSimSelected(simInfo: SimInfo) {
        val isHardwareChange = _uiState.value.simPickerTitle.contains("change")
        _simOptions.value = null
        
        viewModelScope.launch {
            // A choice in the "SIM Change" dialog makes the SIM permanent.
            if (isHardwareChange) {
                val sims = carrierDetector.getAvailableSims()
                prefsRepo.setDefaultSimSlot(simInfo.slotIndex)
                prefsRepo.setSelectedSimCarrier(simInfo.carrierName ?: "Unknown")
                prefsRepo.setLastKnownSimIds(getSimSignature(sims))
            }

            val pending = pendingPayment
            if (pending != null) {
                pendingPayment = null
                ussdEngine.setPreferredSim(simInfo)
                runPayment(pending.recipient, pending.amount, pending.note, pending.pin)
            }
        }
    }

    fun onAskEveryTimeSelected() {
        _simOptions.value = null
        viewModelScope.launch {
            val sims = carrierDetector.getAvailableSims()
            // Permanent choice to disable default
            prefsRepo.setDefaultSimSlot(-1)
            prefsRepo.setSelectedSimCarrier(null)
            prefsRepo.setLastKnownSimIds(getSimSignature(sims))
            
            val pending = pendingPayment
            if (pending != null) {
                pendingPayment = null
                _uiState.update { it.copy(simPickerTitle = "Choose SIM") }
                _simOptions.value = sims
            }
        }
    }

    fun dismissSimSelection() {
        pendingPayment = null
        _simOptions.value = null
    }

    private suspend fun handleSimDetection(sims: List<SimInfo>): SimInfo? {
        val defaultSlot = prefsRepo.defaultSimSlot.first()
        val selectedCarrier = prefsRepo.selectedSimCarrier.first()
        val lastSig = prefsRepo.lastKnownSimIds.first()
        val currentSig = getSimSignature(sims)

        if (sims.size == 1) return sims.first()

        // Block transaction if hardware doesn't match saved signature
        if (defaultSlot != -1 && selectedCarrier != null) {
            if (lastSig != currentSig) {
                _uiState.update { it.copy(simPickerTitle = "SIM change detected: Choose SIM") }
                return null
            }
            val currentInSlot = sims.find { it.slotIndex == defaultSlot }
            if (currentInSlot != null && currentInSlot.carrierName == selectedCarrier) {
                return currentInSlot
            } else {
                _uiState.update { it.copy(simPickerTitle = "SIM change detected: Choose SIM") }
                return null
            }
        }

        _uiState.update { it.copy(simPickerTitle = "Choose SIM") }
        return null
    }

    fun onTargetTypeChanged(targetType: PayTargetType) {
        _uiState.update { it.copy(targetType = targetType, errors = it.errors - FormField.VPA - FormField.MOBILE_NUMBER) }
    }

    fun syncContacts() { viewModelScope.launch { _uiState.update { it.copy(contacts = contactRepo.fetchContacts()) } } }
    fun onContactSearch(query: String) { _uiState.update { it.copy(contactSearchQuery = query) } }
    fun onContactSelected(contact: Contact) { _uiState.update { it.copy(mobileNumber = contact.phoneNumber, contactSearchQuery = "") } }

    fun prefillFromTransaction(vpa: String, amount: String, note: String?) {
        _uiState.update { it.copy(vpa = vpa, amount = amount, note = note ?: "", pin = "", errors = emptyMap()) }
    }

    fun onQrScanned(raw: String) {
        val upiData = UpiParser.parse(raw) ?: return
        _uiState.update { it.copy(vpa = upiData.vpa, payeeName = upiData.payeeName ?: it.payeeName, amount = upiData.amount ?: it.amount, note = upiData.transactionNote ?: it.note, errors = emptyMap()) }
    }

    fun onFormFieldChanged(vpa: String? = null, amount: String? = null, mobileNumber: String? = null, note: String? = null) {
        _uiState.update { current ->
            val newErrors = current.errors.toMutableMap()
            if (vpa != null) newErrors.remove(FormField.VPA)
            if (amount != null) newErrors.remove(FormField.AMOUNT)
            if (mobileNumber != null) newErrors.remove(FormField.MOBILE_NUMBER)
            current.copy(vpa = vpa ?: current.vpa, amount = amount ?: current.amount, mobileNumber = mobileNumber ?: current.mobileNumber, note = note ?: current.note, errors = newErrors)
        }
    }

    fun onPinChanged(pin: String) {
        val maxLength = upiPinLength.value
        val digits = pin.filter { it.isDigit() }.take(maxLength)
        _uiState.update { it.copy(pin = digits, errors = it.errors - FormField.PIN) }
    }

    fun attemptPayment() {
        val state = _uiState.value
        val mode = operationMode.value
        if (mode != OperationMode.MANUAL && !actionRunner.isServiceEnabled()) {
            _sessionState.value = SessionState.Failed(message = "Accessibility service is disabled. Enable it in Settings.", resultText = "")
            return
        }
        val errors = mutableMapOf<FormField, String>()
        when (state.targetType) {
            PayTargetType.VPA -> InputValidator.validateVpa(state.vpa).also { if (!it.isValid) errors[FormField.VPA] = it.errorMessage!! }
            PayTargetType.MOBILE_NUMBER -> InputValidator.validateMobileNumber(state.mobileNumber).also { if (!it.isValid) errors[FormField.MOBILE_NUMBER] = it.errorMessage!! }
        }
        InputValidator.validateAmount(state.amount).also { if (!it.isValid) errors[FormField.AMOUNT] = it.errorMessage!! }
        if (mode != OperationMode.MANUAL) InputValidator.validatePin(state.pin).also { if (!it.isValid) errors[FormField.PIN] = it.errorMessage!! }

        if (errors.isNotEmpty()) { _uiState.update { it.copy(errors = errors) }; return }
        _uiState.update { it.copy(errors = emptyMap()) }

        val recipient = if (state.targetType == PayTargetType.VPA) state.vpa.trim() else state.mobileNumber.trim()
        if (mode == OperationMode.MANUAL) {
            clipboardWriter(recipient); onDialerFallback("*99*1*3#"); _sessionState.value = SessionState.Idle; return
        }
        maybeRequestSimThenRun(recipient, state.amount.trim(), state.note, state.pin)
    }

    private fun maybeRequestSimThenRun(recipient: String, amount: String, note: String, pin: String) {
        viewModelScope.launch {
            val sims = carrierDetector.getAvailableSims()
            if (sims.isEmpty()) { runPayment(recipient, amount, note, pin); return@launch }
            if (operationMode.value == OperationMode.MANUAL) { runPayment(recipient, amount, note, pin); return@launch }

            val targetSim = handleSimDetection(sims)
            if (targetSim != null) {
                ussdEngine.setPreferredSim(targetSim)
                runPayment(recipient, amount, note, pin)
            } else {
                pendingPayment = PendingPayment(recipient, amount, note, pin)
                _simOptions.value = sims
            }
        }
    }

    private fun runPayment(recipient: String, amount: String, note: String, pin: String) {
        val action = if (_uiState.value.targetType == PayTargetType.VPA) Actions.SendUpi else Actions.SendToMobile
        _uiState.update { it.copy(isSessionActive = true) }
        _sessionState.value = SessionState.Running(label = "Starting payment", stepIndex = 0, total = action.steps.size)
        overlayController?.show(title = "Paying ₹$amount", subtitle = recipient, stepLabel = "STARTING")
        overlayController?.onCancel = { cancelSession() }

        val vars = if (_uiState.value.targetType == PayTargetType.VPA) mapOf("vpa" to recipient, "amount" to amount, "note" to note.ifBlank { "Payment" }, "pin" to pin)
                   else mapOf("mobileNumber" to recipient, "amount" to amount, "note" to note.ifBlank { "Payment" }, "pin" to pin)

        val run = actionRunner.runAction(action, vars, viewModelScope)
        activeRun = run
        sessionJob = viewModelScope.launch {
            launch {
                var lastCarrierPrompt: String? = null
                run.events.collect { event ->
                    if (_sessionState.value !is SessionState.Running) return@collect
                    when (event) {
                        is ActionEvent.Frame -> lastCarrierPrompt = cleanCarrierText(event.frame.text)
                        is ActionEvent.Progress -> {
                            _sessionState.value = SessionState.Running(label = event.label ?: "Processing", stepIndex = event.stepIndex, total = event.total, carrierText = lastCarrierPrompt)
                            if (action.steps.getOrNull(event.stepIndex)?.autoSubmit == false) {
                                overlayController?.onConfirm = { viewModelScope.launch { ussdEngine.submitFilledReply() } }
                                overlayController?.show(title = "Paying ₹$amount", subtitle = lastCarrierPrompt ?: recipient, stepLabel = "CONFIRM")
                            } else {
                                overlayController?.onConfirm = null
                                overlayController?.show(title = "Paying ₹$amount", subtitle = lastCarrierPrompt ?: recipient, stepLabel = (event.label ?: "Processing").uppercase())
                            }
                        }
                        is ActionEvent.Done -> { _sessionState.value = SessionState.Success(event.resultText); overlayController?.hide(); onSessionEnded(); launch { historyRepo.recordTransaction(recipient, _uiState.value.payeeName.ifBlank { null }, amount, note.ifBlank { null }, event.resultText) } }
                        is ActionEvent.Error -> { _sessionState.value = SessionState.Failed(event.message, event.resultText); overlayController?.showError("Payment failed", event.message); onSessionEnded() }
                        else -> Unit
                    }
                }
            }
            val result = run.result.await()
            if (!result.success && _sessionState.value is SessionState.Running) { _sessionState.value = SessionState.Failed(result.resultText, result.resultText); onSessionEnded() }
        }
    }

    private fun cleanCarrierText(text: String): String {
        val lines = text.split("\n").filter { line -> line.isNotBlank() && !line.contains(Regex("^\\d+[.)]")) }
        return if (lines.isEmpty()) text.trim() else lines.joinToString("\n").trim()
    }

    fun cancelSession() { viewModelScope.launch { activeRun?.cancel?.invoke(); _sessionState.value = SessionState.Idle; onSessionEnded() } }
    fun dismissSession() { if (_sessionState.value is SessionState.Success) _uiState.update { PayUiState() } else _uiState.update { it.copy(pin = "") }; _sessionState.value = SessionState.Idle }
    fun dismissSnackbar() { _snackbar.value = null }
    fun onNavigateAway() { _uiState.update { it.copy(pin = "") } }
    fun onBackground() { _uiState.update { it.copy(pin = "") } }
    override fun onCleared() { super.onCleared(); _uiState.update { it.copy(pin = "") } }
    private fun onSessionEnded() { _uiState.update { it.copy(isSessionActive = false) }; activeRun = null; pendingPayment = null; _simOptions.value = null; ussdEngine.setPreferredSim(null); viewModelScope.launch { delay(500); _uiState.update { it.copy(pin = "") } } }
}
