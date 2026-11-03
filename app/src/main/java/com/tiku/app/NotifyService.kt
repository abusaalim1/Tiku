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

/**
 * ROUND 15 — BACKGROUND MESSAGE ALERTS FOR THE OWNER (his order after the
 * build-53 device test: "Instagram jaisa — app open na karu tab bhi turant
 * notification aana chahiye").
 *
 * The standing law is UNCHANGED for everyone else: ZERO notifications.
 * This is the same single exception Round 9 created (owner account, his
 * phone, opt-in), extended so the alerts survive the app being closed —
 * the missing piece was never the alert logic ([OwnerNotify] already had
 * it) but PROCESS LIFETIME: the engine's preview listeners die with the
 * process. This foreground service is the free, no-server way to hold
 * them: while it runs it keeps the same preview listeners alive and
 * [OwnerNotify] posts the same generic alerts ("Tiku" / "New message" /
 * "Missed call" — never a sender, never message text).
 *
 * RUN GATE — the service runs only while ALL of these hold, and stops the
 * moment any one breaks (see [NotifyGate.sync], wired to the toggle
 * handlers, the explicit Lock-now / logout paths and MainActivity's
 * lifecycle — there are no polling loops):
 *   1. the signed-in account is the owner ([OwnerState.isOwner]);
 *   2. his Notifications toggle is ON ([OwnerPrefs.notifyOn]; default OFF);
 *   3. his App lock is OFF ([OwnerPrefs.appLockOn] == false; default ON) —
 *      read from OwnerPrefs, the SINGLE source of truth the password gate
 *      itself reads via [OwnerGate]. (SettingsStore's "al" key is only a
 *      display mirror; the gate never reads it.)
 *   4. no pending one-shot gate from an explicit "Lock now"
 *      ([OwnerPrefs.gateOnce]).
 * Fresh install therefore means: service never runs, chat locked, alerts
 * off — exactly the defaults he asked for.
 *
 * While the in-app session is unlocked, the session itself owns the
 * engine and this service is pure keep-alive. In a cold process (the app
 * swiped away / process restarted by the system) the service bootstraps
 * the read-only half of an unlock — local identity + engine listeners,
 * mirroring [ChatSession.unlockFlow]'s returning-user branch — WITHOUT
 * unlocking the vault UI and WITHOUT setting presence: he is not in the
 * app, and his contacts must not see him "online" because a service is
 * listening. Keys loaded this way are the same device-wrapped identity
 * the app itself uses; nothing new is persisted.
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
            OwnerState.isOwner &&
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!NotifyGate.shouldRun()) {
            // The gate broke while we were away (toggle flipped, lock
            // re-engaged, account changed) — die quietly.
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                FOREGROUND_ID, foregroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(FOREGROUND_ID, foregroundNotification())
        }
        ensureEngine()
        // STICKY: if the system kills the process under memory pressure it
        // recreates the service, and onStartCommand re-checks the gate and
        // re-bootstraps the listeners — alerts resume on their own.
        return START_STICKY
    }

    override fun onDestroy() {
        if (engineBootstrapped && !ChatSession.unlocked) {
            // The listeners were ours alone; the live session (if one has
            // unlocked since) owns its own engine and is left untouched.
            runCatching { FirebaseEngine.stop() }
        }
        engineBootstrapped = false
        super.onDestroy()
    }

    /** Bring the engine's preview listeners up when no unlocked session
     *  is already running them. Mirrors unlockFlow's returning-user
     *  branch minus the UI unlock and minus presence. */
    private fun ensureEngine() {
        if (engineBootstrapped) return
        if (ChatSession.unlocked) return // the session owns the engine
        val c = applicationContext
        FirebaseEngine.appCtx = c
        if (!FirebaseEngine.signedIn) return
        if (!FirebaseEngine.loadLocalIdentity(c)) return
        FirebaseEngine.start(c)
        FirebaseEngine.loadMyProfile()
        engineBootstrapped = true
    }

    /** The persistent foreground-service notification: discreet by rule —
     *  app name and a neutral line only. No chat content, no sender
     *  names, ever (same law as the alerts themselves). */
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
