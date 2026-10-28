package moe.rukamori.archivetune.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix

internal object NavigationTransitions {
    private val DetailRoutePrefixes = listOf(
        "album/",
        "spotify_album/",
        "online_playlist/",
        "local_playlist/",
        "spotify_playlist/",
        "auto_playlist/",
        "cache_playlist/",
        "top_playlist/",
    )

    private val HomeMotionEasing = CubicBezierEasing(0.4f, 0.0f, 0.1f, 1.0f)

    private val HomeForwardExit: ExitTransition =
        scaleOut(
            animationSpec = tween(400, easing = HomeMotionEasing),
            targetScale = 0.92f,
        ) + fadeOut(
            animationSpec = tween(400, easing = HomeMotionEasing),
            targetAlpha = 0.7f,
        )

    private val HomePopEnter: EnterTransition =
        scaleIn(
            animationSpec = tween(450, easing = HomeMotionEasing),
            initialScale = 0.92f,
        ) + fadeIn(
            animationSpec = tween(450, easing = HomeMotionEasing),
            initialAlpha = 0.7f,
        )

    private val DetailForwardEnter: EnterTransition =
        scaleIn(
            animationSpec = spring(
                dampingRatio = 0.88f,
                stiffness = 150f,
            ),
            initialScale = 0.95f,
        ) + fadeIn(
            animationSpec = tween(250),
        )

    private val DetailPopExit: ExitTransition =
        scaleOut(
            animationSpec = tween(200),
            targetScale = 0.95f,
        ) + fadeOut(
            animationSpec = tween(200),
        )

    private fun isDetailRoute(route: String?): Boolean {
        if (route == null) return false
        return DetailRoutePrefixes.any { prefix -> route.startsWith(prefix) }
    }

    private fun isHomeRoute(route: String?): Boolean = route == Screens.Home.route

    fun isHomeToDetailPair(initialRoute: String?, targetRoute: String?): Boolean =
        isHomeRoute(initialRoute) && isDetailRoute(targetRoute)

    fun isDetailToHomePair(initialRoute: String?, targetRoute: String?): Boolean =
        isDetailRoute(initialRoute) && isHomeRoute(targetRoute)

    fun enter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): EnterTransition = with(scope) {
        if (disableAnimations) {
            fadeIn(tween(0))
        } else if (isHomeToDetailPair(initialState.destination.route, targetState.destination.route)) {
            DetailForwardEnter
        } else if (initialState.destination.route in topLevelScreens &&
            targetState.destination.route in topLevelScreens
        ) {
            fadeIn(tween(250))
        } else {
            fadeIn(tween(250)) + slideInHorizontally { it / 2 }
        }
    }

    fun exit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): ExitTransition = with(scope) {
        if (disableAnimations) {
            fadeOut(tween(0))
        } else if (isHomeToDetailPair(initialState.destination.route, targetState.destination.route)) {
            HomeForwardExit
        } else if (initialState.destination.route in topLevelScreens &&
            targetState.destination.route in topLevelScreens
        ) {
            fadeOut(tween(200))
        } else {
            fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
        }
    }

    fun popEnter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): EnterTransition = with(scope) {
        if (disableAnimations) {
            fadeIn(tween(0))
        } else if (isDetailToHomePair(initialState.destination.route, targetState.destination.route)) {
            HomePopEnter
        } else if ((
                initialState.destination.route in topLevelScreens ||
                    initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true
            ) &&
            targetState.destination.route in topLevelScreens
        ) {
            fadeIn(tween(250))
        } else {
            fadeIn(tween(250)) + slideInHorizontally { -it }
        }
    }

    fun popExit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        disableAnimations: Boolean,
        topLevelScreens: List<String>,
    ): ExitTransition = with(scope) {
        if (disableAnimations) {
            fadeOut(tween(0))
        } else if (isDetailToHomePair(initialState.destination.route, targetState.destination.route)) {
            DetailPopExit
        } else if ((
                initialState.destination.route in topLevelScreens ||
                    initialState.destination.route?.startsWith(OnlineSearchResultRoutePrefix) == true
            ) &&
            targetState.destination.route in topLevelScreens
        ) {
            fadeOut(tween(200))
        } else {
            fadeOut(tween(200)) + slideOutHorizontally { it }
        }
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationEnterTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): EnterTransition = NavigationTransitions.enter(this, disableAnimations, topLevelScreens)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationExitTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): ExitTransition = NavigationTransitions.exit(this, disableAnimations, topLevelScreens)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationPopEnterTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): EnterTransition = NavigationTransitions.popEnter(this, disableAnimations, topLevelScreens)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.navigationPopExitTransition(
    disableAnimations: Boolean,
    topLevelScreens: List<String>,
): ExitTransition = NavigationTransitions.popExit(this, disableAnimations, topLevelScreens)
