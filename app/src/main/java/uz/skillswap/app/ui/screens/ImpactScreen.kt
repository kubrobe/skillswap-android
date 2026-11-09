package uz.skillswap.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.data.remote.Impact
import uz.skillswap.app.data.remote.TopSkill
import uz.skillswap.app.data.remote.WeekCount
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.CountUpText
import uz.skillswap.app.ui.components.EmptyState
import uz.skillswap.app.ui.components.Gold
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.SectionTitle
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.Lavender
import uz.skillswap.app.util.formatUzs
import uz.skillswap.app.util.growthPercent
import uz.skillswap.app.util.weekLabel

/**
 * "Community impact": what all SkillSwap people (app + website, one database) did together.
 * The numbers reload after every live change, so a lesson finished during the demo shows up at once.
 */
@Composable
fun ImpactScreen(vm: SkillSwapViewModel, onBack: () -> Unit) {
    val impact by vm.impact.collectAsStateWithLifecycle()
    val error by vm.impactError.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    LaunchedEffect(sync.lastSync) { vm.loadImpact() }        // first open + after every live update

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text("Community impact", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Live · app + website, one database", style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Together, people saved about", style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f))
            MoneyCountUp(impact?.savedUzs ?: 0L)
            Text(
                impact?.assumption ?: " ",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(10.dp))
        }

        val data = impact
        if (data == null && error != null) {
            EmptyState("📡", "Could not load the numbers", error ?: "")
            TextButton(onClick = { vm.loadImpact() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Try again")
            }
        } else if (data == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else Column(
            Modifier.padding(16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("👥", data.members, "members", null)
                StatTile("🎓", data.lessonsDone, "lessons finished", "${data.upcoming} coming up")
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("⏱️", data.hoursShared, "hours of knowledge shared", null)
                StatTile("🪙", data.credits, "credits in circulation", "1 credit = 1 hour")
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("🧑‍🏫", data.teachers, "teachers", "${data.skills} different skills")
                RatingTile(data)
            }
            WeeklyCard(data.weekly)
            if (data.topSkills.isNotEmpty()) TopSkillsCard(data.topSkills)
        }
    }
}

/** The big money number counts up like a slot machine. */
@Composable
private fun MoneyCountUp(target: Long) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.toFloat(), tween(1400, easing = FastOutSlowInEasing)) }
    val shown = if (anim.isRunning) anim.value.toLong() else target
    Row(verticalAlignment = Alignment.Bottom) {
        Text(formatUzs(shown), color = Lavender, fontSize = 38.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(" UZS", color = Lavender.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun RowScope.StatTile(emoji: String, value: Int, label: String, sub: String?) {
    AppCard(Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 22.sp)
            CountUpText(value, MaterialTheme.typography.headlineSmall, MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = GoldDeep)
        }
    }
}

@Composable
private fun RowScope.RatingTile(data: Impact) {
    AppCard(Modifier.weight(1f).fillMaxHeight()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("⭐", fontSize = 22.sp)
            Text(if (data.reviews == 0) "–" else "${data.avgRating}", style = MaterialTheme.typography.headlineSmall, color = Gold)
            Text("average rating", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${data.reviews} reviews", style = MaterialTheme.typography.labelSmall, color = GoldDeep)
        }
    }
}

/** Finished lessons per week. Bars grow one after another; the current week is lighter (still filling up). */
@Composable
private fun WeeklyCard(weeks: List<WeekCount>) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val max = (weeks.maxOfOrNull { it.lessons } ?: 0).coerceAtLeast(1)
    val growth = growthPercent(weeks.map { it.lessons })
    val full = Brush.verticalGradient(listOf(GoldPrimary, GoldDeep))
    val partial = Brush.verticalGradient(listOf(GoldPrimary.copy(alpha = 0.45f), GoldDeep.copy(alpha = 0.35f)))

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Lessons finished per week", Modifier.weight(1f))
                if (growth != null) {
                    Text(
                        (if (growth >= 0) "📈 +" else "📉 ") + "$growth%",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = if (growth >= 0) Green else MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(170.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                weeks.forEachIndexed { i, w ->
                    val current = i == weeks.lastIndex
                    val frac = w.lessons.toFloat() / max
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${w.lessons}", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.graphicsLayer { alpha = grow.value })
                        Box(
                            Modifier.fillMaxWidth()
                                .height((120f * frac).coerceAtLeast(4f).dp)
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    // each bar starts a little later than the one before it
                                    scaleY = ((grow.value * (weeks.size + 3) - i) / 4f).coerceIn(0f, 1f)
                                }
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (current) partial else full)
                        )
                        Text(if (current) "now" else weekLabel(w.week), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            if (growth != null) {
                Text("Last 3 full weeks compared with the 3 weeks before.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The most booked skills, with bars that slide out from the left. */
@Composable
private fun TopSkillsCard(top: List<TopSkill>) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, delayMillis = 300, easing = FastOutSlowInEasing)) }
    val max = (top.maxOfOrNull { it.lessons } ?: 0).coerceAtLeast(1)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Most popular skills")
            top.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.emoji, fontSize = 20.sp, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text("${i + 1}. ${s.name}", style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${s.lessons}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            Modifier.padding(top = 4.dp).fillMaxWidth(s.lessons.toFloat() / max).height(6.dp)
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = grow.value
                                }
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(GoldDeep, GoldPrimary)))
                        )
                    }
                }
            }
        }
    }
}
