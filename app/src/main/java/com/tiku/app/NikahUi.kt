package com.tiku.app

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ROUND 60 — Nikah screens (see Nikah.kt for state/crypto). The design
 * maps the approved prototype onto the app's existing dark machinery:
 * bg #121214, glass cards CardC (#1B1B1F) with the white-line border,
 * the Rose → RoseDeep gradient (Redesign.kt), gold accents, serif
 * display headings (SerifRegular — the app's Fraunces stand-in), the
 * app's UI face (PoppinsFamily), and Arabic in the default family
 * (the platform's Arabic-capable fallback rendering). No parallel
 * design system is imported.
 */
val NkGold = Color(0xFFE7C46A)
val NkGoldDeep = Color(0xFFB98A2F)
val NkRoseSoft = Color(0xFFFF93AD)
val NkPetal = Color(0xFFFF4F81)
val NkBg = Color(0xFF121214)
val NkLine = Color(0x17FFFFFF)
val NkPaperInk = Color(0xFF3B1519)
val NkCertGreen = Color(0xFF0A4636)

private val NkRoseBrush = Brush.linearGradient(listOf(Rose, RoseDeep))
private val NkGoldBrush = Brush.linearGradient(listOf(NkGold, NkGoldDeep))

fun nikahDateStr(ms: Long): String =
    SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(Date(ms))

/** Callbacks the ceremony content renders against — the overlay wires
 *  them to [NikahStore]; the screenshot harness passes the defaults
 *  (inert) with a fake state, exactly like the harness's other seams. */
data class NikahCallbacks(
    val onReady: () -> Unit = {},
    val onKhutbahAmeen: () -> Unit = {},
    val onPickMahr: (String) -> Unit = {},
    val onProposeMahr: () -> Unit = {},
    val onAcceptMahr: () -> Unit = {},
    val onRequestMahrChange: () -> Unit = {},
    val onQabool: () -> Unit = {},
    val onSubmitWitness: (String) -> Unit = {},
    val onSubmitSignature: (ByteArray) -> Unit = {},
    val onDuaAmeen: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onClose: () -> Unit = {},
)

// ---------------- shared pieces ----------------

@Composable
private fun NikahMascot(asset: String, small: Boolean = false) {
    val ctx = LocalContext.current
    val bmp = remember(asset) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            ctx.assets.open("nikah/$asset").use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }
    Box(
        Modifier.padding(3.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Rose, Color(0xFFFFB347)))),
    ) {
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(), null,
                Modifier.width(if (small) 200.dp else 280.dp)
                    .clip(RoundedCornerShape(27.dp)),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Box(Modifier.size(if (small) 180.dp else 240.dp)
                .clip(RoundedCornerShape(27.dp)).background(CardC))
        }
    }
}

@Composable
private fun NkCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, NkLine, RoundedCornerShape(22.dp))
            .padding(19.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun NkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(NkRoseBrush) else Modifier.background(Color.White.copy(0.10f)))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = PoppinsFamily)
    }
}

@Composable
private fun NkGhostButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(CircleShape)
            .border(1.dp, NkLine, CircleShape)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFFEDEDF2), fontSize = 14.sp, fontFamily = PoppinsFamily)
    }
}

@Composable
private fun NkHeading(text: String) {
    Text(text, color = Color.White, fontSize = 27.sp, fontFamily = SerifRegular,
        textAlign = TextAlign.Center)
}

@Composable
private fun NkSub(text: String) {
    Text(text, color = Sub, fontSize = 13.5.sp, fontFamily = PoppinsFamily,
        textAlign = TextAlign.Center, lineHeight = 20.sp)
}

@Composable
private fun NkWaiting(text: String) {
    NkCard {
        Text("⏳", fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = Color(0xFFEDEDF2), fontSize = 14.sp, fontFamily = PoppinsFamily,
            textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

@Composable
private fun NkTurnLock(name: String) {
    Text(
        "$name's turn — please wait",
        color = NkGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        fontFamily = PoppinsFamily, textAlign = TextAlign.Center,
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun NkField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), color = NkRoseSoft, fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = PoppinsFamily, color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Rose),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(0.05f))
                .border(1.dp, NkLine, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color(0xFF5C5B66), fontSize = 15.sp,
                            fontFamily = PoppinsFamily)
                    }
                    inner()
                }
            },
        )
    }
}

// ---------------- ceremony shell ----------------

/**
 * The live ceremony surface: watches the session doc, runs MY presence
 * heartbeat for exactly as long as it is open, and renders
 * [NikahCeremonyContent] from the decoded doc state.
 */
@Composable
fun NikahCeremonyOverlay(
    chatId: String, peerUid: String, myName: String, peerName: String,
    onClose: () -> Unit,
) {
    val state by NikahStore.flow(chatId).collectAsState()
    val myUid = remember {
        if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid()
    }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(chatId) {
        NikahStore.watch(chatId, peerUid, myName, peerName)
        while (true) {
            delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }
    DisposableEffect(chatId) {
        NikahStore.startPresence(chatId)
        onDispose { NikahStore.stopPresence() }
    }
    BackHandler { onClose() }
    val st = state
    if (st == null || !st.exists) {
        Box(
            Modifier.fillMaxSize().background(NkBg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NkSub(if (st == null) "Loading…" else "This ceremony is no longer available.")
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 40.dp)) { NkGhostButton("Close", onClose) }
            }
        }
        return
    }
    val iAmGroom = myUid == st.groomUid
    NikahCeremonyContent(
        state = st, myUid = myUid, nowMs = nowMs,
        cb = NikahCallbacks(
            onReady = { NikahStore.setReady(chatId) },
            onKhutbahAmeen = { NikahStore.khutbahAmeen(chatId) },
            onPickMahr = { NikahStore.streamMahr(chatId, peerUid, it) },
            onProposeMahr = { NikahStore.proposeMahr(chatId) },
            onAcceptMahr = { NikahStore.acceptMahr(chatId) },
            onRequestMahrChange = { NikahStore.requestMahrChange(chatId) },
            onQabool = {
                NikahStore.qaboolTap(chatId, NikahStore.current(chatId)?.step == NikahSteps.QABOOL_GROOM)
            },
            onSubmitWitness = { NikahStore.submitWitness(chatId, peerUid, iAmGroom, it) },
            onSubmitSignature = { NikahStore.submitSignature(chatId, peerUid, iAmGroom, it) },
            onDuaAmeen = { NikahStore.duaAmeen(chatId) },
            onCancel = { NikahStore.cancel(chatId) },
            onClose = onClose,
        ),
    )
}

/**
 * The ceremony itself, pure on (state, myUid, nowMs): both phones render
 * the same step from the session doc; the ACTIVE partner's controls are
 * live, the other's are visibly locked behind the turn line. The
 * presence gate is enforced here — while the partner is absent or
 * stale, no advance control is enabled (Cancel always is).
 */
@Composable
fun NikahCeremonyContent(
    state: NikahUiState, myUid: String, nowMs: Long, cb: NikahCallbacks,
) {
    val stepIdx = NikahSteps.ORDER.indexOf(state.step).coerceAtLeast(0)
    val iAmGroom = myUid == state.groomUid
    val partnerUid = if (iAmGroom) state.brideUid else state.groomUid
    val partnerName = state.nameOf(partnerUid)
    // I am present by construction (this screen is open and the overlay
    // heartbeats); the gate turns on the PARTNER's doc stamp.
    val partnerPresent = state.present(partnerUid, nowMs)
    val gateOpen = partnerPresent && state.status == "active"
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF1D1218), NkBg),
                center = Offset(300f, 0f), radius = 900f,
            ),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "✕", color = Color.White, fontSize = 18.sp,
                    modifier = Modifier.clickable { cb.onClose() }.padding(6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                    color = NkGold, fontSize = 19.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                if (state.status == "active") {
                    Box {
                        Text(
                            "⋮", color = Color.White, fontSize = 20.sp,
                            modifier = Modifier.clickable { menuOpen = true }.padding(6.dp),
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Cancel Nikah", fontFamily = PoppinsFamily) },
                                onClick = { menuOpen = false; cb.onCancel() },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(30.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                NikahSteps.ORDER.forEachIndexed { i, _ ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(CircleShape)
                            .then(
                                if (i <= stepIdx) Modifier.background(NkRoseBrush)
                                else Modifier.background(Color.White.copy(0.10f)),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "STEP ${stepIdx + 1} OF ${NikahSteps.ORDER.size} · " +
                    (NikahSteps.LABELS[state.step] ?: "").uppercase(),
                color = Sub, fontSize = 10.5.sp, fontFamily = PoppinsFamily, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(14.dp))

            if (state.status == "cancelled") {
                NkCard {
                    Text("💔", fontSize = 26.sp)
                    Spacer(Modifier.height(10.dp))
                    NkHeading("Nikah Cancelled")
                    Spacer(Modifier.height(6.dp))
                    NkSub("This ceremony was cancelled. The invitation in your chat shows the same.")
                }
                Spacer(Modifier.height(18.dp))
                NkGhostButton("Close") { cb.onClose() }
                return@Column
            }

            if (state.status == "active" && !partnerPresent) {
                NkWaiting("Waiting for $partnerName to join…")
                Spacer(Modifier.height(14.dp))
            }

            when (state.step) {
                NikahSteps.LOBBY -> NkStepLobby(state, myUid, gateOpen, cb)
                NikahSteps.KHUTBAH -> NkStepKhutbah(state, myUid, gateOpen, cb)
                NikahSteps.MAHR -> NkStepMahr(state, myUid, iAmGroom, gateOpen, cb)
                NikahSteps.QABOOL_GROOM -> NkStepQabool(state, groomTurn = true, myUid, gateOpen, cb)
                NikahSteps.QABOOL_BRIDE -> NkStepQabool(state, groomTurn = false, myUid, gateOpen, cb)
                NikahSteps.WITNESSES -> NkStepWitnesses(state, myUid, partnerUid, gateOpen, cb)
                NikahSteps.SIGNATURES -> NkStepSignatures(state, myUid, partnerUid, gateOpen, cb)
                NikahSteps.DUA -> NkStepDua(state, myUid, gateOpen, cb)
                else -> NkStepNama(state, cb)
            }
            Spacer(Modifier.height(10.dp))
            if (state.status == "active" && state.step == NikahSteps.LOBBY) {
                NkGhostButton("Cancel Nikah") { cb.onCancel() }
            }
        }
        if (state.step == NikahSteps.DUA && state.status == "active") {
            NikahConfetti()
        }
    }
}

// ---------------- steps ----------------

@Composable
private fun NkStepLobby(state: NikahUiState, myUid: String, gateOpen: Boolean, cb: NikahCallbacks) {
    Text(
        "NIKAH", color = Color.White, fontSize = 44.sp, fontFamily = SerifRegular,
        letterSpacing = 4.sp,
    )
    Spacer(Modifier.height(6.dp))
    NkSub("Two hearts, one beautiful promise — a joyful Islamic wedding ceremony, right here inside Tiku, wherever in the world you both are.")
    NikahMascot("mascot_hero.webp")
    Spacer(Modifier.height(8.dp))
    Text(
        "${state.groomName}  ✦  ${state.brideName}",
        color = Color.White, fontSize = 17.sp, fontFamily = SerifRegular, textAlign = TextAlign.Center,
    )
    Text(
        "${state.groomName} · the groom    ✦    ${state.brideName} · the bride",
        color = Sub, fontSize = 12.sp, fontFamily = PoppinsFamily, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(18.dp))
    val myReady = state.ready[myUid] == true
    val partnerReady = state.ready.entries.any { it.key != myUid && it.value }
    if (myReady) {
        NkWaiting(
            if (partnerReady) "Both ready — beginning…"
            else "You are ready ✓ — waiting for your partner to be ready…",
        )
    } else {
        NkButton("I'm Ready ✦", enabled = gateOpen) { cb.onReady() }
        if (partnerReady) {
            Spacer(Modifier.height(10.dp))
            NkSub("Your partner is ready ✓")
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "A symbolic celebration of your bond, made with love. Both of you take part live, from your own phones.",
        color = Sub, fontSize = 10.5.sp, fontFamily = PoppinsFamily,
        textAlign = TextAlign.Center, lineHeight = 15.sp,
    )
}

@Composable
private fun NkStepKhutbah(state: NikahUiState, myUid: String, gateOpen: Boolean, cb: NikahCallbacks) {
    NkHeading("Khutbah-tun-Nikah")
    Spacer(Modifier.height(6.dp))
    NkSub("Every Nikah begins with the blessed sermon — praise of Allah, and a reminder of the sacred promise you are about to make.")
    Spacer(Modifier.height(13.dp))
    NkCard {
        Text(
            "الْحَمْدُ لِلَّهِ نَسْتَعِينُهُ وَنَسْتَغْفِرُهُ، وَنَعُوذُ بِاللَّهِ مِنْ شُرُورِ أَنْفُسِنَا",
            color = Color(0xFFF6E3B4), fontSize = 22.sp, lineHeight = 40.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "Alhamdu lillahi nasta'eenuhu wa nastaghfiruh, wa na'oodhu billahi min shuroori anfusina…",
            color = NkRoseSoft, fontSize = 13.5.sp, fontFamily = SerifItalic,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "\"All praise belongs to Allah. We seek His help and His forgiveness, and we seek refuge in Him from the evils within ourselves.\"",
            color = Color(0xFFD8D7DE), fontSize = 13.sp, fontFamily = PoppinsFamily,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
    }
    Spacer(Modifier.height(13.dp))
    NkCard {
        Text(
            "🌙  Marriage in Islam is a mithaqan ghaleeza — a solemn, weighty covenant. A source of peace (sakina), love (mawaddah) and mercy (rahmah) between two souls.",
            color = Color(0xFFD8D7DE), fontSize = 13.sp, fontFamily = PoppinsFamily, lineHeight = 20.sp,
        )
    }
    Spacer(Modifier.height(20.dp))
    if (state.khutbah[myUid] == true) {
        NkWaiting("Ameen ✓ — waiting for your partner…")
    } else {
        NkButton("Ameen — Continue", enabled = gateOpen) { cb.onKhutbahAmeen() }
    }
}

@Composable
private fun NkStepMahr(
    state: NikahUiState, myUid: String, iAmGroom: Boolean,
    gateOpen: Boolean, cb: NikahCallbacks,
) {
    // Mahr local UI state — hoisted ABOVE every conditional branch so
    // hook order never changes when the phase flips mid-composition.
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var customTouched by remember { mutableStateOf(false) }
    LaunchedEffect(customText) {
        if (iAmGroom && customMode && customTouched) {
            delay(600)
            cb.onPickMahr(customText.trim())
        }
    }
    NkHeading("The Mahr")
    Spacer(Modifier.height(6.dp))
    NkSub("The Mahr is given by the groom, to the bride — it is her right and her honour, a symbol of his commitment and respect.")
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        NkPill("🤵 ${state.groomName} · gives")
        Text("  — ✦ →  ", color = NkGold, fontSize = 14.sp)
        NkPill("👰 ${state.brideName} · receives")
    }
    Spacer(Modifier.height(16.dp))
    if (iAmGroom) {
        if (state.mahrPhase == "proposed") {
            NkWaiting("You fixed the Mahr as ${state.mahrText} — waiting for ${state.brideName} to accept…")
            return
        }
        // chips (the groom's pick streams to the bride on every tap)
        val opts = NikahSteps.MAHR_OPTIONS
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                opts.take(3).forEach { o ->
                    NkChip(o, selected = !customMode && state.mahrText == o) {
                        customMode = false
                        cb.onPickMahr(o)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                opts.drop(3).forEach { o ->
                    NkChip(o, selected = !customMode && state.mahrText == o) {
                        customMode = false
                        cb.onPickMahr(o)
                    }
                }
                NkChip("A promise from the heart…", selected = customMode) { customMode = true }
            }
        }
        if (customMode) {
            Spacer(Modifier.height(14.dp))
            NkField("Write the promise", customText, "e.g. A trip to Makkah together, one day") {
                customText = it
                customTouched = true
            }
        }
        Spacer(Modifier.height(20.dp))
        NkButton(
            "Fix the Mahr ✦",
            enabled = gateOpen && state.mahrText.isNotBlank(),
        ) { cb.onProposeMahr() }
    } else {
        // Bride: watch the groom's pick stream in, then accept or ask again.
        NkTurnLock(state.groomName)
        Spacer(Modifier.height(12.dp))
        when (state.mahrPhase) {
            "proposed" -> {
                NkCard {
                    Text("The Mahr", color = NkRoseSoft, fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(state.mahrText, color = NkGold, fontSize = 20.sp,
                        fontFamily = SerifRegular, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    NkSub("${state.groomName} offers this Mahr to you. Accept it, or ask him to choose again.")
                }
                Spacer(Modifier.height(18.dp))
                NkButton("Accept the Mahr ✦", enabled = gateOpen) { cb.onAcceptMahr() }
                Spacer(Modifier.height(10.dp))
                NkGhostButton("Request change") { if (gateOpen) cb.onRequestMahrChange() }
            }
            else -> {
                NkWaiting(
                    if (state.mahrText.isBlank()) "${state.groomName} is choosing the Mahr…"
                    else "${state.groomName} is choosing the Mahr…\n\nSo far: ${state.mahrText}",
                )
            }
        }
    }
}

@Composable
private fun NkPill(text: String) {
    Box(
        Modifier.clip(CircleShape).background(Color.White.copy(0.05f))
            .border(1.dp, NkLine, CircleShape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(text, color = Color(0xFFEDEDF2), fontSize = 12.5.sp, fontFamily = PoppinsFamily)
    }
}

@Composable
private fun NkChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .then(
                if (selected) Modifier.background(NkRoseBrush)
                else Modifier.background(Color.White.copy(0.04f))
                    .border(1.dp, NkLine, CircleShape),
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.5.sp, fontFamily = PoppinsFamily,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NkStepQabool(
    state: NikahUiState, groomTurn: Boolean, myUid: String,
    gateOpen: Boolean, cb: NikahCallbacks,
) {
    val activeUid = if (groomTurn) state.groomUid else state.brideUid
    val activeName = if (groomTurn) state.groomName else state.brideName
    val count = if (groomTurn) state.groomQabool else state.brideQabool
    val mine = myUid == activeUid
    NkHeading("Ijab-o-Qabool")
    Spacer(Modifier.height(6.dp))
    NkSub("The heart of the Nikah — the offer and the acceptance, spoken in one gathering, before the witnesses.")
    Spacer(Modifier.height(14.dp))
    // the offer card (the prototype's script card)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0x29FF4F81), Color.White.copy(0.03f)),
                ),
            )
            .border(1.dp, Color(0x47FF93AD), RoundedCornerShape(22.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (groomTurn) "THE WALI OFFERS" else "THE BRIDE IS ASKED",
            color = NkGold, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = PoppinsFamily, letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            if (groomTurn) {
                "“I give ${state.brideName} to you, ${state.groomName}, in Nikah, " +
                    "with a Mahr of ${state.mahrText}. Do you accept?”"
            } else {
                "“${state.brideName}, ${state.groomName} has accepted you in Nikah " +
                    "with a Mahr of ${state.mahrText}. Do you accept him as your husband?”"
            },
            color = Color.White, fontSize = 18.sp, fontFamily = SerifRegular,
            textAlign = TextAlign.Center, lineHeight = 27.sp,
        )
    }
    Spacer(Modifier.height(18.dp))
    Text(
        "${activeName.uppercase()} · ${if (groomTurn) "THE GROOM" else "THE BRIDE"} SAYS",
        color = Sub, fontSize = 11.sp, fontFamily = PoppinsFamily, letterSpacing = 2.sp,
    )
    Spacer(Modifier.height(16.dp))
    // the big QABOOL button — live for the active partner only
    Box(
        Modifier.size(188.dp).alpha(if (mine) 1f else 0.45f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFFFB3C8), NkPetal, RoseDeep),
                    center = Offset(120f, 90f), radius = 340f,
                ),
            )
            .border(2.dp, NkGold.copy(alpha = 0.55f), CircleShape)
            .clickable(enabled = mine && gateOpen && count < 3) { cb.onQabool() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "QABOOL\nHAI", color = Color.White, fontSize = 24.sp,
            fontFamily = SerifRegular, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 30.sp, letterSpacing = 2.sp,
        )
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until 3).forEach { i ->
            val filled = count > i
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .then(
                        if (filled) Modifier.background(NkGoldBrush)
                        else Modifier.border(2.dp, Color.White.copy(0.16f), CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (filled) {
                    Text("✓", color = Color(0xFF3A2A04), fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    if (mine) {
        NkSub(
            when (count) {
                0 -> "Tap and say it — three times, as is the beautiful custom."
                else -> "$count of 3 — say it again, with your whole heart…"
            },
        )
    } else {
        NkTurnLock(activeName)
        Spacer(Modifier.height(8.dp))
        NkSub("$activeName is saying Qabool…")
    }
}

@Composable
private fun NkStepWitnesses(
    state: NikahUiState, myUid: String, partnerUid: String,
    gateOpen: Boolean, cb: NikahCallbacks,
) {
    // hoisted above the conditional branches (hook-order discipline)
    var witText by remember { mutableStateOf("") }
    NkHeading("The Witnesses")
    Spacer(Modifier.height(6.dp))
    NkSub("A Nikah is never hidden — it is witnessed. Two witnesses stand guard over this promise.")
    Spacer(Modifier.height(13.dp))
    NkCard {
        Text(
            "✦  “There is no Nikah except with a guardian and two upright witnesses.” Their names live on the Nikah Nama forever.",
            color = Color(0xFFD8D7DE), fontSize = 13.sp, fontFamily = PoppinsFamily, lineHeight = 20.sp,
        )
    }
    Spacer(Modifier.height(16.dp))
    val myWitness = state.witnessNameBySide[myUid] ?: ""
    val partnerWitness = state.witnessNameBySide[partnerUid] ?: ""
    val partnerName = state.nameOf(partnerUid)
    if (myWitness.isNotBlank()) {
        NkCard {
            Text("✓ Your witness", color = NkGold, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(myWitness, color = Color.White, fontSize = 16.sp, fontFamily = SerifRegular)
        }
    } else {
        NkField("Your witness · Gawah", witText, "Full name") { witText = it }
        Spacer(Modifier.height(16.dp))
        NkButton("This witness is present ✦", enabled = gateOpen && witText.isNotBlank()) {
            cb.onSubmitWitness(witText)
        }
    }
    Spacer(Modifier.height(14.dp))
    if (partnerWitness.isNotBlank()) {
        NkSub("✓ $partnerName's witness: $partnerWitness")
    } else {
        NkWaiting("Waiting for $partnerName to add their witness…")
    }
}

@Composable
private fun NkStepSignatures(
    state: NikahUiState, myUid: String, partnerUid: String,
    gateOpen: Boolean, cb: NikahCallbacks,
) {
    // pad + ink state hoisted above the conditional branches (hook order)
    val ctx = LocalContext.current
    val pad = remember { NikahSignatureView(ctx) }
    var hasInk by remember { mutableStateOf(false) }
    DisposableEffect(pad) {
        pad.onInkChanged = { hasInk = it }
        onDispose { pad.onInkChanged = null }
    }
    NkHeading("Sign the Nikah Nama")
    Spacer(Modifier.height(6.dp))
    NkSub("With your own hand — the signature that seals the covenant.")
    NikahMascot("mascot_signing.webp", small = true)
    Spacer(Modifier.height(10.dp))
    val partnerName = state.nameOf(partnerUid)
    val mySigned = state.sigDoneBySide[myUid] == true
    if (mySigned) {
        NkCard {
            Text("✓ You have signed", color = NkGold, fontSize = 14.sp,
                fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.nameOf(myUid).uppercase()} · YOUR SIGNATURE",
                color = NkRoseSoft, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = PoppinsFamily, letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Clear", color = NkGold, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = PoppinsFamily,
                modifier = Modifier.clickable { pad.clear() }.padding(6.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        AndroidView(
            factory = { pad },
            modifier = Modifier.fillMaxWidth().height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(0.05f))
                .border(1.5.dp, NkGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(16.dp))
        NkButton("Sign ✦", enabled = gateOpen && hasInk) {
            pad.exportPng()?.let { cb.onSubmitSignature(it) }
        }
    }
    Spacer(Modifier.height(14.dp))
    if (state.sigDoneBySide[partnerUid] == true) {
        NkSub("✓ $partnerName has signed")
    } else {
        NkWaiting("Waiting for $partnerName's signature…")
    }
}

@Composable
private fun NkStepDua(state: NikahUiState, myUid: String, gateOpen: Boolean, cb: NikahCallbacks) {
    NkHeading("Mubarak! 💛")
    NikahMascot("mascot_celebrate.webp", small = true)
    Spacer(Modifier.height(8.dp))
    NkCard {
        Text(
            "بَارَكَ اللهُ لَكُمَا وَبَارَكَ عَلَيْكُمَا وَجَمَعَ بَيْنَكُمَا فِي خَيْرٍ",
            color = Color(0xFFF6E3B4), fontSize = 25.sp, lineHeight = 44.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "Barakallahu lakuma, wa baraka alaikuma, wa jama'a bainakuma fi khair.",
            color = NkRoseSoft, fontSize = 13.5.sp, fontFamily = SerifItalic,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "\"May Allah bless you both, shower His blessings upon you, and unite you both in goodness.\"",
            color = Color(0xFFD8D7DE), fontSize = 13.sp, fontFamily = PoppinsFamily,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
    }
    Spacer(Modifier.height(14.dp))
    NkSub("By the mercy of Allah, ${state.groomName} and ${state.brideName} are now husband and wife. May your days be filled with sakina, mawaddah and rahmah. Ameen.")
    Spacer(Modifier.height(20.dp))
    if (state.dua[myUid] == true) {
        NkWaiting("Ameen ✓ — waiting for your partner…")
    } else {
        NkButton("Ameen", enabled = gateOpen) { cb.onDuaAmeen() }
    }
}

@Composable
private fun NkStepNama(state: NikahUiState, cb: NikahCallbacks) {
    NkHeading("Your Nikah Nama")
    Spacer(Modifier.height(6.dp))
    NikahCertificateCard(state.keepsake())
    Spacer(Modifier.height(14.dp))
    Text(
        "A symbolic keepsake of love, created inside Tiku. A keepsake card has been saved in your chat — open it any time to see this certificate again.",
        color = Sub, fontSize = 10.5.sp, fontFamily = PoppinsFamily,
        textAlign = TextAlign.Center, lineHeight = 15.sp,
    )
    Spacer(Modifier.height(18.dp))
    NkButton("Done ✦") { cb.onClose() }
}

/** Confetti in the prototype's colours — a single deterministic burst
 *  over the Dua screen (drawn, never interactive). */
@Composable
private fun NikahConfetti() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3200)) }
    val colors = remember {
        listOf(Rose, NkRoseSoft, NkGold, Color(0xFFFFB347), Color(0xFFFFF3D6))
    }
    val parts = remember {
        kotlin.random.Random(60).let { r ->
            List(110) {
                floatArrayOf(
                    r.nextFloat(), r.nextFloat() * 0.45f,
                    5f + r.nextFloat() * 6f, 8f + r.nextFloat() * 8f,
                    r.nextFloat(),
                ) to colors[it % colors.size]
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        parts.forEach { (f, color) ->
            val y = ((p * 1.25f - f[1]) * size.height)
            if (y < -20f || y > size.height + 20f) return@forEach
            val x = f[0] * size.width + (f[4] - 0.5f) * 60f * p
            drawRect(
                color = color.copy(alpha = 0.9f),
                topLeft = Offset(x, y),
                size = Size(f[2] * 2f, f[3] * 2f),
            )
        }
    }
}

// ---------------- certificate ----------------

/** The Nikah Nama — a paper document floating on the dark app, rendered
 *  on-device from the decrypted state/payload (prototype layout). Used
 *  by the ceremony's final step AND by the keepsake card in the chat. */
@Composable
fun NikahCertificateCard(k: NikahKeepsake, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF7), Color(0xFFFBF0DA))))
            .border(3.dp, NkGold, RoundedCornerShape(10.dp))
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            color = NkCertGreen, fontSize = 20.sp, textAlign = TextAlign.Center,
        )
        Text(
            "NIKAH NAMA", color = NkCertGreen, fontSize = 23.sp,
            fontFamily = SerifRegular, letterSpacing = 2.5.sp,
        )
        Text(
            "CERTIFICATE OF NIKAH", color = NkGoldDeep, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, fontFamily = PoppinsFamily, letterSpacing = 2.5.sp,
        )
        Spacer(Modifier.height(13.dp))
        Text(
            "This is to joyfully certify that the Nikah of\n" +
                "${k.groomName}  ✦  ${k.brideName}\n" +
                "was solemnised in the Islamic way — with Khutbah, Ijab-o-Qabool spoken " +
                "three times by both, in the presence of two witnesses, with a Mahr of " +
                "${k.mahr} given by the groom to the bride.",
            color = Color(0xFF4A2E33), fontSize = 12.5.sp, fontFamily = PoppinsFamily,
            textAlign = TextAlign.Center, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Date", color = Color(0xFF8A6D3B), fontSize = 11.sp, fontFamily = PoppinsFamily)
                Text(
                    if (k.dateMs > 0L) nikahDateStr(k.dateMs) else "",
                    color = Color(0xFF4A2E33), fontSize = 11.sp,
                    fontFamily = PoppinsFamily, fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Witnesses", color = Color(0xFF8A6D3B), fontSize = 11.sp,
                    fontFamily = PoppinsFamily, textAlign = TextAlign.End)
                Text(
                    listOf(k.witnessGroom, k.witnessBride).filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = Color(0xFF4A2E33), fontSize = 11.sp,
                    fontFamily = PoppinsFamily, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NkCertSignature(k.sigGroomPngB64, "Signature of the Groom", Modifier.weight(1f))
            NkCertSignature(k.sigBridePngB64, "Signature of the Bride", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(74.dp).rotate(-10f).clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFF5D67E), Color(0xFFC29B2C), Color(0xFF96771C)),
                        center = Offset(60f, 50f), radius = 130f,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "QABOOL\n✦\nSEALED", color = Color(0xFF4A3805), fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold, fontFamily = PoppinsFamily,
                textAlign = TextAlign.Center, lineHeight = 11.sp,
            )
        }
    }
}

@Composable
private fun NkCertSignature(pngB64: String, label: String, modifier: Modifier = Modifier) {
    val bmp = remember(pngB64) {
        if (pngB64.isBlank()) null
        else runCatching {
            val bytes = Crypto.unb64(pngB64)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    Modifier.fillMaxWidth().height(44.dp),
                    contentScale = ContentScale.Fit,
                    // the pad's light-pink strokes become dark ink on paper
                    colorFilter = ColorFilter.tint(NkPaperInk),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.2.dp).background(Color(0xFFB49B4E)))
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(), color = Color(0xFF8A6D3B), fontSize = 9.sp,
            fontFamily = PoppinsFamily, letterSpacing = 1.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Full-screen keepsake view (from the chat's keepsake card). */
@Composable
fun NikahCertificateOverlay(k: NikahKeepsake, onClose: () -> Unit) {
    BackHandler { onClose() }
    Box(
        Modifier.fillMaxSize().background(NkBg)
            .clickable { onClose() },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NkHeading("Your Nikah Nama")
            Spacer(Modifier.height(14.dp))
            NikahCertificateCard(k)
            Spacer(Modifier.height(16.dp))
            Text(
                "A symbolic keepsake of love, created inside Tiku — saved privately in this chat.",
                color = Sub, fontSize = 10.5.sp, fontFamily = PoppinsFamily,
                textAlign = TextAlign.Center, lineHeight = 15.sp,
            )
            Spacer(Modifier.height(18.dp))
            NkGhostButton("Close", onClose)
        }
    }
}

// ---------------- chat cards / banner / starter ----------------

/** The invite CARD rendered in the thread in place of the fallback text
 *  bubble (old builds see the fallback line — see TikuMessage.nikah). */
@Composable
fun NikahInviteRow(
    m: TikuMessage, session: NikahUiState?, peerName: String, onOpen: () -> Unit,
) {
    val stateText = when {
        m.nikahState == "cancelled" || session?.status == "cancelled" -> "cancelled"
        m.nikahState == "done" || session?.status == "done" -> "done"
        else -> "active"
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(292.dp).clip(RoundedCornerShape(22.dp))
                .background(CardC).border(1.dp, NkGold.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("💍", fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text("Nikah Invitation", color = Color.White, fontSize = 16.sp,
                fontFamily = SerifRegular)
            Spacer(Modifier.height(4.dp))
            Text(
                if (m.me) "You invited $peerName to a Nikah ceremony"
                else "$peerName invited you to a Nikah ceremony",
                color = Sub, fontSize = 12.5.sp, fontFamily = PoppinsFamily,
                textAlign = TextAlign.Center, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(13.dp))
            when (stateText) {
                "cancelled" -> Text("Cancelled", color = Danger, fontSize = 13.sp,
                    fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold)
                "done" -> Text("Nikah complete 💛", color = NkGold, fontSize = 13.sp,
                    fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold)
                else -> Box(
                    Modifier.clip(CircleShape).background(NkRoseBrush)
                        .clickable { onOpen() }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(
                        if (m.me) "Open the Nikah" else "Join the Nikah",
                        color = Color.White, fontSize = 13.5.sp,
                        fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** The keepsake CARD: the forever copy of the certificate, carried
 *  inside the message itself (see NikahKeepsake). */
@Composable
fun NikahKeepsakeRow(m: TikuMessage, onView: () -> Unit) {
    val k = m.nikahKeepsake
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(292.dp).clip(RoundedCornerShape(22.dp))
                .background(CardC).border(1.dp, NkGold.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("💍", fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (k != null) "Nikah complete — ${
                    SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(k.dateMs))
                }" else m.text,
                color = Color.White, fontSize = 15.sp, fontFamily = SerifRegular,
                textAlign = TextAlign.Center,
            )
            if (k != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${k.groomName}  ✦  ${k.brideName}",
                    color = NkGold, fontSize = 12.5.sp, fontFamily = PoppinsFamily,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(13.dp))
                Box(
                    Modifier.clip(CircleShape).background(NkGoldBrush)
                        .clickable { onView() }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Text(
                        "View certificate", color = Color(0xFF3A2A04), fontSize = 13.5.sp,
                        fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** The rejoin banner shown over the composer while a ceremony is live. */
@Composable
fun NikahProgressBanner(onTap: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardC).border(1.dp, NkGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { onTap() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💍", fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            "Nikah in progress — tap to rejoin",
            color = Color.White, fontSize = 13.5.sp, fontFamily = PoppinsFamily,
            modifier = Modifier.weight(1f),
        )
        Text("→", color = NkGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Role pick shown when "Nikah" is tapped with no live ceremony: the
 *  creator declares their own role; the partner is the other role. */
@Composable
fun NikahStarterSheet(
    myName: String, peerName: String,
    onPick: (String) -> Unit, onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(SheetC).border(1.dp, NkLine, RoundedCornerShape(28.dp))
                .clickable { }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("💍", fontSize = 26.sp)
            Spacer(Modifier.height(6.dp))
            Text("Begin a Nikah", color = Color.White, fontSize = 19.sp, fontFamily = SerifRegular)
            Spacer(Modifier.height(6.dp))
            NkSub("A live ceremony with $peerName, on both phones together. First — who are you in this Nikah?")
            Spacer(Modifier.height(16.dp))
            NkButton("I am the groom") { onPick("groom") }
            Spacer(Modifier.height(10.dp))
            NkGhostButton("I am the bride") { onPick("bride") }
            Spacer(Modifier.height(6.dp))
            Text(
                "Cancel", color = Sub, fontSize = 13.sp, fontFamily = PoppinsFamily,
                modifier = Modifier.clickable { onDismiss() }.padding(10.dp),
            )
        }
    }
}
