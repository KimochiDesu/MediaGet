package com.erdbot.mediagrab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erdbot.mediagrab.extract.Platform
import com.erdbot.mediagrab.ui.theme.Mg
import com.erdbot.mediagrab.ui.theme.MgTheme
import com.erdbot.mediagrab.ui.theme.platformColor

/** A solid, unblurred offset shadow (the Loud theme's signature), drawn outside the shape. */
fun Modifier.hardShadow(offset: Dp, color: Color, shape: Shape): Modifier =
    if (offset <= 0.dp) this else drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(offset.toPx(), offset.toPx()) { drawOutline(outline, color) }
    }

/** The themed container: fill, border, radius and (for Loud) a hard shadow. */
fun Modifier.mgSurface(t: MgTheme, fill: Color = t.surface, shadow: Boolean = true, border: Color = t.outline): Modifier =
    this.hardShadow(if (shadow) t.hardShadow else 0.dp, t.outline, t.shape)
        .clip(t.shape)
        .background(fill)
        .border(t.borderWidth, border, t.shape)

@Composable
fun MgCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val t = Mg
    Column(
        modifier.mgSurface(t).padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Small technical label: monospace, spaced capitals. */
@Composable
fun MgLabel(text: String, modifier: Modifier = Modifier, color: Color = Mg.muted) {
    val t = Mg
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(fontFamily = t.mono, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium, color = color),
    )
}

@Composable
fun MgDisplay(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = Mg.text) {
    val t = Mg
    Text(
        t.caps(text),
        modifier = modifier,
        style = TextStyle(
            fontFamily = t.display, fontWeight = t.displayWeight, fontSize = size,
            lineHeight = size * 1.0f, letterSpacing = (-0.02f * size.value).sp, color = color,
        ),
    )
}

@Composable
fun MgBody(text: String, modifier: Modifier = Modifier, color: Color = Mg.text, size: TextUnit = 15.sp, weight: FontWeight = FontWeight.Normal, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text, modifier = modifier, maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = Mg.body, fontSize = size, fontWeight = weight, color = color, lineHeight = size * 1.4f),
    )
}

enum class ButtonKind { PRIMARY, SECONDARY, GHOST }

/** Themed button; ≥48dp tall so it is easy to hit. */
@Composable
fun MgButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    fill: Color? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = Mg
    val bg = fill ?: when (kind) {
        ButtonKind.PRIMARY -> t.accent
        ButtonKind.SECONDARY -> t.surfaceAlt
        ButtonKind.GHOST -> Color.Transparent
    }
    val fg = when {
        fill != null -> t.text
        kind == ButtonKind.PRIMARY -> t.onAccent
        else -> t.text
    }
    Row(
        modifier
            .height(height)
            .mgSurface(t, fill = if (enabled) bg else bg.copy(alpha = 0.5f), shadow = kind == ButtonKind.PRIMARY,
                border = if (kind == ButtonKind.PRIMARY && t.borderWidth < 2.dp) bg else t.outline)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Text(
            t.caps(text),
            style = TextStyle(fontFamily = t.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = fg),
        )
    }
}

/** Filter chip used in the Media tab. */
@Composable
fun MgChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val t = Mg
    Box(
        Modifier
            .defaultMinSize(minHeight = 40.dp)
            .mgSurface(t, fill = if (selected) t.accent else Color.Transparent, shadow = false,
                border = if (selected) t.accent else t.outline.copy(alpha = if (t.dark) 1f else 0.6f))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            t.caps(text),
            style = TextStyle(fontFamily = t.body, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (selected) t.onAccent else t.text),
        )
    }
}

fun platformShort(p: Platform) = when (p) {
    Platform.TWITTER -> "X"
    Platform.INSTAGRAM -> "IG"
    Platform.FACEBOOK -> "FB"
    Platform.YOUTUBE -> "YT"
}

fun platformName(p: Platform) = when (p) {
    Platform.TWITTER -> "X"
    Platform.INSTAGRAM -> "Instagram"
    Platform.FACEBOOK -> "Facebook"
    Platform.YOUTUBE -> "YouTube"
}

/** "YT 1080p"-style tag on a dark pill, readable over any thumbnail. */
@Composable
fun PlatformTag(platform: Platform, extra: String = "", modifier: Modifier = Modifier) {
    val t = Mg
    val dark = com.erdbot.mediagrab.ui.theme.themeFor(com.erdbot.mediagrab.ui.theme.ThemeId.NIGHT_SHIFT)
    Text(
        listOf(platformShort(platform), extra).filter { it.isNotBlank() }.joinToString(" "),
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(if (t.radius > 0.dp) 6.dp else 0.dp))
            .background(Color(0xE60D0E10))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        style = TextStyle(fontFamily = t.mono, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = dark.platformColor(platform)),
    )
}

/** A tappable row inside a settings card. */
@Composable
fun SettingRow(title: String, subtitle: String, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MgBody(title, size = 15.sp, weight = FontWeight.SemiBold)
            MgBody(subtitle, size = 13.sp, color = Mg.muted)
        }
        trailing()
    }
}

val NoPadding = PaddingValues(0.dp)
