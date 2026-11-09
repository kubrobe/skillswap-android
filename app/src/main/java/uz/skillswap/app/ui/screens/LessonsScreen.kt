package uz.skillswap.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uz.skillswap.app.data.BookingLine
import uz.skillswap.app.data.STATUS_BOOKED
import uz.skillswap.app.data.STATUS_DONE
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.ConfirmDialog
import uz.skillswap.app.ui.components.EmptyState
import uz.skillswap.app.ui.components.Gold
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.Pill
import uz.skillswap.app.ui.components.Red
import uz.skillswap.app.ui.components.SectionTitle
import uz.skillswap.app.ui.components.SmallPill
import uz.skillswap.app.ui.components.StarRow
import uz.skillswap.app.ui.theme.BrandStart
import uz.skillswap.app.ui.theme.Lavender
import uz.skillswap.app.util.formatDate
import uz.skillswap.app.util.slotLabel

@Composable
fun LessonsScreen(vm: SkillSwapViewModel) {
    val learning by vm.learning.collectAsStateWithLifecycle()
    val teaching by vm.teaching.collectAsStateWithLifecycle()
    var learnMode by rememberSaveable { mutableStateOf(true) }
    var rateTarget by remember { mutableStateOf<BookingLine?>(null) }
    var cancelTarget by remember { mutableStateOf<BookingLine?>(null) }
    var doneTarget by remember { mutableStateOf<BookingLine?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun show(err: String?) {
        if (err != null) scope.launch { snackbar.showSnackbar(err) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            GradientHeader {
                Text("My lessons", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Your learning and teaching plan", style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("I learn (${learning.size})", learnMode, { learnMode = true },
                        selectedBg = Color.White, selectedFg = BrandStart,
                        idleBg = Color.White.copy(alpha = 0.2f), idleFg = Color.White)
                    Pill("I teach (${teaching.size})", !learnMode, { learnMode = false },
                        selectedBg = Color.White, selectedFg = BrandStart,
                        idleBg = Color.White.copy(alpha = 0.2f), idleFg = Color.White)
                }
                Spacer(Modifier.height(6.dp))
            }

            // The list fades when you switch between "I learn" and "I teach"
            Crossfade(targetState = learnMode, animationSpec = tween(220), label = "lessonTabs") { mode ->
                val lines = if (mode) learning else teaching
                val upcoming = lines.filter { it.status == STATUS_BOOKED }
                val history = lines.filter { it.status != STATUS_BOOKED }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (lines.isEmpty()) {
                        item {
                            if (mode) EmptyState("🎓", "No lessons yet", "Open Browse and book your first lesson.")
                            else EmptyState("🧑‍🏫", "Nobody booked you yet", "Publish a skill in the Teach tab and learners will find you.")
                        }
                    }
                    if (upcoming.isNotEmpty()) {
                        item { SectionTitle("Upcoming (${upcoming.size})") }
                        items(upcoming, key = { it.bookingId }) { b ->
                            LessonCard(b, mode, onCancel = { cancelTarget = b }, onDone = { doneTarget = b }, onRate = {})
                        }
                    }
                    if (history.isNotEmpty()) {
                        item { SectionTitle("History (${history.size})", Modifier.padding(top = 8.dp)) }
                        items(history, key = { it.bookingId }) { b ->
                            LessonCard(b, mode, onCancel = {}, onDone = {}, onRate = { rateTarget = b })
                        }
                    }
                }
            }
        }
    }

    cancelTarget?.let { target ->
        ConfirmDialog(
            title = "Cancel this lesson?",
            text = if (learnMode) "${target.skillName} · ${slotLabel(target.day, target.hour)}\nThe time becomes free for other learners."
                   else "${target.learnerName} booked ${target.skillName} · ${slotLabel(target.day, target.hour)}.\nCancel it? The learner will see it as cancelled.",
            confirmLabel = "Yes, cancel",
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.cancel(target.bookingId) { show(it) }
                cancelTarget = null
            },
            onDismiss = { cancelTarget = null }
        )
    }
    doneTarget?.let { target ->
        ConfirmDialog(
            title = "Mark as done?",
            text = "Did you finish \"${target.skillName}\"? After this you can rate the lesson.",
            confirmLabel = "Yes, it is done",
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.complete(target.bookingId) { show(it) }
                doneTarget = null
            },
            onDismiss = { doneTarget = null }
        )
    }
    rateTarget?.let { target ->
        RateDialog(
            title = target.skillName,
            onDismiss = { rateTarget = null },
            onSend = { stars, comment ->
                vm.review(target.bookingId, stars, comment) { show(it) }
                rateTarget = null
            }
        )
    }
}

@Composable
private fun LessonCard(
    b: BookingLine, learnMode: Boolean,
    onCancel: () -> Unit, onDone: () -> Unit, onRate: () -> Unit
) {
    val (label, statusColor) = when (b.status) {
        STATUS_BOOKED -> "Upcoming" to Lavender
        STATUS_DONE -> "Completed" to Green
        else -> "Cancelled" to Red
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.emoji, fontSize = 28.sp)
                Text(b.skillName, Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium)
                SmallPill(label, statusColor)
            }
            Text(
                if (learnMode) "Teacher: ${b.teacherName}" else "Learner: ${b.learnerName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("📅 ${slotLabel(b.day, b.hour)}   ·   booked ${formatDate(b.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (learnMode && b.status == STATUS_BOOKED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = onDone, shape = RoundedCornerShape(14.dp)) { Text("Mark as done") }
                    OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Text("Cancel") }
                }
            }
            if (!learnMode && b.status == STATUS_BOOKED) {
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(top = 4.dp)) { Text("Cancel this lesson") }
            }
            if (learnMode && b.status == STATUS_DONE && b.myRating == null) {
                OutlinedButton(onClick = onRate, shape = RoundedCornerShape(14.dp)) { Text("⭐ Rate this lesson") }
            }
            if (b.myRating != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your rating  ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StarRow(b.myRating)
                }
            }
        }
    }
}

@Composable
private fun RateDialog(title: String, onDismiss: () -> Unit, onSend: (Int, String) -> Unit) {
    var stars by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate: $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    for (i in 1..5) {
                        TextButton(onClick = { stars = i }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(if (i <= stars) "★" else "☆", fontSize = 30.sp, color = Gold)
                        }
                    }
                }
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    label = { Text("Comment (optional)") }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(stars, comment) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
