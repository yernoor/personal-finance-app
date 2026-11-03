package dev.yernur.moneytracker.data.security

import dev.yernur.moneytracker.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private fun lockWithPin(pin: String = "1234", autoLock: Int = 30): Pair<AppLock, AppSettings> {
        val settings = AppSettings()
        settings.setAutoLockSeconds(autoLock)
        AppLock(settings).setPin(pin)
        return AppLock(settings) to settings // a new AppLock = a cold start
    }

    @Test
    fun theStoredPin_isASaltedHash_notThePin() {
        val (_, settings) = lockWithPin("1234")
        val stored = settings.pin.value!!
        assertNotEquals("1234", stored.hash)
        assertEquals(4, stored.length)
        assertTrue(Pin.matches("1234", stored.salt, stored.hash))
        assertFalse(Pin.matches("1235", stored.salt, stored.hash))
        assertNotEquals(Pin.hash("1234", Pin.newSalt()), Pin.hash("1234", Pin.newSalt()))
    }

    @Test
    fun coldStartIsLocked_andTheRightPinUnlocks() {
        val (lock, _) = lockWithPin()
        assertTrue(lock.locked.value)
        assertEquals(UnlockResult.Wrong, lock.attempt("0000"))
        assertTrue(lock.locked.value)
        assertEquals(UnlockResult.Ok, lock.attempt("1234"))
        assertFalse(lock.locked.value)
    }

    @Test
    fun afterFiveWrongTries_thePadMakesYouWait_evenForTheRightPin_thenAllowsAgain() {
        val (lock, _) = lockWithPin()
        repeat(4) { assertEquals(UnlockResult.Wrong, lock.attempt("0000", now = 1_000)) }
        assertEquals(UnlockResult.Wait(30), lock.attempt("0000", now = 1_000))
        assertEquals(UnlockResult.Wait(10), lock.attempt("1234", now = 21_000)) // still waiting: the right PIN does not skip it
        assertEquals(UnlockResult.Ok, lock.attempt("1234", now = 31_000))
    }

    @Test
    fun theWaitGrowsWithEveryFurtherFailure() {
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 30_000L, 60_000L, 300_000L, 900_000L), (0..8).map { Pin.lockoutMillis(it) })
    }

    @Test
    fun comingBack_locksOnlyAfterTheAutoLockDelay() {
        val (lock, _) = lockWithPin(autoLock = 30)
        lock.attempt("1234")
        lock.onBackground(now = 0)
        lock.onForeground(now = 29_000)
        assertFalse(lock.locked.value)
        lock.onBackground(now = 0)
        lock.onForeground(now = 30_000)
        assertTrue(lock.locked.value)
    }

    @Test
    fun withoutAPin_nothingIsEverLocked() {
        val settings = AppSettings()
        val lock = AppLock(settings)
        assertFalse(lock.locked.value)
        lock.onBackground(0)
        lock.onForeground(1_000_000)
        assertFalse(lock.locked.value)
    }

    @Test
    fun removingThePin_unlocks() {
        val (lock, settings) = lockWithPin()
        lock.removePin()
        assertFalse(lock.locked.value)
        assertEquals(null, settings.pin.value)
    }
}
