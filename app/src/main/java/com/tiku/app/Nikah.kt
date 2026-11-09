package com.tiku.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * ROUND 60 — the live two-person Nikah ceremony (Nikah.kt = state + store;
 * NikahUi.kt = every screen). One ceremony lives in ONE doc:
 *
 *   chats/{chatId}/nikah/session        (members-only — see firestore.rules)
 *
 * PLAINTEXT on the doc (metadata only): status (active|done|cancelled),
 * step, creatorUid, groomUid, brideUid, ready / khutbah / dua (uid -> bool
 * maps), presence (uid -> heartbeat epoch ms), mahrPhase
 * (picking|proposed|accepted), groomQabool / brideQabool counters,
 * witGroomDone / witBrideDone / sigGroomDone / sigBrideDone booleans,
 * inviteMsgId, createdAt / completedMs timestamps.
 *
 * ENCRYPTED on the doc (personal content — AES-256-GCM ct/iv pairs under
 * the conversation key, through the engine's ONE derivation
 * [FirebaseEngine.nikahKey] -> convKeyOrNull -> Crypto.conversationKey,
 * opened with Crypto.encryptText / Crypto.decryptText /
 * Crypto.encrypt / Crypto.decrypt — no second crypto path exists):
 * mahrCt/mahrIv (the mahr text — the groom's live pick streams here),
 * witGroomCt/witGroomIv + witBrideCt/witBrideIv (each side's witness
 * name), sigGroomCt/sigGroomIv + sigBrideCt/sigBrideIv (each side's
 * signature PNG bytes). Names are NEVER stored: the UI resolves them
 * from the chat's own display names + the role uids.
 *
 * Both phones render from this doc's snapshot listener — it is the
 * single source of truth. Every step transition is written by the client
 * whose action completes the step's condition, inside a Firestore
 * transaction, so two phones racing the same transition converge.
 */
object NikahSteps {
    const val LOBBY = "lobby"
    const val KHUTBAH = "khutbah"
    const val MAHR = "mahr"
    const val QABOOL_GROOM = "qabool_groom"
    const val QABOOL_BRIDE = "qabool_bride"
    const val WITNESSES = "witnesses"
    const val SIGNATURES = "signatures"
    const val DUA = "dua"
    const val NAMA = "nama"

    val ORDER = listOf(
        LOBBY, KHUTBAH, MAHR, QABOOL_GROOM, QABOOL_BRIDE,
        WITNESSES, SIGNATURES, DUA, NAMA,
    )
    val LABELS = mapOf(
        LOBBY to "Welcome", KHUTBAH to "Khutbah", MAHR to "Mahr",
        QABOOL_GROOM to "Qabool · Groom", QABOOL_BRIDE to "Qabool · Bride",
        WITNESSES to "Witnesses", SIGNATURES to "Signatures",
        DUA to "Dua", NAMA to "Nikah Nama",
    )

    /** The prototype's mahr options (the custom promise is separate). */
    val MAHR_OPTIONS = listOf("₹11,000", "₹21,000", "₹51,000", "A gold ring", "A beautiful Qur'an")
}

/** Presence: a heartbeat every [HEARTBEAT_MS] while the ceremony screen
 *  is open; a partner counts as present while their stamp is younger
 *  than [STALE_MS]. The gap (4 missed beats) absorbs jitter + the write
 *  round-trip without ever letting a departed partner look present for
 *  more than ~20 s. */
const val NIKAH_HEARTBEAT_MS = 5_000L
const val NIKAH_STALE_MS = 20_000L

/** Decoded, UI-ready ceremony state (decryption already applied). */
data class NikahUiState(
    val exists: Boolean = false,
    val status: String = "active",
    val step: String = NikahSteps.LOBBY,
    val creatorUid: String = "",
    val groomUid: String = "",
    val brideUid: String = "",
    val groomName: String = "",
    val brideName: String = "",
    val ready: Map<String, Boolean> = emptyMap(),
    val khutbah: Map<String, Boolean> = emptyMap(),
    val dua: Map<String, Boolean> = emptyMap(),
    val presence: Map<String, Long> = emptyMap(),
    val mahrPhase: String = "picking",
    val mahrText: String = "",
    val groomQabool: Int = 0,
    val brideQabool: Int = 0,
    /** uid of the side -> that side's witness name ("" = not submitted). */
    val witnessNameBySide: Map<String, String> = emptyMap(),
    /** Base64 PNG of each side's signature, keyed by that side's uid. */
    val sigPngB64BySide: Map<String, String> = emptyMap(),
    val sigDoneBySide: Map<String, Boolean> = emptyMap(),
    val inviteMsgId: String = "",
    val completedMs: Long = 0L,
) {
    val active: Boolean get() = exists && status == "active"
    fun present(uid: String, nowMs: Long): Boolean =
        uid.isNotEmpty() && (presence[uid] ?: 0L) > 0L &&
            nowMs - (presence[uid] ?: 0L) < NIKAH_STALE_MS

    fun bothPresent(nowMs: Long): Boolean = present(groomUid, nowMs) && present(brideUid, nowMs)
    fun nameOf(uid: String): String = when (uid) {
        groomUid -> groomName
        brideUid -> brideName
        else -> ""
    }

    /** The certificate payload, assembled from decrypted live state —
     *  the exact shape the keepsake card stores (see [NikahKeepsake]). */
    fun keepsake(): NikahKeepsake = NikahKeepsake(
        groomName = groomName, brideName = brideName,
        dateMs = if (completedMs > 0L) completedMs else System.currentTimeMillis(),
        mahr = mahrText,
        witnessGroom = witnessNameBySide[groomUid] ?: "",
        witnessBride = witnessNameBySide[brideUid] ?: "",
        sigGroomPngB64 = sigPngB64BySide[groomUid] ?: "",
        sigBridePngB64 = sigPngB64BySide[brideUid] ?: "",
    )
}

object NikahStore {
    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + TikuEngineHandler)
    private val flows = mutableMapOf<String, MutableStateFlow<NikahUiState?>>()
    private val regs = mutableMapOf<String, ListenerRegistration>()
    private val peers = mutableMapOf<String, String>()
    private val names = mutableMapOf<String, Pair<String, String>>()
    private var presenceJob: Job? = null
    private var presenceChatId: String? = null

    private fun sessionRef(chatId: String) =
        db.collection(Schema.Chats.COLL).document(chatId)
            .collection("nikah").document("session")

    fun flow(chatId: String): StateFlow<NikahUiState?> =
        flows.getOrPut(chatId) { MutableStateFlow(null) }

    fun current(chatId: String): NikahUiState? = flows[chatId]?.value

    /** Attach the session listener for [chatId] (idempotent). Called
     *  when the chat opens — it powers the invite card's live state and
     *  the "Nikah in progress" rejoin banner as well as the ceremony. */
    fun watch(chatId: String, peerUid: String, myName: String, peerName: String) {
        if (Config.PREVIEW_UI_ONLY) return
        peers[chatId] = peerUid
        names[chatId] = myName to peerName
        if (regs.containsKey(chatId)) return
        val reg = runCatching {
            sessionRef(chatId).addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.w("TikuNikah", "session listener failed chat=$chatId", e)
                    return@addSnapshotListener
                }
                scope.launch { decode(chatId, snap) }
            }
        }.getOrNull() ?: return
        regs[chatId] = reg
    }

    fun unwatch(chatId: String) {
        regs.remove(chatId)?.remove()
        if (presenceChatId == chatId) stopPresence()
    }

    // ---------------- decode ----------------

    private fun boolMap(raw: Any?): Map<String, Boolean> =
        (raw as? Map<*, *>)?.entries?.associate {
            it.key.toString() to (it.value == true)
        } ?: emptyMap()

    private fun longMap(raw: Any?): Map<String, Long> =
        (raw as? Map<*, *>)?.entries?.associate {
            it.key.toString() to ((it.value as? Number)?.toLong() ?: 0L)
        } ?: emptyMap()

    private suspend fun decode(chatId: String, snap: DocumentSnapshot?) {
        val out = flows.getOrPut(chatId) { MutableStateFlow(null) }
        if (snap == null || !snap.exists()) {
            out.value = NikahUiState(exists = false)
            return
        }
        val peerUid = peers[chatId] ?: ""
        val myUid = FirebaseEngine.myUid()
        val (myName, peerName) = names[chatId] ?: ("" to "")
        val groomUid = snap.getString("groomUid") ?: ""
        val brideUid = snap.getString("brideUid") ?: ""
        fun nameFor(uid: String) = if (uid == myUid) myName else peerName
        val key = if (peerUid.isNotEmpty()) FirebaseEngine.nikahKey(chatId, peerUid) else null
        fun decText(ctF: String, ivF: String): String {
            val ct = snap.getString(ctF)?.takeIf { it.isNotEmpty() } ?: return ""
            if (key == null) return ""
            return runCatching {
                Crypto.decryptText(key, snap.getString(ivF) ?: "", ct)
            }.getOrDefault("")
        }
        fun decSigB64(ctF: String, ivF: String): String {
            val ct = snap.getString(ctF)?.takeIf { it.isNotEmpty() } ?: return ""
            if (key == null) return ""
            return runCatching {
                Crypto.b64(Crypto.decrypt(key, snap.getString(ivF) ?: "", ct))
            }.getOrDefault("")
        }
        val witGroom = decText("witGroomCt", "witGroomIv")
        val witBride = decText("witBrideCt", "witBrideIv")
        out.value = NikahUiState(
            exists = true,
            status = snap.getString("status") ?: "active",
            step = snap.getString("step") ?: NikahSteps.LOBBY,
            creatorUid = snap.getString("creatorUid") ?: "",
            groomUid = groomUid, brideUid = brideUid,
            groomName = nameFor(groomUid), brideName = nameFor(brideUid),
            ready = boolMap(snap.get("ready")),
            khutbah = boolMap(snap.get("khutbah")),
            dua = boolMap(snap.get("dua")),
            presence = longMap(snap.get("presence")),
            mahrPhase = snap.getString("mahrPhase") ?: "picking",
            mahrText = decText("mahrCt", "mahrIv"),
            groomQabool = (snap.getLong("groomQabool") ?: 0L).toInt(),
            brideQabool = (snap.getLong("brideQabool") ?: 0L).toInt(),
            witnessNameBySide = mapOf(
                groomUid to witGroom, brideUid to witBride,
            ).filterKeys { it.isNotEmpty() },
            sigPngB64BySide = mapOf(
                groomUid to decSigB64("sigGroomCt", "sigGroomIv"),
                brideUid to decSigB64("sigBrideCt", "sigBrideIv"),
            ).filterKeys { it.isNotEmpty() },
            sigDoneBySide = mapOf(
                groomUid to (snap.getBoolean("sigGroomDone") == true),
                brideUid to (snap.getBoolean("sigBrideDone") == true),
            ).filterKeys { it.isNotEmpty() },
            inviteMsgId = snap.getString("inviteMsgId") ?: "",
            completedMs = snap.getTimestamp("completedAt")?.toDate()?.time
                ?: (snap.getLong("completedMs") ?: 0L),
        )
    }

    // ---------------- presence ----------------

    /** Heartbeat while MY ceremony screen is open (called by the
     *  ceremony overlay's DisposableEffect). Writes presence.<me> every
     *  [NIKAH_HEARTBEAT_MS]; stopping zeroes my stamp so the partner's
     *  gate closes at once instead of waiting out the staleness window. */
    fun startPresence(chatId: String) {
        if (Config.PREVIEW_UI_ONLY) return
        if (presenceChatId == chatId && presenceJob?.isActive == true) return
        stopPresence()
        presenceChatId = chatId
        val me = FirebaseEngine.myUid()
        if (me.isEmpty()) return
        presenceJob = scope.launch {
            runCatching {
                sessionRef(chatId).update("presence.$me", System.currentTimeMillis()).await()
            }
            while (true) {
                delay(NIKAH_HEARTBEAT_MS)
                runCatching {
                    sessionRef(chatId)
                        .update("presence.$me", System.currentTimeMillis()).await()
                }
            }
        }
    }

    fun stopPresence() {
        presenceJob?.cancel()
        presenceJob = null
        val chatId = presenceChatId ?: return
        presenceChatId = null
        val me = FirebaseEngine.myUid()
        if (me.isEmpty() || Config.PREVIEW_UI_ONLY) return
        scope.launch {
            runCatching { sessionRef(chatId).update("presence.$me", 0L).await() }
        }
    }

    // ---------------- write helpers ----------------

    private fun merge(chatId: String, fields: Map<String, Any?>) {
        scope.launch {
            runCatching { sessionRef(chatId).set(fields, SetOptions.merge()).await() }
                .onFailure {
                    Log.e("TikuNikah", "session write failed chat=$chatId", it)
                    AppState.toast("Couldn't save — check your connection")
                }
        }
    }

    /** A transactional transition: [body] reads the current doc and
     *  writes through the transaction; returning a value lets the caller
     *  learn whether IT landed the transition (completion claim).
     *  ROUND 62 (Bug A): the whole transaction is retried up to 3
     *  attempts before the failure toast. Both phones' presence
     *  heartbeats write THIS SAME session doc every
     *  [NIKAH_HEARTBEAT_MS], so a transaction that reads the doc is
     *  routinely aborted by a heartbeat landing mid-flight (Firestore
     *  ABORTED) — the SDK's internal retries alone were not always
     *  enough, and the owner's "I'm Ready" tap surfaced exactly that
     *  as "Couldn't save". Retrying the whole transaction is safe:
     *  every body re-reads the doc and no-ops when its step/state
     *  preconditions no longer hold. A re-tap also always re-attempts
     *  (no latched failure state anywhere in the lobby). */
    private suspend fun <T> tx(chatId: String, body: (Transaction) -> T): T? {
        var lastErr: Throwable? = null
        repeat(3) { attempt ->
            val r = runCatching { db.runTransaction { txn -> body(txn) }.await() }
            if (r.isSuccess) return r.getOrNull()
            lastErr = r.exceptionOrNull()
            if (attempt < 2) delay(600L * (attempt + 1))
        }
        Log.e("TikuNikah", "session transaction failed chat=$chatId", lastErr)
        AppState.toast("Couldn't save — check your connection")
        return null
    }

    private fun Transaction.boolMapOf(snap: DocumentSnapshot, field: String): MutableMap<String, Boolean> =
        (snap.get(field) as? Map<*, *>)?.entries?.associate {
            it.key.toString() to (it.value == true)
        }?.toMutableMap() ?: mutableMapOf()

    // ---------------- actions ----------------

    /** Create the session (creator picks their own role; the partner is
     *  the other role) and land the invite card in the chat. */
    fun create(chatId: String, peerUid: String, myRole: String, myName: String) {
        if (Config.PREVIEW_UI_ONLY) return
        val me = FirebaseEngine.myUid()
        if (me.isEmpty()) return
        val groomUid = if (myRole == "groom") me else peerUid
        val brideUid = if (myRole == "groom") peerUid else me
        scope.launch {
            val ok = runCatching {
                sessionRef(chatId).set(
                    mapOf(
                        "status" to "active",
                        "step" to NikahSteps.LOBBY,
                        "creatorUid" to me,
                        "groomUid" to groomUid,
                        "brideUid" to brideUid,
                        "ready" to emptyMap<String, Boolean>(),
                        "khutbah" to emptyMap<String, Boolean>(),
                        "dua" to emptyMap<String, Boolean>(),
                        "presence" to mapOf(me to System.currentTimeMillis()),
                        "mahrPhase" to "picking",
                        "groomQabool" to 0L,
                        "brideQabool" to 0L,
                        "sigGroomDone" to false,
                        "sigBrideDone" to false,
                        "witGroomDone" to false,
                        "witBrideDone" to false,
                        "inviteMsgId" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
                true
            }.onFailure {
                Log.e("TikuNikah", "create failed chat=$chatId", it)
                AppState.toast("Couldn't start the Nikah — check your connection")
            }.getOrDefault(false)
            if (!ok) return@launch
            val fallback = "💍 $myName has invited you to a Nikah ceremony in Tiku. " +
                "Open the latest Tiku to join the ceremony."
            FirebaseEngine.sendNikahCard(chatId, "invite", fallback, null) { msgId ->
                merge(chatId, mapOf("inviteMsgId" to msgId))
            }
        }
    }

    /** Steps 1–2 pattern: mark MY flag; when both flags are in, the same
     *  transaction advances the step. */
    private fun flagStep(chatId: String, field: String, fromStep: String, toStep: String) {
        val me = FirebaseEngine.myUid()
        if (me.isEmpty()) return
        scope.launch {
            tx(chatId) { txn ->
                val ref = sessionRef(chatId)
                val snap = txn.get(ref)
                if (snap.getString("status") != "active") return@tx
                if (snap.getString("step") != fromStep) return@tx
                val flags = txn.boolMapOf(snap, field)
                flags[me] = true
                val groom = snap.getString("groomUid") ?: ""
                val bride = snap.getString("brideUid") ?: ""
                txn.set(ref, mapOf(field to flags), SetOptions.merge())
                if (flags[groom] == true && flags[bride] == true) {
                    txn.set(ref, mapOf("step" to toStep), SetOptions.merge())
                }
            }
        }
    }

    fun setReady(chatId: String) =
        flagStep(chatId, "ready", NikahSteps.LOBBY, NikahSteps.KHUTBAH)

    fun khutbahAmeen(chatId: String) =
        flagStep(chatId, "khutbah", NikahSteps.KHUTBAH, NikahSteps.MAHR)

    /** Groom's mahr pick, STREAMED: every selection (chip or custom text)
     *  is encrypted and written at once, so the bride watches it change
     *  live. Not a step transition — the lock happens at Accept. */
    fun streamMahr(chatId: String, peerUid: String, text: String) {
        scope.launch {
            val key = FirebaseEngine.nikahKey(chatId, peerUid) ?: run {
                AppState.toast("Encryption key not ready yet")
                return@launch
            }
            val (iv, ct) = Crypto.encryptText(key, text)
            merge(chatId, mapOf("mahrCt" to ct, "mahrIv" to iv))
        }
    }

    fun proposeMahr(chatId: String) = merge(chatId, mapOf("mahrPhase" to "proposed"))

    fun requestMahrChange(chatId: String) = merge(chatId, mapOf("mahrPhase" to "picking"))

    fun acceptMahr(chatId: String) {
        scope.launch {
            tx(chatId) { txn ->
                val ref = sessionRef(chatId)
                val snap = txn.get(ref)
                if (snap.getString("status") != "active") return@tx
                if (snap.getString("step") != NikahSteps.MAHR) return@tx
                if (snap.getString("mahrPhase") != "proposed") return@tx
                txn.set(
                    ref,
                    mapOf("mahrPhase" to "accepted", "step" to NikahSteps.QABOOL_GROOM),
                    SetOptions.merge(),
                )
            }
        }
    }

    /** One QABOOL tap: increments the side's shared counter in a
     *  transaction; the third tap passes the turn. */
    fun qaboolTap(chatId: String, groomSide: Boolean) {
        scope.launch {
            tx(chatId) { txn ->
                val ref = sessionRef(chatId)
                val snap = txn.get(ref)
                if (snap.getString("status") != "active") return@tx
                val step = snap.getString("step") ?: ""
                val wantStep = if (groomSide) NikahSteps.QABOOL_GROOM else NikahSteps.QABOOL_BRIDE
                if (step != wantStep) return@tx
                val field = if (groomSide) "groomQabool" else "brideQabool"
                val cur = (snap.getLong(field) ?: 0L).toInt()
                if (cur >= 3) return@tx
                val next = cur + 1
                val fields = mutableMapOf<String, Any?>(field to next.toLong())
                if (next >= 3) {
                    fields["step"] = if (groomSide) NikahSteps.QABOOL_BRIDE else NikahSteps.WITNESSES
                }
                txn.set(ref, fields, SetOptions.merge())
            }
        }
    }

    fun submitWitness(chatId: String, peerUid: String, groomSide: Boolean, name: String) {
        scope.launch {
            val key = FirebaseEngine.nikahKey(chatId, peerUid) ?: run {
                AppState.toast("Encryption key not ready yet")
                return@launch
            }
            val (iv, ct) = Crypto.encryptText(key, name.trim())
            tx(chatId) { txn ->
                val ref = sessionRef(chatId)
                val snap = txn.get(ref)
                if (snap.getString("status") != "active") return@tx
                if (snap.getString("step") != NikahSteps.WITNESSES) return@tx
                val fields = mutableMapOf<String, Any?>(
                    if (groomSide) "witGroomCt" to ct else "witBrideCt" to ct,
                    if (groomSide) "witGroomIv" to iv else "witBrideIv" to iv,
                    if (groomSide) "witGroomDone" to true else "witBrideDone" to true,
                )
                val groomDone = (if (groomSide) true else snap.getBoolean("witGroomDone") == true)
                val brideDone = (if (groomSide) snap.getBoolean("witBrideDone") == true else true)
                if (groomDone && brideDone) fields["step"] = NikahSteps.SIGNATURES
                txn.set(ref, fields, SetOptions.merge())
            }
        }
    }

    /** One side's signature: the pad bitmap (already downscaled by the
     *  pad — see NikahSignatureView.exportPng) is encrypted as ONE blob
     *  pair on the session doc. A signature at pad size is a few KB of
     *  PNG, far under Firestore's per-field limit, so no chunk pipeline
     *  is needed; it is ceremony data, never gallery media, and nothing
     *  here touches MediaStore or any auto-save path. */
    fun submitSignature(chatId: String, peerUid: String, groomSide: Boolean, png: ByteArray) {
        scope.launch {
            val key = FirebaseEngine.nikahKey(chatId, peerUid) ?: run {
                AppState.toast("Encryption key not ready yet")
                return@launch
            }
            val (iv, ct) = Crypto.encrypt(key, png)
            tx(chatId) { txn ->
                val ref = sessionRef(chatId)
                val snap = txn.get(ref)
                if (snap.getString("status") != "active") return@tx
                if (snap.getString("step") != NikahSteps.SIGNATURES) return@tx
                val fields = mutableMapOf<String, Any?>(
                    if (groomSide) "sigGroomCt" to ct else "sigBrideCt" to ct,
                    if (groomSide) "sigGroomIv" to iv else "sigBrideIv" to iv,
                    if (groomSide) "sigGroomDone" to true else "sigBrideDone" to true,
                )
                val groomDone = (if (groomSide) true else snap.getBoolean("sigGroomDone") == true)
                val brideDone = (if (groomSide) snap.getBoolean("sigBrideDone") == true else true)
                if (groomDone && brideDone) fields["step"] = NikahSteps.DUA
                txn.set(ref, fields, SetOptions.merge())
            }
        }
    }

    /** Dua Ameen: the second Ameen CLAIMS completion inside the same
     *  transaction (status -> done, step -> nama), so exactly one phone
     *  sends the keepsake card. */
    fun duaAmeen(chatId: String) {
        val me = FirebaseEngine.myUid()
        if (me.isEmpty()) return
        scope.launch {
            val claimed = tx(chatId) { txn ->
                val ref = sessionRef(chatId)
                val snap = txn.get(ref)
                if (snap.getString("status") != "active") return@tx false
                if (snap.getString("step") != NikahSteps.DUA) return@tx false
                val flags = txn.boolMapOf(snap, "dua")
                flags[me] = true
                val groom = snap.getString("groomUid") ?: ""
                val bride = snap.getString("brideUid") ?: ""
                txn.set(ref, mapOf("dua" to flags), SetOptions.merge())
                if (flags[groom] == true && flags[bride] == true) {
                    txn.set(
                        ref,
                        mapOf(
                            "status" to "done",
                            "step" to NikahSteps.NAMA,
                            "completedAt" to FieldValue.serverTimestamp(),
                            "completedMs" to System.currentTimeMillis(),
                        ),
                        SetOptions.merge(),
                    )
                    true
                } else false
            } ?: false
            if (!claimed) return@launch
            // I landed the completion: re-read + decode the final state,
            // then land the self-contained keepsake card in the chat and
            // reflect "done" onto the invite card.
            val snap = runCatching { sessionRef(chatId).get().await() }.getOrNull()
            decode(chatId, snap)
            val st = current(chatId) ?: return@launch
            val k = st.keepsake()
            val dateStr = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.US)
                .format(java.util.Date(k.dateMs))
            FirebaseEngine.sendNikahCard(
                chatId, "keepsake", "💍 Nikah complete — $dateStr", k,
            )
            FirebaseEngine.setNikahInviteState(chatId, st.inviteMsgId, "done")
        }
    }

    /** Either partner, any time: the ceremony ends where it stands and
     *  the invite card in the chat reflects the cancellation. */
    fun cancel(chatId: String) {
        val inviteMsgId = current(chatId)?.inviteMsgId ?: ""
        merge(chatId, mapOf("status" to "cancelled"))
        FirebaseEngine.setNikahInviteState(chatId, inviteMsgId, "cancelled")
    }
}

/** The JSON form of a keepsake (kept beside the store so the engine's
 *  writer and any future reader share one shape). */
fun NikahKeepsake.toJson(): JSONObject = JSONObject()
    .put("groom", groomName).put("bride", brideName)
    .put("dateMs", dateMs).put("mahr", mahr)
    .put("wGroom", witnessGroom).put("wBride", witnessBride)
    .put("sigGroom", sigGroomPngB64).put("sigBride", sigBridePngB64)

/**
 * The signature pad (prototype: a finger-drawn canvas). A plain Android
 * View — Compose Canvas cannot export a bitmap, and the exported PNG is
 * what gets encrypted into the session doc. Export is downscaled to at
 *  most 720 px wide (a 108 dp-tall pad exports ≈ 720×216): stroke-only
 * PNGs at that size run ≈ 3–12 KB, so the encrypted base64 field stays
 * ≈ 4–16 KB — nowhere near Firestore's 1 MB field limit.
 */
class NikahSignatureView(ctx: Context) : View(ctx) {
    private val strokes = mutableListOf<Path>()
    private var current: Path? = null
    private val ink = Paint().apply {
        color = 0xFFFFD9E4.toInt()
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    var onInkChanged: ((Boolean) -> Unit)? = null

    fun hasInk(): Boolean = strokes.isNotEmpty()

    fun clear() {
        strokes.clear()
        current = null
        invalidate()
        onInkChanged?.invoke(false)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                current = Path().apply { moveTo(e.x, e.y) }
                strokes.add(current!!)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> current?.lineTo(e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                onInkChanged?.invoke(hasInk())
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        strokes.forEach { c.drawPath(it, ink) }
    }

    /** Render the strokes to a transparent PNG, downscaled so the
     *  longest side is at most 720 px. Null when there is no ink or the
     *  view was never laid out. */
    fun exportPng(): ByteArray? {
        if (!hasInk() || width <= 0 || height <= 0) return null
        val scale = minOf(1f, 720f / width.toFloat())
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        strokes.forEach { c.drawPath(it, ink) }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
