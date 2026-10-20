package com.erdbot.mediagrab.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erdbot.mediagrab.R

enum class ThemeId(val title: String, val blurb: String) {
    NIGHT_SHIFT("Night Shift", "Dark, lime accent, contact-sheet grid"),
    SCRAPBOOK("Scrapbook", "Warm paper and taped-in photos"),
    LOUD("Loud", "Yellow, thick outlines, chunky cards"),
    INDEX("Index", "Minimal Swiss list, one bold blue"),
}

/** How the Media tab lays out downloads. */
enum class MediaLayout { CONTACT_SHEET, POLAROIDS, CARDS, LIST }

/** Everything that differs between themes; screens read it through [Mg]. */
data class MgTheme(
    val id: ThemeId,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val outline: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val display: FontFamily,
    val body: FontFamily,
    val mono: FontFamily,
    /** Optional handwriting face for little notes (Scrapbook only). */
    val hand: FontFamily?,
    val displayWeight: FontWeight,
    val radius: Dp,
    val borderWidth: Dp,
    /** Offset of a solid, unblurred shadow under cards and buttons (0 = none). */
    val hardShadow: Dp,
    val uppercase: Boolean,
    val mediaLayout: MediaLayout,
    val headline: String,
    val grabTab: String,
    val mediaTab: String,
    val grabButton: String,
) {
    val shape: Shape get() = RoundedCornerShape(radius)
    fun caps(s: String) = if (uppercase) s.uppercase() else s
}

private val bricolage = FontFamily(Font(R.font.bricolage_grotesque_700, FontWeight.Bold), Font(R.font.bricolage_grotesque_800, FontWeight.ExtraBold))
private val plexSans = FontFamily(
    Font(R.font.ibm_plex_sans_400, FontWeight.Normal), Font(R.font.ibm_plex_sans_500, FontWeight.Medium), Font(R.font.ibm_plex_sans_600, FontWeight.SemiBold),
)
private val plexMono = FontFamily(Font(R.font.ibm_plex_mono_400, FontWeight.Normal), Font(R.font.ibm_plex_mono_500, FontWeight.Medium))
private val instrumentSerif = FontFamily(
    Font(R.font.instrument_serif_400, FontWeight.Normal), Font(R.font.instrument_serif_400_italic, FontWeight.Normal, FontStyle.Italic),
)
private val instrumentSans = FontFamily(
    Font(R.font.instrument_sans_400, FontWeight.Normal), Font(R.font.instrument_sans_500, FontWeight.Medium), Font(R.font.instrument_sans_600, FontWeight.SemiBold),
)
private val caveat = FontFamily(Font(R.font.caveat_600, FontWeight.SemiBold))
private val archivoBlack = FontFamily(Font(R.font.archivo_black_400, FontWeight.Normal))
private val archivo = FontFamily(
    Font(R.font.archivo_400, FontWeight.Normal), Font(R.font.archivo_600, FontWeight.SemiBold), Font(R.font.archivo_700, FontWeight.Bold),
)
private val jetbrains = FontFamily(Font(R.font.jetbrains_mono_500, FontWeight.Medium), Font(R.font.jetbrains_mono_700, FontWeight.Bold))
private val schibsted = FontFamily(
    Font(R.font.schibsted_grotesk_400, FontWeight.Normal), Font(R.font.schibsted_grotesk_500, FontWeight.Medium),
    Font(R.font.schibsted_grotesk_700, FontWeight.Bold), Font(R.font.schibsted_grotesk_900, FontWeight.Black),
)
private val martian = FontFamily(Font(R.font.martian_mono_400, FontWeight.Normal), Font(R.font.martian_mono_500, FontWeight.Medium))

fun themeFor(id: ThemeId): MgTheme = when (id) {
    ThemeId.NIGHT_SHIFT -> MgTheme(
        id = id, dark = true,
        background = Color(0xFF0D0E10), surface = Color(0xFF16171B), surfaceAlt = Color(0xFF23262D),
        outline = Color(0xFF2A2D34), text = Color(0xFFF2F2EF), muted = Color(0xFFA3A7B0),
        accent = Color(0xFFC6F432), onAccent = Color(0xFF0D0E10), accentSoft = Color(0xFF283010),
        display = bricolage, body = plexSans, mono = plexMono, hand = null, displayWeight = FontWeight.ExtraBold,
        radius = 18.dp, borderWidth = 1.dp, hardShadow = 0.dp, uppercase = false,
        mediaLayout = MediaLayout.CONTACT_SHEET,
        headline = "Grab it before it scrolls away.", grabTab = "Grab", mediaTab = "Media", grabButton = "Get media",
    )
    ThemeId.SCRAPBOOK -> MgTheme(
        id = id, dark = false,
        background = Color(0xFFF3EEE4), surface = Color(0xFFFFFDF8), surfaceAlt = Color(0xFFE9E2D4),
        outline = Color(0xFF1C1A17), text = Color(0xFF1C1A17), muted = Color(0xFF5A544B),
        accent = Color(0xFFC23B22), onAccent = Color(0xFFFFFDF8), accentSoft = Color(0xFFF2D9D2),
        display = instrumentSerif, body = instrumentSans, mono = instrumentSans, hand = caveat, displayWeight = FontWeight.Normal,
        radius = 4.dp, borderWidth = 1.5.dp, hardShadow = 0.dp, uppercase = false,
        mediaLayout = MediaLayout.POLAROIDS,
        headline = "Keep the good stuff.", grabTab = "Pin", mediaTab = "Scrapbook", grabButton = "Pin it",
    )
    ThemeId.LOUD -> MgTheme(
        id = id, dark = false,
        background = Color(0xFFFFE14D), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFFFF3B0),
        outline = Color(0xFF111111), text = Color(0xFF111111), muted = Color(0xFF3D3A2A),
        accent = Color(0xFF111111), onAccent = Color(0xFFFFE14D), accentSoft = Color(0xFFFFF3B0),
        display = archivoBlack, body = archivo, mono = jetbrains, hand = null, displayWeight = FontWeight.Normal,
        radius = 0.dp, borderWidth = 3.dp, hardShadow = 4.dp, uppercase = true,
        mediaLayout = MediaLayout.CARDS,
        headline = "Grab any video.", grabTab = "Grab", mediaTab = "Stash", grabButton = "Go",
    )
    ThemeId.INDEX -> MgTheme(
        id = id, dark = false,
        background = Color(0xFFFAFAF7), surface = Color(0xFFFAFAF7), surfaceAlt = Color(0xFFEDEDE7),
        outline = Color(0xFF0B0B0B), text = Color(0xFF0B0B0B), muted = Color(0xFF55554F),
        accent = Color(0xFF1F3BFF), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFE3E7FF),
        display = schibsted, body = schibsted, mono = martian, hand = null, displayWeight = FontWeight.Black,
        radius = 0.dp, borderWidth = 2.dp, hardShadow = 0.dp, uppercase = false,
        mediaLayout = MediaLayout.LIST,
        headline = "Paste a link. Get the file. Nothing else.", grabTab = "Grab", mediaTab = "Media", grabButton = "Grab →",
    )
}

/** Platform tag colours readable on each theme's background. */
fun MgTheme.platformColor(platform: com.erdbot.mediagrab.extract.Platform): Color = when (platform) {
    com.erdbot.mediagrab.extract.Platform.TWITTER -> text
    com.erdbot.mediagrab.extract.Platform.INSTAGRAM -> if (dark) Color(0xFFF0B6FF) else Color(0xFFB0127A)
    com.erdbot.mediagrab.extract.Platform.FACEBOOK -> if (dark) Color(0xFF9DC1FF) else Color(0xFF1F55C7)
    com.erdbot.mediagrab.extract.Platform.YOUTUBE -> if (dark) Color(0xFFFF9C8A) else Color(0xFFC0281A)
}

val LocalMg = staticCompositionLocalOf { themeFor(ThemeId.NIGHT_SHIFT) }

/** The current theme, e.g. `Mg.accent`. */
val Mg: MgTheme @Composable get() = LocalMg.current

@Composable
fun MediaGrabTheme(id: ThemeId, content: @Composable () -> Unit) {
    val t = themeFor(id)
    // Material components (menus, dialogs, snackbars) follow the theme too.
    val scheme = if (t.dark) {
        darkColorScheme(
            primary = t.accent, onPrimary = t.onAccent, background = t.background, onBackground = t.text,
            surface = t.surface, onSurface = t.text, surfaceVariant = t.surfaceAlt, onSurfaceVariant = t.muted,
            outline = t.outline, inverseSurface = t.text, inverseOnSurface = t.background,
        )
    } else {
        lightColorScheme(
            primary = t.accent, onPrimary = t.onAccent, background = t.background, onBackground = t.text,
            surface = t.surface, onSurface = t.text, surfaceVariant = t.surfaceAlt, onSurfaceVariant = t.muted,
            outline = t.outline, inverseSurface = t.text, inverseOnSurface = t.background,
            surfaceContainer = t.surface,
        )
    }
    CompositionLocalProvider(LocalMg provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
