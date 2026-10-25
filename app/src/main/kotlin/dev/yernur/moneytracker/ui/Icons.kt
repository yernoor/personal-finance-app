package dev.yernur.moneytracker.ui

import androidx.compose.ui.graphics.Color
import dev.yernur.moneytracker.data.AccentPalette

/**
 * One icon, drawn on a 24 x 24 grid with round strokes. [body] holds the closed shapes (the duotone style fills them);
 * [detail] holds lines and small marks. Original artwork: no Material or third-party icon paths.
 */
class Glyph(val body: String, val detail: String = "")

/** Icon names stored in the backup (`category.icon`, `account.icon`) plus a few extras for new categories. */
object AppIcons {
    private val glyphs: Map<String, Glyph> = linkedMapOf(
        "other" to Glyph(
            "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0 -17z",
            "M9.6 9.6a2.5 2.5 0 1 1 3.6 2.2c-.8 .5 -1.2 1 -1.2 1.9M12 16.6h.01",
        ),
        "cash" to Glyph(
            "M5 7h14a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1 -1.5 1.5H5a1.5 1.5 0 0 1 -1.5 -1.5v-7A1.5 1.5 0 0 1 5 7z",
            "M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0 -5zM6.5 10v4M17.5 10v4",
        ),
        "purse" to Glyph(
            "M5.5 7h13A1.5 1.5 0 0 1 20 8.5v8a1.5 1.5 0 0 1 -1.5 1.5h-13A1.5 1.5 0 0 1 4 16.5v-8A1.5 1.5 0 0 1 5.5 7z",
            "M15.5 13.2H18M7 7V5.9a1.4 1.4 0 0 1 1.4 -1.4H16",
        ),
        "bank" to Glyph("M12 3.5 3.5 8.2V10h17V8.2z", "M6 12.5v5M10 12.5v5M14 12.5v5M18 12.5v5M4 19.5h16"),
        "deposit" to Glyph(
            "M5 5.5h14a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1 -1.5 1.5H5A1.5 1.5 0 0 1 3.5 17V7A1.5 1.5 0 0 1 5 5.5z",
            "M12 8.7a3.1 3.1 0 1 0 0 6.2 3.1 3.1 0 0 0 0 -6.2zM12 10.5v2.2M7 18.5v1.8M17 18.5v1.8",
        ),
        "piggy_bank" to Glyph(
            "M5.2 12.3c0 -3.1 2.8 -5.6 6.2 -5.6h2.4c.9 0 1.7 .2 2.4 .5l2.4 -1.3v3.1c.8 .8 1.3 1.8 1.3 3 0 1.2 -.4 2.1 -1.1 2.9V19h-3v-1.5h-4.9V19h-3v-2.3c-1.5 -.9 -2.7 -2.4 -2.7 -4.4z",
            "M15.6 11.1h.01M9.5 9.1h3.6M5.2 11.6c-1.1 0 -1.8 .6 -1.8 1.6",
        ),
        "dollar" to Glyph(
            "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0 -17z",
            "M14.6 9.4c-.4 -.9 -1.4 -1.4 -2.6 -1.4 -1.5 0 -2.6 .8 -2.6 2 0 2.8 5.4 1.4 5.4 4.2 0 1.2 -1.1 2 -2.8 2 -1.2 0 -2.3 -.5 -2.8 -1.5M12 6.2V8M12 16v1.8",
        ),
        "prepaid" to Glyph(
            "M4.5 6h15A1.5 1.5 0 0 1 21 7.5v9a1.5 1.5 0 0 1 -1.5 1.5h-15A1.5 1.5 0 0 1 3 16.5v-9A1.5 1.5 0 0 1 4.5 6z",
            "M3 10.5h18M6.5 15h4",
        ),
        "present" to Glyph(
            "M4 11h16v9H4zM3 7.5h18V11H3z",
            "M12 7.5V20M12 7.5C10 3.5 6.5 4.5 7.5 7c.4 1 2.5 .5 4.5 .5M12 7.5c2 -4 5.5 -3 4.5 -.5 -.4 1 -2.5 .5 -4.5 .5",
        ),
        "transport" to Glyph(
            "M5.5 4h13A1.5 1.5 0 0 1 20 5.5v10a1.5 1.5 0 0 1 -1.5 1.5h-13A1.5 1.5 0 0 1 4 15.5v-10A1.5 1.5 0 0 1 5.5 4z",
            "M4 10.5h16M7.5 13.8h.01M16.5 13.8h.01M6.5 17v2.3M17.5 17v2.3",
        ),
        "car" to Glyph(
            "M3.5 13.5a1 1 0 0 1 1 -1h15a1 1 0 0 1 1 1V17a1 1 0 0 1 -1 1h-15a1 1 0 0 1 -1 -1z",
            "M5.5 12.5 7.1 8.4A2 2 0 0 1 9 7h6a2 2 0 0 1 1.9 1.4l1.6 4.1M7 18v1.8M17 18v1.8M6.8 15.2h.01M17.2 15.2h.01",
        ),
        "cafe" to Glyph(
            "M16.5 3.5c-2 1.4 -3.1 3.4 -3.1 5.7 0 1.6 .9 2.6 3.1 2.8z",
            "M16.5 3.5V20.5M6.5 3.5v5.2a2.4 2.4 0 0 0 4.8 0V3.5M8.9 3.5v17",
        ),
        "drinks" to Glyph(
            "M5 9h11v5a4 4 0 0 1 -4 4H9a4 4 0 0 1 -4 -4z",
            "M16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 3.5v2M11 3.5v2",
        ),
        "products" to Glyph(
            "M3.5 10h17l-1.6 8.2a1.5 1.5 0 0 1 -1.5 1.2H6.6a1.5 1.5 0 0 1 -1.5 -1.2L3.5 10z",
            "M8 10l3 -6M16 10l-3 -6M9 13v3M12 13v3M15 13v3",
        ),
        "return" to Glyph(
            "M12 9.2a2.8 2.8 0 1 0 0 5.6 2.8 2.8 0 0 0 0 -5.6z",
            "M5.2 11.5a7 7 0 0 1 12 -4.3M18.8 12.5a7 7 0 0 1 -12 4.3M18.5 3.8v3.7H14.8M5.5 20.2v-3.7h3.7",
        ),
        // A girl: round face, a bow on top, pigtails hanging at the sides
        "girl" to Glyph(
            "M12 8.4a5 5 0 1 0 0 10 5 5 0 0 0 0 -10zM12 5.7 8.7 3.9v3.6zM12 5.7l3.3 -1.8v3.6z",
            "M7.5 12.4c-1.7 .9 -2.5 2.7 -2.3 4.6M16.5 12.4c1.7 .9 2.5 2.7 2.3 4.6M10.2 13.4h.01M13.8 13.4h.01M10.3 16c.9 .7 2.5 .7 3.4 0",
        ),
        "family" to Glyph(
            "M8.5 4.5a2.3 2.3 0 1 0 0 4.6 2.3 2.3 0 0 0 0 -4.6zM17 8.2a1.8 1.8 0 1 0 0 3.6 1.8 1.8 0 0 0 0 -3.6z",
            "M4.5 19.5v-4.2a4 4 0 0 1 8 0v4.2M14.5 19.5v-3a2.5 2.5 0 0 1 5 0v3",
        ),
        "home" to Glyph("M4 11 12 4l8 7v8a1 1 0 0 1 -1 1h-4v-6H9v6H5a1 1 0 0 1 -1 -1v-8z"),
        "heart" to Glyph(
            "M12 20s-7.5 -4.4 -7.5 -10A4.2 4.2 0 0 1 12 7.6 4.2 4.2 0 0 1 19.5 10c0 5.6 -7.5 10 -7.5 10z",
            "M7 12h2.5l1.2 -2 1.8 4 1.2 -2H17",
        ),
        "medical" to Glyph(
            "M4.5 8h15a1 1 0 0 1 1 1v9a1 1 0 0 1 -1 1h-15a1 1 0 0 1 -1 -1V9a1 1 0 0 1 1 -1z",
            "M9 8V6a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v2M12 11v5M9.5 13.5h5",
        ),
        "haircut" to Glyph(
            "M6.5 14.9a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0 -4.8zM17.5 14.9a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0 -4.8z",
            "M8.2 15.7 17.5 4M15.8 15.7 6.5 4",
        ),
        "clothes" to Glyph("M8.6 4 4 6.6l1.8 4 2.2 -1V20h8V9.6l2.2 1 1.8 -4L15.4 4a3.5 3.5 0 0 1 -6.8 0z"),
        "camping" to Glyph("M3.5 19.5 12 5l8.5 14.5z", "M12 5v3M9.8 19.5 12 15.2l2.2 4.3"),
        "mobile" to Glyph(
            "M7.5 3.5h9A1.5 1.5 0 0 1 18 5v14a1.5 1.5 0 0 1 -1.5 1.5h-9A1.5 1.5 0 0 1 6 19V5a1.5 1.5 0 0 1 1.5 -1.5z",
            "M10.5 6h3M12 18h.01",
        ),
        "tax" to Glyph(
            "M6 3.5h12V20l-2 -1.4 -2 1.4 -2 -1.4 -2 1.4 -2 -1.4 -2 1.4z",
            "M9.5 9.5l5 5M9.6 9.6h.01M14.4 14.4h.01",
        ),
        "education" to Glyph(
            "M12 5 2.5 9.5 12 14l9.5 -4.5z",
            "M6.5 11.8v4.2c1.4 1.3 3.2 2 5.5 2s4.1 -.7 5.5 -2v-4.2M21.5 9.5v5.5",
        ),
        "book" to Glyph("M4 5.5h6A2 2 0 0 1 12 7.5V19a2 2 0 0 0 -2 -1.5H4zM20 5.5h-6A2 2 0 0 0 12 7.5V19a2 2 0 0 1 2 -1.5h6z"),
        "sport" to Glyph("M6.5 7.5h2v9h-2zM15.5 7.5h2v9h-2z", "M8.5 12h7M4 9.5v5M20 9.5v5"),
        "game" to Glyph(
            "M7.5 8h9a4.5 4.5 0 0 1 4.4 3.6l.6 3.4a2.5 2.5 0 0 1 -4.3 2l-1.7 -2.2H8.5l-1.7 2.2a2.5 2.5 0 0 1 -4.3 -2l.6 -3.4A4.5 4.5 0 0 1 7.5 8z",
            "M8 10.8v3M6.5 12.3h3M15.5 11.8h.01M17.5 13.4h.01",
        ),
        "pets" to Glyph(
            "M12 12.2c-2.8 0 -5 2.3 -5 4.4 0 1.4 1 2.2 2.4 2.2 1 0 1.7 -.4 2.6 -.4s1.6 .4 2.6 .4c1.4 0 2.4 -.8 2.4 -2.2 0 -2.1 -2.2 -4.4 -5 -4.4z",
            "M7 8.2a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2zM10.3 4.7a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2zM13.7 4.7a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2zM17 8.2a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0 -3.2z",
        ),
        "travels" to Glyph("M20.5 4 3.5 11l6.2 2.3L12 20l2.7 -5.6L20.5 4z", "M9.7 13.3 20.5 4"),
        "mustache" to Glyph(
            "M12 11c-1.5 -1.2 -3 -1.6 -4.7 -1.2C5 10.3 3.5 11.8 3 14c1.6 -1 3.2 -1.3 4.8 -.3 1.4 .8 2.9 1.2 4.2 -.7 1.3 1.9 2.8 1.5 4.2 .7 1.6 -1 3.2 -.7 4.8 .3 -.5 -2.2 -2 -3.7 -4.3 -4.2C15 9.4 13.5 9.8 12 11z",
        ),
    )

    /** Glyphs for the app's own controls; they are not category icons, so the pickers do not list them. */
    private val controls: Map<String, Glyph> = mapOf("add" to Glyph("", "M12 5v14M5 12h14"))

    /** Every icon name, in the order the pickers show them. */
    val names: List<String> = glyphs.keys.toList()

    /** Unknown names (e.g. from a newer backup) get the question-mark glyph. */
    fun glyph(name: String?): Glyph = glyphs[name] ?: controls[name] ?: glyphs.getValue("other")
}

/** The 12 color keys as stored in the backup and shown in the color picker (see AccentPalette for what they look like). */
val CategoryPalette: List<Int> = AccentPalette.KEYS
