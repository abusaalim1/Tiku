package com.tiku.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ROUND 15 → ROUND 18 — BACKGROUND MESSAGE ALERTS (any user, opt-in).
 *
 * Round 15 built this for the owner account only ("Instagram jaisa — app
 * open na karu tab bhi turant notification aana chahiye"). ROUND 18
 * (owner's device-test orders on build 59) makes the gate UNIVERSAL: the
 * app-lock / notification choices are every user's now ("har koi unlock
 * ya lock kr sakta hai") — [OwnerState] is no longer consulted here. The
 * alert CONTENT also changed by his explicit order: alerts carry the
 * sender's name and the decrypted message text, Instagram-style — see
 * [OwnerNotify], which also owns the strict anti-false-alert ledger.
 *
 * What this service is: PROCESS LIFETIME for the engine's preview
 * listeners. The alert logic ([OwnerNotify]) only ever sees snapshots
 * while a process holds those listeners; this foreground service is the
 * free, no-server way to hold them while the app is closed. The
 * persistent banner it must post to stay alive stays generic ("Tiku —
 * Notifications on") — only the per-message alerts carry content.
 *
 * RUN GATE — the service runs only while ALL of these hold, and stops
 * the moment any one breaks (see [NotifyGate.sync], wired to the toggle
 * handlers, the explicit Lock-now / logout paths and MainActivity's
 * lifecycle — there are no polling loops):
 *   1. the Notifications toggle is ON ([OwnerPrefs.notifyOn]; default OFF);
 *   2. App lock is OFF ([OwnerPrefs.appLockOn] == false; default ON) —
 *      read from OwnerPrefs, the SINGLE source of truth the password gate
 *      itself reads via [OwnerGate]. (SettingsStore's "al" key is only a
 *      display mirror; the gate never reads it.)
 *   3. no pending one-shot gate from an explicit "Lock now"
 *      ([OwnerPrefs.gateOnce]).
 * Fresh install therefore means: service never runs, chat locked, alerts
 * off — exactly the defaults he asked for.
 *
 * While the in-app session is unlocked, the session itself owns the
 * engine and this service is pure keep-alive. In a cold process (the app
 * swiped away / process restarted by the system) the service bootstraps
 * the read-only half of an unlock — the SIGNED-IN USER's local identity +
 * engine listeners, mirroring [ChatSession.unlockFlow]'s returning-user
 * branch — WITHOUT unlocking the vault UI and WITHOUT setting presence:
 * the user is not in the app, and their contacts must not see them
 * "online" because a service is listening. Keys loaded this way are the
 * same device-wrapped identity the app itself uses; nothing new is
 * persisted.
 *
 * CRASH DISCIPLINE (Round 18 — "notification crash krta hai"): every
 * entry point is runCatching-wrapped. A half-ready engine, a missing
 * identity, or a foreground-start the OS refuses must make the service
 * skip or stop QUIETLY — it must never take the process down.
 *
 * Honest limits: this is Android's intended mechanism, not a hack — but a
 * user force-stop, or an aggressive OEM battery killer that ignores
 * foreground services, can still silence it until he next opens the app.
 */
object NotifyGate {
    /** The one place the run decision is made. Cheap and side-effect
     *  free; safe to call from any lifecycle point. */
    fun shouldRun(): Boolean =
        !Config.PREVIEW_UI_ONLY &&
            OwnerPrefs.notifyOn &&
            !OwnerPrefs.appLockOn &&
            !OwnerPrefs.gateOnce

    /** Start or stop the service so reality matches the gate. */
    fun sync(ctx: Context) {
        if (shouldRun()) NotifyService.start(ctx) else NotifyService.stop(ctx)
    }
}

class NotifyService : Service() {

    /** True when THIS service (not a live in-app session) brought the
     *  engine's listeners up in a cold process — [onDestroy] then owns
     *  tearing them down again, unless a session has since taken over. */
    private var engineBootstrapped = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ROUND 18 crash discipline: NOTHING in here may throw. Any
        // failure at all means "stop quietly", never a dead process.
        val proceed = runCatching {
            if (!NotifyGate.shouldRun()) {
                // The gate broke while we were away (toggle flipped, lock
                // re-engaged, account changed) — take the banner down
                // EXPLICITLY and die quietly. (His report: "band hota hai
                // toh wo uper screen se hathta nhi hai" — stopForeground
                // REMOVE makes the shade entry leave NOW, not whenever
                // the system gets around to reaping the service.)
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return START_NOT_STICKY
            }
            val fg = runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(
                        FOREGROUND_ID, foregroundNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(FOREGROUND_ID, foregroundNotification())
                }
            }
            if (fg.isFailure) {
                // The OS refused the foreground start (type limits,
                // background-start restrictions) — a service that cannot
                // go foreground must not linger half-alive.
                stopSelf()
                return START_NOT_STICKY
            }
            // A start that begins a NEW alert pipeline raises the
            // watermark BEFORE the first snapshot is judged, so
            // everything that arrived while the service was down is
            // backlog, never alerts. A re-delivered start on a pipeline
            // that is already alive (OwnerNotify.pipelineArmed) must NOT
            // re-arm — that could suppress a message still in flight.
            if (!OwnerNotify.pipelineArmed) OwnerNotify.arm(applicationContext)
            ensureEngine()
            true
        }.getOrDefault(false)
        if (!proceed) {
            // Something above failed unexpectedly — never linger half-up.
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }
        // STICKY: if the system kills the process under memory pressure it
        // recreates the service, and onStartCommand re-checks the gate and
        // re-bootstraps the listeners — alerts resume on their own.
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        if (engineBootstrapped && !ChatSession.unlocked) {
            // The listeners were ours alone; the live session (if one has
            // unlocked since) owns its own engine and is left untouched.
            runCatching { FirebaseEngine.stop() }
        }
        engineBootstrapped = false
        runCatching { serviceScope.cancel() }
        super.onDestroy()
    }

    /** Bring the engine's preview listeners up when no unlocked session
     *  is already running them. Mirrors unlockFlow's returning-user
     *  branch minus the UI unlock and minus presence. Works for ANY
     *  signed-in user — it loads whoever's device-wrapped local identity
     *  this phone holds. Fully guarded: a half-ready engine (signed out,
     *  no local identity yet, mid-teardown) just means "not this time". */
    private fun ensureEngine() {
        runCatching {
            if (engineBootstrapped) return
            if (ChatSession.unlocked) return // the session owns the engine
            val c = applicationContext
            FirebaseEngine.appCtx = c
            if (!FirebaseEngine.signedIn) return
            if (!FirebaseEngine.loadLocalIdentity(c)) return
            FirebaseEngine.start(c)
            serviceScope.launch { runCatching { FirebaseEngine.loadMyProfile() } }
            engineBootstrapped = true
        }
    }

    /** The persistent foreground-service notification: app name and a
     *  neutral line only. This banner is NOT an alert — it never carries
     *  chat content (the per-message alerts in [OwnerNotify] are the ones
     *  the owner ordered to carry sender + text in Round 18). */
    private fun foregroundNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            // Same channel OwnerNotify creates lazily for the alerts
            // (identical id/name/importance — channel creation is
            // idempotent, so there is only ever the one "Reminders").
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Tiku")
            .setContentText("Notifications on")
            .setContentIntent(pi)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tiku_reminders"

        /** Clear of OwnerNotify's per-thread alert ids (0x4000..0xBFFF). */
        private const val FOREGROUND_ID = 0x2A15

        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    ctx.applicationContext,
                    Intent(ctx.applicationContext, NotifyService::class.java),
                )
            }
        }

        fun stop(ctx: Context) {
            runCatching {
                ctx.applicationContext.stopService(
                    Intent(ctx.applicationContext, NotifyService::class.java),
                )
            }
        }
    }
}
