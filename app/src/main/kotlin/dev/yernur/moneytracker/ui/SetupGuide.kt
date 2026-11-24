package dev.yernur.moneytracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yernur.moneytracker.data.AccentPalette

/** One page of the setup guide: a small label, a heading, the text, and a picture drawn from the app's own pieces. */
private class GuidePage(val label: String, val title: String, val body: String, val picture: @Composable () -> Unit)

private val PAGES = listOf(
    GuidePage(
        "Step 1 · Accounts", "Start with where your money lives",
        "Add one account for each place you keep money: cash, a card, a deposit. Each account has its own currency. " +
            "Switch on \"Hide from the Total\" for savings you don't want in the headline balance.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SampleRow("cash", 2, "Cash", "KZT")
            SampleRow("bank", 0, "Card", "KZT")
            SampleRow("deposit", 4, "Savings", "USD · hidden from the Total")
        }
    },
    GuidePage(
        "Step 2 · Categories", "Name what you spend on",
        "Give each category an icon and a colour. Hold a tile to start rearranging the grid. " +
            "Set a planned outlay per month and the app warns you as you get close to it.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                listOf("products" to "Groceries", "cafe" to "Cafes and restaurants", "transport" to "Transport", "home" to "Home"),
                listOf("medical" to "Health", "game" to "Entertainment", "present" to "Gifts", "mobile" to "Phone"),
            ).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { i, (icon, name) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBadge(icon, AccentPalette.KEYS[(r * 4 + i * 3) % AccentPalette.KEYS.size], size = 48.dp)
                            TileLabel(name)
                        }
                    }
                }
            }
        }
    },
    GuidePage(
        "Step 3 · Adding", "Add a payment in two taps",
        "Tap + to add an expense: amount, category, done. Hold + and slide up to Income or Transfer without lifting your finger. " +
            "The Quick add widget opens the same screen from your home screen.",
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SamplePill("Transfer", Icons.Outlined.SwapHoriz, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            SamplePill("Income", Icons.Outlined.ArrowUpward, Semantic.income, Color.White)
            SamplePill("Expense", Icons.Outlined.ArrowDownward, Semantic.expense, Color.White)
            Box(Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    },
    GuidePage(
        "Step 4 · Automation", "Let payments come to you",
        "In More, under Automation, turn on Google Wallet payments or Kaspi Alaqan purchases and allow notification access. " +
            "Each payment then arrives as a ready expense and you only pick the category. A daily reminder (8 pm by default) keeps the habit going.",
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Coffee shop · 2 400 ₸", style = MaterialTheme.typography.titleSmall)
                Text("Paid by card. Save as an expense?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge("cafe", AccentPalette.KEYS[3], size = 32.dp)
                    Text("Cafes and restaurants", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    },
    GuidePage(
        "Step 5 · Analysis", "See where it goes, and what's coming",
        "Analysis compares this month with the last three and lists every category. " +
            "Recurring finds bills that repeat and can remind you before they're due.",
    ) {
        val scheme = MaterialTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // This month against the last three; the current month's faded top is the projection to month-end.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Aug" to 0.55f, "Sep" to 0.7f, "Oct" to 0.62f, "Nov" to 0.9f).forEachIndexed { i, (month, h) ->
                    val now = i == 3
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.fillMaxWidth().height(110.dp), verticalArrangement = Arrangement.Bottom) {
                            val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            if (now) {
                                Box(Modifier.fillMaxWidth().height(110.dp * 0.3f).clip(shape).background(scheme.primary.copy(alpha = 0.35f)))
                                Box(Modifier.fillMaxWidth().height(110.dp * 0.6f).background(scheme.primary))
                            } else Box(Modifier.fillMaxWidth().height(110.dp * h).clip(shape).background(scheme.surfaceVariant))
                        }
                        Text(if (now) "$month · projected" else month, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Recurring found 2 bills", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    listOf(Triple("internet", "Internet", "6 990 ₸"), Triple("mobile", "Phone plan", "4 500 ₸")).forEachIndexed { i, (icon, name, amount) ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(icon, AccentPalette.KEYS[(5 + i * 4) % AccentPalette.KEYS.size], size = 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(if (i == 0) "Every month · due in 3 days" else "Every month · due on the 28th", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                            Text(amount, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    },
)

private const val PAGE_MS = 12_000

/**
 * The setup guide: five short "stories" (accounts, categories, adding, automation, analysis). Each page fills its bar at the top and moves on by
 * itself; holding a finger down pauses it, a tap on the left third goes back and elsewhere forward (TalkBack: Next / Previous step actions).
 * With reduced motion nothing moves by itself and pages just swap.
 */
@Composable
fun SetupGuideScreen(onClose: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // With TalkBack on, pages never move by themselves: the screen reader would lose its place. Back / Next still work.
    val talkBack = remember { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    val autoAdvance = !reduceMotion && !talkBack
    var page by rememberSaveable { mutableStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember(page) { Animatable(0f) }
    val last = PAGES.lastIndex
    LaunchedEffect(page, paused, autoAdvance) {
        if (!autoAdvance || paused) return@LaunchedEffect
        progress.animateTo(1f, tween(((1f - progress.value) * PAGE_MS).toInt(), easing = LinearEasing))
        if (page < last) page++
    }
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.surface).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp).testTag("setup-guide"),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PAGES.indices.forEach { i ->
                val fill = when { i < page -> 1f; i > page -> 0f; !autoAdvance -> 1f; else -> progress.value }
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurface.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(fill).height(3.dp).background(scheme.primary))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${page + 1} of ${PAGES.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            IconButton(onClose, Modifier.testTag("guide-skip")) { Icon(Icons.Outlined.Close, "Close guide") }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().testTag("guide-pages")
                .semantics { // TalkBack has no tap zones: the same moves as actions on the page
                    customActions = listOf(
                        CustomAccessibilityAction("Next step") { if (page < last) { page++; true } else false },
                        CustomAccessibilityAction("Previous step") { if (page > 0) { page--; true } else false },
                    )
                }
                .pointerInput(last) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onLongPress = {}, // a hold only pauses: without this, letting go after any hold counts as a tap and turns the page
                    onTap = { if (it.x < size.width / 3f) page = (page - 1).coerceAtLeast(0) else if (page < last) page++ },
                )
            },
        ) {
            AnimatedContent(
                page, label = "guide-page",
                transitionSpec = {
                    if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 12 }) togetherWith fadeOut(tween(160))
                },
            ) { shown ->
                val p = PAGES[shown]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(p.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    Text(p.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(p.body, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    p.picture()
                }
            }
        }
        if (page == last) Button(onClose, Modifier.fillMaxWidth().padding(top = 12.dp).testTag("guide-start")) { Text("Start tracking") }
    }
}

@Composable
private fun SampleRow(icon: String, color: Int, title: String, subtitle: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon, AccentPalette.KEYS[color % AccentPalette.KEYS.size], size = 40.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SamplePill(label: String, icon: ImageVector, bubble: Color, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label, Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Box(Modifier.size(44.dp).clip(CircleShape).background(bubble), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
    }
}
