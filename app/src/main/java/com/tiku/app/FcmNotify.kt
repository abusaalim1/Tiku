package com.tiku.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ROUND 58 — FCM PUSH (the TikuAlertService replacement).
 *
 * The old system (Rounds 29–52) kept a foreground service + watchdog
 * alive for the whole unlocked session just so Firestore listeners
 * could judge new messages in-process and post alerts locally. That
 * service, its standing shade banner, its battery-exemption flow and
 * the in-process delta judge (the old OwnerNotify, deleted with it)
 * are GONE. Push now rides Firebase Cloud Messaging through the
 * tiku-notify Worker (see Config.NOTIFY_URL):
 *
 *  - SEND: after a message/call write lands in Firestore, the sender
 *    fires a best-effort POST to the Worker ([PushSender]) carrying
 *    only the message's EXISTING ciphertext for text — the Worker
 *    verifies the sender's ID token, checks chat membership, reads
 *    the receiver's stored token and forwards a data-only FCM push.
 *  - RECEIVE: [TikuFcmService] gets the data payload, gates it
 *    (owner = always on; everyone else = their Notifications toggle),
 *    decrypts text ON-DEVICE with the same engine machinery the old
 *    service bootstrap used (loadLocalIdentity + the conversation
 *    key + Crypto.decrypt — see FirebaseEngine.decryptForNotify),
 *    and posts the notification itself ([PushAlerts]).
 *  - TOKEN: the device's FCM token is stored on the user's own doc
 *    as the additive `fcmToken` field ([FcmTokenSync]) — old builds
 *    ignore it; the message schema is untouched.
 *
 * E2EE invariant: nothing here ever sees or moves plaintext off the
 * device — the push payload carries the message's stored ct/iv only,
 * and neither plaintext nor ciphertext is ever logged.
 */

/**
 * The notification POSTING half — channel management + the message
 * and incoming-call notifications. The builders/channels below are
 * the ones the deleted OwnerNotify judge posted through (same channel
 * ids, same ids-per-chat scheme), MOVED here, not duplicated: the
 * FCM service is now their only caller.
 */
object PushAlerts {
    /** Intent extra (a message alert's chat id) on the notification
     *  tap: MainActivity reads it and opens that exact chat. */
    const val EXTRA_CHAT_THREAD = "tiku_chat_thread"

    /** Intent extra (a ringing call's chat id) on the call-alert tap:
     *  MainActivity reads it on delivery and re-runs the engine start,
     *  whose fresh call-watcher snapshot hands the still-ringing call
     *  to the in-app ringing flow. (Same extra string the deleted
     *  TikuAlertService carried — the tap contract is unchanged.) */
    const val EXTRA_CALL_THREAD = "tiku_call_thread"

    /** Message alerts: the Round-49 HIGH-importance channel (heads-up
     *  like WhatsApp/Instagram; a channel's importance is immutable,
     *  which is why the id carries the v2 suffix from that round). */
    private const val CHANNEL_ID = "tiku_reminders_v2"

    /** Incoming-call alerts: the Round-52 SILENT channel (popup only,
     *  no ringtone ever — the owner's explicit order). The pre-52
     *  channel carried the default sound and is deleted at ensure
     *  time below, exactly as the old code did. */
    private const val CALL_CHANNEL_ID = "tiku_call_alerts_v2"
    private const val LEGACY_CALL_CHANNEL_ID = "tiku_call_alerts"

    private const val PERM_PREF = "tiku_push"
    private const val KEY_PERM_ASKED = "perm_asked"
    private const val RC_POST_NOTIFICATIONS = 0x5801

    /** Chats / calls with an alert currently standing on the shade in
     *  THIS process — cleared when the app comes to the foreground
     *  (opening the app means he has seen them). In-memory on purpose,
     *  exactly like the old judge's standing set. */
    private val standingThreads = mutableSetOf<String>()
    private val standingRings = mutableMapOf<String, String>() // callId -> threadId

    private fun messageNotifId(threadId: String): Int = (threadId.hashCode() and 0x3FFF) + 0x4000
    private fun ringNotifId(callId: String): Int = (callId.hashCode() and 0x3FFF) + 0xC000

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** ROUND 58: both alert channels are created at APP START
     *  (TikuApp.onCreate) instead of lazily at first post, so a push
     *  landing in a cold FCM-only process always finds them ready.
     *  Creating a channel the user can see in system settings is the
     *  only trace; nothing is posted here. */
    fun ensureChannels(ctx: Context) {
        runCatching {
            val nm = ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager ?: return
            ensureMessageChannel(nm)
            ensureCallChannel(nm)
        }
    }

    private fun ensureMessageChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
    }

    private fun ensureCallChannel(nm: NotificationManager) {
        // Pre-52 migration: the old channel carried the default
        // ringtone and channels are immutable — retire it so no stale
        // standing alert can ever sound through it again.
        runCatching { nm.deleteNotificationChannel(LEGACY_CALL_CHANNEL_ID) }
        if (nm.getNotificationChannel(CALL_CHANNEL_ID) != null) return
        val ch = NotificationChannel(CALL_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        // NO sound — the call alert is a visual popup only.
        ch.setSound(null, null)
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
    }

    /** Post (or replace) the ONE live message alert for [threadId] —
     *  a burst replaces, never stacks (same per-chat id scheme the old
     *  judge used). Grouping choice: simple per-chat replace, no
     *  MessagingStyle accumulation — one alert per chat, latest
     *  message wins, exactly the old system's shade behaviour. */
    fun postMessage(ctx: Context, threadId: String, title: String, body: String) {
        runCatching {
            if (!canPost(ctx)) return // OS permission not granted (yet) — stay silent
            val c = ctx.applicationContext
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureMessageChannel(nm)
            val intent = Intent(c, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CHAT_THREAD, threadId)
            }
            val pi = PendingIntent.getActivity(
                c, threadId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notif = Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                // A re-post under the same id updates content silently —
                // it must never buzz or peek a second time.
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build()
            synchronized(standingThreads) { standingThreads += threadId }
            nm.notify(messageNotifId(threadId), notif)
        }
    }

    /** The incoming-call presentation the old alert pipeline posted
     *  (Round 29, silent since Round 52): a HIGH-importance call
     *  notification that self-dismisses at the ring timeout; tapping
     *  it brings the app forward carrying the ringing chat's id, and
     *  the in-app ringing flow takes over from there. */
    fun postRing(ctx: Context, threadId: String, callId: String, video: Boolean, callerName: String) {
        runCatching {
            if (!canPost(ctx)) return
            val c = ctx.applicationContext
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureCallChannel(nm)
            val intent = Intent(c, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CALL_THREAD, threadId)
            }
            val pi = PendingIntent.getActivity(
                c, callId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val name = callerName.takeIf { it.isNotBlank() } ?: "Tiku"
            val notif = Notification.Builder(c, CALL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(name)
                .setContentText(if (video) "Incoming video call" else "Incoming voice call")
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                // The caller gives up at the ring timeout; the alert
                // must not outlive the call it announces.
                .setTimeoutAfter(Config.CALL_RING_TIMEOUT_MS)
                .build()
            synchronized(standingRings) { standingRings[callId] = threadId }
            nm.notify(ringNotifId(callId), notif)
        }
    }

    /** Opening the app means he has seen the standing alerts: cancel
     *  the ones this process posted. Called from MainActivity.onResume
     *  (the old judge's foreground clear, kept). */
    fun clearStanding(ctx: Context) {
        runCatching {
            val nm = ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager ?: return
            synchronized(standingThreads) {
                for (tid in standingThreads) runCatching { nm.cancel(messageNotifId(tid)) }
                standingThreads.clear()
            }
            synchronized(standingRings) {
                for (callId in standingRings.keys) runCatching { nm.cancel(ringNotifId(callId)) }
                standingRings.clear()
            }
        }
    }

    /** The Notifications toggle was flipped OFF: clear anything of
     *  ours still on the shade. */
    fun cancelAll(ctx: Context) {
        runCatching {
            (ctx.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager)?.cancelAll()
        }
    }

    /** Android 13+ runtime permission, asked ONCE from the unlock /
     *  resume path when this phone's display gate could actually show
     *  an alert (owner — always on — or a user whose Notifications
     *  toggle is ON). The Settings toggles keep their own immediate
     *  request on enable, exactly as before; this covers the owner,
     *  who never has to touch a toggle. A denial is remembered: the
     *  system dialog is never re-nagged from here. */
    fun maybeRequestPermission(activity: Activity) {
        runCatching {
            if (Build.VERSION.SDK_INT < 33) return
            if (Config.PREVIEW_UI_ONLY) return
            if (!OwnerState.isOwner && !OwnerPrefs.notifyOn) return
            if (ContextCompat.checkSelfPermission(
                    activity, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) return
            val prefs = activity.getSharedPreferences(PERM_PREF, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_PERM_ASKED, false)) return
            prefs.edit().putBoolean(KEY_PERM_ASKED, true).apply()
            activity.requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), RC_POST_NOTIFICATIONS)
        }
    }
}

/**
 * The FCM token's lifecycle. The token lives on the user's OWN doc as
 * `users/{uid}.fcmToken` (additive field, merge write; the deployed
 * rules already allow a user to write their own doc). Token success
 * NEVER gates messaging or unlock: every entry point is
 * fire-and-forget and runCatching-wrapped.
 */
object FcmTokenSync {
    /** Fetch this device's current token and store it. Called after
     *  the unlock bootstrap (ChatSession.unlockFlow — where the old
     *  alert service used to start) and when a Settings toggle turns
     *  notifications ON. */
    fun register(ctx: Context) {
        if (Config.PREVIEW_UI_ONLY) return
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> if (!token.isNullOrBlank()) save(token) }
        }
    }

    /** Store [token] on the signed-in user's own doc (also the
     *  onNewToken path). No-op while signed out — the next unlock's
     *  [register] stores the then-current token. */
    fun save(token: String) {
        runCatching {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection(Schema.Users.COLL).document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
        }
    }

    /** Sign-out cleanup (mirrors the engine's other sign-out state
     *  clearing): delete this device's token AND the stored field.
     *  Both operations are FIRED synchronously — not awaited — so they
     *  are enqueued under the still-live auth session before
     *  FirebaseEngine.signOut calls auth.signOut(). Best-effort: if
     *  the field delete is lost (offline), the Worker's
     *  token_cleaned path removes a dead token on its next send. */
    fun clearOnSignOut() {
        runCatching {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid
            if (uid != null) {
                runCatching {
                    FirebaseFirestore.getInstance().collection(Schema.Users.COLL).document(uid)
                        .update("fcmToken", FieldValue.delete())
                }
            }
            runCatching { FirebaseMessaging.getInstance().deleteToken() }
        }
    }
}

/**
 * The client trigger: after a message write (or a call placement)
 * succeeds in Firestore, tell the Worker to push the peer. STRICTLY
 * fire-and-forget — a background coroutine, one retry with a short
 * backoff (a forced ID-token refresh when the Worker answered 401),
 * and every failure swallowed: message sending must never block or
 * fail because a push could not be sent. Firestore stays the source
 * of truth; a dropped push is recovered on the peer's next sync.
 */
object PushSender {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [type] is the Worker's vocabulary: text / photo / video /
     *  voice / ai_text. [ct]/[iv] ride ONLY for the text types and are
     *  the message's own stored ciphertext — nothing else is sent. */
    fun notifyMessage(chatId: String, peerUid: String, msgId: String, type: String,
                      ct: String? = null, iv: String? = null) {
        if (Config.PREVIEW_UI_ONLY) return
        if (peerUid.isBlank() || chatId.isBlank() || msgId.isBlank()) return
        scope.launch { send(peerUid, chatId, msgId, type, ct, iv) }
    }

    /** An outgoing call was placed (its call doc exists): push the
     *  peer a ringing alert. The call id doubles as the msgId the
     *  Worker requires (dedupe/grouping on the receiving device). */
    fun notifyCall(chatId: String, peerUid: String, callId: String, video: Boolean) {
        if (Config.PREVIEW_UI_ONLY) return
        if (peerUid.isBlank() || chatId.isBlank() || callId.isBlank()) return
        scope.launch { send(peerUid, chatId, callId, if (video) "video_call" else "call", null, null) }
    }

    private suspend fun send(receiverId: String, chatId: String, msgId: String,
                             type: String, ct: String?, iv: String?) {
        runCatching {
            val user = FirebaseAuth.getInstance().currentUser ?: return
            val senderName = FirebaseEngine.myPerson.name
                .takeIf { it.isNotBlank() && it != "You" }
                ?: AppState.myUsername.takeIf { it.isNotBlank() }
                ?: "Tiku"
            val body = JSONObject().apply {
                put("receiverId", receiverId)
                put("chatId", chatId)
                put("msgId", msgId)
                put("senderName", senderName.take(60))
                put("type", type)
                if ((type == "text" || type == "ai_text") && !ct.isNullOrBlank() && !iv.isNullOrBlank()) {
                    put("ct", ct)
                    put("iv", iv)
                }
            }
            var forceRefresh = false
            for (attempt in 0..1) {
                val code = runCatching {
                    val token = user.getIdToken(forceRefresh).await().token ?: return
                    post(token, body)
                }.getOrDefault(-1)
                if (code in 200..299) return
                if (attempt == 0) {
                    // 401 = the ID token went stale between Firestore
                    // and here: the one retry carries a fresh one.
                    if (code == 401) forceRefresh = true
                    delay(1_200)
                }
            }
        }
    }

    /** One POST to the Worker; returns the HTTP status (or -1 via the
     *  caller's runCatching on any transport failure). The response
     *  body is drained, never logged — it carries no user content,
     *  and the discipline costs nothing. */
    private fun post(idToken: String, body: JSONObject): Int {
        val conn = URL(Config.NOTIFY_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
            }
            code
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * The receiving half. Data-only pushes from the Worker land here —
 * including in a cold process started by FCM alone, where no session
 * was ever unlocked in-process: the persisted Firebase Auth session
 * and the device-Keystore-wrapped local identity are what identify
 * this phone's account (the exact cold bootstrap the deleted alert
 * service performed). Display gate, in order:
 *
 *  1. signed out, or no local identity on this device → show nothing;
 *  2. the OWNER account → always show (no toggle can turn it off);
 *  3. any other user → show only while their Notifications toggle
 *     ([OwnerPrefs.notifyOn], default OFF) is on.
 *
 * A muted chat never pushes (same rule the old judge enforced, when
 * the thread is loaded in this process). The exact chat open in the
 * foreground suppresses its own alert. Calls present through
 * [PushAlerts.postRing] — the same incoming-call notification the old
 * service triggered — never as a plain message notification.
 */
class TikuFcmService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        FcmTokenSync.save(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return
        // Decryption may need a peer-key fetch: finish the platform
        // call now and hold the process with goAsync's result.
        val pending = goAsync()
        serviceScope.launch {
            try {
                handle(data)
            } catch (e: Exception) {
                // The push path can never crash the process; a failed
                // push simply shows nothing (Firestore still holds
                // the message for the next sync).
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun handle(data: Map<String, String>) {
        if (Config.PREVIEW_UI_ONLY) return
        val ctx = applicationContext
        FirebaseEngine.appCtx = ctx
        if (!FirebaseEngine.signedIn) return
        // The cold bootstrap: device-wrapped identity, NO journal
        // password (it is never available here, and never needed).
        if (!FirebaseEngine.loadLocalIdentity(ctx)) return
        if (!OwnerState.isOwner && !OwnerPrefs.notifyOn) return

        val chatId = data["chatId"]?.takeIf { it.isNotBlank() } ?: return
        val type = data["type"]?.takeIf { it.isNotBlank() } ?: "text"
        val senderName = data["senderName"]?.takeIf { it.isNotBlank() } ?: "Tiku"

        // Suppression: he is looking at this exact chat right now.
        if (ChatSession.foreground && FirebaseEngine.isChatOpen(chatId)) return
        // Muted chats never push (when the thread state is loaded in
        // this process; a cold process has no thread list and the
        // push shows — the pre-Round-58 judge could not run cold at
        // all, so this is strictly more coverage, never less).
        if (runCatching { AppState.thread(chatId)?.muted }.getOrNull() == true) return

        when (type) {
            "call", "video_call" -> PushAlerts.postRing(
                ctx, chatId, data["msgId"]?.takeIf { it.isNotBlank() } ?: chatId,
                video = type == "video_call", callerName = senderName,
            )
            else -> {
                val body = decryptedBody(chatId, type, data) ?: genericBody(type)
                PushAlerts.postMessage(ctx, chatId, senderName, body)
            }
        }
    }

    /** Text types: decrypt the payload's ct/iv with the conversation
     *  key — the engine's own derivation (ECDH + HKDF over the local
     *  identity and the peer's public key). Null on ANY failure
     *  (missing fields, key not ready, retired-key ciphertext): the
     *  caller falls back to the generic line, never to silence. */
    private suspend fun decryptedBody(chatId: String, type: String, data: Map<String, String>): String? {
        if (type != "text" && type != "ai_text") return null
        val ct = data["ct"]?.takeIf { it.isNotBlank() } ?: return null
        val iv = data["iv"]?.takeIf { it.isNotBlank() } ?: return null
        val peerUid = peerUidOf(chatId) ?: return null
        return FirebaseEngine.decryptForNotify(chatId, peerUid, ct, iv)
            ?.replace(Regex("\\s+"), " ")?.trim()?.take(140)
            ?.takeIf { it.isNotBlank() }
    }

    private fun genericBody(type: String): String = when (type) {
        "photo" -> "Sent you a photo"
        "video" -> "Sent you a video"
        "voice" -> "Sent you a voice message"
        else -> "New message"
    }

    /** The peer is the chat id's other member: chat ids are the
     *  sorted uid pair joined with "_" (FirebaseEngine.chatIdFor)
     *  and uids never contain "_". */
    private fun peerUidOf(chatId: String): String? {
        val me = FirebaseEngine.myUid()
        if (me.isBlank()) return null
        return chatId.split("_").firstOrNull { it.isNotBlank() && it != me }
    }
}
