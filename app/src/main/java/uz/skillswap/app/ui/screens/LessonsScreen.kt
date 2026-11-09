package uz.skillswap.app.ui.screens

import uz.skillswap.app.ui.components.SkillTile
import uz.skillswap.app.ui.i18n.Strings
import uz.skillswap.app.ui.i18n.LocalStrings
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
import uz.skillswap.app.ui.components.bottomBarSpace
import uz.skillswap.app.ui.components.LearnerCheckInDialog
import uz.skillswap.app.ui.components.TeacherCheckInDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import uz.skillswap.app.util.jitsiUrl
import uz.skillswap.app.util.nextOccurrence
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
    var checkinTarget by remember { mutableStateOf<BookingLine?>(null) }   // learner checks in
    var qrTarget by remember { mutableStateOf<BookingLine?>(null) }        // teacher shows the QR
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var planTarget by remember { mutableStateOf<BookingLine?>(null) }
    val tr = LocalStrings.current

    fun show(err: String?) {
        if (err != null) scope.launch { snackbar.showSnackbar(err) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomBarSpace())) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            GradientHeader {
                Text(tr.myLessons, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(tr.lessonsSubtitle, style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(tr.iLearn(learning.size), learnMode, { learnMode = true },
                        selectedBg = Color.White, selectedFg = BrandStart,
                        idleBg = Color.White.copy(alpha = 0.2f), idleFg = Color.White)
                    Pill(tr.iTeach(teaching.size), !learnMode, { learnMode = false },
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = bottomBarSpace()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (lines.isEmpty()) {
                        item {
                            if (mode) EmptyState("🎓", tr.noLessonsYet, tr.noLessonsHint)
                            else EmptyState("🧑‍🏫", tr.nobodyBooked, tr.nobodyBookedHint)
                        }
                    }
                    if (upcoming.isNotEmpty()) {
                        item { SectionTitle(tr.upcomingN(upcoming.size)) }
                        items(upcoming, key = { it.bookingId }) { b ->
                            LessonCard(
                                b, mode, true, onCancel = { cancelTarget = b }, onDone = { checkinTarget = b }, onRate = {},
                                onShowQr = { qrTarget = b },
                                onVideo = {
                                    try { uriHandler.openUri(jitsiUrl(b.bookingId, b.createdAt)) }
                                    catch (e: Exception) { show(tr.noBrowser) }
                                },
                                onCalendar = { if (!addToCalendar(context, b, tr)) show(tr.noCalendar) },
                                onPlan = { planTarget = b }
                            )
                        }
                    }
                    if (history.isNotEmpty()) {
                        item { SectionTitle(tr.historyN(history.size), Modifier.padding(top = 8.dp)) }
                        items(history, key = { it.bookingId }) { b ->
                            LessonCard(
                                b, mode, true, onCancel = {}, onDone = {}, onRate = { rateTarget = b }, onShowQr = {},
                                onVideo = {}, onCalendar = {}, onPlan = { planTarget = b }
                            )
                        }
                    }
                }
            }
        }
    }

    cancelTarget?.let { target ->
        ConfirmDialog(
            title = tr.cancelLessonQ,
            text = if (learnMode) tr.cancelLearnerText(target.skillName, tr.slot(target.day, target.hour))
                   else tr.cancelTeacherText(target.learnerName, target.skillName, tr.slot(target.day, target.hour)),
            confirmLabel = tr.yesCancel,
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.cancel(target.bookingId) { show(it) }
                cancelTarget = null
            },
            onDismiss = { cancelTarget = null }
        )
    }
    checkinTarget?.let { target ->
        LearnerCheckInDialog(
            bookingId = target.bookingId, skillName = target.skillName,
            onDismiss = { checkinTarget = null },
            onSubmit = { code, done ->
                vm.complete(target.bookingId, code) { err ->
                    done(err)
                    if (err == null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        checkinTarget = null
                        vm.celebrate()
                    }
                }
            }
        )
    }
    qrTarget?.let { target ->
        target.checkinCode?.let { code ->
            TeacherCheckInDialog(target.bookingId, code, target.skillName, target.learnerName, onDismiss = { qrTarget = null })
        }
    }
    planTarget?.let { target ->
        PlanDialog(vm, target, onDismiss = { planTarget = null })
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
    b: BookingLine, learnMode: Boolean, online: Boolean,
    onCancel: () -> Unit, onDone: () -> Unit, onRate: () -> Unit, onShowQr: () -> Unit,
    onVideo: () -> Unit, onCalendar: () -> Unit, onPlan: () -> Unit
) {
    val tr = LocalStrings.current
    val (label, statusColor) = when (b.status) {
        STATUS_BOOKED -> tr.statusUpcoming to Lavender
        STATUS_DONE -> tr.statusCompleted to Green
        else -> tr.statusCancelled to Red
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkillTile(b.skillName, "", size = 40.dp)
                Text(b.skillName, Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium)
                SmallPill(label, statusColor)
            }
            Text(
                if (learnMode) tr.teacherIs(b.teacherName) else tr.learnerIs(b.learnerName),
                style = MaterialTheme.typography.bodyMedium
            )
            Text("📅 ${tr.slot(b.day, b.hour)}   ·   ${tr.bookedOn(formatDate(b.createdAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (learnMode && b.status == STATUS_BOOKED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = onDone, shape = RoundedCornerShape(14.dp)) { Text(tr.checkIn) }
                    OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Text(tr.cancel) }
                }
            }
            if (b.status == STATUS_BOOKED || (online && b.status == STATUS_DONE)) {
                Row {
                    if (b.status == STATUS_BOOKED) {
                        SmallAction(tr.video, onVideo)
                        SmallAction(tr.calendar, onCalendar)
                    }
                    if (online) SmallAction(tr.aiPlan, onPlan)
                }
            }
            if (!learnMode && b.status == STATUS_BOOKED && b.checkinCode != null) {
                // the teacher's side: the secret code + a button for the big QR
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr.checkinCode, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(b.checkinCode.toCharArray().joinToString(" "), fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                    }
                    Button(onClick = onShowQr, shape = RoundedCornerShape(14.dp)) { Text(tr.showQr) }
                }
            }
            if (!learnMode && b.status == STATUS_BOOKED) {
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(top = 4.dp)) { Text(tr.cancelThisLesson) }
            }
            if (learnMode && b.status == STATUS_DONE && b.myRating == null) {
                OutlinedButton(onClick = onRate, shape = RoundedCornerShape(14.dp)) { Text(tr.rateLesson) }
            }
            if (b.myRating != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr.yourRating, style = MaterialTheme.typography.bodySmall,
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
    val tr = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr.rateTitle(title)) },
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
                    label = { Text(tr.commentOptional) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(stars, comment) }) { Text(tr.send) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr.close) } }
    )
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Opens the phone's calendar app with the next lesson filled in. Returns false if there is no calendar app. */
private fun addToCalendar(context: Context, b: BookingLine, tr: Strings): Boolean {
    val start = nextOccurrence(System.currentTimeMillis(), b.day, b.hour, java.util.TimeZone.getDefault())
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000L)
        .putExtra(CalendarContract.Events.TITLE, "SkillSwap: ${b.skillName}")
        .putExtra(CalendarContract.Events.DESCRIPTION,
            "${tr.teacherIs(b.teacherName)}\n${tr.learnerIs(b.learnerName)}\n${tr.videoRoom}: ${jitsiUrl(b.bookingId, b.createdAt)}")
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** Asks the server for a 60-minute plan (AI when available, otherwise a smart template). */
@Composable
private fun PlanDialog(vm: SkillSwapViewModel, b: BookingLine, onDismiss: () -> Unit) {
    var plan by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val tr = LocalStrings.current
    LaunchedEffect(b.bookingId) {
        vm.lessonPlan(b.bookingId) { p, s, e -> plan = p; source = s; error = e }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr.planTitle(b.skillName)) },
        text = {
            when {
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                plan == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(22.dp))
                    Text(tr.preparingPlan)
                }
                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plan ?: "", style = MaterialTheme.typography.bodyMedium)
                    Text(if (source == "ai") tr.planByAi else tr.planTemplate,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr.close) } }
    )
}

