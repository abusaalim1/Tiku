package com.tiku.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * ROUND 32 — SCREEN SHARE FOREGROUND SERVICE.
 *
 * Android (14+ in particular) refuses MediaProjection unless the app
 * first runs a foreground service declared with the mediaProjection
 * type. This service is exactly that platform requirement and nothing
 * more: it owns NO capture state — the ScreenCapturerAndroid lives in
 * the call's [RtcSession], in this same process — it only has to EXIST
 * (with its type posted) before the projection is created and for as
 * long as the share runs.
 *
 * LIFETIME = one screen share, exactly:
 *  - STARTED by CallController.beginScreenShare, after the user grants
 *    the system consent dialog and BEFORE the capturer is created.
 *  - STOPPED when the share ends by ANY path: the in-call Stop button,
 *    the system share-chip revocation, call hangup / failure / remote
 *    end, and the session lock (all of those dispose the RtcSession or
 *    run the controller's share cleanup, and both stop this service).
 * It never starts at boot, never runs outside a live call, and its
 * shade entry (LOW importance, "Sharing your screen") is the visible
 * indicator the platform wants for an active projection.
 */
class ScreenShareService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground FIRST: a started-foreground service has only a few
        // seconds to post its entry, and the projection creation that
        // follows in the controller wants this service already typed
        // and running.
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_ID, shareNotification(), TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(FOREGROUND_ID, shareNotification())
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** The share's standing entry: LOW importance (silent, no
     *  heads-up), the app icon, and a tap that returns to the app (the
     *  live call screen / floating window is where stopping lives).
     *  Its channel is created HERE, lazily — never at app startup. */
    private fun shareNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(SERVICE_CHANNEL_ID, "Screen sharing", NotificationManager.IMPORTANCE_LOW)
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
            .setContentText("Sharing your screen")
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        /** The share entry's stable notification id. */
        private const val FOREGROUND_ID = 0x32A1

        private const val SERVICE_CHANNEL_ID = "tiku_screen_share"

        /** ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION — the
         *  named constant is API 34; the VALUE (2) is stable since
         *  API 29 introduced the typed startForeground overload. */
        private const val TYPE_MEDIA_PROJECTION = 2

        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    ctx.applicationContext,
                    Intent(ctx.applicationContext, ScreenShareService::class.java),
                )
            }
        }

        fun stop(ctx: Context) {
            runCatching {
                ctx.applicationContext.stopService(
                    Intent(ctx.applicationContext, ScreenShareService::class.java)
                )
            }
        }
    }
}
