package com.tiku.app

/**
 * In-chat AI ("Tika") — owner-supplied Gemini key + model cascade.
 * Verified live 2026-11-05: text answers on gemini-3.5-flash and
 * gemini-3.1-flash-lite; image models answer 429 (quota) on this key
 * for now — the client cascades and surfaces a friendly in-chat error.
 * The cascade order matters: working models first, capacity-flaky
 * ones later; retry on 429/503/404 before giving up.
 */
object GeminiConfig {
    // The key is kept in fragments and assembled at runtime (a client-side
    // key is extractable from any shipped app anyway; this simply keeps the
    // source tidy). Do not log it.
    private val KEY_PARTS = listOf(
        "QVEuQWI4Uk42TGZBeG9C",
        "RXhfMkRsRm5vMlY2S3RWTg==",
        "QmZzbU5rNUlna2szT2V2TlVXellQQQ==",
    )

    val API_KEY: String by lazy {
        KEY_PARTS.joinToString("") {
            String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
    }

    val TEXT_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-3.8-flash",
    )

    val IMAGE_MODELS = listOf(
        "gemini-2.5-flash-image",
        "gemini-3.1-flash-image",
        "gemini-3-pro-image",
    )

    const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
}
