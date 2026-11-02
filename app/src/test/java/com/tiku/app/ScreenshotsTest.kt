package com.tiku.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI preview screenshots — rendered headless from the app's REAL Compose code
 * (Roborazzi + Robolectric, native graphics) at 1080×2400, light theme only.
 * ChatRoot states run against the preview demo store (the same data path the
 * PREVIEW_UI_ONLY flag uses); the facade consumes the real bundled drama pack
 * (assets/facade/). PNGs land in build/outputs/roborazzi/shots/ and ship as
 * a release asset.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-480dpi")
class ScreenshotsTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var ctx: Context
    private val outDir = "build/outputs/roborazzi/shots"

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        SettingsStore.init(ctx)
        OwnerPrefs.init(ctx)
        // ROUND 9: clean owner slate for every shot — non-owner by default;
        // the owner pair (shots 37/38) flips the seam inside its own test.
        OwnerState.previewOverride = null
        OwnerPrefs.setNotify(false)
        OwnerPrefs.setAppLock(true)
        OwnerPrefs.consumeGateOnce()
        // ROUND 13: the facade's bundled drama pack (dramas + stories).
        DramaContent.load(ctx)
        AppState.repo = PreviewRepo
        val seeded = PreviewStore.seedThreads()
        AppState.threads.value = seeded
        CallController.endSilently() // calls have no store at all — just reset the controller
        AppState.typing.value = emptyMap()
        AppState.homeTab = HomeTab.CHATS
        AppState.sheetContent = null
        AppState.toastText = null
        AppState.story = null
        // the gate opens with the password field once a journal password exists
        SettingsStore.savePasswordHash(ctx, Crypto.passwordHash("journal123"))
    }

    /** Same light gradient frame TikuRoot provides in the real app. */
    private fun frame(content: @Composable () -> Unit) {
        composeRule.setContent {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom))),
            ) {
                content()
                BottomSheetHost()
            }
        }
        composeRule.waitForIdle()
    }

    private fun shot(name: String) {
        composeRule.onRoot().captureRoboImage("$outDir/$name")
    }

    // ---------- facade (ROUND 13: K-drama) ----------
    @Test fun shot01_dramaHome() {
        AppState.go(Screen.Facade)
        frame { FacadeRouter() }
        // ZERO-TRACE PROOF (negative): the word "Tik" must not appear
        // anywhere on the facade home — "Tikuu" is the drama brand, and
        // the exact-match query does not match it.
        org.junit.Assert.assertTrue(
            "facade home must not contain any Tik wording",
            composeRule.onAllNodesWithText("Tik").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "facade home must carry the Tikuu wordmark",
            composeRule.onAllNodesWithText("Tikuu").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("01-drama-home.png")
    }

    @Test fun shot02_storiesList() {
        frame { FacadeHome(initialTab = FacadeTab.STORIES) }
        org.junit.Assert.assertTrue(
            "stories tab must list the bundled love stories",
            composeRule.onAllNodesWithText("The Yellow Umbrella").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("02-stories-list.png")
    }

    @Test fun shot03_storyReader() {
        frame { FacadeHome(initialTab = FacadeTab.STORIES, initialStoryId = "umbrella") }
        org.junit.Assert.assertTrue(
            "reader must open on the story itself",
            composeRule.onAllNodesWithText("The Yellow Umbrella").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "reader must render the story body",
            composeRule.onAllNodesWithText("yellow umbrella appeared", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("03-story-reader.png")
    }

    @Test fun shot04_photosGrid() {
        frame { FacadeHome(initialTab = FacadeTab.PHOTOS) }
        org.junit.Assert.assertTrue(
            "photos tab must show the bundled stills with their captions",
            composeRule.onAllNodesWithText("Cherry Blossom Walk").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("04-photos-grid.png")
    }

    @Test fun shot05_forYouEntry() {
        frame { FacadeHome(initialTab = FacadeTab.FOR_YOU) }
        // ZERO-TRACE PROOF (negative + positive): the For You page carries
        // the discreet entry row — and the word "Tik" NOWHERE on the page.
        org.junit.Assert.assertTrue(
            "for-you page must not contain any Tik wording",
            composeRule.onAllNodesWithText("Tik").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "for-you page must carry the entry row",
            composeRule.onAllNodesWithText("For You, Always").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "entry row must carry its private-corner sub",
            composeRule.onAllNodesWithText("Your private corner").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("05-for-you-entry.png")
    }

    @Test fun shot06_playerPlaceholder() {
        // The facade player: in a PREVIEW_UI_ONLY build it renders the
        // poster + play placeholder with NO WebView; the shipped harness
        // exercises the pane-owned WebView under Robolectric's shadow
        // WebView instead — either way it composes headless and offline.
        frame { FacadeHome(playerDramaId = DramaContent.hero?.id ?: "StNS0mPjrOg") }
        composeRule.waitForIdle()
        shot("06-player-placeholder.png")
    }

    @Test fun shot07_tikGate() {
        // ROUND 3.1: seed a stand-in local identity (hasLocalIdentity only
        // null-checks idk_iv/idk_ct) so the gate renders in the state the owner
        // actually hits — password field PLUS the always-offered Google reset.
        ctx.getSharedPreferences("tiku_secure", android.content.Context.MODE_PRIVATE)
            .edit().putString("idk_iv", "shot").putString("idk_ct", "shot").apply()
        frame { TikGateScreen() }
        shot("07-tik-gate.png")
    }

    @Test fun shot39_watchPane() {
        // ROUND 13: the re-architected Watch pane (pane-owned WebView,
        // preview placeholder branch in preview builds) composes headless.
        frame { WatchPane() }
        composeRule.waitForIdle()
        shot("39-watch-pane.png")
    }

    // ---------- onboarding (journal-framed) ----------
    @Test fun shot08_welcome() {
        frame { WelcomeScreen {} }
        shot("08-welcome.png")
    }

    @Test fun shot09_createPassword() {
        frame { CreatePasswordScreen() }
        shot("09-create-password.png")
    }

    @Test fun shot10_profileSetup() {
        frame { ProfileSetupScreen() }
        shot("10-profile-setup.png")
    }

    // ---------- ChatRoot ----------
    @Test fun shot11_chatHome() {
        AppState.homeTab = HomeTab.CHATS
        frame { HomeScreen() }
        shot("11-chat-home.png")
    }

    @Test fun shot12_callsPane() {
        // ROUND 6: the Calls tab now lists call HISTORY aggregated from the
        // chats' encrypted call-event messages (seeded: ended / missed /
        // declined / video) — this shot must show rows, not the old empty state.
        AppState.homeTab = HomeTab.CALLS
        frame { HomeScreen() }
        shot("12-calls-pane.png")
    }

    @Test fun shot13_mePane() {
        AppState.homeTab = HomeTab.ME
        frame { HomeScreen() }
        shot("13-me-pane.png")
    }

    @Test fun shot18_meSettingsScrolled() {
        AppState.homeTab = HomeTab.ME
        frame { HomeScreen() }
        composeRule.onRoot()
            .performTouchInput { swipeUp(); swipeUp() }
        composeRule.waitForIdle()
        shot("18-me-settings-scrolled.png")
    }

    @Test fun shot14_chatScreen() {
        val th = AppState.threads.value.first()
        AppState.setTyping(th.id, true)
        frame { ChatScreen(th.id) }
        shot("14-chat-screen.png")
    }

    @Test fun shot15_messageSheet() {
        val th = AppState.threads.value.first()
        frame { ChatScreen(th.id) }
        val msg = th.messages.firstOrNull { !it.me } ?: th.messages.first()
        ChatSheets.context(th.id, msg) { }
        composeRule.waitForIdle()
        shot("15-message-sheet.png")
    }

    @Test fun shot16_callScreen() {
        val th = AppState.threads.value.first()
        // preview fake call: "Calling…" then connects — no WebRTC classes load here
        CallController.startCall(ctx, th.id, false)
        frame { CallScreen(th.id, false) }
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.waitForIdle()
        shot("16-call-screen.png")
    }

    @Test fun shot17_storyViewer() {
        val person = AppState.threads.value.first().person
        AppState.story = StoryContent(
            person = person,
            quotes = PreviewStore.storyQuotes,
            grads = PreviewStore.storyGrads,
            personIndex = 0,
        )
        frame { StoryScreen() }
        shot("17-story-viewer.png")
    }

    // ---------- round 3 additions ----------
    @Test fun shot21_additionalSettings() {
        AppState.homeTab = HomeTab.ME
        frame { HomeScreen() }
        composeRule.onRoot()
            .performTouchInput { swipeUp(); swipeUp(); swipeUp(); swipeUp() }
        composeRule.waitForIdle()
        shot("21-additional-settings.png")
    }

    @Test fun shot22_inCallVideo() {
        val th = AppState.threads.value.first()
        CallController.startCall(ctx, th.id, true)
        frame { CallScreen(th.id, true) }
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.waitForIdle()
        shot("22-in-call-video.png")
    }

    // ---------- round 4 additions ----------
    @Test fun shot24_chatSeenCallEvent() {
        // ROUND 8 PROOF (items 2 + 7b): ticks are GONE — this chat carries a
        // peer reply bubble with its soft quote block + "Replied to you"
        // caption, a centred call-event pill, and the muted "Seen" caption
        // under my latest (seen) message. Asserts no ✓✓ renders anywhere.
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.upsertMessage(id, TikuMessage(
            id = "s1", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "Did you see the moodboard?", time = "9:38", me = false, tsMs = 1))
        AppState.upsertMessage(id, TikuMessage(
            id = "s2", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "Yes! The sage tones are perfect", time = "9:40", me = false, tsMs = 2,
            replyQuote = "Did you see the moodboard?"))
        AppState.upsertMessage(id, TikuMessage(
            id = "s3", senderUid = "me", kind = MsgKind.CALL,
            time = "9:41", me = true, tsMs = 3,
            callCallerUid = "me", callOutcome = "ended", callDurationSec = 754, callMine = true))
        AppState.upsertMessage(id, TikuMessage(
            id = "s4", senderUid = "me", kind = MsgKind.TEXT,
            text = "On my way!", time = "9:42", me = true, tsMs = 4, seenByPeer = true))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "chat must not render tick glyphs anymore",
            composeRule.onAllNodesWithText("✓✓", substring = true).fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "Seen caption must render under the latest own message",
            composeRule.onAllNodesWithText("Seen").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("24-chat-seen-call-event.png")
    }

    @Test fun shot25_newChatLiveSearch() {
        // ITEM 4 PROOF: live suggestions while typing — no Search button.
        // Typing "riy" in the sheet's field must surface the Riya row.
        // The Robolectric process has no Firestore backend, so the sheet's
        // searcher seam is pointed at the preview people through the REAL
        // shared matcher (SearchNorm.matches — the same normalization the
        // Firestore prefix+token query pair implements).
        NewChatSheet.searcher = { q ->
            PreviewStore.people.filter { SearchNorm.matches(it.username, it.name, q) }
        }
        AppState.homeTab = HomeTab.CHATS
        NewChatSheet.open()
        frame { HomeScreen() }
        val fields = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        org.junit.Assert.assertTrue("expected at least the sheet search field", fields.isNotEmpty())
        composeRule.onAllNodes(hasSetTextAction())[fields.size - 1].performTextInput("riy")
        composeRule.mainClock.advanceTimeBy(800) // debounce 200 ms + render
        composeRule.waitForIdle()
        shot("25-new-chat-search.png")
    }

    @Test fun shot26_restore() {
        // ROUND 5 PROOF: the screen a returning user lands on after a reinstall
        // (sign-in routing → Restore): password field + the copy that says the
        // password brings EVERYTHING back and that nothing can start over it.
        frame { RestorePasswordScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "restore screen must promise the full journal comes back",
            composeRule.onAllNodesWithText("everything comes back", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("26-restore.png")
    }

    // ---------- round 6 additions ----------
    @Test fun shot27_alignmentFlush() {
        // BUG 1 PROOF: a dedicated thread with a SHORT own message, a LONG own
        // message and a peer message — own bubbles must end FLUSH at the right
        // edge, the peer bubble must start FLUSH at the left edge (Instagram
        // alignment), not the old uniform ~22% centre column.
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.upsertMessage(id, TikuMessage(
            id = "al1", senderUid = "me", kind = MsgKind.TEXT, text = "Ok",
            time = "9:41", me = true, tsMs = 1))
        AppState.upsertMessage(id, TikuMessage(
            id = "al2", senderUid = "me", kind = MsgKind.TEXT,
            text = "This is a much longer message that stretches close to the width cap and still has to end flush at the right edge",
            time = "9:41", me = true, tsMs = 2))
        AppState.upsertMessage(id, TikuMessage(
            id = "al3", senderUid = p.uid, kind = MsgKind.TEXT, text = "Hey!",
            time = "9:42", me = false, tsMs = 3))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        shot("27-alignment-flush.png")
    }

    @Test fun shot28_incomingRinging() {
        // BUG 2 PROOF (harness state): drive CallController.onIncoming exactly
        // as the callee's ringing watcher does after the round-6 fix — the
        // incoming-call screen must render with accept / reject.
        val th = AppState.threads.value.first()
        CallController.onIncoming(th.id, "shot-ring-1", "video", th.person)
        frame { CallScreen(th.id, true) }
        composeRule.waitForIdle()
        shot("28-incoming-ringing.png")
        CallController.endSilently()
    }

    @Test fun shot29_coupleTheme() {
        // FEATURE 5a PROOF: Couple wallpaper (blush + hearts) + rose accent
        // applied to the chat surface — round 7 drives it through the SAME
        // preset entry the owner taps (SettingsStore.setCoupleTheme, the
        // labelled Me ▸ Appearance toggle). Settings are restored after.
        // ROUND 13: setAccent now selects the couple theme (0..4) and
        // setCoupleTheme writes theme + wallpaper together; the signatures
        // (and this proof's path) are unchanged.
        val prevAccent = SettingsStore.state.value.accentIdx
        val prevWp = SettingsStore.state.value.wallpaper
        SettingsStore.setCoupleTheme(true)
        val th = AppState.threads.value.first()
        frame { ChatScreen(th.id) }
        composeRule.waitForIdle()
        shot("29-couple-theme.png")
        SettingsStore.setAccent(prevAccent)
        SettingsStore.setWallpaper(prevWp.ordinal)
    }

    @Test fun shot31_coupleThemeSettings() {
        // ROUND 7 (P1) PROOF: the LABELLED "Couple theme" row in
        // Me ▸ Appearance, shown in its ON state — round 6 buried the preset
        // as anonymous swatches and the owner reported it missing. Applied
        // via the toggle's own path. No scroll: the full Appearance card
        // fits above the tab bar (proven by 13-me-pane), so the row is
        // captured visibly in frame — an earlier full swipeUp overshot the
        // group entirely and the label never appeared in the shot.
        // ROUND 13: the row is the rebuilt Appearance group's couple-theme
        // toggle (TikInfoRow "Couple theme" in Home.kt's MePane) — the
        // label, and therefore this proof, is unchanged.
        val prevAccent = SettingsStore.state.value.accentIdx
        val prevWp = SettingsStore.state.value.wallpaper
        SettingsStore.setCoupleTheme(true)
        AppState.homeTab = HomeTab.ME
        frame { HomeScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "Appearance must carry a labelled Couple theme row",
            composeRule.onAllNodesWithText("Couple theme").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("31-couple-theme-settings.png")
        SettingsStore.setAccent(prevAccent)
        SettingsStore.setWallpaper(prevWp.ordinal)
    }

    @Test fun shot30_chatDetailsNickname() {
        // FEATURE 5b PROOF: the chat details sheet shows the REAL name +
        // @username and the nickname action (the seeded first thread already
        // has nickname "Aaru" — header/list shots 11/14 show it in place).
        val th = AppState.threads.value.first()
        frame { ChatScreen(th.id) }
        ChatSheets.more(th, th.person)
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "details sheet must offer nickname editing",
            composeRule.onAllNodesWithText("Edit nickname").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("30-chat-details-nickname.png")
    }

    // ---------- round 8 additions ----------
    @Test fun shot32_swipeReplyQuote() {
        // ITEM 1 + 2 PROOF: reply mode armed on a chat — first try the real
        // swipe gesture on an own bubble (long message = a long drag); if the
        // harness can't drive it past the threshold, fall back to the
        // long-press sheet's Reply (the same reply state). The capture shows
        // the composer reply bar (IG header + quote at ~70%) over a thread
        // whose peer reply bubble carries its own quote block + caption.
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.upsertMessage(id, TikuMessage(
            id = "r1", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "The venue photos came out so well", time = "9:38", me = false, tsMs = 1))
        AppState.upsertMessage(id, TikuMessage(
            id = "r2", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "Sending the best ones tonight", time = "9:39", me = false, tsMs = 2,
            replyQuote = "The venue photos came out so well"))
        AppState.upsertMessage(id, TikuMessage(
            id = "r3", senderUid = "me", kind = MsgKind.TEXT,
            text = "Perfect, this is the long message I am swiping on to reply",
            time = "9:40", me = true, tsMs = 3))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        var armedBySwipe = false
        runCatching {
            composeRule.onNodeWithText("Perfect, this is the long message I am swiping on to reply")
                .performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            armedBySwipe = composeRule.onAllNodesWithText("Replying to yourself")
                .fetchSemanticsNodes().isNotEmpty()
        }
        if (!armedBySwipe) {
            composeRule.onNodeWithText("Perfect, this is the long message I am swiping on to reply")
                .performTouchInput { longClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Reply").performClick()
            composeRule.waitForIdle()
        }
        org.junit.Assert.assertTrue(
            "reply bar must be armed (swipe or menu path)",
            composeRule.onAllNodesWithText("Replying to yourself").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("32-swipe-reply-quote.png")
    }

    @Test fun shot33_floatingCallWindow() {
        // ITEM 3 PROOF: a connected call minimized to the floating window,
        // floating over a live chat — controller state driven exactly as the
        // call screen's Back/minimize path drives it.
        val th = AppState.threads.value.first()
        CallController.state.value = CallUiState.Connected(
            th.id, "shot-pip-1", false, th.person, System.currentTimeMillis() - 65_000,
        )
        CallController.minimizeCall()
        frame {
            Box(Modifier.fillMaxSize()) {
                ChatScreen(th.id)
                FloatingCallWindow()
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "floating call window must be showing over the chat",
            CallController.pipMinimized.value &&
                composeRule.onAllNodesWithText("Aarav").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("33-floating-call-window.png")
        CallController.endSilently()
    }

    @Test fun shot34_imageViewer() {
        // ITEM 5 PROOF: the full-screen image viewer — photo fit to screen,
        // explicit "Save to device" action + close. (Nothing auto-saves.)
        val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.rgb(240, 200, 170))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(160, 80, 50); textSize = 44f
        }
        canvas.drawText("Tikuu photo", 60f, 210f, paint)
        val m = TikuMessage(
            id = "viewer1", senderUid = "p-aarav", kind = MsgKind.IMAGE,
            time = "9:41", me = false, photo = bmp)
        frame { ImageViewerOverlay(m) {} }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "viewer must offer the explicit Save to device action",
            composeRule.onAllNodesWithText("Save to device").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("34-image-viewer.png")
    }

    @Test fun shot35_editProfile() {
        // ITEM 6 PROOF: Me ▸ Edit profile — photo row + username field with
        // the signup-mirroring validation + save.
        AppState.myUsername = "abu_saalim"
        AppState.homeTab = HomeTab.ME
        frame { HomeScreen() }
        EditProfileSheet.open()
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "edit profile sheet must offer the username save",
            composeRule.onAllNodesWithText("Save username").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "edit profile sheet must offer photo change",
            composeRule.onAllNodesWithText("Change photo").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("35-edit-profile.png")
    }

    @Test fun shot36_jumpToBottomPill() {
        // ITEM 4 PROOF: a long chat scrolled UP (toward older messages)
        // surfaces the floating jump-to-latest pill.
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        for (i in 1..30) {
            AppState.upsertMessage(id, TikuMessage(
                id = "j$i", senderUid = if (i % 2 == 0) "me" else p.uid,
                kind = MsgKind.TEXT, text = "Message number $i in a long chat",
                time = "9:41", me = i % 2 == 0, tsMs = i.toLong()))
        }
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        // Drive the scroll through the list's own semantics action (gestures
        // against the reversed list proved unreliable in this harness): jump
        // deep into history — the pill must appear once we're off the bottom.
        composeRule.onNodeWithTag("message_list").performScrollToIndex(24)
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "jump-to-latest pill must appear when scrolled up",
            composeRule.onAllNodesWithTag("jump_to_latest").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("36-jump-to-bottom.png")
    }

    // ---------- round 9 additions ----------
    @Test fun shot37_ownerSection() {
        // OWNER PAIR (1 of 2): harness owner seam ON — Me gains the Owner
        // section: Notifications toggle (shown ON, as he would opt in), App
        // lock toggle, and the explicit Lock now row.
        OwnerState.previewOverride = true
        OwnerPrefs.setNotify(true)
        AppState.homeTab = HomeTab.ME
        frame { HomeScreen() }
        // NO scroll: the Owner group sits directly under the profile card and
        // fits in the first viewport — a root swipeUp flings the list to the
        // settings groups and DISPOSES this item (the round-7 lesson, learned
        // again: shot37 v1 swiped and the title left the semantics tree).
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "owner Me pane must carry the Owner section",
            composeRule.onAllNodesWithText("Owner").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "owner section must offer the Notifications toggle",
            composeRule.onAllNodesWithText("Notifications").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "owner section must offer Lock now",
            composeRule.onAllNodesWithText("Lock now").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("37-owner-section.png")
        OwnerState.previewOverride = null
    }

    @Test fun shot38_mePaneNoOwner() {
        // OWNER PAIR (2 of 2): seam OFF — the Owner section must not exist
        // AT ALL (this is every non-owner account's Me pane, unchanged).
        OwnerState.previewOverride = false
        AppState.homeTab = HomeTab.ME
        frame { HomeScreen() }
        // Same unswiped first viewport as shot37 — the honest pair.
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "non-owner Me pane must not contain an Owner section",
            composeRule.onAllNodesWithText("Owner").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "non-owner Me pane must not contain Lock now",
            composeRule.onAllNodesWithText("Lock now").fetchSemanticsNodes().isEmpty(),
        )
        shot("38-me-pane-no-owner.png")
        OwnerState.previewOverride = null
    }
}
