package uz.skillswap.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import uz.skillswap.app.data.remote.Match
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.theme.BrandEnd
import uz.skillswap.app.ui.theme.BrandStart
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import kotlinx.coroutines.delay
import uz.skillswap.app.data.OfferCard
import uz.skillswap.app.data.SyncState
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.EmojiTile
import uz.skillswap.app.ui.components.EmptyState
import uz.skillswap.app.ui.components.Gold
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.LevelBadge
import uz.skillswap.app.ui.components.Pill
import uz.skillswap.app.ui.components.Red
import uz.skillswap.app.ui.components.SmallPill
import uz.skillswap.app.ui.components.StaggerIn
import uz.skillswap.app.util.syncAgeText

@Composable
fun HomeScreen(vm: SkillSwapViewModel, onOfferClick: (Long) -> Unit) {
    val offers by vm.offers.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selected by vm.category.collectAsStateWithLifecycle()
    val user by vm.currentUser.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val wants by vm.wants.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    var editingWants by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hi, ${user?.fullName?.substringBefore(' ') ?: "guest"} 👋",
                        style = MaterialTheme.typography.headlineSmall, color = Color.White,
                        modifier = Modifier.weight(1f))
                    wallet?.let { w -> CreditsChip(w.balance) }
                }
                Text("What do you want to learn today?", style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(8.dp))
                SyncStatus(sync, onRefresh = { vm.refresh() })
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search skills or teachers", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { vm.query.value = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                item { Pill("All", selected == null, { vm.category.value = null }) }
                items(categories) { c -> Pill(c, selected == c, { vm.category.value = c }) }
            }
        }
        if (sync.online) {
            item {
                MatchesSection(
                    matches = matches, hasWants = wants.isNotEmpty(),
                    onOpen = onOfferClick, onEditWants = { editingWants = true }
                )
            }
        }
        item {
            Text(
                "${offers.size} skills available",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        if (offers.isEmpty()) {
            item {
                EmptyState("🔍", "Nothing found", "Try another word or choose a different category.")
            }
        }
        itemsIndexed(offers, key = { _, o -> o.offerId }) { index, o ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                StaggerIn(index) { OfferRow(o) { onOfferClick(o.offerId) } }
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

@Composable
fun OfferRow(offer: OfferCard, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(offer.emoji, offer.category)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(offer.skillName, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("by ${offer.teacherName}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LevelBadge(offer.level)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (offer.freeSlots > 0) SmallPill("${offer.freeSlots} free", Green) else SmallPill("Full", Red)
                Text(ratingText(offer), style = MaterialTheme.typography.labelMedium,
                    color = if (offer.reviewCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else Gold,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Small pill: is this data live from the server, saved data (offline), or a phone-only demo? Tap = refresh. */
@Composable
private fun SyncStatus(state: SyncState, onRefresh: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.lastSync) {
        while (true) {                       // keep "updated 3 min ago" fresh
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val (dot, text) = when {
        !state.online -> Color(0xFFFFD166) to "On this phone only (offline demo)"
        state.syncing -> Color.White to "Syncing with the server…"
        state.problem != null -> Color(0xFFFFB4A9) to "Offline · showing saved data"
        else -> Color(0xFF7CF5B5) to "Live · updated ${syncAgeText(now, state.lastSync)}"
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(enabled = state.online && !state.syncing, onClick = onRefresh)
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp))
        if (state.online) {
            if (state.syncing) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White,
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

fun ratingText(offer: OfferCard): String =
    if (offer.reviewCount == 0) "New" else "★ ${offer.rating} (${offer.reviewCount})"

/** Credits balance in the header. */
@Composable
private fun CreditsChip(balance: Int) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🪙", fontSize = 16.sp)
        Text(" $balance", style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

/** "Perfect matches": people who teach what I want - two-way swaps first. */
@Composable
private fun MatchesSection(
    matches: List<Match>, hasWants: Boolean, onOpen: (Long) -> Unit, onEditWants: () -> Unit
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✨ Perfect matches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onEditWants) { Text(if (hasWants) "Edit wishes" else "Add wishes") }
        }
        when {
            !hasWants -> AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onEditWants) {
                Column(Modifier.padding(16.dp)) {
                    Text("What do you want to learn?", style = MaterialTheme.typography.titleSmall)
                    Text("Pick a few skills. We find people who teach them — and who want what you teach.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            matches.isEmpty() -> Text("Nobody teaches your wishes yet. Try other skills!",
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
    val twoWay = m.theyWant != null
    val shape = RoundedCornerShape(22.dp)
    val textColor = if (twoWay) Color.White else MaterialTheme.colorScheme.onSurface
    val base = Modifier.width(250.dp).clip(shape)
    val filled = if (twoWay) base.background(Brush.linearGradient(listOf(BrandStart, BrandEnd)))
                 else base.background(MaterialTheme.colorScheme.surfaceVariant)
    Column(filled.clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.emoji, fontSize = 28.sp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(m.skillName, style = MaterialTheme.typography.titleSmall, color = textColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("by ${m.teacherName}", style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            if (twoWay) "🔁 Perfect swap! They want your ${m.theyWant}" else "Teaches what you want to learn",
            style = MaterialTheme.typography.labelMedium, color = textColor, maxLines = 2
        )
    }
}

/** Choose up to 10 skills you want to learn. */
@Composable
private fun WantsDialog(skills: List<Skill>, selected: Set<Long>, onDismiss: () -> Unit, onSave: (List<Long>) -> Unit) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("I want to learn…") },
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
                        Text("${sk.emoji} ${sk.name}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen.toList()) }) { Text("Find matches") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
