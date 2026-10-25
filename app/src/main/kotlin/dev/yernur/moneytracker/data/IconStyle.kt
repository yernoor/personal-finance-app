package dev.yernur.moneytracker.data

/**
 * How category and account icons are drawn. [Glass] is not a choice: it is used automatically while liquid glass is on,
 * and the three others are the owner's options (settings).
 */
enum class IconStyle(val label: String) {
    Tonal("Tonal tiles"),
    Vivid("Vivid discs"),
    Duotone("Duotone"),
    Glass("Glass"),
}
