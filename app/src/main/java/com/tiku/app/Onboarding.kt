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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val n = strengthOf(p1)
    val match = p2.isNotEmpty() && p1 == p2
    val ctx = LocalContext.current
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
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", enabled = p1.length >= 8 && match, modifier = Modifier.fillMaxWidth()) {
            AppState.pendingPassword = p1
            SettingsStore.savePasswordHash(ctx, Crypto.passwordHash(p1))
            AppState.go(Screen.ProfileSetup)
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
    FormScaffold("Welcome back", "Enter your study-journal password to restore your journal on this phone.") {
        PasswordField(pw, { pw = it; error = "" }, "Backup password")
        Spacer(Modifier.height(6.dp))
        Text(
            if (error.isNotEmpty()) error else "Without this password your journal can’t be restored here — nobody can, not even Tikuu.",
            fontSize = 13.sp, color = if (error.isNotEmpty()) TikuTokens.Red else TikuTokens.Muted, fontFamily = Manrope,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(if (busy) "Restoring…" else "Restore my journal", enabled = pw.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
            busy = true
            scope.launch {
                val ok = FirebaseEngine.restoreFromBackup(ctx, pw)
                if (ok) {
                    ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("onboarded", true).apply()
                    FirebaseEngine.start(ctx)
                    FirebaseEngine.loadMyProfile()
                    FirebaseEngine.setPresence(true)
                    AppState.go(Screen.Home)
                } else {
                    busy = false
                    error = "Couldn’t open the journal. Wrong password — recovery is impossible without it."
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
