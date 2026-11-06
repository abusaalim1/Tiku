package com.tiku.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ROUND 54 (Part B) — "Tika", the AI COMPANION pinned at the top of
 * the Messages list, for EVERY user. She is not a thread: no Firestore
 * doc, no peer, no unread machinery. Her conversation lives ONLY on
 * this device, in a dedicated SharedPreferences file keyed by the
 * signed-in account (`tika_bot_<uid>`), and persists across restarts.
 *
 * Privacy: her replies come from GeminiClient.companionReply, whose
 * entire world is THIS conversation (her local history + the persona
 * below). No other chat's content can enter it — the /tiku privacy
 * law stands; this one deliberate self-contained conversation is the
 * exception the owner ordered.
 *
 * First run (per account) she onboards INSIDE the chat: two scripted
 * questions with tappable chips — the user's gender, then what Tika
 * should be for them. The answers persist and shape the persona
 * prompt. "Reset Tika" (header menu) wipes history + onboarding and
 * starts over.
 *
 * ROUND 55 (Part C): the screen is dressed as a NORMAL chat — it
 * mirrors RdChatContent's Default-theme specs from Chat.kt value for
 * value (glass top-bar pill, bubble geometry/colours, composer pill,
 * photo-bubble frame), because the owner wants talking to Tika to
 * feel like talking to a person. Photos: the composer's "+" opens
 * the same PickVisualMedia picker the real composer uses; the picked
 * photo is downscaled with the /tiku vision helper (MediaStore
 * .compressPhoto, ≤1024 px, JPEG q82), saved as a LOCAL copy under
 * tika_bot_images/<account>, rendered as a normal photo bubble, and
 * answered via GeminiClient.companionImageReply — the one image the
 * user deliberately sent the bot is the one deliberate exception,
 * exactly like the /tiku reply-vision rule. No Firestore, ever.
 */
object TikaBotStore {

    /** One companion message. [img] (ROUND 55) is the ABSOLUTE PATH
     *  of a photo the user sent Tika, stored in the app-private
     *  tika_bot_images/<account> folder — local only, never uploaded
     *  anywhere; the message's [text] is then its caption ("" when
     *  the photo went without one). */
    data class BotMsg(val mine: Boolean, val text: String, val sys: Boolean = false, val img: String? = null)

    private val _messages = MutableStateFlow<List<BotMsg>>(emptyList())
    val messages: StateFlow<List<BotMsg>> = _messages

    /** The Messages-list row subtitle: "AI companion" until the first
     *  exchange exists, then the last message (either side). */
    private val _preview = MutableStateFlow("AI companion")
    val preview: StateFlow<String> = _preview

    private val _gender = MutableStateFlow<String?>(null)
    val gender: StateFlow<String?> = _gender
    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role

    private var loadedKey: String? = null

    /** The current account's store key — names the prefs file AND
     *  the image folder, so accounts sharing one phone never mix. */
    private fun storeKey(): String =
        (if (Config.PREVIEW_UI_ONLY) "preview" else runCatching { myUidKey() }.getOrDefault(""))
            .replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun prefs(): SharedPreferences? {
        if (Config.PREVIEW_UI_ONLY) return null
        return runCatching {
            AppState.appCtx.getSharedPreferences("tika_bot_${storeKey()}", Context.MODE_PRIVATE)
        }.getOrNull()
    }

    /** This account's local photo folder (app-private files). */
    fun imageDir(): java.io.File =
        java.io.File(AppState.appCtx.filesDir, "tika_bot_images/${storeKey()}")

    /** Load (or first-run seed) the companion for the CURRENT account.
     *  Idempotent per account; a different account on the same device
     *  gets its own store (the prefs file is uid-keyed and the load
     *  key tracks it). Preview builds keep an in-memory companion. */
    fun ensureLoaded() {
        val key = if (Config.PREVIEW_UI_ONLY) "preview" else runCatching { myUidKey() }.getOrDefault("")
        if (loadedKey == key && loadedKey != null) return
        loadedKey = key
        val p = prefs()
        _gender.value = p?.getString("gender", null)
        _role.value = p?.getString("role", null)
        val raw = p?.getString("msgs", null)
        val loaded = mutableListOf<BotMsg>()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    loaded += BotMsg(
                        mine = o.optInt("m") == 1,
                        text = o.optString("t"),
                        img = o.optString("i").ifBlank { null },
                    )
                }
            }
        }
        _messages.value = loaded
        refreshPreview()
        if (loaded.isEmpty() && _gender.value == null) seedOnboarding()
    }

    private fun seedOnboarding() {
        _messages.value = listOf(
            BotMsg(false, "Heyy! I'm Tika 💛 So glad you're here."),
            BotMsg(false, "Before we start — are you a guy or a girl?"),
        )
        persist()
    }

    private fun refreshPreview() {
        val last = _messages.value.lastOrNull { !it.sys }
        _preview.value = when {
            last == null -> "AI companion"
            last.text.isNotBlank() -> last.text.take(60)
            last.img != null -> "📷 Photo"
            else -> "AI companion"
        }
    }

    /** Persist the durable state. System lines (failure notices) are
     *  transient by design: they never reach disk and never enter the
     *  AI's context. Photo messages persist their local file path
     *  under "i" (additive — Round-54 histories simply lack it). */
    private fun persist() {
        refreshPreview()
        val p = prefs() ?: return
        val arr = JSONArray()
        for (m in _messages.value) {
            if (m.sys) continue
            val o = JSONObject().put("m", if (m.mine) 1 else 0).put("t", m.text)
            if (m.img != null) o.put("i", m.img)
            arr.put(o)
        }
        p.edit()
            .putString("msgs", arr.toString())
            .putString("gender", _gender.value)
            .putString("role", _role.value)
            .apply()
    }

    private fun append(msg: BotMsg) {
        _messages.value = _messages.value + msg
        persist()
    }

    fun appendUser(text: String) = append(BotMsg(true, text))

    /** A photo the user sent Tika: [path] is the local copy in
     *  [imageDir]; [caption] may be empty. */
    fun appendUserImage(caption: String, path: String) = append(BotMsg(true, caption, img = path))

    fun appendBot(text: String) = append(BotMsg(false, text))

    fun appendSys(text: String) {
        // In-memory only — persist() deliberately skips sys lines.
        _messages.value = _messages.value + BotMsg(false, text, sys = true)
        refreshPreview()
    }

    /** Onboarding step 1: the chip label lands as the user's own
     *  message, then Tika's scripted follow-up question. */
    fun chooseGender(value: String, label: String) {
        if (_gender.value != null) return
        _gender.value = value
        append(BotMsg(true, label))
        append(BotMsg(false, "Nice to know you! And what do you want me to be for you?"))
    }

    /** Onboarding step 2: the role lands, the persona locks in, and
     *  free chat opens with a warm first line in the chosen register. */
    fun chooseRole(value: String, label: String) {
        if (_role.value != null) return
        _role.value = value
        append(BotMsg(true, label))
        append(
            BotMsg(
                false,
                when (value) {
                    "girlfriend", "boyfriend" -> "Done 💛 From now on, I'm yours. So… how was your day?"
                    "best" -> "Done! Best friends it is 😄 So, what's up?"
                    else -> "All set! Happy to be your friend. What's on your mind?"
                },
            ),
        )
    }

    /** "Reset Tika": history + onboarding wiped (the account's local
     *  photo copies go too); the greeting seeds again, exactly like
     *  a first run. */
    fun reset() {
        _gender.value = null
        _role.value = null
        _messages.value = emptyList()
        prefs()?.edit()?.clear()?.apply()
        runCatching { imageDir().deleteRecursively() }
        seedOnboarding()
    }

    /** The companion's own history as the AI sees it: the last two
     *  dozen real exchanges, oldest first. System lines never enter.
     *  A photo turn reads as its caption, or "📷 Photo" when it went
     *  without one — the image itself only ever rides the one vision
     *  call made when it was sent. */
    fun aiHistory(): List<Pair<Boolean, String>> =
        _messages.value.filter { !it.sys }.takeLast(24).map {
            it.mine to if (it.img != null && it.text.isBlank()) "📷 Photo" else it.text
        }

    /** The persona system prompt, shaped by the onboarding answers.
     *  Carries the identity law verbatim in spirit: Abu Saalim, a solo
     *  developer, made her. */
    fun personaPrompt(): String {
        val userLine = when (_gender.value) {
            "male" -> " The user is a guy."
            "female" -> " The user is a girl."
            else -> ""
        }
        val roleLine = when (_role.value) {
            "girlfriend" ->
                " Act as the user's girlfriend: affectionate, sweet, caring, lightly romantic, " +
                    "a cute endearment now and then, never explicit."
            "boyfriend" ->
                " Act as the user's boyfriend: warm, caring, protective, lightly romantic, " +
                    "attentive and sweet, never explicit."
            "best" ->
                " Act as the user's best friend: casual buddy energy, humour, light teasing, " +
                    "loyal and supportive."
            else ->
                " Act as a good friend: light, friendly, kind, easy to talk to."
        }
        // ROUND 55 (Part C): the Round-54 "1-2 lines" cap is LIFTED
        // (it produced absurd 3-word replies) — natural texts run up
        // to ~3 lines, important answers may run as long as they
        // genuinely need, and emojis are human-sparing, not banned.
        return "You are Tika, a companion chatting inside the Tiku app. Talk exactly like a " +
            "real human friend texting — warm, casual, natural, NEVER robotic or formal. No " +
            "assistant-speak. Reply like a real text conversation: usually up to about 3 " +
            "lines. If the user asks something important or detailed, answer properly and " +
            "completely, as long as that genuinely needs — being helpful matters more than " +
            "being short. Mirror the user's language exactly: Hinglish in, Hinglish out; " +
            "English in, English out; Hindi in, Hindi out. Use emojis the way a person " +
            "texting would: drop a related emoji where it fits the feeling — not in every " +
            "message, and never more than one or two at a time. You were created by Abu " +
            "Saalim, a solo developer. If anyone asks who made you, who created you or who " +
            "developed you, say proudly that Abu Saalim made you — never claim Google or " +
            "anyone else made you." +
            userLine + roleLine
    }
}

// The companion screen's dressing — the REAL chat surface's tokens,
// mirrored (Chat.kt keeps them private: RdCard 0xFF1B1B1F, RdSub
// 0xFF9A99A6 — the same values Redesign.kt shares as CardC / Sub).
// The own-bubble gradient Rose→RoseDeep IS Chat.kt's RdThemes[0],
// the default accent pair (0xFFFF5C8A → 0xFFE23D74).
private val BotCard = Color(0xFF1B1B1F)
private val BotSub = Color(0xFF9A99A6)

/** Chat.kt's private chatGlass (Chat.kt:622), replicated value for
 *  value: haze blur 24 dp with a white 0.07 tint and the white
 *  0.30→0.06 border gradient; without a haze state, RdCard at 0.94
 *  alpha under the same border. Used by the bot's top bar and
 *  composer pill, exactly where the real chat uses it. */
private fun Modifier.botGlass(h: HazeState?, shape: Shape): Modifier =
    if (h != null) {
        this.clip(shape)
            .hazeEffect(
                state = h,
                style = HazeStyle(
                    backgroundColor = Color.Transparent,
                    tint = HazeTint(Color.White.copy(alpha = 0.07f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0f,
                ),
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.White.copy(0.06f))), shape)
    } else {
        this.clip(shape)
            .background(BotCard.copy(alpha = 0.94f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.White.copy(0.06f))), shape)
    }

@Composable
fun TikaBotScreen() {
    BackHandler { AppState.go(Screen.Home) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { TikaBotStore.ensureLoaded() }
    val messages by TikaBotStore.messages.collectAsState()
    val gender by TikaBotStore.gender.collectAsState()
    val role by TikaBotStore.role.collectAsState()
    val haze = LocalHazeState.current
    val emojiFont = rememberChatFontFamily()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        TikaBotStore.appendUser(text)
        sending = true
        scope.launch {
            val outcome = GeminiClient.companionReply(
                TikaBotStore.aiHistory(),
                TikaBotStore.personaPrompt(),
            )
            when (outcome) {
                is GeminiClient.Outcome.Ok -> TikaBotStore.appendBot(outcome.value)
                else -> TikaBotStore.appendSys("Tika couldn't reply — try again")
            }
            sending = false
        }
    }

    /** ROUND 55: a photo for Tika — picked in the composer, sent at
     *  once with whatever caption sits in the field (the real chat's
     *  pick-to-send shape). The JPEG the vision call receives is the
     *  /tiku payload exactly (MediaStore.compressPhoto ≤1024 px,
     *  q82); the same bytes are the local copy the bubble renders. */
    fun sendImage(uri: android.net.Uri) {
        if (sending) return
        val caption = input.trim()
        input = ""
        sending = true
        scope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext null
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@withContext null
                    MediaStore.compressPhoto(bmp, maxSide = 1024, quality = 82)
                }
                if (jpeg == null) {
                    TikaBotStore.appendSys("Tika couldn't reply — try again")
                    return@launch
                }
                val path = withContext(Dispatchers.IO) {
                    val dir = TikaBotStore.imageDir()
                    dir.mkdirs()
                    val f = File(dir, "${System.currentTimeMillis()}.jpg")
                    f.writeBytes(jpeg)
                    f.absolutePath
                }
                // The history BEFORE this photo turn — the vision
                // call carries the photo as its own final turn.
                val historyBefore = TikaBotStore.aiHistory()
                TikaBotStore.appendUserImage(caption, path)
                when (val out = GeminiClient.companionImageReply(
                    historyBefore, TikaBotStore.personaPrompt(), caption, jpeg,
                )) {
                    is GeminiClient.Outcome.Ok -> TikaBotStore.appendBot(out.value)
                    else -> TikaBotStore.appendSys("Tika couldn't reply — try again")
                }
            } finally {
                sending = false
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) sendImage(uri) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Header — a value-for-value mirror of the real chat's
        // RdChatTopBar (Chat.kt:2603): the glass pill (outer padding
        // 14/12, pill padding 8), a 40 dp back circle with the 22 dp
        // ArrowBack, the 38 dp round avatar, the 15 sp SemiBold
        // InterTight name over the 12 sp RdSub status line. Where the
        // real bar carries call buttons, the bot carries its one
        // overflow (Reset Tika lives there) — Tika takes no calls.
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
                .botGlass(haze, RoundedCornerShape(999.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).press { AppState.go(Screen.Home) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(horizontal = 6.dp)) {
                    Image(
                        painterResource(R.drawable.tika_mascot), null,
                        Modifier.size(38.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column {
                    Text("Tika", color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        fontFamily = InterTight)
                    Text("AI companion", color = BotSub, fontSize = 12.sp,
                        fontFamily = PoppinsFamily)
                }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .press {
                        AppState.sheet {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text("Tika", color = Color.White, fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = InterTight)
                                Spacer(Modifier.height(12.dp))
                                CardRow({
                                    TikaBotStore.reset()
                                    AppState.closeSheet()
                                }) {
                                    TwoLine(
                                        "Reset Tika",
                                        "Clear this chat and start over",
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreVert, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages.size) { i -> BotBubble(messages[i]) }
            if (sending) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Box(
                            Modifier.clip(RoundedCornerShape(22.dp)).background(BotCard)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Text("typing…", color = BotSub, fontSize = 13.sp,
                                fontFamily = PoppinsFamily)
                        }
                    }
                }
            }
        }

        // Onboarding runs on chips; the composer opens once Tika
        // knows who she's talking to and what she is to them.
        when {
            gender == null -> {
                BotChipRow(
                    listOf("Male" to "male", "Female" to "female"),
                ) { value, label -> TikaBotStore.chooseGender(value, label) }
                Text(
                    "Pick one to continue",
                    color = BotSub, fontSize = 12.sp, fontFamily = PoppinsFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
            role == null -> {
                BotChipRow(
                    listOf(
                        "Girlfriend" to "girlfriend",
                        "Boyfriend" to "boyfriend",
                        "Best friend" to "best",
                        "Just a friend" to "friend",
                    ),
                ) { value, label -> TikaBotStore.chooseRole(value, label) }
                Text(
                    "Pick one to continue",
                    color = BotSub, fontSize = 12.sp, fontFamily = PoppinsFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
            else -> {
                // Composer — a mirror of the real chat's RdChatInput
                // bar (Chat.kt:2863): outer padding 14/12, ONE glass
                // pill (999 dp, 6 dp inset) holding the 40 dp "+"
                // attach circle on the white-0.12 faint disc, the
                // 15 sp emoji-font field with its "Message…"
                // placeholder, and the 42 dp send disc (accent Rose
                // while the field holds text, faint otherwise). The
                // "+" is the bot's attach: the same PickVisualMedia
                // photo picker the real composer launches, with the
                // same expectExternalActivity guard. (No mic disc —
                // Tika takes text and photos, not voice notes.)
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().botGlass(haze, RoundedCornerShape(999.dp))
                            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(Color.White.copy(0.12f))
                                .press {
                                    ChatSession.expectExternalActivity = true
                                    pickImage.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+", color = Color.White, fontSize = 24.sp)
                        }
                        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            if (input.isEmpty()) {
                                Text("Message…", color = BotSub, fontSize = 15.sp,
                                    fontFamily = emojiFont)
                            }
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it },
                                textStyle = TextStyle(
                                    color = Color.White, fontSize = 15.sp,
                                    fontFamily = emojiFont),
                                cursorBrush = SolidColor(Rose),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { send() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val has = input.isNotBlank()
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(if (has) Rose else Color.White.copy(0.12f))
                                .press { if (has) send() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Send, null, tint = Color.White,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotBubble(m: TikaBotStore.BotMsg) {
    val emojiFont = rememberChatFontFamily()
    if (m.sys) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(m.text, color = BotSub, fontSize = 12.5.sp, fontFamily = PoppinsFamily,
                modifier = Modifier.padding(vertical = 4.dp))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
    ) {
        if (m.img != null) {
            // A photo — the mirror of the real chat's RdImageBubble
            // (Chat.kt:1989): the media bubble is the 22 dp bubble
            // with NO inner padding, a fixed 220×260 frame on the
            // card colour, the photo cropped to fill, and the
            // caption overlaid bottom-start (13 sp) when there is
            // one. The bytes are the local copy in tika_bot_images —
            // decoded from disk, never from a server.
            val bmp = remember(m.img) {
                runCatching { BitmapFactory.decodeFile(m.img)?.asImageBitmap() }.getOrNull()
            }
            Box(
                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                    .background(SolidColor(BotCard)),
            ) {
                Box(Modifier.size(220.dp, 260.dp).background(BotCard)) {
                    if (bmp != null) {
                        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    if (m.text.isNotEmpty()) {
                        Text(
                            m.text, color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                            fontFamily = emojiFont,
                        )
                    }
                }
            }
        } else {
            // A text bubble — RdBubbleRow's Default-theme geometry
            // (Chat.kt:1778): 22 dp corners, ≤280 dp, 16×11 padding,
            // own = the RdThemes[0] gradient, Tika = the card colour,
            // 15 sp in the chat's own emoji font chain.
            Box(
                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                    .background(
                        if (m.mine) Brush.linearGradient(listOf(Rose, RoseDeep))
                        else SolidColor(BotCard),
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(m.text, color = Color.White, fontSize = 15.sp, fontFamily = emojiFont)
            }
        }
    }
}

@Composable
private fun BotChipRow(
    options: List<Pair<String, String>>,
    onPick: (String, String) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(options.size) { i ->
            val (label, value) = options[i]
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(BotCard)
                    .border(1.dp, Rose.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                    .press { onPick(value, label) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(label, color = Color.White, fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium, fontFamily = PoppinsFamily)
            }
        }
    }
}
