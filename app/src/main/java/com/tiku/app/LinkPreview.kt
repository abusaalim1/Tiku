package com.tiku.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * ROUND 46 — link detection + Instagram-style link previews for chat
 * text (owner's order). Everything here is CLIENT-DERIVED: a preview
 * is fetched lazily by the phone that renders the bubble, from the
 * URL the message itself carries. Nothing is written to Firestore —
 * no new fields, no schema change (the additive law is untouched),
 * and the peer's client derives its own copy the same way.
 *
 * Privacy scope: the ONLY URLs ever fetched are URLs that appear in
 * a message on screen. Sealed gifts compose no text at all, so no
 * fetch can start from one; view-once surfaces render no text bubble.
 * Preview mode ([Config.PREVIEW_UI_ONLY]) never touches the network —
 * the harness sees the plain styled link only.
 */

// ---------------------------------------------------------------------
// Detection
// ---------------------------------------------------------------------

internal const val LINK_ANNOTATION = "tiku_url"

private val LINK_URL_REGEX = Regex("""https?://[^\s<>"']+""")

/** Trailing characters that belong to the sentence, not the URL. */
private const val LINK_TRAILING = ".,!?:;)]}»…\"'"

private fun linkMatches(text: String): List<Pair<IntRange, String>> {
    if (!text.contains("http")) return emptyList()
    val out = ArrayList<Pair<IntRange, String>>()
    for (m in LINK_URL_REGEX.findAll(text)) {
        val url = m.value.trimEnd { it in LINK_TRAILING }
        if (url.length <= 8) continue // "http://" + at least a host char
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        if (host.isNullOrBlank() || !host.contains('.')) continue
        out.add((m.range.first until m.range.first + url.length) to url)
    }
    return out
}

/** The first http(s) link in [text], or null. Drives the preview card. */
internal fun firstLinkIn(text: String): String? =
    linkMatches(text).firstOrNull()?.second

/** [text] with every detected link styled (accent + bold + underline)
 *  and annotated so a tap can resolve the URL under the finger. */
internal fun linkifyMessageText(text: String, linkColor: Color): AnnotatedString {
    val matches = linkMatches(text)
    if (matches.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for ((range, url) in matches) {
            addStyle(
                SpanStyle(
                    color = linkColor,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                ),
                range.first, range.last + 1,
            )
            addStringAnnotation(LINK_ANNOTATION, url, range.first, range.last + 1)
        }
    }
}

/** Opens [url] in the browser — the house idiom (see Moments.kt). */
internal fun openLinkInBrowser(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ---------------------------------------------------------------------
// Fetching
// ---------------------------------------------------------------------

/** One fetched preview. [image] is the og:image thumbnail (or the
 *  target itself when the link points straight at an image). */
internal data class LinkPreviewData(
    val url: String,
    val host: String,
    val title: String?,
    val description: String?,
    val image: Bitmap?,
)

private const val LINK_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

private fun openLinkConnection(url: URL): HttpURLConnection =
    (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", LINK_UA)
        setRequestProperty("Accept", "text/html,application/xhtml+xml,image/*,*/*")
    }

/** Reads at most [cap] + a byte from [input] — a hostile or endless
 *  body can never grow past the cap. */
private fun readCapped(input: InputStream, cap: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (total <= cap) {
        val n = input.read(buf, 0, min(buf.size, cap + 1 - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

/** Decodes [bytes] downsampled so the long edge stays ≲ 2×[maxDim]. */
private fun decodeDownscaled(bytes: ByteArray, maxDim: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private val META_TAG_REGEX = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
private val META_ATTR_REGEX =
    Regex("""([a-zA-Z_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
private val TITLE_TAG_REGEX =
    Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private fun unescapeHtmlEntities(s: String): String =
    s.replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) {
            runCatching { it.groupValues[1].toInt(16).toChar().toString() }.getOrDefault("")
        }
        .replace(Regex("""&#(\d+);""")) {
            runCatching { it.groupValues[1].toInt().toChar().toString() }.getOrDefault("")
        }
        .trim()

/** property/name → content for every <meta> tag in [html]. */
private fun parseMetaTags(html: String): Map<String, String> {
    val out = HashMap<String, String>()
    for (tag in META_TAG_REGEX.findAll(html)) {
        val attrs = HashMap<String, String>()
        for (a in META_ATTR_REGEX.findAll(tag.value)) {
            attrs[a.groupValues[1].lowercase()] =
                if (a.groupValues[2].isNotEmpty()) a.groupValues[2] else a.groupValues[3]
        }
        val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: continue
        val content = attrs["content"]?.takeIf { it.isNotBlank() } ?: continue
        if (key !in out) out[key] = unescapeHtmlEntities(content)
    }
    return out
}

private fun fetchLinkImage(base: URL, ref: String): Bitmap? {
    var conn: HttpURLConnection? = null
    return try {
        val imgUrl = URL(base, ref)
        if (imgUrl.protocol != "http" && imgUrl.protocol != "https") return null
        conn = openLinkConnection(imgUrl)
        val type = conn.contentType?.lowercase().orEmpty()
        if (!type.startsWith("image/") && type.isNotEmpty()) return null
        val bytes = conn.inputStream.use { readCapped(it, 3 * 1024 * 1024) }
        decodeDownscaled(bytes, 512)
    } catch (e: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}

/** The actual fetch. Returns null for "no preview worth showing" —
 *  callers cache that answer too (negative cache), so a dead link is
 *  not re-fetched on every recomposition. */
private fun fetchLinkPreview(urlStr: String): LinkPreviewData? {
    var conn: HttpURLConnection? = null
    return try {
        val base = URL(urlStr)
        val host = base.host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
            ?: return null
        conn = openLinkConnection(base)
        val type = conn.contentType?.lowercase().orEmpty()
        // A link straight at an image: the image IS the preview.
        if (type.startsWith("image/")) {
            val bytes = conn.inputStream.use { readCapped(it, 4 * 1024 * 1024) }
            val bmp = decodeDownscaled(bytes, 640)
            return if (bmp != null) LinkPreviewData(urlStr, host, null, null, bmp) else null
        }
        val raw = conn.inputStream.use { readCapped(it, 200 * 1024) }
        val charsetName = type.substringAfter("charset=", "").trim()
            .substringBefore(';').trim().ifEmpty { "UTF-8" }
        val charset = runCatching { charset(charsetName) }.getOrDefault(Charsets.UTF_8)
        val html = String(raw, charset)
        val meta = parseMetaTags(html)
        val title = meta["og:title"] ?: meta["twitter:title"]
            ?: TITLE_TAG_REGEX.find(html)?.groupValues?.get(1)?.let { unescapeHtmlEntities(it) }
        val desc = meta["og:description"] ?: meta["twitter:description"]
            ?: meta["description"]
        val imgRef = meta["og:image"] ?: meta["og:image:url"] ?: meta["twitter:image"]
        val img = imgRef?.let { fetchLinkImage(base, it) }
        if (title == null && desc == null && img == null) null
        else LinkPreviewData(urlStr, host, title, desc, img)
    } catch (e: Exception) {
        Log.w("LinkPreview", "fetch failed for $urlStr (${e.javaClass.simpleName})")
        null
    } finally {
        conn?.disconnect()
    }
}

/**
 * Session-scoped preview cache: an access-order LRU capped at 40
 * entries AND ~8 MB of thumbnail bytes, plus an in-flight map so two
 * bubbles showing the same link share one fetch. Failures are cached
 * as null (negative entries) — scrolling never re-hammers a dead URL.
 */
internal object LinkPreviewStore {
    private class Entry(val data: LinkPreviewData?, val bytes: Int)

    private const val MAX_ENTRIES = 40
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val lock = Any()
    private val cache = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private val inFlight = HashMap<String, CompletableDeferred<LinkPreviewData?>>()
    private var cachedBytes = 0

    suspend fun get(url: String): LinkPreviewData? {
        var owner = false
        val deferred: CompletableDeferred<LinkPreviewData?>
        synchronized(lock) {
            cache[url]?.let { return it.data }
            val existing = inFlight[url]
            if (existing != null) {
                deferred = existing
            } else {
                deferred = CompletableDeferred()
                inFlight[url] = deferred
                owner = true
            }
        }
        if (!owner) return deferred.await()
        return try {
            val data = withContext(Dispatchers.IO) { fetchLinkPreview(url) }
            synchronized(lock) {
                putLocked(url, data)
                inFlight.remove(url)
            }
            deferred.complete(data)
            data
        } catch (e: CancellationException) {
            synchronized(lock) { inFlight.remove(url) }
            deferred.cancel(e)
            throw e
        } catch (e: Exception) {
            synchronized(lock) {
                putLocked(url, null)
                inFlight.remove(url)
            }
            deferred.complete(null)
            null
        }
    }

    private fun putLocked(url: String, data: LinkPreviewData?) {
        val bytes = data?.image?.byteCount ?: 0
        cache.put(url, Entry(data, bytes))?.let { cachedBytes -= it.bytes }
        cachedBytes += bytes
        val it = cache.entries.iterator()
        while (it.hasNext() && (cache.size > MAX_ENTRIES || cachedBytes > MAX_BYTES)) {
            cachedBytes -= it.next().value.bytes
            it.remove()
        }
    }
}

// ---------------------------------------------------------------------
// Compose surfaces
// ---------------------------------------------------------------------

private sealed interface LinkFetchState {
    object Loading : LinkFetchState
    data class Done(val data: LinkPreviewData?) : LinkFetchState
}

/**
 * Message text with live links: detected URLs are styled and tapping
 * one opens the browser. The tap detector lives on the text itself —
 * for plain text bubbles the row's own tap handler does nothing, and
 * long-press is NOT consumed here, so the bubble's menu keeps working
 * everywhere, the text included. Without a link this is a plain Text.
 */
@Composable
internal fun RdLinkedText(
    text: String,
    color: Color,
    linkColor: Color,
    fontSize: TextUnit,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier,
) {
    val hasLink = remember(text) { firstLinkIn(text) != null }
    if (!hasLink) {
        Text(text, color = color, fontSize = fontSize, fontFamily = fontFamily, modifier = modifier)
        return
    }
    val annotated = remember(text, linkColor) { linkifyMessageText(text, linkColor) }
    val ctx = LocalContext.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        annotated,
        color = color, fontSize = fontSize, fontFamily = fontFamily,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures(onTap = { pos ->
                val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                annotated.getStringAnnotations(LINK_ANNOTATION, off, off)
                    .firstOrNull()?.let { openLinkInBrowser(ctx, it.item) }
            })
        },
    )
}

/**
 * The IG-style preview card for [url], rendered inside the bubble
 * under the text. Composes NOTHING while loading and when the fetch
 * found no preview — the styled link alone remains, with no spinner
 * and no empty shell. The whole card opens the link on tap; a
 * long-press routes to the bubble's own menu via [onLongPress].
 */
@Composable
internal fun RdLinkPreviewCard(
    url: String,
    cardBg: Color,
    borderColor: Color,
    titleColor: Color,
    descColor: Color,
    hostColor: Color,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Preview mode must be deterministic — no network under the harness.
    if (Config.PREVIEW_UI_ONLY) return
    val state by produceState<LinkFetchState>(LinkFetchState.Loading, url) {
        value = LinkFetchState.Done(LinkPreviewStore.get(url))
    }
    val data = (state as? LinkFetchState.Done)?.data ?: return
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(cardBg).border(1.dp, borderColor, shape)
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { openLinkInBrowser(ctx, url) },
                    onLongPress = { onLongPress() },
                )
            },
    ) {
        data.image?.let { bmp ->
            Image(
                bmp.asImageBitmap(), null,
                Modifier.fillMaxWidth().height(124.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            data.title?.let {
                Text(
                    it, color = titleColor, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            data.description?.let {
                if (data.title != null) Spacer(Modifier.height(2.dp))
                Text(
                    it, color = descColor, fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                data.host.uppercase(), color = hostColor, fontSize = 10.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
