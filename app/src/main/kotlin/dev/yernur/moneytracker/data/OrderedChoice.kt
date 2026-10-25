package dev.yernur.moneytracker.data

/** A chosen, ordered subset of a fixed pool: which tabs the navbar shows, which cards Home shows, and in what order. */
object OrderedChoice {
    /** Drops unknown and duplicate ids; falls back to [default] when what is stored no longer fits [min]..[max]. */
    fun sanitize(stored: List<String>?, pool: List<String>, default: List<String>, min: Int, max: Int): List<String> {
        val clean = stored.orEmpty().filter { it in pool }.distinct()
        return if (clean.size in min..max) clean else default
    }

    /** Hides a shown item (never below [min]) or appends a hidden one (never above [max]). */
    fun toggle(visible: List<String>, id: String, min: Int, max: Int): List<String> = when {
        id in visible -> if (visible.size > min) visible - id else visible
        visible.size < max -> visible + id
        else -> visible
    }

    fun move(visible: List<String>, id: String, by: Int): List<String> {
        val from = visible.indexOf(id)
        val to = from + by
        if (from < 0 || to !in visible.indices) return visible
        return visible.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    /** Shown items in their order, then the hidden ones in pool order, each with its shown flag. */
    fun arranged(visible: List<String>, pool: List<String>): List<Pair<String, Boolean>> =
        visible.map { it to true } + pool.filter { it !in visible }.map { it to false }
}
