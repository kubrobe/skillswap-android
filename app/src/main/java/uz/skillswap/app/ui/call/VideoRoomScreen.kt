package uz.skillswap.app.ui.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import uz.skillswap.app.call.AudioRoute
import uz.skillswap.app.call.CallError
import uz.skillswap.app.call.CallLabels
import uz.skillswap.app.call.CallPhase
import uz.skillswap.app.call.CallUiState
import uz.skillswap.app.call.EndReason
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.AppIcon
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.i18n.Strings
import uz.skillswap.app.ui.theme.BrandEnd
import uz.skillswap.app.ui.theme.BrandStart
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.OnGold

// The call screen is always dark (video looks best on dark), in the app's graphite & gold.
private val CallBg = Color(0xFF0E0F12)
private val CallSurface = Color(0xFF1B1D23)
private val CallText = Color(0xFFF5F3EE)
private val CallMuted = Color(0xFFA9A59B)
private val HangUpRed = Color(0xFFE5484D)
private val Ok = Color(0xFF34D399)
private val Warn = Color(0xFFF2B84B)

/**
 * The video lesson. Opens the lesson's room (after the camera/microphone step), shows the other person big and
 * me small (drag me to any corner), and the controls. Leaving this screen does NOT end the call: only Leave does.
 */
@Composable
fun VideoRoomScreen(appVm: SkillSwapViewModel, bookingId: Long, onClose: () -> Unit) {
    val vm: VideoRoomViewModel = viewModel(factory = VideoRoomViewModel.Factory)
    val call by vm.state.collectAsStateWithLifecycle()
    val learning by appVm.learning.collectAsStateWithLifecycle()
    val teaching by appVm.teaching.collectAsStateWithLifecycle()
    val tr = LocalStrings.current
    val context = LocalContext.current

    // What we know before the room answers: the lesson line (skill + the other person's name)
    val lesson = remember(learning, teaching, bookingId) {
        learning.firstOrNull { it.bookingId == bookingId }?.let { it to it.teacherName }
            ?: teaching.firstOrNull { it.bookingId == bookingId }?.let { it to it.learnerName }
    }
    val skill = call.skill.ifBlank { lesson?.first?.skillName ?: "" }
    val peerName = call.peer?.name?.takeIf { it.isNotBlank() } ?: lesson?.second ?: ""

    // a finished call of ANOTHER lesson is cleared when this screen opens
    LaunchedEffect(bookingId) {
        if (!call.phase.isActive && call.phase != CallPhase.ENDING && call.phase != CallPhase.IDLE && call.bookingId != bookingId) vm.dismiss()
    }

    // keep the screen awake during the lesson; a finished call is cleared once this screen is gone
    // (clearing it while the screen is still shown would bring back the join step and join again)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            vm.dismiss()                      // does nothing while a call is running
        }
    }

    fun join(audio: Boolean, video: Boolean) = vm.join(
        bookingId, audio, video, CallLabels(tr.callNotifTitle(skill), tr.callNotifText, tr.callHangUp)
    )

    Box(Modifier.fillMaxSize().background(CallBg)) {
        val inThisCall = call.bookingId == bookingId && call.phase != CallPhase.IDLE
        when {
            call.error == CallError.BUSY -> BusyCard(tr, onBack = { vm.clearBusyError(); onClose() })
            !inThisCall -> PermissionStep(context, tr, peerName, skill, onJoin = ::join, onBack = onClose)
            call.phase == CallPhase.ENDED || call.phase == CallPhase.FAILED ->
                EndedCard(call, tr, onRetry = { vm.retry() }, onBack = onClose)
            else -> CallContent(call, tr, peerName, skill, vm)
        }
    }
}

// ---------------------------------------------------------------- permissions

private enum class PermStep { EXPLAIN, DENIED }

@Composable
private fun PermissionStep(
    context: Context, tr: Strings, peerName: String, skill: String,
    onJoin: (audio: Boolean, video: Boolean) -> Unit, onBack: () -> Unit
) {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val needed = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)   // optional: the ongoing-call notification
        }
    }
    var step by rememberSaveable { mutableStateOf(PermStep.EXPLAIN) }
    var permanent by rememberSaveable { mutableStateOf(false) }
    var cam by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }

    // Both already allowed: go straight in.
    LaunchedEffect(Unit) { if (cam && mic) onJoin(true, true) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cam = granted(Manifest.permission.CAMERA)
        mic = granted(Manifest.permission.RECORD_AUDIO)
        if (cam && mic) {
            onJoin(true, true)
        } else {
            val activity = context.findActivity()
            val missing = listOfNotNull(
                Manifest.permission.CAMERA.takeIf { !cam }, Manifest.permission.RECORD_AUDIO.takeIf { !mic })
            // asked and refused with "don't ask again" (or refused twice): only Settings can turn it on now
            permanent = activity != null && missing.none { p -> activity.shouldShowRequestPermissionRationale(p) }
            step = PermStep.DENIED
        }
    }

    // Back from Settings with everything allowed: join at once.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && step == PermStep.DENIED) {
                cam = granted(Manifest.permission.CAMERA)
                mic = granted(Manifest.permission.RECORD_AUDIO)
                if (cam && mic) onJoin(true, true)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (cam && mic) {                         // joining: a spinner for the moment until the room answers
        CenterStatus(tr.callOpening, peerName, skill, spinner = true)
        return
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(GoldPrimary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            AppIcon("video", size = 46.dp, tint = GoldPrimary)
        }
        Spacer(Modifier.height(22.dp))
        Text(if (step == PermStep.DENIED) tr.permDeniedTitle else tr.permTitle,
            style = MaterialTheme.typography.headlineSmall, color = CallText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (step == PermStep.DENIED && permanent) tr.permDeniedText else tr.permText,
            style = MaterialTheme.typography.bodyMedium, color = CallMuted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        if (step == PermStep.DENIED && permanent) {
            GoldButton(tr.permOpenSettings) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
        } else {
            GoldButton(tr.permAllow) { launcher.launch(needed.toTypedArray()) }
        }
        if (step == PermStep.DENIED) {
            Spacer(Modifier.height(10.dp))
            when {
                mic && !cam -> OutlinedCallButton(tr.permAudioOnly) { onJoin(true, false) }
                cam && !mic -> OutlinedCallButton(tr.permVideoOnly) { onJoin(false, true) }
                else -> Text(tr.permNeedOne, color = CallMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onBack) { Text(tr.back, color = CallMuted) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---------------------------------------------------------------- the call

@Composable
private fun CallContent(call: CallUiState, tr: Strings, peerName: String, skill: String, vm: VideoRoomViewModel) {
    val haptic = LocalHapticFeedback.current
    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var leaveDialog by remember { mutableStateOf(false) }
    val connected = call.phase == CallPhase.CONNECTED
    val showRemote = call.remoteVideo != null && call.peerCameraOn && connected && call.eglContext != null

    BackHandler { leaveDialog = true }

    // Controls hide after 5 s of a running call (more of the video); a tap brings them back.
    LaunchedEffect(lastTouch, connected) {
        controlsVisible = true
        if (connected) {
            delay(5_000)
            controlsVisible = false
        }
    }

    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null
        ) { lastTouch = System.currentTimeMillis() }
    ) {
        // 1. the other person (or their picture while there is no video)
        val egl = call.eglContext
        if (showRemote && egl != null) {
            VideoSurface(call.remoteVideo, egl, Modifier.fillMaxSize(), fill = true)
        } else {
            PeerStage(call, tr, peerName)
        }

        // 2. soft shades so the white text and controls stay readable on any video
        Box(Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))

        // 3. top: who, what, how long, connection
        TopBar(call, tr, peerName, skill, Modifier.align(Alignment.TopCenter))

        // 4. me, small and draggable
        if (call.hasCamera && egl != null) {
            SelfView(call, tr, egl, controlsVisible)
        }

        // 5. controls
        AnimatedVisibility(
            visible = controlsVisible || !connected,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(220)) + slideOutVertically(tween(260)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Controls(call, tr,
                onMic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.toggleMic(); lastTouch = System.currentTimeMillis() },
                onCamera = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.toggleCamera(); lastTouch = System.currentTimeMillis() },
                onFlip = { vm.switchCamera(); lastTouch = System.currentTimeMillis() },
                onRoute = { vm.selectAudioRoute(it); lastTouch = System.currentTimeMillis() },
                onHangUp = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); leaveDialog = true })
        }
    }

    if (leaveDialog) {
        AlertDialog(
            onDismissRequest = { leaveDialog = false },
            containerColor = CallSurface, titleContentColor = CallText, textContentColor = CallMuted,
            title = { Text(tr.callLeaveQ) },
            text = { Text(tr.callLeaveText(peerName.substringBefore(' ').ifBlank { peerName })) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { leaveDialog = false; vm.hangUp(forBoth = false) }) { Text(tr.callLeave, color = HangUpRed) }
                    if (call.peer?.online == true) {
                        TextButton(onClick = { leaveDialog = false; vm.hangUp(forBoth = true) }) { Text(tr.callEndForBoth, color = HangUpRed) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { leaveDialog = false }) { Text(tr.callStay, color = CallText) } }
        )
    }
}

/** No video from the other person yet (waiting, connecting, or their camera is off): their initial, big and calm. */
@Composable
private fun PeerStage(call: CallUiState, tr: Strings, peerName: String) {
    val first = peerName.substringBefore(' ').ifBlank { peerName }
    val waiting = call.phase == CallPhase.WAITING || call.phase == CallPhase.CREATING || call.phase == CallPhase.JOINING
    val pulse = rememberInfiniteTransition(label = "waiting")
    val ring by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "ring")
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BrandStart, CallBg, CallBg))).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (waiting) {
                Box(Modifier.size(132.dp).graphicsLayer { scaleX = ring; scaleY = ring; alpha = 2f - ring * 1.4f }
                    .clip(CircleShape).background(GoldPrimary.copy(alpha = 0.22f)))
            }
            Box(
                Modifier.size(112.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GoldPrimary, GoldDeep, BrandEnd))),
                contentAlignment = Alignment.Center
            ) {
                Text(first.take(1).uppercase().ifBlank { "?" }, color = OnGold, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(peerName, style = MaterialTheme.typography.titleLarge, color = CallText, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        val line = when {
            call.phase == CallPhase.CREATING -> tr.callOpening
            call.phase == CallPhase.JOINING -> tr.callJoining
            call.phase == CallPhase.ENDING -> tr.callLeaving
            call.phase == CallPhase.WAITING && call.peerLeftReason == "left" -> tr.peerLeft(first)
            call.phase == CallPhase.WAITING && call.peerLeftReason == "disconnected" -> tr.peerDropped(first)
            call.phase == CallPhase.WAITING -> tr.callWaitingFor(first)
            call.phase == CallPhase.CONNECTING -> tr.callConnecting
            call.phase == CallPhase.RECONNECTING -> tr.callReconnecting
            call.phase == CallPhase.CONNECTED && !call.peerCameraOn -> tr.peerCameraOff(first)
            else -> tr.callConnecting
        }
        Text(line, style = MaterialTheme.typography.bodyMedium, color = CallMuted, textAlign = TextAlign.Center)
        if (call.phase == CallPhase.WAITING && call.peerLeftReason == null) {
            Spacer(Modifier.height(6.dp))
            Text(tr.callWaitingHint, style = MaterialTheme.typography.bodySmall, color = CallMuted.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
        if (call.phase == CallPhase.CONNECTING || call.phase == CallPhase.RECONNECTING) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.size(22.dp), color = GoldPrimary, strokeWidth = 2.dp)
        }
        if (!call.turn && (call.phase == CallPhase.CONNECTING || call.phase == CallPhase.WAITING)) {
            Spacer(Modifier.height(16.dp))
            Text(tr.noTurnHint, style = MaterialTheme.typography.labelSmall, color = Warn.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TopBar(call: CallUiState, tr: Strings, peerName: String, skill: String, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(peerName, style = MaterialTheme.typography.titleMedium, color = CallText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill, style = MaterialTheme.typography.bodySmall, color = CallText.copy(alpha = 0.75f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (call.connectedSince > 0L) {
                    Text("  ·  ", color = CallText.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    CallTimer(call.connectedSince)
                }
            }
        }
        StatusChip(call, tr)
    }
}

@Composable
private fun CallTimer(since: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val s = ((now - since) / 1000).coerceAtLeast(0)
    val text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    Text(text, style = MaterialTheme.typography.bodySmall, color = CallText.copy(alpha = 0.75f))
}

@Composable
private fun StatusChip(call: CallUiState, tr: Strings) {
    val (label, color) = when (call.phase) {
        CallPhase.CONNECTED -> tr.callConnected to Ok
        CallPhase.RECONNECTING -> tr.callReconnecting to Warn
        CallPhase.WAITING -> tr.callWaitingFor("").trim().trimEnd(':') to CallMuted
        else -> tr.callConnecting to Warn
    }
    val dot by animateColorAsState(color, tween(300), label = "status")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        if (call.phase != CallPhase.CONNECTED) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = CallText)
        }
    }
}

/** My own picture: a small window, drag it anywhere, it snaps to the nearest corner. */
@Composable
private fun SelfView(call: CallUiState, tr: Strings, egl: org.webrtc.EglBase.Context, controlsVisible: Boolean) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val w = 104.dp
        val h = 148.dp
        val margin = 16.dp
        val maxX = with(density) { (maxWidth - w - margin).toPx() }
        val minX = with(density) { margin.toPx() }
        val minY = with(density) { 64.dp.toPx() }
        // leave room for the control bar when it is shown
        val maxY = with(density) { (maxHeight - h - if (controlsVisible) 120.dp else margin).toPx() }
        val x = remember { Animatable(maxX) }
        val y = remember { Animatable(minY) }
        var corner by remember { mutableStateOf(Pair(true, false)) }      // (right, bottom)
        LaunchedEffect(maxX, maxY, corner) {
            x.animateTo(if (corner.first) maxX else minX, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        }
        LaunchedEffect(maxX, maxY, corner) {
            y.animateTo(if (corner.second) maxY else minY, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        }
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                .size(w, h)
                .clip(shape)
                .background(CallSurface)
                .border(BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.55f)), shape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            corner = Pair(x.value > (minX + maxX) / 2f, y.value > (minY + maxY) / 2f)
                        }
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            x.snapTo((x.value + drag.x).coerceIn(minX, maxX))
                            y.snapTo((y.value + drag.y).coerceIn(minY, maxY))
                        }
                    }
                }
                .semantics { contentDescription = "self view" }
        ) {
            if (call.cameraOn && call.localVideo != null) {
                VideoSurface(call.localVideo, egl, Modifier.fillMaxSize(), mirror = call.frontCamera, overlay = true, cornerRadius = 18.dp)
            } else {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon("video_off", size = 26.dp, tint = CallMuted)
                    Spacer(Modifier.height(6.dp))
                    Text(if (call.cameraPaused) tr.yourCameraPaused else tr.yourCameraOff, color = CallMuted,
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3)
                }
            }
            if (!call.micOn) {
                Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(24.dp).clip(CircleShape).background(HangUpRed),
                    contentAlignment = Alignment.Center) { AppIcon("mic_off", size = 14.dp, tint = Color.White) }
            }
        }
    }
}

@Composable
private fun Controls(
    call: CallUiState, tr: Strings,
    onMic: () -> Unit, onCamera: () -> Unit, onFlip: () -> Unit, onRoute: (AudioRoute) -> Unit, onHangUp: () -> Unit
) {
    val first = call.peer?.firstName ?: ""
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // small notes above the bar: they are muted, I have no microphone permission
        if (call.phase == CallPhase.CONNECTED && !call.peerMicOn && first.isNotBlank()) Note(tr.peerMuted(first), "mic_off")
        if (!call.hasMic) Note(tr.noMicNote, "mic_off")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(36.dp)).background(CallSurface.copy(alpha = 0.88f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(36.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(if (call.micOn) "mic" else "mic_off", if (call.micOn) tr.callMute else tr.callUnmute,
                active = call.micOn, enabled = call.hasMic, onClick = onMic)
            ControlButton(if (call.cameraOn) "video" else "video_off", if (call.cameraOn) tr.callCameraOff else tr.callCameraOn,
                active = call.cameraOn, enabled = call.hasCamera && !call.cameraPaused, onClick = onCamera)
            if (call.canSwitchCamera) {
                ControlButton("flip_camera", tr.callFlip, active = true, enabled = call.cameraOn, onClick = onFlip)
            }
            RouteButton(call, tr, onRoute)
            // the red one
            Box(
                Modifier.size(width = 64.dp, height = 52.dp).clip(RoundedCornerShape(26.dp)).background(HangUpRed)
                    .clickable(onClick = onHangUp).semantics { contentDescription = tr.callHangUp },
                contentAlignment = Alignment.Center
            ) { AppIcon("hangup", size = 26.dp, tint = Color.White) }
        }
    }
}

@Composable
private fun ControlButton(icon: String, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Color.White.copy(alpha = 0.12f) else Color.White, tween(180), label = "ctrl")
    val fg by animateColorAsState(if (active) CallText else CallBg, tween(180), label = "ctrlFg")
    Box(
        Modifier.size(52.dp).graphicsLayer { alpha = if (enabled) 1f else 0.35f }.clip(CircleShape).background(bg)
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { AppIcon(icon, size = 24.dp, tint = fg) }
}

@Composable
private fun RouteButton(call: CallUiState, tr: Strings, onRoute: (AudioRoute) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val icon = when (call.audioRoute) {
        AudioRoute.SPEAKER -> "speaker"; AudioRoute.EARPIECE -> "earpiece"
        AudioRoute.HEADSET -> "headset"; AudioRoute.BLUETOOTH -> "bluetooth"
    }
    Box {
        ControlButton(icon, "${tr.callAudio}: ${tr.audioRoute(call.audioRoute.name)}", active = true,
            enabled = call.audioRoutes.size > 1) {
            // two choices: just flip; more (headphones / Bluetooth): a small menu
            if (call.audioRoutes.size == 2) onRoute(call.audioRoutes.first { it != call.audioRoute }) else open = true
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = CallSurface) {
            call.audioRoutes.forEach { r ->
                DropdownMenuItem(
                    text = { Text(tr.audioRoute(r.name), color = if (r == call.audioRoute) GoldPrimary else CallText) },
                    onClick = { open = false; onRoute(r) }
                )
            }
        }
    }
}

@Composable
private fun Note(text: String, icon: String) {
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon, size = 14.dp, tint = CallText)
        Spacer(Modifier.width(6.dp))
        Text(text, color = CallText, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------------------------------------------------------------- end states

@Composable
private fun EndedCard(call: CallUiState, tr: Strings, onRetry: () -> Unit, onBack: () -> Unit) {
    val failed = call.phase == CallPhase.FAILED
    val reason = call.endReason
    val canRetry = failed || reason in setOf(EndReason.LEFT, EndReason.EMPTY, EndReason.ENDED_BY_USER, EndReason.EXPIRED)
    val text = when {
        failed -> call.errorDetail?.takeIf { call.error == CallError.SERVER } ?: tr.callErrorText(call.error?.name ?: "")
        reason == EndReason.UNKNOWN && call.errorDetail != null -> call.errorDetail ?: ""
        else -> tr.callEndReason(reason?.wire ?: "")
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background((if (failed) HangUpRed else GoldPrimary).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center) {
            AppIcon(if (failed) "signal_off" else "check_circle", size = 42.dp, tint = if (failed) HangUpRed else GoldPrimary)
        }
        Spacer(Modifier.height(20.dp))
        Text(if (failed) tr.callFailedTitle else tr.callEndedTitle, style = MaterialTheme.typography.headlineSmall, color = CallText)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CallMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        if (canRetry) {
            GoldButton(if (failed) tr.callRetry else tr.joinLesson, onRetry)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedCallButton(tr.callBack, onBack)
    }
}

@Composable
private fun BusyCard(tr: Strings, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr.callErrorText("BUSY"), color = CallText, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        OutlinedCallButton(tr.callBack, onBack)
    }
}

@Composable
private fun CenterStatus(text: String, peerName: String, skill: String, spinner: Boolean) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (spinner) CircularProgressIndicator(color = GoldPrimary, strokeWidth = 2.5.dp)
        Spacer(Modifier.height(18.dp))
        Text(text, color = CallText, style = MaterialTheme.typography.titleMedium)
        if (peerName.isNotBlank()) Text("$peerName · $skill", color = CallMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = OnGold),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun OutlinedCallButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(label, color = CallText, style = MaterialTheme.typography.labelLarge) }
}
