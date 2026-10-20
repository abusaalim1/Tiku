package com.tiku.app

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    /** Restore outcomes — kept distinct so the UI never reports an internal
     *  failure (no backup, server refusal, offline) as a wrong password. */
    sealed interface RestoreResult {
        data object Ok : RestoreResult
        data object WrongPassword : RestoreResult
        data class Unavailable(val why: String) : RestoreResult
    }

    /**
     * Restore identity from the password-wrapped backup. A genuinely wrong
     * password -> [RestoreResult.WrongPassword] (recovery without it is
     * impossible, by design); a missing backup or a failed fetch ->
     * [RestoreResult.Unavailable] with the plain reason (owner Bug 0).
     */
    suspend fun restoreFromBackup(ctx: Context, password: String): RestoreResult {
        val blob = try {
            backupBlob()
        } catch (e: Exception) {
            Log.e("TikuGate", "restore: backup fetch failed", e)
            return RestoreResult.Unavailable(sendFailureReason(e))
        } ?: return RestoreResult.Unavailable("no backup was found for this account")
        val priv = try {
            Crypto.backupUnwrap(blob, password)
        } catch (e: Exception) {
            return RestoreResult.WrongPassword
        }
        return try {
            Crypto.privateKeyFromPkcs8(priv) // validates the key material
            myPrivBytes = priv
            val (iv, ct) = Crypto.deviceWrap(priv)
            securePrefs(ctx).edit().putString("idk_iv", iv).putString("idk_ct", ct)
                .putString("idk_uid", uid)
                .putString("pw_hash", Crypto.passwordHash(password)).apply()
            RestoreResult.Ok
        } catch (e: Exception) {
            Log.e("TikuGate", "restore: local save failed", e)
            RestoreResult.Unavailable("the backup opened but couldn't be saved on this phone")
        }
    }

    /** True when this device holds a Keystore-wrapped identity — the
     *  precondition for the Google-gated password reset (owner Bug 0). */
    fun hasLocalIdentity(ctx: Context): Boolean {
        val sp = securePrefs(ctx)
        return sp.getString("idk_iv", null) != null && sp.getString("idk_ct", null) != null
    }

    /** The uid the local identity was created for ("" for pre-round-3 installs). */
    fun localIdentityUid(ctx: Context): String =
        securePrefs(ctx).getString("idk_uid", "") ?: ""

    /**
     * RESCUE PATH (owner Bug 0): re-wrap the EXISTING local identity under a
     * new password and re-upload the cloud backup. The caller (reset screen)
     * must already have re-authenticated the SAME Google account and checked
     * the uid against this identity. The local key is device-Keystore wrapped
     * — password-independent — so history survives the reset intact.
     * Returns the cloud-backup update success as the value.
     */
    suspend fun resetBackupPassword(ctx: Context, newPassword: String): Result<Boolean> {
        return try {
            if (myPrivBytes == null) loadLocalIdentity(ctx)
            val priv = myPrivBytes ?: return Result.failure(
                IllegalStateException("No journal key found on this phone."))
            val blob = Crypto.backupWrap(priv, newPassword)
            val hash = Crypto.passwordHash(newPassword)
            securePrefs(ctx).edit().putString("pw_hash", hash)
                .putString("idk_uid", uid).apply()
            // self-verify the just-saved verifier before claiming success
            val stored = securePrefs(ctx).getString("pw_hash", null)
            if (stored != hash || !Crypto.passwordVerify(newPassword, hash)) {
                return Result.failure(
                    IllegalStateException("Couldn't save the new password on this phone."))
            }
            val cloudOk = runCatching {
                db.collection(Schema.Users.COLL).document(uid).update("keyBackup", blob).await()
            }.isSuccess
            Result.success(cloudOk)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** New user: generate identity, claim username, write user doc + backup. */
    suspend fun finishNewUser(ctx: Context, password: String, username: String, avatarBytes: ByteArray?): Result<Unit> {
        return try {
            if (password.isEmpty()) {
                return Result.failure(IllegalStateException(
                    "Setup password missing — go back and set your password again."))
            }
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
            val sp = securePrefs(ctx)
            // BUG-0 GUARD: the create-password step already saved + self-verified
            // a verifier for THIS password. Never silently overwrite it with a
            // hash of a different string — if the handed-over password doesn't
            // match the saved verifier, setup stops loudly instead of bricking
            // the account behind a "wrong password" the user never chose.
            val priorHash = sp.getString("pw_hash", null)
            if (priorHash != null && Crypto.hashWellFormed(priorHash) &&
                !Crypto.passwordVerify(password, priorHash)) {
                return Result.failure(IllegalStateException(
                    "Setup password mismatch — go back and set your password again."))
            }
            val newHash = Crypto.passwordHash(password)
            sp.edit().putString("idk_iv", iv).putString("idk_ct", ct)
                .putString("idk_uid", uid)
                .putString("pw_hash", newHash).apply()
            if (sp.getString("pw_hash", null) != newHash ||
                !Crypto.passwordVerify(password, newHash)) {
                return Result.failure(IllegalStateException(
                    "Couldn't save your password on this phone — please try again."))
            }
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
                        if (err != null) {
                            Log.e("TikuCall", "incoming-call watcher failed for chat $tid", err)
                            return@addSnapshotListener
                        }
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

    // ================= CHAT DOC BOOTSTRAP =================
    // Message/call writes are gated by rules on the PARENT chat doc
    // (chats/{id}), so the doc — with members=[me, peer] — must EXIST and be
    // committed before the first message or call write. Send and call paths
    // AWAIT ensureChatDoc() first; failures rethrow and surface, never
    // swallowed; duplicate ensures are de-duped per chat.
    //
    // ROUND 3.2 — the build-20 ensure was itself the send bug: it probed with
    // ref.get() and only set() when the snapshot said "missing". But the
    // chats read rule is `request.auth.uid in resource.data.members`, and for
    // a doc that does not exist yet `resource` is null — so the PROBE ITSELF
    // was refused with PERMISSION_DENIED and the create was never attempted
    // (server probe without the read: create of the same doc/members = 200;
    // the values were never wrong). Existence can never be probed with get()
    // under member rules — ensure must WRITE, unconditionally: a merge-set of
    // members (+ updatedAt, so the orderBy("updatedAt") thread list can see
    // the doc). Merge keeps an existing doc's other fields; on a missing doc
    // it is a plain create, checked against request.resource.data.members.
    // typing / clearedBefore are deliberately NOT written here — they are
    // created lazily by dot-path updates, so a re-ensure can never wipe a
    // peer's typing state or a user's clearedBefore watermark.
    private val chatDocJobs = mutableMapOf<String, Deferred<Unit>>()

    suspend fun ensureChatDoc(id: String, peerUid: String) {
        // members are ALWAYS built from the live auth uid + the thread's
        // stored peer uid — never from display state (names/usernames).
        val me = uid
        require(me.isNotEmpty()) { "ensureChatDoc: not signed in (chat $id)" }
        require(peerUid.isNotEmpty()) { "ensureChatDoc: peer uid missing (chat $id)" }
        require(peerUid != me) { "ensureChatDoc: peer uid is my own uid (chat $id)" }
        val job = synchronized(chatDocJobs) {
            chatDocJobs.getOrPut(id) {
                scope.async {
                    val members = listOf(me, peerUid)
                    Log.d("TikuSend", "ensureChatDoc merge-set chatId=$id me=$me peer=$peerUid members=$members")
                    db.collection(Schema.Chats.COLL).document(id)
                        .set(mapOf(
                            "members" to members,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ), SetOptions.merge())
                        .await()
                    Log.d("TikuSend", "ensureChatDoc ok chatId=$id me=$me peer=$peerUid")
                    Unit // keep the async Deferred<Unit> — Log.d returns Int
                }
            }
        }
        try {
            job.await()
        } catch (e: Exception) {
            synchronized(chatDocJobs) { chatDocJobs.remove(id) }
            Log.e("TikuSend", "ensureChatDoc FAILED chatId=$id me=$me peer=$peerUid", e)
            throw e
        }
    }

    /** Plain-language reason for a failed send/call write (toast + bubble). */
    fun sendFailureReason(e: Throwable): String {
        val fe = e as? com.google.firebase.firestore.FirebaseFirestoreException
        return when (fe?.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "the server refused the write (check the Firestore rules are published)"
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                "no connection to the server"
            com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND ->
                "the chat isn't set up on the server yet"
            else -> e.message?.take(70) ?: "unknown error"
        }
    }

    /** INSTANT RE-LOCK: stop listeners, zeroize the private key + conversation keys in RAM,
     *  drop all caches. After this the session is cryptographically dead. */
    fun lock() {
        // any live call ends with the session — and its signaling docs are deleted
        CallController.endSilently()
        stop()
        synchronized(chatDocJobs) { chatDocJobs.clear() }
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
        // opening a chat also tops up the parent doc guarantee (async, logged)
        AppState.thread(id)?.person?.uid?.takeIf { it.isNotEmpty() }?.let { peerUid ->
            scope.launch {
                runCatching { ensureChatDoc(id, peerUid) }
                    .onFailure { Log.e("TikuSend", "ensureChatDoc on open failed for $id", it) }
            }
        }
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
                    val serverMsgs = snap?.documents?.mapNotNull { doc ->
                        runCatching { decryptDoc(id, doc, key) }.getOrNull()
                    }?.filter { m ->
                        !m.deletedForMe && m.tsMs >= clearedBefore && (dmCutoff == 0L || m.tsMs >= dmCutoff)
                    } ?: emptyList()
                    // round 3: keep my local optimistic copies the server hasn't
                    // confirmed yet (SENDING) and the ones it refused (FAILED —
                    // tap-to-retry). Server copies win by id once they land.
                    val serverIds = serverMsgs.map { it.id }.toHashSet()
                    val localOnly = (AppState.thread(id)?.messages ?: emptyList())
                        .filter { it.id !in serverIds && it.status != SendStatus.SENT }
                    val msgs = (serverMsgs + localOnly).sortedBy { it.tsMs }
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

    // ================= SEND PATH (round 3: optimistic + NO silent failures) ====
    // v1's sendText awaited a Firestore add() inside the engine scope with no
    // try/catch and no local echo: when the write was refused (the recursive
    // rules bug), the exception died in the CoroutineExceptionHandler and the
    // message simply never appeared — not on the sender's screen, not anywhere.
    // Now: the bubble appears instantly as SENDING with a client-chosen doc id
    // (so the server snapshot replaces it 1:1), the write is awaited, and any
    // failure flips the bubble to FAILED + toast + Logcat "TikuSend".

    private fun newLocalId() = "m" + UUID.randomUUID().toString().replace("-", "")

    override fun sendText(id: String, text: String, quote: String?) {
        val th = AppState.thread(id) ?: return
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.TEXT, text = text,
            time = fmtTime(System.currentTimeMillis()), me = true, replyQuote = quote,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
        )
        AppState.upsertMessage(id, local)
        scope.launch { deliverText(id, th.person.uid, local) }
    }

    private suspend fun deliverText(id: String, peerUid: String, local: TikuMessage) {
        try {
            ensureChatDoc(id, peerUid)
            val key = convKeyOrNull(id, peerUid)
                ?: throw IllegalStateException("encryption key not ready — the other person may not have finished setting up")
            val (iv, ct) = Crypto.encryptText(key, local.text)
            val (rqIv, rqCt) = if (local.replyQuote != null) Crypto.encryptText(key, local.replyQuote) else ("" to "")
            msgRef(id, local.id).set(mapOf(
                "senderId" to uid, "kind" to "text", "ct" to ct, "iv" to iv,
                "ts" to FieldValue.serverTimestamp(),
                "rqCt" to rqCt, "rqIv" to rqIv,
                "reaction" to emptyMap<String, String>(), "starredBy" to emptyList<String>(),
                "deletedFor" to emptyList<String>(), "seenBy" to listOf(uid),
                "durationMs" to 0, "amps" to emptyList<Int>(), "mediaChunks" to 0,
            )).await()
            AppState.setMessageStatus(id, local.id, SendStatus.SENT)
            touchChat(id)
        } catch (e: Exception) {
            Log.e("TikuSend", "text send failed in chat $id (msg ${local.id})", e)
            AppState.setMessageStatus(id, local.id, SendStatus.FAILED)
            AppState.toast("Couldn't send — ${sendFailureReason(e)}")
        }
    }

    override fun sendPhoto(id: String, bmp: Bitmap) {
        val th = AppState.thread(id) ?: return
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.IMAGE,
            time = fmtTime(System.currentTimeMillis()), me = true, photo = bmp,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
        )
        AppState.upsertMessage(id, local)
        scope.launch { deliverMedia(id, th.person.uid, local, MediaStore.compressPhoto(bmp)) }
    }

    override fun sendVoice(id: String, audioFile: File?, durSec: Int, amps: List<Int>) {
        val th = AppState.thread(id) ?: return
        if (audioFile == null || !audioFile.exists()) {
            AppState.toast("Couldn't send the voice note — recording missing")
            return
        }
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.VOICE,
            time = fmtTime(System.currentTimeMillis()), me = true,
            voiceDurSec = durSec, amps = amps, localPath = audioFile.absolutePath,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
        )
        AppState.upsertMessage(id, local)
        scope.launch {
            val bytes = runCatching { audioFile.readBytes() }.getOrNull()
            if (bytes == null) {
                Log.e("TikuSend", "voice send failed in chat $id: recording unreadable")
                AppState.setMessageStatus(id, local.id, SendStatus.FAILED)
                AppState.toast("Couldn't send — the recording couldn't be read")
            } else {
                deliverMedia(id, th.person.uid, local, bytes)
            }
        }
    }

    /** Shared media delivery: encrypt -> doc under the LOCAL id -> chunk upload. */
    private suspend fun deliverMedia(id: String, peerUid: String, local: TikuMessage, plainBytes: ByteArray) {
        val kind = if (local.kind == MsgKind.IMAGE) "image" else "voice"
        try {
            ensureChatDoc(id, peerUid)
            val key = convKeyOrNull(id, peerUid)
                ?: throw IllegalStateException("encryption key not ready — the other person may not have finished setting up")
            val (iv, ctB64) = Crypto.encrypt(key, plainBytes)
            val ctBytes = Crypto.unb64(ctB64)
            val ref = msgRef(id, local.id)
            ref.set(mapOf(
                "senderId" to uid, "kind" to kind, "ct" to "", "iv" to iv,
                "ts" to FieldValue.serverTimestamp(),
                "rqCt" to "", "rqIv" to "",
                "reaction" to emptyMap<String, String>(), "starredBy" to emptyList<String>(),
                "deletedFor" to emptyList<String>(), "seenBy" to listOf(uid),
                "durationMs" to local.voiceDurSec * 1000, "amps" to local.amps, "mediaChunks" to 0,
            )).await()
            val chunks = MediaStore.upload(db, id, local.id, ctBytes)
            ref.update("mediaChunks", chunks).await()
            AppState.setMessageStatus(id, local.id, SendStatus.SENT)
            // the recording temp file is only cleaned up once delivery landed —
            // a FAILED voice note keeps its file so tap-to-retry can resend it.
            if (local.kind == MsgKind.VOICE) local.localPath?.let { runCatching { File(it).delete() } }
            touchChat(id)
        } catch (e: Exception) {
            Log.e("TikuSend", "$kind send failed in chat $id (msg ${local.id})", e)
            AppState.setMessageStatus(id, local.id, SendStatus.FAILED)
            AppState.toast("Couldn't send — ${sendFailureReason(e)}")
        }
    }

    /** Tap-to-retry on a FAILED bubble: same doc id, same content, fresh attempt. */
    override fun retrySend(id: String, msgId: String) {
        val th = AppState.thread(id) ?: return
        val m = th.messages.firstOrNull { it.id == msgId } ?: return
        if (m.status != SendStatus.FAILED) return
        AppState.setMessageStatus(id, msgId, SendStatus.SENDING)
        scope.launch {
            when (m.kind) {
                MsgKind.TEXT -> deliverText(id, th.person.uid, m)
                MsgKind.IMAGE -> {
                    val bmp = m.photo
                    if (bmp == null) {
                        AppState.setMessageStatus(id, msgId, SendStatus.FAILED)
                        AppState.toast("Couldn't resend — the photo is gone from this device")
                    } else deliverMedia(id, th.person.uid, m, MediaStore.compressPhoto(bmp))
                }
                MsgKind.VOICE -> {
                    val f = m.localPath?.let { File(it) }
                    val bytes = f?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }
                    if (bytes == null) {
                        AppState.setMessageStatus(id, msgId, SendStatus.FAILED)
                        AppState.toast("Couldn't resend — the recording is gone from this device")
                    } else deliverMedia(id, th.person.uid, m, bytes)
                }
            }
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
        // the parent chat doc is created through the SAME awaited path the send
        // and call flows use — and a failure here is logged, never silent.
        if (person.uid.isNotEmpty()) {
            scope.launch {
                runCatching { ensureChatDoc(id, person.uid) }
                    .onFailure { Log.e("TikuSend", "ensureChatDoc failed for new thread $id", it) }
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
