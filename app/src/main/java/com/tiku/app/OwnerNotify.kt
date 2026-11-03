package com.tiku.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * ROUND 9 — THE OWNER'S OPT-IN ALERTS (his phone only).
 *
 * The standing law is unchanged for everyone else: ZERO notifications. This
 * object is the single exception, and it is inert unless ALL of these hold:
 * the signed-in account is the owner ([OwnerState.isOwner]) AND he flipped
 * the Owner ▸ Notifications toggle ON (default OFF, even for him).
 *
 * How it works: while his session is unlocked, the engine's per-chat preview
 * listeners (round 6) keep decrypting the newest messages on-device. Every
 * preview snapshot is offered here; the first snapshot per chat only
 * BASELINES the known message ids (history never alerts), and genuinely new
 * incoming items after that post a local notification. Because the feed is
 * the app's own listeners, alerts exist only while the app process is alive
 * (foreground or backgrounded) — there is no FCM and no server push: the
 * server holds ciphertext only and could not say what arrived anyway.
 * When the owner is looking at the app (foreground) nothing posts — the
 * in-app unread badges already tell him.
 *
 * Content is GENERIC by construction: this object never sees a notification
 * string other than "New message" / "Missed call" — no message text, no
 * sender name, no nickname ever reaches a notification.
 */
object OwnerNotify {
    private const val CHANNEL_ID = "tiku_reminders"

    /** threadId -> message ids already accounted for (baseline + alerted). */
    private val known = mutableMapOf<String, MutableSet<String>>()

    fun onPreviewMessages(ctx: Context, threadId: String, msgs: List<TikuMessage>) {
        if (!OwnerPrefs.notifyOn) return
        if (!OwnerState.isOwner) return
        if (ChatSession.foreground) {
            // He's looking at the app — badges do the job. Still absorb the
            // ids so these messages don't alert later from a stale baseline.
            known[threadId] = (known[threadId] ?: mutableSetOf()).apply {
                addAll(msgs.map { it.id })
            }
            return
        }
        val prev = known[threadId]
        if (prev == null) {
            // First snapshot for this chat since the session started:
            // everything in it is history — baseline only, never alert.
            known[threadId] = msgs.map { it.id }.toMutableSet()
            return
        }
        val fresh = msgs.filter { !it.me && it.id !in prev }
        prev.addAll(msgs.map { it.id })
        if (fresh.isEmpty()) return
        if (fresh.any { it.kind != MsgKind.CALL }) post(ctx, threadId, call = false)
        if (fresh.any { it.kind == MsgKind.CALL }) post(ctx, threadId, call = true)
    }

    /** Session died (lock / sign-out): forget the baselines so a future
     *  session re-baselines instead of alerting on old history. */
    fun reset() {
        known.clear()
    }

    /** The toggle was flipped OFF: clear anything still on the shade. */
    fun cancelAll(ctx: Context) {
        runCatching {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancelAll()
        }
    }

    private fun post(ctx: Context, threadId: String, call: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return // he hasn't granted the OS permission (yet) — stay silent
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, threadId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Tiku")
            .setContentText(if (call) "Missed call" else "New message")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        // One live alert per chat per kind — a burst replaces, never stacks.
        val id = (threadId.hashCode() and 0x3FFF) + if (call) 0x8000 else 0x4000
        runCatching { nm.notify(id, notif) }
    }

    /** The channel is created LAZILY, here, at the first actual alert — the
     *  app creates no notification channel at startup (see TikuApp), and for
     *  every non-owner account this code never runs at all. The name stays
     *  in the study spirit: it reads as a generic reminders channel. */
    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
