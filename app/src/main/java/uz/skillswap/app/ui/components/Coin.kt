package uz.skillswap.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.OnGold
import kotlin.math.roundToInt

/**
 * The flying coin. Every value from [coins] plays once (they queue up):
 *  +1 (someone checked in to my lesson): the coin pops up in the middle, spins in 3D and flies into the wallet (top right).
 *  -1 (I booked a lesson): the coin leaves the wallet, spins into the middle and fades away.
 *
 * Drawn above the blurred screen content, so it never makes the tab bar re-blur.
 * All animated values are read in offset { } / graphicsLayer { } -> no recomposition per frame.
 */
@Composable
fun CoinFlyOverlay(coins: Flow<Int>) {
    val haptic = LocalHapticFeedback.current
    var amount by remember { mutableIntStateOf(0) }      // 0 = nothing on screen
    val appear = remember { Animatable(0f) }              // 0..1 size
    val spin = remember { Animatable(0f) }                // degrees around the vertical axis
    val fly = remember { Animatable(0f) }                 // 0 = middle of the screen, 1 = wallet

    LaunchedEffect(coins) {
        coins.collect { a ->
            if (a == 0) return@collect
            appear.snapTo(0f)
            spin.snapTo(0f)
            fly.snapTo(if (a > 0) 0f else 1f)
            amount = a
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (a > 0) {
                coroutineScope {
                    launch { appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
                    spin.animateTo(720f, tween(1100, easing = FastOutSlowInEasing))
                }
                fly.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            } else {
                coroutineScope {
                    launch { appear.animateTo(1f, tween(260)) }
                    launch { spin.animateTo(540f, tween(1000, easing = FastOutSlowInEasing)) }
                    fly.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 160f))
                }
                delay(200)
                appear.animateTo(0f, tween(320))
            }
            amount = 0
        }
    }
    if (amount == 0) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val coinSize = 84.dp
        val coinPx = with(d) { coinSize.toPx() }
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val topInset = WindowInsets.statusBars.getTop(d).toFloat()
        val startX = w / 2f
        val startY = h * 0.42f
        val endX = w - with(d) { 58.dp.toPx() }
        val endY = topInset + with(d) { 42.dp.toPx() }
        val earned = amount > 0

        Box(
            Modifier
                .size(coinSize)
                .offset {
                    val t = fly.value
                    // x moves straight, y eases in -> the coin travels on a curve, not a line
                    val x = startX + (endX - startX) * t
                    val y = startY + (endY - startY) * (t * t)
                    IntOffset((x - coinPx / 2f).roundToInt(), (y - coinPx / 2f).roundToInt())
                }
                .graphicsLayer {
                    val s = appear.value * (1f - 0.7f * fly.value)
                    scaleX = s
                    scaleY = s
                    rotationY = spin.value
                    cameraDistance = 14f * density
                    alpha = if (earned && fly.value > 0.85f) ((1f - fly.value) / 0.15f).coerceIn(0f, 1f) else 1f
                }
        ) { Coin() }

        // "+1" / "-1" floats up under the coin while it spins
        Text(
            if (earned) "+1" else "−1",
            color = if (earned) GoldPrimary else Color.White,
            fontSize = 30.sp, fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset((startX - coinPx * 0.28f).roundToInt(), (startY + coinPx * 0.62f).roundToInt()) }
                .graphicsLayer {
                    val shown = appear.value.coerceIn(0f, 1f) * (1f - fly.value)
                    alpha = shown
                    translationY = -24f * shown
                }
        )
    }
}

/** A gold coin with a soft shine and an embossed ring. */
@Composable
private fun Coin() {
    Box(
        Modifier.fillMaxSize()
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFE9A8), GoldPrimary, GoldDeep)))
            .border(3.dp, Brush.linearGradient(listOf(Color(0xFFFFF4CC), Color(0xFF7A5718))), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(60.dp).border(1.5.dp, OnGold.copy(alpha = 0.28f), CircleShape))
        Text("S", color = OnGold.copy(alpha = 0.85f), fontSize = 34.sp, fontWeight = FontWeight.Black)
    }
}
