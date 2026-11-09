package uz.skillswap.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uz.skillswap.app.ui.theme.BrandEnd
import uz.skillswap.app.ui.theme.BrandStart

// ---- colours that carry meaning ----
val Gold = Color(0xFFF2B84B)      // stars (warning colour of the palette)
val Green = Color(0xFF25B879)     // success, free places
val Red = Color(0xFFEF5350)       // error, full, cancelled

/** Each category gets its own colour. */
fun categoryColor(category: String): Color = when (category) {
    "Tech", "Programming" -> Color(0xFF3B82F6)
    "Languages" -> Color(0xFF10B981)
    "Design" -> Color(0xFFEC4899)
    "Music" -> Color(0xFFF97316)
    "Fitness & Sport" -> Color(0xFF22C55E)
    "Business & Study" -> Color(0xFFF59E0B)
    "Cooking & Crafts", "Life" -> Color(0xFFEF4444)
    else -> Color(0xFF8B5CF6)
}

/** Bronze / silver / gold, the same idea as the website. */
fun levelColor(level: String): Color = when (level) {
    "Advanced", "Gold" -> Color(0xFFF5B301)
    "Intermediate", "Silver" -> Color(0xFFA8B0C0)
    else -> Color(0xFFCD7F32)
}

fun levelMedal(level: String): String = when (level) {
    "Advanced", "Gold" -> "🥇"
    "Intermediate", "Silver" -> "🥈"
    else -> "🥉"
}

// ---- motion helpers (all short: 120-300 ms) ----

/** Small scale-down while a card or button is pressed. */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** List entrance: the first items fade and slide up one after another. Later items just appear. */
@Composable
fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(index >= 8) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index * 45L)
            shown = true
        }
    }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "stagger")
    Box(Modifier.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 24.dp.toPx()
    }) { content() }
}

/** A number that counts up to its value. */
@Composable
fun CountUpText(target: Int, style: TextStyle, color: Color) {
    val value by animateIntAsState(target, tween(700), label = "count")
    Text("$value", style = style, color = color)
}

// ---- layout building blocks ----

/** The coloured header with rounded bottom corners. It also fills the area behind the status bar. */
@Composable
fun GradientHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(BrandStart, BrandEnd)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

/** The one card style of the app. Pass onClick to make it tappable (it shrinks a little when pressed). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick == null) {
        Card(modifier, shape = shape, colors = colors, border = border, content = content)
    } else {
        val source = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            modifier = modifier.pressScale(source),
            shape = shape, colors = colors, border = border,
            interactionSource = source,
            content = content
        )
    }
}

@Composable
fun EmojiTile(emoji: String, category: String, size: Dp = 56.dp) {
    val color = categoryColor(category)
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun LevelBadge(level: String) {
    val color = levelColor(level)
    Box(
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text("${levelMedal(level)} $level", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SmallPill(text: String, color: Color) {
    Box(
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** A tappable chip we draw ourselves: colour fades between selected and idle, and it shrinks when pressed. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedBg: Color = MaterialTheme.colorScheme.primary,
    selectedFg: Color = Color.White,
    idleBg: Color = MaterialTheme.colorScheme.surfaceVariant,
    idleFg: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) selectedBg else idleBg, tween(180), label = "pillBg")
    val fg by animateColorAsState(if (selected) selectedFg else idleFg, tween(180), label = "pillFg")
    Box(
        modifier = Modifier
            .pressScale(source)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun StarRow(rating: Int) {
    Text("★".repeat(rating) + "☆".repeat((5 - rating).coerceAtLeast(0)), color = Gold,
        style = MaterialTheme.typography.titleSmall)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/** Friendly message when a list is empty. */
@Composable
fun EmptyState(emoji: String, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(emoji, fontSize = 44.sp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Are you sure?" dialog used before cancelling or finishing a lesson. */
@Composable
fun ConfirmDialog(
    title: String, text: String, confirmLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Go back") } }
    )
}
