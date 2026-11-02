package dev.yernur.moneytracker.data.sheets

/** One tab of the spreadsheet. [dateColumn] (0-based) holds dates that are written as serial numbers and shown as yyyy-mm-dd. */
class TabSpec(val title: String, val headers: List<String>, val hidden: Boolean = false, val dateColumn: Int? = null) {
    /** The tab's own columns in A1 notation (`'Tags'!A:B`); anything the owner added to the right of them is not ours to touch. */
    val columnsRange: String get() = "'$title'!A:${'A' + headers.size - 1}"
}

/**
 * The spreadsheet's tabs and column order, exactly as docs/SYNC_DESIGN.md lists them (a test compares the two).
 * `Transactions` keeps the order of the owner's converter so existing SUMIFS formulas keep working.
 */
object SheetLayout {
    val TRANSACTIONS = TabSpec(
        "Transactions",
        listOf("Date", "Type", "Account", "Category", "Tags", "Amount", "Currency", "Net (KZT)", "Comment", "Created", "Modified", "UID"),
        dateColumn = 0,
    )
    val TRANSFERS = TabSpec(
        "Transfers",
        listOf("Date", "From Account", "To Account", "From Amount", "From Cur", "To Amount", "To Cur", "Comment", "UID", "Created", "Modified"),
        dateColumn = 0,
    )
    val ACCOUNTS = TabSpec("Accounts", listOf("Account", "Currency", "Active", "Cached Balance", "UID"))
    val CATEGORIES = TabSpec("Categories", listOf("Category", "Type", "Used", "UID", "Icon", "Color", "Limit", "Position"))
    val TAGS = TabSpec("Tags", listOf("Tag", "UID"))
    val DELETED = TabSpec("_deleted", listOf("Entity", "UID", "DeletedAt"), hidden = true)
    val META = TabSpec("_meta", listOf("Key", "Value"), hidden = true)

    val ALL = listOf(TRANSACTIONS, TRANSFERS, ACCOUNTS, CATEGORIES, TAGS, DELETED, META)

    const val SCHEMA_VERSION = 1
}
