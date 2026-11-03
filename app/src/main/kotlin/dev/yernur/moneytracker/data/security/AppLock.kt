package dev.yernur.moneytracker.data.security

import dev.yernur.moneytracker.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** What the recents (app switcher) thumbnail shows. Screenshots inside the app are never blocked. */
enum class RecentsMode(val label: String) { Off("Show the app"), Blur("Blur it"), Hide("Hide it") }

/** The PIN is stored only as a salted PBKDF2 hash. A 4-6 digit PIN still falls to a brute force on a rooted phone: this keeps out someone holding the unlocked-looking phone, not a forensic lab. */
object Pin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    private const val ITERATIONS = 100_000

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(pin: String, salt: String): String =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), ITERATIONS, 256))
            .encoded.toHex()

    /** Free tries first, then a growing wait: 5 wrong -> 30 s, 6 -> 1 min, 7 -> 5 min, 8 or more -> 15 min. */
    fun lockoutMillis(failures: Int): Long = when {
        failures < 5 -> 0
        failures == 5 -> 30_000
        failures == 6 -> 60_000
        failures == 7 -> 300_000
        else -> 900_000
    }

    fun matches(pin: String, salt: String, hash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

sealed interface UnlockResult {
    data object Ok : UnlockResult
    data object Wrong : UnlockResult
    data class Wait(val seconds: Long) : UnlockResult
}

/** Whether the app is locked right now, and the PIN checks behind it. Locked from a cold start when a PIN is set. */
class AppLock(private val settings: AppSettings) {
    private val _locked = MutableStateFlow(settings.pin.value != null)
    val locked: StateFlow<Boolean> = _locked
    private var leftAt: Long? = null

    fun onBackground(now: Long = System.currentTimeMillis()) {
        leftAt = now
    }

    /** Coming back to the screen: lock when the auto-lock delay has passed (0 = always). */
    fun onForeground(now: Long = System.currentTimeMillis()) {
        val left = leftAt ?: return
        leftAt = null
        if (settings.pin.value != null && now - left >= settings.autoLockSeconds.value * 1000L) _locked.value = true
    }

    fun lockNow() {
        if (settings.pin.value != null) _locked.value = true
    }

    /** Checks [pin] against the stored one, counting failures; a right PIN also unlocks. */
    fun attempt(pin: String, now: Long = System.currentTimeMillis()): UnlockResult {
        val stored = settings.pin.value ?: return UnlockResult.Ok.also { _locked.value = false }
        val (failures, until) = settings.pinFailures.value
        if (now < until) return UnlockResult.Wait((until - now + 999) / 1000)
        if (Pin.matches(pin, stored.salt, stored.hash)) {
            settings.setPinFailures(0, 0)
            _locked.value = false
            return UnlockResult.Ok
        }
        val next = failures + 1
        val wait = Pin.lockoutMillis(next)
        settings.setPinFailures(next, if (wait > 0) now + wait else 0)
        return if (wait > 0) UnlockResult.Wait(wait / 1000) else UnlockResult.Wrong
    }

    /** The phone's own biometric check succeeded (it ignores the wrong-PIN wait: that guards PIN guessing, not the owner's finger). */
    fun unlockWithBiometric() {
        if (!settings.biometricUnlock.value || settings.pin.value == null) return
        settings.setPinFailures(0, 0)
        _locked.value = false
    }

    fun setPin(pin: String) {
        val salt = Pin.newSalt()
        settings.setPin(StoredPin(Pin.hash(pin, salt), salt, pin.length))
        settings.setPinFailures(0, 0)
    }

    fun removePin() {
        settings.setPin(null)
        settings.setBiometricUnlock(false)
        settings.setPinFailures(0, 0)
        _locked.value = false
    }
}

data class StoredPin(val hash: String, val salt: String, val length: Int)
