package com.github.deadizar.aimanager.settings

object ProviderValidator {
    private val URL_REGEX = Regex("""^https?://[^\s/$.?#].[^\s]*$""")

    data class ValidationResult(val ok: Boolean, val message: String)

    fun validateBaseUrl(url: String): ValidationResult {
        if (url.isBlank()) return ValidationResult(false, "Base URL is required")
        if (!URL_REGEX.matches(url)) return ValidationResult(false, "Invalid URL format")
        return ValidationResult(true, "")
    }

    fun validateApiKey(key: String): ValidationResult {
        if (key.isBlank()) return ValidationResult(false, "API key is required")
        if (key.length < 8) return ValidationResult(false, "API key too short")
        return ValidationResult(true, "")
    }

    fun validateModelId(modelId: String): ValidationResult {
        if (modelId.isBlank()) return ValidationResult(false, "Model ID is required")
        return ValidationResult(true, "")
    }
}

