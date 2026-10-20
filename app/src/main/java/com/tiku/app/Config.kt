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
}
