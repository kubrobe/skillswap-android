package uz.skillswap.app.ui.screens

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import uz.skillswap.app.ui.components.IconTile
import uz.skillswap.app.ui.components.SkillIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.data.remote.Galaxy
import uz.skillswap.app.data.remote.GalaxyNode
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.categoryColor
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.i18n.Strings
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.Lavender
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val SpaceDeep = Color(0xFF0A0B10)
private val SpaceGlow = Color(0xFF221D12)

/**
 * Skill Galaxy: every person is a star, every lesson a line from teacher to learner, and gold sparks
 * flow along the lines (knowledge moving). A new lesson anywhere fires a comet along its line, live.
 * Pinch to zoom, drag to move, tap a star for details, double-tap to reset.
 */
@Composable
fun GalaxyScreen(vm: SkillSwapViewModel, onBack: () -> Unit) {
    val galaxy by vm.galaxy.collectAsStateWithLifecycle()
    val error by vm.galaxyError.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val tr = LocalStrings.current
    LaunchedEffect(sync.lastSync) { vm.loadGalaxy() }       // first open + after every live update

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(SpaceGlow, SpaceDeep)))) {
        StarField()
        val g = galaxy
        when {
            g != null && g.nodes.isNotEmpty() -> GalaxyView(g, tr)
            g == null && error != null -> Column(Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📡", fontSize = 44.sp)
                Text(error ?: "", color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { vm.loadGalaxy() }) { Text(tr.tryAgain, color = GoldPrimary) }
            }
            g != null -> Text("🌌  " + tr.galaxyEmpty, color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = GoldPrimary)
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr.back, tint = Color.White)
            }
            Column {
                Text(tr.galaxyTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(tr.galaxyStats(g?.nodes?.size ?: 0, g?.edges?.sumOf { it.lessons } ?: 0),
                    style = MaterialTheme.typography.labelMedium, color = Lavender)
            }
        }
    }
}

/** A still star field, drawn once and cached. */
@Composable
private fun StarField() {
    Spacer(Modifier.fillMaxSize().drawWithCache {
        val rnd = java.util.Random(7)
        val xs = FloatArray(150) { rnd.nextFloat() * size.width }
        val ys = FloatArray(150) { rnd.nextFloat() * size.height }
        val rs = FloatArray(150) { (0.4f + rnd.nextFloat() * 1.1f) * density }
        val alphas = FloatArray(150) { 0.12f + rnd.nextFloat() * 0.45f }
        onDrawBehind {
            for (i in 0 until 150) drawCircle(Color.White.copy(alpha = alphas[i]), rs[i], Offset(xs[i], ys[i]))
        }
    })
}

/** A lesson that just happened: a comet flies along its line, then the learner's star pulses. */
private class Comet(val edge: Int, val start: Long)

/** The last screen transform, so a tap can find the star under the finger. */
private class ViewXform {
    var s = 1f
    var cx = 0f
    var cy = 0f
    var ox = 0f
    var oy = 0f
}

/**
 * Star positions from a small force simulation (Fruchterman–Reingold): stars push each other away,
 * lesson lines pull teacher and learner together, a weak gravity keeps loners near the middle.
 * A new download keeps the old positions, so the picture never jumps.
 */
private class Layout(g: Galaxy, previous: Layout?, measurer: TextMeasurer, tr: Strings) {
    val nodes: List<GalaxyNode> = g.nodes
    val n = nodes.size
    val index = HashMap<Long, Int>()
    val x = FloatArray(n)
    val y = FloatArray(n)
    val radius = FloatArray(n)
    private val fx = FloatArray(n)
    private val fy = FloatArray(n)
    val edgeA: IntArray
    val edgeB: IntArray
    val edgeW: IntArray
    val edgeKey: Array<String>
    val phase: FloatArray
    val paths: List<Path>
    val labels: List<TextLayoutResult>
    val iconIds: List<String>
    var temperature: Float

    init {
        nodes.forEachIndexed { i, node -> index[node.id] = i }
        val es = g.edges.mapNotNull { e ->
            val a = index[e.from]
            val b = index[e.to]
            if (a == null || b == null || a == b) null else Triple(a, b, e)
        }
        edgeA = IntArray(es.size) { es[it].first }
        edgeB = IntArray(es.size) { es[it].second }
        edgeW = IntArray(es.size) { es[it].third.lessons }
        edgeKey = Array(es.size) { "${es[it].third.from}>${es[it].third.to}" }
        phase = FloatArray(es.size) { (it * 0.618f) % 1f }
        paths = List(es.size) { Path() }
        for (i in 0 until n) {
            val old = previous?.index?.get(nodes[i].id)
            if (previous != null && old != null) {
                x[i] = previous.x[old]
                y[i] = previous.y[old]
            } else {                                   // sunflower spiral: an even, repeatable start
                val r = 28f * sqrt(i + 1f)
                val a = i * 2.39996f
                x[i] = r * cos(a)
                y[i] = r * sin(a)
            }
            radius[i] = 7f + 3.2f * sqrt((nodes[i].taught + nodes[i].learned).toFloat())
        }
        labels = nodes.map { node ->
            val me = node.id == g.me
            measurer.measure(if (me) tr.you else node.name,
                TextStyle(color = if (me) GoldPrimary else Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
                    fontWeight = if (me) FontWeight.Bold else FontWeight.Medium))
        }
        iconIds = nodes.map { node -> SkillIcons.idForEmoji(node.emoji) }
        temperature = if (previous == null) 60f else 10f
    }

    fun step() {
        if (temperature < 0.5f) return
        val k = 64f
        java.util.Arrays.fill(fx, 0f)
        java.util.Arrays.fill(fy, 0f)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var dx = x[i] - x[j]
                var dy = y[i] - y[j]
                var d2 = dx * dx + dy * dy
                if (d2 < 0.01f) {
                    dx = 0.3f * (i - j)
                    dy = 0.3f
                    d2 = dx * dx + dy * dy
                }
                val f = k * k / d2                     // push apart: k² / distance
                fx[i] += dx * f; fy[i] += dy * f
                fx[j] -= dx * f; fy[j] -= dy * f
            }
        }
        for (e in edgeA.indices) {                     // pull together: distance² / k, stronger for more lessons
            val a = edgeA[e]
            val b = edgeB[e]
            val dx = x[a] - x[b]
            val dy = y[a] - y[b]
            val f = sqrt(dx * dx + dy * dy) / k * (1f + 0.5f * ln(1f + edgeW[e]))
            fx[a] -= dx * f; fy[a] -= dy * f
            fx[b] += dx * f; fy[b] += dy * f
        }
        for (i in 0 until n) {
            fx[i] -= x[i] * 0.35f
            fy[i] -= y[i] * 0.35f
            val len = sqrt(fx[i] * fx[i] + fy[i] * fy[i])
            if (len > 0f) {
                val m = min(len, temperature) / len    // move at most "temperature" units per step
                x[i] += fx[i] * m
                y[i] += fy[i] * m
            }
        }
        temperature *= 0.975f                          // cool down: the picture settles in about 2 seconds
    }
}

/** A point on the curved lesson line: quadratic Bézier from p0 over c to p1. */
private fun bez(t: Float, p0: Float, c: Float, p1: Float): Float {
    val u = 1f - t
    return u * u * p0 + 2f * u * t * c + t * t * p1
}

@Composable
private fun GalaxyView(g: Galaxy, tr: Strings) {
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    var layout by remember { mutableStateOf<Layout?>(null) }
    val comets = remember { mutableStateListOf<Comet>() }
    var clock by remember { mutableLongStateOf(0L) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var cardNode by remember { mutableStateOf<GalaxyNode?>(null) }
    val view = remember { ViewXform() }
    // our skill icons, drawn white inside the stars
    val painters = SkillIcons.ids.associateWith { id -> rememberVectorPainter(SkillIcons.named(id)) }
    val white = remember { ColorFilter.tint(Color.White) }

    // New data: keep the stars where they are and fire a comet along every new or busier lesson line
    LaunchedEffect(g, tr) {
        val old = layout
        val next = Layout(g, old, measurer, tr)
        comets.clear()
        if (old != null) {
            val before = HashMap<String, Int>()
            for (e in old.edgeKey.indices) before[old.edgeKey[e]] = old.edgeW[e]
            for (e in next.edgeKey.indices) {
                if (next.edgeW[e] > (before[next.edgeKey[e]] ?: 0)) comets.add(Comet(e, clock))
            }
            if (comets.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        layout = next
    }

    // One clock for the whole picture: the simulation steps and the sparks move once per frame
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { t ->
                clock = t
                layout?.let { it.step(); it.step() }
                if (comets.isNotEmpty()) comets.removeAll { t - it.start > 2600L }
                Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, drag, pinch, _ ->
                        zoom = (zoom * pinch).coerceIn(0.5f, 5f)
                        pan += drag
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { zoom = 1f; pan = Offset.Zero },
                        onTap = { p ->
                            val lay = layout
                            var best = -1
                            var bestD = Float.MAX_VALUE
                            if (lay != null) {
                                for (i in 0 until lay.n) {
                                    val sxv = view.ox + (lay.x[i] - view.cx) * view.s
                                    val syv = view.oy + (lay.y[i] - view.cy) * view.s
                                    val d = hypot(p.x - sxv, p.y - syv)
                                    if (d < lay.radius[i] * view.s + 14.dp.toPx() && d < bestD) {
                                        best = i
                                        bestD = d
                                    }
                                }
                            }
                            val node = if (lay != null && best >= 0) lay.nodes[best] else null
                            selected = node?.id
                            if (node != null) {
                                cardNode = node
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
        ) {
            val t = clock                                       // redraw every frame
            val lay = layout ?: return@Canvas
            if (lay.n == 0) return@Canvas

            // fit the whole galaxy on screen, then apply the user's pinch and drag
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until lay.n) {
                minX = min(minX, lay.x[i] - lay.radius[i]); maxX = maxOf(maxX, lay.x[i] + lay.radius[i])
                minY = min(minY, lay.y[i] - lay.radius[i]); maxY = maxOf(maxY, lay.y[i] + lay.radius[i])
            }
            val fit = min(size.width / (maxX - minX + 60f), size.height * 0.68f / (maxY - minY + 60f))
                .coerceAtMost(4f * density)
            view.s = fit * zoom
            view.cx = (minX + maxX) / 2f
            view.cy = (minY + maxY) / 2f
            view.ox = size.width / 2f + pan.x
            view.oy = size.height * 0.52f + pan.y
            val s = view.s

            // lesson lines + sparks flowing from teacher to learner
            for (e in lay.edgeA.indices) {
                val a = lay.edgeA[e]
                val b = lay.edgeB[e]
                val x0 = view.ox + (lay.x[a] - view.cx) * s
                val y0 = view.oy + (lay.y[a] - view.cy) * s
                val x1 = view.ox + (lay.x[b] - view.cx) * s
                val y1 = view.oy + (lay.y[b] - view.cy) * s
                val qx = (x0 + x1) / 2f - (y1 - y0) * 0.18f      // bend the line a little to one side
                val qy = (y0 + y1) / 2f + (x1 - x0) * 0.18f
                val w = lay.edgeW[e]
                val hot = selected != null && (lay.nodes[a].id == selected || lay.nodes[b].id == selected)
                val path = lay.paths[e]
                path.reset()
                path.moveTo(x0, y0)
                path.quadraticBezierTo(qx, qy, x1, y1)
                drawPath(path, GoldPrimary.copy(alpha = if (hot) 0.8f else 0.12f + 0.05f * min(w, 6)),
                    style = Stroke(width = (1f + 0.8f * ln(1f + w)) * density * (if (hot) 1.7f else 1f)))
                val sparks = min(w, 3)
                for (k in 0 until sparks) {
                    val tt = ((t % 3200L) / 3200f + k.toFloat() / sparks + lay.phase[e]) % 1f
                    drawCircle(Lavender.copy(alpha = 0.9f * sin(PI.toFloat() * tt)), 2.2f * density,
                        Offset(bez(tt, x0, qx, x1), bez(tt, y0, qy, y1)))
                }
            }

            // stars
            for (i in 0 until lay.n) {
                val node = lay.nodes[i]
                val c = Offset(view.ox + (lay.x[i] - view.cx) * s, view.oy + (lay.y[i] - view.cy) * s)
                val r = lay.radius[i] * s
                val col = categoryColor(node.category)
                drawCircle(col.copy(alpha = 0.10f), r * 2.6f, c)
                drawCircle(col.copy(alpha = 0.24f), r * 1.7f, c)
                drawCircle(col, r, c)
                drawCircle(Color.White.copy(alpha = 0.35f), r * 0.42f, c + Offset(-r * 0.3f, -r * 0.3f))
                if (node.id == g.me) drawCircle(GoldPrimary, r + 5f * density, c, style = Stroke(2.2f * density))
                if (node.id == selected) drawCircle(Color.White, r + 9f * density, c, style = Stroke(1.5f * density))
                if (r > 11f * density) {
                    val p = painters[lay.iconIds[i]]
                    if (p != null) {
                        val icon = r * 1.15f
                        translate(c.x - icon / 2f, c.y - icon / 2f) {
                            with(p) { draw(Size(icon, icon), colorFilter = white) }
                        }
                    }
                }
                if (node.id == g.me || node.id == selected || node.taught + node.learned >= 3 || zoom > 1.6f) {
                    val lb = lay.labels[i]
                    drawText(lb, topLeft = Offset(c.x - lb.size.width / 2f, c.y + r + 4f * density))
                }
            }

            // comets for lessons that just happened, then a pulse on the learner's star
            for (cm in comets) {
                if (cm.edge >= lay.edgeA.size) continue
                val a = lay.edgeA[cm.edge]
                val b = lay.edgeB[cm.edge]
                val x0 = view.ox + (lay.x[a] - view.cx) * s
                val y0 = view.oy + (lay.y[a] - view.cy) * s
                val x1 = view.ox + (lay.x[b] - view.cx) * s
                val y1 = view.oy + (lay.y[b] - view.cy) * s
                val qx = (x0 + x1) / 2f - (y1 - y0) * 0.18f
                val qy = (y0 + y1) / 2f + (x1 - x0) * 0.18f
                val age = t - cm.start
                if (age < 1800L) {
                    val p = FastOutSlowInEasing.transform(age / 1800f)
                    for (k in 4 downTo 0) {
                        val tt = (p - k * 0.035f).coerceIn(0f, 1f)
                        drawCircle(GoldPrimary.copy(alpha = 0.95f - k * 0.18f), (6f - k) * density,
                            Offset(bez(tt, x0, qx, x1), bez(tt, y0, qy, y1)))
                    }
                    drawCircle(GoldPrimary.copy(alpha = 0.25f), 15f * density, Offset(bez(p, x0, qx, x1), bez(p, y0, qy, y1)))
                } else {
                    val p = ((age - 1800L) / 800f).coerceIn(0f, 1f)
                    drawCircle(GoldPrimary.copy(alpha = 1f - p), lay.radius[b] * s + p * 36f * density,
                        Offset(x1, y1), style = Stroke(3f * density))
                }
            }
        }

        // the tapped star's card, or a hint
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            cardNode?.let { node -> StarCard(node, g, tr) }
        }
        if (selected == null) {
            Text(tr.galaxyHint, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 32.dp, vertical = 20.dp))
        }
    }
}

@Composable
private fun StarCard(node: GalaxyNode, g: Galaxy, tr: Strings) {
    val shape = RoundedCornerShape(24.dp)
    val links = g.edges.count { it.from == node.id || it.to == node.id }
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            .clip(shape).background(Color(0xE61B1D23))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkillIcons.idForEmoji(node.emoji).let { id -> IconTile(id, node.category.ifBlank { SkillIcons.categoryOf(id) }, 48.dp) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(if (node.id == g.me) "${node.name} · ${tr.you}" else node.name,
                style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (node.category.isNotBlank()) {
                Text(node.category, style = MaterialTheme.typography.bodySmall, color = Lavender)
            }
            Text("${tr.taughtLearned(node.taught, node.learned)} · ${tr.connections(links)}",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}
