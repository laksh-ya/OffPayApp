package com.offpay.app.domain

/**
 * Represents a single USSD frame captured from the carrier dialog.
 */
data class UssdFrame(
    val text: String,
    val isMenu: Boolean,
    val isTerminal: Boolean,
    val sessionId: Int,
    val frameId: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Defines a scripted USSD action flow with ordered steps.
 */
data class Action(
    val code: String,
    val steps: List<ActionStep>,
    val failurePatterns: List<Regex> = emptyList(),
    val timeoutMs: Long = 25_000L
)

/**
 * A single step within an Action sequence.
 */
data class ActionStep(
    val match: Regex,
    val reply: String? = null,
    val done: Boolean = false,
    val label: String? = null,
    val delayMs: Long = 250L
)

/**
 * Events emitted during action execution.
 */
sealed class ActionEvent {
    data class Progress(val stepIndex: Int, val total: Int, val label: String?) : ActionEvent()
    data class Frame(val frame: UssdFrame, val stepIndex: Int) : ActionEvent()
    data class Reply(val value: String, val stepIndex: Int) : ActionEvent()
    data class Done(val resultText: String) : ActionEvent()
    data class Error(val message: String, val resultText: String) : ActionEvent()
}

/**
 * Final result of an action execution.
 */
data class ActionResult(val success: Boolean, val resultText: String)
