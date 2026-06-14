package com.github.deadizar.aimanager

import com.intellij.DynamicBundle

private const val BUNDLE = "messages.MyBundle"

@Suppress("unused")
object AiManagerBundle : DynamicBundle(BUNDLE) {

    operator fun get(key: String, vararg params: Any) =
        getMessage(key, *params)

    @JvmStatic
    fun message(key: String, vararg params: Any) =
        getMessage(key, *params)

    @JvmStatic
    @Suppress("unused")
    fun messagePointer(key: String, vararg params: Any) =
        getLazyMessage(key, *params)
}

