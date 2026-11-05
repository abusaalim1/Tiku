package com.tiku.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * ROUND 31 — view-once / view-twice media (WhatsApp / Instagram style).
 *
 * The SENDER arms a view allowance when sending a PHOTO or VIDEO: the
 * message doc carries the additive Int field `vo` (1 or 2; absent =
 * unlimited). The RECIPIENT's client enforces it locally: the bubble is
 * a view-once pill (no inline picture), every actual VIEW consumes one
 * open, and when the allowance runs out the bubble flips to "Opened"
 * and the media can no longer be opened or saved.
 *
 * Enforcement is CLIENT-SIDE by design: the encrypted payload pipeline
 * is unchanged (a view-once photo's chunks sit on the server exactly
 * like any photo's — the recipient's app already holds them to render
 * the viewer; the counting below is what makes this build honour the
 * sender's choice). A tampered client could ignore the count; that is
 * the accepted shape of the feature on every platform that ships it
 * this way, and it is recorded honestly in the round report.
 *
 * Counts are PER ACCOUNT: the prefs file name carries the signed-in uid
 * (`tiku_vo_views_<uid>`), the exact scheme of the Round-28 fx played
 * logs — on a shared phone, one account's views never consume another
 * account's allowance. When the allowance is exhausted, the recipient's
 * client also writes the additive `voDone=true` on the message doc (a
 * member write — reactions set the precedent) so the SENDER's bubble
 * can show "Opened".
 *
 * OWNER OVERRIDE: the owner-only Settings toggle "View once media —
 * unlimited for me" lives in a second per-uid prefs file
 * (`tiku_vo_prefs_<uid>`). When ON and the signed-in account is the
 * owner's ([OwnerState.isOwner] — the email check, never a uid),
 * incoming view-once media on this phone never exhausts: opens don't
 * consume, the bubble never expires, and Save stays available. For
 * every other account the toggle does not exist and the bypass can
 * never engage.
 */
object VoStore {

    /** Compose subscription tick: bumped on every consume / toggle so
     *  bubbles and the settings row re-render with fresh counts. */
    var stateVersion by mutableIntStateOf(0)
        private set

    /** The fx logs' uid namespace (Chat.kt, ROUND 28): sanitized,
     *  capped, "anon" when signed out — never a crash, never a leak
     *  into another account's file. */
    private fun uidKey(): String =
        (if (Config.PREVIEW_UI_ONLY) "me" else FirebaseEngine.myUid())
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(64)
            .ifEmpty { "anon" }

    private fun viewPrefs(ctx: Context) =
        ctx.getSharedPreferences("tiku_vo_views_${uidKey()}", Context.MODE_PRIVATE)

    private fun prefPrefs(ctx: Context) =
        ctx.getSharedPreferences("tiku_vo_prefs_${uidKey()}", Context.MODE_PRIVATE)

    /** How many times THIS account has viewed the message so far. */
    fun opensUsed(ctx: Context, msgId: String): Int =
        viewPrefs(ctx).getInt("c_$msgId", 0)

    /** Record one actual view; returns the new total. */
    fun consumeOpen(ctx: Context, msgId: String): Int {
        val p = viewPrefs(ctx)
        val n = p.getInt("c_$msgId", 0) + 1
        p.edit().putInt("c_$msgId", n).apply()
        stateVersion++
        return n
    }

    /** Views left for an incoming view-once message (0 when spent). */
    fun remaining(ctx: Context, m: TikuMessage): Int {
        val limit = m.vo ?: return Int.MAX_VALUE
        return (limit - opensUsed(ctx, m.id)).coerceAtLeast(0)
    }

    /** The raw count check — the allowance is spent. Owner bypass is a
     *  SEPARATE question ([ownerBypass]); surfaces combine the two. */
    fun expired(ctx: Context, m: TikuMessage): Boolean {
        val limit = m.vo ?: return false
        return opensUsed(ctx, m.id) >= limit
    }

    // ------------------------------------------------ owner override

    /** The owner's "unlimited for me" choice (default OFF, persisted
     *  per account in the per-uid prefs file). */
    fun unlimited(ctx: Context): Boolean =
        prefPrefs(ctx).getBoolean("unlimited", false)

    fun setUnlimited(ctx: Context, on: Boolean) {
        prefPrefs(ctx).edit().putBoolean("unlimited", on).apply()
        stateVersion++
    }

    /** True when view-once limits simply do not apply on this phone:
     *  the signed-in account is the owner's AND he flipped his toggle.
     *  Opens then don't consume, bubbles never expire, Save stays. */
    fun ownerBypass(ctx: Context): Boolean =
        OwnerState.isOwner && unlimited(ctx)
}
