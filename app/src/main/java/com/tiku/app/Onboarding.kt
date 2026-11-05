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
            Text(sub, color = pal.sub, fontSize = 15.sp, fontFamily = InterTight, lineHeight = 21.sp)
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
            textStyle = TextStyle(fontSize = 16.sp, fontFamily = InterTight, color = ink),
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
                if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, fontFamily = InterTight, color = muted)
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
        Text("Use 8+ characters with a number and a symbol.", fontSize = 13.sp, color = pal.sub, fontFamily = InterTight)
        Spacer(Modifier.height(14.dp))
        PasswordField(p2, { p2 = it }, "Confirm password")
        Spacer(Modifier.height(6.dp))
        val mText = if (p2.isEmpty()) "" else if (match) "✓ Passwords match" else "Passwords don’t match yet"
        Text(mText, fontSize = 13.sp, color = if (match) pal.ac else pal.sub, fontFamily = InterTight)
        if (saveError.isNotEmpty()) {
            Text(saveError, fontSize = 13.sp, color = pal.danger, fontFamily = InterTight)
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

@Composable
fun RestorePasswordScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Facade) }
    val pal = LocalPalette.current
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // ROUND 8: the restore field is the same offline guessing surface as the
    // journal gate — it shares the GateGuard escalating backoff.
    var lockRemainingMs by remember { mutableStateOf(GateGuard.lockedRemainingMs(ctx)) }
    LaunchedEffect(lockRemainingMs) {
        while (lockRemainingMs > 0) { delay(500); lockRemainingMs = GateGuard.lockedRemainingMs(ctx) }
    }
    FormScaffold("Welcome back", "Your journal was found on this account. Enter the password you set when you created it — and everything comes back on this phone: every note and every conversation, exactly as you left it.") {
        PasswordField(pw, { pw = it; error = "" }, "Backup password")
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                lockRemainingMs > 0 -> "Too many tries in a row — take a short break and try again in ${(lockRemainingMs + 999) / 1000}s."
                error.isNotEmpty() -> error
                else -> "Only this password opens this journal — there is no starting over with a new one. A wrong try changes nothing and deletes nothing."
            },
            fontSize = 13.sp, color = if (error.isNotEmpty() || lockRemainingMs > 0) pal.danger else pal.sub, fontFamily = InterTight,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "Restoring…" else "Restore my journal", enabled = pw.isNotEmpty() && !busy && lockRemainingMs <= 0, modifier = Modifier.fillMaxWidth()) {
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
        // ROUND 10 PARITY: a forgotten password must have a way forward from
        // HERE too — this restore screen is where a returning user actually
        // lands after sign-in. The link leads to the same reset flow the
        // gate offers (Mode F on this phone: no local key lives here).
        if (!Config.PREVIEW_UI_ONLY) {
            Spacer(Modifier.height(14.dp))
            Text("Forgot your password?",
                fontSize = 14.sp, fontFamily = InterTight, fontWeight = FontWeight.SemiBold,
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
 *    password gone — the cloud backup can never be opened again. After the
 *    same Google re-auth + uid binding and an explicit one-way warning, the
 *    old key material is retired server-side and the user starts a FRESH
 *    journal (same account, same username, new keypair + new password).
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
                fontSize = 14.sp, fontFamily = InterTight, color = pal.sub)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(note, fontSize = 13.sp, color = pal.danger, fontFamily = InterTight)
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
            Text(mText, fontSize = 13.sp,
                color = if (note.isNotEmpty()) pal.danger else pal.sub, fontFamily = InterTight)
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
    // captured ONCE — same binding discipline as Mode R
    val expectedUid = remember { FirebaseEngine.uid }
    if (stage == 0) {
        FormScaffold("Reset your password",
            "This phone no longer holds your journal key. Confirm your Google account to continue.") {
            Text("Sign in with the same Google account this journal was created with. Your phone plus your Google account together unlock this reset — without them it can't happen.",
                fontSize = 14.sp, fontFamily = InterTight, color = pal.sub)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(note, fontSize = 13.sp, color = pal.danger, fontFamily = InterTight)
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
    } else {
        FormScaffold("Start a fresh journal",
            "Read this once — there is no undo.") {
            Text("Your old password is gone and this phone doesn't hold your journal key, so your existing chats can never be opened again — by anyone, on any phone. They stay locked away exactly as they are.",
                fontSize = 14.sp, fontFamily = InterTight, color = pal.tx)
            Spacer(Modifier.height(10.dp))
            Text("Starting fresh keeps the same account and the same username. You will set a new password and get a brand-new journal — new chats work exactly as before.",
                fontSize = 14.sp, fontFamily = InterTight, color = pal.sub)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(note, fontSize = 13.sp, color = pal.danger, fontFamily = InterTight)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (busy) "Starting…" else "Start fresh with a new journal", enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
            Text("@", color = pal.sub, fontSize = 16.sp, fontFamily = InterTight,
                modifier = Modifier.padding(start = 18.dp))
            BasicTextField(
                value = username,
                onValueChange = { raw ->
                    username = raw.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20)
                },
                textStyle = TextStyle(fontSize = 16.sp, fontFamily = InterTight, color = pal.tx),
                cursorBrush = SolidColor(pal.tx),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 18.dp),
                decorationBox = { inner ->
                    if (username.isEmpty()) Text("username", fontSize = 16.sp, fontFamily = InterTight, color = pal.sub)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 13.sp, color = if (available) pal.ac else pal.sub, fontFamily = InterTight)
        Spacer(Modifier.height(4.dp))
        Text("Friends use your Tiku ID to find you.", fontSize = 13.sp, color = pal.sub, fontFamily = InterTight)
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
                        AppState.go(Screen.Home)
                        AppState.toast("Welcome to Tiku")
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


// bitmap -> Compose ImageBitmap helper
fun android.graphics.Bitmap.asBmp(): androidx.compose.ui.graphics.ImageBitmap = asImageBitmap()
