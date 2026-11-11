package dev.yernur.moneytracker.data

/** The animations the owner can switch off one by one (More -> Animations). All are on by default. */
enum class AnimationKind(val id: String, val label: String, val description: String) {
    CountUp("countUp", "Count up balances when a screen opens", "Balances show their value at once instead of counting up from zero"),
    RollChange("rollChange", "Roll balances when they change", "After adding an entry the balance rolls to its new value"),
    Charts("charts", "Draw charts in", "Rings and bars appear fully drawn"),
    PeriodSwitch("periodSwitch", "Period switch animation", "Charts morph when you change the day, week, month or year"),
    Screens("screens", "Screen transitions", "Screens swap with a quick fade instead of sliding and growing");

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}
