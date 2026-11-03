package com.tiku.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SINGLE SECRET-ENTRY POINT.
 *
 * The "For You, Always" row on the K-drama facade's For You page calls
 * [openGate] — and only this function may route into the password gate.
 * (Round 3.2: the entry moved off the old study facade's home tools grid
 * into its Chemistry page as a plain "Reference books" row. ROUND 13: the
 * study facade is gone; the row is now a romance app's personal-corner
 * feature on the For You page — no lock, key or chat wording near it.)
 * If the owner ever swaps the trigger, change this one call site; nothing
 * else in the facade knows the gate exists.
 */
object SecretEntry {
    fun openGate(ctx: Context, scope: CoroutineScope) {
        // ROUND 9: the owner's own phone may skip the password prompt (his
        // App-lock toggle OFF — see OwnerGate.skipGate for the full rule set).
        // Every other account, and the owner whenever any condition is unmet,
        // lands on the gate exactly as before.
        if (OwnerGate.skipGate(ctx)) {
            scope.launch { ChatSession.unlockFlow(ctx) }
            return
        }
        AppState.go(Screen.TikGate)
    }
}

/**
 * ROUND 9 — OWNER IDENTITY, in exactly ONE place. The owner account is the
 * signed-in Firebase Auth account whose EMAIL is [Config.OWNER_EMAIL] —
 * never a uid (uids change whenever an account is recreated; the email is
 * the stable identity the owner named). Every owner-only surface (the Me ▸
 * Owner section, the gate skip, the notification hook) consults [isOwner]
 * here, so no surface can drift from the rule.
 */
object OwnerState {
    /** Screenshot-harness seam ONLY: when non-null it decides owner-ness
     *  without a live Firebase session (the Robolectric process has none) so
     *  the harness can photograph the owner / non-owner Me pair. Production
     *  code never sets it — it stays null and the real email check runs. */
    @Volatile var previewOverride: Boolean? = null

    val isOwner: Boolean
        get() = previewOverride
            ?: (!Config.PREVIEW_UI_ONLY &&
                FirebaseEngine.signedInEmail.equals(Config.OWNER_EMAIL, ignoreCase = true))
}

/**
 * ROUND 9 — the owner's device-local choices (his phone's prefs file; never
 * synced, never on the server). FAIL-SAFE defaults: until [init] has run —
 * and for every non-owner account, whose UI never touches these — the
 * values read as notifications OFF and App lock ON (= build-35 law), so an
 * uninitialized read can only ever mean MORE lock, never less.
 */
object OwnerPrefs {
    private const val PREF = "tiku_owner"
    private var prefs: SharedPreferences? = null

    /** Opt-in message alerts on the owner's phone. Default OFF — even for him. */
    var notifyOn by mutableStateOf(false)
        private set

    /** ON (default) = the password gate guards every entry, exactly as for
     *  everyone. OFF = this device opens his journal with no prompt. */
    var appLockOn by mutableStateOf(true)
        private set

    /** One-shot gate: set by the Owner section's explicit "Lock now"; the
     *  NEXT entry must pass the password gate even with App lock OFF. Any
     *  successful unlock consumes it. */
    var gateOnce by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        notifyOn = p.getBoolean("notify", false)
        appLockOn = p.getBoolean("app_lock", true)
        gateOnce = p.getBoolean("gate_once", false)
    }

    fun setNotify(on: Boolean) {
        notifyOn = on
        prefs?.edit()?.putBoolean("notify", on)?.apply()
    }

    fun setAppLock(on: Boolean) {
        appLockOn = on
        prefs?.edit()?.putBoolean("app_lock", on)?.apply()
    }

    // NOTE: no setGateOnce(Boolean) here — the delegated property's own JVM
    // setter already owns that name (platform declaration clash); the
    // one-shot is armed/consumed through this pair instead.
    fun requestGateOnce() {
        gateOnce = true
        prefs?.edit()?.putBoolean("gate_once", true)?.apply()
    }

    fun consumeGateOnce() {
        gateOnce = false
        prefs?.edit()?.putBoolean("gate_once", false)?.apply()
    }
}

/** ROUND 9 — the gate-relaxation rules for the owner account, in one place. */
object OwnerGate {
    /**
     * True when entering Tik may skip the password prompt entirely: owner
     * account AND his App lock OFF AND no pending one-shot gate (explicit
     * "Lock now") AND this device finished setup and still holds his local
     * journal identity. Any doubt → the gate shows. NOTE what this does NOT
     * touch: the server-side key backup stays wrapped under his password —
     * a NEW device still restores only with the password; gate-off is a
     * convenience of THIS device, never a weakening of the backup.
     */
    fun skipGate(ctx: Context): Boolean {
        if (!OwnerState.isOwner) return false
        if (OwnerPrefs.appLockOn) return false
        if (OwnerPrefs.gateOnce) return false
        if (Config.PREVIEW_UI_ONLY) return false
        if (!FirebaseEngine.signedIn) return false
        val onboarded = ctx.getSharedPreferences("tiku_prefs", Context.MODE_PRIVATE)
            .getBoolean("onboarded", false)
        if (!onboarded) return false
        return FirebaseEngine.hasLocalIdentity(ctx)
    }

    /** True when a live session may SURVIVE leaving Tik (backgrounding or the
     *  quick exit) instead of re-locking instantly: owner + App lock OFF.
     *  Everyone else — and the owner with the toggle ON — re-locks exactly
     *  as the standing law says. */
    fun lockOffActive(): Boolean = OwnerState.isOwner && !OwnerPrefs.appLockOn
}

/**
 * ROUND 8 — GATE GUESSING BACKOFF (item 9b). Consecutive failed password
 * attempts (journal gate AND backup restore — both are offline guessing
 * surfaces over the same secret) accumulate in plain prefs (counters only,
 * never password material). After the 2nd failure an escalating lockout
 * applies: 10s, 20s, 40s, 80s, 160s, then 5 min. The counters SURVIVE the
 * screen being closed/reopened (they live in prefs, not composition state),
 * and a successful open resets them. UX copy stays journal-framed.
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
 * or the app going to background — returns instantly to the drama facade, drops
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

    /** Drop keys + decrypted state from RAM and snap back to the drama facade. */
    fun lock(ctx: Context) {
        val c = ctx.applicationContext
        // ROUND 10 (Watch lock safety): this lock() is the single choke point
        // every lock path funnels through (quick exit, Back-out, backgrounding
        // via MainActivity.onStop, the owner's explicit "Lock now").
        // ROUND 13: the Watch WebView is owned by its pane — clearing the
        // session here (epoch bump) makes the pane's dispose-time save a
        // no-op, and leaving the vault uncomposes the pane, which destroys
        // the WebView — so no video frame and no audio can survive any lock.
        WatchSession.clear()
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

    /** Discreet quick exit from inside ChatRoot.
     *  ROUND 9: for the owner with App lock OFF, leaving to the facade is NOT
     *  a lock — the session (keys, threads, listeners) stays alive on this
     *  device and re-entry needs no password; only the Owner section's
     *  explicit "Lock now" or clearing the app data ends it. Presence still
     *  drops (he is no longer looking at the app). Everyone else gets the
     *  identical-to-lock() behaviour, as always. */
    fun quickExit(ctx: Context) {
        if (OwnerGate.lockOffActive()) {
            // ROUND 10: the relaxed exit keeps the chat SESSION alive, but a
            // playing Watch video must never leak audio into the facade —
            // the session is cleared here too, and the pane (uncomposed by
            // the navigation below) destroys its WebView on dispose (only
            // the keys and threads survive this path, never media).
            WatchSession.clear()
            if (!Config.PREVIEW_UI_ONLY && unlocked && FirebaseEngine.signedIn) {
                FirebaseEngine.setPresence(false)
            }
            if (!AppState.isFacade(AppState.screen)) AppState.instantGo(Screen.Facade)
            AppState.toast("Back to K-Tiku")
            return
        }
        lock(ctx)
        AppState.toast("Back to K-Tiku")
    }

    /**
     * Called after the gate password verifies. Resumes the ChatRoot session
     * (re-deriving keys from the local wrapped key store) or, when onboarding is
     * incomplete, routes into the onboarding flow instead.
     */
    suspend fun unlockFlow(ctx: Context) {
        val c = ctx.applicationContext
        // ROUND 9: reaching here means an unlock succeeded (gate password,
        // restore password, or the owner's gate-skip) — consume any pending
        // one-shot gate left by an explicit "Lock now".
        OwnerPrefs.consumeGateOnce()
        val onboarded = c.getSharedPreferences("tiku_prefs", Context.MODE_PRIVATE)
            .getBoolean("onboarded", false)
        // from here on the user is inside the gated flow — any exit must re-lock
        unlocked = true
        if (Config.PREVIEW_UI_ONLY) {
            val seeded = PreviewStore.seedThreads()
            AppState.threads.value = seeded
            AppState.typing.value = emptyMap()
            // ROUND 14: preview notes so the Messages notes row renders.
            AppState.notes.value = PreviewStore.seedNotes()
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
