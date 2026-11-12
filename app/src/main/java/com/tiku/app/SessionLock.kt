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

    /**
     * ROUND 69 — SYNCHRONOUS direct entry for app startup (MainActivity
     * calls this BEFORE setContent). Returns true only when the local
     * session was established synchronously and [AppState.screen] is
     * ALREADY [Screen.Home], so the very first composed frame is the
     * chat list and the facade is never composed at all.
     *
     * The gate decision itself is unchanged: this returns false unless
     * [OwnerGate.skipGate] — the exact synchronous check [openGate]
     * uses (App lock OFF on this device, no pending one-shot gate,
     * signed in, onboarded, local identity present) — passes, and
     * false again if the local identity fails to load. On false the
     * caller falls back to [openGate], whose behaviour (gate screen,
     * or the async [ChatSession.unlockFlow] routing to Welcome /
     * Restore on any doubt) is exactly as before: every failure here
     * fails CLOSED, toward more gate, never less.
     */
    fun openGateSync(ctx: Context): Boolean {
        if (!OwnerGate.skipGate(ctx)) return false
        return ChatSession.unlockLocalSession(ctx)
    }
}

/**
 * ROUND 9 — OWNER IDENTITY, in exactly ONE place. The owner account is the
 * signed-in Firebase Auth account whose EMAIL is [Config.OWNER_EMAIL] —
 * never a uid (uids change whenever an account is recreated; the email is
 * the stable identity the owner named). Since ROUND 18 the only surface
 * still keyed to it is the Me ▸ Owner settings section: the password gate
 * and the notification service became universal (every user's own
 * device-local toggles decide — see [OwnerGate] / OwnerPrefs), so they no
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
 * FAIL-SAFE defaults: until [init] has run the lock value reads as
 * App lock ON (= build-35 law), so an uninitialized read can only
 * ever mean MORE lock, never less. The notifications preference is
 * the exception by design (ROUND 63): it defaults ON — WhatsApp-style,
 * on out of the box for every user, and theirs to turn off.
 */
object OwnerPrefs {
    private const val PREF = "tiku_owner"
    private var prefs: SharedPreferences? = null

    /** ROUND 63 — THE per-user notifications preference (despite the
     *  store's legacy name, this is every signed-in user's switch,
     *  the owner included — Round 18 made this device-local store
     *  everyone's). DEFAULTS TO ON, in memory and in [init]: a user
     *  who never touched Settings gets notifications; turning the
     *  Settings ▸ Notifications switch off writes false here, and
     *  the FCM display gate (TikuFcmService.handle) reads this flag
     *  and shows nothing while it is off — exactly like muting
     *  everything. Storage stays the existing additive "notify" key;
     *  only its default changed (a device that never set it — the
     *  Round-62 frozen-on builds never wrote it from the switch —
     *  reads ON either way). */
    var notifyOn by mutableStateOf(true)
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
        notifyOn = p.getBoolean("notify", true)
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

    /** ROUND 57 (Fix 5): persist the owner's direct-entry choice.
     *  Named applySkipHome, NOT setSkipHome — the delegated property's
     *  own JVM setter already owns setSkipHome(Z)V (platform
     *  declaration clash, same trap as setGateOnce below; CI caught
     *  this on the first R57 run). */
    fun applySkipHome(on: Boolean) {
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
        // ROUND 58: lock() used to also end the alert session here
        // (NotifyGate.sessionEnded stopped the foreground service).
        // With FCM there is no service to stop and nothing to end:
        // pushes are gated at DISPLAY time in TikuFcmService (ROUND 63:
        // signed-in + local identity + the user's Notifications
        // preference, default ON), independent of the lock — that
        // independence is the point of the FCM switch.
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
        // ROUND 58: an unlock is where the push token is (re)stored —
        // the same bootstrap point where the deleted alert service
        // used to start. Fire-and-forget: a token failure never gates
        // the unlock or messaging. The Android 13+ notification
        // permission is also asked here, once — ROUND 62/63: for EVERY
        // user at first unlock (the in-app Notifications preference
        // defaults ON, and without the OS permission Android shows
        // nothing regardless) — but only when the caller handed us a
        // real Activity (the gate routes do; background callers pass
        // the app context and simply skip the ask).
        FcmTokenSync.register(c)
        (ctx as? android.app.Activity)?.let { PushAlerts.maybeRequestPermission(it) }
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
            if (signedIn && onboarded && localKey) {
                // ROUND 69 — FAST LANE, the old `onboarded && localKey`
                // branch taken WITHOUT the two network awaits that used
                // to precede/reach it: the old code awaited
                // returningUserCheck() BEFORE the `when`, even though
                // this branch never read its result (branch order made
                // it unreachable), and then awaited loadMyProfile()
                // before navigating — two sequential Firestore round
                // trips (~2 s) spent sitting on the previous screen.
                // Neither wait guarded anything: the profile lands in
                // observable state (AppState.myUsername / myAvatar) and
                // now loads fire-and-forget alongside the session, the
                // returning-user check still runs on every path that
                // actually routes by it (below), and the engine start +
                // presence happen exactly as before.
                FirebaseEngine.start(c)
                FirebaseEngine.setPresence(true)
                AppState.scope.launch { FirebaseEngine.loadMyProfile() }
            } else {
                // ROUND 5: tri-state check — a FAILED account check (offline) used
                // to throw out of this flow; it must never be read as "new user".
                val returning = if (signedIn) FirebaseEngine.returningUserCheck() else false
                when {
                    !signedIn -> { AppState.go(Screen.Welcome); return }
                    returning == true -> { AppState.go(Screen.Restore); return }
                    returning == null -> {
                        AppState.toast("Couldn't check your account — check your connection and try again")
                        AppState.go(Screen.Welcome); return
                    }
                    else -> { AppState.go(Screen.Welcome); return }
                }
            }
        }
        AppState.go(Screen.Home)
    }

    /**
     * ROUND 69 — the SYNCHRONOUS twin of [unlockFlow]'s fast lane, for
     * app startup only (via [SecretEntry.openGateSync], called from
     * MainActivity.onCreate BEFORE setContent). The caller has already
     * established — all synchronously, from persisted state — that the
     * gate may be skipped ([OwnerGate.skipGate]: App lock OFF, no
     * one-shot gate, signed in, onboarded, local identity present).
     * Everything this function does is local and non-suspending: the
     * Keystore unwrap of the on-device identity, the engine's listener
     * attach, the fire-and-forget presence/profile/token work — the
     * exact steps [unlockFlow]'s `onboarded && localKey` branch takes,
     * minus the network awaits that never guarded that branch. It ends
     * with [AppState.screen] set to [Screen.Home], so the first frame
     * composed after it is the chat list itself.
     *
     * Returns false — with [unlocked] never set and nothing navigated —
     * if the local identity fails to load despite being present (e.g.
     * corrupt wrapped material): the caller then falls back to the
     * ordinary gate route, which is also what the old async flow would
     * have routed by. Failing closed is deliberate.
     */
    fun unlockLocalSession(ctx: Context): Boolean {
        val c = ctx.applicationContext
        AppState.repo = FirebaseEngine
        FirebaseEngine.appCtx = c
        if (!FirebaseEngine.loadLocalIdentity(c)) return false
        // The gate was legitimately skipped, so — exactly as in
        // unlockFlow — any pending one-shot gate is consumed (skipGate
        // already required gateOnce == false, so this is inert today
        // and stays correct if that rule ever relaxes).
        OwnerPrefs.consumeGateOnce()
        // Same fire-and-forget unlock bootstrap as unlockFlow: the FCM
        // token re-store and the one-time notification-permission ask
        // (an Activity caller only). Neither ever gates the session.
        FcmTokenSync.register(c)
        (ctx as? android.app.Activity)?.let { PushAlerts.maybeRequestPermission(it) }
        // from here on the user is inside Tik — any exit must re-lock,
        // and the engine's sessionLive() gate reads this flag, so it is
        // set BEFORE the listeners attach (unlockFlow's order too).
        unlocked = true
        FirebaseEngine.start(c)
        FirebaseEngine.setPresence(true)
        AppState.scope.launch { FirebaseEngine.loadMyProfile() }
        AppState.instantGo(Screen.Home)
        return true
    }
}
