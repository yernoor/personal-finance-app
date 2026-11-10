package dev.yernur.moneytracker.data

/** The three looks of the home-screen widgets. All follow light/dark mode; only [NothingOs] uses the dot-matrix numerals. */
enum class WidgetStyle(val label: String, val description: String, val dotNumerals: Boolean) {
    NothingOs("Nothing OS", "Very light translucent cards and dot-matrix numbers", true),
    Glass("Glass", "Translucent frosted cards, system font", false),
    Usual("Usual", "Opaque cards, system font", false),
}
