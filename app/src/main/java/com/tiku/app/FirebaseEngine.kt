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
 *   chats/{chatId} {members:[uidA, uidB], typing:{uid:bool}, clearedBefore:{uid:ms}, updatedAt,
 *       nicknames:{uid:nickname} (round 6 — IG semantics: key = the nickname OWNER's uid,
 *       written by either member, rendered by both)}
 *   chats/{chatId}/messages/{id} {senderId, kind, ct, iv, ts, rqCt, rqIv, reaction:{uid:emoji},
 *       starredBy:[], deletedFor:[], seenBy:[], durationMs, amps, mediaChunks}
 *   chats/{chatId}/messages/{id}/chunks/{i} {d: base64 chunk of ciphertext}
 *   chats/{chatId}/calls/{callId} {kind, caller, callee, state, offer, answer,
 *       callerCands, calleeCands, createdAt} (WebRTC signaling only — deleted in
 *       full the moment a call resolves; see Rtc.kt)
 *   moments/{uid}/items/{itemId} {quote, ts}
 */
object FirebaseEngine : ChatRepo {
    lateinit var appCtx: Context

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    val uid: String get() = auth.currentUser?.uid ?: ""
    val signedIn: Boolean get() = auth.currentUser != null

    /**
     * The session's established my-uid (round 4, item 2): the live Auth uid,
     * falling back to the uid the on-device identity was created for
     * (`idk_uid` in the secure prefs). Bubble side (mine vs theirs) is ALWAYS
     * derived as `senderId == myUid()` — on the listener path, the history
     * path and after a restore — never from a local optimistic flag. The
     * fallback covers the window where a snapshot lands before Firebase Auth
     * has finished restoring its persisted user (auth uid momentarily "").
     */
    fun myUid(): String {
        uid.takeIf { it.isNotEmpty() }?.let { return it }
        return if (::appCtx.isInitialized) localIdentityUid(appCtx) else ""
    }

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

    /**
     * Tri-state returning-user check (round 5): null = the check itself failed
     * (offline / server error). Routers must treat null as UNKNOWN and never
     * as "new user" — routing a returning user into fresh setup is exactly
     * how a journal gets orphaned, so the create path is entered only on a
     * definite false.
     */
    suspend fun returningUserCheck(): Boolean? =
        runCatching { isReturningUser() }.getOrNull()

    /**
     * Thrown by finishNewUser when the account ALREADY has a cloud journal:
     * fresh setup would overwrite publicKey/keyBackup with a new keypair and
     * orphan every existing conversation permanently (round 5 hard guard).
     * The UI routes this to the Restore screen instead of an error toast.
     */
    class ExistingJournalException : IllegalStateException(
        "This account already has a journal — restore it with your password instead of starting over.")

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
            // ROUND 5 HARD GUARD: if this account already has a journal in
            // the cloud (a user doc carrying a public key / key backup), fresh
            // setup is IMPOSSIBLE — the set() below would overwrite keyBackup
            // and publicKey with a brand-new keypair, and every existing chat
            // would become undecryptable forever. Fail closed: even a FAILED
            // fetch aborts setup (the outer catch turns it into a failure),
            // never an overwrite on a guess.
            val existingDoc = db.collection(Schema.Users.COLL).document(uid).get().await()
            if (existingDoc.exists()) {
                val hasBackup = existingDoc.get("keyBackup") != null
                val hasPubKey = !existingDoc.getString("publicKey").isNullOrEmpty()
                if (hasBackup || hasPubKey) throw ExistingJournalException()
            }
            val u = username.lowercase()
            db.runTransaction { tx ->
                val claimRef = db.collection(Schema.Users.USERNAME_CLAIM).document(u)
                val claim = tx.get(claimRef)
                if (claim.exists()) {
                    // My OWN claim must never dead-end me (round 5): an earlier
                    // setup may have claimed the name and stopped before the
                    // user doc landed. Only someone ELSE's claim is "taken".
                    if (claim.getString("uid") != uid)
                        throw IllegalStateException("@$u is taken")
                } else {
                    tx.set(claimRef, mapOf("uid" to uid, "at" to FieldValue.serverTimestamp()))
                }
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
                // round 4: live username search fields (prefix + token queries)
                "usernameNorm" to SearchNorm.norm(u),
                "nameTokens" to SearchNorm.tokens(u, u),
            )).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    /** True only when the name is claimed by SOMEONE ELSE. My own claim must
     *  never read as "taken" to me (round 5: the same-uid re-claim dead-end —
     *  the setup screen showed the owner his own username as unavailable). */
    suspend fun usernameTaken(u: String): Boolean {
        val claim = db.collection(Schema.Users.USERNAME_CLAIM).document(u.lowercase()).get().await()
        return claim.exists() && claim.getString("uid") != uid
    }

    /**
     * ROUND 8 — CHANGE USERNAME (Me ▸ Edit profile). One transaction:
     * claim `usernames/{new}`, update the user doc (username + displayName,
     * which doubles as the profile name exactly like signup, plus the live
     * search fields), and free `usernames/{old}` — the rules allow deleting
     * ONLY your own claim, so a name held by anyone else fails the whole
     * transaction cleanly with "@x is taken" and nothing changes. Chats key
     * on uid everywhere (chat ids, members, message senders), so a rename
     * cannot break a single conversation.
     */
    suspend fun changeUsername(newRaw: String): Result<Unit> {
        val new = newRaw.lowercase().trim()
        if (new.length < 3) return Result.failure(IllegalStateException("At least 3 characters."))
        if (!new.all { it.isLetterOrDigit() || it == '.' || it == '_' })
            return Result.failure(IllegalStateException("Letters, numbers, dots and underscores only."))
        val me = uid
        if (me.isEmpty()) return Result.failure(IllegalStateException("Not signed in."))
        return try {
            db.runTransaction { tx ->
                val newClaimRef = db.collection(Schema.Users.USERNAME_CLAIM).document(new)
                val claim = tx.get(newClaimRef)
                if (claim.exists() && claim.getString("uid") != me)
                    throw IllegalStateException("@$new is taken")
                val userRef = db.collection(Schema.Users.COLL).document(me)
                val current = tx.get(userRef).getString("username").orEmpty()
                if (current == new) return@runTransaction Unit
                if (!claim.exists()) {
                    tx.set(newClaimRef, mapOf("uid" to me, "at" to FieldValue.serverTimestamp()))
                }
                tx.update(userRef, mapOf(
                    "username" to new,
                    "displayName" to new,
                    "usernameNorm" to SearchNorm.norm(new),
                    "nameTokens" to SearchNorm.tokens(new, new),
                ))
                if (current.isNotEmpty()) {
                    tx.delete(db.collection(Schema.Users.USERNAME_CLAIM).document(current))
                }
                Unit
            }.await()
            myPerson = myPerson.copy(username = new, name = new)
            AppState.myUsername = new
            Log.d("TikuProfile", "username changed to @$new")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TikuProfile", "changeUsername to @$new failed", e)
            Result.failure(e)
        }
    }

    /** ROUND 8 — CHANGE PROFILE PHOTO (Me ▸ Edit profile): the same
     *  photoData pipeline as signup (compressAvatar → base64 on the user
     *  doc), so every peer surface picks it up through the normal profile
     *  fetch. */
    suspend fun updateProfilePhoto(bmp: Bitmap): Result<Unit> {
        return try {
            if (uid.isEmpty()) throw IllegalStateException("Not signed in.")
            val data = MediaStore.compressAvatar(bmp)
            db.collection(Schema.Users.COLL).document(uid).update("photoData", data).await()
            val decoded = MediaStore.decodeBitmap(data)
            myPerson = myPerson.copy(photoBitmap = decoded)
            AppState.myAvatar = decoded
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TikuProfile", "updateProfilePhoto failed", e)
            Result.failure(e)
        }
    }

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
            // ROUND 4 SELF-HEAL: accounts created before live search have no
            // usernameNorm/nameTokens on their doc, so prefix/token queries
            // can't find them. The owner write is rule-allowed (users/{uid}
            // write = own uid), so top the fields up on sign-in if missing.
            val uname = snap.getString("username").orEmpty()
            if (uname.isNotEmpty() && snap.getString("usernameNorm") == null) {
                scope.launch {
                    runCatching {
                        db.collection(Schema.Users.COLL).document(uid).update(mapOf(
                            "usernameNorm" to SearchNorm.norm(uname),
                            "nameTokens" to SearchNorm.tokens(uname, snap.getString("displayName").orEmpty()),
                        )).await()
                        Log.d("TikuSearch", "self-healed search fields for @$uname")
                    }
                }
            }
        }
    }

    suspend fun lookupPerson(username: String): Person? {
        val claim = db.collection(Schema.Users.USERNAME_CLAIM).document(username.lowercase()).get().await()
        val peerUid = claim.getString("uid") ?: return null
        if (peerUid == uid) return null
        return fetchPerson(peerUid)
    }

    /** Build a Person from a users/{uid} snapshot (shared by fetch + search);
     *  keeps the profile and public-key caches in sync, like fetchPerson. */
    private fun personFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Person {
        val peerUid = doc.id
        val photoData = doc.getString("photoData").orEmpty()
        val p = Person(
            uid = peerUid,
            name = doc.getString("displayName").orEmpty().ifEmpty { doc.getString("username").orEmpty() },
            username = doc.getString("username").orEmpty(),
            emoji = "🙂",
            colorArgb = avatarColorFor(peerUid),
            photoBitmap = photoData.takeIf { it.isNotEmpty() }?.let { MediaStore.decodeBitmap(it) },
            online = doc.getBoolean("online") == true,
            lastSeenMs = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0L,
        )
        doc.getString("publicKey")?.let { if (it.isNotEmpty()) publicKeyCache[peerUid] = it }
        profileCache[peerUid] = p
        return p
    }

    /**
     * LIVE USERNAME SEARCH (round 4, item 4). Two Firestore queries, merged +
     * deduped, self excluded:
     *  1. prefix range on usernameNorm — "abu" finds abu, abu_saalim, … and
     *     the normalized full form makes "abu saalim" / "ABU_SAALIM" hit
     *     abu_saalim exactly (norm strips spaces/underscores/case).
     *  2. array-contains on nameTokens for each typed word-part — "saalim"
     *     alone finds abu_saalim by its name token.
     * Exact-normalized matches rank first, then prefix hits, then token-only
     * hits. Empty normalized query returns NOTHING (never a user dump).
     * Rules: users docs are readable by any signed-in user — no rules change.
     */
    suspend fun searchPeople(raw: String): List<Person> {
        val q = SearchNorm.norm(raw)
        if (q.isEmpty() || uid.isEmpty()) return emptyList()
        val byId = LinkedHashMap<String, Person>()
        runCatching {
            val snap = db.collection(Schema.Users.COLL)
                .whereGreaterThanOrEqualTo("usernameNorm", q)
                .whereLessThanOrEqualTo("usernameNorm", q + "\uf8ff")
                .limit(12).get().await()
            snap.documents.forEach { d -> if (d.id != uid) byId[d.id] = personFromDoc(d) }
        }.onFailure { Log.e("TikuSearch", "prefix query failed for '$raw'", it) }
        val parts = raw.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        for (part in parts) {
            runCatching {
                val snap = db.collection(Schema.Users.COLL)
                    .whereArrayContains("nameTokens", part)
                    .limit(12).get().await()
                snap.documents.forEach { d -> if (d.id != uid && d.id !in byId) byId[d.id] = personFromDoc(d) }
            }.onFailure { Log.e("TikuSearch", "token query failed for part '$part'", it) }
        }
        return byId.values.sortedWith(
            compareByDescending<Person> { SearchNorm.norm(it.username) == q }
                .thenBy { it.username },
        ).take(15)
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
    /** Round 5: bounded re-attach attempts when a threads snapshot had to skip
     *  chats whose peer profile fetch failed transiently (see start()). */
    private var threadsRetryCount = 0
    /** Round 6: bounded re-attach attempts after a threads-listener ERROR
     *  (see start() — an errored listener is dead and must never blank state). */
    private var threadsErrorRetries = 0
    private var msgsReg: ListenerRegistration? = null
    private val callsRegs = mutableMapOf<String, ListenerRegistration>()
    /** Round 6: per-chat lightweight preview listeners (bug 3, second layer):
     *  keep every unopened chat's last messages + unread count live so the
     *  chat list shows a real preview/badge without the chat ever being opened. */
    private val previewRegs = mutableMapOf<String, ListenerRegistration>()
    private var readPrefs: android.content.SharedPreferences? = null
    private val openChatIds = mutableSetOf<String>()

    // ================= ROUND 8 — INCREMENTAL MESSAGES + PAGINATION =========
    // The old attachMessages loaded the chat's ENTIRE history (no limit) and
    // re-decrypted EVERY doc on EVERY snapshot — including re-downloading
    // every image's chunks and every voice file, plus extra chat-doc reads
    // for typing/clear watermarks. On a long chat that is the crash + jank
    // the owner reported, and the receive-path latency of item 7a.
    // Now: the live listener covers only the newest [PAGE_SIZE] messages and
    // applies DocumentChanges INCREMENTALLY into [msgCache] (decrypting only
    // what actually changed; a metadata-only change to a media message reuses
    // its in-memory payload). Older pages load on demand (loadOlder).
    private val PAGE_SIZE = 60L
    /** chatId -> (docId -> decrypted server message), insertion-ordered. */
    private val msgCache = mutableMapOf<String, LinkedHashMap<String, TikuMessage>>()
    private val hasOlderCache = mutableMapOf<String, Boolean>()
    private val loadingOlder = mutableSetOf<String>()
    /** chatId -> my clearedBefore watermark, fed by the threads listener
     *  (the chat doc rides that snapshot — no per-snapshot get() anymore). */
    private val clearedBeforeCache = mutableMapOf<String, Long>()
    /** Pinned chats (round 8): a per-user LOCAL view preference. */
    private var pinPrefs: android.content.SharedPreferences? = null

    fun lastRead(threadId: String): Long = readPrefs?.getLong("read_$threadId", 0L) ?: 0L
    fun markRead(threadId: String) {
        readPrefs?.edit()?.putLong("read_$threadId", System.currentTimeMillis())?.apply()
    }

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        readPrefs = ctx.getSharedPreferences("tiku_read", Context.MODE_PRIVATE)
        pinPrefs = ctx.getSharedPreferences("tiku_pins", Context.MODE_PRIVATE)
        if (uid.isEmpty()) return
        threadsReg?.remove()
        // ROUND 6 — BUGS 2+3 SHARED ROOT CAUSE (proven server-side before this
        // round): this query used to add .orderBy("updatedAt", DESCENDING).
        // array-contains + orderBy on a DIFFERENT field requires a composite
        // index; the project has none, and a live probe (Firestore runQuery
        // executed AS two different users) returned FAILED_PRECONDITION
        // "The query requires an index" for BOTH. The snapshot listener thus
        // ERRORED on attach — and the old handler ignored the error param,
        // treated the null snapshot as "no chats", assigned an EMPTY list and
        // resynced the call watchers to the empty set. Consequences, exactly
        // as the owner reported: (bug 3) server chats never reached the list —
        // only rows inserted locally by search→open survived, (bug 2) no
        // ringing watcher was ever attached, so incoming calls never rang.
        // Fix: query on members ONLY (automatic single-field index) and sort
        // by updatedAt here, client-side. (Creating the composite index via
        // the Admin API was also attempted — the service account lacks
        // index-admin permission, HTTP 403 — so the client must not depend
        // on it anyway.)
        threadsReg = db.collection(Schema.Chats.COLL)
            .whereArrayContains("members", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // An errored listener is REMOVED by Firestore — and an
                    // error must NEVER blank the list or detach the call
                    // watchers. Keep current state; re-attach a few times.
                    Log.e("TikuChat", "threads listener error — keeping current list/watchers", err)
                    if (threadsErrorRetries < 3) {
                        threadsErrorRetries++
                        scope.launch { delay(3000); if (ChatSession.unlocked) start(appCtx) }
                    }
                    return@addSnapshotListener
                }
                threadsErrorRetries = 0
                scope.launch {
                    runCatching {
                        var skipped = 0
                        val built = snap?.documents?.mapNotNull { doc ->
                            @Suppress("UNCHECKED_CAST")
                            val parts = doc.get("members") as? List<String> ?: return@mapNotNull null
                            val peerUid = parts.firstOrNull { it != uid } ?: return@mapNotNull null
                            val existing = AppState.thread(doc.id)
                            // transient profile-fetch failure must NOT drop the thread from
                            // under an open ChatScreen — fall back to the known person.
                            val peer = runCatching { fetchPerson(peerUid) }.getOrNull()
                                ?: existing?.person ?: run { skipped++; return@mapNotNull null }
                            val ts = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                            // round 6: the peer's nickname lives on the chat
                            // doc, keyed by the nickname OWNER's uid (the peer).
                            @Suppress("UNCHECKED_CAST")
                            val nickMap = doc.get("nicknames") as? Map<String, Any?>
                            val nickname = (nickMap?.get(peerUid) as? String)?.takeIf { it.isNotBlank() }
                            // ROUND 8: the chat doc rides THIS snapshot — harvest
                            // the fields attachMessages used to re-fetch with a
                            // get() on every messages snapshot (peer typing state
                            // + my clearedBefore watermark) at zero extra reads.
                            @Suppress("UNCHECKED_CAST")
                            val typingMap = doc.get("typing") as? Map<String, Any?>
                            AppState.setTyping(doc.id, typingMap?.get(peerUid) as? Boolean ?: false)
                            @Suppress("UNCHECKED_CAST")
                            val cbMap = doc.get("clearedBefore") as? Map<String, Any?>
                            clearedBeforeCache[doc.id] = (cbMap?.get(uid) as? Number)?.toLong() ?: 0L
                            val pinnedIds = pinPrefs?.getStringSet("ids", emptySet()) ?: emptySet()
                            ts to ChatThread(id = doc.id, person = peer,
                                messages = existing?.messages ?: mutableListOf(),
                                unread = existing?.unread ?: 0,
                                time = fmtTime(ts),
                                pinned = doc.id in pinnedIds,
                                nickname = nickname,
                                hasOlder = existing?.hasOlder ?: (hasOlderCache[doc.id] ?: false))
                        } ?: emptyList()
                        // client-side updatedAt DESC (the query no longer orders)
                        val list = built.sortedByDescending { it.first }.map { it.second }
                        AppState.threads.value = list
                        // ROUND 5: a chat skipped over a transient profile-fetch
                        // failure must not stay invisible until some unrelated
                        // write re-fires this listener — on a quiet account that
                        // reads as "my chats didn't come back" after a restore.
                        // Re-attach once or twice so the initial snapshot is
                        // rebuilt with the profile fetch retried.
                        if (skipped > 0 && threadsRetryCount < 2) {
                            threadsRetryCount++
                            Log.w("TikuChat", "threads snapshot skipped $skipped chat(s) " +
                                "(peer profile fetch failed) — re-attaching listener, retry $threadsRetryCount")
                            scope.launch { delay(3000); if (ChatSession.unlocked) start(appCtx) }
                        } else if (skipped == 0) {
                            threadsRetryCount = 0
                        }
                        // incoming-call watchers + crash-residue sweep follow the thread set
                        val ids = list.map { it.id }.toSet()
                        syncCallWatchers(ids)
                        syncPreviewListeners(ids)
                        sweepStaleCalls(ids)
                    }
                }
            }
    }

    // ================= CHAT-LIST PREVIEW LISTENERS (round 6, bug 3 layer 2) ====
    /** One lightweight messages listener per known chat. The threads listener
     *  alone could only build EMPTY rows (messages were populated solely by
     *  attachMessages on open) — so even a delivered row had no preview and no
     *  unread badge until the chat was opened once. These listeners keep the
     *  newest ≤40 messages of every UNOPENED chat decrypted (light path: media
     *  payloads are NOT downloaded — a stub carries kind/duration/amps, which
     *  is all a preview needs) and maintain messages + unread on the thread.
     *  While a chat is open, attachMessages owns its message list and the
     *  preview path stays out of the way. */
    private fun syncPreviewListeners(threadIds: Set<String>) {
        (previewRegs.keys - threadIds).forEach { previewRegs.remove(it)?.remove() }
        threadIds.forEach { tid ->
            if (!previewRegs.containsKey(tid)) {
                previewRegs[tid] = db.collection(Schema.Chats.COLL).document(tid)
                    .collection(Schema.Chats.MESSAGES)
                    .orderBy("ts", Query.Direction.DESCENDING).limit(40)
                    .addSnapshotListener { snap, err ->
                        if (err != null) {
                            Log.e("TikuChat", "preview listener error for chat $tid", err)
                            return@addSnapshotListener
                        }
                        val docs = snap?.documents ?: return@addSnapshotListener
                        scope.launch { runCatching { applyPreviewSnapshot(tid, docs) } }
                    }
            }
        }
    }

    /** My clearedBefore watermark for a chat: served from the threads-
     *  listener-fed cache; a one-time get() only on a cold cache. */
    private suspend fun clearedBeforeFor(id: String): Long {
        clearedBeforeCache[id]?.let { return it }
        val v = runCatching {
            val cd = db.collection(Schema.Chats.COLL).document(id).get().await()
                .get("clearedBefore") as? Map<*, *>
            (cd?.get(uid) as? Number)?.toLong() ?: 0L
        }.getOrDefault(0L)
        clearedBeforeCache[id] = v
        return v
    }

    private suspend fun applyPreviewSnapshot(
        id: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        retry: Boolean = true,
    ) {
        if (openChatIds.contains(id)) return // the open-chat listener owns this thread
        val th = AppState.thread(id) ?: return
        val peerUid = th.person.uid
        if (peerUid.isEmpty()) return
        val key = convKeyOrNull(id, peerUid) ?: run {
            // keys often land a beat after the first snapshot (fresh chat)
            if (retry) { delay(1500); applyPreviewSnapshot(id, docs, retry = false) }
            return
        }
        val clearedBefore = clearedBeforeFor(id)
        val dmCutoff = if (SettingsStore.state.value.toggles["dm"] == true)
            System.currentTimeMillis() - 24L * 3600 * 1000 else 0L
        val msgs = docs.mapNotNull { d ->
            runCatching { decryptDoc(id, d, key, peerUid, light = true) }.getOrNull()
        }.filter { m ->
            !m.deletedForMe && m.tsMs >= clearedBefore && (dmCutoff == 0L || m.tsMs >= dmCutoff)
        }.sortedBy { it.tsMs }
        val lr = lastRead(id)
        val unread = msgs.count { !it.me && it.kind != MsgKind.CALL && it.tsMs > lr }
        AppState.threads.value = AppState.threads.value.map { t ->
            if (t.id == id && !openChatIds.contains(id))
                t.copy(messages = msgs.toMutableList(), unread = unread)
            else t
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
        // ROUND 7 ROOT FIX ("Offer missing" on receive): this watcher re-fires
        // on EVERY update to the ringing doc — and the caller pushes ICE
        // candidates (arrayUnion writes) continuously from the moment the
        // call is created. Once the callee was ringing, state was no longer
        // Idle, so the re-fire fell into the "busy" branch below and DELETED
        // the very call doc it was ringing for — offer included. When the
        // callee then tapped Accept, readOffer() found nothing. A re-fire
        // for the call I am already ringing / in is ALWAYS a no-op now.
        if (CallController.activeCallId() == callId) return
        val kind = doc.getString("kind") ?: "audio"
        val offerEnc = doc.getString("offer")
        val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
        scope.launch {
            runCatching {
                // re-check inside the coroutine: the state may have become
                // this very call while fetchPerson was in flight elsewhere.
                if (CallController.activeCallId() == callId) return@runCatching
                when {
                    // stale ringing doc (caller crashed) — sweep it silently
                    System.currentTimeMillis() - createdAt > Config.CALL_RING_TIMEOUT_MS ->
                        CallSignal.deleteCallTree(chatId, callId)
                    // already in a DIFFERENT call: clear the new one (busy)
                    CallController.state.value !is CallUiState.Idle ->
                        CallSignal.deleteCallTree(chatId, callId)
                    ChatSession.unlocked && ChatSession.foreground -> {
                        val person = fetchPerson(caller)
                        // ROUND 7: hand the offer from THIS snapshot to the
                        // controller — accept() uses the cached copy first.
                        CallController.onIncoming(chatId, callId, kind, person, offerEnc)
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
        previewRegs.values.forEach { it.remove() }; previewRegs.clear()
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
        // ROUND 8: the decrypted message cache + pagination bookkeeping die
        // with the session too, and so do the decrypted voice-note FILES in
        // the app cache (dl_*.m4a) — a chat trace that used to outlive the
        // lock. They simply re-download on the next unlock.
        msgCache.clear(); hasOlderCache.clear(); clearedBeforeCache.clear(); loadingOlder.clear()
        runCatching {
            appCtx.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("dl_") && f.name.endsWith(".m4a")) f.delete()
            }
        }
    }

    fun setPresence(online: Boolean) {
        if (uid.isEmpty()) return
        val updates = mutableMapOf<String, Any>("online" to online)
        if (!online && SettingsStore.state.value.lastSeen) updates["lastSeen"] = FieldValue.serverTimestamp()
        runCatching { db.collection(Schema.Users.COLL).document(uid).update(updates) }
    }

    // ================= CHATREPO =================
    override fun closeThread(id: String) { openChatIds -= id }

    /**
     * SEEN MARKING (round 4, item 1): the recipient adds their uid to a
     * message doc's seenBy. Called with the docs of the open chat — on EVERY
     * messages snapshot while the chat is open (attachMessages) and on each
     * pagination page (loadOlder), so a message that arrives while I'm
     * looking at the chat is marked seen immediately, not on the next entry.
     * (Round 8 removed the old extra full-collection get() at open — the
     * listener's own snapshot covers the newest page.) The sender's client
     * derives "Seen" purely from this field (decryptDoc → seenByPeer), so
     * it survives re-entry/restore. Call events are excluded — seen doesn't
     * apply to them. Batched; a snapshot that finds nothing unseen writes
     * nothing (no listener write-loop).
     */
    private fun markIncomingSeen(id: String, docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        if (!SettingsStore.state.value.readReceipts) return
        val unseen = docs.filter { d ->
            d.getString("senderId") != uid && d.getString("kind") != "call" &&
                !((d.get("seenBy") as? List<*>) ?: emptyList<Any>()).contains(uid)
        }
        if (unseen.isEmpty()) return
        scope.launch {
            runCatching {
                val batch = db.batch()
                unseen.forEach { batch.update(it.reference, "seenBy", FieldValue.arrayUnion(uid)) }
                batch.commit().await()
                Log.d("TikuChat", "marked ${unseen.size} message(s) seen in chat $id")
            }.onFailure { Log.e("TikuChat", "markIncomingSeen failed for chat $id", it) }
        }
    }

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
        // ROUND 8: no more full-collection get() here — the old code read
        // EVERY message doc of the chat on EVERY open just to seen-mark them
        // (thousands of reads on a long chat). Seen-marking now runs off the
        // listener's own snapshot (newest page) and off each loadOlder page
        // as it is fetched — the same coverage, zero extra reads.
    }

    private fun attachMessages(id: String) {
        msgsReg?.remove()
        val peerUid = AppState.thread(id)?.person?.uid ?: return
        msgsReg = db.collection(Schema.Chats.COLL).document(id)
            .collection(Schema.Chats.MESSAGES)
            .orderBy("ts", Query.Direction.DESCENDING).limit(PAGE_SIZE)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                // round 4: while this chat is OPEN on screen, incoming messages
                // are marked seen as they land (drives the sender's "Seen").
                if (openChatIds.contains(id)) markIncomingSeen(id, snap.documents)
                scope.launch {
                    // EVERYTHING in this listener is failure-tolerant: a key that is not
                    // ready (fresh searched chat), a transient fetch error, or a broken
                    // doc must degrade to "no messages yet", never crash the process.
                    runCatching {
                        val key = convKeyOrNull(id, peerUid) ?: run {
                            // one delayed retry — the profile/key often lands a beat later
                            delay(1500)
                            convKeyOrNull(id, peerUid)
                        } ?: return@runCatching
                        val cache = msgCache.getOrPut(id) { LinkedHashMap() }
                        val initialFill = cache.isEmpty()
                        // ROUND 8 — INCREMENTAL: decrypt/apply ONLY the docs
                        // this snapshot actually changed. A peer's seen-mark
                        // or reaction used to re-decrypt (and re-download) the
                        // WHOLE chat; now it touches exactly one doc.
                        for (change in snap.documentChanges) {
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val doc = change.document
                                    runCatching {
                                        decryptDoc(id, doc, key, peerUid, cached = cache[doc.id])
                                    }.getOrNull()?.let { cache[doc.id] = it }
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    // Messages are NEVER hard-deleted in this
                                    // schema (delete-for-me is a flag → MODIFIED).
                                    // A REMOVED change means the doc slid out
                                    // of the newest-page live window — it stays
                                    // in the cache and in the visible history.
                                }
                            }
                        }
                        if (initialFill) {
                            hasOlderCache[id] = snap.size() >= PAGE_SIZE.toInt()
                        }
                        applyCacheToThread(id)
                    } // end runCatching guard — failure = "no messages yet", never a crash
                }
            }
    }

    /** Rebuild one thread's visible message list from the incremental cache:
     *  watermark filters (clearedBefore / disappearing-messages) + the round-3
     *  optimistic merge (SENDING/FAILED locals survive until the server copy
     *  lands). Runs on every cache change; the work is a sort of already-
     *  decrypted messages — no I/O, no decryption. */
    private suspend fun applyCacheToThread(id: String) {
        val cache = msgCache[id] ?: return
        val clearedBefore = clearedBeforeFor(id)
        val dmCutoff = if (SettingsStore.state.value.toggles["dm"] == true)
            System.currentTimeMillis() - 24L * 3600 * 1000 else 0L
        val serverMsgs = cache.values.filter { m ->
            !m.deletedForMe && m.tsMs >= clearedBefore && (dmCutoff == 0L || m.tsMs >= dmCutoff)
        }
        val serverIds = serverMsgs.map { it.id }.toHashSet()
        val localOnly = (AppState.thread(id)?.messages ?: emptyList())
            .filter { it.id !in serverIds && it.status != SendStatus.SENT }
        val msgs = (serverMsgs + localOnly).sortedBy { it.tsMs }
        val lr = lastRead(id)
        val older = hasOlderCache[id] ?: false
        AppState.threads.value = AppState.threads.value.map { t ->
            if (t.id == id) t.copy(messages = msgs.toMutableList(), hasOlder = older,
                unread = if (openChatIds.contains(id)) 0 else msgs.count { !it.me && it.tsMs > lr })
            else t
        }
        if (openChatIds.contains(id)) markRead(id)
    }

    /** ROUND 8 — PAGINATION: fetch the next page of messages OLDER than the
     *  oldest cached one (inclusive startAt + id-dedupe, so same-millisecond
     *  neighbours can never fall through a gap). Fetched incoming messages
     *  are seen-marked too (the chat is open while paging). */
    override fun loadOlder(id: String) {
        if (hasOlderCache[id] != true) return
        if (!loadingOlder.add(id)) return
        scope.launch {
            try {
                runCatching {
                    val peerUid = AppState.thread(id)?.person?.uid ?: return@runCatching
                    val key = convKeyOrNull(id, peerUid) ?: return@runCatching
                    val cache = msgCache[id] ?: return@runCatching
                    val oldest = cache.values.minByOrNull { it.tsMs } ?: return@runCatching
                    val snap = db.collection(Schema.Chats.COLL).document(id)
                        .collection(Schema.Chats.MESSAGES)
                        .orderBy("ts", Query.Direction.DESCENDING)
                        .startAt(com.google.firebase.Timestamp(java.util.Date(oldest.tsMs)))
                        .limit(PAGE_SIZE).get().await()
                    if (openChatIds.contains(id)) markIncomingSeen(id, snap.documents)
                    var added = 0
                    for (doc in snap.documents) {
                        if (cache.containsKey(doc.id)) continue
                        runCatching { decryptDoc(id, doc, key, peerUid) }.getOrNull()
                            ?.let { cache[doc.id] = it; added++ }
                    }
                    // a short page — or one that added nothing new — is the top
                    hasOlderCache[id] = snap.size() >= PAGE_SIZE.toInt() && added > 0
                    applyCacheToThread(id)
                }.onFailure { Log.e("TikuChat", "loadOlder failed for chat $id", it) }
            } finally {
                loadingOlder.remove(id)
            }
        }
    }

    /** ROUND 8: pin/unpin — persisted locally (a personal view preference),
     *  reflected onto the thread immediately; the threads listener re-derives
     *  it from the same prefs on every snapshot. */
    override fun setPinned(id: String, on: Boolean) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(pinned = on) else it
        }
        val prefs = pinPrefs ?: return
        val cur = (prefs.getStringSet("ids", emptySet()) ?: emptySet()).toMutableSet()
        if (on) cur += id else cur -= id
        prefs.edit().putStringSet("ids", cur).apply()
    }

    /** [light] (round 6 preview path): text + call payloads decrypt fully,
     *  but image/voice payloads are NOT downloaded — the message is built as a
     *  stub (photo/localPath null, duration + waveform from the doc fields).
     *  A chat-list preview and the Calls tab only need kind/metadata.
     *  [cached] (round 8, incremental path): the previously decrypted copy of
     *  THIS doc. For media messages, when the chunk count is unchanged and
     *  the payload is already in memory, a metadata-only change (seen-by,
     *  reaction, star, delete flag) reuses the payload instead of
     *  re-downloading every chunk — the hot-path fix for items 4 + 7a. */
    private suspend fun decryptDoc(chatId: String, doc: com.google.firebase.firestore.DocumentSnapshot, key: SecretKey, peerUid: String, light: Boolean = false, cached: TikuMessage? = null): TikuMessage {
        val sender = doc.getString("senderId") ?: ""
        val kindRaw = doc.getString("kind") ?: "text"
        // ITEM 2: bubble side derives ONLY from the doc's senderId vs the
        // session's established my-uid — every path (fresh send echo, history
        // load, restored session, second device) funnels through here.
        val my = myUid()
        val me = sender == my
        @Suppress("UNCHECKED_CAST")
        val reactions = (doc.get("reaction") as? Map<String, String>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val starredBy = (doc.get("starredBy") as? List<String>) ?: emptyList<String>()
        @Suppress("UNCHECKED_CAST")
        val deletedFor = (doc.get("deletedFor") as? List<String>) ?: emptyList<String>()
        @Suppress("UNCHECKED_CAST")
        val seenBy = (doc.get("seenBy") as? List<String>) ?: emptyList<String>()
        val tsMs = doc.getTimestamp("ts")?.toDate()?.time ?: 0L
        val replyQuote = doc.getString("rqCt")?.takeIf { it.isNotEmpty() }?.let {
            runCatching { Crypto.decryptText(key, doc.getString("rqIv") ?: "", it) }.getOrNull()
        }
        var photo: Bitmap? = null
        var text = ""
        var durSec = 0
        var amps: List<Int> = emptyList()
        var localPath: String? = null
        var mediaChunks = 0
        // round 4 call-event payload (decrypted JSON from the ct field)
        var callCallerUid = ""; var callOutcome = ""; var callDurSec = 0; var callVideo = false
        when (kindRaw) {
            "text" -> text = Crypto.decryptText(key, doc.getString("iv") ?: "", doc.getString("ct") ?: "")
            "call" -> {
                val payload = Crypto.decryptText(key, doc.getString("iv") ?: "", doc.getString("ct") ?: "")
                runCatching {
                    val j = org.json.JSONObject(payload)
                    callCallerUid = j.optString("caller")
                    callOutcome = j.optString("outcome")
                    callDurSec = j.optInt("dur")
                    callVideo = j.optBoolean("video")
                }
            }
            "image", "voice" -> {
                mediaChunks = (doc.getLong("mediaChunks") ?: 0L).toInt()
                if (light) {
                    // preview stub — no chunk download (see decryptDoc doc)
                    if (kindRaw == "voice") {
                        durSec = ((doc.getLong("durationMs") ?: 0L) / 1000).toInt()
                        @Suppress("UNCHECKED_CAST")
                        amps = (doc.get("amps") as? List<Long>)?.map { it.toInt() } ?: emptyList()
                    }
                } else if (cached != null && mediaChunks > 0 && mediaChunks == cached.mediaChunks &&
                    ((kindRaw == "image" && cached.photo != null) || (kindRaw == "voice" && cached.localPath != null))) {
                    // ROUND 8 — INCREMENTAL: only metadata changed on a media
                    // message whose payload this device already holds. Reuse
                    // the payload; download NOTHING.
                    photo = cached.photo
                    localPath = cached.localPath
                    durSec = cached.voiceDurSec
                    amps = cached.amps
                } else {
                    val chunkCount = mediaChunks
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
        }
        val kind = when (kindRaw) {
            "image" -> MsgKind.IMAGE; "voice" -> MsgKind.VOICE; "call" -> MsgKind.CALL
            else -> MsgKind.TEXT
        }
        return TikuMessage(doc.id, sender, kind, text, fmtTime(tsMs), me, durSec, amps,
            photo, replyQuote, reactions, starredBy.contains(my), deletedFor.contains(my), tsMs, localPath,
            seenByPeer = seenBy.contains(peerUid),
            callCallerUid = callCallerUid, callOutcome = callOutcome,
            callDurationSec = callDurSec, callVideo = callVideo,
            callMine = callCallerUid.isNotEmpty() && callCallerUid == my,
            mediaChunks = mediaChunks)
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
                // call events are system-written, never optimistic/FAILED
                MsgKind.CALL -> AppState.setMessageStatus(id, msgId, SendStatus.SENT)
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

    /**
     * CALL EVENT (round 4, item 3): when a call resolves, a small E2EE note is
     * written INTO the chat's messages — kind="call", the payload (caller uid,
     * outcome, duration, video?) encrypted with the conversation key exactly
     * like a text message. The doc id is deterministic ("call_<callId>") so
     * BOTH clients writing it converge on ONE event, never a duplicate.
     * This is deliberately separate from signaling: the calls/{callId} doc
     * (offer/answer/ICE) is still deleted in full at call end. ROUND 6
     * SUPERSESSION (owner's new rule): the Calls tab now aggregates these
     * encrypted in-chat events into its history list — the event remains a
     * chat message, and no other call record exists anywhere.
     * outcome ∈ "ended" | "declined" | "noanswer". Best-effort by design:
     * a failed event write must never break call teardown.
     */
    suspend fun recordCallEvent(chatId: String, callId: String, callerUid: String,
                                video: Boolean, outcome: String, durationSec: Int) {
        runCatching {
            val peerUid = AppState.thread(chatId)?.person?.uid
                ?.takeIf { it.isNotEmpty() } ?: run {
                    Log.e("TikuCall", "call event skipped: peer unknown for chat $chatId"); return
                }
            ensureChatDoc(chatId, peerUid)
            val key = convKeyOrNull(chatId, peerUid)
                ?: run { Log.e("TikuCall", "call event skipped: key not ready for chat $chatId"); return }
            val payload = org.json.JSONObject().apply {
                put("v", 1); put("caller", callerUid); put("outcome", outcome)
                put("dur", durationSec); put("video", video)
            }.toString()
            val (iv, ct) = Crypto.encryptText(key, payload)
            msgRef(chatId, "call_$callId").set(mapOf(
                "senderId" to uid, "kind" to "call", "ct" to ct, "iv" to iv,
                "ts" to FieldValue.serverTimestamp(),
                "rqCt" to "", "rqIv" to "",
                "reaction" to emptyMap<String, String>(), "starredBy" to emptyList<String>(),
                "deletedFor" to emptyList<String>(), "seenBy" to listOf(uid),
                "durationMs" to 0, "amps" to emptyList<Int>(), "mediaChunks" to 0,
            )).await()
            touchChat(chatId)
            Log.d("TikuCall", "call event recorded chat=$chatId call=$callId outcome=$outcome dur=${durationSec}s")
        }.onFailure { Log.e("TikuCall", "call event write failed for chat $chatId call $callId", it) }
    }

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

    /**
     * NICKNAME (round 6, IG semantics): set/clear the PEER's nickname in this
     * chat. Stored on the chat doc as `nicknames[peerUid]` — the key is the
     * nickname OWNER's uid, the write can be made by either member (the chats
     * update rule allows member updates), and BOTH members' thread listeners
     * read the same map, so the nickname applies to both views. Local state
     * updates optimistically; the threads listener confirms from the doc.
     * Clearing uses a field delete so no empty-string nickname lingers.
     */
    override fun setNickname(id: String, nickname: String?) {
        val th = AppState.thread(id) ?: return
        val peerUid = th.person.uid
        if (peerUid.isEmpty()) return
        val clean = nickname?.trim()?.takeIf { it.isNotEmpty() }
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(nickname = clean) else it
        }
        scope.launch {
            runCatching {
                val ref = db.collection(Schema.Chats.COLL).document(id)
                if (clean == null) ref.update("nicknames.$peerUid", FieldValue.delete()).await()
                else ref.update("nicknames.$peerUid", clean).await()
                Log.d("TikuChat", "nickname ${if (clean == null) "cleared" else "set"} in chat $id")
            }.onFailure { Log.e("TikuChat", "setNickname failed for chat $id", it) }
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
