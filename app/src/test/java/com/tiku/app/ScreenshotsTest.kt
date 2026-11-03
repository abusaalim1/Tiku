package com.tiku.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
 * (Roborazzi + Robolectric, native graphics) at 1080×2400. ROUND 14: the Tik
 * shots exercise the owner's redesign surfaces (ChatScreen / InfoScreen /
 * HomeScreen in black + CardC + Rose) against the preview demo store; the
 * facade shots still consume the real bundled drama pack (assets/facade/).
 * PNGs land in build/outputs/roborazzi/shots/ and ship as a release asset.
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
        // the owner pair (shots 28/31) flips the seam inside its own test.
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
        // ROUND 14: reset the new engine seams — PreviewRepo is a process
        // singleton, so a block or note set by one shot must not leak into
        // the next (blocked threads vanish from lists; notes render in the
        // Messages header row).
        PreviewStore.people.forEach { AppState.repo.unblockUser(it.uid) }
        AppState.repo.deleteMyNote()
        // The harness never runs SessionLock.unlockFlow, whose preview
        // branch is what normally seeds the notes flow — seed it here with
        // the exact same call, AFTER the resets above (deleteMyNote only
        // strips the "me" key; the seeded partner notes are what shot25
        // and the Messages notes row consume).
        AppState.notes.value = PreviewStore.seedNotes()
        // The Round-14 shell routes on AppState.tikDestination; homeTab is
        // only its legacy mirror. Reset the real destination too, or a
        // shot inherits whatever pane the previous shot left behind (the
        // Robolectric classloader — and AppState with it — is shared
        // across this whole class).
        AppState.goTik(0)
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

    // ---------- facade (ROUND 13: K-drama; ROUND 14 addendum: entry moved) ----------
    @Test fun shot01_dramaHome() {
        AppState.go(Screen.Facade)
        frame { FacadeRouter() }
        // ZERO-TRACE PROOF (negative): the word "Tik" must not appear
        // anywhere on the facade home — the drama brand wordmark is fine,
        // and the exact-match query does not match it.
        org.junit.Assert.assertTrue(
            "facade home must not contain any Tik wording",
            composeRule.onAllNodesWithText("Tik").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "facade home must carry the brand wordmark",
            composeRule.onAllNodesWithText(DramaContent.appName).fetchSemanticsNodes().isNotEmpty(),
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

    @Test fun shot05_hiddenEntryStory() {
        // ROUND 14 ADDENDUM: the For You tab + "For You, Always" entry are
        // REMOVED. The hidden Tik entry is now the LAST story in Stories,
        // titled exactly "2023,05" (single tap = decoy story, double tap =
        // the gate). This shot proves the entry story is listed — and that
        // the facade still carries the word "Tik" NOWHERE.
        // Data-level guard: the entry story is genuinely the LAST story.
        org.junit.Assert.assertEquals(
            "the hidden entry story must be the last story in the data",
            "2023,05", DramaContent.stories.last().title,
        )
        frame { FacadeHome(initialTab = FacadeTab.STORIES) }
        // The entry story sits at the very end of the shelf — scroll the
        // list's OWN semantics node straight to it (lazy items: header =
        // 0, stories = 1..size, so index size IS the last story — the
        // Round-16 shelf has no footer item). Swiping the ROOT node
        // did not drive the facade's LazyColumn under Robolectric in
        // build 51 (the last item was never composed, so the text query
        // below found nothing). The query is substring because a composed
        // card may merge its texts into one semantics node; the title is
        // unique on the facade, so the proof is unchanged.
        composeRule.onNode(hasScrollAction())
            .performScrollToIndex(DramaContent.stories.size)
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "stories list must not contain any Tik wording",
            composeRule.onAllNodesWithText("Tik").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "stories list must end with the hidden entry story",
            composeRule.onAllNodesWithText("2023,05", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("05-hidden-entry-story.png")
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

    // ---------- Tik (ROUND 14: the owner's redesign) ----------
    @Test fun shot11_messagesHome() {
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        org.junit.Assert.assertTrue(
            "Messages screen must carry its title",
            composeRule.onAllNodesWithText("Messages").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "Messages must carry the notes row (Your note)",
            composeRule.onAllNodesWithText("Your note").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("11-messages-home.png")
    }

    @Test fun shot12_requests() {
        // Requests live behind the Messages header ("Requests · N") and
        // need a seeded request thread (Worker A preview seam) to open.
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        val header = composeRule.onAllNodesWithText("Requests", substring = true)
            .fetchSemanticsNodes()
        org.junit.Assert.assertTrue(
            "Messages header must offer Requests (a request thread is seeded)",
            header.isNotEmpty(),
        )
        composeRule.onAllNodesWithText("Requests", substring = true)[0]
            .performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "Requests screen must offer Accept on the seeded request",
            composeRule.onAllNodesWithText("Accept").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("12-requests.png")
    }

    @Test fun shot13_callsPane() {
        // The Calls tab lists call HISTORY aggregated from the chats'
        // encrypted call-event messages (seeded: ended / missed /
        // declined / video) — rows, not an empty state.
        AppState.homeTab = HomeTab.CALLS
        AppState.goTik(1) // the Round-14 shell routes on tikDestination, not homeTab
        frame { HomeScreen() }
        shot("13-calls-pane.png")
    }

    @Test fun shot14_settingsMain() {
        AppState.homeTab = HomeTab.ME
        AppState.goTik(3) // the Round-14 shell routes on tikDestination, not homeTab
        frame { HomeScreen() }
        shot("14-settings-main.png")
    }

    @Test fun shot15_settingsBlocked() {
        // Seed a REAL block through the engine seam, then walk the real
        // Settings ▸ Blocked accounts subpage.
        AppState.repo.blockUser(PreviewStore.people[5].uid)
        AppState.homeTab = HomeTab.ME
        AppState.goTik(3) // the Round-14 shell routes on tikDestination, not homeTab
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        // The Blocked accounts row sits near the bottom of the Settings
        // LazyColumn and may start below the composed window — scroll the
        // list to its item first if it isn't composed yet (lazy items:
        // 0 Title, 1 profile, 2 Appearance, 3 Mode, 4 Text size,
        // 5 Privacy, 6–10 toggles, 11 Blocked accounts).
        if (composeRule.onAllNodesWithText("Blocked accounts")
                .fetchSemanticsNodes().isEmpty()
        ) {
            composeRule.onNode(hasScrollAction()).performScrollToIndex(11)
            composeRule.waitForIdle()
        }
        composeRule.onAllNodesWithText("Blocked accounts")[0]
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "blocked subpage must list the blocked person with an Unblock action",
            composeRule.onAllNodesWithText("Unblock").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("15-settings-blocked.png")
        AppState.repo.unblockUser(PreviewStore.people[5].uid)
    }

    @Test fun shot16_chatScreen() {
        val th = AppState.threads.value.first()
        AppState.setTyping(th.id, true)
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "chat top bar must show the chat title",
            composeRule.onAllNodesWithText(th.displayName, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "peer typing must show in the top bar status",
            composeRule.onAllNodesWithText("typing", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("16-chat-screen.png")
        AppState.setTyping(th.id, false)
    }

    @Test fun shot17_chatMenu() {
        // The redesign's long-press menu is IN-CHAT now (centred glass
        // menu + emoji bar) — no bottom sheet. Own text message → Reply /
        // Copy / Star / Pin / Delete all offered.
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.upsertMessage(id, TikuMessage(
            id = "menu1", senderUid = "me", kind = MsgKind.TEXT,
            text = "Long press this message for the menu", time = "9:41", me = true, tsMs = 1))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Long press this message for the menu")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "long-press menu must offer Reply",
            composeRule.onAllNodesWithText("Reply").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "long-press menu must offer Copy for a text message",
            composeRule.onAllNodesWithText("Copy").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "long-press menu must offer Delete for an own message",
            composeRule.onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("17-chat-menu.png")
    }

    @Test fun shot18_chatAttachSheet() {
        val th = AppState.threads.value.first()
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        composeRule.onAllNodesWithText("+")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "attach sheet must offer Photo (the only attach kind — no video messages)",
            composeRule.onAllNodesWithText("Photo", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("18-chat-attach-sheet.png")
    }

    @Test fun shot19_chatRecordingBar() {
        // The redesign ChatInput's REAL recording state, framed directly:
        // driving an actual microphone + MediaRecorder under Robolectric
        // is not possible, and the bar itself is pure UI state.
        frame {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    RdChatInput(
                        h = null, value = "", onChange = {}, onSend = {},
                        accent = Color(0xFFFF5C8A),
                        replyText = null, onClearReply = {},
                        recording = true, recSecs = 42,
                        onPlus = {}, onMic = {}, onRecCancel = {}, onRecSend = {},
                    )
                }
            }
        }
        org.junit.Assert.assertTrue(
            "recording bar must show the elapsed timer",
            composeRule.onAllNodesWithText("0:42").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "recording bar must offer Cancel",
            composeRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("19-chat-recording-bar.png")
    }

    @Test fun shot20_infoMain() {
        val th = AppState.threads.value.first()
        AppState.go(Screen.ChatDetails(th.id))
        frame { InfoScreen(th.id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "info header must show the chat's display name",
            composeRule.onAllNodesWithText(th.displayName, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "info must offer Customize chat",
            composeRule.onAllNodesWithText("Customize chat").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("20-info-main.png")
    }

    @Test fun shot21_infoCustomize() {
        val th = AppState.threads.value.first()
        AppState.go(Screen.ChatDetails(th.id))
        frame { InfoScreen(th.id) }
        composeRule.onAllNodesWithText("Customize chat")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "customize page must carry the text-size control",
            composeRule.onAllNodesWithText("Text size").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("21-info-customize.png")
    }

    @Test fun shot22_infoStarred() {
        val th = AppState.threads.value.first()
        AppState.upsertMessage(th.id, TikuMessage(
            id = "star-shot", senderUid = "me", kind = MsgKind.TEXT,
            text = "This one is starred for the info page", time = "9:41",
            me = true, tsMs = 999_999, starredByMe = true))
        AppState.go(Screen.ChatDetails(th.id))
        frame { InfoScreen(th.id) }
        composeRule.onAllNodesWithText("Starred messages")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "starred page must list the real starred message",
            composeRule.onAllNodesWithText("This one is starred for the info page", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("22-info-starred.png")
    }

    @Test fun shot23_infoNicknames() {
        val th = AppState.threads.value.first()
        AppState.go(Screen.ChatDetails(th.id))
        frame { InfoScreen(th.id) }
        composeRule.onAllNodesWithText("Nicknames")[0]
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "nicknames page must carry the per-person nickname field",
            composeRule.onAllNodesWithText("Nickname for", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("23-info-nicknames.png")
    }

    @Test fun shot24_watchPane() {
        // The Watch pane (pane-owned WebView architecture; preview builds
        // render the placeholder branch) composes headless.
        frame { WatchPane() }
        composeRule.waitForIdle()
        shot("24-watch-pane.png")
    }

    @Test fun shot25_noteSheetPartner() {
        // Data-driven: find a seeded PARTNER note (keyed by a preview
        // person's uid) in the real notes flow, tap its bubble in the
        // Messages notes row, and prove the partner note sheet opened.
        val peopleUids = PreviewStore.people.map { it.uid }.toSet()
        val partnerNote = AppState.notes.value.entries.firstOrNull { it.key in peopleUids }
        org.junit.Assert.assertTrue(
            "preview must seed a partner note for this shot (Worker A seam)",
            partnerNote != null,
        )
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.onAllNodesWithText(partnerNote!!.value.text, substring = true)[0]
            .performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "partner note sheet must offer the note reply field",
            composeRule.onAllNodesWithText("Reply to note", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("25-note-sheet-partner.png")
    }

    @Test fun shot26_noteSheetMine() {
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.onAllNodesWithText("Your note")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "my note sheet must offer the thought field",
            composeRule.onAllNodesWithText("What's on your mind?", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("26-note-sheet-mine.png")
    }

    @Test fun shot27_callPillOverMessages() {
        // A connected call minimized to the shell's floating CallPill over
        // Messages — controller state driven exactly as the call screen's
        // minimize path drives it.
        val th = AppState.threads.value.first()
        CallController.state.value = CallUiState.Connected(
            th.id, "shot-pip-1", false, th.person, System.currentTimeMillis() - 65_000,
        )
        CallController.minimizeCall()
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "minimized call pill must float over Messages",
            CallController.pipMinimized.value &&
                composeRule.onAllNodesWithText(th.person.name, substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("27-call-pill-messages.png")
        CallController.endSilently()
    }

    @Test fun shot28_settingsOwner() {
        // OWNER PAIR (1 of 2): harness owner seam ON — Settings gains the
        // Owner section: Notifications toggle, App lock toggle, Lock now.
        OwnerState.previewOverride = true
        OwnerPrefs.setNotify(true)
        AppState.homeTab = HomeTab.ME
        AppState.goTik(3) // the Round-14 shell routes on tikDestination, not homeTab
        frame { HomeScreen() }
        composeRule.waitForIdle()
        // The Owner section is the LAST Settings LazyColumn item — scroll
        // until its deepest row ("Lock now") is composed, which brings the
        // whole section into the window (index-counting proved brittle:
        // the Round-15 Notifications row shifted the item indices).
        if (composeRule.onAllNodesWithText("Lock now")
                .fetchSemanticsNodes().isEmpty()
        ) {
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Lock now"))
            composeRule.waitForIdle()
        }
        org.junit.Assert.assertTrue(
            "owner Settings must carry the Owner section",
            composeRule.onAllNodesWithText("OWNER").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "owner section must offer the Notifications toggle",
            composeRule.onAllNodesWithText("Notifications").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "owner section must offer Lock now",
            composeRule.onAllNodesWithText("Lock now").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("28-settings-owner.png")
        OwnerState.previewOverride = null
    }

    @Test fun shot29_chatSwipeReply() {
        // Swipe-reply armed on a chat — first try the real swipe gesture
        // on an own bubble; if the harness can't drive it past the arm
        // threshold, fall back to the long-press menu's Reply (the same
        // reply state). The capture shows the composer's reply bar.
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
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        var armedBySwipe = false
        runCatching {
            composeRule.onNodeWithText("Perfect, this is the long message I am swiping on to reply")
                .performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            armedBySwipe = composeRule.onAllNodesWithText("Replying")
                .fetchSemanticsNodes().isNotEmpty()
        }
        if (!armedBySwipe) {
            composeRule.onNodeWithText("Perfect, this is the long message I am swiping on to reply")
                .performTouchInput { longClick() }
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("Reply")[0].performTouchInput { click() }
            composeRule.waitForIdle()
        }
        org.junit.Assert.assertTrue(
            "reply bar must be armed (swipe or menu path)",
            composeRule.onAllNodesWithText("Replying").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("29-chat-swipe-reply.png")
    }

    @Test fun shot30_chatBlockedBar() {
        // Block the partner through the real engine seam — the composer
        // is replaced by the redesign's blocked bar.
        val th = AppState.threads.value.first()
        AppState.repo.blockUser(th.person.uid)
        // PreviewRepo.blockUser (like the real engine's list filtering)
        // also removes the blocked thread from AppState.threads — and
        // ChatScreen renders "Chat unavailable" for a thread that is no
        // longer in the list, so the blocked bar could never compose.
        // The bar's condition reads ONLY AppState.blockedUids (flipped by
        // the blockUser call above, through the real repo seam); restore
        // the thread so the shot frames the state the bar guards: an open
        // chat whose partner is blocked.
        AppState.threads.value = AppState.threads.value + th
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "blocked partner must replace the composer with the blocked bar",
            composeRule.onAllNodesWithText("You blocked this person")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "blocked bar must offer Unblock",
            composeRule.onAllNodesWithText("Unblock").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("30-chat-blocked-bar.png")
        AppState.repo.unblockUser(th.person.uid)
    }

    @Test fun shot31_settingsNoOwner() {
        // OWNER PAIR (2 of 2): seam OFF — the Owner section must not exist
        // AT ALL (every non-owner account's Settings, unchanged).
        OwnerState.previewOverride = false
        AppState.homeTab = HomeTab.ME
        AppState.goTik(3) // the Round-14 shell routes on tikDestination, not homeTab
        frame { HomeScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "non-owner Settings must not contain an Owner section",
            composeRule.onAllNodesWithText("Owner").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "non-owner Settings must not contain Lock now",
            composeRule.onAllNodesWithText("Lock now").fetchSemanticsNodes().isEmpty(),
        )
        shot("31-settings-no-owner.png")
        OwnerState.previewOverride = null
    }

    @Test fun shot32_imageViewer() {
        // The full-screen image viewer — photo fit to screen, explicit
        // "Save to device" action + close. (Nothing auto-saves.)
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
        shot("32-image-viewer.png")
    }

    @Test fun shot33_callScreen() {
        val th = AppState.threads.value.first()
        // preview fake call: "Calling…" then connects — no WebRTC classes load here
        CallController.startCall(ctx, th.id, false)
        frame { CallScreen(th.id, false) }
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.waitForIdle()
        shot("33-call-screen.png")
    }

    @Test fun shot34_incomingRinging() {
        // Drive CallController.onIncoming exactly as the callee's ringing
        // watcher does — the incoming-call screen must render.
        val th = AppState.threads.value.first()
        CallController.onIncoming(th.id, "shot-ring-1", "video", th.person)
        frame { CallScreen(th.id, true) }
        composeRule.waitForIdle()
        shot("34-incoming-ringing.png")
        CallController.endSilently()
    }

    @Test fun shot35_momentsEntry() {
        // ROUND 14 (owner order): the Memories entry — a fixed feature
        // entry (not data-gated) directly under the notes row on
        // Messages, opening the integrated couple-memories web surface.
        AppState.homeTab = HomeTab.CHATS
        AppState.goTik(0)
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "Messages must carry the Memories entry under the notes row",
            composeRule.onAllNodesWithText("Memories").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "Memories entry must carry its subtitle",
            composeRule.onAllNodesWithText("Your private place, together")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("35-moments-entry.png")
    }

    @Test fun shot36_momentsScreen() {
        // The Moments surface in preview: the static dark placeholder
        // (PREVIEW branch — never a real WebView headless) with its
        // chrome title.
        AppState.go(Screen.Moments)
        frame { MomentsScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "Moments placeholder must carry its title",
            composeRule.onAllNodesWithText("Memories").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("36-moments-screen.png")
    }
}
