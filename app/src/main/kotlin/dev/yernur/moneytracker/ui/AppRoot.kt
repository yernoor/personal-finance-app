package dev.yernur.moneytracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.NavGraphBuilder
import androidx.compose.foundation.background
import androidx.navigation.NamedNavArgument
import androidx.navigation.compose.composable as plainComposable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.IconStyle
import dev.yernur.moneytracker.data.Oklch
import dev.yernur.moneytracker.data.db.TransactionType

/** Everything that can sit in the bottom bar; which ones do, and in what order, is a setting (`AppSettings.navTabs`). */
enum class NavTab(val id: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    History("history", "History", Icons.Outlined.History),
    Charts("charts", "Charts", Icons.Outlined.BarChart),
    Accounts("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet),
    Categories("categories", "Categories", Icons.Outlined.Category),
    Tags("tags", "Tags", Icons.AutoMirrored.Outlined.Label),
    Wealth("wealth", "Wealth", Icons.AutoMirrored.Outlined.TrendingUp),
}

private const val ENTRY = "entry?uid={uid}&kind={kind}"
private const val CATEGORY = "category/{uid}?type={type}"
private const val ACCOUNT = "account/{uid}"

/** Where a widget tap should land: an add screen of this kind ("expense", "income", "transfer"), or the operation with this uid. Null kind and uid: just open the app. */
data class AppLaunch(val kind: String?, val uid: String?)

private fun entryRoute(uid: String? = null, kind: EntryKind = EntryKind.Expense) = "entry?uid=${uid.orEmpty()}&kind=$kind"

// Motion: screens always overlap while they swap, so nothing blinks out before the next one is there.
private const val MOVE_MS = 300
private const val FADE_MS = 220
private const val SWIPE_PX = 150f
private val slideEasing = FastOutSlowInEasing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(nav: NavHostController = rememberNavController(), launch: AppLaunch? = null, onLaunched: () -> Unit = {}) {
    LaunchedEffect(launch) {
        val target = launch ?: return@LaunchedEffect
        val kind = when (target.kind) {
            "income" -> EntryKind.Income
            "transfer" -> EntryKind.Transfer
            else -> EntryKind.Expense
        }
        nav.navigate(entryRoute(target.uid, kind))
        onLaunched()
    }
    CrashPrompt(onReport = { nav.navigate("feedback/crash") })
    val settings = LocalAppContainer.current.settings
    val tabIds by settings.navTabs.collectAsStateWithLifecycle()
    val tabs = tabIds.mapNotNull { id -> NavTab.entries.firstOrNull { it.id == id } }
    val tabRoutes = tabs.map { it.id }.toSet()
    val startRoute = remember { tabs.first().id }

    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val tab = tabs.firstOrNull { it.id == route }
    // Bars animate out instead of vanishing, so they keep drawing the last tab while they leave.
    var lastTab by remember { mutableStateOf(tabs.first()) }
    if (tab != null) lastTab = tab

    fun isTab(entry: NavBackStackEntry) = entry.destination.route in tabRoutes

    val glass by settings.glass.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val glow = remember(scheme.surface, scheme.primary, scheme.tertiary) { glowBackground(scheme.surface, scheme.primary, scheme.tertiary) }
    val backdropDraw = remember(glow) { val draw: ContentDrawScope.() -> Unit = { glow(); drawContent() }; draw }
    val backdrop = rememberLayerBackdrop(onDraw = backdropDraw)

    val iconSetting by settings.iconStyle.collectAsStateWithLifecycle()
    val glassIconSetting by settings.glassIconStyle.collectAsStateWithLifecycle()
    val accentHue = remember(scheme.primary) { Oklch.hueOf(scheme.primary.toArgb()) }

    CompositionLocalProvider(
        LocalGlass provides glass, LocalChromeInset provides if (glass && tab != null) GlassBarInset else 0.dp,
        LocalIconStyle provides if (glass) glassIconSetting else iconSetting, // glass allows only glass-style icons
        LocalAccentHue provides accentHue,
    ) {
    Scaffold(
        topBar = {
            AnimatedVisibility(tab != null, enter = fadeIn(tween(FADE_MS)) + expandVertically(), exit = fadeOut(tween(FADE_MS)) + shrinkVertically()) {
                CenterAlignedTopAppBar(
                    title = { Text(lastTab.label) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (glass) Color.Transparent else Color.Unspecified),
                    actions = { IconButton({ nav.navigate("more") }, modifier = Modifier.testTag("more")) { Icon(Icons.Outlined.MoreVert, "More") } },
                )
            }
            if (route == "more") TopAppBar(
                title = { Text("More") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (glass) Color.Transparent else Color.Unspecified),
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                tab != null && !glass,
                enter = slideInVertically(tween(MOVE_MS, easing = slideEasing)) { it } + fadeIn(tween(FADE_MS)),
                exit = slideOutVertically(tween(MOVE_MS, easing = slideEasing)) { it } + fadeOut(tween(FADE_MS)),
            ) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = t == lastTab, onClick = { nav.navigateTop(t.id) },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label) }, modifier = Modifier.testTag("tab-${t.id}"),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                (tab?.id == "home" || tab?.id == "history") && !glass,
                enter = scaleIn(tween(FADE_MS)) + fadeIn(tween(FADE_MS)), exit = scaleOut(tween(FADE_MS)) + fadeOut(tween(FADE_MS)),
            ) {
                FloatingActionButton({ nav.navigate(entryRoute()) }, modifier = Modifier.testTag("add")) { Icon(Icons.Outlined.Add, "Add") }
            }
        },
    ) { padding ->
      Box(
          Modifier.fillMaxSize().then(if (glass) Modifier.drawBehind { glow() } else Modifier)
              .pointerInput(tab, tabs) { // swipe left/right between tabs; drags a child already used (charts, sliders) never reach here
                  if (tab == null) return@pointerInput
                  var dragged = 0f
                  detectHorizontalDragGestures(
                      onDragStart = { dragged = 0f },
                      onHorizontalDrag = { _, delta -> dragged += delta },
                      onDragEnd = {
                          val step = if (dragged < -SWIPE_PX) 1 else if (dragged > SWIPE_PX) -1 else 0
                          tabs.getOrNull(tabs.indexOf(tab) + step)?.takeIf { step != 0 }?.let { nav.navigateTop(it.id) }
                      },
                  )
              },
      ) {
        NavHost(
            nav, startDestination = startRoute,
            // With glass the content runs under the floating bar (so there is something to blur); otherwise the bar owns that strip.
            modifier = if (glass) Modifier.padding(top = padding.calculateTopPadding()).layerBackdrop(backdrop) else Modifier.padding(padding),
            // Between two tabs: a crossfade with a slight scale (no blank frame). Into a detail screen: slide forward while fading.
            enterTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeIn(tween(FADE_MS)) + scaleIn(tween(FADE_MS), initialScale = 0.96f)
                else slideInHorizontally(tween(MOVE_MS, easing = slideEasing)) { it / 6 } + fadeIn(tween(MOVE_MS))
            },
            exitTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeOut(tween(FADE_MS))
                else slideOutHorizontally(tween(MOVE_MS, easing = slideEasing)) { -it / 6 } + fadeOut(tween(MOVE_MS))
            },
            popEnterTransition = { // back (swipe or button), tabs included: opaque slides, a fade shows through the leaving screen
                slideInHorizontally(tween(MOVE_MS, easing = slideEasing)) { -it / 6 }
            },
            popExitTransition = {
                slideOutHorizontally(tween(MOVE_MS, easing = slideEasing)) { it }
            },
            // The swipe gesture has its own defaults (scale down and fade, which shows the screen below through the one leaving): same slides as the button.
            predictivePopEnterTransition = { slideInHorizontally { -it / 6 } },
            predictivePopExitTransition = { slideOutHorizontally { it } },
        ) {
            screen("home") { HomeScreen() }
            screen("history") {
                HistoryScreen { uid, isTransfer -> nav.navigate(entryRoute(uid, if (isTransfer) EntryKind.Transfer else EntryKind.Expense)) }
            }
            screen("charts") { ChartsScreen(onOpenCategory = { nav.navigate("category/$it?type=Expense") }, onOpenTransaction = { nav.navigate(entryRoute(it)) }) }
            screen("accounts") {
                AccountsScreen(
                    onOpen = { nav.navigate("account/$it") }, onNew = { nav.navigate("account/new") },
                    onNewTransfer = { nav.navigate(entryRoute(kind = EntryKind.Transfer)) },
                )
            }
            screen(
                ENTRY,
                arguments = listOf(
                    navArgument("uid") { type = NavType.StringType; defaultValue = "" },
                    navArgument("kind") { type = NavType.StringType; defaultValue = EntryKind.Expense.name },
                ),
            ) { entry ->
                val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotEmpty() }
                EntryScreen(uid, EntryKind.valueOf(entry.arguments?.getString("kind") ?: "Expense")) { nav.popBackStack() }
            }
            screen(ACCOUNT, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                AccountEditScreen(entry.arguments?.getString("uid")?.takeIf { it != "new" }) { nav.popBackStack() }
            }
            screen("more") {
                MoreScreen(
                    onCategories = { nav.navigate("categories") }, onTags = { nav.navigate("tags") },
                    onNavbar = { nav.navigate("navbar-settings") }, onHomeLayout = { nav.navigate("home-settings") },
                    onSync = { nav.navigate("sync") }, onUpdates = { nav.navigate("updates") }, onFeedback = { nav.navigate("feedback") },
                    onCompound = { nav.navigate("compound") }, onWealth = { nav.navigate("wealth") },
                )
            }
            screen("wealth") {
                WealthScreen(embedded = "wealth" in tabRoutes, onCompound = { nav.navigate("compound") }, onBack = { nav.popBackStack() })
            }
            screen("compound") { CompoundScreen { nav.popBackStack() } }
            screen("sync") { SyncScreen { nav.popBackStack() } }
            screen("updates") { UpdatesScreen { nav.popBackStack() } }
            screen("feedback") { FeedbackScreen { nav.popBackStack() } }
            screen("feedback/crash") { FeedbackScreen(reportLastCrash = true) { nav.popBackStack() } }
            screen("navbar-settings") { NavbarSettingsScreen { nav.popBackStack() } }
            screen("home-settings") { HomeLayoutScreen { nav.popBackStack() } }
            screen("categories") {
                CategoriesScreen(
                    onOpen = { nav.navigate("category/$it?type=Expense") },
                    onNew = { nav.navigate("category/new?type=$it") }, onBack = { nav.popBackStack() },
                    embedded = "categories" in tabRoutes, // as a tab it uses the app bar above instead of its own
                )
            }
            screen(
                CATEGORY,
                arguments = listOf(
                    navArgument("uid") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType; defaultValue = TransactionType.Expense.name },
                ),
            ) { entry ->
                CategoryEditScreen(
                    entry.arguments?.getString("uid")?.takeIf { it != "new" },
                    TransactionType.valueOf(entry.arguments?.getString("type") ?: "Expense"),
                ) { nav.popBackStack() }
            }
            screen("tags") { TagsScreen(embedded = "tags" in tabRoutes) { nav.popBackStack() } }
        }
        if (glass) {
            val navInset = padding.calculateBottomPadding()
            AnimatedVisibility(
                tab != null, Modifier.align(Alignment.BottomCenter).padding(bottom = navInset + 8.dp),
                enter = slideInVertically(tween(MOVE_MS, easing = slideEasing)) { it } + fadeIn(tween(FADE_MS)),
                exit = slideOutVertically(tween(MOVE_MS, easing = slideEasing)) { it } + fadeOut(tween(FADE_MS)),
            ) { GlassBottomBar(backdrop, tabs, lastTab, onSelect = { nav.navigateTop(it.id) }) }
            AnimatedVisibility(
                tab?.id == "home" || tab?.id == "history", Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = navInset + 84.dp),
                enter = scaleIn(tween(FADE_MS)) + fadeIn(tween(FADE_MS)), exit = scaleOut(tween(FADE_MS)) + fadeOut(tween(FADE_MS)),
            ) { GlassAddButton(backdrop, { nav.navigate(entryRoute()) }) }
        }
      }
    }
    }
}

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** A destination with its own opaque background, so the screen underneath never shows through while a back swipe slides this one away. */
private fun NavGraphBuilder.screen(
    route: String, arguments: List<NamedNavArgument> = emptyList(), content: @Composable (NavBackStackEntry) -> Unit,
) = plainComposable(route, arguments) { entry ->
    val glass = LocalGlass.current
    Box(Modifier.fillMaxSize().then(if (glass) Modifier else Modifier.background(MaterialTheme.colorScheme.background))) { content(entry) }
}
