package com.tiku.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ROUND 56 — CONSENT-BASED AI-CHAT SHARING.
 *
 * The Tika companion transcript stays LOCAL-ONLY by default (the
 * Round-54 law). The one sanctioned exception, ordered by the owner
 * and built around the target's explicit consent:
 *
 *  - The OWNER (the [OwnerState.isOwner] account) may send a user a
 *    request to see their Tika chat (`aiShareRequests/{id}`).
 *  - The request reaches the TARGET's app as a clear approval prompt
 *    (a banner on Messages + a dialog). NOTHING leaves the target's
 *    device unless the target taps "Approve" — there is no other
 *    code path that writes `aiShared`, and this file's own audit
 *    note in the round report lists every write.
 *  - Approval uploads ONE snapshot copy of the local transcript (up
 *    to that moment, text only, last 500 messages) to
 *    `aiShared/{toUid}` — readable only by the owner and that user
 *    (firestore.rules, prepared this round). Messages written after
 *    the approval are NOT shared; a newer snapshot needs a fresh
 *    request and a fresh Approve.
 *  - The target can revoke at any time (deleting `aiShared/{uid}`
 *    from the Tika sheet) — the owner cannot delete a shared copy.
 *  - Person-to-person chats are never involved: this module reads
 *    ONLY [TikaBotStore] and writes ONLY the two new collections.
 *
 * Preview mode: the whole module is inert (no Firestore in the
 * harness process) — every public entry no-ops or reads empty.
 */
object AiShare {

    const val REQUESTS_COLL = "aiShareRequests"
    const val SHARED_COLL = "aiShared"

    /** After a decline the owner may not re-ask the same user for
     *  7 days (anti-nag law). */
    const val DECLINE_COOLDOWN_MS = 7L * 24 * 3600 * 1000

    /** The snapshot's size cap (the LAST this many transcript
     *  entries ride the approval). */
    const val SNAPSHOT_CAP = 500

    data class ShareRequest(
        val id: String = "",
        val fromUid: String = "",
        val toUid: String = "",
        val toUsername: String = "",
        val status: String = "pending",
        val tsMs: Long = 0L,
        val decidedMs: Long = 0L,
    )

    data class SharedEntry(val who: String = "", val text: String = "", val tsMs: Long = 0L)

    data class SharedCopy(
        val uid: String = "",
        val username: String = "",
        val sharedMs: Long = 0L,
        val entries: List<SharedEntry> = emptyList(),
    )

    // ---- live state (StateFlows the UI collects) ----
    /** The pending request addressed to ME, if any (target side). */
    private val _incoming = MutableStateFlow<ShareRequest?>(null)
    val incoming: StateFlow<ShareRequest?> = _incoming

    /** Every request I have sent (owner side), newest first. */
    private val _outgoing = MutableStateFlow<List<ShareRequest>>(emptyList())
    val outgoing: StateFlow<List<ShareRequest>> = _outgoing

    /** Shared copies I may read: keyed by the SHARING user's uid.
     *  For the owner this is every copy addressed to him; for a
     *  target it is at most their own copy. */
    private val _shared = MutableStateFlow<Map<String, SharedCopy>>(emptyMap())
    val shared: StateFlow<Map<String, SharedCopy>> = _shared

    /** All profile users (owner's "AI chats" list source). */
    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people

    private var incomingReg: ListenerRegistration? = null
    private var outgoingReg: ListenerRegistration? = null
    private var sharedReg: ListenerRegistration? = null
    private var sharedOwnReg: ListenerRegistration? = null

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val myUid: String get() = runCatching { auth.currentUser?.uid ?: "" }.getOrDefault("")

    // ================= listeners (session-scoped) =================

    /**
     * Attach the sharing listeners for the CURRENT signed-in,
     * unlocked session. Called from FirebaseEngine.start() (which
     * only runs inside an unlocked session or the alert service's
     * gated bootstrap) and detached by [stop] from
     * FirebaseEngine.stop()/lock(). Idempotent: safe to re-call on
     * every engine start.
     *
     * Owner gets: outgoing requests + every shared copy + the
     * profile-user list. Everyone gets: their incoming pending
     * requests + their own shared copy (the Tika sheet's Sharing
     * state). Signed-out / preview = fully inert.
     */
    fun start() {
        if (Config.PREVIEW_UI_ONLY) return
        val me = myUid
        if (me.isEmpty()) return
        stop()
        incomingReg = runCatching {
            db.collection(REQUESTS_COLL)
                .whereEqualTo("toUid", me)
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    val reqs = snap?.documents
                        ?.map { docToRequest(it.id, it.data ?: emptyMap()) }
                        ?.sortedByDescending { it.tsMs }
                        .orEmpty()
                    // Anti-nag (client half): extras beyond the
                    // OLDEST pending request are ignored — the first
                    // ask stands, duplicates never surface.
                    _incoming.value = reqs.minByOrNull { it.tsMs }
                }
        }.getOrNull()
        sharedOwnReg = runCatching {
            db.collection(SHARED_COLL).document(me)
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    val copy = snap?.takeIf { it.exists() }?.let {
                        docToCopy(it.id, it.data ?: emptyMap())
                    }
                    _shared.value = if (copy != null) {
                        _shared.value + (me to copy)
                    } else {
                        _shared.value - me
                    }
                }
        }.getOrNull()
        if (OwnerState.isOwner) {
            outgoingReg = runCatching {
                db.collection(REQUESTS_COLL)
                    .whereEqualTo("fromUid", me)
                    .addSnapshotListener { snap, err ->
                        if (err != null) return@addSnapshotListener
                        _outgoing.value = snap?.documents
                            ?.map { docToRequest(it.id, it.data ?: emptyMap()) }
                            ?.sortedByDescending { it.tsMs }
                            .orEmpty()
                    }
            }.getOrNull()
            sharedReg = runCatching {
                db.collection(SHARED_COLL)
                    .addSnapshotListener { snap, err ->
                        if (err != null) return@addSnapshotListener
                        val mine = _shared.value[myUid]
                        val all = snap?.documents
                            ?.map { docToCopy(it.id, it.data ?: emptyMap()) }
                            ?.associateBy { it.uid }
                            .orEmpty()
                        // Keep my own copy's live entry (its listener
                        // above owns it) merged over the sweep.
                        _shared.value = if (mine != null && myUid !in all) {
                            all + (myUid to mine)
                        } else {
                            all
                        }
                    }
            }.getOrNull()
            refreshPeople()
        }
    }

    /** Detach everything and clear state (lock / sign-out). */
    fun stop() {
        incomingReg?.remove(); incomingReg = null
        outgoingReg?.remove(); outgoingReg = null
        sharedReg?.remove(); sharedReg = null
        sharedOwnReg?.remove(); sharedOwnReg = null
        _incoming.value = null
        _outgoing.value = emptyList()
        _shared.value = emptyMap()
        _people.value = emptyList()
    }

    // ================= owner actions =================

    /** Load every profile user for the owner's list (the same
     *  `users` collection the profile/search paths read). */
    fun refreshPeople() {
        if (Config.PREVIEW_UI_ONLY || !OwnerState.isOwner) return
        val me = myUid
        FirebaseEngine.scope.launch {
            runCatching {
                val snap = db.collection(Schema.Users.COLL).get().await()
                _people.value = snap.documents
                    .mapNotNull { d ->
                        val username = d.getString("username").orEmpty()
                        if (d.id == me || username.isBlank()) null
                        else Person(
                            uid = d.id,
                            name = d.getString("displayName").orEmpty().ifEmpty { username },
                            username = username,
                        )
                    }
                    .sortedBy { it.username }
            }
        }
    }

    /** The owner's relationship state with one user, for the list. */
    sealed interface OwnerRowState {
        data object NotAsked : OwnerRowState
        data object Waiting : OwnerRowState
        data class Declined(val decidedMs: Long) : OwnerRowState
        data object SharedRemoved : OwnerRowState
    }

    /** Derive one user's row state. A live shared copy trumps the
     *  request history (the UI shows "View" then); a request whose
     *  approval produced a copy that was later revoked reads as
     *  "Shared copy removed" (approved request, no copy). */
    fun ownerRowState(uid: String): OwnerRowState {
        val reqs = _outgoing.value.filter { it.toUid == uid }
        if (reqs.any { it.status == "pending" }) return OwnerRowState.Waiting
        val latest = reqs.maxByOrNull { maxOf(it.tsMs, it.decidedMs) }
            ?: return OwnerRowState.NotAsked
        return when (latest.status) {
            "declined" -> OwnerRowState.Declined(latest.decidedMs)
            "approved" -> OwnerRowState.SharedRemoved
            else -> OwnerRowState.NotAsked
        }
    }

    /** Non-null when the owner may send [uid] a fresh request right
     *  now; otherwise the reason string for the UI. Enforces the
     *  one-pending law and the 7-day post-decline cooldown. */
    fun requestBlockReason(uid: String): String? {
        val reqs = _outgoing.value.filter { it.toUid == uid }
        if (reqs.any { it.status == "pending" }) return "Request already sent — waiting"
        val declined = reqs.filter { it.status == "declined" }.maxByOrNull { it.decidedMs }
        if (declined != null) {
            val until = declined.decidedMs + DECLINE_COOLDOWN_MS
            if (System.currentTimeMillis() < until) {
                return "Request declined — you can ask again after ${fmtDate(until)}"
            }
        }
        return null
    }

    /** Send a sharing request (owner only; the rules enforce the
     *  same identity server-side once deployed). */
    fun requestAccess(person: Person) {
        if (Config.PREVIEW_UI_ONLY || !OwnerState.isOwner) return
        val me = myUid
        if (me.isEmpty() || person.uid.isEmpty() || person.uid == me) return
        if (requestBlockReason(person.uid) != null) return
        FirebaseEngine.scope.launch {
            runCatching {
                db.collection(REQUESTS_COLL).add(
                    mapOf(
                        "fromUid" to me,
                        "toUid" to person.uid,
                        "toUsername" to person.username,
                        "status" to "pending",
                        "tsMs" to System.currentTimeMillis(),
                        "decidedMs" to 0L,
                    ),
                ).await()
            }.onFailure {
                AppState.toast("Couldn't send the request — try again")
            }
        }
    }

    // ================= target actions =================

    /** Decline: the request closes forever (status + decidedMs are
     *  the only fields the target may write — the rules hold the
     *  same line). */
    fun decline(req: ShareRequest) {
        if (Config.PREVIEW_UI_ONLY) return
        if (req.toUid != myUid || req.status != "pending") return
        FirebaseEngine.scope.launch {
            runCatching {
                db.collection(REQUESTS_COLL).document(req.id).update(
                    mapOf(
                        "status" to "declined",
                        "decidedMs" to System.currentTimeMillis(),
                    ),
                ).await()
                if (_incoming.value?.id == req.id) _incoming.value = null
            }.onFailure {
                AppState.toast("Couldn't save your answer — try again")
            }
        }
    }

    /**
     * Approve — THE consent action, and the ONLY write of a shared
     * copy in the app. Runs solely from the target's explicit
     * "Approve" tap (the Messages dialog / banner). The snapshot is
     * built HERE, at tap time, from the target's own local
     * [TikaBotStore]: text entries only, photos as "[Photo]" (plus
     * their caption), last [SNAPSHOT_CAP] entries. The copy write
     * lands first; only then does the request flip to approved — a
     * failed copy write leaves the request pending and shares
     * nothing.
     */
    fun approve(req: ShareRequest) {
        if (Config.PREVIEW_UI_ONLY) return
        val me = myUid
        if (me.isEmpty() || req.toUid != me || req.status != "pending") return
        // The snapshot content: the target's REAL local transcript —
        // system lines excluded (they never persist, so the store
        // doesn't carry them; filtered again for certainty).
        TikaBotStore.ensureLoaded()
        val entries = TikaBotStore.snapshotForSharing().takeLast(SNAPSHOT_CAP)
        val now = System.currentTimeMillis()
        FirebaseEngine.scope.launch {
            val write = runCatching {
                db.collection(SHARED_COLL).document(me).set(
                    mapOf(
                        "username" to AppState.myUsername,
                        "sharedMs" to now,
                        "entries" to entries.map {
                            mapOf(
                                "who" to it.who,
                                "text" to it.text,
                                "tsMs" to it.tsMs,
                            )
                        },
                    ),
                ).await()
            }
            if (write.isFailure) {
                AppState.toast("Couldn't share — check your connection and try again")
                return@launch
            }
            runCatching {
                db.collection(REQUESTS_COLL).document(req.id).update(
                    mapOf(
                        "status" to "approved",
                        "decidedMs" to System.currentTimeMillis(),
                    ),
                ).await()
                if (_incoming.value?.id == req.id) _incoming.value = null
            }
            AppState.toast("Shared — thank you")
        }
    }

    /** Revoke: delete MY shared copy (the rules let only the target
     *  delete it — the owner cannot). */
    fun revokeMine() {
        if (Config.PREVIEW_UI_ONLY) return
        val me = myUid
        if (me.isEmpty()) return
        FirebaseEngine.scope.launch {
            runCatching {
                db.collection(SHARED_COLL).document(me).delete().await()
                _shared.value = _shared.value - me
            }.onFailure {
                AppState.toast("Couldn't remove the shared copy — try again")
            }
        }
    }

    // ================= doc mapping =================

    private fun docToRequest(id: String, d: Map<String, Any?>): ShareRequest = ShareRequest(
        id = id,
        fromUid = d["fromUid"] as? String ?: "",
        toUid = d["toUid"] as? String ?: "",
        toUsername = d["toUsername"] as? String ?: "",
        status = d["status"] as? String ?: "pending",
        tsMs = (d["tsMs"] as? Number)?.toLong() ?: 0L,
        decidedMs = (d["decidedMs"] as? Number)?.toLong() ?: 0L,
    )

    private fun docToCopy(uid: String, d: Map<String, Any?>): SharedCopy {
        @Suppress("UNCHECKED_CAST")
        val raw = d["entries"] as? List<Map<String, Any?>> ?: emptyList()
        return SharedCopy(
            uid = uid,
            username = d["username"] as? String ?: "",
            sharedMs = (d["sharedMs"] as? Number)?.toLong() ?: 0L,
            entries = raw.map {
                SharedEntry(
                    who = it["who"] as? String ?: "",
                    text = it["text"] as? String ?: "",
                    tsMs = (it["tsMs"] as? Number)?.toLong() ?: 0L,
                )
            },
        )
    }

    fun fmtDate(ms: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))

    fun fmtDateTime(ms: Long): String =
        SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(ms))
}

// ================= UI =================

/**
 * The TARGET's consent surface on Messages: while a request is
 * pending, a persistent banner row sits at the top of the list and
 * the full dialog shows once per request (per process — the banner
 * is the durable reminder). Both lead to the same explicit dialog;
 * the dialog's Approve button is the only consent tap in the app.
 */
@Composable
fun AiShareConsentHost() {
    val incoming by AiShare.incoming.collectAsState()
    val req = incoming ?: return
    var dialogFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(req.id) { dialogFor = req.id }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        CardRow({ dialogFor = req.id }, idx = 0) {
            TwoLine(
                "AI chat sharing request",
                "The app owner is asking to see your chat with Tika — tap to decide",
                Modifier.weight(1f),
            )
        }
    }
    if (dialogFor == req.id) {
        AiShareConsentDialog(
            req = req,
            onDismiss = { dialogFor = null },
        )
    }
}

@Composable
private fun AiShareConsentDialog(req: AiShare.ShareRequest, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    val decided by AiShare.incoming.collectAsState()
    // The decision landed (either button's write, or the listener
    // clearing the pending request) — close.
    LaunchedEffect(decided) { if (decided?.id != req.id) onDismiss() }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(CardC).padding(22.dp),
        ) {
            Text(
                "AI chat sharing request", color = Color.White,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,
            )
            Text(
                "The app owner (Abu Saalim) is asking to see your chat with Tika, " +
                    "the AI companion. If you approve, a copy of your Tika chat so " +
                    "far will be shared with them. Your chats with people are never shared.",
                color = Sub, fontSize = 13.5.sp,
                modifier = Modifier.padding(top = 10.dp), fontFamily = PoppinsFamily,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Decline", Modifier.weight(1f), accent = false) {
                    if (!busy) {
                        busy = true
                        AiShare.decline(req)
                    }
                }
                Pill("Approve", Modifier.weight(1f), accent = true) {
                    if (!busy) {
                        busy = true
                        AiShare.approve(req)
                    }
                }
            }
        }
    }
}

/** The target's Sharing state for the Tika sheet: whether a copy of
 *  their Tika chat currently sits in the shared store, and the
 *  revoke action. Renders nothing of substance when never shared. */
@Composable
fun TikaSharingRow() {
    val shared by AiShare.shared.collectAsState()
    val mine = shared[runCatching { FirebaseEngine.myUid() }.getOrDefault("")]
    var confirmRemove by remember { mutableStateOf(false) }
    if (mine != null) {
        CardRow({ confirmRemove = true }) {
            TwoLine(
                "Sharing",
                "Shared with the app owner on ${AiShare.fmtDate(mine.sharedMs)} — tap to remove the shared copy",
                Modifier.weight(1f),
            )
        }
    } else {
        CardRow({}) {
            TwoLine(
                "Sharing",
                "Not shared — your Tika chat is only on this phone",
                Modifier.weight(1f),
            )
        }
    }
    if (confirmRemove) {
        Dialog(onDismissRequest = { confirmRemove = false }) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(CardC).padding(22.dp),
            ) {
                Text(
                    "Remove the shared copy?", color = Color.White,
                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,
                )
                Text(
                    "The copy of your Tika chat that you shared with the app owner " +
                        "will be deleted. Your chat itself stays on this phone, untouched.",
                    color = Sub, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp), fontFamily = PoppinsFamily,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Keep it", Modifier.weight(1f), accent = false) {
                        confirmRemove = false
                    }
                    Pill("Remove", Modifier.weight(1f), accent = true) {
                        AiShare.revokeMine()
                        confirmRemove = false
                        AppState.closeSheet()
                    }
                }
            }
        }
    }
}

/** OWNER: Settings ▸ Owner ▸ "AI chats" — every profile user with
 *  their sharing state and the one action that state allows. */
@Composable
fun AiChatsPage(onBack: () -> Unit) {
    val people by AiShare.people.collectAsState()
    val outgoing by AiShare.outgoing.collectAsState()
    val shared by AiShare.shared.collectAsState()
    // The `outgoing` delegate read subscribes this composable to
    // request changes (the row states below derive from that flow
    // inside AiShare); `shared` is read directly per row.
    @Suppress("UNUSED_VARIABLE")
    val outgoingTick = outgoing.size
    var viewing by remember { mutableStateOf<AiShare.SharedCopy?>(null) }
    LaunchedEffect(Unit) { AiShare.refreshPeople() }
    val current = viewing
    if (current != null) {
        AiSharedTranscriptPage(copy = current, onBack = { viewing = null })
        return
    }
    SubPage("AI chats", onBack) {
        if (!OwnerState.isOwner) {
            item {
                Text(
                    "Available on the owner account only.",
                    color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily,
                )
            }
        } else if (people.isEmpty()) {
            item {
                Text(
                    "No other users yet.",
                    color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily,
                )
            }
        } else {
            items(people, key = { it.uid }) { p ->
                val copy = shared[p.uid]
                val state = AiShare.ownerRowState(p.uid)
                val block = AiShare.requestBlockReason(p.uid)
                when {
                    copy != null -> CardRow({ viewing = copy }) {
                        TwoLine(
                            "@${p.username}",
                            "Approved — shared ${AiShare.fmtDate(copy.sharedMs)} · tap to view",
                            Modifier.weight(1f),
                        )
                        Text(
                            "View", color = Rose, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                        )
                    }
                    state is AiShare.OwnerRowState.Waiting -> CardRow({}) {
                        TwoLine(
                            "@${p.username}",
                            "Requested — waiting for their answer",
                            Modifier.weight(1f),
                        )
                    }
                    state is AiShare.OwnerRowState.Declined -> CardRow({
                        if (block == null) AiShare.requestAccess(p)
                    }) {
                        TwoLine(
                            "@${p.username}",
                            block ?: "Declined — tap to ask again",
                            Modifier.weight(1f),
                        )
                        if (block == null) {
                            Text(
                                "Request access", color = Rose, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                            )
                        }
                    }
                    state is AiShare.OwnerRowState.SharedRemoved -> CardRow({
                        AiShare.requestAccess(p)
                    }) {
                        TwoLine(
                            "@${p.username}",
                            "Shared copy removed — tap to ask again",
                            Modifier.weight(1f),
                        )
                        Text(
                            "Request access", color = Rose, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                        )
                    }
                    else -> CardRow({ AiShare.requestAccess(p) }) {
                        TwoLine(
                            "@${p.username}",
                            "Not asked — tap to request access to their Tika chat",
                            Modifier.weight(1f),
                        )
                        Text(
                            "Request access", color = Rose, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily,
                        )
                    }
                }
            }
        }
    }
}

/** OWNER: one shared snapshot, read-only, dressed like a chat and
 *  clearly labelled whose it is and when it was shared. The
 *  entries' tsMs is the sharer's local send-order stamp (the local
 *  store keeps no per-message clock) — rows therefore carry no
 *  timestamps; order is the transcript's own. */
@Composable
private fun AiSharedTranscriptPage(copy: AiShare.SharedCopy, onBack: () -> Unit) {
    val live by AiShare.shared.collectAsState()
    val current = live[copy.uid]
    SubPage("AI chats", onBack, bottom = 40.dp) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(CardC).padding(14.dp),
            ) {
                Text(
                    "@${copy.username}", color = Rose, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = InterTight,
                )
                Text(
                    "Shared with you on ${AiShare.fmtDateTime(copy.sharedMs)}",
                    color = Sub, fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 4.dp), fontFamily = PoppinsFamily,
                )
                Text(
                    "A copy of their chat with Tika, the AI companion — " +
                        "shared by them, up to the moment they approved.",
                    color = Sub, fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp), fontFamily = PoppinsFamily,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        if (current == null) {
            item {
                Text(
                    "This shared copy has been removed.",
                    color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily,
                )
            }
        } else if (current.entries.isEmpty()) {
            item {
                Text(
                    "The shared chat is empty.",
                    color = Sub, fontSize = 14.sp, fontFamily = PoppinsFamily,
                )
            }
        } else {
            items(current.entries) { e ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement =
                        if (e.who == "user") Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        Modifier.widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (e.who == "user") {
                                    Brush.linearGradient(listOf(Rose, RoseDeep))
                                } else {
                                    SolidColor(CardC)
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        Text(
                            e.text, color = Color.White, fontSize = 15.sp,
                            fontFamily = rememberChatFontFamily(),
                        )
                    }
                }
            }
        }
    }
}
