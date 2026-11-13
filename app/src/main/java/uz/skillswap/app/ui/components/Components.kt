package uz.skillswap.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Icon
import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
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
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary

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

// ---- motion helpers (all short: 120-300 ms) ----

/** Small scale-down while a card or button is pressed. */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    // a spring, like iOS: quick down, small bounce back up
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f), label = "press"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * List entrance: the first items fade and slide up one after another. Later items just appear.
 * [enabled] = false after the screen's first moment: a LazyColumn forgets rows that scroll away, so
 * without it the top rows played the entrance AGAIN (blank, then fading in) every time you scrolled back up.
 */
@Composable
fun StaggerIn(index: Int, enabled: Boolean = true, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(!enabled || index >= 8) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index * 45L)
            shown = true
        }
    }
    val progress by animateFloatAsState(
        if (shown) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 240f), label = "stagger"
    )
    Box(Modifier.graphicsLayer {
        alpha = progress.coerceIn(0f, 1f)
        translationY = (1f - progress) * 24.dp.toPx()
    }) { content() }
}

/** A number that counts up to its value. */
@Composable
fun CountUpText(target: Int, style: TextStyle, color: Color) {
    val value by animateIntAsState(target, tween(700), label = "count")
    Text("$value", style = style, color = color)
}

// ---- glass look ----

/** Translucent card fill: the coloured glow behind the app shines through it. */
@Composable
fun glassFill(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)
    else Color.White.copy(alpha = 0.50f)

/** The light edge of a glass panel: brighter at the top-left, fading out. */
@Composable
fun glassBorder(): Brush {
    // made once per theme, not again for every card that scrolls into view
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f)))
        else Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.35f)))
    }
}

/**
 * The background of the whole app: three soft colour glows.
 * PERFORMANCE: it is static and its gradients are built once (drawWithCache), so it costs almost
 * nothing per frame - the old drifting animation repainted the whole screen 60 times a second.
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme.background
    val strength = if (dark) 0.30f else 0.26f       // gold glows need less strength than purple
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val w = size.width
            val h = size.height
            val glows = listOf(
                Triple(GoldPrimary, Offset(w * 0.20f, h * 0.16f), w * 0.85f),     // warm gold
                Triple(Color(0xFFB7791F), Offset(w * 0.88f, h * 0.55f), w * 0.75f),  // amber
                Triple(Color(0xFF64748B), Offset(w * 0.30f, h * 0.94f), w * 0.75f)   // cool steel for depth
            ).map { (color, center, radius) ->
                Triple(Brush.radialGradient(listOf(color.copy(alpha = strength), Color.Transparent), center, radius), center, radius)
            }
            onDrawBehind {
                drawRect(base)
                glows.forEach { (brush, center, radius) -> drawCircle(brush, radius, center) }
            }
        }
    )
}

/** Bottom space for screens with the floating tab bar: the bar + the phone's navigation bar. */
@Composable
fun bottomBarSpace(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

// ---- layout building blocks ----

/** The coloured header with rounded bottom corners. It also fills the area behind the status bar. */
@Composable
fun GradientHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(BrandStart.copy(alpha = 0.88f), BrandEnd.copy(alpha = 0.80f))))
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
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = glassFill())
    val border = BorderStroke(1.dp, glassBorder())
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
fun SkillTile(skillName: String, category: String, size: Dp = 56.dp) {
    val id = SkillIcons.idFor(skillName, category)
    IconTile(id, category.ifBlank { SkillIcons.categoryOf(id) }, size)
}

/**
 * A skill's icon on a small glass tile in its category colour: soft colour gradient, a thin
 * coloured edge and the line icon in a light tint of the same colour (our own icons, not emoji).
 */
@Composable
fun IconTile(iconId: String, category: String, size: Dp = 56.dp, color: Color = categoryColor(category)) {
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier = Modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.10f))))
            .border(1.dp, color.copy(alpha = 0.40f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(SkillIcons.named(iconId), contentDescription = null, tint = lerp(color, Color.White, 0.45f),
            modifier = Modifier.size(size * 0.52f))
    }
}

/** Just the skill's line icon, e.g. inside a list row or next to a name. */
@Composable
fun SkillIcon(skillName: String, category: String, size: Dp = 20.dp, tint: Color = Color.Unspecified) {
    val id = SkillIcons.idFor(skillName, category)
    val color = if (tint != Color.Unspecified) tint else categoryColor(category.ifBlank { SkillIcons.categoryOf(id) })
    Icon(SkillIcons.named(id), contentDescription = null, tint = color, modifier = Modifier.size(size))
}

@Composable
fun LevelBadge(level: String) {
    val color = levelColor(level)
    Row(
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.25f))
            .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(SkillIcons.forLevel(level), contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(LocalStrings.current.level(level), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun SmallPill(text: String, color: Color) {
    Box(
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        IconText(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** A tappable chip we draw ourselves: colour fades between selected and idle, and it shrinks when pressed. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedBg: Color = MaterialTheme.colorScheme.primary,
    selectedFg: Color = MaterialTheme.colorScheme.onPrimary,
    idleBg: Color = glassFill(),
    idleFg: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) selectedBg else idleBg, tween(200), label = "pillBg")
    val pop by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "pillPop")
    val fg by animateColorAsState(if (selected) selectedFg else idleFg, tween(180), label = "pillFg")
    Box(
        modifier = Modifier
            .pressScale(source)
            .graphicsLayer { scaleX = pop; scaleY = pop }
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
fun StarRow(rating: Int, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (i in 1..5) AppIcon(if (i <= rating) "star_fill" else "star", size = size, tint = Gold)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/** Friendly message when a list is empty; [icon] is an id from our icon set ("search", "cap"...). */
@Composable
fun EmptyState(icon: String, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconBubble(icon)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A big icon on a soft gold circle, for empty and error states. */
@Composable
fun IconBubble(icon: String, size: Dp = 76.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) { AppIcon(icon, size = size * 0.5f, tint = tint) }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(LocalStrings.current.goBack) } }
    )
}

// ---------- celebration + live messages ----------

private class ConfettiPiece(
    val x: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color, val w: Float
)

/** A short confetti burst (about 1.8 s) every time [trigger] grows. Drawn on a Canvas, no library. */
@Composable
fun ConfettiOverlay(trigger: Int) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val palette = listOf(GoldPrimary, GoldDeep, Gold, Green, Color(0xFFF5F3EE), Color(0xFF94A3B8))
    val pieces = remember(trigger) {
        List(80) {
            ConfettiPiece(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.3f,
                vx = (Random.nextFloat() - 0.5f) * 1.3f,
                vy = -(0.35f + Random.nextFloat() * 0.45f),
                spin = (Random.nextFloat() - 0.5f) * 4f,
                color = palette[it % palette.size],
                w = 10f + Random.nextFloat() * 10f
            )
        }
    }
    // PERFORMANCE: progress is read only inside the Canvas (draw step), so the burst redraws without
    // recomposing 60 times a second; "finished" flips once.
    val finished by remember(trigger) { derivedStateOf { progress.value >= 1f } }
    if (finished) return
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        pieces.forEach { p ->
            val x = (p.x + p.vx * t) * size.width
            val y = (0.3f + p.vy * t + 1.25f * t * t) * size.height        // thrown up, then gravity
            rotate(degrees = p.spin * t * 360f, pivot = Offset(x, y)) {
                drawRect(color = p.color, topLeft = Offset(x - p.w / 2, y - p.w / 4),
                    size = Size(p.w, p.w / 2), alpha = (1f - t).coerceIn(0f, 1f))
            }
        }
    }
}

/** The banner that slides down from the top for a live message. */
@Composable
fun NoticeCard(text: String) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth().shadow(10.dp, shape).clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BrandStart, BrandEnd))),
            contentAlignment = Alignment.Center
        ) { AppIcon("bell", size = 17.dp, tint = Color.White) }
        IconText(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}
