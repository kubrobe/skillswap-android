package uz.skillswap.app.ui.screens

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.skillswap.app.data.remote.Galaxy
import uz.skillswap.app.data.remote.GalaxyNode
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.components.IconTile
import uz.skillswap.app.ui.components.SkillIcons
import uz.skillswap.app.ui.components.categoryColor
import uz.skillswap.app.ui.i18n.LocalStrings
import uz.skillswap.app.ui.i18n.Strings
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.Lavender
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.graphics.Canvas as BitmapCanvas

private val SpaceDeep = Color(0xFF0A0B10)
private val SpaceGlow = Color(0xFF221D12)

// Layout sizes, in dp at zoom 1.
private const val SLOT = 76f            // room for one star and its name along an orbit
private const val FIRST_ORBIT = 110f    // radius of the inner orbit
private const val ORBIT_GAP = 84f       // distance between two orbits
private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * Skill Galaxy, drawn as a solar system: you are the sun in the middle, the people you swapped
 * lessons with circle you on the inner orbit, everyone else on the outer orbits (near the people they
 * swap with). Lines are lessons, gold sparks flow from teacher to learner, the orbits turn slowly.
 * A new lesson anywhere fires a comet along its line, live. Pinch to zoom, tap a star for details.
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
                Icon(SkillIcons.named("signal_off"), contentDescription = null, tint = Lavender, modifier = Modifier.size(52.dp))
                Spacer(Modifier.size(12.dp))
                Text(error ?: "", color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { vm.loadGalaxy() }) { Text(tr.tryAgain, color = GoldPrimary) }
            }
            g != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(SkillIcons.named("orbit"), contentDescription = null, tint = Lavender, modifier = Modifier.size(52.dp))
                Spacer(Modifier.size(12.dp))
                Text(tr.galaxyEmpty, color = Color.White)
            }
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = GoldPrimary)
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp)) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr.back, tint = Color.White)
                }
                Column {
                    Text(tr.galaxyTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(tr.galaxyStats(g?.nodes?.size ?: 0, g?.edges?.sumOf { it.lessons } ?: 0),
                        style = MaterialTheme.typography.labelMedium, color = Lavender)
                }
            }
            if (g != null && g.nodes.isNotEmpty()) Legend(g, tr)
        }
    }
}

/** The colours of the categories in this galaxy, most common first. */
@Composable
private fun Legend(g: Galaxy, tr: Strings) {
    val categories = remember(g) {
        g.nodes.map { it.category }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (c in categories) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(c)))
                Text(tr.categoryName(c), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 5.dp))
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
            for (i in 0 until 150) drawCircle(Color.White, rs[i], Offset(xs[i], ys[i]), alpha = alphas[i])
        }
    })
}

/** A lesson that just happened: a comet flies along its line, then the learner's star pulses. */
private class Comet(val edge: Int, val start: Long)

/** How far the orbits have turned (seconds of turning; stops while a star is selected). */
private class Spin {
    var seconds = 0f
}

/** Angle an orbit has turned after [seconds]: inner orbits a little faster, neighbours opposite ways. */
private fun turned(orbit: Int, seconds: Float): Float =
    if (orbit <= 0) 0f else (if (orbit % 2 == 1) 1f else -1f) * seconds * TWO_PI / (150f + 60f * orbit)

/** Shortest turn from one angle to another, in -PI..PI. */
private fun wrap(a: Float): Float {
    var v = a % TWO_PI
    if (v > PI) v -= TWO_PI
    if (v < -PI) v += TWO_PI
    return v
}

/** Grows past 1 a little and settles back: a soft "pop". */
private fun backOut(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    val c1 = 1.70158f
    val y = x - 1f
    return 1f + (c1 + 1f) * y * y * y + c1 * y * y
}

/** A point on the curved lesson line: quadratic Bézier from p0 over c to p1. */
private fun bez(t: Float, p0: Float, c: Float, p1: Float): Float {
    val u = 1f - t
    return u * u * p0 + 2f * u * t * c + t * t * p1
}

/**
 * Where every star sits. Computed once per download, no physics, so it costs nothing per frame and
 * stars can never overlap: each orbit only takes as many stars as fit around it ([SLOT] apart).
 *  - the centre is me;
 *  - then the people I swapped with (most lessons first), then everyone else (most active first),
 *    filling the orbits from the inside out;
 *  - on the inner orbit each next star is the one with the most lessons with the previous one, so
 *    people who swap with each other sit side by side (short lines, few crossings);
 *  - on outer orbits a star sits in the direction of the people it swaps with.
 * When new data arrives, stars glide from their old place to the new one.
 */
private class Orbits(g: Galaxy, previous: Orbits?, measurer: TextMeasurer, tr: Strings) {
    val nodes: List<GalaxyNode> = g.nodes
    val n = nodes.size
    val index = HashMap<Long, Int>()
    val edgeA: IntArray
    val edgeB: IntArray
    val edgeW: IntArray
    val edgeKey: Array<String>
    val phase: FloatArray
    val orbit = IntArray(n)                 // 0 = centre, 1 = inner orbit, ...
    val dist = FloatArray(n)                // radius of the star's orbit, dp
    val angle = FloatArray(n)               // place on the orbit before turning, radians
    val size = FloatArray(n)                // star radius, dp
    val orbits: FloatArray                  // radius of every orbit (faint circles)
    val maxOrbit: Float
    val centre: Int
    val colors: Array<Color>
    val iconIds: List<String>
    val labels: List<TextLayoutResult>
    val fresh = previous == null            // first picture: stars fly out from the middle
    val fromOrbit = IntArray(n) { -1 }      // where each star was before this download (-1 = new)
    val fromDist = FloatArray(n)
    val fromAngle = FloatArray(n)
    var start = 0L                          // first frame with this layout
    // filled every frame: where each star is on screen (taps use these)
    val sx = FloatArray(n)
    val sy = FloatArray(n)
    val sr = FloatArray(n)
    val grow = FloatArray(n)
    // neighbours of the selected star
    val lit = BooleanArray(n)
    private var litFor = -2
    val path = Path()
    private var strokeDensity = 0f
    private var strokes: Array<Stroke> = emptyArray()
    private var hotStrokes: Array<Stroke> = emptyArray()

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

        // lessons between two people, both directions together
        val link = Array(n) { HashMap<Int, Int>() }
        for (e in es.indices) {
            val a = edgeA[e]
            val b = edgeB[e]
            link[a][b] = (link[a][b] ?: 0) + edgeW[e]
            link[b][a] = (link[b][a] ?: 0) + edgeW[e]
        }
        val activity = IntArray(n) { nodes[it].taught + nodes[it].learned }
        centre = index[g.me] ?: (0 until n).maxByOrNull { activity[it] } ?: 0
        for (i in 0 until n) {
            size[i] = if (i == centre) 28f else min(24f, 12f + 2.4f * sqrt(activity[i].toFloat()))
        }

        val others = (0 until n).filter { it != centre }
        val direct = others.filter { (link[centre][it] ?: 0) > 0 }
            .sortedWith(compareByDescending<Int> { link[centre][it] ?: 0 }.thenBy { it })
        val rest = others.filter { (link[centre][it] ?: 0) == 0 }
            .sortedWith(compareByDescending<Int> { activity[it] }.thenBy { it })
        val queue = direct + rest
        val placed = BooleanArray(n)
        if (n > 0) placed[centre] = true
        val radii = ArrayList<Float>()
        var r = FIRST_ORBIT
        var pos = 0
        var k = 0
        while (pos < queue.size) {
            val fits = max(6, floor(TWO_PI * r / SLOT).toInt())
            val members = queue.subList(pos, min(queue.size, pos + fits))
            pos += members.size
            k += 1
            val wish = HashMap<Int, Float>()        // the direction a star would like to sit in
            val order: List<Int>
            if (k == 1) {
                val left = members.toMutableList()
                val chain = ArrayList<Int>()
                chain.add(left.removeAt(0))
                while (left.isNotEmpty()) {
                    val last = chain[chain.size - 1]
                    var best = 0
                    for (q in 1 until left.size) {
                        if ((link[last][left[q]] ?: 0) > (link[last][left[best]] ?: 0)) best = q
                    }
                    chain.add(left.removeAt(best))
                }
                order = chain
            } else {
                for (m in members) {
                    var x = 0f
                    var y = 0f
                    for ((j, lessons) in link[m]) {
                        if (placed[j] && j != centre) {
                            x += lessons * cos(angle[j])
                            y += lessons * sin(angle[j])
                        }
                    }
                    if (x != 0f || y != 0f) wish[m] = atan2(y, x)
                }
                order = members.sortedWith(compareBy<Int> { if (wish[it] == null) 1 else 0 }.thenBy { wish[it] ?: 0f })
            }
            val step = TWO_PI / order.size
            // turn the whole orbit so the stars land as close as possible to where they wish to be
            var offset = (-PI / 2).toFloat()
            var ox = 0f
            var oy = 0f
            order.forEachIndexed { q, m ->
                val w = wish[m]
                if (w != null) {
                    ox += cos(w - q * step)
                    oy += sin(w - q * step)
                }
            }
            if (ox != 0f || oy != 0f) offset = atan2(oy, ox)
            order.forEachIndexed { q, m ->
                orbit[m] = k
                dist[m] = r
                angle[m] = offset + q * step
                placed[m] = true
            }
            radii.add(r)
            r += ORBIT_GAP
        }
        orbits = radii.toFloatArray()
        maxOrbit = if (radii.isEmpty()) 0f else radii[radii.size - 1]

        iconIds = nodes.map { SkillIcons.idForEmoji(it.emoji) }
        colors = Array(n) { categoryColor(nodes[it].category.ifBlank { SkillIcons.categoryOf(iconIds[it]) }) }
        labels = nodes.mapIndexed { i, node ->
            val me = i == centre
            measurer.measure(if (me) tr.you else node.name,
                TextStyle(color = if (me) GoldPrimary else Color.White.copy(alpha = 0.88f), fontSize = 11.sp,
                    fontWeight = if (me) FontWeight.Bold else FontWeight.Medium))
        }
        if (previous != null) {
            for (i in 0 until n) {
                val old = previous.index[nodes[i].id] ?: continue
                fromOrbit[i] = previous.orbit[old]
                fromDist[i] = previous.dist[old]
                fromAngle[i] = previous.angle[old]
            }
        }
    }

    /** Marks the stars linked to [sel] (recomputed only when the selection changes). */
    fun light(sel: Int) {
        if (sel == litFor) return
        litFor = sel
        java.util.Arrays.fill(lit, false)
        if (sel < 0) return
        for (e in edgeA.indices) {
            if (edgeA[e] == sel) lit[edgeB[e]] = true
            if (edgeB[e] == sel) lit[edgeA[e]] = true
        }
    }

    /** Line strokes, made once per screen density instead of every frame. */
    fun stroke(e: Int, hot: Boolean, density: Float): Stroke {
        if (strokeDensity != density) {
            strokeDensity = density
            strokes = Array(edgeW.size) { Stroke((1f + 0.5f * ln(1f + edgeW[it])) * density) }
            hotStrokes = Array(edgeW.size) { Stroke((1f + 0.5f * ln(1f + edgeW[it])) * density * 1.7f) }
        }
        return if (hot) hotStrokes[e] else strokes[e]
    }
}

/**
 * Our skill icon as a small white picture, drawn once and reused: stretching a ready picture is far
 * cheaper than drawing the vector again for every star, every frame (that was the old lag).
 */
private fun iconBitmap(cache: HashMap<String, ImageBitmap>, id: String, painter: Painter?, density: Float): ImageBitmap? {
    cache[id]?.let { return it }
    if (painter == null) return null
    val px = 96
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, BitmapCanvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        with(painter) { draw(size, colorFilter = ColorFilter.tint(Color.White)) }
    }
    cache[id] = bitmap
    return bitmap
}

@Composable
private fun GalaxyView(g: Galaxy, tr: Strings) {
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    var layout by remember { mutableStateOf<Orbits?>(null) }
    val comets = remember { mutableStateListOf<Comet>() }
    var clock by remember { mutableLongStateOf(0L) }
    val spin = remember { Spin() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var cardNode by remember { mutableStateOf<GalaxyNode?>(null) }
    val painters = SkillIcons.skillIds.associateWith { id -> rememberVectorPainter(SkillIcons.named(id)) }
    val icons = remember { HashMap<String, ImageBitmap>() }

    // New data: stars glide to their new places; a comet flies along every new or busier lesson line
    LaunchedEffect(g, tr) {
        val old = layout
        val next = Orbits(g, old, measurer, tr)
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

    // One clock for the whole picture. Only the Canvas reads it, so a frame = one redraw, no recomposition.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                if (last != 0L && selected == null) spin.seconds += (t - last) / 1000f
                last = t
                clock = t
                if (comets.isNotEmpty()) comets.removeAll { t - it.start > 2600L }
                Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize()
                .graphicsLayer { }                   // its own layer: redrawing it every frame leaves the rest alone
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, drag, pinch, _ ->
                        // zoom around the fingers, not around the middle of the screen
                        val before = zoom
                        val after = (before * pinch).coerceIn(0.6f, 4f)
                        val middle = Offset(size.width / 2f, size.height / 2f) + pan
                        pan = pan + (centroid - middle) * (1f - after / before) + drag
                        zoom = after
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = 1f
                            pan = Offset.Zero
                        },
                        onTap = { p ->
                            val lay = layout
                            var best = -1
                            var bestD = Float.MAX_VALUE
                            if (lay != null) {
                                for (i in 0 until lay.n) {
                                    val d = hypot(p.x - lay.sx[i], p.y - lay.sy[i])
                                    if (d < lay.sr[i] + 14.dp.toPx() && d < bestD) {
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
            val t = clock
            val lay = layout ?: return@Canvas
            if (lay.n == 0) return@Canvas
            if (lay.start == 0L) lay.start = t
            val age = (t - lay.start).toFloat()
            val dp = density

            // fit all orbits on screen, then apply the user's pinch and drag
            val avail = min(size.width / dp / 2f, size.height / dp * 0.36f) - 14f
            val fit = if (lay.maxOrbit <= 0f) 1.2f else (avail / (lay.maxOrbit + 30f)).coerceIn(0.5f, 1.25f)
            val u = fit * zoom * dp                          // pixels per layout dp
            val ox = size.width / 2f + pan.x
            val oy = size.height / 2f + pan.y
            val sel = selected?.let { lay.index[it] } ?: -1
            lay.light(sel)
            val turn = spin.seconds

            // where every star is in this frame
            for (i in 0 until lay.n) {
                var d = lay.dist[i]
                var a = lay.angle[i] + turned(lay.orbit[i], turn)
                var grow = 1f
                if (lay.fresh) {
                    grow = backOut((age - 90f * lay.orbit[i]) / 700f)    // inner orbit first
                    d *= grow
                } else if (age < 700f) {
                    val m = FastOutSlowInEasing.transform(age / 700f)
                    if (lay.fromOrbit[i] >= 0) {
                        val a0 = lay.fromAngle[i] + turned(lay.fromOrbit[i], turn)
                        a = a0 + wrap(a - a0) * m
                        d = lay.fromDist[i] + (d - lay.fromDist[i]) * m
                    } else {
                        grow = backOut(age / 700f)
                    }
                }
                lay.sx[i] = ox + cos(a) * d * u
                lay.sy[i] = oy + sin(a) * d * u
                lay.grow[i] = grow
                lay.sr[i] = lay.size[i] * u * max(grow, 0f)
            }

            // faint orbit circles
            val orbitAlpha = 0.07f * (if (lay.fresh) (age / 900f).coerceIn(0f, 1f) else 1f)
            val thin = Stroke(1f * dp)
            for (r in lay.orbits) drawCircle(Color.White, r * u, Offset(ox, oy), alpha = orbitAlpha, style = thin)

            // lesson lines, bent toward the middle, with sparks flowing from teacher to learner
            val path = lay.path
            for (e in lay.edgeA.indices) {
                val a = lay.edgeA[e]
                val b = lay.edgeB[e]
                val show = min(lay.grow[a], lay.grow[b]).coerceIn(0f, 1f)
                if (show <= 0f) continue
                val hot = sel >= 0 && (a == sel || b == sel)
                val mine = a == lay.centre || b == lay.centre
                val alpha = show * when {
                    hot -> 0.9f
                    sel >= 0 -> 0.05f
                    mine -> 0.34f
                    else -> 0.10f
                }
                val x0 = lay.sx[a]
                val y0 = lay.sy[a]
                val x1 = lay.sx[b]
                val y1 = lay.sy[b]
                val qx = ox + ((x0 + x1) / 2f - ox) * 0.55f
                val qy = oy + ((y0 + y1) / 2f - oy) * 0.55f
                path.reset()
                path.moveTo(x0, y0)
                path.quadraticTo(qx, qy, x1, y1)
                val color = if (hot) lay.colors[if (a == sel) b else a] else GoldPrimary
                drawPath(path, color, alpha = alpha, style = lay.stroke(e, hot, dp))
                if (hot || (mine && sel < 0)) {
                    val sparks = min(lay.edgeW[e], if (hot) 3 else 2)
                    for (k in 0 until sparks) {
                        val tt = ((t % 2600L) / 2600f + k.toFloat() / sparks + lay.phase[e]) % 1f
                        drawCircle(if (hot) Color.White else Lavender, 2.2f * dp,
                            Offset(bez(tt, x0, qx, x1), bez(tt, y0, qy, y1)), alpha = 0.9f * sin(PI.toFloat() * tt) * show)
                    }
                }
            }

            // stars
            val allNames = SLOT * fit * zoom >= 58f          // names only when there is room for them
            val ring = Stroke(2.2f * dp)
            for (i in 0 until lay.n) {
                val r = lay.sr[i]
                if (r < 0.5f) continue
                val c = Offset(lay.sx[i], lay.sy[i])
                val dim = if (sel < 0 || i == sel || lay.lit[i]) 1f else 0.28f
                val col = lay.colors[i]
                drawCircle(col, r * 1.6f, c, alpha = 0.15f * dim)
                drawCircle(col, r, c, alpha = dim)
                drawCircle(Color.White, r * 0.45f, Offset(c.x - r * 0.28f, c.y - r * 0.28f), alpha = 0.2f * dim)
                if (i == lay.centre) {
                    drawCircle(GoldPrimary, r + 5f * dp, c, style = ring)
                    val p = (t % 2400L) / 2400f                  // a slow pulse around me
                    drawCircle(GoldPrimary, r + 5f * dp + p * 26f * dp, c, alpha = 0.5f * (1f - p), style = thin)
                }
                if (i == sel) drawCircle(Color.White, r + 7f * dp, c, style = Stroke(1.5f * dp))
                if (r >= 8f * dp) {
                    val bitmap = iconBitmap(icons, lay.iconIds[i], painters[lay.iconIds[i]], dp)
                    if (bitmap != null) {
                        val side = r * 1.1f
                        translate(c.x - side / 2f, c.y - side / 2f) {
                            drawImage(bitmap, dstSize = IntSize(side.toInt().coerceAtLeast(1), side.toInt().coerceAtLeast(1)),
                                alpha = dim, filterQuality = FilterQuality.Low)
                        }
                    }
                }
                if ((allNames || i == lay.centre || i == sel || lay.lit[i]) && lay.grow[i] > 0.5f) {
                    val lb = lay.labels[i]
                    val gap = when (i) {                        // clear of the gold / white ring
                        sel -> 10f
                        lay.centre -> 9f
                        else -> 4f
                    } * dp
                    drawText(lb, topLeft = Offset(c.x - lb.size.width / 2f, c.y + r + gap), alpha = dim)
                }
            }

            // comets for lessons that just happened, then a pulse on the learner's star
            for (cm in comets) {
                if (cm.edge >= lay.edgeA.size) continue
                val a = lay.edgeA[cm.edge]
                val b = lay.edgeB[cm.edge]
                val x0 = lay.sx[a]
                val y0 = lay.sy[a]
                val x1 = lay.sx[b]
                val y1 = lay.sy[b]
                val qx = ox + ((x0 + x1) / 2f - ox) * 0.55f
                val qy = oy + ((y0 + y1) / 2f - oy) * 0.55f
                val cAge = t - cm.start
                if (cAge < 1800L) {
                    val p = FastOutSlowInEasing.transform(cAge / 1800f)
                    for (k in 4 downTo 0) {
                        val tt = (p - k * 0.035f).coerceIn(0f, 1f)
                        drawCircle(GoldPrimary, (6f - k) * dp, Offset(bez(tt, x0, qx, x1), bez(tt, y0, qy, y1)),
                            alpha = 0.95f - k * 0.18f)
                    }
                    drawCircle(GoldPrimary, 15f * dp, Offset(bez(p, x0, qx, x1), bez(p, y0, qy, y1)), alpha = 0.25f)
                } else {
                    val p = ((cAge - 1800L) / 800f).coerceIn(0f, 1f)
                    drawCircle(GoldPrimary, lay.sr[b] + p * 36f * dp, Offset(x1, y1), alpha = 1f - p, style = Stroke(3f * dp))
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
                Text(tr.categoryName(node.category), style = MaterialTheme.typography.bodySmall, color = Lavender)
            }
            Text("${tr.taughtLearned(node.taught, node.learned)} · ${tr.connections(links)}",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}
