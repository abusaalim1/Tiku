package com.tiku.app

import android.app.NotificationManager
import android.content.Context

/**
 * ROUND 15 → 18 → 19 → ROUND 20 — MESSAGE ALERTS WITHOUT A SERVICE.
 *
 * Rounds 15–19 kept background alerts alive with a foreground service
 * ([NotifyService], dataSync type) that held the engine's preview
 * listeners while the app was closed. Android forces every foreground
 * service to post a PERSISTENT notification — and the owner's build-63
 * verdict on that banner is absolute: "notification bas tab hi ayega
 * jab koi message kare — baki kabhi nahi." A standing "notifications
 * are on" entry on his shade is a bug by definition now, however quiet.
 *
 * ROUND 20 therefore removes the service ENTIRELY: no service class,
 * no <service> element, no FOREGROUND_SERVICE permissions. The Round-19
 * delta judge in [OwnerNotify] runs purely IN-PROCESS, fed by the live
 * engine's preview hook ([FirebaseEngine] calls
 * [OwnerNotify.onPreviewMessages] from the same listeners the in-app
 * session already runs). While the app process lives — foreground, or
 * backgrounded and not yet reaped — alerts behave exactly as before:
 * one per genuinely-new message, sender + text, missed calls included,
 * standing alerts cleared when he opens the app.
 *
 * HONEST LIMIT (stated to the owner): with no service and no push
 * server (the server holds ciphertext only — FCM is impossible by
 * design), a force-stopped or swipe-killed app whose process the
 * system has reaped cannot alert until he next opens it. That is the
 * price of "no standing notification, ever", paid knowingly.
 *
 * This file keeps only the two public faces other files already call:
 *  - [NotifyGate] — the gate decision + lifecycle reconcile hook
 *    (MainActivity calls [NotifyGate.sync] from onCreate/onResume/
 *    onPause; the Settings toggles call it too). sync() starts NOTHING
 *    and posts NOTHING; its only side effect is clearing standing
 *    message alerts when the app is in the foreground (opening the app
 *    means he has seen them — Round 19 behaviour, previously delivered
 *    via the service's onStartCommand).
 *  - [NotifyService] — a compatibility stub: logout (Home.kt) still
 *    calls [NotifyService.stop]; it now just sweeps the LEGACY banner
 *    id a pre-Round-20 install could in principle still show.
 */
object NotifyGate {
    /** The alert gate, unchanged since Round 18 made it universal:
     *  alerts may run only while the Notifications toggle is ON
     *  ([OwnerPrefs.notifyOn]; default OFF), App lock is OFF
     *  ([OwnerPrefs.appLockOn] == false; default ON — OwnerPrefs is the
     *  single source of truth the password gate reads) and no one-shot
     *  "Lock now" gate is pending ([OwnerPrefs.gateOnce]). Cheap and
     *  side-effect free; safe to call from any lifecycle point. It now
     *  describes the IN-PROCESS judge only — there is no service left
     *  for it to start or stop. */
    fun shouldRun(): Boolean =
        !Config.PREVIEW_UI_ONLY &&
            OwnerPrefs.notifyOn &&
            !OwnerPrefs.appLockOn &&
            !OwnerPrefs.gateOnce

    /** Reconcile hook, kept source-compatible with every Round-15+
     *  call site. Starts no service and posts no notification — ever.
     *  Its single job: when the app is in the foreground, any standing
     *  message alerts are seen by definition, so clear them. (On the
     *  onPause call foreground is already false, so leaving the app
     *  never clears anything.) */
    fun sync(ctx: Context) {
        if (ChatSession.foreground) OwnerNotify.clearStandingAlerts(ctx)
    }
}

object NotifyService {
    /** The pre-Round-20 foreground banner's stable id (0x2A15). The
     *  system removes a foreground-service notification when the
     *  service dies — an app update kills the old process and with it
     *  the banner — so this cancel is pure belt-and-braces for the
     *  logout path Home.kt already wires to [stop]. */
    private const val LEGACY_FOREGROUND_ID = 0x2A15

    /** Nothing to start: there is no service anymore. Kept so any
     *  lingering call site stays source-compatible. */
    fun start(ctx: Context) = Unit

    /** Logout choke point (Home.kt): sweep the legacy banner id, just
     *  in case. Message alerts themselves are governed by the toggle /
     *  sign-out paths ([OwnerNotify.cancelAll], engine lock reset). */
    fun stop(ctx: Context) {
        runCatching {
            (ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager)?.cancel(LEGACY_FOREGROUND_ID)
        }
    }
}
