package dev.yernur.moneytracker.data

/**
 * How category and account icons are drawn: iOS-style tiles. Two sets, one per theme mode.
 * With liquid glass on only the glass looks are used (the owner picks between them); with it off, only the others.
 */
enum class IconStyle(val label: String, val forGlass: Boolean) {
    Solid("Solid", false),
    Tonal("Tonal", false),
    Outline("Outline", false),
    Mono("Mono", false),
    Sticker("Sticker", false),
    LiquidTile("Liquid tile", true),
    ClearGlass("Clear glass", true),
}
