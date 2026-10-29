package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.resetHeightOffset

@Stable
internal interface HeaderScrollConnection : NestedScrollConnection {
    var hasCollapsibleHeader: Boolean
    var scrollProgress: () -> Float
    val isBound: Boolean

    fun bind(owner: Any, hasCollapsibleHeader: Boolean, scrollProgress: () -> Float)
    fun unbind(owner: Any)
}

@OptIn(ExperimentalMaterial3Api::class)
@Stable
internal class HeaderAppBarScrollBehavior(
    private val delegate: TopAppBarScrollBehavior,
) : TopAppBarScrollBehavior by delegate {
    var hasCollapsibleHeader by mutableStateOf(false)
    var scrollProgress by mutableStateOf<() -> Float>({ 0f })
    var isBound by mutableStateOf(false)
    private var activeOwner: Any? = null

    fun bind(owner: Any, hasCollapsibleHeader: Boolean, progress: () -> Float) {
        activeOwner = owner
        this.hasCollapsibleHeader = hasCollapsibleHeader
        this.scrollProgress = progress
        this.isBound = true
    }

    fun unbind(owner: Any) {
        if (activeOwner === owner) {
            activeOwner = null
            this.hasCollapsibleHeader = false
            this.scrollProgress = { 0f }
            this.isBound = false
        }
    }

    override val nestedScrollConnection: NestedScrollConnection =
        object : HeaderScrollConnection, NestedScrollConnection by delegate.nestedScrollConnection {
            override var hasCollapsibleHeader: Boolean
                get() = this@HeaderAppBarScrollBehavior.hasCollapsibleHeader
                set(value) {
                    this@HeaderAppBarScrollBehavior.hasCollapsibleHeader = value
                }

            override var scrollProgress: () -> Float
                get() = this@HeaderAppBarScrollBehavior.scrollProgress
                set(value) {
                    this@HeaderAppBarScrollBehavior.scrollProgress = value
                    this@HeaderAppBarScrollBehavior.isBound = true
                }

            override val isBound: Boolean
                get() = this@HeaderAppBarScrollBehavior.isBound

            override fun bind(owner: Any, hasCollapsibleHeader: Boolean, scrollProgress: () -> Float) {
                this@HeaderAppBarScrollBehavior.bind(owner, hasCollapsibleHeader, scrollProgress)
            }

            override fun unbind(owner: Any) {
                this@HeaderAppBarScrollBehavior.unbind(owner)
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
        HeaderAppBarScrollBehavior(rawHomeScrollBehavior)
    }
    val rawSearchScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )
    val searchScrollBehavior = remember(rawSearchScrollBehavior) {
        HeaderAppBarScrollBehavior(rawSearchScrollBehavior)
    }
    val rawTopAppBarScrollBehavior =
        appBarScrollBehavior(
            canScroll = {
                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
        )
    val topAppBarScrollBehavior = remember(rawTopAppBarScrollBehavior) {
        HeaderAppBarScrollBehavior(rawTopAppBarScrollBehavior)
    }

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

@Composable
internal fun rememberHeaderScrollProgress(
    lazyListState: LazyListState,
    hasCollapsibleHeader: Boolean,
    collapseDistance: Dp = 360.dp,
    standardThreshold: Dp = 48.dp,
): () -> Float {
    val density = LocalDensity.current
    val targetThresholdPx = with(density) {
        val targetDp = if (hasCollapsibleHeader) collapseDistance else standardThreshold
        targetDp.toPx().coerceAtLeast(1f)
    }

    return remember(lazyListState, targetThresholdPx) {
        {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / targetThresholdPx).coerceIn(0f, 1f)
            }
        }
    }
}

@Composable
internal fun rememberHeaderScrollProgress(
    lazyGridState: LazyGridState,
    hasCollapsibleHeader: Boolean,
    collapseDistance: Dp = 360.dp,
    standardThreshold: Dp = 48.dp,
): () -> Float {
    val density = LocalDensity.current
    val targetThresholdPx = with(density) {
        val targetDp = if (hasCollapsibleHeader) collapseDistance else standardThreshold
        targetDp.toPx().coerceAtLeast(1f)
    }

    return remember(lazyGridState, targetThresholdPx) {
        {
            if (lazyGridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyGridState.firstVisibleItemScrollOffset / targetThresholdPx).coerceIn(0f, 1f)
            }
        }
    }
}

@Composable
internal fun BindHeaderScroll(
    connection: NestedScrollConnection?,
    lazyListState: LazyListState,
    hasCollapsibleHeader: Boolean,
    collapseDistance: Dp = 360.dp,
    standardThreshold: Dp = 48.dp,
): () -> Float {
    val progressProvider = rememberHeaderScrollProgress(
        lazyListState = lazyListState,
        hasCollapsibleHeader = hasCollapsibleHeader,
        collapseDistance = collapseDistance,
        standardThreshold = standardThreshold,
    )

    return BindHeaderScroll(
        connection = connection,
        hasCollapsibleHeader = hasCollapsibleHeader,
        progressProvider = progressProvider,
    )
}

@Composable
internal fun BindHeaderScroll(
    connection: NestedScrollConnection?,
    lazyGridState: LazyGridState,
    hasCollapsibleHeader: Boolean,
    collapseDistance: Dp = 360.dp,
    standardThreshold: Dp = 48.dp,
): () -> Float {
    val progressProvider = rememberHeaderScrollProgress(
        lazyGridState = lazyGridState,
        hasCollapsibleHeader = hasCollapsibleHeader,
        collapseDistance = collapseDistance,
        standardThreshold = standardThreshold,
    )

    return BindHeaderScroll(
        connection = connection,
        hasCollapsibleHeader = hasCollapsibleHeader,
        progressProvider = progressProvider,
    )
}

@Composable
internal fun BindHeaderScroll(
    connection: NestedScrollConnection?,
    hasCollapsibleHeader: Boolean,
    progressProvider: () -> Float,
): () -> Float {
    val headerConnection = connection as? HeaderScrollConnection
    val registrationToken = remember { Any() }

    DisposableEffect(headerConnection, hasCollapsibleHeader, progressProvider) {
        headerConnection?.bind(
            owner = registrationToken,
            hasCollapsibleHeader = hasCollapsibleHeader,
            scrollProgress = progressProvider,
        )
        onDispose {
            headerConnection?.unbind(registrationToken)
        }
    }

    return progressProvider
}
