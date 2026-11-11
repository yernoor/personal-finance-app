package dev.yernur.moneytracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yernur.moneytracker.data.security.RecentsMode
import dev.yernur.moneytracker.data.wallet.PendingPayment
import dev.yernur.moneytracker.data.wallet.WalletPrompts.Companion.pendingFrom
import dev.yernur.moneytracker.ui.AppLaunch
import dev.yernur.moneytracker.ui.WalletPickerDialog
import dev.yernur.moneytracker.ui.LockScreen
import dev.yernur.moneytracker.widget.EXTRA_KIND
import dev.yernur.moneytracker.widget.EXTRA_UID
import androidx.lifecycle.lifecycleScope
import dev.yernur.moneytracker.ui.AppRoot
import dev.yernur.moneytracker.ui.AppTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MoneyApp).container
        askForNotificationsOnce()
        // Hide: a standing switch (API 33), not a flag set in onPause: the system takes the snapshot before that reaches the window.
        // It affects only the recents thumbnail, so screenshots inside the app still work.
        lifecycleScope.launch { container.settings.recentsMode.collect { setRecentsScreenshotEnabled(it != RecentsMode.Hide) } }
        launch = if (savedInstanceState == null) launchFrom(intent) else null // a rotation or theme change must not replay the widget tap
        walletPending = if (savedInstanceState == null) pendingFrom(intent) else null
        lifecycleScope.launch { container.dailyWealthRefresh() }
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AppTheme {
                    val locked by container.lock.locked.collectAsStateWithLifecycle()
                    val pin by container.settings.pin.collectAsStateWithLifecycle()
                    val biometric by container.settings.biometricUnlock.collectAsStateWithLifecycle()
                    Box {
                        // Kept composed under the lock screen, so unlocking returns to the same screen.
                        AppRoot(launch = launch, onLaunched = { launch = null })
                        walletPending?.takeIf { !locked }?.let { WalletPickerDialog(it) { walletPending = null } }
                        pin?.takeIf { locked }?.let { LockScreen(container.lock, it.length, biometric) }
                    }
                }
            }
        }
    }

    /** A widget tap that asked for an add screen or an operation; consumed once by AppRoot. */
    private var launch by mutableStateOf<AppLaunch?>(null)

    /** A tapped Wallet payment notification: the category picker opens over the app. */
    private var walletPending by mutableStateOf<PendingPayment?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        launch = launchFrom(intent)
        pendingFrom(intent)?.let { walletPending = it }
    }

    private fun launchFrom(intent: android.content.Intent?): AppLaunch? {
        val kind = intent?.getStringExtra(EXTRA_KIND)
        val uid = intent?.getStringExtra(EXTRA_UID)
        return if (kind == null && uid == null) null else AppLaunch(kind, uid)
    }

    override fun onStop() {
        super.onStop()
        (application as MoneyApp).container.lock.onBackground()
    }

    override fun onStart() {
        super.onStart()
        (application as MoneyApp).container.lock.onForeground()
    }

    /** Android 13+ needs a runtime grant before limit warnings can notify; ask on the first launch only. */
    private fun askForNotificationsOnce() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted || prefs.getBoolean("askedNotifications", false)) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
