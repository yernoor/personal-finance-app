package dev.yernur.moneytracker.ui

import dev.yernur.moneytracker.data.AnimationKind
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.font.FontWeight
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
    Analysis("analysis", "Analysis", Icons.Outlined.Insights),
    Categories("categories", "Categories", Icons.Outlined.Category),
    Tags("tags", "Tags", Icons.AutoMirrored.Outlined.Label),
    Wealth("wealth", "Wealth", Icons.AutoMirrored.Outlined.TrendingUp),
}

/** Screens that carry their own top bar when they are not in the navbar. */
private val OWN_BAR_SCREENS = setOf("wealth", "categories", "tags")

private const val ENTRY = "entry?uid={uid}&kind={kind}"
private const val CATEGORY = "category/{uid}?type={type}"
private const val ACCOUNT = "account/{uid}"

/** Where a widget tap should land: an add screen of this kind ("expense", "income", "transfer"), or the operation with this uid. Null kind and uid: just open the app. */
data class AppLaunch(val kind: String?, val uid: String?, val tap: Long = nextTap++)

/** Makes every tap a new value: two quick taps on the same widget must not look like one (state equality would swallow the second). */
private var nextTap = 0L

/** The enter/exit transition of the screen being composed (null outside the nav host), so a screen can wait for its arrival to finish. */
val LocalArrival = staticCompositionLocalOf<Transition<EnterExitState>?> { null }

/** Space around a screen. */
data class Insets(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
    fun max(o: Insets) = Insets(maxOf(left, o.left), maxOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
    fun padding(glass: Boolean) = PaddingValues(left, top, right, if (glass) 0.dp else bottom) // glass screens run under the floating bar
}

/**
 * What each screen keeps clear: the app's own bars for the tab screens and More, otherwise just the system bars. Every screen applies its
 * own, fixed, so a screen sliding away keeps its layout while the bars fade, instead of being resized by the bars' animation (that was the jump).
 */
class ChromeInsets(val tabRoutes: Set<String>, val barRoutes: Set<String>, val glass: Boolean, val tab: Insets, val system: Insets) {
    fun padding(route: String?) = when {
        route in tabRoutes -> tab.padding(true) // every tab screen runs under the floating bar (blurred scrim, or glass)
        route in barRoutes -> tab.copy(bottom = system.bottom).padding(glass) // More and screens hidden from the navbar have the top bar but no tab bar
        else -> PaddingValues() // these screens are Scaffolds with their own app bar, which already keep clear of the system bars
    }
}
val LocalChrome = staticCompositionLocalOf<ChromeInsets?> { null }

private fun entryRoute(uid: String? = null, kind: EntryKind = EntryKind.Expense) = "entry?uid=${uid.orEmpty()}&kind=$kind"

// Motion: screens always overlap while they swap, so nothing blinks out before the next one is there.
private const val MOVE_MS = 300
private const val FADE_MS = 220
private const val GROW_MS = 380 // the add screen growing out of the add button
private val ADD_ORIGIN = TransformOrigin(0.92f, 0.9f) // roughly where the add button sits: bottom right
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
    val container = LocalAppContainer.current
    val settings = container.settings
    val tabIds by settings.navTabs.collectAsStateWithLifecycle()
    val tabs = tabIds.mapNotNull { id -> NavTab.entries.firstOrNull { it.id == id } }
    val tabRoutes = tabs.map { it.id }.toSet()
    val startRoute = remember { tabs.first().id }
    // A screen hidden from the navbar is still reachable from More. Most draw no top bar of their own, so the app bar (with a back arrow) is shown for them.
    val hiddenScreens = NavTab.entries.filter { it.id !in tabRoutes && it.id !in OWN_BAR_SCREENS }
    val barRoutes = remember(hiddenScreens) { hiddenScreens.map { it.id }.toSet() + "more" }

    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val tab = tabs.firstOrNull { it.id == route }
    // Bars animate out instead of vanishing, so they keep drawing the last tab while they leave.
    var lastTab by remember { mutableStateOf(tabs.first()) }
    if (tab != null) lastTab = tab

    fun isTab(entry: NavBackStackEntry) = entry.destination.route in tabRoutes
    fun isAdd(entry: NavBackStackEntry) = entry.destination.route == ENTRY
    /** +1 when going to a tab further right in the bar, -1 to one further left. */
    fun tabDirection(from: NavBackStackEntry, to: NavBackStackEntry) =
        if (tabs.indexOfFirst { it.id == to.destination.route } >= tabs.indexOfFirst { it.id == from.destination.route }) 1 else -1

    val glass by settings.glass.collectAsStateWithLifecycle()
    val animationsOff by settings.animationsOff.collectAsStateWithLifecycle()
    val slides = AnimationKind.Screens !in animationsOff // off: screens just fade, so a swap is still there but nothing travels
    val scheme = MaterialTheme.colorScheme
    val darkTheme = isDarkSurface()
    val glow = remember(scheme.surface, scheme.primary, darkTheme) { Glow.of(scheme.surface, scheme.primary, darkTheme) }
    val plainBackground = scheme.background
    val backdropDraw = remember(glow, glass, plainBackground) {
        val draw: ContentDrawScope.() -> Unit = { if (glass) glow.draw(this, size.width, size.height) else drawRect(plainBackground); drawContent() }
        draw
    }
    val backdrop = rememberLayerBackdrop(onDraw = backdropDraw)

    val iconSetting by settings.iconStyle.collectAsStateWithLifecycle()
    val glassIconSetting by settings.glassIconStyle.collectAsStateWithLifecycle()
    val accentHue = remember(scheme.primary) { Oklch.hueOf(scheme.primary.toArgb()) }

    GlassScaffoldTheme(glass) {
    CompositionLocalProvider(
        LocalAnimationsOff provides animationsOff, LocalGlass provides glass, LocalGlow provides (if (glass) glow else null), LocalChromeInset provides if (tab != null) GlassBarInset else 0.dp,
        LocalIconStyle provides if (glass) glassIconSetting else iconSetting, // glass allows only glass-style icons
        LocalAccentHue provides accentHue,
    ) {
    Scaffold(
        topBar = {
            AnimatedVisibility(tab != null, enter = fadeIn(tween(FADE_MS)), exit = fadeOut(tween(FADE_MS))) {
                AppTopBar(
                    title = { Text(lastTab.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (glass) Color.Transparent else Color.Unspecified),
                    actions = {
                        if (lastTab.id == "wealth") WealthCurrencyAction() // next to the heading, like Compound interest
                        if (container.demoMode) {
                            var exitDemo by remember { mutableStateOf(false) }
                            // The label is the way out too: a tap asks whether to go back to the real data.
                            Text(
                                "Demo", Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { exitDemo = true }
                                    .background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 10.dp, vertical = 6.dp).testTag("demo-badge"),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            if (exitDemo) DemoExitPrompt { exitDemo = false }
                        }
                        IconButton({ nav.navigate("more") }, modifier = Modifier.testTag("more")) { Icon(Icons.Outlined.MoreVert, "More") }
                    },
                )
            }
            val barTitle = if (route == "more") "More" else hiddenScreens.firstOrNull { it.id == route }?.label
            if (barTitle != null) AppTopBar(
                title = { Text(barTitle) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (glass) Color.Transparent else Color.Unspecified),
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
      val ltr = LayoutDirection.Ltr
      val now = Insets(padding.calculateLeftPadding(ltr), padding.calculateTopPadding(), padding.calculateRightPadding(ltr), padding.calculateBottomPadding())
      var tabInsets by remember(glass) { mutableStateOf(now) }
      if (tab != null) SideEffect { tabInsets = tabInsets.max(now) } // the largest seen: the fully shown bars, not a frame of their animation
      val systemBars = ScaffoldDefaults.contentWindowInsets.asPaddingValues()
      val system = Insets(systemBars.calculateLeftPadding(ltr), systemBars.calculateTopPadding(), systemBars.calculateRightPadding(ltr), systemBars.calculateBottomPadding())
      val chrome = remember(tabRoutes, barRoutes, glass, tabInsets, system) { ChromeInsets(tabRoutes, barRoutes, glass, tabInsets, system) }
      CompositionLocalProvider(LocalChrome provides chrome) {
      Box(
          Modifier.fillMaxSize().then(if (glass) Modifier.drawBehind { glow.draw(this, size.width, size.height) } else Modifier)
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
            modifier = Modifier.layerBackdrop(backdrop),
            // Between two tabs: both move the way you are going (right of the current tab slides in from the right) while fading, so it
            // feels like the swipe. The add screen grows out of the add button. Into any other screen: slide forward while fading.
            enterTransition = {
                when {
                    !slides -> fadeIn(tween(FADE_MS))
                    isTab(initialState) && isTab(targetState) -> slideInHorizontally(tween(MOVE_MS, easing = slideEasing)) { it / 5 * tabDirection(initialState, targetState) } + fadeIn(tween(FADE_MS))
                    isAdd(targetState) -> scaleIn(tween(GROW_MS, easing = slideEasing), initialScale = 0.15f, transformOrigin = ADD_ORIGIN) + fadeIn(tween(FADE_MS))
                    else -> slideInHorizontally(tween(MOVE_MS, easing = slideEasing)) { it / 6 } + fadeIn(tween(MOVE_MS))
                }
            },
            exitTransition = {
                when {
                    !slides -> fadeOut(tween(FADE_MS))
                    isTab(initialState) && isTab(targetState) -> slideOutHorizontally(tween(MOVE_MS, easing = slideEasing)) { -it / 5 * tabDirection(initialState, targetState) } + fadeOut(tween(FADE_MS))
                    isAdd(targetState) -> scaleOut(tween(GROW_MS, easing = slideEasing), targetScale = 0.96f) + fadeOut(tween(FADE_MS))
                    else -> slideOutHorizontally(tween(MOVE_MS, easing = slideEasing)) { -it / 6 } + fadeOut(tween(MOVE_MS))
                }
            },
            popEnterTransition = { // back (swipe or button), tabs included: opaque slides, a fade shows through the leaving screen
                if (!slides) fadeIn(tween(FADE_MS))
                else if (isAdd(initialState)) scaleIn(tween(GROW_MS, easing = slideEasing), initialScale = 0.96f) + fadeIn(tween(FADE_MS))
                else slideInHorizontally(tween(MOVE_MS, easing = slideEasing)) { -it / 6 }
            },
            popExitTransition = {
                if (!slides) fadeOut(tween(FADE_MS))
                else if (isAdd(initialState)) scaleOut(tween(GROW_MS, easing = slideEasing), targetScale = 0.15f, transformOrigin = ADD_ORIGIN) + fadeOut(tween(MOVE_MS))
                else slideOutHorizontally(tween(MOVE_MS, easing = slideEasing)) { it }
            },
            // The swipe gesture has its own defaults (scale down and fade, which shows the screen below through the one leaving): same slides as the button.
            predictivePopEnterTransition = { slideInHorizontally { -it / 6 } },
            predictivePopExitTransition = { slideOutHorizontally { it } },
        ) {
            screen("home") { HomeScreen { request -> container.historyRequest.value = request; nav.navigateTop("history") } }
            screen("history") {
                HistoryScreen { uid, isTransfer -> nav.navigate(entryRoute(uid, if (isTransfer) EntryKind.Transfer else EntryKind.Expense)) }
            }
            screen("charts") { ChartsScreen(onOpenTransaction = { nav.navigate(entryRoute(it)) }) }
            screen("analysis") {
                AnalysisScreen(onOpenCategory = { nav.navigate("category/$it?type=Expense") }, onOpenTrend = { nav.navigate("trend/$it") }, onOpenRecurring = { nav.navigate("recurring") })
            }
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
                EntryScreen(uid, EntryKind.valueOf(entry.arguments?.getString("kind") ?: "Expense"), onLeave = { nav.popBackStack() })
            }
            screen(ACCOUNT, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                AccountEditScreen(entry.arguments?.getString("uid")?.takeIf { it != "new" }) { nav.popBackStack() }
            }
            screen("more") {
                MoreScreen(
                    onCategories = { nav.navigate("categories") }, onTags = { nav.navigate("tags") },
                    onNavbar = { nav.navigate("navbar-settings") }, onHomeLayout = { nav.navigate("home-settings") },
                    onSync = { nav.navigate("sync") }, onUpdates = { nav.navigate("updates") }, onFeedback = { nav.navigate("feedback") },
                    onCompound = { nav.navigate("compound") }, onWealth = { nav.navigate("wealth") }, onAfford = { nav.navigate("afford") }, onOpenTab = { nav.navigate(it.id) },
                )
            }
            screen("wealth") {
                WealthScreen(embedded = "wealth" in tabRoutes, onCompound = { nav.navigate("compound") }, onBack = { nav.popBackStack() })
            }
            screen("compound") { CompoundScreen { nav.popBackStack() } }
            screen("trend/{uid}", arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                CategoryTrendScreen(entry.arguments?.getString("uid").orEmpty(), onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate(entryRoute(it)) })
            }
            screen("recurring") { RecurringScreen { nav.popBackStack() } }
            screen("afford") { AffordScreen(onBack = { nav.popBackStack() }, onLogExpense = { nav.navigate(entryRoute()) }) }
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
        run {
            val navInset = padding.calculateBottomPadding()
            val glassBackdrop = if (glass) backdrop else null // without glass: a plain pill and button over a blurred, fading strip of the content
            if (!glass) BarScrim(backdrop, Modifier.align(Alignment.BottomCenter))
            AnimatedVisibility(
                tab != null, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(MOVE_MS, easing = slideEasing)) { it } + fadeIn(tween(FADE_MS)),
                exit = slideOutVertically(tween(MOVE_MS, easing = slideEasing)) { it } + fadeOut(tween(FADE_MS)),
            ) { FloatingTabBar(tabs, lastTab, onSelect = { nav.navigateTop(it.id) }, backdrop = glassBackdrop) }
            val addMenu = remember { mutableStateOf(false) }
            val addShown = tab?.id == "home" || tab?.id == "history"
            if (glass && addMenu.value && addShown) GlassAddMenu(backdrop, bottom = navInset + 88.dp + 60.dp + 12.dp, end = 16.dp, onDismiss = { addMenu.value = false }) {
                addMenu.value = false
                nav.navigate(entryRoute(kind = it))
            }
            AnimatedVisibility(
                addShown, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = navInset + 88.dp),
                enter = scaleIn(tween(FADE_MS)) + fadeIn(tween(FADE_MS)), exit = scaleOut(tween(FADE_MS)) + fadeOut(tween(FADE_MS)),
            ) { AddButton({ nav.navigate(entryRoute()) }, backdrop = glassBackdrop, onPickKind = { nav.navigate(entryRoute(kind = it)) }, menu = addMenu) }
        }
      }
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
    val glow = LocalGlow.current
    val chrome = LocalChrome.current
    // Every screen carries its own opaque background: in glass mode the glow (lined up with the one behind the top bar), otherwise the plain color.
    val background = if (glow != null) Modifier.drawBehind { glow.draw(this, size.width, size.height) }
    else Modifier.background(MaterialTheme.colorScheme.background)
    val onTab = chrome != null && route in chrome.tabRoutes
    CompositionLocalProvider(LocalArrival provides transition, LocalChromeInset provides if (onTab) GlassBarInset else 0.dp) {
        Box(Modifier.fillMaxSize().then(background)) {
            Box(Modifier.fillMaxSize().then(if (chrome != null) Modifier.padding(chrome.padding(route)) else Modifier)) { content(entry) }
        }
    }
}
