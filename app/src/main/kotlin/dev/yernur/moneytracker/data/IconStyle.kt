package dev.yernur.moneytracker.data

/**
 * How category and account icons are drawn. Two sets: the three everyday looks, and the two that go with liquid glass.
 * With liquid glass on only the glass looks are used (the owner picks between them); with it off, only the others.
 */
enum class IconStyle(val label: String, val forGlass: Boolean) {
    Tonal("Tonal tiles", false),
    Vivid("Vivid discs", false),
    Duotone("Duotone", false),
    GlassTile("Frosted tile", true),
    GlassClear("Clear glass", true),
}
