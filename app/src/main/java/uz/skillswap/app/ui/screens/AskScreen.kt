package uz.skillswap.app.ui.screens

import uz.skillswap.app.ui.components.AppIcon
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.data.remote.MatchResult
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppCard
import uz.skillswap.app.ui.components.SkillTile
import uz.skillswap.app.ui.components.SmallPill
import uz.skillswap.app.ui.components.StaggerIn
import uz.skillswap.app.ui.components.pressScale
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.OnGold

private enum class AskState { Idle, Thinking, Done }

/**
 * "Ask SkillSwap": tap the mic and say what you want to learn (English, Uzbek or Russian),
 * or type it. The server (AI when available) answers with the 3 best lessons and a reason for each.
 * Speech uses the phone's own recognizer (RecognizerIntent), so the app needs no microphone permission.
 */
@Composable
fun AskScreen(vm: SkillSwapViewModel, onClose: () -> Unit, onOpen: (Long) -> Unit) {
    val tr = LocalStrings.current
    val offers by vm.allOffers.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(AskState.Idle) }
    var result by remember { mutableStateOf<MatchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun ask(words: String) {
        if (words.isBlank() || state == AskState.Thinking) return
        state = AskState.Thinking
        error = null
        vm.askMatchmaker(words) { r, e ->
            result = r
            error = e
            state = if (r != null) AskState.Done else AskState.Idle
            if (r != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) {
            text = heard
            ask(heard)
        }
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tr.speechLocale)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tr.askPrompt)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            error = tr.noVoice
        }
    }

    // a solid sheet that slides up over Browse
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr.askTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = tr.close) }
            }
            Spacer(Modifier.height(18.dp))
            MicButton(listening = state == AskState.Thinking, label = tr.speak) { listen() }
            Spacer(Modifier.height(14.dp))
            Text(tr.tapToSpeak, style = MaterialTheme.typography.titleMedium)
            Text(tr.askExample, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(300) },
                    placeholder = { Text(tr.orType) }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Button(onClick = { ask(text) }, enabled = text.isNotBlank() && state != AskState.Thinking,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp)) { Text("→", fontSize = 20.sp) }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(20.dp))

            AnimatedContent(targetState = state, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "askState") { s ->
                when (s) {
                    AskState.Thinking -> ThinkingDots(tr.thinking)
                    AskState.Done -> {
                        val r = result
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (r != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (r.understood.isNotBlank()) {
                                        Text(tr.understood(r.understood), style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    SmallPill(if (r.source == "ai") tr.byAi else tr.bySmart, GoldDeep)
                                }
                                if (r.picks.isEmpty()) {
                                    Text(tr.noPicks, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                r.picks.forEachIndexed { i, pick ->
                                    val o = offers.firstOrNull { it.offerId == pick.offerId }
                                    if (o != null) {
                                        StaggerIn(i) {
                                            AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(o.offerId) }) {
                                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    SkillTile(o.skillName, o.category)
                                                    Column(Modifier.weight(1f).padding(start = 12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Text(o.skillName, style = MaterialTheme.typography.titleMedium,
                                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(tr.byTeacher(o.teacherName), style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(pick.reason, style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    Text("›", style = MaterialTheme.typography.headlineSmall,
                                                        color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AskState.Idle -> Spacer(Modifier.height(1.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A big gold mic with soft rings breathing around it. */
@Composable
private fun MicButton(listening: Boolean, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val rings = rememberInfiniteTransition(label = "mic")
    val wave = rings.animateFloat(0f, 1f, infiniteRepeatable(tween(if (listening) 900 else 2200, easing = LinearEasing)), label = "wave")
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        for (k in 0..1) {
            Box(
                Modifier.size(120.dp).graphicsLayer {
                    val p = (wave.value + k * 0.5f) % 1f
                    scaleX = 1f + p * 0.5f
                    scaleY = 1f + p * 0.5f
                    alpha = (1f - p) * 0.5f
                }.clip(CircleShape).background(GoldPrimary)
            )
        }
        Box(
            Modifier.size(120.dp).pressScale(source).clip(CircleShape)
                .background(Brush.linearGradient(listOf(GoldPrimary, GoldDeep)))
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .clickable(interactionSource = source, indication = null, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { AppIcon("mic", size = 54.dp, tint = OnGold) }
    }
}

/** Three gold dots that bounce one after another while the server thinks. */
@Composable
private fun ThinkingDots(label: String) {
    val bounce = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        while (true) {
            for (b in bounce) {
                b.animateTo(1f, tween(180))
                b.animateTo(0f, tween(180))
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            bounce.forEach { b ->
                Box(Modifier.size(12.dp).graphicsLayer { translationY = -b.value * 14.dp.toPx() }
                    .clip(CircleShape).background(GoldPrimary))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
