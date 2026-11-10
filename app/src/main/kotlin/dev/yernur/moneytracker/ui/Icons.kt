package dev.yernur.moneytracker.ui

import androidx.compose.ui.graphics.Color
import dev.yernur.moneytracker.data.AccentPalette

/**
 * One icon, drawn on a 24 x 24 grid with round strokes. [body] holds the closed shapes (every style fills them);
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
        // Stored as "girl" in the backup; drawn as love: a big heart with a small one floating at its upper right
        "girl" to Glyph(
            "M9.5 21s-6.5 -3.9 -6.5 -9A3.6 3.6 0 0 1 9.5 9.8 3.6 3.6 0 0 1 16 12c0 5.1 -6.5 9 -6.5 9zM18.4 10.2s-3.6 -2.1 -3.6 -4.7A2 2 0 0 1 18.4 4.4 2 2 0 0 1 22 5.5c0 2.6 -3.6 4.7 -3.6 4.7z",
            "M6.2 12.6c0 -1.1 .8 -1.9 1.9 -1.9",
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
        // Added 2026-11-11 (redesign): money, home, food, travel, health, shopping, leisure, people and work. Shapes drawn blind, to be checked on screen.
        "tenge" to Glyph("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0 -17z", "M8 8.5h8M8 11h8M12 11v6"),
        "coins" to Glyph("M12 4c4.1 0 7.5 1.3 7.5 3s-3.4 3 -7.5 3 -7.5 -1.3 -7.5 -3 3.4 -3 7.5 -3z", "M4.5 7v5c0 1.7 3.4 3 7.5 3s7.5 -1.3 7.5 -3V7M4.5 12v5c0 1.7 3.4 3 7.5 3s7.5 -1.3 7.5 -3v-5"),
        "invest" to Glyph("M4 14h3.5v6H4zM10.25 10h3.5v10h-3.5zM16.5 7h3.5v13h-3.5z", "M4 9.5l4 -4 3 2 3.5 -3.5"),
        "interest" to Glyph("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0 -17z", "M9 15l6 -6M9.3 9.3h.01M14.7 14.7h.01"),
        "salary" to Glyph("M4.5 8h15A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1 -1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-8A1.5 1.5 0 0 1 4.5 8z", "M9 8V6.5A1.5 1.5 0 0 1 10.5 5h3A1.5 1.5 0 0 1 15 6.5V8M3 12.5h18M12 11.5v2"),
        "bonus" to Glyph("M12 3.5l2.6 5.3 5.9 .9 -4.2 4.1 1 5.8 -5.3 -2.8 -5.3 2.8 1 -5.8 -4.2 -4.1 5.9 -.9z", ""),
        "transfer" to Glyph("", "M4 8.5h14l-3.5 -3.5M20 15.5H6l3.5 3.5"),
        "withdrawal" to Glyph("", "M12 4v10M8 10.5l4 4 4 -4M4.5 16v2.5A1.5 1.5 0 0 0 6 20h12a1.5 1.5 0 0 0 1.5 -1.5V16"),
        "bills" to Glyph("M6 3.5h12V20l-2 -1.4 -2 1.4 -2 -1.4 -2 1.4 -2 -1.4 -2 1.4z", "M9 8h6M9 11.5h6M9 15h3.5"),
        "fines" to Glyph("M12 4l9 15.5H3z", "M12 10v4.5M12 17h.01"),
        "debt" to Glyph("M7 3.5h10V7l-4 5 4 5v3.5H7V17l4 -5 -4 -5z", "M6 3.5h12M6 20.5h12"),
        "insurance" to Glyph("M12 3.5l7.5 2.5v5.5c0 4.5 -3 7.7 -7.5 9 -4.5 -1.3 -7.5 -4.5 -7.5 -9V6z", "M8.8 12l2.3 2.3 4.2 -4.6"),
        "goal" to Glyph("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0 -17z", "M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0 -9zM12 12h.01"),
        "rent" to Glyph("M8 9a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z", "M12 13h8.5M17.5 13v3M20.5 13v2M8 13h.01"),
        "apartment" to Glyph("M6 3.5h12v17H6z", "M9.5 7.5h.01M14.5 7.5h.01M9.5 11.5h.01M14.5 11.5h.01M10.5 20.5V16h3v4.5"),
        "electricity" to Glyph("M13 3 5.5 13.5H11l-1 7.5 7.5 -10.5H12z", ""),
        "water" to Glyph("M12 3.5c3.5 4.2 5.5 7.2 5.5 10a5.5 5.5 0 0 1 -11 0c0 -2.8 2 -5.8 5.5 -10z", "M9.5 14a2.5 2.5 0 0 0 2.5 2.5"),
        "gas" to Glyph("M12 3.5c.5 3 4.5 5 4.5 9.5a4.5 4.5 0 0 1 -9 0c0 -1.8 .8 -3 1.8 -4 .2 1.5 1 2.3 1.7 2.5 -.5 -3 0 -6 1 -8z", ""),
        "internet" to Glyph("M12 17a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z", "M4 10a11.5 11.5 0 0 1 16 0M6.8 13a7.5 7.5 0 0 1 10.4 0M9.5 15.8a3.6 3.6 0 0 1 5 0"),
        "furniture" to Glyph("M5 11.5a2 2 0 0 1 2 2V15h10v-1.5a2 2 0 0 1 4 0V18H3v-4.5a2 2 0 0 1 2 -2z", "M6 11.5V8a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v3.5M6 18v1.5M18 18v1.5"),
        "laundry" to Glyph("M6 3.5h12A1.5 1.5 0 0 1 19.5 5v14a1.5 1.5 0 0 1 -1.5 1.5H6A1.5 1.5 0 0 1 4.5 19V5A1.5 1.5 0 0 1 6 3.5z", "M12 9.5a4 4 0 1 0 0 8 4 4 0 0 0 0 -8zM7.5 6.5h.01M10.5 6.5h.01"),
        "lighting" to Glyph("M12 3.5a6 6 0 0 0 -3.5 10.9V16h7v-1.6A6 6 0 0 0 12 3.5z", "M9.5 18.5h5M10.5 21h3"),
        "security" to Glyph("M6.5 10.5h11A1.5 1.5 0 0 1 19 12v7a1.5 1.5 0 0 1 -1.5 1.5h-11A1.5 1.5 0 0 1 5 19v-7a1.5 1.5 0 0 1 1.5 -1.5z", "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5M12 14.5V17"),
        "restaurant" to Glyph("M4 16a8 8 0 0 1 16 0z", "M3 18.5h18M12 8V6"),
        "fastfood" to Glyph("M4.5 10.5a7.5 5.5 0 0 1 15 0zM4.5 16.5h15v1A2.5 2.5 0 0 1 17 20H7a2.5 2.5 0 0 1 -2.5 -2.5z", "M3.5 13.5h17"),
        "pizza" to Glyph("M12 21 4 6.5a16 16 0 0 1 16 0z", "M10 9.5h.01M13.5 12h.01M11.5 15h.01"),
        "coffee" to Glyph("M7 8h10l-1.2 11.5a1.5 1.5 0 0 1 -1.5 1.3H9.7a1.5 1.5 0 0 1 -1.5 -1.3z", "M6 8h12M8 8l.7 -3h6.6l.7 3M7.7 13h8.6"),
        "bakery" to Glyph("M7 5h10a3.5 3.5 0 0 1 1.5 6.6V18a1.5 1.5 0 0 1 -1.5 1.5H7A1.5 1.5 0 0 1 5.5 18v-6.4A3.5 3.5 0 0 1 7 5z", "M9.5 9v3M12 9v3M14.5 9v3"),
        "alcohol" to Glyph("M7.5 3.5h9v5a4.5 4.5 0 0 1 -9 0z", "M12 13v7.5M8.5 20.5h7"),
        "bar" to Glyph("M4.5 5h15L12 13z", "M12 13v7.5M8.5 20.5h7M15 5l2 -2.5"),
        "delivery" to Glyph("M12 3.5l8 4v9l-8 4 -8 -4v-9z", "M4 7.5l8 4 8 -4M12 11.5v9M8 5.5l8 4"),
        "fuel" to Glyph("M5 4.5h8a1 1 0 0 1 1 1V20H4V5.5a1 1 0 0 1 1 -1z", "M6.5 8h5v3.5h-5zM3 20h12M14 11h1.5a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 0 3 0V9l-2.5 -2.5"),
        "parking" to Glyph("M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2z", "M9.5 17V7.5H13a3 3 0 0 1 0 6H9.5"),
        "train" to Glyph("M7 3.5h10a2 2 0 0 1 2 2V15a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V5.5a2 2 0 0 1 2 -2z", "M5 10.5h14M8.5 13.8h.01M15.5 13.8h.01M8 17l-2 3.5M16 17l2 3.5M10 6.5h4"),
        "flight" to Glyph("M12 3c.8 0 1.5 1 1.5 2.5V9l7 4v2l-7 -2v4.5l2 1.5v1.5L12 19.5l-3.5 1V19l2 -1.5V13l-7 2v-2l7 -4V5.5C10.5 4 11.2 3 12 3z", ""),
        "luggage" to Glyph("M7 8h10a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 17 20H7a1.5 1.5 0 0 1 -1.5 -1.5v-9A1.5 1.5 0 0 1 7 8z", "M9.5 8V5a1 1 0 0 1 1 -1h3a1 1 0 0 1 1 1v3M9.5 11.5v5M14.5 11.5v5"),
        "location" to Glyph("M12 21s-6.5 -6 -6.5 -11a6.5 6.5 0 0 1 13 0c0 5 -6.5 11 -6.5 11z", "M12 7.8a2.3 2.3 0 1 0 0 4.6 2.3 2.3 0 0 0 0 -4.6z"),
        "vacation" to Glyph("M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z", "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4l1.4 -1.4M17 7l1.4 -1.4"),
        "pharmacy" to Glyph("M12 3a4.5 4.5 0 0 1 4.5 4.5v9a4.5 4.5 0 0 1 -9 0v-9A4.5 4.5 0 0 1 12 3z", "M7.5 12h9"),
        "thermometer" to Glyph("M12 14.5a3 3 0 1 0 0 6 3 3 0 0 0 0 -6z", "M10.5 15V5a1.5 1.5 0 0 1 3 0v10M12 17.5V9"),
        "baby" to Glyph("M8.5 9h7v10a1.5 1.5 0 0 1 -1.5 1.5h-4A1.5 1.5 0 0 1 8.5 19z", "M9.5 9V7.5h5V9M11 7.5V5.5a1 1 0 0 1 2 0v2M8.5 13h3M8.5 16h3"),
        "shopping" to Glyph("M5.5 8h13l1 12h-15z", "M9 11V7a3 3 0 0 1 6 0v4"),
        "supermarket" to Glyph("M6 6h14.5l-1.8 8h-11z", "M3 4h2l1 2M9 17.5a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4zM17 17.5a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0 -2.4z"),
        "electronics" to Glyph("M5.5 5.5h13a1 1 0 0 1 1 1v9h-15v-9a1 1 0 0 1 1 -1z", "M2.5 18.5h19M10 18.5h4"),
        "tv" to Glyph("M4.5 5h15A1.5 1.5 0 0 1 21 6.5v9a1.5 1.5 0 0 1 -1.5 1.5h-15A1.5 1.5 0 0 1 3 15.5v-9A1.5 1.5 0 0 1 4.5 5z", "M8.5 20.5h7M12 17v3.5"),
        "camera" to Glyph("M4.5 8h3L9 5.5h6L16.5 8h3A1.5 1.5 0 0 1 21 9.5v8a1.5 1.5 0 0 1 -1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-8A1.5 1.5 0 0 1 4.5 8z", "M12 10.5a3 3 0 1 0 0 6 3 3 0 0 0 0 -6z"),
        "music" to Glyph("M4.5 14H7v5.5H6A1.5 1.5 0 0 1 4.5 18zM17 14h2.5v4a1.5 1.5 0 0 1 -1.5 1.5h-1z", "M4.5 14v-2.5a7.5 7.5 0 0 1 15 0V14"),
        "watch" to Glyph("M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0 -10z", "M9.5 7.5L10 3.5h4l.5 4M9.5 16.5l.5 4h4l.5 -4M12 10v2l1.5 1"),
        "jewelry" to Glyph("M7 4.5h10l4 5 -9 10.5L3 9.5z", "M3 9.5h18M9 9.5l3 10.5 3 -10.5M9 9.5l1.5 -5M15 9.5l-1.5 -5"),
        "stationery" to Glyph("M15.5 4l4.5 4.5L9 19.5l-5.5 1 1 -5.5z", "M13 6.5l4.5 4.5"),
        "sale" to Glyph("M4 4h7.5l8.5 8.5 -7.5 7.5L4 11.5z", "M8 8h.01"),
        "tickets" to Glyph("M4 7h16v3.5a1.5 1.5 0 0 0 0 3V17H4v-3.5a1.5 1.5 0 0 0 0 -3z", "M14.5 7v2M14.5 11v2M14.5 15v2"),
        "cinema" to Glyph("M5.5 4.5h13a1 1 0 0 1 1 1v13a1 1 0 0 1 -1 1h-13a1 1 0 0 1 -1 -1v-13a1 1 0 0 1 1 -1z", "M8.5 4.5v15M15.5 4.5v15M4.5 9h4M4.5 15h4M15.5 9h4M15.5 15h4M8.5 12h7"),
        "concerts" to Glyph("M7 15a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0 -5zM17 13a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0 -5z", "M9.5 17.5V6l10 -2v11.5M9.5 9.5l10 -2"),
        "party" to Glyph("M12 3.5a6 6 0 0 1 6 6c0 3.5 -3 6.5 -6 6.5s-6 -3 -6 -6.5a6 6 0 0 1 6 -6z", "M11 16l-1 2h4l-1 -2M12 18c0 1.5 1.5 1.5 1.5 3"),
        "streaming" to Glyph("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2z", "M10 9v6l5 -3z"),
        "friends" to Glyph("M8 5a3 3 0 1 0 0 6 3 3 0 0 0 0 -6zM16.5 6.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0 -5z", "M2.5 19.5v-1a5.5 5.5 0 0 1 11 0v1M15.5 14.2a4.5 4.5 0 0 1 6 4.3v1"),
        "personal" to Glyph("M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0 -8z", "M4.5 20.5v-1a7.5 7.5 0 0 1 15 0v1"),
        "charity" to Glyph("M12 20s-7.5 -4.4 -7.5 -10A4.2 4.2 0 0 1 12 7.6 4.2 4.2 0 0 1 19.5 10c0 5.6 -7.5 10 -7.5 10z", "M12 10.5V15M9.75 12.75h4.5"),
        "courses" to Glyph("M5 4.5h14a1 1 0 0 1 1 1V15a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V5.5a1 1 0 0 1 1 -1z", "M8 8.5h8M8 11.5h5M15 16v4.5l1.75 -1.2 1.75 1.2V16"),
        "business" to Glyph("M4 5h16v10.5H4z", "M3 5h18M12 15.5V18M8.5 20.5L12 18l3.5 2.5M8 12l2.5 -2.5 2 1.5 3 -3"),
        "software" to Glyph("M4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1 -1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11A1.5 1.5 0 0 1 4.5 5z", "M9.5 9.5L7 12l2.5 2.5M14.5 9.5L17 12l-2.5 2.5"),
        "documents" to Glyph("M7 3.5h7l4.5 4.5v11a1.5 1.5 0 0 1 -1.5 1.5H7A1.5 1.5 0 0 1 5.5 19V5A1.5 1.5 0 0 1 7 3.5z", "M14 3.5V8h4.5M9 12.5h6M9 16h4"),
        "legal" to Glyph("M3.5 13l3 -6 3 6a3 3 0 0 1 -6 0zM14.5 13l3 -6 3 6a3 3 0 0 1 -6 0z", "M6.5 7h11M12 4.5v16M8.5 20.5h7"),
        "mail" to Glyph("M4.5 6h15A1.5 1.5 0 0 1 21 7.5v9a1.5 1.5 0 0 1 -1.5 1.5h-15A1.5 1.5 0 0 1 3 16.5v-9A1.5 1.5 0 0 1 4.5 6z", "M3.5 7.5L12 13l8.5 -5.5"),
        "communication" to Glyph("M5.5 4.5h13A1.5 1.5 0 0 1 20 6v9a1.5 1.5 0 0 1 -1.5 1.5H11l-4.5 4v-4h-1A1.5 1.5 0 0 1 4 15V6a1.5 1.5 0 0 1 1.5 -1.5z", "M8 9h8M8 12.5h5"),
        "calendar" to Glyph("M5.5 5.5h13A1.5 1.5 0 0 1 20 7v11.5a1.5 1.5 0 0 1 -1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5V7a1.5 1.5 0 0 1 1.5 -1.5z", "M4 10h16M8.5 3.5v4M15.5 3.5v4M8 14h.01M12 14h.01M16 14h.01M8 17h.01M12 17h.01"),
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
