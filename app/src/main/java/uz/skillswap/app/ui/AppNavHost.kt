package uz.skillswap.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import uz.skillswap.app.ui.screens.DetailScreen
import uz.skillswap.app.ui.screens.HomeScreen
import uz.skillswap.app.ui.screens.LessonsScreen
import uz.skillswap.app.ui.screens.LoginScreen
import uz.skillswap.app.ui.screens.ProfileScreen
import uz.skillswap.app.ui.screens.RegisterScreen
import uz.skillswap.app.ui.screens.TeachScreen

/** All screen addresses ("routes") in one place */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val LESSONS = "lessons"
    const val TEACH = "teach"
    const val PROFILE = "profile"
    const val DETAIL = "detail/{offerId}"
    fun detail(id: Long) = "detail/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab(Routes.HOME, "Browse", Icons.Filled.Home),
    Tab(Routes.LESSONS, "Lessons", Icons.Filled.DateRange),
    Tab(Routes.TEACH, "Teach", Icons.Filled.Add),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person)
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

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),   // each screen handles its own insets
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                nav.navigateToTab(tab.route)
                            },
                            label = { Text(tab.label) },
                            icon = { Icon(tab.icon, contentDescription = null) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Short, calm transitions: a fade plus a very small horizontal slide (about 250 ms)
        NavHost(
            nav, startDestination = start, modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 12 } },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 12 } }
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
                HomeScreen(vm, onOfferClick = { id -> nav.navigate(Routes.detail(id)) })
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
                })
            }
        }
    }
}

/** Standard bottom-bar navigation: keep one copy of each tab, restore its state */
private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
