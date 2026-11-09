package com.tiku.app

object Config {
    /**
     * false = REAL mode (this shipped build): Firebase Auth + Firestore
     * with the E2EE engine live (Firebase Storage is NOT used — media lives as
     * Firestore chunks). Real mode starts EMPTY — new users see the
     * design's "Search a username to start a chat" empty state.
     * true = preview/demo mode: every screen runs against an in-memory store
     * seeded with the prototype's sample data (UI testing only). Demo data is
     * NEVER presented as real/live outside preview mode.
     */
    const val PREVIEW_UI_ONLY = false

    /**
     * Firebase Console > Authentication > Sign-in method > Google > Web SDK
     * configuration > Web client ID (oauth_client type 3 inside the owner's final
     * google-services.json, project tiku-chat).
     */
    const val WEB_CLIENT_ID = "258621285053-dm6i7dck4iosn7mhh0hk0frfjn6tmdgj.apps.googleusercontent.com"

    /**
     * ROUND 9 — THE OWNER ACCOUNT. The signed-in Firebase Auth account whose
     * email equals this constant is the app's owner: only for that account do
     * the Owner-section extras exist (opt-in notifications on his phone, an
     * optional Tik password lock). Identity is the EMAIL — never a uid (uids
     * change whenever an account is recreated; the email is stable). Every
     * other account gets build-35 behaviour exactly: mandatory gate, zero
     * notifications, all zero-trace laws. The single check lives in
     * [OwnerState]; nothing else may compare against this constant.
     */
    const val OWNER_EMAIL = "taiwanserverforff123@gmail.com"

    /**
     * WebRTC ICE servers (real P2P calls). STUN for NAT discovery; Open Relay
     * Project free TURN as fallback. Swappable without code changes elsewhere.
     */
    val ICE_SERVERS = listOf(
        IceServerCfg(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")),
        IceServerCfg(
            listOf(
                "turn:openrelay.metered.ca:80",
                "turn:openrelay.metered.ca:443",
                "turns:openrelay.metered.ca:443?transport=tcp",
            ),
            username = "openrelayproject",
            credential = "openrelayproject",
        ),
    )

    /** Ringing timeout: unanswered calls delete themselves after this long. */
    const val CALL_RING_TIMEOUT_MS = 40_000L

    /**
     * ROUND 58 — the tiku-notify Worker's push endpoint (FCM switch).
     * After a message/call write lands in Firestore, the sender POSTs
     * here with its Firebase ID token; the Worker verifies it, checks
     * chat membership, and forwards a data-only FCM push to the peer's
     * stored token. The payload carries the message's existing
     * ciphertext only — the Worker never sees plaintext (see
     * PushSender / TikuFcmService in FcmNotify.kt).
     */
    const val NOTIFY_URL = "https://tiku-notify.tiku-notify-abu.workers.dev/notify"
}

/** One ICE server entry (STUN or TURN). */
data class IceServerCfg(
    val urls: List<String>,
    val username: String = "",
    val credential: String = "",
)
