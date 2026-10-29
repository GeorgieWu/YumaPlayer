package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.navigation.NavBackStackEntry
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.resetHeightOffset

@Stable
interface HomeScrollConnection : NestedScrollConnection {
    var isHeroPresent: Boolean
    var heroScrollProgress: () -> Float
}

@OptIn(ExperimentalMaterial3Api::class)
@Stable
class HomeAppBarScrollBehavior(
    private val delegate: TopAppBarScrollBehavior,
) : TopAppBarScrollBehavior by delegate {
    var isHeroPresent by mutableStateOf(false)
    var heroScrollProgress by mutableStateOf<() -> Float>({ 0f })

    override val nestedScrollConnection: NestedScrollConnection =
        object : HomeScrollConnection, NestedScrollConnection by delegate.nestedScrollConnection {
            override var isHeroPresent: Boolean
                get() = this@HomeAppBarScrollBehavior.isHeroPresent
                set(value) {
                    this@HomeAppBarScrollBehavior.isHeroPresent = value
                }

            override var heroScrollProgress: () -> Float
                get() = this@HomeAppBarScrollBehavior.heroScrollProgress
                set(value) {
                    this@HomeAppBarScrollBehavior.heroScrollProgress = value
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Stable
class ScaffoldScrollController(
    val homeScrollBehavior: TopAppBarScrollBehavior,
    val searchScrollBehavior: TopAppBarScrollBehavior,
    val topAppBarScrollBehavior: TopAppBarScrollBehavior,
) {
    fun currentScrollBehavior(route: String?): TopAppBarScrollBehavior =
        when (route) {
            Screens.Home.route -> homeScrollBehavior
            Screens.Search.route -> searchScrollBehavior
            else -> topAppBarScrollBehavior
        }

    suspend fun resetHomeOffset() {
        homeScrollBehavior.state.resetHeightOffset()
    }

    suspend fun resetSearchOffset() {
        searchScrollBehavior.state.resetHeightOffset()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberScaffoldScrollController(
    navBackStackEntry: NavBackStackEntry?,
    playerBottomSheetState: BottomSheetState,
    topLevelScreens: List<String>,
    active: Boolean = false,
): ScaffoldScrollController {
    val rawHomeScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )
    val homeScrollBehavior = remember(rawHomeScrollBehavior) {
        HomeAppBarScrollBehavior(rawHomeScrollBehavior)
    }
    val searchScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )
    val topAppBarScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )

    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screens.Home.route -> {
                homeScrollBehavior.state.resetHeightOffset()
            }

            Screens.Search.route -> {
                searchScrollBehavior.state.resetHeightOffset()
            }

            else -> {}
        }
    }

    LaunchedEffect(navBackStackEntry) {
        val currentEntryRoute = navBackStackEntry?.destination?.route

        val isEnteringSubScreen =
            currentEntryRoute != null &&
                currentEntryRoute !in topLevelScreens &&
                currentEntryRoute.startsWith(OnlineSearchResultRoutePrefix) != true
        if (isEnteringSubScreen) {
            topAppBarScrollBehavior.state.heightOffset = 0f
            topAppBarScrollBehavior.state.contentOffset = 0f
        }
    }

    LaunchedEffect(active) {
        if (active) {
            when (currentRoute) {
                Screens.Home.route -> {
                    homeScrollBehavior.state.resetHeightOffset()
                }

                Screens.Search.route -> {
                    searchScrollBehavior.state.resetHeightOffset()
                }

                else -> {}
            }
        }
    }

    return remember(homeScrollBehavior, searchScrollBehavior, topAppBarScrollBehavior) {
        ScaffoldScrollController(
            homeScrollBehavior = homeScrollBehavior,
            searchScrollBehavior = searchScrollBehavior,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
        )
    }
}
