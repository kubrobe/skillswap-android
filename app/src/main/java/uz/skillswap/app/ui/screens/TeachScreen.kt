package uz.skillswap.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.EmojiTile
import uz.skillswap.app.ui.components.EmptyState
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.LevelBadge
import uz.skillswap.app.ui.components.Pill
import uz.skillswap.app.ui.components.SectionTitle
import uz.skillswap.app.ui.components.levelColor
import uz.skillswap.app.ui.components.levelMedal
import uz.skillswap.app.util.DAY_NAMES
import uz.skillswap.app.util.hasDuplicateSlots
import uz.skillswap.app.util.slotLabel

private class LevelInfo(val name: String, val hint: String, val dots: Int)

private val LEVELS = listOf(
    LevelInfo("Beginner", "I know the basics and can help a starter", 1),
    LevelInfo("Intermediate", "I use it regularly and can explain it well", 2),
    LevelInfo("Advanced", "I am confident and can teach in depth", 3)
)
private val HOURS = (8..21).toList()

@Composable
fun TeachScreen(vm: SkillSwapViewModel) {
    val skills by vm.skills.collectAsStateWithLifecycle()
    val myOffers by vm.myOffers.collectAsStateWithLifecycle()
    var skillId by rememberSaveable { mutableStateOf(0L) }
    var level by rememberSaveable { mutableStateOf(LEVELS[0].name) }
    var description by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(1) }
    var hour by rememberSaveable { mutableStateOf(18) }
    var slots by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
    var formError by remember { mutableStateOf<String?>(null) }
    var justPublished by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun say(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    // The Publish button turns green for a moment after a successful publish
    LaunchedEffect(justPublished) {
        if (justPublished) {
            delay(1600)
            justPublished = false
        }
    }
    val buttonColor by animateColorAsState(
        if (justPublished) Green else MaterialTheme.colorScheme.primary, tween(200), label = "publishColor"
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            GradientHeader {
                Text("Teach a skill", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Share what you know. Others book your free times.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(8.dp))
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("1. Choose a skill")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(skills, key = { it.id }) { s ->
                                Pill("${s.emoji} ${s.name}", skillId == s.id, { skillId = s.id; formError = null })
                            }
                        }
                    }
                }

                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("2. How well do you know it?")
                        LEVELS.forEach { l ->
                            LevelOption(l, selected = level == l.name) { level = l.name }
                        }
                        OutlinedTextField(
                            value = description, onValueChange = { description = it; formError = null },
                            label = { Text("What will you teach?") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("3. Your free times (every week)")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DAY_NAMES.indices.toList()) { i ->
                                Pill(DAY_NAMES[i], day == i + 1, { day = i + 1 })
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(HOURS) { h -> Pill("$h:00", hour == h, { hour = h }) }
                        }
                        OutlinedButton(
                            onClick = {
                                val next = slots + (day to hour)
                                if (hasDuplicateSlots(next)) say("This time is already in the list")
                                else { slots = next; formError = null }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("+ Add ${slotLabel(day, hour)}") }

                        slots.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🕒 ${slotLabel(s.first, s.second)}", Modifier.weight(1f))
                                TextButton(onClick = { slots = slots - s }) { Text("Remove") }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = formError != null,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    Text(formError ?: "", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = {
                        val problem = when {
                            skillId == 0L -> "Step 1: choose a skill first"
                            description.isBlank() -> "Step 2: write a short description"
                            slots.isEmpty() -> "Step 3: add at least one free time"
                            else -> null
                        }
                        if (problem != null) {
                            formError = problem
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            vm.createOffer(skillId, level, description, slots) { err ->
                                if (err == null) {
                                    description = ""; slots = emptyList(); skillId = 0L; formError = null
                                    justPublished = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else formError = err
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text(if (justPublished) "Published ✓" else "Publish my lesson") }

                SectionTitle("My listings (${myOffers.size})", Modifier.padding(top = 6.dp))
                if (myOffers.isEmpty()) {
                    EmptyState("🌱", "You do not teach anything yet", "Fill in the three steps above to publish your first lesson.")
                }
                myOffers.forEach { o ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            EmojiTile(o.emoji, o.category, size = 48.dp)
                            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(o.skillName, style = MaterialTheme.typography.titleMedium)
                                LevelBadge(o.level)
                                Text("${o.freeSlots} free times · ${ratingText(o)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One selectable proficiency row: medal, short explanation and filled dots. */
@Composable
private fun LevelOption(info: LevelInfo, selected: Boolean, onClick: () -> Unit) {
    val color = levelColor(info.name)
    val borderColor by animateColorAsState(
        if (selected) color else MaterialTheme.colorScheme.outlineVariant, tween(180), label = "levelBorder"
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), shape)
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(levelMedal(info.name), fontSize = 24.sp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(info.name, style = MaterialTheme.typography.titleSmall)
            Text(info.hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..3) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (i <= info.dots) color else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}
