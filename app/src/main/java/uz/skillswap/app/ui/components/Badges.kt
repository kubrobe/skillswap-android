package uz.skillswap.app.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.Lavender

/** The picture of each badge (the names are in Strings, per language). */
fun badgeEmoji(id: String): String = when (id) {
    "first_lesson" -> "🎓"
    "first_teach" -> "🧑‍🏫"
    "mentor" -> "🏆"
    "five_star" -> "⭐"
    "trusted" -> "🛡️"
    "explorer" -> "🧭"
    "perfect_swap" -> "🔁"
    "critic" -> "✍️"
    else -> "🏅"
}

/**
 * A badge medal. Unlocked: gold ring and full colour. Locked: grey, faded, with a gold arc that shows
 * how far you are (e.g. 2 of 5 lessons taught).
 */
@Composable
fun BadgeCircle(id: String, progress: Int, goal: Int, size: Dp = 64.dp) {
    val unlocked = progress >= goal
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (unlocked) {
            Box(
                Modifier.size(size).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Lavender, GoldPrimary, GoldDeep)))
                    .padding(3.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) { Text(badgeEmoji(id), fontSize = (size.value * 0.42f).sp) }
        } else {
            val track = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.size(size)) {
                val w = 3.dp.toPx()
                drawCircle(track, radius = (this.size.minDimension - w) / 2f, style = Stroke(w))
                if (progress > 0) {
                    drawArc(GoldPrimary, startAngle = -90f, sweepAngle = 360f * progress / goal, useCenter = false,
                        topLeft = Offset(w / 2f, w / 2f), size = Size(this.size.width - w, this.size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Text(badgeEmoji(id), fontSize = (size.value * 0.38f).sp, modifier = Modifier.graphicsLayer { alpha = 0.35f })
        }
    }
}

/**
 * "Badge unlocked!": the medal pops in on a spinning crown of gold rays, with confetti (via [onShown])
 * and a buzz. Badges unlocked together play one after another. Tap to skip.
 */
@Composable
fun BadgeUnlockedOverlay(events: Flow<String>, onShown: () -> Unit) {
    val tr = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    var current by remember { mutableStateOf<String?>(null) }
    var skip by remember { mutableStateOf(false) }
    val pop = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }

    LaunchedEffect(events) {
        events.collect { id ->
            current = id
            skip = false
            onShown()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            pop.snapTo(0f)
            spin.snapTo(0f)
            coroutineScope {
                val rays = launch { spin.animateTo(360f, tween(9000, easing = LinearEasing)) }
                pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
                var waited = 0
                while (waited < 3200 && !skip) {
                    delay(100)
                    waited += 100
                }
                rays.cancel()
            }
            pop.animateTo(0f, tween(220))
            current = null
        }
    }

    val id = current ?: return
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skip = true },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(340.dp).graphicsLayer { rotationZ = spin.value }) {
            val c = center
            val r = size.minDimension / 2f
            val ray = Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x - r * 0.09f, c.y - r)
                lineTo(c.x + r * 0.09f, c.y - r)
                close()
            }
            for (k in 0 until 12) rotate(k * 30f, c) { drawPath(ray, GoldPrimary.copy(alpha = 0.22f)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(150.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Lavender, GoldPrimary, GoldDeep)))
                    .border(4.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(badgeEmoji(id), fontSize = 68.sp) }
            Text(tr.badgeUnlocked.uppercase(), color = GoldPrimary, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(tr.badgeTitle(id), color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(tr.badgeDesc(id), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
