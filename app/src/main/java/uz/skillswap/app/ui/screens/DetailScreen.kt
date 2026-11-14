package uz.skillswap.app.ui.screens

import uz.skillswap.app.ui.components.AppIcon
import uz.skillswap.app.ui.components.IconText
import uz.skillswap.app.ui.components.SkillTile
import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.BookedCelebration
import uz.skillswap.app.ui.components.GradientHeader
import uz.skillswap.app.ui.components.Green
import uz.skillswap.app.ui.components.SectionTitle
import uz.skillswap.app.ui.components.StarRow
import uz.skillswap.app.util.formatDate

@Composable
fun DetailScreen(vm: SkillSwapViewModel, offerId: Long, onBack: () -> Unit, onBooked: () -> Unit) {
    val offer by remember(offerId) { vm.offer(offerId) }.collectAsStateWithLifecycle(null)
    val slots by remember(offerId) { vm.slots(offerId) }.collectAsStateWithLifecycle(emptyList())
    val reviews by remember(offerId) { vm.reviews(offerId) }.collectAsStateWithLifecycle(emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var bookedText by remember { mutableStateOf<String?>(null) }   // set after a successful booking
    // true while a booking request is on its way: a second tap used to send a second request, which
    // came back as "this time was just booked" right on top of the success screen
    var booking by remember { mutableStateOf(false) }
    val tr = LocalStrings.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) }
    ) { padding ->
        val o = offer
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            GradientHeader {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr.back, tint = Color.White)
                }
                if (o != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkillTile(o.skillName, o.category, size = 72.dp)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(o.skillName, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Text(tr.byTeacher(o.teacherName), style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${tr.level(o.level)}  ·  ${o.category}  ·  ${ratingText(o, tr.new)}",
                        style = MaterialTheme.typography.labelLarge, color = Color.White
                    )
                    if (o.trusted) {
                        Text(tr.trustedTeacher,
                            style = MaterialTheme.typography.labelMedium, color = Color(0xFF9FF0C6))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (o == null) return@Column

            Column(
                Modifier.navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionTitle(tr.aboutLesson)
                        Text(o.description, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                SectionTitle(tr.pickTime, Modifier.padding(top = 6.dp))
                slots.forEach { s ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr.daysFull[(s.day - 1).coerceIn(0, 6)], style = MaterialTheme.typography.titleSmall)
                                Text("${s.hour.toString().padStart(2, '0')}:00",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                enabled = !s.isBooked && !booking,
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    booking = true
                                    vm.book(s.id) { err ->
                                        booking = false
                                        if (err == null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            bookedText = tr.withTeacher(o.skillName, o.teacherName) + "\n" +
                                                tr.slotFull(s.day, s.hour)
                                        } else scope.launch { snackbar.showSnackbar(err) }
                                    }
                                }
                            ) { IconText(if (s.isBooked) tr.taken else tr.bookBtn) }
                        }
                    }
                }

                SectionTitle(tr.reviewsN(reviews.size), Modifier.padding(top = 6.dp))
                if (reviews.isEmpty()) {
                    Text(tr.noReviews,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
                reviews.forEach { r ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.learnerName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                StarRow(r.rating)
                            }
                            if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodyMedium)
                            Text(formatDate(r.createdAt), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    bookedText?.let { text ->
        BookedCelebration(
            skill = text.substringBefore('\n'), details = text.substringAfter('\n', ""),
            onDone = { bookedText = null; vm.celebrate(); vm.playCoin(-1); onBooked() }
        )
    }
}

