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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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

/** .form scaffold: 56dp top padding, 24dp sides, h2 serif title + muted sub. */
@Composable
fun FormScaffold(title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp)) {
        SerifTitle(title, 42.sp)
        Spacer(Modifier.height(8.dp))
        Text(sub, color = TikuTokens.Muted, fontSize = 15.sp, fontFamily = Manrope)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
fun PasswordField(value: String, onValue: (String) -> Unit, placeholder: String) {
    var shown by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(TikuTokens.Card)
            .border(1.dp, TikuTokens.Line, RoundedCornerShape(18.dp)),
    ) {
        BasicTextField(
            value = value, onValueChange = onValue,
            textStyle = TextStyle(fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink),
            cursorBrush = SolidColor(TikuTokens.Ink),
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
                if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                inner()
            },
        )
        Box(
            Modifier.size(44.dp).align(Alignment.CenterEnd).padding(end = 6.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { shown = !shown },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, Modifier.size(20.dp), tint = TikuTokens.Muted)
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
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    val n = strengthOf(p1)
    val match = p2.isNotEmpty() && p1 == p2
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    FormScaffold("Lock your study journal", "Set a password only you know — your journal, notes and progress stay yours.") {
        PasswordField(p1, { p1 = it }, "Password")
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).padding(end = if (i < 3) 6.dp else 0.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i < n) TikuTokens.MeterColors[n - 1] else TikuTokens.Line)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Use 8+ characters with a number and a symbol.", fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
        Spacer(Modifier.height(14.dp))
        PasswordField(p2, { p2 = it }, "Confirm password")
        Spacer(Modifier.height(6.dp))
        val mText = if (p2.isEmpty()) "" else if (match) "✓ Passwords match" else "Passwords don’t match yet"
        Text(mText, fontSize = 13.sp, color = if (match) settings.accent else TikuTokens.Muted, fontFamily = Manrope)
        if (saveError.isNotEmpty()) {
            Text(saveError, fontSize = 13.sp, color = TikuTokens.Red, fontFamily = Manrope)
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
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    FormScaffold("Welcome back", "Your journal was found on this account. Enter the password you set when you created it — and everything comes back on this phone: every note and every conversation, exactly as you left it.") {
        PasswordField(pw, { pw = it; error = "" }, "Backup password")
        Spacer(Modifier.height(6.dp))
        Text(
            if (error.isNotEmpty()) error else "Only this password opens this journal — there is no starting over with a new one. A wrong try changes nothing and deletes nothing.",
            fontSize = 13.sp, color = if (error.isNotEmpty()) TikuTokens.Red else TikuTokens.Muted, fontFamily = Manrope,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "Restoring…" else "Restore my journal", enabled = pw.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
            busy = true
            scope.launch {
                // outcomes are DISTINCT (owner Bug 0): a genuinely wrong password
                // is never confused with a missing backup / server refusal.
                when (val r = FirebaseEngine.restoreFromBackup(ctx, pw)) {
                    is FirebaseEngine.RestoreResult.Ok -> {
                        ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("onboarded", true).apply()
                        FirebaseEngine.start(ctx)
                        FirebaseEngine.loadMyProfile()
                        FirebaseEngine.setPresence(true)
                        AppState.go(Screen.Home)
                    }
                    is FirebaseEngine.RestoreResult.WrongPassword -> {
                        busy = false
                        error = "Wrong password — nothing was changed or deleted, and your journal is still saved. Try again with the password you set when you created it."
                    }
                    is FirebaseEngine.RestoreResult.Unavailable -> {
                        busy = false
                        error = "Couldn't restore right now (${r.why}) — check your connection and try again. This is not a password problem."
                    }
                }
            }
        }
    }
}

/**
 * RESCUE (owner Bug 0): this phone still holds the journal key (device-Keystore
 * wrapped, password-independent) but the password verifier is missing/damaged.
 * The user re-authenticates the SAME Google account, then sets a new password —
 * the existing local key is re-wrapped under it and the cloud backup re-uploaded.
 * Never offered while a valid verifier exists, so it can never bypass a password.
 */
@Composable
fun ResetPasswordScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.TikGate) }
    val ctx = LocalContext.current
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
                fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Muted)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(note, fontSize = 13.sp, color = TikuTokens.Red, fontFamily = Manrope)
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
                color = if (note.isNotEmpty()) TikuTokens.Red else TikuTokens.Muted, fontFamily = Manrope)
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

@Composable
fun ProfileSetupScreen() {
    androidx.activity.compose.BackHandler { AppState.go(Screen.Facade) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = tikuSettings().value
    var username by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("Letters, numbers, dots and underscores.") }
    var available by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { AppState.myAvatar = it }
            }
        }
    }

    LaunchedEffect(username) {
        available = false
        if (username.isEmpty()) { hint = "Letters, numbers, dots and underscores."; return@LaunchedEffect }
        delay(450)
        when {
            username.length < 3 -> hint = "At least 3 characters."
            else -> {
                val taken = if (Config.PREVIEW_UI_ONLY) username in listOf("admin", "tikuu")
                    else FirebaseEngine.usernameTaken(username)
                if (taken) hint = "@$username is taken. Try another."
                else { hint = "✓ @$username is available"; available = true }
            }
        }
    }

    FormScaffold("Set up your student profile", "Add a photo and pick your Study ID.") {
        // avatar picker: mint tile, + badge
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                Modifier.size(116.dp)
                    .shadow(18.dp, RoundedCornerShape(40.dp), spotColor = settings.gradBottom)
                    .clip(RoundedCornerShape(40.dp))
                    .background(TikuTokens.Mint)
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
                    .clip(CircleShape).background(TikuTokens.Button),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = TikuTokens.ButtonText, fontSize = 18.sp, fontWeight = FontWeight.Normal, fontFamily = Manrope)
            }
        }
        Spacer(Modifier.height(22.dp))
        // @ field
        Box(
            Modifier.fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TikuTokens.Card)
                .border(1.dp, TikuTokens.Line, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("@", color = TikuTokens.Muted, fontSize = 16.sp, fontFamily = Manrope,
                modifier = Modifier.padding(start = 18.dp))
            BasicTextField(
                value = username,
                onValueChange = { raw ->
                    username = raw.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.take(20)
                },
                textStyle = TextStyle(fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Ink),
                cursorBrush = SolidColor(TikuTokens.Ink),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 18.dp),
                decorationBox = { inner ->
                    if (username.isEmpty()) Text("username", fontSize = 16.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 13.sp, color = if (available) settings.accent else TikuTokens.Muted, fontFamily = Manrope)
        Spacer(Modifier.height(4.dp))
        Text("Friends use your Study ID to share notes with you.", fontSize = 13.sp, color = TikuTokens.Muted, fontFamily = Manrope)
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "Setting up…" else "Continue",
            enabled = available && !busy, modifier = Modifier.fillMaxWidth()) {
            AppState.myUsername = username
            if (Config.PREVIEW_UI_ONLY) {
                ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("onboarded", true).apply()
                AppState.go(Screen.Home)
                AppState.toast("Welcome to Tikuu")
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
                        AppState.toast("Welcome to Tikuu")
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
