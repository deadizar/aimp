package com.github.deadizar.aimanager.settings

object ProviderTestCache {
    private val cache = mutableMapOf<String, String>()  // instanceId → "OK (2026-06-14 12:00)" | "FAIL: ..."

    fun store(instanceId: String, result: String) {
        cache[instanceId] = result
    }

    fun getLastResult(instanceId: String): String? = cache[instanceId]
}

