package com.tiku.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * ROUND 29 — CLOSED-APP ALERTS VIA A FOREGROUND SERVICE.
 *
 * History: Rounds 15–19 ran background alerts from a foreground
 * service; ROUND 20 removed that service entirely after the owner
 * rejected its standing shade banner ("notification bas tab hi ayega
 * jab koi message kare — baki kabhi nahi"), leaving the Round-19 delta
 * judge ([OwnerNotify]) purely in-process: alerts lived only while the
 * app process did. The owner's 2026-11-05 verdict reverses that trade
 * KNOWINGLY: with the chat UNLOCKED and Notifications ON, message,
 * voice-call and video-call alerts must arrive whether the app is open
 * or not — and he accepted the price in advance: one small permanent
 * silent shade entry while that state lasts, and no alerts at all
 * after a force-stop. (The FCM route was rejected — it needs a billing
 * account; the server holds ciphertext only, by design.)
 *
 * THE SERVICE'S LIFETIME IS THE UNLOCKED SESSION, exactly:
 *  - STARTS when the journal is unlocked AND the toggle is ON — at
 *    gate-unlock success ([NotifyGate.sessionStarted], hooked into
 *    ChatSession.unlockFlow), or when the toggle flips ON while
 *    already unlocked (the Settings rows call [NotifyGate.sync]).
 *  - STOPS on lock — the one-Back re-lock and every other path funnel
 *    through ChatSession.lock, which calls [NotifyGate.sessionEnded] —
 *    on toggle OFF ([NotifyGate.sync] sees the toggle down), and on
 *    sign-out (FirebaseEngine.signOut calls sessionEnded too).
 *  - NEVER starts at boot, before an unlock, or while locked: locked
 *    app = no service = no alerts. With the toggle OFF (the default)
 *    nothing in this file ever runs — no service, no entry, nothing.
 *
 * OWNERSHIP: while the service is up it owns the alert pipeline —
 * [OwnerNotify.pipelineLive] is true only under it, and the judge
 * refuses snapshots otherwise, so the UI path and the service path can
 * never double-alert (there is only ever one engine and one judge in
 * the process; the service merely guarantees they stay alive). When
 * the service cold-restarts into a fresh process (START_STICKY after
 * the system reaped the app), it re-validates the persisted gate
 * ([NotifyGate.shouldRunPersisted]: toggle + the persisted
 * session-unlocked flag + a live Firebase session) and re-bootstraps
 * the engine from the device-Keystore-wrapped identity — the journal
 * password is never needed, and never available, here.
 */
object NotifyGate {
    /** The persisted mirror of "a session was unlocked and has not
     *  been locked / signed out since". The in-process truth is
     *  [ChatSession.unlocked], but that dies with the process; this
     *  flag (own prefs file, COMMIT-ted at unlock) is what lets a
     *  system-restarted service tell a live session from a stale
     *  START_STICKY redelivery. Cleared ONLY at the authoritative
     *  ends — ChatSession.lock and FirebaseEngine.signOut — never by
     *  lifecycle reconciles: an activity relaunch landing on the
     *  locked facade must not silently kill a background session the
     *  lock never ended. */
    private const val SESSION_PREF = "tiku_alert_session"
    private const val KEY_UNLOCKED = "unlocked"

    fun sessionFlag(ctx: Context): Boolean =
        ctx.applicationContext.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_UNLOCKED, false)

    private fun setSessionFlag(ctx: Context, on: Boolean) {
        // commit(), not apply(): the flag is written precisely so it
        // survives the process dying a moment later.
        runCatching {
            ctx.applicationContext.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_UNLOCKED, on).commit()
        }
    }

    /** The in-process gate: alerts may run only inside a live,
     *  signed-in, unlocked session with the toggle ON. (This replaces
     *  the Round-18 gate, which keyed on the App-lock toggle instead:
     *  ROUND 29 law keys on the SESSION — with App lock OFF the
     *  session survives backgrounding, so the service does too; with
     *  App lock ON, leaving re-locks, the session ends, and the
     *  service stops with it.) */
    fun shouldRun(): Boolean =
        !Config.PREVIEW_UI_ONLY &&
            OwnerPrefs.notifyOn &&
            ChatSession.unlocked &&
            FirebaseEngine.signedIn

    /** The cold-start gate a (re)started service validates itself
     *  against: the same conditions, read from what survives a
     *  process death. If any leg is gone, the start was stale and the
     *  service stops itself immediately. */
    fun shouldRunPersisted(ctx: Context): Boolean =
        !Config.PREVIEW_UI_ONLY &&
            OwnerPrefs.notifyOn &&
            sessionFlag(ctx) &&
            FirebaseEngine.signedIn

    /** Gate-unlock success (ChatSession.unlockFlow): record the live
     *  session durably, then reconcile the service. */
    fun sessionStarted(ctx: Context) {
        if (Config.PREVIEW_UI_ONLY) return
        setSessionFlag(ctx, true)
        sync(ctx)
        // ROUND 49: the watchdog's slow safety net lives exactly as
        // long as an unlocked, toggle-on session does.
        AlertWatchdog.armPeriodic(ctx)
    }

    /** Lock / sign-out: the session is over — forget it durably and
     *  stop the service (its shade entry dies with it). */
    fun sessionEnded(ctx: Context) {
        if (Config.PREVIEW_UI_ONLY) return
        setSessionFlag(ctx, false)
        // ROUND 49: the session is over — no watchdog may outlive it.
        AlertWatchdog.disarm(ctx)
        TikuAlertService.stop(ctx)
    }

    /** ROUND 49 — the watchdog receiver's entry: revive the service
     *  if — and only if — the PERSISTED gate still holds (toggle +
     *  session-unlocked flag + live auth, the same legs a START_STICKY
     *  redelivery validates). A stale alarm (locked, toggled off,
     *  signed out while the process was dead) stops here and does
     *  nothing at all. */
    fun revive(ctx: Context) {
        if (Config.PREVIEW_UI_ONLY) return
        if (shouldRunPersisted(ctx)) TikuAlertService.start(ctx)
    }

    /** Reconcile hook, called from MainActivity's lifecycle and the
     *  Settings toggles, exactly as before. ROUND 29 semantics:
     *  starts the service when the gate holds; stops it when the
     *  toggle is OFF (the unambiguous end the toggle handlers mean);
     *  otherwise leaves the service exactly as it is — a live
     *  background session whose flag is set is not a lifecycle
     *  reconcile's to kill (lock and sign-out end it through
     *  [sessionEnded]). As since Round 19, a foreground sync also
     *  clears standing message alerts — opening the app means he has
     *  seen them. (On the onPause call foreground is already false, so
     *  leaving the app never clears anything.) */
    fun sync(ctx: Context) {
        if (Config.PREVIEW_UI_ONLY) return
        when {
            shouldRun() -> {
                setSessionFlag(ctx, true)
                TikuAlertService.start(ctx)
                // ROUND 49: every reconcile that finds the gate open
                // also (re)anchors the watchdog's slow net — this is
                // the path a toggle-ON-while-unlocked takes, and the
                // onPause sync anchors the net right as he walks away.
                AlertWatchdog.armPeriodic(ctx)
            }
            !OwnerPrefs.notifyOn -> {
                // ROUND 49: toggle OFF is an authoritative end too —
                // the watchdog must not keep a dead gate's alarms.
                AlertWatchdog.disarm(ctx)
                TikuAlertService.stop(ctx)
            }
        }
        if (ChatSession.foreground) OwnerNotify.clearStandingAlerts(ctx)
    }
}

/**
 * ROUND 49 — THE ALERT WATCHDOG (AlarmManager route; no WorkManager in
 * this build, and none is added for this).
 *
 * START_STICKY alone never guaranteed revival: an OEM task kill, a
 * force-stop-style swipe on some skins, or the Android 14 dataSync
 * time cap (see TikuAlertService.onTimeout) can end the service with
 * no redelivery ever coming. The watchdog is the second net, armed
 * ONLY while the persisted gate holds:
 *
 *  - a ONE-SHOT nudge ~90 s out, scheduled by the service's own death
 *    paths (onTaskRemoved, onDestroy-while-gate-on, onTimeout) — the
 *    quick revival for a fresh kill;
 *  - a SLOW PERIODIC net (inexact repeating, the platform's 15-minute
 *    floor) armed at unlock/toggle-on and disarmed at lock /
 *    toggle-off / sign-out — the backstop for a kill nobody saw.
 *
 * Both fire explicit intents at [AlertReviveReceiver], which routes
 * through [NotifyGate.revive]: every single revival re-validates the
 * persisted gate (toggle + session-unlocked flag + live auth) exactly
 * like a START_STICKY start, so a stale alarm self-neutralizes. The
 * alarms themselves carry no session state and grant nothing.
 */
object AlertWatchdog {
    private const val ACTION_REVIVE = "com.tiku.app.ALERT_REVIVE"
    private const val RC_ONESHOT = 0x49A1
    private const val RC_PERIODIC = 0x49A2
    private const val SOON_MS = 90_000L

    private fun pending(ctx: Context, rc: Int): PendingIntent {
        val intent = Intent(ctx.applicationContext, AlertReviveReceiver::class.java)
            .setAction(ACTION_REVIVE)
        return PendingIntent.getBroadcast(
            ctx.applicationContext, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarms(ctx: Context): AlarmManager? =
        ctx.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** One revival nudge ~90 s from now (inexact, Doze-tolerant). */
    fun scheduleSoon(ctx: Context) {
        runCatching {
            alarms(ctx)?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + SOON_MS,
                pending(ctx, RC_ONESHOT),
            )
        }
    }

    /** The slow safety net: inexact repeating at the platform floor. */
    fun armPeriodic(ctx: Context) {
        runCatching {
            alarms(ctx)?.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pending(ctx, RC_PERIODIC),
            )
        }
    }

    /** Gate closed (lock / toggle-off / sign-out): both nets down. */
    fun disarm(ctx: Context) {
        runCatching {
            val am = alarms(ctx) ?: return
            am.cancel(pending(ctx, RC_ONESHOT))
            am.cancel(pending(ctx, RC_PERIODIC))
        }
    }
}

/** ROUND 49 — the watchdog's ear (manifest-declared, not exported, no
 *  intent-filter: explicit AlarmManager intents only). A fire means
 *  one thing: re-validate the persisted gate and revive the alert
 *  service if it still holds. The periodic fire also re-arms itself,
 *  so the net survives anything short of the gate closing (which is
 *  what [AlertWatchdog.disarm] is for). */
class AlertReviveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching {
            if (Config.PREVIEW_UI_ONLY) return
            NotifyGate.revive(context)
            if (NotifyGate.shouldRunPersisted(context)) AlertWatchdog.armPeriodic(context)
        }
    }
}

/**
 * The foreground service that keeps the alert pipeline (engine
 * listeners + the Round-19 delta judge in [OwnerNotify]) alive while
 * an unlocked, notifications-on session outlives the UI. Main process,
 * dataSync type. Its ONLY visible trace is the mandated foreground
 * notification: a LOW-importance, silent, ongoing entry ("Tiku —
 * Notifications on") on its own channel — the standing entry the owner
 * explicitly accepted for this state, and the only one: when the
 * toggle is off or the session is locked, this service does not exist.
 */
class TikuAlertService : Service() {

    /** ROUND 49 — the listener-health keepalive: a cheap main-looper
     *  tick (no thread of its own) that asks the engine, every few
     *  minutes, whether its alert listeners have gone silent for too
     *  long (see FirebaseEngine.alertKeepaliveCheck). The tick itself
     *  fetches nothing; a healthy pipeline pays one timestamp compare
     *  per tick. Runs only while the service — and therefore the
     *  gate — lives; removed in onDestroy. */
    private val keepaliveHandler = Handler(Looper.getMainLooper())
    private val keepaliveTick = object : Runnable {
        override fun run() {
            runCatching {
                if (NotifyGate.shouldRunPersisted(this@TikuAlertService)) {
                    FirebaseEngine.alertKeepaliveCheck()
                    keepaliveHandler.postDelayed(this, KEEPALIVE_TICK_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The judge is service-owned from the moment the service
        // exists (see OwnerNotify.pipelineLive).
        OwnerNotify.pipelineLive = true
        keepaliveHandler.postDelayed(keepaliveTick, KEEPALIVE_TICK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-validate against the PERSISTED gate on every start —
        // including START_STICKY redeliveries (intent == null) after a
        // process death: if the session was locked, the toggle turned
        // off or the user signed out while the process was gone, this
        // start is stale and the service removes itself and its entry.
        if (!NotifyGate.shouldRunPersisted(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        OwnerNotify.pipelineLive = true
        // Foreground FIRST (the system gives a started-foreground
        // service only a few seconds to post its entry), engine after.
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_ID, persistentNotification(), TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_ID, persistentNotification())
        }
        bootstrapEngine()
        return START_STICKY
    }

    /** ROUND 49 — the Android 14+ dataSync time cap (~6 h per 24 h):
     *  when the budget runs out the system calls here and then stops
     *  the service — without a recovery, alerts would stay silent
     *  until the next app open however good the network is. Recovery:
     *  leave a watchdog nudge behind BEFORE going down (the gate is
     *  still on — the cap is a system budget, not a session end), then
     *  stop cleanly. The receiver re-validates the persisted gate and
     *  revives the service; the periodic net backs it up. (API 34
     *  callback; on older versions it simply never fires.) */
    override fun onTimeout(startId: Int) {
        runCatching { AlertWatchdog.scheduleSoon(this) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    /** ROUND 49 — a task swipe (recents) on OEM skins that kill the
     *  process with the task: if the gate is still on, leave the same
     *  revival nudge. An INTENTIONAL stop never reaches here with the
     *  gate on — lock and toggle-off close the gate first. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching {
            if (NotifyGate.shouldRunPersisted(this)) AlertWatchdog.scheduleSoon(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Pipeline ownership ends with the service; the judge goes
        // silent (the engine itself is the session's to stop — lock
        // and sign-out already did, on those paths).
        OwnerNotify.pipelineLive = false
        keepaliveHandler.removeCallbacks(keepaliveTick)
        // ROUND 49: if this destroy was NOT a session end — the gate's
        // persisted legs still hold (lock / toggle-off / sign-out all
        // clear them BEFORE stopping the service, so they read false
        // here) — the service was killed under a live gate (OEM killer,
        // system stop, the dataSync cap): leave a revival nudge.
        runCatching {
            if (NotifyGate.shouldRunPersisted(this)) AlertWatchdog.scheduleSoon(this)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** If the UI already started the engine (the normal in-app
     *  unlock), there is nothing to do — the service's job is only to
     *  keep that process (and its pipeline) alive. In a cold process
     *  (system restart after a reap) no UI ever ran: rebuild the
     *  session's engine side from what the device holds — the
     *  persisted Firebase Auth session and the Keystore-wrapped local
     *  identity (loadLocalIdentity needs NO journal password; it is
     *  the same load the gate-skip path performs). If either is
     *  missing the full session can't exist here yet (mid-sign-in, or
     *  a fresh device mid-restore): leave the engine down — the
     *  unlock flow starts it when it completes, into a pipeline that
     *  is already live. */
    private fun bootstrapEngine() {
        runCatching {
            if (FirebaseEngine.listenersActive) return
            val c = applicationContext
            FirebaseEngine.appCtx = c
            if (!FirebaseEngine.signedIn) return
            if (!FirebaseEngine.loadLocalIdentity(c)) return
            // Lets the engine's listener-error retries re-attach in
            // this UI-less process exactly as they do while unlocked.
            FirebaseEngine.serviceSession = true
            FirebaseEngine.start(c)
        }
    }

    /** The mandated standing entry — as quiet as the platform allows:
     *  LOW importance (no sound, no vibration, no heads-up), the app
     *  icon, "Tiku / Notifications on", tapping it opens the app. Its
     *  channel is created HERE, lazily, never at app startup. */
    private fun persistentNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(SERVICE_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, FOREGROUND_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("Tiku")
            .setContentText("Notifications on")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        /** ROUND 49: the listener-health tick spacing (the staleness
         *  threshold it enforces lives engine-side). */
        private const val KEEPALIVE_TICK_MS = 5 * 60_000L

        /** The standing entry's stable notification id. */
        private const val FOREGROUND_ID = 0x291D

        private const val SERVICE_CHANNEL_ID = "tiku_alert_service"

        /** ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC — the named
         *  constant is API 34; the VALUE (1) is stable since API 29
         *  introduced the typed startForeground overload. */
        private const val TYPE_DATA_SYNC = 1

        /** Intent extra (a ringing call's chat id) on the call-alert
         *  tap: MainActivity reads it on delivery and re-runs the
         *  engine start, whose fresh call-watcher snapshot hands the
         *  still-ringing call to the in-app ringing flow. */
        const val EXTRA_CALL_THREAD = "tiku_call_thread"

        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    ctx.applicationContext,
                    Intent(ctx.applicationContext, TikuAlertService::class.java),
                )
            }
        }

        fun stop(ctx: Context) {
            runCatching {
                ctx.applicationContext.stopService(
                    Intent(ctx.applicationContext, TikuAlertService::class.java)
                )
            }
        }
    }
}

/** Compatibility face kept from the service era: logout (Home.kt)
 *  still calls [NotifyService.stop] — it now stops the real Round-29
 *  service and additionally sweeps the pre-Round-20 banner id a very
 *  old install could in principle still show. */
object NotifyService {
    private const val LEGACY_FOREGROUND_ID = 0x2A15

    fun start(ctx: Context) = TikuAlertService.start(ctx)

    fun stop(ctx: Context) {
        TikuAlertService.stop(ctx)
        runCatching {
            (ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager)?.cancel(LEGACY_FOREGROUND_ID)
        }
    }
}
