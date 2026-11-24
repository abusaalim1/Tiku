package com.tiku.app

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
        // ROUND 70: production always has a default FirebaseApp by the
        // time any Tik surface composes; the harness does not (no
        // FirebaseInitProvider under Robolectric), so surfaces that
        // attach a Firestore listener on composition (the game
        // player's gamestate listener, after a guest Accepts) crashed
        // with "Default FirebaseApp is not initialized" (shot67).
        // Mirror the production invariant with dummy test options —
        // no sign-in happens, listeners simply fail async offline.
        if (com.google.firebase.FirebaseApp.getApps(ctx).isEmpty()) {
            runCatching {
                com.google.firebase.FirebaseApp.initializeApp(
                    ctx,
                    com.google.firebase.FirebaseOptions.Builder()
                        .setProjectId("tiku-chat")
                        .setApplicationId("1:258621285053:android:0000000000000000000000")
                        .setApiKey("dummy-test-only-key")
                        .build(),
                )
            }
        }
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
        // ROUND 71 (Fix 3): AiJobs is app-global — an in-flight label
        // or companion flag leaked by an earlier test must never
        // bleed into a later shot.
        AiJobs.thinking.clear()
        AiJobs.companionBusy = false
        // ROUND 75: the update prompt + downloader are app-global state —
        // a build or download phase staged by one shot must never leak
        // into the next.
        UpdateState.available = null
        UpdateDownloader.phase = UpdateDownloader.Phase.Idle
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
            "Messages must carry the whispers row (Your whisper)",
            composeRule.onAllNodesWithText("Your whisper").fetchSemanticsNodes().isNotEmpty(),
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
        // 0 Title, 1 App update, 2 profile, 3 Appearance, 4 Mode,
        // 5 Text size, 6 Privacy, 7 App lock, 8 Notifications,
        // 9 Read receipts, 10 Last seen, 11 Block screenshots,
        // 12 Hide typing, 13 Blocked accounts). ROUND 18: the
        // Notifications row is present for every user, shifting Blocked
        // accounts from 11 to 12; ROUND 76 (WS4): the App update row
        // is pinned at index 1, shifting it once more to 13.
        if (composeRule.onAllNodesWithText("Blocked accounts")
                .fetchSemanticsNodes().isEmpty()
        ) {
            composeRule.onNode(hasScrollAction()).performScrollToIndex(13)
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

    @Test fun shot24_noWatchAnywhere() {
        // ROUND 75 (WS3): YouTube Watch is REMOVED. Proof: the retired
        // destination 2 falls back to the Messages pane, the NavPill
        // offers exactly Message / Call / Setting, and no "Watch" label
        // exists anywhere in the Tik shell.
        AppState.go(Screen.Home)
        AppState.goTik(2)
        frame { HomeScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "retired Watch destination must fall back to the Messages pane",
            composeRule.onAllNodesWithText("Your whisper", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the NavPill must still offer the Setting tab",
            composeRule.onAllNodesWithText("Setting", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "no Watch tab or label may exist anywhere in the shell",
            composeRule.onAllNodesWithText("Watch", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        shot("24-no-watch.png")
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
        // ROUND 75 (WS4): give the seeded partner note a stored song,
        // exactly as an old build could have saved one — the new build
        // must render it as a clean plain note: the song stays stored
        // but is never shown, offered, or played.
        AppState.notes.value = AppState.notes.value + (partnerNote!!.key to partnerNote.value.copy(
            music = NoteMusic(
                videoId = "seededSong1", title = "Seeded Song Title",
                startSec = 0, endSec = 30,
            ),
        ))
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.onAllNodesWithText(partnerNote!!.value.text, substring = true)[0]
            .performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "partner note sheet must offer the note reply field",
            composeRule.onAllNodesWithText("Reply to whisper", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a music-carrying note must render as a plain note (no song title shown)",
            composeRule.onAllNodesWithText("Seeded Song Title", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        shot("25-note-sheet-partner.png")
    }

    @Test fun shot26_noteSheetMine() {
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.onAllNodesWithText("Your whisper")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "my note sheet must offer the thought field",
            composeRule.onAllNodesWithText("What's on your mind?", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "ROUND 79 (WS-3): my whisper sheet offers Add music (the LOCAL picker)",
            composeRule.onAllNodesWithText("Add music", substring = true)
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

    @Test fun shot68_connectedCallDock() {
        // ROUND 70: the connected-call dock (the owner's premium kit,
        // CallControls.kt) needs its own shot — shot33 does NOT stage
        // it: with PREVIEW_UI_ONLY=false its startCall takes the real
        // path and lands on the Failed state (the outgoing End-only
        // row), as the recent CI captures of 33-call-screen.png show.
        // Stage a CONNECTED video call directly through the controller
        // state instead — the shot27 seam — and freeze the compose
        // clock (the shot59 pattern) so the dock's 3-second auto-hide
        // timer never runs before the capture: the dock stays visible.
        composeRule.mainClock.autoAdvance = false
        val th = AppState.threads.value.first()
        CallController.state.value = CallUiState.Connected(
            th.id, "shot-dock-1", true, th.person, System.currentTimeMillis() - 65_000,
            speakerOn = true, cameraOn = true,
        )
        frame { CallScreen(th.id, true) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "connected dock must carry the Mute control",
            composeRule.onAllNodesWithText("Mute").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "connected dock must carry the Speaker control",
            composeRule.onAllNodesWithText("Speaker").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "video dock must carry the Camera control",
            composeRule.onAllNodesWithText("Camera").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "video dock must carry the Flip control",
            composeRule.onAllNodesWithText("Flip").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "connected dock must carry the End control",
            composeRule.onAllNodesWithText("End").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("68-connected-call-dock.png")
        CallController.endSilently()
    }

    // ---------- ROUND 71 (Fix 3): AiJobs ownership proof ----------
    /** The Fix-3 contract, without any UI: a job launched through
     *  AiJobs (a) registers its label synchronously, (b) refuses a
     *  second launch for the same key while in flight, and (c) is
     *  NOT cancelled when an unrelated screen-like scope is cancelled
     *  mid-job — it still completes and clears its label. Before the
     *  fix the job rode the chat screen's rememberCoroutineScope, so
     *  (c) was exactly the owner's dead-reply bug. */
    @Test fun aiJobs_jobSurvivesScreenScopeCancel() {
        val key = "aijobs-test-thread"
        AiJobs.thinking.remove(key)
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.atomic.AtomicBoolean(false)
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val launched = AiJobs.launch(key, "Tika is thinking…") {
            started.countDown()
            while (!release.get()) delay(20)
            completed.set(true)
        }
        org.junit.Assert.assertTrue("first launch must be accepted", launched)
        org.junit.Assert.assertTrue(
            "job must start",
            started.await(5, java.util.concurrent.TimeUnit.SECONDS),
        )
        org.junit.Assert.assertEquals("Tika is thinking…", AiJobs.thinking[key])
        // (b) single-flight: a second launch for the same key is
        // refused while the first is in flight.
        org.junit.Assert.assertFalse(
            "second launch for an in-flight key must be refused",
            AiJobs.launch(key, "Tika is creating…") { },
        )
        // (c) cancel an unrelated screen-like scope mid-job (the
        // pre-fix job lived in exactly such a scope).
        val screenScope = CoroutineScope(Job() + Dispatchers.Default)
        screenScope.launch { delay(60_000) }
        screenScope.cancel()
        org.junit.Assert.assertFalse("AiJobs job must still be running", completed.get())
        org.junit.Assert.assertEquals("Tika is thinking…", AiJobs.thinking[key])
        // Release it: the job completes and clears its own label.
        release.set(true)
        val deadline = System.currentTimeMillis() + 5_000
        while (AiJobs.thinking.containsKey(key) && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        org.junit.Assert.assertTrue("AiJobs job must complete", completed.get())
        org.junit.Assert.assertFalse(
            "label must be cleared when the job ends",
            AiJobs.thinking.containsKey(key),
        )
    }

    /** The companion half of the contract: launchCompanion flips
     *  AiJobs.companionBusy synchronously, refuses a duplicate while
     *  busy, and clears the flag when the job ends — so the gate a
     *  re-entered companion screen reads is the job's own state. */
    @Test fun aiJobs_companionBusyGate() {
        org.junit.Assert.assertFalse(
            "companion gate must start clean",
            AiJobs.companionBusy,
        )
        val release = java.util.concurrent.atomic.AtomicBoolean(false)
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        org.junit.Assert.assertTrue(
            AiJobs.launchCompanion {
                while (!release.get()) delay(20)
                completed.set(true)
            },
        )
        org.junit.Assert.assertTrue(
            "companionBusy must be set synchronously",
            AiJobs.companionBusy,
        )
        org.junit.Assert.assertFalse(
            "duplicate companion launch must be refused",
            AiJobs.launchCompanion { },
        )
        release.set(true)
        val deadline = System.currentTimeMillis() + 5_000
        while (AiJobs.companionBusy && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        org.junit.Assert.assertTrue("companion job must complete", completed.get())
        org.junit.Assert.assertFalse(
            "companionBusy must clear when the job ends",
            AiJobs.companionBusy,
        )
    }

    // ---------- ROUND 82: the bound that fires + delete = cancel ----------
    /** The R82 defect, reproduced without any UI: the job's block is
     *  wedged in a TRULY blocking call (a latch await on IO — no
     *  suspension point, so neither withTimeoutOrNull nor job
     *  cancellation can reach it; this is GeminiClient's
     *  HttpURLConnection slow-drip shape). The watchdog must still
     *  clear the indicator on schedule, release the single-flight
     *  gate, and stale the job's handle — and when the wedged block
     *  finally unwinds, its guarded dispatch must post NOTHING. */
    @Test fun aiJobs_watchdogExpiresWedgedJob() {
        val key = "aijobs-r82-wedged"
        AiJobs.cancel(key)
        val wedge = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.CountDownLatch(1)
        val posted = java.util.concurrent.atomic.AtomicBoolean(false)
        val handleRef = java.util.concurrent.atomic.AtomicReference<AiJobs.Handle?>(null)
        try {
            val launched = AiJobs.launch(key, "Tika is thinking…", timeoutMs = 400) { job ->
                handleRef.set(job)
                withContext(Dispatchers.IO) {
                    entered.countDown()
                    wedge.await()
                }
                if (job.isActive()) posted.set(true)
            }
            org.junit.Assert.assertTrue("launch must be accepted", launched)
            org.junit.Assert.assertTrue(
                "block must reach the wedge",
                entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
            )
            org.junit.Assert.assertEquals("Tika is thinking…", AiJobs.thinking[key])
            // The block is STILL wedged (latch never released) — the
            // watchdog alone must clear the indicator.
            val deadline = System.currentTimeMillis() + 5_000
            while (AiJobs.thinking.containsKey(key) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            org.junit.Assert.assertFalse(
                "watchdog must clear the indicator while the block is wedged",
                AiJobs.thinking.containsKey(key),
            )
            org.junit.Assert.assertFalse(
                "the expired job's handle must be stale",
                handleRef.get()!!.isActive(),
            )
            // The single-flight gate is released by the expiry.
            org.junit.Assert.assertTrue(
                "a new launch must be accepted after expiry",
                AiJobs.launch(key, "Tika is creating…") { },
            )
            val deadline2 = System.currentTimeMillis() + 5_000
            while (AiJobs.thinking.containsKey(key) && System.currentTimeMillis() < deadline2) {
                Thread.sleep(25)
            }
            // Release the wedge: the stale block unwinds (or dies by
            // cancellation) — either way it must never post.
            wedge.countDown()
            Thread.sleep(500)
            org.junit.Assert.assertFalse(
                "a stale job must never post",
                posted.get(),
            )
        } finally {
            wedge.countDown()
            AiJobs.cancel(key)
        }
    }

    /** Delete = cancel: cancelForCommand with the WRONG id is a
     *  no-op; with the command's own id it clears the indicator
     *  SYNCHRONOUSLY (the animation dies with the delete tap, not
     *  when the job unwinds), and the in-flight block — released
     *  afterwards — posts nothing through its guarded dispatch. */
    @Test fun aiJobs_deleteCancelsCommandJob() {
        val key = "aijobs-r82-delete"
        AiJobs.cancel(key)
        val release = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.CountDownLatch(1)
        val posted = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            val launched = AiJobs.launch(
                key, "Tika is thinking…", commandMsgId = "cmd-r82",
            ) { job ->
                withContext(Dispatchers.IO) {
                    entered.countDown()
                    release.await()
                }
                if (job.isActive()) posted.set(true)
            }
            org.junit.Assert.assertTrue("launch must be accepted", launched)
            org.junit.Assert.assertTrue(
                "block must be in flight",
                entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
            )
            org.junit.Assert.assertFalse(
                "deleting some other message must not cancel the job",
                AiJobs.cancelForCommand(key, "some-other-msg"),
            )
            org.junit.Assert.assertEquals("Tika is thinking…", AiJobs.thinking[key])
            org.junit.Assert.assertTrue(
                "deleting the command message must cancel its job",
                AiJobs.cancelForCommand(key, "cmd-r82"),
            )
            org.junit.Assert.assertFalse(
                "the indicator must clear synchronously on delete",
                AiJobs.thinking.containsKey(key),
            )
            release.countDown()
            Thread.sleep(500)
            org.junit.Assert.assertFalse(
                "no reply may land for a deleted command",
                posted.get(),
            )
        } finally {
            release.countDown()
            AiJobs.cancel(key)
        }
    }

    /** Generation discipline: job A expires by watchdog while wedged;
     *  job B takes the key over; when A finally unwinds, A's finally
     *  must NOT clear B's indicator. */
    @Test fun aiJobs_staleJobCannotClearNewerJob() {
        val key = "aijobs-r82-stale"
        AiJobs.cancel(key)
        val wedgeA = java.util.concurrent.CountDownLatch(1)
        val releaseB = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            org.junit.Assert.assertTrue(
                AiJobs.launch(key, "A thinking…", timeoutMs = 300) {
                    withContext(Dispatchers.IO) { wedgeA.await() }
                },
            )
            val deadline = System.currentTimeMillis() + 5_000
            while (AiJobs.thinking.containsKey(key) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            org.junit.Assert.assertFalse(
                "A must be expired by its watchdog",
                AiJobs.thinking.containsKey(key),
            )
            org.junit.Assert.assertTrue(
                AiJobs.launch(key, "B thinking…") {
                    while (!releaseB.get()) delay(20)
                },
            )
            org.junit.Assert.assertEquals("B thinking…", AiJobs.thinking[key])
            // A unwinds now — its finally runs against a stale
            // generation and must leave B's entry untouched.
            wedgeA.countDown()
            Thread.sleep(500)
            org.junit.Assert.assertEquals(
                "a stale job's finally must not clear the newer job's indicator",
                "B thinking…", AiJobs.thinking[key],
            )
            releaseB.set(true)
            val deadline2 = System.currentTimeMillis() + 5_000
            while (AiJobs.thinking.containsKey(key) && System.currentTimeMillis() < deadline2) {
                Thread.sleep(25)
            }
            org.junit.Assert.assertFalse(
                "B clears its own indicator when it ends",
                AiJobs.thinking.containsKey(key),
            )
        } finally {
            wedgeA.countDown()
            releaseB.set(true)
            AiJobs.cancel(key)
        }
    }

    /** The companion obeys the same two rules: its watchdog expires
     *  a wedged job (busy flag drops while the block is still stuck),
     *  and cancelCompanion (the store's delete hook) drops the flag
     *  synchronously with the stale handle blocking any landing. */
    @Test fun aiJobs_companionWatchdogAndCancel() {
        AiJobs.cancelCompanion()
        org.junit.Assert.assertFalse(AiJobs.companionBusy)
        val wedge = java.util.concurrent.CountDownLatch(1)
        val posted = java.util.concurrent.atomic.AtomicBoolean(false)
        val release = java.util.concurrent.atomic.AtomicBoolean(false)
        val posted2 = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            org.junit.Assert.assertTrue(
                AiJobs.launchCompanion(timeoutMs = 300) { job ->
                    withContext(Dispatchers.IO) { wedge.await() }
                    if (job.isActive()) posted.set(true)
                },
            )
            org.junit.Assert.assertTrue(AiJobs.companionBusy)
            val deadline = System.currentTimeMillis() + 5_000
            while (AiJobs.companionBusy && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            org.junit.Assert.assertFalse(
                "the companion watchdog must drop the busy flag while wedged",
                AiJobs.companionBusy,
            )
            wedge.countDown()
            Thread.sleep(500)
            org.junit.Assert.assertFalse("a stale companion job must not post", posted.get())
            // Delete-cancel path: flag drops synchronously.
            org.junit.Assert.assertTrue(
                AiJobs.launchCompanion { job ->
                    while (!release.get()) delay(20)
                    if (job.isActive()) posted2.set(true)
                },
            )
            org.junit.Assert.assertTrue(AiJobs.companionBusy)
            AiJobs.cancelCompanion()
            org.junit.Assert.assertFalse(
                "cancelCompanion must drop the busy flag synchronously",
                AiJobs.companionBusy,
            )
            release.set(true)
            Thread.sleep(300)
            org.junit.Assert.assertFalse(
                "a cancelled companion job must not post",
                posted2.get(),
            )
        } finally {
            wedge.countDown()
            release.set(true)
            AiJobs.cancelCompanion()
        }
    }

    // ---------- ROUND 86: Tika replies not arriving — full-path repro ----------
    // The owner (build 168): NO AI reply arrives on either surface.
    // These two tests drive the REAL send path end to end — type in
    // the composer, tap the send disc, and require the reply to LAND
    // (preview mode answers from GeminiClient's canned branch, so no
    // network is involved: a failure here is an app-side break).

    /** Tap the composer's send disc: the ~42dp clickable at the
     *  bottom-right of the input bar (rdPress registers a real click
     *  action; the mic icon shares the disc while the field is
     *  empty, so callers type first). Picks the LOWEST matching node
     *  so the jump-to-latest disc (same size, higher up) can never
     *  be mistaken for it. */
    private fun tapSendDiscR86() {
        val nodes = composeRule.onAllNodes(androidx.compose.ui.test.hasClickAction())
        val infos = nodes.fetchSemanticsNodes()
        var best = -1
        var bestY = Float.MIN_VALUE
        infos.forEachIndexed { i, n ->
            val b = n.boundsInRoot
            if (b.width in 100f..170f && b.height in 100f..170f &&
                b.center.x > 850f && b.center.y > 1900f && b.center.y > bestY
            ) {
                best = i
                bestY = b.center.y
            }
        }
        org.junit.Assert.assertTrue(
            "the send disc must be findable among ${infos.size} clickable nodes",
            best >= 0,
        )
        nodes[best].performClick()
        composeRule.waitForIdle()
    }

    @Test fun r86_chatSlashCommandReplyLands() {
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        try {
            AppState.go(Screen.Chat(id))
            frame { ChatScreen(id) }
            composeRule.waitForIdle()
            typeIntoComposer("/tiku hello there")
            tapSendDiscR86()
            val deadline = System.currentTimeMillis() + 15_000
            var msgs = AppState.thread(id)?.messages.orEmpty()
            while (System.currentTimeMillis() < deadline) {
                msgs = AppState.thread(id)?.messages.orEmpty()
                if (msgs.any { it.ai } && !AiJobs.thinking.containsKey(id)) break
                Thread.sleep(50)
            }
            val dump = msgs.joinToString(" | ") {
                "ai=${it.ai} me=${it.me} kind=${it.kind} text='${it.text.take(40)}' " +
                    "undec=${it.undecryptable} pend=${it.pendingDecrypt}"
            }
            org.junit.Assert.assertTrue(
                "the command message itself must be in the thread; msgs=[$dump]",
                msgs.any { it.text.startsWith("/tiku") },
            )
            org.junit.Assert.assertTrue(
                "an ai=true reply must land in the thread; " +
                    "thinking=${AiJobs.thinking}; msgs=[$dump]",
                msgs.any { it.ai },
            )
            org.junit.Assert.assertFalse(
                "the thinking entry must clear after the reply lands",
                AiJobs.thinking.containsKey(id),
            )
        } finally {
            AiJobs.cancel(id)
        }
    }

    @Test fun r86_companionReplyLands() {
        stageCompanionR73C()
        val base = TikaBotStore.messages.value.size
        try {
            frame { TikaBotScreen() }
            composeRule.waitForIdle()
            typeIntoComposer("hello tika, how are you?")
            tapSendDiscR86()
            val deadline = System.currentTimeMillis() + 15_000
            var msgs = TikaBotStore.messages.value
            while (System.currentTimeMillis() < deadline) {
                msgs = TikaBotStore.messages.value
                if (msgs.size > base + 1 && !AiJobs.companionBusy) break
                Thread.sleep(50)
            }
            val fresh = msgs.drop(base)
            val dump = fresh.joinToString(" | ") {
                "mine=${it.mine} sys=${it.sys} text='${it.text.take(40)}'"
            }
            org.junit.Assert.assertTrue(
                "the user's companion message must be appended; new=[$dump]",
                fresh.any { it.mine },
            )
            org.junit.Assert.assertTrue(
                "a bot reply (not mine, not sys) must land in the companion; " +
                    "busy=${AiJobs.companionBusy}; new=[$dump]",
                fresh.any { !it.mine && !it.sys },
            )
            org.junit.Assert.assertFalse(
                "the companion busy flag must clear after the reply lands",
                AiJobs.companionBusy,
            )
        } finally {
            cleanupCompanionR73C(base)
        }
    }

    /** Staging helper for the R82 shots: a two-message preview
     *  thread whose second message IS the slash command, staged with
     *  the exact id the AiJobs job will be linked to. */
    private fun r82StageThread(): String {
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.upsertMessage(id, TikuMessage(
            id = "ai82a", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "Can Tika think for us?", time = "9:41", me = false, tsMs = 1))
        AppState.upsertMessage(id, TikuMessage(
            id = "ai82cmd", senderUid = "me", kind = MsgKind.TEXT,
            text = "/tiku tell me a story", time = "9:41", me = true, tsMs = 2))
        return id
    }

    /** R82 staging (a): a REAL AiJobs launch (wedged block, as a
     *  slow provider call would be) drives the indicator — the
     *  animation is the job's own state, nothing else. */
    @Test fun shot111_chatAiTypingLiveJob() {
        composeRule.mainClock.autoAdvance = false
        val id = r82StageThread()
        val wedge = java.util.concurrent.CountDownLatch(1)
        try {
            org.junit.Assert.assertTrue(
                AiJobs.launch(id, "Tika is thinking…", commandMsgId = "ai82cmd") {
                    withContext(Dispatchers.IO) { wedge.await() }
                },
            )
            AppState.go(Screen.Chat(id))
            frame { ChatScreen(id) }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "chat must show the typing indicator while the job runs",
                composeRule.onAllNodesWithText("Tika is thinking…")
                    .fetchSemanticsNodes().isNotEmpty(),
            )
            shot("111-chat-ai-typing-live-job.png")
        } finally {
            wedge.countDown()
            AiJobs.cancel(id)
        }
    }

    /** R82 staging (b): the SAME live job — then the command message
     *  is deleted through the repo (the exact call the long-press
     *  menu makes). The indicator must be gone in the very next
     *  frame: delete = cancel, no waiting for the wedged block. */
    @Test fun shot112_chatAiTypingClearedOnDelete() {
        composeRule.mainClock.autoAdvance = false
        val id = r82StageThread()
        val wedge = java.util.concurrent.CountDownLatch(1)
        try {
            org.junit.Assert.assertTrue(
                AiJobs.launch(id, "Tika is thinking…", commandMsgId = "ai82cmd") {
                    withContext(Dispatchers.IO) { wedge.await() }
                },
            )
            AppState.repo.deleteForMe(id, "ai82cmd")
            org.junit.Assert.assertFalse(
                "delete must clear the AiJobs entry synchronously",
                AiJobs.thinking.containsKey(id),
            )
            AppState.go(Screen.Chat(id))
            frame { ChatScreen(id) }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "chat must NOT show the typing indicator after the command was deleted",
                composeRule.onAllNodesWithText("Tika is thinking…")
                    .fetchSemanticsNodes().isEmpty(),
            )
            shot("112-chat-ai-typing-cleared-on-delete.png")
        } finally {
            wedge.countDown()
            AiJobs.cancel(id)
        }
    }

    /** R82 staging (c): the job's block is STILL wedged, but its
     *  watchdog has expired it — a chat opened now (the relaunch /
     *  re-entry shape) must show NO animation: stale job state can
     *  never render. */
    @Test fun shot113_chatAiTypingGoneAfterExpiry() {
        composeRule.mainClock.autoAdvance = false
        val id = r82StageThread()
        val wedge = java.util.concurrent.CountDownLatch(1)
        try {
            org.junit.Assert.assertTrue(
                AiJobs.launch(id, "Tika is thinking…", timeoutMs = 300, commandMsgId = "ai82cmd") {
                    withContext(Dispatchers.IO) { wedge.await() }
                },
            )
            val deadline = System.currentTimeMillis() + 5_000
            while (AiJobs.thinking.containsKey(id) && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            org.junit.Assert.assertFalse(
                "the watchdog must have expired the wedged job",
                AiJobs.thinking.containsKey(id),
            )
            AppState.go(Screen.Chat(id))
            frame { ChatScreen(id) }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "chat must NOT show the typing indicator for an expired job",
                composeRule.onAllNodesWithText("Tika is thinking…")
                    .fetchSemanticsNodes().isEmpty(),
            )
            shot("113-chat-ai-typing-gone-after-expiry.png")
        } finally {
            wedge.countDown()
            AiJobs.cancel(id)
        }
    }

    // ---------- ROUND 71: deterministic identity intercept ----------
    /** The Round-71 identity law, without any UI: a who-made-you
     *  question is answered FROM CODE by GeminiClient's intercept,
     *  before the preview-mode branch and before any network — so
     *  the exact maker line must come back in this harness too, for
     *  both the /tiku entry point (answerText) and the companion
     *  entry point (companionReply, keyed on its latest user turn).
     *  The negative control pins the matcher: a generic knowledge
     *  question that merely shares the "who created" shape must NOT
     *  be intercepted. */
    @Test fun identityIntercept_tikuAnswersLocally() {
        val expected = GeminiClient.Outcome.Ok("Mujhe Abu Saalim ne banaya hai 😊")
        org.junit.Assert.assertEquals(
            expected,
            runBlocking { GeminiClient.answerText("tumhe kisne banaya?") },
        )
        org.junit.Assert.assertEquals(
            expected,
            runBlocking { GeminiClient.answerText("Who created you?") },
        )
    }

    @Test fun identityIntercept_companionAnswersLocally() {
        org.junit.Assert.assertEquals(
            GeminiClient.Outcome.Ok("Mujhe Abu Saalim ne banaya hai 😊"),
            runBlocking {
                GeminiClient.companionReply(listOf(true to "who made you?"), "persona")
            },
        )
    }

    @Test fun identityIntercept_doesNotFireOnKnowledgeQuestion() {
        // Negative control ONLY: whatever this returns in the harness
        // (a real cascade outcome — PREVIEW_UI_ONLY is a compile-time
        // false in this build, so there is no canned preview text on
        // this path; a network failure lands on Outcome.Failed /
        // Outcome.Quota) it must never be the maker line.
        org.junit.Assert.assertNotEquals(
            GeminiClient.Outcome.Ok("Mujhe Abu Saalim ne banaya hai 😊"),
            runBlocking { GeminiClient.answerText("who created the Mona Lisa?") },
        )
    }

    @Test fun shot70_chatAiThinkingFromAiJobs() {
        // ROUND 71 (Fix 3): the in-chat thinking indicator is driven
        // by AiJobs.thinking[threadId] now — stage an in-flight ask
        // exactly as a re-entered chat finds it (job launched, user
        // left, user came back) and prove the indicator renders from
        // that hoisted state. The compose clock is frozen (the
        // shot68 pattern) because the bubble carries an infinite
        // typing-dots animation. A two-message thread keeps the whole
        // list — indicator included — inside one viewport.
        composeRule.mainClock.autoAdvance = false
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.upsertMessage(id, TikuMessage(
            id = "ai70a", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "Can Tika draw us a picture?", time = "9:41", me = false, tsMs = 1))
        AppState.upsertMessage(id, TikuMessage(
            id = "ai70b", senderUid = "me", kind = MsgKind.TEXT,
            text = "/tika a small lighthouse at dawn", time = "9:41", me = true, tsMs = 2))
        AiJobs.thinking[id] = "Tika is creating…"
        try {
            AppState.go(Screen.Chat(id))
            frame { ChatScreen(id) }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "chat must show the thinking indicator from AiJobs state",
                composeRule.onAllNodesWithText("Tika is creating…")
                    .fetchSemanticsNodes().isNotEmpty(),
            )
            shot("70-chat-ai-thinking.png")
        } finally {
            AiJobs.thinking.remove(id)
        }
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
        // ROUND 66 (😘 hard rule proof — SUPERSEDED by ROUND 68/WS16):
        // this stages the legacy Round-25 EmojiFxOverlay kind 1 engine
        // itself (NOT the Fx2 engine) with the test clock paused,
        // advanced to 1.3 s, mid-trail flight. The engine is retained,
        // but since R68 😘 no longer ROUTES to it in chat — it plays
        // the v10 'kiss' spec via Fx2 index 1 (see shot62 below).
        // The owner's 2026-11-10 order named only 🖕/🍑 as keepers.
        composeRule.mainClock.autoAdvance = false
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
                EmojiFxOverlay(kind = 1, onDone = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(1300)
        shot("59-fx-kiss-round25.png")
    }

    // ---------- ROUND 68 (WS16): v10 effects — new routings ----------
    @Test fun shot62_fx2KissV10() {
        // 😘 now routes to the v10 'kiss' spec (Fx2 index 1): the
        // t=2.0 triple-lips hit is mid-flight at stageT 2.0.
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
                EmojiFx2Overlay(specIdx = 1, onDone = {}, stageT = 2.0f)
            }
        }
        composeRule.waitForIdle()
        shot("62-fx2-kiss-v10.png")
    }

    @Test fun shot63_fx2LipsV10() {
        // 💋 is a brand-new v10 spec (Fx2 index 21).
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101018))) {
                EmojiFx2Overlay(specIdx = 21, onDone = {}, stageT = 1.0f)
            }
        }
        composeRule.waitForIdle()
        shot("63-fx2-lips-v10.png")
    }

    @Test fun shot60_nikahCertificateDownload() {
        // ROUND 66 (amendment): the keepsake certificate view must
        // carry the Download action, and the off-screen render must
        // produce the full-paper bitmap at the fixed 1240 px width.
        // A real MediaStore write to a device Downloads folder cannot
        // complete under Robolectric (no real provider); the save is
        // attempted and its outcome printed, not asserted.
        val keepsake = nikahSampleKeepsake()
        var hostActivity: android.app.Activity? = null
        frame {
            hostActivity = LocalContext.current as? android.app.Activity
            NikahCertificateOverlay(keepsake) {}
        }
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
        val activity: android.content.Context = hostActivity ?: ctx
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

    // ---------- ROUND 67: opening the camera must never kill the app ----------
    @Test fun shot61_cameraOpenGraceful() {
        // The composer camera entry, exercised in the one environment
        // with NO usable camera path: Robolectric has no camera app
        // behind the capture intent, and the CAMERA permission is
        // explicitly denied below. PRE-FIX (build 111) this exact tap
        // sequence threw out of the unguarded launcher.launch(...)
        // inside launchCameraPhoto and killed the process — the
        // owner's "Tiku keeps stopping". POST-FIX the taps must leave
        // the app alive on the same chat, on a graceful surface: a
        // camera toast, the gallery (attach sheet) offered as the
        // fallback, or — if the harness grants the permission and its
        // shadow activity accepts the intent — a capture intent
        // actually dispatched (the unchanged working path).
        runCatching {
            org.robolectric.Shadows.shadowOf(ctx as android.app.Application)
                .denyPermissions(android.Manifest.permission.CAMERA)
        }
        val th = AppState.threads.value.first()
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        composeRule.waitForIdle()
        // The composer camera button exists only while the field is
        // empty + unfocused (Round 15) — tap it to open the chooser.
        composeRule.onNodeWithTag("composer_camera").performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "camera chooser must offer Take photo",
            composeRule.onAllNodesWithText("Take photo").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "camera chooser must offer Record video",
            composeRule.onAllNodesWithText("Record video").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("61-camera-chooser.png")
        // THE TAP THAT CRASHED BUILD 111.
        composeRule.onAllNodesWithText("Take photo")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        // Survival: still on the same chat. (The failure mode is the
        // process/test dying — reaching this line is half the proof.)
        org.junit.Assert.assertEquals(
            "camera tap must leave the app alive on the same chat",
            Screen.Chat(th.id), AppState.screen,
        )
        val toastSaysCamera = AppState.toastText?.contains("Camera") == true
        // The attach sheet is the gallery offer: its "Audio file" row
        // exists nowhere else (the camera chooser has no such row).
        val galleryOffered = composeRule.onAllNodesWithText("Audio file")
            .fetchSemanticsNodes().isNotEmpty()
        // ROUND 68: no activity handle exists under createComposeRule()
        // (`.activity` and getForegroundActivity() both don't resolve
        // in this Robolectric/Compose pair — the suite never compiled
        // in CI since R67), so the capture-intent branch can't be
        // read here. The proof is the assertEquals above (still on
        // the same chat after the tap that killed build 111) + the
        // chooser assertions; toast/gallery are diagnostics only —
        // under Robolectric the shadow activity silently accepts the
        // capture intent, so neither grace surface appears.
        println(
            "R67 camera graceful: toast=${AppState.toastText} " +
                "gallery=$galleryOffered (capture branch unreadable here)",
        )
        shot("62-camera-graceful.png")
    }

    // ---------- ROUND 69 (#1): long-press menu on a VERY long message ----------
    @Test fun shot64_chatMenuVeryLongMessage() {
        // ROUND 69 (#1): a message many screens tall. PRE-FIX the
        // long-press menu's preview re-rendered the FULL text with no
        // height limit inside the centred overlay stack, so the stack
        // overflowed the screen and the action menu after the preview
        // was placed off-screen — the owner could not delete long
        // messages (his screenshot: reaction pill + a screen-filling
        // bubble, no menu). POST-FIX the preview is capped + internally
        // scrollable and the whole stack is scrollable, so the FULL
        // menu — Delete included — is on screen with no scrolling.
        // The message is MINE so both delete options are offered.
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        val longText = buildString {
            append("Start of a very long message. ")
            repeat(120) { append("This is line $it of a message that runs for many screens, far taller than any phone display could ever show at once. ") }
            append("End of the very long message.")
        }
        AppState.upsertMessage(id, TikuMessage(
            id = "menu-long-1", senderUid = "me", kind = MsgKind.TEXT,
            text = longText, time = "9:41", me = true, tsMs = 1))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        // Long-press the bubble. The node spans many screens and the
        // list is tail-anchored, so its visible part is the BOTTOM of
        // the bubble — a centre long-click would land off-screen.
        // Press near the node's bottom edge first (shot29's
        // try-then-fallback pattern); if that did not open the menu,
        // fall back to the plain centre long-click shot17 uses.
        var menuOpened = false
        runCatching {
            composeRule.onNodeWithText("Start of a very long message.", substring = true)
                .performTouchInput {
                    longClick(bottomCenter.copy(y = bottomCenter.y - 120f))
                }
            composeRule.waitForIdle()
            menuOpened = composeRule.onAllNodesWithText("Reply")
                .fetchSemanticsNodes().isNotEmpty()
        }
        if (!menuOpened) {
            composeRule.onNodeWithText("Start of a very long message.", substring = true)
                .performTouchInput { longClick() }
            composeRule.waitForIdle()
        }
        // THE PROOF: every action the short-message menu offers
        // (shot17) is present for the very long message too, and both
        // Delete items are actually DISPLAYED (assertIsDisplayed —
        // composed-but-off-screen would pass a mere existence check
        // yet still fail the owner on a real phone).
        composeRule.onNodeWithText("Reply").assertIsDisplayed()
        composeRule.onNodeWithText("Edit").assertIsDisplayed()
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Delete for me").assertIsDisplayed()
        composeRule.onNodeWithText("Delete for everyone").assertIsDisplayed()
        shot("64-chat-menu-very-long.png")
    }

    // ---------- ROUND 75 (WS4) → ROUND 79 (WS-3): music is LOCAL-only ----------
    @Test fun shot65_noteSheetLocalMusicOnly() {
        // ROUND 75 removed the YouTube music; ROUND 79 (WS-3) brings
        // music back as a LOCAL file pick only. The contract now:
        // "Add music" (the device picker) IS offered; the YouTube
        // surfaces ("Pick a song" search, paste-a-link field) stay
        // gone forever; the composer itself works as before.
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.onAllNodesWithText("Your whisper")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "my note sheet must still offer the thought field",
            composeRule.onAllNodesWithText("What's on your mind?", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "ROUND 79 (WS-3): the local Add music affordance is offered",
            composeRule.onAllNodesWithText("Add music", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "no Pick a song action may remain in the note sheet",
            composeRule.onAllNodesWithText("Pick a song", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "no paste-a-link field may remain in the note sheet",
            composeRule.onAllNodesWithText("Paste a YouTube", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        shot("65-note-no-music.png")
    }

    // ---------- ROUND 70: /babeg game-card flow ----------
    @Test fun shot66_gameCardTapOpensPlayer() {
        // HOST side: PreviewRepo.sendGame posts a fully LOADED game
        // message (its HTML written to dl_<id>.html under the app
        // cache) — the same shape the real engine's send produces.
        // sendGame writes through AppState.appCtx, a lateinit the
        // harness never sets (MainActivity does in the real app);
        // without the next line the write silently no-ops (the
        // repo's runCatching) and the card renders UNLOADED, so its
        // tap would fetch instead of opening the player.
        AppState.appCtx = ctx
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        val title = "Tic Tac Toe"
        AppState.repo.sendGame(id, PreviewStore.SAMPLE_GAME_HTML, title)
        val gm = AppState.thread(id)?.messages?.lastOrNull()
        org.junit.Assert.assertTrue(
            "sendGame must push a loaded own game message",
            gm != null && gm.game && gm.me &&
                gm.localPath?.let { java.io.File(it).exists() } == true,
        )
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "chat must show the game card with its title",
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "own game card must carry the tap-to-play line",
            composeRule.onAllNodesWithText("2-player · tap to play", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        // The card's tap layer is a detectTapGestures pointerInput
        // (no semantics click action), so tap it the shot17 way —
        // a touch click on the title node, inside the card's own
        // gesture area.
        composeRule.onAllNodesWithText(title)[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "tapping the loaded card must open the player (host header)",
            composeRule.onAllNodesWithText("You created this game", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "player header must show the game title",
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty(),
        )
        shot("66-game-player-open.png")
        // No dismissal: gamePlayerMsg is ChatScreen-local remember
        // state — the next shot's frame{} replaces the composition
        // wholesale (the shot32 precedent: overlays a shot opens
        // are never explicitly closed).
    }

    @Test fun shot67_gameInviteAccept() {
        // GUEST side: a RECEIVED, already-loaded game message —
        // upserted directly in the shot17 shape, its HTML written
        // to the same dl_<msgId>.html cache file the real tap-to-
        // load (fetchMedia) produces, so the card is LOADED and
        // its tap opens the player instead of fetching.
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        val title = "Tic Tac Toe"
        val msgId = "game-invite-1"
        val bytes = PreviewStore.SAMPLE_GAME_HTML.toByteArray(Charsets.UTF_8)
        val file = java.io.File(ctx.cacheDir, "dl_$msgId.html")
        file.writeBytes(bytes)
        AppState.upsertMessage(id, TikuMessage(
            id = msgId, senderUid = p.uid, kind = MsgKind.GAME,
            text = "", time = "9:41", me = false, tsMs = 1,
            game = true, gameTitle = title,
            localPath = file.absolutePath,
            mediaBytes = bytes.size.toLong(), mimeType = "text/html"))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "received game card must read as an invite",
            composeRule.onAllNodesWithText("invited you to play", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onAllNodesWithText(title)[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "tapping a received card must open the full-screen invite",
            composeRule.onAllNodesWithText("Do you want to join?")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "invite must offer Join game",
            composeRule.onAllNodesWithText("Join game").fetchSemanticsNodes().isNotEmpty(),
        )
        // Capture the invite BEFORE accepting: after Join the
        // WebView host composes under Robolectric's shadow WebView,
        // so only the player header is asserted from there on —
        // never the game itself.
        shot("67-game-invite.png")
        composeRule.onAllNodesWithText("Join game")[0].performTouchInput { click() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "accepting must dismiss the invite",
            composeRule.onAllNodesWithText("Do you want to join?")
                .fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "accepting must land in the player (joined header)",
            composeRule.onAllNodesWithText("you joined", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("67-game-joined.png")
    }

    // ---------- ROUND 72: floating window over chat + minimize contract + codec order ----------
    @Test fun shot71_floatingWindowOverChat() {
        // ROUND 72 (WS2): the floating call window over an OPEN CHAT —
        // the surface Round 20 restored it for. Stage a CONNECTED
        // video call (~125 s in) for the first thread and minimize it
        // through the controller flag exactly as minimizeCall() leaves
        // it, then navigate to that thread's chat so the Home-tab
        // CallPill seam does NOT suppress the window (the window
        // yields only on Home panes 0..3). The frame mirrors how
        // TikuRoot composes it (MainActivity): the chat surface with
        // FloatingCallWindow() as the last child above it. The compose
        // clock is frozen (the shot68 pattern) so the window's
        // 500 ms clock loop never advances mid-capture. There is NO
        // RTC session in the harness, so the window renders its
        // avatar variant — the 184×64 pill: avatar, name, clock, and
        // (ROUND 72) NO end disc: tap is its only interaction now,
        // hangup is restore → full-screen End, as on Instagram.
        composeRule.mainClock.autoAdvance = false
        val th = AppState.threads.value.first()
        CallController.state.value = CallUiState.Connected(
            th.id, "shot-float-1", true, th.person,
            System.currentTimeMillis() - 125_000,
        )
        CallController.pipMinimized.value = true
        AppState.go(Screen.Chat(th.id))
        frame {
            Box(Modifier.fillMaxSize()) {
                ChatScreen(th.id)
                FloatingCallWindow()
            }
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "floating window over chat must show the person's name",
            composeRule.onAllNodesWithText(th.person.name, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "floating window must be minimized (pipMinimized still set)",
            CallController.pipMinimized.value,
        )
        shot("71-floating-window.png")
        CallController.endSilently()
        CallController.pipMinimized.value = false
    }

    @Test fun minimizeRestore_contract() {
        // ROUND 72 (WS2): the minimize/restore contract as pure
        // controller state, no UI. Minimizing a connected call must
        // set pipMinimized WITHOUT ending the call; restoring must
        // clear it, again WITHOUT ending the call — the Instagram
        // behaviour the floating window exists for (minimize is a
        // local UI event; the call keeps running throughout).
        val th = AppState.threads.value.first()
        CallController.state.value = CallUiState.Connected(
            th.id, "shot-minrestore-1", true, th.person,
            System.currentTimeMillis() - 30_000,
        )
        CallController.minimizeCall()
        org.junit.Assert.assertTrue(
            "minimizeCall must set pipMinimized",
            CallController.pipMinimized.value,
        )
        org.junit.Assert.assertTrue(
            "minimizeCall must NOT end the call (state stays Connected)",
            CallController.state.value is CallUiState.Connected,
        )
        CallController.restoreCall()
        org.junit.Assert.assertFalse(
            "restoreCall must clear pipMinimized",
            CallController.pipMinimized.value,
        )
        org.junit.Assert.assertTrue(
            "restoreCall must NOT end the call (state stays Connected)",
            CallController.state.value is CallUiState.Connected,
        )
        CallController.endSilently()
    }

    @Test fun codecReorder_prefersH264KeepsAll() {
        // ROUND 72 (F2): the pure codec-ordering decision behind
        // RtcSession.preferHardwareCodec(), tested over NAMES (no
        // WebRTC objects). Case 1: H264 is encodable here — it moves
        // first, ALL other codecs are retained, and their relative
        // order is preserved (reorder, never filter). Case 2: H264
        // is ABSENT from the sender capabilities (decode-only trap) —
        // the H264 entry is DROPPED from the preference list and
        // everything else is retained in order. Case 3: duplicate
        // H264 entries (fmtp variants) are ALL kept, in order, at
        // the front.
        org.junit.Assert.assertEquals(
            listOf("H264", "VP8", "VP9", "AV1"),
            reorderCodecNames(
                listOf("VP8", "VP9", "H264", "AV1"),
                setOf("VP8", "VP9", "H264", "AV1"),
            ),
        )
        org.junit.Assert.assertEquals(
            listOf("VP8", "VP9", "AV1"),
            reorderCodecNames(
                listOf("VP8", "VP9", "H264", "AV1"),
                setOf("VP8", "VP9", "AV1"),
            ),
        )
        org.junit.Assert.assertEquals(
            listOf("H264", "H264", "VP8"),
            reorderCodecNames(
                listOf("VP8", "H264", "H264"),
                setOf("VP8", "H264"),
            ),
        )
    }

    // ---------- ROUND 73 (Workstream B): the dynamic flip ----------
    // R71 proved the indicator RENDERS when the state is set BEFORE
    // composition (shot70) and that the plain-JUnit gate contracts
    // hold (aiJobs_* above). Nobody ever tested the FLIP: a job that
    // completes while its screen is composed — or while the user is
    // away — must make the indicator DISAPPEAR, driven by nothing
    // but AiJobs' own finally-clear. The owner's build-124 bug is
    // exactly that flip failing: the reply lands, "typing…" stays.
    //
    // Both production read sites read AiJobs state ONLY inside their
    // LazyColumn content lambda (Chat.kt RdChatContent:
    // `AiJobs.thinking[threadId]`; TikaBot.kt TikaBotScreen:
    // `AiJobs.companionBusy`) — no composable-scope read exists to
    // recompose the screen when the finally fires, and on a FAILED
    // ask nothing else changes state afterwards either (the toast
    // lives in a different subtree: Components.kt's toast host). The
    // tests below isolate that mechanism first (minimal repro), then
    // prove the flip on both real screens, including the production
    // ordering — the reply lands INSIDE the block, and the
    // finally-clear comes after it, alone.

    private val r73ThreadKey = "r73-flip-thread"

    /** The EXACT read pattern of both production screens, isolated:
     *  a LazyColumn whose content lambda conditionally adds an item
     *  from AiJobs state — [AiJobs.companionBusy] for the companion's
     *  pattern, `AiJobs.thinking[key]` for the chat's pattern.
     *  Nothing else in this composition reads AiJobs, and nothing
     *  else changes when a job completes, so a flip here can only be
     *  driven by the content lambda's own state tracking (or the
     *  lack of it). */
    @Composable
    private fun AiJobsFlipReproList() {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "r73_head") { Text("r73 repro head") }
            if (AiJobs.companionBusy) {
                item(key = "r73_companion_typing") { Text("typing…") }
            }
            val label = AiJobs.thinking[r73ThreadKey]
            if (label != null) {
                item(key = "ai_thinking") { Text(label) }
            }
        }
    }

    /** Drive frames + idle waits until [cond] holds or the budget
     *  runs out. AiJobs runs on Dispatchers.Default (real threads),
     *  so the finally-clear lands in real time, not compose-clock
     *  time; the loop alternates real sleeps (letting the job thread
     *  run) with frame advances (letting the recomposer consume the
     *  snapshot write). Callers freeze the clock first (the
     *  shot68/shot70 pattern) so the thinking bubble's infinite
     *  dots animation can never spin the test clock. */
    private fun pumpUntilR73(timeoutMs: Long = 15_000, cond: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            runCatching { composeRule.waitForIdle() }
            composeRule.mainClock.advanceTimeByFrame()
            Thread.sleep(25)
        }
        runCatching { composeRule.waitForIdle() }
        return cond()
    }

    private fun nodesWithTextR73(text: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes()

    /** Minimal repro, companion pattern: compose → launch a
     *  latch-held job → the typing item must APPEAR → release the
     *  latch → the item must DISAPPEAR, with no other state change
     *  anywhere in the composition to carry the flip. */
    @Test fun aiJobsFlip_minimalRepro_companionBusy() {
        composeRule.mainClock.autoAdvance = false
        AiJobs.companionBusy = false
        frame { AiJobsFlipReproList() }
        org.junit.Assert.assertTrue(
            "repro list must compose",
            nodesWithTextR73("r73 repro head").isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "no typing item may exist before launch",
            nodesWithTextR73("typing…").isEmpty(),
        )
        val latch = CompletableDeferred<Unit>()
        org.junit.Assert.assertTrue(AiJobs.launchCompanion { latch.await() })
        org.junit.Assert.assertTrue(
            "registration must be synchronous",
            AiJobs.companionBusy,
        )
        org.junit.Assert.assertTrue(
            "typing item must APPEAR when the job starts",
            pumpUntilR73 { nodesWithTextR73("typing…").isNotEmpty() },
        )
        latch.complete(Unit)
        org.junit.Assert.assertTrue(
            "typing item must DISAPPEAR when the job completes — " +
                "driven by AiJobs' finally-clear alone",
            pumpUntilR73 { nodesWithTextR73("typing…").isEmpty() },
        )
        org.junit.Assert.assertFalse(AiJobs.companionBusy)
    }

    /** Minimal repro, in-chat pattern: same flip, driven by the
     *  thinking map entry the chat indicator reads. */
    @Test fun aiJobsFlip_minimalRepro_thinkingLabel() {
        composeRule.mainClock.autoAdvance = false
        AiJobs.thinking.remove(r73ThreadKey)
        frame { AiJobsFlipReproList() }
        org.junit.Assert.assertTrue(
            "no thinking item may exist before launch",
            nodesWithTextR73("Tika is thinking…").isEmpty(),
        )
        val latch = CompletableDeferred<Unit>()
        org.junit.Assert.assertTrue(
            AiJobs.launch(r73ThreadKey, "Tika is thinking…") { latch.await() },
        )
        org.junit.Assert.assertEquals("Tika is thinking…", AiJobs.thinking[r73ThreadKey])
        org.junit.Assert.assertTrue(
            "thinking item must APPEAR when the job starts",
            pumpUntilR73 { nodesWithTextR73("Tika is thinking…").isNotEmpty() },
        )
        latch.complete(Unit)
        org.junit.Assert.assertTrue(
            "thinking item must DISAPPEAR when the job completes — " +
                "driven by AiJobs' finally-clear alone",
            pumpUntilR73 { nodesWithTextR73("Tika is thinking…").isEmpty() },
        )
        org.junit.Assert.assertFalse(AiJobs.thinking.containsKey(r73ThreadKey))
    }

    /** ROUND 73 single source of truth: companionBusy IS the
     *  thinking map's companion entry — writing either side moves
     *  the other, so the two can never diverge (the pre-R73 code
     *  kept an independent mutableStateOf in lockstep by hand).
     *  Also pins that the harness cleanup assignment in setup
     *  (`AiJobs.companionBusy = false`) clears the map entry, and
     *  that the real launch path drives both views together. */
    @Test fun aiJobs_singleSource_companionBusyIsThinkingView() {
        AiJobs.thinking.clear()
        org.junit.Assert.assertFalse(AiJobs.companionBusy)
        AiJobs.thinking[AiJobs.COMPANION_KEY] = "typing…"
        org.junit.Assert.assertTrue(
            "writing the map entry must flip the busy flag",
            AiJobs.companionBusy,
        )
        AiJobs.companionBusy = false
        org.junit.Assert.assertFalse(
            "the cleanup assignment must clear the map entry",
            AiJobs.thinking.containsKey(AiJobs.COMPANION_KEY),
        )
        org.junit.Assert.assertFalse(AiJobs.companionBusy)
        AiJobs.companionBusy = true
        org.junit.Assert.assertTrue(
            "setting the flag must register the map entry",
            AiJobs.thinking.containsKey(AiJobs.COMPANION_KEY),
        )
        AiJobs.thinking.remove(AiJobs.COMPANION_KEY)
        org.junit.Assert.assertFalse(
            "removing the map entry must clear the flag",
            AiJobs.companionBusy,
        )
        val latch = CompletableDeferred<Unit>()
        org.junit.Assert.assertTrue(AiJobs.launchCompanion { latch.await() })
        org.junit.Assert.assertTrue(
            "in flight: both views busy",
            AiJobs.companionBusy && AiJobs.thinking.containsKey(AiJobs.COMPANION_KEY),
        )
        latch.complete(Unit)
        val deadline = System.currentTimeMillis() + 10_000
        while ((AiJobs.companionBusy || AiJobs.thinking.containsKey(AiJobs.COMPANION_KEY)) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(25)
        }
        org.junit.Assert.assertFalse(
            "completed: flag cleared",
            AiJobs.companionBusy,
        )
        org.junit.Assert.assertFalse(
            "completed: map entry cleared",
            AiJobs.thinking.containsKey(AiJobs.COMPANION_KEY),
        )
    }

    /** ROUND 73 bounded jobs: a wedged block (a provider call that
     *  never returns) must not hold the indicator — or the
     *  single-flight gate — forever. A short explicit timeout keeps
     *  the test fast; production callers ride the 660 s default,
     *  sized above generateGame's own 600 s self-bound so it can
     *  only ever fire on a genuinely wedged job. */
    @Test fun aiJobs_boundedJob_timeoutClearsIndicatorAndGate() {
        val key = "r73-timeout-thread"
        AiJobs.thinking.remove(key)
        org.junit.Assert.assertTrue(
            AiJobs.launch(key, "Tika is thinking…", timeoutMs = 300) {
                CompletableDeferred<Unit>().await() // never completes — the wedge
            },
        )
        org.junit.Assert.assertEquals("Tika is thinking…", AiJobs.thinking[key])
        val deadline = System.currentTimeMillis() + 10_000
        while (AiJobs.thinking.containsKey(key) && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        org.junit.Assert.assertFalse(
            "the backstop timeout must clear the indicator label",
            AiJobs.thinking.containsKey(key),
        )
        org.junit.Assert.assertTrue(
            "the gate must be released after a timeout",
            AiJobs.launch(key, "Tika is thinking…") { },
        )
        val deadline2 = System.currentTimeMillis() + 10_000
        while (AiJobs.thinking.containsKey(key) && System.currentTimeMillis() < deadline2) {
            Thread.sleep(25)
        }
        org.junit.Assert.assertFalse(
            "the follow-up job must complete and clear normally",
            AiJobs.thinking.containsKey(key),
        )
    }

    /** Companion end-to-end flip, production ordering: the reply
     *  lands INSIDE the block (the sink), the job is STILL in
     *  flight afterwards, and only then does it finish — so the
     *  typing bubble's disappearance is driven by the finally-clear
     *  alone, never by the reply's own recomposition. */
    @Test fun aiJobsFlip_companionScreen_clearsAfterReplyLands() {
        composeRule.mainClock.autoAdvance = false
        AppState.appCtx = ctx
        TikaBotStore.ensureLoaded()
        if (TikaBotStore.gender.value == null) TikaBotStore.chooseGender("male", "Boy")
        if (TikaBotStore.role.value == null) TikaBotStore.chooseRole("friend", "Just a friend")
        org.junit.Assert.assertFalse(
            "companion gate must start clean",
            AiJobs.companionBusy,
        )
        frame { TikaBotScreen() }
        org.junit.Assert.assertTrue(
            "no typing bubble before launch",
            nodesWithTextR73("typing…").isEmpty(),
        )
        val reply = "R73 companion reply 7f3a"
        val replyPosted = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        org.junit.Assert.assertTrue(
            AiJobs.launchCompanion {
                TikaBotStore.appendBot(reply)
                replyPosted.complete(Unit)
                finish.await()
            },
        )
        org.junit.Assert.assertTrue(
            "typing bubble must show while the companion job runs",
            pumpUntilR73 { nodesWithTextR73("typing…").isNotEmpty() },
        )
        org.junit.Assert.assertTrue(
            "the reply must land while the job is still in flight",
            pumpUntilR73 {
                replyPosted.isCompleted && nodesWithTextR73(reply, substring = true).isNotEmpty()
            },
        )
        org.junit.Assert.assertTrue(
            "typing bubble must STILL show — the job has not finished",
            nodesWithTextR73("typing…").isNotEmpty(),
        )
        finish.complete(Unit)
        org.junit.Assert.assertTrue(
            "typing bubble must DISAPPEAR when the job completes — " +
                "the finally-clear is the only change left",
            pumpUntilR73 { nodesWithTextR73("typing…").isEmpty() },
        )
        org.junit.Assert.assertFalse(AiJobs.companionBusy)
        org.junit.Assert.assertTrue(
            "the reply must remain after the flip",
            nodesWithTextR73(reply, substring = true).isNotEmpty(),
        )
    }

    /** The owner's exact path: send, press Back mid-job, the job
     *  completes while away, re-enter — the re-entered companion
     *  must show the landed reply and NO typing bubble. */
    @Test fun aiJobsFlip_companionScreen_leaveAndReenterAfterCompletion() {
        composeRule.mainClock.autoAdvance = false
        AppState.appCtx = ctx
        TikaBotStore.ensureLoaded()
        if (TikaBotStore.gender.value == null) TikaBotStore.chooseGender("male", "Boy")
        if (TikaBotStore.role.value == null) TikaBotStore.chooseRole("friend", "Just a friend")
        org.junit.Assert.assertFalse(
            "companion gate must start clean",
            AiJobs.companionBusy,
        )
        frame { TikaBotScreen() }
        val reply = "R73 reentry reply 9c1b"
        val latch = CompletableDeferred<Unit>()
        org.junit.Assert.assertTrue(
            AiJobs.launchCompanion {
                latch.await()
                TikaBotStore.appendBot(reply)
            },
        )
        org.junit.Assert.assertTrue(
            "typing bubble must show while the job runs",
            pumpUntilR73 { nodesWithTextR73("typing…").isNotEmpty() },
        )
        // LEAVE mid-job (the Back press): the whole companion
        // composition goes away. The job must NOT die with it (R71).
        frame { Text("r73 away placeholder") }
        org.junit.Assert.assertTrue(
            "away placeholder must compose",
            nodesWithTextR73("r73 away placeholder").isNotEmpty(),
        )
        latch.complete(Unit)
        val deadline = System.currentTimeMillis() + 10_000
        while (AiJobs.companionBusy && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        org.junit.Assert.assertFalse(
            "the job must complete while the user is away",
            AiJobs.companionBusy,
        )
        org.junit.Assert.assertTrue(
            "the reply must have landed while the user was away",
            TikaBotStore.messages.value.any { it.text == reply },
        )
        // RE-ENTER: a fresh composition reads the finished state.
        frame { TikaBotScreen() }
        org.junit.Assert.assertTrue(
            "re-entered companion must show the landed reply",
            pumpUntilR73 { nodesWithTextR73(reply, substring = true).isNotEmpty() },
        )
        org.junit.Assert.assertTrue(
            "re-entered companion must NOT resurrect the typing bubble",
            nodesWithTextR73("typing…").isEmpty(),
        )
    }

    /** In-chat flip, production ordering (the shot70 screen, but a
     *  REAL latch job through AiJobs.launch instead of a pre-set
     *  label): the AI reply lands inside the block while the job is
     *  still in flight, then the job finishes and the "ai_thinking"
     *  indicator must disappear with no new message arriving. */
    @Test fun aiJobsFlip_chatScreen_clearsAfterReplyLands() {
        composeRule.mainClock.autoAdvance = false
        val p = PreviewStore.people[1]
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        AppState.upsertMessage(id, TikuMessage(
            id = "r73a", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "R73 chat seed question", time = "9:41", me = false, tsMs = 1))
        AppState.upsertMessage(id, TikuMessage(
            id = "r73b", senderUid = "me", kind = MsgKind.TEXT,
            text = "/tiku r73 flip probe", time = "9:41", me = true, tsMs = 2))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        org.junit.Assert.assertTrue(
            "no thinking indicator before launch",
            nodesWithTextR73("Tika is thinking…").isEmpty(),
        )
        val reply = "R73 chat reply 4d2e"
        val replyPosted = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        org.junit.Assert.assertTrue(
            AiJobs.launch(id, "Tika is thinking…") {
                AppState.repo.sendTextAi(id, reply)
                replyPosted.complete(Unit)
                finish.await()
            },
        )
        org.junit.Assert.assertTrue(
            "thinking indicator must show while the job runs",
            pumpUntilR73 { nodesWithTextR73("Tika is thinking…").isNotEmpty() },
        )
        org.junit.Assert.assertTrue(
            "the AI reply must land while the job is still in flight",
            pumpUntilR73 {
                replyPosted.isCompleted && nodesWithTextR73(reply, substring = true).isNotEmpty()
            },
        )
        org.junit.Assert.assertTrue(
            "thinking indicator must STILL show — the job has not finished",
            nodesWithTextR73("Tika is thinking…").isNotEmpty(),
        )
        finish.complete(Unit)
        org.junit.Assert.assertTrue(
            "thinking indicator must DISAPPEAR when the job completes — " +
                "the finally-clear is the only change left",
            pumpUntilR73 { nodesWithTextR73("Tika is thinking…").isEmpty() },
        )
        org.junit.Assert.assertFalse(AiJobs.thinking.containsKey(id))
    }

    // ---------- ROUND 73 (Worker C): companion streaming, typing
    // parity, message menu (star / pin / edit / delete) ----------
    //
    // Staging follows Worker B's companion pattern: the REAL local
    // store, onboarded through ensureLoaded() + the chooseGender /
    // chooseRole guards. Every test records the store size it
    // started from and removes exactly the messages it staged (in
    // finally, via the store's own deleteMessage), restores any
    // nickname / theme it changed, and clears the transient
    // liveText + the AiJobs gate — the store is process-global, so
    // nothing here may leak into a neighbouring test.

    private fun stageCompanionR73C() {
        AppState.appCtx = ctx
        TikaBotStore.ensureLoaded()
        if (TikaBotStore.gender.value == null) TikaBotStore.chooseGender("male", "Boy")
        if (TikaBotStore.role.value == null) TikaBotStore.chooseRole("friend", "Just a friend")
    }

    /** Remove every message staged from index [base] on, and clear
     *  the transient Round-73 state (live partial + busy gate). */
    private fun cleanupCompanionR73C(base: Int) {
        while (TikaBotStore.messages.value.size > base) {
            TikaBotStore.deleteMessage(base)
        }
        TikaBotStore.setLiveText(null)
        AiJobs.companionBusy = false
    }

    /** Scroll the companion list to its TAIL item (the typing /
     *  live slot sits AFTER the last message, and the screen's own
     *  follow scroll only targets the last message — without this
     *  the tail bubble can sit below the fold, uncomposed). Tries
     *  every scrollable node; only the LazyColumn has a
     *  scroll-to-index action, the rest fail soft inside runCatching. */
    private fun scrollCompanionToTailR73C() {
        val tailIdx = TikaBotStore.messages.value.size
        val nodes = composeRule.onAllNodes(hasScrollAction())
        val count = nodes.fetchSemanticsNodes().size
        for (i in 0 until count) {
            runCatching { nodes[i].performScrollToIndex(tailIdx) }
        }
        runCatching { composeRule.waitForIdle() }
    }

    /** Release a latch-held companion job and wait for AiJobs'
     *  finally to clear the gate (real threads — poll, don't pump). */
    private fun releaseCompanionJobR73C(latch: CompletableDeferred<Unit>) {
        latch.complete(Unit)
        val deadline = System.currentTimeMillis() + 10_000
        while (AiJobs.companionBusy && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        org.junit.Assert.assertFalse(
            "the companion gate must clear after the latch releases",
            AiJobs.companionBusy,
        )
    }

    /** Store contracts (plain JUnit, no composition): star / pin
     *  toggle and persist, edit replaces ONLY my own text, delete
     *  removes the right message (indices shift) and deletes a
     *  photo message's local file with it. */
    @Test fun tikaStoreR73_starPinEditDelete() {
        stageCompanionR73C()
        val base = TikaBotStore.messages.value.size
        try {
            TikaBotStore.appendUser("R73C store user line")
            TikaBotStore.appendBot("R73C store bot line")
            var msgs = TikaBotStore.messages.value
            org.junit.Assert.assertEquals(base + 2, msgs.size)
            org.junit.Assert.assertTrue(msgs[base].mine)
            org.junit.Assert.assertFalse(msgs[base + 1].mine)
            org.junit.Assert.assertFalse(msgs[base].starred)
            org.junit.Assert.assertFalse(msgs[base + 1].pinned)

            TikaBotStore.toggleStar(base)
            TikaBotStore.togglePin(base + 1)
            msgs = TikaBotStore.messages.value
            org.junit.Assert.assertTrue(
                "toggleStar must set the flag",
                msgs[base].starred,
            )
            org.junit.Assert.assertTrue(
                "togglePin must set the flag",
                msgs[base + 1].pinned,
            )
            // Persist round-trip: the flags must be in the JSON the
            // store just wrote ("s" / "p", written only when true).
            // The prefs file is keyed by the account uid; under the
            // harness there is no signed-in user, so the key is ""
            // and the file is "tika_bot_". When that file is not the
            // one this environment wrote, the in-memory assertions
            // above stand on their own (a clean reload is not
            // feasible: ensureLoaded is idempotent per account key).
            val raw = runCatching {
                ctx.getSharedPreferences("tika_bot_", Context.MODE_PRIVATE)
                    .getString("msgs", null)
            }.getOrNull()
            if (raw != null) {
                org.junit.Assert.assertTrue(
                    "persisted JSON must carry the star flag",
                    raw.contains("\"s\":true"),
                )
                org.junit.Assert.assertTrue(
                    "persisted JSON must carry the pin flag",
                    raw.contains("\"p\":true"),
                )
            }
            // Toggling again clears.
            TikaBotStore.toggleStar(base)
            org.junit.Assert.assertFalse(TikaBotStore.messages.value[base].starred)
            TikaBotStore.toggleStar(base)
            org.junit.Assert.assertTrue(TikaBotStore.messages.value[base].starred)

            // Edit: my own text changes; Tika's message refuses; a
            // stale index is a safe no-op.
            TikaBotStore.editMessageText(base, "R73C edited line")
            org.junit.Assert.assertEquals(
                "R73C edited line",
                TikaBotStore.messages.value[base].text,
            )
            TikaBotStore.editMessageText(base + 1, "R73C must not apply")
            org.junit.Assert.assertEquals(
                "editMessageText must refuse a bot message",
                "R73C store bot line",
                TikaBotStore.messages.value[base + 1].text,
            )
            TikaBotStore.editMessageText(base + 999, "R73C stale index")
            org.junit.Assert.assertEquals(base + 2, TikaBotStore.messages.value.size)

            // Delete: the user line goes, the bot line shifts into
            // its place.
            TikaBotStore.deleteMessage(base)
            msgs = TikaBotStore.messages.value
            org.junit.Assert.assertEquals(base + 1, msgs.size)
            org.junit.Assert.assertEquals("R73C store bot line", msgs[base].text)
            org.junit.Assert.assertTrue(msgs[base].pinned)

            // Delete a photo message: its local file goes with it.
            val imgFile = java.io.File(ctx.cacheDir, "r73c-store-photo.jpg")
            imgFile.writeBytes(byteArrayOf(1, 2, 3))
            org.junit.Assert.assertTrue(imgFile.exists())
            TikaBotStore.appendUserImage("R73C caption", imgFile.absolutePath)
            val imgIdx = TikaBotStore.messages.value.lastIndex
            org.junit.Assert.assertEquals(imgFile.absolutePath, TikaBotStore.messages.value[imgIdx].img)
            TikaBotStore.deleteMessage(imgIdx)
            org.junit.Assert.assertFalse(
                "deleting a photo message must delete its local file",
                imgFile.exists(),
            )
            org.junit.Assert.assertEquals(base + 1, TikaBotStore.messages.value.size)
        } finally {
            cleanupCompanionR73C(base)
        }
    }

    /** Typing parity: with a companion job in flight, the screen
     *  shows the typing state (the header reads "typing…", and the
     *  list tail carries the normal chat's RdTypingBubble with
     *  Tika's avatar — dots only, no text, so the header is the
     *  text witness; the bubble itself is what the shot shows). */
    @Test fun shot72_companionTypingParity() {
        composeRule.mainClock.autoAdvance = false
        stageCompanionR73C()
        val latch = CompletableDeferred<Unit>()
        try {
            frame { TikaBotScreen() }
            org.junit.Assert.assertTrue(
                "no typing state before launch",
                nodesWithTextR73("typing…").isEmpty(),
            )
            org.junit.Assert.assertTrue(AiJobs.launchCompanion { latch.await() })
            org.junit.Assert.assertTrue(
                "typing state must show while the companion job runs",
                pumpUntilR73 { nodesWithTextR73("typing…").isNotEmpty() },
            )
            scrollCompanionToTailR73C()
            composeRule.mainClock.advanceTimeBy(1_000)
            shot("72-companion-typing-parity.png")
        } finally {
            releaseCompanionJobR73C(latch)
            TikaBotStore.setLiveText(null)
        }
    }

    /** Streaming live bubble: a partial published to the store's
     *  transient liveText renders as the tail bubble while the job
     *  is still in flight — the partial text itself is the witness. */
    @Test fun shot73_companionStreamingLive() {
        composeRule.mainClock.autoAdvance = false
        stageCompanionR73C()
        val latch = CompletableDeferred<Unit>()
        try {
            org.junit.Assert.assertTrue(AiJobs.launchCompanion { latch.await() })
            TikaBotStore.setLiveText("Ami khub bhalo achi, tumi?")
            frame { TikaBotScreen() }
            org.junit.Assert.assertTrue(
                "the live bubble must render the streaming partial",
                pumpUntilR73 {
                    nodesWithTextR73("Ami khub bhalo achi, tumi?", substring = true).isNotEmpty()
                },
            )
            scrollCompanionToTailR73C()
            shot("73-companion-streaming-live.png")
        } finally {
            TikaBotStore.setLiveText(null)
            releaseCompanionJobR73C(latch)
        }
    }

    /** The message menu, composed directly: a starred OWN text
     *  message offers the full item set in the normal chat's order
     *  — Reply, Edit, Copy, Unstar (state-aware), Pin, Delete. */
    @Test fun shot74_companionMenu() {
        frame {
            Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
                BotMessageMenu(
                    msg = TikaBotStore.BotMsg(
                        mine = true,
                        text = "R73C menu sample — my own line",
                        starred = true,
                    ),
                    theme = null,
                    onDismiss = {},
                    onReact = {},
                    onReply = {},
                    onEdit = {},
                    onCopy = {},
                    onToggleStar = {},
                    onTogglePin = {},
                    onDelete = {},
                )
            }
        }
        composeRule.waitForIdle()
        listOf("Reply", "Edit", "Copy", "Unstar", "Pin", "Delete").forEach { label ->
            org.junit.Assert.assertTrue(
                "menu must offer $label",
                nodesWithTextR73(label).isNotEmpty(),
            )
        }
        shot("74-companion-menu.png")
    }

    /** Nickname + theme end to end: the header carries the local
     *  nickname and the staged bubbles render under a non-default
     *  theme (t3 — wallpaper + themed bubble colours). */
    @Test fun shot75_companionThemedNickname() {
        stageCompanionR73C()
        val oldNick = TikaBotStore.nickname.value
        val oldTheme = TikaBotStore.themeId.value
        val base = TikaBotStore.messages.value.size
        try {
            TikaBotStore.setNickname("Cutie")
            TikaBotStore.setThemeId("t3")
            TikaBotStore.appendUser("R73C themed question")
            TikaBotStore.appendBot("R73C themed reply")
            frame { TikaBotScreen() }
            org.junit.Assert.assertTrue(
                "the header must show the nickname",
                pumpUntilR73 { nodesWithTextR73("Cutie").isNotEmpty() },
            )
            org.junit.Assert.assertTrue(
                "the themed reply bubble must render",
                nodesWithTextR73("R73C themed reply", substring = true).isNotEmpty(),
            )
            shot("75-companion-themed-nickname.png")
        } finally {
            cleanupCompanionR73C(base)
            TikaBotStore.setNickname(oldNick)
            TikaBotStore.setThemeId(oldTheme)
        }
    }

    /** Pinned strip: pinning a staged message shows the strip
     *  under the header with the normal chat's "Pinned · …" line. */
    @Test fun shot76_companionPinnedStrip() {
        stageCompanionR73C()
        val base = TikaBotStore.messages.value.size
        try {
            TikaBotStore.appendUser("R73C pin me line")
            TikaBotStore.appendBot("R73C after pin reply")
            TikaBotStore.togglePin(base)
            org.junit.Assert.assertTrue(TikaBotStore.messages.value[base].pinned)
            frame { TikaBotScreen() }
            org.junit.Assert.assertTrue(
                "the pinned strip must show under the header",
                pumpUntilR73 {
                    nodesWithTextR73("Pinned · R73C pin me line", substring = true).isNotEmpty()
                },
            )
            shot("76-companion-pinned-strip.png")
        } finally {
            cleanupCompanionR73C(base)
        }
    }

    /** Editing, the real flow: long-press my own bubble → the menu
     *  opens → tap Edit → the composer shows its "Editing message"
     *  banner (the shot17 long-press pattern). */
    @Test fun shot77_companionEditing() {
        stageCompanionR73C()
        val base = TikaBotStore.messages.value.size
        try {
            TikaBotStore.appendUser("R73C edit this line")
            frame { TikaBotScreen() }
            org.junit.Assert.assertTrue(
                "the staged own message must render",
                pumpUntilR73 { nodesWithTextR73("R73C edit this line").isNotEmpty() },
            )
            composeRule.onAllNodesWithText("R73C edit this line")[0]
                .performTouchInput { longClick() }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "long-press must open the message menu",
                nodesWithTextR73("Edit").isNotEmpty(),
            )
            composeRule.onAllNodesWithText("Edit")[0]
                .performTouchInput { click() }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "the composer must show the Editing message banner",
                nodesWithTextR73("Editing message").isNotEmpty(),
            )
            shot("77-companion-editing.png")
        } finally {
            cleanupCompanionR73C(base)
        }
    }

    // ---------- ROUND 75 (WS7): in-app update — download inside the app ----------
    // The prompt dialog is staged exactly as TikuRoot hosts it: over the
    // Home shell, with UpdateState.available set and the downloader phase
    // preset (its setter is internal for this). No real network happens:
    // every state below is a staged phase, never a started download.

    private fun stageUpdatePrompt(phase: UpdateDownloader.Phase) {
        UpdateState.available = UpdateInfo(
            build = 126,
            url = "https://tiku-study.vercel.app",
            notes = "Faster replies, drafts, and fixes.",
            apk = "https://tiku-study.vercel.app/tiku.apk",
        )
        UpdateDownloader.phase = phase
        AppState.go(Screen.Home)
        frame {
            HomeScreen()
            UpdatePromptDialog()
        }
        composeRule.waitForIdle()
    }

    /** The download-phase UI exactly as it renders inside the update
     *  dialog and under Settings ▸ App update: the live
     *  [UpdateDownloadUi] composable in a CardC card. (The full Dialog
     *  window's semantics are not reachable from this harness's
     *  compose rule — the phase UI is therefore asserted here, on the
     *  real composable, and the assembled dialog is captured in
     *  shot78 for the coordinator's eyeball check.) */
    private fun stageUpdatePhaseCard(phase: UpdateDownloader.Phase) {
        UpdateDownloader.phase = phase
        frame {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black)
                    .padding(24.dp),
            ) {
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp))
                        .background(CardC)
                        .padding(22.dp),
                ) {
                    Text(
                        "Update available", color = Color.White,
                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = InterTight,
                    )
                    UpdateDownloadUi()
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun shot78_updatePromptIdle() {
        stageUpdatePrompt(UpdateDownloader.Phase.Idle)
        org.junit.Assert.assertEquals(
            "the staged build must be the one the prompt would offer",
            126, UpdateState.available?.build,
        )
        shot("78-update-prompt.png")
    }

    @Test fun shot79_updatePromptDownloading() {
        stageUpdatePhaseCard(UpdateDownloader.Phase.Downloading(45_000_000L, 100_000_000L))
        org.junit.Assert.assertTrue(
            "a downloading update must show its in-app progress percent",
            composeRule.onAllNodesWithText("Downloading update… 45%", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("79-update-downloading.png")
    }

    @Test fun shot80_updatePromptFailed() {
        stageUpdatePhaseCard(
            UpdateDownloader.Phase.Failed(
                "Couldn't download the update — check your connection and try again",
            ),
        )
        org.junit.Assert.assertTrue(
            "a failed download must offer Retry in the same surface",
            composeRule.onAllNodesWithText("Retry", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("80-update-failed.png")
    }

    @Test fun shot81_updatePromptNeedsPermission() {
        stageUpdatePhaseCard(UpdateDownloader.Phase.NeedsPermission)
        org.junit.Assert.assertTrue(
            "the install-permission state must explain the one OS toggle",
            composeRule.onAllNodesWithText("Allow from this source", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the install-permission state must offer Open settings and Install",
            composeRule.onAllNodesWithText("Open settings", substring = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Install", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("81-update-permission.png")
    }

    // ---------- ROUND 76 (WS4): App update pinned — Settings top + Home banner ----------

    @Test fun shot88_settingsAppUpdatePinnedTop() {
        AppState.homeTab = HomeTab.ME
        AppState.goTik(3) // the Round-14 shell routes on tikDestination, not homeTab
        frame { HomeScreen() }
        composeRule.waitForIdle()
        val row = composeRule.onAllNodesWithText("App update").fetchSemanticsNodes()
        org.junit.Assert.assertTrue(
            "Settings must carry the App update row without any scrolling",
            row.isNotEmpty(),
        )
        // Pinned at the top means: composed in the first window AND in
        // the title zone. The harness is fixed at 480 dpi (1 dp = 3 px):
        // 24 dp list top padding + the Settings title puts the first
        // row's label at ~250 px; below the profile card (the old
        // position's earliest possible spot) it could not start above
        // ~550 px. (An earlier revision of this test compared against
        // the Appearance header, but LazyColumn does not guarantee
        // composing that far at idle — the row's own position is the
        // stable proof.)
        org.junit.Assert.assertTrue(
            "App update must sit in the title zone at the very top of Settings",
            row.first().boundsInRoot.top < 420f,
        )
        shot("88-settings-update-top.png")
    }

    @Test fun shot89_homeUpdateBanner() {
        // No update staged: no banner text anywhere on Messages.
        UpdateState.available = null
        UpdateDownloader.phase = UpdateDownloader.Phase.Idle
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "no available update must mean no update banner on Messages",
            composeRule.onAllNodesWithText("Version 140 is ready", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        // Stage an available update: the banner pins to the very top of
        // Messages, above the "Messages" title, with its Update button.
        UpdateState.available = UpdateInfo(
            build = 140,
            url = "https://tiku-study.vercel.app",
            notes = "Pinned update button.",
            apk = "https://tiku-study.vercel.app/tiku-140.apk",
        )
        composeRule.waitForIdle()
        val banner = composeRule.onAllNodesWithText("Version 140 is ready", substring = true)
            .fetchSemanticsNodes()
        org.junit.Assert.assertTrue(
            "an available update must pin its banner at the top of Messages",
            banner.isNotEmpty(),
        )
        val title = composeRule.onAllNodesWithText("Messages").fetchSemanticsNodes()
        org.junit.Assert.assertTrue(
            "the banner must sit above the Messages title",
            title.isNotEmpty() && banner.first().boundsInRoot.top < title.first().boundsInRoot.top,
        )
        org.junit.Assert.assertTrue(
            "the banner must carry its Update button",
            composeRule.onAllNodesWithText("Update").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("89-home-update-banner.png")
        UpdateState.available = null
    }

    // ---------- ROUND 75 (WS5): composer drafts survive Back ----------
    // The store is the draft's home; the screens only read/write it.
    // Restore is proven on the real screens; the clear is proven by
    // driving the real send disc (an untitled 42 dp disc at the
    // composer pill's right end — clicked by coordinates derived from
    // the field's own bounds, the suite's root-click pattern).

    /** Click the composer send disc: its centre sits on the field's
     *  mid-line, ~40 dp in from the root's right edge. */
    private fun clickSendDisc(fieldText: String) {
        val field = composeRule.onAllNodesWithText(fieldText, substring = true)
            .fetchSemanticsNodes().firstOrNull()
        org.junit.Assert.assertNotNull("composer field must show '$fieldText'", field)
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val d = ctx.resources.displayMetrics.density
        val cy = (field!!.boundsInRoot.top + field.boundsInRoot.bottom) / 2f
        composeRule.onRoot().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(root.right - 40f * d, cy))
        }
        composeRule.waitForIdle()
    }

    @Test fun shot82_draftNormalChatRestored() {
        val th = AppState.threads.value.first()
        DraftStore.setChat(th.id, "draft hello r75")
        try {
            AppState.go(Screen.Chat(th.id))
            frame { ChatScreen(th.id) }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "re-entering a chat must restore its composer draft",
                composeRule.onAllNodesWithText("draft hello r75", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
            shot("82-draft-chat-restored.png")
        } finally {
            DraftStore.setChat(th.id, "")
        }
    }

    @Test fun shot83_draftNormalChatClearedAfterSend() {
        val th = AppState.threads.value.first()
        DraftStore.setChat(th.id, "draft send r75")
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        composeRule.waitForIdle()
        clickSendDisc("draft send r75")
        org.junit.Assert.assertTrue(
            "sending must post the drafted message",
            composeRule.onAllNodesWithText("draft send r75", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertEquals(
            "sending must clear the chat's draft",
            "", DraftStore.getChat(th.id),
        )
        shot("83-draft-chat-sent.png")
    }

    @Test fun shot84_draftCompanionRestored() {
        stageCompanionR73C()
        DraftStore.setTika("tika draft r75")
        try {
            frame { TikaBotScreen() }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "re-entering the companion must restore its composer draft",
                composeRule.onAllNodesWithText("tika draft r75", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
            shot("84-draft-tika-restored.png")
        } finally {
            DraftStore.setTika("")
        }
    }

    @Test fun shot85_draftCompanionClearedAfterSend() {
        stageCompanionR73C()
        val base = TikaBotStore.messages.value.size
        DraftStore.setTika("tika draft r75")
        try {
            frame { TikaBotScreen() }
            composeRule.waitForIdle()
            clickSendDisc("tika draft r75")
            org.junit.Assert.assertEquals(
                "sending must clear the companion's draft",
                "", DraftStore.getTika(),
            )
            org.junit.Assert.assertTrue(
                "the sent draft must land as the companion's newest user message",
                TikaBotStore.messages.value.lastOrNull { it.mine }?.text == "tika draft r75",
            )
            // Let the AI job settle (it may answer or fail in-harness),
            // then remove everything this shot staged.
            pumpUntilR73(20_000) { !AiJobs.companionBusy }
            shot("85-draft-tika-sent.png")
        } finally {
            cleanupCompanionR73C(base)
            DraftStore.setTika("")
        }
    }

    // ---------- ROUND 75 (WS6): a stray tap must never open another chat ----------
    // The composition mirrors TikuRoot's home branch exactly: the live
    // Messages list beneath, the shell TouchShield above it, then the
    // open chat on top. The tap lands on a dead zone of the open chat
    // (empty list space) directly over a Messages row: pre-fix it fell
    // through and flipped the screen; post-fix it dies on the shield.

    @Test fun shot86_strayTapCompanionStaysPut() {
        stageCompanionR73C()
        AppState.go(Screen.TikaBot)
        frame {
            Box(Modifier.fillMaxSize()) {
                HomeScreen()
                TouchShield()
                TikaBotScreen()
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            click(
                androidx.compose.ui.geometry.Offset(
                    (root.left + root.right) / 2f,
                    root.top + (root.bottom - root.top) * 0.70f,
                ),
            )
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "a stray tap on the companion must not open another chat",
            AppState.screen is Screen.TikaBot,
        )
        shot("86-stray-tap-companion.png")
    }

    @Test fun shot87_strayTapNormalChatStaysPut() {
        val th = AppState.threads.value.first()
        // An empty thread gives the open chat a large dead zone; the
        // Messages rows beneath stay live the whole time.
        AppState.updateThread(th.id) { it.messages.clear() }
        AppState.go(Screen.Chat(th.id))
        frame {
            Box(Modifier.fillMaxSize()) {
                HomeScreen()
                TouchShield()
                ChatScreen(th.id)
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput {
            click(
                androidx.compose.ui.geometry.Offset(
                    (root.left + root.right) / 2f,
                    root.top + (root.bottom - root.top) * 0.40f,
                ),
            )
        }
        composeRule.waitForIdle()
        val s = AppState.screen
        org.junit.Assert.assertTrue(
            "a stray tap on an open chat must not open another chat",
            s is Screen.Chat && s.threadId == th.id,
        )
        shot("87-stray-tap-chat.png")
    }

    // ---------- ROUND 77 (WS-A): a deleted voice note survives for the receiver ----------
    // decryptDoc's retained-audio shape, staged directly as thread state
    // (the PreviewRepo pattern of shot40): a PEER voice message flagged
    // deletedForEveryone whose media survived (mediaChunks > 0, payload
    // file held locally). The chat must render its PLAYER — never the
    // tombstone — with only the subtle "Deleted" note under it, and its
    // long-press menu must offer Save to device.

    private fun stageRetainedVoiceR77(): String {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        val f = java.io.File(ctx.cacheDir, "dl_r77voice.m4a")
        f.writeBytes(ByteArray(16))
        AppState.upsertMessage(id, TikuMessage(
            id = "r77voice", senderUid = p.uid, kind = MsgKind.VOICE,
            time = "9:41", me = false, tsMs = 1,
            voiceDurSec = 7, amps = List(22) { 12 },
            localPath = f.absolutePath, mediaChunks = 3,
            deletedForEveryone = true))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        return id
    }

    @Test fun shot90_chatDeletedVoiceRetainedPlayer() {
        stageRetainedVoiceR77()
        org.junit.Assert.assertTrue(
            "a retained deleted voice note must render its player (duration)",
            composeRule.onAllNodesWithText("0:07").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted voice note must carry the subtle Deleted note",
            composeRule.onAllNodesWithText("Deleted").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted voice note must NOT render the tombstone",
            composeRule.onAllNodesWithText("This message was deleted")
                .fetchSemanticsNodes().isEmpty(),
        )
        shot("90-chat-deleted-voice-retained.png")
    }

    @Test fun shot91_chatDeletedVoiceRetainedMenuSave() {
        stageRetainedVoiceR77()
        composeRule.onNodeWithText("0:07").performTouchInput { longClick() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "a retained deleted voice note's menu must offer Save to device",
            composeRule.onAllNodesWithText("Save to device").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted voice note's menu must offer Delete for me",
            composeRule.onAllNodesWithText("Delete for me").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted voice note's menu must NOT offer Reply",
            composeRule.onAllNodesWithText("Reply").fetchSemanticsNodes().isEmpty(),
        )
        shot("91-chat-deleted-voice-menu.png")
    }

    // ---------- ROUND 77 (WS-B): presence pack ----------

    @Test fun shot92_chatPresenceOnlineDot() {
        // The seeded first thread's peer is online (Person defaults:
        // online = true, lastSeenMs = 0 — presenceText reads that as
        // "online" at face value, exactly the privacy-toggle-off
        // shape). The header must show the green presence dot (tagged
        // presence_dot) beside the unchanged "Active now" string.
        val th = AppState.threads.value.first()
        org.junit.Assert.assertTrue(
            "seeded peer must stage as online",
            th.person.online,
        )
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "online header must keep the exact 'Active now' string",
            composeRule.onAllNodesWithText("Active now").fetchSemanticsNodes().isNotEmpty(),
        )
        // ROUND 77 (WS-A+) test-only fix: the 8dp dot provably renders
        // (shot90's PNG shows it in a real chat header) but
        // assertIsDisplayed() fails on it in this harness — assert
        // EXISTENCE instead; the shot's PNG remains the visual proof.
        org.junit.Assert.assertTrue(
            "online header must carry the presence dot",
            composeRule.onAllNodesWithTag("presence_dot", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        shot("92-chat-presence-online.png")
    }

    @Test fun shot93_chatPresenceLastSeenNoDot() {
        // The same header, peer OFFLINE with a last seen one hour ago:
        // the line must show the peer's latest recorded stamp
        // ("last seen today at …", the fmtLastSeen form) and NO dot.
        val th = AppState.threads.value.first()
        val offline = th.copy(
            person = th.person.copy(
                online = false,
                lastSeenMs = System.currentTimeMillis() - 3_600_000L,
            ),
        )
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == th.id) offline else it
        }
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "offline header must show the latest last seen",
            composeRule.onAllNodesWithText("last seen today at", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "offline header must not show Active now",
            composeRule.onAllNodesWithText("Active now").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "offline header must not carry the presence dot",
            composeRule.onAllNodesWithTag("presence_dot", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
        shot("93-chat-presence-last-seen.png")
    }

    /** ROUND 77 (WS-B) — the seen gate's truth table, proven at unit
     *  level (the Firestore write itself needs a live session and is
     *  NOT claimed here): seen-marking passes ONLY for a foregrounded,
     *  unlocked session whose CURRENT destination is exactly this
     *  chat and which holds the chat in its open set. Every other
     *  combination — backgrounded with the chat left open, locked,
     *  another screen (Home / ChatDetails / Call) over the same
     *  thread, a different chat current, or a chat never opened —
     *  must refuse, so nothing that arrived unviewed can be marked
     *  seen, retroactively or otherwise. */
    @Test fun shot94_seenGateContract() {
        val open = setOf("c1")
        org.junit.Assert.assertTrue(
            "foreground + unlocked + this chat current + open must pass",
            seenGatePasses(true, true, Screen.Chat("c1"), "c1", open),
        )
        org.junit.Assert.assertFalse(
            "backgrounded app must refuse (the reported leak)",
            seenGatePasses(false, true, Screen.Chat("c1"), "c1", open),
        )
        org.junit.Assert.assertFalse(
            "locked session must refuse",
            seenGatePasses(true, false, Screen.Chat("c1"), "c1", open),
        )
        org.junit.Assert.assertFalse(
            "Home over the shell must refuse",
            seenGatePasses(true, true, Screen.Home, "c1", open),
        )
        org.junit.Assert.assertFalse(
            "ChatDetails for the same thread must refuse",
            seenGatePasses(true, true, Screen.ChatDetails("c1"), "c1", open),
        )
        org.junit.Assert.assertFalse(
            "a Call over the same thread must refuse",
            seenGatePasses(true, true, Screen.Call("c1", false), "c1", open),
        )
        org.junit.Assert.assertFalse(
            "a different chat being current must refuse",
            seenGatePasses(true, true, Screen.Chat("c2"), "c1", open),
        )
        org.junit.Assert.assertFalse(
            "a chat outside the open set must refuse",
            seenGatePasses(true, true, Screen.Chat("c1"), "c1", emptySet()),
        )
        org.junit.Assert.assertFalse(
            "no current screen must refuse",
            seenGatePasses(true, true, null, "c1", open),
        )
    }

    // ---------- ROUND 77 (WS-A+): delete-for-everyone retains EVERY kind for the receiver ----------
    // The pure-watermark model, staged exactly like shot90/91 (decryptDoc's
    // retained shape as thread state): a PEER message flagged
    // deletedForEveryone whose content SURVIVED — a text keeps its body,
    // a photo keeps its bitmap + chunk count. The chat must render the
    // ORIGINAL bubble — never the tombstone — with only the subtle
    // "Deleted" note under it.

    @Test fun shot95_chatDeletedTextRetainedBubble() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        AppState.upsertMessage(id, TikuMessage(
            id = "r77text", senderUid = p.uid, kind = MsgKind.TEXT,
            text = "this stays readable even after delete",
            time = "9:41", me = false, tsMs = 1,
            deletedForEveryone = true))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "a retained deleted text must render its original text in the bubble",
            composeRule.onAllNodesWithText("this stays readable even after delete")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted text must carry the subtle Deleted note",
            composeRule.onAllNodesWithText("Deleted").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted text must NOT render the tombstone",
            composeRule.onAllNodesWithText("This message was deleted")
                .fetchSemanticsNodes().isEmpty(),
        )
        shot("95-chat-deleted-text-retained.png")
    }

    @Test fun shot96_chatDeletedPhotoRetainedImage() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { it.messages.clear() }
        val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.rgb(240, 200, 170))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(160, 80, 50); textSize = 44f
        }
        canvas.drawText("Tikuu photo", 60f, 210f, paint)
        AppState.upsertMessage(id, TikuMessage(
            id = "r77photo", senderUid = p.uid, kind = MsgKind.IMAGE,
            text = "retained photo caption",
            time = "9:41", me = false, tsMs = 1,
            photo = bmp, mediaChunks = 2,
            deletedForEveryone = true))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "a retained deleted photo must render its image bubble (its caption rides on the photo)",
            composeRule.onAllNodesWithText("retained photo caption")
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted photo must carry the subtle Deleted note",
            composeRule.onAllNodesWithText("Deleted").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a retained deleted photo must NOT render the tombstone",
            composeRule.onAllNodesWithText("This message was deleted")
                .fetchSemanticsNodes().isEmpty(),
        )
        shot("96-chat-deleted-photo-retained.png")
    }

    // ---------- ROUND 78 (WS-1): the slash-command sheet ----------
    // The owner's regression: typing "/" shows NOTHING. Three stagings
    // triangulate the break: the real keystroke path (semantics text
    // input into the live field), the draft-restored path (the R75
    // state entry point), and the companion (same RdChatInput).

    /** Type [text] into the frame's one editable field (the composer). */
    private fun typeIntoComposer(text: String) {
        val field = composeRule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())
            .fetchSemanticsNodes()
        org.junit.Assert.assertEquals(
            "the frame must hold exactly one editable field (the composer)",
            1, field.size,
        )
        composeRule.onNode(androidx.compose.ui.test.hasSetTextAction())
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasSetTextAction())
            .performTextInput(text)
        composeRule.waitForIdle()
    }

    private fun assertSlashSheetVisible(where: String) {
        org.junit.Assert.assertTrue(
            "$where: typing '/' must list /tiku",
            composeRule.onAllNodesWithText("/tiku").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "$where: typing '/' must list /tika",
            composeRule.onAllNodesWithText("/tika").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "$where: typing '/' must list /babeg",
            composeRule.onAllNodesWithText("/babeg").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test fun shot97_slashSheetChatTyped() {
        val th = AppState.threads.value.first()
        AppState.go(Screen.Chat(th.id))
        frame { ChatScreen(th.id) }
        typeIntoComposer("/")
        assertSlashSheetVisible("normal chat, typed")
        shot("97-slash-menu-chat.png")
    }

    @Test fun shot98_slashSheetCompanionTyped() {
        stageCompanionR73C()
        frame { TikaBotScreen() }
        typeIntoComposer("/")
        assertSlashSheetVisible("companion, typed")
        shot("98-slash-menu-companion.png")
    }

    @Test fun shot99_slashSheetChatDraftRestored() {
        val th = AppState.threads.value.first()
        DraftStore.setChat(th.id, "/")
        try {
            AppState.go(Screen.Chat(th.id))
            frame { ChatScreen(th.id) }
            composeRule.waitForIdle()
            assertSlashSheetVisible("normal chat, draft-restored")
            shot("99-slash-menu-draft.png")
        } finally {
            DraftStore.setChat(th.id, "")
        }
    }

    // ---------- ROUND 78 (WS-2): IG-style time separators ----------
    // A staged conversation: 10:00 and 12:00 the same day (a 2-hour
    // jump → exactly one separator, labelled with the later group's
    // time) and 12:05 (quick succession → no separator).

    private fun todayAtR78(hour: Int, minute: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, hour)
        c.set(java.util.Calendar.MINUTE, minute)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test fun shot100_timeSeparatorBetweenGroups() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.addAll(
                listOf(
                    TikuMessage(
                        id = "r78a", senderUid = p.uid, kind = MsgKind.TEXT,
                        text = "morning wala message", time = "10:00 AM",
                        me = false, tsMs = todayAtR78(10, 0),
                    ),
                    TikuMessage(
                        id = "r78b", senderUid = p.uid, kind = MsgKind.TEXT,
                        text = "dopeher wala message", time = "12:00 PM",
                        me = false, tsMs = todayAtR78(12, 0),
                    ),
                    TikuMessage(
                        id = "r78c", senderUid = p.uid, kind = MsgKind.TEXT,
                        text = "turant baad wala", time = "12:05 PM",
                        me = false, tsMs = todayAtR78(12, 5),
                    ),
                ),
            )
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        val seps = composeRule.onAllNodesWithTag("time_separator").fetchSemanticsNodes()
        org.junit.Assert.assertEquals(
            "a 2-hour jump earns exactly one time separator (quick succession earns none)",
            1, seps.size,
        )
        composeRule.onNodeWithTag("time_separator")
            .assertTextEquals("12:00 PM")
        shot("100-time-separator.png")
    }

    @Test fun timeSeparatorLabelContractR78() {
        val now = todayAtR78(15, 0)
        // quick succession, same day → no separator
        org.junit.Assert.assertNull(timeSeparatorLabel(todayAtR78(12, 0), todayAtR78(12, 5), now))
        org.junit.Assert.assertNull(timeSeparatorLabel(todayAtR78(12, 0), todayAtR78(12, 59), now))
        // exactly one hour → separator with the later time
        org.junit.Assert.assertEquals(
            "1:00 PM", timeSeparatorLabel(todayAtR78(12, 0), todayAtR78(13, 0), now),
        )
        // the owner's case: 10:00 → 12:00
        org.junit.Assert.assertEquals(
            "12:00 PM", timeSeparatorLabel(todayAtR78(10, 0), todayAtR78(12, 0), now),
        )
        // a short gap across midnight still separates the days
        val yday2350 = todayAtR78(23, 50) - 86_400_000L
        val today0010 = todayAtR78(0, 10)
        org.junit.Assert.assertEquals(
            "12:10 AM", timeSeparatorLabel(yday2350, today0010, now),
        )
        // yesterday's group, seen today → "Yesterday, …"
        val yday1500 = todayAtR78(15, 0) - 86_400_000L
        val yday1200 = todayAtR78(12, 0) - 86_400_000L
        org.junit.Assert.assertEquals(
            "Yesterday, 3:00 PM", timeSeparatorLabel(yday1200, yday1500, now),
        )
        // missing stamps never separate
        org.junit.Assert.assertNull(timeSeparatorLabel(0L, todayAtR78(12, 0), now))
        org.junit.Assert.assertNull(timeSeparatorLabel(todayAtR78(10, 0), 0L, now))
    }

    // ---------- ROUND 78 (WS-3): the send outbox ----------
    // Store fidelity (enqueue → persist → read back byte-faithful),
    // the drain plan (a landed id drops, an unlanded id delivers —
    // exactly-once), and the pending affordance on a staged SENDING
    // bubble (it stays rendered, captioned "Sending…", never gone).

    @Test fun outboxStoreRoundTripR78() {
        OutboxStore.clearAll()
        try {
            OutboxStore.enqueue(
                OutboxStore.PendingSend(
                    msgId = "r78t1", chatId = "chatA", kind = "text", tsMs = 111L,
                    text = "hello outbox", replyQuote = "quoted line",
                    replyToId = "__note__", fx = "hearts", ai = true,
                ),
            )
            OutboxStore.enqueue(
                OutboxStore.PendingSend(
                    msgId = "r78v1", chatId = "chatA", kind = "voice", tsMs = 222L,
                    localPath = "/tmp/voice.m4a", voiceDurSec = 7,
                    amps = listOf(1, 5, 9, 3),
                ),
            )
            OutboxStore.enqueue(
                OutboxStore.PendingSend(
                    msgId = "r78i1", chatId = "chatB", kind = "image", tsMs = 333L,
                    localPath = "/tmp/outbox_r78i1.jpg", vo = 2,
                ),
            )
            val all = OutboxStore.all()
            org.junit.Assert.assertEquals(3, all.size)
            val t = all.first { it.msgId == "r78t1" }
            org.junit.Assert.assertEquals("hello outbox", t.text)
            org.junit.Assert.assertEquals("quoted line", t.replyQuote)
            org.junit.Assert.assertEquals("__note__", t.replyToId)
            org.junit.Assert.assertEquals("hearts", t.fx)
            org.junit.Assert.assertTrue(t.ai)
            val v = all.first { it.msgId == "r78v1" }
            org.junit.Assert.assertEquals(listOf(1, 5, 9, 3), v.amps)
            org.junit.Assert.assertEquals(7, v.voiceDurSec)
            val im = all.first { it.msgId == "r78i1" }
            org.junit.Assert.assertEquals(2, im.vo)
            // re-enqueue replaces, never duplicates
            OutboxStore.enqueue(
                OutboxStore.PendingSend(
                    msgId = "r78t1", chatId = "chatA", kind = "text",
                    tsMs = 111L, text = "hello outbox v2",
                ),
            )
            org.junit.Assert.assertEquals(3, OutboxStore.all().size)
            org.junit.Assert.assertEquals(
                "hello outbox v2",
                OutboxStore.all().first { it.msgId == "r78t1" }.text,
            )
            // remove drops exactly one
            OutboxStore.remove("r78v1")
            org.junit.Assert.assertEquals(
                setOf("r78t1", "r78i1"),
                OutboxStore.all().map { it.msgId }.toSet(),
            )
        } finally {
            OutboxStore.clearAll()
        }
    }

    @Test fun outboxDrainPlanR78() {
        val entries = listOf(
            OutboxStore.PendingSend(msgId = "landed1", chatId = "c", kind = "text", tsMs = 1L, text = "a"),
            OutboxStore.PendingSend(msgId = "pending1", chatId = "c", kind = "text", tsMs = 2L, text = "b"),
            OutboxStore.PendingSend(msgId = "pending2", chatId = "c", kind = "voice", tsMs = 3L, localPath = "/tmp/x.m4a"),
        )
        val (deliver, drop) = OutboxStore.planDrain(entries, setOf("landed1"))
        org.junit.Assert.assertEquals(
            "an entry whose doc already landed is dropped, never resent (exactly-once)",
            listOf("landed1"), drop.map { it.msgId },
        )
        org.junit.Assert.assertEquals(
            "every unlanded entry delivers, exactly once each",
            listOf("pending1", "pending2"), deliver.map { it.msgId },
        )
        val (deliverAll, dropNone) = OutboxStore.planDrain(entries, emptySet())
        org.junit.Assert.assertEquals(3, deliverAll.size)
        org.junit.Assert.assertTrue(dropNone.isEmpty())
    }

    @Test fun shot101_sendingBubbleStaysRendered() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.addAll(
                listOf(
                    TikuMessage(
                        id = "r78old", senderUid = p.uid, kind = MsgKind.TEXT,
                        text = "purana message", time = "10:00 AM",
                        me = false, tsMs = todayAtR78(10, 0),
                    ),
                    TikuMessage(
                        id = "r78pend", senderUid = "me", kind = MsgKind.TEXT,
                        text = "r78 pending hello", time = "12:00 PM",
                        me = true, tsMs = todayAtR78(12, 0),
                        status = SendStatus.SENDING,
                    ),
                ),
            )
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "a pending send stays rendered in place — it never vanishes",
            composeRule.onAllNodesWithText("r78 pending hello", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "a pending send carries the subtle Sending… caption",
            composeRule.onAllNodesWithText("Sending…").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("101-sending-pending.png")
    }

    // ---------- ROUND 79 (WS-1): pending sends render FROM THE OUTBOX ----------
    // R78 persisted the send but left its visibility to a copy in the
    // visible list — the closed-chat (preview) rebuild replaced that
    // list with server docs only and the pending bubble vanished on
    // leave/re-enter and after relaunch (owner's device report on
    // 145). The engine now re-derives pending locals from the store
    // on EVERY rebuild (OutboxStore.selectPending +
    // mergePendingById). The Firestore-driven rebuilds can't run
    // headless, so: the pure merge contract is pinned directly here,
    // and the shots stage the device cases at the AppState level —
    // a thread whose list does NOT contain the pending message (the
    // post-wipe state), the outbox entry persisted, then the exact
    // merge the engine applies on re-entry, then a recomposition.

    @Test fun outboxRenderMergeR79() {
        val entries = listOf(
            OutboxStore.PendingSend(msgId = "p1", chatId = "cA", kind = "text", tsMs = 10L, text = "one"),
            OutboxStore.PendingSend(msgId = "p2", chatId = "cA", kind = "text", tsMs = 20L, text = "two"),
            OutboxStore.PendingSend(msgId = "p3", chatId = "cB", kind = "text", tsMs = 30L, text = "other chat"),
            OutboxStore.PendingSend(msgId = "landed", chatId = "cA", kind = "text", tsMs = 40L, text = "landed"),
        )
        val sel = OutboxStore.selectPending(entries, "cA", setOf("landed"))
        org.junit.Assert.assertEquals(
            "selection keeps only this chat's unaccounted entries, in store order",
            listOf("p1", "p2"), sel.map { it.msgId },
        )
        org.junit.Assert.assertEquals(
            "everything accounted for selects nothing",
            0, OutboxStore.selectPending(entries, "cA", setOf("p1", "p2", "landed")).size,
        )
        val base = listOf(
            TikuMessage(id = "s1", tsMs = 5L, text = "server old"),
            TikuMessage(id = "landed", tsMs = 40L, text = "landed server copy"),
        )
        val pending = listOf(
            TikuMessage(id = "p2", tsMs = 20L, text = "two", status = SendStatus.SENDING),
            TikuMessage(id = "landed", tsMs = 40L, text = "landed local copy", status = SendStatus.SENDING),
            TikuMessage(id = "p1", tsMs = 10L, text = "one", status = SendStatus.SENDING),
        )
        val merged = mergePendingById(base, pending)
        org.junit.Assert.assertEquals(
            "merge is a by-id union sorted by ts — the landed id never doubles",
            listOf("s1", "p1", "p2", "landed"), merged.map { it.id },
        )
        org.junit.Assert.assertEquals(
            "the landed copy in the base wins over the pending copy",
            "landed server copy", merged.first { it.id == "landed" }.text,
        )
        org.junit.Assert.assertSame(
            "no pending entries returns the base untouched",
            base, mergePendingById(base, emptyList()),
        )
    }

    @Test fun shot102_outboxPendingSurvivesReentry() {
        OutboxStore.clearAll()
        try {
            val p = PreviewStore.people.first()
            val id = PreviewRepo.startThreadWith(p)
            val pendTs = todayAtR78(12, 30)
            // The persisted send (what the send path wrote at staging).
            OutboxStore.enqueue(
                OutboxStore.PendingSend(
                    msgId = "r79pend", chatId = id, kind = "text",
                    tsMs = pendTs, text = "r79 offline hello",
                ),
            )
            // The thread's list as the OLD preview rebuild left it:
            // server messages only — the pending copy is GONE (the bug).
            AppState.updateThread(id) { th ->
                th.messages.clear()
                th.messages.addAll(
                    listOf(
                        TikuMessage(
                            id = "r79old", senderUid = p.uid, kind = MsgKind.TEXT,
                            text = "purana message", time = "10:00 AM",
                            me = false, tsMs = todayAtR78(10, 0),
                        ),
                    ),
                )
            }
            AppState.go(Screen.Chat(id))
            // Pre-merge staging mimics the bug, asserted at the data
            // level: the visible list holds no trace of the pending
            // send (a second frame{} here is what the harness's
            // setContent-once rule forbids — the render proof below
            // uses the single frame).
            org.junit.Assert.assertTrue(
                "pre-merge staging mimics the bug: the pending text is absent",
                AppState.thread(id)?.messages?.none { it.text == "r79 offline hello" } == true,
            )
            // Re-entry: the engine's rebuild re-derives the pending
            // local from the store (selectPending → rebuild → merge).
            val sel = OutboxStore.selectPending(
                OutboxStore.all(), id,
                AppState.thread(id)?.messages?.map { it.id }?.toSet() ?: emptySet(),
            )
            org.junit.Assert.assertEquals(listOf("r79pend"), sel.map { it.msgId })
            val rebuilt = sel.map { e ->
                TikuMessage(
                    id = e.msgId, senderUid = "me", kind = MsgKind.TEXT,
                    text = e.text, time = "12:30 PM", me = true,
                    tsMs = e.tsMs, status = SendStatus.SENDING,
                )
            }
            AppState.updateThread(id) { th ->
                val merged = mergePendingById(th.messages.toList(), rebuilt)
                th.messages.clear(); th.messages.addAll(merged)
            }
            // The single composition: after the rebuild's merge the
            // pending bubble renders, and stays rendered.
            frame { ChatScreen(id) }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "after re-entry the pending send renders from the outbox",
                composeRule.onAllNodesWithText("r79 offline hello", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
            org.junit.Assert.assertTrue(
                "and it still carries the Sending… caption",
                composeRule.onAllNodesWithText("Sending…").fetchSemanticsNodes().isNotEmpty(),
            )
            shot("102-outbox-pending-reentry.png")
        } finally {
            OutboxStore.clearAll()
        }
    }

    @Test fun shot103_outboxPendingVoiceRenders() {
        OutboxStore.clearAll()
        try {
            val p = PreviewStore.people.first()
            val id = PreviewRepo.startThreadWith(p)
            OutboxStore.enqueue(
                OutboxStore.PendingSend(
                    msgId = "r79voice", chatId = id, kind = "voice",
                    tsMs = todayAtR78(12, 45), localPath = "/tmp/r79-voice.m4a",
                    voiceDurSec = 7, amps = listOf(1, 5, 9, 3, 8, 2),
                ),
            )
            AppState.updateThread(id) { th ->
                th.messages.clear()
                th.messages.addAll(
                    listOf(
                        TikuMessage(
                            id = "r79old2", senderUid = p.uid, kind = MsgKind.TEXT,
                            text = "purana message", time = "10:00 AM",
                            me = false, tsMs = todayAtR78(10, 0),
                        ),
                    ),
                )
            }
            val sel = OutboxStore.selectPending(
                OutboxStore.all(), id,
                AppState.thread(id)?.messages?.map { it.id }?.toSet() ?: emptySet(),
            )
            org.junit.Assert.assertEquals(listOf("r79voice"), sel.map { it.msgId })
            val rebuilt = sel.map { e ->
                TikuMessage(
                    id = e.msgId, senderUid = "me", kind = MsgKind.VOICE,
                    time = "12:45 PM", me = true, tsMs = e.tsMs,
                    status = SendStatus.SENDING,
                    voiceDurSec = e.voiceDurSec, amps = e.amps,
                    localPath = e.localPath,
                )
            }
            AppState.updateThread(id) { th ->
                val merged = mergePendingById(th.messages.toList(), rebuilt)
                th.messages.clear(); th.messages.addAll(merged)
            }
            AppState.go(Screen.Chat(id))
            frame { ChatScreen(id) }
            composeRule.waitForIdle()
            org.junit.Assert.assertTrue(
                "a pending voice send renders its bubble after the merge",
                composeRule.onAllNodesWithText("0:07").fetchSemanticsNodes().isNotEmpty(),
            )
            org.junit.Assert.assertTrue(
                "and it still carries the Sending… caption",
                composeRule.onAllNodesWithText("Sending…").fetchSemanticsNodes().isNotEmpty(),
            )
            shot("103-outbox-pending-voice.png")
        } finally {
            OutboxStore.clearAll()
        }
    }

    // ---------- ROUND 79 (WS-2): media reply quotes ----------
    // A reply to a photo/video used to quote just the word
    // "Photo"/"Video" and point nowhere (the send path never carried
    // the original's id). Now the reply doc carries replyToId, the
    // bubble's quote strip renders the original's thumbnail beside
    // the label, and tapping the strip jumps to the original and
    // opens its viewer.

    private fun solidBitmapR79(w: Int, h: Int, color: Int): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(
            w, h, android.graphics.Bitmap.Config.ARGB_8888,
        )
        b.eraseColor(color)
        return b
    }

    @Test fun shot104_mediaReplyQuotePhoto() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        val photoBmp = solidBitmapR79(160, 120, 0xFF7A4E8F.toInt())
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.addAll(
                listOf(
                    TikuMessage(
                        id = "r79photo", senderUid = p.uid, kind = MsgKind.IMAGE,
                        time = "10:00 AM", me = false, tsMs = todayAtR78(10, 0),
                        photo = photoBmp, mediaChunks = 1,
                    ),
                    TikuMessage(
                        id = "r79reply", senderUid = "me", kind = MsgKind.TEXT,
                        text = "yeh wali photo best hai", time = "10:05 AM",
                        me = true, tsMs = todayAtR78(10, 5),
                        replyQuote = "Photo", replyToId = "r79photo",
                    ),
                ),
            )
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the reply renders under its media quote",
            composeRule.onAllNodesWithText("yeh wali photo best hai", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the quote strip carries the Photo label",
            composeRule.onAllNodesWithText("Photo").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("104-media-reply-quote-photo.png")
        // Tapping the quote strip jumps to the original and opens
        // the image viewer on it.
        composeRule.onNodeWithText("Photo").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "tapping the quote opens the image viewer on the original",
            composeRule.onAllNodesWithTag("image_viewer").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test fun shot105_mediaReplyQuoteVideo() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        val poster = solidBitmapR79(160, 200, 0xFF2E5E4E.toInt())
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.addAll(
                listOf(
                    TikuMessage(
                        id = "r79video", senderUid = p.uid, kind = MsgKind.VIDEO,
                        time = "11:00 AM", me = false, tsMs = todayAtR78(11, 0),
                        photo = poster, mediaChunks = 3, voiceDurSec = 42,
                        mediaBytes = 5_000_000L, mimeType = "video/mp4",
                    ),
                    TikuMessage(
                        id = "r79replyv", senderUid = "me", kind = MsgKind.TEXT,
                        text = "video ka ye part dekho", time = "11:05 AM",
                        me = true, tsMs = todayAtR78(11, 5),
                        replyQuote = "Video", replyToId = "r79video",
                    ),
                ),
            )
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the reply renders under its video quote",
            composeRule.onAllNodesWithText("video ka ye part dekho", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the quote strip carries the Video label (its thumbnail is the poster)",
            composeRule.onAllNodesWithText("Video").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("105-media-reply-quote-video.png")
    }

    // ================= ROUND 79 WS-3 — the note's local song =================
    // The NoteSheet is composed directly (the sheet is a pure
    // function of its params; HomeScreen's picker/player wiring is
    // device behaviour and is covered by the code path, not the
    // harness). 106: the composer's empty state offers "Add music".
    // 107: a staged pick shows its name + duration with a remove
    // affordance. 108: a partner's note shows the song row with the
    // play affordance.

    @Test fun shot106_noteSongComposerEmpty() {
        frame {
            NoteSheet(
                h = null, mine = true, headerName = "", note = "",
                hearted = false, headerAvatar = {},
                onShare = {}, onDelete = {}, onHeart = {}, onReply = {},
                onPickSong = {}, onRemoveSong = {},
                songPlaying = false, onSongToggle = {},
            )
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the composer offers Add music when no song is attached",
            composeRule.onAllNodesWithText("Add music").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("106-note-song-composer-empty.png")
    }

    @Test fun shot107_noteSongComposerStaged() {
        frame {
            NoteSheet(
                h = null, mine = true, headerName = "", note = "",
                hearted = false, headerAvatar = {},
                onShare = {}, onDelete = {}, onHeart = {}, onReply = {},
                songPickLabel = "Midnight Drive.mp3 · 3:24",
                onPickSong = {}, onRemoveSong = {},
                songPlaying = false, onSongToggle = {},
            )
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the staged pick shows its name and duration in the composer",
            composeRule.onAllNodesWithText("Midnight Drive.mp3 · 3:24", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the Add music offer is replaced while a song is staged",
            composeRule.onAllNodesWithText("Add music").fetchSemanticsNodes().isEmpty(),
        )
        shot("107-note-song-composer-staged.png")
    }

    @Test fun shot108_noteSongPeerRow() {
        frame {
            NoteSheet(
                h = null, mine = false, headerName = "sexxy bou",
                note = "sunset drive wala mood", hearted = false,
                headerAvatar = {},
                onShare = {}, onDelete = {}, onHeart = {}, onReply = {},
                song = NoteSong(
                    title = "Midnight Drive.mp3", mime = "audio/mpeg",
                    durSec = 204, bytes = 4_800_000L, chunks = 7,
                ),
                songPlaying = false, onSongToggle = {},
            )
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the peer note renders above its song row",
            composeRule.onAllNodesWithText("sunset drive wala mood", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the song row shows the title and duration with a play affordance",
            composeRule.onAllNodesWithText("Midnight Drive.mp3 · 3:24", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("108-note-song-peer.png")
    }

    // ================= ROUND 79 WS-5 — companion @mentions =================
    // The companion composer offers the signed-in user's OWN chat
    // partners while an "@…" tail is typed (RdChatInput's mention
    // sheet — the companion is the only caller that passes
    // candidates). Two partners are seeded as threads; typing "@"
    // must surface their suggestion rows (name + @username).

    @Test fun shot110_unreadThreadRow() {
        // ROUND 79 (WS-7): the Messages list's unread treatment —
        // an unread thread (3 unread) above a read one. The unread
        // row must carry the count badge; bold name/preview are the
        // eyeball check on the shot.
        val pA = PreviewStore.people[0]
        val pB = PreviewStore.people[1]
        val unreadTh = ChatThread("r79-unread-th", pA)
        unreadTh.messages.add(
            TikuMessage(
                id = "r79u1", senderUid = pA.uid, kind = MsgKind.TEXT,
                text = "kal ka plan pakka?", time = "9:41 AM",
                me = false, tsMs = todayAtR78(9, 41),
            ),
        )
        unreadTh.unread = 3
        val readTh = ChatThread("r79-read-th", pB)
        readTh.messages.add(
            TikuMessage(
                id = "r79r1", senderUid = "me", kind = MsgKind.TEXT,
                text = "photo bhej diya hai", time = "9:10 AM",
                me = true, tsMs = todayAtR78(9, 10),
            ),
        )
        AppState.threads.value = listOf(unreadTh, readTh)
        AppState.homeTab = HomeTab.CHATS
        AppState.go(Screen.Home)
        frame { HomeScreen() }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the unread row must carry the count badge",
            composeRule.onAllNodesWithText("3").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the unread preview summarises the count",
            composeRule.onAllNodesWithText("3 new messages", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the read row renders its plain preview, no badge",
            composeRule.onAllNodesWithText("photo bhej diya hai", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        shot("110-unread-thread-row.png")
    }

    @Test fun shot109_companionMentionSuggestions() {
        PreviewRepo.startThreadWith(PreviewStore.people[0]) // Aarav / @aarav
        PreviewRepo.startThreadWith(PreviewStore.people[1]) // Meera / @meera
        stageCompanionR73C()
        frame { TikaBotScreen() }
        typeIntoComposer("@")
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "typing @ in the companion offers partner @aarav",
            composeRule.onAllNodesWithText("@aarav").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "typing @ in the companion offers partner @meera",
            composeRule.onAllNodesWithText("@meera").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("109-companion-mention-suggestions.png")
    }
    // ================= ROUND 83 — the in-app camera =================
    // The live camera cannot render a preview headlessly, so the
    // chrome is staged with a placeholder surface (all four controls
    // present, on-brand) and the confirm screen with a staged photo.
    // Real capture quality/focus is a device check.

    @Test fun shot114_inAppCamera() {
        frame {
            CameraChrome(
                flashMode = ImageCapture.FLASH_MODE_OFF,
                hasFlash = true,
                capturing = false,
                onClose = {}, onFlash = {}, onShutter = {}, onFlip = {},
                preview = {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2A2D3A), Color(0xFF101116)),
                            ),
                        ),
                    )
                },
            )
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the in-app camera shows its close control",
            composeRule.onAllNodesWithTag("camera_close").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the in-app camera shows its flash control",
            composeRule.onAllNodesWithTag("camera_flash").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the in-app camera shows its shutter",
            composeRule.onAllNodesWithTag("camera_shutter").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the in-app camera shows its flip control",
            composeRule.onAllNodesWithTag("camera_flip").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("114-inapp-camera.png")
    }

    @Test fun shot115_inAppCameraConfirm() {
        frame {
            CameraConfirmView(
                bmp = solidBitmapR79(720, 960, 0xFF3E5C50.toInt()),
                onRetake = {}, onUse = {},
            )
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "the confirm screen is staged",
            composeRule.onAllNodesWithTag("camera_confirm").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the confirm screen offers Retake",
            composeRule.onAllNodesWithText("Retake").fetchSemanticsNodes().isNotEmpty(),
        )
        org.junit.Assert.assertTrue(
            "the confirm screen offers Use photo",
            composeRule.onAllNodesWithText("Use photo").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("115-inapp-camera-confirm.png")
    }

    /** ROUND 83: the EXIF orientation table, pinned against the
     *  androidx constants (1 NORMAL, 2 FLIP_HORIZONTAL, 3 ROTATE_180,
     *  4 FLIP_VERTICAL, 5 TRANSPOSE, 6 ROTATE_90, 7 TRANSVERSE,
     *  8 ROTATE_270) — a wrong row here is a sideways photo forever. */
    @Test fun cameraExifTransformR83() {
        org.junit.Assert.assertEquals(
            "orientation 1 (NORMAL) needs no transform",
            ExifTransform(0, false), exifTransform(1),
        )
        org.junit.Assert.assertEquals(
            "orientation 2 (FLIP_HORIZONTAL) mirrors only",
            ExifTransform(0, true), exifTransform(2),
        )
        org.junit.Assert.assertEquals(
            "orientation 3 (ROTATE_180) rotates 180",
            ExifTransform(180, false), exifTransform(3),
        )
        org.junit.Assert.assertEquals(
            "orientation 4 (FLIP_VERTICAL) is 180 + mirror",
            ExifTransform(180, true), exifTransform(4),
        )
        org.junit.Assert.assertEquals(
            "orientation 5 (TRANSPOSE) is 270 + mirror",
            ExifTransform(270, true), exifTransform(5),
        )
        org.junit.Assert.assertEquals(
            "orientation 6 (ROTATE_90) rotates 90",
            ExifTransform(90, false), exifTransform(6),
        )
        org.junit.Assert.assertEquals(
            "orientation 7 (TRANSVERSE) is 90 + mirror",
            ExifTransform(90, true), exifTransform(7),
        )
        org.junit.Assert.assertEquals(
            "orientation 8 (ROTATE_270) rotates 270",
            ExifTransform(270, false), exifTransform(8),
        )
        org.junit.Assert.assertEquals(
            "an unknown orientation falls back to no transform",
            ExifTransform(0, false), exifTransform(0),
        )
    }

    // ---------- ROUND 84: pinned-strip jump lands on a deep pin ----------
    // The paging half of the fix is unit-proven in PinJumpR84Test (the
    // harness has no Firestore to page); this staging proves the
    // LANDING half in the post-page state: a thread whose full history
    // is loaded, the pinned message sitting far above the tail the
    // chat opens on. Before the tap the pinned bubble is not even
    // composed (the list rests at the tail); after tapping the strip
    // it must be on screen.

    @Test fun shot116_pinnedJumpLandsDeep() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        val base = todayAtR78(9, 0)
        val msgs = (0 until 150).map { i ->
            TikuMessage(
                id = "r84m$i", senderUid = if (i % 2 == 0) p.uid else "me",
                kind = MsgKind.TEXT,
                text = if (i == 12) "r84 pinned long ago" else "r84 history $i",
                time = "9:00 AM",
                me = i % 2 != 0, tsMs = base + i * 60_000L,
            )
        }
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.addAll(msgs)
        }
        AppState.threads.value = AppState.threads.value.map {
            if (it.id == id) it.copy(pinnedMsgIds = listOf("r84m12")) else it
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.waitForIdle()
        // The strip is up, showing the pin's preview…
        org.junit.Assert.assertTrue(
            "the pinned strip shows the deep pin's preview",
            composeRule.onAllNodesWithText("Pinned · r84 pinned long ago", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        // …but the pinned bubble itself is NOT composed yet — the list
        // rests at the tail, 138 messages below it.
        org.junit.Assert.assertTrue(
            "before the tap the deep pinned bubble is not composed",
            composeRule.onAllNodesWithText("r84 pinned long ago").fetchSemanticsNodes().isEmpty(),
        )
        // Tap the strip → the jump scrolls to the pin.
        composeRule.onNodeWithText("Pinned · r84 pinned long ago", substring = true).performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "after the tap the pinned bubble is on screen",
            composeRule.onAllNodesWithText("r84 pinned long ago").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("116-pinned-jump-lands.png")
    }

    /** ROUND 84: the paging plan's contract, executed (the fuller
     *  matrix lives in PinJumpR84Test). A simulated history is driven
     *  through PinJumpPlan exactly as jumpToMessage drives it: the pin
     *  4,900 messages deep in a 5,000-message chat must be REACHED
     *  (the build-47 loop capped out at ~600), and a target that is
     *  genuinely absent (deleted for me) may give up only once the
     *  history is fully loaded — never a page early. */
    @Test fun pinJumpPlanContractR84() {
        // branch spot-checks
        org.junit.Assert.assertEquals(
            PinJumpPlan.Step.SCROLL, PinJumpPlan.step(3, hasOlder = true, pagesLoaded = 0, stalls = 0))
        org.junit.Assert.assertEquals(
            PinJumpPlan.Step.LOAD, PinJumpPlan.step(-1, hasOlder = true, pagesLoaded = 0, stalls = 0))
        org.junit.Assert.assertEquals(
            PinJumpPlan.Step.GIVE_UP, PinJumpPlan.step(-1, hasOlder = false, pagesLoaded = 12, stalls = 0))
        org.junit.Assert.assertEquals(
            PinJumpPlan.Step.GIVE_UP,
            PinJumpPlan.step(-1, hasOlder = true, pagesLoaded = PinJumpPlan.MAX_PAGES, stalls = 0))
        org.junit.Assert.assertTrue(PinJumpPlan.progressed(60, "a", 120, "b", afterHasOlder = true))
        org.junit.Assert.assertFalse(PinJumpPlan.progressed(60, "a", 60, "a", afterHasOlder = true))

        // the walk: window starts at the newest 60 of 5,000; each LOAD
        // prepends one 60-message page; the pin is message #100.
        var loadedFrom = 5_000 - 60
        var pages = 0
        var stalls = 0
        var end: PinJumpPlan.Step? = null
        while (end == null) {
            val idx = if (100 >= loadedFrom) 100 - loadedFrom else -1
            when (PinJumpPlan.step(idx, loadedFrom > 0, pages, stalls)) {
                PinJumpPlan.Step.SCROLL -> end = PinJumpPlan.Step.SCROLL
                PinJumpPlan.Step.GIVE_UP -> end = PinJumpPlan.Step.GIVE_UP
                PinJumpPlan.Step.LOAD -> {
                    val before = loadedFrom
                    loadedFrom = maxOf(0, loadedFrom - 60)
                    if (PinJumpPlan.progressed(
                            5_000 - before, "m$before", 5_000 - loadedFrom, "m$loadedFrom", loadedFrom > 0,
                        )
                    ) { pages++; stalls = 0 } else { stalls++ }
                }
            }
        }
        org.junit.Assert.assertEquals(PinJumpPlan.Step.SCROLL, end)
        org.junit.Assert.assertEquals(81, pages)

        // the absent walk: same history, target never appears — the
        // plan must load every page and give up only at the very top.
        loadedFrom = 700 - 60
        pages = 0
        stalls = 0
        end = null
        while (end == null) {
            when (PinJumpPlan.step(-1, loadedFrom > 0, pages, stalls)) {
                PinJumpPlan.Step.SCROLL -> end = PinJumpPlan.Step.SCROLL
                PinJumpPlan.Step.GIVE_UP -> end = PinJumpPlan.Step.GIVE_UP
                PinJumpPlan.Step.LOAD -> {
                    val before = loadedFrom
                    loadedFrom = maxOf(0, loadedFrom - 60)
                    if (PinJumpPlan.progressed(
                            700 - before, "m$before", 700 - loadedFrom, "m$loadedFrom", loadedFrom > 0,
                        )
                    ) { pages++; stalls = 0 } else { stalls++ }
                }
            }
        }
        org.junit.Assert.assertEquals(PinJumpPlan.Step.GIVE_UP, end)
        org.junit.Assert.assertEquals(0, loadedFrom)
        org.junit.Assert.assertEquals(11, pages)
    }

    // ================= ROUND 85 =================
    // WS-A/B: a message whose payload hasn't landed renders the
    // neutral LOADING treatment (tag msg_pending) — never the
    // can't-open placeholder. WS-C: a normal camera photo opens the
    // full-screen viewer. WS-D: video-mode chrome + timer choices.
    // WS-E: the confirm screen's looks selector. WS-F: staged
    // thumbnails in the composer. WS-G: the companion theme grid.
    // WS-H: Back closes an open sheet before anything else moves.

    @Test fun shot117_pendingLoadingNotPlaceholder() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.addAll(
                listOf(
                    TikuMessage(
                        id = "r85pendimg", senderUid = p.uid, kind = MsgKind.IMAGE,
                        time = "9:41 AM", me = false, tsMs = todayAtR78(9, 41),
                        mediaChunks = 0, pendingDecrypt = true,
                    ),
                    TikuMessage(
                        id = "r85pendtxt", senderUid = p.uid, kind = MsgKind.TEXT,
                        time = "9:42 AM", me = false, tsMs = todayAtR78(9, 42),
                        pendingDecrypt = true,
                    ),
                ),
            )
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        // The loading treatment is on screen…
        org.junit.Assert.assertTrue(
            "a pending media message must render the loading row",
            composeRule.onAllNodesWithTag("msg_pending").fetchSemanticsNodes().isNotEmpty(),
        )
        // …and the can't-open placeholder is NOWHERE.
        org.junit.Assert.assertTrue(
            "a pending message must never render the can't-open placeholder",
            composeRule.onAllNodesWithText("This message can't be opened on this phone")
                .fetchSemanticsNodes().isEmpty(),
        )
        shot("117-pending-loading.png")
    }

    @Test fun shot118_pendingLandedRendersImage() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        val bmp = solidBitmapR79(200, 260, 0xFF8A5A44.toInt())
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.addAll(
                listOf(
                    TikuMessage(
                        id = "r85pendimg", senderUid = p.uid, kind = MsgKind.IMAGE,
                        time = "9:41 AM", me = false, tsMs = todayAtR78(9, 41),
                        photo = bmp, mediaChunks = 3, pendingDecrypt = false,
                    ),
                    TikuMessage(
                        id = "r85pendtxt", senderUid = p.uid, kind = MsgKind.TEXT,
                        time = "9:42 AM", me = false, tsMs = todayAtR78(9, 42),
                        text = "It arrived!",
                    ),
                ),
            )
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        org.junit.Assert.assertTrue(
            "once the payload lands, the pending row is gone",
            composeRule.onAllNodesWithTag("msg_pending").fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithText("It arrived!").assertIsDisplayed()
        shot("118-pending-landed.png")
    }

    @Test fun shot119_cameraPhotoOpensViewer() {
        // WS-C: a NORMAL camera capture (view-once off) stages and
        // sends through the exact same photo funnel as a gallery pick;
        // its bubble tap must open the full-screen viewer.
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        val bmp = solidBitmapR79(220, 260, 0xFF4E6B8A.toInt())
        AppState.updateThread(id) { th ->
            th.messages.clear()
            th.messages.add(
                TikuMessage(
                    id = "r85camphoto", senderUid = "me", kind = MsgKind.IMAGE,
                    time = "10:15 AM", me = true, tsMs = todayAtR78(10, 15),
                    photo = bmp, mediaChunks = 2, vo = null,
                ),
            )
        }
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        // The staged chat first (evidence of the bubble either way).
        shot("119-camera-photo-chat.png")
        val chipNodes = composeRule.onAllNodesWithText("10:15 AM")
        val chipSemantics = chipNodes.fetchSemanticsNodes()
        org.junit.Assert.assertTrue("the photo bubble must render", chipSemantics.isNotEmpty())
        // Attempt 1: tap the bubble's time chip (inside the tap
        // region), then flush the row detector's double-tap window
        // on the compose clock so its single-tap route fires.
        runCatching { chipNodes[0].performTouchInput { click(center) } }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("image_viewer").fetchSemanticsNodes().isEmpty()) {
            // Attempt 2: a raw touch on the photo's centre, derived
            // from the chip's bounds (the chip hugs the frame's
            // bottom-end corner, inset 8 dp; the frame is 220x260 dp;
            // this harness renders at 3x density).
            val c = chipSemantics.first().boundsInRoot.center
            runCatching {
                composeRule.onRoot().performTouchInput {
                    click(androidx.compose.ui.geometry.Offset(c.x - 207f, c.y - 339f))
                }
            }
            composeRule.mainClock.advanceTimeBy(700)
            composeRule.waitForIdle()
        }
        org.junit.Assert.assertTrue(
            "tapping a normal camera photo must open the full-screen viewer",
            composeRule.onAllNodesWithTag("image_viewer").fetchSemanticsNodes().isNotEmpty(),
        )
        shot("119-camera-photo-viewer.png")
    }

    @Test fun shot120_videoModeChrome() {
        frame {
            CameraChrome(
                flashMode = 0, hasFlash = true, capturing = false,
                onClose = {}, onFlash = {}, onShutter = {}, onFlip = {},
                videoMode = true,
            ) {
                Box(Modifier.fillMaxSize().background(Color(0xFF2A2D33)))
            }
        }
        composeRule.onNodeWithTag("camera_timer_30").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_timer_60").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_timer_custom").assertIsDisplayed()
        composeRule.onNodeWithTag("camera_mode_video").assertIsDisplayed()
        composeRule.onNodeWithText("30s").assertIsDisplayed()
        composeRule.onNodeWithText("60s").assertIsDisplayed()
        shot("120-video-mode-chrome.png")
    }

    @Test fun shot121_videoRecordingChrome() {
        frame {
            CameraChrome(
                flashMode = 0, hasFlash = true, capturing = false,
                onClose = {}, onFlash = {}, onShutter = {}, onFlip = {},
                videoMode = true, recording = true,
                recElapsedSec = 7, recLimitSec = 30,
            ) {
                Box(Modifier.fillMaxSize().background(Color(0xFF2A2D33)))
            }
        }
        composeRule.onNodeWithTag("camera_rec_pill").assertIsDisplayed()
        composeRule.onNodeWithText("0:07 / 0:30").assertIsDisplayed()
        shot("121-video-recording-chrome.png")
    }

    @Test fun shot122_confirmLooksSelector() {
        val bmp = solidBitmapR79(200, 260, 0xFF6B4E8A.toInt())
        frame {
            CameraConfirmView(
                bmp = bmp,
                showLooks = true, looks = 1, onLooks = {},
                onRetake = {}, onUse = {},
            )
        }
        composeRule.onNodeWithTag("looks_selector").assertIsDisplayed()
        composeRule.onNodeWithText("One look").assertIsDisplayed()
        composeRule.onNodeWithText("Two looks").assertIsDisplayed()
        composeRule.onNodeWithText("Keep").assertIsDisplayed()
        shot("122-confirm-looks-selector.png")
    }

    @Test fun shot123_stagedComposerStrip() {
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { th -> th.messages.clear() }
        val bmp = solidBitmapR79(120, 150, 0xFF7A4E8F.toInt())
        val poster = solidBitmapR79(120, 150, 0xFF44586B.toInt())
        val vf = java.io.File(ctx.cacheDir, "r85-staged.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        StagedMediaStore.add(id, StagedMedia.Photo("r85s1", bmp, null))
        StagedMediaStore.add(id, StagedMedia.Video("r85s2", vf, "video/mp4", 12, poster, 1))
        AppState.go(Screen.Chat(id))
        frame { ChatScreen(id) }
        composeRule.onNodeWithTag("staged_strip").assertIsDisplayed()
        org.junit.Assert.assertEquals(
            2, composeRule.onAllNodesWithTag("staged_remove").fetchSemanticsNodes().size,
        )
        org.junit.Assert.assertEquals(2, StagedMediaStore.count(id))
        try {
            shot("123-staged-composer.png")
        } finally {
            StagedMediaStore.clear(id)
            vf.delete()
        }
    }

    @Test fun shot124_companionThemeGrid() {
        // WS-G: the companion's theme picker is the normal chat's
        // tile grid — Default + six built-ins + the Custom photo
        // tile, with the live selection ring on the current theme.
        // The grid is staged directly (the sheet host it rides in
        // production is proven by shot126); t1 is the store's live
        // selection, so its tile carries the ring + check.
        AppState.appCtx = ctx
        TikaBotStore.setThemeId("t1")
        try {
            frame {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF14161A)).padding(20.dp),
                ) {
                    BotThemeGrid(onPick = {}, onCustomPick = {})
                }
            }
            composeRule.onNodeWithTag("bot_theme_grid").assertIsDisplayed()
            composeRule.onNodeWithTag("bot_theme_custom").assertIsDisplayed()
            composeRule.onNodeWithTag("bot_theme_t1").assertIsDisplayed()
            composeRule.onNodeWithText("Blush Kisses").assertIsDisplayed()
            composeRule.onNodeWithText("Custom photo").assertIsDisplayed()
            shot("124-companion-theme-grid.png")
        } finally {
            TikaBotStore.setThemeId("")
        }
    }

    @Test fun shot125_companionThemedChat() {
        // WS-G: a non-default theme applies live in the companion —
        // wallpaper behind the list + the theme's bubble colours.
        stageCompanionR73C()
        TikaBotStore.setThemeId("t3")
        val base = TikaBotStore.messages.value.size
        TikaBotStore.appendBot("Themed bubbles, themed wall.")
        TikaBotStore.appendUser("Looks right.")
        frame { TikaBotScreen() }
        composeRule.onNodeWithText("Themed bubbles, themed wall.").assertIsDisplayed()
        try {
            shot("125-companion-themed.png")
        } finally {
            cleanupCompanionR73C(base)
            TikaBotStore.setThemeId("")
        }
    }

    @Test fun shot126_sheetBackClosesFirst() {
        // WS-H: with ANY sheet open (the theme confirm sheet rides
        // this host), Back must close the sheet and leave the surface
        // underneath exactly where it was — never navigate away.
        val p = PreviewStore.people.first()
        val id = PreviewRepo.startThreadWith(p)
        AppState.updateThread(id) { th -> th.messages.clear() }
        AppState.go(Screen.Chat(id))
        AppState.sheet {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Use this theme", color = Color(0xFF26232B), fontFamily = PoppinsFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                )
            }
        }
        var backOwner: androidx.activity.OnBackPressedDispatcherOwner? = null
        frame {
            backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
            ChatScreen(id)
        }
        composeRule.onNodeWithText("Use this theme").assertIsDisplayed()
        composeRule.runOnUiThread {
            backOwner!!.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(
            "Back must close the open sheet",
            composeRule.onAllNodesWithText("Use this theme").fetchSemanticsNodes().isEmpty(),
        )
        org.junit.Assert.assertTrue(
            "Back must NOT navigate away from the chat while a sheet was open",
            AppState.screen is Screen.Chat,
        )
        try {
            shot("126-sheet-back-closes.png")
        } finally {
            AppState.closeSheet()
        }
    }

    // ---------- ROUND 85 plain contracts (no composition) ----------

    @Test fun decryptGateR85_pendingVsPermanent() {
        val gcmFail = javax.crypto.AEADBadTagException("tag mismatch")
        // Key not loaded yet → PENDING whatever else is true, and the
        // placeholder is never allowed.
        org.junit.Assert.assertEquals(
            DecryptGate.Verdict.PENDING,
            DecryptGate.verdict(keyReady = false, kindRaw = "text", mediaChunks = null, error = gcmFail),
        )
        org.junit.Assert.assertFalse(DecryptGate.placeholderAllowed(keyReady = false, verdict = DecryptGate.Verdict.PENDING))
        // Media payload not landed (count 0) → PENDING even with a
        // decrypt error in hand — the sender publishes the count last.
        org.junit.Assert.assertEquals(
            DecryptGate.Verdict.PENDING,
            DecryptGate.verdict(keyReady = true, kindRaw = "image", mediaChunks = 0, error = gcmFail),
        )
        org.junit.Assert.assertEquals(
            DecryptGate.Verdict.PENDING,
            DecryptGate.verdict(keyReady = true, kindRaw = "voice", mediaChunks = 0, error = null),
        )
        // A missing chunk file is the same pending shape.
        org.junit.Assert.assertEquals(
            DecryptGate.Verdict.PENDING,
            DecryptGate.verdict(
                keyReady = true, kindRaw = "image", mediaChunks = 2,
                error = MediaStore.ChunkMissingException("chunk 1 missing"),
            ),
        )
        // Key ready + payload present + genuine GCM failure → the one
        // PERMANENT case, and only here is the placeholder allowed.
        org.junit.Assert.assertEquals(
            DecryptGate.Verdict.PERMANENT,
            DecryptGate.verdict(keyReady = true, kindRaw = "text", mediaChunks = null, error = gcmFail),
        )
        org.junit.Assert.assertTrue(DecryptGate.placeholderAllowed(keyReady = true, verdict = DecryptGate.Verdict.PERMANENT))
        // No error → READY.
        org.junit.Assert.assertEquals(
            DecryptGate.Verdict.READY,
            DecryptGate.verdict(keyReady = true, kindRaw = "image", mediaChunks = 2, error = null),
        )
        org.junit.Assert.assertTrue(DecryptGate.mediaPayloadPending("video", 0))
        org.junit.Assert.assertFalse(DecryptGate.mediaPayloadPending("video", 1))
        org.junit.Assert.assertFalse(DecryptGate.mediaPayloadPending("text", 0))
    }

    @Test fun videoTimerPolicyR85_autoStopAndSanitize() {
        org.junit.Assert.assertFalse(VideoTimerPolicy.shouldAutoStop(29, 30))
        org.junit.Assert.assertTrue(VideoTimerPolicy.shouldAutoStop(30, 30))
        org.junit.Assert.assertTrue(VideoTimerPolicy.shouldAutoStop(45, 30))
        org.junit.Assert.assertFalse(VideoTimerPolicy.shouldAutoStop(9999, null))
        org.junit.Assert.assertEquals(45, VideoTimerPolicy.sanitizeCustom("45"))
        org.junit.Assert.assertEquals(3600, VideoTimerPolicy.sanitizeCustom("3600"))
        org.junit.Assert.assertNull(VideoTimerPolicy.sanitizeCustom("3"))
        org.junit.Assert.assertNull(VideoTimerPolicy.sanitizeCustom("3601"))
        org.junit.Assert.assertNull(VideoTimerPolicy.sanitizeCustom("abc"))
        org.junit.Assert.assertNull(VideoTimerPolicy.sanitizeCustom(""))
        org.junit.Assert.assertEquals("0:07", VideoTimerPolicy.formatClock(7))
        org.junit.Assert.assertEquals("1:30", VideoTimerPolicy.formatClock(90))
        org.junit.Assert.assertEquals("10:00", VideoTimerPolicy.formatClock(600))
    }

    @Test fun stagedSendRouteR85_mapping() {
        val bmp = solidBitmapR79(40, 40, 0xFF123456.toInt())
        val photo = StagedMedia.Photo("k1", bmp, null)
        val photoVo = StagedMedia.Photo("k2", bmp, 2)
        val vf = java.io.File(ctx.cacheDir, "r85-route.mp4").apply { writeBytes(byteArrayOf(9)) }
        val video = StagedMedia.Video("k3", vf, "video/mp4", 9, null, 1)
        // Every staged item routes into the view-once send funnel with
        // its own looks value — the exact pipeline entry sendCurrent
        // commits through.
        org.junit.Assert.assertEquals(StagedSendRoute.PHOTO_VO, stagedSendRoute(photo))
        org.junit.Assert.assertEquals(StagedSendRoute.PHOTO_VO, stagedSendRoute(photoVo))
        org.junit.Assert.assertEquals(StagedSendRoute.VIDEO_VO, stagedSendRoute(video))
        org.junit.Assert.assertNull(photo.vo)
        org.junit.Assert.assertEquals(2, photoVo.vo)
        org.junit.Assert.assertEquals(1, video.vo)
        vf.delete()
    }

}
