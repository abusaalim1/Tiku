package com.tiku.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * THE STUDY FACADE — the app's default surface.
 *
 * A genuine offline Class 11 study app in the same peach/cream design language:
 * working focus timer, real CBSE chapter notes + practice questions, daily
 * questions, revision cards and local notes. Everything here is local-only.
 * Home stays minimal by design: greeting, tools row, focus card, subjects,
 * Question of the Day — everything else lives on its own sub-page.
 *
 * Zero-trace rule: no messaging vocabulary appears anywhere on this surface.
 */

// ---------- shared facade bits ----------

@Composable
private fun FacadeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(TikuTokens.Card)
            .border(1.dp, TikuTokens.Line, RoundedCornerShape(24.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onClick() } else Modifier,
            )
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun FacadeBackRow(title: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(TikuTokens.Glass)
                .border(1.dp, TikuTokens.Line, CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() },
            contentAlignment = Alignment.Center,
        ) { Text("‹", fontSize = 24.sp, color = TikuTokens.Ink, fontFamily = SerifRegular) }
        Spacer(Modifier.width(12.dp))
        SerifTitle(title, 26.sp)
    }
}

@Composable
private fun SrcTag(src: String?) {
    if (src.isNullOrEmpty()) return
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(TikuTokens.Mint)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(src, fontSize = 11.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = TikuTokens.Muted)
    }
}

// ---------- FACADE HOME (minimal by design) ----------

@Composable
fun FacadeHome() {
    val ctx = LocalContext.current
    val settings = tikuSettings().value
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(AppState.screen) { refresh++ }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            // greeting header
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
            Column(Modifier.padding(top = 26.dp)) {
                SerifTitle(greeting, 32.sp)
                Spacer(Modifier.height(4.dp))
                Text("CBSE Class 11 · let's make today count.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
            }
        }

        item {
            // study tools row — the last tile is the single discreet entry point
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile("📝", "Notes", Modifier.weight(1f)) { AppState.go(Screen.StudyNotes) }
                ToolTile("⏱", "Timer", Modifier.weight(1f)) { AppState.go(Screen.StudyTimer) }
                ToolTile("∑", "Formulas", Modifier.weight(1f)) { AppState.go(Screen.StudyFormulas) }
                ToolTile("📓", "Tik", Modifier.weight(1f), serif = true) { SecretEntry.openGate() }
            }
        }

        item {
            // focus timer card (tap -> full timer)
            val mins = StudyStore.focusMinutesToday(ctx)
            val streak = StudyStore.streak(ctx)
            FacadeCard(Modifier.fillMaxWidth(), onClick = { AppState.go(Screen.StudyTimer) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        val prog = (mins.coerceAtMost(60) / 60f)
                        Canvas(Modifier.size(84.dp)) {
                            drawArc(
                                color = TikuTokens.Line, startAngle = -90f, sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                                size = Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                            )
                            drawArc(
                                color = settings.accent, startAngle = -90f, sweepAngle = 360f * prog,
                                useCenter = false,
                                topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                                size = Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$mins", fontSize = 22.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                            Text("min today", fontSize = 10.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Focus timer", fontSize = 17.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (streak > 0) {
                                EmojiArt("🔥", 15.dp)
                                Spacer(Modifier.width(5.dp))
                                Text("$streak day streak — keep it going.", fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                            } else {
                                Text("Start your first session today.", fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(settings.gradBrush)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) { Text("Start focusing", fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = Color.White) }
                    }
                }
            }
        }

        item {
            // subjects grid — exactly the 4 Class 11 subjects from the pack
            Column {
                SerifTitle("Subjects", 24.sp)
                Spacer(Modifier.height(12.dp))
                val subjects = StudyContent.subjects.ifEmpty { defaultSubjects() }
                subjects.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { subj ->
                            FacadeCard(Modifier.weight(1f), onClick = { AppState.go(Screen.StudySubject(subj.name)) }) {
                                Column {
                                    SubjectArt(subj.name, 30.dp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(subj.name, fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                                    Spacer(Modifier.height(2.dp))
                                    Text(if (subj.chapters.isNotEmpty()) "${subj.chapters.size} chapters" else "Chapter notes",
                                        fontSize = 12.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        item {
            // Question of the Day (tap an option to check; revision cards live one tap deeper)
            QuestionOfTheDayCard()
        }

        item { Spacer(Modifier.height(10.dp)) }
    }
}

// ---------- DESIGN D typography helpers ----------
/**
 * Renders study body text with **highlight** spans (content v2) as Instrument
 * Serif italic + accent + SemiBold inside the Manrope body. The ** markers are
 * stripped. Strings without markers render as plain Manrope.
 */
@Composable
fun HighlightedText(text: String, fontSize: TextUnit, color: Color) {
    val settings = tikuSettings().value
    val annotated = remember(text, settings.accent) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                val start = text.indexOf("**", i)
                if (start < 0) { append(text.substring(i)); break }
                val end = text.indexOf("**", start + 2)
                if (end < 0) { append(text.substring(i)); break }
                append(text.substring(i, start))
                withStyle(SpanStyle(
                    fontFamily = SerifItalic, fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold, color = settings.accent,
                )) { append(text.substring(start + 2, end)) }
                i = end + 2
            }
        }
    }
    Text(annotated, fontSize = fontSize, fontFamily = Manrope, color = color)
}

/** Small "ANSWER" label chip over revealed answers. */
@Composable
fun AnswerChip() {
    val settings = tikuSettings().value
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(settings.accent.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text("ANSWER", fontSize = 10.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            color = settings.accent, letterSpacing = 1.sp)
    }
}

@Composable
private fun ToolTile(glyph: String, label: String, modifier: Modifier = Modifier, serif: Boolean = false, onClick: () -> Unit) {
    // same card look as FacadeCard but tighter padding: 4-across tiles have ~72dp each
    Box(
        modifier.clip(RoundedCornerShape(24.dp)).background(TikuTokens.Card)
            .border(1.dp, TikuTokens.Line, RoundedCornerShape(24.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (EmojiArt.assetName(glyph) != null) EmojiArt(glyph, 26.dp) else Text(glyph, fontSize = 22.sp, fontFamily = if (serif) SerifItalic else Manrope)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.5.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = TikuTokens.Ink,
                maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/**
 * ONE MCQ option renderer for the whole facade (owner round-3 item 5): a
 * VERTICAL list of full-width option rows — each its own rounded card with a
 * circular A/B/C/D letter chip and Manrope Medium text that wraps freely over
 * two lines when long. Never a horizontal pill row, never one compressed line.
 */
@Composable
fun McqOptions(options: List<String>, picked: Int?, correct: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, opt ->
            val isPicked = picked == i
            val isRight = i == correct
            val bg = when {
                picked == null -> TikuTokens.Bubble
                isRight -> Color(0xFFE7F4E4)
                isPicked -> Color(0xFFFBE3DC)
                else -> TikuTokens.Bubble
            }
            val borderC = when {
                picked != null && isRight -> Color(0xFF7CB87C)
                isPicked && !isRight -> Color(0xFFD9603A)
                else -> TikuTokens.Line
            }
            val revealed = picked != null && (isRight || isPicked)
            val chipBg = when {
                picked != null && isRight -> Color(0xFF7CB87C)
                isPicked && !isRight -> Color(0xFFD9603A)
                else -> TikuTokens.Mint
            }
            Row(
                Modifier.fillMaxWidth().testTag("mcq_option_$i")
                    .clip(RoundedCornerShape(16.dp)).background(bg)
                    .border(1.dp, borderC, RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(i) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(chipBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${"ABCD"[i]}", fontSize = 13.sp, fontFamily = Bricolage,
                        fontWeight = FontWeight.Bold,
                        color = if (revealed) Color.White else TikuTokens.Ink,
                    )
                }
                Spacer(Modifier.width(12.dp))
                // wraps to as many lines as the text needs — never compressed
                Text(opt, fontSize = 14.5.sp, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    color = TikuTokens.Ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuestionOfTheDayCard() {
    val settings = tikuSettings().value
    val q = StudyContent.questionOfTheDay()
    FacadeCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("QUESTION OF THE DAY", fontSize = 11.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold, color = settings.accent)
                if (q != null) Text(q.subject, fontSize = 12.sp, fontFamily = Manrope, color = TikuTokens.Muted)
            }
            Spacer(Modifier.height(10.dp))
            if (q == null) {
                // graceful degradation if the pack is unavailable
                Text("Study tip", fontFamily = SerifItalic, fontSize = 20.sp, color = TikuTokens.Ink)
                Spacer(Modifier.height(6.dp))
                Text("Revise in short, focused bursts — 25 minutes of full attention beats an hour of half-attention.",
                    fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Muted)
            } else {
                var picked by remember(q.q) { mutableStateOf<Int?>(null) }
                Column {
                    // stems are Bricolage SemiBold; options are Manrope Medium in pills —
                    // the two roles are visually distinct by design.
                    Text(q.q, fontSize = 17.sp, fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, color = TikuTokens.Ink)
                    Spacer(Modifier.height(12.dp))
                    McqOptions(q.options, picked, q.correct) { picked = it }
                    if (picked != null) {
                        Spacer(Modifier.height(10.dp))
                        AnswerChip()
                        Spacer(Modifier.height(6.dp))
                        Text(if (picked == q.correct) "✓ Correct — " else "✗ Not quite — the correct answer is ${"ABCD"[q.correct]}. ",
                            fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                            color = if (picked == q.correct) Color(0xFF4E7C4E) else settings.accent)
                        Text(q.why, fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted.copy(alpha = 0.85f))
                        SrcTag(q.src)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Revision cards →",
                fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = settings.accent,
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    AppState.go(Screen.StudyRevision)
                })
        }
    }
}

private fun defaultSubjects(): List<StudyContent.Subject> = listOf(
    StudyContent.Subject("Physics", "⚛️", emptyList()),
    StudyContent.Subject("Chemistry", "🧪", emptyList()),
    StudyContent.Subject("Mathematics", "📐", emptyList()),
    StudyContent.Subject("Biology", "🧬", emptyList()),
)

// ---------- PASSWORD GATE (reached ONLY via SecretEntry / the "Tik" tool) ----------

@Composable
fun TikGateScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // BUG-0: the gate distinguishes THREE states — a verifiable password, a
    // missing verifier, and a DAMAGED verifier. Only the first may ever produce
    // the words "Wrong password"; missing/damaged data routes to the Google-
    // gated rescue instead (offered only when this phone still holds the key).
    val storedHash = remember { SettingsStore.passwordHash(ctx) }
    val hasHash = storedHash != null
    val hashDamaged = hasHash && !Crypto.hashWellFormed(storedHash)
    val rescuable = remember {
        !Config.PREVIEW_UI_ONLY && FirebaseEngine.hasLocalIdentity(ctx)
    }

    BackHandler { AppState.go(Screen.Facade) }
    FormScaffold("Your journal is locked", "Enter your password to open your study journal.") {
        when {
            hasHash && !hashDamaged -> {
                PasswordField(pw, { pw = it; error = "" }, "Password")
                Spacer(Modifier.height(6.dp))
                Text(error, fontSize = 13.sp, color = TikuTokens.Red, fontFamily = Manrope)
                Spacer(Modifier.weight(1f))
                PrimaryButton(if (busy) "Opening…" else "Open journal", enabled = pw.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                    busy = true
                    scope.launch {
                        val hash = SettingsStore.passwordHash(ctx)
                        val ok = hash != null && Crypto.hashWellFormed(hash) &&
                            withContext(Dispatchers.Default) { Crypto.passwordVerify(pw, hash) }
                        if (ok) {
                            // first unlock in preview = run onboarding before the home
                            val onboarded = ctx.getSharedPreferences("tiku_prefs", android.content.Context.MODE_PRIVATE)
                                .getBoolean("onboarded", false)
                            if (Config.PREVIEW_UI_ONLY && !onboarded) {
                                ChatSession.unlocked = true
                                AppState.go(Screen.Welcome)
                            } else {
                                ChatSession.unlockFlow(ctx)
                            }
                        } else {
                            error = "Wrong password — try again."
                        }
                        busy = false
                    }
                }
            }
            rescuable -> {
                Text(
                    if (hashDamaged) "Your saved lock data on this phone looks damaged, so your password can't be checked here."
                    else "This phone still holds your journal key, but its password record is missing.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                Spacer(Modifier.height(8.dp))
                Text("You can reset the password with the Google account this journal was created with — your notes and chats stay intact.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Reset password with Google", modifier = Modifier.fillMaxWidth()) {
                    AppState.go(Screen.PasswordReset)
                }
            }
            else -> {
                Text("Set up your journal in a minute — notes, formulas and daily questions, all yours.",
                    fontSize = 14.sp, color = TikuTokens.Muted, fontFamily = Manrope)
                Spacer(Modifier.weight(1f))
                PrimaryButton("Get started", onClick = { ChatSession.unlocked = true; AppState.go(Screen.Welcome) })
            }
        }
    }
}

// ---------- FOCUS TIMER ----------

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    val settings = tikuSettings().value
    var preset by remember { mutableIntStateOf(25) }
    var totalSec by remember { mutableIntStateOf(25 * 60) }
    var left by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (left > 0) { delay(1000); left-- }
        if (left == 0 && !finished) {
            finished = true
            running = false
            StudyStore.addFocusMinutes(ctx, totalSec / 60)
            AppState.toast("Focus session complete")
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FacadeBackRow("Focus timer") { AppState.go(Screen.Facade) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 45, 60).forEach { m ->
                val on = preset == m && !running
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (on) settings.accent else TikuTokens.Card)
                        .border(1.dp, if (on) settings.accent else TikuTokens.Line, RoundedCornerShape(14.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!running) { preset = m; totalSec = m * 60; left = m * 60; finished = false }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) { Text("$m min", fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else TikuTokens.Ink) }
            }
        }
        Spacer(Modifier.height(26.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val prog = if (totalSec > 0) left.toFloat() / totalSec else 0f
            Canvas(Modifier.size(240.dp)) {
                drawArc(
                    color = TikuTokens.Line, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                    size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = settings.accent, startAngle = -90f, sweepAngle = 360f * prog, useCenter = false,
                    topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                    size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val mm = left / 60; val ss = left % 60
                Text("%02d:%02d".format(mm, ss), fontSize = 52.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                Text(if (running) "Stay with it…" else if (left == totalSec) "Ready when you are" else "Paused",
                    fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(if (running) "Pause" else if (finished) "Done ✓" else "Start", enabled = true, modifier = Modifier.weight(1f)) {
                if (finished) AppState.go(Screen.Facade) else running = !running
            }
            PrimaryButton("Reset", enabled = !running, modifier = Modifier.weight(1f)) {
                left = totalSec; finished = false
            }
        }
        if (!running && left < totalSec && !finished) {
            Spacer(Modifier.height(12.dp))
            Text("Finish session early (count ${(totalSec - left) / 60} min)",
                fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = settings.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        StudyStore.addFocusMinutes(ctx, (totalSec - left) / 60)
                        AppState.go(Screen.Facade)
                    })
        }
        Spacer(Modifier.height(16.dp))
        Text("Focused today: ${StudyStore.focusMinutesToday(ctx)} min · Streak: ${StudyStore.streak(ctx)} days",
            fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

// ---------- NOTES (local only) ----------

@Composable
fun NotesScreen() {
    val ctx = LocalContext.current
    val settings = tikuSettings().value
    var version by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<StudyStore.Note?>(null) }
    var creating by remember { mutableStateOf(false) }
    val subjects = StudyContent.subjects.map { it.name }.ifEmpty { listOf("Physics", "Chemistry", "Mathematics", "Biology") }

    if (creating || editing != null) {
        NoteEditor(
            initial = editing, subjects = subjects,
            onClose = { creating = false; editing = null },
            onSave = { n -> StudyStore.saveNote(ctx, n); creating = false; editing = null; version++ },
            onDelete = { id -> StudyStore.deleteNote(ctx, id); creating = false; editing = null; version++ },
        )
        return
    }

    BackHandler { AppState.go(Screen.Facade) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FacadeBackRow("My notes") { AppState.go(Screen.Facade) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<String?>(null).plus(subjects).forEach { subj ->
                val on = filter == subj
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (on) settings.accent else TikuTokens.Card)
                        .border(1.dp, if (on) settings.accent else TikuTokens.Line, RoundedCornerShape(14.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { filter = subj }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) { Text(subj ?: "All", fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else TikuTokens.Ink) }
            }
        }
        Spacer(Modifier.height(14.dp))
        val notes = StudyStore.notes(ctx).filter { filter == null || it.subject == filter }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (notes.isEmpty()) {
                item {
                    Text("No notes yet. Tap + New note to write your first one.",
                        fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Muted,
                        modifier = Modifier.padding(top = 10.dp))
                }
            }
            items(notes, key = { it.id }) { n ->
                FacadeCard(Modifier.fillMaxWidth(), onClick = { editing = n }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(n.title.ifEmpty { "Untitled" }, fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                            Text(n.subject, fontSize = 11.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = settings.accent)
                        }
                        if (n.body.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(n.body.take(140), fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomEnd) {
        PrimaryButton("+ New note", modifier = Modifier) { creating = true }
    }
}

@Composable
private fun NoteEditor(
    initial: StudyStore.Note?,
    subjects: List<String>,
    onClose: () -> Unit,
    onSave: (StudyStore.Note) -> Unit,
    onDelete: (String) -> Unit,
) {
    val settings = tikuSettings().value
    var subject by remember { mutableStateOf(initial?.subject ?: subjects.firstOrNull() ?: "Physics") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FacadeBackRow(if (initial == null) "New note" else "Edit note") { onClose() }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { subj ->
                val on = subject == subj
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (on) settings.accent else TikuTokens.Card)
                        .border(1.dp, if (on) settings.accent else TikuTokens.Line, RoundedCornerShape(14.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { subject = subj }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) { Text(subj, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else TikuTokens.Ink) }
            }
        }
        Spacer(Modifier.height(14.dp))
        androidx.compose.material3.OutlinedTextField(
            value = title, onValueChange = { title = it },
            placeholder = { Text("Title", fontFamily = Manrope, color = TikuTokens.Muted) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TikuTokens.Ink),
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedTextField(
            value = body, onValueChange = { body = it },
            placeholder = { Text("Write your note…", fontFamily = Manrope, color = TikuTokens.Muted) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 15.sp, color = TikuTokens.Ink),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (initial != null) {
                PrimaryButton("Delete", modifier = Modifier.weight(1f)) { onDelete(initial.id) }
            }
            PrimaryButton("Save note", enabled = title.isNotEmpty() || body.isNotEmpty(), modifier = Modifier.weight(1f)) {
                onSave(
                    if (initial == null) StudyStore.Note(subject = subject, title = title, body = body)
                    else initial.copy(subject = subject, title = title, body = body),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- SUBJECT + CHAPTER ----------

@Composable
fun SubjectScreen(name: String) {
    val subj = StudyContent.subject(name)
    BackHandler { AppState.go(Screen.Facade) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FacadeBackRow(name) { AppState.go(Screen.Facade) }
        Spacer(Modifier.height(8.dp))
        // subject hero (item 7): one of the 4 subject heroes from the art
        // manifest — shown only when the manifest actually lists this subject
        val hero = StudyArt.subjectArt(name)
        if (hero != null && subj != null && subj.chapters.isNotEmpty()) {
            AsyncImage(
                model = StudyArt.assetModel(hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(16.dp))
        }
        if (subj == null || subj.chapters.isEmpty()) {
            Text("Chapter notes for $name arrive with the content update. Meanwhile, keep your own notes in My notes.",
                fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Muted)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(subj.chapters.indices.toList(), key = { it }) { i ->
                    val ch = subj.chapters[i]
                    FacadeCard(Modifier.fillMaxWidth(), onClick = { AppState.go(Screen.StudyChapter(name, i)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // rows for chapters WITH art get a thumbnail; the
                            // rest stay plain numbered rows (item 7 final scope)
                            val thumb = StudyArt.chapterArt(name, ch.title)
                            if (thumb != null) {
                                AsyncImage(
                                    model = StudyArt.assetModel(thumb),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
                                )
                            } else {
                                Text("${i + 1}", fontSize = 15.sp, fontFamily = SerifItalic, color = TikuTokens.Muted)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(ch.title, fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                                Spacer(Modifier.height(2.dp))
                                Text("${ch.points.size} key points · ${ch.questions.size} practice questions" +
                                    if (ch.mcqs.isNotEmpty()) " · ${ch.mcqs.size} MCQs" else "",
                                    fontSize = 12.sp, fontFamily = Manrope, color = TikuTokens.Muted)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * One key point as a designed note card (owner round-3 item 7): soft card
 * background, a small numbered gradient marker; formula-heavy points
 * (containing =, ^ or √) get an inset "formula strip"; **term** highlights
 * keep rendering inside both.
 */
@Composable
private fun PointCard(num: Int, point: String) {
    val settings = tikuSettings().value
    val formulaHeavy = point.contains('=') || point.contains('^') || point.contains('√')
    FacadeCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(settings.gradBrush),
                contentAlignment = Alignment.Center,
            ) {
                Text("$num", fontSize = 11.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            if (formulaHeavy) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(TikuTokens.BgTop)
                        .border(1.dp, TikuTokens.Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { HighlightedText(point, 14.sp, TikuTokens.Ink) }
            } else {
                Box(Modifier.weight(1f)) { HighlightedText(point, 14.sp, TikuTokens.Ink) }
            }
        }
    }
}

@Composable
fun ChapterScreen(subjectName: String, index: Int, revealFirst: Boolean = false) {
    val chapter = StudyContent.subject(subjectName)?.chapters?.getOrNull(index)
    BackHandler { AppState.go(Screen.StudySubject(subjectName)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FacadeBackRow(chapter?.title ?: subjectName) { AppState.go(Screen.StudySubject(subjectName)) }
        Spacer(Modifier.height(6.dp))
        if (chapter == null) {
            Text("This chapter is not available yet.", fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Muted)
            return@Column
        }
        // chapter hero (item 7): banner below the title, ONLY for chapters with
        // art in the manifest (final scope: Physics 1–10 + Biology 1–10);
        // no hero and no fallback otherwise — the cards stay clean
        val hero = StudyArt.chapterArt(subjectName, chapter.title)
        if (hero != null) {
            AsyncImage(
                model = StudyArt.assetModel(hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(14.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column {
                    Text("Key points", fontFamily = SerifItalic, fontSize = 21.sp, color = TikuTokens.Ink)
                    Spacer(Modifier.height(10.dp))
                    chapter.points.forEachIndexed { pi, pt -> PointCard(pi + 1, pt) }
                }
            }
            item {
                Column {
                    Text("Practice questions", fontFamily = SerifItalic, fontSize = 21.sp, color = TikuTokens.Ink)
                    Spacer(Modifier.height(10.dp))
                    chapter.questions.forEachIndexed { qi, qa -> PracticeQuestion(qa, initiallyRevealed = revealFirst && qi == 0) }
                }
            }
            if (chapter.mcqs.isNotEmpty()) {
                item {
                    Column {
                        Text("MCQs", fontFamily = SerifItalic, fontSize = 21.sp, color = TikuTokens.Ink)
                        Spacer(Modifier.height(10.dp))
                        chapter.mcqs.forEach { mq -> ChapterMcq(mq) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PracticeQuestion(qa: StudyContent.QA, initiallyRevealed: Boolean = false) {
    val settings = tikuSettings().value
    var open by remember(qa.q) { mutableStateOf(initiallyRevealed) }
    FacadeCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column {
            // question stem: Bricolage SemiBold, ink
            Text(qa.q, fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, color = TikuTokens.Ink)
            Spacer(Modifier.height(8.dp))
            if (!open) {
                Text("Tap to reveal answer", fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = settings.accent,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true })
            } else {
                AnswerChip()
                Spacer(Modifier.height(6.dp))
                Text(qa.a, fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Ink.copy(alpha = 0.85f))
                SrcTag(qa.src)
            }
        }
    }
}

/** Per-chapter MCQ (content v2) — same D typography as Question of the Day. */
@Composable
fun ChapterMcq(mq: StudyContent.DailyQ) {
    val settings = tikuSettings().value
    var picked by remember(mq.q) { mutableStateOf<Int?>(null) }
    FacadeCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column {
            Text(mq.q, fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, color = TikuTokens.Ink)
            Spacer(Modifier.height(10.dp))
            McqOptions(mq.options, picked, mq.correct) { picked = it }
            if (picked != null) {
                Spacer(Modifier.height(10.dp))
                AnswerChip()
                Spacer(Modifier.height(6.dp))
                Text(if (picked == mq.correct) "✓ Correct" else "✗ Not quite — the correct answer is ${"ABCD"[mq.correct]}",
                    fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    color = if (picked == mq.correct) Color(0xFF4E7C4E) else settings.accent)
                if (mq.why.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(mq.why, fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted.copy(alpha = 0.85f))
                }
                SrcTag(mq.src)
            }
        }
    }
}

// ---------- FORMULAS (pack chapter points, per subject) ----------

@Composable
fun FormulasScreen() {
    var subject by remember { mutableStateOf<String?>(null) }
    var chapterIdx by remember { mutableStateOf<Int?>(null) }
    val cur = subject?.let { StudyContent.subject(it) }
    BackHandler {
        when {
            chapterIdx != null -> chapterIdx = null
            subject != null -> subject = null
            else -> AppState.go(Screen.Facade)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FacadeBackRow(
            when {
                chapterIdx != null && cur != null -> cur.chapters.getOrNull(chapterIdx!!)?.title ?: "Formulas"
                subject != null -> subject!!
                else -> "Formulas & key points"
            },
        ) {
            when {
                chapterIdx != null -> chapterIdx = null
                subject != null -> subject = null
                else -> AppState.go(Screen.Facade)
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            cur != null && chapterIdx != null -> {
                val ch = cur.chapters.getOrNull(chapterIdx!!)
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Column {
                            ch?.points?.forEach { pt ->
                                Row(Modifier.padding(vertical = 5.dp)) {
                                    Text("• ", fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Ink)
                                    Box(Modifier.weight(1f)) { HighlightedText(pt, 14.sp, TikuTokens.Ink) }
                                }
                            }
                        }
                    }
                }
            }
            cur != null -> {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(cur.chapters.indices.toList(), key = { it }) { i ->
                        FacadeCard(Modifier.fillMaxWidth(), onClick = { chapterIdx = i }) {
                            Text(cur.chapters[i].title, fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
            else -> {
                val subjects = StudyContent.subjects.ifEmpty { defaultSubjects() }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(subjects, key = { it.name }) { s ->
                        FacadeCard(Modifier.fillMaxWidth(), onClick = { subject = s.name }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SubjectArt(s.name, 30.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(s.name, fontSize = 16.sp, fontFamily = Bricolage, fontWeight = FontWeight.Bold, color = TikuTokens.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- REVISION CARDS ----------

@Composable
fun RevisionScreen() {
    val settings = tikuSettings().value
    val cards = StudyContent.flashcards
    var idx by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    BackHandler { AppState.go(Screen.Facade) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FacadeBackRow("Revision cards") { AppState.go(Screen.Facade) }
        Spacer(Modifier.height(14.dp))
        if (cards.isEmpty()) {
            Text("Revision cards arrive with the content update.",
                fontSize = 14.sp, fontFamily = Manrope, color = TikuTokens.Muted)
            return@Column
        }
        val card = cards[idx % cards.size]
        FacadeCard(
            Modifier.fillMaxWidth().height(300.dp),
            onClick = { flipped = !flipped },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (flipped) card.back else card.front,
                    fontFamily = if (flipped) Manrope else SerifItalic,
                    fontSize = if (flipped) 17.sp else 24.sp,
                    color = TikuTokens.Ink, textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (flipped) "Tap the card to flip back" else "Tap the card to reveal",
            fontSize = 12.sp, fontFamily = Manrope, color = TikuTokens.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Prev", fontSize = 15.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = settings.accent,
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    idx = (idx - 1 + cards.size) % cards.size; flipped = false
                })
            Text("${idx % cards.size + 1} / ${cards.size}", fontSize = 13.sp, fontFamily = Manrope, color = TikuTokens.Muted)
            Text("Next ›", fontSize = 15.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = settings.accent,
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    idx = (idx + 1) % cards.size; flipped = false
                })
        }
    }
}
