package uz.skillswap.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import uz.skillswap.app.data.remote.LeaderRow
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.BadgeCircle
import uz.skillswap.app.ui.components.Gold
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Pill
import uz.skillswap.app.ui.components.SectionTitle
import uz.skillswap.app.ui.components.SmallPill
import uz.skillswap.app.ui.components.StaggerIn
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.theme.BrandStart
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.Lavender
import uz.skillswap.app.ui.theme.OnGold

/** Badges (with progress) and the top teachers, this week or all time, on a podium. */
@Composable
fun AchievementsScreen(vm: SkillSwapViewModel, onBack: () -> Unit) {
    val tr = LocalStrings.current
    val data by vm.achievements.collectAsStateWithLifecycle()
    var weekly by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { vm.loadAchievements() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr.back, tint = Color.White)
                }
                Column {
                    Text(tr.achievementsTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    val badges = data?.badges.orEmpty()
                    Text(tr.unlockedOf(badges.count { it.unlocked }, badges.size),
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val a = data
        if (a == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // badges, two per row
            a.badges.chunked(2).forEachIndexed { r, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { c, b ->
                        Box(Modifier.weight(1f)) {
                            StaggerIn(r * 2 + c) {
                                AppCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BadgeCircle(b.id, b.progress, b.goal)
                                        Text(tr.badgeTitle(b.id), style = MaterialTheme.typography.titleSmall,
                                            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(tr.badgeDesc(b.id), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
                                        if (b.goal > 1 && !b.unlocked) {
                                            Text("${b.progress} / ${b.goal}", style = MaterialTheme.typography.labelSmall, color = GoldDeep)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionTitle("🏆 " + tr.topTeachers)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(tr.thisWeek, weekly, { weekly = true })
                Pill(tr.allTimeLabel, !weekly, { weekly = false })
            }
            val rows = if (weekly) a.week else a.allTime
            if (rows.isEmpty()) {
                Text(tr.noOneYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Podium(rows.take(3), a.me, key = weekly)
                rows.drop(3).forEachIndexed { i, row -> LeaderLine(i + 4, row, row.id == a.me) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Places 2, 1, 3 side by side; the blocks rise one after another, a crown on the winner. */
@Composable
private fun Podium(top: List<LeaderRow>, me: Long, key: Boolean) {
    val tr = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom) {
        for (place in listOf(1, 0, 2)) {
            val row = top.getOrNull(place)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (row != null) {
                    val rise = remember(row.id, key) { Animatable(0f) }
                    LaunchedEffect(row.id, key) {
                        delay(listOf(250L, 0L, 450L)[place])
                        rise.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 200f))
                    }
                    Text(if (place == 0) "👑" else " ", fontSize = 22.sp)
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(if (row.id == me) GoldPrimary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Text(row.emoji, fontSize = 24.sp) }
                    Text(if (row.id == me) "${row.name} · ${tr.you}" else row.name,
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                    Text(tr.lessonsN(row.lessons), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    PodiumBlock(place, listOf(128.dp, 96.dp, 76.dp)[place], rise.value)
                }
            }
        }
    }
}

@Composable
private fun PodiumBlock(place: Int, height: Dp, rise: Float) {
    val colors = when (place) {
        0 -> listOf(Lavender, GoldPrimary, GoldDeep)                         // gold
        1 -> listOf(Color(0xFFE3E6EB), Color(0xFFB7BEC9), Color(0xFF8E96A3))  // silver
        else -> listOf(Color(0xFFE8B98F), Color(0xFFC98A55), Color(0xFF9C6233)) // bronze
    }
    Box(
        Modifier.fillMaxWidth().height(height)
            .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f); scaleY = rise }
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Brush.verticalGradient(colors)),
        contentAlignment = Alignment.TopCenter
    ) {
        Text("${place + 1}", color = if (place == 0) OnGold else BrandStart, fontSize = 30.sp,
            fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun LeaderLine(rank: Int, row: LeaderRow, mine: Boolean) {
    val tr = LocalStrings.current
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp))
            Text(row.emoji, fontSize = 22.sp)
            Text(row.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 10.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (mine) SmallPill(tr.you, GoldDeep)
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(tr.lessonsN(row.lessons), style = MaterialTheme.typography.labelLarge)
                if (row.rating > 0) Text("★ ${row.rating}", style = MaterialTheme.typography.labelSmall, color = Gold)
            }
        }
    }
}
