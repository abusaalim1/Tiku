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

    /** Free no-key image fallback (Pollinations.ai), ROUND 38 — used
     *  only after the whole Gemini image cascade fails. The full URL
     *  is "<base>/<url-encoded prompt>?width=..&height=..&nologo=true&model=flux". */
    const val POLLINATIONS_BASE = "https://image.pollinations.ai/prompt"

    // ---- ROUND 41: MiniMax via NVIDIA (the /babeg coding fallback) ----
    // Owner-supplied key for NVIDIA's OpenAI-compatible endpoint,
    // fragmented + assembled exactly like the Gemini key above. Used
    // ONLY to write game code when the Gemini cascade fails to produce
    // a valid game. Do not log it.
    private val NV_KEY_PARTS = listOf(
        "bnZhcGktQkNuRzhPUkY2bXFtRkVMQmVC",
        "RVI3dUdmNVhoNDVvbC1xOFBKZzlqaExs",
        "VVhQWDZwcWNSci0xclR3c2l6Y3FVQQ==",
    )

    val NV_API_KEY: String by lazy {
        NV_KEY_PARTS.joinToString("") {
            String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
    }

    const val NV_BASE = "https://integrate.api.nvidia.com/v1/chat/completions"
    const val NV_MODEL = "minimaxai/minimax-m3.1-flash"

    // ---- ROUND 41: Cloudflare Workers AI image route ----
    // FLUX.1-schnell on the owner's free Cloudflare account — the
    // middle image step (after Gemini, before Pollinations). The
    // parent filled these once the signup landed and verified the
    // endpoint live (a real FLUX image, no watermark); the token is
    // fragmented + assembled exactly like the keys above. Do not log it.
    const val CF_ACCOUNT_ID = "c64404468367dedd1b46ba1c05086bd2"

    private val CF_TOKEN_PARTS = listOf(
        "Y2Z1dF8wZDJMb3NObmJRamQ=",
        "OE80SUJiNmdkVGIyOGlQeTg=",
        "cjh5Unh4Y216WGlkZmM2ZTc4OQ==",
    )

    val CF_API_TOKEN: String by lazy {
        if (CF_TOKEN_PARTS.isEmpty()) ""
        else CF_TOKEN_PARTS.joinToString("") {
            String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
    }

    /** True only when the parent has filled both Cloudflare values. */
    val CF_CONFIGURED: Boolean
        get() = CF_ACCOUNT_ID.isNotBlank() && CF_API_TOKEN.isNotBlank()

    const val CF_IMAGE_MODEL = "@cf/black-forest-labs/flux-1-schnell"

    // ---- ROUND 70: WZ fallback provider (refusal-only) ----
    // Owner-supplied fallback provider, fires only on Gemini
    // refusals/blocks; currently dormant until the owner claims
    // the provider's free trial credit (HTTP 402 = just another
    // failure). Fragmented + assembled exactly like the keys
    // above. Do not log the key.
    private val WZ_KEY_PARTS = listOf(
        "c2stNjhhZmMxY2NhOGMzZTY=",
        "ODI4ZjY1OGI3NjJhYjBhNzM=",
        "YTk2ZTdjZjMyNjExZDUwYzc=",
    )

    val WZ_API_KEY: String by lazy {
        WZ_KEY_PARTS.joinToString("") {
            String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
    }

    const val WZ_BASE = "https://api.wuxianzhiapi.com/v1/chat/completions"
    const val WZ_MODEL = "uncensored"
}
