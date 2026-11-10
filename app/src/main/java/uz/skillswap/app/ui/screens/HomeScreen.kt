package uz.skillswap.app.ui.screens

import uz.skillswap.app.ui.theme.Lavender
import uz.skillswap.app.ui.components.AppIcon
import uz.skillswap.app.ui.components.IconText
import uz.skillswap.app.ui.components.SkillIcon
import uz.skillswap.app.ui.components.SkillTile
import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import uz.skillswap.app.data.Skill
import uz.skillswap.app.data.remote.ActivityLine
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import uz.skillswap.app.ui.components.pressScale
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.OnGold
import uz.skillswap.app.util.syncAgeText
import uz.skillswap.app.data.remote.Match
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.glassBorder
import uz.skillswap.app.ui.components.glassFill
import uz.skillswap.app.ui.theme.BrandEnd
import uz.skillswap.app.ui.theme.BrandStart
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import uz.skillswap.app.ui.components.bottomBarSpace
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.data.OfferCard
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.EmptyState
import uz.skillswap.app.ui.components.Gold
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.LevelBadge
import uz.skillswap.app.ui.components.Pill
import uz.skillswap.app.ui.components.Red
import uz.skillswap.app.ui.components.SmallPill
import uz.skillswap.app.ui.components.StaggerIn

@Composable
fun HomeScreen(
    vm: SkillSwapViewModel, onOfferClick: (Long) -> Unit, onSwipe: () -> Unit, onImpact: () -> Unit,
    onGalaxy: () -> Unit, onAsk: () -> Unit
) {
    val offers by vm.offers.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selected by vm.category.collectAsStateWithLifecycle()
    val user by vm.currentUser.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val wants by vm.wants.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val tr = LocalStrings.current
    var editingWants by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // While the user is searching, the extras (ticker, swipe banner, matches) fold away so the results
    // slide up right under the search field instead of sitting far below it.
    val searching = query.isNotBlank()

    // The list shrinks to the area above the keyboard, so results are never hidden behind it.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember { derivedStateOf { ime.getBottom(density) > 0 } }

    // Dragging the list closes the keyboard (only a finger drag, not scrolls the app does itself).
    val closeKeyboardOnDrag = remember(focusManager, ime, density) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // only do any work while the keyboard is actually open (not on every normal scroll)
                if (source == NestedScrollSource.UserInput && available.y != 0f && ime.getBottom(density) > 0) {
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().imePadding().nestedScroll(closeKeyboardOnDrag),
        state = listState,
        contentPadding = PaddingValues(bottom = if (imeVisible) 16.dp else bottomBarSpace())
    ) {
        item {
            // iOS-style parallax: the header moves up slower than the list and fades out
            GradientHeader(Modifier.graphicsLayer {
                val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 900f
                translationY = off * 0.45f
                alpha = 1f - (off / 700f).coerceIn(0f, 0.9f)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconText(tr.hi(user?.fullName?.substringBefore(' ') ?: tr.guest),
                        style = MaterialTheme.typography.headlineSmall, color = Color.White,
                        modifier = Modifier.weight(1f))
                    GalaxyButton(tr.galaxyTitle, onGalaxy)
                    Spacer(Modifier.width(8.dp))
                    wallet?.let { w -> CreditsChip(w.balance) }
                }
                AnimatedVisibility(
                    visible = !searching,
                    enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                    exit = shrinkVertically(tween(260)) + fadeOut(tween(160))
                ) {
                    Text(tr.whatToLearn, style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text(tr.searchHint, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        // empty search: a mic for the AI matchmaker; while typing: clear
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.query.value = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = tr.clearSearch)
                            }
                        } else {
                            IconButton(onClick = onAsk) { AppIcon("mic", size = 22.dp, tint = Color.White) }
                        }
                    },
                    singleLine = true,
                    // the keyboard's action key becomes a search key that closes the keyboard
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(18.dp),
                    // glass search field: white frost on the purple header
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.20f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.75f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.75f),
                        focusedLeadingIconColor = Color.White,
                        unfocusedLeadingIconColor = Color.White,
                        focusedTrailingIconColor = Color.White,
                        unfocusedTrailingIconColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (activity.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = !searching,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(180))
                ) { LiveTicker(activity, onImpact) }
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                item { Pill(tr.all, selected == null, { vm.category.value = null }) }
                items(categories) { c -> Pill(c, selected == c, { vm.category.value = c }) }
            }
        }
        item {
            AnimatedVisibility(
                visible = !searching,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(180))
            ) { SwipeBanner(onSwipe) }
        }
        item {
            AnimatedVisibility(
                visible = !searching,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(180))
            ) {
                MatchesSection(
                    matches = matches, hasWants = wants.isNotEmpty(),
                    onOpen = onOfferClick, onEditWants = { editingWants = true }
                )
            }
        }
        item {
            Text(
                tr.skillsAvailable(offers.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        if (offers.isEmpty()) {
            item {
                EmptyState("search", tr.nothingFound, tr.nothingFoundHint)
            }
        }
        itemsIndexed(offers, key = { _, o -> o.offerId }) { index, o ->
            // animateItem (rows glide when others appear/disappear) only while searching, so normal scrolling stays light
            Column((if (searching) Modifier.animateItem() else Modifier).padding(horizontal = 16.dp, vertical = 5.dp)) {
                StaggerIn(index) { OfferRow(o, highlight = query) { onOfferClick(o.offerId) } }
            }
        }
    }

    if (editingWants) {
        WantsDialog(
            skills = skills, selected = wants.toSet(),
            onDismiss = { editingWants = false },
            onSave = { ids -> vm.setWants(ids) { editingWants = false } }
        )
    }
}

/** [highlight] is the current search text: the matching part of the skill name is shown in gold. */
@Composable
fun OfferRow(offer: OfferCard, highlight: String = "", onClick: () -> Unit) {
    val tr = LocalStrings.current
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SkillTile(offer.skillName, offer.category)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(highlightMatch(offer.skillName, highlight, Gold), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildAnnotatedString {
                        append(tr.byTeacher(offer.teacherName))
                        if (offer.trusted) withStyle(SpanStyle(color = Green, fontWeight = FontWeight.SemiBold)) { append("  " + tr.trusted) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                LevelBadge(offer.level)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (offer.freeSlots > 0) SmallPill(tr.freeN(offer.freeSlots), Green) else SmallPill(tr.full, Red)
                Text(ratingText(offer, tr.new), style = MaterialTheme.typography.labelMedium,
                    color = if (offer.reviewCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else Gold,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** [text] with the first occurrence of [query] (ignoring upper/lower case) in [color] and bold. */
private fun highlightMatch(text: String, query: String, color: Color) = buildAnnotatedString {
    val q = query.trim()
    val at = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (at < 0) {
        append(text)
    } else {
        append(text.substring(0, at))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text.substring(at, at + q.length)) }
        append(text.substring(at + q.length))
    }
}

fun ratingText(offer: OfferCard, newLabel: String = "New"): String =
    if (offer.reviewCount == 0) newLabel else "★ ${offer.rating} (${offer.reviewCount})"

/** Round glass button in the header that opens the Skill Galaxy. */
@Composable
private fun GalaxyButton(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(36.dp).pressScale(source).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(interactionSource = source, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { AppIcon("orbit", size = 21.dp, tint = Color.White) }
}

/** Credits balance in the header. */
@Composable
private fun CreditsChip(balance: Int) {
    // the chip bounces whenever the balance changes, and the number counts to the new value
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(balance) {
        bounce.animateTo(1.18f, spring(dampingRatio = 0.4f, stiffness = 700f))
        bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    val shown by animateIntAsState(balance, tween(600), label = "credits")
    Row(
        Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon("coin", size = 17.dp, tint = Lavender)
        Text(" $shown", style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

/** "Perfect matches": people who teach what I want - two-way swaps first. */
@Composable
private fun MatchesSection(
    matches: List<Match>, hasWants: Boolean, onOpen: (Long) -> Unit, onEditWants: () -> Unit
) {
    val tr = LocalStrings.current
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconText(tr.perfectMatches, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                iconColor = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onEditWants) { Text(if (hasWants) tr.editWishes else tr.addWishes) }
        }
        when {
            !hasWants -> AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onEditWants) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr.wishesTitle, style = MaterialTheme.typography.titleSmall)
                    Text(tr.wishesText,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            matches.isEmpty() -> Text(tr.noMatches,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(matches, key = { it.offerId }) { m -> MatchCard(m) { onOpen(m.offerId) } }
            }
        }
    }
}

@Composable
private fun MatchCard(m: Match, onClick: () -> Unit) {
    val tr = LocalStrings.current
    val twoWay = m.theyWant != null
    val shape = RoundedCornerShape(22.dp)
    val textColor = if (twoWay) Color.White else MaterialTheme.colorScheme.onSurface
    val base = Modifier.width(250.dp).clip(shape)
    val filled = if (twoWay) base.background(Brush.linearGradient(listOf(BrandStart, BrandEnd)))
                 else base.background(glassFill()).border(BorderStroke(1.dp, glassBorder()), shape)
    Column(filled.clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkillTile(m.skillName, "", size = 42.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(m.skillName, style = MaterialTheme.typography.titleSmall, color = textColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr.byTeacher(m.teacherName), style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconText(
            if (twoWay) tr.perfectSwap(m.theyWant ?: "") else tr.teachesYourWish,
            style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 2
        )
    }
}

/** Choose up to 10 skills you want to learn. */
@Composable
private fun WantsDialog(skills: List<Skill>, selected: Set<Long>, onDismiss: () -> Unit, onSave: (List<Long>) -> Unit) {
    var chosen by remember { mutableStateOf(selected) }
    val tr = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr.iWantToLearn) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                skills.forEach { sk ->
                    val on = sk.id in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { chosen = if (on) chosen - sk.id else if (chosen.size < 10) chosen + sk.id else chosen }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        SkillIcon(sk.name, sk.category, size = 18.dp)
                        Text(sk.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen.toList()) }) { Text(tr.findMatches) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr.close) } }
    )
}

/**
 * "LIVE" strip under the header: what is happening on SkillSwap right now, one line every 3.5 s.
 * The green dot pings once per line (no endless animation: the blurred tab bar would have to re-blur every frame).
 */
@Composable
private fun LiveTicker(lines: List<ActivityLine>, onClick: () -> Unit) {
    val tr = LocalStrings.current
    var index by remember { mutableIntStateOf(0) }
    val ping = remember { Animatable(0f) }
    LaunchedEffect(lines) {
        index = 0                                   // a new event arrived: show it first
        while (true) {
            ping.snapTo(1f)
            ping.animateTo(0f, tween(900))
            delay(2_600)
            if (lines.size > 1) index = (index + 1) % lines.size
        }
    }
    val line = lines[index.coerceIn(0, lines.lastIndex)]
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(glassFill())
            .border(BorderStroke(1.dp, glassBorder()), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).graphicsLayer {
                val grow = 1f + (1f - ping.value) * 1.6f
                scaleX = grow
                scaleY = grow
                alpha = ping.value * 0.6f
            }.clip(CircleShape).background(Green))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Green))
        }
        Text(tr.live, style = MaterialTheme.typography.labelSmall, color = Green, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 7.dp, end = 10.dp))
        AnimatedContent(
            targetState = line,
            transitionSpec = {
                (slideInVertically(tween(320)) { it } + fadeIn(tween(320))) togetherWith
                    (slideOutVertically(tween(260)) { -it } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "ticker"
        ) { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconText(l.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f), iconColor = MaterialTheme.colorScheme.primary)
                Text(tr.ago(System.currentTimeMillis(), l.at), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(" ›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Entry to "Swipe to swap". */
@Composable
private fun SwipeBanner(onClick: () -> Unit) {
    val tr = LocalStrings.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .pressScale(source)
            .clip(shape)
            .background(Brush.linearGradient(listOf(GoldPrimary, GoldDeep)))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon("cards", size = 30.dp, tint = OnGold)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(tr.swipeTitle, style = MaterialTheme.typography.titleMedium, color = OnGold, fontWeight = FontWeight.Bold)
            Text(tr.swipeHint, style = MaterialTheme.typography.bodySmall,
                color = OnGold.copy(alpha = 0.75f))
        }
        Text("→", fontSize = 24.sp, color = OnGold, fontWeight = FontWeight.Bold)
    }
}

