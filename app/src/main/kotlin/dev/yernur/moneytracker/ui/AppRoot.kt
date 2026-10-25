package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.yernur.moneytracker.data.db.TransactionType

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    History("history", "History", Icons.Outlined.History),
    Charts("charts", "Charts", Icons.Outlined.BarChart),
    Accounts("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet),
}

private const val ENTRY = "entry?uid={uid}&kind={kind}"
private const val CATEGORY = "category/{uid}?type={type}"
private const val ACCOUNT = "account/{uid}"

private fun entryRoute(uid: String? = null, kind: EntryKind = EntryKind.Expense) = "entry?uid=${uid.orEmpty()}&kind=$kind"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(nav: NavHostController = rememberNavController()) {
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }

    Scaffold(
        topBar = {
            if (tab != null) CenterAlignedTopAppBar(
                title = { Text(tab.label) },
                actions = { IconButton({ nav.navigate("more") }, modifier = Modifier.testTag("more")) { Icon(Icons.Outlined.MoreVert, "More") } },
            )
        },
        bottomBar = {
            if (tab != null) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab, onClick = { nav.navigateTop(t.route) },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) }, modifier = Modifier.testTag("tab-${t.route}"),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Home || tab == Tab.History) FloatingActionButton({ nav.navigate(entryRoute()) }, modifier = Modifier.testTag("add")) {
                Icon(Icons.Outlined.Add, "Add")
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Home.route) { HomeScreen() }
            composable(Tab.History.route) {
                HistoryScreen { uid, isTransfer -> nav.navigate(entryRoute(uid, if (isTransfer) EntryKind.Transfer else EntryKind.Expense)) }
            }
            composable(Tab.Charts.route) { Text("Charts and projections arrive in the next milestone.", Modifier.padding(16.dp)) }
            composable(Tab.Accounts.route) {
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
            composable("more") { MoreScreen(onCategories = { nav.navigate("categories") }, onTags = { nav.navigate("tags") }) }
            composable("categories") {
                CategoriesScreen(
                    onOpen = { nav.navigate("category/$it?type=Expense") },
                    onNew = { nav.navigate("category/new?type=$it") }, onBack = { nav.popBackStack() },
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
            composable("tags") { TagsScreen { nav.popBackStack() } }
        }
    }
}

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
