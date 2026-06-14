package com.github.deadizar.aimanager.provider

sealed class AiProviderError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    // API key inválida o expirada (HTTP 401, 403)
    class AuthError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)

    // Cuota agotada o rate limit (HTTP 429)
    class QuotaError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)

    // Error de red o timeout
    class NetworkError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)

    // Modelo no disponible en el provider (HTTP 404 + body contiene "model")
    class InvalidModelError(message: String, cause: Throwable? = null) : AiProviderError(message, cause)

    // Cualquier otro error HTTP
    class ApiError(val statusCode: Int, message: String, cause: Throwable? = null) : AiProviderError(message, cause)
}

