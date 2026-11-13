package uz.skillswap.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Emoji that can still arrive inside a text (our strings, or a line from the server like
 * "Jasur taught Guitar · +1 🪙") and the icon of our own set that is drawn in their place.
 * The longest emoji come first ("🧑‍🏫" is several characters joined together).
 */
private val INLINE_ICONS: List<Pair<String, String>> = listOf(
    "🧑‍🏫" to "easel",
    "↩" to "undo",
    "🪙" to "coin",
    "✨" to "sparkle",
    "🔁" to "swap",
    "✅" to "check_circle",
    "📅" to "calendar",
    "🎥" to "video",
    "⭐" to "star_fill",
    "📷" to "scan",
    "🎁" to "gift",
    "📚" to "book",
    "🎓" to "cap",
    "🏆" to "trophy",
    "🕒" to "clock",
    "🎉" to "party",
    "🔔" to "bell",
    "👋" to ""                  // greeting hand: simply left out
)

/** A piece of a text: plain words ([icon] = null), or one of our icons in place of an emoji. */
class TextPiece(val text: String, val icon: String?)

/** Splits a text into words and icons. One space is kept after an icon (some strings have two). */
fun splitIcons(text: String): List<TextPiece> {
    val pieces = ArrayList<TextPiece>()
    val plain = StringBuilder()
    var i = 0
    while (i < text.length) {
        val hit = INLINE_ICONS.firstOrNull { text.startsWith(it.first, i) }
        if (hit == null) {
            plain.append(text[i])
            i += 1
            continue
        }
        i += hit.first.length
        while (i < text.length && text[i] == '️') i += 1          // emoji style marker
        if (hit.second.isEmpty()) {                                    // dropped: take its space too
            if (plain.endsWith(" ") && (i >= text.length || text[i] == ' ')) plain.setLength(plain.length - 1)
            continue
        }
        if (plain.isNotEmpty()) {
            pieces.add(TextPiece(plain.toString(), null))
            plain.setLength(0)
        }
        pieces.add(TextPiece(hit.first, hit.second))
        if (i < text.length && text[i] == ' ') {
            plain.append(' ')
            while (i < text.length && text[i] == ' ') i += 1
        }
    }
    if (plain.isNotEmpty()) pieces.add(TextPiece(plain.toString().trimEnd(), null))
    return pieces
}

/** Text in which emoji are drawn as our own icons, in the same colour as the words (or [iconColor]). */
@Composable
fun IconText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    iconColor: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val pieces = remember(text) { splitIcons(text) }
    // PERFORMANCE: most texts (pills, names, levels) have no emoji at all - draw them as plain Text.
    // Before, every row built a new map of icon lambdas on every recomposition, so Compose could never skip it.
    val hasIcons = remember(pieces) { pieces.any { it.icon != null } }
    if (!hasIcons) {
        val plain = remember(pieces) { pieces.joinToString("") { it.text } }
        Text(
            plain, modifier = modifier, color = color, fontWeight = fontWeight, textAlign = textAlign,
            maxLines = maxLines, overflow = overflow, style = style
        )
        return
    }
    val tint = when {
        iconColor.isSpecified -> iconColor
        color.isSpecified -> color
        style.color.isSpecified -> style.color
        else -> LocalContentColor.current
    }
    val annotated = remember(pieces) {
        buildAnnotatedString {
            for (p in pieces) {
                if (p.icon == null) append(p.text) else appendInlineContent("icon:${p.icon}", p.text)
            }
        }
    }
    val inline = remember(pieces, tint) {
        val map = HashMap<String, InlineTextContent>()
        for (p in pieces) {
            val id = p.icon ?: continue
            map["icon:$id"] = InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(SkillIcons.named(id), contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
            }
        }
        map
    }
    Text(
        annotated, modifier = modifier, color = color, fontWeight = fontWeight, textAlign = textAlign,
        maxLines = maxLines, overflow = overflow, inlineContent = inline, style = style
    )
}

/** One icon of our set, e.g. AppIcon("calendar"). */
@Composable
fun AppIcon(id: String, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = LocalContentColor.current) {
    Icon(SkillIcons.named(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
