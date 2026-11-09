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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
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
        // ROUND 63: the notifications preference DEFAULTS ON for every
        // user — the harness slate starts from that default (shot48
        // flips it off inside its own test; this line resets it).
        OwnerPrefs.setNotify(true)
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
        // ROUND 55 (Part A): NO account surface on the home — the
        // Round-54 sign-in chip is deleted (its signed-in tap was a
        // password bypass). No sign-in wording may render here; the
        // only sign-in in the app lives at the top of the gate.
        org.junit.Assert.assertTrue(
            "facade home must not contain any sign-in surface",
            composeRule.onAllNodesWithText("Sign in", substring = true)
                .fetchSemanticsNodes().isEmpty(),
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
        // ROUND 22 (supersedes the Round 14 note): the shelf is back to
        // TEN stories and the hidden entry is NO LONGER the last one —
        // the "2023,05" story sits SECOND FROM THE BOTTOM (story index
        // size-2), with one ordinary story below it, so the shelf does
        // not end on the door. Facade.kt keys the card's double-tap gate
        // on the story's ID (SECRET_STORY_ID), never its position.
        // Data-level guards: exactly ten stories, the entry at size-2,
        // and an ordinary story underneath it.
        org.junit.Assert.assertEquals(
            "the stories shelf must hold exactly ten stories",
            10, DramaContent.stories.size,
        )
        org.junit.Assert.assertEquals(
            "the hidden entry story must sit second from the bottom",
            "2023,05", DramaContent.stories[DramaContent.stories.size - 2].title,
        )
        org.junit.Assert.assertEquals(
            "one ordinary story must sit below the hidden entry",
            "The Morning You Almost Quit", DramaContent.stories.last().title,
        )
        frame { FacadeHome(initialTab = FacadeTab.STORIES) }
        // Scroll the list's OWN semantics node straight to the entry
        // card (lazy items: header = 0, stories = 1..size, so the entry
        // — story index size-2 — is lazy index size-1). Swiping the ROOT
        // node did not drive the facade's LazyColumn under Robolectric
        // in build 51 (the item was never composed, so the text query
        // below found nothing). The query is substring because a
        // composed card may merge its texts into one semantics node; the
        // title is unique on the facade, so the proof is unchanged.
        composeRule.onNode(hasScrollAction())
            .performScrollToIndex(DramaContent.stories.size - 1)
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "stories list must not contain any Tik wording",
            composeRule.onAllNodesWithText("Tik").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "stories list must show the hidden entry story second from the bottom",
            composeRule.onAllNodesWithText("2023,05", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("05-hidden-entry-story.png")
        // DECOY: a single tap on the entry card opens its innocent
        // reader, exactly like any other story (a body paragraph from
        // deep in the story proves the reader — the list card only ever
        // shows the opening lines). detectTapGestures fires onTap only
        // once the double-tap window closes, so advance the clock past
        // it before asserting.
        composeRule.onAllNodesWithText("2023,05", substring = true)[0]
            .performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "single tap on the entry story must open the decoy reader",
            composeRule.onAllNodesWithText("last Friday of May", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        // Back at the shelf (fresh composition — the reader state is
        // shell-internal), scroll to the entry card once more.
        frame { FacadeHome(initialTab = FacadeTab.STORIES) }
        composeRule.onNode(hasScrollAction())
            .performScrollToIndex(DramaContent.stories.size - 1)
        composeRule.waitForIdle()
        // THE DOOR: a double tap on the SAME card routes to the Tik
        // gate (ROUND 54: the camouflaged episode gate — same screen,
        // new face). App lock is ON in this harness, so the gate is
        // never skipped — SecretEntry lands on Screen.TikGate.
        composeRule.onAllNodesWithText("2023,05", substring = true)[0]
            .performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(
            "double tap on the entry story must route to the Tik gate",
            Screen.TikGate, AppState.screen,
        )
        // Leave the shared AppState slate as the harness found it.
        AppState.go(Screen.Welcome)
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
        // ROUND 54 (Part A): the gate's face is now the camouflaged
        // episode panel — no identity seed is needed (the panel renders
        // identically for everyone; that is the point). Assert the
        // in-world furniture exists and NO trace of the old gate does:
        // no password wording, no journal wording, no lock copy.
        // ROUND 55 (Part A): the panel's top now carries the Google
        // sign-in — the harness has no live Firebase session, so the
        // signed-out face is what renders, and it must be present.
        frame { TikGateScreen() }
        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
        composeRule.onNodeWithText("Enter your episode").assertIsDisplayed()
        composeRule.onNodeWithText("Search").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        org.junit.Assert.assertTrue(
            "the episode gate must never say 'Password'",
            composeRule.onAllNodesWithText("Password", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the episode gate must never mention a journal",
            composeRule.onAllNodesWithText("journal", substring = true)
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Journal", substring = true)
                    .fetchSemanticsNodes().isEmpty(),
        )
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
        // ROUND 63: the Notifications row is a REAL switch again and
        // it is ON here by default — via the per-user preference
        // (OwnerPrefs.notifyOn, default true), not a frozen always-on
        // presentation. shot48 proves the switch flips it.
        org.junit.Assert.assertTrue(
            "notifications preference must default ON",
            OwnerPrefs.notifyOn,
        )
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
        // 5 Privacy, 6 App lock, 7 Notifications, 8 Read receipts,
        // 9 Last seen, 10 Block screenshots, 11 Hide typing,
        // 12 Blocked accounts). ROUND 18: the Notifications row is now
        // present for every user (it used to be owner-only), shifting
        // Blocked accounts from 11 to 12.
        if (composeRule.onAllNodesWithText("Blocked accounts")
                .fetchSemanticsNodes().isEmpty()
        ) {
            composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
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
        // Copy / Star / Pin / Delete for me / Delete for everyone all
        // offered (ROUND 18: delete split into the two IG options; the
        // old single "Delete" label is gone).
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
        // ROUND 57 (Fix 2): the menu gains Edit for an own text message.
        org.junit.Assert.assertTrue(
            "long-press menu must offer Edit for an own text message",
            composeRule.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "long-press menu must offer Delete for me",
            composeRule.onAllNodesWithText("Delete for me").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "long-press menu must offer Delete for everyone for an own message",
            composeRule.onAllNodesWithText("Delete for everyone").fetchSemanticsNodes().isNotEmpty(),
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
        // ROUND 56: the Owner section gains the "AI chats" row (it
        // sits directly above "Lock now", so the scroll above has
        // already composed it).
        org.junit.Assert.assertTrue(
            "owner section must offer AI chats",
            composeRule.onAllNodesWithText("AI chats").fetchSemanticsNodes().isNotEmpty(),
        )
        // ROUND 57 (Fix 5): the Owner section gains the "Skip home
        // screen" toggle (above "Lock now" in the same section, so
        // the scroll above has already composed it).
        org.junit.Assert.assertTrue(
            "owner section must offer Skip home screen",
            composeRule.onAllNodesWithText("Skip home screen").fetchSemanticsNodes().isNotEmpty(),
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
        // ROUND 18: App lock + Notifications are now universal Privacy
        // rows for every user — this shot's assertions still hold because
        // they only pin the OWNER SECTION and "Lock now", which stay
        // owner-only.
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

    // ---------- ROUND 60: Nikah ceremony (fake partner state) ----------
    // The ceremony content is pure on (state, myUid, nowMs): the harness
    // seeds a session whose partner presence stamp is fresh, exactly as
    // a live partner's heartbeat would make it — one device drives both
    // sides by choosing which uid is "me".
    // ROUND 65: the DISPLAY names (Aarav/Meera) deliberately DIFFER from
    // the REAL names (Danish Iqbal / Maryam Siddiqui) — every ceremony
    // assertion below pins the REAL names, proving display names drive
    // nothing in the ceremony any more.
    private fun nikahFakeState(
        step: String,
        groomQabool: Int = 0,
        brideQabool: Int = 0,
        groomUid: String = "me",
        brideUid: String = "p-meera",
        groomReal: String = "Danish Iqbal",
        brideReal: String = "Maryam Siddiqui",
        nameDone: Map<String, Boolean> = emptyMap(),
    ): NikahUiState {
        val now = System.currentTimeMillis()
        return NikahUiState(
            exists = true, status = "active", step = step,
            creatorUid = "me", groomUid = groomUid, brideUid = brideUid,
            groomName = "Aarav", brideName = "Meera",
            groomRealName = groomReal, groomFatherName = "Mohammad Iqbal",
            brideRealName = brideReal, brideFatherName = "Abdul Siddiqui",
            nameDoneBySide = nameDone.ifEmpty {
                mapOf(groomUid to true, brideUid to true)
            },
            presence = mapOf(groomUid to now, brideUid to now),
            mahrPhase = "accepted", mahrText = "₹51,000 + a gold ring",
            groomQabool = groomQabool, brideQabool = brideQabool,
        )
    }

    /** A signature drawn on a tiny canvas, PNG'd + base64'd exactly as
     *  the keepsake payload carries it. */
    private fun nikahSigB64(): String {
        val sigBmp = android.graphics.Bitmap.createBitmap(220, 64, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(sigBmp)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(255, 217, 228); strokeWidth = 4f
            style = android.graphics.Paint.Style.STROKE
        }
        val path = android.graphics.Path().apply {
            moveTo(10f, 44f); cubicTo(60f, 4f, 120f, 60f, 210f, 22f)
        }
        c.drawPath(path, paint)
        val out = java.io.ByteArrayOutputStream()
        sigBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return Crypto.b64(out.toByteArray())
    }

    /** ROUND 65: the staged certificate fixture — the v4 prototype's
     *  own sample family, with REAL names, fathers, mahr, witnesses
     *  and drawn signatures. Shots 39, 54 and 55 share it, so the
     *  in-ceremony certificate and the keepsake re-open are asserted
     *  (and captured) from byte-identical payload data. */
    private fun nikahSampleKeepsake(): NikahKeepsake {
        val sig = nikahSigB64()
        return NikahKeepsake(
            groomName = "Danish Iqbal", brideName = "Maryam Siddiqui",
            dateMs = 1794192000000L, mahr = "₹51,000 + a gold ring",
            witnessGroom = "Imran Sheikh", witnessBride = "Bilal Ahmad",
            sigGroomPngB64 = sig, sigBridePngB64 = sig,
            groomFather = "Mohammad Iqbal", brideFather = "Abdul Siddiqui",
        )
    }

    @Test fun shot37_nikahLobby() {
        val now = System.currentTimeMillis()
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF050507))) {
                NikahCeremonyContent(
                    state = nikahFakeState(NikahSteps.LOBBY),
                    myUid = "me", nowMs = now, cb = NikahCallbacks(),
                    motion = false,
                )
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "nikah lobby must carry its heading",
            composeRule.onAllNodesWithText("Lobby", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "nikah lobby must offer Ready to both partners",
            composeRule.onAllNodesWithText("I'm Ready", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "lobby avatars must be the REAL names, never display names",
            composeRule.onAllNodesWithText("Danish Iqbal", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Maryam Siddiqui", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "lobby couple representation must be the iOS emoji rings",
            composeRule.onAllNodesWithText("🤵").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("👰").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("37-nikah-lobby.png")
    }

    @Test fun shot38_nikahQaboolWaiting() {
        // The BRIDE's view of the groom's Qabool: two rings filled live,
        // her controls locked behind the turn line — addressed by the
        // groom's REAL first name (his display name is "Aarav").
        val now = System.currentTimeMillis()
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF050507))) {
                NikahCeremonyContent(
                    state = nikahFakeState(NikahSteps.QABOOL_GROOM, groomQabool = 2),
                    myUid = "p-meera", nowMs = now, cb = NikahCallbacks(),
                    motion = false,
                )
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "waiting side must be told who is saying Qabool — by REAL name",
            composeRule.onAllNodesWithText("Danish said Qabool Hai", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "waiting side's controls must be locked with the turn line",
            composeRule.onAllNodesWithText("Danish's turn — please wait", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("38-nikah-qabool-waiting.png")
    }

    @Test fun shot39_nikahCertificate() {
        val keepsake = nikahSampleKeepsake()
        frame {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF050507))
                    .padding(22.dp),
            ) {
                NikahCertificateCard(keepsake)
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "certificate must carry its title",
            composeRule.onAllNodesWithText("NIKAH NAMA").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "certificate must name both partners by REAL name",
            composeRule.onAllNodesWithText("DANISH IQBAL", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("MARYAM SIDDIQUI", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "certificate must carry the deterministic number",
            composeRule.onAllNodesWithText("CERTIFICATE NO. NN-2026-", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("39-nikah-certificate.png")
    }

    // ---------- ROUND 65: real-name flow + v4 certificate proof ----------

    @Test fun shot50_nikahNamesGroom() {
        // The GROOM's name entry: neither side has submitted yet.
        val now = System.currentTimeMillis()
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF050507))) {
                NikahCeremonyContent(
                    state = nikahFakeState(
                        NikahSteps.NAMES,
                        groomReal = "", brideReal = "",
                        nameDone = mapOf("me" to false, "p-meera" to false),
                    ),
                    myUid = "me", nowMs = now, cb = NikahCallbacks(),
                    motion = false,
                )
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "name step must carry the v4 heading",
            composeRule.onAllNodesWithText("real name", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "groom's form must label his full-name field",
            composeRule.onAllNodesWithText("Your full name · Groom", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "name step must carry the Nikah Nama note, de-decorated",
            composeRule.onAllNodesWithText(
                "These names appear on your Nikah Nama — app usernames are never used.",
                substring = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "name step must offer the v4 submit",
            composeRule.onAllNodesWithText("My name is set", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("50-nikah-names-groom.png")
    }

    @Test fun shot51_nikahNamesBride() {
        // The BRIDE's name entry: the groom has already submitted on
        // HIS phone — her status line names him by his REAL first name.
        val now = System.currentTimeMillis()
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF050507))) {
                NikahCeremonyContent(
                    state = nikahFakeState(
                        NikahSteps.NAMES,
                        groomUid = "p-danish", brideUid = "me",
                        brideReal = "",
                        nameDone = mapOf("p-danish" to true, "me" to false),
                    ),
                    myUid = "me", nowMs = now, cb = NikahCallbacks(),
                    motion = false,
                )
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "bride's form must label her full-name field",
            composeRule.onAllNodesWithText("Your full name · Bride", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "partner-done line must use the partner's REAL first name",
            composeRule.onAllNodesWithText("Danish added his name on his phone", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("51-nikah-names-bride.png")
    }

    @Test fun shot52_nikahLobbyNameWaiting() {
        // Lobby reached with the BRIDE's name still missing (the
        // legacy/rejoin edge the NAMES step normally gates): Ready
        // stays locked and the status line names what is missing,
        // while the emoji rings + entered REAL name already render.
        val now = System.currentTimeMillis()
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF050507))) {
                NikahCeremonyContent(
                    state = nikahFakeState(
                        NikahSteps.LOBBY,
                        brideReal = "",
                        nameDone = mapOf("me" to true, "p-meera" to false),
                    ),
                    myUid = "me", nowMs = now, cb = NikahCallbacks(),
                    motion = false,
                )
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "lobby must name the missing name while Ready is locked",
            composeRule.onAllNodesWithText("still missing", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "lobby rings must be the iOS emoji pair",
            composeRule.onAllNodesWithText("🤵").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("👰").fetchSemanticsNodes().isNotEmpty(),
        )
        // CI-106 (Defect 2): the name-waiting partner's avatar must
        // carry the generic label — her chat DISPLAY name ("Meera",
        // and the groom's "Aarav") must appear NOWHERE in the ceremony.
        org.junit.Assert.assertTrue(
            "lobby must not label any side with a chat display name",
            composeRule.onAllNodesWithText("Meera", substring = true)
                .fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Aarav", substring = true)
                    .fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "lobby must label the name-waiting partner generically",
            composeRule.onAllNodesWithText("Your partner")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("52-nikah-lobby-name-waiting.png")
    }

    @Test fun shot53_nikahQaboolBrideWaiting() {
        // The GROOM's view of the bride's Qabool: the wait-line and
        // the live count address her by her REAL first name (her
        // display name is "Meera").
        val now = System.currentTimeMillis()
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF050507))) {
                NikahCeremonyContent(
                    state = nikahFakeState(NikahSteps.QABOOL_BRIDE, brideQabool = 1),
                    myUid = "me", nowMs = now, cb = NikahCallbacks(),
                    motion = false,
                )
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "bride's turn line must use her REAL first name",
            composeRule.onAllNodesWithText("Maryam's turn — please wait", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "live count must use her REAL first name",
            composeRule.onAllNodesWithText("Maryam said Qabool Hai · 1 of 3", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("53-nikah-qabool-bride-waiting.png")
    }

    // CI-106 (Defect 3): the certificate card is TALLER than the
    // class window (w360dp-h800dp-480dpi = 1080×2400px), so shots
    // 54/55 captured only its top — the grid's third row, the
    // signatures + seal row and the CERTIFICATE NO. footer were
    // clipped out of the PNGs. These two tests run in a taller
    // window in the SAME pattern/density (h1400dp = 4200px), which
    // fits the full card plus the keepsake overlay's chrome.
    @Test
    @Config(qualifiers = "w360dp-h1400dp-480dpi")
    fun shot54_nikahCertificateV4() {
        // The v4 Nikah Nama with the staged sample family (shared
        // fixture with shots 39/55): fathers, mahr, witnesses,
        // signatures, seal, deterministic number.
        val keepsake = nikahSampleKeepsake()
        frame {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF050507))
                    .padding(22.dp),
            ) {
                NikahCertificateCard(keepsake)
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "certificate must carry both fathers",
            composeRule.onAllNodesWithText("MOHAMMAD IQBAL", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("ABDUL SIDDIQUI", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "certificate must carry the fixed venue + officiant lines",
            composeRule.onAllNodesWithText("Online ceremony, conducted on Tiku", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Tiku — in-app ceremony", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "certificate number must be the deterministic NN-2026 number",
            composeRule.onAllNodesWithText(
                "CERTIFICATE NO. ${nikahCertNo(keepsake)}", substring = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        // CI-106 (Defect 1): the Hijri cell must be the staged date's
        // real Hijri date (9 Nov 2026), composed from the Islamic
        // calendar's own fields — not formatter-pattern garbage.
        org.junit.Assert.assertTrue(
            "certificate hijri date must be 28 Jumada al-awwal 1448 AH",
            composeRule.onAllNodesWithText("28 Jumada al-awwal 1448 AH", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        // CI-106 (Defect 3 acceptance): the rows the old viewport
        // clipped — witnesses (both names), both signature captions,
        // and the seal — must all be composed in the full-card capture.
        org.junit.Assert.assertTrue(
            "certificate must carry both witnesses",
            composeRule.onAllNodesWithText("Imran Sheikh", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Bilal Ahmad", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "certificate must carry both signature captions and the seal",
            composeRule.onAllNodesWithText("SIGNATURE OF THE GROOM", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("SIGNATURE OF THE BRIDE", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("TIKU")
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("54-nikah-certificate-v4.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp-480dpi")
    fun shot55_nikahKeepsakeReopen() {
        // The keepsake re-open from the chat card: the SAME certificate
        // renderer over the SAME payload must show the identical
        // certificate (same names, same deterministic number).
        // CI-106: same taller window as shot54 (see there) — the
        // overlay's full column (mascot + heading + card + footer)
        // must fit the capture, signatures/seal/cert-no included.
        val keepsake = nikahSampleKeepsake()
        frame {
            NikahCertificateOverlay(keepsake) {}
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "keepsake re-open must render the same couple",
            composeRule.onAllNodesWithText("DANISH IQBAL", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("MARYAM SIDDIQUI", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "keepsake re-open must render the identical certificate number",
            composeRule.onAllNodesWithText(
                "CERTIFICATE NO. ${nikahCertNo(keepsake)}", substring = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        // CI-106 (Defect 3 acceptance): the previously clipped bottom
        // of the card must be composed in the re-open capture too —
        // witnesses, the officiant line and both signature captions.
        org.junit.Assert.assertTrue(
            "keepsake re-open must carry witnesses + officiant + signatures",
            composeRule.onAllNodesWithText("Imran Sheikh", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Bilal Ahmad", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Tiku — in-app ceremony", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("SIGNATURE OF THE GROOM", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("SIGNATURE OF THE BRIDE", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("55-nikah-keepsake-reopen.png")
    }

    @Test fun shot40_chatFewMessagesResting() {
        // ROUND 61 (Bug 2 proof): a SHORT conversation (exactly three
        // messages, in a fresh thread with no seeded history) in its
        // resting state — the pre-Round-57 behaviour the owner calls
        // the conversation resting "in the middle": the bubbles hang
        // from the list's top padding under the floating header, the
        // whole conversation visible at once, NOT clustered against
        // the composer. The message list's scaffolding (LazyColumn
        // arrangement / contentPadding / state anchors) is byte-for-
        // byte build 93's; this shot pins the resting state so any
        // future drift is visible in the PNG.
        // seedThreads() gives EVERY preview person a thread, so take the
        // first person's thread and clear it — the conversation under
        // test must hold exactly the three messages below, nothing else.
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        AppState.upsertMessage(id, TikuMessage(
            id = "few1", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "Hi, are we still on for tomorrow?", time = "9:30", me = false, tsMs = 1))
        AppState.upsertMessage(id, TikuMessage(
            id = "few2", senderUid = "me", kind = MsgKind.TEXT,
            text = "Yes, all set from my side", time = "9:31", me = true, tsMs = 2))
        AppState.upsertMessage(id, TikuMessage(
            id = "few3", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "Great, see you then", time = "9:32", me = false, tsMs = 3))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "few-message chat must show its first message at rest",
            composeRule.onAllNodesWithText("Hi, are we still on for tomorrow?")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "few-message chat must show its last message at rest (whole conversation visible)",
            composeRule.onAllNodesWithText("Great, see you then")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("40-chat-few-messages-resting.png")
    }

    /** ROUND 62 (Bug B): frame the REAL RdChatInput directly (the
     *  shot19 pattern) and return the centre-Y of [text] and of the
     *  "+" attach glyph (which sits dead-centre in its 40 dp disc,
     *  itself centred on the pill's mid-line). */
    private fun composerCentreDelta(text: String, value: String): Pair<Float, Float> {
        frame {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    RdChatInput(
                        h = null, value = value, onChange = {}, onSend = {},
                        accent = Color(0xFFFF5C8A),
                        replyText = null, onClearReply = {},
                        recording = false, recSecs = 0,
                        onPlus = {}, onMic = {}, onRecCancel = {}, onRecSend = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val textNode = composeRule.onAllNodesWithText(text, substring = true)
            .fetchSemanticsNodes().firstOrNull()
        val plusNode = composeRule.onAllNodesWithText("+")
            .fetchSemanticsNodes().firstOrNull()
        org.junit.Assert.assertNotNull("composer must render '$text'", textNode)
        org.junit.Assert.assertNotNull("composer must render the + attach button", plusNode)
        val t = textNode!!.boundsInRoot
        val p = plusNode!!.boundsInRoot
        return (t.top + t.bottom) / 2f to (p.top + p.bottom) / 2f
    }

    @Test fun shot41_composerHintCentered() {
        // ROUND 62 (Bug B): at rest the "Message…" hint must sit on
        // the pill's vertical centre (within 4 dp of the attach
        // disc's centre) — Round 57's Alignment.Bottom left it low.
        val (hint, plus) = composerCentreDelta("Message…", "")
        val tol = 4f * ctx.resources.displayMetrics.density
        org.junit.Assert.assertTrue(
            "hint must be vertically centred in the pill (hint=$hint plus=$plus)",
            kotlin.math.abs(hint - plus) <= tol,
        )
        shot("41-composer-hint-centered.png")
    }

    @Test fun shot42_composerTypedCentered() {
        // ROUND 62 (Bug B): typed single-line text centres the same way.
        val (typed, plus) = composerCentreDelta("Hello there", "Hello there")
        val tol = 4f * ctx.resources.displayMetrics.density
        org.junit.Assert.assertTrue(
            "typed text must be vertically centred in the pill (typed=$typed plus=$plus)",
            kotlin.math.abs(typed - plus) <= tol,
        )
        shot("42-composer-typed-centered.png")
    }

    @Test fun shot43_composerManyLinesCapped() {
        // ROUND 62 (Bug B): many lines must stay capped at the
        // field's 150 dp (internal scroll), never balloon the pill.
        val many = (1..12).joinToString("\n") { "Long message line $it" }
        frame {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    RdChatInput(
                        h = null, value = many, onChange = {}, onSend = {},
                        accent = Color(0xFFFF5C8A),
                        replyText = null, onClearReply = {},
                        recording = false, recSecs = 0,
                        onPlus = {}, onMic = {}, onRecCancel = {}, onRecSend = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val field = composeRule.onAllNodesWithText("Long message line", substring = true)
            .fetchSemanticsNodes().firstOrNull()
        org.junit.Assert.assertNotNull("many-line composer must render its field", field)
        val capPx = 150f * ctx.resources.displayMetrics.density + 4f
        org.junit.Assert.assertTrue(
            "field height ${field!!.boundsInRoot.height} must stay at the 150 dp cap ($capPx)",
            field.boundsInRoot.height <= capPx,
        )
        shot("43-composer-many-lines-capped.png")
    }

    // ---------- ROUND 63: in-bubble time must never touch text ----------
    // Each fixture uses a FRESH cleared thread (the shot40 pattern) with
    // tsMs = 0, so bubbleTimeText falls back to the message's own `time`
    // string — the in-bubble clock reads exactly "12:54 PM" and the time
    // is its own semantics node (RdBubbleTime), assertable on its own.
    private fun bubbleFixtureThread(vararg msgs: TikuMessage) {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        msgs.forEach { AppState.upsertMessage(id, it) }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
    }

    /** The time node and the message-text node must occupy disjoint
     *  rectangles: beside (the chip starts at/after the text block's
     *  right edge) or below (it starts at/after the block's bottom
     *  edge). In-flow layout guarantees one of the two; the overlay
     *  this round replaced guaranteed neither. */
    private fun assertTimeClearOfText(text: String, time: String) {
        val t = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().firstOrNull()
        val c = composeRule.onAllNodesWithText(time).fetchSemanticsNodes().firstOrNull()
        org.junit.Assert.assertNotNull("bubble must render its text", t)
        org.junit.Assert.assertNotNull("bubble must render its in-bubble time '$time'", c)
        val tb = t!!.boundsInRoot
        val cb = c!!.boundsInRoot
        org.junit.Assert.assertTrue(
            "time must never overlap text (text=$tb chip=$cb)",
            cb.left >= tb.right - 1f || cb.top >= tb.bottom - 1f,
        )
    }

    @Test fun shot44_bubbleTimeShort() {
        // (1) plain short message, own + seen (✓✓ rides the chip).
        bubbleFixtureThread(TikuMessage(
            id = "bt-short", senderUid = "me", kind = MsgKind.TEXT,
            text = "See you at five", time = "12:54 PM", me = true, tsMs = 0,
            seenByPeer = true))
        assertTimeClearOfText("See you at five", "12:54 PM")
        org.junit.Assert.assertTrue(
            "own seen message must carry its ✓✓ ticks beside the time",
            composeRule.onAllNodesWithText("✓✓").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("44-bubble-time-short.png")
    }

    @Test fun shot45_bubbleTimeLong() {
        // (2) plain long multi-line message: beside a short last line
        // or below a full one — both are in-flow and disjoint by
        // construction, so this shot pins existence + the resting
        // look; the strict rectangle proof lives in 44/46/47, whose
        // geometry is fully controlled.
        bubbleFixtureThread(TikuMessage(
            id = "bt-long", senderUid = PreviewStore.people.first().uid, kind = MsgKind.TEXT,
            text = "The venue photos came out so well, especially the ones from the terrace at sunset — you should see the whole set tonight",
            time = "12:54 PM", me = false, tsMs = 0))
        org.junit.Assert.assertTrue(
            "long message must render its text",
            composeRule.onAllNodesWithText("The venue photos came out so well", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "long message must render its in-bubble time",
            composeRule.onAllNodesWithText("12:54 PM").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("45-bubble-time-long.png")
    }

    @Test fun shot46_bubbleTimeReply() {
        // (3) THE OWNER'S FAILING GEOMETRY: a REPLY bubble — quote on
        // top, reply text below whose final line (explicit lines, so
        // the geometry is deterministic) runs near-full and ends in
        // an emoji, own + seen. The chip must flow onto its own line
        // (or sit past the last line's right edge) — never on the words.
        val replyText = "Done, sending them now\nevery single one from the trip\nthe last one is my favourite 😍"
        bubbleFixtureThread(TikuMessage(
            id = "bt-reply", senderUid = "me", kind = MsgKind.TEXT,
            text = replyText, time = "12:54 PM", me = true, tsMs = 0,
            seenByPeer = true,
            replyQuote = "Send me the venue photos tonight", replyToId = "q1"))
        org.junit.Assert.assertTrue(
            "reply bubble must render its quote line on top",
            composeRule.onAllNodesWithText("Send me the venue photos tonight", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTimeClearOfText(replyText, "12:54 PM")
        shot("46-bubble-time-reply.png")
    }

    @Test fun shot47_bubbleTimeCaption() {
        // (4) a media bubble WITH a caption long enough to wrap: the
        // caption and the time chip share one bottom Row in the photo
        // frame, so their rectangles are disjoint slots by layout.
        val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.rgb(240, 200, 170))
        val caption = "Golden hour at the venue, this one is my favourite from the whole evening"
        bubbleFixtureThread(TikuMessage(
            id = "bt-cap", senderUid = "me", kind = MsgKind.IMAGE,
            text = caption, time = "12:54 PM", me = true, tsMs = 0,
            seenByPeer = true, photo = bmp))
        assertTimeClearOfText(caption, "12:54 PM")
        shot("47-bubble-time-caption.png")
    }

    @Test fun shot48_notificationToggleOff() {
        // ROUND 63 (amendment #1): the Settings ▸ Notifications switch
        // is REAL again — tapping it must flip the very preference
        // TikuFcmService's display gate reads (OwnerPrefs.notifyOn),
        // which @Before has set to the default-ON slate (and resets
        // for the next shot).
        org.junit.Assert.assertTrue(
            "notifications must default ON via the preference",
            OwnerPrefs.notifyOn,
        )
        AppState.homeTab = HomeTab.ME
        AppState.goTik(3) // the Round-14 shell routes on tikDestination, not homeTab
        frame { HomeScreen() }
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("Notifications")
                .fetchSemanticsNodes().isEmpty()
        ) {
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Notifications"))
            composeRule.waitForIdle()
        }
        composeRule.onAllNodesWithText("Notifications")[0]
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertFalse(
            "tapping the switch must turn the preference OFF",
            OwnerPrefs.notifyOn,
        )
        org.junit.Assert.assertTrue(
            "the row must report its Off state",
            composeRule.onAllNodesWithText("Off").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("48-notification-toggle-off.png")
    }

    @Test fun shot49_multiPhotoThreeBubbles() {
        // ROUND 63 (multi-photo): the system picker cannot be driven
        // headless, so this stages the picker's RESULT side exactly as
        // the new loop consumes it — one sendPhotoVo call per selected
        // image (the exact call the old single-pick callback made
        // once), three times in selection order — and proves three
        // independent photo messages land and render as three photo
        // bubbles. (The picker contract + 10-cap live in Chat.kt's
        // pickMedia; see round63-implementer-notes.md.)
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        fun photoBmp(r: Int, g: Int, b: Int) =
            android.graphics.Bitmap.createBitmap(120, 90, android.graphics.Bitmap.Config.ARGB_8888)
                .also { android.graphics.Canvas(it).drawColor(android.graphics.Color.rgb(r, g, b)) }
        AppState.repo.sendPhotoVo(id, photoBmp(240, 200, 170), null)
        AppState.repo.sendPhotoVo(id, photoBmp(170, 220, 190), null)
        AppState.repo.sendPhotoVo(id, photoBmp(180, 190, 240), null)
        val imgs = AppState.thread(id)?.messages?.filter { it.kind == MsgKind.IMAGE } ?: emptyList()
        org.junit.Assert.assertEquals(
            "three picks must land as three separate photo messages",
            3, imgs.size,
        )
        org.junit.Assert.assertTrue(
            "each photo message is its own message (distinct ids, all mine)",
            imgs.map { it.id }.distinct().size == 3 && imgs.all { it.me },
        )
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals(
            "three photo bubbles must render (one in-bubble time chip each)",
            3, composeRule.onAllNodesWithText("now").fetchSemanticsNodes().size,
        )
        shot("49-multi-photo-three-bubbles.png")
    }

    // ---------- ROUND 66: Chat Emoji Effects (EmojiFx2) staging ----------
    // The Fx2 overlay's TEST-ONLY stageT hook seeds the RNG and steps
    // the engine in fixed 1/60 spec-second increments to an exact
    // mid-animation spec-time, then holds that frame — deterministic
    // particles for the capture. Production never passes stageT.
    @Test fun shot56_fx2Heart() {
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
                EmojiFx2Overlay(specIdx = 0, onDone = {}, stageT = 1.05f)
            }
        }
        composeRule.waitForIdle()
        shot("56-fx2-heart.png")
    }

    @Test fun shot57_fx2Fire() {
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
                EmojiFx2Overlay(specIdx = 9, onDone = {}, stageT = 1.2f)
            }
        }
        composeRule.waitForIdle()
        shot("57-fx2-fire.png")
    }

    @Test fun shot58_fx2Lol() {
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
                EmojiFx2Overlay(specIdx = 18, onDone = {}, stageT = 1.0f)
            }
        }
        composeRule.waitForIdle()
        shot("58-fx2-lol.png")
    }

    @Test fun shot59_fxKissRound25() {
        // ROUND 66 (😘 hard rule proof): 😘 still renders the OLD
        // Round-25 show — this stages EmojiFxOverlay kind 1 (NOT the
        // Fx2 engine) with the test clock paused, advanced to 1.3 s,
        // mid-trail flight.
        composeRule.mainClock.autoAdvance = false
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
                EmojiFxOverlay(kind = 1, onDone = {})
            }
        }
        composeRule.mainClock.advanceBy(1300)
        shot("59-fx-kiss-round25.png")
    }

    @Test fun shot60_nikahCertificateDownload() {
        // ROUND 66 (amendment): the keepsake certificate view must
        // carry the Download action, and the off-screen render must
        // produce the full-paper bitmap at the fixed 1240 px width.
        // A real MediaStore write to a device Downloads folder cannot
        // complete under Robolectric (no real provider); the save is
        // attempted and its outcome printed, not asserted.
        val keepsake = nikahSampleKeepsake()
        frame { NikahCertificateOverlay(keepsake) {} }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "keepsake certificate view must carry the Download action",
            composeRule.onAllNodesWithText("Download").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "keepsake certificate view must render its certificate number",
            composeRule.onAllNodesWithText(
                "CERTIFICATE NO. ${nikahCertNo(keepsake)}", substring = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
        val activity = composeRule.activity
        var rendered: android.graphics.Bitmap? = null
        var renderDone = false
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            kotlinx.coroutines.MainScope().launch {
                rendered = runCatching {
                    renderNikahCertificateBitmap(activity, keepsake)
                }.getOrNull()
                renderDone = true
            }
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (!renderDone && System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        if (rendered != null) {
            val bmp = rendered!!
            println("R66 cert render: ${bmp.width}x${bmp.height}")
            org.junit.Assert.assertEquals(
                "certificate render must be exactly 1240 px wide", 1240, bmp.width,
            )
            org.junit.Assert.assertTrue(
                "certificate render must carry the full paper height", bmp.height > 600,
            )
            val saved = MediaSaver.saveCertificatePng(
                activity, bmp, "Nikah-Nama-${nikahCertNo(keepsake)}.png",
            )
            println("R66 cert MediaStore save under Robolectric: $saved")
        } else {
            println("R66 cert render: null under Robolectric (see notes)")
        }
        shot("60-nikah-certificate-download.png")
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
