package uz.skillswap.app.ui.screens

import uz.skillswap.app.ui.components.SkillIcons
import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.Gold
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.LevelBadge
import uz.skillswap.app.ui.components.Red
import uz.skillswap.app.ui.components.SmallPill
import uz.skillswap.app.ui.components.categoryColor
import uz.skillswap.app.ui.components.glassBorder
import uz.skillswap.app.ui.components.pressScale
import uz.skillswap.app.ui.theme.BrandEnd
import uz.skillswap.app.ui.theme.BrandStart
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.OnGold
import uz.skillswap.app.util.DeckCard
import uz.skillswap.app.util.buildDeck
import kotlin.math.abs

/**
 * "Swipe to swap": one lesson card at a time, like a dating app.
 * Right = I want to learn this (it becomes a wish), left = skip.
 * A two-way match (they also want what I teach) shows the "It's a swap!" screen with confetti.
 */
@Composable
fun SwipeScreen(vm: SkillSwapViewModel, onClose: () -> Unit, onOpen: (Long) -> Unit) {
    val offers by vm.allOffers.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val user by vm.currentUser.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tr = LocalStrings.current

    // The deck is built once when the screen opens, so a background refresh never reshuffles it.
    var deck by remember { mutableStateOf<List<DeckCard>?>(null) }
    LaunchedEffect(offers, matches) {
        if (deck == null && offers.isNotEmpty()) deck = buildDeck(offers, matches)
    }
    var position by remember { mutableIntStateOf(0) }
    val liked = remember { mutableStateListOf<DeckCard>() }
    var swap by remember { mutableStateOf<DeckCard?>(null) }

    // Leaving the screen saves the right-swipes as wishes (one server call, not one per swipe).
    val close = {
        vm.saveLiked(liked.map { it.offer.skillId })
        onClose()
    }
    BackHandler {
        val showingSwap = swap != null
        if (showingSwap) swap = null
        if (!showingSwap) close()
    }
    // a user with no lessons around: stop "loading" after a moment and say so
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_500)
        if (deck == null) deck = buildDeck(offers, matches)
    }

    val cards = deck ?: emptyList()
    val top = cards.getOrNull(position)
    val next = cards.getOrNull(position + 1)

    fun decide(card: DeckCard, right: Boolean) {
        if (right) {
            liked.add(card)
            if (card.theyWant != null) {
                swap = card
                vm.celebrate()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        position += 1
    }

    // a solid sheet: it slides up over Browse
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = tr.close) }
            Column(Modifier.weight(1f)) {
                Text(tr.swipeTitle, style = MaterialTheme.typography.titleLarge)
                Text(tr.swipeArrows, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (cards.isNotEmpty()) {
                Text("${(position + 1).coerceAtMost(cards.size)}/${cards.size}", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            when {
                deck == null -> EmptyDeck("⏳", tr.loadingLessons, null)
                cards.isEmpty() -> EmptyDeck("🙌", tr.noLessonsToSwipe, tr.allFull)
                top == null -> DeckDone(liked, onOpen = { id -> vm.saveLiked(liked.map { it.offer.skillId }); onOpen(id) }, onDone = close)
                else -> {
                    // remembered per card: a new card always starts in the middle
                    val dragX = remember(top.offer.offerId) { Animatable(0f) }
                    val dragY = remember(top.offer.offerId) { Animatable(0f) }

                    fun fling(right: Boolean) {
                        if (abs(dragX.targetValue) > widthPx) return      // already flying away (double tap)
                        scope.launch {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            launch { dragY.animateTo(dragY.value + 60f, tween(240)) }
                            dragX.animateTo(if (right) widthPx * 1.6f else -widthPx * 1.6f, tween(240, easing = FastOutLinearInEasing))
                            decide(top, right)
                        }
                    }

                    // the card underneath grows as the top card leaves
                    if (next != null) {
                        LessonCard(next, Modifier.fillMaxSize().graphicsLayer {
                            val p = (abs(dragX.value) / (widthPx * 0.5f)).coerceIn(0f, 1f)
                            scaleX = 0.92f + 0.08f * p
                            scaleY = 0.92f + 0.08f * p
                            translationY = (1f - p) * 26.dp.toPx()
                        })
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                translationX = dragX.value
                                translationY = dragY.value
                                rotationZ = dragX.value / widthPx * 16f
                            }
                            .pointerInput(top.offer.offerId) {
                                detectDragGestures(
                                    onDragEnd = {
                                        if (abs(dragX.value) > widthPx * 0.28f) fling(dragX.value > 0f)
                                        else scope.launch {
                                            launch { dragY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f)) }
                                            dragX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f))
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { dragX.animateTo(0f); dragY.animateTo(0f) }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        scope.launch {
                                            dragX.snapTo(dragX.value + amount.x)
                                            dragY.snapTo(dragY.value + amount.y)
                                        }
                                    }
                                )
                            }
                    ) {
                        LessonCard(top, Modifier.fillMaxSize(), onClick = { onOpen(top.offer.offerId) })
                        // stamps that fade in while dragging
                        Stamp(tr.stampLearn, Green, Modifier.align(Alignment.TopStart).padding(22.dp)
                            .graphicsLayer {
                                alpha = (dragX.value / (widthPx * 0.25f)).coerceIn(0f, 1f)
                                rotationZ = -14f
                            })
                        Stamp(tr.stampSkip, Red, Modifier.align(Alignment.TopEnd).padding(22.dp)
                            .graphicsLayer {
                                alpha = (-dragX.value / (widthPx * 0.25f)).coerceIn(0f, 1f)
                                rotationZ = 14f
                            })
                    }

                    // the same as swiping, for people who prefer to tap
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        RoundAction("✕", Red, tr.skip) { fling(false) }
                        RoundAction("♥", GoldPrimary, tr.iWantToLearn) { fling(true) }
                    }
                }
            }
        }
    }

    swap?.let { card ->
        SwapOverlay(
            card = card,
            myName = user?.fullName ?: "You",
            onBook = {
                swap = null
                vm.saveLiked(liked.map { it.offer.skillId })
                onOpen(card.offer.offerId)
            },
            onKeep = { swap = null }
        )
    }
    }   // end of the sheet
}

/** One lesson as a big card. Solid (not glass), because cards lie on top of each other. */
@Composable
private fun LessonCard(card: DeckCard, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val tr = LocalStrings.current
    val o = card.offer
    val shape = RoundedCornerShape(30.dp)
    val tap = if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier
    Column(
        modifier.padding(bottom = 76.dp)          // room for the round buttons
            .shadow(14.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, glassBorder()), shape)
            .then(tap)
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(Brush.linearGradient(listOf(BrandStart, BrandEnd, categoryColor(o.category).copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(SkillIcons.forSkill(o.skillName, o.category), contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(124.dp))
            Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill(o.category, Color.White)
                if (o.trusted) SmallPill(tr.trusted, Green)
            }
            if (card.theyWant != null) {
                Text(
                    tr.swapBadge(card.theyWant),
                    color = OnGold, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp)
                        .clip(RoundedCornerShape(50)).background(GoldPrimary).padding(horizontal = 14.dp, vertical = 6.dp)
                )
            } else if (card.isMatch) {
                Text(
                    "✨ " + tr.teachesYourWish, color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp)
                        .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(o.skillName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr.byTeacher(o.teacherName), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                Text(ratingText(o, tr.new), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = if (o.reviewCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else Gold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(o.level)
                SmallPill(tr.freeN(o.freeSlots), Green)
                SmallPill(tr.perHour, GoldDeep)
            }
            if (o.description.isNotBlank()) {
                Text(o.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, modifier: Modifier) {
    Text(
        text, color = color, fontSize = 30.sp, fontWeight = FontWeight.Black,
        modifier = modifier.border(3.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun RoundAction(symbol: String, color: Color, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(64.dp).pressScale(source)
            .shadow(10.dp, CircleShape).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, color.copy(alpha = 0.7f), CircleShape)
            .clickable(interactionSource = source, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun EmptyDeck(emoji: String, title: String, text: String?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(emoji, fontSize = 56.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** End of the deck: what I liked, ready to open. */
@Composable
private fun DeckDone(liked: List<DeckCard>, onOpen: (Long) -> Unit, onDone: () -> Unit) {
    val tr = LocalStrings.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉", fontSize = 56.sp)
        Text(tr.thatsEveryone, style = MaterialTheme.typography.titleLarge)
        Text(
            if (liked.isEmpty()) tr.nothingCaught else tr.likedSaved(liked.size),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(liked, key = { it.offer.offerId }) { c -> OfferRow(c.offer) { onOpen(c.offer.offerId) } }
        }
        Button(onClick = onDone, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(tr.done)
        }
    }
}

/** "It's a swap!" - both of us want what the other one teaches. */
@Composable
private fun SwapOverlay(card: DeckCard, myName: String, onBook: () -> Unit, onKeep: () -> Unit) {
    val tr = LocalStrings.current
    val enter = remember(card.offer.offerId) { Animatable(0f) }
    LaunchedEffect(card.offer.offerId) { enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 200f)) }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF0111317), Color(0xF02B2E36), Color(0xF06E5420))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(28.dp).graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                tr.itsASwap, color = GoldPrimary, fontSize = 42.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.graphicsLayer {
                    val s = 0.6f + 0.4f * enter.value
                    scaleX = s
                    scaleY = s
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(initials(myName), null, Modifier.graphicsLayer { translationX = -(1f - enter.value) * 320f })
                Text("🔁", fontSize = 32.sp, modifier = Modifier.padding(horizontal = 10.dp)
                    .graphicsLayer { rotationZ = enter.value * 360f })
                Avatar(initials(card.offer.teacherName), SkillIcons.idFor(card.offer.skillName, card.offer.category),
                    Modifier.graphicsLayer { translationX = (1f - enter.value) * 320f })
            }
            Text(
                tr.swapText(card.offer.teacherName.substringBefore(' '), card.offer.skillName, card.theyWant ?: ""),
                color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center
            )
            Button(
                onClick = onBook, shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = OnGold),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(tr.bookLesson, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onKeep) { Text(tr.keepSwiping, color = Color.White) }
        }
    }
}

@Composable
private fun Avatar(initials: String, badgeIcon: String?, modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(GoldPrimary, GoldDeep)))
                .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(initials, color = OnGold, fontSize = 32.sp, fontWeight = FontWeight.Black) }
        if (badgeIcon != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape)
                    .background(Color.White).border(2.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(SkillIcons.named(badgeIcon), contentDescription = null, tint = GoldDeep, modifier = Modifier.size(20.dp)) }
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
