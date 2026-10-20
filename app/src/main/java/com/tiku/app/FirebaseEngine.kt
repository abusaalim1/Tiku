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

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + TikuEngineHandler)

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

    /**
     * Non-throwing conversation-key derivation. The v1 crash: this derivation ran
     * unguarded inside the messages snapshot listener on a handler-less Main scope —
     * the first failure (peer key not cached yet for a brand-new searched chat, a
     * transient fetch error, undecryptable key material) killed the whole process.
     * Callers must treat null as "keys not ready yet", never as fatal.
     */
    private suspend fun convKeyOrNull(chatId: String, peerUid: String): SecretKey? =
        runCatching { convKey(chatId, peerUid) }.getOrNull()

    // ================= LISTENERS =================
    private var threadsReg: ListenerRegistration? = null
    private var msgsReg: ListenerRegistration? = null
    private val callsRegs = mutableMapOf<String, ListenerRegistration>()
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
                    runCatching {
                        val list = snap?.documents?.mapNotNull { doc ->
                            @Suppress("UNCHECKED_CAST")
                            val parts = doc.get("members") as? List<String> ?: return@mapNotNull null
                            val peerUid = parts.firstOrNull { it != uid } ?: return@mapNotNull null
                            val existing = AppState.thread(doc.id)
                            // transient profile-fetch failure must NOT drop the thread from
                            // under an open ChatScreen — fall back to the known person.
                            val peer = runCatching { fetchPerson(peerUid) }.getOrNull()
                                ?: existing?.person ?: return@mapNotNull null
                            val ts = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                            ChatThread(id = doc.id, person = peer,
                                messages = existing?.messages ?: mutableListOf(),
                                unread = existing?.unread ?: 0,
                                time = fmtTime(ts),
                                pinned = existing?.pinned ?: false)
                        } ?: emptyList()
                        AppState.threads.value = list
                        // incoming-call watchers + crash-residue sweep follow the thread set
                        syncCallWatchers(list.map { it.id }.toSet())
                        sweepStaleCalls(list.map { it.id }.toSet())
                    }
                }
            }
    }

    // ================= CALL SIGNALING WATCHERS =================
    /** One ringing-listener per chat I belong to. A ringing call only ever rings
     *  when I'm unlocked AND foregrounded (checked in handleIncomingCall) — there
     *  are no notifications, by design. */
    private fun syncCallWatchers(threadIds: Set<String>) {
        (callsRegs.keys - threadIds).forEach { callsRegs.remove(it)?.remove() }
        threadIds.forEach { tid ->
            if (!callsRegs.containsKey(tid)) {
                callsRegs[tid] = db.collection(Schema.Chats.COLL).document(tid).collection("calls")
                    .whereEqualTo("state", "ringing")
                    .addSnapshotListener { snap, err ->
                        if (err != null) return@addSnapshotListener
                        snap?.documents?.forEach { doc -> handleIncomingCall(tid, doc.id, doc) }
                    }
            }
        }
    }

    private fun handleIncomingCall(chatId: String, callId: String, doc: com.google.firebase.firestore.DocumentSnapshot) {
        val caller = doc.getString("caller") ?: return
        if (caller == uid) return // my own outgoing call echo
        val kind = doc.getString("kind") ?: "audio"
        val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
        scope.launch {
            runCatching {
                when {
                    // stale ringing doc (caller crashed) — sweep it silently
                    System.currentTimeMillis() - createdAt > Config.CALL_RING_TIMEOUT_MS ->
                        CallSignal.deleteCallTree(chatId, callId)
                    // already in a call: clear the new one (busy), no ringing
                    CallController.state.value !is CallUiState.Idle ->
                        CallSignal.deleteCallTree(chatId, callId)
                    ChatSession.unlocked && ChatSession.foreground -> {
                        val person = fetchPerson(caller)
                        CallController.onIncoming(chatId, callId, kind, person)
                    }
                    // not inside Tik right now: the call simply never rings;
                    // it times out and deletes itself on the caller's side.
                }
            }
        }
    }

    /** Crash-residue cleanup: delete ended calls and anything older than 2 hours. */
    private var sweepDoneFor: Set<String> = emptySet()
    private fun sweepStaleCalls(threadIds: Set<String>) {
        val fresh = threadIds - sweepDoneFor
        if (fresh.isEmpty()) return
        sweepDoneFor = sweepDoneFor + fresh
        scope.launch {
            runCatching {
                val cutoff = System.currentTimeMillis() - 2L * 3600 * 1000
                fresh.forEach { tid ->
                    val docs = db.collection(Schema.Chats.COLL).document(tid).collection("calls")
                        .get().await().documents
                    docs.forEach { doc ->
                        val st = doc.getString("state") ?: ""
                        val ts = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        if (st == "ended" || ts < cutoff) CallSignal.deleteCallTree(tid, doc.id)
                    }
                }
            }
        }
    }

    fun stop() {
        threadsReg?.remove(); msgsReg?.remove()
        callsRegs.values.forEach { it.remove() }; callsRegs.clear()
    }

    /** INSTANT RE-LOCK: stop listeners, zeroize the private key + conversation keys in RAM,
     *  drop all caches. After this the session is cryptographically dead. */
    fun lock() {
        // any live call ends with the session — and its signaling docs are deleted
        CallController.endSilently()
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
                runCatching {
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
                    // EVERYTHING in this listener is failure-tolerant: a key that is not
                    // ready (fresh searched chat), a transient fetch error, or a broken
                    // doc must degrade to "no messages yet", never crash the process.
                    runCatching {
                    val peer = fetchPerson(peerUid)
                    val key = convKeyOrNull(id, peerUid) ?: run {
                        // one delayed retry — the profile/key often lands a beat later
                        delay(1500)
                        convKeyOrNull(id, peerUid)
                    } ?: return@runCatching
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
                    } // end runCatching guard — failure = "no messages yet", never a crash
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
            val key = convKeyOrNull(id, th.person.uid)
                ?: return@launch.also { AppState.toast("Couldn't send — encryption key not ready") }
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
        val key = convKeyOrNull(id, th.person.uid)
            ?: return.also { AppState.toast("Couldn't send — encryption key not ready") }
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

    override fun startThreadWith(person: Person): String {
        val id = chatIdFor(uid, person.uid)
        // v1 crash fix: surface the thread SYNCHRONOUSLY so the Chat screen that
        // opens right after always finds it (the Firestore create runs async below).
        if (AppState.thread(id) == null) {
            AppState.threads.value = listOf(ChatThread(id = id, person = person, time = "now")) + AppState.threads.value
        }
        scope.launch {
            runCatching {
                val ref = db.collection(Schema.Chats.COLL).document(id)
                if (!ref.get().await().exists()) {
                    ref.set(mapOf(
                        "members" to listOf(uid, person.uid),
                        "typing" to emptyMap<String, Boolean>(),
                        "clearedBefore" to emptyMap<String, Long>(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )).await()
                }
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
        AppState.threads.value = emptyList()
        ctx.getSharedPreferences("tiku_prefs", Context.MODE_PRIVATE).edit().putBoolean("onboarded", false).apply()
        AppState.go(Screen.Welcome)
    }
}
