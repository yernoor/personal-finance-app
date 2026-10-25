package dev.yernur.moneytracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon names stored in the backup (`category.icon`, `account.icon`) mapped to Material icons; unknown names fall back. */
object AppIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "other" to Icons.AutoMirrored.Outlined.HelpOutline,
        "cash" to Icons.Outlined.Payments,
        "purse" to Icons.Outlined.AccountBalanceWallet,
        "bank" to Icons.Outlined.AccountBalance,
        "deposit" to Icons.Outlined.Savings,
        "piggy_bank" to Icons.Outlined.Savings,
        "dollar" to Icons.Outlined.AttachMoney,
        "prepaid" to Icons.Outlined.CreditCard,
        "present" to Icons.Outlined.CardGiftcard,
        "transport" to Icons.Outlined.DirectionsBus,
        "car" to Icons.Outlined.DirectionsCar,
        "cafe" to Icons.Outlined.Restaurant,
        "drinks" to Icons.Outlined.LocalCafe,
        "products" to Icons.Outlined.ShoppingBasket,
        "return" to Icons.Outlined.Replay,
        "girl" to Icons.Outlined.ChildCare,
        "family" to Icons.Outlined.FamilyRestroom,
        "home" to Icons.Outlined.Home,
        "heart" to Icons.Outlined.MonitorHeart,
        "medical" to Icons.Outlined.MedicalServices,
        "haircut" to Icons.Outlined.ContentCut,
        "clothes" to Icons.Outlined.Checkroom,
        "camping" to Icons.Outlined.Cabin,
        "mobile" to Icons.Outlined.PhoneAndroid,
        "tax" to Icons.Outlined.ReceiptLong,
        "education" to Icons.Outlined.School,
        "book" to Icons.Outlined.MenuBook,
        "sport" to Icons.Outlined.FitnessCenter,
        "game" to Icons.Outlined.SportsEsports,
        "pets" to Icons.Outlined.Pets,
        "travels" to Icons.Outlined.Flight,
        "mustache" to Icons.Outlined.Face,
    )

    fun get(name: String?): ImageVector = all[name] ?: Icons.Outlined.Category
}

/** ARGB colors of the original's palette, as stored in the backup (signed Int). */
val CategoryPalette: List<Int> = listOf(
    0xFFE00000, 0xFFF53535, 0xFFEB5ACD, 0xFFB01DFF, 0xFF3078D0, 0xFF73B6E4,
    0xFF61AE49, 0xFF99B290, 0xFFF1CE06, 0xFFEBB20B, 0xFFE0731F, 0xFF222222,
).map { it.toInt() }

fun colorOf(argb: Int?, fallback: Color = Color(0xFF7A8C84)): Color = if (argb != null) Color(argb) else fallback
