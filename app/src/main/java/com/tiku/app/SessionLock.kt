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
        // ROUND 9 → ROUND 18: any user's own phone may skip the password
        // prompt when THEY turned App lock OFF on this device (see
        // OwnerGate.skipGate for the full rule set — Round 18 made the
        // choice universal). Whenever any condition is unmet, the gate
        // shows exactly as before.
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
 * the stable identity the owner named). Since ROUND 18 the only surface
 * still keyed to it is the Me ▸ Owner settings section: the password gate
 * and the notification service became universal (every user's own
 * device-local toggles decide — see [OwnerGate] / NotifyGate), so they no
 * longer consult [isOwner].
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
 * ROUND 9 → ROUND 18 — the DEVICE-LOCAL choices (this phone's prefs file;
 * never synced, never on the server). Round 9 kept them for the owner
 * account; Round 18 made them every signed-in user's ("har koi unlock ya
 * lock kr sakta hai") — the Settings rows simply surface them to all.
 * FAIL-SAFE defaults: until [init] has run the values read as
 * notifications OFF and App lock ON (= build-35 law), so an uninitialized
 * read can only ever mean MORE lock, never less.
 */
object OwnerPrefs {
    private const val PREF = "tiku_owner"
    private var prefs: SharedPreferences? = null

    /** Opt-in message alerts on this phone. Default OFF for everyone. */
    var notifyOn by mutableStateOf(false)
        private set

    /** ON (default) = the password gate guards every entry. OFF = this
     *  device opens the signed-in user's journal with no prompt. */
    var appLockOn by mutableStateOf(true)
        private set

    /** ROUND 57 (Fix 5) — OWNER direct entry: ON (the default) = for
     *  the OWNER account only, opening the app routes straight to the
     *  episode gate (the sign-in box), skipping the facade double-tap.
     *  Stored in this same device-local prefs file (key "skip_home");
     *  both readers — the MainActivity entry routing and the Settings
     *  ▸ Owner row — additionally gate on [OwnerState.isOwner], so the
     *  stored value is inert for every other account, and the gate
     *  itself is untouched: the password is still required exactly as
     *  from the double-tap route. */
    var skipHome by mutableStateOf(true)
        private set

    /** One-shot gate: set by the explicit "Lock now" action; the NEXT
     *  entry must pass the password gate even with App lock OFF. Any
     *  successful unlock consumes it. Applies to every user (Round 18). */
    var gateOnce by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        notifyOn = p.getBoolean("notify", false)
        appLockOn = p.getBoolean("app_lock", true)
        skipHome = p.getBoolean("skip_home", true)
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

    /** ROUND 57 (Fix 5): persist the owner's direct-entry choice. */
    fun setSkipHome(on: Boolean) {
        skipHome = on
        prefs?.edit()?.putBoolean("skip_home", on)?.apply()
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

/** ROUND 9 → ROUND 18 — the gate-relaxation rules, in one place. Round 9
 *  scoped them to the owner account; ROUND 18 (owner's order: "har koi
 *  unlock ya lock kr sakta hai... default me bas option enable rahega and
 *  users usko disable bhi kr sakta hai") makes them UNIVERSAL — the
 *  device-local [OwnerPrefs] choices of whoever is signed in on THIS
 *  phone decide. [OwnerState] is deliberately NOT consulted here any
 *  more. Defaults are untouched: App lock ON, so out of the box every
 *  user still gets the password gate on every entry. */
object OwnerGate {
    /**
     * True when entering Tik may skip the password prompt entirely: the
     * signed-in user turned App lock OFF on this device AND no pending
     * one-shot gate (explicit "Lock now") AND this device finished setup
     * and still holds that user's local journal identity. Any doubt → the
     * gate shows. NOTE what this does NOT touch: the server-side key
     * backup stays wrapped under the user's password — a NEW device still
     * restores only with the password; gate-off is a convenience of THIS
     * device, never a weakening of the backup.
     */
    fun skipGate(ctx: Context): Boolean {
        if (OwnerPrefs.appLockOn) return false
        if (OwnerPrefs.gateOnce) return false
        if (Config.PREVIEW_UI_ONLY) return false
        if (!FirebaseEngine.signedIn) return false
        val onboarded = ctx.getSharedPreferences("tiku_prefs", Context.MODE_PRIVATE)
            .getBoolean("onboarded", false)
        if (!onboarded) return false
        return FirebaseEngine.hasLocalIdentity(ctx)
    }

    /** True when a live session may SURVIVE leaving Tik (backgrounding or
     *  the quick exit) instead of re-locking instantly: this device's
     *  App lock is OFF (any user, Round 18). With the toggle ON —
     *  including every fresh install — the session re-locks exactly as
     *  the standing law says. */
    fun lockOffActive(): Boolean = !OwnerPrefs.appLockOn
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
        // ROUND 29: this lock() is the choke point EVERY lock path
        // funnels through (one-Back re-lock, quick exit with App lock
        // on, backgrounding via MainActivity.onStop, "Lock now") — so
        // it is also where the alert session ends: the persisted
        // unlocked flag clears and the foreground service stops.
        // Locked app = no service = no alerts.
        NotifyGate.sessionEnded(c)
        // ROUND 10 (Watch lock safety): this lock() is the single choke point
        // every lock path funnels through (quick exit, Back-out, backgrounding
        // via MainActivity.onStop, the explicit "Lock now").
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
     *  ROUND 9 → ROUND 18: for ANY user with App lock OFF on this device,
     *  leaving to the facade is NOT a lock — the session (keys, threads,
     *  listeners) stays alive on this device and re-entry needs no
     *  password; only the explicit "Lock now" or clearing the app data
     *  ends it. Presence still drops (the user is no longer looking at
     *  the app). With App lock ON (the default) this is the
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
            AppState.toast("Back to Tiku")
            return
        }
        lock(ctx)
        AppState.toast("Back to Tiku")
    }

    /**
     * Called after the gate password verifies. Resumes the ChatRoot session
     * (re-deriving keys from the local wrapped key store) or, when onboarding is
     * incomplete, routes into the onboarding flow instead.
     */
    suspend fun unlockFlow(ctx: Context) {
        val c = ctx.applicationContext
        // ROUND 9: reaching here means an unlock succeeded (gate password,
        // restore password, or the App-lock-OFF gate-skip) — consume any
        // pending one-shot gate left by an explicit "Lock now".
        OwnerPrefs.consumeGateOnce()
        // ROUND 29: an unlock is the alert service's start signal —
        // record the live session (it can now outlive this process)
        // and reconcile the service with the Notifications toggle.
        NotifyGate.sessionStarted(c)
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
