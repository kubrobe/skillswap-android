package uz.skillswap.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.theme.BrandEnd
import uz.skillswap.app.ui.theme.BrandStart
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.OnGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * "Moments": short animations that make progress FEEL like progress.
 * PERFORMANCE: every animated value is read inside graphicsLayer { } or a Canvas (draw step only),
 * so an animation frame redraws without recomposing the screen.
 */

/**
 * Only one full-screen celebration at a time. Finishing your first lesson unlocks a level AND a badge
 * at the same moment: they now play one after the other instead of on top of each other.
 */
val CelebrationLock = Mutex()

/** "Level up!" — a full-screen moment when finished lessons move you to Bronze, Silver or Gold. */
@Composable
fun LevelUpOverlay(events: Flow<String>, onShown: () -> Unit) {
    val tr = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    var current by remember { mutableStateOf<String?>(null) }
    var skip by remember { mutableStateOf(false) }
    val pop = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }

    LaunchedEffect(events) {
        events.collect { level ->
            CelebrationLock.lock()
            try {
                current = level
                skip = false
                onShown()                                     // confetti
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                pop.snapTo(0f); spin.snapTo(0f); rise.snapTo(0f)
                coroutineScope {
                    val rays = launch { spin.animateTo(360f, tween(9000, easing = LinearEasing)) }
                    launch { delay(250); rise.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 200f)) }
                    pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 200f))
                    var waited = 0
                    while (waited < 3400 && !skip) {
                        delay(100)
                        waited += 100
                    }
                    rays.cancel()
                }
                pop.animateTo(0f, tween(220))
                current = null
            } finally {
                CelebrationLock.unlock()
            }
        }
    }

    val level = current ?: return
    val medal = levelColor(level)
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skip = true },
        contentAlignment = Alignment.Center
    ) {
        // slowly turning rays in the medal's colour
        Canvas(Modifier.size(360.dp).graphicsLayer { rotationZ = spin.value }) {
            val c = center
            val r = size.minDimension / 2f
            val ray = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x - r * 0.08f, c.y - r)
                lineTo(c.x + r * 0.08f, c.y - r)
                close()
            }
            for (k in 0 until 16) rotate(k * 22.5f, c) { drawPath(ray, medal.copy(alpha = 0.20f)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(156.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1f - pop.value) * -25f }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), medal, medal.copy(alpha = 0.75f))))
                    .border(5.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(SkillIcons.forLevel(level), contentDescription = null, tint = OnGold, modifier = Modifier.size(78.dp))
            }
            Column(
                Modifier.graphicsLayer {
                    alpha = rise.value.coerceIn(0f, 1f)
                    translationY = (1f - rise.value) * 30.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(tr.levelUp.uppercase(), color = GoldPrimary, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text(tr.youAreNow(tr.level(level)), color = Color.White, style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center)
                Text(tr.levelUpText, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp))
            }
        }
    }
}

/** A green circle pops in, then the check mark draws itself from left to right. */
@Composable
fun AnimatedCheck(diameter: Dp = 76.dp, color: Color = Green) {
    val circle = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) { circle.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
    LaunchedEffect(Unit) {
        delay(180)
        tick.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    val measure = remember { PathMeasure() }
    val full = remember { Path() }
    val part = remember { Path() }
    Canvas(Modifier.size(diameter).graphicsLayer { scaleX = circle.value; scaleY = circle.value }) {
        val w = size.width
        drawCircle(color)
        full.reset()
        full.moveTo(w * 0.28f, w * 0.53f)
        full.lineTo(w * 0.44f, w * 0.68f)
        full.lineTo(w * 0.73f, w * 0.37f)
        measure.setPath(full, false)
        part.reset()
        measure.getSegment(0f, measure.length * tick.value, part, true)
        drawPath(part, Color.White, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * After a booking: the check draws itself, a ticket for the lesson slides in, and "See my lessons"
 * sends the ticket flying down toward the Lessons tab before the screen changes.
 */
@Composable
fun BookedCelebration(skill: String, details: String, onDone: () -> Unit) {
    val tr = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ticket = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(420)
        ticket.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 240f))
    }
    val finish: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                leave.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                onDone()
            }
        }
    }
    Dialog(onDismissRequest = { finish() }) {
        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    // fly down toward the tab bar, shrinking like it drops into the Lessons tab
                    val l = leave.value
                    alpha = 1f - l
                    translationY = l * 320.dp.toPx()
                    scaleX = 1f - 0.45f * l
                    scaleY = 1f - 0.45f * l
                }
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedCheck()
            Text(tr.lessonBooked, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Row(
                Modifier.fillMaxWidth()
                    .graphicsLayer {
                        val t = ticket.value
                        alpha = t.coerceIn(0f, 1f)
                        translationY = (1f - t) * 48.dp.toPx()
                        rotationZ = (1f - t) * -5f
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(BrandStart, BrandEnd)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon("calendar", size = 28.dp, tint = GoldPrimary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(skill, style = MaterialTheme.typography.titleMedium, color = Color.White,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (details.isNotBlank()) {
                        Text(details, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
            Button(onClick = { finish() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(tr.seeMyLessons)
            }
        }
    }
}

/** After rating a lesson: the stars pop in one by one, a ring of gold sparks bursts out, then "Thank you!". */
@Composable
fun RatingThanks(stars: Int) {
    val tr = LocalStrings.current
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(90L * stars.coerceIn(1, 5) + 180L)
        burst.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width = 280.dp, height = 84.dp)) {
                val p = burst.value
                if (p <= 0f || p >= 1f) return@Canvas
                val c = center
                for (k in 0 until 14) {
                    val a = k / 14f * 2f * PI.toFloat()
                    val dx = cos(a) * size.width * 0.48f * (0.35f + 0.65f * p)
                    val dy = sin(a) * size.height * 0.62f * (0.35f + 0.65f * p)
                    drawCircle(if (k % 2 == 0) GoldPrimary else Gold, radius = 2.dp.toPx() + 3.dp.toPx() * (1f - p),
                        center = Offset(c.x + dx, c.y + dy), alpha = 1f - p)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 1..5) PopStar(filled = i <= stars, delayMs = 90L * i)
            }
        }
        Text(tr.thanksTitle, style = MaterialTheme.typography.titleLarge)
        Text(tr.thanksText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PopStar(filled: Boolean, delayMs: Long) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
    }
    AppIcon(if (filled) "star_fill" else "star", size = 34.dp, tint = Gold,
        modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
}
