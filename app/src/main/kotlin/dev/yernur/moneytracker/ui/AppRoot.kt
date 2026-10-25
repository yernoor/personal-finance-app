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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.db.TransactionType

/** Everything that can sit in the bottom bar; which ones do, and in what order, is a setting (`AppSettings.navTabs`). */
enum class NavTab(val id: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    History("history", "History", Icons.Outlined.History),
    Charts("charts", "Charts", Icons.Outlined.BarChart),
    Accounts("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet),
    Categories("categories", "Categories", Icons.Outlined.Category),
    Tags("tags", "Tags", Icons.AutoMirrored.Outlined.Label),
}

private const val ENTRY = "entry?uid={uid}&kind={kind}"
private const val CATEGORY = "category/{uid}?type={type}"
private const val ACCOUNT = "account/{uid}"

private fun entryRoute(uid: String? = null, kind: EntryKind = EntryKind.Expense) = "entry?uid=${uid.orEmpty()}&kind=$kind"

// Motion: screens always overlap while they swap, so nothing blinks out before the next one is there.
private const val MOVE_MS = 300
private const val FADE_MS = 220
private val slideEasing = FastOutSlowInEasing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(nav: NavHostController = rememberNavController()) {
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

    Scaffold(
        topBar = {
            AnimatedVisibility(tab != null, enter = fadeIn(tween(FADE_MS)) + expandVertically(), exit = fadeOut(tween(FADE_MS)) + shrinkVertically()) {
                CenterAlignedTopAppBar(
                    title = { Text(lastTab.label) },
                    actions = { IconButton({ nav.navigate("more") }, modifier = Modifier.testTag("more")) { Icon(Icons.Outlined.MoreVert, "More") } },
                )
            }
            if (route == "more") TopAppBar(
                title = { Text("More") },
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                tab != null,
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
                tab?.id == "home" || tab?.id == "history",
                enter = scaleIn(tween(FADE_MS)) + fadeIn(tween(FADE_MS)), exit = scaleOut(tween(FADE_MS)) + fadeOut(tween(FADE_MS)),
            ) {
                FloatingActionButton({ nav.navigate(entryRoute()) }, modifier = Modifier.testTag("add")) { Icon(Icons.Outlined.Add, "Add") }
            }
        },
    ) { padding ->
        NavHost(
            nav, startDestination = startRoute, modifier = Modifier.padding(padding),
            // Between two tabs: a crossfade with a slight scale (no blank frame). Into a detail screen: slide forward while fading.
            enterTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeIn(tween(FADE_MS)) + scaleIn(tween(FADE_MS), initialScale = 0.96f)
                else slideInHorizontally(tween(MOVE_MS, easing = slideEasing)) { it / 6 } + fadeIn(tween(MOVE_MS))
            },
            exitTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeOut(tween(FADE_MS))
                else slideOutHorizontally(tween(MOVE_MS, easing = slideEasing)) { -it / 6 } + fadeOut(tween(MOVE_MS))
            },
            popEnterTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeIn(tween(FADE_MS)) + scaleIn(tween(FADE_MS), initialScale = 0.96f)
                else slideInHorizontally(tween(MOVE_MS, easing = slideEasing)) { -it / 6 } + fadeIn(tween(MOVE_MS))
            },
            popExitTransition = {
                if (isTab(initialState) && isTab(targetState)) fadeOut(tween(FADE_MS))
                else slideOutHorizontally(tween(MOVE_MS, easing = slideEasing)) { it / 6 } + fadeOut(tween(MOVE_MS))
            },
        ) {
            composable("home") { HomeScreen() }
            composable("history") {
                HistoryScreen { uid, isTransfer -> nav.navigate(entryRoute(uid, if (isTransfer) EntryKind.Transfer else EntryKind.Expense)) }
            }
            composable("charts") { ChartsScreen(onOpenCategory = { nav.navigate("category/$it?type=Expense") }) }
            composable("accounts") {
                AccountsScreen(
                    onOpen = { nav.navigate("account/$it") }, onNew = { nav.navigate("account/new") },
                    onNewTransfer = { nav.navigate(entryRoute(kind = EntryKind.Transfer)) },
                )
            }
            composable(
                ENTRY,
                arguments = listOf(
                    navArgument("uid") { type = NavType.StringType; defaultValue = "" },
                    navArgument("kind") { type = NavType.StringType; defaultValue = EntryKind.Expense.name },
                ),
            ) { entry ->
                val uid = entry.arguments?.getString("uid")?.takeIf { it.isNotEmpty() }
                EntryScreen(uid, EntryKind.valueOf(entry.arguments?.getString("kind") ?: "Expense")) { nav.popBackStack() }
            }
            composable(ACCOUNT, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entry ->
                AccountEditScreen(entry.arguments?.getString("uid")?.takeIf { it != "new" }) { nav.popBackStack() }
            }
            composable("more") {
                MoreScreen(
                    onCategories = { nav.navigate("categories") }, onTags = { nav.navigate("tags") },
                    onNavbar = { nav.navigate("navbar-settings") }, onHomeLayout = { nav.navigate("home-settings") },
                )
            }
            composable("navbar-settings") { NavbarSettingsScreen { nav.popBackStack() } }
            composable("home-settings") { HomeLayoutScreen { nav.popBackStack() } }
            composable("categories") {
                CategoriesScreen(
                    onOpen = { nav.navigate("category/$it?type=Expense") },
                    onNew = { nav.navigate("category/new?type=$it") }, onBack = { nav.popBackStack() },
                    embedded = "categories" in tabRoutes, // as a tab it uses the app bar above instead of its own
                )
            }
            composable(
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
            composable("tags") { TagsScreen(embedded = "tags" in tabRoutes) { nav.popBackStack() } }
        }
    }
}

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
