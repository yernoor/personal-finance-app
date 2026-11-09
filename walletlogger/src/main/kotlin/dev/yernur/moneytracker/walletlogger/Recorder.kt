package dev.yernur.moneytracker.walletlogger

/** The privacy gate. `read` is only called for allow-listed packages, so other apps' content is never touched. */
class Recorder(private val store: LogStore, private val ownPackage: String) {
    fun onPosted(packageName: String, read: () -> LoggedNotification) {
        if (packageName == ownPackage) return
        if (store.isAllowed(packageName)) store.append(read()) else store.noteSeen(packageName)
    }
}
