package uz.skillswap.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.data.OfferCard
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
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

@Composable
fun HomeScreen(vm: SkillSwapViewModel, onOfferClick: (Long) -> Unit) {
    val offers by vm.offers.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selected by vm.category.collectAsStateWithLifecycle()
    val user by vm.currentUser.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            GradientHeader {
                Text("Hi, ${user?.fullName?.substringBefore(' ') ?: "guest"} 👋",
                    style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("What do you want to learn today?", style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search a skill or a teacher") },
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

fun ratingText(offer: OfferCard): String =
    if (offer.reviewCount == 0) "New" else "★ ${offer.rating} (${offer.reviewCount})"
