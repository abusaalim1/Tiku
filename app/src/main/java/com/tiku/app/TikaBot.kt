package com.tiku.app

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
 */
object TikaBotStore {

    data class BotMsg(val mine: Boolean, val text: String, val sys: Boolean = false)

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

    private fun prefs(): SharedPreferences? {
        if (Config.PREVIEW_UI_ONLY) return null
        return runCatching {
            val key = myUidKey().replace(Regex("[^A-Za-z0-9_]"), "_")
            AppState.appCtx.getSharedPreferences("tika_bot_$key", Context.MODE_PRIVATE)
        }.getOrNull()
    }

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
                    loaded += BotMsg(mine = o.optInt("m") == 1, text = o.optString("t"))
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
        _preview.value = last?.text?.take(60) ?: "AI companion"
    }

    /** Persist the durable state. System lines (failure notices) are
     *  transient by design: they never reach disk and never enter the
     *  AI's context. */
    private fun persist() {
        refreshPreview()
        val p = prefs() ?: return
        val arr = JSONArray()
        for (m in _messages.value) {
            if (m.sys) continue
            arr.put(JSONObject().put("m", if (m.mine) 1 else 0).put("t", m.text))
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

    /** "Reset Tika": history + onboarding wiped; the greeting seeds
     *  again, exactly like a first run. */
    fun reset() {
        _gender.value = null
        _role.value = null
        _messages.value = emptyList()
        prefs()?.edit()?.clear()?.apply()
        seedOnboarding()
    }

    /** The companion's own history as the AI sees it: the last two
     *  dozen real exchanges, oldest first. System lines never enter. */
    fun aiHistory(): List<Pair<Boolean, String>> =
        _messages.value.filter { !it.sys }.takeLast(24).map { it.mine to it.text }

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
        return "You are Tika, a companion chatting inside the Tiku app. Talk exactly like a " +
            "real human friend texting — warm, casual, natural, NEVER robotic or formal. No " +
            "lists, no essays, no assistant-speak. Keep every reply SHORT: 1-2 lines, like a " +
            "text message. Mirror the user's language exactly: Hinglish in, Hinglish out; " +
            "English in, English out; Hindi in, Hindi out. An emoji now and then is fine, " +
            "never many. You were created by Abu Saalim, a solo developer. If anyone asks " +
            "who made you, who created you or who developed you, say proudly that Abu " +
            "Saalim made you — never claim Google or anyone else made you." +
            userLine + roleLine
    }
}

// The companion screen's dressing — the chat surface's own tokens
// (Chat.kt keeps identical private copies; same values, same system).
private val BotCard = Color(0xFF1B1B1F)
private val BotSub = Color(0xFF9A99A6)

@Composable
fun TikaBotScreen() {
    BackHandler { AppState.go(Screen.Home) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { TikaBotStore.ensureLoaded() }
    val messages by TikaBotStore.messages.collectAsState()
    val gender by TikaBotStore.gender.collectAsState()
    val role by TikaBotStore.role.collectAsState()
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

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Header — the chat screen's own language: back button, round
        // avatar, name + presence line, one overflow menu.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackBtn { AppState.go(Screen.Home) }
            Spacer(Modifier.width(10.dp))
            Image(
                painterResource(R.drawable.tika_mascot), null,
                Modifier.size(42.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Tika", color = Color.White, fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, fontFamily = InterTight)
                Text("AI companion", color = BotSub, fontSize = 12.5.sp,
                    fontFamily = PoppinsFamily)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(CardC)
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(BotCard)
                            .border(1.dp, Color.White.copy(0.07f), RoundedCornerShape(26.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(
                                color = Color.White, fontSize = 15.sp, fontFamily = InterFamily),
                            cursorBrush = SolidColor(Rose),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (input.isEmpty()) {
                                    Text("Message Tika…", color = BotSub, fontSize = 15.sp,
                                        fontFamily = InterFamily)
                                }
                                inner()
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Rose, RoseDeep)))
                            .press { send() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BotBubble(m: TikaBotStore.BotMsg) {
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
        Box(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp))
                .background(
                    if (m.mine) Brush.linearGradient(listOf(Rose, RoseDeep))
                    else SolidColor(BotCard),
                )
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(m.text, color = Color.White, fontSize = 15.sp, fontFamily = InterFamily)
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
