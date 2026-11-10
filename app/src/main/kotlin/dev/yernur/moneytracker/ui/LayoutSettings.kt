package dev.yernur.moneytracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.LocalAppContainer
import dev.yernur.moneytracker.data.AppSettings
import dev.yernur.moneytracker.data.OrderedChoice
import dev.yernur.moneytracker.data.PeriodUnit

/** One choosable entry: a tab or a Home card. */
class LayoutItem(val id: String, val label: String, val icon: ImageVector? = null)

/**
 * Checkbox + up/down arrows for each item: shown items first, in order; hidden ones below.
 * Tags: `<prefix>-check-<id>`, `<prefix>-up-<id>`, `<prefix>-down-<id>`.
 */
@Composable
private fun ReorderChecklist(
    items: List<LayoutItem>, visible: List<String>, min: Int, max: Int, prefix: String, onChange: (List<String>) -> Unit,
) {
    val byId = items.associateBy { it.id }
    Column {
        OrderedChoice.arranged(visible, items.map { it.id }).forEach { (id, shown) ->
            val item = byId.getValue(id)
            ListItem(
                leadingContent = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(shown, { onChange(OrderedChoice.toggle(visible, id, min, max)) }, modifier = Modifier.testTag("$prefix-check-$id"))
                        item.icon?.let { Icon(it, null, Modifier.padding(start = 4.dp)) }
                    }
                },
                headlineContent = { Text(item.label) },
                trailingContent = {
                    if (shown) Row {
                        IconButton({ onChange(OrderedChoice.move(visible, id, -1)) }, enabled = visible.indexOf(id) > 0, modifier = Modifier.testTag("$prefix-up-$id")) {
                            Icon(Icons.Outlined.ArrowUpward, "Move up")
                        }
                        IconButton({ onChange(OrderedChoice.move(visible, id, 1)) }, enabled = visible.indexOf(id) < visible.lastIndex, modifier = Modifier.testTag("$prefix-down-$id")) {
                            Icon(Icons.Outlined.ArrowDownward, "Move down")
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavbarSettingsScreen(onBack: () -> Unit) {
    val settings = LocalAppContainer.current.settings
    val visible by settings.navTabs.collectAsStateWithLifecycle()
    val items = AppSettings.NAV_POOL.map { id -> NavTab.entries.first { it.id == id }.let { LayoutItem(it.id, it.label, it.icon) } }
    Scaffold(topBar = {
        AppTopBar(title = { Text("Navigation bar") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Show ${AppSettings.NAV_MIN} to ${AppSettings.NAV_MAX} tabs. The first one opens with the app.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium,
            )
            ReorderChecklist(items, visible, AppSettings.NAV_MIN, AppSettings.NAV_MAX, "nav", settings::setNavTabs)
            TextButton({ settings.setNavTabs(AppSettings.NAV_DEFAULT) }, Modifier.padding(horizontal = 8.dp).testTag("nav-reset")) { Text("Reset to default") }
        }
    }
}

private val HOME_ITEMS = listOf(
    LayoutItem("warnings", "Spending warnings (only when there are any)"),
    LayoutItem("balance", "Balance"),
    LayoutItem("totals", "Income and expenses"),
    LayoutItem("top-categories", "Top spending categories"),
    LayoutItem("recent", "Latest entries"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeLayoutScreen(onBack: () -> Unit) {
    val settings = LocalAppContainer.current.settings
    val visible by settings.homeCards.collectAsStateWithLifecycle()
    val period by settings.homeTotalsPeriod.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        AppTopBar(title = { Text("Home layout") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Totals cover", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge)
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodUnit.entries.forEach { unit ->
                    FilterChip(period == unit, { settings.setHomeTotalsPeriod(unit) }, label = { Text(unit.name) }, modifier = Modifier.testTag("home-period-${unit.name}"))
                }
            }
            Text("Cards", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge)
            ReorderChecklist(HOME_ITEMS, visible, 1, HOME_ITEMS.size, "home", settings::setHomeCards)
            TextButton({ settings.setHomeCards(AppSettings.HOME_DEFAULT); settings.setHomeTotalsPeriod(PeriodUnit.Month) }, Modifier.padding(horizontal = 8.dp).testTag("home-reset")) {
                Text("Reset to default")
            }
        }
    }
}
