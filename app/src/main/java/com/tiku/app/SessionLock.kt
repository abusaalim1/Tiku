package com.tiku.app

import android.content.Context

/**
 * SINGLE SECRET-ENTRY POINT.
 *
 * The discreet book row on the Chemistry subject page calls [openGate] — and
 * only this function may route into the password gate. (Round 3.2: the entry
 * moved off the facade home tools grid into the Chemistry page. Round 8: the
 * row itself no longer says "Tik" at all — it is a plain study "Reference
 * books" row with a book icon, indistinguishable from its neighbours.)
 * If the owner ever swaps the trigger, change this one call site; nothing
 * else in the facade knows the gate exists.
 */
object SecretEntry {
    fun openGate() {
        AppState.go(Screen.TikGate)
    }
}

/**
 * ROUND 8 — GATE GUESSING BACKOFF (item 9b). Consecutive failed password
 * attempts (journal gate AND backup restore — both are offline guessing
 * surfaces over the same secret) accumulate in plain prefs (counters only,
 * never password material). After the 2nd failure an escalating lockout
 * applies: 10s, 20s, 40s, 80s, 160s, then 5 min. The counters SURVIVE the
 * screen being closed/reopened (they live in prefs, not composition state),
 * and a successful open resets them. UX copy stays study-framed.
 */
object GateGuard {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("tiku_gate", Context.MODE_PRIVATE)

    fun lockedRemainingMs(ctx: Context): Long {
        val until = prefs(ctx).getLong("locked_until", 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Records one failed attempt; returns the lockout (ms) now in force (0 = none yet). */
    fun recordFailure(ctx: Context): Long {
        val p = prefs(ctx)
        val fails = p.getInt("fails", 0) + 1
        val delay = when {
            fails <= 2 -> 0L
            fails == 3 -> 10_000L
            fails == 4 -> 20_000L
            fails == 5 -> 40_000L
            fails == 6 -> 80_000L
            fails == 7 -> 160_000L
            else -> 300_000L
        }
        p.edit().putInt("fails", fails)
            .putLong("locked_until", if (delay > 0) System.currentTimeMillis() + delay else 0L)
            .apply()
        return delay
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().putInt("fails", 0).putLong("locked_until", 0L).apply()
    }
}

/**
 * Owns the unlocked ChatRoot session.
 *
 * Lock rules (owner, hard): the session exists ONLY while the user is inside the
 * Tik section. Any exit — Back at ChatRoot home, the discreet quick-exit control,
 * or the app going to background — returns instantly to the study facade, drops
 * decrypted material and key material from RAM, and requires the password again.
 * Keys are ephemeral in-memory only: derived on unlock, zeroed on exit.
 */
object ChatSession {
    @Volatile var unlocked: Boolean = false

    /** True while MainActivity is resumed — used to gate incoming-call ringing
     *  (calls ring ONLY inside an unlocked, foregrounded ChatRoot). */
    @Volatile var foreground: Boolean = false

    /** Set right before launching an external picker (photo) so onStop doesn't
     *  re-lock mid-pick; consumed by the very next onStop. */
    @Volatile var expectExternalActivity: Boolean = false

    /** Drop keys + decrypted state from RAM and snap back to the study facade. */
    fun lock(ctx: Context) {
        val c = ctx.applicationContext
        if (!Config.PREVIEW_UI_ONLY && unlocked && FirebaseEngine.signedIn) {
            FirebaseEngine.setPresence(false)
        }
        if (!Config.PREVIEW_UI_ONLY) FirebaseEngine.lock()
        // decrypted state must not be reconstructible after exit
        AppState.threads.value = emptyList()
        AppState.typing.value = emptyMap()
        unlocked = false
        if (!AppState.isFacade(AppState.screen)) AppState.instantGo(Screen.Facade)
    }

    /** Discreet quick exit from inside ChatRoot — identical to lock(). */
    fun quickExit(ctx: Context) {
        lock(ctx)
        AppState.toast("Back to study mode")
    }

    /**
     * Called after the gate password verifies. Resumes the ChatRoot session
     * (re-deriving keys from the local wrapped key store) or, when onboarding is
     * incomplete, routes into the onboarding flow instead.
     */
    suspend fun unlockFlow(ctx: Context) {
        val c = ctx.applicationContext
        val onboarded = c.getSharedPreferences("tiku_prefs", Context.MODE_PRIVATE)
            .getBoolean("onboarded", false)
        // from here on the user is inside the gated flow — any exit must re-lock
        unlocked = true
        if (Config.PREVIEW_UI_ONLY) {
            val seeded = PreviewStore.seedThreads()
            AppState.threads.value = seeded
            AppState.typing.value = emptyMap()
        } else {
            AppState.repo = FirebaseEngine
            FirebaseEngine.appCtx = c
            val signedIn = FirebaseEngine.signedIn
            val localKey = signedIn && FirebaseEngine.loadLocalIdentity(c)
            // ROUND 5: tri-state check — a FAILED account check (offline) used
            // to throw out of this flow; it must never be read as "new user".
            val returning = if (signedIn) FirebaseEngine.returningUserCheck() else false
            when {
                !signedIn -> { AppState.go(Screen.Welcome); return }
                onboarded && localKey -> {
                    FirebaseEngine.start(c)
                    FirebaseEngine.loadMyProfile()
                    FirebaseEngine.setPresence(true)
                }
                returning == true -> { AppState.go(Screen.Restore); return }
                returning == null -> {
                    AppState.toast("Couldn't check your account — check your connection and try again")
                    AppState.go(Screen.Welcome); return
                }
                else -> { AppState.go(Screen.Welcome); return }
            }
        }
        AppState.go(Screen.Home)
    }
}
