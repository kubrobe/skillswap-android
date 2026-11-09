package uz.skillswap.app.ui

import uz.skillswap.app.ui.i18n.Strings
import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import uz.skillswap.app.ui.components.GlassBackground
import uz.skillswap.app.ui.components.glassBorder
import uz.skillswap.app.ui.components.glassFill
import uz.skillswap.app.ui.theme.BrandEnd
import uz.skillswap.app.ui.theme.BrandStart
import uz.skillswap.app.ui.theme.GoldDeep
import uz.skillswap.app.ui.theme.GoldPrimary
import uz.skillswap.app.ui.theme.OnGold
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import uz.skillswap.app.ui.components.ConfettiOverlay
import uz.skillswap.app.ui.components.NoticeCard
import uz.skillswap.app.ui.screens.DetailScreen
import uz.skillswap.app.ui.screens.HomeScreen
import uz.skillswap.app.ui.screens.ImpactScreen
import uz.skillswap.app.ui.screens.LessonsScreen
import uz.skillswap.app.ui.screens.LoginScreen
import uz.skillswap.app.ui.screens.ProfileScreen
import uz.skillswap.app.ui.screens.RegisterScreen
import uz.skillswap.app.ui.screens.SwipeScreen
import uz.skillswap.app.ui.screens.TeachScreen
import uz.skillswap.app.ui.components.CoinFlyOverlay

/** All screen addresses ("routes") in one place */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val LESSONS = "lessons"
    const val TEACH = "teach"
    const val PROFILE = "profile"
    const val DETAIL = "detail/{offerId}"
    const val SWIPE = "swipe"
    const val IMPACT = "impact"
    fun detail(id: Long) = "detail/$id"
}

/** [label] picks the tab name from the current language. */
private class Tab(val route: String, val label: (Strings) -> String, val icon: ImageVector)
private val tabs = listOf(
    Tab(Routes.HOME, { it.tabBrowse }, Icons.Filled.Home),
    Tab(Routes.LESSONS, { it.tabLessons }, Icons.Filled.DateRange),
    Tab(Routes.TEACH, { it.tabTeach }, Icons.Filled.Add),
    Tab(Routes.PROFILE, { it.tabProfile }, Icons.Filled.Person)
)

@Composable
fun AppNavHost(vm: SkillSwapViewModel) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    if (!ready) {   // database is being prepared - show a spinner
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val nav = rememberNavController()
    val haptic = LocalHapticFeedback.current
    // decided once: logged-in users go straight to Browse
    val start = remember { if (vm.currentUser.value != null) Routes.HOME else Routes.LOGIN }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val celebrate by vm.celebrate.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val user by vm.currentUser.collectAsStateWithLifecycle()

    // Live messages from the server: show the newest one for 4 seconds
    var noticeText by remember { mutableStateOf("") }
    var noticeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.notices.collect { text ->
            noticeText = text
            noticeVisible = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(noticeText, noticeVisible) {
        if (noticeVisible) {
            delay(4_000)
            noticeVisible = false
        }
    }

    // Everything inside this Box is the "source" the tab bar blurs (background glow + screens).
    val hazeState = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().hazeSource(state = hazeState)) {
    GlassBackground()                    // soft colour glow behind every screen
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)   // each screen handles its own insets
        // no bottomBar slot: the tab bar FLOATS over the content (below), so lists scroll under it
    ) { padding ->
        // iOS-style motion: a detail screen is PUSHED in from the right with a spring while the
        // screen below slides a little to the left (parallax); switching tabs is a soft fade + zoom.
        NavHost(
            nav, startDestination = start, modifier = Modifier.padding(padding),
            enterTransition = {
                if (targetState.destination.route in MODAL) slideInVertically(modalSpring) { it }
                else if (targetState.destination.route in PUSHED) slideInHorizontally(pushSpring) { it }
                else fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)
            },
            exitTransition = {
                if (targetState.destination.route in MODAL) fadeOut(tween(300), targetAlpha = 0.5f)
                else if (targetState.destination.route in PUSHED) slideOutHorizontally(pushSpring) { -it / 4 } + fadeOut(tween(300), targetAlpha = 0.6f)
                else fadeOut(tween(150))
            },
            popEnterTransition = {
                if (initialState.destination.route in MODAL) fadeIn(tween(300), initialAlpha = 0.5f)
                else if (initialState.destination.route in PUSHED) slideInHorizontally(pushSpring) { -it / 4 } + fadeIn(tween(300), initialAlpha = 0.6f)
                else fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)
            },
            popExitTransition = {
                if (initialState.destination.route in MODAL) slideOutVertically(modalSpring) { it }
                else if (initialState.destination.route in PUSHED) slideOutHorizontally(pushSpring) { it }
                else fadeOut(tween(150))
            }
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(vm,
                    onLoggedIn = { nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                    onRegisterClick = { nav.navigate(Routes.REGISTER) })
            }
            composable(Routes.REGISTER) {
                RegisterScreen(vm,
                    onRegistered = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                    onBack = { nav.popBackStack() })
            }
            composable(Routes.HOME) {
                HomeScreen(vm, onOfferClick = { id -> nav.navigate(Routes.detail(id)) },
                    onSwipe = { nav.navigate(Routes.SWIPE) },
                    onImpact = { nav.navigate(Routes.IMPACT) })
            }
            composable(Routes.IMPACT) { ImpactScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.SWIPE) {
                SwipeScreen(vm,
                    onClose = { nav.popBackStack() },
                    // opening a lesson replaces the deck, so "back" from the lesson goes to Browse
                    onOpen = { id -> nav.navigate(Routes.detail(id)) { popUpTo(Routes.SWIPE) { inclusive = true } } })
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("offerId") { type = NavType.LongType })
            ) { entry ->
                DetailScreen(vm, offerId = entry.arguments?.getLong("offerId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onBooked = { nav.navigateToTab(Routes.LESSONS) })
            }
            composable(Routes.LESSONS) { LessonsScreen(vm) }
            composable(Routes.TEACH) { TeachScreen(vm) }
            composable(Routes.PROFILE) {
                ProfileScreen(vm, onLoggedOut = {
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }, onImpact = { nav.navigate(Routes.IMPACT) })
            }
        }
    }

    AnimatedVisibility(
        visible = noticeVisible,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp)
    ) {
        NoticeCard(noticeText)
    }
    }   // end of the blurred source

    // The floating liquid-glass tab bar. It slides away when a lesson is pushed in, like on iOS.
    AnimatedVisibility(
        visible = currentRoute in tabs.map { it.route },
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 500f)) { it } + fadeOut(tween(150)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        GlassTabBar(hazeState, currentRoute) { route ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            nav.navigateToTab(route)
        }
    }

    // SkillSwap is always online: when the server cannot be reached, say so and offer a retry.
    if (user != null && sync.problem != null) {
        NoConnectionScreen(message = sync.problem ?: "", retrying = sync.syncing, onRetry = { vm.refresh() })
    }
    CoinFlyOverlay(vm.coins)
    ConfettiOverlay(celebrate)
    }
}

/** Screens that slide in like an iPhone "push"; everything else fades. */
private val PUSHED = setOf(Routes.DETAIL, Routes.REGISTER, Routes.IMPACT)
/** Screens that slide up from the bottom like an iPhone sheet. */
private val MODAL = setOf(Routes.SWIPE)
private val pushSpring = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val modalSpring = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

/**
 * Floating "liquid glass" tab bar: real blur of the content behind it (Haze), a glossy edge, and a
 * glass lens that springs to the selected tab and stretches while it moves.
 *
 * PERFORMANCE: every animated value is read only inside offset { } / graphicsLayer { } lambdas.
 * Those run in the layout/draw step, so an animation frame never re-composes the bar.
 */
@Composable
private fun GlassTabBar(hazeState: HazeState, currentRoute: String?, onSelect: (String) -> Unit) {
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(34.dp)
    val lensShape = RoundedCornerShape(28.dp)
    val frost = remember(dark) {
        HazeStyle(
            backgroundColor = if (dark) Color(0xFF111317) else Color(0xFFF7F6F2),
            tints = listOf(HazeTint(if (dark) Color(0xFF1B1D23).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.60f))),
            blurRadius = 22.dp,
            noiseFactor = 0.05f,
            fallbackTint = HazeTint(if (dark) Color(0xFF1B1D23).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f))
        )
    }
    val edge = remember(dark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.50f else 0.95f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = if (dark) 0.22f else 0.6f)))
    }
    val gloss = remember(dark) { Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.12f else 0.30f), Color.Transparent)) }
    val lensFill = remember { Brush.linearGradient(listOf(GoldPrimary.copy(alpha = 0.92f), GoldDeep.copy(alpha = 0.85f))) }
    val lensGloss = remember { Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)) }
    val lensEdge = remember { BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f)))) }

    // the lens position as a fraction of the bar: 0 = first tab, 3 = last tab
    val lens = animateFloatAsState(selectedIndex.toFloat(), spring(dampingRatio = 0.7f, stiffness = 340f), label = "lens")

    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(66.dp)
                .shadow(8.dp, shape)
                .clip(shape)
                .hazeEffect(state = hazeState, style = frost)
                .background(gloss)
                .border(BorderStroke(1.2.dp, edge), shape)
        ) {
            val itemWidth = maxWidth / tabs.size
            Box(
                Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    .offset { IntOffset((itemWidth.toPx() * lens.value).roundToInt(), 0) }
                    .graphicsLayer {
                        // the further it still has to go, the more it stretches - like a drop of liquid
                        val stretch = 1f + kotlin.math.abs(selectedIndex - lens.value).coerceAtMost(1f) * 0.45f
                        scaleX = stretch
                        scaleY = 1f / kotlin.math.sqrt(stretch)
                    }
                    .padding(5.dp)
                    .clip(lensShape)
                    .background(lensFill)
                    .background(lensGloss)
                    .border(lensEdge, lensShape)
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, tab ->
                    TabItem(tab, selected = i == selectedIndex, modifier = Modifier.weight(1f)) { onSelect(tab.route) }
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pop = animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.45f, stiffness = 520f), label = "tabIcon")
    val tint = if (selected) OnGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val label = tab.label(LocalStrings.current)
    Column(
        modifier.fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = label, tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                translationY = (1f - pop.value) * 8f          // a small lift while it pops
            })
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** Standard bottom-bar navigation: keep one copy of each tab, restore its state */
private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun NoConnectionScreen(message: String, retrying: Boolean, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📡", fontSize = 56.sp)
            Text(LocalStrings.current.noConnection, style = MaterialTheme.typography.headlineSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry, enabled = !retrying, shape = RoundedCornerShape(16.dp)) {
                if (retrying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(LocalStrings.current.tryAgain)
            }
        }
    }
}

