package com.offpay.app.domain

/**
 * Result of validating a single input field.
 */
data class ValidationResult(val isValid: Boolean, val errorMessage: String?)

/**
 * Result of validating the entire payment form.
 * Contains a map of field → error message for fields that failed validation.
 * An empty map indicates all fields are valid.
 */
data class FormValidationResult(val errors: Map<FormField, String>)

/**
 * Identifiers for the payment form fields.
 */
enum class FormField { VPA, AMOUNT, PIN }
