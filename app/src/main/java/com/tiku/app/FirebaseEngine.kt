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
import com.google.firebase.firestore.DocumentReference
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
 *   chats/{chatId} {members:[uidA, uidB], typing:{uid:epochMs} (round 22 — was {uid:bool}), clearedBefore:{uid:ms}, updatedAt,
 *       nicknames:{uid:nickname} (round 6 — IG semantics: key = the nickname OWNER's uid,
 *       written by either member, rendered by both),
 *       themeAccent/themeWallpaper (round 10 — per-chat theme indices),
 *       receiptsOff:[uid] (round 10 — members who muted their read receipts here),
 *       pinnedMsgs:[msgId] (round 10 — pinned messages, max 3)}
 *   chats/{chatId}/messages/{id} {senderId, kind, ct, iv, ts, rqCt, rqIv, reaction:{uid:emoji},
 *       starredBy:[], deletedFor:[], seenBy:[], durationMs, amps, mediaChunks}
 *   chats/{chatId}/messages/{id}/chunks/{i} {d: base64 chunk of ciphertext}
 *   chats/{chatId}/calls/{callId} {kind, caller, callee, state, offer, answer,
 *       callerCands, calleeCands, createdAt} (WebRTC signaling only — deleted in
 *       full the moment a call resolves; see Rtc.kt)
 *   chats/{chatId}/gamestate/{msgId} {json, by, updatedAt} (round 41 — the
 *       shared board state of one /babeg game message; see listenGameState)
 *   moments/{uid}/items/{itemId} {quote, ts}
 */
/** ROUND 22 — the Instagram-style send-effect kinds the `fx` message
 *  field may carry (see [TikuMessage.fx]). One shared vocabulary: the
 *  engine validates against it on both write and read, the chat UI
 *  renders from it. */
val MESSAGE_FX_KINDS = setOf("gift", "fire", "confetti", "hearts")

object FirebaseEngine : ChatRepo {
    lateinit var appCtx: Context

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    // runCatching for the same reason as signedInEmail below: UI code reads
    // these during composition, and the screenshot harness composes in a
    // process where no FirebaseApp is initialized — there they must read as
    // signed-out defaults instead of throwing. On a real device FirebaseApp
    // is always initialized before any UI composes, so behaviour is identical.
    val uid: String get() = runCatching { auth.currentUser?.uid ?: "" }.getOrDefault("")
    val signedIn: Boolean get() = runCatching { auth.currentUser != null }.getOrDefault(false)

    /** The signed-in account's email ("" when signed out). ROUND 9: owner
     *  identity keys off THIS — never the uid. runCatching because the
     *  screenshot harness composes owner-gated UI in a process where no
     *  FirebaseApp is initialized. */
    val signedInEmail: String
        get() = runCatching { auth.currentUser?.email ?: "" }.getOrDefault("")

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

    /** ROUND 29: true while this process's listeners are attached
     *  (set by [start], cleared by [stop] — lock and sign-out both run
     *  through stop). [TikuAlertService] reads it to decide whether a
     *  cold process must bootstrap the engine itself or the UI already
     *  did — the engine is started exactly once per process either
     *  way, so its snapshots can never be double-fed to the judge. */
    @Volatile var listenersActive: Boolean = false
        private set

    /** ROUND 29: true while the alert service holds a background
     *  session in a UI-less (restarted) process. The listener-error
     *  retries below re-attach while EITHER the in-app session is
     *  unlocked OR this is set — same law, both lives of the session.
     *  Set by the service's bootstrap; cleared by [stop]. */
    @Volatile var serviceSession: Boolean = false

    private fun sessionLive(): Boolean = ChatSession.unlocked || serviceSession

    /** ROUND 49 (this engine hook exists ONLY for the alert service's
     *  keepalive — see TikuAlertService / alertKeepaliveCheck): the
     *  wall-clock time of the last alert-relevant snapshot activity
     *  (threads or any per-chat preview listener). Stamped by [start]
     *  on attach and by both listeners on every successful snapshot.
     *  Firestore gives app code no heartbeat, so a LONG silence is the
     *  only stall signal available; the threads listener's own error
     *  retries and the preview listeners' error self-heal below cover
     *  the known-error cases, and this stamp covers the silent ones. */
    @Volatile var lastAlertActivityAt: Long = 0L
        private set

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
        val snap = db.collection(Schema.Users.COLL).document(uid).get().await()
        if (!snap.exists()) return false
        // ROUND 18: a BARE doc is not a restorable journal. A doc can exist
        // with no key material — setup died after the doc landed but before
        // the backup did, or a start-fresh retired the material and the
        // re-setup never finished. Routing such an account to Restore
        // dead-ends on "no backup was found for this account" with no way
        // forward (the Atika re-register trap). Only a doc carrying key
        // material counts as returning — the exact same test
        // finishNewUser's hard guard applies, so router and setup can
        // never disagree: no key material -> fresh onboarding, where the
        // set() simply completes the half-written doc.
        return snap.get("keyBackup") != null ||
            !snap.getString("publicKey").isNullOrEmpty()
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

    /**
     * ROUND 10 — START FRESH AFTER KEY LOSS (forgot-password parity, Mode F).
     * The caller has ALREADY re-authenticated the same Google account and
     * bound it to this uid (ResetPasswordScreen stage 0). This phone holds
     * no local identity and the journal password is gone, so the cloud
     * keyBackup can never be opened again — the only way forward is to
     * retire it: delete keyBackup + publicKey from MY OWN user doc (the
     * users rule allows exactly this write), wipe every local remnant
     * (device-wrapped identity keys, password verifier, in-RAM key
     * material), and let the caller route into the normal fresh-journal
     * onboarding, where finishNewUser creates a brand-new keypair +
     * password + backup. The account and username survive untouched;
     * existing chats stay on the server as ciphertext that no key can
     * ever open again — the reset screen says so before this ever runs.
     */
    suspend fun startFreshAfterKeyLoss(ctx: Context): Result<Unit> {
        return try {
            if (uid.isEmpty()) return Result.failure(IllegalStateException("Not signed in."))
            db.collection(Schema.Users.COLL).document(uid).update(mapOf(
                "keyBackup" to FieldValue.delete(),
                "publicKey" to FieldValue.delete(),
            )).await()
            securePrefs(ctx).edit()
                .remove("idk_iv").remove("idk_ct").remove("idk_uid").remove("pw_hash")
                .apply()
            runCatching { myPrivBytes?.fill(0) }
            myPrivBytes = null
            convKeyCache.clear()
            Log.d("TikuGate", "start-fresh after key loss completed for uid $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TikuGate", "startFreshAfterKeyLoss failed", e)
            Result.failure(e)
        }
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
        val palette = listOf(0xFFB95B72, 0xFFB07A5C, 0xFF7D6FA8, 0xFF4F8F7E, 0xFF5B86B8, 0xFF9A6B8A)
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
    /** ROUND 20: per-chat unread count captured at open time (see
     *  ChatThread.unreadAtOpen). openThread writes it BEFORE markRead can
     *  zero the badge; the threads listener's from-scratch rebuild reads
     *  it back from here so a snapshot landing mid-open can never wipe
     *  the value the chat screen is about to pin its divider from. */
    private val unreadAtOpenCache = mutableMapOf<String, Int>()

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
    /** ROUND 44: chatId -> last AI-image expiry sweep instant (see
     *  [sweepExpiredAiImages]). In-memory on purpose: the guard only
     *  keeps the snapshot hot paths cheap — a fresh session simply
     *  re-checks every chat it loads. */
    private val aiSweepAt = mutableMapOf<String, Long>()
    /** chatId -> my clearedBefore watermark, fed by the threads listener
     *  (the chat doc rides that snapshot — no per-snapshot get() anymore). */
    private val clearedBeforeCache = mutableMapOf<String, Long>()
    // ================= ROUND 33 — HIDE RECOVERY REGISTRY =================
    /** Every message this device has decrypted whose doc carries MY uid
     *  in deletedFor (key "chatId|msgId"), with the decrypted copy.
     *  decryptDoc is the ONE funnel every loaded message passes through
     *  (open-chat pages AND the 40-message preview windows), so this map
     *  is the enumeration of my delete-for-me hides — the data behind
     *  the owner's "Hidden by me" page (OwnerExtras.kt). Session-scoped:
     *  cleared in start(), rebuilt by the listeners; entries drop out
     *  the moment a decrypt shows the doc no longer hides the message. */
    private val hiddenLock = Any()
    private val hiddenCopies = LinkedHashMap<String, HiddenMessage>()

    private fun noteHidden(chatId: String, m: TikuMessage) {
        synchronized(hiddenLock) {
            val k = "$chatId|${m.id}"
            if (m.deletedForMe) hiddenCopies[k] = HiddenMessage(chatId, m)
            else hiddenCopies.remove(k)
        }
    }
    // ---- ROUND 14: requests / hide / block / notes ----
    /** chatId -> my acceptedFor / hiddenFor stamps (ms), fed by the threads
     *  listener exactly like clearedBeforeCache. */
    private val acceptedForCache = mutableMapOf<String, Long>()
    private val hiddenForCache = mutableMapOf<String, Long>()
    /** My blocked uids (users/{me}.blockedUids), fed by the my-doc listener. */
    private var blockedCache: Set<String> = emptySet()
    private var myDocReg: com.google.firebase.firestore.ListenerRegistration? = null
    private val noteRegs = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration?>()

    /** ROUND 14 derivation — a thread is a REQUEST when the partner has sent
     *  at least one message, I have sent none, and I never accepted it
     *  (acceptedFor[me] on the chat doc; threads I started count as accepted
     *  because startThreadWith/ensureChatDoc is only reached by my action —
     *  the acceptedFor stamp is written on accept; a thread with NO messages
     *  yet is never a request). */
    private fun deriveIsRequest(msgs: List<TikuMessage>, accepted: Boolean): Boolean {
        if (accepted || msgs.isEmpty()) return false
        val hasIncoming = msgs.any { !it.me }
        val hasOutgoing = msgs.any { it.me }
        return hasIncoming && !hasOutgoing
    }

    /** Hidden = I hid it and the partner has not sent a NEWER message since
     *  (a new partner message resurfaces the thread — spec semantics). */
    private fun deriveHidden(id: String, msgs: List<TikuMessage>): Boolean {
        val h = hiddenForCache[id] ?: 0L
        if (h <= 0L) return false
        return msgs.none { !it.me && it.tsMs > h }
    }
    /** Pinned chats (round 8): a per-user LOCAL view preference. */
    private var pinPrefs: android.content.SharedPreferences? = null

    fun lastRead(threadId: String): Long = readPrefs?.getLong("read_$threadId", 0L) ?: 0L

    /**
     * ROUND 8 (item B1) — the read anchor is the NEWEST SERVER TIMESTAMP
     * actually rendered, never the device wall clock. Message `ts` is a
     * Firestore serverTimestamp, so comparing it against
     * System.currentTimeMillis() mixed two clocks: a phone running behind
     * the server left already-read messages "unread" forever (badge never
     * cleared); a phone running ahead swallowed brand-new messages (badge
     * never counted). Anchoring to the newest server-ts message seen makes
     * `tsMs > lastRead` an exact same-clock comparison. Optimistic locals
     * are excluded (their tsMs IS device time) — only SENT (server-backed)
     * messages qualify. Monotonic: never decreases; written with commit()
     * only when it advances, so a kill right after reading can't resurrect
     * the badge on the next launch.
     */
    fun markRead(threadId: String) {
        // ROUND 33 — GHOST READ (owner-only): while the owner's ghost
        // toggle is on, the local read watermark must NOT advance
        // either — his chats keep showing unread to HIM, the honest
        // twin of not sending Seen (see markIncomingSeen). GhostStore
        // .enabled self-gates on OwnerState.isOwner, so for every other
        // account this line never fires.
        if (GhostStore.enabled(appCtx)) return
        val newest = AppState.thread(threadId)?.messages
            ?.filter { it.status == SendStatus.SENT }
            ?.maxOfOrNull { it.tsMs } ?: 0L
        val prev = lastRead(threadId)
        val anchor = maxOf(prev, newest)
        if (anchor > prev) {
            readPrefs?.edit()?.putLong("read_$threadId", anchor)?.commit()
        }
    }

    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        // ROUND 30: the owner's local vault index loads with the engine
        // (its writes stay gated on OwnerState.isOwner at every call site).
        OwnerVault.init(ctx)
        // ROUND 33: the hide-recovery registry is session-scoped — a new
        // sign-in starts empty and the listeners rebuild it.
        synchronized(hiddenLock) { hiddenCopies.clear() }
        readPrefs = ctx.getSharedPreferences("tiku_read", Context.MODE_PRIVATE)
        pinPrefs = ctx.getSharedPreferences("tiku_pins", Context.MODE_PRIVATE)
        if (uid.isEmpty()) return
        listenersActive = true
        // ROUND 49: attaching (or re-attaching) is itself activity —
        // the keepalive's staleness clock starts from here.
        lastAlertActivityAt = System.currentTimeMillis()
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
                        scope.launch { delay(3000); if (sessionLive()) start(appCtx) }
                    }
                    return@addSnapshotListener
                }
                threadsErrorRetries = 0
                lastAlertActivityAt = System.currentTimeMillis()
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
                            // ROUND 22: the peer's typing entry is an
                            // epoch-ms stamp (freshness judged reader-
                            // side against the 5 s window). A legacy
                            // Boolean from a pre-round-22 peer carries
                            // no stamp to age: true counts as fresh on
                            // the snapshot that carries it (that client
                            // still clears its own entry), false as off.
                            val peerTypingAt = when (val rawTyping = typingMap?.get(peerUid)) {
                                is Number -> rawTyping.toLong()
                                is Boolean -> if (rawTyping) System.currentTimeMillis() else 0L
                                else -> 0L
                            }
                            AppState.setPeerTypingAt(doc.id, peerTypingAt)
                            @Suppress("UNCHECKED_CAST")
                            val cbMap = doc.get("clearedBefore") as? Map<String, Any?>
                            clearedBeforeCache[doc.id] = (cbMap?.get(uid) as? Number)?.toLong() ?: 0L
                            // ROUND 14: request/hide stamps ride this snapshot too.
                            @Suppress("UNCHECKED_CAST")
                            val accMap = doc.get("acceptedFor") as? Map<String, Any?>
                            val accMs = (accMap?.get(uid) as? Number)?.toLong()
                                ?: (accMap?.get(uid) as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                            acceptedForCache[doc.id] = accMs
                            @Suppress("UNCHECKED_CAST")
                            val hidMap = doc.get("hiddenFor") as? Map<String, Any?>
                            val hidMs = (hidMap?.get(uid) as? Number)?.toLong()
                                ?: (hidMap?.get(uid) as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                            hiddenForCache[doc.id] = hidMs
                            val createdMs = doc.getTimestamp("createdAt")?.toDate()?.time
                                ?: existing?.createdAtMs?.takeIf { it > 0L } ?: ts
                            // ROUND 10: per-chat details ride this same
                            // snapshot (theme, receipts, pinned messages, MY
                            // nickname) — zero extra reads, both members see
                            // the same chat-doc values.
                            val myNickname = (nickMap?.get(uid) as? String)?.takeIf { it.isNotBlank() }
                            val receiptsOffList = (doc.get("receiptsOff") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            val pinnedMsgsList = (doc.get("pinnedMsgs") as? List<*>)?.mapNotNull { it as? String } ?: existing?.pinnedMsgIds ?: emptyList()
                            val themeAccentVal = doc.getLong("themeAccent")?.toInt()
                            val themeWallpaperVal = doc.getLong("themeWallpaper")?.toInt()
                            val pinnedIds = pinPrefs?.getStringSet("ids", emptySet()) ?: emptySet()
                            val knownMsgs = existing?.messages ?: mutableListOf()
                            ts to ChatThread(id = doc.id, person = peer,
                                messages = knownMsgs,
                                unread = existing?.unread ?: 0,
                                time = fmtTime(ts),
                                pinned = doc.id in pinnedIds,
                                nickname = nickname,
                                hasOlder = existing?.hasOlder ?: (hasOlderCache[doc.id] ?: false),
                                myNickname = myNickname,
                                themeAccent = themeAccentVal,
                                themeWallpaper = themeWallpaperVal,
                                // ROUND 26: the shared wallpaper theme
                                // (missing field = Default, "").
                                themeId = doc.getString("theme") ?: "",
                                // ROUND 35: the custom-theme asset
                                // version (missing = no asset, 0).
                                themeAssetAt = doc.getTimestamp("themeAssetAt")?.toDate()?.time ?: 0L,
                                receiptsOffMine = uid in receiptsOffList,
                                pinnedMsgIds = pinnedMsgsList,
                                isRequest = deriveIsRequest(knownMsgs, accMs > 0L),
                                createdAtMs = createdMs,
                                hiddenForMe = deriveHidden(doc.id, knownMsgs),
                                // ROUND 20: this from-scratch rebuild must
                                // not wipe the open-time unread capture.
                                unreadAtOpen = existing?.unreadAtOpen
                                    ?: (unreadAtOpenCache[doc.id] ?: 0))
                        } ?: emptyList()
                        // client-side updatedAt DESC (the query no longer orders)
                        // ROUND 14: a blocked person's threads never surface.
                        val list = built.sortedByDescending { it.first }.map { it.second }
                            .filter { it.person.uid !in blockedCache }
                        AppState.threads.value = list
                        // ROUND 35: custom gallery themes — make sure
                        // every custom chat's encrypted photo asset is
                        // cached + published (no-ops for built-ins and
                        // for already-current caches).
                        ensureCustomThemeAssets(list)
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
                            scope.launch { delay(3000); if (sessionLive()) start(appCtx) }
                        } else if (skipped == 0) {
                            threadsRetryCount = 0
                        }
                        // incoming-call watchers + crash-residue sweep follow the thread set
                        val ids = list.map { it.id }.toSet()
                        syncCallWatchers(ids)
                        syncPreviewListeners(ids)
                        syncNoteListeners(list.map { it.person.uid }.toSet())
                        sweepStaleCalls(ids)
                    }
                }
            }
        attachMyDocListener()
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
                            // ROUND 49: an errored listener is REMOVED by
                            // Firestore — and until this round the dead
                            // registration STAYED in previewRegs, whose
                            // containsKey gate then never re-registered
                            // it: that chat's alerts silently died until
                            // the next engine restart (a real "message
                            // aaya hi nahi" stall on a good network).
                            // Drop the corpse and re-register shortly;
                            // syncPreviewListeners rebuilds from the
                            // live thread set.
                            previewRegs.remove(tid)?.remove()
                            scope.launch {
                                delay(5000)
                                if (sessionLive() && listenersActive) {
                                    syncPreviewListeners(
                                        AppState.threads.value.map { it.id }.toSet()
                                    )
                                }
                            }
                            return@addSnapshotListener
                        }
                        lastAlertActivityAt = System.currentTimeMillis()
                        val docs = snap?.documents ?: return@addSnapshotListener
                        scope.launch { runCatching { applyPreviewSnapshot(tid, docs) } }
                    }
            }
        }
    }

    /** ROUND 18/19 — metadata stub for a message doc whose ciphertext
     *  can't be opened on this device (history written under a retired
     *  keypair — e.g. the pre-reset journal's chats). Kind, sender and
     *  ts are plaintext doc fields, so the stub is honest, and ROUND 19
     *  marks it [TikuMessage.undecryptable]: EVERY surface (chat list,
     *  open chat, pagination) renders the placeholder "This message
     *  can't be opened on this phone" from it — never a blank bubble.
     *  Used by the preview path, the open-chat listener and loadOlder
     *  alike. A delAll doc never reaches here (decryptDoc returns its
     *  tombstone without touching ciphertext). */
    private fun undecryptableStub(doc: com.google.firebase.firestore.DocumentSnapshot): TikuMessage? {
        val sender = doc.getString("senderId") ?: return null
        val kind = when (doc.getString("kind") ?: "text") {
            "image" -> MsgKind.IMAGE; "voice" -> MsgKind.VOICE; "call" -> MsgKind.CALL
            "video" -> MsgKind.VIDEO; "audio" -> MsgKind.AUDIO; "game" -> MsgKind.GAME
            else -> MsgKind.TEXT
        }
        val my = myUid()
        @Suppress("UNCHECKED_CAST")
        val deletedFor = (doc.get("deletedFor") as? List<String>) ?: emptyList<String>()
        val tsMs = doc.getTimestamp("ts")?.toDate()?.time ?: 0L
        return TikuMessage(
            id = doc.id, senderUid = sender, kind = kind, text = "",
            time = fmtTime(tsMs), me = sender == my,
            deletedForMe = deletedFor.contains(my), tsMs = tsMs,
            undecryptable = true,
        )
    }

    /** ROUND 19 — does this copy hold content worth protecting from a
     *  stub? (Decrypted text, a decrypted image/poster, or a playable
     *  local file.) A metadata-only VIDEO/AUDIO stub does NOT qualify —
     *  it carries no plaintext — but it never reaches the stub fallback
     *  anyway (those kinds don't throw in decryptDoc). */
    private fun TikuMessage.hasPlaintext(): Boolean =
        !undecryptable && (text.isNotEmpty() || photo != null || localPath != null ||
            kind == MsgKind.CALL && callOutcome.isNotEmpty())

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
        val existingById = th.messages.associateBy { it.id }
        val msgs = docs.mapNotNull { d ->
            // ROUND 18: a doc whose ciphertext this device cannot open
            // (written under a retired key — e.g. the pre-reset journal's
            // chats) must still EXIST in the list. Dropping it silently
            // left threads with real messages rendering a BLANK preview
            // (the owner's abu2 row: name + "now", no text). The stub
            // carries only the doc's plaintext metadata (kind / sender /
            // ts) and is flagged undecryptable (ROUND 19).
            // ROUND 19: a stub must never overwrite a copy whose
            // plaintext this device already holds — if an earlier
            // snapshot decrypted this doc and this one (transiently)
            // didn't, the held copy wins over the stub.
            runCatching { decryptDoc(id, d, key, peerUid, light = true) }.getOrNull()
                ?: existingById[d.id]?.takeIf { it.hasPlaintext() }
                ?: undecryptableStub(d)
        }.filter { m ->
            !m.deletedForMe && m.tsMs >= clearedBefore && (dmCutoff == 0L || m.tsMs >= dmCutoff)
        }.sortedBy { it.tsMs }
        val lr = lastRead(id)
        val unread = msgs.count { !it.me && it.kind != MsgKind.CALL && it.tsMs > lr }
        AppState.threads.value = AppState.threads.value.map { t ->
            if (t.id == id && !openChatIds.contains(id))
                t.copy(
                    messages = msgs.toMutableList(), unread = unread,
                    // ROUND 14: every fresh preview window re-derives the
                    // request flag, the hide state (a NEW partner message is
                    // exactly what un-hides a thread) and the creation time.
                    isRequest = deriveIsRequest(msgs, (acceptedForCache[id] ?: 0L) > 0L),
                    hiddenForMe = deriveHidden(id, msgs),
                    createdAtMs = if (t.createdAtMs > 0L) t.createdAtMs
                        else msgs.firstOrNull()?.tsMs ?: 0L,
                )
            else t
        }
        // ROUND 44: this freshly built preview window doubles as the
        // thread-list expiry pass — an expired /tika image sitting in an
        // UNOPENED chat is swept from here (opened chats are covered by
        // the message listener's pass in attachMessages).
        sweepExpiredAiImages(id, msgs)
        // ROUND 9: the owner's opt-in alert hook sees this same freshly
        // decrypted preview window (regular messages AND call events).
        // OwnerNotify itself checks owner + toggle + foreground and
        // baselines history before ever posting, so for every other
        // account — and for the owner with the toggle off — this is a no-op.
        OwnerNotify.onPreviewMessages(appCtx, id, msgs)
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
                    // ROUND 29: not in front, but the alert service holds
                    // a live unlocked session — the call alerts instead of
                    // ringing in-app. OwnerNotify dedupes per call id
                    // (this watcher re-fires on every ICE write) and the
                    // alert dies at the ring timeout; if he answers by
                    // opening the app, the foreground ring takes over,
                    // and if he never does, the call still lands later
                    // as a missed-call message for the delta judge.
                    OwnerNotify.pipelineLive -> {
                        val person = fetchPerson(caller)
                        OwnerNotify.onIncomingCall(
                            appCtx, chatId, callId, kind == "video", person.name)
                    }
                    // not inside Tik right now and no alert session: the
                    // call simply never rings; it times out and deletes
                    // itself on the caller's side.
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
        listenersActive = false
        serviceSession = false
        threadsReg?.remove(); msgsReg?.remove()
        callsRegs.values.forEach { it.remove() }; callsRegs.clear()
        previewRegs.values.forEach { it.remove() }; previewRegs.clear()
        myDocReg?.remove(); myDocReg = null
        noteRegs.values.forEach { it?.remove() }; noteRegs.clear()
    }

    /** ROUND 49 — listener-health keepalive, called ONLY by
     *  [TikuAlertService]'s 5-minute tick while the alert gate holds.
     *  If no alert-relevant snapshot (threads or any preview listener)
     *  has landed for over [ALERT_STALE_MS], the listeners are assumed
     *  silently stalled (the failure mode the error callbacks cannot
     *  see: a wedged sync stream after a network switch or a long
     *  Doze) and are force-rebuilt — preview registrations dropped and
     *  the proven full re-attach ([start], the same path the threads
     *  error retries use) re-run, whose first snapshot re-syncs every
     *  preview listener. The stamp is reset BEFORE the rebuild so a
     *  slow re-attach cannot retrigger on the next tick. The delta
     *  judge makes the rebuild safe: its per-thread baselines and
     *  prev-id sets survive in-process, so a rebuilt listener's first
     *  snapshot diffs against the pre-stall window — a message that
     *  arrived DURING the stall is a genuine delta and alerts (that is
     *  the point), and nothing already seen can re-alert.
     *
     *  Honest cost: on a genuinely quiet account this rebuild also
     *  fires (silence is indistinguishable from a stall without a
     *  heartbeat) — bounded to at most once per [ALERT_STALE_MS] while
     *  the gate is on, and a re-attach's initial snapshots are served
     *  from the local cache the listeners already maintain. No message
     *  data is polled: between rebuilds this is one timestamp compare
     *  per service tick. */
    fun alertKeepaliveCheck() {
        runCatching {
            if (!listenersActive || !sessionLive()) return
            val now = System.currentTimeMillis()
            if (now - lastAlertActivityAt <= ALERT_STALE_MS) return
            lastAlertActivityAt = now
            previewRegs.values.forEach { it.remove() }; previewRegs.clear()
            if (::appCtx.isInitialized) start(appCtx)
        }
    }

    /** ROUND 49: silence longer than this (with the gate on) reads as
     *  a stalled listener rather than a quiet account. */
    private const val ALERT_STALE_MS = 10 * 60_000L

    // ================= ROUND 14: MY DOC (block list + my note) =================
    /** One listener on users/{me}: feeds AppState.blockedUids (+ the resolved
     *  people for Settings) and MY note entry in AppState.notes. Attached from
     *  start(); removed by stop()/signOut. */
    private fun attachMyDocListener() {
        if (uid.isEmpty()) return
        myDocReg?.remove()
        myDocReg = db.collection(Schema.Users.COLL).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val blocked = (snap.get("blockedUids") as? List<String>)?.toSet() ?: emptySet()
                if (blocked != blockedCache) {
                    blockedCache = blocked
                    AppState.blockedUids.value = blocked
                    // a newly blocked person's threads vanish immediately;
                    // unblocking re-runs start() from unblockUser instead.
                    if (blocked.isNotEmpty()) {
                        AppState.threads.value = AppState.threads.value
                            .filter { it.person.uid !in blocked }
                    }
                    scope.launch {
                        AppState.blockedPeople.value = blocked.mapNotNull { b ->
                            runCatching { fetchPerson(b) }.getOrNull()
                        }
                    }
                }
                applyNoteSnapshot(uid, snap)
            }
    }

    /** Fold one user doc's note fields into AppState.notes (24h window,
     *  blank text = no note). Shared by the my-doc and per-peer listeners. */
    private fun applyNoteSnapshot(ownerUid: String, snap: com.google.firebase.firestore.DocumentSnapshot) {
        val text = snap.getString("noteText")?.trim().orEmpty()
        val atMs = snap.getTimestamp("noteAt")?.toDate()?.time ?: 0L
        @Suppress("UNCHECKED_CAST")
        val hearts = (snap.get("noteHearts") as? List<String>)?.toSet() ?: emptySet()
        val fresh = text.isNotEmpty() && atMs > 0L &&
            System.currentTimeMillis() - atMs < 24L * 3600 * 1000
        AppState.notes.value = if (fresh) {
            AppState.notes.value + (ownerUid to NoteUi(ownerUid, text, hearts, atMs))
        } else {
            AppState.notes.value - ownerUid
        }
    }

    /** Per-peer user-doc listeners feeding AppState.notes — the note set is
     *  exactly my thread partners (spec: notes are visible to thread
     *  partners). Synced from the threads listener like the preview regs. */
    private fun syncNoteListeners(peerUids: Set<String>) {
        (noteRegs.keys - peerUids).forEach { noteRegs.remove(it)?.remove() }
        peerUids.forEach { p ->
            if (!noteRegs.containsKey(p)) {
                noteRegs[p] = db.collection(Schema.Users.COLL).document(p)
                    .addSnapshotListener { snap, err ->
                        if (err != null || snap == null) return@addSnapshotListener
                        applyNoteSnapshot(p, snap)
                    }
            }
        }
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
        // ROUND 9: the owner's alert bookkeeping baselines die with the
        // session too — after a re-lock, history can never re-alert.
        OwnerNotify.reset()
        runCatching {
            appCtx.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("dl_") && f.name.endsWith(".m4a")) f.delete()
            }
        }
    }

    // ROUND 8 (item A): presence heartbeat — while I'm online, my lastSeen
    // stamp is refreshed every 60s so a crash/kill (which never gets to
    // write online=false) SELF-EXPIRES on the peer's screen within ~3
    // minutes (see presenceText) instead of showing "online" forever with a
    // days-old last seen next to it.
    @Volatile private var presenceOnline = false
    private var presenceJob: kotlinx.coroutines.Job? = null

    fun setPresence(online: Boolean) {
        if (uid.isEmpty()) return
        presenceOnline = online
        presenceJob?.cancel(); presenceJob = null
        val lsOn = SettingsStore.state.value.lastSeen
        val ref = db.collection(Schema.Users.COLL).document(uid)
        if (online) {
            val updates = mutableMapOf<String, Any>("online" to true)
            // stamp immediately too — "last active" starts moving from the
            // first second, not the first heartbeat.
            if (lsOn) updates["lastSeen"] = FieldValue.serverTimestamp()
            runCatching { ref.update(updates) }
            if (lsOn) {
                presenceJob = scope.launch {
                    while (presenceOnline) {
                        delay(60_000)
                        if (presenceOnline) runCatching {
                            ref.update("lastSeen", FieldValue.serverTimestamp())
                        }
                    }
                }
            }
        } else {
            val updates = mutableMapOf<String, Any>("online" to false)
            if (lsOn) updates["lastSeen"] = FieldValue.serverTimestamp()
            else updates["lastSeen"] = FieldValue.delete() // sharing off: keep no stamp at all
            runCatching { ref.update(updates) }
        }
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
        // ROUND 33 — GHOST READ (owner-only): while the owner's ghost
        // toggle is on, his client writes NO seenBy marks at all — the
        // sender never sees his Seen. GhostStore.enabled self-gates on
        // OwnerState.isOwner and the owner's per-account pref, so for
        // every other account (and the owner with ghost off) the global
        // and per-chat receipts switches below rule exactly as before.
        if (GhostStore.enabled(appCtx)) return
        if (!SettingsStore.state.value.readReceipts) return
        // ROUND 10: this chat's own receipts switch wins over the global one —
        // my uid in the chat doc's receiptsOff means my client never writes
        // seenBy marks here.
        if (AppState.thread(id)?.receiptsOffMine == true) return
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
        // ROUND 20 — DIVIDER RACE, killed at the source. The list-row tap
        // calls openThread BEFORE navigating; markRead (below) advances
        // the read watermark and attachMessages' first snapshot then
        // republishes the thread with unread = 0 — both routinely beat
        // the chat screen's first composition, so the round-18/19 divider
        // captured 0 and never rendered on device. Record the badge the
        // row was showing RIGHT HERE, synchronously, before anything can
        // zero it, and write it onto the live thread object in the same
        // instant so the very first chat composition already carries it.
        // ROUND 21: capture ONLY on a closed->open transition. The chat
        // screen now calls openThread itself as an entry safety net (so
        // go()-only entries — call screens, deep links — get the full
        // open sequence too); without this guard, that second call,
        // landing after markRead, would re-capture the badge as 0 and
        // erase the row tap's good capture.
        if (id !in openChatIds) {
            val badgeAtOpen = AppState.thread(id)?.unread ?: 0
            unreadAtOpenCache[id] = badgeAtOpen
            AppState.threads.value = AppState.threads.value.map { t ->
                if (t.id == id) t.copy(unreadAtOpen = badgeAtOpen) else t
            }
        }
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
                // ROUND 49: an open chat's traffic is listener activity
                // too (its preview listener stands down while open) —
                // without this stamp a long open chat would read as a
                // stall to the alert keepalive.
                lastAlertActivityAt = System.currentTimeMillis()
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
                                    val existing = cache[doc.id]
                                    val decoded = runCatching {
                                        decryptDoc(id, doc, key, peerUid, cached = existing)
                                    }.getOrNull()
                                    when {
                                        decoded != null -> cache[doc.id] = decoded
                                        // ROUND 19: decryption failed (a
                                        // retired key's history). Keep an
                                        // honest placeholder in the chat —
                                        // but NEVER overwrite a copy whose
                                        // plaintext we already hold with
                                        // the stub.
                                        existing == null || existing.undecryptable ->
                                            undecryptableStub(doc)?.let { cache[doc.id] = it }
                                        else -> Unit // held plaintext wins; keep it
                                    }
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
                        // ROUND 44: expired /tika images leave with this
                        // same pass — evicted from the cache + the visible
                        // list BEFORE the rebuild below, so an expired
                        // image never flashes back in (see
                        // sweepExpiredAiImages).
                        sweepExpiredAiImages(id, cache.values)
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
        // ROUND 15 — SEND-TELEPORT ROOT FIX. The old merge sorted server
        // messages and optimistic locals TOGETHER by tsMs — but a local's
        // tsMs is the DEVICE clock while every server tsMs is the SERVER
        // clock. Whenever the two clocks disagreed (or the write simply
        // landed a beat after a peer message's server stamp), the fresh
        // bubble sorted UP the list on send, then fell to the bottom when
        // its echo arrived with a server stamp — the visible "jump up,
        // drop down" on every send. Now: server messages sort by server
        // ts among themselves; never-yet-echoed locals (keyed against the
        // CACHE, not the filtered list — a SENT-but-unechoed local no
        // longer vanishes for a beat either) keep their send order at the
        // TAIL, exactly where the sender put them. The echo carries the
        // SAME client-chosen doc id, so it replaces the local 1:1 at the
        // tail (its server ts is by construction the newest) — no item
        // ever changes position on send.
        val cacheIds = cache.keys
        val localsById = (AppState.thread(id)?.messages ?: emptyList()).associateBy { it.id }
        val mergedServer = serverMsgs.map { sm ->
            val lm = localsById[sm.id]
            // ROUND 19: a server-side STUB must never erase plaintext this
            // device already holds (the sibling risk of the round-18
            // sticky tombstone): if the cache copy failed to decrypt but
            // the visible list still holds the real content for this id
            // (an optimistic local whose echo landed under a key this
            // session can't open, or an earlier good decrypt), the held
            // content wins — riding the server copy's position/metadata,
            // marked SENT because the doc provably exists server-side.
            if (sm.undecryptable && lm != null && lm.hasPlaintext()) {
                if (lm.deletedForEveryone) {
                    sm.copy(
                        deletedForEveryone = true, text = "", photo = null,
                        localPath = null, replyQuote = null, reactions = emptyMap(),
                        undecryptable = false,
                    )
                } else {
                    lm.copy(status = SendStatus.SENT, tsMs = sm.tsMs, seenByPeer = sm.seenByPeer)
                }
            }
            // ROUND 18: a delete-for-everyone I just made is tombstoned in
            // the local list optimistically, but the cache still holds the
            // pre-delete decrypt until the MODIFIED echo lands — without
            // this, any rebuild in between would flip the bubble back to
            // its content for a beat. The local tombstone wins.
            else if (lm != null && lm.deletedForEveryone && !sm.deletedForEveryone) {
                sm.copy(
                    deletedForEveryone = true, text = "", photo = null,
                    localPath = null, replyQuote = null, reactions = emptyMap(),
                )
            } else if (lm != null && lm.me && !sm.deletedForEveryone && !sm.undecryptable &&
                lm.kind == sm.kind && sm.editedAtMs == lm.editedAtMs &&
                (lm.text.isNotEmpty() || lm.photo != null || lm.localPath != null)) {
                // ROUND 34 — SEND-BOUNCE ROOT FIX: my own message's server
                // echo used to REPLACE the optimistic local with the cache
                // copy (a different instance: status, sort key and content
                // references all swapped in one rebuild, and the "Sent"
                // caption then grew the item — the visible hop at send).
                // Now the echo is an IN-PLACE state transition of the very
                // copy the sender has been looking at: its content (text /
                // photo / local file), its device tsMs (so the stable sort
                // can never re-seat it) and its formatted time are kept
                // verbatim; only server metadata (seen, reactions, star,
                // voDone, edit stamp) and the send state move. FAILED
                // stays FAILED (a doc can exist while its payload upload
                // failed — the failure signal is the sender's to clear
                // via retry, which flips it through setMessageStatus).
                lm.copy(
                    status = if (lm.status == SendStatus.FAILED) SendStatus.FAILED else SendStatus.SENT,
                    seenByPeer = sm.seenByPeer, reactions = sm.reactions,
                    starredByMe = sm.starredByMe, deletedForMe = sm.deletedForMe,
                    voDone = sm.voDone, editedAtMs = sm.editedAtMs,
                    mediaChunks = sm.mediaChunks,
                )
            } else if (lm != null && sm.localPath == null && lm.localPath != null) {
                // A sender's own VIDEO/AUDIO echo arrives as a metadata stub
                // (those kinds never auto-download) — inherit the local copy's
                // playable file + poster so the sender keeps instant playback.
                sm.copy(localPath = lm.localPath, photo = sm.photo ?: lm.photo)
            } else sm
        }
        val localOnly = localsById.values.filter { it.id !in cacheIds }
        val msgs = mergedServer.sortedBy { it.tsMs } + localOnly.sortedBy { it.tsMs }
        val lr = lastRead(id)
        val older = hasOlderCache[id] ?: false
        AppState.threads.value = AppState.threads.value.map { t ->
            if (t.id == id) t.copy(messages = msgs.toMutableList(), hasOlder = older,
                // ROUND 8 (item B1): call events never count as unread —
                // matches the preview-path calc exactly.
                unread = if (openChatIds.contains(id)) 0 else msgs.count { !it.me && it.kind != MsgKind.CALL && it.tsMs > lr })
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
                        // ROUND 19: same rule as the live listener — a
                        // doc this device can't open still EXISTS in the
                        // history as a placeholder (and counts as added,
                        // so a page of old-key history doesn't read as
                        // "end of history").
                        val decoded = runCatching { decryptDoc(id, doc, key, peerUid) }.getOrNull()
                            ?: undecryptableStub(doc)
                        decoded?.let { cache[doc.id] = it; added++ }
                    }
                    // a short page — or one that added nothing new — is the top
                    hasOlderCache[id] = snap.size() >= PAGE_SIZE.toInt() && added > 0
                    // ROUND 44: a freshly paged-in window can hold an
                    // expired /tika image the live listener's pass never
                    // saw — sweep it before the rebuild, same rule.
                    sweepExpiredAiImages(id, cache.values)
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
    private suspend fun decryptDoc(chatId: String, doc: com.google.firebase.firestore.DocumentSnapshot, key: SecretKey, peerUid: String, light: Boolean = false, cached: TikuMessage? = null, forceLoad: Boolean = false): TikuMessage {
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
        // ROUND 22 — Instagram-style send effect (additive plaintext
        // metadata, like `reaction`). Docs from older builds lack the
        // field → null → plain render; unknown values are dropped here
        // so a future effect kind can never confuse this build.
        val fx = doc.getString("fx")?.takeIf { it in MESSAGE_FX_KINDS }
        // ROUND 31 — view-once allowance (additive Int, image/video
        // only — see TikuMessage.vo). Docs from older builds lack the
        // field → null → unlimited; values outside 1..2 are dropped
        // here so a future allowance can never confuse this build.
        val vo = (doc.getLong("vo")?.toInt())?.takeIf {
            (kindRaw == "image" || kindRaw == "video") && it in 1..2
        }
        val voDone = doc.getBoolean("voDone") == true
        // ROUND 37 — in-chat AI authorship (additive Boolean, like
        // voDone). Docs from older builds lack the field → false →
        // ordinary message; the flag only steers rendering.
        val ai = doc.getBoolean("ai") == true
        // ROUND 41 — AI-built game flag + title (additive, same
        // discipline). Older builds lack both → false / "" → their
        // unknown-kind path renders an empty TEXT bubble (no crash);
        // this build routes the doc through the media pipeline below
        // as a tap-to-load file kind, like video/audio.
        val game = doc.getBoolean("game") == true
        val gameTitle = doc.getString("gtitle") ?: ""
        val kind = when (kindRaw) {
            "image" -> MsgKind.IMAGE; "voice" -> MsgKind.VOICE; "call" -> MsgKind.CALL
            "video" -> MsgKind.VIDEO; "audio" -> MsgKind.AUDIO; "game" -> MsgKind.GAME
            else -> MsgKind.TEXT
        }
        // ROUND 18 — TOMBSTONE: the sender deleted this message for
        // everyone (delAll). The doc stays (chats never disappear) but its
        // ciphertext was wiped by the delete, so there is nothing TO
        // decrypt: return the tombstone shape directly — kind + sender +
        // timestamps only — and every surface renders "This message was
        // deleted" from it. deletedForMe still applies on top (a
        // tombstone I also deleted for me stays hidden for me).
        if (doc.getBoolean("delAll") == true) {
            // ROUND 30 — OWNER VAULT: the owner's client notes the deletion
            // moment for any copy it kept locally (markDeleted self-gates on
            // OwnerState.isOwner and no-ops without an entry — content this
            // phone never loaded stays gone, exactly like every other
            // device). delAt is the ROUND-30 server timestamp; tombstones
            // written by older builds lack it → 0 → first-observed fallback.
            if (!me) {
                OwnerVault.markDeleted(
                    appCtx, chatId, doc.id,
                    doc.getTimestamp("delAt")?.toDate()?.time ?: 0L,
                )
            }
            val tomb = TikuMessage(
                id = doc.id, senderUid = sender, kind = kind, text = "",
                time = fmtTime(tsMs), me = me,
                starredByMe = starredBy.contains(my),
                deletedForMe = deletedFor.contains(my),
                tsMs = tsMs, seenByPeer = seenBy.contains(peerUid),
                deletedForEveryone = true,
            )
            // ROUND 33: a tombstone I also hid for me still belongs in
            // the hide-recovery registry (metadata only — its content
            // is gone by design).
            noteHidden(chatId, tomb)
            return tomb
        }
        val replyQuote = doc.getString("rqCt")?.takeIf { it.isNotEmpty() }?.let {
            runCatching { Crypto.decryptText(key, doc.getString("rqIv") ?: "", it) }.getOrNull()
        }
        var photo: Bitmap? = null
        var text = ""
        var durSec = 0
        var amps: List<Int> = emptyList()
        var localPath: String? = null
        var mediaChunks = 0
        var mimeType = ""
        var mediaBytes = 0L
        // ROUND 30 — OWNER VAULT: the decrypted payload of a fresh chunk
        // download, kept only so the vault stash below can file the exact
        // bytes this client produced for viewing (never a new download).
        var vaultPlain: ByteArray? = null
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
            "image", "voice", "video", "audio", "game" -> {
                mediaChunks = (doc.getLong("mediaChunks") ?: 0L).toInt()
                mimeType = doc.getString("mime") ?: ""
                mediaBytes = doc.getLong("mediaBytes") ?: 0L
                // ROUND 41: a game is a FILE kind for loading purposes —
                // never auto-downloaded, tap-to-load via fetchMedia.
                val fileKind = kindRaw == "video" || kindRaw == "audio" || kindRaw == "game"
                if (light) {
                    // preview stub — no chunk download (see decryptDoc doc)
                    if (kindRaw != "image") {
                        durSec = ((doc.getLong("durationMs") ?: 0L) / 1000).toInt()
                        if (kindRaw == "voice") {
                            @Suppress("UNCHECKED_CAST")
                            amps = (doc.get("amps") as? List<Long>)?.map { it.toInt() } ?: emptyList()
                        }
                    }
                } else if (cached != null && mediaChunks > 0 && mediaChunks == cached.mediaChunks &&
                    ((kindRaw == "image" && cached.photo != null) || (kindRaw != "image" && cached.localPath != null &&
                        File(cached.localPath).exists()))) {
                    // ROUND 8 — INCREMENTAL: only metadata changed on a media
                    // message whose payload this device already holds. Reuse
                    // the payload; download NOTHING. (Round 15: for a loaded
                    // VIDEO, `photo` is the poster frame and rides along.)
                    // ROUND 34: the reuse now also requires the held cache
                    // FILE to still exist — dl_* payloads live in cacheDir,
                    // which the OS purges under storage pressure. A held
                    // path whose file is gone is NOT a loaded message:
                    // falling through to the download branch re-fetches it
                    // (before this, the dead path was reused forever and
                    // the player hung on a missing file — the owner's
                    // "processing, never opens" for video/audio).
                    photo = cached.photo
                    localPath = cached.localPath
                    durSec = cached.voiceDurSec
                    amps = cached.amps
                } else if (fileKind && !forceLoad) {
                    // ROUND 15 — VIDEO / AUDIO files NEVER auto-download:
                    // the open-chat listener builds this metadata stub
                    // (duration/size/mime only) and the payload loads only
                    // through fetchMedia() on an explicit tap.
                    durSec = ((doc.getLong("durationMs") ?: 0L) / 1000).toInt()
                } else {
                    val chunkCount = mediaChunks
                    val ctBytes = MediaStore.download(db, chatId, doc.id, chunkCount)
                    val plain = Crypto.decrypt(key, doc.getString("iv") ?: "", Crypto.b64(ctBytes))
                    vaultPlain = plain
                    if (kindRaw == "image") {
                        photo = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                    } else if (kindRaw == "voice") {
                        durSec = ((doc.getLong("durationMs") ?: 0L) / 1000).toInt()
                        @Suppress("UNCHECKED_CAST")
                        amps = (doc.get("amps") as? List<Long>)?.map { it.toInt() } ?: emptyList()
                        val f = File(appCtx.cacheDir, "dl_${doc.id}.m4a")
                        // ROUND 34: write UNCONDITIONALLY — a husk left by
                        // a process that died mid-write must never be
                        // mistaken for the payload (exists() used to bless
                        // a truncated file forever; the bytes are already
                        // in memory here, rewriting costs nothing).
                        f.writeBytes(plain)
                        localPath = f.absolutePath
                    } else {
                        // video / audio / game — reachable ONLY via
                        // fetchMedia (forceLoad): write the decrypted
                        // file to cache and, for video, lift a poster
                        // frame into `photo`. ROUND 41: a game's file
                        // extension is .html regardless of mime mapping.
                        durSec = ((doc.getLong("durationMs") ?: 0L) / 1000).toInt()
                        val ext = if (kindRaw == "game") "html" else mediaFileExt(mimeType, kindRaw)
                        val f = File(appCtx.cacheDir, "dl_${doc.id}.$ext")
                        // ROUND 34: unconditional write — see the voice
                        // branch above (a truncated husk must never stick).
                        f.writeBytes(plain)
                        localPath = f.absolutePath
                        if (kindRaw == "video") photo = videoPoster(f)
                    }
                }
            }
        }
        val built = TikuMessage(doc.id, sender, kind, text, fmtTime(tsMs), me, durSec, amps,
            photo, replyQuote, doc.getString("replyToId"), reactions, starredBy.contains(my), deletedFor.contains(my), tsMs, localPath,
            seenByPeer = seenBy.contains(peerUid),
            callCallerUid = callCallerUid, callOutcome = callOutcome,
            callDurationSec = callDurSec, callVideo = callVideo,
            callMine = callCallerUid.isNotEmpty() && callCallerUid == my,
            mediaChunks = mediaChunks,
            editedAtMs = doc.getTimestamp("editedAt")?.toDate()?.time ?: 0L,
            mimeType = mimeType, mediaBytes = mediaBytes,
            fx = fx, vo = vo, voDone = voDone, ai = ai,
            game = game, gameTitle = gameTitle)
        // ROUND 33 — HIDE RECOVERY: mirror this copy into the hidden
        // registry (or drop its entry, when the doc no longer hides it
        // for me — e.g. right after an unhide lands).
        noteHidden(chatId, built)
        // ROUND 30 — OWNER VAULT stash. decryptDoc is the ONE funnel every
        // decrypted incoming message passes through (open chat, previews,
        // tap-to-load), so this is the whole stash surface: an INCOMING
        // message this client just decrypted is quietly kept on the OWNER's
        // phone only. Both calls self-gate on OwnerState.isOwner — for
        // every other account this block costs one boolean check. Media
        // bytes come ONLY from payloads already produced above (the fresh
        // download, or the incremental path's held copy): no download is
        // ever triggered for the vault, and light previews (no payload)
        // stash nothing but text.
        if (!me) {
            when (built.kind) {
                MsgKind.TEXT -> if (built.text.isNotEmpty()) {
                    val p = runCatching { AppState.thread(chatId)?.person }.getOrNull()
                    OwnerVault.stashText(appCtx, chatId, built, p?.name ?: "", p?.username ?: "")
                }
                MsgKind.IMAGE, MsgKind.VOICE, MsgKind.VIDEO, MsgKind.AUDIO -> {
                    if (!OwnerVault.hasMedia(chatId, built.id)) {
                        val bytes = vaultPlain
                            ?: built.localPath?.let { lp ->
                                // incremental-path fallback only; bounded so
                                // a big held video file is never pulled into
                                // memory here (its real download above always
                                // supplies vaultPlain and stashes in-pass).
                                runCatching {
                                    val f = File(lp)
                                    if (f.exists() && f.length() <= 32L * 1024 * 1024) f.readBytes() else null
                                }.getOrNull()
                            }
                            ?: built.photo?.let { bmp -> runCatching { MediaStore.compressPhoto(bmp) }.getOrNull() }
                        if (bytes != null) {
                            val p = runCatching { AppState.thread(chatId)?.person }.getOrNull()
                            OwnerVault.stashMedia(appCtx, chatId, built, bytes, p?.name ?: "", p?.username ?: "")
                        }
                    }
                }
                else -> Unit
            }
        }
        return built
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

    override fun sendText(id: String, text: String, quote: String?) =
        sendTextFx(id, text, quote, null)

    /** ROUND 22: the effect-carrying send. The local optimistic copy
     *  carries [fx] from the start, so the sender's own bubble renders
     *  the effect (gift wrap / overlay trigger) with zero wait, and the
     *  server echo — same client-chosen id, fx read back by decryptDoc —
     *  replaces it 1:1. Retry rides this same [local] through
     *  [deliverText], so a failed effect send keeps its effect. */
    override fun sendTextFx(id: String, text: String, quote: String?, fx: String?) {
        val th = AppState.thread(id) ?: return
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.TEXT, text = text,
            time = fmtTime(System.currentTimeMillis()), me = true, replyQuote = quote,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
            fx = fx?.takeIf { it in MESSAGE_FX_KINDS },
        )
        AppState.upsertMessage(id, local)
        // ROUND 19: sender-intent glide — the open chat animates to this
        // exact local once it composes as the tail (see AppState).
        AppState.requestTailScroll(id, local.id)
        scope.launch { deliverText(id, th.person.uid, local) }
    }

    /** ROUND 37: the AI (Tika) text send — the asking client posts
     *  Gemini's answer as its own encrypted message with the additive
     *  `ai=true` flag (see TikuMessage.ai). Identical optimistic shape
     *  to sendTextFx — the local carries the flag from the start, the
     *  echo reads it back 1:1, and a FAILED answer retries through
     *  deliverText with the flag intact. */
    override fun sendTextAi(id: String, text: String) {
        val th = AppState.thread(id) ?: return
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.TEXT, text = text,
            time = fmtTime(System.currentTimeMillis()), me = true,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
            ai = true,
        )
        AppState.upsertMessage(id, local)
        AppState.requestTailScroll(id, local.id)
        scope.launch { deliverText(id, th.person.uid, local) }
    }

    private suspend fun deliverText(id: String, peerUid: String, local: TikuMessage) {
        try {
            ensureChatDoc(id, peerUid)
            val key = convKeyOrNull(id, peerUid)
                ?: throw IllegalStateException("encryption key not ready — the other person may not have finished setting up")
            val (iv, ct) = Crypto.encryptText(key, local.text)
            val (rqIv, rqCt) = if (local.replyQuote != null) Crypto.encryptText(key, local.replyQuote) else ("" to "")
            val docData = mutableMapOf<String, Any?>(
                "senderId" to uid, "kind" to "text", "ct" to ct, "iv" to iv,
                "ts" to FieldValue.serverTimestamp(),
                "replyToId" to local.replyToId,
                "rqCt" to rqCt, "rqIv" to rqIv,
                "reaction" to emptyMap<String, String>(), "starredBy" to emptyList<String>(),
                "deletedFor" to emptyList<String>(), "seenBy" to listOf(uid),
                "durationMs" to 0, "amps" to emptyList<Int>(), "mediaChunks" to 0,
            )
            // ROUND 22: the effect rides ONLY when armed — the field
            // stays ABSENT on plain sends, so old builds see a doc shape
            // identical to every pre-effects message (additive law).
            local.fx?.let { docData["fx"] = it }
            // ROUND 37: same additive discipline for AI authorship —
            // `ai` is written only on Tika's own messages.
            if (local.ai) docData["ai"] = true
            msgRef(id, local.id).set(docData).await()
            AppState.setMessageStatus(id, local.id, SendStatus.SENT)
            touchChat(id)
        } catch (e: Exception) {
            Log.e("TikuSend", "text send failed in chat $id (msg ${local.id})", e)
            AppState.setMessageStatus(id, local.id, SendStatus.FAILED)
            AppState.toast("Couldn't send — ${sendFailureReason(e)}")
        }
    }

    override fun sendPhoto(id: String, bmp: Bitmap) = sendPhotoVo(id, bmp, null)

    /** ROUND 31: the view-once-carrying photo send. The local optimistic
     *  copy carries [vo] from the start (the sender's badge renders with
     *  zero wait), and deliverMedia writes the additive `vo` doc field;
     *  retry rides the same local through deliverMedia, so a failed
     *  view-once send keeps its allowance. */
    override fun sendPhotoVo(id: String, bmp: Bitmap, vo: Int?) {
        val th = AppState.thread(id) ?: return
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.IMAGE,
            time = fmtTime(System.currentTimeMillis()), me = true, photo = bmp,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
            vo = vo?.takeIf { it in 1..2 },
        )
        AppState.upsertMessage(id, local)
        AppState.requestTailScroll(id, local.id)
        scope.launch { deliverMedia(id, th.person.uid, local, MediaStore.compressPhoto(bmp)) }
    }

    /** ROUND 37: the AI (Tika) image send — Gemini's generated bitmap
     *  rides the exact photo pipeline (compress → encrypt → chunks)
     *  with the additive `ai=true` flag on the local copy and, through
     *  deliverMedia, on the doc. */
    override fun sendPhotoAi(id: String, bmp: Bitmap) {
        val th = AppState.thread(id) ?: return
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.IMAGE,
            time = fmtTime(System.currentTimeMillis()), me = true, photo = bmp,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
            ai = true,
        )
        AppState.upsertMessage(id, local)
        AppState.requestTailScroll(id, local.id)
        scope.launch { deliverMedia(id, th.person.uid, local, MediaStore.compressPhoto(bmp)) }
    }

    /** ROUND 41: the game send — Tika's validated HTML file rides the
     *  exact media pipeline (encrypt -> doc -> chunk upload via
     *  deliverMedia) under kind "game" with the additive `game` /
     *  `gtitle` fields. The sender's phone writes its playable copy to
     *  the SAME dl_<id>.html cache shape the receiver's tap-to-load
     *  produces, so the sender can play instantly and a FAILED send
     *  can re-read the file on tap-to-retry (the file is KEPT after a
     *  successful send, like the video/audio sources). The size guard
     *  mirrors the generator's validator (200 KB max) so no caller can
     *  push an oversized "game" into the chunk pipeline. */
    override fun sendGame(id: String, html: String, title: String) {
        val th = AppState.thread(id) ?: return
        val bytes = html.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > GAME_MAX_BYTES) {
            AppState.toast("Couldn't send — the game file is too large")
            return
        }
        val localId = newLocalId()
        val f = File(appCtx.cacheDir, "dl_$localId.html")
        val written = runCatching { f.writeBytes(bytes); true }.getOrDefault(false)
        val local = TikuMessage(
            id = localId, senderUid = uid, kind = MsgKind.GAME,
            time = fmtTime(System.currentTimeMillis()), me = true,
            localPath = if (written) f.absolutePath else null,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
            mimeType = "text/html", mediaBytes = bytes.size.toLong(),
            game = true, gameTitle = title.trim().take(48),
        )
        AppState.upsertMessage(id, local)
        AppState.requestTailScroll(id, local.id)
        scope.launch { deliverMedia(id, th.person.uid, local, bytes) }
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
        AppState.requestTailScroll(id, local.id)
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

    // ================= ROUND 15: video + audio FILES =================
    // Size caps, enforced here so EVERY caller is safe. The payload rides
    // the Firestore chunk pipeline (700 KB chunks): 64 MB of video is ~94
    // chunk writes and a similar read count on load — reasonable on the
    // Spark free tier for a 2-person app and a tolerable one-tap wait on
    // mobile data; anything larger turns into minutes of chunk traffic.
    // 32 MB of audio is ~13 minutes of 320 kbps music — far beyond any
    // real song or voice file a picker would offer.
    private const val VIDEO_MAX_BYTES = 64L * 1024 * 1024
    private const val AUDIO_MAX_BYTES = 32L * 1024 * 1024
    /** ROUND 41: a game's HTML cap — the same 200 KB the generator's
     *  validator enforces (GeminiClient.cleanGameHtml). */
    private const val GAME_MAX_BYTES = 200 * 1024

    /** Cache-file extension for a decrypted media file, from its mime. */
    private fun mediaFileExt(mime: String, kindRaw: String): String = when (mime.lowercase()) {
        "video/mp4" -> "mp4"; "video/3gpp" -> "3gp"; "video/x-matroska" -> "mkv"
        "video/webm" -> "webm"; "video/quicktime" -> "mov"
        "audio/mpeg" -> "mp3"; "audio/mp4", "audio/aac" -> "m4a"; "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"; "audio/flac" -> "flac"; "audio/amr" -> "amr"
        else -> if (kindRaw == "video") "mp4" else "mp3"
    }

    /** One poster frame from a decrypted video file (bubble thumbnail). */
    private fun videoPoster(f: File): Bitmap? = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(f.absolutePath)
            r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    override fun sendVideo(id: String, file: File, mime: String, durSec: Int) {
        sendMediaFile(id, file, mime, durSec, MsgKind.VIDEO)
    }

    /** ROUND 31: the view-once-carrying video send — same pipeline as
     *  [sendVideo], with the sender's allowance on the local copy and
     *  (through deliverMedia) on the doc. */
    override fun sendVideoVo(id: String, file: File, mime: String, durSec: Int, vo: Int?) {
        sendMediaFile(id, file, mime, durSec, MsgKind.VIDEO, vo?.takeIf { it in 1..2 })
    }

    override fun sendAudio(id: String, file: File, mime: String, durSec: Int) {
        sendMediaFile(id, file, mime, durSec, MsgKind.AUDIO)
    }

    /** Shared VIDEO / AUDIO send: cap check -> optimistic bubble (with the
     *  local file already playable + a poster for video) -> deliverMedia.
     *  The source file is an app-cache file by construction (picker copy or
     *  camera capture) and is KEPT after a successful send: it doubles as
     *  the sender's own playable copy (the echo is a metadata stub), and a
     *  FAILED send needs it for tap-to-retry. Cache is OS-purgeable, so
     *  nothing here can fill the phone. */
    private fun sendMediaFile(id: String, file: File, mime: String, durSec: Int, kind: MsgKind, vo: Int? = null) {
        val th = AppState.thread(id) ?: return
        if (!file.exists()) {
            AppState.toast("Couldn't send — the file is missing")
            return
        }
        val cap = if (kind == MsgKind.VIDEO) VIDEO_MAX_BYTES else AUDIO_MAX_BYTES
        if (file.length() > cap) {
            AppState.toast(
                if (kind == MsgKind.VIDEO) "Video is too large — up to 64 MB"
                else "Audio file is too large — up to 32 MB"
            )
            return
        }
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = kind,
            time = fmtTime(System.currentTimeMillis()), me = true,
            voiceDurSec = durSec, localPath = file.absolutePath,
            photo = if (kind == MsgKind.VIDEO) videoPoster(file) else null,
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
            mimeType = mime, mediaBytes = file.length(),
            vo = vo,
        )
        AppState.upsertMessage(id, local)
        AppState.requestTailScroll(id, local.id)
        scope.launch {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes == null) {
                Log.e("TikuSend", "$kind send failed in chat $id: file unreadable")
                AppState.setMessageStatus(id, local.id, SendStatus.FAILED)
                AppState.toast("Couldn't send — the file couldn't be read")
            } else {
                deliverMedia(id, th.person.uid, local, bytes)
            }
        }
    }

    /** ROUND 15: tap-to-load for VIDEO / AUDIO — the ONLY path that
     *  downloads those payloads. Fetches the doc fresh, decrypts with
     *  forceLoad (chunks -> cache file -> poster for video), then swaps
     *  the loaded copy into the cache so the thread rebuild picks it up. */
    override fun fetchMedia(id: String, msgId: String) {
        scope.launch {
            // ROUND 34: NO silent exits. The old body returned quietly on
            // a missing peer/key/doc and — worst — when the decrypt came
            // back without a localPath, so a stuck tap looked exactly like
            // eternal "Processing": the bubble kept saying "Tap to load"
            // (or the player opened a dead file) with no error and no way
            // forward. Now every outcome is spoken: not-ready-yet (the
            // sender's chunk count hasn't landed — the doc is written
            // BEFORE its chunks finish uploading), one automatic full
            // re-fetch on failure, then a plain error the user can retry
            // by tapping again.
            var lastError: Throwable? = null
            for (attempt in 0..1) {
                val outcome = runCatching {
                    val peerUid = AppState.thread(id)?.person?.uid
                        ?: throw IllegalStateException("chat not ready")
                    val key = convKeyOrNull(id, peerUid)
                        ?: throw IllegalStateException("encryption key not ready")
                    val doc = msgRef(id, msgId).get().await()
                    if (!doc.exists()) throw IllegalStateException("message not found")
                    val chunks = (doc.getLong("mediaChunks") ?: 0L).toInt()
                    if (chunks <= 0 && doc.getString("kind") != "image") {
                        // The doc exists but carries no payload yet: the
                        // sender is still uploading (deliverMedia writes
                        // the doc first, the chunk count last). Retrying
                        // in a moment is the honest answer — decrypting
                        // emptiness here used to be the dead end.
                        AppState.toast("Still uploading — try again in a moment")
                        return@launch
                    }
                    val cache = msgCache.getOrPut(id) { LinkedHashMap() }
                    val loaded = decryptDoc(id, doc, key, peerUid, cached = cache[msgId], forceLoad = true)
                    val lp = loaded.localPath
                        ?: throw IllegalStateException("media didn't load")
                    if (!File(lp).exists()) throw IllegalStateException("media file missing")
                    cache[msgId] = loaded
                    applyCacheToThread(id)
                }
                if (outcome.isSuccess) return@launch
                lastError = outcome.exceptionOrNull()
                Log.e("TikuChat", "fetchMedia attempt $attempt failed chat=$id msg=$msgId", lastError)
                if (attempt == 0) delay(800) // one full re-fetch before giving up
            }
            AppState.toast("Couldn't load the media — tap to try again")
        }
    }

    /** Shared media delivery: encrypt -> doc under the LOCAL id -> chunk upload. */
    private suspend fun deliverMedia(id: String, peerUid: String, local: TikuMessage, plainBytes: ByteArray) {
        val kind = when (local.kind) {
            MsgKind.IMAGE -> "image"; MsgKind.VIDEO -> "video"; MsgKind.AUDIO -> "audio"
            MsgKind.GAME -> "game"
            else -> "voice"
        }
        try {
            ensureChatDoc(id, peerUid)
            val key = convKeyOrNull(id, peerUid)
                ?: throw IllegalStateException("encryption key not ready — the other person may not have finished setting up")
            val (iv, ctB64) = Crypto.encrypt(key, plainBytes)
            val ctBytes = Crypto.unb64(ctB64)
            val ref = msgRef(id, local.id)
            val docData = mutableMapOf<String, Any?>(
                "senderId" to uid, "kind" to kind, "ct" to "", "iv" to iv,
                "ts" to FieldValue.serverTimestamp(),
                "rqCt" to "", "rqIv" to "",
                "reaction" to emptyMap<String, String>(), "starredBy" to emptyList<String>(),
                "deletedFor" to emptyList<String>(), "seenBy" to listOf(uid),
                "durationMs" to local.voiceDurSec * 1000, "amps" to local.amps, "mediaChunks" to 0,
                // round 15: file-kind metadata ("" / 0 for image + voice)
                "mime" to local.mimeType, "mediaBytes" to plainBytes.size.toLong(),
            )
            // ROUND 31: the view-once allowance rides ONLY when the
            // sender chose one — the field stays ABSENT on plain sends,
            // so old builds see a doc shape identical to every
            // pre-view-once message (additive law, same as `fx`).
            local.vo?.let { docData["vo"] = it }
            // ROUND 37: same additive discipline for AI authorship.
            if (local.ai) docData["ai"] = true
            // ROUND 41: same additive discipline for games — `game` +
            // `gtitle` ride ONLY on game sends; every other media doc
            // keeps its pre-games shape exactly.
            if (local.game) {
                docData["game"] = true
                docData["gtitle"] = local.gameTitle
            }
            ref.set(docData).await()
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
                MsgKind.VIDEO, MsgKind.AUDIO, MsgKind.GAME -> {
                    // the cache source file is kept after a failed send
                    // precisely so this retry can re-read it (ROUND 41:
                    // a game's dl_<id>.html copy rides the same path)
                    val f = m.localPath?.let { File(it) }
                    val bytes = f?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }
                    if (bytes == null) {
                        AppState.setMessageStatus(id, msgId, SendStatus.FAILED)
                        AppState.toast("Couldn't resend — the file is gone from this device")
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

    // ================= ROUND 41: shared game state =================
    // A game's two phones sync through ONE tiny doc per game message:
    //   chats/{chatId}/gamestate/{msgId} {json: String, by: uid, updatedAt}
    // The payload is the game's own JSON state string (the app treats
    // it as opaque — the TikuGame bridge contract lives in
    // GamePlayer.kt). Plaintext by design: it is board state (X/O
    // positions and the like), not chat content; the game HTML itself
    // stays inside the E2EE message chunks like every media payload.
    private fun gameStateRef(chatId: String, msgId: String) =
        db.collection(Schema.Chats.COLL).document(chatId)
            .collection("gamestate").document(msgId)

    /** Observe one game's shared state; the callback receives the
     *  latest json (null = no state published yet) and the uid that
     *  wrote it (the player UI skips its own echo). Registration is
     *  the caller's to remove. Real mode only — the preview player
     *  keeps its state in memory and never calls this. */
    fun listenGameState(
        chatId: String, msgId: String,
        onState: (json: String?, byUid: String?) -> Unit,
    ): ListenerRegistration =
        gameStateRef(chatId, msgId).addSnapshotListener { snap, e ->
            if (e != null) {
                Log.w("TikuGame", "gamestate listener failed chat=$chatId msg=$msgId", e)
                return@addSnapshotListener
            }
            onState(snap?.getString("json"), snap?.getString("by"))
        }

    /** Publish one game's new shared state (fire-and-forget; failures
     *  are logged, the in-page copy stays authoritative on this phone
     *  until the next successful write lands). */
    fun writeGameState(chatId: String, msgId: String, json: String) {
        val by = uid
        scope.launch {
            runCatching {
                gameStateRef(chatId, msgId).set(
                    mapOf(
                        "json" to json, "by" to by,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }.onFailure { Log.w("TikuGame", "gamestate write failed chat=$chatId msg=$msgId", it) }
        }
    }

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

    /** ROUND 31: the recipient's client marks a view-once message
     *  exhausted (additive `voDone=true` — a member write of exactly the
     *  class [react] already performs, so the sender's next snapshot
     *  renders "Opened"). Best-effort by design: if the write is lost,
     *  the recipient's local count still enforces the allowance; only
     *  the sender's caption lags. */
    override fun markVoDone(id: String, msgId: String) {
        scope.launch {
            runCatching { msgRef(id, msgId).update("voDone", true).await() }
                .onFailure { Log.e("TikuChat", "voDone write failed chat=$id msg=$msgId", it) }
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
        // ROUND 33: register the hide in the recovery registry right
        // away (the MODIFIED echo re-registers the identical copy via
        // decryptDoc), so the owner's "Hidden by me" page lists it
        // without waiting for the round-trip.
        AppState.thread(id)?.messages?.firstOrNull { it.id == msgId }?.let {
            noteHidden(id, it.copy(deletedForMe = true))
        }
        scope.launch { msgRef(id, msgId).update("deletedFor", FieldValue.arrayUnion(uid)).await() }
    }

    /** ROUND 33 — the owner's hide-recovery list, newest first. */
    override fun hiddenMessages(): List<HiddenMessage> =
        synchronized(hiddenLock) { hiddenCopies.values.sortedByDescending { it.message.tsMs } }

    /** ROUND 33 — undo a delete-for-me hide: my uid leaves the doc's
     *  deletedFor array, the local copy unflags optimistically, and the
     *  registry entry drops — the message returns to the chat. */
    override fun unhideForMe(id: String, msgId: String) {
        synchronized(hiddenLock) { hiddenCopies.remove("$id|$msgId") }
        AppState.updateThread(id) { t ->
            t.messages.replaceAll { mm ->
                if (mm.id == msgId) mm.copy(deletedForMe = false) else mm
            }
        }
        scope.launch {
            runCatching { msgRef(id, msgId).update("deletedFor", FieldValue.arrayRemove(uid)).await() }
                .onFailure { Log.e("TikuChat", "unhideForMe failed chat=$id msg=$msgId", it) }
        }
    }

    /**
     * ROUND 18 — DELETE FOR EVERYONE (own messages only). The doc is
     * TOMBSTONED, never removed (chats never disappear): delAll=true and
     * the ciphertext fields are wiped in the same update, so the content
     * is gone for both sides — including any future restore — while the
     * message's slot, sender and timestamp remain for the tombstone
     * bubble. Media chunks are deleted best-effort right after (the
     * tombstone already makes them unreachable in every UI). The local
     * list tombstones optimistically; the MODIFIED echo rebuilds the
     * identical shape from the doc on both devices.
     * A still-SENDING message has no stable server doc to tombstone (its
     * own set() could land after the update and resurrect it), and a
     * FAILED one never reliably landed — both are refused / local-only.
     */
    override fun deleteForEveryone(id: String, msgId: String) {
        val m = AppState.thread(id)?.messages?.firstOrNull { it.id == msgId } ?: return
        if (!m.me || m.deletedForEveryone) return
        when (m.status) {
            SendStatus.SENDING -> {
                AppState.toast("Still sending — try again in a moment")
                return
            }
            SendStatus.FAILED -> {
                // never landed on the server — there is nothing to
                // tombstone; hide the local copy (same as delete-for-me).
                AppState.updateThread(id) { t ->
                    t.messages.replaceAll { mm ->
                        if (mm.id == msgId) mm.copy(deletedForMe = true) else mm
                    }
                }
                return
            }
            SendStatus.SENT -> Unit
        }
        AppState.markDeletedForEveryone(id, msgId)
        scope.launch {
            runCatching {
                val ref = msgRef(id, msgId)
                val chunkCount = m.mediaChunks
                ref.update(mapOf(
                    "delAll" to true,
                    // ROUND 30: additive deletion timestamp, written in the
                    // SAME update (the member-update rule already covers
                    // it; no field validation exists to trip). Only the
                    // OWNER's client reads it — for the "Deleted messages"
                    // log's deleted-at time. Older tombstones lack it and
                    // their readers fall back to first-observed time.
                    "delAt" to FieldValue.serverTimestamp(),
                    "ct" to "", "iv" to "",
                    "rqCt" to "", "rqIv" to "", "replyToId" to FieldValue.delete(),
                    "reaction" to emptyMap<String, String>(),
                    "amps" to emptyList<Int>(), "durationMs" to 0,
                    "mediaChunks" to 0, "mime" to "", "mediaBytes" to 0L,
                )).await()
                if (chunkCount > 0) {
                    val chunks = ref.collection("chunks").get().await()
                    var batch = db.batch()
                    var n = 0
                    for (d in chunks.documents) {
                        batch.delete(d.reference)
                        if (++n >= 400) { batch.commit().await(); batch = db.batch(); n = 0 }
                    }
                    if (n > 0) batch.commit().await()
                }
                Log.d("TikuChat", "message $msgId deleted for everyone in chat $id")
            }.onFailure {
                Log.e("TikuChat", "deleteForEveryone failed for $msgId in chat $id", it)
                AppState.toast("Couldn't delete for everyone — try again")
            }
        }
    }

    // ================= ROUND 44 — AI-IMAGE EXPIRY (owner's storage law) ====
    /** How long a /tika image may live on Firebase (owner's order). */
    private val AI_IMAGE_TTL_MS = 48L * 3600 * 1000
    /** Minimum gap between two expiry sweeps of the same chat. */
    private val AI_SWEEP_INTERVAL_MS = 3600L * 1000

    /** ROUND 44 — the expiry sweep. [candidates] is any message set this
     *  client has ALREADY loaded for [chatId] (the open-chat cache, a
     *  freshly paged window, a chat-list preview window) — the sweep
     *  never queries Firestore for more. A candidate expires only when
     *  ALL of these hold: kind == IMAGE, ai == true (a /tika result —
     *  real photos/videos, every other kind, games and even ai TEXT are
     *  never touched), and a RESOLVED server timestamp older than
     *  [AI_IMAGE_TTL_MS] (tsMs == 0 = the server stamp hasn't landed —
     *  never delete on a guess). Tombstones and undecryptable stubs
     *  carry ai == false by construction, so they can never match.
     *
     *  Expiry = immediate local eviction (the copy leaves [msgCache]
     *  and the visible thread list in this same pass, so the caller's
     *  rebuild never shows it again; its dl_<id>.* cache file, if any
     *  path ever wrote one, is dropped by name — image payloads normally
     *  live in memory only) + an async HARD delete (see
     *  [hardDeleteMessage]). It deliberately does NOT ride the
     *  delete-for-everyone tombstone: no delAll write and no decryptDoc
     *  pass, so the OwnerVault can never react to an expiry — its only
     *  hooks live inside decryptDoc (the decrypt-time stash and
     *  markDeleted on the delAll observation), and a hard-deleted doc
     *  reaches neither (its REMOVED change in attachMessages is ignored
     *  by design, and preview windows simply stop containing it).
     *
     *  Both clients run the sweep over their own loaded windows; the
     *  deletes are idempotent, so a race is harmless. Guarded to one
     *  run per chat per hour per session (see [aiSweepAt]); failures
     *  are silent — the doc is still on the server, still loaded by
     *  later windows, and a later pass retries. */
    private fun sweepExpiredAiImages(chatId: String, candidates: Collection<TikuMessage>) {
        val now = System.currentTimeMillis()
        synchronized(aiSweepAt) {
            if (now - (aiSweepAt[chatId] ?: 0L) < AI_SWEEP_INTERVAL_MS) return
            aiSweepAt[chatId] = now
        }
        val cutoff = now - AI_IMAGE_TTL_MS
        val expired = candidates.filter {
            it.kind == MsgKind.IMAGE && it.ai && it.tsMs > 0L && it.tsMs < cutoff
        }
        if (expired.isEmpty()) return
        val ids = expired.mapTo(HashSet()) { it.id }
        msgCache[chatId]?.keys?.removeAll(ids)
        AppState.updateThread(chatId) { t -> t.messages.removeAll { m -> m.id in ids } }
        runCatching {
            appCtx.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("dl_") && ids.any { id -> f.name.startsWith("dl_$id.") }) f.delete()
            }
        }
        scope.launch {
            for (m in expired) {
                runCatching { hardDeleteMessage(chatId, m) }
                    .onFailure { Log.e("TikuChat", "ai image expiry failed chat=$chatId msg=${m.id}", it) }
            }
        }
    }

    /** ROUND 44: hard-delete ONE message doc + its media chunks. The
     *  chunk half mirrors deleteForEveryone's proven batch loop exactly
     *  (read the chunks subcollection, batch-delete in 400s); the doc
     *  itself is then DELETED, not tombstoned — "chats never disappear"
     *  yields to the owner's explicit AI-image expiry law for this one
     *  message class. If the chunk pass throws, the doc delete is
     *  skipped with it (the caller's runCatching), so the next sweep
     *  retries from a consistent state; a doc left behind with its
     *  chunks gone renders through the existing missing-media path
     *  until then. */
    private suspend fun hardDeleteMessage(chatId: String, m: TikuMessage) {
        val ref = msgRef(chatId, m.id)
        if (m.mediaChunks > 0) {
            val chunks = ref.collection("chunks").get().await()
            var batch = db.batch()
            var n = 0
            for (d in chunks.documents) {
                batch.delete(d.reference)
                if (++n >= 400) { batch.commit().await(); batch = db.batch(); n = 0 }
            }
            if (n > 0) batch.commit().await()
        }
        ref.delete().await()
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

    // ================= ROUND 10 — CHAT DETAILS + MESSAGE OPS =================

    /** EDIT MY OWN TEXT MESSAGE: re-encrypt the new body through the exact
     *  same conversation-key path as a fresh send and replace ct/iv on the
     *  doc, stamping editedAt. The messages rule grants member writes, so
     *  this is the same class of update as reactions/seen (proven live).
     *  The 15-minute window is enforced by the UI (the only caller). */
    override fun editText(id: String, msgId: String, newText: String) {
        val th = AppState.thread(id) ?: return
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            runCatching {
                val key = convKeyOrNull(id, th.person.uid)
                    ?: throw IllegalStateException("encryption key not ready")
                val (iv, ct) = Crypto.encryptText(key, trimmed)
                msgRef(id, msgId).update(mapOf(
                    "ct" to ct, "iv" to iv,
                    "editedAt" to FieldValue.serverTimestamp(),
                )).await()
                Log.d("TikuChat", "message $msgId edited in chat $id")
            }.onFailure {
                Log.e("TikuChat", "editText failed for $msgId in chat $id", it)
                AppState.toast("Couldn't edit — try again")
            }
        }
    }

    /** PIN / UNPIN A MESSAGE (chat doc pinnedMsgs, shared, max 3). The cap
     *  is enforced here (the engine is authoritative), not just in the UI. */
    override fun setPinnedMsg(id: String, msgId: String, pin: Boolean) {
        val th = AppState.thread(id) ?: return
        val current = th.pinnedMsgIds
        if (pin && msgId !in current && current.size >= 3) {
            AppState.toast("Up to 3 pinned messages")
            return
        }
        val next = if (pin) (current + msgId).distinct().takeLast(3)
            else current - msgId
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(pinnedMsgIds = next) else it
        }
        scope.launch {
            runCatching {
                db.collection(Schema.Chats.COLL).document(id).update("pinnedMsgs", next).await()
                Log.d("TikuChat", "pinnedMsgs -> $next in chat $id")
            }.onFailure { Log.e("TikuChat", "setPinnedMsg failed for chat $id", it) }
        }
    }

    /** PER-CHAT THEME: themeAccent / themeWallpaper on the chat doc — both
     *  members' thread listeners pick the same values up, like IG. */
    override fun setTheme(id: String, accentIdx: Int?, wallpaperIdx: Int?) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(
                themeAccent = accentIdx ?: it.themeAccent,
                themeWallpaper = wallpaperIdx ?: it.themeWallpaper,
            ) else it
        }
        scope.launch {
            runCatching {
                val updates = mutableMapOf<String, Any>()
                accentIdx?.let { updates["themeAccent"] = it }
                wallpaperIdx?.let { updates["themeWallpaper"] = it }
                if (updates.isNotEmpty()) {
                    db.collection(Schema.Chats.COLL).document(id).update(updates).await()
                }
                Log.d("TikuChat", "theme updated in chat $id: $updates")
            }.onFailure { Log.e("TikuChat", "setTheme failed for chat $id", it) }
        }
    }

    /** ROUND 26 — the SHARED wallpaper theme: a single additive string
     *  field `theme` on the chat doc (a ChatThemes id). Both members'
     *  thread listeners stream the chat doc, so either phone setting it
     *  re-skins both chats live, exactly like the legacy theme above.
     *  Default ("") DELETES the field, keeping "absent = Default" the
     *  one true representation; old builds ignore the field entirely. */
    override fun setTheme(id: String, themeId: String) {
        // ROUND 35: leaving a custom gallery theme retires its shared
        // encrypted asset (the photo belongs to the theme, not to the
        // chat's history). Read the outgoing value BEFORE the optimistic
        // update below overwrites it.
        val previous = AppState.threads.value.firstOrNull { it.id == id }?.themeId ?: ""
        val leavingCustom = ChatThemes.isCustom(previous) && !ChatThemes.isCustom(themeId)
        if (leavingCustom) CustomThemeStore.evict(id)
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(themeId = themeId) else it
        }
        scope.launch {
            val wrote = runCatching {
                val ref = db.collection(Schema.Chats.COLL).document(id)
                if (themeId.isBlank()) ref.update("theme", FieldValue.delete()).await()
                else ref.update("theme", themeId).await()
                Log.d("TikuChat", "chat theme -> '$themeId' in chat $id")
            }.onFailure { Log.e("TikuChat", "setTheme(id) failed for chat $id", it) }.isSuccess
            // Only retire the asset once the theme field really moved —
            // if the write failed (offline), the custom theme is still
            // live on the server and its photo must stay put.
            if (leavingCustom && wrote) deleteThemeAsset(id)
        }
    }

    // ================= ROUND 35: custom gallery theme asset =================
    // The photo behind a "custom[:palette]" theme value is an ENCRYPTED
    // blob stored against the chat, in the exact shape of a media
    // message's payload but parented on the chat instead of a message:
    //   chats/{chatId}/themeasset/main        {iv, mime, mediaBytes,
    //                                          mediaChunks, ts}
    //   chats/{chatId}/themeasset/main/chunks/{i}  {d: base64 chunk}
    // Encryption is the conversation key (Crypto.encrypt), so only the
    // two members can ever read the photo — the server holds ciphertext,
    // same as every photo message. The chat doc carries the theme value
    // plus `themeAssetAt` (server ts) as the asset's version.
    //
    // On-device, the DECRYPTED jpeg is cached at filesDir/theme_custom/
    // {chatId}.jpg with its version in prefs "tiku_theme_custom", and the
    // decoded bitmap is published to CustomThemeStore for the render
    // side. A strictly newer themeAssetAt in a threads snapshot means
    // the partner replaced the photo -> re-fetch; an equal version is
    // served from the cache file with no network at all. (This download
    // is the one functional exception to the no-auto-download media
    // law: without it the chosen theme could never be shown. Nothing
    // here ever touches the gallery or leaves app-private storage.)

    private fun themeAssetRef(chatId: String): DocumentReference =
        db.collection(Schema.Chats.COLL).document(chatId)
            .collection("themeasset").document("main")

    /** Chunk writers mirroring MediaStore's message pipeline, but
     *  parented on an arbitrary doc (the theme asset). */
    private suspend fun uploadChunksTo(parent: DocumentReference, ciphertext: ByteArray): Int {
        val chunksRef = parent.collection("chunks")
        val count = (ciphertext.size + MediaStore.CHUNK_BYTES - 1) / MediaStore.CHUNK_BYTES
        var batch = db.batch()
        var inBatch = 0
        for (i in 0 until count) {
            val from = i * MediaStore.CHUNK_BYTES
            val slice = ciphertext.copyOfRange(from, minOf(from + MediaStore.CHUNK_BYTES, ciphertext.size))
            batch.set(chunksRef.document(i.toString()), mapOf("d" to Crypto.b64(slice)))
            if (++inBatch >= 400) { batch.commit().await(); batch = db.batch(); inBatch = 0 }
        }
        if (inBatch > 0) batch.commit().await()
        return count
    }

    private suspend fun downloadChunksFrom(parent: DocumentReference, chunkCount: Int): ByteArray {
        val snap = parent.collection("chunks")
            .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.ASCENDING)
            .get().await()
        val out = java.io.ByteArrayOutputStream()
        val byIndex = snap.documents.associate { (it.id.toIntOrNull() ?: -1) to (it.getString("d") ?: "") }
        for (i in 0 until chunkCount) {
            val d = byIndex[i] ?: continue
            out.write(Crypto.unb64(d))
        }
        return out.toByteArray()
    }

    // ---- on-device cache (decrypted jpeg + its version) ----
    private fun themeCacheDir(): File = File(appCtx.filesDir, "theme_custom").apply { mkdirs() }
    private fun themeCacheFile(chatId: String): File = File(themeCacheDir(), "$chatId.jpg")
    private fun themeCacheVersion(chatId: String): Long =
        appCtx.getSharedPreferences("tiku_theme_custom", Context.MODE_PRIVATE).getLong(chatId, 0L)

    private fun themeCachePut(chatId: String, jpeg: ByteArray, version: Long) {
        runCatching { themeCacheFile(chatId).writeBytes(jpeg) }
        appCtx.getSharedPreferences("tiku_theme_custom", Context.MODE_PRIVATE)
            .edit().putLong(chatId, version).apply()
    }

    private fun themeCacheDrop(chatId: String) {
        runCatching { themeCacheFile(chatId).delete() }
        appCtx.getSharedPreferences("tiku_theme_custom", Context.MODE_PRIVATE)
            .edit().remove(chatId).apply()
    }

    /** Chats with a theme-asset load (cache read or network fetch)
     *  currently in flight — the threads listener fires often and must
     *  never stack duplicate downloads of the same photo. */
    private val themeAssetBusy = mutableSetOf<String>()

    /**
     * Set THIS chat's theme to the custom [jpeg] photo (already
     * downscaled by the caller) with the palette [paletteId] ("" =
     * Default colours). Applies locally at once — cache file, version,
     * published bitmap, optimistic thread — then uploads the encrypted
     * asset and stamps the chat doc (theme + themeAssetAt in one
     * update, so the partner's listener always sees a consistent pair).
     * Called from the Customize picker on the real engine only; the
     * harness takes the PreviewStore path instead.
     */
    fun setCustomTheme(id: String, peerUid: String, paletteId: String, jpeg: ByteArray) {
        val value = ChatThemes.customValue(paletteId)
        val localVersion = System.currentTimeMillis()
        if (::appCtx.isInitialized) {
            themeCachePut(id, jpeg, localVersion)
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { CustomThemeStore.publish(id, it) }
        }
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(themeId = value, themeAssetAt = localVersion) else it
        }
        scope.launch {
            runCatching {
                val key = convKeyOrNull(id, peerUid)
                    ?: throw IllegalStateException("encryption key not ready — the other person may not have finished setting up")
                val (iv, ctB64) = Crypto.encrypt(key, jpeg)
                val ctBytes = Crypto.unb64(ctB64)
                val asset = themeAssetRef(id)
                asset.set(mapOf(
                    "iv" to iv, "mime" to "image/jpeg",
                    "mediaBytes" to jpeg.size.toLong(), "mediaChunks" to 0,
                    "ts" to FieldValue.serverTimestamp(),
                )).await()
                val chunks = uploadChunksTo(asset, ctBytes)
                asset.update("mediaChunks", chunks).await()
                val chatRef = db.collection(Schema.Chats.COLL).document(id)
                chatRef.update(mapOf(
                    "theme" to value,
                    "themeAssetAt" to FieldValue.serverTimestamp(),
                )).await()
                // Pin the cache version to the EXACT server stamp so
                // this phone never re-downloads its own upload when
                // the listener echo lands. If the read-back fails, the
                // local version stands and the worst case is one
                // redundant (identical) fetch.
                val serverAt = chatRef.get().await().getTimestamp("themeAssetAt")?.toDate()?.time
                if (serverAt != null && ::appCtx.isInitialized) {
                    appCtx.getSharedPreferences("tiku_theme_custom", Context.MODE_PRIVATE)
                        .edit().putLong(id, serverAt).apply()
                }
                Log.d("TikuChat", "custom theme set in chat $id ('$value', $chunks chunks)")
            }.onFailure {
                Log.e("TikuChat", "setCustomTheme failed for chat $id", it)
                AppState.toast("Couldn't set the custom theme — ${sendFailureReason(it)}")
            }
        }
    }

    /** Threads-listener hook (called on every snapshot): for each
     *  custom-themed chat, publish the cached photo if we hold it, and
     *  fetch the encrypted asset when the snapshot's version is newer
     *  than the cache's. No-ops for everything else. */
    private fun ensureCustomThemeAssets(threads: List<ChatThread>) {
        if (!::appCtx.isInitialized) return
        for (t in threads) {
            if (!ChatThemes.isCustom(t.themeId)) continue
            val stale = t.themeAssetAt > themeCacheVersion(t.id)
            val unpublished = CustomThemeStore.bitmap(t.id) == null && themeCacheFile(t.id).exists()
            if (!stale && !unpublished) continue
            val go = synchronized(themeAssetBusy) { themeAssetBusy.add(t.id) }
            if (!go) continue
            if (stale) {
                fetchCustomThemeAsset(t)
            } else {
                scope.launch {
                    runCatching {
                        val bytes = themeCacheFile(t.id).readBytes()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?.let { CustomThemeStore.publish(t.id, it) }
                    }
                    synchronized(themeAssetBusy) { themeAssetBusy.remove(t.id) }
                }
            }
        }
    }

    private fun fetchCustomThemeAsset(thread: ChatThread) {
        val chatId = thread.id
        scope.launch {
            try {
                runCatching {
                    val doc = themeAssetRef(chatId).get().await()
                    val chunks = (doc.getLong("mediaChunks") ?: 0L).toInt()
                    val iv = doc.getString("iv") ?: ""
                    if (chunks <= 0 || iv.isEmpty()) return@runCatching
                    val key = convKeyOrNull(chatId, thread.person.uid) ?: return@runCatching
                    val ctBytes = downloadChunksFrom(themeAssetRef(chatId), chunks)
                    val plain = Crypto.decrypt(key, iv, Crypto.b64(ctBytes))
                    themeCachePut(chatId, plain, thread.themeAssetAt)
                    BitmapFactory.decodeByteArray(plain, 0, plain.size)
                        ?.let { CustomThemeStore.publish(chatId, it) }
                    Log.d("TikuChat", "custom theme asset fetched for chat $chatId (v${thread.themeAssetAt})")
                }.onFailure { Log.e("TikuChat", "custom theme fetch failed for chat $chatId", it) }
            } finally {
                synchronized(themeAssetBusy) { themeAssetBusy.remove(chatId) }
            }
        }
    }

    /** Retire a chat's custom-theme asset (switching to a built-in
     *  theme or Default): chunk docs + asset doc + the chat doc's
     *  version stamp + the local cache all go. Best-effort — a failure
     *  leaves an orphaned asset that nothing references. */
    private suspend fun deleteThemeAsset(chatId: String) {
        if (::appCtx.isInitialized) themeCacheDrop(chatId)
        runCatching {
            val asset = themeAssetRef(chatId)
            val chunks = asset.collection("chunks").get().await()
            val batch = db.batch()
            chunks.documents.forEach { batch.delete(it.reference) }
            batch.delete(asset)
            batch.commit().await()
            db.collection(Schema.Chats.COLL).document(chatId)
                .update("themeAssetAt", FieldValue.delete()).await()
            Log.d("TikuChat", "custom theme asset deleted for chat $chatId")
        }.onFailure { Log.e("TikuChat", "deleteThemeAsset failed for chat $chatId", it) }
    }

    /** MY READ RECEIPTS IN THIS CHAT: receiptsOff on the chat doc carries
     *  MY uid while my receipts are off here. markIncomingSeen honours it;
     *  the chat screen also hides my "Seen" caption while it is set. */
    override fun setReceipts(id: String, on: Boolean) {
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(receiptsOffMine = !on) else it
        }
        scope.launch {
            runCatching {
                val ref = db.collection(Schema.Chats.COLL).document(id)
                if (on) ref.update("receiptsOff", FieldValue.arrayRemove(uid)).await()
                else ref.update("receiptsOff", FieldValue.arrayUnion(uid)).await()
                Log.d("TikuChat", "receipts ${if (on) "on" else "off"} in chat $id")
            }.onFailure { Log.e("TikuChat", "setReceipts failed for chat $id", it) }
        }
    }

    /** ROUND 22 — typing presence stamps (see the chat-doc schema
     *  note): my entry in chats/{id}.typing is the epoch ms of my last
     *  refresh, and the peer renders "typing…" only while it is
     *  younger than 5 s. This ledger is my outstanding stamp per
     *  thread (threadId -> stamp written): refreshes write at most
     *  once per 3 s, and a clear is written only when a stamp is
     *  actually outstanding — the composer's per-keystroke calls are
     *  therefore cheap. Every write is a single-leaf
     *  update("typing.$uid", …): no other chat-doc field (updatedAt
     *  included) is touched, and messages live in the subcollection,
     *  so typing traffic can never perturb previews, unread counts or
     *  the notification delta judge. All failures are silent —
     *  presence is best-effort by design. */
    private val typingStamps = mutableMapOf<String, Long>()

    override fun setTyping(id: String, on: Boolean) {
        if (on) {
            if (SettingsStore.state.value.hideTyping) return
            val now = System.currentTimeMillis()
            val last = typingStamps[id]
            if (last != null && now - last < 3_000L) return
            typingStamps[id] = now
            scope.launch {
                runCatching {
                    db.collection(Schema.Chats.COLL).document(id).update("typing.$uid", now).await()
                }
            }
        } else {
            if (typingStamps.remove(id) == null) return
            scope.launch { setTypingRaw(id, false) }
        }
    }

    /** The unconditional clear — the send path (touchChat) funnels
     *  here so a send always drops my stamp even if the throttle
     *  ledger lost track of it. Clearing DELETES the leaf: a missing
     *  entry reads as "not typing" on every client, old and new. */
    private suspend fun setTypingRaw(id: String, on: Boolean) {
        if (on) { setTyping(id, true); return }
        typingStamps.remove(id)
        runCatching {
            db.collection(Schema.Chats.COLL).document(id)
                .update("typing.$uid", FieldValue.delete()).await()
        }
    }

    // ================= ROUND 14: REQUESTS / HIDE / BLOCK / NOTES =================
    override fun acceptThread(id: String) {
        acceptedForCache[id] = System.currentTimeMillis()
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(isRequest = false) else it
        }
        scope.launch {
            runCatching {
                db.collection(Schema.Chats.COLL).document(id)
                    .update("acceptedFor.$uid", FieldValue.serverTimestamp()).await()
            }.onFailure { Log.e("TikuChat", "acceptThread failed for $id", it) }
        }
    }

    override fun hideThread(id: String) {
        hiddenForCache[id] = System.currentTimeMillis()
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(hiddenForMe = true) else it
        }
        scope.launch {
            runCatching {
                db.collection(Schema.Chats.COLL).document(id)
                    .update("hiddenFor.$uid", FieldValue.serverTimestamp()).await()
            }.onFailure { Log.e("TikuChat", "hideThread failed for $id", it) }
        }
    }

    override fun blockUser(uid2: String) {
        if (uid2.isEmpty() || uid2 == uid) return
        blockedCache = blockedCache + uid2
        AppState.blockedUids.value = blockedCache
        // our thread with them hides everywhere at once
        AppState.threads.value.firstOrNull { it.person.uid == uid2 }?.let { hideThread(it.id) }
        AppState.threads.value = AppState.threads.value.filter { it.person.uid != uid2 }
        scope.launch {
            runCatching {
                db.collection(Schema.Users.COLL).document(uid)
                    .update("blockedUids", FieldValue.arrayUnion(uid2)).await()
            }.onFailure { Log.e("TikuChat", "blockUser failed for $uid2", it) }
        }
    }

    override fun unblockUser(uid2: String) {
        if (uid2.isEmpty()) return
        blockedCache = blockedCache - uid2
        AppState.blockedUids.value = blockedCache
        AppState.blockedPeople.value = AppState.blockedPeople.value.filter { it.uid != uid2 }
        scope.launch {
            runCatching {
                db.collection(Schema.Users.COLL).document(uid)
                    .update("blockedUids", FieldValue.arrayRemove(uid2)).await()
            }.onFailure { Log.e("TikuChat", "unblockUser failed for $uid2", it) }
            // their thread was filtered out of the list while blocked — a
            // fresh threads snapshot brings it back.
            if (ChatSession.unlocked) start(appCtx)
        }
    }

    override fun setMyNote(text: String) {
        val t = text.trim().take(60)
        if (t.isEmpty()) { deleteMyNote(); return }
        AppState.notes.value = AppState.notes.value +
            (uid to NoteUi(uid, t, emptySet(), System.currentTimeMillis()))
        scope.launch {
            runCatching {
                db.collection(Schema.Users.COLL).document(uid).update(mapOf(
                    "noteText" to t,
                    "noteAt" to FieldValue.serverTimestamp(),
                    "noteHearts" to emptyList<String>(),
                )).await()
            }.onFailure { Log.e("TikuChat", "setMyNote failed", it) }
        }
    }

    override fun deleteMyNote() {
        AppState.notes.value = AppState.notes.value - uid
        scope.launch {
            runCatching {
                db.collection(Schema.Users.COLL).document(uid).update(mapOf(
                    "noteText" to FieldValue.delete(),
                    "noteAt" to FieldValue.delete(),
                    "noteHearts" to emptyList<String>(),
                )).await()
            }.onFailure { Log.e("TikuChat", "deleteMyNote failed", it) }
        }
    }

    override fun toggleNoteHeart(ownerUid: String) {
        if (ownerUid.isEmpty() || ownerUid == uid) return
        val cur = AppState.notes.value[ownerUid] ?: return
        val hearted = uid in cur.hearts
        AppState.notes.value = AppState.notes.value + (ownerUid to cur.copy(
            hearts = if (hearted) cur.hearts - uid else cur.hearts + uid,
        ))
        scope.launch {
            runCatching {
                db.collection(Schema.Users.COLL).document(ownerUid).update(
                    "noteHearts",
                    if (hearted) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid),
                ).await()
            }.onFailure { Log.e("TikuChat", "toggleNoteHeart failed for $ownerUid", it) }
        }
    }

    /** Note reply: a normal text send whose quote snapshot is the note
     *  ("Note: <text>") with the "__note__" replyToId sentinel. */
    override fun sendNoteReply(id: String, text: String, noteText: String) {
        val th = AppState.thread(id) ?: return
        val local = TikuMessage(
            id = newLocalId(), senderUid = uid, kind = MsgKind.TEXT, text = text,
            time = fmtTime(System.currentTimeMillis()), me = true,
            replyQuote = "Note: $noteText", replyToId = "__note__",
            tsMs = System.currentTimeMillis(), status = SendStatus.SENDING,
        )
        AppState.upsertMessage(id, local)
        AppState.requestTailScroll(id, local.id)
        scope.launch { deliverText(id, th.person.uid, local) }
    }

    /** Edit-profile Name field: displayName + search tokens on my user doc. */
    suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val n = name.trim()
        require(n.isNotEmpty()) { "Name can't be empty." }
        db.collection(Schema.Users.COLL).document(uid).update(mapOf(
            "displayName" to n,
            "nameTokens" to SearchNorm.tokens(myPerson.username, n),
        )).await()
        myPerson = myPerson.copy(name = n)
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

    /**
     * ROUND 8 (item A) — IG-form last seen: "last seen today at 7:41 PM" /
     * "yesterday at …" / "Oct 12 at …" (+"yyyy" across years). Buckets are
     * computed on DEVICE-LOCAL epoch days (the stored value is an absolute
     * epoch from the server timestamp; Calendar/SimpleDateFormat render it
     * in the device's own timezone). The old one-liner reused fmtTime, which
     * printed a bare "h:mm" (no AM/PM) for today and a bare date with NO
     * time for anything older — the "count sahi nhi hota" the owner meant.
     */
    fun fmtLastSeen(tsMs: Long): String {
        if (tsMs <= 0) return ""
        fun epochDay(ms: Long): Long {
            val c = Calendar.getInstance().apply { timeInMillis = ms }
            val local = ms + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)
            return Math.floorDiv(local, 86_400_000L)
        }
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(tsMs))
        val dayDiff = epochDay(System.currentTimeMillis()) - epochDay(tsMs)
        val sameYear = Calendar.getInstance().apply { timeInMillis = tsMs }.get(Calendar.YEAR) ==
            Calendar.getInstance().get(Calendar.YEAR)
        return when {
            dayDiff == 0L -> "last seen today at $time"
            dayDiff == 1L -> "last seen yesterday at $time"
            sameYear -> "last seen ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(tsMs))} at $time"
            else -> "last seen ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(tsMs))} at $time"
        }
    }

    /**
     * ROUND 8 (item A) — the peer's presence line, freshness-aware: "online"
     * is honoured only while the peer's heartbeat stamp is fresh (≤3 min) or
     * unmeasurable (they share no lastSeen — privacy toggle off; then the
     * online flag is taken at face value). A STALE online flag (app crashed
     * / killed before it could write offline) falls through to the exact
     * last-seen instead of showing "online" forever.
     */
    fun presenceText(p: Person): String = when {
        p.online && (p.lastSeenMs == 0L ||
            System.currentTimeMillis() - p.lastSeenMs <= 180_000L) -> "online"
        p.lastSeenMs > 0L -> fmtLastSeen(p.lastSeenMs)
        else -> "offline"
    }

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
        // ROUND 29: sign-out ends the alert session authoritatively —
        // the persisted unlocked flag clears and the service stops
        // (Home's logout also calls NotifyService.stop; both are
        // idempotent).
        NotifyGate.sessionEnded(ctx)
        stop()
        auth.signOut()
        myPrivBytes = null
        convKeyCache.clear(); profileCache.clear(); publicKeyCache.clear()
        AppState.threads.value = emptyList()
        // ROUND 14: no note/block state may leak into the next session.
        AppState.notes.value = emptyMap()
        AppState.blockedUids.value = emptySet()
        AppState.blockedPeople.value = emptyList()
        blockedCache = emptySet()
        acceptedForCache.clear(); hiddenForCache.clear()
        ctx.getSharedPreferences("tiku_prefs", Context.MODE_PRIVATE).edit().putBoolean("onboarded", false).apply()
        AppState.go(Screen.Welcome)
    }
}
