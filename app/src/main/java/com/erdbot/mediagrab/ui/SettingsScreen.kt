package com.erdbot.mediagrab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erdbot.mediagrab.ui.theme.Mg
import com.erdbot.mediagrab.ui.theme.ThemeId
import com.erdbot.mediagrab.ui.theme.themeFor

/** Theme picker, site logins and preferences. */
@Composable
fun SettingsScreen(viewModel: MainViewModel, state: UiState, onBack: () -> Unit, onLogin: (String) -> Unit) {
    val t = Mg
    val current by viewModel.theme.collectAsStateWithLifecycle()
    val suggest by viewModel.suggestClipboard.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).mgSurface(t, shadow = false).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = t.text)
            }
            MgDisplay("Settings", 30.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MgLabel("Theme")
            ThemeId.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { id -> ThemeSwatch(id, id == current, Modifier.weight(1f)) { viewModel.setTheme(id) } }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MgLabel("Accounts")
            MgCard(padding = 14.dp) {
                AccountRow("Instagram", state.instagramLoggedIn, "Needed for stories and many posts",
                    onLogin = { onLogin(LoginActivity.SITE_INSTAGRAM) },
                    onLogout = { viewModel.logout(LoginActivity.SITE_INSTAGRAM) })
                HorizontalDivider(color = t.outline.copy(alpha = 0.4f))
                AccountRow("Facebook", state.facebookLoggedIn, "Unlocks videos that aren't public",
                    onLogin = { onLogin(LoginActivity.SITE_FACEBOOK) },
                    onLogout = { viewModel.logout(LoginActivity.SITE_FACEBOOK) })
                HorizontalDivider(color = t.outline.copy(alpha = 0.4f))
                AccountRow("X", state.xLoggedIn, "For protected accounts you follow",
                    onLogin = { onLogin(LoginActivity.SITE_X) },
                    onLogout = { viewModel.logout(LoginActivity.SITE_X) })
                HorizontalDivider(color = t.outline.copy(alpha = 0.4f))
                AccountRow("YouTube", state.youtubeLoggedIn, "For age-restricted videos",
                    onLogin = { onLogin(LoginActivity.SITE_YOUTUBE) },
                    onLogout = { viewModel.logout(LoginActivity.SITE_YOUTUBE) })
            }
            MgBody("You sign in on the site's own page. MediaGrab never sees your password; the session stays on this phone.",
                size = 12.sp, color = t.muted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MgLabel("Preferences")
            MgCard(padding = 14.dp) {
                SettingRow("Suggest copied links", "Offer a link you copied when you open the app", onClick = { viewModel.setSuggestClipboard(!suggest) }) {
                    Switch(
                        checked = suggest,
                        onCheckedChange = viewModel::setSuggestClipboard,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = t.accent, checkedThumbColor = t.onAccent,
                            uncheckedTrackColor = t.surfaceAlt, uncheckedThumbColor = t.muted, uncheckedBorderColor = t.outline,
                        ),
                    )
                }
                HorizontalDivider(color = t.outline.copy(alpha = 0.4f))
                SettingRow("Saved to", "Downloads/MediaGrab", onClick = null) {}
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AccountRow(name: String, loggedIn: Boolean, why: String, onLogin: () -> Unit, onLogout: () -> Unit) {
    val t = Mg
    SettingRow(name, if (loggedIn) "Logged in" else why, onClick = null) {
        MgButton(
            if (loggedIn) "Log out" else "Log in",
            if (loggedIn) onLogout else onLogin,
            kind = if (loggedIn) ButtonKind.GHOST else ButtonKind.PRIMARY,
            height = 44.dp,
        )
    }
}

/** A small preview drawn in the theme it represents. */
@Composable
private fun ThemeSwatch(id: ThemeId, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = Mg
    val p = themeFor(id)
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) t.accent else t.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(p.background).padding(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    p.caps("Aa"),
                    style = TextStyle(fontFamily = p.display, fontWeight = p.displayWeight, fontSize = 28.sp, color = p.text),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(width = 44.dp, height = 18.dp)
                            .hardShadow(if (p.hardShadow > 0.dp) 2.dp else 0.dp, p.outline, RoundedCornerShape(p.radius.coerceAtMost(9.dp)))
                            .clip(RoundedCornerShape(p.radius.coerceAtMost(9.dp))).background(p.accent),
                    )
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(p.radius.coerceAtMost(9.dp))).background(p.surfaceAlt))
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(t.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = t.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(t.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(id.title, style = TextStyle(fontFamily = t.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = t.text))
            Text(id.blurb, style = TextStyle(fontFamily = t.body, fontSize = 11.sp, color = t.muted, lineHeight = 14.sp), minLines = 2)
        }
    }
}
