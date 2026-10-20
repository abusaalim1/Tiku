package com.tiku.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKey

/**
 * REAL-mode engine: Firebase Auth + Firestore (Spark plan — no Firebase Storage;
 * media rides inside Firestore via MediaStore chunks). Server holds ciphertext only.
 *   users/{uid} {username, displayName, photoData, publicKey, keyBackup{salt,iv,ct}, online, lastSeen}
 *   usernames/{username} -> {uid}
 *   chats/{chatId} {members:[uidA, uidB], typing:{uid:bool}, clearedBefore:{uid:ms}, updatedAt}
 *   chats/{chatId}/messages/{id} {senderId, kind, ct, iv, ts, rqCt, rqIv, reaction:{uid:emoji},
 *       starredBy:[], deletedFor:[], seenBy:[], durationMs, amps, mediaChunks}
 *   chats/{chatId}/messages/{id}/chunks/{i} {d: base64 chunk of ciphertext}
 *   chats/{chatId}/calls/{callId} {by, peerUid, kind, direction, at}
 *   moments/{uid}/items/{itemId} {quote, ts}
 */
object FirebaseEngine : ChatRepo {
    lateinit var appCtx: Context

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    val uid: String get() = auth.currentUser?.uid ?: ""
    val signedIn: Boolean get() = auth.currentUser != null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---------- identity ----------
    private var myPrivBytes: ByteArray? = null
    private val convKeyCache = mutableMapOf<String, SecretKey>()
    private val profileCache = mutableMapOf<String, Person>()
    private val publicKeyCache = mutableMapOf<String, String>()
    var myPerson: Person = Person(name = "You")

    private fun securePrefs(ctx: Context) =
        ctx.getSharedPreferences("tiku_secure", Context.MODE_PRIVATE)

    fun chatIdFor(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    // ================= AUTH =================
    suspend fun googleSignIn(activity: Activity): Result<Unit> {
        return try {
        val nonce = UUID.randomUUID().toString()
        val option = GetSignInWithGoogleOption.Builder(Config.WEB_CLIENT_ID).setNonce(nonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val cred = result.credential as? CustomCredential
            ?: return Result.failure(IllegalStateException("Not a Google credential"))
        if (cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            return Result.failure(IllegalStateException("Wrong credential type"))
        val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isReturningUser(): Boolean {
        if (uid.isEmpty()) return false
        return db.collection(Schema.Users.COLL).document(uid).get().await().exists()
    }

    suspend fun backupBlob(): Map<String, String>? {
        if (uid.isEmpty()) return null
        val snap = db.collection(Schema.Users.COLL).document(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        return snap.get("keyBackup") as? Map<String, String>
    }

    /** Load the local device-wrapped identity; false when this is a new device. */
    fun loadLocalIdentity(ctx: Context): Boolean {
        val sp = securePrefs(ctx)
        val iv = sp.getString("idk_iv", null); val ct = sp.getString("idk_ct", null)
        if (iv == null || ct == null) return false
        return try {
            myPrivBytes = Crypto.deviceUnwrap(iv, ct)
            true
        } catch (e: Exception) { false }
    }

    /**
     * Restore identity from the password-wrapped backup. Wrong password -> false
     * (recovery is impossible without the backup password, by design).
     */
    suspend fun restoreFromBackup(ctx: Context, password: String): Boolean {
        val blob = backupBlob() ?: return false
        return try {
            val priv = Crypto.backupUnwrap(blob, password)
            Crypto.privateKeyFromPkcs8(priv) // validates the key material
            myPrivBytes = priv
            val (iv, ct) = Crypto.deviceWrap(priv)
            securePrefs(ctx).edit().putString("idk_iv", iv).putString("idk_ct", ct)
                .putString("pw_hash", Crypto.passwordHash(password)).apply()
            true
        } catch (e: Exception) { false }
    }

    /** New user: generate identity, claim username, write user doc + backup. */
    suspend fun finishNewUser(ctx: Context, password: String, username: String, avatarBytes: ByteArray?): Result<Unit> {
        return try {
            val u = username.lowercase()
            db.runTransaction { tx ->
                val claimRef = db.collection(Schema.Users.USERNAME_CLAIM).document(u)
                if (tx.get(claimRef).exists()) throw IllegalStateException("@$u is taken")
                tx.set(claimRef, mapOf("uid" to uid, "at" to FieldValue.serverTimestamp()))
            }.await()
            val kp = Crypto.generateIdentity()
            val priv = kp.private.encoded
            myPrivBytes = priv
            val blob = Crypto.backupWrap(priv, password)
            val (iv, ct) = Crypto.deviceWrap(priv)
            securePrefs(ctx).edit().putString("idk_iv", iv).putString("idk_ct", ct)
                .putString("pw_hash", Crypto.passwordHash(password)).apply()
            val photoData = avatarBytes?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.let { b -> MediaStore.compressAvatar(b) }
            } ?: ""
            myPerson = Person(uid = uid, name = u, username = u,
                photoBitmap = photoData.takeIf { it.isNotEmpty() }?.let { MediaStore.decodeBitmap(it) })
            db.collection(Schema.Users.COLL).document(uid).set(mapOf(
                "username" to u, "displayName" to u, "photoData" to photoData,
                "publicKey" to Crypto.publicKeyB64(kp), "keyBackup" to blob,
                "online" to true, "lastSeen" to FieldValue.serverTimestamp(),
            )).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun usernameTaken(u: String): Boolean =
        db.collection(Schema.Users.USERNAME_CLAIM).document(u.lowercase()).get().await().exists()

    suspend fun loadMyProfile() {
        if (uid.isEmpty()) return
        runCatching {
            val snap = db.collection(Schema.Users.COLL).document(uid).get().await()
            val photoData = snap.getString("photoData").orEmpty()
            myPerson = Person(
                uid = uid,
                name = snap.getString("displayName").orEmpty(),
                username = snap.getString("username").orEmpty(),
                photoBitmap = photoData.takeIf { it.isNotEmpty() }?.let { MediaStore.decodeBitmap(it) },
            )
            AppState.myUsername = myPerson.username
            AppState.myAvatar = myPerson.photoBitmap
        }
    }

    suspend fun lookupPerson(username: String): Person? {
        val claim = db.collection(Schema.Users.USERNAME_CLAIM).document(username.lowercase()).get().await()
        val peerUid = claim.getString("uid") ?: return null
        if (peerUid == uid) return null
        return fetchPerson(peerUid)
    }

    private suspend fun fetchPerson(peerUid: String): Person {
        profileCache[peerUid]?.let { return it }
        val snap = db.collection(Schema.Users.COLL).document(peerUid).get().await()
        val photoData = snap.getString("photoData").orEmpty()
        val p = Person(
            uid = peerUid,
            name = snap.getString("displayName").orEmpty().ifEmpty { snap.getString("username").orEmpty() },
            username = snap.getString("username").orEmpty(),
            emoji = "🙂",
            colorArgb = avatarColorFor(peerUid),
            photoBitmap = photoData.takeIf { it.isNotEmpty() }?.let { MediaStore.decodeBitmap(it) },
            online = snap.getBoolean("online") == true,
            lastSeenMs = snap.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
        )
        snap.getString("publicKey")?.let { if (it.isNotEmpty()) publicKeyCache[peerUid] = it }
        profileCache[peerUid] = p
        return p
    }

    private fun avatarColorFor(peerUid: String): Int {
        val palette = listOf(0xFFE4714C, 0xFFD98FA0, 0xFFE0A04A, 0xFFB9876F, 0xFFC45A5A, 0xFFC9B13A)
        return palette[(peerUid.hashCode() and Int.MAX_VALUE) % palette.size].toInt()
    }

    private suspend fun convKey(chatId: String, peerUid: String): SecretKey {
        convKeyCache[chatId]?.let { return it }
        val priv = myPrivBytes ?: throw IllegalStateException("Identity not loaded")
        val peerPub = publicKeyCache[peerUid] ?: run {
            fetchPerson(peerUid); publicKeyCache[peerUid]
        } ?: throw IllegalStateException("Peer public key missing")
        val key = Crypto.conversationKey(priv, peerPub, uid, peerUid)
        convKeyCache[chatId] = key
        return key
    }

    // ================= LISTENERS =================
    private var threadsReg: ListenerRegistration? = null
    private var msgsReg: ListenerRegistration? = null
    private var readPrefs: android.content.SharedPreferences? = null
    private val openChatIds = mutableSetOf<String>()

    fun lastRead(threadId: String): Long = readPrefs?.getLong("read_$threadId", 0L) ?: 0L
    fun markRead(threadId: String) {
        readPrefs?.edit()?.putLong("read_$threadId", System.currentTimeMillis())?.apply()
    }

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        readPrefs = ctx.getSharedPreferences("tiku_read", Context.MODE_PRIVATE)
        if (uid.isEmpty()) return
        threadsReg?.remove()
        threadsReg = db.collection(Schema.Chats.COLL)
            .whereArrayContains("members", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                scope.launch {
                    val list = snap?.documents?.mapNotNull { doc ->
                        @Suppress("UNCHECKED_CAST")
                        val parts = doc.get("members") as? List<String> ?: return@mapNotNull null
                        val peerUid = parts.firstOrNull { it != uid } ?: return@mapNotNull null
                        val peer = runCatching { fetchPerson(peerUid) }.getOrNull() ?: return@mapNotNull null
                        val ts = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                        val existing = AppState.thread(doc.id)
                        ChatThread(id = doc.id, person = peer,
                            messages = existing?.messages ?: mutableListOf(),
                            unread = existing?.unread ?: 0,
                            time = fmtTime(ts),
                            pinned = existing?.pinned ?: false)
                    } ?: emptyList()
                    AppState.threads.value = list
                }
            }
    }

    fun stop() {
        threadsReg?.remove(); msgsReg?.remove()
    }

    /** INSTANT RE-LOCK: stop listeners, zeroize the private key + conversation keys in RAM,
     *  drop all caches. After this the session is cryptographically dead. */
    fun lock() {
        stop()
        threadsReg = null; msgsReg = null
        runCatching { myPrivBytes?.fill(0) }
        myPrivBytes = null
        convKeyCache.clear(); profileCache.clear(); publicKeyCache.clear()
        openChatIds.clear()
    }

    fun setPresence(online: Boolean) {
        if (uid.isEmpty()) return
        val updates = mutableMapOf<String, Any>("online" to online)
        if (!online && SettingsStore.state.value.lastSeen) updates["lastSeen"] = FieldValue.serverTimestamp()
        runCatching { db.collection(Schema.Users.COLL).document(uid).update(updates) }
    }

    // ================= CHATREPO =================
    override fun closeThread(id: String) { openChatIds -= id }

    override fun openThread(id: String) {
        markRead(id)
        openChatIds += id
        attachMessages(id)
        if (SettingsStore.state.value.readReceipts) {
            scope.launch {
                val unseen = db.collection(Schema.Chats.COLL).document(id)
                    .collection(Schema.Chats.MESSAGES)
                    .whereNotEqualTo("senderId", uid).get().await()
                    .documents.filter { !((it.get("seenBy") as? List<*>) ?: emptyList<Any>()).contains(uid) }
                if (unseen.isNotEmpty()) {
                    val batch = db.batch()
                    unseen.forEach { batch.update(it.reference, "seenBy", FieldValue.arrayUnion(uid)) }
                    batch.commit().await()
                }
            }
        }
    }

    private fun attachMessages(id: String) {
        msgsReg?.remove()
        val peerUid = AppState.thread(id)?.person?.uid ?: return
        msgsReg = db.collection(Schema.Chats.COLL).document(id)
            .collection(Schema.Chats.MESSAGES)
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val chatDocId = id
                scope.launch {
                    val peer = fetchPerson(peerUid)
                    val key = convKey(id, peerUid)
                    val clearedBefore = runCatching {
                        val cd = db.collection(Schema.Chats.COLL).document(id).get().await()
                            .get("clearedBefore") as? Map<*, *>
                        (cd?.get(uid) as? Number)?.toLong() ?: 0L
                    }.getOrDefault(0L)
                    val dmCutoff = if (SettingsStore.state.value.toggles["dm"] == true)
                        System.currentTimeMillis() - 24L * 3600 * 1000 else 0L
                    val msgs = snap?.documents?.mapNotNull { doc ->
                        runCatching { decryptDoc(id, doc, key) }.getOrNull()
                    }?.filter { m ->
                        !m.deletedForMe && m.tsMs >= clearedBefore && (dmCutoff == 0L || m.tsMs >= dmCutoff)
                    } ?: emptyList()
                    // peer typing indicator -> chat header status
                    runCatching {
                        val chatDoc = db.collection(Schema.Chats.COLL).document(chatDocId).get().await()
                        val typing = (chatDoc.get("typing") as? Map<*, *>)?.get(peerUid) as? Boolean ?: false
                        AppState.setTyping(chatDocId, typing)
                        // seen-by for my messages (✓✓ state rides on TikuMessage via seenBy of last mine message)
                    }
                    val lr = lastRead(id)
                    AppState.threads.value = AppState.threads.value.map { t ->
                        if (t.id == id) t.copy(messages = msgs.toMutableList(),
                            unread = if (openChatIds.contains(id)) 0 else msgs.count { !it.me && it.tsMs > lr })
                        else t
                    }
                    if (openChatIds.contains(id)) markRead(id)
                    @Suppress("UNUSED_VARIABLE") val unused = peer
                }
            }
    }

    private suspend fun decryptDoc(chatId: String, doc: com.google.firebase.firestore.DocumentSnapshot, key: SecretKey): TikuMessage {
        val sender = doc.getString("senderId") ?: ""
        val kindRaw = doc.getString("kind") ?: "text"
        val me = sender == uid
        @Suppress("UNCHECKED_CAST")
        val reactions = (doc.get("reaction") as? Map<String, String>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val starredBy = (doc.get("starredBy") as? List<String>) ?: emptyList<String>()
        @Suppress("UNCHECKED_CAST")
        val deletedFor = (doc.get("deletedFor") as? List<String>) ?: emptyList<String>()
        val tsMs = doc.getTimestamp("ts")?.toDate()?.time ?: 0L
        val replyQuote = doc.getString("rqCt")?.takeIf { it.isNotEmpty() }?.let {
            runCatching { Crypto.decryptText(key, doc.getString("rqIv") ?: "", it) }.getOrNull()
        }
        var photo: Bitmap? = null
        var text = ""
        var durSec = 0
        var amps: List<Int> = emptyList()
        var localPath: String? = null
        when (kindRaw) {
            "text" -> text = Crypto.decryptText(key, doc.getString("iv") ?: "", doc.getString("ct") ?: "")
            "image", "voice" -> {
                val chunkCount = (doc.getLong("mediaChunks") ?: 0L).toInt()
                val ctBytes = MediaStore.download(db, chatId, doc.id, chunkCount)
                val plain = Crypto.decrypt(key, doc.getString("iv") ?: "", Crypto.b64(ctBytes))
                if (kindRaw == "image") {
                    photo = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                } else {
                    durSec = ((doc.getLong("durationMs") ?: 0L) / 1000).toInt()
                    @Suppress("UNCHECKED_CAST")
                    amps = (doc.get("amps") as? List<Long>)?.map { it.toInt() } ?: emptyList()
                    val f = File(appCtx.cacheDir, "dl_${doc.id}.m4a")
                    if (!f.exists()) f.writeBytes(plain)
                    localPath = f.absolutePath
                }
            }
        }
        val kind = when (kindRaw) { "image" -> MsgKind.IMAGE; "voice" -> MsgKind.VOICE; else -> MsgKind.TEXT }
        return TikuMessage(doc.id, sender, kind, text, fmtTime(tsMs), me, durSec, amps,
            photo, replyQuote, reactions, starredBy.contains(uid), deletedFor.contains(uid), tsMs, localPath)
    }

    override fun sendText(id: String, text: String, quote: String?) {
        scope.launch {
            val th = AppState.thread(id) ?: return@launch
            val key = convKey(id, th.person.uid)
            val (iv, ct) = Crypto.encryptText(key, text)
            val (rqIv, rqCt) = if (quote != null) Crypto.encryptText(key, quote) else ("" to "")
            db.collection(Schema.Chats.COLL).document(id).collection(Schema.Chats.MESSAGES).add(mapOf(
                "senderId" to uid, "kind" to "text", "ct" to ct, "iv" to iv,
                "ts" to FieldValue.serverTimestamp(),
                "rqCt" to rqCt, "rqIv" to rqIv,
                "reaction" to emptyMap<String, String>(), "starredBy" to emptyList<String>(),
                "deletedFor" to emptyList<String>(), "seenBy" to listOf(uid),
                "durationMs" to 0, "amps" to emptyList<Int>(), "mediaChunks" to 0,
            )).await()
            touchChat(id)
        }
    }

    private suspend fun sendMedia(id: String, kind: String, plainBytes: ByteArray, durMs: Int = 0, amps: List<Int> = emptyList()) {
        val th = AppState.thread(id) ?: return
        val key = convKey(id, th.person.uid)
        val (iv, ctB64) = Crypto.encrypt(key, plainBytes)
        val ctBytes = Crypto.unb64(ctB64)
        val msgRef = db.collection(Schema.Chats.COLL).document(id)
            .collection(Schema.Chats.MESSAGES).document()
        msgRef.set(mapOf(
            "senderId" to uid, "kind" to kind, "ct" to "", "iv" to iv,
            "ts" to FieldValue.serverTimestamp(),
            "rqCt" to "", "rqIv" to "",
            "reaction" to emptyMap<String, String>(), "starredBy" to emptyList<String>(),
            "deletedFor" to emptyList<String>(), "seenBy" to listOf(uid),
            "durationMs" to durMs, "amps" to amps, "mediaChunks" to 0,
        )).await()
        val chunks = MediaStore.upload(db, id, msgRef.id, ctBytes)
        msgRef.update("mediaChunks", chunks).await()
        touchChat(id)
    }

    /** Photo pipeline: downscale <=1280px -> JPEG q80 -> encrypt -> chunk-upload. */
    override fun sendPhoto(id: String, bmp: Bitmap) {
        scope.launch { sendMedia(id, "image", MediaStore.compressPhoto(bmp)) }
    }

    override fun sendVoice(id: String, audioFile: File?, durSec: Int, amps: List<Int>) {
        scope.launch {
            if (audioFile == null || !audioFile.exists()) return@launch
            try { sendMedia(id, "voice", audioFile.readBytes(), durSec * 1000, amps) }
            finally { audioFile.delete() }
        }
    }

    private suspend fun touchChat(id: String) {
        runCatching { db.collection(Schema.Chats.COLL).document(id)
            .update("updatedAt", FieldValue.serverTimestamp()).await() }
        setTypingRaw(id, false)
    }

    private fun msgRef(id: String, msgId: String) =
        db.collection(Schema.Chats.COLL).document(id).collection(Schema.Chats.MESSAGES).document(msgId)

    override fun react(id: String, msgId: String, emoji: String?) {
        scope.launch {
            if (emoji == null) msgRef(id, msgId).update("reaction.$uid", FieldValue.delete()).await()
            else msgRef(id, msgId).update("reaction.$uid", emoji).await()
        }
    }

    override fun toggleStar(id: String, msgId: String) {
        scope.launch {
            val m = AppState.thread(id)?.messages?.firstOrNull { it.id == msgId } ?: return@launch
            if (m.starredByMe) msgRef(id, msgId).update("starredBy", FieldValue.arrayRemove(uid)).await()
            else msgRef(id, msgId).update("starredBy", FieldValue.arrayUnion(uid)).await()
        }
    }

    /** Delete = delete-for-me ONLY (append uid to deletedFor). Never a hard delete. */
    override fun deleteForMe(id: String, msgId: String) {
        scope.launch { msgRef(id, msgId).update("deletedFor", FieldValue.arrayUnion(uid)).await() }
    }

    /** Clear chat = per-user clearedBefore timestamp; older messages hide locally only. */
    override fun clearChat(id: String) {
        scope.launch {
            db.collection(Schema.Chats.COLL).document(id)
                .update("clearedBefore.$uid", System.currentTimeMillis()).await()
            AppState.updateThread(id) { it.messages.clear() }
        }
    }

    override fun setTyping(id: String, on: Boolean) {
        if (SettingsStore.state.value.hideTyping && on) return
        scope.launch { setTypingRaw(id, on) }
    }

    private suspend fun setTypingRaw(id: String, on: Boolean) {
        runCatching { db.collection(Schema.Chats.COLL).document(id).update("typing.$uid", on).await() }
        if (on) {
            delay(3000)
            runCatching { db.collection(Schema.Chats.COLL).document(id).update("typing.$uid", false).await() }
        }
    }

    /** Call logs live INSIDE the chat (chats/{chatId}/calls) — per the rules contract. */
    override fun logCall(id: String, video: Boolean) {
        scope.launch {
            val th = AppState.thread(id) ?: return@launch
            db.collection(Schema.Chats.COLL).document(id).collection("calls").add(mapOf(
                "by" to uid, "peerUid" to th.person.uid,
                "kind" to if (video) "video" else "voice",
                "direction" to "outgoing", "at" to FieldValue.serverTimestamp(),
            )).await()
            refreshCallLogs()
        }
    }

    fun refreshCallLogs() {
        if (uid.isEmpty()) return
        scope.launch {
            val all = mutableListOf<CallLog>()
            for (th in AppState.threads.value) {
                val docs = runCatching {
                    db.collection(Schema.Chats.COLL).document(th.id).collection("calls")
                        .orderBy("at", Query.Direction.DESCENDING).limit(20).get().await()
                }.getOrNull()?.documents ?: continue
                docs.forEach { doc ->
                    val ts = doc.getTimestamp("at")?.toDate()?.time ?: 0L
                    all += CallLog(th.person, doc.getString("kind") ?: "voice",
                        doc.getString("direction") ?: "outgoing", fmtTime(ts), ts, th.id)
                }
            }
            AppState.callLogs.value = all.sortedByDescending { it.tsMs }
        }
    }

    override fun startThreadWith(person: Person): String {
        val id = chatIdFor(uid, person.uid)
        scope.launch {
            val ref = db.collection(Schema.Chats.COLL).document(id)
            if (!ref.get().await().exists()) {
                ref.set(mapOf(
                    "members" to listOf(uid, person.uid),
                    "typing" to emptyMap<String, Boolean>(),
                    "clearedBefore" to emptyMap<String, Long>(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )).await()
            }
            // surface the new thread locally right away
            if (AppState.thread(id) == null) {
                AppState.threads.value = listOf(ChatThread(id = id, person = person, time = "now")) + AppState.threads.value
            }
        }
        return id
    }

    fun fmtTime(tsMs: Long): String {
        if (tsMs <= 0) return "now"
        val now = System.currentTimeMillis()
        val sameDay = Calendar.getInstance().apply { timeInMillis = tsMs }
            .get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR) &&
            Calendar.getInstance().apply { timeInMillis = tsMs }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
        return when {
            now - tsMs < 60_000 -> "now"
            sameDay -> SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(tsMs))
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(tsMs))
        }
    }

    fun fmtLastSeen(tsMs: Long): String = if (tsMs <= 0) "" else "last seen ${fmtTime(tsMs)}"

    /** Re-wrap target for password change (Change password row in Settings). */
    suspend fun updateBackupBlob(blob: Map<String, String>) {
        db.collection(Schema.Users.COLL).document(uid).update("keyBackup", blob).await()
    }

    private val momentsCache = mutableMapOf<String, List<String>>()

    /** Moments items for a contact (moments/{uid}/items) — real data model, read-only in v1. */
    suspend fun loadMoments(person: Person): List<String> {
        momentsCache[person.uid]?.let { return it }
        if (person.uid.isEmpty()) return emptyList()
        val items = runCatching {
            db.collection(Schema.Moments.COLL).document(person.uid).collection("items")
                .orderBy("ts", Query.Direction.DESCENDING).limit(3).get().await()
                .documents.mapNotNull { it.getString("quote") }
        }.getOrDefault(emptyList())
        momentsCache[person.uid] = items
        return items
    }

    fun signOut(ctx: Context) {
        stop()
        auth.signOut()
        myPrivBytes = null
        convKeyCache.clear(); profileCache.clear(); publicKeyCache.clear()
        AppState.threads.value = emptyList(); AppState.callLogs.value = emptyList()
        ctx.getSharedPreferences("tiku_prefs", Context.MODE_PRIVATE).edit().putBoolean("onboarded", false).apply()
        AppState.go(Screen.Welcome)
    }
}
