package com.tiku.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** .form scaffold: 56dp top padding, 24dp sides, title + muted sub.
 *  ROUND 36: the whole auth family (create / restore / reset / profile
 *  setup — and the journal gate, which shares this scaffold) moved onto
 *  the current app's premium-dark stage: palette background with accent
 *  glow pools + the theme pattern (the call stage's recipe) and an
 *  InterTight title. Layout, padding and structure are unchanged —
 *  visuals only. */
@Composable
fun FormScaffold(title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize().background(pal.bg)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(pal.ac.copy(alpha = 0.30f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(pal.ac2.copy(alpha = 0.20f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                        radius = size.width * 0.9f,
                    ),
                )
                with(density) { drawThemePattern(pal.pat, pal.ac, pal.isDark, 160.dp.toPx()) }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp)) {
            Text(title, color = pal.tx, fontSize = 32.sp, fontFamily = InterTight,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Spacer(Modifier.height(8.dp))
            Text(sub, color = pal.sub, fontSize = 14.sp, fontFamily = PoppinsFamily, lineHeight = 21.sp)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
fun PasswordField(value: String, onValue: (String) -> Unit, placeholder: String, dark: Boolean = false) {
    var shown by remember { mutableStateOf(false) }
    // ROUND 12/13: [dark] = the Tik glass skin (palette field + hairline)
    // for the in-Tik Change password sheet.
    // ROUND 36: the default skin is the SAME palette glass — the auth
    // family and the gate now sit on the premium-dark stage (FormScaffold)
    // and the Change-password sheet was already a dark glass surface, so
    // the old peach field matched nothing. [dark] now only tightens the
    // corner radius for the sheet.
    val pal = LocalPalette.current
    val bg = pal.s2
    val line = pal.ln
    val ink = pal.tx
    val muted = pal.sub
    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .clip(RoundedCornerShape(if (dark) 12.dp else 16.dp))
            .background(bg)
            .border(1.dp, line, RoundedCornerShape(if (dark) 12.dp else 16.dp)),
    ) {
        BasicTextField(
            value = value, onValueChange = onValue,
            textStyle = TextStyle(fontSize = 15.sp, fontFamily = PoppinsFamily, color = ink),
            cursorBrush = SolidColor(ink),
            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
            // OWNER BUG 0: a password field MUST present a password IME — plain-text
            // keyboards auto-correct / auto-capitalize / suggest, so the string the
            // phone commits at creation can differ from the string at the gate.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart)
                .padding(start = 18.dp, end = 56.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, fontFamily = PoppinsFamily, color = muted)
                inner()
            },
        )
        Box(
            Modifier.size(44.dp).align(Alignment.CenterEnd).padding(end = 6.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { shown = !shown },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, Modifier.size(20.dp), tint = muted)
        }
    }
}

/** Strength level exactly like the prototype JS: len>=8, digit, symbol, mixed case. */
fun strengthOf(p: String): Int =
    listOf(p.length >= 8, p.any { it.isDigit() }, p.any { !it.isLetterOrDigit() && !it.isWhitespace() },
        p.any { it.isLowerCase() } && p.any { it.isUpperCase() }).count { it }

@Composable
fun CreatePasswordScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Facade) }
    val settings = tikuSettings().value
    val pal = LocalPalette.current
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    val n = strengthOf(p1)
    val match = p2.isNotEmpty() && p1 == p2
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    FormScaffold("Lock your journal", "Set a password only you know — your journal and everything inside it stays yours.") {
        PasswordField(p1, { p1 = it }, "Password")
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).padding(end = if (i < 3) 6.dp else 0.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i < n) TikuTokens.MeterColors[n - 1] else pal.ln)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Use 8+ characters with a number and a symbol.", fontSize = 12.sp, color = pal.sub, fontFamily = PoppinsFamily)
        Spacer(Modifier.height(14.dp))
        PasswordField(p2, { p2 = it }, "Confirm password")
        Spacer(Modifier.height(6.dp))
        val mText = if (p2.isEmpty()) "" else if (match) "✓ Passwords match" else "Passwords don’t match yet"
        Text(mText, fontSize = 12.sp, color = if (match) pal.ac else pal.sub, fontFamily = PoppinsFamily)
        if (saveError.isNotEmpty()) {
            Text(saveError, fontSize = 12.sp, color = pal.danger, fontFamily = PoppinsFamily)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "Saving…" else "Continue", enabled = p1.length >= 8 && match && !busy, modifier = Modifier.fillMaxWidth()) {
            busy = true
            saveError = ""
            scope.launch {
                // OWNER BUG 0 — PERSIST + SELF-VERIFY BEFORE PROCEEDING: the
                // verifier saved on this phone must accept THIS password, or
                // setup stops HERE with a plain error. Onboarding must never
                // reach a gate that rejects the password just set.
                val ok = withContext(Dispatchers.Default) {
                    val hash = Crypto.passwordHash(p1)
                    SettingsStore.savePasswordHash(ctx, hash)
                    val stored = SettingsStore.passwordHash(ctx)
                    stored == hash && Crypto.hashWellFormed(stored) &&
                        Crypto.passwordVerify(p1, stored)
                }
                if (ok) {
                    AppState.pendingPassword = p1
                    AppState.go(Screen.ProfileSetup)
                } else {
                    busy = false
                    saveError = "Couldn't save your password on this phone — please try again."
                }
            }
        }
    }
}

/**
 * ROUND 51 — the ONE recovery-key restore form, shared by every place
 * a journal restore is offered (the Restore screen and the
 * forgot-password flow's Mode F) so the crypto path can never diverge:
 * code + a NEW journal password -> FirebaseEngine.restoreWithRecoveryKey
 * -> the host's own landing via [onRestored]. A wrong code feeds the
 * same GateGuard backoff as a wrong password (it is the same class of
 * guessing surface, even at 128 bits) and changes NOTHING — the engine
 * writes only after the code has actually opened the blob.
 */
@Composable
fun RecoveryRestoreForm(onRestored: (cloudUpdated: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var np by remember { mutableStateOf("") }
    var cf by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var lockRemainingMs by remember { mutableStateOf(GateGuard.lockedRemainingMs(ctx)) }
    LaunchedEffect(lockRemainingMs) {
        while (lockRemainingMs > 0) { delay(500); lockRemainingMs = GateGuard.lockedRemainingMs(ctx) }
    }
    val match = cf.isNotEmpty() && np == cf
    PasswordField(code, { code = it; error = "" }, "Recovery key")
    Spacer(Modifier.height(14.dp))
    PasswordField(np, { np = it; error = "" }, "New password")
    Spacer(Modifier.height(14.dp))
    PasswordField(cf, { cf = it; error = "" }, "Confirm new password")
    Spacer(Modifier.height(6.dp))
    Text(
        when {
            lockRemainingMs > 0 -> "Too many tries in a row — take a short break and try again in ${(lockRemainingMs + 999) / 1000}s."
            error.isNotEmpty() -> error
            cf.isNotEmpty() && !match -> "Passwords don’t match yet"
            else -> "Use 8+ characters with a number and a symbol."
        },
        fontSize = 12.5.sp,
        color = if (error.isNotEmpty() || lockRemainingMs > 0) pal.danger else pal.sub,
        fontFamily = PoppinsFamily,
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        if (busy) "Restoring…" else "Restore with my recovery key",
        enabled = code.isNotBlank() && np.length >= 8 && match && !busy && lockRemainingMs <= 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        busy = true
        error = ""
        scope.launch {
            when (val r = FirebaseEngine.restoreWithRecoveryKey(ctx, code, np)) {
                is FirebaseEngine.RecoveryResult.Ok -> {
                    GateGuard.reset(ctx)
                    onRestored(r.cloudUpdated)
                }
                FirebaseEngine.RecoveryResult.WrongCode -> {
                    busy = false
                    GateGuard.recordFailure(ctx)
                    lockRemainingMs = GateGuard.lockedRemainingMs(ctx)
                    code = ""
                    error = "That recovery key doesn't match — check it and try again. Nothing was changed or deleted."
                }
                FirebaseEngine.RecoveryResult.NoRecovery ->
                    { busy = false; error = "This account doesn't have a recovery key — it can't be restored with one." }
                is FirebaseEngine.RecoveryResult.Unavailable ->
                    { busy = false; error = "Couldn't restore right now (${r.why}) — check your connection and try again." }
            }
        }
    }
}

@Composable
fun RestorePasswordScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Facade) }
    val pal = LocalPalette.current
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    // ROUND 51: the recovery-key alternative, offered on this very
    // screen (spec: anywhere the password restore is offered) — it
    // lands through the exact same post-restore path as a password.
    var recOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // ROUND 8: the restore field is the same offline guessing surface as the
    // journal gate — it shares the GateGuard escalating backoff.
    var lockRemainingMs by remember { mutableStateOf(GateGuard.lockedRemainingMs(ctx)) }
    LaunchedEffect(lockRemainingMs) {
        while (lockRemainingMs > 0) { delay(500); lockRemainingMs = GateGuard.lockedRemainingMs(ctx) }
    }
    FormScaffold("Welcome back", "Your journal was found on this account. Enter the password you set when you created it — and everything comes back on this phone: every note and every conversation, exactly as you left it.") {
        // ROUND 51: the password form and the recovery form SWAP (the
        // scaffold doesn't scroll — stacking both could push the
        // recovery button off a small screen).
        if (!recOpen) {
            PasswordField(pw, { pw = it; error = "" }, "Backup password")
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    lockRemainingMs > 0 -> "Too many tries in a row — take a short break and try again in ${(lockRemainingMs + 999) / 1000}s."
                    error.isNotEmpty() -> error
                    else -> "Only this password opens this journal — there is no starting over with a new one. A wrong try changes nothing and deletes nothing."
                },
                fontSize = 13.sp, color = if (error.isNotEmpty() || lockRemainingMs > 0) pal.danger else pal.sub, fontFamily = PoppinsFamily,
            )
        } else {
            Text("Restore with your recovery key — your journal and every chat in it come back; only the password changes.",
                fontSize = 12.5.sp, color = pal.sub, fontFamily = PoppinsFamily)
            Spacer(Modifier.height(12.dp))
            RecoveryRestoreForm { cloudUpdated ->
                ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarded", true).apply()
                FirebaseEngine.start(ctx)
                FirebaseEngine.loadMyProfile()
                FirebaseEngine.setPresence(true)
                AppState.toast(if (cloudUpdated) "Journal restored — welcome back"
                    else "Journal restored on this phone — the cloud backup updates when you're back online")
                AppState.go(Screen.Home)
            }
        }
        Spacer(Modifier.weight(1f))
        if (!recOpen) PrimaryButton(if (busy) "Restoring…" else "Restore my journal", enabled = pw.isNotEmpty() && !busy && lockRemainingMs <= 0, modifier = Modifier.fillMaxWidth()) {
            busy = true
            scope.launch {
                // outcomes are DISTINCT (owner Bug 0): a genuinely wrong password
                // is never confused with a missing backup / server refusal.
                when (val r = FirebaseEngine.restoreFromBackup(ctx, pw)) {
                    is FirebaseEngine.RestoreResult.Ok -> {
                        GateGuard.reset(ctx)
                        ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("onboarded", true).apply()
                        FirebaseEngine.start(ctx)
                        FirebaseEngine.loadMyProfile()
                        FirebaseEngine.setPresence(true)
                        AppState.go(Screen.Home)
                    }
                    is FirebaseEngine.RestoreResult.WrongPassword -> {
                        busy = false
                        // ROUND 8: wrong restore attempts feed the shared backoff too.
                        val delayMs = GateGuard.recordFailure(ctx)
                        lockRemainingMs = GateGuard.lockedRemainingMs(ctx)
                        pw = ""
                        error = if (delayMs > 0) {
                            "Too many tries in a row — take a short break and try again in ${delayMs / 1000}s."
                        } else "Wrong password — nothing was changed or deleted, and your journal is still saved. Try again with the password you set when you created it."
                    }
                    is FirebaseEngine.RestoreResult.Unavailable -> {
                        busy = false
                        error = "Couldn't restore right now (${r.why}) — check your connection and try again. This is not a password problem."
                    }
                }
            }
        }
        // ROUND 51: forgot the password but saved the recovery key?
        // The toggle swaps the form above — the key restores the SAME
        // journal, every chat included.
        if (!Config.PREVIEW_UI_ONLY) {
            Spacer(Modifier.height(16.dp))
            Text(if (recOpen) "Use my password instead" else "Use my recovery key instead",
                fontSize = 13.sp, fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                color = pal.ac,
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        recOpen = !recOpen
                    },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        // ROUND 10 PARITY: a forgotten password must have a way forward from
        // HERE too — this restore screen is where a returning user actually
        // lands after sign-in. The link leads to the same reset flow the
        // gate offers (Mode F on this phone: no local key lives here).
        if (!Config.PREVIEW_UI_ONLY && !recOpen) {
            Spacer(Modifier.height(14.dp))
            Text("Forgot your password?",
                fontSize = 13.sp, fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                color = pal.ac,
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        AppState.go(Screen.PasswordReset)
                    },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/**
 * FORGOT-PASSWORD RESET (owner Bug 0 + ROUND 10 PARITY). Two modes, chosen
 * by whether THIS phone still holds the journal key:
 *  - Mode R ([ResetPasswordRewrap]): the key is here (device-Keystore
 *    wrapped, password-independent). Re-auth the SAME Google account, set a
 *    new password — the existing key is re-wrapped under it and the cloud
 *    backup re-uploaded. History survives intact.
 *  - Mode F ([ResetPasswordFreshStart]): no key on this phone and the old
 *    password gone. ROUND 51: the first option is now the RECOVERY KEY —
 *    it unwraps the SAME key, a new password is set, and every chat
 *    survives (see RecoveryRestoreForm). Only when there is no usable
 *    recovery key does the old road remain: after the same Google
 *    re-auth + uid binding and an explicit one-way warning (plus the
 *    ROUND-50 confirmation checkbox), the old key material is retired
 *    server-side and the user starts a FRESH journal (same account,
 *    same username, new keypair + new password).
 *
 * ROUND 10 PARITY RULE: this flow is IDENTICAL for every account. There is
 * deliberately NO OwnerState / isOwner / OWNER_EMAIL check anywhere in
 * either mode — the owner was previously the only account with no reset
 * path at all when his phone lost the local key to an uninstall, which is
 * exactly the gap Mode F closes.
 */
@Composable
fun ResetPasswordScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.TikGate) }
    val ctx = LocalContext.current
    val hasIdentity = remember {
        !Config.PREVIEW_UI_ONLY && FirebaseEngine.hasLocalIdentity(ctx)
    }
    if (hasIdentity) ResetPasswordRewrap() else ResetPasswordFreshStart()
}

/** Mode R — the original Bug-0 rescue body, unchanged. */
@Composable
private fun ResetPasswordRewrap() {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val match = p2.isNotEmpty() && p1 == p2
    // captured ONCE: the account this device's journal belongs to — a mistaken
    // account switch during re-auth must not be able to move these goalposts
    val expectedUid = remember { FirebaseEngine.uid }
    val boundUid = remember { FirebaseEngine.localIdentityUid(ctx) }
    FormScaffold("Reset your password",
        "Your journal key is still safe on this phone. Confirm your Google account, then choose a new password.") {
        if (stage == 0) {
            Text("Sign in with the same Google account this journal was created with. Your phone plus your Google account together unlock this reset — without them it can't happen.",
                fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.sub)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(note, fontSize = 12.sp, color = pal.danger, fontFamily = PoppinsFamily)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (busy) "Checking…" else "Verify with Google", enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                val act = ctx as? android.app.Activity
                if (act == null) { note = "Something went wrong — go back and try again."; return@PrimaryButton }
                busy = true
                note = ""
                scope.launch {
                    val res = FirebaseEngine.googleSignIn(act)
                    val freshUid = FirebaseEngine.uid
                    when {
                        res.isFailure -> { busy = false; note = "Google sign-in didn't complete. Try again." }
                        expectedUid.isNotEmpty() && freshUid != expectedUid -> {
                            busy = false
                            note = "That's a different Google account — use the one this journal was created with."
                        }
                        boundUid.isNotEmpty() && freshUid != boundUid -> {
                            busy = false
                            note = "That's a different Google account — use the one this journal was created with."
                        }
                        else -> { busy = false; stage = 1 }
                    }
                }
            }
        } else {
            PasswordField(p1, { p1 = it }, "New password")
            Spacer(Modifier.height(14.dp))
            PasswordField(p2, { p2 = it }, "Confirm new password")
            Spacer(Modifier.height(6.dp))
            val mText = when {
                note.isNotEmpty() -> note
                p2.isEmpty() -> ""
                match -> "✓ Passwords match"
                else -> "Passwords don’t match yet"
            }
            Text(mText, fontSize = 12.sp,
                color = if (note.isNotEmpty()) pal.danger else pal.sub, fontFamily = PoppinsFamily)
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (busy) "Resetting…" else "Set new password",
                enabled = p1.length >= 8 && match && !busy, modifier = Modifier.fillMaxWidth()) {
                busy = true
                note = ""
                scope.launch {
                    val r = FirebaseEngine.resetBackupPassword(ctx, p1)
                    if (r.isSuccess) {
                        ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("onboarded", true).apply()
                        AppState.toast(if (r.getOrNull() == true) "Password reset — welcome back"
                            else "Password reset on this phone — the cloud backup updates when you're back online")
                        ChatSession.unlockFlow(ctx)
                    } else {
                        busy = false
                        note = r.exceptionOrNull()?.message ?: "Couldn't reset. Try again."
                    }
                }
            }
        }
    }
}

/**
 * Mode F — START FRESH (Round 10). Same two-stage shape as Mode R: stage 0
 * is the identical Google re-auth with uid binding (the re-authed uid must
 * equal the uid captured on entry — a different account can never retire
 * this journal); stage 1 is the one-way warning + the single explicit
 * "Start fresh" button. On tap the engine retires the old key material
 * (server + local) and this routes into the SAME fresh-journal onboarding
 * new users get (CreatePassword → ProfileSetup → finishNewUser), so the
 * new keypair, password and backup are created by the existing code.
 */
@Composable
private fun ResetPasswordFreshStart() {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    // ROUND 50: the one-way door needs a deliberate, separate act of
    // consent — the start-fresh button stays disabled until this box
    // is ticked. A single tap on a warning screen was too easy a way
    // to orphan a whole journal (the owner's build-84 key loss).
    var confirmed by remember { mutableStateOf(false) }
    // ROUND 51: does this account carry a recovery wrap at all?
    // (null = the check itself failed — never reported as "none".)
    var hasRecovery by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        if (!Config.PREVIEW_UI_ONLY) hasRecovery = FirebaseEngine.hasRecoveryKey()
    }
    // captured ONCE — same binding discipline as Mode R
    val expectedUid = remember { FirebaseEngine.uid }
    if (stage == 0) {
        FormScaffold("Reset your password",
            "This phone no longer holds your journal key. Confirm your Google account to continue.") {
            Text("Sign in with the same Google account this journal was created with. Your phone plus your Google account together unlock this reset — without them it can't happen.",
                fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.sub)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(note, fontSize = 12.sp, color = pal.danger, fontFamily = PoppinsFamily)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (busy) "Checking…" else "Verify with Google", enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                val act = ctx as? android.app.Activity
                if (act == null) { note = "Something went wrong — go back and try again."; return@PrimaryButton }
                busy = true
                note = ""
                scope.launch {
                    val res = FirebaseEngine.googleSignIn(act)
                    val freshUid = FirebaseEngine.uid
                    when {
                        res.isFailure -> { busy = false; note = "Google sign-in didn't complete. Try again." }
                        expectedUid.isNotEmpty() && freshUid != expectedUid -> {
                            busy = false
                            note = "That's a different Google account — use the one this journal was created with."
                        }
                        else -> { busy = false; stage = 1 }
                    }
                }
            }
        }
    } else if (stage == 1) {
        // ROUND 51: the choice. The recovery key is the FIRST option —
        // it keeps every chat; start-fresh is demoted to the last
        // resort at the bottom. The recovery button shows unless the
        // account is KNOWN to have no recovery wrap (a failed check
        // never hides it — the form itself reports the truth).
        FormScaffold("Reset your password",
            "Your Google account is confirmed. Choose how to continue.") {
            if (hasRecovery == false) {
                Text("This account doesn't have a recovery key, so its chats can't be brought back with one.",
                    fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.tx)
            } else {
                Text(
                    if (hasRecovery == true) "Enter the recovery key you saved when you set up your journal, choose a new password — and everything comes back: every note and every conversation, exactly as you left it."
                    else "If you saved a recovery key when you set up your journal, it brings everything back — every chat, exactly as it was. Only your password changes.",
                    fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.sub)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Use my recovery key — keeps all your chats", enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    stage = 2
                }
            }
            Spacer(Modifier.weight(1f))
            Text("I don't have a recovery key — start fresh",
                fontSize = 13.sp, fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                color = pal.sub,
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        stage = 3
                    },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    } else if (stage == 2) {
        FormScaffold("Restore with your recovery key",
            "Your journal and every chat in it come back exactly as they were — only your password changes.") {
            RecoveryRestoreForm { cloudUpdated ->
                ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarded", true).apply()
                AppState.toast(if (cloudUpdated) "Journal restored — welcome back"
                    else "Journal restored on this phone — the cloud backup updates when you're back online")
                ChatSession.unlockFlow(ctx)
            }
            Spacer(Modifier.height(14.dp))
            Text("Back",
                fontSize = 13.sp, fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
                color = pal.ac,
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        stage = 1
                    },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    } else {
        FormScaffold("Start a fresh journal",
            "Read this once — there is no undo.") {
            Text("Your old password is gone and this phone doesn't hold your journal key, so your existing chats can never be opened again — by anyone, on any phone. They stay locked away exactly as they are.",
                fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.tx)
            Spacer(Modifier.height(10.dp))
            Text("Starting fresh keeps the same account and the same username. You will set a new password and get a brand-new journal — new chats work exactly as before.",
                fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.sub)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { confirmed = !confirmed }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (confirmed) pal.ac else Color.Transparent)
                        .border(1.5.dp, if (confirmed) pal.ac else pal.sub, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (confirmed) Text("✓", color = Color.White, fontSize = 14.sp, fontFamily = PoppinsFamily)
                }
                Spacer(Modifier.width(12.dp))
                Text("I understand: start fresh — my old chats will never open again, on any phone.",
                    fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.tx)
            }
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(note, fontSize = 12.sp, color = pal.danger, fontFamily = PoppinsFamily)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (busy) "Starting…" else "Start fresh with a new journal", enabled = confirmed && !busy, modifier = Modifier.fillMaxWidth()) {
                busy = true
                note = ""
                scope.launch {
                    val r = FirebaseEngine.startFreshAfterKeyLoss(ctx)
                    if (r.isSuccess) {
                        // route into the standard fresh-journal onboarding
                        ChatSession.unlocked = true
                        AppState.toast("Old journal closed — set a new password to start fresh")
                        AppState.go(Screen.Password)
                    } else {
                        busy = false
                        note = "Couldn't start fresh (${r.exceptionOrNull()?.message ?: "try again"}) — check your connection and try again. Nothing was changed."
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSetupScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Facade) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = tikuSettings().value
    val pal = LocalPalette.current
    var username by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("Letters, numbers, dots and underscores.") }
    var available by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // ROUND 51: after a successful finishNewUser, the flow detours to
    // the one-time recovery-key display before Home (the engine
    // stashed the code in AppState.pendingRecoveryCode).
    var showRecovery by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // ROUND 18: a photo that can't be read used to fail silently (the
        // tap looked like it did nothing). Say so.
        if (uri != null) {
            val bmp = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
            }.getOrNull()
            if (bmp != null) AppState.myAvatar = bmp
            else AppState.toast("Couldn't load that photo")
        }
    }

    LaunchedEffect(username) {
        available = false
        if (username.isEmpty()) { hint = "Letters, numbers, dots and underscores."; return@LaunchedEffect }
        delay(450)
        when {
            username.length < 3 -> hint = "At least 3 characters."
            else -> {
                // ROUND 18: this check used to run bare — a failed read
                // (offline, a hiccup) threw out of the effect and the
                // screen just sat there: stale hint, Continue never
                // enabled, no error anywhere (part of the Atika setup
                // trap). A failure now SAYS so; the next keystroke
                // retries.
                val taken = runCatching {
                    if (Config.PREVIEW_UI_ONLY) username in listOf("admin", "tikuu")
                    else FirebaseEngine.usernameTaken(username)
                }.getOrElse {
                    hint = "Couldn't check that ID — check your connection, then type again"
                    return@LaunchedEffect
                }
                if (taken) hint = "@$username is taken. Try another."
                else { hint = "✓ @$username is available"; available = true }
            }
        }
    }

    if (showRecovery) {
        RecoveryKeyShowStage()
        return
    }

    FormScaffold("Set up your profile", "Add a photo and pick your Tiku ID.") {
        // avatar picker: mint tile, + badge
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                Modifier.size(116.dp)
                    .shadow(18.dp, RoundedCornerShape(40.dp), spotColor = pal.ac2)
                    .clip(RoundedCornerShape(40.dp))
                    .background(pal.s2)
                    .border(1.dp, pal.gb, RoundedCornerShape(40.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        // opening the system picker backgrounds the app — without
                        // this flag the instant re-lock yanked onboarding to the
                        // facade mid-setup (owner Bug 0)
                        ChatSession.expectExternalActivity = true
                        pick.launch("image/*")
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = AppState.myAvatar
                if (bmp != null) {
                    androidx.compose.foundation.Image(bmp.asBmp(), null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    EmojiArt("🙂", 40.dp)
                }
            }
            Box(
                Modifier.size(28.dp).align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp)
                    .clip(CircleShape).background(pal.buttonGradient),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Normal, fontFamily = InterTight)
            }
        }
        Spacer(Modifier.height(22.dp))
        // ROUND 11: bundled presets — picking one sets the SAME
        // AppState.myAvatar the upload path sets, so Continue sends it
        // through finishNewUser → compressAvatar → photoData unchanged.
        PresetAvatarPicker(dark = true) { bmp -> AppState.myAvatar = bmp }
        Spacer(Modifier.height(18.dp))
        // @ field
        Box(
            Modifier.fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pal.s2)
                .border(1.dp, pal.ln, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("@", color = pal.sub, fontSize = 15.sp, fontFamily = PoppinsFamily,
                modifier = Modifier.padding(start = 18.dp))
            BasicTextField(
                value = username,
                onValueChange = { raw ->
                    username = raw.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20)
                },
                textStyle = TextStyle(fontSize = 15.sp, fontFamily = PoppinsFamily, color = pal.tx),
                cursorBrush = SolidColor(pal.tx),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 18.dp),
                decorationBox = { inner ->
                    if (username.isEmpty()) Text("username", fontSize = 15.sp, fontFamily = PoppinsFamily, color = pal.sub)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 12.sp, color = if (available) pal.ac else pal.sub, fontFamily = PoppinsFamily)
        Spacer(Modifier.height(4.dp))
        Text("Friends use your Tiku ID to find you.", fontSize = 12.sp, color = pal.sub, fontFamily = PoppinsFamily)
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "Setting up…" else "Continue",
            enabled = available && !busy, modifier = Modifier.fillMaxWidth()) {
            AppState.myUsername = username
            if (Config.PREVIEW_UI_ONLY) {
                ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarded", true).apply()
                AppState.go(Screen.Home)
                AppState.toast("Welcome to Tiku")
            } else {
                busy = true
                scope.launch {
                    val avatarBytes = AppState.myAvatar?.let { bmp ->
                        val out = java.io.ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        out.toByteArray()
                    }
                    val res = FirebaseEngine.finishNewUser(ctx, AppState.pendingPassword, username, avatarBytes)
                    AppState.pendingPassword = ""
                    if (res.isSuccess) {
                        ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("onboarded", true).apply()
                        FirebaseEngine.start(ctx)
                        // ROUND 51: show the recovery key ONCE before
                        // Home — setup is not complete until the user
                        // confirms they saved it. (An empty stash —
                        // impossible on this path — falls through to
                        // Home; a fresh key can be made in Settings.)
                        if (AppState.pendingRecoveryCode.isNotEmpty()) {
                            busy = false
                            showRecovery = true
                        } else {
                            AppState.go(Screen.Home)
                            AppState.toast("Welcome to Tiku")
                        }
                    } else if (res.exceptionOrNull() is FirebaseEngine.ExistingJournalException) {
                        // ROUND 5: this account already has a journal in the
                        // cloud — setup was refused BEFORE anything could be
                        // overwritten. The only way forward is the password.
                        busy = false
                        AppState.toast("This account already has a journal — enter your password to bring everything back")
                        AppState.go(Screen.Restore)
                    } else {
                        busy = false
                        AppState.toast(res.exceptionOrNull()?.message ?: "Setup failed")
                    }
                }
            }
        }
    }
}


/**
 * ROUND 51 — the one-time recovery-key display that completes every
 * NEW journal setup. The code (AppState.pendingRecoveryCode, RAM
 * only) is shown once with a copy button; Continue stays disabled
 * until the unticked-by-default "I have saved it" box is ticked —
 * the same deliberate-consent pattern ROUND 50 put on start-fresh.
 * Leaving clears the stash: the code is never persisted anywhere,
 * and a user who loses it can rotate a fresh one from Settings
 * (which retires this one).
 */
@Composable
private fun RecoveryKeyShowStage() {
    androidx.activity.compose.BackHandler { /* setup is done — nowhere to go back to */ }
    val pal = LocalPalette.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val code = AppState.pendingRecoveryCode
    var confirmed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    FormScaffold("Save your recovery key",
        "If you ever forget your journal password, this key brings your journal — and every chat in it — back on a new phone. It is shown only this once: Tiku never stores it and can never show it again.") {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(pal.s2)
                .border(1.dp, pal.ln, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(code, color = pal.tx, fontSize = 18.sp, fontFamily = InterTight,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (copied) "Copied ✓" else "Copy the key",
            fontSize = 13.sp, fontFamily = PoppinsFamily, fontWeight = FontWeight.SemiBold,
            color = pal.ac,
            modifier = Modifier.fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                    copied = true
                },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Keep it somewhere only you can reach. Anyone who has this key together with your Google account can restore your journal — treat it like a password.",
            fontSize = 12.5.sp, fontFamily = PoppinsFamily, color = pal.sub)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { confirmed = !confirmed }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (confirmed) pal.ac else Color.Transparent)
                    .border(1.5.dp, if (confirmed) pal.ac else pal.sub, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (confirmed) Text("✓", color = Color.White, fontSize = 14.sp, fontFamily = PoppinsFamily)
            }
            Spacer(Modifier.width(12.dp))
            Text("I have saved my recovery key somewhere safe.",
                fontSize = 13.sp, fontFamily = PoppinsFamily, color = pal.tx)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", enabled = confirmed, modifier = Modifier.fillMaxWidth()) {
            AppState.pendingRecoveryCode = ""
            AppState.go(Screen.Home)
            AppState.toast("Welcome to Tiku")
        }
    }
}

// bitmap -> Compose ImageBitmap helper
fun android.graphics.Bitmap.asBmp(): androidx.compose.ui.graphics.ImageBitmap = asImageBitmap()
