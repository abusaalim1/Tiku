package com.tiku.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

// ============================================================================
// ROUND 41 (owner order, 2026-11-05) — /babeg GAME PLAYER.
//
// A game message's payload is ONE self-contained HTML file written by
// the in-chat AI (Tika). This overlay plays it full-screen for BOTH
// chat partners, each on their own phone: the two WebViews stay in
// sync through ONE tiny Firestore doc per game message
// (chats/{chatId}/gamestate/{msgId} — see FirebaseEngine.listenGameState),
// which the game itself drives through the injected bridge:
//
//   window.TikuGame = {
//     myRole():  "host" | "guest"   (the game's sender is the host)
//     myName():  my display name
//     peerName(): the other player's display name
//     state():   the latest shared state JSON string, or null
//     setState(jsonString): publish a new shared state
//     onState(cb): cb(newStateString) fires on partner changes
//   }
//
// The native half is exposed to the page as "TikuGameNative"; a small
// JS shim (prepended into the document before it loads, and re-asserted
// on page finish) shapes it into the exact window.TikuGame surface the
// generation contract promises the models. NO code is ever shown to the
// users — the chat renders a game card, this overlay renders the game.
//
// WebView discipline mirrors Moments / Watch (the house rules): the
// view is created inside remember{}, lives in exactly ONE AndroidView
// slot, is never re-parented, and is destroyed in DisposableEffect —
// closing the player, or any session lock / quick-exit (which
// uncomposes the whole Tik tree), tears it down completely. Network is
// denied twice over (every navigation refused + http/https/file
// subresource loads answered empty): a game is inline by contract and
// must never reach the network. DOM storage is OFF (the shared doc is
// the only state channel). In PREVIEW mode the overlay shows its chrome
// over a placeholder — no WebView under the harness (Robolectric has
// no real renderer), and the state stays in memory.
// ============================================================================

/** The generated games' palette (the colour law the generator prompt
 *  enforces) — the player's own chrome wears the same cream/ink so the
 *  game sits in a matching frame. */
private val GameCream = Color(0xFFFFF9EE)
private val GameInk = Color(0xFF33261A)

/** The JS shim defining window.TikuGame over the native bridge.
 *  Plain ES5 on purpose (no template literals), and it self-guards so
 *  re-evaluating it after page load is a no-op when the prepended copy
 *  already ran. `last` remembers the most recent delivered state so a
 *  game that registers onState late still gets the current position,
 *  and state() falls back to it when the native side has none yet. */
internal const val TIKU_GAME_SHIM = """
(function () {
  if (window.TikuGame) return;
  var cb = null;
  var last = null;
  var has = false;
  window.__tikuGameDeliver = function (s) {
    last = s;
    has = true;
    if (cb) { try { cb(s); } catch (e) {} }
  };
  window.TikuGame = {
    myRole: function () { return window.TikuGameNative.myRole(); },
    myName: function () { return window.TikuGameNative.myName(); },
    peerName: function () { return window.TikuGameNative.peerName(); },
    state: function () {
      var s = window.TikuGameNative.state();
      if (s === null || s === undefined) return has ? last : null;
      return s;
    },
    setState: function (j) { window.TikuGameNative.setState(j); },
    onState: function (f) {
      cb = f;
      if (has) { try { f(last); } catch (e) {} }
    }
  };
})();
"""

/** Insert the shim script immediately after the <head …> opening tag
 *  (or the <html …> tag when there is no head), so window.TikuGame
 *  exists BEFORE any of the game's own scripts run. A document with
 *  neither tag gets the shim prepended (validation guarantees <html
 *  exists, so this is only a belt-and-braces path). */
internal fun injectGameShim(html: String): String {
    val tag = "<script>" + TIKU_GAME_SHIM + "</script>"
    val lower = html.lowercase()
    val headIdx = lower.indexOf("<head")
    val anchor = if (headIdx >= 0) headIdx else lower.indexOf("<html")
    if (anchor < 0) return tag + html
    val close = html.indexOf('>', anchor)
    if (close < 0) return tag + html
    return html.substring(0, close + 1) + tag + html.substring(close + 1)
}

/** The freshest shared state this phone knows (fed by the Firestore
 *  listener and by this phone's own setState calls). Read by the
 *  bridge's state() from the WebView's JS thread — hence @Volatile. */
private class GameBridgeState {
    @Volatile var latest: String? = null
}

/** The native half of the bridge, exposed to the page as
 *  "TikuGameNative". Its methods run on the WebView's private JS
 *  thread; every field it touches is immutable or @Volatile, and
 *  writes hand off to the (internally synchronised) state writer. */
private class TikuGameNative(
    private val role: String,
    private val myName: String,
    private val peerName: String,
    private val state: GameBridgeState,
    private val onSetState: (String) -> Unit,
) {
    @JavascriptInterface fun myRole(): String = role
    @JavascriptInterface fun myName(): String = myName
    @JavascriptInterface fun peerName(): String = peerName
    @JavascriptInterface fun state(): String? = state.latest
    @JavascriptInterface fun setState(json: String) = onSetState(json)
}

/** setState write pacing (the brief's rule): at most one Firestore
 *  write per 300 ms, coalescing to the LATEST offered state — a fast
 *  sequence of moves lands as one write of the final position, never
 *  a queue of stale boards. */
private class GameStateWriter(
    private val chatId: String,
    private val msgId: String,
) {
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var lastWriteAtMs = 0L
    private var pendingJson: String? = null
    private var flushQueued = false

    fun offer(json: String) {
        synchronized(lock) {
            pendingJson = json
            val wait = MIN_WRITE_GAP_MS - (System.currentTimeMillis() - lastWriteAtMs)
            if (wait <= 0L) {
                flushLocked()
            } else if (!flushQueued) {
                flushQueued = true
                handler.postDelayed({
                    synchronized(lock) {
                        flushQueued = false
                        flushLocked()
                    }
                }, wait)
            }
        }
    }

    private fun flushLocked() {
        val json = pendingJson ?: return
        pendingJson = null
        lastWriteAtMs = System.currentTimeMillis()
        FirebaseEngine.writeGameState(chatId, msgId, json)
    }

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
    }

    companion object { const val MIN_WRITE_GAP_MS = 300L }
}

/** The inert bridge behind the chat card's mini preview: the game's
 *  first frame can ask who it is and render its initial board, but
 *  setState goes nowhere and state() is always null — the card is a
 *  picture of the game, never a third player. The full player uses
 *  [TikuGameNative] instead. */
internal class GamePreviewNative(
    private val role: String,
    private val peerName: String,
) {
    @JavascriptInterface fun myRole(): String = role
    @JavascriptInterface fun myName(): String = "You"
    @JavascriptInterface fun peerName(): String = peerName
    @JavascriptInterface fun state(): String? = null
    @JavascriptInterface fun setState(json: String) = Unit
}

/** Push one shared-state snapshot into the page (main thread, via the
 *  view's own queue). The state string rides as a properly quoted JS
 *  string literal (JSONObject.quote) or the literal null. */
internal fun deliverGameState(wv: WebView, json: String?) {
    val arg = if (json == null) "null" else JSONObject.quote(json)
    wv.post {
        runCatching {
            wv.evaluateJavascript(
                "if (window.__tikuGameDeliver) { window.__tikuGameDeliver($arg); }",
                null,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
internal fun buildGameWebView(
    ctx: Context,
    native: Any,
    onPageReady: () -> Unit,
    onLoadError: (() -> Unit)? = null,
): WebView {
    val wv = WebView(ctx)
    wv.settings.apply {
        javaScriptEnabled = true
        // Games are single-document by contract: no DOM storage (the
        // shared Firestore doc is the ONLY state channel), no local
        // files, no content providers, no mixed content.
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
    }
    wv.addJavascriptInterface(native, "TikuGameNative")
    wv.webViewClient = object : WebViewClient() {
        // Network lockdown: every navigation is refused in-view, and
        // http/https/file subresource loads are answered with an
        // empty body — a contract-abiding game never notices (it is
        // fully inline); a misbehaving one gets nothing.
        override fun shouldOverrideUrlLoading(
            view: WebView?, request: WebResourceRequest?,
        ): Boolean = true

        override fun shouldInterceptRequest(
            view: WebView?, request: WebResourceRequest?,
        ): WebResourceResponse? {
            val scheme = request?.url?.scheme?.lowercase()
            return if (scheme == "http" || scheme == "https" || scheme == "file" ||
                scheme == "content" || scheme == "ws" || scheme == "wss"
            ) {
                WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            } else null
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onPageReady()
        }

        // ROUND 51: a main-frame load failure used to be invisible —
        // the player sat on the game's own loading screen (or a blank
        // page) forever, because the initial state delivery only ever
        // happens from onPageFinished. Surface it: the host turns it
        // into a real error state with a retry. Subresource errors
        // stay ignored (the lockdown answers them empty by design).
        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?,
        ) {
            if (request?.isForMainFrame == true) onLoadError?.invoke()
        }
    }
    return wv
}

internal fun destroyGameWebView(wv: WebView) {
    runCatching { wv.stopLoading() }
    runCatching { wv.loadUrl("about:blank") }
    (wv.parent as? ViewGroup)?.removeView(wv)
    runCatching { wv.destroy() }
}

/** The stateful WebView host for one open game (real mode only). Owns
 *  the bridge, the throttled writer, the Firestore listener and the
 *  view itself; everything is torn down together in DisposableEffect. */
@Composable
private fun GameWebViewHost(
    chatId: String,
    m: TikuMessage,
    role: String,
    myUid: String,
    myName: String,
    peerName: String,
    html: String,
    onError: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val bridgeState = remember(m.id) { GameBridgeState() }
    val writer = remember(m.id) { GameStateWriter(chatId, m.id) }
    val native = remember(m.id) {
        TikuGameNative(role, myName, peerName, bridgeState) { json ->
            // My own move: this phone's copy is authoritative the
            // moment I make it — the holder updates immediately, the
            // Firestore write rides the throttle, and the listener
            // below deliberately skips my own echo (by == my uid).
            bridgeState.latest = json
            writer.offer(json)
        }
    }
    var pageReady by remember(m.id) { mutableStateOf(false) }
    val wvHolder = remember(m.id) { mutableStateOf<WebView?>(null) }
    val webView = remember(m.id) {
        buildGameWebView(ctx, native, onPageReady = {
            // Page ready: re-assert the shim (a no-op when the
            // prepended copy already defined window.TikuGame — e.g.
            // if a model's document shape defeated the prepend), then
            // push the current snapshot once, exactly as the contract
            // promises ("on open, the app hands you the state").
            pageReady = true
            val wv = wvHolder.value ?: return@buildGameWebView
            runCatching { wv.evaluateJavascript(TIKU_GAME_SHIM, null) }
            deliverGameState(wv, bridgeState.latest)
        }, onLoadError = onError).also { wvHolder.value = it }
    }
    LaunchedEffect(m.id, html) {
        webView.loadDataWithBaseURL(null, injectGameShim(html), "text/html", "utf-8", null)
    }
    // ROUND 51 watchdog: if the page never reports finished (a wedged
    // renderer, a document whose load stalls), the game would sit on
    // its own loading screen forever — the contract's initial state
    // delivery rides onPageFinished. 25 s without it is an error the
    // user can retry, not a life sentence.
    LaunchedEffect(m.id, html) {
        kotlinx.coroutines.delay(25_000)
        if (!pageReady) onError()
    }
    DisposableEffect(m.id) {
        val reg = FirebaseEngine.listenGameState(chatId, m.id) { json, by ->
            bridgeState.latest = json
            if (by != myUid) deliverGameState(webView, json)
        }
        onDispose {
            reg.remove()
            writer.dispose()
            destroyGameWebView(webView)
        }
    }
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
}

/** The player's one error face (ROUND 51): a plain sentence and a
 *  working Retry — file failures re-run the fetch, WebView failures
 *  rebuild the view. Never a spinner without an exit. */
@Composable
private fun GameLoadError(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "The game couldn't load.",
            color = GameInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
        Spacer(Modifier.size(6.dp))
        Text(
            "Check your connection and try again.",
            color = GameInk.copy(alpha = 0.55f), fontSize = 12.5.sp, fontFamily = PoppinsFamily,)
        Spacer(Modifier.size(16.dp))
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(GameInk)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onRetry() }
                .padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Try again", color = GameCream, fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = PoppinsFamily)
        }
    }
}

/**
 * The full-screen game player (see the file header). Back closes it.
 * The game's HTML comes from the message's decrypted cache file
 * ([TikuMessage.localPath] — set by sendGame for the sender, by
 * fetchMedia for the receiver); the overlay only ever opens on a
 * LOADED game message (the card gates the tap). ROUND 51: a file
 * that vanished anyway (cache purge) is re-fetched from inside the
 * overlay, and every failure lands on [GameLoadError] with a retry.
 */
@Composable
fun GamePlayerOverlay(
    chatId: String,
    m: TikuMessage,
    myUid: String,
    myName: String,
    peerName: String,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    // The host created the game (its sender), the partner joins as
    // guest. TikuMessage.me IS the engine's uid-vs-senderId verdict;
    // the explicit uid comparison rides alongside for the copies
    // whose senderUid is populated.
    val role = if (m.me || (myUid.isNotEmpty() && myUid == m.senderUid)) "host" else "guest"

    // ROUND 51: the load is a small state machine, never a dead end.
    // A missing/unreadable cache file (the OS purges cacheDir) triggers
    // ONE re-fetch through the same bounded, error-speaking tap-to-load
    // path the card uses, with a short poll for the file it writes; any
    // final failure — file or WebView — lands on an error state with a
    // working Retry button instead of an endless "Loading…".
    val ctx = LocalContext.current
    var html by remember(m.id) { mutableStateOf<String?>(null) }
    var loadFailed by remember(m.id) { mutableStateOf(false) }
    var fileAttempt by remember(m.id) { mutableStateOf(0) }
    var webFailed by remember(m.id) { mutableStateOf(false) }
    var webAttempt by remember(m.id) { mutableStateOf(0) }
    LaunchedEffect(m.id, fileAttempt) {
        loadFailed = false
        val candidates = listOfNotNull(
            m.localPath,
            File(ctx.cacheDir, "dl_${m.id}.html").absolutePath,
        )
        suspend fun readAny(): String? = withContext(Dispatchers.IO) {
            candidates.firstNotNullOfOrNull { p ->
                runCatching { File(p).readText(Charsets.UTF_8) }.getOrNull()
            }
        }
        var loaded = readAny()
        if (loaded == null && !Config.PREVIEW_UI_ONLY) {
            AppState.repo.fetchMedia(chatId, m.id)
            for (i in 0 until 20) {
                kotlinx.coroutines.delay(1_000)
                loaded = readAny()
                if (loaded != null) break
            }
        }
        html = loaded
        if (loaded == null) loadFailed = true
    }

    Column(Modifier.fillMaxSize().background(GameCream)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiGlyph("🎮", 24.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.gameTitle.ifBlank { "Game" },
                    color = GameInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = InterTight,)
                Text(
                    if (role == "host") "You created this game · 2-player"
                    else "2-player game · you joined",
                    color = GameInk.copy(alpha = 0.55f), fontSize = 12.sp, fontFamily = PoppinsFamily,)
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(GameInk.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, Modifier.size(20.dp), tint = GameInk)
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                Config.PREVIEW_UI_ONLY -> {
                    // House rule (Moments/Watch): no WebView under the
                    // screenshot harness — chrome over a placeholder.
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmojiGlyph("🎮", 44.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "Game preview",
                            color = GameInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterTight,)
                        Text(
                            "In the real app the game plays here — both players join from their own phones.",
                            color = GameInk.copy(alpha = 0.55f), fontSize = 12.sp, fontFamily = PoppinsFamily,)
                    }
                }
                webFailed -> GameLoadError(
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { webFailed = false; webAttempt++ },
                )
                html != null -> androidx.compose.runtime.key(webAttempt) {
                    GameWebViewHost(
                        chatId = chatId, m = m, role = role, myUid = myUid,
                        myName = myName, peerName = peerName, html = html!!,
                        onError = { webFailed = true },
                    )
                }
                loadFailed -> GameLoadError(
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { fileAttempt++ },
                )
                else -> Text(
                    "Loading the game…",
                    color = GameInk.copy(alpha = 0.6f), fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp), fontFamily = PoppinsFamily,)
            }
        }
    }
}
