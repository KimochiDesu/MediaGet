package com.erdbot.mediagrab

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erdbot.mediagrab.ui.AppScreen
import com.erdbot.mediagrab.ui.LoginActivity
import com.erdbot.mediagrab.ui.MainViewModel
import com.erdbot.mediagrab.ui.theme.MediaGrabTheme
import com.erdbot.mediagrab.ui.theme.themeFor

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** 0 = Grab tab, 1 = Media tab. */
    private val tab = mutableIntStateOf(0)

    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshLogins()
        // Retry the link that asked for a login.
        val s = viewModel.state.value
        if (s.loginSite != null && (s.instagramLoggedIn || s.facebookLoggedIn || s.xLoggedIn || s.youtubeLoggedIn)) viewModel.fetch()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val themeId by viewModel.theme.collectAsStateWithLifecycle()
            LaunchedEffect(themeId) {
                // Status/navigation bar icons must contrast with the theme's background.
                val style = if (themeFor(themeId).dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MediaGrabTheme(themeId) {
                AppScreen(
                    viewModel = viewModel,
                    tab = tab.intValue,
                    onTab = { tab.intValue = it },
                    onLogin = { site -> login.launch(LoginActivity.intent(this, site)) },
                )
            }
        }
    }

    /** Android only lets apps read the clipboard while focused, so check on focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        viewModel.onClipboard(text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                tab.intValue = 0
                viewModel.onShared(it)
            }
            intent?.getBooleanExtra(EXTRA_OPEN_MEDIA, false) == true -> tab.intValue = 1
        }
    }

    companion object {
        const val EXTRA_OPEN_MEDIA = "open_media"
    }
}
