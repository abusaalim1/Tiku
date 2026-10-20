package com.tiku.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
 * PREVIEW_UI_ONLY flag uses); the facade consumes the real bundled content pack.
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
        StudyContent.load(ctx)
        AppState.repo = PreviewRepo
        val seeded = PreviewStore.seedThreads()
        AppState.threads.value = seeded
        AppState.callLogs.value = PreviewStore.seedCallLogsFor(seeded)
        AppState.typing.value = emptyMap()
        AppState.homeTab = HomeTab.CHATS
        AppState.sheetContent = null
        AppState.toastText = null
        AppState.story = null
        // one real local note so the notes page isn't empty
        if (StudyStore.notes(ctx).isEmpty()) {
            StudyStore.saveNote(
                ctx,
                StudyStore.Note(
                    subject = "Physics",
                    title = "Units & dimensions — quick notes",
                    body = "Dimensional formula of energy = [M L^2 T^-2]. Always check homogeneity before solving.",
                ),
            )
        }
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

    // ---------- facade ----------
    @Test fun shot01_facadeHome() {
        AppState.go(Screen.Facade)
        frame { FacadeRouter() }
        shot("01-facade-home.png")
    }

    @Test fun shot02_subject() {
        frame { SubjectScreen("Physics") }
        shot("02-subject.png")
    }

    @Test fun shot03_chapter() {
        frame { ChapterScreen("Physics", 0) }
        composeRule.onNodeWithText("Tap to reveal answer").performClick()
        composeRule.waitForIdle()
        shot("03-chapter.png")
    }

    @Test fun shot04_timer() {
        frame { TimerScreen() }
        shot("04-timer.png")
    }

    @Test fun shot05_notes() {
        frame { NotesScreen() }
        shot("05-notes.png")
    }

    @Test fun shot07_tikGate() {
        frame { TikGateScreen() }
        shot("07-tik-gate.png")
    }

    @Test fun shot19_qotdAnswered() {
        AppState.go(Screen.Facade)
        frame { FacadeRouter() }
        val q = StudyContent.questionOfTheDay()
        if (q != null) {
            composeRule.onNodeWithText(q.options[q.correct], substring = true).performClick()
            composeRule.waitForIdle()
        }
        shot("19-qotd-answered.png")
    }

    // ---------- onboarding (study-framed) ----------
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
        composeRule.onNode(hasScrollAction())
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
        frame { CallScreen(th.id, false) }
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
}
