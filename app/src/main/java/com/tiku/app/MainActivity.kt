package com.tiku.app

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TikuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        FirebaseEngine.ensureChannel(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bootstrap()
        setContent { TikuRoot(onGoogleClick = ::onGoogleClick) }
    }

    override fun onResume() {
        super.onResume()
        if (!Config.PREVIEW_UI_ONLY && FirebaseEngine.signedIn && isOnboarded()) FirebaseEngine.setPresence(true)
        applySecureFlag()
    }

    override fun onPause() {
        super.onPause()
        if (!Config.PREVIEW_UI_ONLY && FirebaseEngine.signedIn) {
            FirebaseEngine.setPresence(false)
        }
    }

    private fun isOnboarded(): Boolean =
        getSharedPreferences("tiku_prefs", MODE_PRIVATE).getBoolean("onboarded", false)

    private fun applySecureFlag() {
        val block = SettingsStore.state.value.toggles["bs"] == true
        if (block) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Repo + data bootstrap, cold-start route. */
    private fun bootstrap() {
        if (Config.PREVIEW_UI_ONLY) {
            AppState.repo = PreviewRepo
            if (AppState.threads.value.isEmpty()) {
                val seeded = PreviewStore.seedThreads()
                AppState.threads.value = seeded
                AppState.callLogs.value = PreviewStore.seedCallLogsFor(seeded)
            }
            AppState.go(Screen.Welcome)
            return
        }
        AppState.repo = FirebaseEngine
        FirebaseEngine.appCtx = applicationContext
        if (!FirebaseEngine.signedIn) {
            AppState.go(Screen.Welcome)
            return
        }
        lifecycleScope.launch {
            val onboarded = isOnboarded()
            when {
                onboarded && FirebaseEngine.loadLocalIdentity(this@MainActivity) -> {
                    FirebaseEngine.start(this@MainActivity)
                    FirebaseEngine.loadMyProfile()
                    FirebaseEngine.refreshCallLogs()
                    FirebaseEngine.setPresence(true)
                    if (SettingsStore.state.value.appLock && SettingsStore.passwordHash(this@MainActivity) != null) {
                        AppState.go(Screen.Locked)
                    } else AppState.go(Screen.Home)
                }
                FirebaseEngine.isReturningUser() -> AppState.go(Screen.Restore)
                else -> AppState.go(Screen.Password)
            }
        }
    }

    /** Welcome Google button: 1.1s spinner in preview; real Credential Manager + Firebase otherwise. */
    private fun onGoogleClick() {
        if (Config.PREVIEW_UI_ONLY) {
            lifecycleScope.launch { delay(1100); AppState.authBusy = false; AppState.go(Screen.Password) }
            return
        }
        lifecycleScope.launch {
            val result = FirebaseEngine.googleSignIn(this@MainActivity)
            AppState.authBusy = false
            if (result.isFailure) {
                AppState.toast("Sign-in failed. Please try again.")
                return@launch
            }
            when {
                FirebaseEngine.isReturningUser() && FirebaseEngine.loadLocalIdentity(this@MainActivity) -> {
                    getSharedPreferences("tiku_prefs", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
                    FirebaseEngine.start(this@MainActivity)
                    FirebaseEngine.loadMyProfile()
                    FirebaseEngine.refreshCallLogs()
                    FirebaseEngine.setPresence(true)
                    AppState.go(Screen.Home)
                }
                FirebaseEngine.isReturningUser() -> AppState.go(Screen.Restore)
                else -> AppState.go(Screen.Password)
            }
        }
    }
}

@Composable
fun TikuRoot(onGoogleClick: () -> Unit) {
    // app-wide light gradient (the design's .s background)
    val bgBrush = Brush.verticalGradient(listOf(TikuTokens.BgTop, TikuTokens.BgBottom))
    val screen = AppState.screen
    Box(Modifier.fillMaxSize().background(bgBrush)) {
        val target = when (screen) {
            is Screen.Welcome, is Screen.Password, is Screen.Restore,
            is Screen.ProfileSetup, is Screen.Locked -> "gate"
            else -> "main"
        }
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                (slideInHorizontally(tween(550, easing = TikuEase)) { it / 3 } + fadeIn(tween(450, easing = TikuEase)))
                    .togetherWith(slideOutHorizontally(tween(550, easing = TikuEase)) { -it / 4 } + fadeOut(tween(300, easing = TikuEase)))
            },
            label = "root",
        ) { kind ->
            if (kind == "gate") {
                when (screen) {
                    is Screen.Welcome -> WelcomeScreen(onGoogleClick)
                    is Screen.Password -> CreatePasswordScreen()
                    is Screen.Restore -> RestorePasswordScreen()
                    is Screen.ProfileSetup -> ProfileSetupScreen()
                    is Screen.Locked -> LockedScreen()
                    else -> WelcomeScreen(onGoogleClick)
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen()
                    when (screen) {
                        is Screen.Chat -> ChatScreen(screen.threadId)
                        is Screen.Call -> {
                            val s = screen as? Screen.Call ?: return@Box
                            CallScreen(s.threadId, s.video)
                        }
                        is Screen.Story -> StoryScreen()
                        else -> {}
                    }
                }
            }
        }
        DesignToast()
        BottomSheetHost()
    }
    // block-screenshots toggle applies FLAG_SECURE live
    val settings by SettingsStore.state.collectAsState()
    LaunchedEffect(settings.toggles["bs"]) { /* handled in MainActivity.onResume / below hook */ }
}
